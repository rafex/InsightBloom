#!/bin/sh
set -eu

# The musl archive is a static binary and works in both the Debian and Alpine
# IDE images. Keep the version and digest explicit so image rebuilds remain
# reproducible.
just_version=1.57.0
just_sha256=45b548094283cb9739af8f13273b8cddeee869f5b4ef2bb631b1f311cb566155
just_url="https://github.com/casey/just/releases/download/${just_version}/just-${just_version}-x86_64-unknown-linux-musl.tar.gz"

curl -fsSL "$just_url" -o /tmp/just.tar.gz
echo "$just_sha256  /tmp/just.tar.gz" | sha256sum -c -
tar -xzf /tmp/just.tar.gz -C /tmp just
install -m 0755 /tmp/just /usr/local/bin/just
rm -f /tmp/just.tar.gz /tmp/just

mkdir -p /usr/share/bash-completion/completions
just --completions bash > /usr/share/bash-completion/completions/just
