"""Tests para el flujo entrante POST /telegram/webhook."""

from unittest.mock import AsyncMock

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from routers import webhook


class FakeDb:
    def __init__(self):
        self.bindings = {}
        self.processed = set()

    def mark_update_processed(self, update_id):
        if update_id in self.processed:
            return False
        self.processed.add(update_id)
        return True

    def upsert_binding(self, chat_id, message_thread_id, conference_uuid, purpose,
                        chat_title=None, user_id=None, username=None):
        self.bindings[(chat_id, message_thread_id or 0, purpose)] = conference_uuid

    def find_chat_purpose_binding(self, chat_id, message_thread_id):
        thread = message_thread_id or 0
        return self.bindings.get((chat_id, thread, "chat"))

    def find_notification_chats(self, conference_uuid):
        return [
            {"chat_id": c, "message_thread_id": t}
            for (c, t, p), u in self.bindings.items()
            if p == "notifications" and u == conference_uuid
        ]


def build_app():
    app = FastAPI()
    app.state.db = FakeDb()
    app.include_router(webhook.router)
    return app


def base_update(update_id, text, chat_id=-100123, user_id=555, username="rafex", thread_id=None):
    msg = {
        "message_id": 1,
        "chat": {"id": chat_id, "title": "Grupo Demo"},
        "from": {"id": user_id, "username": username},
        "text": text,
    }
    if thread_id is not None:
        msg["message_thread_id"] = thread_id
    return {"update_id": update_id, "message": msg}


@pytest.fixture(autouse=True)
def no_webhook_secret(monkeypatch):
    monkeypatch.setattr(webhook, "TELEGRAM_WEBHOOK_SECRET", "")
    yield


class TestWebhookAuth:
    def test_secret_configured_rejects_missing_header(self, monkeypatch):
        monkeypatch.setattr(webhook, "TELEGRAM_WEBHOOK_SECRET", "s3cr3t")
        app = build_app()
        client = TestClient(app)
        r = client.post("/telegram/webhook", json=base_update(1, "/dudas a b"))
        assert r.status_code == 401

    def test_secret_configured_accepts_correct_header(self, monkeypatch):
        monkeypatch.setattr(webhook, "TELEGRAM_WEBHOOK_SECRET", "s3cr3t")
        app = build_app()
        client = TestClient(app)
        r = client.post(
            "/telegram/webhook",
            json=base_update(1, "/start"),
            headers={"X-Telegram-Bot-Api-Secret-Token": "s3cr3t"},
        )
        assert r.status_code == 200


class TestConferenciaCommand:
    def test_admin_binds_chat(self, monkeypatch):
        import services.binding_service as binding_service
        from services import telegram_client
        monkeypatch.setattr(binding_service, "is_chat_admin", AsyncMock(return_value=True))
        monkeypatch.setattr(binding_service, "resolve_conference", AsyncMock(return_value="uuid-123"))
        monkeypatch.setattr(telegram_client, "send_message", AsyncMock())

        app = build_app()
        client = TestClient(app)
        r = client.post("/telegram/webhook", json=base_update(1, "/conferencia demo-2026 chat"))
        assert r.status_code == 200

        db: FakeDb = app.state.db
        assert db.find_chat_purpose_binding(-100123, None) == "uuid-123"
        telegram_client.send_message.assert_called_once()

    def test_non_admin_is_rejected(self, monkeypatch):
        import services.binding_service as binding_service
        from services import telegram_client
        monkeypatch.setattr(binding_service, "is_chat_admin", AsyncMock(return_value=False))
        monkeypatch.setattr(binding_service, "resolve_conference", AsyncMock(return_value="uuid-123"))
        monkeypatch.setattr(telegram_client, "send_message", AsyncMock())

        app = build_app()
        client = TestClient(app)
        r = client.post("/telegram/webhook", json=base_update(1, "/conferencia demo-2026 chat"))
        assert r.status_code == 200

        db: FakeDb = app.state.db
        assert db.find_chat_purpose_binding(-100123, None) is None
        text_sent = telegram_client.send_message.call_args.args[1]
        assert "administradores" in text_sent


class TestDudasTemasCommand:
    def test_unbound_chat_gets_warning(self, monkeypatch):
        from services import telegram_client
        monkeypatch.setattr(telegram_client, "send_message", AsyncMock())

        app = build_app()
        client = TestClient(app)
        r = client.post("/telegram/webhook", json=base_update(1, "/dudas ia ¿Qué es IA?"))
        assert r.status_code == 200
        text_sent = telegram_client.send_message.call_args.args[1]
        assert "no está vinculado" in text_sent

    def test_bound_chat_forwards_to_ingest(self, monkeypatch):
        from services import telegram_client, command_parser
        monkeypatch.setattr(telegram_client, "send_message", AsyncMock())
        send_to_ib = AsyncMock()
        monkeypatch.setattr(command_parser, "_send_to_insightbloom", send_to_ib)

        app = build_app()
        app.state.db.bindings[(-100123, 0, "chat")] = "uuid-123"
        client = TestClient(app)
        r = client.post("/telegram/webhook", json=base_update(2, "/temas ia ¿Qué es IA?"))
        assert r.status_code == 200
        # Esperar a que la tarea async fire-and-forget corra.
        import asyncio
        asyncio.get_event_loop().run_until_complete(asyncio.sleep(0.05))
        send_to_ib.assert_called_once_with("ia", "¿Qué es IA?", "topic", "rafex", "uuid-123")


class TestIdempotency:
    def test_duplicate_update_id_ignored(self, monkeypatch):
        from services import telegram_client
        monkeypatch.setattr(telegram_client, "send_message", AsyncMock())

        app = build_app()
        client = TestClient(app)
        client.post("/telegram/webhook", json=base_update(99, "/dudas ia ¿Qué es IA?"))
        first_call_count = telegram_client.send_message.call_count
        client.post("/telegram/webhook", json=base_update(99, "/dudas ia ¿Qué es IA?"))
        assert telegram_client.send_message.call_count == first_call_count
