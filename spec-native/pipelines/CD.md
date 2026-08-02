# CD.md

Proceso de entrega y ambientes para InsightBloom.

## Límite de responsabilidad

InsightBloom está separado en dos repositorios operativos:

| Repositorio | Responsabilidad | No hace |
|-------------|-----------------|---------|
| `InsightBloom` | Código fuente, pruebas, Dockerfiles y CI para construir/publicar imágenes en GHCR | No administra el rollout del cluster ni es la fuente de verdad de FluxCD |
| `/Users/rafex/repository/github/rafex/InsightBloom-gitops` | Manifiestos Helm/GitOps, valores de ambiente y configuración observada por FluxCD | No contiene el código de aplicación ni construye las imágenes |

La entrega sigue esta cadena:

```text
push/merge a main
  -> GitHub Actions en InsightBloom
  -> imagen versionada en GHCR
  -> FluxCD en k3s-server1 detecta la nueva imagen
  -> InsightBloom-gitops actualiza/reconcilia el HelmRelease
  -> rollout en el namespace insightbloom
```

No se debe agregar un workflow de deploy, un `helm upgrade` del cluster o una
modificación manual de los manifiestos de despliegue en este repositorio como
parte del flujo normal.

## Ambientes

| Ambiente | Propósito | Infra | Trigger |
|----------|-----------|-------|---------|
| **Local** | Desarrollo | Docker Compose (`container/compose.yml`) | Manual (`just container-dev`) |
| **K3s (insightbloom)** | Demo/staging | K3s + Helm (gestionado por FluxCD) | Automático al publicarse una imagen nueva en GHCR |
| **GHCR** | Registro de imágenes | GitHub Container Registry | Push a `main` (path-filtered) o tag `v*.*` |

## Workflows de CI (build + publish a GHCR)

Cada imagen tiene su propio workflow bajo `.github/workflows/publish-*.yml`. Los servicios
de aplicación usan un `paths:` filter para crecer de forma independiente sin reconstruirse
entre sí en cada push. Las imágenes del IDE son la excepción: se reconstruyen en cada push a
`main` para que su SHA embebido y su tag inmutable no queden atrasados respecto al código que
las consume. Dos workflows con
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
| `publish-code-ide.yml` | sandbox IDE (3 imágenes vía matriz) | — | `infra/docker/Dockerfile.code-ide-*` | `insightbloom-code-ide-debian` / `insightbloom-code-ide-neovim*` |

Los 7 workflows Java disparan además si cambia `backend/common/**`, `backend/contracts/**`
o `pom.xml` (dependencias reales del reactor Maven), no solo su propio directorio.

`publish-code-ide.yml` se mantiene deliberadamente sin `needs:` de nadie ni como `needs:`
de nadie — ver comentario en el propio archivo (postmortem 2026-07-13: acoplarlo a un job
de deploy hizo que un fallo ahí frenara imágenes ya listas de la aplicación real).

**Trigger de cada workflow**: los servicios de aplicación usan `push` a `main` y `push` de
tag `v*.*` con su `paths:` filter propio, más `workflow_dispatch` manual con input
`version_tag` (formato `vN.YYYYmmDD[-N]`). `publish-code-ide.yml` es la excepción: corre en
cualquier `push` a `main` o tag `v*.*`, no pide `version_tag` en `workflow_dispatch` y publica
`latest` más `build-${{ github.run_id }}`. Ese tag inmutable es el que ImagePolicy de
InsightBloom-gitops promueve al HelmRelease; los sandboxes dinámicos no resuelven `latest`.

**Versionado de tags** (igual en los 14 workflows):
- Tag semántico (`v1.20260424`) → tag + sha
- Push a main → `latest` + sha + `build-${{ github.run_number }}`
- Manual dispatch → tag validado (formato `vN.YYYYmmDD[-N]`)

**Características comunes**: multi-plataforma `linux/amd64`, cache GHA (`type=gha`, con
`scope` por servicio en los reusable workflows para no pisarse cache entre sí),
`provenance: false` / `sbom: false` (optimización de tiempo de build).

## Deploy a K3s mediante FluxCD

El deploy **no vive en este repositorio** — no hay ningún workflow de `deploy`/`helm upgrade`
aquí. FluxCD corre en el cluster k3s y observa las imágenes publicadas en GHCR vía
`ImageUpdateAutomation`, siguiendo la convención de tag `build-${{ github.run_number }}` que
generan los 14 workflows de arriba; al detectar una imagen nueva, Flux actualiza el
`HelmRelease` correspondiente en el repo separado `InsightBloom-gitops`, que es la fuente de
verdad del manifiesto Helm desplegado (valores, secrets, RBAC, etc. — no duplicar esa
configuración aquí). El polling de Flux no es instantáneo: puede haber un delay entre el
push de una imagen nueva y el rollout real en el cluster.

Para cambiar imágenes, valores, secrets, HelmReleases o la política de reconciliación,
trabajar en `/Users/rafex/repository/github/rafex/InsightBloom-gitops`. Ese repositorio
es el lugar correcto para revisar el estado de Flux y solicitar el despliegue.

## Verificación manual del cluster

```bash
export KUBECONFIG=~/.kube/config_k3s_server1
kubectl get pods -n insightbloom \
  -o custom-columns=NAME:.metadata.name,IMAGE:.spec.containers[0].image
```

Este comando sólo verifica el estado real; no reemplaza la reconciliación de Flux ni
debe usarse para aplicar cambios manuales al cluster. Para comprobar una actualización,
compara el tag desplegado con el tag publicado en GHCR y revisa el `HelmRelease` desde
`InsightBloom-gitops`.
