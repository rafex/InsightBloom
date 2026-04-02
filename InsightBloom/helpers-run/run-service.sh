#!/usr/bin/env bash
set -euo pipefail
SERVICE=${1:?Usage: run-service.sh <service-name>}
JAR=$(find services/$SERVICE/target -name "*.jar" -not -name "*sources*" | head -1)
if [[ -z "$JAR" ]]; then
  echo "[run-service] JAR not found for $SERVICE. Run build-service.sh first."
  exit 1
fi
echo "[run-service] Starting $SERVICE from $JAR..."
java -jar "$JAR"
