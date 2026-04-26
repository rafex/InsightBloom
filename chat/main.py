"""
InsightBloom Chat — backend principal.

Flujo de usuario:
  1. POST /api/register   { phone, nickname, password }
  2. POST /api/login      { phone, password }         → token
  3. POST /api/join       { token, conference_id }    → valida e inicia sesión de conferencia
  4. WS   /ws/{token}     chat en tiempo real

Webhook entrante (InsightBloom → Chat):
  POST /api/webhook/insightbloom  { word, kind, author }

Comandos en chat:
  /dudas <palabra> <descripción hasta 300 chars>
  /temas <palabra> <descripción hasta 300 chars>
"""

import asyncio
import logging
import os
import re
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from bot import Roberto
from crypto import decrypt, encrypt
from db import Database

logging.basicConfig(level=logging.INFO, format="%(levelname)s  %(name)s  %(message)s")
log = logging.getLogger("chat")

# URL interna del microservicio insightbloom-ingest dentro del cluster K3s.
# Dentro del cluster usar: http://insightbloom-ingest:8085
# En local usar: http://localhost:8085
INGEST_URL = os.getenv("INGEST_URL", "http://localhost:8085")

# Regex de validación de comandos
_CMD_RE = re.compile(r"^/(dudas|temas)\s+(\S+)\s+(.{1,300})$", re.DOTALL)

db      = Database()
roberto = Roberto()


# ── Connection manager ──────────────────────────────────────────────────────────

class ConnectionManager:
    def __init__(self):
        # (websocket, user_dict)  donde user_dict incluye conference_id
        self._conns: list[tuple[WebSocket, dict]] = []

    async def connect(self, ws: WebSocket, user: dict) -> None:
        await ws.accept()
        self._conns.append((ws, user))

    def disconnect(self, ws: WebSocket) -> None:
        self._conns = [(w, u) for w, u in self._conns if w is not ws]

    async def broadcast(self, payload: dict) -> None:
        dead: list[WebSocket] = []
        for ws, _ in self._conns:
            try:
                await ws.send_json(payload)
            except Exception:
                dead.append(ws)
        for ws in dead:
            self.disconnect(ws)

    def online_nicknames(self) -> list[str]:
        return [u["nickname"] for _, u in self._conns]

    def count(self) -> int:
        return len(self._conns)


manager = ConnectionManager()


# ── App ────────────────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(_: FastAPI):
    log.info("Chat service arrancando. INGEST_URL=%s", INGEST_URL)
    yield

app = FastAPI(title="InsightBloom Chat", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"]
)


# ── REST ───────────────────────────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {"ok": True}


@app.post("/api/register")
async def register(body: dict):
    phone    = (body.get("phone") or "").strip()
    nickname = (body.get("nickname") or "").strip().lower()
    password = (body.get("password") or "").strip()

    if not phone or not nickname or not password:
        raise HTTPException(400, "phone, nickname y password son requeridos")
    if not phone.lstrip("+").isdigit():
        raise HTTPException(400, "El teléfono solo debe contener dígitos (y + al inicio)")
    if not re.match(r"^[a-z0-9_.-]{2,20}$", nickname):
        raise HTTPException(400, "Nickname inválido: 2-20 chars, letras minúsculas, números, _ . -")

    try:
        db.create_user(phone, nickname, encrypt(password))
    except Exception:
        raise HTTPException(409, "El teléfono o nickname ya está registrado")

    return {"ok": True, "nickname": nickname}


@app.post("/api/login")
async def login(body: dict):
    phone    = (body.get("phone") or "").strip()
    password = (body.get("password") or "").strip()

    if not phone or not password:
        raise HTTPException(400, "phone y password son requeridos")

    user = db.find_by_phone(phone)
    if not user:
        raise HTTPException(401, "Usuario no encontrado")

    try:
        stored = decrypt(user["password_enc"])
    except Exception:
        raise HTTPException(500, "Error al verificar credenciales")

    if stored != password:
        raise HTTPException(401, "Contraseña incorrecta")

    token = db.create_session(phone)
    return {"ok": True, "token": token, "nickname": user["nickname"]}


@app.post("/api/join")
async def join_conference(body: dict):
    """
    Asocia una sesión autenticada a un CONFERENCE_ID.
    Debe llamarse después del login y antes de conectar el WebSocket.
    """
    token         = (body.get("token") or "").strip()
    conference_id = (body.get("conference_id") or "").strip()

    if not token:
        raise HTTPException(400, "token requerido")
    if not conference_id:
        raise HTTPException(400, "conference_id requerido")

    user = db.session_user(token)
    if not user:
        raise HTTPException(401, "Sesión inválida o expirada")

    db.set_conference(token, conference_id)
    return {"ok": True, "conference_id": conference_id, "nickname": user["nickname"]}


@app.post("/api/webhook/insightbloom")
async def insightbloom_webhook(body: dict):
    """InsightBloom notifica eventos al chat (duda o tema registrado)."""
    word    = body.get("word", "")
    kind    = body.get("kind", "doubt")
    author  = body.get("author", "alguien")
    kind_es = "DUDA" if kind in ("doubt", "DOUBT") else "TEMA"

    await manager.broadcast({
        "type": "system",
        "text": f"📊 InsightBloom registró [{kind_es}]: \"{word}\" — de {author}",
    })
    asyncio.create_task(roberto.on_insightbloom_event(word, kind, manager))
    return {"ok": True}


# ── WebSocket ──────────────────────────────────────────────────────────────────

@app.websocket("/ws/{token}")
async def ws_endpoint(websocket: WebSocket, token: str):
    user = db.session_user(token)
    if not user:
        await websocket.close(code=4001, reason="Sesión inválida")
        return

    if not user.get("conference_id"):
        await websocket.close(code=4002, reason="Debes seleccionar una conferencia primero")
        return

    await manager.connect(websocket, user)
    nickname      = user["nickname"]
    conference_id = user["conference_id"]
    log.info("Conectado: %s → conferencia %s (%d online)", nickname, conference_id, manager.count())

    await manager.broadcast({"type": "system", "text": f"👋 {nickname} entró al chat"})
    await websocket.send_json({
        "type":          "users",
        "users":         manager.online_nicknames(),
        "count":         manager.count(),
        "conference_id": conference_id,
    })

    try:
        while True:
            data = await websocket.receive_json()
            text = (data.get("text") or "").strip()
            if not text:
                continue

            if text.startswith("/dudas ") or text.startswith("/temas "):
                await _handle_command(text, nickname, conference_id)
                await manager.broadcast({"type": "message", "nickname": nickname, "text": text})
                continue

            await manager.broadcast({"type": "message", "nickname": nickname, "text": text})
            asyncio.create_task(roberto.maybe_respond(text, nickname, manager))

    except WebSocketDisconnect:
        manager.disconnect(websocket)
        db.delete_session(token)
        log.info("Desconectado: %s (%d online)", nickname, manager.count())
        await manager.broadcast({"type": "system", "text": f"👋 {nickname} salió del chat"})
        await manager.broadcast({
            "type": "users", "users": manager.online_nicknames(), "count": manager.count(),
        })


# ── Comandos /dudas y /temas ───────────────────────────────────────────────────

async def _handle_command(text: str, nickname: str, conference_id: str) -> None:
    m = _CMD_RE.match(text)
    if not m:
        cmd = "dudas" if text.startswith("/dudas") else "temas"
        await manager.broadcast({
            "type": "system",
            "text": (
                f"⚠️ Formato incorrecto.\n"
                f"Uso: /{cmd} <una_palabra> <descripción hasta 300 chars>\n"
                f"Ej: /{cmd} inteligencia ¿Cómo afecta la IA al mercado laboral?"
            ),
        })
        return

    cmd, word, description = m.group(1), m.group(2), m.group(3).strip()
    kind    = "doubt" if cmd == "dudas" else "topic"
    kind_es = "duda"  if kind == "doubt" else "tema"

    asyncio.create_task(_send_to_insightbloom(word, description, kind, nickname, conference_id))
    preview = description[:60] + ("…" if len(description) > 60 else "")
    await manager.broadcast({
        "type": "system",
        "text": f"✅ {nickname} envió {kind_es}: [{word}] {preview}",
    })


# ── InsightBloom ingest ────────────────────────────────────────────────────────

async def _send_to_insightbloom(
    word: str, detail: str, kind: str, author: str, conference_id: str
) -> None:
    payload = {
        "conferenceId": conference_id,
        "author":  {"displayName": author, "kind": "guest"},
        "message": {"type": kind, "word": word, "detail": detail},
        "receivedAt": None,
    }
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            r = await client.post(f"{INGEST_URL}/api/v1/webhooks/messages", json=payload)
            log.info("Ingest [%s] %s → HTTP %s", kind, word, r.status_code)
    except Exception as exc:
        log.error("Error enviando a InsightBloom: %s", exc)


# ── Static files ───────────────────────────────────────────────────────────────

app.mount("/", StaticFiles(directory="static", html=True), name="static")
