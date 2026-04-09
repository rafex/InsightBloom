#!/usr/bin/env bash
set -euo pipefail
echo "[install-services] Installing contracts..."
./mvnw -f backend/contracts/pom.xml clean install -DskipTests -q
echo "[install-services] Resolving service dependencies..."
./mvnw -f backend/services/pom.xml dependency:resolve -q
echo "[install-services] Done."
