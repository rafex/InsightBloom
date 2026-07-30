# Diagnóstico del Proyecto

_Fecha: 2026-07-29 | Repositorio: InsightBloom_

---

## 1. Exploración

### Estructura general

Monorepo organizado en dominios funcionales:

```
backend/          → Microservicios Java 25 (Maven multi-módulo)
  cli/            → CLI administrativo
  common/         → Librería compartida entre servicios
  services/       → 8 microservicios (users, ingest, query, moderation,
                     stats, survey, tools-gateway, presentations)
  contracts/      → Definiciones de contratos entre servicios
frontend/web/     → SPA Vue 3 + Vite 6 + D3.js + Leaflet
chat/             → FastAPI + WebSocket + bot IA (Python 3.12)
telegram/         → Bot de Telegram (Python 3.12)
container/        → Docker Compose (7 servicios) + Dockerfiles
infra/            → Egress proxy, code-ide, sandbox agents, scripts
scripts/          → Build, run, simulación y demos
spec-native/      → Documentación SpecNative (60+ archivos)
test/             → Tests E2E con pytest
agents/           → DIAGNOSE.md, SECURITY.md
```

### Lenguajes y tecnologías

| Stack | Tecnologías |
|-------|-------------|
| Backend | Java 25 (Temurin) + Ether 9.5.5 (Jetty 12) + SQLite (WAL mode) |
| Frontend | Vue 3 + Vite 6 + D3.js 7.9 + Leaflet 1.9 + Monaco Editor + Excalidraw + SurveyJS |
| Chat/IA | Python 3.12 + FastAPI + WebSocket + NATS + openai |
| Telegram | Python 3.12 + FastAPI |
| Presentaciones | Node.js 22 + Express + Marp CLI |
| Contenedores | Docker Compose, nginx:1.27-alpine |
| CI/CD | GitHub Actions (18 workflows), Helm charts para K3s |
| Identidad | JWT (ether-jwt), ThumbmarkJS (fingerprint), Twilio SMS, Zoho SMTP |

### Sistema de build / dependencias

- **Maven** — `pom.xml` raíz como parent POM; 3 submódulos (cli, common, services). Maven Wrapper.
- **npm** — `frontend/web/package.json` con 23 dependencias; dependencias con `^` (semver compatible). Vitest + ESLint.
- **pip** — `chat/requirements.txt` (7 deps) y `telegram/requirements.txt` (4 deps), ambas con versiones fijas (`==`).
- **Makefile** — Build principal: `make build` → `services-build` + `web-build`.
- **Justfile** — Task runner (5389 bytes).
- **Docker Compose** — `container/compose.yml` con 7 servicios orquestados con `depends_on` + `condition: service_healthy`.

### Puntos de entrada

| Componente | Entry point | Puerto |
|-----------|-------------|--------|
| Chat | `chat/main.py` (FastAPI + lifespan) | 8090 |
| Telegram | `telegram/main.py` (FastAPI + lifespan) | N/A (webhook) |
| Users | `backend/services/insightbloom-users/` | 8081 |
| Ingest | `backend/services/insightbloom-ingest/` | 8082 |
| Query | `backend/services/insightbloom-query/` | 8083 |
| Moderation | `backend/services/insightbloom-moderation/` | 8084 |
| Stats | `backend/services/insightbloom-stats/` | 8085 |
| Survey | `backend/services/insightbloom-survey/` | — |
| Tools Gateway | `backend/services/insightbloom-tools-gateway/` | — |
| Presentations | `backend/services/insightbloom-presentations/` | — |
| Frontend SPA | `frontend/web/index.html` → `src/` | 8080 |
| CLI | `backend/cli/insightbloom-cli/` | N/A |
| Egress Proxy | `infra/egress-proxy/egress_proxy.py` | — |

### Módulos y componentes clave

**Microservicios Java** (arquitectura hexagonal con capas domain/application/adapters):
1. **Users** — Auth (JWT/OTP), conferencias, roles, tickets, sandboxes, certificados, perfil. El más grande.
2. **Ingest** — Ingesta, clasificación y normalización de mensajes. Se comunica con users, moderation, stats, query.
3. **Query** — Consultas de nube de palabras y timeline.
4. **Moderation** — Censura automática/manual, dashboard de moderación.
5. **Stats** — Estadísticas y relevancia de palabras.
6. **Survey** — Encuestas y respuestas.
7. **Tools Gateway** — API gateway para Jitsi, Etherpad, sandboxes.
8. **Presentations** — Conversión Markdown→HTML slides (Marp).

**Python**: Chat (FastAPI+WebSocket+NATS+bot IA Roberto) y Telegram (FastAPI+webhook). Ambos hablan con ingest vía HTTP.

**Frontend**: SPA Vue 3 con router, nginx reverse proxy hacia todos los backends.

### Archivos de configuración relevantes

| Archivo | Propósito |
|---------|-----------|
| `.gitignore` (166 líneas) | Exclusión de IDE, OS, logs, DBs, secrets, artifacts |
| `container/compose.yml` | Orquestación 7 servicios con healthchecks |
| `container/frontend/nginx.conf` | Reverse proxy + cache SPA + catch-all |
| `.github/workflows/` (18 archivos) | CI + publish por servicio |
| `.github/dependabot.yml` | Auto-actualización de dependencias |
| `opencode.json` | Configuración de opencode |
| `codex.toml` | Configuración Codex/Codeium |
| `Makefile` + `Justfile` | Build y task runner |
| `pom.xml` | Parent POM Maven |

### Estado del repositorio

- **Rama activa**: `main` (sincronizado con `origin/main`)
- **Último commit**: `545e17c3` — `fix(web): nest event QR icon in button` (2026-07-29)
- **Working tree**: Limpio (sin staged, unstaged, untracked, ni conflictos)
- **Ramas**: 11 total, 6 activas de agentes (event-canvas-access-modes, ui-ux-audit, etc.)

---

## 2. Revisión de calidad

### Problemas estructurales o de diseño

- **Handlers monolíticos**: `ConferenceHandler.java` (~3025 líneas) y otros handlers (>500 líneas) concentran demasiada lógica en una sola clase. Violan SRP y dificultan mantenimiento y testing.
- **Dependencia circular**: `ingest` → `moderation` → `query` → `ingest` forma un ciclo de dependencias HTTP directas que complica despliegues y puede causar deadlocks en inicialización.
- **Servicio `users` sobrecargado**: Acumula auth, conferencias, tickets, sandboxes, certificados, roles, OTP, perfil — es un "god service" que debería descomponerse.
- **Duplicación Python**: `chat/` y `telegram/` comparten patrones (CommandParser, modelos, clientes HTTP) pero no comparten código. Cada uno mantiene su propia copia.

### Deuda técnica identificada

| Archivo | Líneas | Severidad | Problema |
|---------|--------|-----------|----------|
| `ConferenceHandler.java` | ~3025 | **Alta** | Handler monolítico, múltiples responsabilidades |
| `DatabaseManager.java` | ~1078 | Media | Gestión de DB demasiado grande |
| `SurveyHandler.java` | 662 | Media | Complejidad elevada para un handler |
| `SandboxHandler.java` | 633 | Media | Lógica de sandbox densa |
| `AuthGateHandler.java` | 572 | Media | Gateway de auth con múltiples concerns |
| `chat/services/command_parser.py` | ~100 | Baja | Duplicado casi idéntico en `telegram/` |

Nota positiva: no se encontraron marcadores `TODO`/`FIXME`/`HACK` en el código fuente.

### Prácticas del lenguaje no seguidas

- **Python**: Los docstrings están presentes en los módulos principales (main.py, connection_manager.py, command_parser.py) — buena práctica. Sin embargo, falta tipado estático (type hints) en varios servicios.
- **Java**: La estructura de paquetes sigue el estándar `dev.rafex.insightbloom.<servicio>`. Arquitectura hexagonal con capas bien definidas (`domain`, `application`, `adapters`).
- **Shell**: Scripts con shebangs correctos. `wait-for-service.sh` incluye healthcheck con timeout.
- **Frontend**: ESLint configurado, TypeScript config presente aunque el código es principalmente JavaScript.

### Riesgos de seguridad

- **Archivos `.secret_key`**: Presentes en disco (raíz y `chat/.secret_key`) pero correctamente excluidos por `.gitignore`. No están en git history. **Riesgo: Bajo**.
- **Dependencias npm con `^`**: Las versiones con rango `^` pueden introducir breaking changes en minor updates. Dependabot mitiga parcialmente.
- **Dependencias Python fijas**: `requirements.txt` usan `==` — buena práctica. Reduce riesgo de supply chain.
- **Comunicación inter-servicio**: Protegida con header `X-Internal-Auth` (no opcional en producción).
- **Variables de entorno**: `.env` y `secrets/` en `.gitignore`. Docker Compose requiere `DEEPSEEK_API_KEY` como variable.
- **Sin secretos hardcodeados**: No se detectaron credenciales en Dockerfiles ni código fuente.

### Cobertura de tests y documentación

| Componente | Tests | Cobertura |
|-----------|-------|-----------|
| Java `users` | 37 tests | **Alta** |
| Java `ingest` | 2 tests | Baja |
| Java `moderation` | 1 test | Baja |
| Java `query` | 1 test | Baja |
| Java `stats` | 1 test | Baja |
| Java `survey` | 1 test | Baja |
| Java `tools-gateway` | 2 tests | Baja |
| Java `cli` | 0 tests | **Nula** |
| Java `common` | 0 tests | **Nula** |
| Python `chat` | 4 tests | Media |
| Python `telegram` | 4 tests | Media |
| Python `egress-proxy` | 1 test | Baja |
| Frontend JS | 12+ tests | Media |
| Presentations Node.js | 3 tests | Media |
| E2E | 2 tests | Baja |

**Documentación**: Extensa (60+ archivos en `spec-native/`). Cubre producto, arquitectura, stack, convenciones, decisiones (ADRs), roadmap, roles, trazabilidad, workflows y pipelines.

---

## 3. Síntesis ejecutiva

### Resumen del proyecto

InsightBloom es una plataforma de conferencias en vivo que convierte mensajes de chat en nubes de palabras interactivas (dudas y temas), complementada con moderación en vivo, encuestas con certificados, presentaciones de slides (Marp), y un bot de IA (Roberto). Está construida como un monorepo que integra microservicios Java 25 (Ether 9.5.5 + SQLite), servicios Python 3.12 (FastAPI + WebSocket + NATS), y una SPA Vue 3 + D3.js + Leaflet. La infraestructura se orquesta con Docker Compose (7 servicios con healthchecks) y se despliega en K3s vía Helm. Toda la documentación sigue la metodología SpecNative (spec-native/ con 12 specs, 6 task sets, 9 workflows).

### Estado de salud

**🟡 Amarillo** — El repositorio está operacional y estable (CI/CD activo, working tree limpio, buena cobertura de tests en el servicio principal). Sin embargo, existen handlers monolíticos en Java (~3000 líneas), una dependencia circular entre microservicios (`ingest → moderation → query → ingest`), y duplicación de lógica entre servicios Python. Estos problemas no bloquean el desarrollo actual pero elevan el costo de mantenimiento y el riesgo de regresiones a mediano plazo.

### Top 3 fortalezas

1. **Documentación exhaustiva** — `spec-native/` contiene PRODUCT, ARCHITECTURE, STACK, DECISIONS (ADRs), specs por iniciativa, tareas, workflows y pipelines. Facilita onboarding, trazabilidad y continuidad multi-agente.
2. **Arquitectura bien delimitada** — Separación clara entre backend Java, servicios Python y frontend SPA. Docker Compose con healthchecks y `depends_on` asegura orden de inicio correcto. Arquitectura hexagonal en servicios Java con capas domain/application/adapters.
3. **Cobertura de tests razonable en componentes críticos** — 60+ tests unitarios Java, 12+ tests frontend, tests Python en chat/telegram, tests E2E. CI verifica cada commit con GitHub Actions.

### Top 3 riesgos o deudas

1. **Handlers monolíticos en Java** — `ConferenceHandler.java` (~3025 líneas) y otros handlers >500 líneas concentran demasiada lógica. Violan SRP, dificultan testing y ralentizan cualquier cambio en la lógica de conferencias.
2. **Dependencia circular entre microservicios** — `ingest → moderation → query → ingest` crea un ciclo de dependencias HTTP directas que puede provocar deadlocks en inicialización, despliegues inconsistentes y dificultad para testear servicios de forma aislada.
3. **Servicio `users` sobrecargado y duplicación Python** — `users` acumula demasiadas responsabilidades (auth, conferencias, tickets, sandboxes, certificados). Los servicios Python (`chat` y `telegram`) duplican lógica de parsing y modelos sin compartir código.

### Próximos pasos recomendados

1. **Refactorizar `ConferenceHandler`** — Extraer responsabilidades en handlers separados (ConferenceCreateHandler, ConferenceUpdateHandler, ConferenceQueryHandler) y mover lógica de negocio a use cases del dominio. *Impacto: alto — reduce deuda del archivo más grande del proyecto.*
2. **Romper el ciclo de dependencias** — Reemplazar llamadas HTTP directas entre ingest/moderation/query por eventos asincrónicos vía NATS (ya disponible en el chat). Definir tópicos y contratos de eventos. *Impacto: alto — elimina riesgo de deadlocks y habilita testeo aislado.*
3. **Crear paquete Python compartido** — Factorizar `CommandParser`, modelos de dominio y utilidades HTTP en `common/python/` y actualizar `chat/` y `telegram/` para consumirlo. *Impacto: medio — elimina duplicación y unifica comportamiento.*
4. **Ampliar cobertura de tests en servicios Java** — Agregar tests unitarios en `ingest`, `moderation`, `query`, `stats`, `survey`, `common` y `cli`. Usar los 37 tests de `users` como referencia de estilo. *Impacto: medio — reduce riesgo de regresiones.*
5. **Fijar versiones npm** — Cambiar rangos `^` a versiones exactas en `frontend/web/package.json` o auditar con `npm audit` periódicamente. *Impacto: bajo — reduce riesgo de supply chain.*

---

## 4. Archivos relevantes

| Archivo | Tipo | Relevancia |
|---------|------|------------|
| `backend/services/insightbloom-users/src/main/java/.../ConferenceHandler.java` | handler | Núcleo de lógica de conferencias, ~3025 líneas — deuda técnica más alta |
| `backend/services/insightbloom-ingest/src/main/java/.../IngestHandler.java` | handler | Punto de entrada del ciclo de dependencias circular |
| `backend/services/insightbloom-moderation/src/main/java/.../ModerationHandler.java` | handler | Participa en el ciclo ingest→moderation→query→ingest |
| `chat/main.py` | entry | Punto de entrada del bot de IA y WebSocket |
| `chat/services/command_parser.py` | module | Lógica duplicada en `telegram/services/command_parser.py` |
| `frontend/web/package.json` | config | 23 dependencias con rangos `^` — riesgo de supply chain |
| `frontend/web/src/main.js` | entry | Bootstrap de la SPA Vue 3 |
| `container/compose.yml` | infra | Orquestación de 7 servicios — fuente de verdad de topología local |
| `container/frontend/nginx.conf` | infra | Reverse proxy con reglas de ruteo hacia backends |
| `pom.xml` | build | Parent POM Maven — versiones centralizadas de Ether/Jetty/Jackson |
| `spec-native/ARCHITECTURE.md` | docs | Define la arquitectura del sistema (24K) |
| `spec-native/DECISIONS.md` | docs | 10 decisiones de arquitectura y trade-offs persistentes (94K) |
| `spec-native/STACK.md` | docs | Stack tecnológico y restricciones |
| `.github/workflows/ci.yml` | ci | Workflow principal de CI |
| `agents/SECURITY.md` | docs | Auditoría de seguridad y mitigaciones (19K) |
| `Makefile` | build | Orquestación de build multi-lenguaje |
| `Justfile` | tasks | Task runner con 50+ comandos |
