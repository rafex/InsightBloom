#!/usr/bin/env bash
set -euo pipefail
SERVICES=(insightbloom-users insightbloom-moderation insightbloom-stats insightbloom-query insightbloom-ingest)
PORTS=(8081 8082 8083 8084 8085)

# ── Matar procesos existentes en los puertos de los servicios ─────────────────
echo "[run-services] Checking for running services..."
for port in "${PORTS[@]}"; do
  pids=$(lsof -ti:"$port" 2>/dev/null || true)
  if [[ -n "$pids" ]]; then
    echo "[run-services] Stopping process on port $port (PID $pids)..."
    kill "$pids" 2>/dev/null || true
  fi
done

# Dar un momento para que los puertos queden libres
sleep 1

echo "[run-services] Starting all services..."
for svc in "${SERVICES[@]}"; do
  JAR=$(find services/$svc/target -name "*.jar" -not -name "*sources*" -not -name "original-*" 2>/dev/null | head -1)
  if [[ -n "$JAR" ]]; then
    echo "[run-services] Starting $svc..."
    java -jar "$JAR" &
  else
    echo "[run-services] Skipping $svc (not built)"
  fi
done
echo "[run-services] All services started. Use 'kill %1 %2 ...' to stop."
wait
