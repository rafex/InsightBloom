#!/bin/sh
set -eu

export GNUPGHOME="${GNUPGHOME:-/tmp/gnupg}"
mkdir -p "$GNUPGHOME"
chmod 700 "$GNUPGHOME"

if [ -s /run/gopass-keys/gpg-private-key.asc ] && [ -s /run/gopass-keys/gpg-passphrase ] \
    && [ -n "${TICKET_SIGNER_GPG_FINGERPRINT:-}" ]; then
  export GOPASS_GPG_OPTS="--pinentry-mode loopback --passphrase-file /run/gopass-keys/gpg-passphrase"
  gpg --batch --pinentry-mode loopback --passphrase-file /run/gopass-keys/gpg-passphrase \
      --import /run/gopass-keys/gpg-private-key.asc >/dev/null 2>&1
  store="$GOPASS_HOMEDIR/.local/share/gopass/stores/root"
  if [ ! -f "$store/.gpg-id" ]; then
    mkdir -p "$(dirname "$store")"
    gopass init --path "$store" "$TICKET_SIGNER_GPG_FINGERPRINT" >/dev/null
  fi
fi

exec uvicorn app:app --host 0.0.0.0 --port 8097 --no-server-header
