"""Offline rotation command. Supply the old and active versioned keys to the process."""

import os

from app.crypto import KeyRing
from app.store import CredentialStore


def main() -> None:
    database = os.environ.get("LLM_GATEWAY_DATABASE", "/data/llm-gateway.sqlite3")
    ring = KeyRing.from_environment()
    changed = CredentialStore(database, ring).reencrypt_all()
    print(f"reciphered_records={changed}")


if __name__ == "__main__":
    main()
