"""SQLite store. Plaintext credentials are never persisted."""

from __future__ import annotations

import sqlite3
import time
import os
from pathlib import Path
from typing import Any

from .crypto import KeyRing

CAPABILITIES = {"chat", "tutor", "survey", "seatLayout", "email"}


class CredentialStore:
    def __init__(self, database: str, keyring: KeyRing):
        self.database = database
        self.keyring = keyring
        os.umask(0o077)
        Path(database).parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        with self._connect() as db:
            try:
                os.chmod(database, 0o600)
            except OSError:
                pass
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("PRAGMA secure_delete=ON")
            db.executescript("""
                CREATE TABLE IF NOT EXISTS credentials (
                    scope_type TEXT NOT NULL CHECK(scope_type IN ('organizer','event')),
                    scope_id TEXT NOT NULL,
                    capability TEXT NOT NULL,
                    provider TEXT NOT NULL,
                    model TEXT NOT NULL,
                    base_url TEXT,
                    ciphertext TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY(scope_type, scope_id, capability)
                );
                CREATE TABLE IF NOT EXISTS user_credentials (
                    scope_id TEXT NOT NULL, capability TEXT NOT NULL, provider TEXT NOT NULL,
                    model TEXT NOT NULL, base_url TEXT, ciphertext TEXT NOT NULL, updated_at INTEGER NOT NULL,
                    PRIMARY KEY(scope_id, capability)
                );
                CREATE TABLE IF NOT EXISTS credential_usage (
                    event_id TEXT NOT NULL, credential_owner TEXT NOT NULL, capability TEXT NOT NULL,
                    minute INTEGER NOT NULL, requests INTEGER NOT NULL,
                    PRIMARY KEY(event_id, credential_owner, capability, minute)
                );
                CREATE TABLE IF NOT EXISTS effective_sources (
                    event_id TEXT NOT NULL, capability TEXT NOT NULL, source TEXT NOT NULL,
                    PRIMARY KEY(event_id, capability)
                );
                CREATE TABLE IF NOT EXISTS audit (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    actor TEXT NOT NULL,
                    action TEXT NOT NULL,
                    scope_type TEXT NOT NULL,
                    scope_id TEXT NOT NULL,
                    capability TEXT NOT NULL,
                    occurred_at INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS usage (
                    organizer_id TEXT NOT NULL,
                    event_id TEXT NOT NULL,
                    minute INTEGER NOT NULL,
                    requests INTEGER NOT NULL,
                    PRIMARY KEY(organizer_id, event_id, minute)
                );
            """)
            columns = {row[1] for row in db.execute("PRAGMA table_info(credentials)")}
            if "base_url" not in columns:
                db.execute("ALTER TABLE credentials ADD COLUMN base_url TEXT")
            # Organizer-scoped records were only a transitional fallback; retain event scope only.
            db.execute("DELETE FROM credentials WHERE scope_type='organizer'")
        # Checkpoint after closing the migration transaction/connection; SQLite rejects
        # TRUNCATE checkpoints while a write transaction still owns the database.
        with self._connect() as db:
            db.execute("PRAGMA wal_checkpoint(TRUNCATE)")

    def _connect(self) -> sqlite3.Connection:
        db = sqlite3.connect(self.database, timeout=5, isolation_level="IMMEDIATE")
        db.row_factory = sqlite3.Row
        return db

    @staticmethod
    def _aad(scope_type: str, scope_id: str, capability: str) -> bytes:
        return f"{scope_type}\0{scope_id}\0{capability}".encode("utf-8")

    @staticmethod
    def _table(scope_type: str) -> str:
        if scope_type not in {"event", "user"}:
            raise ValueError("credential_scope_invalid")
        return "user_credentials" if scope_type == "user" else "credentials"

    def set(self, *, scope_type: str, scope_id: str, capability: str, provider: str, model: str,
            api_key: str, actor: str, base_url: str | None = None) -> None:
        table = self._table(scope_type)
        if capability not in CAPABILITIES or not scope_id or len(scope_id) > 128 or len(api_key) > 4096:
            raise ValueError("credential_input_invalid")
        now = int(time.time())
        with self._connect() as db:
            old = db.execute(f"SELECT * FROM {table} WHERE scope_id=? AND capability=?" +
                             (" AND scope_type='event'" if scope_type == "event" else ""), (scope_id, capability)).fetchone()
            if not api_key:
                if old is None or old["provider"] != provider or old["base_url"] != base_url:
                    raise ValueError("new_api_key_required")
                encrypted = old["ciphertext"]
            else:
                encrypted = self.keyring.encrypt(api_key, associated_data=self._aad(scope_type, scope_id, capability))
            if scope_type == "event":
                db.execute("""INSERT INTO credentials(scope_type,scope_id,capability,provider,model,base_url,ciphertext,updated_at)
                    VALUES('event',?,?,?,?,?,?,?) ON CONFLICT(scope_type,scope_id,capability) DO UPDATE SET
                    provider=excluded.provider,model=excluded.model,base_url=excluded.base_url,
                    ciphertext=excluded.ciphertext,updated_at=excluded.updated_at""",
                           (scope_id, capability, provider, model, base_url, encrypted, now))
            else:
                db.execute("""INSERT INTO user_credentials(scope_id,capability,provider,model,base_url,ciphertext,updated_at)
                    VALUES(?,?,?,?,?,?,?) ON CONFLICT(scope_id,capability) DO UPDATE SET
                    provider=excluded.provider,model=excluded.model,base_url=excluded.base_url,
                    ciphertext=excluded.ciphertext,updated_at=excluded.updated_at""",
                           (scope_id, capability, provider, model, base_url, encrypted, now))
            db.execute("INSERT INTO audit(actor,action,scope_type,scope_id,capability,occurred_at) VALUES(?,?,?,?,?,?)",
                       (actor, "credential_set", scope_type, scope_id, capability, now))

    def clear(self, *, scope_type: str, scope_id: str, capability: str, actor: str) -> None:
        table = self._table(scope_type)
        with self._connect() as db:
            db.execute(f"DELETE FROM {table} WHERE scope_id=? AND capability=?" +
                       (" AND scope_type='event'" if scope_type == "event" else ""), (scope_id, capability))
            db.execute("INSERT INTO audit(actor,action,scope_type,scope_id,capability,occurred_at) VALUES(?,?,?,?,?,?)",
                       (actor, "credential_cleared", scope_type, scope_id, capability, int(time.time())))

    def status(self, *, scope_type: str, scope_id: str, capability: str) -> dict[str, Any]:
        table = self._table(scope_type)
        with self._connect() as db:
            row = db.execute(f"SELECT provider,model,base_url,updated_at FROM {table} WHERE scope_id=? AND capability=?" +
                             (" AND scope_type='event'" if scope_type == "event" else ""), (scope_id, capability)).fetchone()
        if row is None:
            return {"configured": False}
        return {"configured": True, "provider": row["provider"], "model": row["model"],
                "baseUrl": row["base_url"].removesuffix("/chat/completions") if row["base_url"] else None,
                "updatedAt": row["updated_at"]}

    def resolve(self, *, event_id: str, capability: str, scope_type: str = "event", scope_id: str | None = None) -> tuple[str, str, str, str | None] | None:
        table = self._table(scope_type)
        identity = scope_id or event_id
        with self._connect() as db:
            row = db.execute(f"SELECT provider,model,base_url,ciphertext FROM {table} WHERE scope_id=? AND capability=?" +
                             (" AND scope_type='event'" if scope_type == "event" else ""), (identity, capability)).fetchone()
        if row is None:
            return None
        key = self.keyring.decrypt(row["ciphertext"], associated_data=self._aad(scope_type, identity, capability))
        return row["provider"], row["model"], key, row["base_url"]

    def record_source(self, event_id: str, capability: str, source: str, actor: str = "gateway") -> None:
        with self._connect() as db:
            old = db.execute("SELECT source FROM effective_sources WHERE event_id=? AND capability=?", (event_id, capability)).fetchone()
            if old is None or old[0] != source:
                db.execute("INSERT INTO effective_sources VALUES(?,?,?) ON CONFLICT(event_id,capability) DO UPDATE SET source=excluded.source", (event_id, capability, source))
                db.execute("INSERT INTO audit(actor,action,scope_type,scope_id,capability,occurred_at) VALUES(?,?,?,?,?,?)",
                           (actor, "source_changed:" + source, "event", event_id, capability, int(time.time())))

    def record_usage(self, event_id: str, credential_owner: str, capability: str) -> None:
        with self._connect() as db:
            db.execute("INSERT INTO credential_usage VALUES(?,?,?,?,1) ON CONFLICT(event_id,credential_owner,capability,minute) DO UPDATE SET requests=requests+1",
                       (event_id, credential_owner, capability, int(time.time() // 60)))
            db.execute("DELETE FROM credential_usage WHERE minute < ?", (int(time.time() // 60) - 1440,))

    def consume_quota(self, *, organizer_id: str, event_id: str, limit: int = 30) -> bool:
        minute = int(time.time() // 60)
        with self._connect() as db:
            row = db.execute("SELECT requests FROM usage WHERE organizer_id=? AND event_id=? AND minute=?",
                             (organizer_id, event_id, minute)).fetchone()
            if row and row["requests"] >= limit:
                return False
            db.execute("""INSERT INTO usage(organizer_id,event_id,minute,requests) VALUES(?,?,?,1)
                ON CONFLICT(organizer_id,event_id,minute) DO UPDATE SET requests=requests+1""",
                       (organizer_id, event_id, minute))
            db.execute("DELETE FROM usage WHERE minute < ?", (minute - 2,))
        return True

    def audit_request(self, *, actor: str, event_id: str, capability: str) -> None:
        with self._connect() as db:
            db.execute("INSERT INTO audit(actor,action,scope_type,scope_id,capability,occurred_at) VALUES(?,?,?,?,?,?)",
                       (actor, "completion_requested", "event", event_id, capability, int(time.time())))

    def reencrypt_all(self) -> int:
        """Recipher both event and personal credentials with their original AAD."""
        changed = 0
        with self._connect() as db:
            for scope_type in ("event", "user"):
                table = self._table(scope_type)
                rows = db.execute(f"SELECT scope_id,capability,ciphertext FROM {table}" +
                                  (" WHERE scope_type='event'" if scope_type == "event" else "")).fetchall()
                for row in rows:
                    if row["ciphertext"].split(":", 1)[0] == self.keyring.active_version:
                        continue
                    encrypted = self.keyring.reencrypt(row["ciphertext"], associated_data=self._aad(scope_type, row["scope_id"], row["capability"]))
                    db.execute(f"UPDATE {table} SET ciphertext=?,updated_at=? WHERE scope_id=? AND capability=?" +
                               (" AND scope_type='event'" if scope_type == "event" else ""),
                               (encrypted, int(time.time()), row["scope_id"], row["capability"]))
                    changed += 1
        return changed
