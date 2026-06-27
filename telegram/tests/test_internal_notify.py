"""Tests para el endpoint saliente POST /internal/notify (protegido con X-Internal-Auth)."""

from unittest.mock import AsyncMock

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from routers import internal


class FakeDb:
    def __init__(self, chats):
        self._chats = chats

    def find_notification_chats(self, conference_uuid):
        return self._chats


def build_app(chats):
    app = FastAPI()
    app.state.db = FakeDb(chats)
    app.include_router(internal.router)
    return app


@pytest.fixture(autouse=True)
def reset_internal_api_key(monkeypatch):
    monkeypatch.setattr(internal, "INTERNAL_API_KEY", "")
    yield


class TestInternalNotifyAuth:
    def test_no_key_configured_allows_request(self, monkeypatch):
        monkeypatch.setattr(internal, "INTERNAL_API_KEY", "")
        app = build_app([])
        client = TestClient(app)
        r = client.post("/internal/notify", json={"conferenceUuid": "uuid-1", "message": "hola"})
        assert r.status_code == 200

    def test_key_configured_rejects_missing_header(self, monkeypatch):
        monkeypatch.setattr(internal, "INTERNAL_API_KEY", "secret123")
        app = build_app([])
        client = TestClient(app)
        r = client.post("/internal/notify", json={"conferenceUuid": "uuid-1", "message": "hola"})
        assert r.status_code == 403

    def test_key_configured_rejects_wrong_header(self, monkeypatch):
        monkeypatch.setattr(internal, "INTERNAL_API_KEY", "secret123")
        app = build_app([])
        client = TestClient(app)
        r = client.post(
            "/internal/notify",
            json={"conferenceUuid": "uuid-1", "message": "hola"},
            headers={"X-Internal-Auth": "wrong"},
        )
        assert r.status_code == 403

    def test_key_configured_accepts_correct_header(self, monkeypatch):
        monkeypatch.setattr(internal, "INTERNAL_API_KEY", "secret123")
        app = build_app([])
        client = TestClient(app)
        r = client.post(
            "/internal/notify",
            json={"conferenceUuid": "uuid-1", "message": "hola"},
            headers={"X-Internal-Auth": "secret123"},
        )
        assert r.status_code == 200


class TestInternalNotifyFanout:
    def test_sends_to_all_bound_chats(self, monkeypatch):
        send_mock = AsyncMock()
        monkeypatch.setattr(internal.telegram_client, "send_message", send_mock)
        chats = [
            {"chat_id": -1, "message_thread_id": 0},
            {"chat_id": -2, "message_thread_id": 7},
        ]
        app = build_app(chats)
        client = TestClient(app)
        r = client.post("/internal/notify", json={"conferenceUuid": "uuid-1", "message": "Respondieron tu duda"})
        assert r.status_code == 200
        assert r.json() == {"ok": True, "sent": 2}
        assert send_mock.call_count == 2
        send_mock.assert_any_call(-1, "Respondieron tu duda", None)
        send_mock.assert_any_call(-2, "Respondieron tu duda", 7)

    def test_no_bound_chats_sends_nothing(self, monkeypatch):
        send_mock = AsyncMock()
        monkeypatch.setattr(internal.telegram_client, "send_message", send_mock)
        app = build_app([])
        client = TestClient(app)
        r = client.post("/internal/notify", json={"conferenceUuid": "uuid-1", "message": "hola"})
        assert r.status_code == 200
        assert r.json() == {"ok": True, "sent": 0}
        send_mock.assert_not_called()
