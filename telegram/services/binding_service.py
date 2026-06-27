from __future__ import annotations

from services.command_parser import parse_conferencia, resolve_conference
from services.telegram_client import is_chat_admin


async def handle_conferencia_command(
    text: str,
    chat_id: int,
    message_thread_id: int | None,
    chat_title: str | None,
    from_user_id: int,
    from_username: str | None,
    db,
) -> str | None:
    """Procesa /conferencia <id> <chat|notificaciones>. Devuelve el texto de respuesta, o None si no matcheó."""
    parsed = parse_conferencia(text)
    if not parsed:
        return None
    raw_id, purpose = parsed

    if not await is_chat_admin(chat_id, from_user_id):
        return "⚠️ Solo administradores del grupo pueden vincular este chat a una conferencia."

    conference_uuid = await resolve_conference(raw_id)
    if not conference_uuid:
        return f"⚠️ No encontré ninguna conferencia con identificador '{raw_id}'."

    db.upsert_binding(
        chat_id=chat_id,
        message_thread_id=message_thread_id,
        conference_uuid=conference_uuid,
        purpose=purpose,
        chat_title=chat_title,
        user_id=from_user_id,
        username=from_username,
    )
    purpose_es = "notificaciones" if purpose == "notifications" else "chat"
    return f"✅ Este chat quedó vinculado a la conferencia '{raw_id}' para {purpose_es}."
