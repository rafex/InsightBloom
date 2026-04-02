#!/usr/bin/env bash
set -euo pipefail
SERVICES=(insightbloom-users insightbloom-moderation insightbloom-stats insightbloom-query insightbloom-ingest)
echo "[run-services] Starting all services..."
for svc in "${SERVICES[@]}"; do
  JAR=$(find services/$svc/target -name "*.jar" -not -name "*sources*" 2>/dev/null | head -1)
  if [[ -n "$JAR" ]]; then
    echo "[run-services] Starting $svc..."
    java -jar "$JAR" &
  else
    echo "[run-services] Skipping $svc (not built)"
  fi
done
echo "[run-services] All services started. Use 'kill %1 %2 ...' to stop."
wait
