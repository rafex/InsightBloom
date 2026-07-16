# Plan de sesión — 2026-07-16

Documento de trabajo persistido tal cual se planeó y ejecutó durante la sesión, para
tener registro histórico. Reúne cuatro iniciativas distintas (no relacionadas entre sí
salvo por haber salido en la misma conversación). Ver `spec-native/tasks/README.md` para
el formato granular por-tarea que usan otras iniciativas (`code-ide-sandboxes/TASKS.md`,
etc.) — este documento es más informal, tal como se escribió durante la planificación.

## Estado al momento de persistir este documento

**Corrección (2026-07-16, tras revisar el código):** el estado original de este documento
decía "No iniciado" para boletos/asientos por error — no se verificó el repo antes de
escribirlo. En realidad ya estaba implementado en commits previos de esta misma sesión
(`1ae6350`, `07bae79`, `8b5b461`), incluyendo la Fase 2.6 (generación de layout con IA).

| Iniciativa | Estado |
|---|---|
| Sistema de boletos y reserva de asientos (Fases 1-2, incl. 2.6 IA) | ✅ Implementado (commits `1ae6350`, `07bae79`, `8b5b461`) |
| Fase 3 del IDE — provisión real de pods de sandbox | ✅ Implementado y verificado |
| Fase 4 del IDE — separar code-server (Debian) del runtime (Alpine) | ✅ Implementado y verificado |
| Fase 5 — Tests en dos niveles (unitarios + integración bajo demanda) | ✅ Implementado y verificado |

---

# Sistema de boletos y reserva de asientos (tipo Eventbrite)

## Context

El organizador quiere evolucionar InsightBloom de "plataforma de charlas" hacia un ecosistema más completo tipo Eventbrite: reservar un lugar, tener un boleto digital, recibir recordatorio del evento, y controlar el acceso el día del evento. Por ahora es sobre sus propias conferencias, pero el diseño debe justificar capturar estos datos como un plus real (aforo, asistencia real vs. registrados, control de acceso).

Alcance confirmado con el usuario:
- **Sin pagos por ahora** (reservas gratuitas; se puede agregar cobro después).
- Cada conferencia elige un modo de reserva, **editable después de creada**:
  - `GENERAL`: solo aforo (capacidad numérica), sin mapa de asientos.
  - `SEATED`: el organizador sube una imagen del recinto y coloca marcadores de asiento sobre ella; el asistente elige un asiento libre específico.
- Cada reserva genera un **boleto digital con QR**.
- **Check-in**: el organizador escanea el QR con la cámara del celular/tablet para marcar la entrada y evitar reuso del mismo boleto.
- Es una **extensión de la entidad Conference existente**, no un concepto nuevo de "evento" — se reutiliza toda la infraestructura de registro, dashboard y correo ya construida.

Se sigue el mismo patrón de esta sesión: reutilizar patrones existentes (migraciones SQLite idempotentes, patrón use case + handler + endpoint, scheduler en proceso para recordatorios, imágenes como base64 en columna TEXT), un commit por punto, sin push hasta confirmación explícita.

## Supuestos de diseño (a confirmar/ajustar durante la implementación, no bloqueantes)

- **Aforo (`GENERAL`) es un tope duro**: la reserva falla si ya se llegó a `capacity`. Se enforce con un `UPDATE conferences SET reserved_count = reserved_count + 1 WHERE uuid=? AND reserved_count < capacity` atómico (mismo espíritu que el resto del código: sin locks, apoyado en la atomicidad de SQLite) en vez de un `COUNT` previo con ventana de carrera.
- **Doble reserva del mismo asiento (`SEATED`)** se resuelve con `UNIQUE(conference_uuid, seat_uuid)` en la tabla `reservations`: dos INSERT concurrentes, SQLite solo permite uno, el otro lanza `SQLITE_CONSTRAINT` → se traduce a `SeatAlreadyTakenException` → 409 al cliente. Sin locks de aplicación.
- **Cancelar una reserva**: se incluye como parte de la Fase 1 (borra la fila, libera aforo/asiento) porque es barato y evita boletos "fantasma" bloqueando cupo.
- **Quién puede hacer check-in**: mismo chequeo de organizador/admin que ya usa `ConferenceHandler` (`isOrganizerOrAdmin(role)` + dueño de la conferencia) — no se introduce un rol nuevo de "staff" en esta primera versión.
- **Payload del QR**: el QR codifica directamente el `ticket_code` (string opaco), no una URL pública — el escaneo lo interpreta la propia página de check-in del organizador (autenticada), evitando exponer un endpoint público de consulta de boletos.
- **Cambiar de modo después de tener reservas activas**: se permite libremente excepto salir de `SEATED` mientras existan reservas activas con asiento asignado (se bloquea con un mensaje claro) — evita invalidar boletos ya emitidos en silencio.
- **`conference_memberships` no cambia de propósito**: sigue siendo "esta persona se unió/sigue la conferencia" (igual que hoy, se crea siempre). `reservations` es una tabla nueva y aparte que solo existe cuando `seating_mode != NONE` — así no hay dos fuentes de verdad divergentes sobre "¿está registrado?".

---

## Fase 1 — Infraestructura + modo GENERAL de punta a punta

Se prioriza porque es demostrable por sí sola (RSVP gratis con aforo + boleto QR + escaneo en la puerta) y prueba el mecanismo más riesgoso (generación/escaneo de QR + máquina de estados del check-in) sin la complejidad extra del editor de mapa.

### 1.1 Modelo de datos (`insightbloom-users`, `DatabaseManager.java`)

Siguiendo el patrón real ya usado para `conferences` (try/catch ignorado, no el `ColumnMigrationHelper` que solo usa la tabla `users`):

```java
try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN seating_mode TEXT NOT NULL DEFAULT 'NONE'"); } catch (SQLException ignored) {}
try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN capacity INTEGER"); } catch (SQLException ignored) {}
try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN reserved_count INTEGER NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
try { stmt.executeUpdate("ALTER TABLE conferences ADD COLUMN venue_map_base64 TEXT"); } catch (SQLException ignored) {}
```
`seating_mode` default `'NONE'` es clave: toda conferencia existente sigue comportándose exactamente igual (compatibilidad hacia atrás automática).

Nueva tabla `reservations` (uuid, conference_uuid, user_uuid, seat_uuid nullable, ticket_code UNIQUE, status TEXT `RESERVED|CHECKED_IN`, created_at, checked_in_at nullable, `UNIQUE(conference_uuid, seat_uuid)`). Cancelación borra la fila (no hay estado `CANCELLED` — más simple, sin necesidad de distinguir activas vs. canceladas en el `UNIQUE`). Índices por `conference_uuid` y `user_uuid`.

### 1.2 Dominio + casos de uso (`insightbloom-users`)

- `Reservation` (domain model) + `ReservationRepository` (puerto) + `SqliteReservationRepository` (adaptador), calcado de `SqliteConferenceMembershipRepository`.
- `ReserveGeneralUseCase`: valida `seating_mode == GENERAL`, hace el UPDATE atómico de `reserved_count`, si `rowsAffected == 0` lanza `CapacityExceededException`; genera `ticket_code` (`UUID.randomUUID()`), guarda `Reservation` con status `RESERVED`.
- `GetMyTicketUseCase`: `Optional<Reservation>` para (conferenceUuid, userUuid).
- `CheckInTicketUseCase`: busca por `ticket_code` **+ conference_uuid** (para que un boleto de otra conferencia no se pueda colar), valida `status == RESERVED` (si ya es `CHECKED_IN` → `TicketAlreadyUsedException`), marca `CHECKED_IN` + `checked_in_at`.
- `CancelReservationUseCase`: borra la fila, decrementa `reserved_count` si era `GENERAL`.
- `SetSeatingModeUseCase`: valida dueño de la conferencia (mismo `.filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))` que ya usa `UpdateConferenceUseCase`), setea `seating_mode`/`capacity`.
- **`JoinConferenceUseCase` cambia**: después de `membershipRepository.recordJoin(...)`, si `conference.getSeatingMode() == GENERAL` y no existe ya una reserva para ese usuario, llama a `ReserveGeneralUseCase` internamente. El correo de confirmación (`sendConfirmationEmail`, ya best-effort) se extiende para incluir el link "ver mi boleto" cuando exista reserva.

### 1.3 HTTP (`ConferenceHandler` — se extiende, no un handler nuevo por ahora; el editor de asientos de Fase 2 sí puede justificar separarlo)

Todas siguen el patrón exacto ya usado en `handleUpdate` (`extractToken` → null-check 401 → `validateTokenUseCase.execute` → `!v.valid()` → 401):

- `PUT /api/v1/conferences/{id}/seating` — organizer-only (`isOrganizerOrAdmin(v.role())` + dueño) — body `{seatingMode, capacity}`.
- `POST /api/v1/conferences/{id}/reservations` — autenticado — reserva (sin body en modo GENERAL); 409 si `capacity` llena.
- `GET /api/v1/conferences/{id}/reservations/me` — autenticado — boleto propio.
- `DELETE /api/v1/conferences/{id}/reservations/me` — autenticado — cancelar.
- `GET /api/v1/conferences/{id}/reservations` — organizer-only — lista para el dashboard de check-in.
- `POST /api/v1/conferences/{id}/reservations/check-in` — organizer-only — body `{ticketCode}`; 409 `already_checked_in` si repetido.

### 1.4 Recordatorios (`SendConferenceRemindersUseCase`)

Se extiende (no un scheduler nuevo): recibe `ReservationRepository` inyectado en `UsersApplication.java`. Si `seating_mode != NONE`, busca la reserva del destinatario y agrega al cuerpo del correo el link "ver mi boleto"; si no, el cuerpo queda igual que hoy. Mismo tick de 5 min, misma columna `reminder_sent_at`, mismo try/catch best-effort por destinatario.

### 1.5 Frontend

- `usersApi.ts` + `types.ts`: `setSeatingMode`, `reserveGeneral`, `getMyTicket`, `cancelReservation`, `checkInTicket`, `listReservations`; tipos `SeatingMode`, `Reservation`.
- `EditConferencePage.vue`: sección nueva "Boletos" — selector de modo (Ninguno/General/Con asientos) + input de aforo si es General.
- Nuevo componente `TicketQr.vue` (no reutilizar `QrCodeModal.vue` tal cual — ese codifica una URL de unirse a la conferencia; este codifica el `ticket_code` opaco): usa el mismo paquete `qrcode` ya instalado, `QRCode.toCanvas`.
- Nueva pestaña "Mi boleto" en `ConferencePage.vue`/router (`TicketPage.vue`): si `seating_mode === NONE` no se muestra la pestaña; si `GENERAL`, boleto ya existe tras unirse (mostrar QR + estado); botón cancelar.
- Nueva página `dashboard/conferences/:conferenceId/check-in` (`CheckInScannerPage.vue`), organizer-only: librería **`qr-scanner`** (npm, mantenida, sin bindings nativos — apta para el build de Vite/Docker actual, usa `BarcodeDetector` nativo cuando está disponible y cae a un decoder WASM propio si no) contra la cámara vía `getUserMedia`; al decodificar llama `checkInTicket`; toast verde/rojo (`already_checked_in` en rojo).

### 1.6 Verificación Fase 1

- Backend: no hay tests de `ConferenceHandler`/`JoinConferenceUseCase`/`DatabaseManager` hoy (solo `LoginUseCaseTest`, `FriendlyIdServiceTest` existen) — se agregan `ReserveGeneralUseCaseTest` y `CheckInTicketUseCaseTest` con fakes de repositorio (aforo lleno lanza excepción; segundo check-in del mismo ticket lanza `TicketAlreadyUsedException`; ticket de otra conferencia se rechaza), siguiendo el estilo de `LoginUseCaseTest.java`. `mvn -o clean compile`/`test` tras cada cambio, como el resto de la sesión.
- Test de concurrencia: dos hilos llamando al UPDATE atómico de `reserved_count` con `capacity=1` vía `ExecutorService` — assert que exactamente uno tiene éxito.
- Frontend: `preview_*` — conferencia con aforo 1, unirse con usuario A (boleto con QR), intentar con B (error claro "conferencia llena"); llamar `check-in` dos veces contra el mismo `ticket_code` y confirmar el segundo da error.
- `npm run typecheck` / `npm test` / `npm run build` deben seguir en verde (mantener strict mode limpio, igual que el resto de esta sesión).

---

## Fase 2 — Modo SEATED (mapa de asientos)

Se apoya en toda la infraestructura de boleto/check-in/recordatorio de la Fase 1 sin tocarla — solo cambia cómo se llena `seat_uuid` en `reservations`.

### 2.1 Modelo de datos
Nueva tabla `venue_seats` (uuid, conference_uuid, label, x REAL, y REAL 0.0–1.0 relativos a la imagen, created_at). Reemplazo completo por conferencia al guardar (borra todo lo no referenciado por una reserva activa e inserta el set nuevo) — bloquea el borrado de un asiento con reserva activa.

### 2.2 Casos de uso
- `DefineVenueSeatsUseCase` (organizer-only, full-replace con el guard anterior).
- `GetConferenceSeatMapUseCase`: asientos + flag ocupado/libre (sin exponer quién ocupa cada uno).
- `ReserveSeatUseCase`: valida que el asiento pertenece a la conferencia, INSERT confiando en `UNIQUE(conference_uuid, seat_uuid)` para la concurrencia (catch `SQLException` con "UNIQUE constraint failed" → `SeatAlreadyTakenException` → 409).
- `SetSeatingModeUseCase` se extiende: bloquea pasar de `SEATED` a otro modo si hay reservas activas con `seat_uuid` no nulo.
- `JoinConferenceUseCase`: en modo `SEATED` NO auto-reserva (no hay asiento que elegir); el frontend lleva al asistente al selector de asientos tras unirse, y el correo de confirmación de boleto se dispara desde `ReserveSeatUseCase` una vez elegido el asiento (no desde el join).

### 2.3 HTTP
- `PUT /api/v1/conferences/{id}/venue-map` — organizer-only — body `{imageBase64}` (mismo patrón base64-en-columna que `flyer_base64`).
- `PUT /api/v1/conferences/{id}/seats` — organizer-only — reemplazo completo `{seats: [{label,x,y}]}`.
- `GET /api/v1/conferences/{id}/seats` — autenticado — mapa + ocupación.
- `POST /api/v1/conferences/{id}/reservations` se extiende para aceptar `{seatUuid}` opcional (modo SEATED).

### 2.4 Frontend
- `VenueMapEditorPage.vue` (`dashboard/conferences/:conferenceId/venue-map`, organizer-only): input de imagen → base64 (mismo helper ya usado para `flyerBase64` en `EditConferencePage.vue`) → subir; overlay de marcadores sobre la imagen (`position:absolute; left:${x*100}%; top:${y*100}%`), clic para agregar (con label), botón eliminar por marcador, "Guardar asientos" hace el full-replace.
- `SeatMapPicker.vue` (asistente): misma imagen + marcadores, ocupados en gris/deshabilitados, clic en uno libre dispara la reserva.
- `TicketPage.vue` se extiende: si `SEATED` sin reserva aún, muestra `SeatMapPicker`; tras elegir, muestra el boleto (igual que en Fase 1).

### 2.5 Verificación Fase 2
- Subir imagen, colocar 2-3 asientos, guardar, recargar y confirmar que las posiciones relativas persisten igual (probar redimensionando el viewport con `preview_resize` entre guardar y recargar).
- Dos sesiones distintas reservando el mismo asiento en paralelo: exactamente una debe tener éxito, la otra debe ver "asiento ya tomado" y el asiento aparece ocupado al refrescar.
- Confirmar que cambiar de modo lejos de `SEATED` con reservas activas se bloquea con mensaje claro.

### 2.6 Generación asistida por IA del layout de asientos (por descripción de texto)

Alcance confirmado con el usuario: **solo texto** (descripción, medidas, distancias, referencias, uso de figuras geométricas) — no imagen/multimodal por ahora. El resultado es siempre una **propuesta editable**, nunca se guarda directo: se pre-llena el editor manual de `VenueMapEditorPage.vue` (2.4) y el organizador ajusta/confirma antes de "Guardar asientos".

- Mismo patrón que `insightbloom-survey`'s `GroqLlmClient`/`LlmPort` (ver `backend/services/insightbloom-survey/src/main/java/dev/rafex/insightbloom/survey/adapters/outbound/llm/GroqLlmClient.java`), pero replicado dentro de `insightbloom-users` (cada microservicio mantiene su propio adaptador LLM, no hay un servicio LLM compartido) — mismo `LLM_PROVIDER_API_KEY`/`LLM_PROVIDER_BASE_URL`/`LLM_PROVIDER_MODEL` ya usados por survey y chat.
- `GenerateSeatLayoutUseCase`: prompt estructurado que le pide al modelo interpretar la descripción (medidas en metros/filas/columnas, distancias entre asientos, referencias como "escenario al frente", figuras geométricas como "semicírculo", "en herradura", "8 filas de 10") y devolver **JSON estricto** `{seats: [{label, x, y}]}` con `x,y` relativos 0.0–1.0; se valida el JSON de salida (rechaza si no parsea o si algún punto cae fuera de 0–1) y se devuelve como propuesta, sin tocar `venue_seats` todavía.
- Igual que en survey, la función es **opcional**: si `LlmPort.isEnabled()` es falso (sin API key), el endpoint responde 503 `llm_not_configured` — el editor manual sigue funcionando igual sin esto.
- `PUT /api/v1/conferences/{id}/venue-map/generate-seats` — organizer-only — body `{description}` → devuelve `{seats: [...]}` propuestos (no persiste).
- Frontend: en `VenueMapEditorPage.vue`, un textarea "Describe el recinto" + botón "✨ Generar con IA" (mismo espíritu que "Sugerir preguntas con IA" de `SurveyManagePage.vue`) que llena el overlay de marcadores con la propuesta; el organizador edita/borra/mueve igual que si los hubiera puesto a mano, y "Guardar asientos" hace el mismo full-replace de 2.2.

---

## Nota pendiente — Kill switch de IA en el chat (panel administrativo)

Fuera del alcance de boletos/asientos, pero registrado aquí porque salió en la misma conversación: el bot de chat "Roberto" (`chat/bot.py`) hoy se activa/desactiva únicamente por la **presencia de la env var** `LLM_PROVIDER_API_KEY` (ver `Roberto.__init__`, `chat/bot.py:90-99`) — no existe ningún control en caliente, ni por conferencia ni global, para apagarlo sin redeploy. El usuario pidió poder encender/apagar la IA del chat desde el panel administrativo, específicamente para poder cortarla rápido ante un intento de abuso/ataque (prompt injection, spam dirigido al bot, etc.) sin depender de un despliegue.

Diseño propuesto (para una fase futura, no bloqueante para boletos/asientos):
- Nueva tabla de una sola fila en `insightbloom-users` (`platform_settings` o similar) con columna `chat_ai_enabled BOOLEAN NOT NULL DEFAULT true` — mismo patrón de migración idempotente ya usado en el resto del proyecto.
- Endpoint `GET /api/v1/settings/chat-ai` (público o interno) y `PUT /api/v1/settings/chat-ai` (admin-only, mismo guard que `RolesAdminPage.vue`/`EventTypesAdminPage.vue` usan hoy vía `isAdmin`).
- El servicio `chat` (Python) consulta este flag antes de responder — no vía DB compartida (son servicios separados), sino con una llamada HTTP interna a `insightbloom-users` reutilizando el patrón `X-Internal-Auth` ya establecido (tarea #46, Fase 4 de la migración a `backend/common`), cacheada en memoria unos segundos/minutos para no pegarle a `insightbloom-users` en cada mensaje.
- Nueva página en el panel admin: `frontend/web/src/pages/dashboard/AdminChatSettingsPage.vue` (sección "Chat" en `DashboardLayout.vue`, ruta `dashboard/admin/chat`, gateada por `isAdmin` igual que las otras rutas `admin/*`), con un toggle simple "IA habilitada en el chat" que llama al PUT anterior.
- Efecto esperado al apagar: `Roberto.maybe_respond`/`on_insightbloom_event` deben hacer el mismo short-circuit que ya hacen hoy cuando `self._client is None` (sin cliente configurado), pero ahora condicionado también al flag remoto, sin necesitar quitar la API key ni redeploy.

### Critical Files
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/outbound/sqlite/DatabaseManager.java
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/application/usecases/JoinConferenceUseCase.java
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/application/usecases/SendConferenceRemindersUseCase.java
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/application/usecases/UpdateConferenceUseCase.java
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/inbound/http/handlers/ConferenceHandler.java
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/bootstrap/UsersApplication.java
- frontend/web/src/app/router/index.ts
- frontend/web/src/services/api/usersApi.ts
- frontend/web/src/services/api/types.ts
- frontend/web/src/components/QrCodeModal.vue
- frontend/web/src/pages/dashboard/EditConferencePage.vue
- frontend/web/src/pages/conference/ConferencePage.vue
- backend/services/insightbloom-survey/src/main/java/dev/rafex/insightbloom/survey/adapters/outbound/llm/GroqLlmClient.java (referencia de patrón LLM a replicar en insightbloom-users)
- frontend/web/src/pages/dashboard/VenueMapEditorPage.vue
- chat/bot.py (kill switch de IA — nota pendiente, fase futura)
- frontend/web/src/pages/dashboard/DashboardLayout.vue (nueva sección admin "Chat" — nota pendiente, fase futura)

---

# Fase 3 del IDE — provisión real de pods de sandbox (Kubernetes)

## Context

El tab "IDE de código" (capacidad `CODE_IDE`) tiene todo el andamiaje construido — modelo `Sandbox`, tabla SQLite `sandbox_assignments`, `AssignSandboxUseCase`, `SandboxHandler`, config por conferencia, frontend `IdePage.vue` — pero **nunca funcionó**: `AssignSandboxUseCase.findUnassignedSlotForConference` busca una fila con `user_uuid IS NULL`, y ningún código en todo el repo inserta esas filas. El propio `sandbox-pool.yaml` (gateado `if false`, documentativo) ya decía explícitamente: *"Fase 3: el backend crea Pods dinámicamente vía Kubernetes API (Fabric8 o similar), esto no se hace con Helm"* — esa Fase 3 nunca se construyó. Confirmado con `grep` que no hay ninguna dependencia de cliente Kubernetes en ningún `pom.xml` del repo, y que `ide-insightbloom.v1.rafex.cloud` no está wireado en ningún `GATEWAY_ROUTES` ni Ingress.

Objetivo: cada asistente que abre el IDE en un evento con `CODE_IDE` obtiene un Pod real de `code-server` corriendo en el namespace aislado `insightbloom-sandboxes` (que ya existe vía Helm, junto con su `ResourceQuota`/`LimitRange` — sin cambios ahí), accesible a través del gateway de herramientas existente (mismo patrón de sesión que drawio/Etherpad/Excalidraw), y limpiado automáticamente al expirar.

## Decisión de diseño clave: sin fabric8, cliente K8s hand-rolled

El proyecto no usa frameworks pesados en ningún lado — ni siquiera para llamadas HTTP salientes: `GroqLlmClient` (`insightbloom-survey`) hand-rolla las llamadas a Groq con `java.net.http.HttpClient` puro. Se sigue el mismo patrón: un `KubernetesPodClient` nuevo en `insightbloom-users` que habla directo al API server de Kubernetes (`https://kubernetes.default.svc`, alcanzable desde cualquier pod del cluster) usando el token del ServiceAccount montado por defecto (`/var/run/secrets/kubernetes.io/serviceaccount/{token,ca.crt}`) — sin añadir la dependencia `fabric8-kubernetes-client` (pesada, con generación de código, watches, etc. que no se necesitan para crear/borrar 3 tipos de recursos). Solo se necesitan 3 verbos REST: `POST/DELETE /api/v1/namespaces/{ns}/pods`, `POST/DELETE /api/v1/namespaces/{ns}/services`, `POST/PUT/DELETE /apis/networking.k8s.io/v1/namespaces/{ns}/networkpolicies`.

## Sub-fases

### 3a — Provisión de Pods on-demand (sin acceso real todavía)

Rediseño de `AssignSandboxUseCase`: hoy busca un slot "pre-sembrado" que nunca existió. El modelo correcto, dado que `GenerateWorkspaceDownloadUrlUseCase` ya asume "un sandbox por (conferencia, usuario)": **crear el Pod la primera vez que un usuario lo pide**, hasta `conference.sandboxPoolSize` sandboxes concurrentes por evento (default 1 si no está configurado).

- `execute(conferenceUuid, userUuid)`:
  1. Si ya existe `findByConferenceAndUser` y el Pod sigue vivo → devolverlo (idempotente, recarga de página).
  2. Si `count(sandboxes activos de esta conferencia) >= sandboxPoolSize` → `sandbox_pool_full` (409, mismo contrato que hoy).
  3. Elegir el próximo slot libre (0..poolSize-1), nombre determinístico `sandbox-{8 chars del conferenceUuid}-{slot}`.
  4. `KubernetesPodClient.createPod(...)` con el spec ya documentado en `sandbox-pool.yaml` (securityContext non-root, drop ALL caps, seccomp RuntimeDefault, recursos de `.Values.sandbox.resources` ahora pasados a `insightbloom-users` por env vars, imagen según `conference.sandboxVariant`, `restartPolicy: Never`).
  5. `KubernetesPodClient.createService(...)` — un `Service` `ClusterIP` con selector al label único del pod, para tener un DNS estable (`sandbox-{slot}.insightbloom-sandboxes.svc.cluster.local:8080`) que no se rompa si el pod IP cambia.
  6. Guardar la fila `Sandbox` (ya asignada desde el momento de creación — se elimina el concepto de "slot vacío pre-sembrado").
  7. Devolver `{sandboxUuid, sandboxSlot, status: PENDING|READY, gatewayUrl}` — `status` viene de un `KubernetesPodClient.getPodPhase(name)` (Pending mientras hace pull+boot, Running cuando el `startupProbe` documentado pasa).
- RBAC: nuevo `templates/sandbox-rbac.yaml` — `Role` en el namespace `insightbloom-sandboxes` (verbos `create,get,list,delete` sobre `pods`, `services`) + `RoleBinding` al mismo ServiceAccount que ya usa el Deployment de `insightbloom-users` (`insightbloom.serviceAccountName`, ya montado con `automountServiceAccountToken: true` por default) — no se crea un ServiceAccount nuevo, se reusa el existente vía RBAC scoped por namespace.
- `PurgeSandboxPoolUseCase.execute` se extiende: para cada `Sandbox` vencido, llama `KubernetesPodClient.deletePod/deleteService` **antes** de `sandboxRepository.deleteExpired` (ya corre cada 5 min en el scheduler existente de `UsersApplication.java` — no se agrega infraestructura nueva de cron).
- Frontend `IdePage.vue`: agrega polling (cada 3s, timeout 5 min) mientras `status === 'PENDING'`, mostrando "Preparando tu ambiente..." — mismo patrón de polling que ya existe en otras páginas live de la app (revisar `RemoteControlPage.vue`/`PresentationPage.vue` para el helper de polling si existe uno reusable).

### 3b — Ruteo dinámico gateway → pod del usuario

drawio/Etherpad/Excalidraw son instancias **compartidas** (un target fijo por host, resuelto una sola vez en `GatewayApplication.parseRoutes` desde `GATEWAY_ROUTES`). El IDE es **por (conferencia, usuario)** — no hay un target fijo. Se extiende `AuthGateHandler` sin tocar su forma general:

- `SessionCache` pasa de `Map<String,Instant>` a `Map<String,Session>` con `record Session(Instant expiry, String dynamicTarget)` (`dynamicTarget` nulo para las herramientas compartidas de siempre).
- `isTokenValid`/`checkAuth`: cuando el host es el nuevo `ide-insightbloom.v1.rafex.cloud` (config `GATEWAY_IDE_HOST`), en vez de solo validar el token contra `/api/v1/auth/validate`, llama a un endpoint nuevo `GET /internal/sandbox-target?conferenceId=&token=` en `insightbloom-users` (protegido con el header `X-Internal-Auth` ya establecido en la Fase 4 de la migración a `backend/common`) que resuelve el Service DNS del sandbox activo del usuario y lo devuelve; ese valor se guarda como `dynamicTarget` al mintear la sesión de gateway.
- `proxy()`: `target = session.dynamicTarget() != null ? session.dynamicTarget() : routesByHost.get(host)`.
- El WebSocket ya está resuelto por el trabajo previo de esta sesión (`WebSocketProxyCreator`/`JettyWebSocketEndpointBridge`, construido para Etherpad) — code-server también necesita WS (editor en vivo + terminal), se reusa el mismo mecanismo sin cambios, solo que ahora también puede resolver `dynamicTarget`.
- Nuevo Ingress `ingressIde` (mismo patrón que `ingressDrawio`/`ingressEtherpad`) apuntando a `insightbloom-toolsgateway`.
- `IdePage.vue`: `gatewayUrl` deja de ser el host estático — se construye como `https://ide-insightbloom.v1.rafex.cloud/?ib_token={token}` (mismo patrón `?ib_token=` que ya usa `AuthGateHandler.checkAuth` para las otras herramientas).

### 3c — Aislamiento de red + activar el toggle de Internet ya construido

`SetSandboxInternetUseCase` existe (confirmado) pero no está wireado a ninguna ruta HTTP — es código muerto hoy. Se completa junto con el aislamiento real:

- `templates/sandbox-networkpolicy.yaml` nuevo: default-deny egress para todo pod en `insightbloom-sandboxes` excepto DNS (kube-dns) y el propio API server (si aplica) — el chart ya tiene el patrón de `network-policy.yaml` para el namespace principal, se replica el mismo estilo (`podSelector` + `policyTypes`) pero para egress.
- `SetSandboxInternetUseCase.execute` pasa de solo guardar el flag en SQLite a también crear/borrar (vía `KubernetesPodClient`) una `NetworkPolicy` adicional con `podSelector` sobre el label del pod específico que permite egress `0.0.0.0/0` — esto es lo que el comentario ya presente en el código ("NetworkPolicy se actualiza en paralelo, ver TASK-0050") anticipaba.
- Agregar la ruta HTTP faltante: `PUT /{id}/sandbox/internet` en `ConferenceHandler` con el mismo guard organizer-only (`extractToken` → `validateTokenUseCase` → `isOrganizerOrAdmin`) usado en `handleSetSandboxConfig`.
- Frontend: toggle en la misma sección donde ya está el resto de la config de sandbox (buscar dónde vive `sandboxVariant`/`sandboxPoolSize` en el dashboard, probablemente `EditConferencePage.vue` o una página dedicada).

## Verificación

- Backend: `mvn -o clean test` en `insightbloom-users` — nuevos tests con un fake `KubernetesPodClient` (interfaz, para no pegarle al API real en unit tests): pool lleno lanza `sandbox_pool_full`; segunda llamada del mismo usuario reusa el sandbox existente (no crea un segundo pod); purga llama delete antes de borrar la fila.
- `helm template` dry-run del chart completo (como se hizo para el fix de LimitRange en esta misma sesión) para validar RBAC/NetworkPolicy/Ingress nuevos sin desplegar.
- Deploy a k3s vía el pipeline existente, luego verificación real vía `ssh my-k3s` + `sudo kubectl get pods -n insightbloom-sandboxes -w`: crear un evento con `CODE_IDE`, pedir el sandbox vía la API/UI, confirmar que aparece un Pod real `Running`, abrir la URL del gateway y confirmar que carga code-server (browser preview), esperar a que expire (o forzar `expiresAt` corto en una conferencia de prueba) y confirmar que el Pod+Service se borran solos en el siguiente tick del purge.
- Extender `scripts/run/smoke-test-events.sh` (ya existe en el repo) con un paso opcional `--with-ide` que pida el sandbox tras crear el evento y verifique que el estado pasa de `PENDING` a `READY` dentro de un timeout razonable.

### Critical Files (Fase 3)
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/outbound/kubernetes/KubernetesPodClient.java (nuevo)
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/application/usecases/AssignSandboxUseCase.java (rediseño)
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/application/usecases/PurgeSandboxPoolUseCase.java
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/application/usecases/SetSandboxInternetUseCase.java (activar, hoy código muerto)
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/domain/model/Sandbox.java
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/outbound/sqlite/SqliteSandboxRepository.java
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/inbound/http/handlers/ConferenceHandler.java (ruta `/sandbox/internet` faltante)
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/bootstrap/UsersApplication.java
- backend/services/insightbloom-tools-gateway/src/main/java/dev/rafex/insightbloom/toolsgateway/AuthGateHandler.java (SessionCache dinámico)
- backend/services/insightbloom-tools-gateway/src/main/java/dev/rafex/insightbloom/toolsgateway/SessionCache.java
- backend/services/insightbloom-tools-gateway/src/main/java/dev/rafex/insightbloom/toolsgateway/GatewayApplication.java
- infra/helm/charts/insightbloom/templates/sandbox-pool.yaml (referencia del spec de Pod ya documentado — deja de ser solo documentativo)
- infra/helm/charts/insightbloom/templates/sandbox-rbac.yaml (nuevo)
- infra/helm/charts/insightbloom/templates/sandbox-networkpolicy.yaml (nuevo)
- infra/helm/charts/insightbloom/templates/ingress-ide.yaml (nuevo, mismo patrón que ingress-drawio/etherpad)
- infra/helm/charts/insightbloom/values.yaml
- frontend/web/src/pages/conference/IdePage.vue
- frontend/web/src/services/api/usersApi.ts / types.ts
- scripts/run/smoke-test-events.sh (extensión `--with-ide`)

---

# Fase 4 del IDE — separar code-server (Debian) del runtime de ejecución (Alpine)

## Context

Tras completar Fase 3 (3a/3b/3c, arriba), el pipeline de CI que construye `insightbloom-code-ide` falló repetidamente al instalar `code-server` vía `npm install -g` dentro de la imagen Alpine: primero por requerir `--unsafe-perm` como root, luego (tras corregir eso) por una corrupción sistemática y determinística de tarballs de npm/cacache al extraer como usuario no-root con un prefix no estándar bajo BuildKit (reproducido 3/3 veces, siempre los mismos ~140 paquetes afectados — no era flakiness de red). Un primer fix movió la instalación a una etapa de build descartable (`node:22-alpine`, root, prefix por defecto) y solo copiaba los artefactos ya instalados a la imagen final — mitigaba el síntoma pero no estaba probado en CI todavía.

Investigando el instalador oficial de code-server (`install.sh`) se confirmó la causa raíz real: **Alpine no tiene releases standalone de code-server** (sus dependencias nativas, como `node-pty`, están compiladas contra glibc) — por eso el propio instalador oficial cae a `npm install` específicamente en Alpine/FreeBSD. En Debian/Ubuntu sí existen releases standalone precompiladas (`code-server-X.Y.Z-linux-amd64.tar.gz`), que es el método que usa la propia imagen Docker oficial de `coder/code-server`. Migrar el contenedor que corre code-server a Debian permite **eliminar npm por completo** de esa instalación (solo `curl` + `tar`, sin postinstall, sin dependencias transitivas, sin --unsafe-perm en ningún punto) — arregla la causa raíz de forma permanente, no solo mitiga el síntoma.

En paralelo, el usuario propuso separar responsabilidades en dos contenedores dentro del mismo Pod de sandbox: un contenedor `ide` (Debian, solo code-server — el editor/servidor web) y un contenedor `runtime` (Alpine, el toolchain de java/python/node) — la terminal integrada de code-server debe ejecutar comandos en el contenedor `runtime`, no en `ide`. Esto además reduce el acoplamiento: la imagen `ide` deja de necesitar toolchains completos (más liviana, y su superficie de build ya no depende de compiladores/paquetes de cada lenguaje), y las imágenes `runtime` dejan de necesitar código de VS Code.

Decisiones ya confirmadas con el usuario:
- **Mecanismo de terminal remota: socat sobre loopback intra-Pod**, no `kubectl exec`. El contenedor `runtime` corre un pequeño servidor `socat` (`TCP-LISTEN` → `EXEC` con pty) escuchando solo en `127.0.0.1:7681` (containers del mismo Pod comparten namespace de red, por lo que este tráfico ni siquiera atraviesa el CNI — las `NetworkPolicy` no aplican a loopback intra-Pod). El perfil de terminal por defecto de code-server se configura para lanzar `socat STDIO TCP:127.0.0.1:7681` en vez de un shell local. Esto mantiene el hardening ya documentado en `KubernetesPodClient` (sandboxes sin acceso a la API de Kubernetes, `automountServiceAccountToken: false` se mantiene en ambos contenedores) — cero RBAC nuevo, cero token de ServiceAccount en el sandbox, cero binario `kubectl` empaquetado (sin precedente en el repo, se evita).
- **Imagen `ide` única y universal** (no una por variante): un solo code-server con las extensiones de las 3 variantes preinstaladas (Java+Maven, Python+Pylance, Prettier/ESLint/Volar/React/Tailwind). Simplifica el CI (1 imagen `ide` en vez de 3) — el costo extra es solo el peso de las extensiones (unos MB), no de toolchains completos. El contenedor `runtime` sigue siendo específico por variante (python/java/web), igual que hoy.

## Diseño

### Imágenes Docker

- **`infra/docker/Dockerfile.code-ide-server`** (nuevo, reemplaza `Dockerfile.code-ide`): `FROM debian:12-slim`. Instala `curl ca-certificates socat dumb-init` vía `apt-get`. Descarga el release standalone oficial de code-server (`curl` a `https://github.com/coder/code-server/releases/download/v${VERSION}/code-server-${VERSION}-linux-amd64.tar.gz`, `tar -xzf --strip-components=1`, symlink a `/usr/local/bin/code-server`) — **sin npm en absoluto**, sin postinstall, versión fijada por `ARG CODE_SERVER_VERSION`. Crea el usuario no-root `coder` (mismo uid/gid 1000 que ya usa el resto del stack). Como `coder`, instala las extensiones de las 3 variantes en una sola pasada (`code-server --install-extension ...` con los 11 IDs ya usados hoy en las 3 variantes actuales). Copia (`COPY --chown=coder:coder`) un `settings.json` baked-in a `/home/coder/.local/share/code-server/User/settings.json` con:
  ```json
  {
    "terminal.integrated.profiles.linux": {
      "runtime": { "path": "/usr/bin/socat", "args": ["STDIO", "TCP:127.0.0.1:7681"] }
    },
    "terminal.integrated.defaultProfile.linux": "runtime"
  }
  ```
  Mismo `ENTRYPOINT`/`CMD` que hoy (`dumb-init -- code-server --bind-addr 0.0.0.0:8080 /home/coder/workspace --disable-auth`), mismo puerto 8080, mismos directorios `workspace`/`db`.

- **`infra/docker/Dockerfile.code-ide-runtime.python|java|web`** (nuevos, reemplazan las variantes actuales `Dockerfile.code-ide.*`): `FROM alpine:3.21` directo (ya no `FROM ...code-ide:base` — cada variante es un build independiente y paralelizable en CI, sin cadena). Instalan el toolchain igual que hoy (java: `openjdk21 maven`; python: `python3-dev py3-pip gcc musl-dev libffi-dev openssl-dev` + los paquetes pip; web: `npm install -g typescript ts-node @vue/cli ...`) más `socat bash dumb-init git sqlite curl ca-certificates` — **sin `nodejs npm`/code-server en la imagen base** salvo lo que cada variante ya necesita para su propio toolchain (web variant sigue necesitando node/npm como parte del toolchain en sí, no para instalar code-server). Mismo usuario `coder` uid/gid 1000 (clave para que el volumen compartido `workspace` tenga permisos consistentes entre ambos contenedores vía el `fsGroup` ya existente). **Sin extensiones de code-server** — eso ya no vive aquí. `ENTRYPOINT ["dumb-init", "--"]`, `CMD` en forma JSON-array (sin shell intermedio) apuntando a socat:
  ```
  CMD ["socat", "TCP-LISTEN:7681,reuseaddr,fork,bind=127.0.0.1", "EXEC:/bin/bash -l,pty,stderr,setsid,sigint,sane"]
  ```
  (forma exec-array: cada elemento se pasa literal a `execve`, sin necesitar comillas de shell — patrón estándar de socat para "shell sobre TCP").

### CI (`.github/workflows/publish_container.yml`)

El job `build-and-push-code-ide` (ya deliberadamente fuera del `needs:` de `deploy-after-publish`, ver Fase 3 CI fixes arriba) se reestructura: build de `code-ide-server` (1 imagen) + 3 builds de `code-ide-runtime` (python/java/web) — como ya no hay cadena `FROM` entre ellas, los 4 builds pueden correr como pasos independientes/matriz en paralelo en vez de secuenciales (mejora de tiempo de CI, no solo de robustez). Tags nuevos: `ghcr.io/rafex/insightbloom-code-ide-server:latest` y `ghcr.io/rafex/insightbloom-code-ide-runtime:{python,java,web}`.

### `KubernetesPodClient.java` — Pod de 2 contenedores

`buildPodBody` (hoy arma un `containers[]` de un solo elemento) pasa a construir dos:
- Contenedor `ide`: imagen = nuevo config `serverImage` (tag único, no por variante), puerto = el `port` actual (8080), probes tcpSocket igual que hoy, `volumeMounts`: `workspace` (+ `database` si se sigue usando). Recursos: nuevo set de límites más liviano (config separada en `values.yaml`, ej. `sandbox.resources.ide.*`).
- Contenedor `runtime`: imagen = nuevo config `runtimeImageBase + ":" + variant`, sin puerto expuesto en el `Service` (solo 7681 en loopback, no forma parte del `Service` del Pod), mismos `volumeMounts` (`workspace`+`database`), env `EXTRA_PACKAGES`/`REMOTE_GIT_URL` (se conservan, aplican naturalmente a este contenedor si algún día se consumen — hoy no tienen consumidor, no es parte de este cambio). Recursos: set más generoso (compilar/ejecutar código del usuario). Probes tcpSocket sobre 7681.
- Ambos contenedores: mismo `securityContext` no-root (mismo uid/gid 1000 = `coder`, consistente con el volumen compartido), `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]` — **corrección de bug existente**: hoy `capabilities` está incorrectamente en el `securityContext` a nivel Pod (campo inválido ahí, se ignora silenciosamente); se mueve correctamente a nivel de cada contenedor.
- Pod-level: `automountServiceAccountToken: false` se mantiene (ninguno de los dos contenedores necesita la API de Kubernetes).
- `Service`: sin cambios de fondo — sigue seleccionando `sandbox-pod: {podName}` y enrutando solo al puerto del contenedor `ide` (K8s Services enrutan por Pod+puerto, no por contenedor — el puerto 7681 de `runtime` nunca se expone vía Service, solo accesible por loopback intra-Pod).

### Helm (`values.yaml`, RBAC sin cambios)

- `sandbox.images.server` (imagen+tag único) reemplaza `sandbox.images.base`.
- `sandbox.images.runtimeBase` + se reutiliza el mecanismo existente `variants.{python,java,web}.tag`.
- Nuevos bloques `sandbox.resources.ide.*` y `sandbox.resources.runtime.*` (cpu/memory request/limit) reemplazando el set único actual.
- `infra/helm/charts/insightbloom/templates/sandbox-rbac.yaml`: **sin cambios** — el mecanismo socat/loopback no requiere `pods/exec` ni ningún verbo nuevo.
- `infra/helm/charts/insightbloom/templates/sandbox-networkpolicy.yaml`: **sin cambios** — tráfico intra-Pod no pasa por NetworkPolicy.

### Riesgo conocido, no bloqueante

Pequeña carrera posible: `getPhase` (usado por el polling de `AssignSandboxUseCase`/`IdePage.vue`) refleja la fase del Pod, no el estado "Ready" de cada contenedor — es técnicamente posible que el usuario abra la terminal justo antes de que el `socat` del contenedor `runtime` termine de levantar (arranque casi instantáneo, ventana de riesgo de bajo impacto: reintentar conexión). No se resuelve en este cambio; se deja como nota si llegara a manifestarse en la práctica.

## Sub-fases (con confirmación entre cada una, mismo patrón que Fase 3)

- **4a — Imágenes + CI**: nuevos Dockerfiles (`code-ide-server`, `code-ide-runtime.*`), `settings.json` baked-in, restructurar el job de CI a build en paralelo con los tags nuevos. Verificación: los 4 builds pasan en CI y las imágenes quedan publicadas en GHCR (sin tocar aún `KubernetesPodClient`/backend — las imágenes nuevas quedan sin usar hasta 4b).
- **4b — Backend + Helm**: reescribir `KubernetesPodClient.buildPodBody` para 2 contenedores, corregir el bug de `capabilities` a nivel Pod, `values.yaml` con imágenes/recursos separados, wiring en `UsersApplication.java`. Verificación: `mvn -o clean test` (tests existentes con el fake orchestrator siguen pasando), `helm template` dry-run del chart completo.
- **4c — Verificación end-to-end**: deploy a k3s, pedir un sandbox nuevo, `sudo kubectl get pods -n insightbloom-sandboxes` confirma 2/2 contenedores `Running`, abrir code-server en el browser, abrir una terminal y confirmar que corre en el contenedor `runtime` (ej. `hostname`/`python3 --version` reflejan el toolchain de `runtime`, no `ide`), editar un archivo en el editor y confirmar que es visible desde la terminal (volumen `workspace` compartido funciona).

### Critical Files (Fase 4)
- infra/docker/Dockerfile.code-ide-server (nuevo, reemplaza Dockerfile.code-ide)
- infra/docker/Dockerfile.code-ide-runtime.python / .java / .web (nuevos, reemplazan Dockerfile.code-ide.python/java/web)
- .github/workflows/publish_container.yml (job build-and-push-code-ide)
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/outbound/kubernetes/KubernetesPodClient.java (buildPodBody: 2 contenedores)
- backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/bootstrap/UsersApplication.java (wiring de nuevas env vars de imágenes/recursos)
- infra/helm/charts/insightbloom/values.yaml (sandbox.images.server/runtimeBase, sandbox.resources.ide/runtime)
- infra/helm/charts/insightbloom/templates/deployment.yaml (env vars nuevas hacia insightbloom-users)

---

# Fase 5 — Tests en dos niveles: unitarios (ya existen) + integración bajo demanda

## Context

`ci.yml` ya corre tests unitarios en cada push/PR a `main`: `mvnw clean verify` (JUnit5 + Mockito, todo mockeado — sin HTTP real, sin SQLite real) para los 7 microservicios Java vía Maven Reactor, `vitest run` para el frontend (14 archivos, sobre todo clientes de API), y `pytest` para `chat`/`telegram`. Eso ya cubre "fase 1" razonablemente bien.

Lo que falta es una "fase 2": tests que efectivamente levanten varios servicios reales juntos y verifiquen que la integración entre ellos (no cada uno mockeado por separado) funciona — por ejemplo, que un usuario creado en `insightbloom-users` sea visible para `insightbloom-ingest`, o que una moderación en `insightbloom-moderation` se refleje en las estadísticas de `insightbloom-stats` vía `insightbloom-query`. El usuario pidió explícitamente que esta fase **no corra en automático** (es lenta, requiere levantar contenedores) — solo bajo demanda.

Ya existe `infra/compose/local.yml`, que define 5 servicios (`users`, `moderation`, `stats`, `query`, `ingest`) + `web`, con su grafo de dependencias (`depends_on: condition: service_healthy`) — pero **tiene un bug real**: el `healthcheck` de `insightbloom-users` apunta a `GET /health`, un endpoint que no existe en ningún lado del código (confirmado por grep en todo `backend/`) — y ninguno de los otros 4 servicios tiene `healthcheck` propio, así que las condiciones `service_healthy` de las que dependen (`query` depende de `stats`+`moderation`, `ingest` depende de `users`+`moderation`+`stats`) nunca podrían resolverse hoy. Lo que sí existe y funciona en los 5 servicios es `GET /version` (`VersionHandler`, registrado en cada `*Application.java` vía `backend/common`) — se reusa eso como healthcheck real, en vez de inventar un endpoint nuevo.

Decisión confirmada con el usuario: los tests de integración usan **ambos** mecanismos que ya existen o casi existen en el repo — tests estructurados en Java (JUnit5 + Maven Failsafe, mismo framework que ya usan los unitarios) para verificar comportamiento cruzado entre servicios con asserts reales sobre JSON, y el patrón bash+curl+jq que ya usa `scripts/run/smoke-test-events.sh` (hoy pensado para correr manualmente contra prod), generalizado para apuntar a `localhost` como chequeo complementario de humo. Alcance de servicios: los 5 que ya están en `infra/compose/local.yml` (no se agregan `survey`/`tools-gateway`/`chat`/`telegram`/`presentations` en esta primera fase — necesitan LLM keys, NATS, o sandboxes reales, mucho más infraestructura para levantar en CI).

## Diseño

### 5a — Arreglar el compose para que `up --wait` funcione de verdad

- `infra/compose/local.yml`: cambiar el `healthcheck` de `insightbloom-users` de `GET /health` a `GET /version` (endpoint real, ya devuelve 200 con JSON `{service, version, ...}` vía `VersionHandler`).
- Agregar el mismo bloque `healthcheck` (test `wget -qO- http://localhost:{port}/version`, mismo `interval`/`timeout`/`retries` que ya usa `users`) a `moderation`, `stats`, `query`, `ingest` — hoy no tienen ninguno, por lo que sus propios `depends_on: condition: service_healthy` (los que dependen de ellos) nunca podrían resolverse.
- Verificación de esta sub-fase por sí sola: `docker compose -f infra/compose/local.yml up --build --wait` debe terminar en estado sano (todos los healthchecks en verde) antes de tocar nada más — es el prerequisito duro de todo lo que sigue.

### 5b — Tests de integración en Java (JUnit5 + Maven Failsafe)

- `backend/pom.xml` (parent): agregar `maven-failsafe-plugin` a `<pluginManagement>`, bindeado a las fases estándar `integration-test`/`verify`, con el patrón de inclusión por defecto de Failsafe (`*IT.java`) — así `mvn test`/`mvn verify` normales (los que ya corre `ci.yml`) siguen sin verse afectados, porque Surefire (que corre los `*Test.java` existentes) ignora `*IT.java` por convención y viceversa. Se agrega un profile Maven `integration` que activa Failsafe (para no correrlo por accidente en cada `mvn verify` normal).
- Nuevos tests `src/test/java/.../it/*IT.java`, uno representativo por cada uno de los 5 servicios para arrancar (ej. `UsersCrossServiceIT.java`, `IngestReflectsModerationIT.java`) — usan `java.net.http.HttpClient` liso (mismo patrón hand-rolled que ya usa `GroqLlmClient`/`KubernetesPodClient`, sin RestAssured ni dependencias nuevas) apuntando a `http://localhost:{port}` (los mismos puertos que expone `local.yml`, configurables por system property con ese default). Ejemplo de flujo a cubrir: crear un evento + registrar un usuario en `users`, moderar algo en `moderation`, y verificar vía `query` que la agregación cruzada refleja ambos cambios — esto es exactamente lo que los tests unitarios mockeados de hoy no pueden probar.
- Failsafe solo corre los tests, no levanta ni tira abajo el stack — eso lo hace el workflow (5c). No se introduce Testcontainers (dependencia nueva pesada, y el repo ya tiene un compose file que cumple el mismo rol).

### 5c — Script de humo complementario + workflow on-demand

- Nuevo `scripts/test/integration-smoke.sh`: mismo idioma curl+jq que `scripts/run/smoke-test-events.sh` (que se deja intacto, sigue siendo la herramienta manual contra prod/staging), pero parametrizado a `localhost` y con credenciales de un usuario admin sembrado para la corrida de CI. Cubre 1-2 flujos felices end-to-end como chequeo de humo adicional, en paralelo/después de los tests de Failsafe — no reemplaza los asserts estructurados de 5b, los complementa como una verificación black-box más simple.
- Nuevo `.github/workflows/integration-tests.yml`, **`on: workflow_dispatch:` únicamente** (sin `push`/`pull_request`) — coherente con "no se ejecutarán en automático". Un solo job:
  1. `actions/checkout@v6` + `actions/setup-java@v5` (mismo patrón que `ci.yml`).
  2. `./mvnw -f pom.xml -pl backend/services/insightbloom-users,backend/services/insightbloom-moderation,backend/services/insightbloom-stats,backend/services/insightbloom-query,backend/services/insightbloom-ingest -am package -DskipTests` — necesario porque `infra/docker/Dockerfile.java` construye cada imagen a partir de un JAR ya compilado (`JAR_FILE` build-arg), no compila dentro del contenedor.
  3. `docker compose -f infra/compose/local.yml up --build --wait` (falla rápido si algún healthcheck de 5a no resuelve).
  4. `./mvnw -f pom.xml verify -Pintegration -pl <mismos 5 módulos> -am` (corre los `*IT.java` de 5b contra el stack ya levantado).
  5. `bash scripts/test/integration-smoke.sh` (5c) contra `localhost`.
  6. `if: always()`: `docker compose -f infra/compose/local.yml logs` (para debug si algo falló) + `docker compose -f infra/compose/local.yml down -v` (limpia también los volúmenes nombrados, cada corrida arranca con SQLite limpio).
- Sin relación `needs:`/dependencia con `ci.yml` ni `publish_container.yml` — mismo principio ya aplicado al job de `code-ide` en esta sesión (lo lento/opcional no debe acoplarse a lo que corre en cada push).

## Verificación

- Local, antes de tocar el workflow: `docker compose -f infra/compose/local.yml up --build --wait` debe terminar sano (valida 5a).
- Local: `./mvnw -f pom.xml verify -Pintegration -pl <los 5 módulos> -am` contra el stack ya levantado debe pasar los `*IT.java` nuevos (valida 5b).
- Local: `bash scripts/test/integration-smoke.sh` contra `localhost` debe pasar igual que `smoke-test-events.sh` pasa hoy contra prod (valida 5c).
- Disparar el workflow nuevo manualmente (`gh workflow run integration-tests.yml` o desde la UI de Actions) y confirmar: (a) corre solo cuando se dispara a mano, nunca en push/PR; (b) levanta el stack, corre ambas capas de test, y limpia al final; (c) provocar una falla deliberada (ej. romper temporalmente un endpoint) para confirmar que el job efectivamente falla y muestra logs útiles, no que queda verde pase lo que pase.

### Critical Files (Fase 5)
- infra/compose/local.yml (fix healthcheck /health→/version en users, agregar a moderation/stats/query/ingest)
- backend/pom.xml (maven-failsafe-plugin + profile `integration`)
- backend/services/insightbloom-users/src/test/java/.../it/*IT.java (nuevo, patrón representativo)
- backend/services/insightbloom-{moderation,stats,query,ingest}/src/test/java/.../it/*IT.java (nuevos, mismo patrón)
- scripts/run/smoke-test-events.sh (referencia de patrón, sin cambios)
- scripts/test/integration-smoke.sh (nuevo)
- .github/workflows/integration-tests.yml (nuevo, workflow_dispatch únicamente)
