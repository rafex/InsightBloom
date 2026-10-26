"""Internal HTTP API for isolated provider credentials and completions."""

from __future__ import annotations

import hmac
import asyncio
import http.client
import ipaddress
import os
import re
import socket
import ssl
import time
from typing import Any, Optional
from urllib.parse import urlsplit

import httpx
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, field_validator

from .catalog import get_model
from .crypto import KeyRing
from .store import CAPABILITIES, CredentialStore

app = FastAPI(title="InsightBloom LLM Gateway", docs_url=None, redoc_url=None)
has_master_key = any(os.environ.get(f"LLM_CREDENTIALS_MASTER_KEY_V{n}") for n in range(1, 10))
keyring = KeyRing.from_environment() if has_master_key else None
store = CredentialStore(os.environ.get("LLM_GATEWAY_DATABASE", "/data/llm-gateway.sqlite3"), keyring) if keyring else None

SERVICE_ENV = {
    "users": "LLM_GATEWAY_USERS_TOKEN",
    "chat": "LLM_GATEWAY_CHAT_TOKEN",
    "survey": "LLM_GATEWAY_SURVEY_TOKEN",
}
SERVICE_CAPABILITIES = {"chat": {"chat"}, "survey": {"tutor", "survey", "seatLayout"}, "users": {"email", "seatLayout"}}


@app.exception_handler(RequestValidationError)
async def validation_error_without_echo(request: Request, exc: RequestValidationError) -> JSONResponse:
    # Pydantic's default error includes rejected input values; request bodies can contain API keys.
    return JSONResponse(status_code=422, content={"error": {"code": "request_invalid", "message": "Request validation failed"}})


class CredentialInput(BaseModel):
    scopeType: str = Field(pattern="^(event|user)$")
    scopeId: str = Field(min_length=1, max_length=128)
    capability: str
    provider: str
    model: str
    apiKey: str = Field(default="", max_length=4096)
    baseUrl: Optional[str] = Field(default=None, max_length=2048)


class CompletionInput(BaseModel):
    organizerId: str = Field(min_length=1, max_length=128)
    eventId: str = Field(min_length=1, max_length=128)
    capability: str
    contextToken: str = Field(min_length=32, max_length=4096)
    messages: list[dict[str, str]] = Field(min_length=1, max_length=40)
    temperature: Optional[float] = Field(default=None, ge=0, le=2)

    @field_validator("messages")
    @classmethod
    def validate_messages(cls, messages: list[dict[str, str]]) -> list[dict[str, str]]:
        if sum(len(str(item.get("content", "")).encode("utf-8")) for item in messages) > 100_000:
            raise ValueError("request_too_large")
        if any(item.get("role") not in {"system", "user", "assistant"} or not isinstance(item.get("content"), str)
               or set(item) - {"role", "content"} for item in messages):
            raise ValueError("messages_invalid")
        return messages


def _store() -> CredentialStore:
    if store is None:
        raise HTTPException(503, "llm_gateway_not_configured")
    return store


def _authenticate(service: str, token: Optional[str]) -> None:
    variable = SERVICE_ENV.get(service)
    expected = os.environ.get(variable or "", "")
    if not expected or not token or not hmac.compare_digest(expected, token):
        raise HTTPException(401, "service_auth_required")


def _public_https_target(url: str) -> tuple[str, int, str, str]:
    """Validate an HTTPS endpoint and return a DNS-pinned target and request path."""
    try:
        parsed = urlsplit(url)
        if (parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password
                or parsed.query or parsed.fragment or parsed.port not in (None, 443)):
            raise ValueError("endpoint_invalid")
        host = parsed.hostname.rstrip(".").lower()
        if not re.fullmatch(r"[a-z0-9.-]{1,253}", host) or ".." in host:
            raise ValueError("endpoint_invalid")
        port = 443
        addresses = {item[4][0] for item in socket.getaddrinfo(host, port, type=socket.SOCK_STREAM)}
        if not addresses:
            raise ValueError("endpoint_unavailable")
        for address in addresses:
            parsed_ip = ipaddress.ip_address(address.split("%", 1)[0])
            if not parsed_ip.is_global:
                raise ValueError("endpoint_not_public")
        pinned_ip = sorted(addresses)[0]
        path = parsed.path.rstrip("/")
        return host, port, pinned_ip, path
    except ValueError:
        raise
    except Exception as exc:
        raise ValueError("endpoint_unavailable") from exc


class _PinnedHTTPSConnection(http.client.HTTPSConnection):
    def __init__(self, host: str, port: int, pinned_ip: str):
        super().__init__(host, port, timeout=30, context=ssl.create_default_context())
        self.pinned_ip = pinned_ip

    def connect(self) -> None:
        raw = socket.create_connection((self.pinned_ip, self.port), self.timeout)
        self.sock = self._context.wrap_socket(raw, server_hostname=self.host)


def _post_provider(url: str, api_key: str, payload: dict[str, Any]) -> dict[str, Any]:
    host, port, pinned_ip, path = _public_https_target(url)
    connection = _PinnedHTTPSConnection(host, port, pinned_ip)
    try:
        body = __import__("json").dumps(payload).encode("utf-8")
        connection.request("POST", path, body=body, headers={
            "Authorization": f"Bearer {api_key}", "Content-Type": "application/json",
            "Host": host, "Connection": "close",
        })
        response = connection.getresponse()
        data = response.read(1_000_001)
        if response.status < 200 or response.status >= 300 or len(data) > 1_000_000:
            raise ValueError("provider_request_failed")
        result = __import__("json").loads(data)
        if not isinstance(result, dict):
            raise ValueError("provider_response_invalid")
        return result
    finally:
        connection.close()


def _verify_context_token(token: str, *, organizer_id: str, event_id: str, capability: str) -> None:
    """The users service signs short-lived, event-scoped claims; token format is payload.signature."""
    secret = os.environ.get("LLM_GATEWAY_CONTEXT_SIGNING_KEY", "")
    if len(secret) < 32:
        raise HTTPException(503, "llm_gateway_context_validation_unavailable")
    try:
        import base64
        import hashlib
        import json
        payload_part, signature = token.split(".", 1)
        expected = hmac.new(secret.encode(), payload_part.encode(), hashlib.sha256).digest()
        provided = base64.urlsafe_b64decode(signature + "=" * (-len(signature) % 4))
        if not hmac.compare_digest(expected, provided):
            raise ValueError("signature")
        payload = json.loads(base64.urlsafe_b64decode(payload_part + "=" * (-len(payload_part) % 4)))
        if payload.get("sub") != organizer_id or payload.get("event") != event_id:
            raise ValueError("scope")
        if capability not in payload.get("capabilities", []) or int(payload.get("exp", 0)) <= int(time.time()):
            raise ValueError("claims")
    except Exception as exc:
        raise HTTPException(403, "event_context_invalid") from exc


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok" if store is not None else "disabled"}


@app.get("/internal/catalog")
def catalog(x_service_id: Optional[str] = Header(default=None), x_service_token: Optional[str] = Header(default=None)) -> dict[str, Any]:
    if x_service_id != "users":
        raise HTTPException(403, "credential_management_forbidden")
    _authenticate(x_service_id, x_service_token)
    from .catalog import CATALOG
    return {"data": {"providers": [{"provider": item.provider, "model": item.model,
            "baseUrl": item.endpoint.removesuffix("/chat/completions")} for item in CATALOG.values()]}}


@app.put("/internal/credentials")
def set_credential(body: CredentialInput, x_service_id: Optional[str] = Header(default=None), x_service_token: Optional[str] = Header(default=None), x_actor_id: Optional[str] = Header(default=None)) -> dict[str, Any]:
    if x_service_id != "users":
        raise HTTPException(403, "credential_management_forbidden")
    _authenticate("users", x_service_token)
    if not x_actor_id or len(x_actor_id) > 128:
        raise HTTPException(400, "actor_id_invalid")
    if body.capability not in CAPABILITIES:
        raise HTTPException(400, "capability_invalid")
    try:
        if body.provider == "custom":
            if not body.baseUrl or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}", body.model):
                raise ValueError("provider_model_invalid")
            _public_https_target(body.baseUrl.rstrip("/") + "/chat/completions")
        else:
            get_model(body.provider, body.model)
            if body.baseUrl:
                raise ValueError("catalog_url_cannot_be_overridden")
        _store().set(scope_type=body.scopeType, scope_id=body.scopeId, capability=body.capability,
                     provider=body.provider, model=body.model, api_key=body.apiKey, actor=x_actor_id,
                     base_url=(body.baseUrl.rstrip("/") + "/chat/completions") if body.baseUrl else None)
        return {"data": {"configured": True, "provider": body.provider, "model": body.model}}
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@app.delete("/internal/credentials/{scope_type}/{scope_id}/{capability}")
def clear_credential(scope_type: str, scope_id: str, capability: str,
                     x_service_id: Optional[str] = Header(default=None), x_service_token: Optional[str] = Header(default=None), x_actor_id: Optional[str] = Header(default=None)) -> dict[str, Any]:
    if x_service_id != "users":
        raise HTTPException(403, "credential_management_forbidden")
    _authenticate("users", x_service_token)
    if not x_actor_id or len(x_actor_id) > 128:
        raise HTTPException(400, "actor_id_invalid")
    if scope_type not in {"event", "user"} or capability not in CAPABILITIES:
        raise HTTPException(400, "credential_scope_invalid")
    _store().clear(scope_type=scope_type, scope_id=scope_id, capability=capability, actor=x_actor_id)
    return {"data": {"configured": False}}


@app.get("/internal/credentials/{scope_type}/{scope_id}/{capability}")
def credential_status(scope_type: str, scope_id: str, capability: str,
                      x_service_id: Optional[str] = Header(default=None), x_service_token: Optional[str] = Header(default=None)) -> dict[str, Any]:
    if x_service_id not in SERVICE_CAPABILITIES:
        raise HTTPException(403, "credential_management_forbidden")
    _authenticate(x_service_id, x_service_token)
    status_capabilities = CAPABILITIES if x_service_id == "users" else SERVICE_CAPABILITIES[x_service_id]
    if capability not in status_capabilities or scope_type not in {"event", "user"} or (scope_type == "user" and x_service_id != "users"):
        raise HTTPException(400, "credential_scope_invalid")
    if x_service_id != "users" and os.environ.get("LLM_USERS_RESOLUTION_URL"):
        return {"data": _effective_status(scope_id, capability)}
    return {"data": _store().status(scope_type=scope_type, scope_id=scope_id, capability=capability)}


def _resolution_context(event_id: str, capability: str) -> dict[str, Any]:
    url = os.environ.get("LLM_USERS_RESOLUTION_URL", "").rstrip("/")
    token = os.environ.get("LLM_GATEWAY_USERS_TOKEN", "")
    if not url or not token:
        raise HTTPException(503, "resolution_unavailable")
    try:
        # Internal fixed deployment URL only; no caller-supplied host or redirects.
        response = httpx.get(f"{url}/{event_id}/{capability}", headers={"X-Service-Token": token}, timeout=5, follow_redirects=False)
        response.raise_for_status()
        data = response.json()["data"]
        if data.get("mode") not in {"AUTO", "EVENT", "INHERIT_OWNER", "MEMBER_PROFILE", "DISABLED"} or not data.get("ownerId"):
            raise ValueError("context_invalid")
        return data
    except Exception as exc:
        raise HTTPException(503, "resolution_unavailable") from exc


def _effective_status(event_id: str, capability: str) -> dict[str, Any]:
    if capability not in CAPABILITIES or not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", event_id):
        raise HTTPException(400, "scope_invalid")
    ctx = _resolution_context(event_id, capability)
    mode = ctx["mode"]
    event = _store().status(scope_type="event", scope_id=event_id, capability=capability)
    if mode == "AUTO":
        mode = "EVENT" if event["configured"] else "INHERIT_OWNER"
    scope, identity, source, fallback = "event", event_id, "EVENT", None
    status = event
    if mode == "DISABLED":
        status = {"configured": False}; source = "DISABLED"
    elif mode != "EVENT":
        scope, identity, source = "user", ctx["ownerId"], "OWNER"
        status = _store().status(scope_type="user", scope_id=identity, capability=capability) if ctx.get("ownerEligible") else {"configured": False}
        if mode == "MEMBER_PROFILE":
            member = _store().status(scope_type="user", scope_id=ctx["memberId"], capability=capability) if ctx.get("memberEligible") and ctx.get("memberId") else {"configured": False}
            if member["configured"]:
                identity, source, status = ctx["memberId"], "MEMBER_PROFILE", member
            else:
                fallback = "linked_profile_unavailable"
    if not status["configured"]:
        source = "DISABLED"
    result = {**status, "mode": mode, "effectiveSource": source, "fallbackReason": fallback,
              "ownerId": ctx["ownerId"], "credentialOwnerId": identity if status["configured"] and scope == "user" else ctx["ownerId"],
              "scopeType": scope, "scopeId": identity, "canManage": ctx.get("canManage", False),
              "linkedProfileName": ctx.get("linkedProfileName"), "eventConfiguration": event}
    _store().record_source(event_id, capability, source + ":" + (identity if status["configured"] else "none") + ":" + (fallback or ""))
    return result


@app.get("/internal/effective/{event_id}/{capability}")
def effective_status(event_id: str, capability: str, x_service_id: Optional[str] = Header(default=None), x_service_token: Optional[str] = Header(default=None)) -> dict[str, Any]:
    if x_service_id != "users":
        raise HTTPException(403, "credential_management_forbidden")
    _authenticate("users", x_service_token)
    return {"data": _effective_status(event_id, capability)}


@app.post("/internal/complete")
async def complete(body: CompletionInput, x_service_id: Optional[str] = Header(default=None), x_service_token: Optional[str] = Header(default=None)) -> dict[str, Any]:
    if x_service_id not in SERVICE_CAPABILITIES:
        raise HTTPException(401, "service_auth_required")
    _authenticate(x_service_id, x_service_token)
    if body.capability not in SERVICE_CAPABILITIES[x_service_id]:
        raise HTTPException(403, "service_capability_forbidden")
    _verify_context_token(body.contextToken, organizer_id=body.organizerId, event_id=body.eventId, capability=body.capability)
    try:
        effective = _effective_status(body.eventId, body.capability) if os.environ.get("LLM_USERS_RESOLUTION_URL") else None
        if effective and not effective["configured"]:
            raise HTTPException(404, "llm_not_configured")
        resolved = _store().resolve(event_id=body.eventId, capability=body.capability,
            scope_type=effective["scopeType"] if effective else "event", scope_id=effective["scopeId"] if effective else None)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(503, "credential_unavailable") from exc
    if resolved is None:
        raise HTTPException(404, "llm_not_configured")
    provider, model, api_key, custom_endpoint = resolved
    try:
        if provider == "custom":
            endpoint = custom_endpoint
        else:
            selected = get_model(provider, model)
            endpoint = selected.endpoint
    except ValueError as exc:
        raise HTTPException(503, "provider_model_unavailable") from exc
    if not _store().consume_quota(organizer_id=effective["ownerId"] if effective else body.organizerId, event_id=body.eventId):
        raise HTTPException(429, "llm_rate_limit")
    _store().audit_request(actor=x_service_id, event_id=body.eventId, capability=body.capability)
    _store().record_usage(body.eventId, effective["credentialOwnerId"] if effective else body.organizerId, body.capability)
    payload: dict[str, Any] = {"model": model, "messages": body.messages, "max_tokens": 2048}
    if body.temperature is not None:
        payload["temperature"] = body.temperature
    try:
        return {"data": await asyncio.to_thread(_post_provider, endpoint, api_key, payload)}
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(502, "llm_provider_unavailable") from exc
