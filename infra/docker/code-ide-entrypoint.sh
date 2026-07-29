#!/bin/bash
# Punto de entrada de la imagen "debian" (code-server) del IDE. /home/coder/workspace es un
# volumen emptyDir montado por Kubernetes (ver KubernetesPodClient.java) -- lo que se copia ahi
# en build time del Dockerfile queda tapado/invisible en runtime, asi que sembrar el launch.json
# de debug remoto tiene que pasar aca, al arrancar el contenedor, no en el Dockerfile.
set -euo pipefail

# Clona el repositorio remoto del organizador (Conference.sandboxRemoteGitUrl), si configuro
# uno, ANTES de cualquier otro seed -- "git clone" exige un directorio destino vacio, asi que
# tiene que correr primero (ver seed-remote-git.sh).
/usr/local/bin/seed-remote-git.sh /home/coder/workspace

# El workspace es un emptyDir y oculta los archivos de build. Publicamos los tipos de Node.js
# precargados antes de iniciar code-server para que el TypeScript language service pueda
# resolver fs/http/process/Buffer sin npm ni Internet durante la sesión.
/usr/local/bin/seed-node-types.sh /home/coder/workspace
/usr/local/bin/seed-ide-docs.sh /home/coder/workspace

if [ ! -f /home/coder/workspace/.vscode/launch.json ]; then
    mkdir -p /home/coder/workspace/.vscode
    cp /etc/insightbloom/code-ide-launch.json /home/coder/workspace/.vscode/launch.json
fi

# tasks.json con "runOn": "folderOpen" -- fuerza que la Terminal se enfoque/muestre al abrir el
# workspace. El layout de paneles/vistas (que panel queda visible al arrancar) es estado de
# sesion de VS Code (workspaceStorage/state.vscdb), no una clave de settings.json, y ese estado
# tambien se pierde entre sesiones (mismo emptyDir efimero) -- por eso no alcanza con settings.json
# solo (ver code-ide-settings.json: "workbench.panel.defaultLocation") y hace falta esta tarea
# para reclamar el foco del panel inferior de forma confiable en cada sesion (reportado
# 2026-07-28: se abria un panel lateral que no era la terminal y tapaba espacio de la vista).
if [ ! -f /home/coder/workspace/.vscode/tasks.json ]; then
    mkdir -p /home/coder/workspace/.vscode
    cp /etc/insightbloom/code-ide-tasks.json /home/coder/workspace/.vscode/tasks.json
fi

# Agente de archivos del workspace (Fase 4, dashboard de moderador) -- puerto de control 8079
# (basePort 8080 - 1, misma convencion que el seat-agent de la imagen neovim, ver
# KubernetesPodClient.controlPort()), en background: "exec code-server" de abajo REEMPLAZA este
# proceso, asi que tiene que quedar lanzado antes, no despues (una linea despues del exec nunca
# se ejecutaria). Al reemplazarse el proceso padre, este background queda re-parentado a
# dumb-init (PID 1, ENTRYPOINT de la imagen), que reapea sus zombies igual que los de cualquier
# huerfano -- no hace falta nada especial de su lado.
python3 /usr/local/bin/sandbox-file-agent.py --control-port 8079 &

# --locale es: UI en español (unico idioma de nuestro publico). --disable-telemetry
# --disable-update-check: ver postmortem 2026-07-17 en DECISIONS.md, DEC-0023.
exec code-server --bind-addr 0.0.0.0:8080 --auth none \
    --disable-telemetry --disable-update-check --locale es \
    /home/coder/workspace
