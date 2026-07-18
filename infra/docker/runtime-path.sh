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
# los directorios bin que el Dockerfile agrega via ENV (Temurin, Node, npm-global, opencode), no
# solo npm-global/opencode -- y se re-declara JAVA_HOME explicitamente porque tambien se pierde.
export JAVA_HOME="/usr/local/lib/jvm/temurin-25"
export PATH="${JAVA_HOME}/bin:/usr/local/lib/node-24/bin:/home/coder/.opencode/bin:/home/coder/.npm-global/bin:$PATH"
