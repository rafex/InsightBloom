#!/usr/bin/env bash
set -euo pipefail
echo "[install-services] Installing Maven dependencies..."
./mvnw -f pom.xml dependency:resolve -q
echo "[install-services] Done."
