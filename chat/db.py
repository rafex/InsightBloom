import sqlite3
import os

DB_PATH = os.getenv("DB_PATH", "/data/chat.db")


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
                CREATE TABLE IF NOT EXISTS users (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    phone        TEXT    NOT NULL UNIQUE,
                    nickname     TEXT    NOT NULL UNIQUE,
                    password_enc TEXT    NOT NULL,
                    created_at   TEXT    NOT NULL DEFAULT (datetime('now'))
                )
            """)
            c.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    token         TEXT PRIMARY KEY,
                    phone         TEXT NOT NULL,
                    conference_id TEXT,
                    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """)
            # Migración segura para DBs existentes sin la columna
            try:
                c.execute("ALTER TABLE sessions ADD COLUMN conference_id TEXT")
            except Exception:
                pass
            c.commit()

    # ── users ──────────────────────────────────────────────────────────────

    def create_user(self, phone: str, nickname: str, password_enc: str) -> None:
        """Guarda el usuario. nickname siempre en minúsculas."""
        with _conn() as c:
            c.execute(
                "INSERT INTO users (phone, nickname, password_enc) VALUES (?, ?, ?)",
                (phone, nickname.lower(), password_enc),
            )
            c.commit()

    def find_by_phone(self, phone: str) -> dict | None:
        with _conn() as c:
            row = c.execute(
                "SELECT * FROM users WHERE phone = ?", (phone,)
            ).fetchone()
            return dict(row) if row else None

    # ── sessions ───────────────────────────────────────────────────────────

    def create_session(self, phone: str) -> str:
        import uuid
        token = str(uuid.uuid4())
        with _conn() as c:
            c.execute(
                "INSERT OR REPLACE INTO sessions (token, phone) VALUES (?, ?)",
                (token, phone),
            )
            c.commit()
        return token

    def set_conference(self, token: str, conference_id: str) -> None:
        """Asocia un conference_id a la sesión activa."""
        with _conn() as c:
            c.execute(
                "UPDATE sessions SET conference_id = ? WHERE token = ?",
                (conference_id.strip(), token),
            )
            c.commit()

    def session_user(self, token: str) -> dict | None:
        """Devuelve usuario + conference_id de la sesión."""
        with _conn() as c:
            row = c.execute(
                """SELECT u.*, s.conference_id
                   FROM sessions s
                   JOIN users u ON u.phone = s.phone
                   WHERE s.token = ?""",
                (token,),
            ).fetchone()
            return dict(row) if row else None

    def delete_session(self, token: str) -> None:
        with _conn() as c:
            c.execute("DELETE FROM sessions WHERE token = ?", (token,))
            c.commit()
