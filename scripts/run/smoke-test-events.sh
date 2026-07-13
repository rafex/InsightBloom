#!/usr/bin/env bash
# Smoke test: login como organizador/admin, crea un tipo de evento nuevo (con todas las
# capacidades), crea un evento usando ese tipo, y verifica que quede leible por friendlyId.
# Pensado para probar de punta a punta el flujo de creación tras cambios de infra (ej. HPA/replicas
# de insightbloom-users) sin depender de la UI ni de credenciales manuales en el navegador.
#
# Uso:
#   scripts/run/smoke-test-events.sh --password <PASSWORD_ADMIN_USER> [opciones]
#
# Requiere: curl, jq
set -euo pipefail

BASE_URL="${BASE_URL:-https://insightbloom.v1.rafex.cloud/api/users}"
USERNAME="${USERNAME:-admin}"
PASSWORD="${PASSWORD:-}"
KEEP="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url) BASE_URL="$2"; shift 2 ;;
    --username) USERNAME="$2"; shift 2 ;;
    --password) PASSWORD="$2"; shift 2 ;;
    --keep)     KEEP="true"; shift 1 ;;
    --help|-h)
      cat <<'EOF'
Uso:
  scripts/run/smoke-test-events.sh --password <PASSWORD_ADMIN_USER> [opciones]

Opciones:
  --base-url <url>   Base de la API de insightbloom-users (default: https://insightbloom.v1.rafex.cloud/api/users)
  --username <u>     Usuario a usar para login (default: admin)
  --password <p>     Contraseña (requerido; o exportar PASSWORD)
  --keep              No desactivar el tipo de evento creado al terminar (queda visible en el admin)

Crea (en este orden):
  1. Login -> token
  2. POST /api/v1/event-types      (tipo de evento nuevo, todas las capacidades)
  3. POST /api/v1/conferences      (evento usando ese tipo)
  4. GET  /api/v1/conferences/by-friendly/{friendlyId}  (verificacion de lectura)

Por default desactiva el tipo de evento de prueba al final (PUT .../active {active:false})
para no ensuciar el selector real; pasa --keep para dejarlo activo.
EOF
      exit 0
      ;;
    *) echo "Argumento desconocido: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "${PASSWORD}" ]]; then
  echo "ERROR: --password es requerido (o exporta PASSWORD)" >&2
  exit 1
fi

for bin in curl jq; do
  command -v "$bin" >/dev/null 2>&1 || { echo "ERROR: falta '$bin' en el PATH" >&2; exit 1; }
done

STAMP="$(date +%s)"
EVENT_TYPE_KEY="smoke-test-${STAMP}"
EVENT_NAME="Smoke Test ${STAMP}"

log() { echo "[smoke-test] $*"; }
fail() { echo "[smoke-test] FALLO: $*" >&2; exit 1; }

# ── 1. Login ────────────────────────────────────────────────────────────────
log "Login como '${USERNAME}'..."
LOGIN_RESP="$(curl -sS -X POST "${BASE_URL}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(jq -n --arg u "$USERNAME" --arg p "$PASSWORD" '{username:$u, password:$p}')")"

TOKEN="$(echo "$LOGIN_RESP" | jq -r '.data.token // empty')"
ROLE="$(echo "$LOGIN_RESP" | jq -r '.data.role // empty')"
[[ -n "$TOKEN" ]] || fail "no se pudo obtener token de login. Respuesta: $LOGIN_RESP"
log "OK — role=${ROLE}"

if [[ "$ROLE" != *admin* ]]; then
  fail "el usuario '${USERNAME}' no tiene rol admin (rol actual: '${ROLE}'); se requiere admin para crear tipos de evento"
fi

AUTH_HEADER="Authorization: Bearer ${TOKEN}"

# ── 2. Crear tipo de evento (todas las capacidades) ─────────────────────────
log "Creando tipo de evento '${EVENT_TYPE_KEY}'..."
ALL_CAPS='["TICKETING_GENERAL","TICKETING_SEATED","SURVEY","PRESENTATION","WORD_CLOUD","CHAT_BOT","VIDEO_CONFERENCE","WHITEBOARD","DIAGRAMMING","COLLAB_NOTES","CODE_IDE"]'
ET_RESP="$(curl -sS -X POST "${BASE_URL}/api/v1/event-types" \
  -H 'Content-Type: application/json' -H "$AUTH_HEADER" \
  -d "$(jq -n --arg key "$EVENT_TYPE_KEY" --arg name "Smoke Test ${STAMP}" \
        --argjson caps "$ALL_CAPS" \
        '{key:$key, name:$name, description:"Tipo de evento generado por smoke-test-events.sh", capabilities:$caps}')")"

ET_UUID="$(echo "$ET_RESP" | jq -r '.data.uuid // empty')"
ET_KEY="$(echo "$ET_RESP" | jq -r '.data.key // empty')"
[[ -n "$ET_UUID" ]] || fail "no se pudo crear el tipo de evento. Respuesta: $ET_RESP"
log "OK — event_type uuid=${ET_UUID} key=${ET_KEY}"

# ── 3. Crear evento usando ese tipo ─────────────────────────────────────────
log "Creando evento '${EVENT_NAME}' con eventTypeKey='${ET_KEY}'..."
CONF_RESP="$(curl -sS -X POST "${BASE_URL}/api/v1/conferences" \
  -H 'Content-Type: application/json' -H "$AUTH_HEADER" \
  -d "$(jq -n --arg name "$EVENT_NAME" --arg etk "$ET_KEY" '{name:$name, eventTypeKey:$etk}')")"

CONF_UUID="$(echo "$CONF_RESP" | jq -r '.data.conferenceId // empty')"
FRIENDLY_ID="$(echo "$CONF_RESP" | jq -r '.data.friendlyId // empty')"
CONF_ETK="$(echo "$CONF_RESP" | jq -r '.data.eventTypeKey // empty')"
[[ -n "$CONF_UUID" && -n "$FRIENDLY_ID" ]] || fail "no se pudo crear el evento. Respuesta: $CONF_RESP"
[[ "$CONF_ETK" == "$ET_KEY" ]] || fail "el evento se creo pero con eventTypeKey inesperado: '${CONF_ETK}' (esperado '${ET_KEY}')"
log "OK — conference uuid=${CONF_UUID} friendlyId=${FRIENDLY_ID}"

# ── 4. Verificar lectura por friendlyId ─────────────────────────────────────
log "Verificando lectura por friendlyId..."
READ_RESP="$(curl -sS "${BASE_URL}/api/v1/conferences/by-friendly/${FRIENDLY_ID}")"
READ_UUID="$(echo "$READ_RESP" | jq -r '.data.conferenceId // empty')"
[[ "$READ_UUID" == "$CONF_UUID" ]] || fail "la lectura por friendlyId no devolvio el mismo evento. Respuesta: $READ_RESP"
log "OK — lectura consistente"

# ── 5. Limpieza (desactivar el tipo de evento de prueba) ────────────────────
if [[ "$KEEP" != "true" ]]; then
  log "Desactivando tipo de evento de prueba (usa --keep para conservarlo activo)..."
  curl -sS -X PUT "${BASE_URL}/api/v1/event-types/${ET_UUID}/active" \
    -H 'Content-Type: application/json' -H "$AUTH_HEADER" \
    -d '{"active":false}' > /dev/null
fi

echo ""
echo "════════════════════════════════════════════════════════"
echo " SMOKE TEST OK"
echo "  event_type : ${ET_KEY} (${ET_UUID})"
echo "  event      : ${EVENT_NAME}"
echo "  friendlyId : ${FRIENDLY_ID}"
echo "  conferenceId: ${CONF_UUID}"
echo "════════════════════════════════════════════════════════"
