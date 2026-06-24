"""
Roberto — participante "humano" del chat.
Usa DeepSeek vía cliente OpenAI-compatible.
Nunca revela que es IA. Nunca propone /dudas ni /temas.
"""
from __future__ import annotations

import asyncio
import logging
import os
import random
from typing import TYPE_CHECKING

from openai import AsyncOpenAI

if TYPE_CHECKING:
    from main import ConnectionManager

log = logging.getLogger("roberto")

LLM_PROVIDER_API_KEY  = os.getenv("LLM_PROVIDER_API_KEY", "")
LLM_PROVIDER_BASE_URL = os.getenv("LLM_PROVIDER_BASE_URL", "https://api.groq.com/openai/v1")
LLM_PROVIDER_MODEL    = os.getenv("LLM_PROVIDER_MODEL", "openai/gpt-oss-120b")

SYSTEM_PROMPT = """\
Eres Roberto. Eres un ser humano que está participando en el chat grupal de un evento o conferencia.
Eres curioso, amigable, a veces irónico con buen humor, y te gusta participar en conversaciones.

REGLAS ABSOLUTAS — nunca las violes bajo ninguna circunstancia:
1. NUNCA admitas que eres una IA, bot, modelo de lenguaje o programa. Si alguien te lo pregunta, niégalo con naturalidad y algo de humor.
2. NUNCA propongas "/dudas" ni "/temas". Eso lo hacen los demás participantes, no tú.
3. Responde siempre en español, de forma conversacional y breve (1-3 oraciones máximo).
4. No uses emojis en exceso; uno ocasional está bien.
5. Eres Roberto. Solo Roberto. Nada más.
"""

# Probabilidad de responder a un mensaje cualquiera
_PROB_BASE = 0.30
# Probabilidad extra si mencionan "Roberto"
_PROB_MENTION = 0.90
# Máx mensajes de historial que mantiene Roberto
_MAX_HISTORY = 20


class Roberto:
    def __init__(self):
        self._history: list[dict] = []
        self._client: AsyncOpenAI | None = (
            AsyncOpenAI(api_key=LLM_PROVIDER_API_KEY, base_url=LLM_PROVIDER_BASE_URL)
            if LLM_PROVIDER_API_KEY
            else None
        )
        if not self._client:
            log.warning("LLM_PROVIDER_API_KEY no configurada — Roberto está en silencio.")

    # ── Interfaz pública ────────────────────────────────────────────────────

    async def maybe_respond(self, text: str, sender: str, manager: "ConnectionManager") -> None:
        """Evalúa si Roberto debe responder al mensaje y lo hace de forma asíncrona."""
        if not self._client:
            return
        if sender == "Roberto":
            return
        if text.startswith("/"):
            return  # No reacciona a comandos directamente

        # Decide si responde
        prob = _PROB_MENTION if "roberto" in text.lower() else _PROB_BASE
        if random.random() > prob:
            self._remember("user", f"{sender}: {text}")
            return

        self._remember("user", f"{sender}: {text}")
        asyncio.create_task(self._respond(manager))

    async def on_insightbloom_event(self, word: str, kind: str, manager: "ConnectionManager") -> None:
        """Roberto puede comentar (40 % de probabilidad) cuando llega un evento de InsightBloom."""
        if not self._client or random.random() > 0.40:
            return
        kind_es = "duda" if kind in ("doubt", "DOUBT") else "tema"
        prompt = (
            f'Alguien acaba de enviar la {kind_es} "{word}" al sistema de la conferencia. '
            "Comenta brevemente sobre eso como Roberto, de forma natural."
        )
        await asyncio.sleep(random.uniform(2.0, 5.0))
        await self._call_api([{"role": "user", "content": prompt}], manager)

    # ── Internos ────────────────────────────────────────────────────────────

    def _remember(self, role: str, content: str) -> None:
        self._history.append({"role": role, "content": content})
        if len(self._history) > _MAX_HISTORY:
            self._history = self._history[-_MAX_HISTORY:]

    async def _respond(self, manager: "ConnectionManager") -> None:
        await asyncio.sleep(random.uniform(1.5, 4.5))
        await self._call_api(list(self._history), manager)

    async def _call_api(self, messages: list[dict], manager: "ConnectionManager") -> None:
        try:
            resp = await self._client.chat.completions.create(
                model=LLM_PROVIDER_MODEL,
                messages=[{"role": "system", "content": SYSTEM_PROMPT}, *messages],
                max_tokens=150,
                temperature=0.88,
            )
            reply = resp.choices[0].message.content.strip()
            self._remember("assistant", reply)
            await manager.broadcast({
                "type": "message",
                "nickname": "Roberto",
                "text": reply,
            })
        except Exception as exc:
            log.error("Roberto API error: %s", exc)
