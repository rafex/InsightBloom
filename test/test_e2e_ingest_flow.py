"""
Test end-to-end del flujo de ingesta de mensajes.

Crear una conferencia requiere una cuenta ORGANIZER, y el registro
público siempre asigna ATTENDEE (ver test_e2e_auth_flow.py). Estos
tests por lo tanto requieren un organizador ya sembrado (p.ej. el
usuario "admin" del CLI) y se omiten si no se proveen credenciales via
TEST_ORGANIZER_EMAIL / TEST_ORGANIZER_PASSWORD.
"""

import os
import uuid

import httpx
import pytest


pytestmark = pytest.mark.usefixtures("compose_up")

_ORGANIZER_EMAIL = os.getenv("TEST_ORGANIZER_EMAIL")
_ORGANIZER_PASSWORD = os.getenv("TEST_ORGANIZER_PASSWORD")

requires_organizer = pytest.mark.skipif(
    not (_ORGANIZER_EMAIL and _ORGANIZER_PASSWORD),
    reason="Set TEST_ORGANIZER_EMAIL/TEST_ORGANIZER_PASSWORD for a pre-seeded organizer account",
)


def _get_guest_token(users_url: str, conference_uuid: str) -> str:
    r = httpx.post(
        f"{users_url}/api/v1/auth/guest",
        json={
            "displayName": "Attendee",
            "deviceFingerprint": f"test-{uuid.uuid4().hex[:8]}",
            "conferenceUuid": conference_uuid,
        },
    )
    assert r.status_code == 201
    return r.json()["data"]["token"]


def _get_organizer_token_and_conference(users_url: str) -> tuple[str, str]:
    r = httpx.post(
        f"{users_url}/api/v1/auth/login",
        json={"username": _ORGANIZER_EMAIL, "password": _ORGANIZER_PASSWORD},
    )
    assert r.status_code == 201, "El organizador de prueba no existe o la contraseña es incorrecta"
    token = r.json()["data"]["token"]
    r = httpx.post(
        f"{users_url}/api/v1/conferences",
        json={"name": f"IngestTest-{uuid.uuid4().hex[:8]}"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert r.status_code == 201
    conf_uuid = r.json()["data"]["uuid"]
    return token, conf_uuid


@requires_organizer
def test_ingest_message(users_url, ingest_url):
    _, conf_uuid = _get_organizer_token_and_conference(users_url)
    _get_guest_token(users_url, conf_uuid)

    r = httpx.post(
        f"{ingest_url}/api/v1/webhooks/messages",
        json={
            "conferenceId": conf_uuid,
            "author": {"displayName": "TestUser", "kind": "guest"},
            "message": {
                "type": "doubt",
                "word": "inteligencia",
                "detail": "¿Cómo afecta la IA al mercado laboral?",
            },
            "receivedAt": None,
        },
    )
    assert r.status_code == 201
    data = r.json()
    assert "data" in data
    # El endpoint responde un resumen, no el mensaje completo.
    assert "messageId" in data["data"]
    assert data["data"]["status"] in ("visible", "censurado_auto", "pendiente_revision")


@requires_organizer
def test_ingest_shows_in_cloud(users_url, ingest_url, query_url):
    _, conf_uuid = _get_organizer_token_and_conference(users_url)
    _get_guest_token(users_url, conf_uuid)

    httpx.post(
        f"{ingest_url}/api/v1/webhooks/messages",
        json={
            "conferenceId": conf_uuid,
            "author": {"displayName": "User", "kind": "guest"},
            "message": {
                "type": "topic",
                "word": "blockchain",
                "detail": "Aplicaciones de blockchain",
            },
            "receivedAt": None,
        },
    )

    r = httpx.get(f"{query_url}/api/v1/conferences/{conf_uuid}/cloud/topics")
    assert r.status_code == 200
    words = r.json().get("data", [])
    assert any(w["wordNormalized"] == "blockchain" for w in words)
