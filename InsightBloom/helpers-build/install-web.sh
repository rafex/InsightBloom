#!/usr/bin/env bash
set -euo pipefail
echo "[install-web] Installing frontend dependencies..."
npm --prefix apps/insightbloom-web install
echo "[install-web] Done."
