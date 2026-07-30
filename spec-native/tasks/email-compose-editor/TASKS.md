# Tasks: Editor de correo con soporte Markdown/HTML + Asistente IA

## Estado

- Estado: `done`
- Spec: `spec-native/specs/email-compose-editor/SPEC.md`
- Owner funcional: producto/plataforma
- Owners tecnicos: `insightbloom-users` (backend), `frontend/web` (frontend)

## Fase 1 — Configuracion IA para emails

### TASK-0001 — Agregar capability `emailAi` al modelo de dominio

- Estado: `done`
- Owner: backend
- Dependencias: ninguna
- Archivos esperados:
  - `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/domain/model/PlatformSettings.java`
- Criterio de cierre: `PlatformSettings` tiene campo `emailAi` con getter/setter, inicializado con `AiProviderSettings.defaults(false)`.
- Validacion: compila, test `AiDefaultsSeederTest` sigue pasando.

### TASK-0002 — Crear columnas DB para capability `email`

- Estado: `done`
- Owner: backend
- Dependencias: TASK-0001
- Archivos esperados:
  - `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/outbound/sqlite/DatabaseManager.java`
- Criterio de cierre: El array de capabilities en la migracion incluye `"email"`. Se crean columnas `email_ai_*`.
- Validacion: compila, la DB se crea sin errores.

### TASK-0003 — Leer/escribir `emailAi` en repositorio de settings

- Estado: `done`
- Owner: backend
- Dependencias: TASK-0002
- Archivos esperados:
  - `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/outbound/sqlite/SqlitePlatformSettingsRepository.java`
- Criterio de cierre: `get()` lee `emailAi` via `readProvider`, `save()` lo escribe via `saveProvider`.
- Validacion: test `SqlitePlatformSettingsRepositoryTest` pasa con el nuevo campo.

### TASK-0004 — Agregar case `email` al switch de SetAiSettingsUseCase

- Estado: `done`
- Owner: backend
- Dependencias: TASK-0001
- Archivos esperados:
  - `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/application/usecases/SetAiSettingsUseCase.java`
- Criterio de cierre: Switch en `provider()` tiene `case "email" -> settings.getEmailAi()`.
- Validacion: compila, guardar config de capability `email` funciona.

### TASK-0005 — Agregar tab de email en AdminAiSettingsPage

- Estado: `done`
- Owner: frontend
- Dependencias: TASK-0004
- Archivos esperados:
  - `frontend/web/src/pages/dashboard/AdminAiSettingsPage.vue`
  - `frontend/web/src/services/api/types.ts`
- Criterio de cierre: Nuevo tab con capability `email` visible y funcional en `/dashboard/admin/ai/email`.
- Validacion: la pestana aparece, permite configurar y guardar.

### TASK-0006 — Mapear capability `email` en PlatformSettingsHandler

- Estado: `done`
- Owner: backend
- Dependencias: TASK-0004
- Archivos esperados:
  - `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/inbound/http/handlers/PlatformSettingsHandler.java`
- Criterio de cierre: `toView()` y `toInternalView()` incluyen el provider `email`.
- Validacion: `GET /api/v1/platform/settings` retorna `providers.email`.

## Fase 2 — Endpoint de generacion de borrador IA

### TASK-0007 — Crear EmailLlmClient

- Estado: `done`
- Owner: backend
- Dependencias: TASK-0003
- Archivos esperados:
  - `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/outbound/llm/EmailLlmClient.java`
- Criterio de cierre: Clase que llama al LLM usando `PlatformSettings.getEmailAi()`. Patron igual a `GroqLlmClient`.
- Validacion: compila, unit test mockeando `PlatformSettingsRepository`.

### TASK-0008 — Crear GenerateEmailDraftUseCase

- Estado: `done`
- Owner: backend
- Dependencias: TASK-0007
- Archivos esperados:
  - `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/application/usecases/GenerateEmailDraftUseCase.java`
  - `backend/services/insightbloom-users/src/test/java/dev/rafex/insightbloom/users/application/usecases/GenerateEmailDraftUseCaseTest.java`
- Criterio de cierre: Use case genera draft via LLM, rechaza si AI no configurada o prompt vacio.
- Validacion: 3+ tests unitarios pasando.

### TASK-0009 — Registrar endpoint POST /{id}/email/draft

- Estado: `done`
- Owner: backend
- Dependencias: TASK-0008
- Archivos esperados:
  - `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/inbound/http/handlers/ConferenceHandler.java`
  - `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/bootstrap/UsersApplication.java`
- Criterio de cierre: Ruta registrada, handler valida token y permisos, retorna draft.
- Validacion: curl al endpoint retorna draft con AI configurada.

## Fase 3 — Soporte de formatos en envio

### TASK-0010 — Agregar campo `format` al SendRequest

- Estado: `done`
- Owner: backend
- Dependencias: ninguna
- Archivos esperados:
  - `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/application/usecases/SendAttendeeEmailUseCase.java`
- Criterio de cierre: `SendRequest` tiene `format`, default `"text"`, validacion de valores permitidos.
- Validacion: test existente sigue pasando (backward compat).

### TASK-0011 — Renderizar por formato en AttendeeEmailTemplate

- Estado: `done`
- Owner: backend
- Dependencias: TASK-0010
- Archivos esperados:
  - `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/domain/services/AttendeeEmailTemplate.java`
- Criterio de cierre: Nuevo metodo `render()` con logica por formato + sanitizacion HTML whitelist.
- Validacion: tests unitarios para cada formato + payloads XSS.

### TASK-0012 — Parsear `format` en ConferenceHandler

- Estado: `done`
- Owner: backend
- Dependencias: TASK-0010
- Archivos esperados:
  - `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/inbound/http/handlers/ConferenceHandler.java`
- Criterio de cierre: `handleSendAttendeeEmail()` extrae `format` con default `"text"`.
- Validacion: curl con `format: "html"` funciona.

## Fase 4 — Frontend: Editor y componentes

### TASK-0013 — Crear componente EmailComposeEditor

- Estado: `done`
- Owner: frontend
- Dependencias: ninguna
- Archivos esperados:
  - `frontend/web/src/components/EmailComposeEditor.vue`
  - `frontend/web/src/components/__tests__/EmailComposeEditor.test.js`
- Criterio de cierre: 3 tabs, textarea, toggle preview, sanitizacion MD/HTML/plain.
- Validacion: 4+ tests pasando.

### TASK-0014 — Crear componente AiEmailAssistant

- Estado: `done`
- Owner: frontend
- Dependencias: TASK-0009
- Archivos esperados:
  - `frontend/web/src/components/AiEmailAssistant.vue`
  - `frontend/web/src/components/__tests__/AiEmailAssistant.test.js`
- Criterio de cierre: Panel inline con textarea, generate, loading, draft display, insert.
- Validacion: 4+ tests pasando.

### TASK-0015 — Agregar funcion generateEmailDraft en usersApi

- Estado: `done`
- Owner: frontend
- Dependencias: TASK-0009
- Archivos esperados:
  - `frontend/web/src/services/api/usersApi.ts`
- Criterio de cierre: Funcion `generateEmailDraft()` llama al endpoint correcto.
- Validacion: test del API service pasa.

### TASK-0016 — Integrar editor en TicketManagementPage

- Estado: `done`
- Owner: frontend
- Dependencias: TASK-0013, TASK-0014, TASK-0015
- Archivos esperados:
  - `frontend/web/src/pages/dashboard/TicketManagementPage.vue`
- Criterio de cierre: Compose card usa editor, AI assistant integrado, envio con format.
- Validacion: compose card funciona con los 3 formatos.

## Fase 5 — Tests y validacion

### TASK-0017 — Actualizar SendAttendeeEmailUseCaseTest

- Estado: `done`
- Owner: backend
- Dependencias: TASK-0010, TASK-0011
- Archivos esperados:
  - `backend/services/insightbloom-users/src/test/java/dev/rafex/insightbloom/users/application/usecases/SendAttendeeEmailUseCaseTest.java`
- Criterio de cierre: Tests para format=null, text, html, invalid.
- Validacion: todos los tests pasan.

### TASK-0018 — Test manual E2E de envio de email

- Estado: `done`
- Owner: QA/manual
- Dependencias: TASK-0016, TASK-0017
- Archivos esperados: ninguno (manual)
- Criterio de cierre: Checklist completado con los 3 formatos + AI + backward compat.
- Validacion: checklist completado.
