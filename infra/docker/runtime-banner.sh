# Banner de bienvenida del contenedor runtime del IDE, mostrado solo en shells interactivas
# (nunca en `bash -c "..."` ni scripts no interactivos, ver el chequeo de "$-" abajo) -- ver
# infra/docker/Dockerfile.code-ide-runtime y runtime-prompt.sh (mismo mecanismo /etc/profile.d).
case "$-" in
    *i*) ;;
    *) return ;;
esac

__insightbloom_version_file=/etc/insightbloom-runtime-version

printf '\e[0;36m'
printf -- '--- InsightBloom Sandbox Runtime ---\n'
printf '\e[0m'
if [ -r "$__insightbloom_version_file" ]; then
    cat "$__insightbloom_version_file"
fi
printf 'Java:   %s\n' "$(java --version 2>&1 | head -1)"
printf 'Node:   %s\n' "$(node --version 2>&1)"
printf 'Python: %s\n' "$(python3 --version 2>&1)"
printf '\e[0;36m'
printf -- '-------------------------------------\n'
printf '\e[0m'

unset __insightbloom_version_file
