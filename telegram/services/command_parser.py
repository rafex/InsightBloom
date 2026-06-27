from __future__ import annotations

import asyncio
import logging
import re

import httpx

from config import INGEST_URL, USERS_URL

log = logging.getLogger("telegram")

_UUID_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    re.IGNORECASE,
)
_SHORT_RE = re.compile(r"^[0-9a-f]{7}$", re.IGNORECASE)
# En Telegram ambos comandos usan "/" (privacy mode ON: solo se reciben comandos).
_CMD_RE = re.compile(r"^/(dudas|temas)\s+(\S+)\s+(.{1,300})$", re.DOTALL)
_CONFERENCIA_RE = re.compile(r"^/conferencia\s+(\S+)\s+(chat|notificaciones)$", re.IGNORECASE)


async def _send_to_insightbloom(
    word: str, detail: str, kind: str, author: str, conference_id: str,
) -> None:
    payload = {
        "conferenceId": conference_id,
        "author": {"displayName": author, "kind": "guest"},
        "message": {"type": kind, "word": word, "detail": detail},
        "receivedAt": None,
    }
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            r = await client.post(
                f"{INGEST_URL}/api/v1/webhooks/messages", json=payload
            )
            log.info("Ingest [%s] %s → HTTP %s", kind, word, r.status_code)
    except Exception as exc:
        log.error("Error enviando a InsightBloom: %s", exc)


async def resolve_conference(raw: str) -> str | None:
    """
    Devuelve el UUID de la conferencia dado:
      - UUID completo  (36 chars con guiones)   → lo devuelve tal cual
      - short code     (7 hex chars)             → busca en users /by-short/
      - friendly id    (cualquier otra cosa)     → busca en users /by-friendly/
    """
    if _UUID_RE.match(raw):
        return raw

    endpoint = (
        f"{USERS_URL}/api/v1/conferences/by-short/{raw}"
        if _SHORT_RE.match(raw)
        else f"{USERS_URL}/api/v1/conferences/by-friendly/{raw}"
    )
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            r = await client.get(endpoint)
            if r.status_code == 200:
                data = r.json()
                return (data.get("data") or data).get("uuid")
    except Exception as exc:
        log.warning("No se pudo resolver conferencia '%s': %s", raw, exc)
    return None


def parse_doubt_or_topic(text: str) -> tuple[str, str, str] | None:
    """Si el texto matchea /dudas o /temas, devuelve (kind, word, detail)."""
    m = _CMD_RE.match(text)
    if not m:
        return None
    prefix, word, description = m.group(1), m.group(2), m.group(3).strip()
    kind = "doubt" if prefix == "dudas" else "topic"
    return kind, word, description


def parse_conferencia(text: str) -> tuple[str, str] | None:
    """Si el texto matchea /conferencia <id> <chat|notificaciones>, devuelve (id, purpose)."""
    m = _CONFERENCIA_RE.match(text)
    if not m:
        return None
    raw_id, purpose_es = m.group(1), m.group(2).lower()
    purpose = "notifications" if purpose_es == "notificaciones" else "chat"
    return raw_id, purpose


async def handle_doubt_or_topic(
    text: str, chat_id: int, message_thread_id: int | None, author: str, db,
) -> str | None:
    """Procesa /dudas o /temas. Devuelve el texto de respuesta a enviar al chat, o None si no matcheó."""
    parsed = parse_doubt_or_topic(text)
    if not parsed:
        return None
    kind, word, detail = parsed

    conference_uuid = db.find_chat_purpose_binding(chat_id, message_thread_id)
    if not conference_uuid:
        return "⚠️ Este chat no está vinculado a ninguna conferencia. Usa /conferencia <id> chat primero."

    asyncio.create_task(_send_to_insightbloom(word, detail, kind, author, conference_uuid))
    kind_es = "duda" if kind == "doubt" else "tema"
    preview = detail[:60] + ("…" if len(detail) > 60 else "")
    return f"✅ {author} envió {kind_es}: [{word}] {preview}"
