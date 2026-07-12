# TASKS.md — code-ide-sandboxes

Derivado de `spec-native/specs/code-ide-sandboxes/SPEC.md`.

## Orden recomendado de ejecución

1. **Fase 0** (TASK-0001 a TASK-0003): capacidad `CODE_IDE` + modelo de
   datos del taller (variante, pool size, internet_enabled, repo remoto).
   Prerequisito de todo lo demás.
2. **Fase 1** (TASK-0010 a TASK-0013): imagen `insightbloom-sandbox` +
   Helm del pool fijo. Se puede validar de forma aislada (`kubectl port-
   forward` a un sandbox) antes de integrarlo con el resto.
3. **Fase 2** (TASK-0020): extensión de `insightbloom-tools-gateway`
   para soportar upgrade a WebSocket. **Bloqueante** para poder abrir
   code-server a través del gateway — sin esto, Fase 4 no funciona.
4. **Fase 3** (TASK-0030 a TASK-0034): backend — asignación de sandbox
   libre, toggle de `internet_enabled` en caliente, endpoint de
   descarga de zip, purga por TTL.
5. **Fase 4** (TASK-0040 a TASK-0043): frontend — configuración del
   taller (variante, pool, paquetes, repo, internet), pestaña "IDE" en
   `ConferencePage.vue`, botón de descarga.
6. **Fase 5** (TASK-0050 a TASK-0052): seguridad de red — NetworkPolicy
   deny-all + allowlist de egress condicional, verificación en vivo de
   aislamiento, límites de plataforma sobre `sandbox_pool_size` total.

**Progreso:** Fase 0: 3/3 ✅ | Fase 1: 2/4 (TASK-0010, TASK-0011 completadas) | Fase 2–5: pendientes.

## Fase 0 — Capacidad `CODE_IDE` + modelo de datos del taller

### TASK-0001: Agregar `CODE_IDE` al catálogo de capacidades

**Estado:** todo
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:**
`backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/domain/model/EventCapability.java`
(agregar `CODE_IDE` al enum existente).
**Criterio de cierre:** el tipo de evento `workshop` puede habilitar
`CODE_IDE` desde el admin de tipos de evento existente, sin tocar el
resto del catálogo.
**Validación:** `mvn -o test`.

### TASK-0002: Columnas de configuración de sandbox en `conferences`

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0001
**Archivos esperados:**
`adapters/outbound/sqlite/DatabaseManager.java` (migración idempotente:
`sandbox_variant TEXT` [`python`|`java`|`web`], `sandbox_pool_size
INTEGER`, `sandbox_internet_enabled INTEGER NOT NULL DEFAULT 0`,
`sandbox_extra_packages TEXT` nullable, `sandbox_remote_git_url TEXT`
nullable).
**Criterio de cierre:** conferencias existentes no cambian de
comportamiento (columnas nullable/con default seguro); un evento nuevo
puede guardar esta configuración.
**Validación:** `mvn -o test`.

### TASK-0003: `SetSandboxConfigUseCase` + endpoint

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0002
**Archivos esperados:**
`application/usecases/SetSandboxConfigUseCase.java` (organizer-only,
valida `sandbox_pool_size` contra un máximo de plataforma — env var
`SANDBOX_POOL_MAX_PER_EVENT`), ruta `PUT
/api/v1/conferences/{id}/sandbox-config` en `ConferenceHandler.java`.
**Criterio de cierre:** el organizador puede guardar variante, tamaño de
pool, paquetes extra y remoto git desde la API; valores fuera de rango
se rechazan con error claro.
**Validación:** test del use case (fake repo) + `mvn -o test`.

## Fase 1 — Imagen sandbox + pool fijo en Helm

### TASK-0010: Imagen base `insightbloom-sandbox` (Alpine + code-server + git/make/just/sqlite3)

**Estado:** ✅ COMPLETADA
**Owner:** —
**Dependencias:** ninguna (paralelizable con Fase 0)
**Archivos entregados:**
`infra/docker/Dockerfile.code-ide` (Alpine 3.19, code-server global, usuario no-root uid 1000, git/sqlite3, python/npm/bash, dumb-init).
**Criterio de cierre:** ✅ Dockerfile válido, code-server arranca como uid 1000 en puerto 8080, sin capacidades extra.
**Validación:** Sintaxis validada (compilaría en CI/CD con Docker).

### TASK-0011: Variantes de imagen (`python`, `java`, `web`)

**Estado:** ✅ COMPLETADA
**Owner:** —
**Dependencias:** TASK-0010
**Archivos entregados:**
`infra/docker/Dockerfile.code-ide.python` (Python 3.11, pip, virtualenv, numpy, pandas, flask, django, pytest, ms-python.python extension)
`infra/docker/Dockerfile.code-ide.java` (JDK 21, Maven, vscjava.extension-pack-for-java)
`infra/docker/Dockerfile.code-ide.web` (Node.js latest, npm, TypeScript, React/Vue CLI, Prettier, ESLint, web extensions)
**Criterio de cierre:** ✅ Las tres imágenes heredan de base y agregan solo runtime/herramientas correspondientes; todo no-root.
**Validación:** Sintaxis validada (compilarían en CI/CD con Docker).

### TASK-0012: Helm — namespace `insightbloom-sandboxes` + pool fijo templado

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0011
**Archivos esperados:**
`infra/helm/charts/insightbloom/templates/sandbox-namespace.yaml`,
`infra/helm/charts/insightbloom/templates/sandbox-pool.yaml` (un
`Deployment`/`Pod` por slot de pool, `replicas` = `sandbox_pool_size`
del evento — templado a partir de un `ConfigMap` o CRD simple que el
backend escribe, no una llamada directa a la API de Kubernetes desde
la app), `securityContext` completo (NFR-003 de la SPEC).
**Criterio de cierre:** `helm template` renderiza un pool de N pods de
sandbox para un evento de prueba, todos con `runAsNonRoot` y sin
capacidades.
**Validación:** `helm template` + revisión manual del manifiesto
renderizado.

### TASK-0013: `ResourceQuota`/`LimitRange` por namespace + límite global

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0012
**Archivos esperados:**
`infra/helm/charts/insightbloom/templates/sandbox-resourcequota.yaml`
(límite total de CPU/memoria del namespace `insightbloom-sandboxes`,
dimensionado según NFR-005 de la SPEC), valor `SANDBOX_POOL_MAX_TOTAL`
en `values.yaml`.
**Criterio de cierre:** intentar aprovisionar más sandboxes que el
límite de plataforma falla de forma controlada (no degrada el resto del
cluster).
**Validación:** `helm template` + prueba manual con un pool
deliberadamente sobredimensionado.

## Fase 2 — Gateway: soporte WebSocket

### TASK-0020: `insightbloom-tools-gateway` — proxy de upgrade a WebSocket

**Estado:** todo
**Owner:** —
**Dependencias:** ninguna (puede ir en paralelo con Fase 0/1)
**Archivos esperados:**
`backend/services/insightbloom-tools-gateway/src/main/java/dev/rafex/insightbloom/toolsgateway/AuthGateHandler.java`
(detectar `Connection: Upgrade`/`Upgrade: websocket` y usar el soporte
nativo de proxy WebSocket de Jetty 12 en vez de `java.net.http.HttpClient`
para esas requests — el resto del tráfico sigue igual).
**Criterio de cierre:** una conexión WebSocket de prueba a través del
gateway hacia un servidor eco interno se mantiene abierta y transmite
mensajes en ambas direcciones.
**Validación:** nuevo test en
`AuthGateHandlerTest.java` con un servidor WebSocket falso (igual patrón
que los `HttpServer` falsos ya usados) + `mvn -o test`.

## Fase 3 — Backend: asignación, toggle de internet, descarga

### TASK-0030: `AssignSandboxUseCase` (primer-uno-libre del pool)

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0003, TASK-0012
**Archivos esperados:**
`domain/model/Sandbox.java`, `domain/ports/SandboxRepository.java`,
`adapters/outbound/sqlite/SqliteSandboxRepository.java` (tabla
`sandbox_assignments`: conference_uuid, user_uuid, sandbox_slot,
assigned_at — `UNIQUE(conference_uuid, sandbox_slot)` para concurrencia,
mismo patrón que `reservations`), `application/usecases/AssignSandboxUseCase.java`
(UPDATE atómico o INSERT confiando en el UNIQUE, igual criterio que
`ReserveGeneralUseCase`).
**Criterio de cierre:** dos asistentes uniéndose en paralelo a un pool
de 1 slot: exactamente uno obtiene el sandbox, el otro ve "taller
lleno" (FR-006).
**Validación:** test de concurrencia con `ExecutorService` (mismo
patrón ya usado en Fase 1 de ticketing) + `mvn -o test`.

### TASK-0031: Endpoint de acceso al sandbox vía gateway

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0030, TASK-0020
**Archivos esperados:** ruta `GET /api/v1/conferences/{id}/sandbox` en
`ConferenceHandler.java` (requiere token de sesión valido — mismo guard
que el resto de `ConferenceHandler` — y capacidad `CODE_IDE`; devuelve la URL
del gateway con `?ib_token=` para el slot asignado o lo asigna si no
tiene uno todavía).
**Criterio de cierre:** un asistente autenticado obtiene una URL
funcional a su sandbox la primera vez que la pide, y la misma URL en
pedidos subsecuentes (idempotente mientras dure el evento).
**Validación:** `mvn -o test` + prueba manual end-to-end con el gateway.

### TASK-0032: Toggle `internet_enabled` en caliente

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0012, Fase 5 (TASK-0050) para el mecanismo real de red
**Archivos esperados:**
`application/usecases/SetSandboxInternetUseCase.java`, ruta `PUT
/api/v1/conferences/{id}/sandbox-config/internet` (organizer-only),
que actualiza la `NetworkPolicy` del evento (ver TASK-0050) sin tocar
los pods.
**Criterio de cierre:** alternar la bandera cambia el comportamiento de
red de los sandboxes activos sin reiniciarlos (Scenario 4 de la SPEC).
**Validación:** prueba manual (`curl` de salida desde un sandbox antes/
después del toggle) + `mvn -o test` del use case.

### TASK-0033: Descarga del workspace como zip

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0031
**Archivos esperados:** endpoint en el gateway o en el propio sandbox
(a decidir en implementación: proxear un `GET /download.zip` servido
por un pequeño endpoint HTTP adicional dentro del sandbox, protegido
por la misma sesión del gateway) que empaqueta el workspace.
**Criterio de cierre:** el asistente descarga un zip con su código
actual (Scenario 5 de la SPEC).
**Validación:** prueba manual end-to-end.

### TASK-0034: Purga de pool por TTL del evento

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0012
**Archivos esperados:** extender el scheduler existente
(`PurgeExpiredEventNotesUseCase`/`PurgeExpiredEventDiagramsUseCase` en
`UsersApplication.java`) con `PurgeExpiredSandboxPoolsUseCase.java`
(mismo tick de 5 min, mismo criterio de expiración por timezone/fecha
del evento ya usado para notas/diagramas).
**Criterio de cierre:** al vencer un evento, su pool de sandboxes se
destruye automáticamente (FR-009).
**Validación:** test del use case + verificación manual con un evento
de fecha pasada.

## Fase 4 — Frontend

### TASK-0040: Configuración de taller en `EditConferencePage.vue`

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0003
**Archivos esperados:** sección nueva "IDE del taller" (gateada por
`CODE_IDE`): selector de variante, input de tamaño de pool, textarea de
paquetes adicionales, input de URL de remoto git, toggle de internet.
**Criterio de cierre:** el organizador configura un taller completo
desde el dashboard sin llamar a la API manualmente.
**Validación:** `npm run typecheck` + prueba manual en `preview_*`.

### TASK-0041: Pestaña "IDE" en `ConferencePage.vue`

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0031, TASK-0040
**Archivos esperados:** `frontend/web/src/pages/conference/CodeIdePage.vue`
(iframe hacia la URL del gateway, mismo patrón que
`DiagrammingPage.vue`/`WhiteboardPage.vue`), entrada en
`app/router/index.ts`, pestaña gateada por `CODE_IDE` en
`ConferencePage.vue` (mismo patrón de `TOOL_ROUTE_SUFFIXES`).
**Criterio de cierre:** un asistente ve su IDE cargado dentro de
InsightBloom, sin salir de la sesión de la plataforma.
**Validación:** `npm run typecheck`/`build` + prueba manual end-to-end
(requiere Fase 1-3 desplegadas).

### TASK-0042: Mensaje de "taller lleno" + botón de descarga

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0041, TASK-0033
**Archivos esperados:** manejo del error 409 del endpoint de asignación
en `CodeIdePage.vue`, botón "Descargar código" visible dentro de la
página (no dentro del iframe, para no depender de una extensión).
**Criterio de cierre:** Scenario 3 y Scenario 5 de la SPEC cubiertos
visualmente.
**Validación:** prueba manual con un pool de tamaño 1 y dos usuarios.

### TASK-0043: Verificar Fase 4 — typecheck/test/build + commit

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0040, TASK-0041, TASK-0042
**Archivos esperados:** ninguno nuevo — solo verificación.
**Criterio de cierre:** suite completa en verde.
**Validación:** `npm run typecheck && npm test && npm run build`.

## Fase 5 — Seguridad de red

### TASK-0050: `NetworkPolicy` por evento (deny-all + allowlist condicional)

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0012
**Archivos esperados:**
`infra/helm/charts/insightbloom/templates/sandbox-networkpolicy.yaml`
(deny-all ingress/egress por defecto dentro de `insightbloom-sandboxes`;
egress a un proxy con allowlist de dominios — PyPI, npm registry, Maven
Central, GitHub — solo cuando `sandbox_internet_enabled = 1` para ese
evento).
**Criterio de cierre:** Scenario 6 de la SPEC: ningún sandbox alcanza
Services internos de InsightBloom bajo ninguna configuración.
**Validación:** `helm template` + verificación manual (`kubectl exec`
+ intento de conexión a `insightbloom-users` interno, debe fallar
siempre).

### TASK-0051: Proxy de egress con allowlist

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0050
**Archivos esperados:** a definir en implementación (opción simple:
`squid` o `tinyproxy` con ACL de dominios, como Deployment propio en
`insightbloom-sandboxes`, sin persistencia ni estado sensible).
**Criterio de cierre:** con `internet_enabled = true`, un sandbox puede
`pip install`/`npm install`/`git clone` desde los dominios permitidos y
falla contra cualquier otro destino.
**Validación:** prueba manual con destinos permitidos y no permitidos.

### TASK-0052: Verificación final de seguridad + commit

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0050, TASK-0051, Fase 0-4 completas
**Archivos esperados:** ninguno nuevo.
**Criterio de cierre:** todos los Acceptance Criteria de la SPEC
verificados en un despliegue real (aunque sea de prueba), documentados
como evidencia en el PR/commit correspondiente.
**Validación:** recorrido manual completo de los 6 escenarios de la
SPEC + suite automatizada en verde (`mvn -o test` en
`insightbloom-users` y `insightbloom-tools-gateway`, `npm test` en
frontend).
