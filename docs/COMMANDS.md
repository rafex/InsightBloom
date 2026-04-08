# COMMANDS.md

Lista de comandos operativos del proyecto.

## Objetivo

Reducir la ambiguedad de ejecucion para agentes y humanos.

## Convencion general

- `Makefile` es el builder: compila, testea, lintea y produce artefactos.
- `Justfile` es el task runner: orquesta flujos de desarrollo y operaciones.
  Cuando un flujo requiere compilacion, delega a `make`.
- `scripts/build/` concentra scripts de construccion e instalacion.
- `scripts/run/` concentra scripts de arranque local.
- `scripts/sim/` concentra scripts de simulacion y demo.

## Setup

```bash
make install
./scripts/build/install-web.sh
./scripts/build/install-services.sh
```

## Desarrollo

```bash
# Orquestar entorno completo (compila + arranca servicios + frontend)
just dev

# Solo frontend en modo dev (sin compilar)
just dev-web

# Solo servicios backend (sin compilar)
just dev-services

# Un servicio individual
just service-run insightbloom-users
./scripts/run/run-service.sh insightbloom-users
```

## Build

```bash
make build
make services-build
make web-build
make cli-build

./scripts/build/build-services.sh
./mvnw -f backend/services/pom.xml package
./mvnw -f backend/cli/insightbloom-cli/pom.xml clean package -DskipTests
npm --prefix frontend/web run build
```

## Test

```bash
make test
make services-test
make web-test

./mvnw -f backend/services/pom.xml test
npm --prefix frontend/web run test
```

## Lint y formato

```bash
make lint
make fmt
npm --prefix frontend/web run lint
```

## CI local

```bash
# Pipeline completo (build + test + lint)
just ci

# O paso a paso con make
make build
make test
make lint
```

## Compose local

```bash
just up
just down
docker compose -f infra/compose/local.yml up --build
docker compose -f infra/compose/local.yml down
```

## Helm

```bash
just helm-lint
helm lint infra/helm/charts/*
```

## Admin CLI

Compilar:

```bash
make cli-build
```

Crear o actualizar un usuario:

```bash
# Via Justfile (variadic args despues de --)
just create-user -- --username <u> --password <p> --role ORGANIZER
just create-user -- --username <u> --password <p> --role MODERATOR --db /data/users.db

# Directamente con java
java -jar backend/cli/insightbloom-cli/target/insightbloom-cli-0.1.0-SNAPSHOT.jar \
  create-user --username admin --password clave-segura --role ORGANIZER
```

Ver [`ROLES.md`](./ROLES.md) para la descripcion de roles y permisos.

## Simulacion y demo

```bash
# Simular asistentes enviando palabras a una conferencia nueva
just simulate

# Con parametros
just simulate -- --count 80 --delay 0.2
just simulate -- --conference-id <uuid>

# Observar la nube en tiempo real
just watch-cloud <friendly-id>
just watch-cloud <uuid>

# Demo end-to-end: compila + crea conferencia + simula + nube en vivo
just demo
```

## Nota

Estos comandos definen la interfaz operativa esperada del repositorio. Si
algun wrapper aun no existe, debe crearse siguiendo esta convencion en lugar
de inventar otro entrypoint.
