# Diagnóstico del Proyecto

_Fecha: 2026-07-22 | Repositorio: InsightBloom_

---

## 1. Exploración

### Estructura general

Monorepo con 3 ecosistemas tecnológicos:

```
InsightBloom/
├── backend/
│   ├── common/              → código compartido (BaseResourceHandler, SQLite helpers)
│   ├── contracts/           → DTOs compartidos entre servicios
│   ├── services/            → 6 microservicios Java + 1 Node.js + 1 gateway
│   │   ├── insightbloom-users      (8081) — auth, conferencias, roles, boletos
│   │   ├── insightbloom-ingest     (8082) — recepción y normalización de mensajes
│   │   ├── insightbloom-query      (8083) — nubes D3.js y timelines
│   │   ├── insightbloom-moderation (8084) — censura manual/automática
│   │   ├── insightbloom-stats      (8085) — agregados y relevancia
│   │   ├── insightbloom-survey     (8086) — encuestas y certificados
│   │   ├── insightbloom-presentations — Node.js/Express (Marp/Slidev)
│   │   └── insightbloom-tools-gateway (8090) — proxy WebSocket a herramientas/IDE
│   └── cli/
│       └── insightbloom-cli        — CLI administrativo (crear usuarios sin endpoint admin)
├── frontend/
│   └── web/                 → SPA Vue 3 + Vite 6 + D3.js + Leaflet (servido por nginx)
├── chat/                    → Python 3.12 FastAPI + WebSocket (puerto 8090, bot IA "Roberto")
├── telegram/                → Python 3.12 FastAPI, integración Telegram
├── container/               → Docker Compose + Dockerfiles multi-stage
│   ├── compose.yml          — orquestación completa (9 servicios + healthchecks)
│   ├── backend/java/Dockerfile — multi-stage parametrizado por ARG SERVICE
│   └── frontend/Dockerfile  — Vite build + runtime nginx con nginx.conf
├── infra/                   → Helm charts para K3s (Chart.yaml, values.*.yaml)
├── scripts/                 → build/, run/, sim/ (bash)
├── spec-native/             → contexto SpecNative (PRODUCT, ARCHITECTURE, STACK, DECISIONS…)
├── agents/                  → SECURITY.md, DIAGNOSE.md
├── .github/workflows/       → 17 workflows (ci.yml, 14 publish-*.yml, integration-tests.yml, 2 shared)
├── pom.xml                  → Parent POM Maven (hereda ether-parent 9.5.5)
├── Makefile                 → Builder (build, test, lint, clean)
├── Justfile                 → Task runner (dev, CI, docker, k3s, demo, simulación)
└── README.md                → Navegación del repositorio
```

### Lenguajes y tecnologías

| Capa | Lenguaje | Framework / Runtime | Versión |
|------|----------|---------------------|---------|
| Backend | Java | Ether 9.5.5 (Jetty 12, Jackson, JWT, SQLite JDBC) | Java 25 |
| Backend Presentations | JavaScript | Node.js, Express 4.21, Marp CLI, Slidev CLI | Node 22 |
| Frontend | JavaScript/TypeScript | Vue 3.5, Vite 6, Vue Router 4.5, D3.js 7.9, d3-cloud 1.2, Leaflet 1.9, Axios 1.7 | Node 25 |
| Chat | Python | FastAPI, uvicorn, cryptography, openai (DeepSeek) | Python 3.12 |
| Telegram | Python | FastAPI | Python 3.12 |
| Infra | — | Docker (temurin:25-jdk-alpine, nginx:1.27-alpine), Helm, K3s, FluxCD | — |
| CI/CD | — | GitHub Actions (17 workflows), GHCR | — |

### Sistema de build / dependencias

- **Java — Maven multi-módulo**: `pom.xml` raíz declara 4 módulos (common, contracts, services, cli). Hereda de `dev.rafex.ether.parent:ether-parent:9.5.5`. Plugins versionados explícitamente en properties. Maven Wrapper (mvnw) incluido.
- **Frontend — npm**: `package.json` en `frontend/web/`. 19 dependencias + 15 devDependencies, todas con rango `^`. Vite 6, ESLint 9, Vitest 4, TypeScript 6 solo para typecheck (no runtime).
- **Chat/Telegram — pip**: `requirements.txt` con versiones fijadas (`==`). También `pyproject.toml`.

### Puntos de entrada

| Servicio | Entry point | Puerto |
|----------|-------------|--------|
| Users | `backend/services/insightbloom-users/.../bootstrap/UsersApplication.java` | 8081 |
| Ingest | `backend/services/insightbloom-ingest/.../bootstrap/IngestApplication.java` | 8082 |
| Query | `backend/services/insightbloom-query/.../bootstrap/QueryApplication.java` | 8083 |
| Moderation | `backend/services/insightbloom-moderation/.../bootstrap/ModerationApplication.java` | 8084 |
| Stats | `backend/services/insightbloom-stats/.../bootstrap/StatsApplication.java` | 8085 |
| Survey | `backend/services/insightbloom-survey/.../bootstrap/SurveyApplication.java` | 8086 |
| Tools Gateway | `backend/services/insightbloom-tools-gateway/.../bootstrap/GatewayApplication.java` | 8090 |
| Presentations | `backend/services/insightbloom-presentations/server.js` | 8091 |
| CLI | `backend/cli/insightbloom-cli/` | — |
| Frontend | `frontend/web/src/main.ts` → `index.html` | 80 (nginx) |
| Chat | `chat/main.py` | 8090 |
| Telegram | `telegram/main.py` | según entorno |

### Módulos y componentes clave

```
frontend (Vue 3 SPA)
  ├─► /api/users       → insightbloom-users (8081)     [auth, conferencias, boletos]
  ├─► /api/ingest      → insightbloom-ingest (8082)     [recepción mensajes]
  ├─► /api/query       → insightbloom-query (8083)      [nube, timeline]
  ├─► /api/moderation  → insightbloom-moderation (8084) [censura manual]
  ├─► /api/survey      → insightbloom-survey (8086)     [encuestas, certificados]
  ├─► /api/presentations → insightbloom-presentations (8091) [slides]
  └─► WebSocket        → insightbloom-tools-gateway (8090) [proxy a herramientas/IDE]

insightbloom-ingest
  ├─► users (validación token, resolución usuario/conferencia)
  ├─► moderation (censura automática)
  ├─► stats (recálculo agregados)
  └─► query (proyecciones de nube)

insightbloom-moderation
  └─► query (POST /internal/visibility — best-effort)

insightbloom-chat (Python)
  ├─► users (validación login)
  └─► ingest (envío mensajes vía webhook con HMAC-SHA256)
```

Arquitectura hexagonal en cada servicio Java:
```
domain/model → domain/ports → domain/services → application/usecases
                                                      ↓
adapters/inbound/http/handlers ←──────────────────────┘
adapters/outbound/sqlite, {servicio}client

Wiring manual en bootstrap/*Application.java:
DatabaseManager → Repositorios → Puertos → Servicios → Casos de uso → Handlers → HttpServer.start()
```

### Archivos de configuración relevantes

| Archivo | Propósito |
|---------|-----------|
| `pom.xml` | Parent POM Maven, gestión de dependencias Ether y plugins |
| `frontend/web/package.json` | Dependencias npm y scripts (dev, build, test, lint, typecheck) |
| `frontend/web/vite.config.js` | Proxy de desarrollo /api/* → backends |
| `frontend/web/tsconfig.json` | Configuración TypeScript (solo typecheck, no runtime) |
| `chat/requirements.txt` | Dependencias Python con versiones fijadas (==) |
| `chat/pyproject.toml` | Metadatos del proyecto Python |
| `container/compose.yml` | Orquestación Docker de 9 servicios con healthchecks, depends_on |
| `container/backend/java/Dockerfile` | Imagen multi-stage Java parametrizada (ARG SERVICE) |
| `container/frontend/Dockerfile` | Build Vite + runtime nginx con nginx.conf |
| `.github/workflows/ci.yml` | CI: build + test Maven, frontend y chat en paralelo |
| `.github/workflows/publish-*.yml` | Build y push de imágenes Docker a GHCR (14 workflows, path-filtered) |
| `.github/workflows/integration-tests.yml` | Tests de integración (workflow_dispatch, bajo demanda) |
| `.github/workflows/_build-java-service.yml` | Workflow reutilizable para build de servicios Java |
| `infra/Chart.yaml` | Helm chart metadata para K3s |
| `infra/values.dev.yaml`, `values.staging.yaml`, `values.prod.yaml` | Configuración de entorno Helm |
| `.gitignore` | 166 líneas — cubre Java, Node, Python, Docker, Helm, secretos, BD locales |
| `Makefile` | Builder: build, test, lint, clean |
| `Justfile` | Task runner: dev, CI, docker, k3s, demo, simulación |

### Estado del repositorio

- **Rama activa**: `main`
- **Último commit**: `4e7a689 cambios` (2026-07-22)
- **Ramas locales**: main, agent/event-canvas-access-modes, agent/event-notes-materials, agent/operational-tickets-counted, agent/ticket-access-expiration, feature/specnative-migration, refactor/tech-debt
- **Ramas remotas**: origin/main, origin/develop, origin/feature/deploy-k3s, origin/feature/specnative-migration, origin/refactor/tech-debt, + 20 branches de dependabot
- **Archivos modificados/sin trackear**: clean (nada pendiente)
- **CD**: repositorio externo `InsightBloom-gitops` reconciliado por FluxCD en K3s

---

## 2. Revisión de calidad

### Problemas estructurales o de diseño

- **[ALTA] Sobrediseño de microservicios**: 6+ servicios Java con comunicación HTTP síncrona para la escala actual. El riesgo está documentado en `ARCHITECTURE.md:549` — "la topología puede ser más compleja de operar que el valor que aporta". Merger candidates: stats + query (proyecciones de lectura), moderation como módulo de ingest.
- **[MEDIA] Wiring manual sin DI**: cada `*Application.java` instancia manualmente DatabaseManager → Repositorios → Puertos → Servicios → Casos de uso → Handlers. Es repetitivo y propenso a errores de orden de inicialización. No hay framework de inyección de dependencias externo.
- **[MEDIA] SQLite aislado sin migraciones versionadas compartidas**: cada servicio maneja su propio esquema. Cambios en cadena (censura → query) requieren coordinación manual de esquemas entre servicios.

### Deuda técnica identificada

- **[ALTA] DatabaseManager duplicado 6 veces** (`backend/services/*/adapters/outbound/sqlite/DatabaseManager.java`): implementaciones casi idénticas de gestión de conexiones SQLite y migraciones. El módulo `backend/common` existe con dependencias de JDBC/SQLite pero no centraliza esta lógica.
- **[MEDIA] Patrones de handlers inconsistentes**: validación de tokens, parseo de body JSON y manejo de errores HTTP replicados en cada handler. `BaseResourceHandler` existe en `backend/common` pero su uso no es uniforme en todos los servicios.
- **[MEDIA] Módulo common subutilizado**: `backend/common/pom.xml` declara dependencias de ether-http-jetty12, ether-json, ether-jdbc, sqlite-jdbc, insightbloom-contracts. Tiene potencial para centralizar mucho más de lo que actualmente contiene.

### Prácticas del lenguaje no seguidas

- **Java**: arquitectura hexagonal consistente pero el wiring manual contradice prácticas estándar para microservicios Java. El framework Ether 9.5.5 no impone un estilo particular de DI, pero la comunidad Java espera algún contenedor (Spring, Micronaut, Guice) para esta escala.
- **Frontend JavaScript/Vue**: sin TypeScript en código de runtime (decisión explícita documentada en STACK.md). Sin embargo, el tooling TS está presente (vue-tsc, typescript, @types/*) para typecheck estático.
- **Python chat**: `main.py` (91 líneas) concentra creación de app, CORS, static files, lifespan, variables globales (`db`, `roberto`, `manager`), routers, NATS connection y health/version endpoints. Una estructura más modular con factories mejoraría testabilidad.

### Riesgos de seguridad

- **[MEDIA] Dependencias npm con rangos flotantes (`^`)**: 34 dependencias en `package.json` usan `^`, permitiendo actualizaciones automáticas que podrían introducir vulnerabilidades. Python (`requirements.txt`) usa `==` correctamente.
- **[MEDIA] Dependencias transitivas con vulnerabilidades sin fix compatible** (documentado en `agents/SECURITY.md`):
  - SEC-DEP-001: Slidev → @hono/node-server (path traversal, CVSS 5.9)
  - SEC-DEP-002: Excalidraw → lodash-es (code injection, CVSS 8.1)
  - SEC-DEP-003: Excalidraw → nanoid (IDs predecibles, CVSS 4.3)
- **[BAJA] `.secret_key` local presente**: `chat/.secret_key` existe en el filesystem pero NO está trackeado en git (cubierto por `.gitignore`).
- **Hallazgos de seguridad previos CERRADOS**: 3 auditorías documentadas en `agents/SECURITY.md` con 15 hallazgos. Todos cerrados (5 críticos, 6 altos, 3 medios, 1 bajo). Estado actual: ningún hallazgo de código abierto.

### Cobertura de tests y documentación

- **Tests Java**: 41 archivos de test. Distribución desigual — users concentra ~32 tests; ingest (2), moderation (1), query (1), stats (1), survey (1), tools-gateway (2). Servicios críticos como ingest y moderation tienen cobertura mínima.
- **Tests frontend**: 14 archivos de test cubriendo APIs (9), stores (2), composables (1), utils (1). Distribución aceptable. Vitest configurado.
- **Tests chat**: 36 tests en 8 archivos (commands, websocket, auth, security). Cobertura mejorando.
- **Tests Telegram**: 4 archivos de test (internal notify, db, command parser, webhook).
- **Sin tests de integración entre servicios**: perfil Maven `-Pintegration` existe en `pom.xml:330` pero se ejecuta solo bajo demanda (`workflow_dispatch`). Sin tests automatizados de flujos cross-service.
- **Sin OpenAPI/Swagger specs generadas**: existe dependencia `ether-http-openapi` en `pom.xml:139` pero no se usa para generar documentación de API.
- **Documentación excelente**: `spec-native/` contiene PRODUCT.md (105 líneas), ARCHITECTURE.md (559 líneas), STACK.md (99 líneas), CONVENTIONS.md (66 líneas), DECISIONS.md, ROADMAP.md, ROLES.md, TRACEABILITY.md, + specs, tasks, workflows y pipelines.

---

## 3. Síntesis ejecutiva

### Resumen del proyecto

InsightBloom es una plataforma que transforma mensajes de chat en nubes de palabras interactivas para conferencias en vivo. Los participantes envían comandos como `/duda` y `/tema`, y la plataforma agrega y visualiza en tiempo real los conceptos que concentran más interés. Incluye además sistema de boletos con QR, encuestas con certificados, presentaciones (Marp/Slidev), herramientas colaborativas (Drawio, Excalidraw, Etherpad), IDE aislado por participante (code-server, Neovim), videollamada Jitsi y un bot de chat con IA.

Organizado como monorepo con backend Java 25 (6 microservicios sobre Ether 9.5.5, Jetty 12, arquitectura hexagonal, SQLite WAL aislado), frontend Vue 3 + Vite 6 + D3.js + Leaflet (SPA con PWA y modo offline), servicios Python 3.12 para chat (FastAPI + WebSocket) y Telegram, y servicio Node.js para presentaciones. Infraestructura completamente contenerizada (Docker Compose para desarrollo, Helm charts para K3s en producción). CI/CD con 17 workflows de GitHub Actions, GHCR y GitOps vía FluxCD.

### Estado de salud

**🟡 Amarillo** — El proyecto tiene una base sólida: arquitectura limpia y bien documentada, infraestructura reproducible con un solo comando, seguridad operativa auditada y corregida (15 hallazgos cerrados), y una suite de tests en crecimiento. Sin embargo, sufre de sobrediseño de microservicios para la etapa actual (6 servicios Java cuando 3-4 bastarían), deuda técnica por duplicación de código de infraestructura (DatabaseManager ×6, wiring manual repetitivo), y cobertura de tests desbalanceada (users concentra el 78% de los tests Java mientras ingest y moderation tienen solo 1-2 tests). Las dependencias npm con rangos flotantes (`^`) y 3 vulnerabilidades transitivas sin fix compatible representan un riesgo latente.

### Top 3 fortalezas

1. **Infraestructura reproducible y automatizada**: Docker Compose con 9 servicios, healthchecks, depends_on y volúmenes nombrados. Helm charts con NetworkPolicy, CronJob de backup SQLite, y ServiceAccount dedicado para sandbox. 17 workflows de GitHub Actions con publish path-filtered. El proyecto se levanta completo con `just container-dev`.

2. **Documentación SpecNative de alta calidad**: PRODUCT.md, ARCHITECTURE.md (559 líneas), STACK.md, CONVENTIONS.md, DECISIONS.md, ROADMAP.md, ROLES.md, TRACEABILITY.md, specs por iniciativa, workflows operativos. Contexto completo para cualquier desarrollador o agente.

3. **Seguridad operativa madura**: 3 auditorías documentadas en SECURITY.md (333 líneas) con 15 hallazgos — todos cerrados. PBKDF2 para passwords, SHA-256 para tokens at rest, rate limiting en auth, NetworkPolicy deny-by-default, validación HMAC-SHA256 en webhooks, backup automatizado de SQLite. `.gitignore` de 166 líneas cubriendo todos los artefactos sensibles.

### Top 3 riesgos o deudas

1. **[ALTA] Sobrediseño de microservicios**: 6 servicios Java con HTTP síncrono y SQLite aislado para una etapa que no requiere esa granularidad. La complejidad operativa supera el beneficio. Consolidar stats+query y evaluar moderation como módulo de ingest reduciría puntos de fallo sin perder capacidades.

2. **[ALTA] Cobertura de tests desbalanceada**: users concentra el 78% de los tests Java; ingest, moderation, query, stats y survey tienen solo 1-2 tests cada uno. Sin tests de integración cross-service ejecutándose en CI, cualquier cambio en la cadena ingest→moderation→query requiere despliegue manual para validación.

3. **[MEDIA] Duplicación de infraestructura y wiring manual**: DatabaseManager ×6, patrones de handlers inconsistentes, módulo `backend/common` subutilizado. El wiring manual sin DI en cada `*Application.java` es repetitivo y propenso a errores. Un fix en DatabaseManager requiere replicación en 6 servicios.

### Próximos pasos recomendados

1. **Centralizar DatabaseManager y helpers HTTP en backend/common** — extraer la lógica duplicada de gestión SQLite, migraciones y validación de tokens al módulo compartido existente. Impacto alto, esfuerzo medio.

2. **Añadir tests a servicios con baja cobertura** — priorizar ingest (2 tests → 10+), moderation (1 test → 8+), query (1 test → 5+). Usar el patrón de tests existente en users como referencia. Impacto alto, esfuerzo medio.

3. **Habilitar tests de integración en CI** — activar perfil Maven `-Pintegration` en CI para flujos críticos (ingest→moderation→query, users→auth→token validation). Impacto alto, esfuerzo bajo (la infraestructura ya existe en `integration-tests.yml`).

4. **Fijar versiones npm** — reemplazar rangos `^` por versiones exactas (`==`) en `package.json`. Evaluar overrides para las 3 vulnerabilidades transitivas documentadas. Impacto medio, esfuerzo bajo.

5. **Evaluar consolidación de microservicios** — planificar merge de stats + query (ambos son proyecciones de lectura) y evaluar si moderation puede ser un módulo interno de ingest. Pasar de 6 a 3-4 servicios. Impacto medio, esfuerzo alto.

---

## 4. Archivos relevantes

| Archivo | Tipo | Relevancia |
|---------|------|------------|
| `pom.xml` | config | Parent POM raíz — define 4 módulos, dependencias Ether 9.5.5, plugins y perfil de integración |
| `spec-native/ARCHITECTURE.md` | doc | Fuente de verdad de la arquitectura — boundaries, flujo de datos, contratos (559 líneas) |
| `spec-native/PRODUCT.md` | doc | Define problema, usuarios, objetivos, capacidades y alcance del producto |
| `spec-native/STACK.md` | doc | Stack tecnológico completo con versiones y restricciones (99 líneas) |
| `spec-native/CONVENTIONS.md` | doc | Reglas de código, naming, testing, estructura de carpetas |
| `spec-native/SESSION.md` | doc | Estado activo de trabajo — iniciativa certificate-editor en progreso |
| `agents/SECURITY.md` | doc | 3 auditorías de seguridad — 15 hallazgos documentados, todos cerrados (333 líneas) |
| `container/compose.yml` | config | Orquestación Docker de 9 servicios — entry point para desarrollo local |
| `container/backend/java/Dockerfile` | config | Imagen multi-stage Java parametrizada por ARG SERVICE |
| `container/frontend/Dockerfile` | config | Build Vite + runtime nginx con reverse proxy a backends |
| `Makefile` | config | Builder principal — targets: build, test, lint, clean |
| `Justfile` | config | Task runner — orquesta dev, CI, docker, k3s, demo, simulación |
| `.github/workflows/ci.yml` | config | CI pipeline — build + test Maven, frontend y chat en paralelo |
| `.github/workflows/publish-*.yml` | config | 14 workflows de publicación de imágenes Docker a GHCR (path-filtered) |
| `.gitignore` | config | 166 líneas cubriendo Java, Node, Python, Docker, Helm, secretos, BD locales |
| `frontend/web/package.json` | config | 34 dependencias del frontend (Vue 3, D3.js, Leaflet, Excalidraw, SurveyJS) |
| `frontend/web/vite.config.js` | config | Proxy de desarrollo /api/* → backends |
| `chat/main.py` | entry | Entry point del chat — FastAPI + WebSocket + bot IA Roberto |
| `chat/requirements.txt` | config | Dependencias Python con versiones fijadas |
| `backend/services/insightbloom-users/.../bootstrap/UsersApplication.java` | entry | Entry point del servicio más complejo — wiring canónico de la arquitectura hexagonal |
| `backend/services/insightbloom-ingest/.../bootstrap/IngestApplication.java` | entry | Entry point del servicio más interconectado — 4 puertos externos |
| `backend/common/pom.xml` | config | Módulo compartido subutilizado — debe centralizar DatabaseManager y helpers |
| `backend/services/insightbloom-stats/src/main/java/.../adapters/outbound/sqlite/DatabaseManager.java` | module | Ejemplo representativo de código duplicado 6 veces |
| `spec-native/DECISIONS.md` | doc | Registro de decisiones de diseño con trade-offs |
| `spec-native/pipelines/CI.md` | doc | Gates de integración continua |
| `spec-native/pipelines/CD.md` | doc | Proceso de entrega vía FluxCD y GitOps |
| `infra/Chart.yaml` | config | Helm chart para despliegue en K3s |
| `infra/values.prod.yaml` | config | Valores de producción para Helm |
