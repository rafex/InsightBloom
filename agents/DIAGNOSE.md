# Diagnóstico del Proyecto

_Fecha: 2026-06-26 | Repositorio: InsightBloom_

---

## 1. Exploración

### Estructura general

Monorepo con 3 ecosistemas tecnológicos:

```
InsightBloom/
├── backend/
│   ├── common/              → código compartido (utils, DTOs base)
│   ├── contracts/           → insightbloom-contracts (DTOs compartidos entre servicios)
│   ├── services/            → 6 microservicios Java
│   │   ├── insightbloom-users      (8081) — auth, conferencias
│   │   ├── insightbloom-ingest     (8082) — recepción de eventos
│   │   ├── insightbloom-query      (8083) — nubes y timelines
│   │   ├── insightbloom-moderation (8084) — censura manual/auto
│   │   ├── insightbloom-stats      (8085) — agregados y relevancia
│   │   └── insightbloom-survey     — encuestas
│   └── cli/
│       └── insightbloom-cli        — CLI administrativo
├── frontend/
│   └── web/                 → SPA Vue 3 + Vite 6 (servido por nginx)
├── chat/                    → Python FastAPI + WebSocket (puerto 8090, bot IA «Roberto»)
├── container/               → Docker Compose + Dockerfiles multi-stage
├── infra/                   → Helm charts (K3s) + scripts de despliegue
├── scripts/                 → build/, run/, sim/ (bash)
├── docs/                    → PRODUCT, ARCHITECTURE, STACK, SPEC, DECISIONS, etc.
├── agents/                  → SECURITY.md, DIAGNOSE.md (contexto operativo para IAs)
├── test/                    → tests e2e (Python + requests)
├── pom.xml                  → Parent POM Maven (hereda ether-parent 9.5.5)
├── Makefile                 → Builder (compilación, test, lint)
├── Justfile                 → Task runner (dev, CI, docker, k3s, demo, simulación)
└── README.md                → Documento de navegación del repo
```

### Lenguajes y tecnologías

| Capa | Lenguaje | Framework / Runtime | Versión |
|------|----------|---------------------|---------|
| Backend | Java | Ether 9.5.5 (Jetty 12, Jackson, JWT, SQLite via JDBC) | Java 25 |
| Frontend | JavaScript | Vue 3.5, Vite 6, Vue Router 4.5, D3.js 7.9, d3-cloud 1.2, Leaflet 1.9 | Node 25 |
| Chat | Python | FastAPI, uvicorn, cryptography, openai (DeepSeek) | Python 3.13 |
| Build | — | Maven Wrapper (mvnw), npm, pip+venv | — |
| Infra | — | Docker (temurin:25-jdk-alpine, nginx:1.27-alpine), Docker Compose, Helm, K3s | — |
| CI/CD | — | GitHub Actions (3 workflows) | — |

### Sistema de build / dependencias

- **Java — Maven multi-módulo**: `pom.xml` raíz declara 4 módulos (common, contracts, services, cli). Hereda de `dev.rafex.ether.parent:ether-parent:9.5.5` que gestiona versiones de Jetty 12, Jackson, y módulos Ether de forma centralizada. Plugins versionados explícitamente en `properties`.
- **Frontend — npm**: `package.json` en `frontend/web/`. Dependencias con rango `^` (Vue 3.5.13, D3 7.9, Leaflet 1.9, Axios 1.7). Dev: Vite 6, ESLint 9, Vitest 2.1.
- **Chat — pip**: `chat/requirements.txt` con versiones fijadas (`==`). También `pyproject.toml`. Dependencias: fastapi, uvicorn, httpx, cryptography, openai.

### Puntos de entrada

| Entry point | Archivo | Descripción |
|-------------|---------|-------------|
| Users (8081) | `backend/services/insightbloom-users/.../bootstrap/UsersApplication.java` | Auth JWT, conferencias, perfiles |
| Ingest (8082) | `backend/services/insightbloom-ingest/.../bootstrap/IngestApplication.java` | Recepción de mensajes, validación, persistencia |
| Query (8083) | `backend/services/insightbloom-query/.../bootstrap/QueryApplication.java` | Nubes de palabras, timelines |
| Moderation (8084) | `backend/services/insightbloom-moderation/.../bootstrap/ModerationApplication.java` | Censura manual, revisión de mensajes |
| Stats (8085) | `backend/services/insightbloom-stats/.../bootstrap/StatsApplication.java` | Agregados, relevancia |
| Survey (8086) | `backend/services/insightbloom-survey/.../bootstrap/SurveyApplication.java` | Encuestas |
| CLI | `backend/cli/insightbloom-cli/` | Herramienta administrativa |
| Frontend | `frontend/web/src/main.js` → `index.html` | SPA Vue con router |
| Chat | `chat/main.py` | FastAPI app (puerto 8090) |

### Módulos y componentes clave

```
frontend (Vue 3 SPA)
  ├─► /api/users       → insightbloom-users (8081)     [auth, conferencias]
  ├─► /api/ingest      → insightbloom-ingest (8082)     [recepción mensajes]
  ├─► /api/query       → insightbloom-query (8083)      [nube, timeline]
  ├─► /api/moderation  → insightbloom-moderation (8084) [censura manual]
  └─► /api/survey      → insightbloom-survey (8086)     [encuestas]

insightbloom-ingest
  ├─► users (validación token)
  ├─► moderation (censura automática)
  ├─► stats (recálculo agregados)
  └─► query (proyecciones de nube)

insightbloom-moderation
  └─► query (POST /internal/visibility)

chat (Python FastAPI)
  ├─► users (validación login)
  └─► ingest (envío de mensajes vía webhook)
```

### Archivos de configuración relevantes

| Archivo | Propósito |
|---------|-----------|
| `pom.xml` | Parent POM Maven, gestión de dependencias y plugins |
| `frontend/web/package.json` | Dependencias npm y scripts |
| `frontend/web/vite.config.js` | Proxy de desarrollo /api/* → backends |
| `chat/requirements.txt` | Dependencias Python fijadas |
| `chat/pyproject.toml` | Metadatos del proyecto Python |
| `container/compose.yml` | Orquestación Docker de 7 servicios con healthchecks, depends_on |
| `container/backend/java/Dockerfile` | Imagen multi-stage Java parametrizada |
| `container/frontend/Dockerfile` | Build Vue + runtime nginx |
| `infra/helm/charts/` | Charts Helm para K3s |
| `.github/workflows/ci.yml` | CI: build + test Java, frontend y chat |
| `.github/workflows/deploy.yml` | Deploy a K3s vía Helm |
| `.github/workflows/publish_container.yml` | Build y push de imágenes Docker |
| `.gitignore` | 162 líneas — cubre Java, Node, Python, Docker, Helm, secretos |
| `Makefile` | Builder: build, test, lint, clean |
| `Justfile` | Task runner: dev, deploy, demo, simulación |

### Estado del repositorio

- **Branch**: `main` (synced con `origin/main`)
- **Último commit**: `cf80f63 fix: require organizer auth on stats overview/relevance endpoints (SECURITY.md #6)`
- **Commits recientes**: fixes de seguridad (SECURITY.md #1-#6), refactor de migración a backend/common, tests de chat, fixes de autoría en dudas
- **Ramas remotas**: `origin/main`, `origin/develop`, `origin/feature/deploy-k3s`
- **Archivos sin trackear**: `agents/DIAGNOSE.md`

---

## 2. Revisión de calidad

### Problemas estructurales o de diseño

- **[ALTA] Sobrediseño de microservicios para PoC**: 6 microservicios Java con comunicación HTTP síncrona entre ellos. Para la etapa actual del proyecto, 2-3 servicios consolidados reducirían complejidad operativa y puntos de fallo.
- **[MEDIA] SQLite aislado por servicio**: cada microservicio tiene su propia instancia SQLite. Sin migraciones versionadas compartidas, cualquier cambio de esquema en cadena (ej: censura → query) es frágil.
- **[MEDIA] Wiring manual sin DI**: cada `*Application.java` instancia manualmente DatabaseManager → Repositorios → Puertos → Casos de uso → Handlers. Es repetitivo y propenso a errores de orden de inicialización.

### Deuda técnica identificada

- **[ALTA] DatabaseManager duplicado**: cada microservicio tiene su propia implementación casi idéntica de gestión de conexiones SQLite y migraciones de esquema.
- **[MEDIA] Patrones repetidos en handlers**: validación de tokens, parseo de body JSON, manejo de errores HTTP replicados en cada handler. Existe `BaseHandler` pero su uso es inconsistente.
- **[MEDIA] chat/main.py monolítico**: 72 líneas pero mezcla creación de app, routers, middlewares, ciclo de vida y variables globales en un solo archivo.
- **[BAJA] Nombres genéricos**: `db.py` en chat, variables como `roberto` para el bot IA — falta claridad semántica.

### Prácticas del lenguaje no seguidas

- **Java**: arquitectura hexagonal sin inyección de dependencias. El wiring manual contradice la práctica estándar para microservicios Java, aunque el framework Ether no impone un estilo particular.
- **JavaScript/Vue**: sin TypeScript (por decisión explícita documentada en STACK.md). Vitest configurado pero casi sin uso — solo 1 test en frontend.
- **Python**: `main.py` concentra creación de app, CORS, static files, ciclo de vida y variables globales. Sin validación de schemas con Pydantic models explícitos en algunas rutas.

### Riesgos de seguridad

- **[MEDIA] Rangos de versión `^` en npm**: las dependencias del frontend usan `^` (ej: `"vue": "^3.5.13"`), permitiendo actualizaciones automáticas que podrían introducir vulnerabilidades. Las dependencias Python están correctamente fijadas con `==`.
- **[MEDIA] 7 hallazgos documentados en agents/SECURITY.md**: 1 crítico (moderación sin auth), 3 altos, 2 medios, 1 bajo. Los commits recientes muestran que se están resolviendo (#1-#6 ya corregidos).
- **[BAJA] Endpoints internos sin protección adicional**: `/internal/*` en query depende del reverse proxy nginx para no exponerse. Si se omite o configura mal, quedan expuestos.
- **Sin secretos expuestos**: `.gitignore` bien configurado (162 líneas cubriendo `.env`, `secrets/`, `.pem`, `.key`, certificados, kubeconfig).

### Cobertura de tests y documentación

- **[ALTA] Cobertura de tests baja en backend Java**: tests unitarios presentes pero insuficientes para 6 microservicios. Sin tests de integración entre servicios.
- **[ALTA] Chat Python con tests mínimos**: se agregaron tests unitarios recientemente (`c9c2023`), pero la cobertura sigue siendo baja para módulos críticos como `bot.py` y `crypto.py`.
- **[MEDIA] Frontend con 1 test**: solo auth store tiene test. Sin tests de componentes Vue ni tests E2E.
- **[MEDIA] Documentación de arquitectura excelente, pero sin OpenAPI/Swagger**: aunque el stack incluye `ether-http-openapi`, no hay specs de API generadas ni mantenidas.

---

## 3. Síntesis ejecutiva

### Resumen del proyecto

InsightBloom convierte mensajes de chat en nubes de palabras interactivas para conferencias en vivo. Los participantes envían comandos como `/duda` y `/tema`, y la plataforma agrega y visualiza en tiempo real los conceptos que concentran más interés. Organizado como monorepo con backend Java 25 (6 microservicios sobre Ether 9.5.5), frontend Vue 3 + D3.js + Leaflet, y servicio de chat Python/FastAPI con bot IA. Usa Maven + npm + pip, contenedores Docker orquestados con Compose, y Helm para despliegue en K3s. CI/CD con GitHub Actions.

### Estado de salud

**🟡 Amarillo** — El proyecto tiene una arquitectura bien documentada y una infraestructura reproducible sólida, pero sufre de sobrediseño de microservicios para etapa PoC, cobertura de tests insuficiente, y deuda técnica por duplicación de código de infraestructura (DatabaseManager, handlers). La documentación de producto y arquitectura es excelente. Los hallazgos de seguridad del audit reciente están siendo corregidos activamente (commits recientes abordan findings #1-#6). La ejecución técnica va rezagada respecto al diseño, pero hay momentum de mejora visible en los últimos commits.

### Top 3 fortalezas

1. **Documentación de producto y arquitectura de alta calidad**: PRODUCT.md, ARCHITECTURE.md (468 líneas), STACK.md, DECISIONS.md (26 decisiones registradas) y SPEC.md forman un contexto completo que cualquier desarrollador puede consumir para entender el sistema.
2. **Infraestructura reproducible completa**: Docker Compose con 7 servicios, healthchecks, depends_on, volúmenes nombrados, imágenes multi-stage parametrizadas. Helm charts para K3s. Scripts de demo y simulación. El proyecto se levanta completo con un solo comando (`just container-dev`).
3. **Diseño de producto claro y enfocado**: PRODUCT.md define con precisión el problema, usuarios (ponente, organizador, audiencia, moderador), objetivos medibles y no-objetivos explícitos. El alcance está bien acotado para PoC.

### Top 3 riesgos o deudas

1. **[ALTA] Sobrediseño de microservicios para etapa PoC**: 6 servicios Java con comunicación HTTP síncrona y SQLite aislado. La complejidad operativa es innecesaria para la escala actual. Una consolidación a 3-4 servicios (merge stats+query, considerar moderation como módulo de ingest) reduciría puntos de fallo y mantenimiento sin perder capacidades.
2. **[ALTA] Cobertura de tests insuficiente**: tests unitarios escasos en Java, casi inexistentes en Python/chat, y solo 1 test en frontend. Sin tests de integración entre servicios, cualquier cambio en la cadena ingest→moderation→query requiere despliegue manual para validación.
3. **[MEDIA] Duplicación de código de infraestructura**: DatabaseManager, manejo de errores HTTP, y validación de tokens replicados en cada microservicio. Un fix en uno requiere replicación manual en 5+ lugares. El módulo `backend/common` existe pero está subutilizado.

### Próximos pasos recomendados

1. **Completar el plan de seguridad** — resolver el hallazgo crítico restante del SECURITY.md y fijar versiones exactas en `package.json` (quitar `^`).
2. **Añadir tests de integración para flujos críticos** — ingest → moderation → query, y users → auth → token validation. Priorizar los contratos entre servicios.
3. **Consolidar lógica duplicada en backend/common** — extraer DatabaseManager, helpers HTTP, y validación de tokens al módulo compartido ya existente.
4. **Evaluar consolidación de microservicios** — merger stats + query (ambos son proyecciones de lectura). Considerar si moderation puede ser un módulo interno de ingest. Pasar de 6 a 3-4 servicios.
5. **Añadir tests al chat Python** — pytest para bot.py, crypto.py, db.py y los routers.

---

## 4. Archivos relevantes

| Archivo | Tipo | Relevancia |
|---------|------|------------|
| `pom.xml` | config | Parent POM raíz — define módulos, dependencias y plugins para todo el backend |
| `docs/ARCHITECTURE.md` | doc | Fuente de verdad de la arquitectura — boundaries, flujo de datos, contratos (468 líneas) |
| `docs/PRODUCT.md` | doc | Define problema, usuarios, objetivos y alcance del producto |
| `docs/SPEC.md` | doc | Spec activa del trabajo en curso — detalle completo de funcionalidades |
| `docs/STACK.md` | doc | Stack tecnológico completo con versiones y restricciones |
| `docs/DECISIONS.md` | doc | Registro de 26 decisiones de diseño con trade-offs |
| `container/compose.yml` | config | Orquestación Docker de 7 servicios — entry point para desarrollo local |
| `container/backend/java/Dockerfile` | config | Imagen multi-stage Java parametrizada por servicio |
| `container/frontend/nginx.conf` | config | Reverse proxy del SPA — ruteo a backends |
| `frontend/web/vite.config.js` | config | Proxy de desarrollo del frontend — ruteo /api/* a cada backend |
| `frontend/web/package.json` | config | Dependencias y scripts del frontend (Vue 3, D3.js, Leaflet) |
| `chat/main.py` | entry | Entry point del chat Python — FastAPI + WebSocket + bot IA |
| `backend/services/insightbloom-ingest/.../bootstrap/IngestApplication.java` | entry | Entry point del servicio más complejo — 4 puertos externos |
| `backend/services/insightbloom-users/.../bootstrap/UsersApplication.java` | entry | Entry point del servicio de auth — wiring canónico |
| `agents/SECURITY.md` | doc | 7 hallazgos de seguridad documentados — 6 ya corregidos en commits recientes |
| `.github/workflows/ci.yml` | config | CI pipeline — build + test Maven, frontend y chat en paralelo |
| `.github/workflows/deploy.yml` | config | Deploy a K3s vía Helm |
| `.gitignore` | config | 162 líneas cubriendo Java, Node, Python, Docker, Helm, secretos |
| `Makefile` | config | Builder principal — targets: build, test, lint, clean |
| `Justfile` | config | Task runner — orquesta dev, deploy, demo, simulación |
