#!/usr/bin/env bash
set -euo pipefail
echo "[install-web] Installing frontend dependencies..."
npm --prefix frontend/web install
echo "[install-web] Done."
