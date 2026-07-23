# Security Audit Report — InsightBloom

_Fecha inicial: 2026-06-26 | Actualizado: 2026-07-22 | Estado: ✅ Correcciones implementadas; validar después del despliegue_

---

## Resumen ejecutivo

Se realizaron **tres rondas de auditoría**. Las dos primeras cubrieron autenticación, endpoints, criptografía, red e infraestructura; la tercera cubrió presentaciones, chat, frontend y CD. Los hallazgos confirmados tienen corrección implementada; las dependencias transitivas sin parche compatible quedan bajo seguimiento automatizado.

Estado actual: **ningún hallazgo crítico/alto de código abierto; dependencias upstream bajo seguimiento**.
El riesgo de compartir enlaces directos de 8x8 queda fuera de esta ronda porque
la integración actual ya no expone esos enlaces como mecanismo de entrada.

---

## Auditoría 3 — Endpoints, servicios, frontend y CD (2026-07-21)

Esta ronda cubrió el flujo que quedó incorporado después del crecimiento del
proyecto: carga y publicación de presentaciones Marp/Slidev, WebSocket de
presentaciones, webhook del chat, HTML generado, credenciales y permisos de
Kubernetes.

### A3 — Upload de presentaciones sin autorización ✅ CORREGIDO

`POST /api/v1/conferences/{id}/presentation` ahora valida el Bearer token en
`insightbloom-users` **antes** de que Multer lea el ZIP. El endpoint nuevo
`GET /api/v1/conferences/{id}/presentation-access` permite únicamente a un
administrador de plataforma, al propietario del evento o a un rol de evento con
`MANAGE_PRESENTATION`. El servicio de presentaciones usa ese endpoint para
upload y para emitir/controlar enlaces remotos.

### A4 — Bypass de archivos estáticos y ejecución de preview ✅ CORREGIDO

Todos los archivos de una presentación completa requieren acceso al evento;
ya no se protege solamente Slidev. El preview Marp elimina scripts, iframes,
object/embed, handlers `on*` y enlaces `javascript:` y responde con CSP sin
ejecución de scripts. El HTML Marp persistido se sanitiza con las mismas reglas
y su iframe usa un sandbox sin `allow-scripts`; Slidev mantiene sólo el runtime
necesario y queda sujeto a la auditoría de artefactos FAT.

### A5 — Tokens en URL de iframe/WebSocket/PDF ✅ CORREGIDO

El frontend primero inicializa una cookie `ib_token` HttpOnly, `SameSite=Lax` y
acotada al path de la conferencia. Después carga iframe, WebSocket y PDF sin
poner el token en la URL. El servicio de presentaciones ya no acepta
`ib_token` desde query string: sólo Authorization o la cookie de acceso. El
control remoto conserva únicamente su token HMAC propio, ahora con TTL de 30
minutos, comparación constant-time y rechazo si la clave interna está ausente.
La audiencia continúa validando boleto/acceso.

### A6 — Webhook del chat sin autenticación ✅ CORREGIDO

`POST /api/webhook/insightbloom` exige
`X-InsightBloom-Signature: HMAC-SHA256(raw_body)` y limita el cuerpo a 16 KiB.
GitOps inyecta la clave existente de chat como secreto para mantener una
migración coordinada sin publicar valores en Git.

### A7 — XSS y contraseñas reversibles en chat ✅ CORREGIDO

Los datos recientes y las menciones se construyen con `textContent`, nunca con
HTML interpolado. Los registros nuevos usan scrypt con salt aleatorio; las
cuentas Fernet antiguas se migran al primer login exitoso. La columna conserva
su nombre histórico `password_enc` para no romper SQLite.

### A8 — Amplificación de invitados y headers ✅ CORREGIDO

El canje de boleto invitado aplica rate limit por IP en nginx y verifica el
bloqueo de dispositivo antes de guardar el invitado o emitir el token. Web y
presentaciones agregan headers de seguridad; CI genera SBOM/provenance y ejecuta
lint, typecheck, pruebas y auditorías de dependencias.

### A9 — ServiceAccount compartido con permisos de sandbox ✅ CORREGIDO

GitOps deja `automountServiceAccountToken: false` para servicios normales y
crea `insightbloom-sandbox-manager` exclusivamente para `insightbloom-users`.
El RoleBinding de Pods/Services/NetworkPolicies apunta a ese ServiceAccount
dedicado.

### A10 — Autorización por substring y documentos de certificado ✅ CORREGIDO

Los roles legacy se comparan como tokens completos (`admin`, `organizer`,
`moderator`), no mediante `contains`, evitando que valores como `notadmin`
obtengan permisos. Los endpoints de configuración de certificados exigen el
rol correspondiente y validan límites, tipos, colores, imágenes locales y
bloques del JSON antes de entregarlo a Chromium con JavaScript deshabilitado.

### A11 — Persistencia del token principal en el navegador ✅ MITIGADO

El token `ib_token` ya no se guarda en `localStorage`; se mantiene sólo en
`sessionStorage` y se elimina al cerrar sesión o recibir un 401. Los tokens
legacy se migran una sola vez y se borran de `localStorage`. Esto reduce la
exposición en perfiles compartidos y tras cerrar el navegador. `sessionStorage`
todavía es legible por JavaScript durante la vida de la pestaña; el cierre
definitivo requiere migrar el flujo completo a una cookie HttpOnly con defensa
CSRF y conservar el token sólo en memoria como siguiente iniciativa.

### Verificación realizada

- `./mvnw -f pom.xml -pl backend/services/insightbloom-users -am test -DskipITs`
  — 154 tests correctos.
- `npm run lint`, `npm run typecheck` y `npm run test -- --run` en frontend —
  correctos (96 tests).
- `python3 -m pytest -q` en chat — 36 tests correctos.
- `node --check server.js` y `node --check live.js` — correctos.
- `git diff --check` — correcto.
- `node --check backend/services/insightbloom-presentations/server.js` y
  `node --check backend/services/insightbloom-presentations/live.js` — correctos.
- Suite Maven completa (`./mvnw -o test`) — BUILD SUCCESS.

### Riesgos transitorios de dependencias

Estado: **pendiente de resolver en una iniciativa posterior**. Auditoría ejecutada
el 2026-07-21 con `npm audit --omit=dev --json`.

#### SEC-DEP-001 — Slidev y `@hono/node-server`

- **Ruta:** `@slidev/cli@52.18.0` → `@modelcontextprotocol/sdk` →
  `@hono/node-server`.
- **Aviso:** path traversal en `serve-static` mediante una barra invertida
  codificada en Windows; CWE-22; CVSS 5.9; severidad moderada.
- **Corrección propuesta por npm:** bajar `@slidev/cli` a `52.16.0`.
  Es un cambio incompatible y no se aplicó.
- **Exposición actual:** el servicio ejecuta Slidev en pods Linux/K3s, no
  publica un servidor Hono directamente y procesa la presentación dentro de un
  staging aislado. La explotación en el despliegue actual es de baja
  probabilidad, pero la dependencia vulnerable permanece en la cadena de
  compilación.
- **Para resolver:** probar primero un override compatible de
  `@hono/node-server >=2.0.5`; si no funciona, actualizar Slidev a una versión
  que lo incorpore o evaluar el downgrade junto con pruebas de Marp/Slidev.
- **Criterio de cierre:** `npm audit --omit=dev` sin este aviso y build/export
  de una presentación Slidev real exitoso.

#### SEC-DEP-002 — `lodash-es` en Excalidraw/Mermaid

- **Ruta:** `@excalidraw/excalidraw@0.18.1` →
  `@excalidraw/mermaid-to-excalidraw` → `mermaid` → `dagre-d3-es` →
  `lodash-es@4.17.21`.
- **Avisos:** code injection vía `_.template` (CWE-94, CVSS 8.1, alta) y
  prototype pollution vía `_.unset`/`_.omit` (CWE-1321, CVSS 6.5, moderada).
- **Exposición actual:** ocurre en el navegador al convertir contenido Mermaid
  dentro de Excalidraw; no afecta directamente las sesiones, contraseñas ni
  tokens de InsightBloom. El riesgo depende de que una entrada controlada por
  el usuario alcance las funciones vulnerables durante esa conversión.
- **Corrección propuesta por npm:** bajar Excalidraw a `0.17.6`, con posible
  ruptura de APIs y comportamiento.
- **Para resolver:** probar una versión más nueva de Excalidraw/Mermaid,
  investigar si existe una versión corregida de `lodash-es` o reemplazar la
  ruta de conversión Mermaid. No usar `npm audit fix --force` sin pruebas de
  pizarra, exportación y carga de escenas.
- **Criterio de cierre:** eliminar el aviso de `lodash-es` y demostrar que una
  escena Mermaid controlada no permite ejecución ni modificación fuera del
  canvas.

#### SEC-DEP-003 — `nanoid` anidado en Excalidraw

- **Ruta:** `@excalidraw/excalidraw/node_modules/nanoid@3.3.3` y
  `@excalidraw/mermaid-to-excalidraw/node_modules/nanoid@4.0.2`.
- **Aviso:** generación predecible de IDs cuando recibe valores no enteros;
  CWE-835; CVSS 4.3; severidad moderada.
- **Exposición actual:** afecta identificadores internos de Excalidraw/Mermaid,
  no los tokens de autenticación ni los UUID de conferencias.
- **Corrección propuesta por npm:** también bajar Excalidraw a `0.17.6`.
- **Para resolver:** probar un override de `nanoid` a versiones corregidas o
  actualizar Excalidraw/Mermaid; verificar compatibilidad ESM/CJS y exportación
  de escenas.
- **Criterio de cierre:** no quedan versiones vulnerables en
  `npm ls nanoid` y las pruebas funcionales de Excalidraw pasan.

Los avisos de `@mermaid-js/parser`, `langium`, `chevrotain`,
`@chevrotain/gast` y `@chevrotain/cst-dts-gen` son efectos transitivos de la
misma cadena `lodash-es`; no deben tratarse como cinco problemas independientes.

#### Estado de mitigaciones y seguimiento

- DOMPurify ya se fuerza a `^3.4.12` mediante `overrides` y dejó de aparecer
  como riesgo activo.
- El CI ejecuta auditorías de npm, `pip-audit`, Dependabot y genera SBOM.
- La auditoría del frontend conserva `continue-on-error` porque el arreglo
  automático actual degrada Excalidraw; el reporte sigue visible en cada CI.
- No aplicar `npm audit fix --force` sin una rama de compatibilidad y pruebas
  manuales de Marp, Slidev, Drawio, Excalidraw y la vista pública.

### Controles operativos todavía recomendados

Estos puntos no abren por sí mismos un endpoint ni permiten el acceso directo
al evento, pero deben permanecer en el backlog de seguridad:

- añadir SAST y escaneo de secretos como jobs bloqueantes del CI;
- mantener un escaneo de imágenes y SBOM/provenance verificables en el
  registro;
- completar la migración de `sessionStorage` a cookie HttpOnly/CSRF;
- resolver las cadenas transitivas de `lodash-es`, `nanoid` y
  `@hono/node-server` cuando exista una actualización compatible.

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

Todos los pods del namespace operativo de InsightBloom podían recibir tráfico de cualquier origen — incluyendo pods comprometidos de otros namespaces.

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
