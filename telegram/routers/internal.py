from __future__ import annotations

import asyncio
import logging

from fastapi import APIRouter, Request, Response

from config import INTERNAL_API_KEY
from models.schemas import NotifyRequest
from services import telegram_client

log = logging.getLogger("telegram")

router = APIRouter()


def _valid_internal_auth(request: Request) -> bool:
    if not INTERNAL_API_KEY:
        return True  # no configurada: modo desarrollo, no se exige el header
    return request.headers.get("X-Internal-Auth") == INTERNAL_API_KEY


@router.post("/internal/notify")
async def internal_notify(request: Request, body: NotifyRequest):
    if not _valid_internal_auth(request):
        return Response(status_code=403)

    db = request.app.state.db
    targets = db.find_notification_chats(body.conferenceUuid)
    for t in targets:
        await telegram_client.send_message(t["chat_id"], body.message, t["message_thread_id"] or None)
        await asyncio.sleep(0.05)  # throttle simple para no exceder rate limits de Telegram

    return {"ok": True, "sent": len(targets)}
