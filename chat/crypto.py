"""
Hash resistente a offline cracking para contraseñas y Fernet para secretos
legacy que todavía necesitan cifrado reversible durante la migración.

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
import secrets
import uuid
from pathlib import Path

from cryptography.fernet import Fernet
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt

log = logging.getLogger("crypto")

_KEY_FILE = Path(os.getenv("KEY_FILE", ".secret_key"))


def _key_from_uuid() -> bytes:
    """Genera una clave Fernet de 44 chars a partir de un UUID v4.
    uuid4().hex → 32 chars ASCII → base64url → clave Fernet válida de 44 chars.
    """
    return base64.urlsafe_b64encode(uuid.uuid4().hex.encode())


def _is_valid_fernet_key(raw: str) -> bool:
    """Comprueba si raw es una clave Fernet válida (32 bytes base64url → 44 chars)."""
    try:
        Fernet(raw.encode())
        return True
    except Exception:
        return False


def _load_or_create_key() -> bytes:
    # 1. Variable de entorno
    env_key = os.getenv("CHAT_SECRET_KEY", "").strip()
    if env_key:
        if _is_valid_fernet_key(env_key):
            log.info("Clave Fernet cargada desde CHAT_SECRET_KEY")
            return env_key.encode()
        log.warning(
            "CHAT_SECRET_KEY presente pero no es una clave Fernet válida (necesita 44 chars base64url). "
            "Se ignorará y se usará la siguiente fuente disponible."
        )

    # 2. Archivo persistente
    if _KEY_FILE.exists():
        key = _KEY_FILE.read_text().strip()
        if key and _is_valid_fernet_key(key):
            log.info("Clave Fernet cargada desde %s", _KEY_FILE)
            return key.encode()

    # 3. Generar desde UUID v4 y persistir
    key = _key_from_uuid()
    try:
        _KEY_FILE.write_text(key.decode())
        log.warning(
            "CHAT_SECRET_KEY no configurada (o inválida). "
            "Clave generada desde UUID v4 y guardada en %s\n"
            "  → Para producción genera una clave válida:\n"
            "    python3 -c \"from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())\"",
            _KEY_FILE,
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

_SCRYPT_PREFIX = "$scrypt$"
_SCRYPT_N = 2**15
_SCRYPT_R = 8
_SCRYPT_P = 1
_SCRYPT_DKLEN = 32


def _derive_scrypt(password: str, salt: bytes, n: int, r: int, p: int, length: int) -> bytes:
    # Bound parameters read from the database so a corrupt or tampered record
    # cannot turn a login attempt into an unbounded memory/CPU allocation.
    if n < 2**14 or n > 2**20 or n & (n - 1) or r < 1 or r > 32 or p < 1 or p > 8:
        raise ValueError("invalid scrypt parameters")
    if length < 16 or length > 64:
        raise ValueError("invalid scrypt output length")
    return Scrypt(salt=salt, length=length, n=n, r=r, p=p).derive(password.encode("utf-8"))


def hash_password(password: str) -> str:
    """Genera un hash scrypt con salt aleatorio, sin guardar la contraseña."""
    salt = secrets.token_bytes(16)
    derived = _derive_scrypt(password, salt, _SCRYPT_N, _SCRYPT_R, _SCRYPT_P, _SCRYPT_DKLEN)
    return f"{_SCRYPT_PREFIX}{_SCRYPT_N},{_SCRYPT_R},{_SCRYPT_P}${salt.hex()}${derived.hex()}"


def verify_password(password: str, stored: str) -> tuple[bool, bool]:
    """Devuelve (válida, requiere_rehash). Acepta Fernet solo para migración."""
    if not stored:
        return False, False
    if stored.startswith(_SCRYPT_PREFIX):
        try:
            params, salt_hex, expected_hex = stored[len(_SCRYPT_PREFIX):].split("$")
            n, r, p = (int(value) for value in params.split(","))
            expected = bytes.fromhex(expected_hex)
            actual = _derive_scrypt(password, bytes.fromhex(salt_hex), n, r, p, len(expected))
            return secrets.compare_digest(actual.hex(), expected_hex), False
        except (ValueError, TypeError):
            return False, False
    try:
        # Legacy chat accounts are upgraded after a successful login.
        return secrets.compare_digest(decrypt(stored), password), True
    except Exception:
        return False, False


def encrypt(plain: str) -> str:
    """Cifra texto plano → token Fernet."""
    return _fernet.encrypt(plain.encode()).decode()


def decrypt(token: str) -> str:
    """Descifra token Fernet → texto plano."""
    return _fernet.decrypt(token.encode()).decode()
