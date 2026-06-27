# ARCHITECTURE.md

Describe la arquitectura actual del proyecto.

## Vision general

El sistema se divide en un frontend web para visualizacion interactiva y
un conjunto de microservicios HTTP para recepcion, consulta, moderacion,
usuarios y estadisticas. Los servicios reciben mensajes estructurados a
partir de comandos como `/duda` y `/tema` ya parseados, normalizan la
palabra clave, conservan el detalle descriptivo y permiten operar una
moderacion mixta: automatica y manual. El frontend consulta los datos
agregados para renderizar nubes separadas de dudas y temas y permite abrir
una vista de detalle por palabra en formato timeline cronologico.

La responsabilidad de agregacion, ordenamiento, seguridad y moderacion vive
en backend. La responsabilidad de visualizacion, navegacion y animacion vive
en frontend. Para el PoC, cada microservicio tiene su propia base SQLite
efimera. Los microservicios backend implementan arquitectura hexagonal para
separar dominio, casos de uso y adaptadores.

## Modulos principales

- Modulo frontend web:
  renderiza la nube de palabras con D3.js, administra rutas con Vue Router,
  presenta animaciones, obtiene el identificador de dispositivo con
  ThumbmarkJS y muestra el timeline de detalles al seleccionar una palabra.
  Incluye una pantalla introductoria con mapa Leaflet+OpenStreetMap animado
  que muestra la ubicacion geografica de la conferencia antes de presentar
  la nube de palabras.
- Modulo dashboard de moderacion:
  permite revisar palabras y mensajes capturados, ver estado de censura e
  intencion estimada y aplicar censura manual sobre casos no detectados por
  la barrera automatica.
- Microservicio `insightbloom-ingest`:
  recibe eventos ya parseados por webhook o API REST, valida tokens y
  persiste mensajes canonicos con metadatos del emisor y del dispositivo.
- Microservicio `insightbloom-query`:
  expone nubes separadas de dudas y temas y consulta timelines por palabra.
  Recibe actualizaciones de visibilidad desde `moderation` via endpoints
  internos.
- Microservicio `insightbloom-moderation`:
  expone listas de palabras y mensajes para revision, aplica censura manual,
  restauracion y edicion de palabras o mensajes. Propaga los cambios de
  visibilidad a `insightbloom-query` por HTTP sincrono.
- Microservicio `insightbloom-users`:
  gestiona usuarios registrados, invitados o nuevos, emite o valida tokens y
  administra conferencias (nombre, UUID, identificador amigable, expiracion
  y coordenadas geograficas).
- Microservicio `insightbloom-stats`:
  calcula agregados y relevancia para enriquecer la nube de palabras.

## Distribucion de paquetes Java

Todos los microservicios backend siguen el mismo patron de paquetes raiz:

```
dev.rafex.insightbloom.{servicio}
```

Donde `{servicio}` es `users`, `ingest`, `query`, `moderation` o `stats`.

### Capas y sub-paquetes

```
dev.rafex.insightbloom.{servicio}
├── domain
│   ├── model          Entidades, value objects y enums del dominio.
│   │                  No depende de HTTP, SQLite ni ninguna libreria externa.
│   ├── ports          Interfaces que el dominio necesita del exterior:
│   │                  repositorios (Repository), clientes de otros servicios
│   │                  (Port). Definidas como interfaces Java puras.
│   └── services       Logica de dominio que no pertenece a una sola entidad:
│                      AutoCensureService, WordNormalizationService,
│                      IntentClassificationService, RelevanceService.
├── application
│   └── usecases       Orquestacion de casos de uso. Cada clase implementa
│                      un caso de uso concreto usando puertos del dominio.
│                      Recibe sus dependencias por constructor.
│                      Ejemplos: IngestMessageUseCase, CensorWordUseCase,
│                      SetMessageVisibilityUseCase, CreateConferenceUseCase.
├── adapters
│   ├── inbound
│   │   └── http
│   │       └── handlers  Handlers HTTP que implementan la interfaz de Jetty.
│   │                     BaseHandler centraliza parseo de body, escritura de
│   │                     respuesta JSON, errores y helpers comunes.
│   │                     Cada handler concreto atiende un grupo de rutas.
│   └── outbound
│       ├── sqlite         Implementaciones de repositorios sobre SQLite JDBC.
│       │                  Cada clase implementa un puerto de dominio.
│       │                  DatabaseManager gestiona el pool de conexiones y
│       │                  las migraciones de esquema al arrancar.
│       ├── {servicio}client  Clientes HTTP salientes hacia otros servicios.
│       │                     Implementan un puerto de dominio.
│       │                     Nombre del sub-paquete segun el servicio destino:
│       │                     usersclient, moderationclient, queryclient,
│       │                     statsclient.
│       └── queryclient    Adaptador de salida exclusivo para propagar
│                          visibilidad desde moderation hacia query.
└── bootstrap              Clase main que instancia y conecta todos los
                           componentes (wiring manual sin inyeccion de
                           dependencias externa) y arranca el servidor HTTP.
```

### Patron de puertos y adaptadores

Los puertos de dominio (`domain/ports`) son interfaces Java puras. Nunca
importan clases de infraestructura. Los adaptadores de salida
(`adapters/outbound`) los implementan con SQLite o HTTP. Los casos de uso
(`application/usecases`) solo conocen los puertos, nunca los adaptadores
concretos.

```
UseCase → Puerto (interfaz) ← Adaptador (implementacion)
            ^
            |
         domain/ports
```

### Paquetes por servicio

#### `insightbloom-users` — Puerto 8081

```
users/
  domain/model/        User, GuestUser, Token, Conference, ConferenceStatus
  domain/ports/        UserRepository, GuestUserRepository, TokenRepository,
                       ConferenceRepository
  domain/services/     FriendlyIdService, TokenService
  application/usecases/ LoginUseCase, CreateGuestUseCase, ValidateTokenUseCase,
                        CreateConferenceUseCase, GetConferenceUseCase
  adapters/inbound/http/handlers/
                       AuthHandler, ConferenceHandler, HealthHandler, BaseHandler
  adapters/outbound/sqlite/
                       DatabaseManager,
                       SqliteUserRepository, SqliteGuestUserRepository,
                       SqliteTokenRepository, SqliteConferenceRepository
  bootstrap/           UsersApplication
```

`Conference` incluye: `uuid`, `friendlyId`, `name`, `createdByUserUuid`,
`status`, `createdAt`, `updatedAt`, `expiresAt` (nullable), `latitude`
(nullable), `longitude` (nullable).

#### `insightbloom-ingest` — Puerto 8082

```
ingest/
  domain/model/        Message, MessageType, SourceType, AuthorKind
  domain/ports/        MessageRepository,
                       UsersPort, ModerationPort, StatsPort, QueryPort
  domain/services/     IntentClassificationService, WordNormalizationService
  application/usecases/ IngestMessageUseCase, GetMessageUseCase
  adapters/inbound/http/handlers/
                       IngestHandler, HealthHandler, BaseHandler
  adapters/outbound/sqlite/
                       DatabaseManager, SqliteMessageRepository
  adapters/outbound/usersclient/
                       HttpUsersPort
  adapters/outbound/moderationclient/
                       HttpModerationPort
  adapters/outbound/statsclient/
                       HttpStatsPort
  adapters/outbound/queryclient/
                       HttpQueryPort
  bootstrap/           IngestApplication
```

Variables de entorno: `USERS_URL`, `MODERATION_URL`, `STATS_URL`,
`QUERY_URL`.

#### `insightbloom-query` — Puerto 8083

```
query/
  domain/model/        CloudWord, WordTimeline
  domain/ports/        CloudWordRepository, WordTimelineRepository
  application/usecases/ GetCloudUseCase, GetTimelineUseCase,
                        UpdateCloudUseCase,
                        SetVisibilityUseCase,        ← visibilidad de palabra
                        SetMessageVisibilityUseCase  ← visibilidad de mensaje
  adapters/inbound/http/handlers/
                       CloudHandler, TimelineHandler, UpdateHandler,
                       VisibilityHandler,        POST /internal/visibility
                       MessageVisibilityHandler, POST /internal/message-visibility
                       HealthHandler, BaseHandler
  adapters/outbound/sqlite/
                       DatabaseManager,
                       SqliteCloudWordRepository, SqliteWordTimelineRepository
  bootstrap/           QueryApplication
```

Los endpoints `/internal/*` son exclusivamente para llamadas inter-servicio
desde `insightbloom-moderation`. No se exponen al frontend.

#### `insightbloom-moderation` — Puerto 8084

```
moderation/
  domain/model/        ModerationWord, ModerationMessage, BlockedTerm
  domain/ports/        ModerationWordRepository, ModerationMessageRepository,
                       BlockedTermRepository,
                       QueryPort  ← propaga visibilidad a insightbloom-query
  domain/services/     AutoCensureService
  application/usecases/ EvaluateCensureUseCase, ListModerationUseCase,
                        CensorWordUseCase, RestoreWordUseCase, EditWordUseCase,
                        CensorMessageUseCase, RestoreMessageUseCase,
                        EditMessageUseCase
  adapters/inbound/http/handlers/
                       ModerationWordHandler, ModerationMessageHandler,
                       InternalEvaluateHandler,
                       HealthHandler, BaseHandler
  adapters/outbound/sqlite/
                       DatabaseManager,
                       SqliteBlockedTermRepository,
                       SqliteModerationWordRepository,
                       SqliteModerationMessageRepository
  adapters/outbound/queryclient/
                       HttpQueryPort  ← implementa QueryPort via HTTP
  bootstrap/           ModerationApplication
```

`CensorMessageUseCase` y `RestoreMessageUseCase` llaman a
`QueryPort.setMessageVisibility` (best-effort) despues de persistir la
decision. `CensorWordUseCase` y `RestoreWordUseCase` llaman a
`QueryPort.setWordVisibility`.

Variable de entorno: `QUERY_SERVICE_URL`.

#### `insightbloom-stats` — Puerto 8085

```
stats/
  domain/model/        WordStats
  domain/ports/        WordStatsRepository
  domain/services/     RelevanceService
  application/usecases/ GetStatsUseCase, RecalcStatsUseCase
  adapters/inbound/http/handlers/
                       StatsHandler, RecalcHandler, HealthHandler, BaseHandler
  adapters/outbound/sqlite/
                       DatabaseManager, SqliteWordStatsRepository
  bootstrap/           StatsApplication
```

### Wiring en bootstrap

Cada `*Application.java` instancia y conecta manualmente todos los
componentes. No hay framework de inyeccion de dependencias. El orden de
instanciacion es:

```
DatabaseManager → Repositorios → Puertos de salida (clientes HTTP)
    → Servicios de dominio → Casos de uso → Handlers → HttpServer → start()
```

Ejemplo simplificado de `ModerationApplication`:

```
db → blockedTermRepo, wordRepo, messageRepo
queryPort = HttpQueryPort(queryUrl)        // adaptador de salida
autoCensureService = AutoCensureService(blockedTermRepo)
censorMessageUseCase = CensorMessageUseCase(messageRepo, queryPort)
messageHandler = ModerationMessageHandler(..., censorMessageUseCase, ...)
server = HttpServer(port, ..., messageHandler, ...)
server.start()
```

## Estructura base de un microservicio backend

```text
backend/services/insightbloom-{servicio}/
  pom.xml
  src/
    main/java/dev/rafex/insightbloom/{servicio}/
      domain/
        model/
        ports/
        services/
      application/
        usecases/
      adapters/
        inbound/http/handlers/
        outbound/
          sqlite/
          {destino}client/   uno por cada servicio al que llama
      bootstrap/
    test/java/dev/rafex/insightbloom/{servicio}/
      domain/services/
      application/usecases/
```

## Estructura base del frontend

```text
frontend/web/
  package.json
  vite.config.js        proxy de desarrollo: /api/{servicio} → localhost:{puerto}
  src/
    main.js
    app/
      router/           rutas y guards
      layout/           AppHeader y shells
    pages/
      landing/
      login/
      conference/       ConferencePage (intro map + tabs + router-view)
      dashboard/        NewConferencePage, ModerationWordsPage,
                        ModerationMessagesPage
    components/
      cloud/            WordCloud
      map/              ConferenceMap (preview), ConferenceIntroMap (intro)
      tables/           ModerationTable
    services/
      api/              usersApi, ingestApi, queryApi, moderationApi
    features/
      auth/
```

## Estructura raiz del repositorio

```text
/
  README.md
  Justfile              task runner (delega en make y docker compose)
  Makefile              builder: compila, testea, produce artefactos
  pom.xml               parent POM raiz (hereda de ether-parent 9.5.5)
  frontend/
    web/                aplicacion Vue + Vite
  backend/
    contracts/
      insightbloom-contracts/   DTOs e interfaces compartidas
    services/
      insightbloom-users/
      insightbloom-ingest/
      insightbloom-query/
      insightbloom-moderation/
      insightbloom-stats/
    cli/
      insightbloom-cli/
  container/
    backend/java/
      Dockerfile        build multi-etapa Maven → JRE (ARG SERVICE)
    frontend/
      Dockerfile        build multi-etapa Vite → nginx
      nginx.conf        SPA + reverse proxy /api/{servicio}/
    compose.yml         orquestacion completa de los 6 servicios
  infra/
    docker/             Dockerfiles originales (referencia)
    compose/            compose local original
    helm/charts/
  scripts/
    build/
    run/
    sim/
  docs/
    ARCHITECTURE.md
    STACK.md
    SPEC.md
    PRODUCT.md
    ROADMAP.md
    specs/
```

- `container/`:
  configuracion Docker definitiva del proyecto.
  `backend/java/Dockerfile` acepta `--build-arg SERVICE=insightbloom-{name}`
  para producir la imagen de cualquier microservicio desde un unico
  Dockerfile parametrizado.
  `compose.yml` se ejecuta con
  `docker compose -f container/compose.yml up --build`
  desde la raiz del repositorio.

## Ownership de datos

- `insightbloom-users`:
  usuarios registrados, invitados, tokens, roles y conferencias
  (incluyendo coordenadas geograficas y tiempo de expiracion).
- `insightbloom-ingest`:
  mensajes canonicos recibidos, palabra original, normalizada,
  detalle original, detalle visible y metadatos de envio.
- `insightbloom-moderation`:
  decisiones de censura, estado actual de palabras y mensajes y
  valores editados vigentes.
- `insightbloom-query`:
  proyecciones de lectura para nubes (`cloud_words`) y timelines
  (`word_timeline`). Mantiene `is_visible` sincronizado con
  las decisiones de moderacion.
- `insightbloom-stats`:
  agregados de relevancia y metricas derivadas para ranking de palabras.

## Contratos entre servicios

```
insightbloom-users
  └─► insightbloom-ingest
        validacion de token, resolucion de usuario y conferencia

insightbloom-ingest
  ├─► insightbloom-moderation  (evaluacion de censura automatica)
  ├─► insightbloom-stats       (recalculo de agregados tras ingesta)
  └─► insightbloom-query       (actualizacion de proyecciones de nube)

insightbloom-moderation
  └─► insightbloom-query
        POST /internal/visibility          censura/restauracion de palabra
        POST /internal/message-visibility  censura/restauracion de mensaje
        (ambas llamadas son best-effort / fire-and-forget)
```

Comunicacion: HTTP sincrono entre servicios. Sin broker de eventos en el PoC.

## Flujo principal

1. El organizador autenticado crea una conferencia con nombre, UUID interno,
   identificador amigable, tiempo de expiracion opcional y coordenadas
   geograficas opcionales.
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
11. El frontend muestra la pantalla introductoria con el mapa animado
    (si la conferencia tiene coordenadas); al hacer clic en el pin,
    presenta la nube de palabras.
12. El moderador entra al dashboard y revisa palabras o mensajes.
13. El moderador puede censurar, restaurar o editar manualmente.
14. La censura propaga `is_visible = false` a `insightbloom-query`
    via `/internal/visibility` o `/internal/message-visibility`.
15. El conferencista selecciona una palabra.
16. El frontend consulta el timeline de esa palabra.
17. El timeline muestra mensajes en orden cronologico.

## Restricciones

- Evitar mover logica de agregacion importante al frontend.
- Evitar mover censura e intencion al frontend; esas reglas deben vivir en
  backend para mantener consistencia.
- La censura manual desde dashboard debe impactar la consulta de nube y
  detalle sin requerir reprocesos manuales complejos.
- El sistema debe aceptar tanto webhook como API REST como entradas validas.
- Respetar ownership de datos por microservicio; no compartir SQLite.
- La persistencia efimera del PoC no se asume como almacenamiento historico.
- Mantener separacion clara entre comandos entrantes, modelo de dominio y
  representacion visual.
- Los endpoints `/internal/*` de `insightbloom-query` son solo para
  comunicacion inter-servicio y no deben exponerse al frontend ni a
  usuarios externos.

## Riesgos

- La topologia de cinco microservicios puede ser mas compleja de operar que
  el valor que aporta en el PoC. Mitigacion: contratos HTTP pequenos y
  ownership bien delimitado.
- La fusion singular/plural puede generar una forma canonica equivocada.
  Mitigacion: usar la variante con mayor frecuencia y permitir edicion manual.
- La medicion de intencion puede ser demasiado ambigua. Mitigacion: conjunto
  pequeno de categorias con reglas explicitas.
- La censura automatica no detectara ironia ni contexto cultural. Mitigacion:
  dashboard de moderacion con censura manual y trazabilidad.
