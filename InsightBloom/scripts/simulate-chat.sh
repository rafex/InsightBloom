#!/usr/bin/env bash
# =============================================================================
# simulate-chat.sh — Simula asistentes enviando palabras a una conferencia
#
# Uso:
#   ./scripts/simulate-chat.sh
#   ./scripts/simulate-chat.sh --conference-id <uuid>
#   ./scripts/simulate-chat.sh --count 80 --delay 0.2 --mode topics
#   ./scripts/simulate-chat.sh --count 50 --delay 0 --mode mixed
# =============================================================================
set -euo pipefail

USERS_URL="${USERS_URL:-http://localhost:8081}"
INGEST_URL="${INGEST_URL:-http://localhost:8082}"
QUERY_URL="${QUERY_URL:-http://localhost:8083}"
STATS_URL="${STATS_URL:-http://localhost:8085}"

ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-}"
COUNT="${COUNT:-40}"
DELAY="${DELAY:-0.3}"
MODE="${MODE:-mixed}"   # doubt | topic | mixed
CONFERENCE_ID=""
CONF_NAME="Demo $(date +'%H:%M:%S')"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --)              shift ;;                        # ignorar separador de Just/Make
    --conference-id) CONFERENCE_ID="$2"; shift 2 ;;
    --conf-name)     CONF_NAME="$2"; shift 2 ;;
    --count)         COUNT="$2"; shift 2 ;;
    --delay)         DELAY="$2"; shift 2 ;;
    --mode)          MODE="$2"; shift 2 ;;
    --admin-user)    ADMIN_USER="$2"; shift 2 ;;
    --admin-pass)    ADMIN_PASS="$2"; shift 2 ;;
    *) echo "Argumento desconocido: $1" >&2; exit 1 ;;
  esac
done

# ─── Colores ─────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

info() { echo -e "${CYAN}[sim]${RESET} $*"; }
ok()   { echo -e "${GREEN}[ok]${RESET}  $*"; }
fail() { echo -e "${RED}[err]${RESET} $*" >&2; exit 1; }

# ─── Palabras para la simulación ─────────────────────────────────────────────
DOUBT_WORDS=(
  kubernetes docker microservicios jwt oauth redis kafka grpc graphql
  rest sql nosql mongodb postgresql git cicd devops terraform ansible
  prometheus grafana nginx loadbalancer websocket sse polling caching
  elasticsearch rabbitmq nats jetstream tracing opentelemetry helm
  istio servicemesh sidecar ingress rbac secrets configmap etcd
  dapr temporal saga choreography orchestration idempotency eventual
  springboot quarkus micronaut helidon vertx netty jetty tomcat
)

DOUBT_DETAILS=(
  "¿Cómo se configura en producción?"
  "¿Cuándo usarlo vs la alternativa?"
  "¿Qué limitaciones tiene?"
  "¿Cómo escala horizontalmente?"
  "¿Tiene soporte en cloud nativo?"
  "¿Cómo se monitorea?"
  "¿Cómo manejo los errores?"
  "¿Existe alguna mejor práctica documentada?"
  "¿Cuál es el costo en producción?"
  ""
)

TOPIC_WORDS=(
  arquitectura escalabilidad seguridad rendimiento testing documentacion
  monitoreo observabilidad despliegue automatizacion integracion migracion
  refactoring patrones principios solid ddd hexagonal event-driven
  resiliencia circuit-breaker retry backpressure throttling rate-limiting
  clean-code pair-programming code-review onboarding tecnica-deuda
)

TOPIC_DETAILS=(
  "Propongo hablar de las mejores prácticas"
  "Creo que deberíamos dedicarle más tiempo"
  "Tengo un caso de uso concreto para compartir"
  "Necesitamos definir un estándar aquí"
  "¿Alguien tiene experiencia en esto?"
  ""
)

GUEST_NAMES=(
  "Ana García" "Carlos López" "María Rodríguez" "Luis Martínez"
  "Sofía Hernández" "Diego Torres" "Valeria Flores" "Andrés Ramírez"
  "Camila Vega" "Roberto Sánchez" "Lucía Jiménez" "Fernando Castro"
  "Isabella Morales" "Sebastián Reyes" "Natalia Gómez" "Pablo Ortega"
  "Daniela Cruz" "Alejandro Díaz" "Valentina Ruiz" "Miguel Vargas"
)

rand() {
  local arr=("$@")
  echo "${arr[$((RANDOM % ${#arr[@]}))]}"
}

# ─── Header ──────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║        InsightBloom — Simulador de Chat              ║${RESET}"
echo -e "${BOLD}╚══════════════════════════════════════════════════════╝${RESET}"
echo ""

# ─── 1. Login ────────────────────────────────────────────────────────────────
info "Autenticando como ${ADMIN_USER}..."
LOGIN_RESP=$(curl -sf -X POST "${USERS_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}" 2>/dev/null) \
  || fail "No se pudo conectar con el servicio de usuarios en ${USERS_URL}"

TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])" 2>/dev/null) \
  || fail "Login fallido"
ok "Token: ${TOKEN:0:16}..."

# ─── 2. Crear o verificar conferencia ────────────────────────────────────────
if [[ -z "$CONFERENCE_ID" ]]; then
  info "Creando conferencia: \"${CONF_NAME}\"..."
  CONF_RESP=$(curl -sf -X POST "${USERS_URL}/api/v1/conferences" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${TOKEN}" \
    -d "{\"name\":\"${CONF_NAME}\"}" 2>/dev/null) \
    || fail "No se pudo crear la conferencia"

  CONFERENCE_ID=$(echo "$CONF_RESP" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d.get('uuid') or d.get('conferenceId'))
" 2>/dev/null) || fail "No se pudo extraer el ID de la conferencia"

  FRIENDLY_ID=$(echo "$CONF_RESP" | python3 -c \
    "import sys,json; print(json.load(sys.stdin)['data']['friendlyId'])" 2>/dev/null || echo "?")

  ok "Conferencia: ${CONFERENCE_ID}"
  ok "Friendly ID: ${FRIENDLY_ID}"
else
  # Si parece un friendly-id (no tiene guiones en posición UUID), resolvemos el UUID
  if [[ ! "$CONFERENCE_ID" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]; then
    info "Resolviendo friendly-id '${CONFERENCE_ID}'..."
    CONF_RESP=$(curl -sf "${USERS_URL}/api/v1/conferences/by-friendly/${CONFERENCE_ID}" 2>/dev/null) \
      || fail "No se encontró la conferencia con friendly-id '${CONFERENCE_ID}'"
    CONFERENCE_ID=$(echo "$CONF_RESP" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(d.get('uuid') or d.get('conferenceId'))
" 2>/dev/null) || fail "No se pudo resolver el UUID desde el friendly-id"
    ok "UUID resuelto: ${CONFERENCE_ID}"
  else
    ok "Usando conferencia: ${CONFERENCE_ID}"
  fi
fi

echo ""
info "Enviando ${COUNT} mensajes [modo=${MODE}, delay=${DELAY}s]..."
echo ""

# ─── 3. Enviar mensajes ───────────────────────────────────────────────────────
ok_count=0
err_count=0

for i in $(seq 1 "$COUNT"); do
  # Elegir tipo según modo
  case "$MODE" in
    doubt) msg_type="doubt" ;;
    topic) msg_type="topic" ;;
    *)
      if (( RANDOM % 4 == 0 )); then
        msg_type="topic"
      else
        msg_type="doubt"
      fi
      ;;
  esac

  # Elegir palabra y detalle
  if [[ "$msg_type" == "doubt" ]]; then
    word=$(rand "${DOUBT_WORDS[@]}")
    detail=$(rand "${DOUBT_DETAILS[@]}")
  else
    word=$(rand "${TOPIC_WORDS[@]}")
    detail=$(rand "${TOPIC_DETAILS[@]}")
  fi

  guest_name=$(rand "${GUEST_NAMES[@]}")
  fingerprint="fp-$(printf '%04d' $((RANDOM % 9999)))"

  # Body JSON usando python para escapado seguro
  BODY=$(python3 -c "
import json
print(json.dumps({
    'conferenceId': '${CONFERENCE_ID}',
    'message': {'type': '${msg_type}', 'word': '${word}', 'detail': '${detail}'},
    'author': {'displayName': '${guest_name}', 'kind': 'guest'},
    'device': {'fingerprint': '${fingerprint}'}
}))
")

  if RESP=$(curl -sf -X POST "${INGEST_URL}/api/v1/messages" \
      -H "Content-Type: application/json" \
      -d "$BODY" 2>/dev/null); then
    status=$(echo "$RESP" | python3 -c \
      "import sys,json; print(json.load(sys.stdin)['data'].get('status','ok'))" 2>/dev/null || echo "ok")
    icon="✓"; [[ "$status" == "censurado_auto" ]] && icon="✗"
    printf "  [%3d/%d] %-8s %-22s %-18s %s\n" \
      "$i" "$COUNT" "[${msg_type}]" "$word" "${guest_name:0:17}" "$icon"
    (( ok_count++ )) || true
  else
    printf "  [%3d/%d] ERROR\n" "$i" "$COUNT"
    (( err_count++ )) || true
  fi

  [[ "$DELAY" != "0" ]] && sleep "$DELAY"
done

echo ""
ok "${ok_count} mensajes enviados, ${err_count} errores"

# ─── 4. Resultados ────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DISPLAY_PY="${SCRIPT_DIR}/cloud-display.py"

_show_cloud() {
  local label="$1" endpoint="$2"
  shift 2
  echo ""
  echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
  echo -e "${BOLD}  ${label}${RESET}"
  echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
  curl -sf "$endpoint" 2>/dev/null \
    | python3 "$DISPLAY_PY" "$@" \
    || echo "  (error consultando ${endpoint})"
}

# Query service — todas las palabras (relevanceScore puede ser 0 en el PoC)
_show_cloud "NUBE — DOUBTS  [query]" \
  "${QUERY_URL}/api/v1/conferences/${CONFERENCE_ID}/cloud/doubts" \
  --field-word wordNormalized --field-score relevanceScore --field-count messageCount

_show_cloud "NUBE — TOPICS  [query]" \
  "${QUERY_URL}/api/v1/conferences/${CONFERENCE_ID}/cloud/topics" \
  --field-word wordNormalized --field-score relevanceScore --field-count messageCount

# Stats service — scores reales calculados por el motor de relevancia
_show_cloud "TOP RELEVANCIA — DOUBTS  [stats]" \
  "${STATS_URL}/api/v1/conferences/${CONFERENCE_ID}/stats/relevance?type=DOUBT" \
  --field-word wordNormalized --field-score relevanceScore --field-count countTotal

_show_cloud "TOP RELEVANCIA — TOPICS  [stats]" \
  "${STATS_URL}/api/v1/conferences/${CONFERENCE_ID}/stats/relevance?type=TOPIC" \
  --field-word wordNormalized --field-score relevanceScore --field-count countTotal

echo ""
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
echo -e "  Conference ID: ${YELLOW}${CONFERENCE_ID}${RESET}"
echo -e "  Dashboard:     ${CYAN}http://localhost:5173${RESET}"
echo -e "  Watch live:    ${CYAN}./scripts/watch-cloud.sh --conference-id ${CONFERENCE_ID}${RESET}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
echo ""
