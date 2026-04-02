#!/usr/bin/env bash
set -euo pipefail
SERVICE=${1:?Usage: build-service.sh <service-name>}
echo "[build-service] Building $SERVICE..."
./mvnw -f services/$SERVICE/pom.xml clean package -DskipTests
echo "[build-service] Done."
