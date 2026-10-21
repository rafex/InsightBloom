# TASKS — Aislamiento de credenciales LLM

## Metadata

- Iniciativa: `llm-credential-isolation`
- Spec relacionada: [`spec-native/specs/llm-credential-isolation/SPEC.md`](../../specs/llm-credential-isolation/SPEC.md)
- Owner: product owner
- Estado general: `in_progress`

## Tareas

### TASK-0001 — Gateway, almacenamiento versionado y catálogo aprobado

- ID: TASK-0001
- State: `in_progress`
- Owner: product owner
- Dependencies: —
- Expected files: `backend/services/insightbloom-llm-gateway/**`, `infra/**`, GitOps chart
- Close criteria: Gateway dedicado almacena secretos solo cifrados con AES-GCM y clave maestra separada, resuelve ámbito de organizador/evento, restringe catálogo y audita sin datos sensibles.
- Validation: pruebas unitarias de cifrado, rotación, persistencia, precedencia, autorización, catálogo y filtración; compilación/imagen local.

Avance parcial: el almacén cifrado, el catálogo, la API interna, los límites, la auditoría, el Dockerfile, el workflow de publicación y el pod apagado/restringido están creados. Siguen pendientes las credenciales de despliegue, la emisión de contexto firmada desde `users`, el TLS interno y las pruebas de staging; por eso la tarea sigue `in_progress`.

Validación local: `insightbloom-llm-gateway` 7 pruebas aprobadas; `insightbloom-users` 414 pruebas aprobadas; `npm --prefix frontend/web run build` aprobado; `helm lint infrastructure/charts/insightbloom` y `helm template` aprobados. La compilación del frontend muestra advertencias existentes de chunks grandes y directivas `use client` ignoradas en dependencias.

### TASK-0002 — Gestión segura de credenciales desde usuarios y UI

- ID: TASK-0002
- State: `todo`
- Owner: product owner
- Dependencies: TASK-0001
- Expected files: `insightbloom-users`, API frontend y vistas de configuración de IA
- Close criteria: Organizador solo gestiona su clave; propietario del evento o ADMIN gestiona override; respuestas solo muestran estado sin hints; UX reemplaza o borra explícitamente y es accesible.
- Validation: pruebas de roles, aislamiento entre tenants/eventos, respuestas JSON, persistencia y UX.

### TASK-0003 — Migrar consumidores y retirar distribución heredada

- ID: TASK-0003
- State: `todo`
- Owner: product owner
- Dependencies: TASK-0001, TASK-0002
- Expected files: `chat`, `insightbloom-survey`, demás consumidores y `PlatformSettingsHandler`
- Close criteria: Ningún consumidor obtiene claves de `/ai/internal`; gateway ejecuta peticiones para las cinco capacidades y el endpoint heredado deja de distribuir claves.
- Validation: pruebas de integración locales para capacidades, tenant/evento y errores seguros; búsqueda de referencias residuales.

### TASK-0004 — Despliegue declarativo y controles operativos

- ID: TASK-0004
- State: `todo`
- Owner: product owner
- Dependencies: TASK-0001, TASK-0003
- Expected files: chart GitOps, secretos cifrados, NetworkPolicy, runbook y pipelines
- Close criteria: Gateway apagado por defecto, secreto maestro dedicado, PVC/backups, llamadas solo desde consumidores permitidos, observabilidad redactada y procedimiento de rotación/restauración.
- Validation: lint de Helm/manifiestos, revisión de políticas, ensayo local/staging sin producción.

## Riesgos abiertos

- La configuración efectiva de proxies/APM y políticas de red de producción debe verificarse en staging; no se infiere del chart.
- Las claves globales existentes no se copiarán; eventos sin credencial propia no tendrán IA.
