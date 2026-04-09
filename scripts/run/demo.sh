#!/usr/bin/env bash
set -euo pipefail
# Demo movido a scripts/sim/demo.sh
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/../sim/demo.sh" "$@"
