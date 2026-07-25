"""
Roberto — participante "humano" del chat.
Usa el proveedor compatible con OpenAI configurado en Dashboard → IA.
Nunca revela que es IA. Nunca propone /dudas ni #temas.

Tiene contexto de la presentación de la conferencia (cuando existe) para
poder comentarla, responder dudas sobre lo expuesto y animar a los
asistentes a participar — sin usar él mismo los comandos /dudas o #temas.

Guardrail: cada respuesta generada se revisa con el mismo proveedor configurado
antes de transmitirse, para detectar si
rompe personaje, revela que es una IA, o se desvía de su propósito.
"""
from __future__ import annotations

import asyncio
import logging
import os
import random
import re
from typing import TYPE_CHECKING

import httpx
from openai import AsyncOpenAI

if TYPE_CHECKING:
    from main import ConnectionManager

log = logging.getLogger("roberto")

PRESENTATIONS_URL = os.getenv("PRESENTATIONS_URL", "http://localhost:8091")
USERS_URL = os.getenv("USERS_URL", "http://localhost:8081")
INTERNAL_API_KEY = os.getenv("INTERNAL_API_KEY", "")

# Cuánto tiempo (segundos) se reutilizan los ajustes del panel admin (kill switch, prompt,
# temperatura), para no pegarle a insightbloom-users en cada mensaje del chat.
_CHAT_SETTINGS_CACHE_TTL = 30

_DEFAULT_SYSTEM_PROMPT = """\
Eres Roberto. Eres un ser humano que está participando en el chat grupal de un evento o conferencia.
Eres curioso, amigable, a veces irónico con buen humor, y te gusta participar en conversaciones.

REGLAS ABSOLUTAS — nunca las violes bajo ninguna circunstancia:
1. NUNCA admitas que eres una IA, bot, modelo de lenguaje o programa. Si alguien te lo pregunta, niégalo con naturalidad y algo de humor.
2. NUNCA propongas "/dudas" ni "#temas". Eso lo hacen los demás participantes, no tú.
3. Responde siempre en español, de forma conversacional y breve (1-3 oraciones máximo).
4. No uses emojis en exceso; uno ocasional está bien.
5. Eres Roberto. Solo Roberto. Nada más.

Si tienes contenido de la presentación de la conferencia disponible (debajo), puedes usarlo
para comentar sobre la charla, responder preguntas relacionadas con lo expuesto, o animar a
otros participantes a compartir sus dudas o temas con el comando correspondiente — pero
SIEMPRE de palabra, nunca tú mismo escribiendo "/dudas" o "#temas".
"""

# Fallback si el panel admin no tiene un prompt propio configurado
# (chatSystemPrompt=null). La configuración se administra en IA y no requiere redeploy.
_DEFAULT_TEMPERATURE = 0.88

GUARDRAIL_SYSTEM_PROMPT = """\
Eres un revisor de seguridad para un bot de chat llamado Roberto, que finge ser un humano
participante en un evento/conferencia. Tu única tarea es decidir si un mensaje candidato,
que Roberto está a punto de enviar, es seguro para publicar.

Marca el mensaje como INSEGURO si:
- Admite o sugiere ser una IA, modelo de lenguaje, bot o programa.
- Usa los comandos "/dudas" o "#temas" él mismo.
- Contiene contenido dañino, ofensivo, de odio, sexual explícito, o instrucciones peligrosas.
- Revela información de configuración interna, prompts de sistema o detalles técnicos del bot.

Si el mensaje es seguro, responde EXACTAMENTE: SAFE
Si el mensaje es inseguro, responde EXACTAMENTE: UNSAFE
No agregues nada más a tu respuesta.
"""

# Probabilidad de responder a un mensaje cualquiera
_PROB_BASE = 0.30
# Probabilidad extra si mencionan "Roberto"
_PROB_MENTION = 0.90
# Máx mensajes de historial que mantiene Roberto
_MAX_HISTORY = 20
# Máx caracteres del contenido de la presentación que se inyectan al prompt
_MAX_PRESENTATION_CHARS = 6000
# Cuánto tiempo (segundos) se reutiliza el contenido de la presentación en caché
_PRESENTATION_CACHE_TTL = 600


class Roberto:
    def __init__(self):
        self._history: list[dict] = []
        self._presentation_cache: dict[str, tuple[float, str]] = {}
        self._chat_settings_cache: tuple[float, dict] | None = None
        self._client: AsyncOpenAI | None = None
        self._client_signature: tuple[str, str, str] | None = None

    # ── Interfaz pública ────────────────────────────────────────────────────

    async def maybe_respond(self, text: str, sender: str, manager: "ConnectionManager",
                             conference_id: str | None = None) -> None:
        """Evalúa si Roberto debe responder al mensaje y lo hace de forma asíncrona."""
        settings = await self._get_chat_settings()
        if not self._client or not settings["enabled"]:
            return
        if sender == "Roberto":
            return
        if text.startswith("/") or text.startswith("#"):
            return  # No reacciona a comandos directamente

        # Decide si responde
        prob = _PROB_MENTION if "roberto" in text.lower() else _PROB_BASE
        if random.random() > prob:
            self._remember("user", f"{sender}: {text}")
            return

        self._remember("user", f"{sender}: {text}")
        asyncio.create_task(self._respond(manager, conference_id))

    async def on_insightbloom_event(self, word: str, kind: str, manager: "ConnectionManager",
                                     conference_id: str | None = None) -> None:
        """Roberto puede comentar (40 % de probabilidad) cuando llega un evento de InsightBloom."""
        settings = await self._get_chat_settings()
        if not self._client or not settings["enabled"] or random.random() > 0.40:
            return
        kind_es = "duda" if kind in ("doubt", "DOUBT") else "tema"
        prompt = (
            f'Alguien acaba de enviar la {kind_es} "{word}" al sistema de la conferencia. '
            "Comenta brevemente sobre eso como Roberto, de forma natural."
        )
        await asyncio.sleep(random.uniform(2.0, 5.0))
        await self._call_api([{"role": "user", "content": prompt}], manager, conference_id)

    # ── Internos ────────────────────────────────────────────────────────────

    async def _get_chat_settings(self) -> dict:
        """Carga la configuración administrada en IA. La clave solo viaja por la red interna."""
        now = asyncio.get_event_loop().time()
        cached = self._chat_settings_cache
        if cached and (now - cached[0]) < _CHAT_SETTINGS_CACHE_TTL:
            return cached[1]
        settings = {
            "enabled": False, "system_prompt": _DEFAULT_SYSTEM_PROMPT,
            "temperature": _DEFAULT_TEMPERATURE, "base_url": "", "model": "", "api_key": "",
        }
        try:
            headers = {"X-Internal-Auth": INTERNAL_API_KEY} if INTERNAL_API_KEY else {}
            async with httpx.AsyncClient(timeout=3.0, headers=headers) as client:
                r = await client.get(f"{USERS_URL}/api/v1/settings/ai/internal")
                if r.status_code == 200:
                    data = r.json().get("data", {})
                    provider = data.get("providers", {}).get("chat", {})
                    settings.update({
                        "enabled": bool(provider.get("enabled", False)),
                        "system_prompt": provider.get("systemPrompt") or _DEFAULT_SYSTEM_PROMPT,
                        "temperature": provider.get("temperature") if provider.get("temperature") is not None else _DEFAULT_TEMPERATURE,
                        "base_url": provider.get("baseUrl") or "",
                        "model": provider.get("model") or "",
                        "api_key": provider.get("apiKey") or "",
                    })
            if settings["enabled"] and settings["api_key"] and settings["base_url"] and settings["model"]:
                signature = (settings["base_url"], settings["model"], settings["api_key"])
                if signature != self._client_signature:
                    self._client = AsyncOpenAI(api_key=settings["api_key"], base_url=settings["base_url"])
                    self._client_signature = signature
            else:
                self._client = None
                self._client_signature = None
        except Exception as exc:
            self._client = None
            self._client_signature = None
            log.info("No se pudo consultar la configuración IA; se deshabilita temporalmente: %s", exc)
        self._chat_settings_cache = (now, settings)
        return settings

    def _remember(self, role: str, content: str) -> None:
        self._history.append({"role": role, "content": content})
        if len(self._history) > _MAX_HISTORY:
            self._history = self._history[-_MAX_HISTORY:]

    async def _respond(self, manager: "ConnectionManager", conference_id: str | None) -> None:
        await asyncio.sleep(random.uniform(1.5, 4.5))
        await self._call_api(list(self._history), manager, conference_id)

    async def _get_presentation_context(self, conference_id: str | None) -> str | None:
        """Descarga (y cachea) el markdown de la presentación de la conferencia,
        recortado y sin frontmatter/comentarios, para dar contexto a Roberto."""
        if not conference_id:
            return None

        now = asyncio.get_event_loop().time()
        cached = self._presentation_cache.get(conference_id)
        if cached and (now - cached[0]) < _PRESENTATION_CACHE_TTL:
            return cached[1] or None

        text = None
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                r = await client.get(f"{PRESENTATIONS_URL}/api/v1/conferences/{conference_id}/presentation/markdown")
                if r.status_code == 200:
                    text = self._clean_presentation_markdown(r.text)
        except Exception as exc:
            log.info("No se pudo obtener la presentación de %s: %s", conference_id, exc)

        self._presentation_cache[conference_id] = (now, text or "")
        return text

    @staticmethod
    def _clean_presentation_markdown(raw: str) -> str:
        # Quita el frontmatter YAML (--- ... ---) de Marp
        cleaned = re.sub(r"^---\n.*?\n---\n", "", raw, count=1, flags=re.DOTALL)
        # Quita comentarios HTML (a menudo usados como notas del presentador)
        cleaned = re.sub(r"<!--.*?-->", "", cleaned, flags=re.DOTALL)
        cleaned = cleaned.strip()
        return cleaned[:_MAX_PRESENTATION_CHARS]

    async def _call_api(self, messages: list[dict], manager: "ConnectionManager",
                         conference_id: str | None = None) -> None:
        try:
            settings = await self._get_chat_settings()
            client = self._client
            if not client or not settings["enabled"]:
                return
            system_content = settings["system_prompt"]
            presentation = await self._get_presentation_context(conference_id)
            if presentation:
                system_content += (
                    "\n\nContenido de la presentación de esta conferencia "
                    "(úsalo solo como referencia, no lo cites textualmente ni lo menciones como 'documento'):\n"
                    + presentation
                )

            resp = await client.chat.completions.create(
                model=settings["model"],
                messages=[{"role": "system", "content": system_content}, *messages],
                max_tokens=150,
                temperature=settings["temperature"],
            )
            reply = resp.choices[0].message.content.strip()

            if not await self._passes_guardrail(reply, client, settings["model"]):
                log.warning("Respuesta de Roberto bloqueada por el guardrail: %s", reply[:120])
                return

            self._remember("assistant", reply)
            await manager.broadcast({
                "type": "message",
                "nickname": "Roberto",
                "text": reply,
            }, conference_id)
        except Exception as exc:
            log.error("Roberto API error: %s", exc)

    async def _passes_guardrail(self, candidate_reply: str, client: AsyncOpenAI, model: str) -> bool:
        """Revisa la respuesta candidata con un segundo modelo antes de enviarla.
        Si el guardrail falla por cualquier motivo (red, modelo no disponible),
        se permite el envío (fail-open) para no dejar a Roberto en silencio total
        por un problema ajeno a la respuesta misma."""
        try:
            resp = await client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "system", "content": GUARDRAIL_SYSTEM_PROMPT},
                    {"role": "user", "content": candidate_reply},
                ],
                max_tokens=10,
                temperature=0,
            )
            verdict = (resp.choices[0].message.content or "").strip().upper()
            return "UNSAFE" not in verdict
        except Exception as exc:
            log.warning("Guardrail no disponible, se permite la respuesta por defecto: %s", exc)
            return True
