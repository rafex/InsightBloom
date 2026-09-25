#!/bin/sh
# Hace visible la copia inmutable de @types/node sin ensuciar workspaces normales.
# TypeScript busca node_modules/@types en directorios ancestros del workspace, por lo
# que /home/node_modules sirve a Web y CLI (incluido /home/<asiento>/workspace).

set -eu

SOURCE="${INSIGHTBLOOM_NODE_TYPES_SOURCE:-/usr/local/share/insightbloom-node-types/node_modules}"
MODE="${1:-}"

if [ "$MODE" = "--global" ]; then
    TARGET="${2:-/home/node_modules}"
elif [ "$MODE" = "--workspace" ]; then
    WORKSPACE="${2:-}"
    if [ -z "$WORKSPACE" ] || [ ! -d "$WORKSPACE" ]; then
        echo "uso: seed-node-types.sh --workspace RUTA | --global [RUTA]" >&2
        exit 2
    fi

    # Retira únicamente los enlaces creados por este seeder. Los paquetes reales y cualquier
    # otro contenido de node_modules pertenecen al proyecto y nunca se borran.
    cleanup_managed_link() {
        destination="$1"
        expected_target="$2"
        if [ -L "$destination" ] && [ "$(readlink "$destination")" = "$expected_target" ]; then
            rm "$destination"
        fi
    }

    workspace_modules="$WORKSPACE/node_modules"
    if [ -d "$workspace_modules" ]; then
        cleanup_managed_link "$workspace_modules/@types" "$SOURCE/@types"
        cleanup_managed_link "$workspace_modules/undici-types" "$SOURCE/undici-types"
        rmdir "$workspace_modules" 2>/dev/null || true
    fi

    # Evalúa JSONC, extends y rutas relativas con una API fijada en la imagen. El TypeScript
    # visible para los alumnos puede cambiar de layout y no debe controlar este fallback.
    if node - "$WORKSPACE" "${INSIGHTBLOOM_TYPESCRIPT_API:-/opt/insightbloom/tsconfig-api/node_modules/typescript/lib/typescript.js}" <<'NODE'
const fs = require('node:fs');
const path = require('node:path');
const workspace = path.resolve(process.argv[2]);
let ts;
try {
  ts = require(process.argv[3]);
} catch (error) {
  console.error(`seed-node-types: no se pudo cargar la API TypeScript; no se crean enlaces locales: ${error.message}`);
  process.exit(2);
}

const configs = ['tsconfig.json', 'jsconfig.json']
  .map((name) => path.join(workspace, name))
  .filter((file) => fs.existsSync(file));
if (configs.length === 0) process.exit(1);

let fallback = false;
for (const configPath of configs) {
  let fatalDiagnostic = false;
  let parsed;
  try {
    parsed = ts.getParsedCommandLineOfConfigFile(configPath, {}, {
      ...ts.sys,
      onUnRecoverableConfigFileDiagnostic: (diagnostic) => {
        fatalDiagnostic = true;
        console.error(ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'));
      },
    });
  } catch (error) {
    console.error(`seed-node-types: no se pudo analizar ${path.basename(configPath)}; no se crean enlaces locales: ${error.message}`);
    process.exit(2);
    continue;
  }
  const configErrors = parsed ? parsed.errors.filter((diagnostic) =>
    diagnostic.category === ts.DiagnosticCategory.Error && diagnostic.code !== 18003) : [];
  if (!parsed || fatalDiagnostic || configErrors.length > 0) {
    console.error(`seed-node-types: configuración TypeScript no resoluble; no se crean enlaces locales para ${path.basename(configPath)}`);
    process.exit(2);
  }
  const localTypes = path.resolve(workspace, 'node_modules/@types');
  const roots = parsed.options.typeRoots || [];
  if (roots.some((root) => path.resolve(path.dirname(configPath), root) === localTypes)) fallback = true;
}
process.exit(fallback ? 0 : 1);
NODE
    then
        :
    else
        status=$?
        case "$status" in
            1) exit 0 ;; # Configuración ausente o sin typeRoots local: /home/node_modules basta.
            2) exit 0 ;; # Error de evaluación: mantener el IDE disponible sin ensuciar el workspace.
            *) exit "$status" ;;
        esac
    fi
    TARGET="$WORKSPACE/node_modules"
else
    echo "uso: seed-node-types.sh --workspace RUTA | --global [RUTA]" >&2
    exit 2
fi

if [ ! -d "$SOURCE" ]; then
    echo "seed-node-types: no existe la copia precargada: $SOURCE" >&2
    exit 1
fi

mkdir -p "$TARGET"
if [ "$MODE" = "--global" ]; then
    if [ "$(id -u)" -eq 0 ]; then chown root:root "$TARGET"; fi
    chmod 0755 "$TARGET"
fi

# Publica paquetes y scopes sin pisar instalaciones del proyecto. En modo global, el
# directorio root-owned y no escribible impide que los alumnos reemplacen enlaces compartidos.
for source_entry in "$SOURCE"/* "$SOURCE"/@*/*; do
    [ -e "$source_entry" ] || [ -L "$source_entry" ] || continue
    entry_name="${source_entry##*/}"
    case "$source_entry" in
        "$SOURCE"/@*/*)
            scope_name="${source_entry#"$SOURCE"/}"
            scope_name="${scope_name%%/*}"
            mkdir -p "$TARGET/$scope_name"
            if [ "$MODE" = "--global" ]; then
                if [ "$(id -u)" -eq 0 ]; then chown root:root "$TARGET/$scope_name"; fi
                chmod 0755 "$TARGET/$scope_name"
            fi
            destination="$TARGET/$scope_name/$entry_name"
            ;;
        *) destination="$TARGET/$entry_name" ;;
    esac
    if [ ! -e "$destination" ] && [ ! -L "$destination" ]; then
        ln -s "$source_entry" "$destination"
    fi
done
