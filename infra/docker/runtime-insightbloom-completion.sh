#!/usr/bin/env bash
# Load command completions in interactive bash login shells.
# Debian and Alpine expose bash-completion at different profile stages, so this
# bridge makes the InsightBloom completion deterministic in both images.
if [ -z "${BASH_VERSION:-}" ]; then
    return 0
fi

case "$-" in
    *i*) ;;
    *) return 0 ;;
esac

if ! declare -F _init_completion >/dev/null 2>&1; then
    for completion_loader in \
        /usr/share/bash-completion/bash_completion \
        /etc/bash_completion; do
        if [ -r "$completion_loader" ]; then
            # shellcheck disable=SC1090
            . "$completion_loader"
            break
        fi
    done
fi

# shellcheck source=/dev/null
if [ -r /usr/share/bash-completion/completions/insightbloom ]; then
    . /usr/share/bash-completion/completions/insightbloom
fi
