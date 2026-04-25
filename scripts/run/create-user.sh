#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLI_JAR="${ROOT_DIR}/backend/cli/insightbloom-cli/target/insightbloom-cli-0.1.0-SNAPSHOT.jar"

ARGS=("$@")

# Permite invocación via Just con separador: just create-user -- --username ...
while [[ ${#ARGS[@]} -gt 0 && "${ARGS[0]}" == "--" ]]; do
  ARGS=("${ARGS[@]:1}")
done

exec java -jar "${CLI_JAR}" create-user "${ARGS[@]}"
