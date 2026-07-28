# /etc/profile (Alpine y Debian) resetea PATH a un valor fijo del sistema ANTES de recorrer
# /etc/profile.d/*.sh -- descarta cualquier PATH/JAVA_HOME seteado via ENV en el Dockerfile para
# shells de login (code-server invoca la terminal integrada como "bash -l"; ttyd tambien arranca
# "bash -lc"). Confirmado en vivo (2026-07-17, imagen Alpine vieja): "opencode: command not
# found" pese a que el binario existe y el ENV PATH del Dockerfile lo incluye -- se perdia al
# pasar por /etc/profile.
#
# Confirmado en vivo otra vez (2026-07-17, cambio de paradigma, imagen Debian nueva): el mismo
# reseteo se comia TAMBIEN el JAVA_HOME/PATH de Temurin y el PATH de Node -- "node: command not
# found", y "java --version" caia silenciosamente al OpenJDK 17 que trae Debian como dependencia
# transitiva del paquete apt "maven" (no a un error), lo cual es peor: sin este fix el alumno
# nunca ve Java 25, ve una version distinta sin ningun aviso. Por eso ahora se reexportan TODOS
# los directorios bin que el Dockerfile agrega via ENV (Temurin, Node, Python, npm-global,
# opencode), no solo npm-global/opencode -- y se re-declara JAVA_HOME explicitamente porque
# tambien se pierde. Python (python-3.12/bin) se agrego 2026-07-28: aunque /usr/local/bin/python3
# es un symlink que sobrevive el reseteo, pip/uv/etc. instalan OTROS scripts directo en
# python-3.12/bin sin symlinkear, y la extension de VS Code reportaba no encontrar el interprete
# al resolverlo por su path real (mismo bug ya documentado para este mismo directorio en
# Dockerfile.code-ide-neovim, nunca portado a esta imagen).
export JAVA_HOME="/usr/local/lib/jvm/temurin-25"
export PATH="${JAVA_HOME}/bin:/usr/local/lib/node-24/bin:/usr/local/lib/python-3.12/bin:/home/coder/.opencode/bin:/home/coder/.npm-global/bin:$PATH"
