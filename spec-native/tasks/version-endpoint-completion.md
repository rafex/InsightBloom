# Plan: Completar endpoint /version — 3 tareas faltantes

## Estado: in_progress

## Contexto

El plan original (`version-endpoint.md`) tiene 8 pasos. Los pasos 1-6 se completaron
en commits anteriores. Quedan 3 tareas que el coder agent no ejecutó:

1. Endpoint `/version` en presentations (Node.js, NO Java)
2. ARG/ENV en Dockerfile de presentations
3. `build-args` en `publish_container.yml` (todos los jobs)

**Corrección importante:** El plan original decía "PresentationsApplication.java"
pero el servicio `insightbloom-presentations` es **Node.js/Express** (`server.js`),
no Java. El coder agent no encontró el archivo porque buscaba un `*Application.java`
que no existe.

---

## Task 1 — Endpoint `/version` en presentations (server.js)

**Archivo:** `backend/services/insightbloom-presentations/server.js`
**Línea 374:** `app.get('/health', (_req, res) => res.json({ status: 'ok' }));`

**Cambiar a:**

```javascript
app.get('/version', (_req, res) => res.json({
  service: 'insightbloom-presentations',
  version: process.env.APP_VERSION || 'dev',
  gitSha: process.env.GIT_SHA || 'unknown',
}));

app.get('/health', (_req, res) => res.json({ status: 'ok' }));
```

---

## Task 2 — ARG/ENV en Dockerfile de presentations

**Archivo:** `backend/services/insightbloom-presentations/Dockerfile`
**Después de línea 9:** `ENV DATA_DIR=/data`

**Insertar:**

```dockerfile
ARG APP_VERSION=dev
ARG GIT_SHA=unknown
ENV APP_VERSION=${APP_VERSION}
ENV GIT_SHA=${GIT_SHA}
```

---

## Task 3 — build-args en `publish_container.yml`

**Archivo:** `.github/workflows/publish_container.yml`

### 3a. Servicios Java (7 jobs) — agregar 2 líneas a `build-args` existente

| Job | Línea actual (último build-arg) | Agregar después |
|-----|-------------------------------|-----------------|
| users | L80: `SERVICE_PORT=8081` | `APP_VERSION=${{ steps.image_tag.outputs.value }}` + `GIT_SHA=${{ github.sha }}` |
| tools-gateway | L144: `SERVICE_PORT=8090` | idem |
| stats | L210: `SERVICE_PORT=8085` | idem |
| survey | L272: `SERVICE_PORT=8086` | idem |
| moderation | L386: `SERVICE_PORT=8084` | idem |
| query | L450: `SERVICE_PORT=8083` | idem |
| ingest | L516: `SERVICE_PORT=8082` | idem |

Ejemplo (users):
```yaml
          build-args: |
            SERVICE=insightbloom-users
            SERVICE_PORT=8081
            APP_VERSION=${{ steps.image_tag.outputs.value }}
            GIT_SHA=${{ github.sha }}
```

### 3b. Presentations (Node.js) — NUEVO `build-args`

**Línea 320:** después de `file: backend/services/insightbloom-presentations/Dockerfile`

**Insertar:**
```yaml
          build-args: |
            APP_VERSION=${{ steps.image_tag.outputs.value }}
            GIT_SHA=${{ github.sha }}
```

### 3c. Chat (Python) — NUEVO `build-args`

**Línea 564:** después de `file: chat/Dockerfile`

**Insertar:**
```yaml
          build-args: |
            APP_VERSION=${{ steps.image_tag.outputs.value }}
            GIT_SHA=${{ github.sha }}
```

### 3d. Telegram (Python) — NUEVO `build-args`

**Línea 612:** después de `file: telegram/Dockerfile`

**Insertar:**
```yaml
          build-args: |
            APP_VERSION=${{ steps.image_tag.outputs.value }}
            GIT_SHA=${{ github.sha }}
```

### 3e. Frontend (web) — NUEVO `build-args`

**Línea 731:** después de `file: container/frontend/Dockerfile`

**Insertar:**
```yaml
          build-args: |
            VITE_APP_VERSION=${{ steps.image_tag.outputs.value }}
            VITE_GIT_SHA=${{ github.sha }}
```

### 3f. code-ide — SIN CAMBIOS

No es servicio de aplicación, no necesita endpoint de versión.

---

## Verificación

1. `grep -n "build-args" .github/workflows/publish_container.yml` — debe mostrar 12 entradas (7 Java + presentations + chat + telegram + web = 11 con build-args, code-ide sin).
2. `grep -n "APP_VERSION\|GIT_SHA" backend/services/insightbloom-presentations/Dockerfile` — debe mostrar 4 líneas.
3. `grep -n "/version" backend/services/insightbloom-presentations/server.js` — debe mostrar 1 línea.

## Archivos modificados (3)

- `backend/services/insightbloom-presentations/server.js`
- `backend/services/insightbloom-presentations/Dockerfile`
- `.github/workflows/publish_container.yml`
