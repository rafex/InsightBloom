# Tareas — Invitaciones seguras para organizadores beta

### TASK-BI-001: Dominio, persistencia y migración SQLite

**Estado:** done
**Owner:** product owner
**Dependencias:** ninguna
**Archivos esperados:** `backend/services/insightbloom-users/`
**Criterio de cierre:** entidades/repositorios para invitaciones, recomendaciones, ajustes y auditoría; token hash, TTL, normalización y operaciones transaccionales/idempotentes implementadas.
**Validación:** pruebas SQLite en base nueva, expiración, rotación/uso único, correo verificado, consumo concurrente y cuotas; el esquema se crea idempotentemente al inicializar.

### TASK-BI-002: Casos de uso, correo y API segura

**Estado:** in_progress
**Owner:** product owner
**Dependencias:** TASK-BI-001
**Archivos esperados:** `backend/services/insightbloom-users/`
**Criterio de cierre:** endpoints ADMIN y usuario autenticado para invitar, gestionar, recomendar, aprobar/rechazar y aceptar; grant de ORGANIZER tras OTP EMAIL; fallos/reintentos de correo auditados.
**Validación:** pruebas de matriz ADMIN/ORGANIZER/ATTENDEE, errores genéricos, no elevación de rol, cuotas y rotación.

**Seguimiento 2026-10-22:** La interfaz desplegada no podía cargar los datos administrativos porque `UsersApplication` montaba `OrganizerInvitationHandler` solo en el prefijo público `/api/v1/organizer-invitations/*`; faltaba el prefijo `/api/v1/admin/organizer-invitations/*` que cubre las rutas administrativas declaradas por el handler. Se agregó el montaje faltante. Queda pendiente validar el flujo HTTP autenticado después del despliegue.

### TASK-BI-003: Flujos Vue de administración e invitado

**Estado:** done
**Owner:** product owner
**Dependencias:** TASK-BI-002
**Archivos esperados:** `frontend/web/src/`
**Criterio de cierre:** panel ADMIN para invitaciones, cola de recomendaciones y límites; aceptación integrada con registro/login/OTP; UX responsive y accesible con feedback accionable.
**Validación:** pruebas de servicios/componentes, `vue-tsc`, build y revisión de teclado/estados.

### TASK-BI-004: Documentación y gates de lanzamiento

**Estado:** done
**Owner:** product owner
**Dependencias:** TASK-BI-001, TASK-BI-002, TASK-BI-003
**Archivos esperados:** `spec-native/`
**Criterio de cierre:** roles y arquitectura describen el nuevo flujo; gate global apagado por defecto; procedimiento de reversión/operación y evidencia de aceptación documentados.
**Validación:** revisión contra todos los escenarios de SPEC y lista de archivos modificados.

## Evidencia de validación

- `./mvnw -f backend/services/pom.xml -pl insightbloom-users -am test` — 414 pruebas pasaron; incluye límites por usuario/IP, redención concurrente, entrega fallida/reintento y exclusión de invitaciones derivadas al habilitar recomendadores.
- `SqliteOrganizerInvitationRepositoryTest` — 7 escenarios focalizados pasaron; incluye inicialización idempotente sobre BD existente y escenarios SQLite del flujo.
- `npm --prefix frontend/web test` — 183 pruebas pasaron.
- `npm --prefix frontend/web run typecheck` — correcto.
- `npm --prefix frontend/web run lint` — correcto.
- `npm --prefix frontend/web run lint:ui-governance` — correcto.
- `npm --prefix frontend/web run build` — correcto; Vite reportó avisos de chunks grandes y directivas `use client` ignoradas por dependencias al empaquetar.

La validación de SMTP fue simulada en pruebas; no se enviaron correos reales ni se probó el flujo HTTP autenticado en staging.
