# /etc/profile de Alpine resetea PATH incondicionalmente a un valor fijo
# ("/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin") ANTES de recorrer
# /etc/profile.d/*.sh -- descarta cualquier PATH seteado via ENV en el Dockerfile para shells de
# login (exactamente el tipo de shell que usa la terminal integrada, "bash -l" via socat).
# Confirmado en vivo (2026-07-17): "opencode: command not found" pese a que el binario existe y
# el ENV PATH del Dockerfile lo incluye -- se perdia al pasar por /etc/profile. Mismo problema
# ya afectaba en silencio a los paquetes globales de npm (NPM_CONFIG_PREFIX/.npm-global/bin).
export PATH="/home/coder/.opencode/bin:/home/coder/.npm-global/bin:$PATH"
