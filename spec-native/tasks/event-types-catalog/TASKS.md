# TASKS.md — event-types-catalog

Derivado de `spec-native/specs/event-types-catalog/SPEC.md`.

## Orden recomendado de ejecución

Ver sección "Complexity Ranking & Execution Order" en SPEC.md para el análisis de complejidad.  
**Resumen:**

1. **Fase 0 + 1** (TASK-0001 a TASK-0012): Catálogo de EventType + wiring. Prerequisito de todo lo demás.
2. **drawio** (parte de Fase 3/4): TASK-0031 + TASK-0041. Demuestra capability gating.
3. **Etherpad** (parte de Fase 3/4): TASK-0030 + TASK-0040. Poco más que drawio.
4. **Jitsi público** (parte de Fase 3/4, solo `meet.jit.si`): **adelantarlo** — no esperar al self-hosted. TASK-0032 + TASK-0042 con solo la instancia pública, sin el Helm chart de Jitsi self-hosted.
5. **Excalidraw** (parte de Fase 3/4): TASK-0033 + TASK-0043. Colaboración en vivo, más fricción.
6. **SurveyJS** (Fase 5): TASK-0050 a TASK-0054. Encuestas alternativas, riesgo de licencia.
7. **seatmap-canvas** (Fase 6): TASK-0060 a TASK-0065. Alternativa a FREEFORM, mantenedor único.
8. **Jitsi self-hosted** (parte de Fase 3/4, JVB + NAT/TURN): **al final**. Riesgo de infraestructura más alto; `meet.jit.si` es funcional como fallback.

Con la ejecución de los pasos 1-4 tienes un tipo de evento "Taller remoto" completamente funcional (boletos GENERAL, videollamada pública, pizarra colaborativa, notas compartidas) sin tocar infraestructura operativamente riesgosa.

**Progreso:** 1 ✅ · 2 (drawio) ✅ · 3 (Etherpad) ✅ · 4 (Jitsi público) ✅ · 5-8 pendientes.

Nota: la capacidad `CODE_IDE` (IDE web + ejecucion de codigo del taller)
esta fuera de esta iniciativa. No crear tareas para ella hasta que el
usuario defina las reglas de seguridad de ejecucion; en ese momento se abre
una iniciativa nueva que solo agrega una capacidad al catalogo fijo.

## Fase 0 — Catalogo de capacidades + EventType (backend)

### TASK-0001: Catalogo fijo de capacidades en codigo

**Estado:** todo
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:**
`backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/domain/model/EventCapability.java`
(enum: `TICKETING_GENERAL`, `TICKETING_SEATED`, `SURVEY`, `PRESENTATION`,
`WORD_CLOUD`, `CHAT_BOT`)
**Criterio de cierre:** el enum existe y es la unica fuente de verdad de
capacidades disponibles; no hay strings de capacidad sueltos en otros
archivos.
**Validacion:** `mvn -o clean compile`.

### TASK-0002: Entidad `EventType` + tabla + repositorio

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0001
**Archivos esperados:**
`domain/model/EventType.java` (uuid, key/slug unico, name, description,
capabilities (set de `EventCapability`), active, createdAt, updatedAt),
`domain/ports/EventTypeRepository.java`,
`adapters/outbound/sqlite/SqliteEventTypeRepository.java`,
`adapters/outbound/sqlite/DatabaseManager.java` (tabla `event_types` +
migracion + seed de dos filas: `conference` con todas las capacidades
actuales, `workshop` con las mismas capacidades salvo `CODE_IDE` que no
existe todavia).
**Criterio de cierre:** al arrancar el servicio con una base nueva o
existente, el catalogo contiene al menos `conference` (activo) y `workshop`
(activo).
**Validacion:** `mvn -o test`.

### TASK-0003: Casos de uso de administracion de tipos de evento

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0002
**Archivos esperados:**
`application/usecases/CreateEventTypeUseCase.java`,
`application/usecases/UpdateEventTypeUseCase.java`,
`application/usecases/SetEventTypeActiveUseCase.java`,
`application/usecases/ListEventTypesUseCase.java` (con filtro
`activeOnly` para el selector del organizador)
**Criterio de cierre:** crear con slug duplicado falla claro; editar
capacidades reemplaza el set completo; desactivar no borra ni afecta
eventos existentes.
**Validacion:** tests unitarios con fakes de `EventTypeRepository`
(siguiendo el estilo de `ReserveGeneralUseCaseTest`).

### TASK-0004: Rutas HTTP de administracion + catalogo de capacidades

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0003
**Archivos esperados:** nuevo `EventTypeHandler.java` con:
`GET /api/v1/event-types` (activos, publico/autenticado — para el selector
del organizador), `GET /api/v1/event-types/all` (ADMIN-only, incluye
inactivos), `POST /api/v1/event-types` (ADMIN-only),
`PUT /api/v1/event-types/{id}` (ADMIN-only),
`PUT /api/v1/event-types/{id}/active` (ADMIN-only),
`GET /api/v1/event-capabilities` (catalogo fijo, de solo lectura, FR-009);
wiring en `UsersApplication.java`.
**Criterio de cierre:** un `ORGANIZER` sin rol `ADMIN` recibe 403 al intentar
crear/editar un tipo de evento; `GET /event-types` sin auth devuelve solo
los activos.
**Validacion:** tests de handler o verificacion manual con curl/preview.

## Fase 1 — Generalizar Event (backend)

### TASK-0010: `Conference` referencia `EventType`

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0002
**Archivos esperados:** `domain/model/Conference.java` (campo
`eventTypeKey`, default `"conference"`), `DatabaseManager.java`
(`ALTER TABLE conferences ADD COLUMN event_type_key TEXT NOT NULL DEFAULT
'conference'`, mismo patron try/catch ya usado en esa tabla),
`SqliteConferenceRepository.java`
**Criterio de cierre:** toda conferencia existente se lee con
`eventTypeKey = "conference"` sin migracion manual.
**Validacion:** `mvn -o test` (0 regresiones sobre la suite actual).

### TASK-0011: Gate de capacidad en rutas existentes de boletos/encuesta/presentacion

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0010, TASK-0002
**Archivos esperados:** un `EventCapabilityGuard`/servicio de dominio
compartido que, dado un `Conference`, resuelve su `EventType` y expone
`hasCapability(EventCapability)`; usarlo en `ConferenceHandler` (rutas de
`/seating`, `/reservations`, `/venue-map`, `/seats`) para responder con un
error claro (ej. `capability_not_available`, 409 o 400) si el tipo de
evento del evento no tiene la capacidad requerida.
**Criterio de cierre:** un evento cuyo tipo no tiene `TICKETING_SEATED`
rechaza `PUT .../seats` con un error explicito en vez de un 500 o un estado
inconsistente.
**Validacion:** tests de los handlers/use cases afectados con un
`EventType` sin la capacidad correspondiente.

### TASK-0012: Endpoint y flujo para cambiar el tipo de un evento

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0011
**Archivos esperados:** `SetEventTypeUseCase.java` (organizer-only, dueño
del evento; bloquea el cambio si hay datos incompatibles activos, mismo
criterio que `SetSeatingModeUseCase` para `SEATED`), ruta
`PUT /{id}/event-type` en `ConferenceHandler`.
**Criterio de cierre:** cambiar a un tipo sin `TICKETING_SEATED` estando en
modo `SEATED` con reservas de asiento activas se bloquea con mensaje claro,
igual que ya ocurre al salir de `seatingMode = SEATED`.
**Validacion:** test unitario espejo de `SetSeatingModeUseCaseTest` (o el
existente si se reutiliza).

## Fase 2 — Frontend

### TASK-0020: Página de administración de tipos de evento (ADMIN-only)

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0004
**Archivos esperados:**
`pages/dashboard/EventTypesAdminPage.vue` (listar, crear, editar,
activar/desactivar; checkboxes de capacidades poblados desde
`GET /event-capabilities`), `usersApi.ts`, `types.ts`, ruta
`/dashboard/admin/event-types` con guard `ADMIN` (mismo patron que
`/dashboard/admin/users`).
**Criterio de cierre:** un usuario sin rol `ADMIN` no puede llegar a la
pagina (guard de router + 403 de backend como respaldo).
**Validacion:** `npx vue-tsc --noEmit`, `npx vitest run`, prueba manual en
preview.

### TASK-0021: Selector de tipo de evento en creación/edición

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0020, TASK-0012
**Archivos esperados:** `NewConferencePage.vue`, `EditConferencePage.vue`
(selector poblado desde `GET /event-types` activos, reemplaza cualquier
selector hardcodeado)
**Criterio de cierre:** el organizador ve y elige entre los tipos activos
del catalogo, no un enum fijo en el frontend.
**Validacion:** prueba manual en preview.

### TASK-0022: Pestañas de la vista de evento gateadas por capacidad

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0021
**Archivos esperados:** `ConferencePage.vue` (las pestañas de boletos,
encuesta, presentacion, nube de palabras se muestran segun las
capacidades del `eventType` recibido del backend, no segun comparaciones de
string hardcodeadas)
**Criterio de cierre:** un evento cuyo tipo no tiene `SURVEY` no muestra la
pestaña de encuesta, sin tocar la logica de la pagina de encuesta en si.
**Validacion:** prueba manual en preview con un tipo de evento de prueba
con capacidades minimas.

## Fase 3 — Infraestructura self-hosted (Jitsi, Excalidraw, drawio, Etherpad)

### TASK-0030: Helm chart Jitsi Meet self-hosted

**Estado:** todo
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:** `infra/helm/charts/jitsi/` (o subchart dentro del
chart principal), values para dominio propio, recursos minimos.
**Criterio de cierre:** una sala de prueba conecta audio/video entre dos
navegadores contra la instancia propia en K3s.
**Validacion:** `helm template` + prueba manual en un cluster de desarrollo.

### TASK-0031: Helm chart Excalidraw self-hosted (editor + servidor de colaboracion)

**Estado:** todo
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:** `infra/helm/charts/excalidraw/`
**Criterio de cierre:** dos sesiones en la misma sala derivada de un `uuid`
ven los cambios del otro en vivo.
**Validacion:** `helm template` + prueba manual.

### TASK-0032: Helm chart drawio self-hosted

**Estado:** done
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:** `infra/helm/charts/insightbloom/templates/drawio-deployment.yaml`,
`drawio-service.yaml`, `drawio-hpa.yaml` (instancia compartida, no chart
separado — sigue el mismo patron ya usado para NATS dentro del chart
principal, ver DEC-0020), `ingress.yaml` (bloque `ingressDrawio`),
`network-policy.yaml` (regla de ingress publico para drawio), `values.yaml`
(`drawio:` + `ingressDrawio:`).
**Criterio de cierre:** el editor de drawio carga embebido sin errores de
consola (revisar cabeceras `X-Frame-Options`/CSP si aplica).
**Validacion:** `helm template` + `helm lint` verificados en verde. Prueba
manual de carga del iframe (headers `X-Frame-Options`/CSP) **pendiente**
hasta el primer despliegue real en K3s — no verificable sin un cluster.

### TASK-0033: Helm chart Etherpad self-hosted + API key

**Estado:** done
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:**
`infra/helm/charts/insightbloom/templates/etherpad-deployment.yaml`,
`etherpad-service.yaml`, `etherpad-pvc.yaml` (instancia unica compartida,
SQLite sobre PVC — ver DEC-0020; replica fija en 1, sin HPA, porque
Etherpad mantiene el estado de edicion en vivo en memoria del proceso),
`ingress.yaml` (bloque `ingressEtherpad`), `network-policy.yaml` (regla de
ingress publico), `values.yaml` (`etherpad:` + `ingressEtherpad:`),
`.github/workflows/deploy.yml` (step "Upsert etherpad secrets in k3s",
falla el deploy si falta el secret `ETHERPAD_API_KEY` — nunca versionado
en el repo, ver NFR-007; se monta como `/opt/etherpad-lite/APIKEY.txt` via
`subPath` en vez de usar dirtydb).
**Criterio de cierre:** se puede crear un pad via la API HTTP de Etherpad
usando la API key generada al desplegar.
**Validacion:** `helm lint`/`helm template` verificados en verde. Prueba
manual con `curl` contra la API **pendiente** hasta el primer despliegue
real en K3s.

## Fase 4 — Integracion backend + frontend

### TASK-0040: Configuracion de URLs/credenciales de integraciones self-hosted

**Estado:** parcial (drawio + Etherpad hechos; Jitsi/Excalidraw pendientes)
**Owner:** —
**Dependencias:** TASK-0030, TASK-0031, TASK-0032, TASK-0033
**Archivos esperados:** `UsersApplication.java` (variables de entorno
`JITSI_SELF_HOSTED_DOMAIN`, `EXCALIDRAW_BASE_URL`, `DRAWIO_BASE_URL` ✅,
`ETHERPAD_BASE_URL` ✅, `ETHERPAD_API_KEY` ✅), Helm `values.yaml` +
`deploy.yml` para inyectarlas.
**Criterio de cierre:** el backend expone estas URLs (sin exponer la API
key de Etherpad) via un endpoint de configuracion publica que el frontend
consulta para armar los `iframe`. ✅ Hecho para drawio y Etherpad:
`IntegrationConfigHandler` (`GET /api/v1/integrations`) devuelve
`drawioBaseUrl` y `etherpadBaseUrl` (nunca `ETHERPAD_API_KEY`, que solo
vive como `secretEnv` de `insightbloom-users`); queda extender el mismo
handler con los campos de Jitsi/Excalidraw cuando se implementen.
**Validacion:** `mvn -o test` + revision manual de que la API key nunca
aparece en una respuesta HTTP — confirmado: `IntegrationConfigView` solo
expone `drawioBaseUrl`/`etherpadBaseUrl`, `ETHERPAD_API_KEY` solo se lee
para pasarlo al constructor de `HttpEtherpadPort`, nunca a una vista.

### TASK-0041: Campo `videoProvider` + endpoint de configuracion de Jitsi por evento

**Estado:** diferido — solo `JITSI_PUBLIC` implementado (sin campo `videoProvider`)
**Owner:** —
**Dependencias:** TASK-0040
**Archivos esperados:** `Conference.java` (`videoProvider`:
`JITSI_PUBLIC` | `JITSI_SELF_HOSTED`, solo relevante si `VIDEO_CONFERENCE`
activa), migracion en `DatabaseManager.java`, ruta
`PUT /{id}/video-provider` (organizer-only, gateada por capacidad segun
TASK-0011).
**Criterio de cierre:** el organizador elige el proveedor de Jitsi para su
evento; cambiarlo no requiere reiniciar nada.
**Validacion:** test unitario + prueba manual.
**Nota (2026-07-11):** siguiendo el orden recomendado, se adelanto Jitsi
publico sin esperar a self-hosted (ver TASK-0043). Como todavia no existe
un segundo proveedor, no hay nada que elegir: no se agrego el campo
`videoProvider` ni el endpoint. `VideoConferencePage.vue` apunta siempre a
`meet.jit.si`. Esta tarea se retoma cuando se implemente Jitsi
self-hosted (ultimo paso del orden recomendado) y el organizador
efectivamente tenga dos proveedores entre los que elegir.

### TASK-0042: `EtherpadClient` + creacion perezosa de pad por evento

**Estado:** done (falta solo la prueba manual contra la instancia real desplegada)
**Owner:** —
**Dependencias:** TASK-0040
**Archivos esperados:** `domain/ports/EtherpadPort.java`,
`adapters/outbound/etherpadclient/HttpEtherpadPort.java`,
`application/usecases/GetOrCreateEventPadUseCase.java` (crea el pad con
`padID = event.uuid` en el primer acceso, idempotente), ruta
`GET /{id}/notes` (gateada por `COLLAB_NOTES`). Tambien se agrego
`PurgeExpiredEventNotesUseCase` (no estaba en el alcance original de esta
tarea, pero es necesario para cerrar DEC-0020): borra el pad cuando
`event.eventDate + event.endTime + 1h` ya paso, marcado via la nueva
columna `notes_purged_at` para no reintentar cada tick; corre en el mismo
scheduler in-process que `SendConferenceRemindersUseCase`.
**Criterio de cierre:** el primer asistente que abre "Notas" crea el pad;
los siguientes reutilizan el mismo pad sin duplicarlo.
**Validacion:** `mvn -o test` en verde (0 regresiones). Prueba manual
contra la instancia real desplegada en TASK-0033 **pendiente**.

### TASK-0043: Pestañas frontend "Videollamada", "Pizarra", "Diagramas", "Notas"

**Estado:** parcial (Videollamada + Diagramas + Notas hechas; Pizarra pendiente)
**Owner:** —
**Dependencias:** TASK-0022, TASK-0041, TASK-0042
**Archivos esperados:**
`pages/conference/VideoConferencePage.vue` ✅ (embebe Jitsi via su
IFrame API cargando `external_api.js` dinamicamente — no un `<iframe>`
crudo —, sala derivada del `uuid` del evento: `insightbloom-{uuid}`, solo
`meet.jit.si` por ahora, ver nota en TASK-0041),
`pages/conference/WhiteboardPage.vue` (embebe Excalidraw self-hosted en
`iframe` con la sala derivada del `uuid`),
`pages/conference/DiagrammingPage.vue` ✅ (embebe drawio en `iframe`,
degrada con mensaje claro si `drawioBaseUrl` no esta configurado — NFR-006),
`pages/conference/CollabNotesPage.vue` ✅ (embebe Etherpad en `iframe`
usando la URL del pad obtenida via TASK-0042, degrada igual que Diagramas
si `etherpadBaseUrl` no esta configurado o la llamada a `/notes` falla),
`app/router/index.ts` ✅ (rutas `/c/:friendlyId/video`, `/diagrams` y
`/notes` agregadas),
`ConferencePage.vue` ✅ (pestañas "Videollamada", "Diagramas" y "Notas"
gateadas por `VIDEO_CONFERENCE`/`DIAGRAMMING`/`COLLAB_NOTES`, mismo patron
de TASK-0022; falta Pizarra).
**Criterio de cierre:** cada pestaña solo aparece si su capacidad esta
activa; si la integracion self-hosted correspondiente no responde, se
muestra un mensaje claro (NFR-006) en vez de una pantalla en blanco.
**Validacion:** `npx vue-tsc --noEmit`, `npx vitest run`, prueba manual de
las 4 integraciones en preview contra las instancias desplegadas.

## Fase 5 — Motor de encuestas alternativo SurveyJS

### TASK-0050: Confirmar licencia de survey-creator-* antes de implementar

**Estado:** todo
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:** ninguno (investigacion) — el resultado se registra
como nota en `spec-native/DECISIONS.md` (actualizar DEC-0018 con el
hallazgo antes de continuar).
**Criterio de cierre:** se sabe con certeza si `survey-creator-core` +
`survey-creator-js` se pueden usar en produccion sin costo/marca de agua, o
si el editor visual queda fuera del alcance inicial (ver Risks del SPEC).
**Validacion:** revision manual de la licencia vigente publicada por
SurveyJS.

### TASK-0051: `surveyEngine` en el modelo de encuesta + persistencia del schema SurveyJS

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0050
**Archivos esperados** (en `insightbloom-survey`): modelo de encuesta
(campo `engine`: `NATIVE` | `SURVEYJS`, fijo al crear), columna nueva para
guardar el JSON schema de SurveyJS cuando `engine = SURVEYJS` (migracion
idempotente, mismo patron que el resto del proyecto).
**Criterio de cierre:** una encuesta `SURVEYJS` guarda y recupera su schema
completo sin perdida de datos.
**Validacion:** tests de dominio/casos de uso del servicio `survey`.

### TASK-0052: Endpoint de respuestas compatible con ambos motores

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0051
**Archivos esperados:** casos de uso/handler de respuesta de encuesta en
`insightbloom-survey`, aceptando el payload de resultado de SurveyJS
(`survey.data`) para `engine = SURVEYJS` sin romper el contrato existente
de `NATIVE`.
**Criterio de cierre:** una respuesta a encuesta `SURVEYJS` queda asociada
al evento y al asistente, visible en el listado de respuestas.
**Validacion:** test del handler + prueba manual.

### TASK-0053: Frontend — editor visual SurveyJS Creator (organizer)

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0050, TASK-0051
**Archivos esperados:** `frontend/web/package.json` (`survey-core`,
`survey-creator-core`, `survey-creator-js` o el paquete Vue equivalente si
existe), nueva vista dentro de `SurveyManagePage.vue` o pagina dedicada
para elegir motor al crear una encuesta y, si es `SURVEYJS`, abrir el
editor visual.
**Criterio de cierre:** el organizador arma una encuesta completa con el
editor SurveyJS y la guarda sin salir del dashboard.
**Validacion:** `npm run build` (confirmar que el bundle no rompe con las
nuevas dependencias), prueba manual en preview.

### TASK-0054: Frontend — render de encuesta SurveyJS para el asistente

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0052, TASK-0053
**Archivos esperados:** `survey-js-ui` (o el paquete Vue equivalente),
`SurveyPage.vue` (renderiza el componente SurveyJS cuando
`engine = SURVEYJS`, mantiene el flujo actual cuando `engine = NATIVE`).
**Criterio de cierre:** el asistente responde una encuesta `SURVEYJS` desde
la misma pagina y flujo de siempre, sin URL ni paso adicional.
**Validacion:** `npx vue-tsc --noEmit`, `npx vitest run`, prueba manual de
punta a punta (crear, responder, ver resultado).

## Fase 6 — Motor de mapa de asientos alternativo seatmap-canvas

### TASK-0060: Verificar mantenimiento/compatibilidad de seatmap-canvas

**Estado:** todo
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:** ninguno (investigacion) — el resultado se registra
como nota en `spec-native/DECISIONS.md` (actualizar DEC-0019 con el
hallazgo antes de continuar).
**Criterio de cierre:** se confirma que `seatmap-canvas` sigue mantenido o,
si no, se decide si igual se adopta (vendorizado/congelado) o se busca una
alternativa equivalente antes de iniciar TASK-0061.
**Validacion:** revision manual del repositorio y su historial de commits.

### TASK-0061: `venueMapEngine` en `Conference` + modelo de distribucion `SEATMAP_CANVAS`

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0060
**Archivos esperados** (en `insightbloom-users`): `Conference.java`
(`venueMapEngine`: `FREEFORM` | `SEATMAP_CANVAS`, solo relevante si
`seatingMode = SEATED`), columna nueva para guardar la configuracion de
filas/secciones/butacas de `seatmap-canvas` (JSON, migracion idempotente
try/catch como el resto de la tabla `conferences`).
**Criterio de cierre:** un evento `SEATMAP_CANVAS` guarda y recupera su
distribucion completa (filas, butacas, numeracion) sin perdida de datos.
**Validacion:** tests de dominio/repositorio en `insightbloom-users`.

### TASK-0062: `DefineVenueSeatsUseCase` extendido para `SEATMAP_CANVAS`

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0061
**Archivos esperados:** `DefineVenueSeatsUseCase.java` (o un caso de uso
paralelo si el modelo de entrada difiere demasiado del `SeatInput`
freeform actual), manteniendo el mismo guard de "no borrar un asiento con
reserva activa" ya implementado para `FREEFORM`.
**Criterio de cierre:** el `venue_seats` resultante (uuid, label, x/y o
fila/columna segun el motor) sigue siendo compatible con
`ReserveSeatUseCase`/`GetConferenceSeatMapUseCase` sin duplicar esa logica
de reserva/concurrencia.
**Validacion:** tests unitarios espejo de `DefineVenueSeatsUseCaseTest`.

### TASK-0063: Endpoint de configuracion de motor + reutilizar distribucion entre eventos

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0062
**Archivos esperados:** ruta `PUT /{id}/venue-map-engine` (organizer-only,
gateada por `TICKETING_SEATED`), endpoint para clonar la distribucion de
un evento anterior a uno nuevo (FR-023).
**Criterio de cierre:** el organizador reutiliza el mapa de un recinto ya
definido en otro evento sin redefinir filas/butacas.
**Validacion:** test unitario + prueba manual.

### TASK-0064: Frontend — wrapper Vue de seatmap-canvas + editor de distribucion

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0060, TASK-0063
**Archivos esperados:** `frontend/web/package.json` (`seatmap-canvas`),
`components/SeatMapCanvasPicker.vue` (wrapper delgado sobre la libreria
canvas vanilla-JS, siguiendo el mismo criterio de integracion ya usado para
`qrcode`/`qr-scanner`), extension de `VenueMapEditorPage.vue` para elegir
motor (`FREEFORM` vs `SEATMAP_CANVAS`) y, si es `SEATMAP_CANVAS`, definir
filas/secciones en vez de hacer clic libre.
**Criterio de cierre:** el organizador define un recinto con filas y
butacas numeradas usando el editor de `seatmap-canvas`.
**Validacion:** `npm run build`, prueba manual en preview.

### TASK-0065: Frontend — selector de asiento con seatmap-canvas para el asistente

**Estado:** todo
**Owner:** —
**Dependencias:** TASK-0064
**Archivos esperados:** `TicketPage.vue` (rama `SEATED` usa
`SeatMapCanvasPicker.vue` en vez de `SeatMapPicker.vue` cuando
`venueMapEngine = SEATMAP_CANVAS`, mismo flujo de reserva/409 ya
implementado).
**Criterio de cierre:** el asistente elige un asiento numerado real y lo
reserva; un asiento tomado aparece deshabilitado/gris.
**Validacion:** `npx vue-tsc --noEmit`, `npx vitest run`, prueba manual de
concurrencia (dos sesiones reservando el mismo asiento).
