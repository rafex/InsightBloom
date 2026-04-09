# STACK.md

Fuente de verdad de la base tecnologica del proyecto.

## Runtime

- Frontend:
  Node.js para desarrollo local y toolchain de construccion.
- Backend:
  Java 25.

## Frameworks

- Frontend:
  Vite, Vue, Vue Router, animate.css, Pug, JavaScript, D3.js para la nube
  de palabras interactiva y ThumbmarkJS para obtener el identificador de
  dispositivo del cliente.
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
  SQLite para el PoC inicial. Cada microservicio debe mantener su propia
  base de datos y ownership de datos. La persistencia es efimera para el PoC
  y no necesita sobrevivir al reinicio del contenedor.
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
- La visualizacion principal debe apoyarse en D3.js.
- La UI debe estar preparada para una experiencia en vivo, con enfasis en
  lectura rapida y actualizacion fluida.
