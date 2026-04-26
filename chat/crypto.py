"""
Cifrado reversible de contraseñas con Fernet (AES-128-CBC + HMAC-SHA256).

Resolución de la clave (en orden de prioridad):
  1. Variable de entorno CHAT_SECRET_KEY  (clave Fernet válida de 44 chars)
  2. Archivo .secret_key en el directorio de trabajo
  3. Genera una clave a partir de un UUID v4 y la persiste en .secret_key

Para producción genera una clave segura:
  python3 -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"
"""

import base64
import logging
import os
import uuid
from pathlib import Path

from cryptography.fernet import Fernet

log = logging.getLogger("crypto")

_KEY_FILE = Path(os.getenv("KEY_FILE", ".secret_key"))


def _key_from_uuid() -> bytes:
    """Genera una clave Fernet de 44 chars a partir de un UUID v4.
    uuid4().hex → 32 chars ASCII → base64url → clave Fernet válida de 44 chars.
    """
    return base64.urlsafe_b64encode(uuid.uuid4().hex.encode())


def _load_or_create_key() -> bytes:
    # 1. Variable de entorno
    env_key = os.getenv("CHAT_SECRET_KEY", "").strip()
    if env_key:
        log.info("Clave Fernet cargada desde CHAT_SECRET_KEY")
        return env_key.encode()

    # 2. Archivo persistente
    if _KEY_FILE.exists():
        key = _KEY_FILE.read_text().strip().encode()
        if key:
            log.info("Clave Fernet cargada desde %s", _KEY_FILE)
            return key

    # 3. Generar desde UUID v4 y persistir
    key = _key_from_uuid()
    try:
        _KEY_FILE.write_text(key.decode())
        log.warning(
            "CHAT_SECRET_KEY no configurada. Clave generada desde UUID v4 y guardada en %s\n"
            "  → Para producción usa: CHAT_SECRET_KEY=%s",
            _KEY_FILE,
            key.decode(),
        )
    except OSError as exc:
        log.warning(
            "No se pudo escribir %s: %s  "
            "— la clave NO persistirá entre reinicios",
            _KEY_FILE,
            exc,
        )
    return key


_fernet = Fernet(_load_or_create_key())


def encrypt(plain: str) -> str:
    """Cifra texto plano → token Fernet."""
    return _fernet.encrypt(plain.encode()).decode()


def decrypt(token: str) -> str:
    """Descifra token Fernet → texto plano."""
    return _fernet.decrypt(token.encode()).decode()
