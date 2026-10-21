"""Versioned AES-GCM envelope encryption for provider credentials."""

from __future__ import annotations

import base64
import binascii
import os
from dataclasses import dataclass

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


class InvalidCiphertext(ValueError):
    """Ciphertext cannot be authenticated or uses an unsupported version."""


@dataclass(frozen=True)
class KeyRing:
    active_version: str
    keys: dict[str, bytes]

    @classmethod
    def from_environment(cls) -> "KeyRing":
        """Load active and retained keys from versioned base64 environment variables."""
        active = os.environ.get("LLM_CREDENTIALS_ACTIVE_KEY_VERSION", "v1").strip()
        keys: dict[str, bytes] = {}
        for number in range(1, 10):
            version = f"v{number}"
            encoded = os.environ.get(f"LLM_CREDENTIALS_MASTER_KEY_{version.upper()}", "").strip()
            if not encoded:
                continue
            try:
                key = base64.b64decode(encoded, validate=True)
            except (binascii.Error, ValueError) as exc:
                raise RuntimeError("llm_gateway_master_key_invalid") from exc
            if len(key) != 32:
                raise RuntimeError("llm_gateway_master_key_invalid")
            keys[version] = key
        if active not in keys:
            raise RuntimeError("llm_gateway_master_key_invalid")
        return cls(active_version=active, keys=keys)

    def encrypt(self, plaintext: str, *, associated_data: bytes) -> str:
        key = self.keys[self.active_version]
        nonce = os.urandom(12)
        ciphertext = AESGCM(key).encrypt(nonce, plaintext.encode("utf-8"), associated_data)
        token = base64.urlsafe_b64encode(nonce + ciphertext).decode("ascii").rstrip("=")
        return f"{self.active_version}:{token}"

    def decrypt(self, value: str, *, associated_data: bytes) -> str:
        try:
            version, token = value.split(":", 1)
            key = self.keys[version]
            raw = base64.urlsafe_b64decode(token + "=" * (-len(token) % 4))
            if len(raw) < 12 + 16:
                raise ValueError("short ciphertext")
            return AESGCM(key).decrypt(raw[:12], raw[12:], associated_data).decode("utf-8")
        except Exception as exc:
            raise InvalidCiphertext("credential_unavailable") from exc

    def reencrypt(self, value: str, *, associated_data: bytes) -> str:
        plaintext = self.decrypt(value, associated_data=associated_data)
        return self.encrypt(plaintext, associated_data=associated_data)
