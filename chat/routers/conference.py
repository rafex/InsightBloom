from fastapi import APIRouter, HTTPException

from db import Database
from models.schemas import JoinRequest
from services.command_parser import resolve_conference

router = APIRouter()
db = Database()


@router.post("/api/join")
async def join_conference(body: JoinRequest):
    """
    Asocia una sesión autenticada a una conferencia.
    Acepta UUID completo, friendly-id o short code de 7 hex chars.
    """
    token = body.token.strip()
    raw = body.conference_id.strip()

    user = db.session_user(token)
    if not user:
        raise HTTPException(401, "Sesión inválida o expirada")

    uuid = await resolve_conference(raw)
    if not uuid:
        raise HTTPException(404, f"Conferencia '{raw}' no encontrada")

    db.set_conference(token, uuid)
    return {"ok": True, "conference_id": uuid, "nickname": user["nickname"]}
