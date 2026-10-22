# SPEC: Invitaciones seguras para organizadores de la beta

## Initiative
beta-organizer-invitations

## Status
active

## Summary
La plataforma permitirá que ADMIN invite por correo a cuentas ORGANIZER mediante un token temporal, ligado al destinatario y de un solo uso. El registro público continuará disponible para participantes; las recomendaciones de nuevos organizadores quedarán detrás de una habilitación administrativa y requerirán aprobación antes de enviar otra invitación.

## Problem
La beta necesita incorporar organizadores de forma controlada sin abrir la creación de eventos a cualquier cuenta, y necesita trazabilidad para detectar abuso, corregir invitaciones y controlar el crecimiento.

## Objective
Solo usuarios autenticados con correo verificado y una invitación vigente pueden obtener ORGANIZER mediante este flujo. ADMIN controla invitaciones, recomendaciones, límites, pausas y auditoría desde la aplicación.

## Scope
### Includes
- Invitaciones administrativas por correo con estado, expiración, reenvío y revocación.
- Aceptación para cuentas nuevas y para cuentas ATTENDEE ya existentes con el mismo correo verificado.
- Recomendaciones enviadas por organizadores seleccionados cuando el gate global esté habilitado; cada recomendación requiere aprobación ADMIN.
- Configuración ADMIN de expiración y cuota, auditoría persistente y panel accesible/responsive.
- Controles antiabuso en servidor y pruebas de autorización, token, cuotas y entrega.

### Excludes
- Autorregistro público de cuentas ORGANIZER.
- Que organizadores emitan invitaciones directamente o que las invitaciones secundarias sean recursivas.
- Asignación de ADMIN mediante invitación o endpoint público.
- Cambios en GitOps, SMTP o proveedores de correo salvo configuración imprescindible detectada durante validación.

## Functional Requirements
- FR-001: El registro público existente conserva el rol ATTENDEE.
- FR-002: ADMIN puede emitir, listar, reenviar y revocar invitaciones dirigidas a una dirección de correo normalizada.
- FR-003: El token se genera con aleatoriedad criptográfica, se persiste solo como hash, es de un uso y expira; GET no consume el token.
- FR-004: El invitado solo recibe ORGANIZER tras autenticarse/registrarse y probar control del mismo correo mediante OTP EMAIL.
- FR-005: ADMIN puede habilitar globalmente recomendaciones y habilitar/deshabilitar emisores individuales.
- FR-006: Cada emisor ve su cuota vigente y puede recomendar correos; no emite invitaciones ni ve recomendaciones ajenas.
- FR-007: ADMIN aprueba/rechaza recomendaciones; aprobar emite y envía una invitación final.
- FR-008: ADMIN configura TTL (1–30 días) y cuota (1–10 recomendaciones por período de 30 días); defaults: 7 días y 3 recomendaciones.
- FR-009: El sistema registra cambios de estado y acciones relevantes sin registrar tokens en claro.
- FR-010: Fallos de envío quedan visibles para ADMIN y pueden reintentarse con un token rotado.

## Non-functional Requirements
- NFR-001: Todas las mutaciones administrativas validan rol ADMIN en backend, nunca solo en la UI.
- NFR-002: Aceptación y consumo del token son atómicos frente a reintentos y carreras.
- NFR-003: Las respuestas públicas no permiten enumerar correos o estados de invitación.
- NFR-004: Se aplican límites de frecuencia por usuario/IP y cuota de recomendaciones; se rechazan duplicados activos, auto-recomendaciones y cuentas suspendidas.
- NFR-005: UI operable por teclado y lector de pantalla, con estados claros y responsive.

## Acceptance Criteria
### Invitación directa
- **Given** un ADMIN y un correo válido
- **When** ADMIN envía una invitación
- **Then** se registra el emisor, se entrega un enlace de un solo uso con TTL, y el estado/envío queda visible.

### Cuenta nueva
- **Given** una invitación vigente para el correo X
- **When** una persona se registra con X, verifica X por OTP EMAIL y acepta explícitamente
- **Then** la invitación se consume una vez y la cuenta recibe ORGANIZER, nunca ADMIN.

### Cuenta participante existente
- **Given** una cuenta ATTENDEE con correo X
- **When** su titular inicia sesión, verifica X y acepta la invitación
- **Then** obtiene ORGANIZER; otros correos, sesiones sin autenticar y cuentas suspendidas son rechazados.

### Recomendaciones
- **Given** el gate global habilitado y un organizador individual habilitado
- **When** recomienda un correo dentro de cuota
- **Then** se crea una recomendación privada pendiente; ADMIN decide y solo aprobar emite la invitación.

### Seguridad y auditoría
- **Given** token inválido/vencido/usado/revocado o usuario sin ADMIN
- **When** intenta aceptar o administrar invitaciones
- **Then** no hay elevación de rol; errores públicos son genéricos y el evento auditable no contiene secretos.

## Dependencies
- `insightbloom-users`, OTP EMAIL existente (Zoho), registro web y rutas ADMIN existentes.

## Risks
- Elevación de privilegios por carrera o correo no verificado — mitigar con transacción, token email-bound y pruebas concurrentes.
- Abuso de correo/referidos — gate apagado por defecto, límites y aprobación manual.
- Caída del proveedor SMTP — persistir estado de entrega fallida y permitir reintento con rotación del token.

## Execution Plan
→ `tasks/beta-organizer-invitations/TASKS.md`

## Validation Plan
Pruebas de caso de uso/repositorio/handler, contrato API y UI; ejecutar los gates backend/frontend aplicables y revisar migración SQLite sobre DB nueva y existente.
