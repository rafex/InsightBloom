#!/usr/bin/env bash
# Smoke test de humo contra el stack de infra/compose/local.yml corriendo en localhost.
# Complementa (no reemplaza) los tests estructurados de Fase 5b (mvn verify -Pintegration,
# *IT.java): mismo idioma curl+jq que scripts/run/smoke-test-events.sh (pensado para prod),
# pero apuntado a localhost y sin requerir un usuario admin sembrado, ya que
# POST /api/v1/messages en ingest acepta autores "guest" sin autenticacion.
#
# Uso:
#   scripts/test/integration-smoke.sh
#
# Requiere: curl, jq
set -euo pipefail

USERS_URL="${USERS_URL:-http://localhost:8081}"
MODERATION_URL="${MODERATION_URL:-http://localhost:8084}"
STATS_URL="${STATS_URL:-http://localhost:8085}"
QUERY_URL="${QUERY_URL:-http://localhost:8083}"
INGEST_URL="${INGEST_URL:-http://localhost:8082}"

for bin in curl jq; do
  command -v "$bin" >/dev/null 2>&1 || { echo "ERROR: falta '$bin' en el PATH" >&2; exit 1; }
done

log() { echo "[integration-smoke] $*"; }
fail() { echo "[integration-smoke] FALLO: $*" >&2; exit 1; }

# ── 1. /version en los 5 servicios ──────────────────────────────────────────
declare -A SERVICES=(
  [insightbloom-users]="$USERS_URL"
  [insightbloom-moderation]="$MODERATION_URL"
  [insightbloom-stats]="$STATS_URL"
  [insightbloom-query]="$QUERY_URL"
  [insightbloom-ingest]="$INGEST_URL"
)
for name in "${!SERVICES[@]}"; do
  url="${SERVICES[$name]}"
  log "Verificando /version en ${name} (${url})..."
  RESP="$(curl -sS "${url}/version")"
  SERVICE_FIELD="$(echo "$RESP" | jq -r '.data.service // empty')"
  [[ "$SERVICE_FIELD" == "$name" ]] || fail "${name}: /version devolvio service inesperado. Respuesta: $RESP"
done
log "OK — los 5 servicios responden /version correctamente"

# ── 2. Flujo real ingest -> moderation (sin auth, autor guest) ──────────────
CONFERENCE_ID="smoke-$(date +%s)"
log "Enviando mensaje de prueba a ingest (conferenceId=${CONFERENCE_ID})..."
INGEST_RESP="$(curl -sS -X POST "${INGEST_URL}/api/v1/messages" \
  -H 'Content-Type: application/json' \
  -d "$(jq -n --arg cid "$CONFERENCE_ID" \
        '{conferenceId:$cid, author:{displayName:"Integration Smoke"}, message:{type:"doubt", word:"prueba-smoke", detail:"mensaje de integration-smoke.sh"}}')")"

MESSAGE_ID="$(echo "$INGEST_RESP" | jq -r '.data.messageId // empty')"
STATUS="$(echo "$INGEST_RESP" | jq -r '.data.status // empty')"
[[ -n "$MESSAGE_ID" ]] || fail "no se pudo ingerir el mensaje de prueba. Respuesta: $INGEST_RESP"
case "$STATUS" in
  visible|censurado_auto|censurado_manual) ;;
  *) fail "status inesperado en la respuesta de ingest: '${STATUS}'. Respuesta: $INGEST_RESP" ;;
esac
log "OK — mensaje ingerido (messageId=${MESSAGE_ID}, status=${STATUS})"

echo ""
echo "════════════════════════════════════════════════════════"
echo " INTEGRATION SMOKE OK"
echo "  servicios verificados: ${!SERVICES[*]}"
echo "  mensaje de prueba    : ${MESSAGE_ID} (status=${STATUS})"
echo "════════════════════════════════════════════════════════"
