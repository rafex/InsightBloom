#!/usr/bin/env bash
set -euo pipefail
echo "[build-services] Installing parent..."
./mvnw -f pom.xml install -N -DskipTests -q
echo "[build-services] Installing contracts..."
./mvnw -f backend/contracts/pom.xml clean install -DskipTests -q
echo "[build-services] Building services..."
./mvnw -f backend/services/pom.xml clean package -DskipTests
echo "[build-services] Done."
