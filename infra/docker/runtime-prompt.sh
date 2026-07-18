# Prompt coloreado con rama de git, usado por las dos imagenes del IDE (Dockerfile.code-ide-debian
# y Dockerfile.code-ide-neovim). Va en /etc/profile.d/ (no en ~/.bashrc): tanto la terminal
# integrada de code-server como el "bash -lc" que arranca ttyd son shells de LOGIN, que no leen
# ~/.bashrc por defecto -- /etc/profile.d/*.sh es el unico lugar que /etc/profile siempre carga.

__insightbloom_git_branch() {
    local branch
    branch=$(git symbolic-ref --short HEAD 2>/dev/null) || \
        branch=$(git rev-parse --short HEAD 2>/dev/null) || return
    printf ' (%s)' "$branch"
}

# Verde = usuario@host, azul = directorio, amarillo = rama de git.
PS1='\[\e[0;32m\]\u@\h\[\e[0m\]:\[\e[0;34m\]\w\[\e[0;33m\]$(__insightbloom_git_branch)\[\e[0m\]\$ '
