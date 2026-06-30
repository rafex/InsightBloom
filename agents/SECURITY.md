# Security Audit Report — InsightBloom

_Fecha inicial: 2026-06-26 | Actualizado: 2026-06-30 | Estado: ✅ TODOS LOS HALLAZGOS CERRADOS_

---

## Resumen ejecutivo

Se realizaron **dos rondas de auditoría**. La primera identificó 7 hallazgos sobre autenticación y exposición de endpoints. La segunda amplió el alcance a criptografía, infraestructura de red, y operaciones. En total **17 issues** fueron identificados y corregidos.

Estado actual: **ningún hallazgo abierto**.

---

## Auditoría 1 — Autenticación y endpoints (2026-06-26)

### 🔴 CRÍTICA — #1: Moderación sin autenticación ✅ CERRADO

**Endpoint**: `POST /api/v1/conferences/*` via nginx `/api/moderation`
**Handler**: `ConferenceModerationHandler` (`insightbloom-moderation:8084`)

El handler gestionaba censura, restauración, edición y eliminación sin validar ningún token. Cualquier persona con el UUID de conferencia podía censurar/restaurar/editar/eliminar palabras y mensajes.

**Corrección**: `requireOrganizer(jx)` al inicio de `get()`, `post()` y `patch()`. Valida Bearer token contra users y exige `role=organizer`.

---

### 🔴 ALTA — #2: `/internal/evaluate` sin protección ✅ CERRADO

**Handler**: `InternalEvaluateHandler` (moderation:8084)

Sin header `X-Internal-Auth`. Accesible directamente en el puerto 8084.

**Corrección**: `validInternalAuth(jx)` en `post()`. `HttpModerationClient` (ingest) ahora envía `X-Internal-Auth`.

---

### 🔴 ALTA — #3: `/internal/update` sin protección ✅ CERRADO

**Handler**: `UpdateHandler` (query:8083)

**Corrección**: `validInternalAuth(jx)` en `post()`. `HttpQueryClient` (ingest) envía `X-Internal-Auth`.

---

### 🔴 ALTA — #4: `/internal/recalc` sin protección ✅ CERRADO

**Handler**: `RecalcHandler` (stats:8085)

**Corrección**: `validInternalAuth(jx)` en `post()`. `HttpStatsClient` (ingest) envía `X-Internal-Auth`.

---

### 🟡 MEDIA — #5: Query expuesto sin auth — ACEPTADO POR DISEÑO

**Endpoint**: `GET /api/v1/conferences/{id}/cloud/*` (query:8083)

**Decisión**: Los datos de nube/timeline se consideran públicos por diseño — son consumidos sin login desde `CloudDoubtsPage`, `CloudTopicsPage`, `WordTimelinePage`. No se requiere cambio.

---

### 🟡 MEDIA — #6: Stats expuesto sin auth ✅ CERRADO

**Handler**: `StatsHandler` (stats:8085, no mapeado en nginx)

**Corrección**: `requireOrganizer(jx)` igual que moderación.

---

### 🟢 BAJA — #7: `validInternalAuth` fail-open ✅ CERRADO

Sin `INTERNAL_API_KEY` configurada, la función retornaba `true` silenciosamente.

**Corrección** (Auditoría 2 / C2): La función ahora falla cerrado — loguea `SECURITY WARNING` y retorna `false` si la env var está ausente o vacía. Vive una sola vez en `BaseResourceHandler` (backend/common).

---

## Auditoría 2 — Criptografía, red e infraestructura (2026-06-30)

Auditoría experta completa sobre el proyecto (ya no MVP). Se identificaron y corrigieron 10 items adicionales.

### 🔴 CRÍTICA — C1: 4 endpoints DELETE públicamente accesibles ✅ CERRADO

Los endpoints `DELETE /api/v1/conferences/{uuid}` en ingest, query, moderation y survey no tenían validación `X-Internal-Auth`. Eran invocables desde internet a través de nginx.

**Corrección**: `validInternalAuth(jx)` añadido al inicio del método `delete()` en `IngestHandler`, `ConferenceQueryHandler`, `ConferenceModerationHandler` y `SurveyHandler`. Retorna 403 si el header no coincide.

---

### 🔴 CRÍTICA — C2: `validInternalAuth` fail-open → fail-closed ✅ CERRADO

_(Ver hallazgo #7 de Auditoría 1 — corregido en esta ronda)_

---

### 🔴 CRÍTICA — C3: Contraseñas SHA-256 sin sal ✅ CERRADO

Las contraseñas se almacenaban como `SHA-256(password)` en texto claro — vulnerable a ataques de diccionario y rainbow tables.

**Corrección**: Nueva clase `PasswordService` que usa `ether-crypto` (PBKDF2-HMAC-SHA256, 310 000 iteraciones, salt aleatorio de 16 bytes). Formato de almacenamiento: `$pbkdf2$<iter>$<salt_hex>$<hash_hex>`. Migración transparente: el login detecta el formato legacy y actualiza el hash automáticamente en la primera autenticación exitosa. Afecta `RegisterUseCase`, `LoginUseCase`, `ChangePasswordUseCase`.

---

### 🔴 CRÍTICA — C4: Tokens de sesión almacenados en claro ✅ CERRADO

La tabla `tokens` almacenaba el valor crudo del token. Un dump de la BD permitía impersonar cualquier sesión activa.

**Corrección**: `SqliteTokenRepository` ahora guarda `SHA-256(token_value)` en la columna `token_value`. El cliente recibe y envía el token crudo; la BD nunca lo ve. Lookup y revocación también pasan por el hash. Los tokens existentes se invalidaron automáticamente en el deploy.

---

### 🔴 CRÍTICA — C5: Sin rate limiting en endpoints de autenticación ✅ CERRADO

Login, registro y OTP no tenían protección contra ataques de fuerza bruta.

**Corrección**: `nginx.conf` — dos zonas `limit_req_zone` y bloques `location` específicos **antes** del catch-all `/api/users`:
- `/auth/otp/send`: 2 req/min por IP, burst=2
- `/auth/login`, `/auth/register`, `/auth/otp/verify`: 5 req/min por IP, burst=3

---

### 🔴 ALTA — A1: Sin límite de tamaño en upload de presentaciones ✅ CERRADO

`multer` en `server.js` (presentations) no tenía límite de tamaño de archivo. Posible DoS por archivos gigantes.

**Corrección**: `limits: { fileSize: 100 * 1024 * 1024 }` (100 MB) en la configuración de multer.

---

### 🔴 ALTA — A2: Sin aislamiento de red entre pods ✅ CERRADO

Todos los pods del namespace `mvps` podían recibir tráfico de cualquier origen — incluyendo pods comprometidos de otros namespaces.

**Corrección**: Helm template `network-policy.yaml` con 4 políticas:
1. Default-deny ingress para todos los pods del release
2. Allow-from-anywhere para el pod `web` (frontend público)
3. Allow-intra-namespace para todos los pods backend (web→backend, users→cascade, etc.)
4. Allow-intra-namespace para NATS

Controlado por `networkPolicy.enabled: true` en `values.yaml`.

---

### 🟡 MEDIA — M3: Sin endpoint de logout ✅ CERRADO

No existía forma de invalidar un token de sesión activo. Un token robado era válido hasta su expiración (24h).

**Corrección**: `POST /api/v1/auth/logout` — extrae el Bearer token y llama a `LogoutUseCase → TokenService.revokeToken() → SqliteTokenRepository.revokeByValue()`. Actualiza `revoked_at` en la tabla `tokens`. El frontend (`authStore.logout()`) llama al endpoint antes de limpiar el estado local (best-effort: limpia siempre, falle o no el servidor).

---

### 🟡 MEDIA — M4: Path traversal en servicio de presentaciones ✅ CERRADO

`server.js` usaba `req.params.id` directamente en `path.join()` para construir rutas del sistema de archivos. Una entrada como `../../etc/passwd` podría acceder a archivos fuera del directorio de presentaciones.

**Corrección**: Regex UUID `^[0-9a-f]{8}-...$` validada antes de cualquier operación con el parámetro. Los 6 endpoints (POST upload, GET slides, GET preview, GET pdf, GET status, DELETE) rechazan con 400 si el ID no es un UUID válido.

---

### 🟢 BAJA — AR3: Sin backup automatizado de SQLite ✅ CERRADO

Las bases de datos SQLite (users, stats) no tenían backup automatizado. Una pérdida del PVC implicaba pérdida total de datos.

**Corrección**: Helm template `sqlite-backup-cronjob.yaml` — CronJob diario (02:00 UTC) por cada servicio con `persistence.enabled` + `DB_PATH`. Usa `alpine+sqlite3` con la API de backup online (`.backup` command — safe para DBs en vivo). Retiene los últimos 7 backups en `/data/backups/`. Controlado por `backup.enabled: true` en `values.yaml`.

---

## Resumen de cierre

| ID | Severidad | Descripción | Estado | Commit |
|----|-----------|-------------|--------|--------|
| #1 | 🔴 Crítica | Moderación sin auth | ✅ Cerrado | prior |
| #2 | 🔴 Alta | `/internal/evaluate` sin X-Internal-Auth | ✅ Cerrado | prior |
| #3 | 🔴 Alta | `/internal/update` sin X-Internal-Auth | ✅ Cerrado | prior |
| #4 | 🔴 Alta | `/internal/recalc` sin X-Internal-Auth | ✅ Cerrado | prior |
| #5 | 🟡 Media | Query público sin auth | ✅ Aceptado por diseño | — |
| #6 | 🟡 Media | Stats sin auth | ✅ Cerrado | prior |
| #7 | 🟢 Baja | `validInternalAuth` fail-open | ✅ Cerrado | C2 |
| C1 | 🔴 Crítica | 4 DELETE endpoints sin X-Internal-Auth | ✅ Cerrado | `6557a7d` |
| C2 | 🔴 Crítica | Internal auth fail-open → fail-closed | ✅ Cerrado | `6557a7d` |
| C3 | 🔴 Crítica | SHA-256 sin sal → PBKDF2 | ✅ Cerrado | `e7048c5` |
| C4 | 🔴 Crítica | Tokens en claro en BD → SHA-256 at rest | ✅ Cerrado | `e7048c5` |
| C5 | 🔴 Crítica | Sin rate limiting en auth | ✅ Cerrado | `fde6490` |
| A1 | 🔴 Alta | Sin límite de tamaño en upload | ✅ Cerrado | `6557a7d` |
| A2 | 🔴 Alta | Sin NetworkPolicy | ✅ Cerrado | `fde6490` |
| M3 | 🟡 Media | Sin endpoint de logout | ✅ Cerrado | `fde6490` |
| M4 | 🟡 Media | Path traversal en presentations | ✅ Cerrado | `6557a7d` |
| AR3 | 🟢 Baja | Sin backup automatizado | ✅ Cerrado | `e7048c5` |
