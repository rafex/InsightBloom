# SPEC: Esquema de permisos y roles administrables (plataforma + por evento)

## Initiative
event-roles

## Status
draft

## Summary
Generaliza el modelo de roles, hoy plano (`UserRole`: ADMIN, ORGANIZER,
MODERATOR, GUEST, ATTENDEE, un solo nivel global), en dos jerarquías
independientes: **roles de plataforma** (globales, ej. quien administra
usuarios vs. quien solo administra el catálogo de tipos de evento) y
**roles por evento** (asignados por el creador del evento — su Host/
Anfitrión — a otras personas para ESE evento especifico: moderadores,
staff de acceso, presentadores invitados, etc.). Sigue el mismo patrón ya
usado para `EventType` (DEC-0016): un catálogo fijo de **permisos** vive en
código, y el `ADMIN` de sistema administra **roles** como combinaciones
configurables de esos permisos — sin fijar los roles en código, para que
el administrador pueda crear roles nuevos (ej. "Coordinador de Staff")
combinando permisos existentes sin requerir un release.

## Problem
Con el catálogo de tipos de evento (DEC-0016) y las integraciones
colaborativas (DEC-0017/DEC-0020) ya en producción, aparecieron varios
huecos que el modelo de roles plano no resuelve:
- Quien administra el catálogo de tipos de evento hoy necesita el mismo
  rol `ADMIN` que administra usuarios — no hay forma de dar ese permiso
  acotado sin dar control total de la plataforma.
- El creador de un evento (`created_by_user_uuid`) es su dueño implícito,
  pero no puede delegar tareas: no existe forma de nombrar a alguien
  moderador, staff de acceso, o presentador invitado solo para ese evento,
  sin subirlo a `ORGANIZER` global (lo que le daría permiso de crear sus
  propios eventos y ver todos los suyos, no solo ayudar en este).
- La videollamada (Jitsi) no distingue quien debería tener controles de
  moderador (silenciar a todos, expulsar) — todos los asistentes entran
  con el mismo nivel. Esto es ademas una limitacion tecnica de
  `meet.jit.si` publico (ver Dependencies) que solo Jitsi self-hosted con
  JWT puede resolver de verdad, pero el modelo de datos (quien ES
  moderador de este evento) debe existir independientemente de esa
  limitacion tecnica, para aplicarse en cuanto self-hosted este listo.
- Tareas ya identificadas y deliberadamente pospuestas en iniciativas
  anteriores por falta de este esquema: un "staff de acceso" que solo
  pueda hacer check-in sin editar el evento (ver DEC de ticketing Fase 1,
  "no se introduce un rol nuevo de staff en esta primera version").

## Objective
El `ADMIN` de sistema puede crear, editar y desactivar **roles** desde el
dashboard, cada uno con un **alcance** (`PLATFORM` o `EVENT`) y una lista
de **permisos** activos (de un catálogo fijo que sí vive en código). El
creador de un evento se convierte automáticamente en su **Host**, con
todos los permisos de alcance `EVENT` incluido `ASSIGN_EVENT_ROLES`, y
puede asignar cualquier rol de alcance `EVENT` a otros usuarios para ese
evento especifico. Las rutas y la UI existentes que hoy solo chequean
"es organizador dueño del evento" migran gradualmente a chequear el
permiso especifico que corresponda, sin romper el comportamiento actual.

## Scope
### Includes
- Catálogo de permisos fijo en código: `MANAGE_USERS`, `MANAGE_EVENT_TYPES`,
  `HOST_EVENT`, `MANAGE_EVENT_SETTINGS`, `ASSIGN_EVENT_ROLES`,
  `MODERATE_CONTENT`, `CHECK_IN`, `MANAGE_PRESENTATION`, `MANAGE_SURVEY`,
  `MANAGE_CERTIFICATE`, `VIDEO_MODERATE`.
- Entidad `Role` administrada por `ADMIN`: nombre, slug, descripcion,
  alcance (`PLATFORM` | `EVENT`), lista de permisos activos,
  activo/inactivo. Mismo patron CRUD que `EventType` (DEC-0016).
- Roles de plataforma sembrados por defecto (alcance `PLATFORM`):
  `system_admin` (`MANAGE_USERS` + `MANAGE_EVENT_TYPES`; ademas actua
  implicitamente como Host/Moderador de cualquier evento, ver FR-008),
  `event_type_admin` (solo `MANAGE_EVENT_TYPES`), `organizer` (solo
  `HOST_EVENT`).
- Roles de evento sembrados por defecto (alcance `EVENT`): `host` (todos
  los permisos de evento + `ASSIGN_EVENT_ROLES`), `moderator`
  (`MODERATE_CONTENT` + `VIDEO_MODERATE`), `checkin_staff` (`CHECK_IN`),
  `guest_presenter` (`MANAGE_PRESENTATION`), `survey_manager`
  (`MANAGE_SURVEY`).
- Tabla `event_roles`: asignacion de un rol de alcance `EVENT` a un
  usuario para un evento especifico. El creador del evento recibe la fila
  `host` automaticamente al crear el evento (sin accion manual).
- Endpoint y UI para que el Host de un evento asigne/quite roles de
  evento a otros usuarios (busqueda por email/username).
- Al asignar un rol de moderación, el usuario recibe un boleto operativo
  contado, ya canjeado e idempotente; ese boleto no puede revocarse y la plaza
  permanece ocupada durante la vigencia del evento.
- Endpoint y UI de administracion del catalogo de roles (`ADMIN`-only,
  gateado por `MANAGE_USERS` — ver Risks sobre por que no un permiso
  nuevo dedicado).
- `EventPermissionGuard`: dado un evento y un usuario, resuelve sus
  permisos efectivos (si el usuario tiene un rol de plataforma que
  implica bypass total — ver FR-008 — o si tiene una fila en
  `event_roles` para ese evento, cuyo rol resuelve a un set de permisos).
### Excludes
- Migrar TODAS las rutas existentes que hoy chequean `isOrganizerOrAdmin`
  a permisos especificos — se migra solo lo necesario para demostrar el
  patron (ver Execution Plan); el resto queda para iteraciones futuras,
  sin romper su comportamiento actual.
- Aplicar `VIDEO_MODERATE` de forma efectiva en la videollamada de Jitsi
  — `meet.jit.si` publico no permite asignar moderador de forma confiable
  via API (se determina por "quien entra primero" o requiere JWT). El
  dato de quien tiene `VIDEO_MODERATE` para el evento queda listo en
  `event_roles`, pero su aplicacion real en Jitsi se activa cuando exista
  Jitsi self-hosted con JWT (ver Dependencies).
- Roles jerarquicos o herencia entre roles (ej. "Host hereda todo lo de
  Moderador automaticamente") — cada rol declara su propio set de
  permisos de forma explicita, sin composicion. Mas simple de razonar
  para el `ADMIN` al crear roles nuevos.
- Permisos con alcance mas fino que "el evento completo" (ej. moderar
  solo el chat pero no las dudas) — todos los permisos de esta iniciativa
  son a nivel de evento completo.
- Eliminar o reemplazar `UserRole` (ADMIN/ORGANIZER/MODERATOR/GUEST/
  ATTENDEE) — sigue existiendo tal cual para compatibilidad hacia atras;
  los roles de plataforma nuevos (`event_type_admin`) son adicionales, no
  reemplazan el campo `roles` CSV que ya usa `User`.

## Functional Requirements
- FR-001: un usuario con permiso `MANAGE_USERS` (hoy: rol `ADMIN`) puede
  crear un rol con nombre, slug unico, alcance (`PLATFORM` o `EVENT`) y
  una lista de permisos elegidos del catalogo fijo.
- FR-002: un usuario con `MANAGE_USERS` puede editar nombre, descripcion y
  permisos de un rol existente, y activarlo/desactivarlo.
- FR-003: desactivar un rol no afecta asignaciones ya existentes; solo
  deja de ofrecerse como opcion para asignaciones nuevas.
- FR-004: al crear un evento, el creador recibe automaticamente una fila
  en `event_roles` con rol `host` para ese evento, sin accion manual.
- FR-005: un usuario con `ASSIGN_EVENT_ROLES` sobre un evento (por
  defecto, su Host) puede asignar cualquier rol activo de alcance `EVENT`
  a otro usuario para ese evento, buscandolo por email o username.
- FR-006: un usuario con `ASSIGN_EVENT_ROLES` sobre un evento puede quitar
  una asignacion de rol existente (excepto la propia fila `host` del
  creador original, ver Risks).
- FR-007: las rutas HTTP que dependen de un permiso de evento (ej. asignar
  roles, moderar contenido) rechazan la operacion con un error claro si el
  usuario no tiene ese permiso para ese evento especifico.
- FR-008: un usuario con rol de plataforma `system_admin` tiene todos los
  permisos de alcance `EVENT` sobre cualquier evento sin necesidad de una
  fila en `event_roles` (bypass total, igual que el `ADMIN` actual).
- FR-009: el catalogo de permisos disponibles se expone via un endpoint
  de solo lectura para que el `ADMIN` pueda armar el formulario de roles
  sin hardcodear opciones en el frontend.
- FR-010: la pagina de configuracion de un evento muestra, solo a quien
  tenga `ASSIGN_EVENT_ROLES`, la lista de personas con un rol asignado
  para ese evento y permite agregar/quitar.

## Non-functional Requirements
- NFR-001: seguir el patron ya usado para `EventType` (DEC-0016):
  migracion idempotente try/catch, catalogo de permisos en codigo,
  catalogo de roles en base de datos administrable.
- NFR-002: la administracion del catalogo de roles requiere el permiso
  `MANAGE_USERS`, reutilizando el mecanismo de autorizacion ya existente
  (DEC-0011) — no se crea un permiso nuevo solo para esto (ver Risks).
- NFR-003: agregar un permiso nuevo al catalogo fijo debe ser un cambio
  acotado (un enum + su gate correspondiente), sin tocar el modelo de
  `Role` ni la tabla que lo persiste — mismo criterio que NFR-004 de
  event-types-catalog.
- NFR-004: la migracion de rutas existentes a chequeo por permiso debe
  preservar exactamente el comportamiento actual para conferencias ya
  creadas (regresion 0 en la suite de tests de `insightbloom-users`).

## Acceptance Criteria
### Scenario 1 — Admin crea un rol de evento nuevo
- **Given** un usuario con `MANAGE_USERS`
- **When** crea el rol "Coordinador de Staff" con permisos `CHECK_IN` y
  `MODERATE_CONTENT`, alcance `EVENT`
- **Then** el rol aparece disponible para que cualquier Host lo asigne en
  sus propios eventos.

### Scenario 2 — Host automatico al crear evento
- **Given** un organizador con permiso `HOST_EVENT`
- **When** crea un evento nuevo
- **Then** aparece automaticamente como Host de ese evento en
  `event_roles`, sin accion manual adicional.

### Scenario 3 — Host asigna un moderador
- **Given** el Host de un evento
- **When** asigna el rol "Moderador" a otro usuario buscandolo por email
- **Then** ese usuario obtiene `MODERATE_CONTENT` y `VIDEO_MODERATE` para
  ese evento especifico, sin volverse organizador global.

### Scenario 4 — Usuario sin permiso no puede asignar roles
- **Given** un asistente sin `ASSIGN_EVENT_ROLES` sobre un evento
- **When** intenta asignar un rol a otro usuario para ese evento
- **Then** el sistema responde con un error claro de permiso insuficiente,
  sin ejecutar la operacion.

### Scenario 5 — system_admin tiene bypass total
- **Given** un usuario con rol de plataforma `system_admin`
- **When** accede a cualquier ruta gateada por un permiso de evento, sobre
  cualquier evento, sin tener una fila en `event_roles` para el
- **Then** la operacion se permite igual, sin necesidad de asignacion
  explicita.

## Dependencies
- `UserRole` existente (DEC-0011) — se reutiliza sin cambios para
  compatibilidad; los roles de plataforma nuevos son adicionales.
- Aplicacion real de `VIDEO_MODERATE` en Jitsi depende de Jitsi
  self-hosted con JWT (DEC-0017, ultimo paso del orden recomendado de
  event-types-catalog) — `meet.jit.si` publico no soporta asignacion de
  moderador confiable via API. Esta iniciativa deja el dato listo
  (`event_roles`), su aplicacion en la videollamada se conecta despues.
- Migracion de rutas existentes especificas (Fase 2 del Execution Plan)
  depende de que `EventPermissionGuard` este funcionando (Fase 1).

## Risks
- Dar el permiso de administrar roles bajo el mismo `MANAGE_USERS` que
  usuarios (en vez de un permiso dedicado) simplifica la primera version
  pero significa que quien administra usuarios tambien puede crear roles
  con cualquier combinacion de permisos, incluido asignarse a si mismo
  permisos de evento amplios — mitigacion: es el mismo nivel de confianza
  que ya se deposita en `ADMIN` hoy, no es una regresion.
  Se revisaria en una iteracion futura si se necesita separar.
- Migrar solo una parte de las rutas a chequeo por permiso (ver Excludes)
  deja el sistema en un estado mixto (algunas rutas siguen chequeando
  "organizador dueño", otras chequean permiso especifico) — mitigacion:
  documentar claramente en cada TASK cual patron usa cada ruta migrada, y
  no mezclar ambos criterios en la misma ruta.
- Quitar la fila `host` del creador original de un evento (si se permite)
  podria dejar un evento sin Host — mitigacion (FR-006): la fila `host`
  del creador original no se puede eliminar via el endpoint de
  asignacion; solo se libera si el evento se transfiere explicitamente
  (no incluido en esta iniciativa).
- El bypass total de `system_admin` (FR-008) significa que un cambio
  accidental en el rol `system_admin` (ej. si el `ADMIN` edita ese rol de
  plataforma y le quita el permiso equivocado) podria alterar el
  comportamiento de bypass de forma no obvia — mitigacion: el bypass se
  resuelve por el **key** fijo `system_admin` en `EventPermissionGuard`
  (no por permisos individuales), asi que solo desactivar o renombrar ese
  rol especifico rompe el bypass, no editar sus permisos.

## Execution Plan
-> `tasks/event-roles/TASKS.md`

## Validation Plan
- Manual: crear un rol de evento nuevo con un subconjunto de permisos,
  asignarlo a un usuario en un evento, confirmar que solo ese usuario y
  solo en ese evento obtiene el permiso correspondiente.
- Automatizado: tests de dominio del nuevo `RoleRepository`/`EventRoleRepository`
  y sus casos de uso (crear rol, asignar/quitar rol de evento, bypass de
  `system_admin`), tests de `EventPermissionGuard` con distintas
  combinaciones de rol/permiso, y verificacion de que ninguna conferencia
  existente cambia de comportamiento (regresion 0 en la suite actual de
  `insightbloom-users`).
- Evidencia esperada: la suite completa de backend y frontend sigue en
  verde despues de introducir el catalogo; un usuario con un rol de
  evento acotado (ej. solo `CHECK_IN`) no puede ejecutar acciones fuera
  de ese permiso sobre el mismo evento.
