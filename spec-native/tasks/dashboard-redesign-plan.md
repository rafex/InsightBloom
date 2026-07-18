# Rediseño del Dashboard de organizador: Inicio, Eventos y Usuarios

## Contexto

El dashboard de organizador tiene hoy tres problemas de usabilidad que se piden resolver:

1. **Inicio** duplica navegación que ya vive en el sidebar ("+ Nueva conferencia", "Ver todas tus
   conferencias") y sus 4 tarjetas de estadísticas no distinguen estado real de los eventos
   (activos/expirados) ni de los usuarios (activos vs solo registrados).
2. **Eventos** (`ConferencesListPage.vue`) tiene 8+ botones sueltos en la columna Acciones sin
   agrupar, "QR" como botón de texto en vez de icono, y "Live"/"Presentar" con nombres que no
   reflejan bien su función real.
3. **Usuarios** (`AdminUsersPage.vue`) muestra demasiadas columnas de una vez, no tiene filtro por
   rol, el filtro de estado es solo cosmético (filtra la página ya cargada, no la fuente completa
   paginada), no hay orden alfabético, y no hay una vista de detalle por usuario (a qué eventos
   está inscrito, si respondió la encuesta, si descargó su certificado, última conexión).

Investigado el código actual (ver hallazgos por archivo abajo) para no proponer nada que ya
exista, y para que las partes nuevas seleccionan por defecto sensato en toda pieza donde el pedido
del usuario dejaba una interpretación abierta (marcadas explícitamente como "interpretación" para
que se puedan corregir al leer este plan).

**Interpretaciones tomadas** (confirmar al leer, son las únicas ambigüedades reales del pedido):
- *Acciones → "Presentación"* = la página de **subir/gestionar la presentación**
  (`PresentationManagePage.vue`, "aquí se carga la presentación" — el usuario lo aclaró
  explícitamente). *Modos → "Presentador"* (dropdown) = las acciones de **presentar en vivo**:
  "Presentar" (`SpeakerPanelPage.vue`) + "Encuesta" (`SurveyManagePage.vue`) — se leen como cosas
  que el presentador hace DURANTE el evento, distinto de subir contenido antes.
- *Modos → "Moderación" → "Editor Monaco"* navega a la sección de estado de sandboxes ya
  construida en `EditConferencePage.vue` (tabla de Pods + botón por asiento que abre
  `WorkspaceFileEditor.vue`) — no existe hoy una página separada de "elegir alumno primero", y
  construir una sería una feature nueva no pedida; se reusa lo que ya existe.
- *Usuarios: "activos"* (en Inicio) = alumnos únicos registrados en eventos del organizador cuyo
  `User.status == ACTIVE` (mismo campo que ya gobierna banear/reactivar en `AdminUsersPage.vue`) —
  no un concepto nuevo, solo una variante con filtro del conteo que ya existe hoy.

---

## Fase 1 — Inicio del dashboard (`DashboardHome.vue`)

**Quitar**: botón "+ Nueva conferencia" (línea ~5) y link "Ver todas tus conferencias →" (línea
~34) — ya están en el sidebar (`DashboardLayout.vue`). Ambos elementos tienen `id` referenciados
por el tour de onboarding (`ORGANIZER_TOUR_STEPS`, líneas ~73-76) — hay que quitar/re-anclar esos
pasos del tour también, o el tour se rompe silenciosamente buscando un `id` que ya no existe.

**Reemplazar** las 4 tarjetas sueltas por dos grupos (reusar el mismo `.summary-grid`/
`.summary-card`, con un `h2` de grupo arriba de cada uno — el estilo `h2` ya existe en el archivo
sin usar, quedó preparado para esto):

- **Eventos**: activos / registrados / expirados — **sin backend nuevo**, se calculan en el
  cliente sobre el array que `getConferences()` ya trae completo (mismo dato que hoy alimenta la
  tarjeta "Conferencias"):
  - `registrados` = `conferences.value.length` (igual que hoy).
  - `activos` = `conferences.value.filter(c => c.status === 'ACTIVE').length`.
  - `expirados` = `conferences.value.filter(c => c.expiresAt && isExpired(c.expiresAt)).length`
    (reusar la función `isExpired()` ya definida en `ConferencesListPage.vue:142` — moverla a un
    util compartido, ej. `frontend/web/src/utils/dates.ts`, ya que ahora la necesitan dos páginas).
- **Usuarios**: registrados / activos:
  - `registrados` = igual que hoy (`getUniqueRegisteredAttendeesCount`, sin cambios).
  - `activos` = **nuevo** `CountActiveRegisteredAttendeesUseCase`, mismo patrón que
    `CountUniqueRegisteredAttendeesUseCase` (`application/usecases/`) pero agregando
    `AND users.status = 'ACTIVE'` al join contra la tabla `users` — nuevo método en
    `ConferenceMembershipRepository`/`UserRepository` (el que corresponda según cómo esté armado
    el query existente), nuevo endpoint `GET /conferences/attendees/active-summary` en
    `ConferenceHandler.java` (mismo lugar que el endpoint de `registered-summary`), nueva función
    `getActiveRegisteredAttendeesCount()` en `usersApi.ts`.

Archivos: `frontend/web/src/pages/dashboard/DashboardHome.vue`, nuevo
`frontend/web/src/utils/dates.ts` (o el que ya exista, si hay uno — revisar antes de crear),
`backend/services/insightbloom-users/.../application/usecases/CountActiveRegisteredAttendeesUseCase.java`,
`ConferenceHandler.java`, `usersApi.ts`.

**Verificación**: cargar `/dashboard` como organizador con al menos un evento expirado y uno
activo, confirmar los 3 números de "Eventos" suman correcto contra la lista real; confirmar que
"Usuarios activos" ≤ "Usuarios registrados" siempre.

---

## Fase 2 — Tabla de Eventos: QR como icono + reordenar

- Botón "QR" (`ConferencesListPage.vue:47`, hoy texto plano) pasa a **icono** (SVG inline, mismo
  patrón que ya usa el botón de basura en la misma tabla, líneas 58-64 — no agregar una librería
  de iconos nueva por un solo icono) y se mueve a una columna **nueva, la primera de la tabla**
  (antes de "Nombre"), sin encabezado de texto (`th` vacío o con `sr-only "QR"` para accesibilidad).
- No toca `QrCodeModal.vue` ni su lógica — mismo `@click="qrTarget = c"`.

**Verificación**: la columna QR aparece primero, el ícono abre el modal igual que antes.

---

## Fase 3 — Tabla de Eventos: columna "Modos" + primer componente Dropdown

No existe hoy ningún componente de menú desplegable reusable en el frontend (se investigó a
fondo) — el único precedente es un dropdown hecho a mano en `ConferencePage.vue:33-37`
("Agregar a mi calendario"), **sin cierre al hacer click afuera**. Se construye un componente
nuevo `frontend/web/src/components/DropdownMenu.vue` (botón + lista de items via slot, cierre por
click-afuera con un listener manual en `document` — sin librería nueva) y se usa tanto para
"Presentador" como para "Moderación". "Público" queda como link suelto (no es un grupo).

Columna nueva "Modos" en `ConferencesListPage.vue`, con tres entradas:

- **Presentador** (`DropdownMenu`, visible si `PRESENTATION` o `SURVEY`): item "Presentar" →
  `SpeakerPanelPage.vue` (antes botón "Presentar" suelto, mismo target), item "Encuesta" →
  `SurveyManagePage.vue` (antes botón suelto). Cada item se oculta individualmente si su
  capacidad no está habilitada (mismo `hasCapability()` ya usado hoy).
- **Público** (antes "Live"): mismo link `<a target="_blank">` a `/c/{friendlyId}/presentation`,
  solo cambia el texto/label (de "🔴 Live" a "📺 Público" o similar) y el gate de capacidad
  (`PRESENTATION`, sin cambios).
- **Moderación** (`DropdownMenu`, visible si `WORD_CLOUD`): item "Mensajes" →
  `ModerationMessagesPage.vue`, item "Palabras/Nube" → `ModerationWordsPage.vue`, item **"Editor
  Monaco"** (nuevo, visible solo si `hasCapability(c, 'CODE_IDE')`) → navega a
  `/dashboard/conferences/{id}/edit#sandbox-status` (la sección ya construida en la Fase 3 del
  plan anterior — `EditConferencePage.vue`, tabla de estado de sandboxes con botón por asiento que
  abre `WorkspaceFileEditor.vue`).

Archivos: nuevo `frontend/web/src/components/DropdownMenu.vue`,
`frontend/web/src/pages/dashboard/ConferencesListPage.vue`.

**Verificación**: con un evento que tenga `PRESENTATION+SURVEY+WORD_CLOUD+CODE_IDE`, confirmar
que Presentador tiene 2 items y Moderación tiene 3; con un evento sin `CODE_IDE`, confirmar que
"Editor Monaco" no aparece; click afuera del menú lo cierra.

---

## Fase 4 — Tabla de Eventos: columna Acciones + Activar/Desactivar

Reordenar Acciones a: **Presentación** (`PresentationManagePage.vue`, gate `PRESENTATION`,
existía ya, solo se reubica) → **Activar/Desactivar** (nuevo) → **Editar** → **Eliminar** (icono
de basura, sin cambios). Los links de check-in/mapa de asientos (`seatingMode !== 'NONE'`) se
mantienen tal cual, no fueron mencionados para tocar.

**Activar/Desactivar**: visible **solo si `!c.expiresAt`** (pedido explícito del usuario — un
evento con fecha de expiración se desactiva solo, por tiempo, no manualmente). Alterna
`Conference.status` entre `ACTIVE`/`CLOSED` (el único otro valor del enum hoy, no hay estado
"paused" separado). Nuevo backend, mismo patrón que `setEventTypeActive`/`setRoleActive`
(`usersApi.ts:332-335`/`398-401`):
- Nuevo `SetConferenceActiveUseCase` (misma guarda de dueño/organizador que ya usa
  `UpdateConferenceUseCase`/borrado — revisar esa clase para replicar el chequeo exacto de
  ownership, no inventar uno nuevo).
- Nueva ruta `PUT /api/v1/conferences/{id}/active` en `ConferenceHandler.java`.
- Nueva función `setConferenceActive(uuid, active, token)` en `usersApi.ts`.

Archivos: `ConferencesListPage.vue`,
`backend/services/insightbloom-users/.../application/usecases/SetConferenceActiveUseCase.java`,
`ConferenceHandler.java`, `usersApi.ts`.

**Verificación**: un evento sin `expiresAt` muestra el botón y alterna estado correctamente
(refleja en la columna "Estado" ya existente); un evento CON `expiresAt` no muestra el botón.

---

## Fase 5 — Usuarios: tabla mínima + fila clickeable + vista de detalle

### 5.1 Tabla (`AdminUsersPage.vue`)
Reducir a 4 columnas: **ID usuario, Usuario, Estado, Acciones** (se quitan Email/Teléfono y Rol
de la vista de lista, se mueven al detalle). Fila clickeable en las celdas "ID usuario"/"Usuario"
→ navega a una página nueva `UserDetailPage.vue` (ruta `/dashboard/admin/users/{uuid}`, coherente
con que el resto de la app usa páginas ruteadas para vistas de detalle, no modales, excepto para
popups chicos como QR).

### 5.2 Filtros + orden — mover a server-side (hoy son 100% client-side sobre la página cargada,
lo cual es incorrecto con paginación real de 50 registros)
Encadenar `estado` (ya existe como filtro pero solo local — pasa a query param real) + **`rol`**
(nuevo, valores del enum `UserRole`: `ADMIN, ORGANIZER, MODERATOR, GUEST, ATTENDEE` — la UI de
edición actual hoy omite `GUEST` de su lista, corregir esa omisión de paso) + **orden
alfabético** (nuevo `sort=username` vs el default actual `created_at DESC`) a través de toda la
cadena: `AdminUserHandler.java` (nuevos query params `status`/`role`/`sort`) →
`ListUsersUseCase.execute(page, pageSize, status, role, sort)` → `UserRepository.findAll(...)`
(interfaz + `SqliteUserRepository` con `WHERE`/`ORDER BY` condicionales).

### 5.3 Vista de detalle (`UserDetailPage.vue`, nueva)
Campos ya disponibles hoy sin cambios de backend: email, teléfono, nombre, rol (todo ya está en
`UserView`/`GET /admin/users/{uuid}` si existe, o se agrega un `GET` por id si falta — confirmar
antes de asumir que ya existe un endpoint de detalle único, la investigación solo confirmó el de
listado paginado).

Campos que **requieren backend nuevo** (el más grande de todo el plan, cada uno es independiente):

- **Eventos en que está inscrito**: `ReservationRepository` no tiene `findByUser(userUuid)` hoy
  (solo `findByConference`/`findByConferenceAndUser`) — agregar el método al puerto +
  `SqliteReservationRepository`, nuevo endpoint `GET /admin/users/{uuid}/reservations` (devuelve
  conferenceUuid + nombre del evento, un join simple contra `conferences`).
- **Si respondió la encuesta**: hoy `SurveyResponseRepository.existsByUserAndConference` vive en
  el microservicio `insightbloom-survey`, separado, y es por-conferencia, no agregado por
  usuario. Opción más simple sin tocar el otro servicio: desde el frontend, una vez que se tiene
  la lista de eventos del usuario (punto anterior), hacer N llamadas a un endpoint existente o
  nuevo `GET /survey/api/v1/conferences/{id}/responses/exists?userUuid=...` en
  `insightbloom-survey` — evita cambios de esquema, solo un endpoint nuevo ahí.
- **Si descargó su certificado**: `DownloadEventRepository`/`RecordDownloadUseCase` hoy NO
  reciben `userUuid` — es un conteo agregado por conferencia, sin atribución por persona. Esto
  **sí exige** cambio de esquema: agregar columna `user_uuid` (nullable, para no romper conteos
  agregados históricos) a `download_events`, agregar el parámetro a
  `RecordDownloadUseCase.execute(conferenceUuid, kind, userUuid)` y a los call-sites en
  `ConferenceHandler.java` (líneas ~555 y ~592 — ahí se sabe el usuario porque el request ya está
  autenticado), y un nuevo método de consulta
  `existsByConferenceAndUserAndKind(conferenceUuid, userUuid, "certificate")`.
- **Última conexión**: no existe ningún tracking de login hoy (ni columna, ni escritura). Es lo
  más nuevo del plan: columna `last_login_at` en `users` (migración `addColumnIfMissing`, mismo
  patrón ya usado varias veces en `DatabaseManager.java`), escritura en el use case de login
  exitoso (revisar `LoginUseCase`/el que corresponda — hay que ubicarlo exactamente antes de
  tocarlo, no se investigó su código interno todavía), exposición en `UserView`.

Archivos: nuevo `frontend/web/src/pages/dashboard/UserDetailPage.vue`, ruta nueva en
`frontend/web/src/app/router/index.ts`, `AdminUsersPage.vue` (tabla + filtros), `adminApi.ts`
(nuevas funciones), backend: `AdminUserHandler.java`, `ListUsersUseCase.java`,
`UserRepository.java`/`SqliteUserRepository.java`, nuevo endpoint de reservas por usuario, nuevo
endpoint de encuesta-respondida en `insightbloom-survey`, cambio de esquema en
`download_events`/`RecordDownloadUseCase`, y el trabajo de `last_login_at` (login use case +
columna + `UserView`).

**Verificación**: filtrar por rol=ORGANIZER y estado=ACTIVE devuelve solo esos usuarios (no solo
en la página actual — probar con más de 50 usuarios si es posible, o revisar el SQL generado);
orden alfabético correcto; abrir el detalle de un usuario con al menos una inscripción, una
respuesta de encuesta y una descarga de certificado, confirmar que los 3 se reflejan; loguearse
como ese usuario y confirmar que "última conexión" se actualiza.

---

## Orden de entrega sugerido

Fases 1-4 son autocontenidas en `insightbloom-users` + frontend, bajo riesgo, se pueden entregar
juntas. Fase 5 es claramente la más grande — sugiero entregarla aparte, y dentro de ella, el
tracking de "última conexión" y "certificado descargado" (los dos cambios de esquema) son los que
más conviene revisar con más cuidado antes de escribir código, ya que tocan flujos ya en
producción (login, descarga de certificado) en vez de agregar algo nuevo aislado.
