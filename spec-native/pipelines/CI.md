# CI.md

Gates de integración continua para InsightBloom.

## Platform

GitHub Actions (`.github/workflows/ci.yml`)

## Triggers

- **Push to**: `main`, `claude/**`
- **PR to**: `main`

## Jobs

### 1. Build & Test Services (Java)

**Runtime**: `ubuntu-latest`, Java 25 (Temurin), Maven cache

```bash
./mvnw -f pom.xml clean verify
```

Gates:
- [x] Compilación sin errores
- [x] Tests unitarios (JUnit 5 + Mockito)
- [x] Surefire reports generados

### 2. Build & Test Frontend (JavaScript)

**Runtime**: `ubuntu-latest`, Node 25, npm cache

```bash
npm --prefix frontend/web install
npm --prefix frontend/web run build
npm --prefix frontend/web run test
```

Gates:
- [x] Build sin errores
- [x] Tests Vitest pasan
- [x] ESLint sin errores

### 3. Test Chat Service (Python)

**Runtime**: `ubuntu-latest`, Python 3.12, pip cache

```bash
pip install -r chat/requirements.txt
pip install pytest pytest-asyncio
cd chat && python -m pytest -v
```

Gates:
- [x] Tests unitarios pasan

## Optional Gates (futuro)

- [ ] Coverage report (JaCoCo / vitest coverage)
- [ ] SAST scan (CodeQL / SonarQube)
- [ ] Integration tests con Docker Compose
- [ ] E2E tests con Playwright

## Failure Policy

- **PR**: bloquea merge si cualquier job falla.
- **Push a main**: notifica fallo, no revierte.
