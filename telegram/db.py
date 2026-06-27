from __future__ import annotations

import sqlite3
import os

DB_PATH = os.getenv("DB_PATH", "/data/telegram.db")


def _conn() -> sqlite3.Connection:
    c = sqlite3.connect(DB_PATH)
    c.row_factory = sqlite3.Row
    c.execute("PRAGMA journal_mode=WAL")
    c.execute("PRAGMA busy_timeout=5000")
    return c


class Database:
    def __init__(self):
        self._init_schema()

    def _init_schema(self):
        with _conn() as c:
            c.execute("""
                CREATE TABLE IF NOT EXISTS bindings (
                    id                INTEGER PRIMARY KEY AUTOINCREMENT,
                    chat_id           INTEGER NOT NULL,
                    message_thread_id INTEGER NOT NULL DEFAULT 0,
                    chat_title        TEXT,
                    conference_uuid   TEXT    NOT NULL,
                    purpose           TEXT    NOT NULL CHECK (purpose IN ('chat', 'notifications')),
                    bound_by_user_id  INTEGER,
                    bound_by_username TEXT,
                    created_at        TEXT    NOT NULL DEFAULT (datetime('now')),
                    updated_at        TEXT    NOT NULL DEFAULT (datetime('now')),
                    UNIQUE (chat_id, message_thread_id, purpose)
                )
            """)
            c.execute("""
                CREATE INDEX IF NOT EXISTS idx_bindings_conference_purpose
                    ON bindings (conference_uuid, purpose)
            """)
            c.execute("""
                CREATE TABLE IF NOT EXISTS processed_updates (
                    update_id    INTEGER PRIMARY KEY,
                    processed_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """)
            c.commit()

    # ── bindings ───────────────────────────────────────────────────────────

    def upsert_binding(
        self,
        chat_id: int,
        message_thread_id: int | None,
        conference_uuid: str,
        purpose: str,
        chat_title: str | None = None,
        user_id: int | None = None,
        username: str | None = None,
    ) -> None:
        thread = message_thread_id or 0
        with _conn() as c:
            c.execute(
                """
                INSERT INTO bindings
                    (chat_id, message_thread_id, chat_title, conference_uuid, purpose,
                     bound_by_user_id, bound_by_username, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
                ON CONFLICT(chat_id, message_thread_id, purpose) DO UPDATE SET
                    conference_uuid = excluded.conference_uuid,
                    chat_title = excluded.chat_title,
                    bound_by_user_id = excluded.bound_by_user_id,
                    bound_by_username = excluded.bound_by_username,
                    updated_at = datetime('now')
                """,
                (chat_id, thread, chat_title, conference_uuid, purpose, user_id, username),
            )
            c.commit()

    def find_chat_purpose_binding(self, chat_id: int, message_thread_id: int | None) -> str | None:
        """Resuelve la conferencia vinculada a un chat para purpose='chat'.
        Intenta primero con el thread exacto, luego cae al binding general del chat (thread=0)."""
        thread = message_thread_id or 0
        with _conn() as c:
            row = c.execute(
                "SELECT conference_uuid FROM bindings WHERE chat_id=? AND message_thread_id=? AND purpose='chat'",
                (chat_id, thread),
            ).fetchone()
            if row:
                return row["conference_uuid"]
            if thread != 0:
                row = c.execute(
                    "SELECT conference_uuid FROM bindings WHERE chat_id=? AND message_thread_id=0 AND purpose='chat'",
                    (chat_id,),
                ).fetchone()
                if row:
                    return row["conference_uuid"]
        return None

    def find_notification_chats(self, conference_uuid: str) -> list[dict]:
        with _conn() as c:
            rows = c.execute(
                "SELECT chat_id, message_thread_id FROM bindings WHERE conference_uuid=? AND purpose='notifications'",
                (conference_uuid,),
            ).fetchall()
            return [dict(r) for r in rows]

    # ── idempotencia de updates ────────────────────────────────────────────

    def mark_update_processed(self, update_id: int) -> bool:
        """Retorna False si el update_id ya había sido procesado antes."""
        with _conn() as c:
            cur = c.execute(
                "INSERT OR IGNORE INTO processed_updates (update_id) VALUES (?)",
                (update_id,),
            )
            c.commit()
            return cur.rowcount > 0
