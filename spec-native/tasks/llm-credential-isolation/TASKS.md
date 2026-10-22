# TASKS — Configuración LLM por evento y herramienta

## Metadata

- Iniciativa: `llm-credential-isolation`
- Spec relacionada: [`spec-native/specs/llm-credential-isolation/SPEC.md`](../../specs/llm-credential-isolation/SPEC.md)
- Owner: product owner
- Estado general: `in_progress`

## Tareas

### TASK-0001 — Gateway event-only y endpoint personalizado seguro

- ID: TASK-0001
- State: `in_progress`
- Owner: product owner
- Dependencies: —
- Expected files: `backend/services/insightbloom-llm-gateway/**`
- Close criteria: el gateway almacena únicamente credenciales de evento, cifra secretos, resuelve por evento/capacidad y valida URLs HTTPS públicas contra SSRF y redirects.
- Evidence: se añadió `base_url`, borrado al iniciar de filas de organizador y resolución event-only; el cliente conecta a una IP pública fijada y verifica TLS contra el hostname original.
- Validation: gateway `pytest -q` pasó (9 pruebas), incluida la validación de proveedor personalizado y rechazo de DNS privado.
- Remaining: validar DNS rebinding/redirect contra servidor de prueba local y completar rotación/recifrado en integración.

### TASK-0002 — Gestión autenticada y UI por evento

- ID: TASK-0002
- State: `in_progress`
- Owner: product owner
- Dependencies: TASK-0001
- Expected files: `insightbloom-users`, API frontend y configuración del evento
- Close criteria: propietario/ADMIN puede consultar, guardar, reemplazar y borrar por herramienta; solo se muestran metadatos; acciones quedan auditadas.
- Evidence: endpoints `/{eventId}/llm/{capability}` en `ConferenceHandler`, cliente de gateway con actor, y tarjetas de configuración para cinco herramientas en `ConferenceConfigPage.vue`. La vista global se retiró.
- Validation: `insightbloom-users` pasó 415 pruebas y el build del frontend pasó. La verificación sigue pendiente en pruebas de handler para propietario/ADMIN y aislamiento de otro evento, más revisión de lector de pantalla.

### TASK-0003 — Consumidores event-only y eliminación de credenciales globales activas

- ID: TASK-0003
- State: `in_progress`
- Owner: product owner
- Dependencies: TASK-0001, TASK-0002
- Expected files: `insightbloom-users`, `insightbloom-survey`, `PlatformSettingsHandler` y frontend
- Close criteria: todas las herramientas solicitan al gateway solo su evento/capacidad; ningún consumidor ni endpoint distribuye o usa claves globales.
- Evidence: correo, distribución de asientos, tutor, creación/mejora de preguntas, evaluación y Roberto/chat consultan el gateway por evento. Se retiraron rutas globales, API/UI frontend y dependencia OpenAI directa. Historial/cache del bot quedan aislados por evento. `DatabaseManager` limpia valores globales activos al iniciar; `SqlitePlatformSettingsRepository` ya no descifra ni persiste credenciales globales y el seeder no se invoca.
- Validation: `insightbloom-users` (415), `insightbloom-survey` (3), gateway (9), chat (37) y `npm run build` aprobaron. La búsqueda no encontró referencias ejecutables a `/settings/ai/internal`, `/settings/chat-ai` ni `/settings/ai` en backend, chat o frontend.
- Remaining: confirmar las rutas reales de SQLite en producción y observar la purga tras el siguiente reinicio planificado; no se ha accedido ni cambiado producción.

### TASK-0004 — Despliegue declarativo y validación operativa

- ID: TASK-0004
- State: `in_progress`
- Owner: product owner
- Dependencies: TASK-0001, TASK-0003
- Expected files: chart GitOps, secretos cifrados, NetworkPolicy, runbook y pipelines
- Evidence: el chart declara referencias opcionales a clave maestra y tokens dedicados, URLs internas para gateway/consumidores, NetworkPolicy de entrada solo desde `users`, `chat` y `survey`, y salida del gateway limitada a DNS de clúster y TCP/443 público fuera de rangos privados/reservados. Sin los secretos la funcionalidad queda apagada.
- Close criteria: secreto maestro dedicado y tokens provisionados; tráfico limitado a consumidores autorizados; backups/restauración y observabilidad no registran secretos; validación funcional en staging.
- Validation: lint de Helm/manifiestos, revisión de políticas, integración de cada capacidad y ensayo staging sin producción.
- Validation local: `helm lint` y `helm template` aprobaron con las reglas de egress del gateway. Las referencias a secretos son opcionales y no contienen valores secretos.
- Note: no desplegar ni crear secretos desde esta implementación local.

## Riesgos y pendientes de salida

- Aún no se ha demostrado en staging el aislamiento de eventos, la compatibilidad real de proveedores personalizados ni el comportamiento de proxies/APM.
- Configurar el gateway requerirá provisioning de secretos y valores en GitOps; sin ellos la funcionalidad nueva no estará activa en despliegue.
- La limpieza de credenciales solo se aplica a la base activa al inicializar `users`; backups históricos quedan bajo su retención y no deben restaurarse como configuración activa.
