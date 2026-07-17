# Atajos para debugging remoto desde el editor (contenedor "ide") hacia procesos que corren aca
# (contenedor "runtime"). Ambos contenedores comparten namespace de red del Pod, asi que
# "localhost:<puerto>" desde "ide" llega directo a estos listeners sin configuracion adicional.
# El launch.json ya viene sembrado con "Adjuntar a Java (runtime, puerto 5005)" y "Adjuntar a
# Python (runtime, puerto 5678)" (ver code-ide-entrypoint.sh) -- solo hace falta arrancar el
# programa con uno de estos wrappers y despues correr "Run and Debug" -> Adjuntar, en el editor.
#
# No se instalo Java/Python en el contenedor "ide" a proposito (ver DECISIONS.md, DEC-0023):
# el editor NO necesita el interprete local para *depurar* via el protocolo de adjuntar remoto
# (JDWP para Java, debugpy para Python) -- solo IntelliSense de paquetes instalados pierde
# precision sin un interprete local, el debugging visual completo si funciona igual.

javadebug() {
    if [ -z "${1:-}" ]; then
        echo "uso: javadebug <ClasePrincipal> [args...]" >&2
        return 1
    fi
    local class="$1"; shift
    java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 "$class" "$@"
}

pydebug() {
    if [ -z "${1:-}" ]; then
        echo "uso: pydebug <script.py> [args...]" >&2
        return 1
    fi
    python3 -m debugpy --listen 0.0.0.0:5678 --wait-for-client "$@"
}
