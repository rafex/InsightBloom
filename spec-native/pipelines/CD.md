# CD.md

Proceso de entrega y ambientes para InsightBloom.

## Ambientes

| Ambiente | Propósito | Infra | Trigger |
|----------|-----------|-------|---------|
| **Local** | Desarrollo | Docker Compose (`container/compose.yml`) | Manual (`just container-dev`) |
| **K3s (insightbloom)** | Demo/staging | K3s + Helm (gestionado por FluxCD) | Automático al publicarse una imagen nueva en GHCR |
| **GHCR** | Registro de imágenes | GitHub Container Registry | Push a `main` (path-filtered) o tag `v*.*` |

## Workflows de CI (build + publish a GHCR)

Cada imagen tiene su propio workflow bajo `.github/workflows/publish-*.yml`, disparado
solo cuando cambian los archivos de ese servicio (`paths:` filter) — así los 12 servicios
crecen de forma independiente sin reconstruirse entre sí en cada push. Dos workflows con
prefijo `_` son reusable workflows (`workflow_call`, no se disparan solos) que concentran
la lógica compartida de resolución/validación de tag, login a GHCR y setup de Buildx.

### Reusable workflows

| Archivo | Usado por | Qué hace |
|---------|-----------|----------|
| `_build-java-service.yml` | los 7 workflows Java (abajo) | Build+push desde `container/backend/java/Dockerfile` (build-args `SERVICE`/`SERVICE_PORT`). El propio Dockerfile compila el reactor Maven completo dentro del build de Docker (stage builder) — no hay paso de `mvn package` en el runner. |
| `_build-standalone-service.yml` | presentations, chat, telegram | Build+push directo desde el Dockerfile propio de cada servicio (Node/Python autocontenidos, sin paso de build previo en el runner). |

### Workflows por servicio

| Workflow | Servicio | Puerto | Dockerfile | Imagen GHCR |
|----------|----------|--------|------------|-------------|
| `publish-users.yml` | insightbloom-users | 8081 | `container/backend/java/Dockerfile` | `insightbloom-users` |
| `publish-tools-gateway.yml` | insightbloom-tools-gateway | 8090 | `container/backend/java/Dockerfile` | `insightbloom-tools-gateway` |
| `publish-stats.yml` | insightbloom-stats | 8085 | `container/backend/java/Dockerfile` | `insightbloom-stats` |
| `publish-survey.yml` | insightbloom-survey | 8086 | `container/backend/java/Dockerfile` | `insightbloom-survey` |
| `publish-moderation.yml` | insightbloom-moderation | 8084 | `container/backend/java/Dockerfile` | `insightbloom-moderation` |
| `publish-query.yml` | insightbloom-query | 8083 | `container/backend/java/Dockerfile` | `insightbloom-query` |
| `publish-ingest.yml` | insightbloom-ingest | 8082 | `container/backend/java/Dockerfile` | `insightbloom-ingest` |
| `publish-presentations.yml` | insightbloom-presentations (Node) | — | `backend/services/insightbloom-presentations/Dockerfile` | `insightbloom-presentations` |
| `publish-chat.yml` | chat (Python/FastAPI) | — | `chat/Dockerfile` | `insightbloom-chat` |
| `publish-telegram.yml` | telegram (Python/FastAPI) | — | `telegram/Dockerfile` | `insightbloom-telegram` |
| `publish-web.yml` | frontend (Vue) | — | `container/frontend/Dockerfile` | `insightbloom-web` |
| `publish-code-ide.yml` | sandbox IDE (4 imágenes vía matriz) | — | `infra/docker/Dockerfile.code-ide-*` | `insightbloom-code-ide-server` / `insightbloom-code-ide-runtime` |

Los 7 workflows Java disparan además si cambia `backend/common/**`, `backend/contracts/**`
o `pom.xml` (dependencias reales del reactor Maven), no solo su propio directorio.

`publish-code-ide.yml` se mantiene deliberadamente sin `needs:` de nadie ni como `needs:`
de nadie — ver comentario en el propio archivo (postmortem 2026-07-13: acoplarlo a un job
de deploy hizo que un fallo ahí frenara imágenes ya listas de la aplicación real).

**Trigger de cada workflow**: `push` a `main` (con `paths:` filter propio) + `push` de tag
`v*.*` (mismo `paths:` filter — un tag no reconstruye un servicio si no cambió desde el
último tag) + `workflow_dispatch` manual con input `version_tag` (formato `vN.YYYYmmDD[-N]`).
`publish-code-ide.yml` es la excepción: su `workflow_dispatch` no pide `version_tag` (usa
tags fijos `latest`/`python`/`java`/`web` + `build-${{ github.run_number }}`).

**Versionado de tags** (igual en los 14 workflows):
- Tag semántico (`v1.20260424`) → tag + sha
- Push a main → `latest` + sha + `build-${{ github.run_number }}`
- Manual dispatch → tag validado (formato `vN.YYYYmmDD[-N]`)

**Características comunes**: multi-plataforma `linux/amd64`, cache GHA (`type=gha`, con
`scope` por servicio en los reusable workflows para no pisarse cache entre sí),
`provenance: false` / `sbom: false` (optimización de tiempo de build).

## Deploy a K3s

El deploy **no vive en este repositorio** — no hay ningún workflow de `deploy`/`helm upgrade`
aquí. FluxCD corre en el cluster k3s y observa las imágenes publicadas en GHCR vía
`ImageUpdateAutomation`, siguiendo la convención de tag `build-${{ github.run_number }}` que
generan los 14 workflows de arriba; al detectar una imagen nueva, Flux actualiza el
`HelmRelease` correspondiente en el repo separado `InsightBloom-gitops`, que es la fuente de
verdad del manifiesto Helm desplegado (valores, secrets, RBAC, etc. — no duplicar esa
configuración aquí). El polling de Flux no es instantáneo: puede haber un delay entre el
push de una imagen nueva y el rollout real en el cluster.

Para detalles de secrets/values/política de reconciliación de Flux, ver el repo
`InsightBloom-gitops` directamente — está fuera del alcance de este documento.

## Verificación manual del cluster

```bash
ssh my-k3s "sudo kubectl get pods -n insightbloom -o custom-columns=NAME:.metadata.name,IMAGE:.spec.containers[0].image"
```

Confirma qué tag de imagen está corriendo cada Deployment — útil para verificar si Flux ya
reconcilió una imagen nueva.
