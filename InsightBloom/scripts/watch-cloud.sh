#!/usr/bin/env bash
# =============================================================================
# watch-cloud.sh — Muestra la nube de palabras en tiempo real (modo live)
#
# Uso:
#   ./scripts/watch-cloud.sh --conference-id <uuid>
#   ./scripts/watch-cloud.sh --conference-id <uuid> --interval 2 --type doubt
#   ./scripts/watch-cloud.sh --conference-id <uuid> --type topic --interval 4
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DISPLAY_PY="${SCRIPT_DIR}/cloud-display.py"

QUERY_URL="${QUERY_URL:-http://localhost:8083}"
STATS_URL="${STATS_URL:-http://localhost:8085}"
INTERVAL="${INTERVAL:-3}"
TYPE="${TYPE:-doubt}"
CONFERENCE_ID=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --conference-id) CONFERENCE_ID="$2"; shift 2 ;;
    --interval)      INTERVAL="$2"; shift 2 ;;
    --type)          TYPE="$2"; shift 2 ;;
    *) echo "Argumento desconocido: $1" >&2; exit 1 ;;
  esac
done

[[ -z "$CONFERENCE_ID" ]] && { echo "ERROR: --conference-id requerido" >&2; exit 1; }

STATS_TYPE=$(echo "$TYPE" | tr '[:lower:]' '[:upper:]')
CLOUD_EP="${QUERY_URL}/api/v1/conferences/${CONFERENCE_ID}/cloud/${TYPE}s"
STATS_EP="${STATS_URL}/api/v1/conferences/${CONFERENCE_ID}/stats/relevance?type=${STATS_TYPE}"
OVERVIEW_EP="${STATS_URL}/api/v1/conferences/${CONFERENCE_ID}/stats"

clear
trap 'tput cnorm; echo ""; echo "Watch detenido."' EXIT INT TERM
tput civis  # ocultar cursor

ITER=0
while true; do
  ITER=$(( ITER + 1 ))
  TIMESTAMP=$(date +"%H:%M:%S")

  # ─── Leer datos ─────────────────────────────────────────────────────────────
  CLOUD=$(curl -sf "$CLOUD_EP" 2>/dev/null || echo '{"data":[]}')
  STATS=$(curl -sf "$STATS_EP" 2>/dev/null || echo '{"data":[]}')
  OVERVIEW=$(curl -sf "$OVERVIEW_EP" 2>/dev/null || echo '{}')

  # ─── Totales rápidos ────────────────────────────────────────────────────────
  TOTAL=$(echo "$STATS" | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data',[])
print(sum(int(x.get('countTotal',0)) for x in d))
" 2>/dev/null || echo "0")

  UNIQUE=$(echo "$STATS" | python3 -c "
import sys,json
print(len(json.load(sys.stdin).get('data',[])))
" 2>/dev/null || echo "0")

  CENSORED=$(echo "$STATS" | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data',[])
print(sum(int(x.get('countCensored',0)) for x in d))
" 2>/dev/null || echo "0")

  # ─── Redibujar pantalla ──────────────────────────────────────────────────────
  tput cup 0 0

  printf '━%.0s' {1..68}; echo ""
  printf "  \033[1mInsightBloom LIVE\033[0m [%s]  Mensajes: %-6s  Palabras: %-4s  Censurados: %s\n" \
    "$TIMESTAMP" "$TOTAL" "$UNIQUE" "$CENSORED"
  printf "  Conf: %s   Tipo: %s   iter #%d\n" "${CONFERENCE_ID:0:36}" "$TYPE" "$ITER"
  printf '━%.0s' {1..68}; echo ""

  echo ""
  echo "  [stats service — scores reales]"
  echo ""
  echo "$STATS" | python3 "$DISPLAY_PY" \
    --field-word wordNormalized \
    --field-score relevanceScore \
    --field-count countTotal \
    --top 18 --bars 30 2>/dev/null || echo "  (cargando...)"

  # Padding para limpiar líneas previas
  for _ in $(seq 1 3); do printf '%80s\n' ""; done

  printf '─%.0s' {1..68}; echo ""
  echo "  [query service — todas las palabras]"
  echo ""
  echo "$CLOUD" | python3 "$DISPLAY_PY" \
    --field-word wordNormalized \
    --field-score relevanceScore \
    --field-count messageCount \
    --top 8 --bars 20 2>/dev/null || echo "  (cargando...)"

  for _ in $(seq 1 3); do printf '%80s\n' ""; done

  printf '━%.0s' {1..68}; echo ""
  printf "  Ctrl+C para salir  |  Actualizando en %ss\n" "$INTERVAL"
  printf '━%.0s' {1..68}; echo ""

  sleep "$INTERVAL"
done
