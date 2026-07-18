# Atajos para debugging remoto (adjuntar) desde el editor hacia un proceso propio, dentro del
# MISMO contenedor -- desde el cambio de paradigma 2026-07-17 (imagenes autocontenidas, sin
# split ide/runtime) ya no hay dos contenedores compartiendo namespace de red; "localhost" aca
# es simplemente el propio contenedor. Se mantiene el flujo de adjuntar-remoto (en vez de
# lanzar el debugger directo desde el editor) porque sigue siendo la forma mas simple de
# depurar un programa que el alumno ya arranco a mano desde la terminal.
# El launch.json ya viene sembrado con "Adjuntar a Java (puerto 5005)" y "Adjuntar a Python
# (puerto 5678)" (ver code-ide-entrypoint.sh) -- solo hace falta arrancar el programa con uno
# de estos wrappers y despues correr "Run and Debug" -> Adjuntar, en el editor.

javadebug() {
    if [ -z "${1:-}" ]; then
        echo "uso: javadebug <ClasePrincipal> [args...]" >&2
        return 1
    fi
    local class="$1"; shift
    # address=localhost (no "*"): JDWP no tiene autenticacion -- quien se conecte ejecuta
    # bytecode arbitrario en esta JVM. Bindear a todas las interfaces exponia esto a
    # cualquier otro Pod del mismo namespace (sin NetworkPolicy de Ingress que lo evite),
    # es decir a cualquier otro alumno.
    java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=localhost:5005 "$class" "$@"
}

pydebug() {
    if [ -z "${1:-}" ]; then
        echo "uso: pydebug <script.py> [args...]" >&2
        return 1
    fi
    # 127.0.0.1 (no 0.0.0.0): mismo motivo que javadebug -- debugpy sin auth expone
    # ejecucion de codigo arbitrario a quien se conecte.
    python3 -m debugpy --listen 127.0.0.1:5678 --wait-for-client "$@"
}
