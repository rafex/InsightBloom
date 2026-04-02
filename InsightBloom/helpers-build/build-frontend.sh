#!/usr/bin/env bash
set -euo pipefail
echo "[build-frontend] Building frontend..."
npm --prefix apps/insightbloom-web run build
echo "[build-frontend] Done."
