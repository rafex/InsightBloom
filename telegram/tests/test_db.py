"""Tests unitarios para Database (bindings + idempotencia de updates).

IMPORTANTE: db.py lee DB_PATH a nivel de módulo, así que el env var debe
fijarse ANTES de importar `db` por primera vez en el proceso de pytest.
"""

import os
import tempfile

import pytest

_tmp_dir = tempfile.mkdtemp()
os.environ["DB_PATH"] = os.path.join(_tmp_dir, "test_telegram.db")

from db import Database  # noqa: E402  (import después de fijar DB_PATH a propósito)


@pytest.fixture
def db():
    return Database()


class TestBindings:
    def test_upsert_and_find_chat_binding(self, db):
        db.upsert_binding(chat_id=-100123, message_thread_id=None, conference_uuid="uuid-1", purpose="chat")
        assert db.find_chat_purpose_binding(-100123, None) == "uuid-1"

    def test_no_binding_returns_none(self, db):
        assert db.find_chat_purpose_binding(-999999, None) is None

    def test_thread_specific_binding_falls_back_to_general(self, db):
        # Vínculo general (thread=0) del chat
        db.upsert_binding(chat_id=-100123, message_thread_id=None, conference_uuid="uuid-general", purpose="chat")
        # No hay binding específico para thread=5 -> debe caer al general
        assert db.find_chat_purpose_binding(-100123, 5) == "uuid-general"

    def test_thread_specific_binding_takes_precedence(self, db):
        db.upsert_binding(chat_id=-100123, message_thread_id=None, conference_uuid="uuid-general", purpose="chat")
        db.upsert_binding(chat_id=-100123, message_thread_id=5, conference_uuid="uuid-specific", purpose="chat")
        assert db.find_chat_purpose_binding(-100123, 5) == "uuid-specific"
        assert db.find_chat_purpose_binding(-100123, None) == "uuid-general"

    def test_upsert_replaces_existing_binding(self, db):
        db.upsert_binding(chat_id=-100123, message_thread_id=None, conference_uuid="uuid-old", purpose="chat")
        db.upsert_binding(chat_id=-100123, message_thread_id=None, conference_uuid="uuid-new", purpose="chat")
        assert db.find_chat_purpose_binding(-100123, None) == "uuid-new"

    def test_chat_and_notifications_purposes_are_independent(self, db):
        db.upsert_binding(chat_id=-100123, message_thread_id=None, conference_uuid="uuid-1", purpose="chat")
        db.upsert_binding(chat_id=-100123, message_thread_id=None, conference_uuid="uuid-2", purpose="notifications")
        assert db.find_chat_purpose_binding(-100123, None) == "uuid-1"
        assert db.find_notification_chats("uuid-2") == [{"chat_id": -100123, "message_thread_id": 0}]

    def test_find_notification_chats_multiple(self, db):
        db.upsert_binding(chat_id=-1, message_thread_id=None, conference_uuid="uuid-x", purpose="notifications")
        db.upsert_binding(chat_id=-2, message_thread_id=None, conference_uuid="uuid-x", purpose="notifications")
        result = {row["chat_id"] for row in db.find_notification_chats("uuid-x")}
        assert result == {-1, -2}


class TestProcessedUpdates:
    def test_first_time_returns_true(self, db):
        assert db.mark_update_processed(12345) is True

    def test_duplicate_returns_false(self, db):
        assert db.mark_update_processed(54321) is True
        assert db.mark_update_processed(54321) is False
