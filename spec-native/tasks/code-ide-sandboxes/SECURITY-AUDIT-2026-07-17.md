# SECURITY-AUDIT-2026-07-17.md — code-ide-sandboxes

Auditoría de seguridad + mejoras de diseño del IDE de code-server/runtime,
pedida en modo auditor el 2026-07-17. Hallazgos verificados con pruebas en
vivo contra el cluster real (no son hipótesis) — ver cada tarea para el
comando de reproducción usado.

## Orden recomendado de ejecución

1. **Críticos** (TASK-SEC-01 a TASK-SEC-03): fixes acotados, bajo riesgo de
   romper nada, cierran accesos no autenticados entre sandboxes de distintos
   alumnos. Ejecutar primero y todos juntos.
2. **Alto** (TASK-SEC-04): valida `Origin` en el gateway — cierra CSWSH/CSRF
   reabierto por el fix de `SameSite=None` de la sesión anterior.
3. **Medio** (TASK-SEC-05, TASK-SEC-06): supply chain de extensiones y
   superficie de `opencode` instalada por defecto — no bloqueantes, se
   documentan como deuda si no se resuelven ahora.
4. **Diseño** (TASK-SEC-07 a TASK-SEC-09): mejoras de defensa en profundidad,
   no vulnerabilidades puntuales — quedan documentadas para revisitar.

## Críticos

### TASK-SEC-01: NetworkPolicy Ingress para namespace de sandboxes

**Estado:** done
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:**
`backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/outbound/kubernetes/KubernetesPodClient.java`
(agregar creación de una `NetworkPolicy` de tipo `Ingress` para el
namespace de sandboxes, o reforzar la política base existente en el
cluster: `sandbox-default-deny-egress`).

**Contexto:** confirmado en vivo — pod A en `insightbloom-sandboxes`
alcanza el Service de pod B (mismo namespace) sin ningún token:
```bash
# pod "víctima": python3 -m http.server 8080, expuesto como Service
# pod "atacante", mismo namespace:
curl http://victim-sim-svc.insightbloom-sandboxes.svc.cluster.local:8080/
# -> http_code=200
```
Causa: `sandbox-default-deny-egress` permite egress a **cualquier pod del
mismo namespace** sin restricción de puerto, y no existe ninguna
`NetworkPolicy` de tipo `Ingress` — el tráfico entrante no está
restringido en absoluto. `code-server` corre con `--auth none` y `ttyd`
con `-W` sin credencial: la única autenticación real vive en
`insightbloom-tools-gateway`, que el tráfico pod-a-pod nunca atraviesa.

**Criterio de cierre:** el mismo comando de reproducción de arriba
devuelve conexión rechazada/timeout entre dos sandboxes de distinta
conferencia (o, como mínimo, de distinto pod). El flujo legítimo
(gateway → Service del sandbox) sigue funcionando sin cambios.

**Validación:** repetir la prueba en vivo (pod atacante + pod víctima en
el mismo namespace) y confirmar bloqueo; smoke test de abrir el IDE real
vía el gateway para confirmar que el acceso legítimo no se rompió.

### TASK-SEC-02: JDWP bindeado a loopback, no a todas las interfaces

**Estado:** done
**Owner:** —
**Dependencias:** ninguna (independiente de TASK-SEC-01, pero
complementaria: cierra el vector de RCE específico aunque TASK-SEC-01
falle o se retrase)
**Archivos esperados:**
`infra/docker/runtime-debug-helpers.sh` (función `javadebug`).

**Contexto:** `java -agentlib:jdwp=...,address=*:5005` bindea a **todas
las interfaces**, no solo loopback. JDWP no tiene autenticación por
diseño — cualquiera que se conecte ejecuta bytecode arbitrario en esa
JVM (técnica pública conocida, "jdwp-shellifier"). El propio comentario
del script ya documenta que `ide` y `runtime` comparten namespace de red
del Pod, así que `localhost` alcanza perfecto para el caso de uso real
(adjuntar el debugger desde el editor) — bindear a `*` fue innecesario.

**Criterio de cierre:** `address=localhost:5005` en vez de `address=*:5005`.
El flujo de "Adjuntar a Java (runtime, puerto 5005)" desde el editor
sigue funcionando sin cambios (mismo Pod, mismo namespace de red).

**Validación:** `javadebug` sigue aceptando la conexión del `launch.json`
sembrado; `curl`/`nc` a `<pod-ip>:5005` desde OTRO pod del mismo
namespace falla (antes daba 200/conectaba).

### TASK-SEC-03: debugpy bindeado a loopback, no a todas las interfaces

**Estado:** done
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:**
`infra/docker/runtime-debug-helpers.sh` (función `pydebug`).

**Contexto:** mismo problema que TASK-SEC-02 pero para Python:
`--listen 0.0.0.0:5678` expone attach-and-execute sin auth a cualquier
pod del mismo namespace.

**Criterio de cierre:** `--listen 127.0.0.1:5678` en vez de
`0.0.0.0:5678`. El flujo de "Adjuntar a Python (runtime, puerto 5678)"
sigue funcionando sin cambios.

**Validación:** igual que TASK-SEC-02, aplicado a debugpy.

## Alto

### TASK-SEC-04: Validar `Origin` en insightbloom-tools-gateway

**Estado:** done
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:**
`backend/services/insightbloom-tools-gateway/src/main/java/dev/rafex/insightbloom/toolsgateway/AuthGateHandler.java`,
`backend/services/insightbloom-tools-gateway/src/main/java/dev/rafex/insightbloom/toolsgateway/WebSocketProxyCreator.java`.

**Contexto:** la cookie de sesión `ib_gw` se emite con `SameSite=None`
(cambio de esta misma sesión, necesario para el extension host
sandboxeado de code-server — ver DEC-0022). Verificado: no hay
validación de `Origin`/`Referer` en ningún punto del gateway. Con
`SameSite=None` y sin chequeo de `Origin`, un sitio malicioso puede
abrir un WebSocket hacia `ide-insightbloom.v1.rafex.cloud` desde el
navegador de la víctima (la cookie se adjunta automáticamente, WS no
respeta CORS/SOP) y operar su terminal/IDE en tiempo real — Cross-Site
WebSocket Hijacking — además de CSRF clásico sobre cualquier endpoint
HTTP alcanzable via el gateway.

**Criterio de cierre:** requests (HTTP y upgrade a WebSocket) con header
`Origin` ausente o que no matchee la allowlist de hosts esperados
(`*.rafex.cloud`, o el/los host(s) configurados) se rechazan antes de
usar la cookie de sesión. No revierte `SameSite=None` (sigue siendo
necesario para el iframe del extension host).

**Validación:** request con `Origin: https://evil.example` y cookie
válida → rechazado. Request sin header `Origin` desde un navegador real
(same-site) → sigue funcionando (algunos navegadores no mandan `Origin`
en navegaciones top-level GET, cuidado con no romper el flujo normal de
abrir la URL del IDE directamente).

## Medio (documentar como deuda si no se resuelve ahora)

### TASK-SEC-05: Pin de versiones de extensiones de code-server

**Estado:** done
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:** `infra/docker/Dockerfile.code-ide-server`.

**Contexto:** las ~16 extensiones se instalan sin `@version` — cada
rebuild de CI toma la última versión publicada en open-vsx.org de cada
una. Un publisher comprometido podría inyectar código malicioso que
termina baked-in en el próximo build sin revisión.

**Criterio de cierre:** cada extensión fijada a una versión explícita
(`publisher.nombre@X.Y.Z`), actualizada deliberadamente vía PR en vez de
automáticamente en cada build.

### TASK-SEC-06: `opencode` condicional a `internet_enabled`

**Estado:** done
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:** `infra/docker/Dockerfile.code-ide-runtime` (o
moverlo a instalación en runtime del pod, condicionada por env var, en
vez de build time incondicional).

**Contexto:** ~174MB de binario + superficie de un agente de IA con
tool-use, instalado en el 100% de los sandboxes aunque `internet_enabled`
sea `false` (caso en el que no puede hacer nada útil). Superficie
instalada innecesaria en el caso común.

**Criterio de cierre:** decisión explícita — o se acepta el costo fijo
(documentar por qué) o se condiciona la instalación/activación a
`internet_enabled=true` por conferencia.

## Diseño (defensa en profundidad, no vulnerabilidades puntuales)

### TASK-SEC-07: Namespace por conferencia (o filtro por label en NetworkPolicy)

**Estado:** done
**Owner:** —
**Dependencias:** TASK-SEC-01 (complementaria, no bloqueante)

**Contexto:** la raíz de TASK-SEC-01 es que *todos* los sandboxes de
*todas* las conferencias activas viven en el mismo namespace
`insightbloom-sandboxes`. Aunque se arregle con NetworkPolicy de
Ingress, un namespace por conferencia (o al menos filtrar también por
label `sandbox-conference` además de namespace) da defensa en
profundidad real y limita el blast radius a la propia cohorte en vez de
a toda la plataforma.

**Criterio de cierre:** decisión documentada en DECISIONS.md sobre si se
adopta namespace-por-conferencia o filtro por label; si se adopta,
tareas de implementación separadas.

### TASK-SEC-08: Timeout de inactividad en `ttyd` (modo terminal-nvim)

**Estado:** done
**Owner:** —
**Dependencias:** ninguna

**Contexto:** una sesión de `ttyd` queda abierta indefinidamente si el
alumno no cierra la pestaña. Sumado a TASK-SEC-01 (mientras no esté
resuelto), una sesión abandonada y alcanzable es una ventana de ataque
más larga.

**Criterio de cierre:** `ttyd` configurado con `--ping-interval` y algún
mecanismo de timeout de inactividad razonable para un taller (ej. 30-60
min sin actividad).

### TASK-SEC-09: Rate limiting en validación de token del gateway

**Estado:** done
**Owner:** —
**Dependencias:** ninguna

**Contexto:** no hay throttling en `resolveSandboxTarget`/`isTokenValid`
del gateway para intentos de token inválido repetidos.

**Criterio de cierre:** límite razonable de intentos fallidos por IP/
ventana de tiempo antes de rechazar temporalmente, sin afectar el uso
legítimo (reconexiones normales del navegador).
