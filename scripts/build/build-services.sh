#!/usr/bin/env bash
set -euo pipefail
echo "[build-services] Building all services..."
./mvnw -f backend/services/pom.xml clean package -DskipTests
echo "[build-services] Done."
