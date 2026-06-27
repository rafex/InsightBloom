# STACK.md

Fuente de verdad de la base tecnologica del proyecto.

## Runtime

- Frontend:
  Node.js para desarrollo local y toolchain de construccion.
- Backend:
  Java 25.

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
  SQLite para el PoC inicial. Cada microservicio mantiene su propia base de
  datos y ownership de datos bajo `/data/<service>.db`. La persistencia es
  efimera para el PoC y no necesita sobrevivir al reinicio del contenedor,
  aunque los volumenes Docker la preservan entre reinicios en la config actual.
- Contenedores:
  Estructura bajo `container/`:
  - `container/backend/java/Dockerfile`: imagen multi-stage parametrizada.
    Stage builder con `eclipse-temurin:25-jdk-alpine` compila todos los
    modulos Maven; stage runtime con `eclipse-temurin:25-jre-alpine` copia
    solo el JAR del servicio indicado por `ARG SERVICE`.
  - `container/frontend/Dockerfile`: stage builder `node:22-alpine` ejecuta
    `npm ci && npm run build`; stage runtime `nginx:1.27-alpine` sirve el SPA
    y actua como reverse proxy hacia los backends.
  - `container/frontend/nginx.conf`: proxy rules replicando exactamente los
    rewrites de `vite.config.js` para `/api/users`, `/api/ingest`,
    `/api/query`, `/api/moderation`. Cache de assets estaticos (1 año).
    SPA catch-all con `try_files`.
  - `container/compose.yml`: orquesta 6 servicios con `depends_on` +
    `condition: service_healthy`. Red interna `backend`. Volumenes nombrados
    para cada servicio.
- Hosting:
  despliegue apoyado en contenedores y charts Helm.
- CI/CD:
  GitHub Actions via `.github/workflows`.
- Observabilidad:
  por definir.

## Integraciones

- Chat de entrada:
  fuente de comandos `/duda` y `/tema` ya parseados. El sistema recibira
  eventos por webhook y tambien llamadas por API REST.
- Seguridad:
  tokens para usuarios registrados y usuarios invitados o nuevos, apoyados en
  `ether-crypto`.
- Identidad de dispositivo:
  el frontend obtendra un identificador de dispositivo con ThumbmarkJS para
  enviarlo como parte del contrato.
- Maven Central / Sonatype:
  todos los modulos Ether se distribuyen desde Maven Central via
  `dev.rafex.ether.*`. Catalogo oficial: https://ether.rafex.io/
- Tooling local:
  `Makefile` como builder y `Justfile` como task runner. Los scripts de
  soporte viven en `scripts/build/` (construccion), `scripts/run/`
  (ejecucion) y `scripts/sim/` (simulacion y demo).

## Restricciones

- El frontend debe construirse inicialmente con JavaScript, no TypeScript.
- El backend debe implementarse con Java 25 y evitar frameworks pesados
  fuera de la biblioteca estandar y la libreria HTTP seleccionada.
- La persistencia inicial del PoC debe resolverse con SQLite.
- La persistencia del PoC es efimera y puede perderse al reiniciar el
  contenedor.
- Los identificadores expuestos por APIs y entre servicios deben ser UUID.
- La nube de palabras debe apoyarse en D3.js (d3-cloud).
- Los mapas de ubicacion deben usar Leaflet.js + OpenStreetMap (no Google Maps
  ni tiles de pago).
- La UI debe estar preparada para una experiencia en vivo, con enfasis en
  lectura rapida y actualizacion fluida.
