# DECISIONS.md

Registro de decisiones persistentes del proyecto.

## Cuando registrar aqui

Registrar una decision cuando cambie:

- la arquitectura
- una convencion importante
- una tecnologia base
- un tradeoff que otros agentes deben respetar

## Formato sugerido

### DEC-0001 - Titulo de la decision

- Fecha: YYYY-MM-DD
- Estado: proposed | accepted | deprecated | replaced
- Contexto: que problema obligo la decision
- Decision: que se decidio exactamente
- Consecuencias: costos, beneficios y limites
- Reemplaza: DEC-XXXX o `none`

### DEC-0001 - SQLite como persistencia inicial del PoC

- Fecha: 2026-04-01
- Estado: accepted
- Contexto:
  el sistema se construira como un conjunto de microservicios con base de
  datos propia. Para el PoC hacia falta elegir una opcion embebida y simple
  que permitiera persistir eventos de chat, agregarlos por palabra y
  consultarlos desde endpoints HTTP sin incorporar complejidad innecesaria.
- Decision:
  usar SQLite como persistencia inicial del PoC. Cada microservicio mantiene
  ownership de su propia base de datos SQLite. No se adopta H2, DuckDB,
  RocksDB ni MapDB en esta etapa.
- Consecuencias:
  se simplifica la puesta en marcha, el modelo de consultas agregadas y la
  evolucion posterior hacia PostgreSQL;
  se acepta la limitacion de concurrencia de escritura de SQLite como
  suficiente para el escenario esperado de una conferencia o demo;
  la informacion puede perderse al reiniciar los contenedores porque la
  persistencia del PoC es efimera;
  si el volumen real de escrituras concurrentes supera lo previsto, esta
  decision debera revisarse.
- Reemplaza: `none`

### DEC-0002 - Microservicios separados desde el dia uno

- Fecha: 2026-04-01
- Estado: accepted
- Contexto:
  el PoC necesita cubrir ingestion, consulta, moderacion, usuarios y
  estadisticas con responsabilidades claras, integracion por webhook y REST
  y ownership de datos por servicio.
- Decision:
  iniciar el sistema con cinco microservicios separados:
  `insightbloom-ingest`, `insightbloom-query`, `insightbloom-moderation`,
  `insightbloom-users` e `insightbloom-stats`.
- Consecuencias:
  la separacion de responsabilidades queda clara desde el inicio y alinea el
  PoC con la direccion futura del sistema;
  aumenta la complejidad de arranque, despliegue y coordinacion entre
  servicios;
  obliga a mantener contratos HTTP pequenos y ownership bien definido.
- Reemplaza: `none`

### DEC-0003 - Identificadores externos con UUID y tablas internas con serial

- Fecha: 2026-04-01
- Estado: accepted
- Contexto:
  el sistema necesita exponer identificadores estables entre peticiones y
  microservicios, pero tambien simplificar relaciones y rendimiento en las
  tablas SQLite internas del PoC.
- Decision:
  usar UUID para identificadores externos compartidos entre APIs y
  microservicios. Las tablas internas usaran ids seriales incrementales.
- Consecuencias:
  mejora la estabilidad del contrato externo y evita exponer ids internos;
  simplifica joins y almacenamiento interno;
  obliga a mantener ambos identificadores en las entidades que crucen
  fronteras de servicio.
- Reemplaza: `none`

### DEC-0004 - RelevanceScore visible y ponderado

- Fecha: 2026-04-01
- Estado: accepted
- Contexto:
  la nube necesita una regla observable para que otro agente pueda
  implementarla sin inventar el ranking de palabras.
- Decision:
  usar `relevanceScore = visibleCount * typeWeight * averageIntentWeight`,
  con pesos de tipo e intencion definidos en `SPEC.md`, calculado solo sobre
  mensajes visibles.
- Consecuencias:
  la implementacion del ranking queda cerrada para el PoC;
  el sistema puede explicar por que una palabra crece o baja;
  la formula es simple y probablemente deba revisarse tras validar uso real.
- Reemplaza: `none`

### DEC-0005 - FriendlyId derivado del nombre con slug estable

- Fecha: 2026-04-01
- Estado: accepted
- Contexto:
  la conferencia necesita un acceso rapido tipo Kahoot y hacia falta definir
  una regla estable para generar el identificador amigable.
- Decision:
  generar `friendlyId` automaticamente desde el nombre usando slug en
  minusculas, sin acentos, con guiones, longitud de 4 a 24 y sufijo
  incremental en caso de colision.
- Consecuencias:
  el acceso rapido queda predecible y facil de compartir;
  se evita depender de edicion manual para resolver colisiones;
  el identificador queda estable despues de creado.
- Reemplaza: `none`

### DEC-0006 - Makefile como builder y Justfile como task runner

- Fecha: 2026-04-08
- Estado: accepted
- Contexto:
  el proyecto tenia un Makefile y un Justfile con responsabilidades solapadas.
  Ambos archivos duplicaban targets y no quedaba claro donde agregar un nuevo
  comando de construccion versus uno de orquestacion.
- Decision:
  el Makefile es el unico responsable de producir artefactos: compila,
  testea, lintea y construye. El Justfile es el task runner que orquesta
  flujos de desarrollo; cuando necesita compilar delega en `make`.
  `just dev` llama a `make build` antes de arrancar los procesos.
  `just ci` llama a `make build && make test && make lint`.
- Consecuencias:
  queda claro donde poner cada tipo de comando;
  el Justfile no compila por cuenta propia;
  scripts de construccion viven en `scripts/build/` y scripts de ejecucion
  en `scripts/run/`, sin mezclar responsabilidades.
- Reemplaza: `none`

### DEC-0007 - Sincronizacion de visibilidad de palabras entre moderation y query

- Fecha: 2026-04-08
- Estado: accepted
- Contexto:
  moderation y query son microservicios con bases de datos SQLite separadas.
  Al censurar o restaurar una palabra en moderation, la nube de palabras de
  query seguia mostrando la palabra porque `cloud_words.is_visible` no se
  actualizaba.
- Decision:
  el servicio moderation llama a `POST /internal/visibility` del servicio
  query despues de cada operacion de censura o restauracion de palabra.
  La llamada es best-effort: si falla, la operacion de moderacion no se
  revierte. El endpoint interno no requiere token de usuario.
- Consecuencias:
  la nube refleja el estado de moderacion en tiempo real sin compartir base
  de datos entre servicios;
  si el servicio query no esta disponible en el momento de la llamada, la
  visibilidad quedara desincronizada hasta la proxima operacion;
  la URL del query service se configura con la variable de entorno
  `QUERY_URL` (default `http://localhost:8083`).
- Reemplaza: `none`

### DEC-0008 - Adopcion de Ether 9.5.5 como BOM y framework base del backend Java

- Fecha: 2026-04-09
- Estado: accepted
- Contexto:
  el backend de InsightBloom manejaba versiones de Jetty 12 y Jackson
  directamente en el parent POM. Con la publicacion de Ether 9.5.5 el
  ecosistema paso a ofrecer un BOM oficial (`ether-parent`) que gestiona esas
  versiones, ademas de modulos de alto nivel para HTTP, JSON, JWT, crypto,
  JDBC y cliente HTTP saliente.
- Decision:
  `insightbloom-parent` hereda de `dev.rafex.ether.parent:ether-parent:9.5.5`.
  Las versiones de Jetty 12 y Jackson se eliminan del parent propio y quedan
  bajo gestion de ether-parent. El parent de InsightBloom anade en
  `dependencyManagement` los 17 modulos Ether disponibles para que cada
  servicio solo declare el artifactId sin version. El `pluginManagement`
  centraliza los 13 plugins Maven del ecosistema con versiones en propiedades.
  Los servicios usan `ether-http-jetty12` (servidor), `ether-json` (JSON),
  `ether-jdbc` + `ether-database-core` (base de datos), `ether-http-client`
  (llamadas salientes), `ether-jwt` y `ether-crypto` donde aplica. El CLI
  agrega `ether-crypto` para el hashing de contrasenas.
- Consecuencias:
  un solo lugar controla todas las versiones de dependencias y plugins;
  actualizar Ether es cambiar un unico numero de version en el parent;
  el catalogo oficial de modulos esta en https://ether.rafex.io/;
  la version minima de Java soportada por Ether es 21; InsightBloom usa 25.
- Reemplaza: `none`

### DEC-0009 - Login con password obligatorio y provisión administrativa por CLI

- Fecha: 2026-04-25
- Estado: accepted
- Contexto:
  el PoC permitía autenticar usuarios seed sin `password_hash`, lo que abría
  una puerta insegura en entornos locales y remotos. Además, se requería
  evitar endpoints de administración para alta de usuarios en operación.
- Decision:
  el login de `ORGANIZER` y `MODERATOR` exige siempre `username` + `password`
  válidos contra `password_hash`. Se elimina el seed automático de `admin`
  sin contraseña. La provisión de usuarios administrativos se realiza
  únicamente con `insightbloom-cli create-user` (local, Docker o K3s).
- Consecuencias:
  mejora la seguridad básica del flujo de autenticación;
  se requiere un paso operativo explícito de bootstrap de usuarios antes de
  iniciar sesión por primera vez;
  scripts de simulación y demo deben recibir `ADMIN_PASS`;
  la documentación operativa incorpora el mecanismo para K3s sin exponer
  endpoints de administración.
- Reemplaza: `none`

### DEC-0010 - SQLite en modo WAL para concurrencia de escrituras

- Fecha: 2026-04-25
- Estado: accepted
- Contexto:
  con múltiples microservicios y operaciones concurrentes sobre SQLite, el
  modo de journal por defecto aumenta la probabilidad de bloqueos de
  escritura en escenarios locales y de demo.
- Decision:
  configurar `PRAGMA journal_mode=WAL` en cada conexión SQLite de los
  servicios backend y del CLI administrativo. Se añade además
  `PRAGMA busy_timeout=5000` para esperar locks transitorios.
- Consecuencias:
  mejora la convivencia entre lecturas y escrituras concurrentes;
  reduce errores intermitentes tipo "database is locked" bajo carga;
  mantiene SQLite como persistencia del PoC sin introducir infraestructura
  adicional.
- Reemplaza: `none`

### DEC-0011 - Rol ADMIN y soporte multi-rol en usuarios

- Fecha: 2026-06-27
- Estado: accepted
- Contexto:
  la plataforma necesitaba un rol de administracion del sistema separado del
  organizador de conferencias. Ademas, un mismo usuario podia necesitar ser
  administrador del sistema y organizador de conferencias simultaneamente.
- Decision:
  crear el rol `ADMIN` que hereda todas las capacidades de `ORGANIZER` y
  anade gestion de usuarios (listar, editar, banear, restaurar). El campo
  `role` en la tabla `users` pasa a llamarse `roles` y acepta multiples
  roles separados por coma (ej: `ORGANIZER,ADMIN`). Los endpoints de admin
  (`/admin/users`) requieren rol `ADMIN`.
- Consecuencias:
  separacion clara entre gestion de plataforma y gestion de conferencias;
  un mismo usuario puede operar en ambos contextos sin necesidad de cuentas
  separadas;
  el CLI debe aceptar `--role` con valores multiples;
  los tests de auth deben verificar multi-rol en el token JWT.
- Reemplaza: `none`

### DEC-0012 - Presentations como microservicio Node.js independiente

- Fecha: 2026-06-27
- Estado: accepted
- Contexto:
  la plataforma necesitaba soporte para subir y visualizar presentaciones de
  slides durante conferencias. La herramienta elegida fue Marp (Markdown →
  HTML). Marp CLI es un paquete npm sin equivalente Java maduro, y embeberlo
  en el frontend o en un servicio Java añadiria complejidad innecesaria.
- Decision:
  `insightbloom-presentations` se implementa como un microservicio
  Node.js/Express independiente (no Java, no arquitectura hexagonal).
  Usa `@marp-team/marp-cli` para conversion, `multer` para upload multipart,
  `adm-zip` para extraer archivos, `cheerio` para parsear slides HTML.
  No usa base de datos — los archivos se almacenan en volumen Docker.
  Se despliega en puerto 8091.
- Consecuencias:
  se introduce Node.js como runtime adicional en el stack backend;
  el Dockerfile es independiente (no usa el Dockerfile Java parametrizado);
  la ausencia de base de datos simplifica el despliegue pero limita
  funcionalidades futuras (historial, busqueda de slides);
  el build de CI no incluye este servicio actualmente.
- Reemplaza: `none`

### DEC-0013 - OTP dual channel: Twilio SMS + Zoho email

- Fecha: 2026-06-27
- Estado: accepted
- Contexto:
  se requeria verificacion de identidad de usuarios mas alla del
  username/password. El sistema debia soportar al menos un canal de OTP,
  pero Twilio tiene costos por SMS y Zoho SMTP permite email gratuito.
- Decision:
  implementar ambos canales como opcionales: Twilio SMS (via API REST) y
  Zoho email (via SMTP). Si ninguno esta configurado, el OTP queda
  deshabilitado sin bloquear el flujo de autenticacion. Las credenciales
  se inyectan via variables de entorno y Kubernetes secrets, nunca en el
  codigo ni en archivos de configuracion.
- Consecuencias:
  el organizador puede elegir el canal segun su presupuesto y audiencia;
  si ambos canales fallan o no estan configurados, el sistema funciona en
  modo sin OTP (degradacion graceful);
  añade dependencia operativa de dos servicios externos.
- Reemplaza: `none`

### DEC-0014 - LLM para generacion de preguntas en survey

- Fecha: 2026-06-27
- Estado: accepted
- Contexto:
  el servicio de encuestas necesitaba generar preguntas contextuales para
  cada conferencia. Escribirlas manualmente no escalaba. El sistema ya tenia
  integracion con LLM provider via el chat (bot Roberto).
- Decision:
  `insightbloom-survey` puede usar el mismo LLM provider (DeepSeek via API
  OpenAI-compatible) para generacion de preguntas. La funcionalidad es
  opcional y se activa via variables de entorno (`LLM_PROVIDER_BASE_URL`,
  `LLM_PROVIDER_MODEL`, `LLM_PROVIDER_API_KEY`). La generacion de preguntas
  es on-demand, no automatica.
- Consecuencias:
  reutiliza la misma infraestructura de LLM del chat;
  el survey funciona sin LLM (preguntas manuales) si no esta configurado;
  añade latencia y costo por llamada al generar preguntas;
  la calidad de las preguntas depende del modelo configurado.
- Reemplaza: `none`

### DEC-0015 - Migracion a SpecNative Development v0.7

- Fecha: 2026-06-26
- Estado: accepted
- Contexto:
  el proyecto supero la fase PoC y necesitaba una base documental escalable
  para desarrollo multi-agente. La documentacion vivia en `docs/` con una
  estructura ad-hoc inspirada en SpecNative pero sin seguir el estandar.
- Decision:
  migrar toda la documentacion de contexto a `spec-native/` siguiendo el
  estandar SpecNative Development v0.7. Instalar el servidor MCP para
  soporte multi-agente. Mantener `docs/` como referencia legacy con aviso
  de deprecacion. Mantener `agents/` para artefactos operativos (SECURITY,
  DIAGNOSE). Adoptar `opencode.json` con prompts spec-*.
- Consecuencias:
  cualquier agente compatible con MCP puede retomar trabajo sin friccion;
  la estructura documental es validable y trazable;
  `docs/` queda deprecado pero se mantiene para no romper enlaces externos;
  la migracion es incremental — los documentos se actualizan progresivamente
  para reflejar el codigo real.
- Reemplaza: `none`

### DEC-0016 - Catalogo de tipos de evento administrado por ADMIN, gateado por capacidades

- Fecha: 2026-07-10
- Estado: proposed
- Contexto:
  con la llegada de boletos digitales, la plataforma puede soportar mas
  formatos de evento que solo "conferencia" (taller, standup, concierto,
  tocada...). Fijar estos tipos como un enum cerrado en codigo obligaria a
  un release por cada tipo nuevo, y el organizador no es quien deberia
  decidir que "tecnologias" trae cada tipo — esa es una decision de
  plataforma que le corresponde al rol `ADMIN` (DEC-0011).
- Decision:
  el catalogo de **capacidades** (boletos con aforo, mapa de asientos,
  encuestas, presentaciones, nube de palabras, chat, videollamada Jitsi,
  pizarra Excalidraw, diagramas drawio, notas Etherpad, y a futuro IDE de
  codigo) vive fijo en codigo como enum. El catalogo de **tipos de evento**
  (que combinacion de capacidades trae cada tipo) es administrado por
  `ADMIN` en runtime, persistido en una tabla `event_types` nueva.
  `Conference` gana una referencia (`event_type_key`) a un tipo del
  catalogo, con `"conference"` como valor por defecto sembrado y aplicado a
  todo evento existente (compatibilidad hacia atras automatica, mismo
  patron que `seating_mode`). Las rutas y la UI gatean por capacidad
  (`hasCapability(X)`), nunca comparando el tipo de evento por nombre.
- Consecuencias:
  agregar un tipo de evento nuevo (ej. "Concierto") es una operacion de
  `ADMIN` en runtime, sin release;
  agregar una capacidad nueva (ej. `CODE_IDE` cuando se definan sus reglas
  de seguridad) sigue siendo un cambio de codigo acotado a un enum + su
  gate, sin tocar el modelo de `EventType`;
  toda conferencia existente sigue funcionando igual sin migracion manual;
  la ejecucion de codigo (capacidad `CODE_IDE`) queda explicitamente fuera
  de esta decision — se definira por separado cuando el usuario fije las
  reglas de seguridad de ejecucion.
- Reemplaza: `none`

### DEC-0017 - Colaboracion en vivo (Jitsi, Excalidraw, drawio, Etherpad) autoalojada en K3s, con Jitsi dual

- Fecha: 2026-07-10
- Estado: proposed
- Contexto:
  eventos remotos o con transmision necesitan videollamada, y talleres o
  standups se benefician de pizarra, diagramas y notas compartidas en vivo.
  El proyecto ya opera su propio K3s con Helm para todos sus servicios y
  prefiere no depender de SaaS de pago para herramientas que tienen
  alternativa open source auto-alojable.
- Decision:
  Excalidraw, drawio y Etherpad se despliegan como instancias propias en el
  K3s del proyecto, cada una con su propio Helm chart. Jitsi se soporta en
  dos modalidades simultaneas y seleccionables por el organizador: la
  instancia publica `meet.jit.si` (cero infraestructura, sujeta a limites
  de terceros) y una instancia propia self-hosted en el mismo K3s (Helm
  chart de Jitsi Meet). El nombre de sala/pad de cada integracion se deriva
  deterministicamente del `uuid` del evento, sin necesidad de coordinacion
  manual entre asistentes.
- Consecuencias:
  el proyecto gana 4 piezas de infraestructura nuevas para operar y
  actualizar (mas Helm charts, mas pods, mas superficie de monitoreo);
  el organizador puede elegir Jitsi publico para una prueba rapida sin
  esperar infraestructura propia, o self-hosted cuando necesite mas control
  o volumen sin los limites de `meet.jit.si`;
  ninguna de estas integraciones persiste contenido en la base de datos de
  InsightBloom en su primera version (drawio no guarda el diagrama, Jitsi
  no graba) — ver Excludes del spec para el detalle;
  las credenciales (API key de Etherpad, si se agrega JWT de Jitsi
  self-hosted mas adelante) se inyectan por variable de entorno y
  Kubernetes secret, nunca versionadas (mismo criterio que DEC-0013).
- Reemplaza: `none`

### DEC-0018 - SurveyJS como motor de encuestas alternativo, no reemplazo

- Fecha: 2026-07-10
- Estado: proposed
- Contexto:
  el motor de encuestas propio (`insightbloom-survey`) cubre tipos de
  pregunta basicos y calificacion LLM opcional (DEC-0014). SurveyJS ofrece
  un editor visual (drag-and-drop) y un modelo de pregunta mas amplio, util
  para organizadores que quieren armar encuestas complejas sin depender del
  editor propio.
- Decision:
  el organizador elige, por encuesta, un `engine` (`NATIVE` o `SURVEYJS`),
  fijo despues de creada. Ambos motores viven dentro de
  `insightbloom-survey` y comparten la misma asociacion evento/asistente y
  el mismo dashboard de resultados; una encuesta `SURVEYJS` persiste su
  definicion como el JSON schema nativo de `survey-core`. **Pendiente**:
  confirmar antes de implementar (TASK-0050) si `survey-creator-*` (el
  editor visual) se puede usar en produccion sin costo ni marca de agua
  bajo la licencia vigente de SurveyJS; si no, la primera version solo
  ofrece el render (`survey-core`/`survey-js-ui`) sobre un schema
  editado/importado a mano, sin editor visual.
- Consecuencias:
  el organizador gana una alternativa mas rica para encuestas complejas sin
  perder el motor propio ya construido;
  se duplica el esfuerzo de mantenimiento entre dos motores de encuesta;
  la calificacion automatica con LLM (DEC-0014) sigue disponible solo para
  `NATIVE` en esta primera version;
  el estado `proposed` debe pasar a `accepted` una vez resuelto el punto de
  licencia pendiente.
- Reemplaza: `none`

### DEC-0019 - seatmap-canvas como motor alternativo de mapa de asientos, no reemplazo

- Fecha: 2026-07-10
- Estado: proposed
- Contexto:
  el editor de mapa de asientos de la Fase 2 de ticketing (`FREEFORM`,
  marcadores libres sobre una imagen) sirve para recintos sin layout fijo,
  pero no representa bien un recinto con asientos numerados reales (teatro,
  auditorio, sala de cine), donde el organizador necesita definir filas y
  secciones, no solo puntos sueltos.
- Decision:
  para `TICKETING_SEATED`, el organizador elige un `venueMapEngine`:
  `FREEFORM` (existente, sigue siendo el default) o `SEATMAP_CANVAS`,
  basado en la libreria `alisaitteke/seatmap-canvas` (MIT), que modela
  filas/secciones/butacas numeradas. Ambos motores comparten el mismo
  mecanismo de reserva y concurrencia ya construido
  (`ReserveSeatUseCase` + `UNIQUE(conference_uuid, seat_uuid)`); solo
  cambia como se define y renderiza el layout, no como se reserva un
  asiento. **Pendiente**: confirmar antes de implementar (TASK-0060) que
  `seatmap-canvas` sigue mantenido y es viable integrar en el stack Vue 3
  actual (es una libreria canvas vanilla-JS, requiere un wrapper).
- Consecuencias:
  el organizador con un recinto real gana un editor mas fiel a la
  distribucion fisica del lugar, sin perder `FREEFORM` para casos sin
  layout fijo;
  se agrega una dependencia externa de un proyecto de mantenimiento no
  garantizado — si deja de ser viable, `FREEFORM` sigue siendo el camino
  soportado por defecto;
  el modelo de datos de `venue_seats` debe seguir siendo compatible con
  ambos motores sin duplicar la logica de reserva ya construida;
  el estado `proposed` debe pasar a `accepted` una vez confirmado el punto
  de mantenimiento/compatibilidad pendiente.
- Reemplaza: `none`

### DEC-0020 - Instancias compartidas (multi-tenant) para drawio/Etherpad/Jitsi/Excalidraw, con TTL de datos por evento

- Fecha: 2026-07-11
- Estado: accepted
- Contexto:
  antes de implementar los 4 Helm charts self-hosted de DEC-0017 (Jitsi,
  Excalidraw, drawio, Etherpad) habia que decidir el modelo de aislamiento:
  ¿un Deployment compartido para toda la plataforma, o un pod dedicado por
  evento, nacido bajo demanda y destruido al terminar? Las 4 herramientas
  ya soportan multiplexar varias salas/documentos dentro de un mismo
  proceso (room name o padID derivado del `uuid` del evento), asi que un
  pod por evento agregaria overhead operativo (mas pods, arranques en frio)
  sin ganar aislamiento real, ya que ninguna de las 4 necesita aislamiento
  de proceso por evento — solo aislamiento de *datos/sala*, que ya resuelven
  por diseño.
- Decision:
  cada una de las 4 herramientas se despliega como **un unico Deployment
  compartido** para toda la plataforma (no un pod por evento), multiplexando
  eventos por un identificador derivado del `uuid` del evento:
  - drawio: stateless total, no persiste nada del lado del servidor — no
    hay dato que aislar.
  - Etherpad: multiplexa `pads` por `padID = event.uuid` en una sola
    instancia.
  - Jitsi: multiplexa salas por nombre de sala derivado del `uuid` en una
    sola instancia (prosody/jicofo/JVB).
  - Excalidraw: el room-server (`excalidraw-room`) multiplexa salas en
    memoria dentro de un solo proceso.
  Cada Deployment compartido escala con un `HorizontalPodAutoscaler` propio
  (min/max replicas, disparado por CPU/memoria) — no hay scale-to-zero via
  KEDA, para no agregar una dependencia nueva al cluster solo por ahorrar un
  pod idle. El JVB de Jitsi es el mas sensible a este min/max porque procesa
  video en tiempo real.
  La efimeridad de los datos se resuelve a nivel de *dato*, no de pod: TTL =
  `event.eventDate + event.endTime + 1 hora` de margen. Etherpad necesita un
  job periodico (mismo patron de scheduler in-process que
  `SendConferenceRemindersUseCase`) que borre pads de eventos vencidos via su
  API HTTP. drawio y Jitsi no necesitan limpieza porque no persisten nada del
  lado del servidor. Excalidraw queda pendiente de verificar en su propia
  tarea de implementacion si `excalidraw-room` ya limpia salas inactivas o si
  hace falta agregarlo.
- Consecuencias:
  se reutiliza el mismo patron de Helm ya usado para NATS (Deployment +
  Service dedicados, fuera del loop generico de `.Values.services`, con su
  propio Ingress publico cuando la herramienta se embebe directamente en el
  navegador del asistente, como ya ocurre con chat/telegram);
  el costo operativo es mucho menor que un pod por evento (una sola imagen
  corriendo, autoscaling normal de Kubernetes, sin necesidad de un
  orquestador de ciclo de vida por evento);
  se introduce la necesidad de un job de limpieza de pads de Etherpad, que
  no existia antes en la plataforma — sigue el mismo patron de scheduler ya
  probado, sin infraestructura nueva;
  si alguna de las 4 herramientas resulta no soportar multiplexado
  confiable en produccion (por ejemplo, degradacion de Jitsi con muchas
  salas concurrentes en una sola instancia), se reevaluaria un modelo hibrido
  (pods dedicados solo para esa herramienta) como iteracion futura — no
  bloquea la implementacion actual.
- Reemplaza: `none`

### DEC-0021 - Esquema de permisos y roles administrables (plataforma + por evento)

- Fecha: 2026-07-11
- Estado: accepted
- Contexto:
  el modelo de roles plano (`UserRole`: ADMIN, ORGANIZER, MODERATOR,
  GUEST, ATTENDEE, un solo nivel global) no distingue entre quien
  administra usuarios y quien solo deberia administrar el catalogo de
  tipos de evento, ni permite que el creador de un evento delegue tareas
  (moderar, hacer check-in, presentar) a otra persona solo para ESE
  evento sin volverla organizador global. Esto ya se habia identificado
  y pospuesto deliberadamente en la Fase 1 de ticketing ("no se
  introduce un rol nuevo de staff en esta primera version").
- Decision:
  se generaliza en dos jerarquias independientes, siguiendo el mismo
  patron ya usado para `EventType` (DEC-0016): un catalogo fijo de
  **permisos** vive en codigo (`MANAGE_USERS`, `MANAGE_EVENT_TYPES`,
  `HOST_EVENT`, `MANAGE_EVENT_SETTINGS`, `ASSIGN_EVENT_ROLES`,
  `MODERATE_CONTENT`, `CHECK_IN`, `MANAGE_PRESENTATION`, `MANAGE_SURVEY`,
  `MANAGE_CERTIFICATE`, `VIDEO_MODERATE`), y el `ADMIN` administra
  **roles** como combinaciones configurables de esos permisos, con un
  alcance `PLATFORM` (global) o `EVENT` (por evento especifico). El
  creador de un evento recibe automaticamente el rol `host` para ese
  evento (con `ASSIGN_EVENT_ROLES`), y puede asignar otros roles de
  alcance `EVENT` (`moderator`, `checkin_staff`, `guest_presenter`,
  `survey_manager`) a otras personas solo para ese evento. Un usuario con
  el rol de plataforma `system_admin` tiene bypass total sobre cualquier
  evento sin necesidad de una fila explicita.
  La aplicacion real de `VIDEO_MODERATE` en la videollamada de Jitsi
  queda pospuesta hasta que exista Jitsi self-hosted con JWT (DEC-0017);
  `meet.jit.si` publico no permite asignar moderador de forma confiable
  via API. El dato queda listo en `event_roles` desde ahora.
- Consecuencias:
  el `ADMIN` puede crear roles nuevos (ej. "Coordinador de Staff")
  combinando permisos existentes sin requerir un release del backend;
  las rutas existentes migran gradualmente a chequeo por permiso
  especifico en vez de "es organizador dueño", sin romper su
  comportamiento actual para conferencias ya creadas (solo se migra una
  parte en esta iteracion, ver SPEC Excludes);
  `UserRole` (ADMIN/ORGANIZER/MODERATOR/GUEST/ATTENDEE) sigue existiendo
  sin cambios para compatibilidad — los roles de plataforma nuevos son
  adicionales, no un reemplazo;
  el permiso de administrar el catalogo de roles vive bajo el mismo
  `MANAGE_USERS` que usuarios (no un permiso dedicado), mismo nivel de
  confianza que ya se deposita en `ADMIN` hoy — se revisaria si hace
  falta separarlo en una iteracion futura.
- Reemplaza: `none`

### DEC-0022 - Proxy autenticador (insightbloom-tools-gateway) delante de drawio/Excalidraw/Etherpad

- Fecha: 2026-07-12
- Estado: accepted
- Contexto:
  drawio, Excalidraw y Etherpad se exponen via Ingress publico propio (DEC-0017/DEC-0020),
  sin autenticacion a nivel de esos servicios — cualquiera con la URL exacta (ej.
  `drawio-insightbloom.v1.rafex.cloud`) podia usarlos directamente, sin pasar por el login
  de InsightBloom ni por el gating de capacidades del frontend. Un chequeo solo en el
  frontend/backend de la app (como el ya existente para drawio via `getEventDiagram`) no
  cierra este hueco: no impide pegar la URL del servicio directamente en el navegador.
- Decision:
  nuevo servicio `insightbloom-tools-gateway` (Jetty 12 core `Handler` de bajo nivel +
  `java.net.http.HttpClient` del JDK para el reenvio — sin jetty-proxy/jetty-servlet, no
  compatibles en las versiones cacheadas para Jetty 12) que se interpone entre el Ingress
  publico y los pods reales de drawio/Excalidraw/Etherpad:
  - Rutea por el header `Host` (mismo host publico de siempre) hacia el Service interno
    correspondiente (`GATEWAY_ROUTES`, mapa host→target).
  - Exige sesion de InsightBloom antes de reenviar cualquier request: primer acceso trae
    `?ib_token=<token>` en la query (mismo patron ya usado para chat), el gateway lo valida
    contra `GET /api/v1/auth/validate` de insightbloom-users y, si es valido, emite una
    cookie de sesion propia (`ib_gw`, HttpOnly/Secure/SameSite=Lax, TTL 4h) cacheada en
    memoria — evita revalidar el token en cada sub-recurso (JS/CSS/XHR) del iframe.
    Sin token ni cookie valida: pagina HTML 401 "inicia sesion", sin tocar el pod real.
  - Los Ingress de drawio/Excalidraw/Etherpad (`ingressDrawio`/`ingressExcalidraw`/
    `ingressEtherpad`) ahora apuntan al Service del gateway, no al de la herramienta — los
    Services de las herramientas ya no tienen ingress publico (NetworkPolicy ajustada:
    se retiran las 3 reglas "allow-X-ingress" abiertas a internet y se agrega una sola
    "allow-toolsgateway-ingress"), asi que solo son alcanzables desde otros pods del
    namespace.
- Consecuencias:
  - Limitacion conocida: el reenvio via `HttpClient` de request/response estandar no
    soporta upgrade a WebSocket — el socket.io de Etherpad cae a su fallback de
    long-polling (funcional, no optimo).
  - No valida capacidad de evento (`EventCapability`) en el gateway, solo sesion — ese
    gating sigue viviendo en el frontend/backend de la app, sin cambios.
  - `AUTH_VALIDATE_URL`/`GATEWAY_ROUTES`/`GATEWAY_LOGIN_URL` configurables por env var,
    mismo patron que el resto de servicios del chart.
