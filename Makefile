# InsightBloom - Builder
# Produces artefacts, verifica código, ejecuta pruebas.
# Uso: make <target>

.PHONY: all install build services-build web-build cli-build \
        test services-test web-test \
        lint fmt clean

# ── Default ───────────────────────────────────────────────────────────────────
all: build

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

cli-build:
	./mvnw -f backend/cli/insightbloom-cli/pom.xml clean package -DskipTests

# ── Test ──────────────────────────────────────────────────────────────────────
test: services-test web-test

services-test:
	./mvnw -f backend/services/pom.xml test

web-test:
	npm --prefix frontend/web run test

# ── Lint & Format ─────────────────────────────────────────────────────────────
lint:
	npm --prefix frontend/web run lint

fmt: lint

# ── Clean ─────────────────────────────────────────────────────────────────────
clean:
	./mvnw -f backend/services/pom.xml clean
	./mvnw -f backend/cli/insightbloom-cli/pom.xml clean
	rm -rf frontend/web/dist
