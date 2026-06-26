import asyncio

from fastapi import APIRouter, Request

from models.schemas import WebhookPayload

router = APIRouter()


@router.post("/api/webhook/insightbloom")
async def insightbloom_webhook(body: WebhookPayload, request: Request):
    """InsightBloom notifica eventos al chat (duda o tema registrado)."""
    manager = request.app.state.manager
    roberto = request.app.state.roberto
    word = body.word
    kind = body.kind
    author = body.author
    kind_es = "DUDA" if kind in ("doubt", "DOUBT") else "TEMA"

    await manager.broadcast({
        "type": "system",
        "text": f"📊 InsightBloom registró [{kind_es}]: \"{word}\" — de {author}",
    })
    asyncio.create_task(roberto.on_insightbloom_event(word, kind, manager))
    return {"ok": True}
