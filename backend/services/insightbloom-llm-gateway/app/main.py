"""Internal HTTP API for isolated provider credentials and completions."""

from __future__ import annotations

import hmac
import os
import time
from typing import Any

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
    scopeType: str = Field(pattern="^(organizer|event)$")
    scopeId: str = Field(min_length=1, max_length=128)
    capability: str
    provider: str
    model: str
    apiKey: str = Field(min_length=1, max_length=4096)


class CompletionInput(BaseModel):
    organizerId: str = Field(min_length=1, max_length=128)
    eventId: str = Field(min_length=1, max_length=128)
    capability: str
    contextToken: str = Field(min_length=32, max_length=4096)
    messages: list[dict[str, str]] = Field(min_length=1, max_length=40)
    temperature: float | None = Field(default=None, ge=0, le=2)

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


def _authenticate(service: str, token: str | None) -> None:
    variable = SERVICE_ENV.get(service)
    expected = os.environ.get(variable or "", "")
    if not expected or not token or not hmac.compare_digest(expected, token):
        raise HTTPException(401, "service_auth_required")


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


@app.put("/internal/credentials")
def set_credential(body: CredentialInput, x_service_id: str | None = Header(default=None), x_service_token: str | None = Header(default=None), x_actor_id: str | None = Header(default=None)) -> dict[str, Any]:
    if x_service_id != "users":
        raise HTTPException(403, "credential_management_forbidden")
    _authenticate("users", x_service_token)
    if not x_actor_id or len(x_actor_id) > 128:
        raise HTTPException(400, "actor_id_invalid")
    if body.capability not in CAPABILITIES:
        raise HTTPException(400, "capability_invalid")
    try:
        get_model(body.provider, body.model)
        _store().set(scope_type=body.scopeType, scope_id=body.scopeId, capability=body.capability,
                     provider=body.provider, model=body.model, api_key=body.apiKey, actor=x_actor_id)
        return {"data": {"configured": True, "provider": body.provider, "model": body.model}}
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@app.delete("/internal/credentials/{scope_type}/{scope_id}/{capability}")
def clear_credential(scope_type: str, scope_id: str, capability: str,
                     x_service_id: str | None = Header(default=None), x_service_token: str | None = Header(default=None), x_actor_id: str | None = Header(default=None)) -> dict[str, Any]:
    if x_service_id != "users":
        raise HTTPException(403, "credential_management_forbidden")
    _authenticate("users", x_service_token)
    if not x_actor_id or len(x_actor_id) > 128:
        raise HTTPException(400, "actor_id_invalid")
    _store().clear(scope_type=scope_type, scope_id=scope_id, capability=capability, actor=x_actor_id)
    return {"data": {"configured": False}}


@app.get("/internal/credentials/{scope_type}/{scope_id}/{capability}")
def credential_status(scope_type: str, scope_id: str, capability: str,
                      x_service_id: str | None = Header(default=None), x_service_token: str | None = Header(default=None)) -> dict[str, Any]:
    if x_service_id != "users":
        raise HTTPException(403, "credential_management_forbidden")
    _authenticate("users", x_service_token)
    if capability not in CAPABILITIES or scope_type not in {"organizer", "event"}:
        raise HTTPException(400, "credential_scope_invalid")
    return {"data": _store().status(scope_type=scope_type, scope_id=scope_id, capability=capability)}


@app.post("/internal/complete")
async def complete(body: CompletionInput, x_service_id: str | None = Header(default=None), x_service_token: str | None = Header(default=None)) -> dict[str, Any]:
    if x_service_id not in SERVICE_CAPABILITIES:
        raise HTTPException(401, "service_auth_required")
    _authenticate(x_service_id, x_service_token)
    if body.capability not in SERVICE_CAPABILITIES[x_service_id]:
        raise HTTPException(403, "service_capability_forbidden")
    _verify_context_token(body.contextToken, organizer_id=body.organizerId, event_id=body.eventId, capability=body.capability)
    try:
        resolved = _store().resolve(organizer_id=body.organizerId, event_id=body.eventId, capability=body.capability)
    except Exception as exc:
        raise HTTPException(503, "credential_unavailable") from exc
    if resolved is None:
        raise HTTPException(404, "llm_not_configured")
    provider, model, api_key, _scope = resolved
    try:
        selected = get_model(provider, model)
    except ValueError as exc:
        raise HTTPException(503, "provider_model_unavailable") from exc
    if not _store().consume_quota(organizer_id=body.organizerId, event_id=body.eventId):
        raise HTTPException(429, "llm_rate_limit")
    _store().audit_request(actor=x_service_id, event_id=body.eventId, capability=body.capability)
    payload: dict[str, Any] = {"model": model, "messages": body.messages, "max_tokens": 2048}
    if body.temperature is not None:
        payload["temperature"] = body.temperature
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(30), follow_redirects=False) as client:
            response = await client.post(selected.endpoint, headers={"Authorization": f"Bearer {api_key}"}, json=payload)
        if response.status_code >= 400:
            raise HTTPException(502, "llm_provider_request_failed")
        return {"data": response.json()}
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(502, "llm_provider_unavailable") from exc
