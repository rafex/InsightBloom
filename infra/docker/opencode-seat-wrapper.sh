#!/bin/sh
set -eu

if [ "${XDG_DATA_HOME:-}" = "/opt/insightbloom/nvim-lazy/data" ]; then
    : "${HOME:?HOME must be set to a writable seat home}"
    export XDG_DATA_HOME="${HOME}/.local/share"
fi

exec /opt/insightbloom/.opencode/bin/opencode "$@"
