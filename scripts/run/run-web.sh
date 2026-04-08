#!/usr/bin/env bash
set -euo pipefail
echo "[run-web] Starting frontend dev server..."
npm --prefix apps/insightbloom-web run dev
