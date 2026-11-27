"""Private Ed25519 ticket signer backed by gopass.

This service is deliberately small: it accepts only event-scoped ticket payloads and
never returns private key material. Keep it on a ClusterIP and restrict ingress to users.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import re
import subprocess
import threading
import uuid
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)
TOKEN = os.environ.get("TICKET_SIGNER_API_TOKEN", "")
STORE_PREFIX = "insightbloom/ticket-signing/events"
MAX_PAYLOAD = 16_384
UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", re.I)
KEY_LOCK = threading.Lock()


class SignRequest(BaseModel):
    eventUuid: str = Field(min_length=36, max_length=36)
    payload: str = Field(min_length=1, max_length=MAX_PAYLOAD * 2)


class SignResponse(BaseModel):
    keyId: str
    publicKeySpki: str
    signature: str


def authorize(authorization: Annotated[str | None, Header()] = None) -> None:
    if not TOKEN or not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="unauthorized")
    if not hmac.compare_digest(authorization[7:], TOKEN):
        raise HTTPException(status_code=401, detail="unauthorized")


def store_path(event_uuid: str) -> str:
    if not UUID_RE.fullmatch(event_uuid):
        raise HTTPException(status_code=400, detail="event_uuid_invalid")
    return f"{STORE_PREFIX}/{str(uuid.UUID(event_uuid))}"


def gopass(*args: str, input_text: str | None = None) -> str:
    try:
        result = subprocess.run(
            ["gopass", *args], input=input_text, text=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=15, check=False,
            env={**os.environ, "GOPASS_NO_COLOR": "1"},
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise HTTPException(status_code=503, detail="secret_store_unavailable") from exc
    if result.returncode != 0:
        raise HTTPException(status_code=503, detail="secret_store_operation_failed")
    return result.stdout.strip()


def load_or_create_key(event_uuid: str) -> tuple[str, Ed25519PrivateKey]:
    path = store_path(event_uuid)
    # Listing the store first distinguishes an absent key from an unavailable or
    # locked gopass store; never replace an existing secret after an operational error.
    listed = gopass("ls", "--flat")
    if path in listed.splitlines():
        encoded = gopass("show", "--password", path)
        seed = base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4))
        if len(seed) != 32:
            raise HTTPException(status_code=503, detail="stored_signing_key_invalid")
        key = Ed25519PrivateKey.from_private_bytes(seed)
    else:
        key = Ed25519PrivateKey.generate()
        seed = key.private_bytes(serialization.Encoding.Raw, serialization.PrivateFormat.Raw,
                                 serialization.NoEncryption())
        encoded = base64.urlsafe_b64encode(seed).decode().rstrip("=")
        gopass("insert", "--force", "--multiline", path, input_text=encoded)
    public_spki = key.public_key().public_bytes(serialization.Encoding.DER,
                                                serialization.PublicFormat.SubjectPublicKeyInfo)
    key_id = hashlib.sha256(public_spki).hexdigest()[:32]
    return base64.urlsafe_b64encode(public_spki).decode().rstrip("="), key


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/internal/v1/tickets/sign", response_model=SignResponse, dependencies=[Depends(authorize)])
def sign_ticket(request: SignRequest) -> SignResponse:
    try:
        payload = base64.urlsafe_b64decode(request.payload + "=" * (-len(request.payload) % 4))
        if not payload or len(payload) > MAX_PAYLOAD:
            raise ValueError("size")
        claims = json.loads(payload)
        if not isinstance(claims, dict) or claims.get("conferenceUuid") != str(uuid.UUID(request.eventUuid)):
            raise ValueError("event")
    except (ValueError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise HTTPException(status_code=400, detail="ticket_payload_invalid") from exc
    # A single signer replica still handles concurrent requests. Serialize load/create
    # so two first-use requests cannot return signatures from different event keys.
    with KEY_LOCK:
        public_key, private_key = load_or_create_key(request.eventUuid)
    # Match the canonical COSE_Sign1 Sig_structure used by the Java verifier.
    length = len(payload)
    if length < 24:
        payload_header = bytes([0x40 + length])
    elif length <= 0xFF:
        payload_header = b"\x58" + bytes([length])
    elif length <= 0xFFFF:
        payload_header = b"\x59" + length.to_bytes(2, "big")
    else:
        payload_header = b"\x5a" + length.to_bytes(4, "big")
    sig_structure = b"\x84\x6aSignature1\x43\xa1\x01\x27\x40" + payload_header + payload
    signature = private_key.sign(sig_structure)
    spki = base64.urlsafe_b64decode(public_key + "=" * (-len(public_key) % 4))
    return SignResponse(keyId=hashlib.sha256(spki).hexdigest()[:32], publicKeySpki=public_key,
                        signature=base64.urlsafe_b64encode(signature).decode().rstrip("="))
