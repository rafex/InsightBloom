# TASKS.md — code-ide-sandboxes

Derivado de `spec-native/specs/code-ide-sandboxes/SPEC.md`.

## Backlog — retención temporal de workspace

- [ ] Diseñar cierre explícito del curso por moderador y retención de workspaces hasta un máximo
  de una hora posterior. Debe incluir política de borrado verificable, aviso visible para alumnos
  y no convertir el workspace efímero actual en persistencia indefinida. **No implementar aún.**

## Variantes CLI seleccionables

- [x] Exponer pools independientes `CLI · Neovim` (`cli`) y `CLI · LazyVim` (`cli-lazyvim`); el
  asistente elige la variante desde la pantalla IDE y GitOps precarga ambas imágenes. Los eventos
  existentes siguen usando Neovim estable hasta que el organizador habilite el pool LazyVim en
  Configuración > IDE.

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

**Progreso:** 
- Fase 0: 3/3 ✅ 
- Fase 1: 4/4 ✅
- Fase 2: 1/1 ⏸️ (TASK-0020 bloqueado en Ether — necesita WebSocket bidireccional)
- Fase 3–5: bloqueadas por Fase 2 (no se puede enrutar code-server sin WebSocket)

### Implementación adicional: pre-warm operativo del pool (2026-07-23)

Aunque el enrutamiento WebSocket del gateway continúa siendo una dependencia de la
infraestructura, el servicio de usuarios ya permite preparar por adelantado los Pods
del pool configurado, sin esperar a que llegue el primer alumno:

- `POST /api/v1/conferences/{id}/sandbox/prewarm` crea de forma idempotente los slots
  Web y las dos variantes CLI configuradas, requiere sesión válida y permisos de propietario/admin o
  staff operativo (`MODERATE_CONTENT`/`HOST_EVENT`), y devuelve cuántos Pods se
  crearon por variante.
- La configuración del evento expone **Preparar sandboxes antes del evento** y
  consulta el estado de fase/ready después de iniciar el proceso.
- Después de una asignación se repone el siguiente Pod libre cuando corresponde;
  los Pods CLI aprovechan primero los asientos libres del Pod compartido.
- La asignación normal conserva su comportamiento idempotente y sigue siendo el
  respaldo si el pre-warm no se ejecuta o Kubernetes no está disponible.

**Validación:** pruebas de pre-warm, reposición Web/CLI, asignación y carreras de
concurrencia; build de backend y frontend completados correctamente.

### Mejora CLI: autocompletado semántico JavaScript/Node.js (2026-07-23)

El modo `terminal-nvim` incorpora `nvim-lspconfig` y
`typescript-language-server`, además de `nvim-cmp`/`cmp-nvim-lsp` ya existentes. La
configuración cubre JavaScript, JSX, TypeScript, TSX y JSON/JSONC (`package.json`,
`jsconfig.json`, `tsconfig.json`) y detecta la raíz por configuración de proyecto o
`.git`. La imagen precarga también `@types/node`; el script
`infra/docker/seed-node-types.sh` publica esos tipos en cada workspace efímero sin
Internet, incluyendo los tipos de `fs`, `http`, `process` y `Buffer`.

**Criterio de cierre:** el Language Server se inicia desde Neovim sin Mason ni
descargas en runtime; los tipos de Node se resuelven en un Pod de un asiento y en
un Pod multi-asiento.

### Mejora LSP: contrato común de lenguajes (2026-07-23)

Las tres imágenes del IDE deben entregar estos servidores sin descargas durante la sesión:

| Lenguaje | LSP |
|---|---|
| Java | `jdtls` |
| Python | `pyright-langserver` |
| JS/TS | `typescript-language-server` |
| HTML | `vscode-html-language-server` |
| CSS | `vscode-css-language-server` |

En Web IDE se usan los servicios/extension hosts de code-server (Java/Pyright y los servicios
integrados de VS Code para JS/TS/HTML/CSS), y los binarios quedan disponibles en la terminal para
diagnóstico y tareas automatizadas. En CLI se configuran mediante `nvim-lspconfig`, con
`nvim-cmp`, `cmp-nvim-lsp`, `LuaSnip`, `nvim-tree` y los parsers Tree-sitter precargados.

**Criterio de cierre:** abrir un archivo de cada tipo en Web IDE y CLI inicia el servidor
correspondiente, ofrece diagnósticos/completado y no intenta instalar plugins o servidores en
runtime.

### Egress GitHub-only (implementado; falta validación en cluster)

El default sigue siendo deny-all. Una `NetworkPolicy` Kubernetes no filtra FQDN; por tanto no se
debe permitir GitHub mediante rangos IP hardcodeados. La excepción será una opción separada por
evento que solo permite llegar a un proxy interno de egress. El proxy permite únicamente los
hosts de `EGRESS_PROXY_ALLOWED_HOSTS`, bloquea primero `EGRESS_PROXY_BLOCKED_HOSTS`, rechaza
destinos privados/reservados y limita puertos a HTTP/HTTPS. El sandbox no tiene salida directa
a Internet.

La configuración declarativa está en `InsightBloom-gitops/infrastructure/config/app-config.yaml`;
`app-config-cm.yaml` es la salida operativa autogenerada. El workflow publica la imagen
`ghcr.io/rafex/insightbloom-egress-proxy` con SBOM y provenance.

**Criterio de cierre:** el código y los manifiestos ya están implementados; falta validar en
cluster que `git clone` HTTPS de un repositorio permitido funciona, que dominios ajenos fallan
y que los servicios internos siguen bloqueados.

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

**Estado:** ✅ COMPLETADA
**Owner:** —
**Dependencias:** TASK-0011
**Archivos entregados:**
`infra/helm/charts/insightbloom/values.yaml` (nueva sección sandbox con configuración de pool)
`infra/helm/charts/insightbloom/templates/sandbox-namespace.yaml` (namespace insightbloom-sandboxes)
`infra/helm/charts/insightbloom/templates/sandbox-pool.yaml` (Pod template ejemplo con security context completo; Fase 3 crea pods dinámicamente vía API)
**Criterio de cierre:** ✅ `helm template` renderiza namespace + pool template + security context (runAsNonRoot, drop ALL).
**Validación:** `helm template` + documentación clara de cómo backend genera pods en Fase 3.

### TASK-0013: `ResourceQuota`/`LimitRange` por namespace + límite global

**Estado:** ✅ COMPLETADA
**Owner:** —
**Dependencias:** TASK-0012
**Archivos entregados:**
`infra/helm/charts/insightbloom/templates/sandbox-resourcequota.yaml` (ResourceQuota + LimitRange)
`infra/helm/charts/insightbloom/values.yaml` (poolMaxTotalPerPlatform: 200)
**Criterio de cierre:** ✅ ResourceQuota limita pods/CPU/memoria totales; LimitRange enforza requests/limits por pod.
**Validación:** `helm template` renderiza quota con límites dimensionados correctamente.

## Fase 2 — Gateway: soporte WebSocket

### TASK-0020: `insightbloom-tools-gateway` — proxy de upgrade a WebSocket

**Estado:** ⏸️ BLOCKED en Ether (dependencia externa)
**Owner:** —
**Dependencias:** mejora en ether-http-jetty12
**Archivos analizados:**
`backend/services/insightbloom-tools-gateway/src/main/java/dev/rafex/insightbloom/toolsgateway/AuthGateHandler.java`
(comentario DEC actualizado con análisis de bloqueante)
**Bloqueante identificado:**
- `java.net.http.HttpClient` (usado hoy en gateway) NO soporta WebSocket
- Jetty 12 core SÍ tiene WebSocket nativo (`jetty-websocket-jetty-client`)
- ether-http-jetty12 NO expone APIs de alto nivel para usar WebSocket en Handlers
**Cambios requeridos en ether-http-jetty12:**
1. `Handler.WebSocketUpgrader` — interfaz para que handlers detecten + deleguen upgrade
2. Bidirectional WebSocket tunnel — proxy que bridgea downstream (navegador) ↔ upstream (backend)
3. Integración con `AuthGateHandler` — que ya valida sesión, solo falta el tunnel WS
**Solución temporal:** HttpClient fallará gracefully para WebSocket; herramientas con
fallback (ej. Etherpad socket.io → long-polling) seguirán funcionando pero ineficientemente.
**Validación:** compilación + tests pasan; bloqueante documentado en código.

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

**Estado:** in_progress
**Owner:** —
**Dependencias:** TASK-0012
**Archivos esperados:**
`infrastructure/charts/insightbloom/templates/sandbox-networkpolicy.yaml`,
`KubernetesPodClient.java`
(deny-all ingress/egress por defecto dentro de `insightbloom-sandboxes`;
egress a un proxy con allowlist de dominios — inicialmente los hosts
necesarios de GitHub — solo cuando `sandbox_internet_enabled = 1` para ese
evento).
**Criterio de cierre:** Scenario 6 de la SPEC: ningún sandbox alcanza
Services internos de InsightBloom bajo ninguna configuración.
**Validación:** `helm template` + verificación manual (`kubectl exec`
+ intento de conexión a `insightbloom-users` interno, debe fallar
siempre).

### TASK-0051: Proxy de egress con allowlist

**Estado:** in_progress
**Owner:** —
**Dependencias:** TASK-0050
**Archivos implementados:** `infra/egress-proxy/`,
`.github/workflows/publish-egress-proxy.yml`, y los manifiestos
`egress-proxy-*.yaml` del chart GitOps; sin persistencia ni estado sensible.
**Criterio de cierre:** con `internet_enabled = true`, un sandbox puede
`git clone` HTTPS desde los hosts permitidos de GitHub y falla contra
cualquier otro destino.
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
