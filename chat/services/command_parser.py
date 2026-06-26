from __future__ import annotations

import asyncio
import logging
import re

import httpx

from config import INGEST_URL, USERS_URL

log = logging.getLogger("chat")

_UUID_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    re.IGNORECASE,
)
_SHORT_RE = re.compile(r"^[0-9a-f]{7}$", re.IGNORECASE)
# "/dudas" usa prefijo "/"; "#temas" usa "#".
_CMD_RE = re.compile(r"^(/dudas|#temas)\s+(\S+)\s+(.{1,300})$", re.DOTALL)


async def handle_command(
    text: str,
    nickname: str,
    conference_id: str,
    manager: "ConnectionManager",
    roberto,
    user_id: str | None = None,
) -> None:
    m = _CMD_RE.match(text)
    if not m:
        prefix = "/dudas" if text.startswith("/dudas") else "#temas"
        await manager.broadcast({
            "type": "system",
            "text": (
                f"⚠️ Formato incorrecto.\n"
                f"Uso: {prefix} <una_palabra> <descripción hasta 300 chars>\n"
                f"Ej: {prefix} inteligencia ¿Cómo afecta la IA al mercado laboral?"
            ),
        })
        return

    prefix, word, description = m.group(1), m.group(2), m.group(3).strip()
    kind = "doubt" if prefix == "/dudas" else "topic"
    kind_es = "duda" if kind == "doubt" else "tema"

    asyncio.create_task(
        _send_to_insightbloom(word, description, kind, nickname, conference_id, user_id)
    )
    preview = description[:60] + ("…" if len(description) > 60 else "")
    await manager.broadcast({
        "type": "system",
        "text": f"✅ {nickname} envió {kind_es}: [{word}] {preview}",
    })


async def _send_to_insightbloom(
    word: str, detail: str, kind: str, author: str, conference_id: str,
    user_id: str | None = None,
) -> None:
    author_payload = {"displayName": author, "kind": "guest"}
    if user_id:
        author_payload["userId"] = user_id
        author_payload["kind"] = "user"
    payload = {
        "conferenceId": conference_id,
        "author": author_payload,
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
