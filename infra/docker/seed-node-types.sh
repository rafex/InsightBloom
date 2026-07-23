#!/bin/sh
# Hace visibles los tipos de Node.js dentro del workspace del alumno.
# El workspace es un volumen efimero y oculta cualquier archivo creado durante
# el build; este script solo crea enlaces hacia la copia precargada en la imagen.

set -eu

WORKSPACE="${1:-${HOME}/workspace}"
SOURCE="/usr/local/share/insightbloom-node-types/node_modules"

if [ ! -d "$SOURCE" ]; then
    echo "seed-node-types: no existe la copia precargada: $SOURCE" >&2
    exit 1
fi

mkdir -p "$WORKSPACE/node_modules"

# Publica @types/node y sus dependencias, sin pisar paquetes del proyecto.
for source_entry in "$SOURCE"/* "$SOURCE"/@*/*; do
    [ -e "$source_entry" ] || [ -L "$source_entry" ] || continue
    entry_name="${source_entry##*/}"
    case "$source_entry" in
        "$SOURCE"/@*/*)
            scope_name="${source_entry#"$SOURCE"/}"
            scope_name="${scope_name%%/*}"
            mkdir -p "$WORKSPACE/node_modules/$scope_name"
            destination="$WORKSPACE/node_modules/$scope_name/$entry_name"
            ;;
        *)
            destination="$WORKSPACE/node_modules/$entry_name"
            ;;
    esac
    if [ ! -e "$destination" ] && [ ! -L "$destination" ]; then
        ln -s "$source_entry" "$destination"
    fi
done
