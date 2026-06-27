# STACK.md

Fuente de verdad de la base tecnologica del proyecto.

## Runtime

- Frontend SPA: Node.js 25 para desarrollo y build (Vite). Runtime: nginx 1.27-alpine.
- Backend Java: Java 25 (Temurin) + Ether 9.5.5 (Jetty 12).
- Backend presentations: Node.js 22 (Express 4.21).
- Chat: Python 3.12 (FastAPI + uvicorn + WebSocket).
- CLI admin: Java 25.

## Frameworks

- Frontend:
  Vite 6, Vue 3, Vue Router 4, animate.css, Pug, JavaScript.
  Librerias de visualizacion:
  - `d3` ^7.9 + `d3-cloud` ^1.2 para la nube de palabras interactiva.
  - `leaflet` ^1.9 + OpenStreetMap para mapas interactivos (pantalla de
    introduccion a la conferencia y preview de ubicacion en el formulario
    de creacion). Reemplaza a topojson-client/world-atlas.
  - `axios` ^1.7 para llamadas HTTP al backend.
  - `thumbmark.js` para identificador de dispositivo del cliente.
- Backend:
  Java 25 con Ether 9.5.5 como framework base.
  `insightbloom-parent` hereda de `dev.rafex.ether.parent:ether-parent:9.5.5`,
  que gestiona las versiones de Jetty 12 y Jackson de forma centralizada.
  Modulos Ether en uso:
  - `ether-http-jetty12`: servidor HTTP sobre Jetty 12 con routing y handlers.
  - `ether-json`: codec JSON sobre Jackson.
  - `ether-jwt`: generacion y validacion de tokens JWT (HS256/RS256/ES256).
  - `ether-crypto`: primitivas criptograficas, hashing SHA-256 de contrasenas.
  - `ether-http-client`: cliente HTTP saliente para llamadas inter-servicio.
  - `ether-database-core` + `ether-jdbc`: abstracciones JDBC para SQLite.
  - `ether-config`: configuracion tipada.

## Infraestructura

- Persistencia:
  SQLite con `PRAGMA journal_mode=WAL` y `busy_timeout=5000` en todos los
  servicios Java y el chat. Cada microservicio mantiene su propia base de
  datos bajo `/data/<service>.db` con ownership exclusivo. Volumenes Docker
  nombrados preservan datos entre reinicios en desarrollo.
  `presentations` no usa base de datos — almacena archivos en volumen Docker.
- Contenedores:
  Estructura bajo `container/`:
  - `container/backend/java/Dockerfile`: imagen multi-stage parametrizada.
    Stage builder `eclipse-temurin:25-jdk-alpine` compila modulos Maven;
    stage runtime `eclipse-temurin:25-jre-alpine` copia solo el JAR del
    servicio indicado por `ARG SERVICE`. Usado por: users, ingest, query,
    moderation, stats, survey.
  - `backend/services/insightbloom-presentations/Dockerfile`: imagen Node.js
    para el servicio de presentaciones (Express + Marp CLI).
  - `chat/Dockerfile`: imagen Python 3.12 para el servicio de chat.
  - `container/frontend/Dockerfile`: stage builder `node:22-alpine` ejecuta
    `npm ci && npm run build`; stage runtime `nginx:1.27-alpine` sirve el SPA
    y actua como reverse proxy hacia los backends.
  - `container/frontend/nginx.conf`: proxy rules para `/api/users`, `/api/ingest`,
    `/api/query`, `/api/moderation`, `/api/survey`, `/api/presentations`.
    Cache de assets estaticos (1 año). SPA catch-all con `try_files`.
  - `container/compose.yml`: orquesta 9 servicios con `depends_on` +
    `condition: service_healthy`. Red interna `backend`. Volumenes nombrados
    para cada servicio con persistencia.
- Hosting: despliegue en K3s via Helm charts (`infra/helm/charts/insightbloom`).
- Container Registry: GitHub Container Registry (`ghcr.io/rafex/insightbloom-*`).
- CI/CD: GitHub Actions (3 workflows). Ver `pipelines/CI.md` y `pipelines/CD.md`.

## Integraciones

- **LLM Provider** (DeepSeek via API compatible con OpenAI):
  usado por `chat` (bot Roberto) y `survey` (generacion de preguntas).
  Configuracion: `LLM_PROVIDER_BASE_URL`, `LLM_PROVIDER_MODEL`, `LLM_PROVIDER_API_KEY`.
  Libreria: `openai` 1.55 (Python).
- **Twilio**: envio de OTP via SMS para verificacion de usuarios.
  Configuracion: `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER`.
- **Zoho SMTP**: envio de correos (certificados, notificaciones, OTP email).
  Configuracion: `ZOHO_SMTP_HOST` (default: smtppro.zoho.com), `ZOHO_SMTP_PORT`
  (default: 465), `ZOHO_SMTP_USERNAME`, `ZOHO_SMTP_PASSWORD`, `ZOHO_FROM_ADDRESS`.
- **Marp CLI**: conversion Markdown → HTML slides. Paquete `@marp-team/marp-cli` ^4.0.
- **Identidad de dispositivo**: ThumbmarkJS para fingerprint del cliente (GUEST).
- **Seguridad**: tokens JWT (ether-jwt), hashing SHA-256 (ether-crypto),
  header `X-Internal-Auth` para comunicacion entre servicios.
- **Maven Central / Sonatype**: modulos Ether desde `dev.rafex.ether.*`.
  Catalogo: https://ether.rafex.io/
- **Tooling local**: `Makefile` (builder) + `Justfile` (task runner).
  Scripts: `scripts/build/`, `scripts/run/`, `scripts/sim/`.

## Restricciones

- El frontend SPA se construye con JavaScript, no TypeScript (por decision de diseño).
- El backend Java usa Ether 9.5.5 + Jetty 12. Evitar frameworks externos (Spring, etc.).
- La persistencia por defecto es SQLite con WAL mode.
- Los datos en SQLite persisten via volumenes Docker y no son efimeros.
- Los identificadores expuestos por APIs y entre servicios deben ser UUID.
- La nube de palabras debe usar D3.js (d3-cloud), no librerias alternativas.
- Los mapas deben usar Leaflet.js + OpenStreetMap (no Google Maps ni tiles de pago).
- La UI debe estar preparada para experiencia en vivo: lectura rapida, actualizacion fluida.
- Las comunicaciones entre servicios se protegen con `X-Internal-Auth` (no opcional en prod).
- Los secretos nunca se versionan en el repositorio (ver `.gitignore`).
