+++
artifact_type = "spec"
id = "SPEC-LPI"
state = "active"
owner = "Codex"
+++

# Herencia de configuración IA por dueño del evento

Reemplaza el modelo event-only de [llm-credential-isolation](../llm-credential-isolation/SPEC.md); las credenciales de evento ya almacenadas siguen siendo sobrescrituras propias.

## Objetivo
Perfiles personales de ADMIN/ORGANIZER para chat, tutor, survey, seatLayout y email. Sin credenciales globales. Cada evento/herramienta elige INHERIT_OWNER, EVENT, MEMBER_PROFILE o DISABLED. Las configuraciones existentes se conservan como EVENT; las ausentes heredan dinámicamente del dueño.

## Autorización y fuentes
Dueño y ADMIN administran el evento. Un moderador ADMIN/ORGANIZER necesita MANAGE_EVENT_AI explícito. Solo el titular vincula su perfil. El vínculo se revalida en users para cada solicitud del gateway. Pérdida de permiso, suspensión, retirada o borrado de credencial vinculada vuelve a heredar del dueño; ausencia de configuración del dueño deshabilita. Fallos del proveedor o de la comprobación interna no cambian fuente. Cada fuente aporta proveedor/modelo/URL/clave completos.

## Seguridad y persistencia
Modos, vínculos y auditoría sin secretos en users. Credenciales personales en tabla nueva user_credentials del gateway, AES-GCM con AAD por usuario/capacidad, rotación y limpieza de legado sin reactivar datos antiguos. APIs me/llm por herramienta y GET/PUT/DELETE/PATCH por evento; nunca devolver claves, hints ni ciphertext. Conservar SSRF y límites por evento, atribuir uso al evento y titular efectivo.

## UX
Mi configuración de IA y editor compartido por herramienta; eventos dependientes y retirada de vínculos propios. Estado seleccionado y efectivo con motivo de fallback. Acceso delegado solo a IA. Confirmar eliminación de configuración propia al cambiar fuente y explicar consumo personal. Clave vacía conserva la existente salvo cambio de proveedor/URL, que requiere nueva clave.

## Aceptación
Pruebas con proveedores simulados para herencia dinámica, cuatro modos, aislamiento y permisos, revocación y fallback, cifrado/persistencia/rotación, SSRF y no filtraciones. Staging con dueño, moderador y organizador ajeno, dos eventos y herramientas diferentes. Gateway compatible primero; users y UI después. Sin sondeos activos de producción.
