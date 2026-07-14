"""
InsightBloom Telegram — bot de Telegram para conferencias (Fase 1).

Flujo entrante (Telegram → InsightBloom), vía webhook:
  POST /telegram/webhook

Comandos soportados dentro de un grupo de Telegram:
  /conferencia <friendlyId|shortCode|uuid> <chat|notificaciones>
  /dudas <palabra> <descripción hasta 300 chars>
  /temas <palabra> <descripción hasta 300 chars>

Flujo saliente (InsightBloom → Telegram), service-to-service:
  POST /internal/notify   { conferenceUuid, message }   (protegido con X-Internal-Auth)
"""

import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI

from config import INGEST_URL
from db import Database
from routers import internal, webhook

logging.basicConfig(
    level=logging.INFO, format="%(levelname)s  %(name)s  %(message)s"
)
log = logging.getLogger("telegram")

db = Database()


@asynccontextmanager
async def lifespan(_: FastAPI):
    log.info("Telegram service arrancando. INGEST_URL=%s", INGEST_URL)
    yield


app = FastAPI(title="InsightBloom Telegram", lifespan=lifespan)
app.state.db = db

app.include_router(webhook.router)
app.include_router(internal.router)


@app.get("/health")
async def health():
    return {"ok": True}


@app.get("/version")
async def version():
    return {"service": "telegram", "version": os.getenv("APP_VERSION", "dev"),
            "gitSha": os.getenv("GIT_SHA", "unknown")}
