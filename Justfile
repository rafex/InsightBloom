# InsightBloom - Task Runner
# Orquesta flujos de desarrollo, demos y operaciones.
# Requires: just (https://just.systems)

default:
    @just --list

# ── Desarrollo ────────────────────────────────────────────────────────────────

# Compila todo y levanta servicios + frontend
dev:
    make build
    @just dev-services &
    @just dev-web

# Levanta solo el frontend en modo dev (sin compilar)
dev-web:
    npm --prefix frontend/web run dev

# Levanta los servicios backend (sin compilar)
dev-services:
    ./scripts/run/run-services.sh

# Levanta un servicio individual: just service-run insightbloom-ingest
service-run SERVICE:
    ./scripts/run/run-service.sh {{SERVICE}}

# ── CI ────────────────────────────────────────────────────────────────────────

# Ejecuta el pipeline completo de integración continua
ci:
    make build
    make test
    make lint

# ── Docker / Compose ──────────────────────────────────────────────────────────

# Construye imágenes y levanta todos los contenedores (equivalente a `just dev` pero en Docker)
# Detiene cualquier instancia previa del stack antes de levantar
container-dev:
    docker compose -f container/compose.yml down --remove-orphans 2>/dev/null || true
    docker compose -f container/compose.yml up --build

# Construye imágenes y levanta en segundo plano
up:
    docker compose -f container/compose.yml up --build -d

# Para los contenedores (sin eliminar volúmenes)
down:
    docker compose -f container/compose.yml down

# Para y elimina volúmenes (reset completo)
down-clean:
    docker compose -f container/compose.yml down -v

# Muestra los logs de todos los servicios (o de uno: just logs insightbloom-users)
logs SERVICE="":
    docker compose -f container/compose.yml logs -f {{SERVICE}}

# ── Helm ──────────────────────────────────────────────────────────────────────

helm-lint:
    helm lint infra/helm/charts/*

# Despliega en k3s via Helm. Requiere kubeconfig configurado.
#   just deploy-k3s                          # deploy con tag 'latest'
#   just deploy-k3s v1.20260424              # deploy con tag específico
#   just deploy-k3s --dry-run                # solo validar, no aplicar
deploy-k3s TAG="latest" DRY="":
    helm upgrade --install insightbloom infra/helm/charts/insightbloom \
      --namespace mvps \
      --set image.tag={{TAG}} \
      --atomic \
      --wait --timeout 10m \
      {{ if DRY == "--dry-run" { "--dry-run" } else { "" } }}

# ── Admin CLI ─────────────────────────────────────────────────────────────────

# Crea o actualiza un usuario. Pasar argumentos después de --:
#   just create-user -- --username john --password s3cr3t --role ORGANIZER --db users.db
create-user *ARGS:
    ./scripts/run/create-user.sh {{ARGS}}

# Crea/actualiza usuario dentro de K3s (pod users). Pasar args después de --:
#   just k3s-create-user -- --namespace default --release insightbloom --username admin --password x --role ORGANIZER
k3s-create-user *ARGS:
    ./scripts/run/k3s-create-user.sh {{ARGS}}

# ── Chat (servicio Python) ────────────────────────────────────────────────────

# Crea el venv e instala dependencias Python del chat
chat-install:
    python3.13 -m venv chat/.venv
    chat/.venv/bin/pip install -r chat/requirements.txt

# Levanta el chat en local (requiere DEEPSEEK_API_KEY en el entorno)
# Apunta al ingest local por defecto; cámbialo con INGEST_URL=http://...
#   just chat-dev
#   INGEST_URL=http://localhost:8082 just chat-dev
chat-dev:
    DB_PATH=./chat/chat.db \
    INGEST_URL="${INGEST_URL:-http://localhost:8082}" \
    USERS_URL="${USERS_URL:-http://localhost:8081}" \
    chat/.venv/bin/uvicorn main:app --reload --port 8090 --app-dir chat

# ── Simulación / Demo ─────────────────────────────────────────────────────────

# Simula asistentes enviando palabras a una conferencia
#   just simulate                           # crea conferencia nueva con 40 mensajes
#   just simulate -- --count 80 --delay 0.2
#   just simulate -- --conference-id <uuid>
simulate *ARGS:
    ./scripts/sim/simulate-chat.sh {{ARGS}}

# Observa la nube de palabras en tiempo real (acepta UUID o friendly-id)
#   just watch-cloud demo-ai-2026
#   just watch-cloud 4dce90ae-2110-414d-9d23-b95e3b338a5a
watch-cloud CONF:
    ./scripts/sim/watch-cloud.sh --conference-id {{CONF}}

# Demo end-to-end: compila, crea conferencia, simula y muestra nube en vivo
demo *ARGS:
    make build
    ./scripts/sim/demo.sh {{ARGS}}
