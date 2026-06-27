from __future__ import annotations

import logging

import httpx

from config import TELEGRAM_API_BASE

log = logging.getLogger("telegram")


async def send_message(chat_id: int, text: str, message_thread_id: int | None = None) -> None:
    """Envía un mensaje a un chat de Telegram. Best-effort: nunca lanza."""
    payload = {"chat_id": chat_id, "text": text}
    if message_thread_id:
        payload["message_thread_id"] = message_thread_id
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            r = await client.post(f"{TELEGRAM_API_BASE}/sendMessage", json=payload)
            if r.status_code != 200:
                log.warning("sendMessage a chat %s devolvió HTTP %s: %s", chat_id, r.status_code, r.text)
    except Exception as exc:
        log.error("Error enviando mensaje a Telegram (chat %s): %s", chat_id, exc)


async def is_chat_admin(chat_id: int, user_id: int) -> bool:
    """Verifica si user_id es administrador/creador del chat_id."""
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            r = await client.get(
                f"{TELEGRAM_API_BASE}/getChatMember",
                params={"chat_id": chat_id, "user_id": user_id},
            )
            if r.status_code != 200:
                return False
            data = r.json()
            status = (data.get("result") or {}).get("status")
            return status in ("administrator", "creator")
    except Exception as exc:
        log.warning("No se pudo verificar admin de chat %s/user %s: %s", chat_id, user_id, exc)
        return False
