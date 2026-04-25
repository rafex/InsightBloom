#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLI_JAR="${ROOT_DIR}/backend/cli/insightbloom-cli/target/insightbloom-cli-0.1.0-SNAPSHOT.jar"

NAMESPACE="${NAMESPACE:-default}"
RELEASE="${RELEASE:-insightbloom}"
DB_PATH="${DB_PATH:-/data/users.db}"
ROLE="ORGANIZER"
USERNAME=""
PASSWORD=""
DISPLAY_NAME=""
EMAIL=""
KUBECTL_CONTEXT="${KUBECTL_CONTEXT:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace)    NAMESPACE="$2"; shift 2 ;;
    --release)      RELEASE="$2"; shift 2 ;;
    --db-path)      DB_PATH="$2"; shift 2 ;;
    --username)     USERNAME="$2"; shift 2 ;;
    --password)     PASSWORD="$2"; shift 2 ;;
    --role)         ROLE="${2^^}"; shift 2 ;;
    --display-name) DISPLAY_NAME="$2"; shift 2 ;;
    --email)        EMAIL="$2"; shift 2 ;;
    --context)      KUBECTL_CONTEXT="$2"; shift 2 ;;
    --help|-h)
      cat <<'EOF'
Uso:
  scripts/run/k3s-create-user.sh --username <u> --password <p> [opciones]

Opciones:
  --namespace <ns>      Namespace de Kubernetes (default: default)
  --release <name>      Helm release name (default: insightbloom)
  --db-path <ruta>      Ruta de users.db en el pod (default: /data/users.db)
  --role <rol>          ORGANIZER | MODERATOR (default: ORGANIZER)
  --display-name <txt>  Nombre visible
  --email <txt>         Email opcional
  --context <ctx>       Contexto de kubectl (opcional)
EOF
      exit 0
      ;;
    *)
      echo "Argumento desconocido: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "${USERNAME}" || -z "${PASSWORD}" ]]; then
  echo "--username y --password son requeridos" >&2
  exit 1
fi

if [[ "${ROLE}" != "ORGANIZER" && "${ROLE}" != "MODERATOR" ]]; then
  echo "--role debe ser ORGANIZER o MODERATOR" >&2
  exit 1
fi

KUBECTL=(kubectl)
if [[ -n "${KUBECTL_CONTEXT}" ]]; then
  KUBECTL+=(--context "${KUBECTL_CONTEXT}")
fi

if [[ ! -f "${CLI_JAR}" ]]; then
  echo "[k3s-create-user] CLI no encontrado, compilando..."
  "${ROOT_DIR}/mvnw" -f "${ROOT_DIR}/backend/cli/insightbloom-cli/pom.xml" clean package -DskipTests
fi

POD="$("${KUBECTL[@]}" -n "${NAMESPACE}" get pods \
  -l "app.kubernetes.io/instance=${RELEASE},app.kubernetes.io/component=users" \
  -o jsonpath='{range .items[*]}{.metadata.name}{"|"}{.status.phase}{"\n"}{end}' \
  | awk -F'|' '$2=="Running"{print $1; exit}')"

if [[ -z "${POD}" ]]; then
  echo "No se encontró un pod users en Running para release=${RELEASE} namespace=${NAMESPACE}" >&2
  exit 1
fi

REMOTE_JAR="/tmp/insightbloom-cli.jar"
echo "[k3s-create-user] Copiando CLI a ${POD}:${REMOTE_JAR}..."
"${KUBECTL[@]}" -n "${NAMESPACE}" cp "${CLI_JAR}" "${POD}:${REMOTE_JAR}"

CMD=(
  java -jar "${REMOTE_JAR}" create-user
  --db "${DB_PATH}"
  --username "${USERNAME}"
  --password "${PASSWORD}"
  --role "${ROLE}"
)

if [[ -n "${DISPLAY_NAME}" ]]; then
  CMD+=(--display-name "${DISPLAY_NAME}")
fi
if [[ -n "${EMAIL}" ]]; then
  CMD+=(--email "${EMAIL}")
fi

echo "[k3s-create-user] Ejecutando create-user en pod ${POD}..."
"${KUBECTL[@]}" -n "${NAMESPACE}" exec "${POD}" -- "${CMD[@]}"
echo "[k3s-create-user] OK"
