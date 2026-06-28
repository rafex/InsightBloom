import asyncio
import logging

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from db import Database
from services.command_parser import handle_command

log = logging.getLogger("chat")

router = APIRouter()
db = Database()


@router.websocket("/ws/{token}")
async def ws_endpoint(websocket: WebSocket, token: str):
    manager = websocket.app.state.manager
    roberto = websocket.app.state.roberto

    user = db.session_user(token)
    if not user:
        await websocket.close(code=4001, reason="Sesión inválida")
        return

    if not user.get("conference_id"):
        await websocket.close(
            code=4002, reason="Debes seleccionar una conferencia primero"
        )
        return

    await manager.connect(websocket, user)
    nickname = user["nickname"]
    conference_id = user["conference_id"]
    phone = user.get("phone") or ""
    user_id = phone[3:] if phone.startswith("ib:") else None
    log.info(
        "Conectado: %s → conferencia %s (%d online)",
        nickname,
        conference_id,
        manager.count(conference_id),
    )

    await manager.broadcast({
        "type": "system",
        "text": f"👋 {nickname} entró al chat",
    }, conference_id)
    await manager.broadcast({
        "type": "users",
        "users": manager.online_nicknames(conference_id),
        "count": manager.count(conference_id),
        "conference_id": conference_id,
    }, conference_id)

    try:
        while True:
            data = await websocket.receive_json()
            text = (data.get("text") or "").strip()
            if not text:
                continue

            if text.startswith("/dudas ") or text.startswith("#temas "):
                await handle_command(
                    text, nickname, conference_id, manager, roberto, user_id
                )
                await manager.broadcast({
                    "type": "message",
                    "nickname": nickname,
                    "text": text,
                }, conference_id)
                continue

            await manager.broadcast({
                "type": "message",
                "nickname": nickname,
                "text": text,
            }, conference_id)
            asyncio.create_task(
                roberto.maybe_respond(text, nickname, manager, conference_id)
            )

    except WebSocketDisconnect:
        manager.disconnect(websocket)
        db.delete_session(token)
        log.info("Desconectado: %s (%d online)", nickname, manager.count(conference_id))
        await manager.broadcast({
            "type": "system",
            "text": f"👋 {nickname} salió del chat",
        }, conference_id)
        await manager.broadcast({
            "type": "users",
            "users": manager.online_nicknames(conference_id),
            "count": manager.count(conference_id),
        }, conference_id)
