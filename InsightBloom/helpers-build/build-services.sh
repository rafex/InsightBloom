#!/usr/bin/env bash
set -euo pipefail
echo "[build-services] Building all services..."
./mvnw -f pom.xml clean package -DskipTests
echo "[build-services] Done."
