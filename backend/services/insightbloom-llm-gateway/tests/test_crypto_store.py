import sqlite3
import base64
import hashlib
import hmac
import json

import pytest
from fastapi.testclient import TestClient

from app.crypto import InvalidCiphertext, KeyRing
from app.store import CredentialStore
from app import main as gateway


def ring(active="v1", keys=None):
    return KeyRing(active, keys or {active: b"a" * 32})


def test_ciphertext_authenticates_scope_and_tampering():
    cipher = ring()
    encrypted = cipher.encrypt("sk-private-example", associated_data=b"organizer\0org-1\0chat")
    assert "sk-private-example" not in encrypted
    assert cipher.decrypt(encrypted, associated_data=b"organizer\0org-1\0chat") == "sk-private-example"
    with pytest.raises(InvalidCiphertext):
        cipher.decrypt(encrypted[:-2] + "aa", associated_data=b"organizer\0org-1\0chat")
    with pytest.raises(InvalidCiphertext):
        cipher.decrypt(encrypted, associated_data=b"organizer\0org-2\0chat")
    with pytest.raises(InvalidCiphertext):
        ring("v2", {"v2": b"b" * 32}).decrypt(encrypted, associated_data=b"organizer\0org-1\0chat")


def test_store_persists_ciphertext_and_resolves_only_event_scope(tmp_path):
    database = str(tmp_path / "gateway.sqlite3")
    store = CredentialStore(database, ring())
    store.set(scope_type="event", scope_id="event-1", capability="chat", provider="openai",
              model="gpt-4o-mini", api_key="event-secret", actor="users")
    assert store.resolve(event_id="event-1", capability="chat")[2] == "event-secret"
    assert store.resolve(event_id="event-2", capability="chat") is None
    with pytest.raises(ValueError):
        store.set(scope_type="organizer", scope_id="org-1", capability="chat", provider="groq",
                  model="llama-3.3-70b-versatile", api_key="org-secret", actor="users")
    assert store.status(scope_type="event", scope_id="event-1", capability="chat")["configured"]
    store.clear(scope_type="event", scope_id="event-1", capability="chat", actor="users")
    assert store.resolve(event_id="event-1", capability="chat") is None
    assert sqlite3.connect(database).execute("SELECT COUNT(*) FROM credentials").fetchone()[0] == 0


def test_store_reencrypts_records_after_key_rotation(tmp_path):
    database = str(tmp_path / "gateway.sqlite3")
    old_store = CredentialStore(database, ring("v1", {"v1": b"a" * 32}))
    old_store.set(scope_type="event", scope_id="event", capability="survey", provider="groq",
                  model="llama-3.1-8b-instant", api_key="rotate-me", actor="users")
    rotating = KeyRing("v2", {"v1": b"a" * 32, "v2": b"b" * 32})
    new_store = CredentialStore(database, rotating)
    assert new_store.reencrypt_all() == 1
    raw = sqlite3.connect(database).execute("SELECT ciphertext FROM credentials").fetchone()[0]
    assert raw.startswith("v2:")
    assert new_store.resolve(event_id="event", capability="survey")[2] == "rotate-me"
    assert new_store.reencrypt_all() == 0


def test_store_without_credentials_is_disabled_and_audit_has_no_secret(tmp_path):
    store = CredentialStore(str(tmp_path / "gateway.sqlite3"), ring())
    assert store.resolve(event_id="event", capability="chat") is None
    store.set(scope_type="event", scope_id="event", capability="email", provider="groq",
              model="llama-3.1-8b-instant", api_key="not-in-audit", actor="users")
    rows = sqlite3.connect(store.database).execute("SELECT actor,action,scope_type,scope_id,capability FROM audit").fetchall()
    assert all("not-in-audit" not in str(row) for row in rows)


def test_management_api_returns_metadata_only_and_rejects_other_services(tmp_path, monkeypatch):
    store = CredentialStore(str(tmp_path / "gateway.sqlite3"), ring())
    monkeypatch.setattr(gateway, "store", store)
    monkeypatch.setenv("LLM_GATEWAY_USERS_TOKEN", "users-secret-token")
    client = TestClient(gateway.app)
    request = {
        "scopeType": "event", "scopeId": "event-1", "capability": "chat",
        "provider": "groq", "model": "llama-3.3-70b-versatile", "apiKey": "sk-sensitive-value",
    }
    denied = client.put("/internal/credentials", json=request, headers={"X-Service-Id": "chat", "X-Service-Token": "users-secret-token", "X-Actor-Id": "user-1"})
    assert denied.status_code == 403
    saved = client.put("/internal/credentials", json=request, headers={"X-Service-Id": "users", "X-Service-Token": "users-secret-token", "X-Actor-Id": "user-1"})
    assert saved.status_code == 200
    assert "sk-sensitive-value" not in saved.text
    status = client.get("/internal/credentials/event/event-1/chat", headers={"X-Service-Id": "users", "X-Service-Token": "users-secret-token"})
    assert status.status_code == 200
    assert "sk-sensitive-value" not in status.text
    assert "ciphertext" not in status.text and "apiKeyHint" not in status.text


def test_status_is_available_to_capability_scoped_service_but_not_for_other_events(tmp_path, monkeypatch):
    store = CredentialStore(str(tmp_path / "gateway.sqlite3"), ring())
    store.set(scope_type="event", scope_id="event-1", capability="tutor", provider="groq",
              model="llama-3.3-70b-versatile", api_key="event-key", actor="admin-1")
    monkeypatch.setattr(gateway, "store", store)
    monkeypatch.setenv("LLM_GATEWAY_SURVEY_TOKEN", "survey-secret-token")
    client = TestClient(gateway.app)
    status = client.get("/internal/credentials/event/event-1/tutor",
                        headers={"X-Service-Id": "survey", "X-Service-Token": "survey-secret-token"})
    assert status.status_code == 200 and status.json()["data"]["configured"] is True
    assert "event-key" not in status.text
    denied = client.get("/internal/credentials/organizer/org-1/tutor",
                        headers={"X-Service-Id": "survey", "X-Service-Token": "survey-secret-token"})
    assert denied.status_code == 400


def test_custom_endpoint_requires_public_https_and_is_dns_pinned(monkeypatch):
    import socket
    public = [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("8.8.8.8", 443))]
    monkeypatch.setattr(socket, "getaddrinfo", lambda *args, **kwargs: public)
    assert gateway._public_https_target("https://api.example.test/v1/chat/completions") == (
        "api.example.test", 443, "8.8.8.8", "/v1/chat/completions")
    with pytest.raises(ValueError, match="endpoint_invalid"):
        gateway._public_https_target("http://api.example.test/v1")
    private = [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("10.0.0.4", 443))]
    monkeypatch.setattr(socket, "getaddrinfo", lambda *args, **kwargs: private)
    with pytest.raises(ValueError, match="endpoint_not_public"):
        gateway._public_https_target("https://api.example.test/v1/chat/completions")


def test_validation_errors_do_not_echo_submitted_key(tmp_path, monkeypatch):
    monkeypatch.setattr(gateway, "store", CredentialStore(str(tmp_path / "gateway.sqlite3"), ring()))
    monkeypatch.setenv("LLM_GATEWAY_USERS_TOKEN", "users-secret-token")
    client = TestClient(gateway.app)
    response = client.put("/internal/credentials", json={"apiKey": "sk-sensitive-value"},
                          headers={"X-Service-Id": "users", "X-Service-Token": "users-secret-token", "X-Actor-Id": "user-1"})
    assert response.status_code == 422
    assert "sk-sensitive-value" not in response.text


def test_completion_requires_valid_event_context_and_scoped_service(tmp_path, monkeypatch):
    monkeypatch.setattr(gateway, "store", CredentialStore(str(tmp_path / "gateway.sqlite3"), ring()))
    monkeypatch.setenv("LLM_GATEWAY_CHAT_TOKEN", "chat-secret-token")
    signing_key = "context-signing-secret-that-is-long-enough"
    monkeypatch.setenv("LLM_GATEWAY_CONTEXT_SIGNING_KEY", signing_key)
    claims = {"sub": "org-1", "event": "event-1", "capabilities": ["chat"], "exp": 4_000_000_000}
    payload = base64.urlsafe_b64encode(json.dumps(claims).encode()).decode().rstrip("=")
    signature = hmac.new(signing_key.encode(), payload.encode(), hashlib.sha256).digest()
    token = payload + "." + base64.urlsafe_b64encode(signature).decode().rstrip("=")
    client = TestClient(gateway.app)
    body = {"organizerId": "org-1", "eventId": "event-1", "capability": "chat", "contextToken": token,
            "messages": [{"role": "user", "content": "hello"}]}
    forbidden = client.post("/internal/complete", json={**body, "capability": "email"},
                             headers={"X-Service-Id": "chat", "X-Service-Token": "chat-secret-token"})
    assert forbidden.status_code == 403
    missing = client.post("/internal/complete", json=body,
                          headers={"X-Service-Id": "chat", "X-Service-Token": "chat-secret-token"})
    assert missing.status_code == 404
    assert "apiKey" not in missing.text and "secret" not in missing.text
