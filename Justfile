# InsightBloom - Task Runner
# Requires: just (https://just.systems)

default:
    @just --list

# ── Setup ─────────────────────────────────────────────────────────────────────

install:
    ./scripts/build/install-web.sh
    ./scripts/build/install-services.sh

# ── Development ───────────────────────────────────────────────────────────────

dev:
    @echo "Starting all services and frontend..."
    @just dev-services &
    @just dev-web

dev-web:
    npm --prefix frontend/web run dev

dev-services:
    ./scripts/run/run-services.sh

service-run SERVICE:
    ./scripts/run/run-service.sh {{SERVICE}}

# ── Build ─────────────────────────────────────────────────────────────────────

web-build:
    npm --prefix frontend/web run build

services-build:
    ./scripts/build/build-services.sh

build:
    @just services-build
    @just web-build

# ── Tests ─────────────────────────────────────────────────────────────────────

test:
    @just test-services
    @just test-web

test-web:
    npm --prefix frontend/web run test

test-services:
    ./mvnw -f backend/services/pom.xml test

web-test:
    npm --prefix frontend/web run test

services-test:
    ./mvnw -f backend/services/pom.xml test

# ── Lint & Format ─────────────────────────────────────────────────────────────

lint:
    @just lint-web

lint-web:
    npm --prefix frontend/web run lint

fmt:
    @just lint-web

web-lint:
    npm --prefix frontend/web run lint

# ── Compose ───────────────────────────────────────────────────────────────────

up:
    docker compose -f infra/compose/local.yml up --build

down:
    docker compose -f infra/compose/local.yml down

# ── CI ────────────────────────────────────────────────────────────────────────

ci:
    @just services-build
    @just test-services
    @just web-build
    @just test-web

# ── Helm ──────────────────────────────────────────────────────────────────────

helm-lint:
    helm lint infra/helm/charts/*

# ── Admin CLI ─────────────────────────────────────────────────────────────────

cli-build:
    ./mvnw -f backend/cli/insightbloom-cli/pom.xml clean package -DskipTests

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

# Demo end-to-end: crea conferencia + simula + muestra nube en vivo
demo *ARGS:
    ./scripts/sim/demo.sh {{ARGS}}
