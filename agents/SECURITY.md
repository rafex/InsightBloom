# Security Audit Report — InsightBloom

_Fecha: 2026-06-26 | Auditor: @opencode | Severidad máxima: 🔴 CRÍTICA_

---

## Resumen ejecutivo

Se identificaron **7 hallazgos** de seguridad: 1 crítico, 3 altos, 2 medios, 1 bajo.
El riesgo principal es que los endpoints de moderación y comunicación interna entre servicios no tienen autenticación, y los puertos de los microservicios están expuestos directamente al host en Docker Compose, permitiendo saltarse el proxy nginx.

---

## Hallazgos detallados

### 🔴 CRÍTICA — #1: Moderación sin autenticación

**Endpoint**: `POST /api/v1/conferences/*` via nginx `/api/moderation`  
**Handler**: `ConferenceModerationHandler` (`insightbloom-moderation:8084`)  
**Archivo**: `backend/services/insightbloom-moderation/.../handlers/ConferenceModerationHandler.java`

El handler gestiona censura, restauración, edición y eliminación de palabras y mensajes. **No realiza ninguna validación de token.** Cualquier persona que conozca el UUID de una conferencia puede:

- `POST .../words/{wordId}/censor` — censurar palabras
- `POST .../words/{wordId}/restore` — restaurar palabras
- `POST .../words/{wordId}/edit` — editar palabras
- `DELETE .../words/{wordId}` — eliminar palabras
- `POST .../messages/{msgId}/censor` — censurar mensajes
- `POST .../messages/{msgId}/restore` — restaurar mensajes
- `POST .../messages/{msgId}/edit` — editar mensajes
- `DELETE .../messages/{msgId}` — eliminar mensajes
- `GET .../{conferenceId}/words` — listar palabras para moderar
- `GET .../{conferenceId}/messages` — listar mensajes

**Causa**: El handler no llama a `validateTokenUseCase`, ni extrae el header `Authorization`. No hay ninguna protección.

**Recomendación**: Añadir validación de token JWT al inicio de cada método `get()` y `post()`, verificando que el rol sea `organizer`. Usar `ValidateTokenUseCase` como lo hace `ConferenceHandler` en users.

---

### 🔴 ALTA — #2: `/internal/evaluate` sin protección

**Endpoint**: `POST /internal/evaluate` (moderation:8084)  
**Handler**: `InternalEvaluateHandler`  
**Archivo**: `backend/services/insightbloom-moderation/.../handlers/InternalEvaluateHandler.java`

Recibe payloads de evaluación de censura automática desde el servicio de ingest. **Sin header `X-Internal-Auth`.** Accesible directamente en `localhost:8084/internal/evaluate`.

**Recomendación**: Añadir `validInternalAuth(jx)` al inicio del método `post()`, mismo patrón que `VisibilityHandler`.

---

### 🔴 ALTA — #3: `/internal/update` sin protección

**Endpoint**: `POST /internal/update` (query:8083)  
**Handler**: `UpdateHandler`  
**Archivo**: `backend/services/insightbloom-query/.../handlers/UpdateHandler.java`

Recibe actualizaciones de nube de palabras desde stats/ingest. **Sin header `X-Internal-Auth`.** Accesible en `localhost:8083/internal/update`.

**Recomendación**: Añadir `validInternalAuth(jx)` al inicio del método `post()`.

---

### 🔴 ALTA — #4: `/internal/recalc` sin protección

**Endpoint**: `POST /internal/recalc` (stats:8085)  
**Handler**: `RecalcHandler`  
**Archivo**: `backend/services/insightbloom-stats/.../handlers/RecalcHandler.java`

Recibe solicitudes de recálculo de estadísticas desde ingest. **Sin header `X-Internal-Auth`.** Accesible en `localhost:8085/internal/recalc`.

**Recomendación**: Añadir `validInternalAuth(jx)` al inicio del método `post()`.

---

### 🟡 MEDIA — #5: Query expuesto sin auth

**Endpoint**: `GET /api/v1/conferences/{id}/cloud/*` via nginx `/api/query`  
**Handler**: `ConferenceQueryHandler` (query:8083)  
**Archivo**: `backend/services/insightbloom-query/.../handlers/ConferenceQueryHandler.java`

Expone nubes de palabras (`/cloud/doubts`, `/cloud/topics`) y timelines (`/words/{word}/timeline`) sin autenticación. Cualquiera con el UUID de conferencia puede leer estos datos.

**Evaluación**: Si los datos de conferencia son públicos por diseño, puede ser aceptable. Si no, requiere validación de token.

---

### 🟡 MEDIA — #6: Stats expuesto sin auth

**Endpoint**: `GET /api/v1/conferences/{id}` (stats:8085)  
**Handler**: `StatsHandler`  
**Archivo**: `backend/services/insightbloom-stats/.../handlers/StatsHandler.java`

Expone overview y relevance de conferencias sin autenticación. No mapeado en nginx (solo accesible directo al puerto 8085).

---

### 🟢 BAJA — #7: Fallback inseguro en `validInternalAuth`

**Endpoint**: `/internal/visibility`, `/internal/message-visibility` (query:8083)  
**Handlers**: `VisibilityHandler`, `MessageVisibilityHandler`

La validación `X-Internal-Auth` tiene un fallback que permite el acceso si la variable de entorno `INTERNAL_API_KEY` no está configurada:

```java
if (key == null || key.isEmpty()) {
    return true; // ← permite todo en desarrollo
}
```

**Riesgo**: En entornos donde `INTERNAL_API_KEY` no se setea explícitamente (desarrollo local sin compose, CI), los endpoints quedan sin protección.

**Recomendación**: Loguear warning pero mantener el comportamiento para no romper desarrollo. Alternativa: generar una key por defecto con UUID aleatorio y exigirla siempre.

---

## Exposición de puertos

`container/compose.yml` mapea todos los puertos al host:

| Servicio | Puerto host | Puerto interno |
|----------|------------|----------------|
| users | 8081 | 8081 |
| ingest | 8082 | 8082 |
| query | 8083 | 8083 |
| moderation | 8084 | 8084 |
| stats | 8085 | 8085 |

Nginx (`container/frontend/nginx.conf`) solo enruta `/api/users`, `/api/ingest`, `/api/query`, `/api/moderation`, `/api/survey`. **Los endpoints `/internal/*` NO pasan por nginx** pero son accesibles directamente en los puertos mapeados.

**Recomendación**: Quitar `ports` del host para servicios internos en `compose.yml`, reemplazando con `expose` para que solo sean accesibles dentro de la red Docker `backend`. O mantener `ports` solo para desarrollo local y usar `INTERNAL_API_KEY` como protección.

---

## Scan de secretos

Resultado: 1 hallazgo — posible falso positivo.

| Archivo | Línea | Match |
|---------|-------|-------|
| `frontend/web/src/pages/profile/ProfilePage.vue` | 126 | `Passwo...` (probable placeholder de input) |

**Conclusión**: No se detectaron secretos reales expuestos en el código.

---

## Resumen de acciones

| Prioridad | Acción | Esfuerzo |
|-----------|--------|----------|
| 🔴 Inmediata | Añadir `X-Internal-Auth` a `InternalEvaluateHandler` | 5 min |
| 🔴 Inmediata | Añadir `X-Internal-Auth` a `UpdateHandler` | 5 min |
| 🔴 Inmediata | Añadir `X-Internal-Auth` a `RecalcHandler` | 5 min |
| 🔴 Alta | Añadir validación de token JWT a `ConferenceModerationHandler` | 30 min |
| 🟡 Media | Evaluar auth en `ConferenceQueryHandler` y `StatsHandler` | Decisión de producto |
| 🟢 Baja | Quitar `ports` del host o endurecer fallback de `validInternalAuth` | 10 min |

---

## Verificación de cierre

- [x] #1 `ConferenceModerationHandler` — `requireOrganizer(jx)` (valida Bearer token contra `insightbloom-users` y exige `role=organizer`) al inicio de `get()` (excepto el GET público de respuesta), `post()` y `patch()`. `delete()` (cascade interno desde users) se deja igual que el resto de endpoints de borrado por conferencia.
- [x] #2 `InternalEvaluateHandler` — `validInternalAuth(jx)` en `post()`; `HttpModerationClient` (ingest) ahora envía `X-Internal-Auth`
- [x] #3 `UpdateHandler` — `validInternalAuth(jx)` en `post()`; `HttpQueryClient` (ingest) ahora envía `X-Internal-Auth`
- [x] #4 `RecalcHandler` — `validInternalAuth(jx)` en `post()`; `HttpStatsClient` (ingest) ahora envía `X-Internal-Auth`
- [ ] #5 `ConferenceQueryHandler` — decisión de producto sobre si requiere auth (sigue público; los datos de nube/timeline se consideran públicos por diseño del portal de asistentes)
- [ ] #6 `StatsHandler` — decisión de producto sobre si requiere auth
- [ ] #7 `validInternalAuth` — se mantiene el fallback fail-open documentado (sin `INTERNAL_API_KEY` no se exige el header); ahora vive una sola vez en `BaseResourceHandler` (`backend/common`) en vez de duplicado por handler

`validInternalAuth` se promovió a `backend/common/.../BaseResourceHandler.java` para evitar duplicar la lógica en cada handler; `VisibilityHandler`/`MessageVisibilityHandler` (query) se actualizaron para usar la versión compartida. `INTERNAL_API_KEY` se añadió también a `ingest` y `stats` en `values.yaml` (antes solo lo tenían `moderation` y `query`), ya que ahora ambos envían/reciben el header.
