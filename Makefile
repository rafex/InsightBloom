.PHONY: install build test lint fmt up down ci helm-lint \
        services-build services-test web-build web-test dev dev-web dev-services \
        cli-build create-user

# ── Setup ─────────────────────────────────────────────────────────────────────
install:
	./scripts/build/install-web.sh
	./scripts/build/install-services.sh

# ── Build ─────────────────────────────────────────────────────────────────────
build: services-build web-build

services-build:
	./scripts/build/build-services.sh

web-build:
	npm --prefix frontend/web run build

# ── Tests ─────────────────────────────────────────────────────────────────────
test: services-test web-test

services-test:
	./mvnw -f backend/services/pom.xml test

web-test:
	npm --prefix frontend/web run test

# ── Lint ──────────────────────────────────────────────────────────────────────
lint:
	npm --prefix frontend/web run lint

fmt: lint

# ── Dev ───────────────────────────────────────────────────────────────────────
dev: dev-services dev-web

dev-web:
	npm --prefix frontend/web run dev

dev-services:
	./scripts/run/run-services.sh

# ── Compose ───────────────────────────────────────────────────────────────────
up:
	docker compose -f infra/compose/local.yml up --build

down:
	docker compose -f infra/compose/local.yml down

# ── CI ────────────────────────────────────────────────────────────────────────
ci: services-build services-test web-build web-test

# ── Helm ──────────────────────────────────────────────────────────────────────
helm-lint:
	helm lint infra/helm/charts/*

# ── Admin CLI ─────────────────────────────────────────────────────────────────
cli-build:
	./mvnw -f backend/cli/insightbloom-cli/pom.xml clean package -DskipTests

create-user:
	java -jar backend/cli/insightbloom-cli/target/insightbloom-cli-0.1.0-SNAPSHOT.jar create-user $(ARGS)
