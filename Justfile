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
container-dev:
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

# ── Admin CLI ─────────────────────────────────────────────────────────────────

# Crea o actualiza un usuario. Pasar argumentos después de --:
#   just create-user -- --username john --password s3cr3t --role ORGANIZER --db users.db
create-user *ARGS:
    java -jar backend/cli/insightbloom-cli/target/insightbloom-cli-0.1.0-SNAPSHOT.jar create-user {{ARGS}}

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
