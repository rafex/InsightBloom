import asyncio
import hashlib
import hmac

from fastapi import APIRouter, HTTPException, Request

from models.schemas import WebhookPayload

router = APIRouter()


@router.post("/api/webhook/insightbloom")
async def insightbloom_webhook(request: Request):
    """InsightBloom notifica eventos al chat (duda o tema registrado)."""
    from config import INSIGHTBLOOM_WEBHOOK_SECRET

    raw = await request.body()
    if len(raw) > 16 * 1024:
        raise HTTPException(413, "Payload demasiado grande")
    provided = request.headers.get("X-InsightBloom-Signature", "")
    expected = hmac.new(
        INSIGHTBLOOM_WEBHOOK_SECRET.encode("utf-8"), raw, hashlib.sha256
    ).hexdigest() if INSIGHTBLOOM_WEBHOOK_SECRET else ""
    if not expected or not hmac.compare_digest(provided, expected):
        raise HTTPException(403, "Firma de webhook inválida")
    try:
        body = WebhookPayload.model_validate_json(raw)
    except Exception as exc:
        raise HTTPException(400, "Payload inválido") from exc

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
