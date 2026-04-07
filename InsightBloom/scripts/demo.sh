#!/usr/bin/env bash
# =============================================================================
# demo.sh — Demo completo de InsightBloom end-to-end
#
# Levanta una conferencia, simula asistentes enviando palabras en background
# y muestra la nube de palabras creciendo en tiempo real.
#
# Uso:
#   ./scripts/demo.sh
#   ./scripts/demo.sh --count 60 --delay 0.5
#   ./scripts/demo.sh --conference-id <uuid>   # re-usar conferencia existente
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
USERS_URL="${USERS_URL:-http://localhost:8081}"
INGEST_URL="${INGEST_URL:-http://localhost:8082}"
QUERY_URL="${QUERY_URL:-http://localhost:8083}"
STATS_URL="${STATS_URL:-http://localhost:8085}"
COUNT="${COUNT:-60}"
DELAY="${DELAY:-0.4}"
CONFERENCE_ID=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --conference-id) CONFERENCE_ID="$2"; shift 2 ;;
    --count)         COUNT="$2"; shift 2 ;;
    --delay)         DELAY="$2"; shift 2 ;;
    *) echo "Argumento desconocido: $1"; exit 1 ;;
  esac
done

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

log()  { echo -e "${CYAN}[demo]${RESET} $*"; }
ok()   { echo -e "${GREEN}[ok]${RESET}   $*"; }
fail() { echo -e "${RED}[err]${RESET}  $*" >&2; exit 1; }

# ─── Verificar servicios ──────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║   InsightBloom — Demo End-to-End                    ║${RESET}"
echo -e "${BOLD}╚══════════════════════════════════════════════════════╝${RESET}"
echo ""

log "Verificando servicios..."
for svc_info in "users|${USERS_URL}" "ingest|${INGEST_URL}" "query|${QUERY_URL}" "stats|${STATS_URL}"; do
  name="${svc_info%%|*}"
  url="${svc_info##*|}"
  if curl -sf "${url}/health" > /dev/null 2>&1; then
    ok "${name} → ${url}"
  else
    fail "Servicio '${name}' no responde en ${url}. Ejecuta: just dev-services"
  fi
done

# ─── Login ────────────────────────────────────────────────────────────────────
log "Login como admin..."
LOGIN_RESP=$(curl -sf -X POST "${USERS_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":""}') \
  || fail "Login fallido"

TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])") \
  || fail "No se pudo extraer el token"
ok "Autenticado"

# ─── Crear conferencia si no hay una ─────────────────────────────────────────
if [[ -z "$CONFERENCE_ID" ]]; then
  CONF_NAME="InsightBloom Demo $(date +'%H:%M')"
  log "Creando conferencia: \"${CONF_NAME}\"..."
  CONF_RESP=$(curl -sf -X POST "${USERS_URL}/api/v1/conferences" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${TOKEN}" \
    -d "{\"name\":\"${CONF_NAME}\"}") \
    || fail "No se pudo crear la conferencia"

  CONFERENCE_ID=$(echo "$CONF_RESP" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d.get('uuid') or d.get('conferenceId'))
") \
    || fail "Sin UUID en respuesta"
  FRIENDLY=$(echo "$CONF_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['friendlyId'])" 2>/dev/null || echo "?")
  ok "Conferencia: ${CONFERENCE_ID}"
  ok "Friendly ID: ${FRIENDLY}"
fi

echo ""
echo -e "${YELLOW}  Participantes simulados: ${COUNT}  |  Delay: ${DELAY}s  |  Modo: mixed${RESET}"
echo ""

# ─── Lanzar simulación en background ─────────────────────────────────────────
log "Iniciando simulación de chat en background..."

SIMULATE_CMD="${SCRIPT_DIR}/simulate-chat.sh \
  --conference-id ${CONFERENCE_ID} \
  --count ${COUNT} \
  --delay ${DELAY} \
  --mode mixed"

bash $SIMULATE_CMD > /tmp/insightbloom-sim.log 2>&1 &
SIM_PID=$!

echo -e "  PID simulador: ${SIM_PID}"
echo ""
echo -e "  ${CYAN}Observa la nube de palabras crecer mientras llegan mensajes...${RESET}"
echo -e "  ${CYAN}Presiona Ctrl+C para detener.${RESET}"
echo ""
sleep 2

# ─── Mostrar watch-cloud ──────────────────────────────────────────────────────
trap "kill $SIM_PID 2>/dev/null; echo ''; echo 'Demo finalizado.'" EXIT INT TERM

bash "${SCRIPT_DIR}/watch-cloud.sh" \
  --conference-id "${CONFERENCE_ID}" \
  --interval 3 \
  --type doubt
