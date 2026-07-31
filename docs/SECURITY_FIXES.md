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

No existía forma de invalidar un token de sesión activo. Un token robado podía ser válido durante
24h.

La vigencia de los nuevos tokens es de una hora. El frontend comparte la sesión entre pestañas en
`localStorage` y sólo la renueva cerca del vencimiento cuando detecta actividad reciente; una
sesión inactiva no se prolonga.

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

## I1 — `INTERNAL_API_KEY` vacío rompía la autenticación interna entre microservicios

**Severidad**: Crítica (incidente operativo, no vulnerabilidad de código)
**Fecha**: 2026-07-05
**Archivos involucrados**:
- `.github/workflows/deploy.yml`
- Secreto de Kubernetes `insightbloom-internal-secrets` (namespace `insightbloom`)
- Secret de GitHub Actions `INTERNAL_API_KEY`

### Qué pasó

`validInternalAuth()` (ver **C2** arriba) compara el header `X-Internal-Auth` contra la env var `INTERNAL_API_KEY`, y fue diseñado para **fallar cerrado**: si la clave no está configurada, rechaza toda petición interna con 403 en vez de dejarla pasar. Ese diseño es correcto — el problema fue que la clave llevaba **vacía en producción desde que se creó el secreto** (2026-07-01), sin que nadie lo notara, porque el síntoma no es un error visible sino un silencio: los mensajes se aceptan (`HTTP 201`), pero nunca llegan a completarse en el servicio destino.

Esto rompió, sin lanzar ningún error visible al usuario:
- Las nubes de Dudas/Temas (`insightbloom-ingest` → `insightbloom-query`/`insightbloom-moderation`), reportado como *"ni por chat ni por el nuevo formulario registra dudas o temas"*.
- Cualquier otra llamada `/internal/*` entre servicios (conteo de descargas, borrado en cascada de conferencias, derivación de nombre desde la presentación, etc.).

**Causa raíz #1 (secreto nunca configurado)**: el secreto de Kubernetes se creó con el valor vacío y nadie lo notó porque el fallo es silencioso (`catch (Exception e) { /* fire and forget */ }` en los clientes HTTP internos — ver el fix de logging en `HttpQueryClient`/`HttpModerationClient` de `insightbloom-ingest`).

**Causa raíz #2 (reincidencia tras el primer fix)**: `deploy.yml` tiene un paso que en **cada deploy** sobreescribe el secreto de Kubernetes a partir del secret de GitHub Actions `INTERNAL_API_KEY`:

```yaml
- name: Upsert internal service-to-service secret in k3s
  env:
    INTERNAL_API_KEY: ${{ secrets.INTERNAL_API_KEY }}
  run: |
    kubectl -n "$K3S_NAMESPACE" create secret generic "${RELEASE_NAME}-internal-secrets" \
      --from-literal=internal-api-key="${INTERNAL_API_KEY:-}" \
      --dry-run=client -o yaml | kubectl apply -f -
```

El primer arreglo se aplicó **solo en el clúster** (`kubectl create secret ... | kubectl apply -f -`), pero nunca en el secret de GitHub Actions que este paso usa como fuente de verdad. El siguiente deploy automático (disparado por el propio push del fix de SSE) volvió a sobreescribir el secreto con el valor vacío de `secrets.INTERNAL_API_KEY`, que nunca se había configurado — reintroduciendo el mismo bug.

### Corrección

1. Generar una clave aleatoria fuerte: `openssl rand -hex 32`.
2. Aplicarla al secreto del clúster (para efecto inmediato):
   ```bash
   kubectl create secret generic insightbloom-internal-secrets -n insightbloom \
     --from-literal=internal-api-key=<valor> --dry-run=client -o yaml | kubectl apply -f -
   ```
3. **Persistirla como GitHub Actions secret** (esto es lo que evita la reincidencia, ya que `deploy.yml` la reaplica en cada deploy):
   ```bash
   gh secret set INTERNAL_API_KEY --repo rafex/InsightBloom --body "<mismo_valor>"
   ```
4. Reiniciar los deployments que la consumen: `users`, `stats`, `moderation`, `query`, `ingest`, `survey`, `web`, `telegram`, `presentations`.

### Ejemplo de valor válido

```
INTERNAL_API_KEY=394ecb672b214aa0187e85ed7b359937758c9b21159b1709570f1b36b511b823
```

_(Este valor de ejemplo fue el usado para el primer fix del 2026-07-05 y ya fue rotado — no es válido en producción. Nunca documentar aquí el valor **actualmente activo**.)_

Cualquier cadena aleatoria de alta entropía sirve (no tiene un formato especial que validar) — lo importante es generarla con un CSPRNG y no reutilizar valores predecibles como fechas, nombres de proyecto o placeholders (`changeme`, `secret123`, etc.). Generarla con:

```bash
openssl rand -hex 32
```

### Por qué no debe exponerse nunca

`INTERNAL_API_KEY` es la **única barrera** entre "cualquier pod del clúster" y "cualquier endpoint `/internal/*`" en los ocho microservicios Java (ver **C1**/**C2** arriba: el borrado en cascada de una conferencia, la actualización de nubes de palabras, el registro de descargas, etc. son todos endpoints internos protegidos solo por este header). Si se filtra:

- Alguien con esa clave y acceso de red al clúster (o, si `NetworkPolicy` no estuviera bien configurada — ver **A2** — incluso desde fuera) podría **borrar todos los datos de cualquier conferencia** vía los endpoints `DELETE /api/v1/conferences/{uuid}` de los cuatro servicios que los exponen.
- Podría inyectar actualizaciones falsas en las nubes de palabras o el conteo de descargas de cualquier conferencia.
- Al ser **una sola clave compartida por todos los servicios** (no una por servicio), su exposición compromete la autenticación interna de toda la plataforma de una vez, no solo de un microservicio.

Por eso:
- Nunca debe aparecer en logs, mensajes de commit, código fuente, ni en este documento con su valor real (el valor de ejemplo de arriba fue rotado inmediatamente después de escribir esto).
- Debe vivir únicamente en: el secret de GitHub Actions (`repo secrets`, no `variables`) y el Secret de Kubernetes — nunca en `values.yaml` ni en ConfigMaps.
- Si se sospecha una filtración, rotarla (repetir los pasos de corrección con un valor nuevo) invalida inmediatamente cualquier copia filtrada, sin necesidad de tocar código.

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
