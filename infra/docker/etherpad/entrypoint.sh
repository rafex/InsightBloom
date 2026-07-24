#!/bin/sh
set -eu

# Etherpad's generic development/startup helpers can run dependency installers.
# This image is intentionally self-contained: the InsightBloom plugin and all of
# Etherpad's dependencies are installed at build time, so a reboot must not need
# npm, pnpm, or external network access to start.
ETHERPAD_HOME=/opt/etherpad-lite
PLUGIN_NAME=ep_insightbloom_hide_delete
PLUGIN_DIR="${ETHERPAD_HOME}/local_plugins/${PLUGIN_NAME}"
INSTALLED_PLUGIN_DIR="${ETHERPAD_HOME}/node_modules/${PLUGIN_NAME}"

if [ ! -f "${PLUGIN_DIR}/ep.json" ] || [ ! -f "${PLUGIN_DIR}/index.js" ]; then
  echo "FATAL: embedded Etherpad plugin ${PLUGIN_NAME} is missing from the image" >&2
  exit 1
fi

if [ ! -f "${INSTALLED_PLUGIN_DIR}/ep.json" ]; then
  echo "FATAL: Etherpad plugin ${PLUGIN_NAME} is not installed in the image" >&2
  exit 1
fi

cd "${ETHERPAD_HOME}"

# The default command is deliberately the production process, not bin/run.sh:
# bin/run.sh calls bin/installDeps.sh and can reach npm during a restart.
if [ "$#" -eq 0 ]; then
  set -- pnpm run prod
fi

exec "$@"
