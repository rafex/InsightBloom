# Integracion de shell de fzf (Ctrl+R historial difuso, Ctrl+T buscar archivos, Alt+C cambiar
# de directorio) -- el paquete fzf de APT (imagen debian) y el de APK (imagen neovim/Alpine)
# instalan el binario pero NINGUNO de los dos engancha estos atajos solos, hay que sourcear los
# scripts a mano (confirmado en vivo 2026-07-18: "fzf --version" funcionaba pero Ctrl+R seguia
# siendo el reverse-i-search de bash de siempre). Mismo mecanismo /etc/profile.d que
# runtime-banner.sh -- ver ese archivo para el porque de shells interactivas solamente.
case "$-" in
    *i*) ;;
    *) return ;;
esac

# Alpine (apk): un solo entrypoint que sourcea completion+key-bindings.
# Debian (apt): key-bindings.bash suelto (completion ya la resuelve bash-completion via
# /usr/share/bash-completion/completions/fzf, sourceado por /etc/profile.d/bash_completion.sh).
if [ -r /usr/share/bash/plugins/fzf/fzf.plugin.sh ]; then
    source /usr/share/bash/plugins/fzf/fzf.plugin.sh
elif [ -r /usr/share/doc/fzf/examples/key-bindings.bash ]; then
    source /usr/share/doc/fzf/examples/key-bindings.bash
fi
