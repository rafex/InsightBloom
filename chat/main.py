"""
InsightBloom Chat — backend principal.

Flujo de usuario:
  1. POST /api/register   { phone, nickname, password }
  2. POST /api/login      { phone, password }         → token
  3. POST /api/sso        { ib_token }                → token (cuenta del sitio principal)
  4. POST /api/join       { token, conference_id }    → valida e inicia sesión de conferencia
  5. WS   /ws/{token}     chat en tiempo real

Webhook entrante (InsightBloom → Chat):
  POST /api/webhook/insightbloom  { word, kind, author }

Comandos en chat:
  /dudas <palabra> <descripción hasta 300 chars>
  #temas <palabra> <descripción hasta 300 chars>
"""

import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

import nats

from bot import Roberto
from config import INGEST_URL, NATS_URL, NATS_AUTH_TOKEN
from db import Database
from services.connection_manager import ConnectionManager
from routers import auth, conference, webhook, websocket

logging.basicConfig(
    level=logging.INFO, format="%(levelname)s  %(name)s  %(message)s"
)
log = logging.getLogger("chat")

db = Database()
roberto = Roberto()
manager = ConnectionManager()


@asynccontextmanager
async def lifespan(_: FastAPI):
    log.info("Chat service arrancando. INGEST_URL=%s", INGEST_URL)
    nc = None
    if NATS_URL:
        try:
            nc = await nats.connect(servers=[NATS_URL], token=NATS_AUTH_TOKEN or None)
            log.info("Conectado a NATS en %s", NATS_URL)
        except Exception:
            log.warning("No se pudo conectar a NATS, sigue en modo local", exc_info=True)
    manager.attach_nats(nc)
    yield
    if nc is not None:
        await nc.close()


app = FastAPI(title="InsightBloom Chat", lifespan=lifespan)
app.state.manager = manager
app.state.roberto = roberto

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(conference.router)
app.include_router(webhook.router)
app.include_router(websocket.router)


@app.get("/health")
async def health():
    return {"ok": True}


_STATIC_DIR = Path(__file__).parent / "static"
app.mount("/", StaticFiles(directory=_STATIC_DIR, html=True), name="static")
