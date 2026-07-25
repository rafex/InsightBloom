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
ADMIN_PASS=<password-admin> just simulate

# Con parametros
ADMIN_PASS=<password-admin> just simulate -- --count 80 --delay 0.2
ADMIN_PASS=<password-admin> just simulate -- --conference-id <uuid>

# Observar la nube en tiempo real
just watch-cloud <friendly-id>
just watch-cloud <uuid>

# Demo end-to-end: compila + crea conferencia + simula + nube en vivo
ADMIN_PASS=<password-admin> just demo
```

## K3s / Kubernetes: crear usuarios por CLI

Compilar CLI:

```bash
make cli-build
```

Crear o actualizar usuario dentro del pod `users` (sin endpoint admin):

```bash
./scripts/run/k3s-create-user.sh \
  --namespace <ns> \
  --release <helm-release> \
  --username <u> \
  --password <p> \
  --role ORGANIZER,ADMIN

# ejemplo rápido (defaults: ns=default, release=insightbloom)
./scripts/run/k3s-create-user.sh \
  --username admin \
  --password "cambia-esta-clave" \
  --role ORGANIZER
```

## Chat (Python + FastAPI)

```bash
# Instalar dependencias y crear venv
just chat-install

# Desarrollo local (requiere DEEPSEEK_API_KEY)
just chat-dev

# Personalizar URL de ingest
INGEST_URL=http://localhost:8082 just chat-dev

# Ejecutar tests
cd chat && python -m pytest -v
```

## Survey

```bash
# El servicio survey se levanta con el resto de servicios Java
just dev-services

# La configuración de IA se administra en Dashboard → IA, por capacidad:
# Chat, Tutor IA, Encuestas y Mapas de asientos.
# Cada pestaña tiene URL, modelo, prompt, temperatura, kill switch y API key.
# No se pasan URL, modelo, prompt ni API key como variables de despliegue.
```

## Presentations

```bash
# Instalar dependencias
npm --prefix backend/services/insightbloom-presentations install

# Levantar standalone
PORT=8091 DATA_DIR=./data node backend/services/insightbloom-presentations/server.js
```

## Perfil de usuario

```bash
# Ver/editar perfil desde el frontend
# Endpoints: GET /profile, PATCH /profile
# Requiere token JWT valido (ORGANIZER, MODERATOR o ADMIN)
```

## Admin de usuarios (rol ADMIN)

```bash
# Endpoints disponibles en el frontend:
# GET    /admin/users              — listar usuarios
# PATCH  /admin/users/{id}         — editar usuario
# POST   /admin/users/{id}/ban     — banear (soft-delete)
# POST   /admin/users/{id}/restore — restaurar
```

## CI/CD

```bash
# Pipeline completo local
just ci

# Build y publicacion de contenedores
#   push a main → tag latest en GHCR
#   push de tag vN.YYYYmmDD → tag versionado en GHCR
#   workflow_dispatch manual → tag especifico

# El deploy no se ejecuta desde este repositorio.
# Las imágenes publicadas en GHCR son reconciliadas por FluxCD desde:
# /Users/rafex/repository/github/rafex/InsightBloom-gitops

# Verificación manual del rollout (solo lectura):
export KUBECONFIG=~/.kube/config_k3s_server1
kubectl get pods -n insightbloom \
  -o custom-columns=NAME:.metadata.name,IMAGE:.spec.containers[0].image
```

## Nota

Estos comandos definen la interfaz operativa esperada del repositorio. Si
algun wrapper aun no existe, debe crearse siguiendo esta convencion en lugar
de inventar otro entrypoint.
