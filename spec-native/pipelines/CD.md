# CD.md

Proceso de entrega y ambientes para InsightBloom.

## Ambientes

| Ambiente | Propósito | Infra | Trigger |
|----------|-----------|-------|---------|
| **Local** | Desarrollo | Docker Compose (`container/compose.yml`) | Manual (`just container-dev`) |
| **K3s (insightbloom)** | Demo/staging | K3s + Helm | Push a `main` o tag `v*.*` |
| **GHCR** | Registro de imágenes | GitHub Container Registry | Push a `main` o tag `v*.*` |

## Workflows de CD

### 1. Build & Publish Containers (`.github/workflows/publish_container.yml`)

**Trigger**: push a `main`, push de tag `v*.*`, workflow_dispatch manual.
**Permisos**: `packages: write` para GHCR.

**9 jobs paralelos** de build de imágenes Docker:

| Job | Servicio | Dockerfile | Registro |
|-----|----------|------------|----------|
| `build-and-push-users` | users (8081) | `container/backend/java/Dockerfile` | `ghcr.io/rafex/insightbloom-users` |
| `build-and-push-stats` | stats (8085) | `container/backend/java/Dockerfile` | `ghcr.io/rafex/insightbloom-stats` |
| `build-and-push-survey` | survey (8086) | `container/backend/java/Dockerfile` | `ghcr.io/rafex/insightbloom-survey` |
| `build-and-push-presentations` | presentations (8091) | `backend/services/insightbloom-presentations/Dockerfile` | `ghcr.io/rafex/insightbloom-presentations` |
| `build-and-push-moderation` | moderation (8084) | `container/backend/java/Dockerfile` | `ghcr.io/rafex/insightbloom-moderation` |
| `build-and-push-query` | query (8083) | `container/backend/java/Dockerfile` | `ghcr.io/rafex/insightbloom-query` |
| `build-and-push-ingest` | ingest (8082) | `container/backend/java/Dockerfile` | `ghcr.io/rafex/insightbloom-ingest` |
| `build-and-push-chat` | chat (8090) | `chat/Dockerfile` | `ghcr.io/rafex/insightbloom-chat` |
| `build-and-push-web` | web (80) | `container/frontend/Dockerfile` | `ghcr.io/rafex/insightbloom-web` |

**Versionado de tags**:
- Tag semántico (`v1.20260424`) → tag + sha
- Push a main → `latest` + sha
- Manual dispatch → tag validado (formato `vN.YYYYmmDD[-N]`)

**Características del pipeline**:
- Multi-plataforma: `linux/amd64`
- Cache GHA para builds Maven y npm
- `provenance: false`, `sbom: false` (optimización)
- Java services compilan `mvn -DskipTests package -q` antes del build Docker
- Node services (web) compilan `npm ci && npm run build` antes del build Docker

### 2. Deploy to K3s (`.github/workflows/deploy.yml`)

**Trigger**: llamado desde `publish_container.yml` al terminar (solo en `main` o tag).
También ejecutable manualmente via `workflow_dispatch`.

**Proceso**:
1. Configurar `kubeconfig` desde secreto `KUBE_CONFIG_DATA` (base64)
2. Reemplazar server del cluster con `SERVER_K3S` (variable)
3. Instalar Helm
4. Resolver tag de imagen
5. Crear/asegurar namespace `insightbloom`
6. Upsert de secrets en K3s:
   - `{release}-admin-auth`: username + password del admin
   - `{release}-chat-secrets`: LLM API key + chat secret key
   - `{release}-users-secrets`: Twilio auth token + Zoho SMTP password (opcionales)
   - `{release}-internal-secrets`: INTERNAL_API_KEY (comunicación entre servicios)
7. Lint del chart Helm (`helm lint`)
8. Deploy con Helm:
   ```bash
   helm upgrade --install insightbloom infra/helm/charts/insightbloom \
     --namespace insightbloom \
     --set image.tag=<tag> \
     --atomic --wait --timeout 10m
   ```
9. Force rollout restart (para imágenes `latest`)
10. Verificar rollout status de cada deployment
11. Compilar y ejecutar CLI admin para asegurar usuario admin existe
12. Dump de diagnóstico en caso de fallo (deployments, pods, events)

**Secretos requeridos** (GitHub Secrets):
- `KUBE_CONFIG_DATA` — kubeconfig base64 del cluster K3s
- `PASSWORD_ADMIN_USER` — contraseña del admin (ORGANIZER + ADMIN)
- `LLM_PROVIDER_API_KEY` — API key del proveedor LLM (obligatorio)
- `CHAT_SECRET_KEY` — clave de cifrado para tokens del chat
- `TWILIO_AUTH_TOKEN` — OTP SMS (opcional)
- `ZOHO_SMTP_PASSWORD` — email OTP (opcional)
- `INTERNAL_API_KEY` — clave para `/internal/*` (opcional, sin ella endpoints sin proteger)

**Variables requeridas** (GitHub Variables):
- `SERVER_K3S` — IP/hostname del servidor K3s
- `LLM_PROVIDER_BASE_URL` — endpoint del LLM
- `LLM_PROVIDER_MODEL` — modelo a usar
- `TWILIO_ACCOUNT_SID` / `TWILIO_FROM_NUMBER` — OTP SMS
- `ZOHO_SMTP_HOST` / `ZOHO_SMTP_PORT` / `ZOHO_SMTP_USERNAME` / `ZOHO_FROM_ADDRESS` — email
- `ROBERTO_SYSTEM_PROMPT` — prompt del sistema para el bot (opcional)

## Rollback

```bash
helm rollback insightbloom --namespace insightbloom
```

## Configuración de secretos

Los secretos se gestionan exclusivamente via GitHub Secrets → Kubernetes Secrets.
Nunca en el repositorio (ver `.gitignore`).
