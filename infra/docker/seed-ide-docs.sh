#!/bin/sh
set -eu

workspace="${1:?workspace requerido}"
mkdir -p "$workspace/.insightbloom"
cp /usr/local/share/doc/insightbloom/IDE-WEB-PUBLICATION.md "$workspace/.insightbloom/IDE-WEB-PUBLICATION.md"
