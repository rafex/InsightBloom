#!/bin/sh
set -eu

plugin_root="${1:-/usr/local/share/insightbloom/tmux/plugins}"
mkdir -p "$plugin_root"

clone_plugin() {
    name="$1"
    url="$2"
    rm -rf "${plugin_root:?}/$name"
    git clone --depth 1 "$url" "$plugin_root/$name"
}

clone_plugin tpm https://github.com/tmux-plugins/tpm
clone_plugin tmux-sensible https://github.com/tmux-plugins/tmux-sensible
clone_plugin tmux-resurrect https://github.com/tmux-plugins/tmux-resurrect
clone_plugin tmux-continuum https://github.com/tmux-plugins/tmux-continuum
clone_plugin vim-tmux-navigator https://github.com/christoomey/vim-tmux-navigator

chmod -R a+rX "$plugin_root"
