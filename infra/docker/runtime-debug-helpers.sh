# Atajos para debugging remoto (adjuntar) desde el editor hacia un proceso propio, dentro del
# MISMO contenedor -- desde el cambio de paradigma 2026-07-17 (imagenes autocontenidas, sin
# split ide/runtime) ya no hay dos contenedores compartiendo namespace de red; "localhost" aca
# es simplemente el propio contenedor. Se mantiene el flujo de adjuntar-remoto (en vez de
# lanzar el debugger directo desde el editor) porque sigue siendo la forma mas simple de
# depurar un programa que el alumno ya arranco a mano desde la terminal.
# El launch.json ya viene sembrado con "Adjuntar a Java (puerto 5005)" y "Adjuntar a Python
# (puerto 5678)" (ver code-ide-entrypoint.sh) -- solo hace falta arrancar el programa con uno
# de estos wrappers y despues correr "Run and Debug" -> Adjuntar, en el editor.
#
# Pods "neovim" multi-asiento (DEC-0025): $SEAT_INDEX lo inyecta el seat-agent en el ambiente
# de cada ttyd (ver sandbox-agent.py:_spawn_seat) -- puerto real = puerto base + SEAT_INDEX,
# para que cada alumno tenga su propio puerto y no choquen entre si (todos los asientos de un
# Pod comparten el mismo namespace de red, loopback incluido). Sin $SEAT_INDEX (imagen debian,
# siempre 1 alumno por Pod, o imagen neovim de un solo asiento) el comportamiento es identico
# al de siempre: puerto 5005/5678 fijo, coincide con lo que ya viene sembrado en launch.json.
javadebug() {
    if [ -z "${1:-}" ]; then
        echo "uso: javadebug <ClasePrincipal> [args...]" >&2
        return 1
    fi
    local class="$1"; shift
    local port=$((5005 + ${SEAT_INDEX:-0}))
    # address=localhost (no "*"): JDWP no tiene autenticacion -- quien se conecte ejecuta
    # bytecode arbitrario en esta JVM. Bindear a todas las interfaces exponia esto a
    # cualquier otro Pod del mismo namespace (sin NetworkPolicy de Ingress que lo evite),
    # es decir a cualquier otro alumno. Loopback sigue siendo compartido ENTRE asientos del
    # mismo Pod (mismo namespace de red) -- ver nota de archivo arriba, es un limite aceptado,
    # no resuelto por el puerto distinto (que solo evita choques, no aisla completamente).
    echo "javadebug: escuchando en localhost:$port (adjuntar ahi desde el editor)" >&2
    java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=localhost:$port "$class" "$@"
}

pydebug() {
    if [ -z "${1:-}" ]; then
        echo "uso: pydebug <script.py> [args...]" >&2
        return 1
    fi
    local port=$((5678 + ${SEAT_INDEX:-0}))
    # 127.0.0.1 (no 0.0.0.0): mismo motivo que javadebug -- debugpy sin auth expone
    # ejecucion de codigo arbitrario a quien se conecte.
    echo "pydebug: escuchando en 127.0.0.1:$port (adjuntar ahi desde el editor)" >&2
    python3 -m debugpy --listen 127.0.0.1:$port --wait-for-client "$@"
}
