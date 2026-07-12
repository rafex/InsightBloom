# TASKS.md — event-roles

Derivado de `spec-native/specs/event-roles/SPEC.md`.

**Progreso (2026-07-11):** Fase 0, 1 y 2 completas (backend + frontend).
Pendiente solo la migracion gradual de rutas existentes a chequeo por
permiso especifico (fuera de alcance de esta iteracion, ver SPEC
Excludes) y la aplicacion real de `VIDEO_MODERATE` en Jitsi (depende de
self-hosted+JWT).

## Fase 0 — Catálogo de permisos + Role administrable (plataforma)

### TASK-0001: Catálogo fijo de permisos en código

**Estado:** done
**Owner:** —
**Dependencias:** ninguna
**Archivos esperados:**
`backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/domain/model/Permission.java`
(enum: `MANAGE_USERS`, `MANAGE_EVENT_TYPES`, `HOST_EVENT`,
`MANAGE_EVENT_SETTINGS`, `ASSIGN_EVENT_ROLES`, `MODERATE_CONTENT`,
`CHECK_IN`, `MANAGE_PRESENTATION`, `MANAGE_SURVEY`, `MANAGE_CERTIFICATE`,
`VIDEO_MODERATE`)
**Criterio de cierre:** el enum existe y es la unica fuente de verdad de
permisos disponibles.
**Validacion:** `mvn -o clean compile`.

### TASK-0002: Entidad `Role` + tabla + repositorio

**Estado:** done
**Owner:** —
**Dependencias:** TASK-0001
**Archivos esperados:** `domain/model/RoleScope.java` (enum: `PLATFORM`,
`EVENT`), `domain/model/Role.java` (uuid, key/slug unico, name,
description, scope, permissions (set de `Permission`), active, createdAt,
updatedAt), `domain/ports/RoleRepository.java`,
`adapters/outbound/sqlite/SqliteRoleRepository.java`,
`adapters/outbound/sqlite/DatabaseManager.java` (tabla `roles` + migracion
+ seed de 3 roles PLATFORM: `system_admin`, `event_type_admin`,
`organizer`; y 5 roles EVENT: `host`, `moderator`, `checkin_staff`,
`guest_presenter`, `survey_manager`).
**Criterio de cierre:** al arrancar el servicio con una base nueva o
existente, el catalogo contiene los 8 roles sembrados, activos.
**Validacion:** `mvn -o test`.

### TASK-0003: Casos de uso de administracion de roles

**Estado:** done
**Owner:** —
**Dependencias:** TASK-0002
**Archivos esperados:** `application/usecases/CreateRoleUseCase.java`,
`application/usecases/UpdateRoleUseCase.java`,
`application/usecases/SetRoleActiveUseCase.java`,
`application/usecases/ListRolesUseCase.java` (con filtro `scope` y
`activeOnly`).
**Criterio de cierre:** crear con slug duplicado falla claro; editar
permisos reemplaza el set completo; desactivar no borra ni afecta
asignaciones existentes.
**Validacion:** tests unitarios con fakes de `RoleRepository` (mismo estilo
que `EventTypeUseCasesTest`).

### TASK-0004: Rutas HTTP de administracion de roles + catálogo de permisos

**Estado:** done
**Owner:** —
**Dependencias:** TASK-0003
**Archivos esperados:** nuevo `RoleHandler.java` con:
`GET /api/v1/roles` (activos, filtrable por `scope` — para el selector del
Host al asignar roles de evento), `GET /api/v1/roles/all` (`MANAGE_USERS`,
incluye inactivos), `POST /api/v1/roles` (`MANAGE_USERS`),
`PUT /api/v1/roles/{id}` (`MANAGE_USERS`),
`PUT /api/v1/roles/{id}/active` (`MANAGE_USERS`),
`GET /api/v1/permissions` (catalogo fijo, de solo lectura, FR-009);
wiring en `UsersApplication.java`. Gate por `MANAGE_USERS` reutiliza el
mismo chequeo de rol `admin` ya existente (NFR-002), no un permiso nuevo.
**Criterio de cierre:** un usuario sin `MANAGE_USERS` recibe 403 al
intentar crear/editar un rol; `GET /roles` sin auth devuelve solo los
activos.
**Validacion:** tests de handler o verificacion manual con curl/preview.

## Fase 1 — Asignación de roles por evento

### TASK-0010: Entidad `EventRole` + tabla + repositorio

**Estado:** done
**Owner:** —
**Dependencias:** TASK-0002
**Archivos esperados:** `domain/model/EventRole.java` (uuid, event_uuid,
user_uuid, role_key, assigned_at), `domain/ports/EventRoleRepository.java`,
`adapters/outbound/sqlite/SqliteEventRoleRepository.java`,
`DatabaseManager.java` (tabla `event_roles`,
`UNIQUE(event_uuid, user_uuid)` — una persona tiene un solo rol por evento
a la vez).
**Criterio de cierre:** se puede guardar y consultar asignaciones de rol
por evento.
**Validacion:** `mvn -o test`.

### TASK-0011: `EventPermissionGuard` + auto-asignación de Host al crear evento

**Estado:** done
**Owner:** —
**Dependencias:** TASK-0010, TASK-0002
**Archivos esperados:** `domain/services/EventPermissionGuard.java` (dado
un `conferenceUuid` + `userUuid`, resuelve si tiene un `Permission` dado:
bypass total si el usuario tiene el rol de plataforma `system_admin`
—FR-008—, si no, busca su fila en `event_roles` para ese evento y resuelve
los permisos del `Role` correspondiente), `CreateConferenceUseCase.java`
(al crear el evento, inserta automaticamente
`event_roles(event_uuid, creador, role_key='host')` — FR-004).
**Criterio de cierre:** el creador de un evento nuevo aparece
automaticamente como su Host; un `system_admin` tiene permiso sobre
cualquier evento sin fila en `event_roles`.
**Validacion:** test unitario con fakes de `EventRoleRepository` y
`RoleRepository` (casos: usuario con rol de evento correcto, usuario sin
asignacion, `system_admin` con bypass).

### TASK-0012: Endpoints para asignar/quitar roles de un evento

**Estado:** done
**Owner:** —
**Dependencias:** TASK-0011
**Archivos esperados:** `AssignEventRoleUseCase.java` (gateado por
`ASSIGN_EVENT_ROLES` via `EventPermissionGuard`; busca el usuario objetivo
por email/username, valida que el rol sea de alcance `EVENT` y este
activo), `ListEventRolesUseCase.java`, `RemoveEventRoleUseCase.java`
(bloquea quitar la fila `host` del creador original — FR-006), rutas en
`ConferenceHandler`: `GET /{id}/roles`, `POST /{id}/roles`
(`{userIdentifier, roleKey}`), `DELETE /{id}/roles/{userUuid}`.
**Criterio de cierre:** el Host de un evento puede asignar "Moderador" a
otro usuario buscandolo por email; un asistente sin `ASSIGN_EVENT_ROLES`
recibe 403 al intentarlo; no se puede quitar al Host original.
**Validacion:** tests unitarios (fakes) + prueba manual en preview.

## Fase 2 — Frontend

### TASK-0020: Página de administración de roles (MANAGE_USERS-only)

**Estado:** done
**Owner:** —
**Dependencias:** TASK-0004
**Archivos esperados:**
`pages/dashboard/RolesAdminPage.vue` (listar, crear, editar,
activar/desactivar; checkboxes de permisos poblados desde
`GET /api/v1/permissions`; selector de alcance PLATFORM/EVENT),
`usersApi.ts`, `types.ts`, ruta `/dashboard/admin/roles` con guard admin
(mismo patron que `/dashboard/admin/event-types`).
**Criterio de cierre:** un usuario sin `MANAGE_USERS` no puede llegar a la
pagina (guard de router + 403 de backend como respaldo).
**Validacion:** `npx vue-tsc --noEmit`, `npx vitest run`, prueba manual en
preview.

### TASK-0021: Sección "Roles del evento" en la edición de un evento

**Estado:** done
**Owner:** —
**Dependencias:** TASK-0012, TASK-0020
**Archivos esperados:** sección nueva en `EditConferencePage.vue` (visible
solo si el usuario actual tiene `ASSIGN_EVENT_ROLES` sobre ese evento):
tabla de personas con rol asignado + boton quitar, formulario para
asignar (buscar por email/username + selector de rol EVENT activo).
**Criterio de cierre:** el Host ve y gestiona los roles de su evento; un
asistente sin el permiso no ve la seccion en absoluto.
**Validacion:** `npx vue-tsc --noEmit`, `npx vitest run`, prueba manual en
preview.

## Validación general

- `mvn -o test` (0 regresiones sobre la suite actual de
  `insightbloom-users`).
- `npx vue-tsc --noEmit`, `npx vitest run`, `npm run build` en frontend.
- Prueba manual: crear un rol de evento nuevo, asignarlo a un usuario en
  un evento especifico, confirmar que el permiso aplica solo ahi.
