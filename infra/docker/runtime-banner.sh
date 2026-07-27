# Banner de bienvenida, usado por las dos imagenes del IDE, mostrado solo en shells
# interactivas (nunca en `bash -c "..."` ni scripts no interactivos, ver el chequeo de "$-"
# abajo) -- ver Dockerfile.code-ide-debian / Dockerfile.code-ide-neovim y runtime-prompt.sh
# (mismo mecanismo /etc/profile.d).
case "$-" in
    *i*) ;;
    *) return ;;
esac

__insightbloom_version_file=/etc/insightbloom-runtime-version
__insightbloom_image_file=/etc/insightbloom-image-name

# Un solo script para las dos imagenes (Dockerfile.code-ide-debian y Dockerfile.code-ide-neovim,
# ver runtime-prompt.sh) -- el nombre de imagen viene de un archivo bakeado en build time (cada
# Dockerfile escribe el suyo) porque el shell no tiene otra forma confiable de saber "en cual de
# las dos imagenes estoy" mas que detectar que binarios existen.
printf '\e[0;36m'
if [ -r "$__insightbloom_image_file" ]; then
    printf -- '--- InsightBloom Sandbox (%s) ---\n' "$(cat "$__insightbloom_image_file")"
else
    printf -- '--- InsightBloom Sandbox ---\n'
fi
printf '\e[0m'
if [ -r "$__insightbloom_version_file" ]; then
    cat "$__insightbloom_version_file"
fi
printf 'Java:   %s\n' "$(java --version 2>&1 | head -1)"
printf 'Node:   %s\n' "$(node --version 2>&1)"
printf 'Python: %s\n' "$(python3 --version 2>&1)"
# code-server (imagen debian) o nvim+lazygit (imagen neovim) -- cualquiera de los dos que
# exista en esta imagen, nunca los dos a la vez.
if command -v code-server >/dev/null 2>&1; then
    printf 'code-server: %s\n' "$(code-server --version 2>&1 | head -1)"
fi
if command -v nvim >/dev/null 2>&1; then
    printf 'Neovim: %s\n' "$(nvim --version 2>&1 | head -1)"
    # "lazygit --version" imprime "version=X" Y "git version=Y" en la misma linea -- no se
    # puede usar un grep/cut simple sin que matchee las dos, hay que anclar al campo exacto.
    printf 'lazygit: %s\n' "$(lazygit --version 2>&1 | sed -n 's/.*, version=\([^,]*\),.*/\1/p')"
fi
printf 'Corre "insightbloom --help" para publicar tu sitio o tu backend/API con una URL pública.\n'
printf '\e[0;36m'
printf -- '-------------------------------------\n'
printf '\e[0m'

unset __insightbloom_version_file __insightbloom_image_file
