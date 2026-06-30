#!/usr/bin/env bash
# fix-orphaned-memberships.sh
#
# Elimina de la SQLite del users service los registros de membresía y conferencia
# que quedaron huérfanos (conference_memberships apuntan a una conferencia que ya
# no existe en la tabla conferences).
#
# Uso:
#   ./infra/scripts/fix-orphaned-memberships.sh [--namespace mvps] [--kubeconfig ~/.kube/config_k3s] [--dry-run]
#
# Requiere: kubectl, sqlite3 (local)

set -euo pipefail

NAMESPACE="mvps"
KUBECONFIG_PATH="${KUBECONFIG:-$HOME/.kube/config_k3s}"
DRY_RUN=false
TMP_DB=$(mktemp /tmp/users-db-XXXXXX.db)

cleanup() { rm -f "$TMP_DB"; }
trap cleanup EXIT

# --- Argumentos ---
while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace|-n)   NAMESPACE="$2"; shift 2 ;;
    --kubeconfig)     KUBECONFIG_PATH="$2"; shift 2 ;;
    --dry-run)        DRY_RUN=true; shift ;;
    *) echo "Argumento desconocido: $1"; exit 1 ;;
  esac
done

export KUBECONFIG="$KUBECONFIG_PATH"

# --- Dependencias ---
for cmd in kubectl sqlite3; do
  command -v "$cmd" &>/dev/null || { echo "ERROR: '$cmd' no está en PATH"; exit 1; }
done

# --- Localizar el pod ---
POD=$(kubectl get pods -n "$NAMESPACE" \
  -l app.kubernetes.io/name=insightbloom-users \
  -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)

if [[ -z "$POD" ]]; then
  echo "ERROR: No se encontró ningún pod con label app.kubernetes.io/name=insightbloom-users en namespace '$NAMESPACE'"
  exit 1
fi

echo "Pod encontrado: $POD (namespace: $NAMESPACE)"

# --- Copiar DB ---
echo "Copiando /data/users.db desde el pod..."
kubectl cp "$NAMESPACE/$POD:/data/users.db" "$TMP_DB"

# --- Inspeccionar huérfanos ---
ORPHAN_MEMBERSHIPS=$(sqlite3 "$TMP_DB" \
  "SELECT count(*) FROM conference_memberships m
   WHERE NOT EXISTS (SELECT 1 FROM conferences c WHERE c.uuid = m.conference_uuid);")

ORPHAN_CONFERENCES=$(sqlite3 "$TMP_DB" \
  "SELECT count(*) FROM conferences c
   WHERE NOT EXISTS (SELECT 1 FROM conference_memberships m WHERE m.conference_uuid = c.uuid)
   AND c.status = 'DELETED';")

echo ""
echo "=== Diagnóstico ==="
sqlite3 "$TMP_DB" \
  "SELECT 'Conferencias totales:', count(*) FROM conferences
   UNION ALL
   SELECT 'Memberships totales:', count(*) FROM conference_memberships
   UNION ALL
   SELECT 'Memberships huérfanas (conferencia inexistente):', $ORPHAN_MEMBERSHIPS;"

echo ""
echo "Detalle de memberships huérfanas:"
sqlite3 -column -header "$TMP_DB" \
  "SELECT m.user_uuid, m.conference_uuid, m.conference_name_snapshot, m.joined_at
   FROM conference_memberships m
   WHERE NOT EXISTS (SELECT 1 FROM conferences c WHERE c.uuid = m.conference_uuid)
   ORDER BY m.joined_at;" || true

if [[ "$ORPHAN_MEMBERSHIPS" -eq 0 ]]; then
  echo ""
  echo "No hay registros huérfanos. No se requiere acción."
  exit 0
fi

echo ""
if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY RUN] Se eliminarían $ORPHAN_MEMBERSHIPS membership(s) huérfana(s). Saliendo sin cambios."
  exit 0
fi

read -rp "¿Eliminar $ORPHAN_MEMBERSHIPS membership(s) huérfana(s)? [s/N] " CONFIRM
if [[ "${CONFIRM,,}" != "s" ]]; then
  echo "Operación cancelada."
  exit 0
fi

# --- Limpiar ---
sqlite3 "$TMP_DB" \
  "DELETE FROM conference_memberships
   WHERE NOT EXISTS (SELECT 1 FROM conferences c WHERE c.uuid = conference_uuid);"

REMAINING=$(sqlite3 "$TMP_DB" "SELECT count(*) FROM conference_memberships;")
echo "Limpieza completada. Memberships restantes: $REMAINING"

# --- Devolver DB al pod ---
echo "Copiando DB limpia de vuelta al pod..."
kubectl cp "$TMP_DB" "$NAMESPACE/$POD:/data/users.db"

# --- Reiniciar deployment ---
echo "Reiniciando deployment/insightbloom-users..."
kubectl rollout restart deployment/insightbloom-users -n "$NAMESPACE"
kubectl rollout status deployment/insightbloom-users -n "$NAMESPACE" --timeout=60s

echo ""
echo "Listo. Las memberships huérfanas fueron eliminadas y el servicio está corriendo."
