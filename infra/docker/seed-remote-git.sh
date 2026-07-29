#!/bin/sh
# Clona el repositorio remoto configurado por el organizador (Conference.sandboxRemoteGitUrl,
# ver ConferenceConfigPage.vue) dentro del workspace del alumno, si el organizador definio uno.
# Tiene que correr ANTES que cualquier otro script de seed (seed-node-types.sh, seed-ide-docs.sh,
# .vscode/launch.json/tasks.json): "git clone" exige un directorio destino vacio o inexistente,
# asi que si corriera despues encontraria el workspace ya con archivos y jamas clonaria nada.
#
# No requiere privilegios (git clone es una operacion de usuario normal) ni root/sudo -- corre
# como el mismo usuario no-root que arranca el resto del entrypoint.

set -eu

WORKSPACE="${1:-${HOME}/workspace}"

if [ -z "${REMOTE_GIT_URL:-}" ]; then
    exit 0
fi

# Si el volumen ya tiene contenido (reinicio del mismo Pod, o el alumno ya escribio algo) no se
# clona encima: evita pisar trabajo en progreso y evita el error de "git clone" contra un
# directorio no vacio.
mkdir -p "$WORKSPACE"
if [ -n "$(ls -A "$WORKSPACE" 2>/dev/null)" ]; then
    echo "seed-remote-git: el workspace ya tiene contenido, se omite el clonado de $REMOTE_GIT_URL" >&2
    exit 0
fi

echo "seed-remote-git: clonando $REMOTE_GIT_URL en $WORKSPACE" >&2
# --depth 1: al alumno le interesa el estado actual del repo, no su historial completo; el
# workspace es efimero igual. Si el clonado falla (URL invalida, repo privado, red caida) no se
# aborta el arranque del IDE -- el alumno se queda con un workspace vacio, que es el
# comportamiento de siempre sin esta feature.
if ! git clone --depth 1 -- "$REMOTE_GIT_URL" "$WORKSPACE" 2>&1; then
    echo "seed-remote-git: fallo el clonado de $REMOTE_GIT_URL -- se continua con workspace vacio" >&2
fi
