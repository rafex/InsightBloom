#!/usr/bin/env bash
set -euo pipefail
echo "[demo] Starting InsightBloom PoC demo..."
echo "[demo] Services: users:8081 ingest:8082 query:8083 moderation:8084 stats:8085"
echo "[demo] Frontend: http://localhost:5173"
./helpers-run/run-services.sh &
sleep 5
./helpers-run/run-web.sh
