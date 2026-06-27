from __future__ import annotations

import logging

from fastapi import APIRouter, Request, Response

from config import TELEGRAM_WEBHOOK_SECRET
from services import telegram_client
from services.binding_service import handle_conferencia_command
from services.command_parser import handle_doubt_or_topic

log = logging.getLogger("telegram")

router = APIRouter()


@router.post("/telegram/webhook")
async def telegram_webhook(request: Request):
    if TELEGRAM_WEBHOOK_SECRET:
        secret = request.headers.get("X-Telegram-Bot-Api-Secret-Token")
        if secret != TELEGRAM_WEBHOOK_SECRET:
            return Response(status_code=401)

    db = request.app.state.db
    body = await request.json()

    update_id = body.get("update_id")
    if update_id is not None and not db.mark_update_processed(update_id):
        return {"ok": True}  # update ya procesado, idempotente

    message = body.get("message") or body.get("edited_message")
    if not message:
        return {"ok": True}  # otros tipos de update (callback_query, etc.) se ignoran en Fase 1

    chat = message.get("chat") or {}
    chat_id = chat.get("id")
    chat_title = chat.get("title")
    message_thread_id = message.get("message_thread_id")
    text = (message.get("text") or "").strip()
    from_user = message.get("from") or {}
    from_user_id = from_user.get("id")
    from_username = from_user.get("username") or from_user.get("first_name") or "alguien"

    if chat_id is None or not text:
        return {"ok": True}

    reply_text = await handle_conferencia_command(
        text, chat_id, message_thread_id, chat_title, from_user_id, from_username, db
    )
    if reply_text is None:
        reply_text = await handle_doubt_or_topic(text, chat_id, message_thread_id, from_username, db)

    if reply_text:
        await telegram_client.send_message(chat_id, reply_text, message_thread_id)

    return {"ok": True}
