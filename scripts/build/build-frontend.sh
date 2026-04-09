#!/usr/bin/env bash
set -euo pipefail
echo "[build-frontend] Building frontend..."
npm --prefix frontend/web run build
echo "[build-frontend] Done."
