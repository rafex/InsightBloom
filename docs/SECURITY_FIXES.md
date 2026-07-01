# Correcciones de Seguridad — InsightBloom

_Fecha: 2026-06-30 | Rama: `main` | Commits: `6557a7d`, `fde6490`, `e7048c5`_

Este documento describe las correcciones de seguridad aplicadas como resultado de la auditoría experta realizada sobre el proyecto. La auditoría analizó autenticación, criptografía, validación de entradas, control de red e infraestructura operativa.

---

## Contexto

InsightBloom pasó de ser un MVP a un sistema con usuarios reales, por lo que se realizó una auditoría de seguridad completa. Se identificaron **17 hallazgos** (7 en una primera ronda previa, 10 en esta ronda). Todos fueron corregidos.

La segunda ronda abarcó:
- Autenticación interna entre microservicios
- Criptografía de contraseñas y tokens
- Validación de entradas en el servicio de presentaciones
- Rate limiting en endpoints de autenticación
- Aislamiento de red en Kubernetes
- Endpoint de logout
- Backup operativo de las bases de datos

---

## C1 — Endpoints DELETE sin autenticación interna

**Severidad**: Crítica  
**Archivos modificados**:
- `backend/services/insightbloom-ingest/.../handlers/IngestHandler.java`
- `backend/services/insightbloom-query/.../handlers/ConferenceQueryHandler.java`
- `backend/services/insightbloom-moderation/.../handlers/ConferenceModerationHandler.java`
- `backend/services/insightbloom-survey/.../handlers/SurveyHandler.java`

Los endpoints `DELETE /api/v1/conferences/{uuid}` en los cuatro servicios no validaban el header `X-Internal-Auth`. Cualquier petición HTTP al endpoint podía borrar todos los datos de una conferencia, ya que nginx enruta públicamente `/api/ingest`, `/api/query`, `/api/moderation` y `/api/survey`.

**Corrección**: Se añadió al inicio del método `delete()` de cada handler:

```java
if (!validInternalAuth(jx)) {
    sendError(jx, 403, "forbidden", "Internal access only");
    return true;
}
```

`validInternalAuth()` vive en `BaseResourceHandler` (backend/common) y compara el header `X-Internal-Auth` contra la env var `INTERNAL_API_KEY`.

---

## C2 — Internal auth fail-open → fail-closed

**Severidad**: Crítica  
**Archivo modificado**: `backend/common/.../http/BaseResourceHandler.java`

La función `validInternalAuth()` retornaba `true` cuando `INTERNAL_API_KEY` no estaba configurada, permitiendo que cualquier petición pasara sin autenticación.

**Corrección**:

```java
protected static boolean validInternalAuth(final JettyHttpExchange jx) {
    final String key = System.getenv("INTERNAL_API_KEY");
    if (key == null || key.isEmpty()) {
        System.err.println("SECURITY WARNING: INTERNAL_API_KEY is not set — rejecting internal request");
        return false;  // fail-closed
    }
    final String header = jx.request().getHeaders().get("X-Internal-Auth");
    return key.equals(header);
}
```

---

## C3 — Contraseñas: SHA-256 sin sal → PBKDF2

**Severidad**: Crítica  
**Archivos nuevos/modificados**:
- `backend/services/insightbloom-users/.../domain/services/PasswordService.java` _(nuevo)_
- `backend/services/insightbloom-users/.../application/usecases/LoginUseCase.java`
- `backend/services/insightbloom-users/.../application/usecases/RegisterUseCase.java`
- `backend/services/insightbloom-users/.../application/usecases/ChangePasswordUseCase.java`
- `backend/services/insightbloom-users/.../bootstrap/UsersApplication.java`

Las contraseñas se almacenaban como `SHA-256(password)` — sin sal, algoritmo rápido, vulnerable a rainbow tables y ataques de diccionario con GPU.

**Corrección**: `PasswordService` wrappea la librería `ether-crypto` (PBKDF2-HMAC-SHA256):
- 310 000 iteraciones (recomendación OWASP 2024)
- Salt aleatorio de 16 bytes generado con `SecureRandom`
- Formato: `$pbkdf2$310000$<salt_hex>$<hash_hex>`

**Migración transparente**: El login detecta si el hash almacenado es legacy (sin prefijo `$pbkdf2$`) y, tras verificar correctamente con SHA-256, reemplaza el hash por la versión PBKDF2. Los usuarios existentes son migrados automáticamente en su próximo login — sin bloqueo de cuentas ni intervención manual.

```java
if (passwordService.isLegacyHash(u.getPasswordHash())) {
    u.setPasswordHash(passwordService.hash(request.password()));
    userRepository.save(u);
}
```

---

## C4 — Tokens de sesión en claro → hasheados en BD

**Severidad**: Crítica  
**Archivo modificado**: `backend/services/insightbloom-users/.../adapters/outbound/sqlite/SqliteTokenRepository.java`

La tabla `tokens` almacenaba el valor crudo de los tokens de sesión (UUID pares de 64 hex chars). Un acceso no autorizado a la base de datos permitía impersonar cualquier sesión activa.

**Corrección**: Se almacena `SHA-256(token_value)` en la columna `token_value`. El cliente recibe y envía siempre el token en claro; la BD nunca lo ve.

```java
private static String hashToken(final String rawToken) {
    return PasswordService.sha256(rawToken);
}

// En save(): ps.setString(5, hashToken(token.getTokenValue()));
// En findByValue(): ps.setString(1, hashToken(tokenValue));
// En revokeByValue(): ps.setString(2, hashToken(tokenValue));
```

**Efecto de deployment**: Los tokens existentes fueron invalidados automáticamente (los valores crudos no coinciden con el hash esperado). Todos los usuarios debieron iniciar sesión de nuevo tras el deploy.

---

## C5 — Rate limiting en endpoints de autenticación

**Severidad**: Crítica  
**Archivo modificado**: `container/frontend/nginx.conf`

No existía ninguna limitación de velocidad en login, registro ni envío de OTP — ataques de fuerza bruta y enumeración de cuentas eran posibles.

**Corrección**: Dos zonas de rate limiting y bloques `location` específicos **antes** del catch-all `/api/users` (prioridad nginx: match exacto > regex > prefijo):

```nginx
limit_req_zone $binary_remote_addr zone=auth_limit:10m rate=5r/m;
limit_req_zone $binary_remote_addr zone=otp_limit:10m  rate=2r/m;

location = /api/users/api/v1/auth/otp/send {
    limit_req zone=otp_limit burst=2 nodelay;
    ...
}

location ~ ^/api/users/api/v1/auth/(login|register|otp/verify)$ {
    limit_req zone=auth_limit burst=3 nodelay;
    ...
}
```

| Endpoint | Límite | Burst |
|----------|--------|-------|
| `/auth/otp/send` | 2 req/min por IP | 2 |
| `/auth/login`, `/auth/register`, `/auth/otp/verify` | 5 req/min por IP | 3 |

---

## A1 — Límite de tamaño en upload de presentaciones

**Severidad**: Alta  
**Archivo modificado**: `backend/services/insightbloom-presentations/server.js`

Multer no tenía límite de tamaño. Un archivo ZIP de varias GB podía agotar disco y memoria del pod.

**Corrección**:

```js
const upload = multer({
    dest: uploadDir,
    limits: { fileSize: 100 * 1024 * 1024 }  // 100 MB
})
```

---

## A2 — NetworkPolicy: aislamiento de red entre pods

**Severidad**: Alta  
**Archivo nuevo**: `infra/helm/charts/insightbloom/templates/network-policy.yaml`

Sin NetworkPolicy, cualquier pod del clúster podía conectarse directamente a cualquier microservicio del namespace operativo de InsightBloom, eludiendo el proxy nginx.

**Corrección**: 4 NetworkPolicy aplicadas vía Helm:

1. **Default-deny** ingress para todos los pods del release
2. **Allow-from-anywhere** para el pod `web` — es el único punto público
3. **Allow-intra-namespace** para todos los pods backend — permite nginx→backend, users→cascade, chat→backend
4. **Allow-intra-namespace** para NATS

Habilitado con `networkPolicy.enabled: true` en `values.yaml`.

---

## M3 — Endpoint de logout con revocación de token

**Severidad**: Media  
**Archivos nuevos/modificados**:
- `backend/services/insightbloom-users/.../application/usecases/LogoutUseCase.java` _(nuevo)_
- `backend/services/insightbloom-users/.../domain/ports/TokenRepository.java`
- `backend/services/insightbloom-users/.../adapters/outbound/sqlite/SqliteTokenRepository.java`
- `backend/services/insightbloom-users/.../domain/services/TokenService.java`
- `backend/services/insightbloom-users/.../adapters/inbound/http/handlers/AuthHandler.java`
- `frontend/web/src/features/auth/authStore.js`

No existía forma de invalidar un token de sesión activo. Un token robado era válido durante 24h.

**Corrección**: Nuevo endpoint `POST /api/v1/auth/logout`:

```java
// Extrae Bearer token, llama a:
logoutUseCase.execute(auth.substring(7));
// → tokenService.revokeToken(tokenValue)
// → tokenRepository.revokeByValue(tokenValue)
// → UPDATE tokens SET revoked_at = NOW() WHERE token_value = SHA256(token)
```

El frontend llama al endpoint antes de limpiar estado local. Si el servidor falla, el estado local se limpia igualmente (best-effort).

---

## M4 — Path traversal en servicio de presentaciones

**Severidad**: Media  
**Archivo modificado**: `backend/services/insightbloom-presentations/server.js`

`req.params.id` se usaba directamente en `path.join()` para construir rutas del sistema de archivos. Una entrada como `../../etc/passwd` podría leer archivos fuera del directorio de presentaciones.

**Corrección**: Validación UUID antes de cualquier operación de fichero, aplicada a los 6 endpoints:

```js
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function validConferenceId(id) {
    return typeof id === 'string' && UUID_RE.test(id)
}

// En cada endpoint:
if (!validConferenceId(req.params.id)) {
    return res.status(400).json({ error: 'invalid_conference_id' })
}
```

---

## AR3 — Backup automatizado de SQLite

**Severidad**: Baja (riesgo operativo)  
**Archivos nuevos/modificados**:
- `infra/helm/charts/insightbloom/templates/sqlite-backup-cronjob.yaml` _(nuevo)_
- `infra/helm/charts/insightbloom/values.yaml`

Las bases de datos SQLite (users, stats) no tenían backup automatizado. Una pérdida o corrupción del PVC implicaba pérdida total e irrecuperable de datos de usuarios y conferencias.

**Corrección**: CronJob Helm por cada servicio con `persistence.enabled` y `DB_PATH` definido:

- **Horario**: diario a las 02:00 UTC (`"0 2 * * *"`)
- **Imagen**: `alpine:3` con `sqlite3` instalado en tiempo de ejecución
- **Método**: API de backup online de SQLite (`.backup` command) — seguro para bases de datos en uso
- **Retención**: últimos 7 backups en `/data/backups/<service>-YYYYMMDD-HHMMSS.db`
- **Control**: `backup.enabled: true` / `backup.retain: 7` en `values.yaml`

```yaml
backup:
  enabled: true
  schedule: "0 2 * * *"
  retain: 7
```

---

## Commits asociados

| Commit | Descripción | Items |
|--------|-------------|-------|
| `6557a7d` | Hardening endpoints DELETE + validación presentaciones | C1, C2, A1, M4 |
| `fde6490` | Logout endpoint, rate limiting nginx, NetworkPolicy | M3, C5, A2 |
| `e7048c5` | PBKDF2 passwords, token hashing, backup CronJob | C3, C4, AR3 |

---

## Estado final

Todos los hallazgos de la auditoría están cerrados. No quedan issues de seguridad abiertos identificados durante este proceso.

Para hallazgos futuros o nuevas auditorías, documentar en `agents/SECURITY.md`.
