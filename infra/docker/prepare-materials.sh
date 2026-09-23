#!/bin/sh
# Ejecuta la preparación declarada por el propietario antes de exponer el IDE.
# El material montado es siempre de solo lectura; el script corre como el usuario
# del alumno y solo puede modificar su workspace.
set -u

workspace="${1:-${INSIGHTBLOOM_WORKSPACE:-${HOME}/workspace}}"
state_dir="$workspace/.insightbloom"
status_file="$state_dir/bootstrap-status.json"
log_file="$state_dir/bootstrap.log"
mkdir -p "$state_dir"
: > "$log_file"

write_status() {
  result="$1"; message="$2"
  python3 - "$status_file" "$result" "$message" <<'PY'
import json, os, sys, time
path, result, message = sys.argv[1:]
with open(path, "w", encoding="utf-8") as out:
    json.dump({"status": result, "message": message,
               "revision": os.getenv("INSIGHTBLOOM_MATERIAL_REVISION", ""),
               "source": os.getenv("INSIGHTBLOOM_MATERIAL_SOURCE", ""),
               "finishedAt": int(time.time())}, out)
    out.write("\n")
PY
}

# Sin configuración no hay trabajo que hacer. Conservamos el contrato actual de
# workspaces vacíos y de REMOTE_GIT_URL.
if [ -z "${INSIGHTBLOOM_BOOTSTRAP_KIND:-}" ]; then
  write_status "skipped" "No hay preparación de materiales configurada"
  exit 0
fi

export INSIGHTBLOOM_WORKSPACE="$workspace"
export INSIGHTBLOOM_MATERIALS_ROOT="${INSIGHTBLOOM_MATERIALS_ROOT:-/opt/insightbloom/materials}"
if [ -n "${INSIGHTBLOOM_MATERIAL_SOURCE:-}" ] && [ -f "$INSIGHTBLOOM_MATERIALS_ROOT/$INSIGHTBLOOM_MATERIAL_SOURCE/.insightbloom-material-revision" ]; then
  INSIGHTBLOOM_MATERIAL_REVISION="$(cat "$INSIGHTBLOOM_MATERIALS_ROOT/$INSIGHTBLOOM_MATERIAL_SOURCE/.insightbloom-material-revision")"
  export INSIGHTBLOOM_MATERIAL_REVISION
fi

run_bootstrap() {
  case "${INSIGHTBLOOM_BOOTSTRAP_SOURCE:-}" in
    inline)
      [ -n "${INSIGHTBLOOM_BOOTSTRAP_INLINE:-}" ] || return 64
      case "$INSIGHTBLOOM_BOOTSTRAP_KIND" in
        shell) printf '%s\n' "$INSIGHTBLOOM_BOOTSTRAP_INLINE" | sh ;;
        python) printf '%s\n' "$INSIGHTBLOOM_BOOTSTRAP_INLINE" | python3 - ;;
        *) return 64 ;;
      esac
      ;;
    material)
      script="${INSIGHTBLOOM_MATERIALS_ROOT}/${INSIGHTBLOOM_MATERIAL_SOURCE}/${INSIGHTBLOOM_BOOTSTRAP_PATH:-}"
      case "$script" in "$INSIGHTBLOOM_MATERIALS_ROOT"/*) ;; *) return 64 ;; esac
      [ -f "$script" ] || return 66
      case "$INSIGHTBLOOM_BOOTSTRAP_KIND" in
        shell) sh "$script" ;;
        python) python3 "$script" ;;
        *) return 64 ;;
      esac
      ;;
    *) return 64 ;;
  esac
}

if run_bootstrap >>"$log_file" 2>&1; then
  write_status "ready" "Preparación de materiales completada"
  exit 0
fi

code=$?
write_status "failed" "La preparación falló (código $code); el IDE sigue disponible."
cat > "$workspace/README-INSIGHTBLOOM-BOOTSTRAP.md" <<'EOF'
# Preparación de materiales incompleta

El IDE está disponible, pero la preparación configurada por el evento falló.
Revisá `.insightbloom/bootstrap.log` y `.insightbloom/bootstrap-status.json`.
EOF
exit 0
