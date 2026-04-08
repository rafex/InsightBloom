#!/bin/sh
# Maven wrapper script — busca mvn en rutas comunes si no está en el PATH
MVN=""
for candidate in \
  "$(command -v mvn 2>/dev/null)" \
  /opt/homebrew/bin/mvn \
  /usr/local/bin/mvn \
  "$HOME/.sdkman/candidates/maven/current/bin/mvn"; do
  if [ -x "$candidate" ]; then
    MVN="$candidate"
    break
  fi
done

if [ -z "$MVN" ]; then
  echo "ERROR: mvn not found. Install Maven or add it to PATH." >&2
  exit 1
fi

exec "$MVN" "$@"
