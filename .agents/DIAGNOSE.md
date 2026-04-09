# Diagnóstico del Proyecto

_Fecha: 2026-04-09 | Repositorio: InsightBloom_

---

## 1. Exploración

### Estructura general
El proyecto tiene una estructura organizada en directorios principales:
- `backend/` - Microservicios Java con arquitectura hexagonal
- `frontend/web/` - Aplicación web con Vue.js y D3.js
- `infra/` - Configuración de infraestructura (Docker, Compose, Helm)
- `docs/` - Documentación del proyecto
- `scripts/` - Scripts de build, ejecución y simulación

### Lenguajes y tecnologías
- **Java 25** - Backend (microservicios)
- **Maven** - Sistema de build para backend
- **JavaScript/TypeScript** - Frontend
- **Vue.js** - Framework frontend
- **D3.js** - Visualización de datos
- **SQLite** - Bases de datos efímeras por microservicio
- **Docker/Compose/Helm** - Contenerización y orquestación

### Sistema de build / dependencias
- **Backend**: Maven (pom.xml raíz con módulos: contracts, services, cli)
- **Frontend**: npm (package.json en frontend/web)
- **CI/CD**: GitHub Actions (.github/workflows/ci.yml)

### Puntos de entrada
- `backend/services/insightbloom-moderation/src/main/java/dev/rafex/insightbloom/moderation/bootstrap/ModerationApplication.java`
- `backend/services/insightbloom-ingest/src/main/java/dev/rafex/insightbloom/ingest/bootstrap/IngestApplication.java`
- `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/bootstrap/UsersApplication.java`
- `backend/services/insightbloom-query/src/main/java/dev/rafex/insightbloom/query/bootstrap/QueryApplication.java`
- `backend/cli/insightbloom-cli/src/main/java/dev/rafex/insightbloom/cli/CreateUserCli.java`

### Módulos y componentes clave
- **Microservicio Ingest**: Recibe eventos parseados, valida tokens, persiste mensajes
- **Microservicio Query**: Expone nubes de palabras y timelines
- **Microservicio Moderación**: Gestiona revisión y censura manual
- **Microservicio Usuarios**: Gestiona autenticación y tokens
- **Microservicio Stats**: Calcula agregados y relevancia
- **Frontend Web**: Visualización con D3.js y Vue Router

### Archivos de configuración relevantes
- `.gitignore` - Configuración de Git
- `.github/workflows/ci.yml` - Pipeline CI/CD
- `infra/compose/local.yml` - Configuración Docker Compose local
- `infra/helm/charts/insightbloom/` - Configuración Helm para Kubernetes

### Estado del repositorio
- **Ramas**: `main` (actual), `remotes/origin/main`
- **Último commit**: `14f8f6f fix(moderation): detalle visible en tarjetas y sin duplicados`
- **Estado**: Working tree clean (sin cambios sin commitear)
- **Archivos trackeados**: 234 archivos

---

## 2. Revisión de calidad

### Problemas estructurales o de diseño
- **Baja cobertura de tests**: Solo 7 archivos de test para 126 archivos de código (5.5%)
- **Ausencia de tests en componentes críticos**: Microservicios clave no tienen cobertura de tests adecuada
- **CI/CD incompleto**: El pipeline de CI ejecuta `DskipTests` en el paso de build

### Deuda técnica identificada
- **Configuración de Java 25**: Versión muy reciente (lanzada en 2025), posible incompatibilidad
- **Dependencias sin versionar específicamente**: package.json no especifica versiones exactas
- **Estructura de worktrees**: Presencia de `.claude/worktrees/` en el repo principal

### Prácticas del lenguaje no seguidas
- **Java**: Falta de tests unitarios e integración
- **Maven**: Posible falta de profiles para diferentes entornos
- **JavaScript**: Posible falta de TypeScript en el frontend

### Riesgos de seguridad
- **Configuración de Java 25**: Versión beta/experimental puede tener vulnerabilidades
- **No se detectan archivos sensibles expuestos** en la exploración inicial

### Cobertura de tests y documentación
- **Tests**: Muy baja cobertura (5.5%)
- **Documentación**: Buena documentación en `docs/` con arquitectura, comandos y convenciones
- **CI/CD**: Pipeline presente pero ejecuta build sin tests (`-DskipTests`)

---

## 3. Síntesis ejecutiva

### Resumen del proyecto
Plataforma compuesta por varios micro‑servicios que gestionan ingestión de datos, consultas, moderación, usuarios y métricas, con una UI web para visualización y administración.

**Arquitectura:**
- `backend/` – 5 micro‑servicios Java basados en Spring Boot
- `frontend/web/` – SPA escrita en Vue.js + TypeScript, con visualizaciones D3.js
- `infra/` – Docker‑Compose y Helm charts para despliegues locales y en Kubernetes

**Tecnologías clave:**
- Java 25 + Maven (build del backend)
- Node.js (npm) + TypeScript + Vue 3 (frontend)
- SQLite como base de datos embebida
- GitHub Actions para CI/CD, Docker/Compose/Helm para orquestación

### Estado de salud
**🟡 Amarillo** – el proyecto funciona y la arquitectura está bien delimitada, pero la falta de pruebas automatizadas y la dependencia de una versión muy nueva de Java (25) generan riesgos operacionales y de seguridad que deben mitigarse antes de escalar.

### Top 3 fortalezas
1. **Arquitectura modular** – separación clara en micro‑servicios y frontend permite escalar y mantener componentes de forma independiente.
2. **Infraestructura reproducible** – Docker‑Compose y Helm proporcionan entornos consistentes para desarrollo, pruebas y despliegue.
3. **Documentación sólida** – la carpeta `docs/` está bien mantenida, facilitando onboarding y comprensión del sistema.

### Top 3 riesgos o deudas
1. **Cobertura de pruebas extremadamente baja (≈5%)**. CI ejecuta sólo el build, sin ejecutar pruebas → riesgo de regresiones y dificultad para refactorizar.
2. **Uso de Java 25** – versión recién lanzada, todavía con pocos parches y posible incompatibilidad con librerías; además, dependencias no están versionadas de forma exacta, lo que complica reproducibilidad y auditoría de vulnerabilidades.
3. **Falta de pruebas en el frontend (TypeScript) y ausencia de lint/format en el pipeline** – calidad del código JavaScript/TS no está garantizada, lo que puede generar bugs de UI y dificultar la integración continua.

### Próximos pasos recomendados
1. **Introducir pruebas automatizadas para el backend** – Crear módulo de pruebas con JUnit 5 y Mockito, alcanzar al menos 70% de cobertura, configurar GitHub Actions para ejecutar `mvn test`.
2. **Añadir pruebas unitarias y de integración al frontend** – Adoptar Jest + Vue Test Utils, añadir linting (ESLint) y formateo (Prettier) al CI.
3. **Fijar versiones de dependencias y evaluar Java 25** – Revisar `pom.xml` y fijar versiones exactas, considerar bajar a Java 21 LTS para mayor estabilidad.

---

## 4. Archivos relevantes

| Archivo | Tipo | Relevancia |
|---------|------|------------|
| `pom.xml` | config | Archivo raíz de Maven que define la estructura de módulos del proyecto |
| `backend/services/pom.xml` | config | Configuración de Maven para los microservicios backend |
| `frontend/web/package.json` | config | Dependencias y scripts del frontend Vue.js |
| `.github/workflows/ci.yml` | config | Pipeline de CI/CD con GitHub Actions |
| `docs/ARCHITECTURE.md` | docs | Documentación detallada de la arquitectura del proyecto |
| `backend/services/insightbloom-moderation/src/main/java/dev/rafex/insightbloom/moderation/bootstrap/ModerationApplication.java` | entry | Punto de entrada del microservicio de moderación |
| `backend/services/insightbloom-ingest/src/main/java/dev/rafex/insightbloom/ingest/bootstrap/IngestApplication.java` | entry | Punto de entrada del microservicio de ingestión |
| `infra/compose/local.yml` | config | Configuración de Docker Compose para entorno local |
| `frontend/web/src/` | module | Módulo frontend con Vue.js y D3.js |
| `backend/services/` | module | Directorio con todos los microservicios backend |
