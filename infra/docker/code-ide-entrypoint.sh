#!/bin/bash
# Punto de entrada de la imagen "debian" (code-server) del IDE. /home/coder/workspace es un
# volumen emptyDir montado por Kubernetes (ver KubernetesPodClient.java) -- lo que se copia ahi
# en build time del Dockerfile queda tapado/invisible en runtime, asi que sembrar el launch.json
# de debug remoto tiene que pasar aca, al arrancar el contenedor, no en el Dockerfile.
set -euo pipefail

if [ ! -f /home/coder/workspace/.vscode/launch.json ]; then
    mkdir -p /home/coder/workspace/.vscode
    cp /etc/insightbloom/code-ide-launch.json /home/coder/workspace/.vscode/launch.json
fi

# --locale es: UI en español (unico idioma de nuestro publico). --disable-telemetry
# --disable-update-check: ver postmortem 2026-07-17 en DECISIONS.md, DEC-0023.
exec code-server --bind-addr 0.0.0.0:8080 --auth none \
    --disable-telemetry --disable-update-check --locale es \
    /home/coder/workspace
