# CI.md

Gates de integración continua para InsightBloom.

## Alcance

Este repositorio controla el código y la integración continua. Sus workflows validan
el código y, cuando corresponde, construyen y publican imágenes en GHCR. No despliegan
directamente al cluster K3s; el CD pertenece al repositorio separado
`/Users/rafex/repository/github/rafex/InsightBloom-gitops` y es ejecutado por FluxCD.

## Platform

GitHub Actions (`.github/workflows/ci.yml`)

## Triggers

- **Push to**: `main`, `claude/**`
- **PR to**: `main`

## Jobs (3 paralelos)

### 1. Build & Test Services (Java)

**Runtime**: `ubuntu-latest`, Java 25 (Temurin), Maven cache

```bash
./mvnw -f pom.xml clean verify
```

Gates:
- [x] Compilación sin errores de todos los módulos Maven
- [x] Tests unitarios (JUnit 5.11.4 + Mockito 5.18.0)
- [x] Surefire reports generados en `target/surefire-reports/`

### 2. Build & Test Frontend (JavaScript)

**Runtime**: `ubuntu-latest`, Node 25, npm cache desde `package-lock.json`

```bash
npm --prefix frontend/web install
npm --prefix frontend/web run build    # Vite build
npm --prefix frontend/web run test     # Vitest
```

Gates:
- [x] Build de producción sin errores (Vite 6)
- [x] Tests Vitest pasan
- [x] ESLint sin errores

### 3. Test Chat Service (Python)

**Runtime**: `ubuntu-latest`, Python 3.12, pip cache desde `requirements.txt`

```bash
pip install -r chat/requirements.txt
pip install pytest pytest-asyncio
cd chat && python -m pytest -v
```

Gates:
- [x] Tests unitarios del chat (auth, commands, websocket)

## Servicios cubiertos por CI

| Servicio | Build | Tests |
|----------|-------|-------|
| users (Java) | Maven | JUnit |
| ingest (Java) | Maven | JUnit |
| query (Java) | Maven | JUnit |
| moderation (Java) | Maven | JUnit |
| stats (Java) | Maven | JUnit |
| survey (Java) | Maven | JUnit |
| cli (Java) | Maven | — |
| presentations (Node.js) | — | — (no incluido en CI actual) |
| chat (Python) | — | pytest |
| web (Vue) | Vite | Vitest + ESLint |

## Fail Policy

- **PR**: bloquea merge si cualquier job falla.
- **Push a main**: notifica fallo, no revierte.

## Optional Gates (no implementados aún)

- [ ] Coverage report (JaCoCo / vitest coverage)
- [ ] SAST scan (CodeQL / SonarQube)
- [ ] Integration tests con Docker Compose
- [ ] E2E tests con Playwright/Cypress
- [ ] CI para presentations service
