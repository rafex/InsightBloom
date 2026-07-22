import re
import secrets

import httpx
from fastapi import APIRouter, HTTPException

from config import USERS_URL
from crypto import hash_password, verify_password
from db import Database
from models.schemas import LoginRequest, RegisterRequest, SsoRequest

router = APIRouter()
db = Database()


def _sanitize_nickname(raw: str) -> str:
    base = re.sub(r"[^a-z0-9_.-]", "", (raw or "").strip().lower())[:20]
    return base if len(base) >= 2 else "user"


@router.post("/api/register")
async def register(body: RegisterRequest):
    phone = body.phone.strip()
    nickname = body.nickname.strip().lower()
    password = body.password.strip()

    if not phone.lstrip("+").isdigit():
        raise HTTPException(400, "El teléfono solo debe contener dígitos (y + al inicio)")
    if not re.match(r"^[a-z0-9_.-]{2,20}$", nickname):
        raise HTTPException(
            400, "Nickname inválido: 2-20 chars, letras minúsculas, números, _ . -"
        )

    try:
        db.create_user(phone, nickname, hash_password(password))
    except Exception:
        raise HTTPException(409, "El teléfono o nickname ya está registrado")

    return {"ok": True, "nickname": nickname}


@router.post("/api/login")
async def login(body: LoginRequest):
    phone = body.phone.strip()
    password = body.password.strip()

    user = db.find_by_phone(phone)
    if not user:
        raise HTTPException(401, "Usuario no encontrado")

    valid, needs_rehash = verify_password(password, user["password_enc"])
    if not valid:
        raise HTTPException(401, "Contraseña incorrecta")
    if needs_rehash:
        db.update_password(phone, hash_password(password))

    token = db.create_session(phone)
    return {"ok": True, "token": token, "nickname": user["nickname"]}


@router.post("/api/sso")
async def sso(body: SsoRequest):
    """
    Inicio de sesión unificado: recibe el token de insightbloom-users (sitio
    principal), lo valida contra ese servicio, y si corresponde a una cuenta
    registrada (no invitado) auto-provisiona/reutiliza un usuario de chat
    ligado a ese mismo uuid, evitando el registro/login propio del chat.
    """
    ib_token = body.ib_token.strip()

    async with httpx.AsyncClient(timeout=5.0) as client:
        try:
            resp = await client.get(
                f"{USERS_URL}/api/v1/auth/validate",
                headers={"Authorization": f"Bearer {ib_token}"},
            )
        except Exception:
            raise HTTPException(503, "No se pudo validar la sesión")
        if resp.status_code != 200:
            raise HTTPException(401, "Sesión inválida o expirada")
        validation = (resp.json() or {}).get("data", {})
        if not validation.get("valid") or validation.get("kind") == "guest":
            raise HTTPException(401, "Se requiere una cuenta registrada (no invitado)")
        subject_uuid = validation.get("subjectUuid")
        if not subject_uuid:
            raise HTTPException(401, "Sesión inválida")

        display_name = subject_uuid[:8]
        try:
            prof_resp = await client.get(f"{USERS_URL}/api/v1/users/{subject_uuid}")
            if prof_resp.status_code == 200:
                profile = (prof_resp.json() or {}).get("data", {})
                display_name = profile.get("displayName") or display_name
        except Exception:
            pass

    phone_key = f"ib:{subject_uuid}"
    user = db.find_by_phone(phone_key)
    if not user:
        base_nick = _sanitize_nickname(display_name)
        nickname = base_nick
        for attempt in range(50):
            try:
                db.create_user(phone_key, nickname, hash_password(secrets.token_urlsafe(16)))
                break
            except Exception:
                nickname = f"{base_nick[:16]}{attempt + 1}"
        else:
            raise HTTPException(500, "No se pudo crear el usuario de chat")
        user = db.find_by_phone(phone_key)

    token = db.create_session(phone_key)
    return {"ok": True, "token": token, "nickname": user["nickname"]}
