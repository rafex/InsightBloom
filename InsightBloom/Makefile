.PHONY: install build test lint fmt up down ci helm-lint \
        services-build services-test web-build web-test dev dev-web dev-services

# ── Setup ─────────────────────────────────────────────────────────────────────
install:
	./helpers-build/install-web.sh
	./helpers-build/install-services.sh

# ── Build ─────────────────────────────────────────────────────────────────────
build: services-build web-build

services-build:
	./helpers-build/build-services.sh

web-build:
	npm --prefix apps/insightbloom-web run build

# ── Tests ─────────────────────────────────────────────────────────────────────
test: services-test web-test

services-test:
	./mvnw -f services/pom.xml test

web-test:
	npm --prefix apps/insightbloom-web run test

# ── Lint ──────────────────────────────────────────────────────────────────────
lint:
	npm --prefix apps/insightbloom-web run lint

fmt: lint

# ── Dev ───────────────────────────────────────────────────────────────────────
dev: dev-services dev-web

dev-web:
	npm --prefix apps/insightbloom-web run dev

dev-services:
	./helpers-run/run-services.sh

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
