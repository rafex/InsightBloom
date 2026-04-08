# ARCHITECTURE.md

Describe la arquitectura actual del proyecto.

## Vision general

El sistema se dividira en un frontend web para visualizacion interactiva y
un conjunto de microservicios HTTP para recepcion, consulta, moderacion,
usuarios y estadisticas. Los servicios reciben mensajes estructurados a
partir de comandos como `/duda` y `/tema` ya parseados, normalizan la
palabra clave, conservan el detalle descriptivo y permiten operar una
moderacion mixta: automatica y manual. El frontend consulta los datos
agregados para renderizar nubes separadas de dudas y temas y permite abrir
una vista de detalle por palabra en formato timeline cronologico.

La responsabilidad de agregacion, ordenamiento, seguridad y moderacion vive
en backend. La responsabilidad de visualizacion, navegacion y animacion vive
en frontend. Para el PoC, cada microservicio debe tener su propia base
SQLite efimera. Los microservicios backend deben implementarse con
arquitectura hexagonal para separar dominio, casos de uso y adaptadores.

## Modulos principales

- Modulo frontend web:
  renderiza la nube de palabras con D3.js, administra rutas con Vue Router,
  presenta animaciones, obtiene el identificador de dispositivo con
  ThumbmarkJS y muestra el timeline de detalles al seleccionar una palabra.
- Modulo dashboard de moderacion:
  permite revisar palabras y mensajes capturados, ver estado de censura e
  intencion estimada y aplicar censura manual sobre casos no detectados por
  la barrera automatica.
- Microservicio `insightbloom-ingest`:
  recibe eventos ya parseados por webhook o API REST, valida tokens y
  persiste mensajes canonicos con metadatos del emisor y del dispositivo.
- Microservicio `insightbloom-query`:
  expone nubes separadas de dudas y temas y consulta timelines por palabra.
- Microservicio `insightbloom-moderation`:
  expone listas de palabras y mensajes para revision, aplica censura manual,
  restauracion y edicion de palabras o mensajes.
- Microservicio `insightbloom-users`:
  gestiona usuarios registrados, invitados o nuevos, emite o valida tokens y
  administra el acceso al portal de creacion de conferencias.
- Microservicio `insightbloom-stats`:
  calcula agregados y relevancia para enriquecer la nube de palabras.
- Modulo de agregacion:
  agrupa por palabra, mantiene conteos, fusiona singular/plural y calcula la
  forma visible canonica segun la variante con mayor frecuencia.
- Modulo de moderacion y clasificacion ligera:
  aplica censura de palabras o terminos excluidos del PoC y calcula una
  intencion basica del mensaje para enriquecer analisis y filtros futuros.
  Para el PoC, la palabra se clasifica en `pregunta`, `idea`, `duda` o
  `interes`, y el detalle en `pregunta`, `preocupacion`, `critica` o
  `propuesta`.

## Estilo backend

- Cada microservicio backend sigue arquitectura hexagonal.
- El dominio no depende de HTTP, SQLite ni librerias concretas de
  infraestructura.
- Los casos de uso exponen puertos de entrada para las capacidades del
  servicio.
- HTTP, SQLite, autenticacion y mensajeria entre servicios viven como
  adaptadores.
- Las reglas de negocio deben poder probarse sin levantar Jetty ni acceder a
  SQLite real.

## Estructura base de un microservicio backend

```text
backend/services/insightbloom-<service-name>/
  README.md
  pom.xml
  src/
    main/
      java/
        dev/rafex/insightbloom/<service-name>/
          domain/
            model/
            ports/
            services/
          application/
            usecases/
          adapters/
            inbound/
              http/
                handlers/
                dto/
            outbound/
              sqlite/
              usersclient/
              moderationclient/
              statsclient/
          bootstrap/
```

- `domain/model`:
  entidades, value objects, enums y reglas invariantes.
- `domain/ports`:
  contratos que necesita el dominio o los casos de uso para hablar con el
  exterior.
- `domain/services`:
  logica de dominio que no pertenece claramente a una sola entidad.
- `application/usecases`:
  orquestacion de casos de uso.
- `adapters/inbound/http`:
  handlers, mapeo de request/response y DTOs HTTP.
- `adapters/outbound/sqlite`:
  repositorios y gateways sobre SQLite.
- `adapters/outbound/*client`:
  clientes HTTP hacia otros microservicios.
- `bootstrap`:
  wiring, configuracion y arranque del servicio.

## Estructura base del frontend

```text
frontend/web/
  README.md
  package.json
  vite.config.js
  src/
    main.js
    app/
      router/
      layout/
    pages/
      landing/
      login/
      conference/
      moderation/
    features/
      conferences/
      doubts/
      topics/
      timeline/
      moderation/
      auth/
    components/
      cloud/
      timeline/
      tables/
      forms/
    services/
      api/
      auth/
    styles/
    pug/
```

- `app/router`:
  definicion de rutas y guards de navegacion.
- `app/layout`:
  shells y layouts base de aplicacion.
- `pages`:
  entradas por ruta.
- `features`:
  logica por capacidad del producto.
- `components`:
  piezas visuales reutilizables.
- `services/api`:
  clientes HTTP y acceso a microservicios.
- `services/auth`:
  manejo de token, sesion y permisos en frontend.
- `styles`:
  estilos globales y variables visuales.
- `pug`:
  plantillas o utilidades relacionadas con Pug.

## Estructura raiz del repositorio

```text
/
  .github/
  README.md
  Justfile
  Makefile
  frontend/
    web/
  backend/
    contracts/
      insightbloom-contracts/
    services/
      insightbloom-users/
      insightbloom-ingest/
      insightbloom-query/
      insightbloom-moderation/
      insightbloom-stats/
    cli/
      insightbloom-cli/
  infra/
    docker/
    compose/
    helm/
      charts/
  scripts/
    build/
    run/
    sim/
  docs/
```

- `.github/`:
  workflows de GitHub Actions.
- `Justfile`:
  task runner para flujos de desarrollo, demos y operaciones.
  Delega compilacion a `make`.
- `Makefile`:
  builder: compila, testea, lintea y produce artefactos.
- `frontend/web`:
  aplicacion web con Vite, Vue y D3.js.
- `backend/contracts`:
  contratos compartidos entre microservicios (DTOs, interfaces).
- `backend/services`:
  microservicios HTTP del sistema.
- `backend/cli`:
  CLI de administracion para operaciones de usuarios y configuracion.
- `infra/docker`:
  Dockerfiles por servicio.
- `infra/compose`:
  composicion local de servicios con Docker Compose.
- `infra/helm/charts`:
  charts de despliegue en Kubernetes.
- `scripts/build`:
  scripts de instalacion, compilacion y empaquetado.
- `scripts/run`:
  scripts de arranque local de servicios.
- `scripts/sim`:
  scripts de simulacion, demo y observacion en tiempo real.
- `docs`:
  documentacion del proyecto: arquitectura, stack, convenciones y specs.

## Ownership de datos

- `insightbloom-users`:
  usuarios registrados, invitados, tokens, roles y conferencias.
- `insightbloom-ingest`:
  mensajes canonicos recibidos, palabra original, palabra normalizada,
  detalle original, detalle visible y metadatos de envio.
- `insightbloom-moderation`:
  decisiones de censura, estado actual de palabras y mensajes y valores
  editados vigentes.
- `insightbloom-query`:
  proyecciones de lectura para nubes y timelines.
- `insightbloom-stats`:
  agregados de relevancia y metricas derivadas para ranking de palabras.

## Contratos entre servicios

- `insightbloom-users -> insightbloom-ingest`:
  validacion de token, resolucion de usuario y conferencia.
- `insightbloom-ingest -> insightbloom-moderation`:
  evaluacion de censura automatica y consulta de estado manual vigente.
- `insightbloom-ingest -> insightbloom-stats`:
  publicacion de mensaje aceptado para recalculo de agregados.
- `insightbloom-stats -> insightbloom-query`:
  entrega de proyecciones o snapshots para nubes separadas.
- `insightbloom-moderation -> insightbloom-query`:
  propagacion del estado visible, censurado o restaurado para reflejarlo en
  las vistas.

Para el PoC, la comunicacion entre microservicios se resuelve por HTTP
sincrono. No se introduce broker de eventos en esta etapa. Los servicios de
lectura pueden regenerar proyecciones por llamada o por actualizacion
explicita, pero el contrato base es HTTP a HTTP.

## Flujo principal

1. El organizador autenticado crea una conferencia con nombre, UUID interno
   e identificador amigable.
2. Un participante envia una duda o tema desde un cliente integrado.
3. El sistema origen entrega el mensaje ya parseado por webhook o API REST.
4. `insightbloom-users` valida o resuelve identidad y token del emisor.
5. `insightbloom-ingest` registra tipo, palabra, detalle, autor, timestamp,
   conferencia y dispositivo.
6. El backend normaliza la palabra: minusculas, sin acentos, sin espacios
   extra y fusion de singular/plural.
7. El backend evalua censura automatica sobre palabra y detalle y calcula
   intencion por separado para ambos campos.
8. Si el detalle es censurable y la palabra no, el detalle visible pasa a
   `detalle censurado`.
9. `insightbloom-stats` recalcula agregados y relevancia.
10. `insightbloom-query` expone nubes separadas de dudas y temas.
11. El frontend consulta y representa la nube correspondiente.
12. El moderador entra al dashboard y revisa palabras o mensajes.
13. El moderador puede censurar, restaurar o editar manualmente.
14. El conferencista selecciona una palabra.
15. El frontend consulta el timeline de esa palabra.
16. El timeline muestra mensajes en orden cronologico: primero el primer
    mensaje que llego y continua hasta el ultimo.

## Restricciones

- Evitar mover logica de agregacion importante al frontend.
- Evitar mover censura e intencion al frontend; esas reglas deben vivir en
  backend para mantener consistencia.
- La censura manual desde dashboard debe impactar la consulta de nube y
  detalle sin requerir reprocesos manuales complejos.
- El sistema debe aceptar tanto webhook como API REST como entradas validas
  del PoC.
- Respetar ownership de datos por microservicio; no compartir una misma base
  SQLite entre servicios.
- La persistencia efimera del PoC no debe asumirse como almacenamiento
  historico ni como fuente duradera.
- Mantener separacion clara entre comandos entrantes, modelo de dominio y
  representacion visual.
- Evitar introducir frameworks backend adicionales sin necesidad clara.

## Riesgos

- Riesgo actual:
  la topologia de cinco microservicios puede ser mas compleja de operar que
  el valor que aporta en el PoC.
  Impacto:
  aumenta el costo de arranque, coordinacion y despliegue.
  Mitigacion:
  mantener contratos HTTP pequenos y ownership bien delimitado.
- Riesgo actual:
  la fusion singular/plural puede generar una forma canonica equivocada en
  algunos casos.
  Impacto:
  la nube puede mostrar una variante menos natural para la audiencia.
  Mitigacion:
  usar la variante con mayor frecuencia y permitir edicion manual desde
  moderacion.
- Riesgo actual:
  la medicion de intencion puede ser demasiado ambigua si no se define un
  esquema simple y observable para el PoC.
  Impacto:
  clasificaciones inconsistentes o poco utiles.
  Mitigacion:
  empezar con un conjunto pequeno de categorias y reglas explicitas.
- Riesgo actual:
  la censura automatica no detectara ironia, dobles sentidos o contexto
  cultural con suficiente precision.
  Impacto:
  pueden aparecer terminos inapropiados o engañosos en la nube.
  Mitigacion:
  incorporar dashboard de moderacion con censura manual y trazabilidad.
