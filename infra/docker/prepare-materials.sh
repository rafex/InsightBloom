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
  write_status "skipped" "No hay preparador activado para este workspace"
  exit 0
fi

export INSIGHTBLOOM_WORKSPACE="$workspace"
export INSIGHTBLOOM_MATERIALS_ROOT="${INSIGHTBLOOM_MATERIALS_ROOT:-/opt/insightbloom/materials}"
export INSIGHTBLOOM_MATERIAL_CACHE_URL="${INSIGHTBLOOM_MATERIAL_CACHE_URL:-}"
export INSIGHTBLOOM_MATERIAL_REVISION="${INSIGHTBLOOM_MATERIAL_REVISION:-}"
material_copy="${INSIGHTBLOOM_MATERIAL_COPY_BIN:-/usr/local/bin/insightbloom-material-copy}"

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
      [ -n "$INSIGHTBLOOM_MATERIAL_REVISION" ] || return 69
      script_dir="$(mktemp -d "$state_dir/bootstrap-source.XXXXXX")" || return 1
      trap 'rm -f "$script_dir/$(basename "${INSIGHTBLOOM_BOOTSTRAP_PATH:-bootstrap}")"; rmdir "$script_dir" 2>/dev/null || true' EXIT
      "$material_copy" "$script" "$script_dir" || return $?
      script="$script_dir/$(basename "${INSIGHTBLOOM_BOOTSTRAP_PATH:-bootstrap}")"
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
else
  code=$?
fi
write_status "failed" "La preparación falló (código $code); el IDE sigue disponible."
cat > "$workspace/README-INSIGHTBLOOM-BOOTSTRAP.md" <<'EOF'
# Preparación de materiales incompleta

El IDE está disponible, pero la preparación configurada por el evento falló.
Revisá `.insightbloom/bootstrap.log` y `.insightbloom/bootstrap-status.json`.
EOF
exit 0
