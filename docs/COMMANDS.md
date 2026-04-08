# COMMANDS.md

Lista de comandos operativos del proyecto.

## Objetivo

Reducir la ambiguedad de ejecucion para agentes y humanos.

## Convencion general

- `Justfile` expone los comandos de uso diario.
- `Makefile` apoya build y tareas repetibles.
- `helpers-build/` concentra scripts de build.
- `helpers-run/` concentra scripts de ejecucion local.

## Setup

```bash
just install
make install
./helpers-build/install-web.sh
./helpers-build/install-services.sh
```

## Desarrollo

```bash
just dev
just dev-web
just dev-services
make dev
./helpers-run/run-web.sh
./helpers-run/run-services.sh
```

## Frontend

```bash
just web-dev
just web-build
just web-test
just web-lint
npm --prefix apps/insightbloom-web install
npm --prefix apps/insightbloom-web run dev
npm --prefix apps/insightbloom-web run build
npm --prefix apps/insightbloom-web run test
npm --prefix apps/insightbloom-web run lint
```

## Backend

```bash
just services-test
just services-build
just service-run SERVICE=insightbloom-users
make services-test
make services-build
./helpers-build/build-service.sh insightbloom-users
./helpers-run/run-service.sh insightbloom-users
./mvnw -f services/insightbloom-users/pom.xml test
./mvnw -f services/insightbloom-users/pom.xml package
```

## Tests

```bash
just test
just test-web
just test-services
make test
```

## Lint y formato

```bash
just lint
just fmt
make lint
make fmt
```

## Compose local

```bash
just up
just down
make up
make down
docker compose -f infra/compose/local.yml up --build
docker compose -f infra/compose/local.yml down
```

## CI local

```bash
just ci
make ci
```

## Helm

```bash
just helm-lint
make helm-lint
helm lint infra/helm/charts/*
```

## Admin CLI

Compilar el CLI de administración:

```bash
just cli-build
make cli-build
./mvnw -f tools/insightbloom-cli/pom.xml clean package -DskipTests
```

Crear o actualizar un usuario (requiere haber compilado antes):

```bash
# Vía Justfile (variadic args después de --)
just create-user -- --username <u> --password <p> --role ORGANIZER
just create-user -- --username <u> --password <p> --role MODERATOR --db /data/users.db

# Vía Makefile
make create-user ARGS="--username <u> --password <p> --role ORGANIZER"

# Directamente con java
java -jar tools/insightbloom-cli/target/insightbloom-cli-0.1.0-SNAPSHOT.jar create-user \
  --username admin --password clave-segura --role ORGANIZER
```

Ver [`tools/insightbloom-cli/README.md`](../tools/insightbloom-cli/README.md) para la referencia completa de opciones y ejemplos.
Ver [`ROLES.md`](./ROLES.md) para la descripción de roles y permisos.

## Nota

Estos comandos definen la interfaz operativa esperada del repositorio. Si
algun wrapper aun no existe, debe crearse siguiendo esta convencion en lugar
de inventar otro entrypoint.
