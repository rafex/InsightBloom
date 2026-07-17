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
  - **Postmortem (2026-07-16/17)**: el proxy de WebSocket agregado despues (TASK-0020,
    `WebSocketProxyCreator`/`JettyWebSocketEndpointBridge`, ver DEC-0023 Fase 3b para el
    caso dinamico del IDE) **nunca funciono de verdad**, ni siquiera para Etherpad — quedo
    enmascarado porque socket.io hace fallback automatico a HTTP long-polling cuando el
    WebSocket real no conecta, exactamente la "limitacion conocida" ya anotada arriba en
    esta misma decision, solo que result no era una limitacion aceptada sino un bug activo.
    Se hizo visible recien con el IDE (code-server no tiene fallback: su protocolo remoto
    exige un WebSocket real) — sintoma: "WebSocket close with status code 1006" repetido en
    el cliente, sin ningun rastro en logs del gateway ni del backend.
    - **Diagnostico**: se descarto el backend (probado con un socket TCP crudo contra el
      Service real, incluyendo el path+query exacto que fallaba — acepta perfecto, 101
      Switching Protocols) y la logica de negocio del proxy (se agrego logging
      incondicional en cada callback de `JettyWebSocketEndpointBridge` —
      `onWebSocketOpen`/`onWebSocketClose`/`onWebSocketError` — y ninguno se disparaba
      nunca, pese a que `WebSocketProxyCreator.createWebSocket()` si completaba y
      devolvia el listener sin error).
    - **Root cause real**: `insightbloom-tools-gateway` empaqueta un uber-jar con
      `maven-shade-plugin` sin `ServicesResourceTransformer` configurado. Jetty registra
      componentes internos de su maquinaria de WebSocket (extensiones como
      `PerMessageDeflateExtension`, parsers de `ExtensionConfig`, etc.) via archivos
      `META-INF/services/*`; cuando varios JARs de Jetty comparten el mismo nombre de
      archivo de servicio, el shade plugin por defecto **sobrescribe en vez de fusionar**
      esos archivos — rompe en silencio el wiring interno de negociacion/registro de
      WebSocket, mientras el resto del `Server` HTTP crudo sigue funcionando normal (por
      eso HTTP normal, incluyendo el propio handshake de upgrade a nivel HTTP, nunca dio
      sintomas). Gotcha documentado de Jetty combinado con shade-plugin.
    - **Intento 1 (descartado)**: agregar `<transformer implementation="org.apache.
      maven.plugins.shade.resource.ServicesResourceTransformer"/>` al shade-plugin. El
      jar reconstruido si traia `META-INF/services/org.eclipse.jetty.websocket.core.
      Extension` con las 5 extensiones internas de Jetty correctamente fusionadas (antes
      se perdian por sobreescritura) — pero **probado en vivo contra el cluster real, el
      WebSocket seguia sin conectar, cero cambio observable**: `onWebSocketOpen`/
      `onWebSocketClose`/`onWebSocketError` seguian sin invocarse nunca. Descarta la
      teoria de que el problema fuera (solo) la fusion de `META-INF/services/*`.
    - **Intento 2 (descartado)**: reemplazar maven-shade-plugin por maven-assembly-plugin
      (`jar-with-dependencies`, `appendAssemblyId=false`, `containerDescriptorHandlers`
      con `metaInf-services`). Build limpio, `java -jar` arranca y sirve `GET /version`
      con 200 — pero **probado en vivo, cero cambio observable**: mismos callbacks sin
      invocarse nunca. Descarta definitivamente la teoria de empaquetado/`META-INF/
      services` como causa (ninguno de los dos intentos, atacando el mismo sospechoso
      desde dos angulos distintos, cambio el sintoma). El modulo se quedo en
      maven-assembly-plugin de todos modos (empaquetado mas simple, sin necesidad real
      de las features de shade-plugin), pero como decision independiente, no como fix.
    - **Root cause real (confirmado, 2026-07-17)**: nada que ver con empaquetado. Leyendo
      el codigo fuente de Jetty 12.1.7 (`JettyWebSocketFrameHandlerFactory.
      createListenerMetadata()`), Jetty conecta los callbacks (`onWebSocketOpen`/
      `onWebSocketClose`/`onWebSocketError`/etc.) de cualquier `Session.Listener` via
      `MethodHandles.publicLookup().in(endpointClass)` — un lookup **publico** sobre una
      clase que no es `public` (aunque sus metodos individuales si lo sean) no tiene
      acceso a sus miembros. Tanto `JettyWebSocketEndpointBridge` (el listener del lado
      servidor, para la conexion entrante del navegador) como
      `LoggingWebSocketProxyEndpoint.BackendSessionListener` (el listener del lado
      cliente, para la conexion saliente hacia el pod real) eran clases no-`public`
      (`final class` / `private static final class`). El fallo (`IllegalAccessException:
      class is not public`) quedo invisible **semanas** porque Jetty loguea sus propios
      errores internos via SLF4J, y este servicio nunca tuvo un provider real bindeado
      (fallback silencioso a NOP) — se agrego `slf4j-jdk14` como puente a
      `java.util.logging` (misma infra que ya usa el resto del servicio) para poder ver
      esto en absoluto. Fix: ambas clases pasaron a `public`, cada una con su commit
      separado porque el mismo bug aplica simetricamente en ambos lados del proxy
      (servidor primero, cliente saliente despues) — confirmado en vivo tras cada uno
      que `onWebSocketOpen invocado por Jetty` empezaba a aparecer en logs.
    - **Segundo bug, descubierto tras el primero (2026-07-17)**: con el WebSocket
      conectando, el navegador seguia mostrando "close 1006" para varios sub-canales
      especificos (extension host, `/update/check`, iframes internos). Causa: la cookie
      de sesion (`ib_gw`) se emitia con `SameSite=Lax`; el extension host de code-server
      corre en un iframe/worker sandboxeado con origen opaco
      (`webWorkerExtensionHostIframe.html`), y Chrome trata sus requests como cross-site
      para efectos de `SameSite` aunque la URL sea el mismo host — la cookie nunca
      llegaba en esos canales, autenticacion rechazada, WS cerrado (visible como 1006).
      Fix: `SameSite=None` (sigue `Secure`+`HttpOnly`) en ambos lugares donde se emite
      la cookie (`AuthGateHandler`, `WebSocketProxyCreator`).
    - **Tercer bug, descubierto tras el segundo (2026-07-17)**: con auth resuelta, el
      WS conectaba pero entraba en loop de reconexion constante (~1/seg, visible en
      logs del gateway). Causa: el limite por defecto de Jetty para mensajes binarios
      (64KB) es insuficiente para el canal de management de code-server, que manda
      frames binarios mas grandes — el backend cerraba con `1009 Binary message too
      large`, cascadeando a un cierre 1011 del lado cliente. Fix:
      `setMaxBinaryMessageSize(20MB)` en ambos lados del proxy (`ServerWebSocketContainer`
      del lado servidor, `WebSocketClient` del lado saliente hacia el backend) — mismo
      patron de fix simetrico que el bug de visibilidad de clases.
    - **Confirmado en vivo end-to-end** tras los tres fixes: WebSocket estable (0
      reconexiones en 15s de observacion continua), extension host activo (deteccion de
      lenguaje, Prettier disponible), editor funcional. Leccion general: los tres bugs
      eran completamente independientes entre si (reflexion de Jetty, SameSite de
      cookies, limite de tamano de mensaje) y cada uno enmascaraba al siguiente bajo el
      mismo sintoma superficial ("WebSocket close 1006") — hizo falta arreglar y
      verificar en vivo uno a la vez, sin asumir que el primer fix que cambiaba el
      comportamiento observable era "el" fix completo.

### DEC-0023 - IDE web en sandbox (code-server) por asistente, pool fijo sin RBAC de pods

- Fecha: 2026-07-12
- Estado: accepted
- Contexto:
  `event-types-catalog` (DEC-0016) dejo la capacidad `CODE_IDE` explicitamente fuera de
  alcance hasta definir reglas de seguridad de ejecucion: es la unica capacidad de la
  plataforma que implicaria ejecutar codigo arbitrario de asistentes en un contenedor con
  terminal real, la superficie de ataque mas sensible posible hacia el cluster. Se evaluaron
  dos IDEs web equivalentes (code-server de Coder vs. OpenVSCode Server de Gitpod, ambos
  MIT, ambos VS Code sobre Open VSX) y dos modelos de aprovisionamiento (pool fijo
  pre-creado vs. creacion dinamica de pods bajo demanda).
- Decision:
  - **code-server** sobre OpenVSCode Server: trae autenticacion propia (password por
    sandbox, defensa en profundidad detras de la sesion de InsightBloom), esta disenado
    para self-host detras de reverse proxy (el caso exacto de este proyecto), y tiene mayor
    madurez de comunidad para ese escenario. OpenVSCode Server esta pensado para vivir
    embebido dentro de Gitpod, con auth minima (solo un token en la URL) y documentacion de
    self-host mas escasa.
  - **Pool fijo, no dinamico**: al habilitar `CODE_IDE` para un taller, el organizador define
    `sandbox_pool_size`; Helm/el backend pre-crea ese numero de pods, y el backend solo
    *asigna* un sandbox libre a cada asistente (primer-uno-libre, `UNIQUE` en SQLite, mismo
    patron que `reservations`). **Ningun componente de la aplicacion tiene RBAC de
    Kubernetes para crear/eliminar pods en runtime** — es la decision que mas reduce la
    superficie de ataque: un servicio comprometido no puede escalar creando pods arbitrarios.
    El nodo actual (12 CPU/32 GB) soporta ~15-18 sandboxes Java concurrentes (el caso mas
    pesado, JVM + language server) o ~25-30 Python/web sin hardware adicional; escala mayor
    es una decision operativa por evento (unir un nodo agente temporal), no parte de esta
    iniciativa.
  - Imagen propia `insightbloom-sandbox` (Alpine minima, no-root uid 1000, mismo estandar de
    hardening ya aplicado al resto de la plataforma) con tres variantes (`python`, `java`,
    `web`) construidas sobre la misma base; git/make/just/sqlite3 preinstalados; extension de
    Open VSX (nunca del Marketplace oficial de Microsoft, cuyos terminos no permiten forks de
    VS Code) para visualizar tablas SQLite.
  - `internet_enabled` es una **bandera por taller, cambiable en cualquier momento sin
    reiniciar sandboxes activos** — implementada como `NetworkPolicy` a nivel de evento
    (deny-all por defecto; egress a un proxy con allowlist de PyPI/npm/Maven Central/GitHub
    solo cuando esta en `true`), nunca egress abierto sin filtro. Namespace dedicado
    `insightbloom-sandboxes`, aislado del resto del cluster (los sandboxes nunca alcanzan
    `insightbloom-users` ni ningun otro Service interno, con o sin internet habilitado).
  - Lista de paquetes adicionales por taller (texto declarativo, una entrada por linea) se
    instala una sola vez al aprovisionar el pool, nunca en caliente durante la sesion del
    asistente — evita que sea un vector de ejecucion arbitraria.
  - **Auth reutilizada, no nueva**: el unico portero real es la sesion de InsightBloom ya
    existente (`ib_token`/`ib_gw` via `insightbloom-tools-gateway`, DEC-0022) — solo un
    usuario registrado y con sesion valida llega a ver un sandbox, mismo patron exacto que
    ya gatea drawio/Etherpad/Excalidraw. El password propio de code-server, si se usa, es un
    secreto interno autogenerado por sandbox que el backend/gateway inyecta de forma
    transparente; nunca una segunda pantalla de login ni una credencial que el asistente
    deba conocer o gestionar. No se construye un sistema de auth independiente para el IDE.
  - Acceso exclusivo via `insightbloom-tools-gateway` (DEC-0022), que requiere extenderse
    para soportar upgrade a WebSocket antes de poder enrutar code-server (VS Code web no
    tiene fallback a long-polling, a diferencia del socket.io de Etherpad).
  - Flujo de git de punta a punta dentro del sandbox: repo local inicializado por defecto, o
    clonado de un remoto publico que el profesor indica; el alumno agrega su propio remoto y
    hace push con sus propias credenciales (la plataforma nunca gestiona ni ve credenciales
    git de terceros). Boton de descarga del workspace como zip para quien no use git.
- Consecuencias:
  - Detalle completo de fases, tareas y criterios de cierre en
    `spec-native/specs/code-ide-sandboxes/SPEC.md` y
    `spec-native/tasks/code-ide-sandboxes/TASKS.md`.
  - Queda fuera de esta iniciativa (ver SPEC, Non-Goals): pool dinamico con RBAC delegado,
    persistencia del workspace mas alla del TTL del evento, instalacion de software en vivo
    mas alla de lo declarado por el organizador, colaboracion en tiempo real dentro de un
    mismo sandbox, y autenticacion git federada.
  - La nota en `spec-native/tasks/event-types-catalog/TASKS.md` que marcaba `CODE_IDE` como
    fuera de alcance se actualiza para apuntar a esta iniciativa.
  - **Nota pendiente (2026-07-16, sin analizar todavia)**: para cohortes grandes (ej. 30
    alumnos) el modelo actual 1 pod = 1 usuario puede ser costoso en recursos. Se esta
    pensando una estrategia para que un mismo sandbox sirva a ~10 usuarios en vez de 1,
    reduciendo la cantidad de pods necesarios. **No iniciado** — pendiente de validar
    primero en vivo el modelo actual (pre-warm de al menos un sandbox libre por
    conferencia, ver commit `3e37942`) antes de explorar esto. Punto abierto no trivial a
    resolver cuando se retome: `code-server` no tiene multi-tenencia nativa (una instancia
    sirve a un usuario/workspace), asi que "reutilizar" un pod para 10 usuarios implica
    decidir entre correr 10 procesos de `code-server` dentro del mismo pod (vuelve a ser
    10 contenedores de trabajo, solo co-ubicados — dudoso ahorro real) o adoptar una
    herramienta con multi-tenencia real (ej. patron tipo JupyterHub) — ninguna opcion
    evaluada todavia.
  - **Actualizacion (2026-07-16)**: se evaluo puntualmente reutilizar solo el contenedor
    `runtime` (no el par completo ide+runtime) entre varios alumnos, ya que `socat` con
    `fork` ya soporta multiples conexiones concurrentes sin cambios. Se descarta por ahora:
    requiere resolver aislamiento de filesystem (hoy `/home/coder/workspace` y `/db` son
    volumenes por Pod, compartidos = todos ven los archivos de todos), aislamiento de
    recursos (CPU/memoria hoy es por Pod, compartir = un alumno pesado afecta a los demas)
    y aislamiento de seguridad (mismo uid 1000 y mismo espacio de procesos para todos —
    justo la superficie que DEC-0023 diseño para aislar por Pod). Ademas, Kubernetes no
    permite agregar contenedores a un Pod corriendo, asi que N contenedores `ide` + 1
    `runtime` compartido implicaria un Pod fijo por cohorte (no elastico), con mayor blast
    radius: si ese Pod se cae, se caen todos los alumnos de la cohorte a la vez.
    Estrategia propuesta a explorar si se retoma: aislar cada alumno dentro del `runtime`
    compartido via **chroot y cgroups** (namespace de filesystem + limite de CPU/memoria
    por alumno dentro del mismo contenedor), con un **identificador unico por alumno**
    (asignado al registrarse) como clave de particion, y un **proceso vigilante** que mate
    procesos colgados/fuera de limite por alumno. Esto es, en esencia, reconstruir a mano
    parte de lo que un container runtime ya da gratis por Pod — el trade-off a evaluar
    quando se retome es si el ahorro de recursos justifica construir y mantener ese
    aislamiento propio en vez de simplemente pre-provisionar mas pods. **Sigue sin
    iniciar** — ninguna implementacion, solo la estrategia queda anotada para cuando se
    revisite.
  - **Actualizacion (2026-07-17): segundo modo de IDE, "terminal-nvim"**. Se agrega una
    alternativa mas liviana a code-server: Neovim configurado como IDE (explorador de
    archivos, autocompletado, LSP de Java via `jdtls`, syntax highlighting via los parsers
    de tree-sitter del sistema — ver `infra/docker/Dockerfile.code-ide-runtime` y
    `infra/docker/nvim-init.lua`), servido por `ttyd` (terminal web sobre WebSocket) en vez
    de un navegador VS Code completo. El Pod de este modo tiene **un solo contenedor**
    (`runtime`, sin `ide`) — mas liviano en RAM/CPU y en tiempo de arranque (sin JS de
    VS Code que descargar/parsear en el navegador). Se elige por conferencia reutilizando
    el campo `sandboxVariant` (ver arriba, "solo label informativo" en DEC anterior) como
    "modo de IDE": el valor literal `"terminal-nvim"` activa este modo en
    `KubernetesPodClient.buildPodBody`; cualquier otro valor (incluidos los historicos
    `python`/`java`/`web`, o vacio/null) significa code-server — no se agrego una columna
    nueva a la DB. Todo el resto del pipeline (auth por sesion via `insightbloom-tools-gateway`,
    resolucion de `gatewayUrl` en `SandboxHandler`, la pagina `IdePage.vue` del frontend)
    es agnostico al modo: abre la misma URL `?ib_token=...&conferenceId=...` sin cambios,
    el gateway proxea HTTP/WS al Service del Pod sin importar si el contenedor detras es
    `ide` (code-server) o `runtime` (ttyd). Ambos toolchains completos (Java+Node+Python)
    siguen disponibles en los dos modos, ya que viven en la misma imagen `runtime` unica.
- Reemplaza: `none`

### DEC-0024 - Camino de escalado para los 6 servicios con SQLite: PostgreSQL, no un PVC compartido

- Fecha: 2026-07-16
- Estado: proposed
- Contexto:
  a raiz de un incidente (Etherpad atascado en "Reconectando...") causado por el HPA de
  `insightbloom-tools-gateway` escalando a 3 replicas mientras su `SessionCache` vivia en
  memoria por pod, surgio la pregunta de si los 6 servicios que persisten en SQLite
  (users, moderation, stats, query, ingest, survey — todos con `autoscaling.enabled: false`
  + `replicaCount: 1` fijo) podrian escalar horizontalmente montando el mismo PVC desde
  multiples pods en modo WAL (DEC-0010 ya habilita WAL por conexion, con
  `busy_timeout=5000`). Se investigo el estado real del cluster antes de concluir:
  - El cluster k3s es **de un solo nodo** (`rafex-server`), con `local-path` como unico
    StorageClass (`hostPath`, RWO, `WaitForFirstConsumer`) — no hay RWX disponible.
  - Los 6 servicios muestran **CPU de 1-14 millicores** en uso actual (practicamente idle),
    sin evidencia de presion de trafico real hoy.
  - **WAL resuelve concurrencia lector/escritor, no escritor/escritor**: SQLite sigue
    siendo, por diseno del motor, un unico escritor a la vez sin importar cuantos
    procesos/pods abran el archivo. Montar el mismo PVC desde N pods no multiplica el
    throughput de escritura — los N procesos serializan en el mismo lock de archivo,
    ahora entre procesos (mas costoso) en vez de entre threads de una sola JVM (mas
    barato, que es lo que ya da la replica unica actual).
  - Al ser un solo nodo, el peligro clasico de "SQLite sobre filesystem de red" (locking
    roto en NFS/CIFS) no aplica tal cual — `local-path` es un filesystem local real. Pero
    tampoco hay CPU/memoria adicional real que desbloquear: son mas pods en la misma
    maquina fisica, no mas capacidad.
- Decision:
  no se ejecuta ninguna migracion ahora — no hay senal medible que la justifique. Se deja
  documentado el analisis de las dos rutas evaluadas para cuando (si) aparezca presion
  real, con una preferencia clara por la opcion B:
  - **Opcion A — capa/worker que arbitre el acceso a SQLite** (serializar escrituras desde
    un unico proceso, o adoptar rqlite/dqlite, SQLite envuelto en consenso Raft, para
    escritura multi-nodo real): tecnicamente viable, pero implica construir o adoptar un
    sistema distribuido nuevo (membership de cluster, snapshots, cambio de driver/cliente
    en los 6 servicios) para terminar con, en el mejor caso, paridad de escritura
    concurrente con lo que Postgres ya da de fabrica. En un cluster de un solo nodo, el
    beneficio real seria solo resiliencia durante un restart (un pod sigue sirviendo
    lecturas mientras otro reinicia), no mas capacidad — un costo de ingenieria alto para
    un beneficio acotado, y hoy el codigo tampoco separa rutas de lectura/escritura (cada
    handler lee y escribe con el mismo repositorio) asi que ni siquiera hay como
    aprovechar esa resiliencia sin reescribir el ruteo interno.
  - **Opcion B — migrar a PostgreSQL** (recomendada si se dispara la necesidad): motor
    cliente-servidor con concurrencia multi-escritor nativa, desacopla los datos del
    ciclo de vida del pod (cualquier cantidad de pods stateless puede escalar y
    reconectar a la misma instancia), y es el camino estandar, bien documentado, sin
    inventar un protocolo de arbitraje propio. Costo: una pieza de infraestructura nueva
    que operar (backups, upgrades) — pero una sola, compartida entre los 6 servicios, en
    vez de un sistema de arbitraje custom que resolver para cada uno. Implica trabajo de
    migracion por servicio (DDL, tipado estricto vs. el tipado dinamico de SQLite,
    `SERIAL`/`IDENTITY` en vez de `AUTOINCREMENT`, pooling de conexiones) que no se
    dimensiona en esta decision.
  - **Disparador explicito para revisitar esto**: CPU sostenida >60% en un pod de alguno
    de los 6 servicios, o errores `SQLITE_BUSY`/timeout de lock observados en logs de
    produccion, o un plan concreto de un evento con audiencia significativamente mayor a
    lo visto hasta ahora. Sin uno de estos tres, `replicaCount: 1` fijo sigue siendo
    correcto y no es deuda tecnica pendiente.
- Consecuencias:
  - Los 6 servicios SQLite permanecen en `autoscaling.enabled: false` + `replicaCount: 1`
    sin cambios.
  - Si se dispara la migracion, el candidato por defecto es PostgreSQL (opcion B); la
    opcion A solo se reconsideraria si aparece una razon concreta que impida sumar
    Postgres (ninguna identificada hoy).
  - Orden sugerido si se dispara, no vinculante: `users`/`ingest`/`moderation` son los de
    mayor trafico esperado durante un evento en vivo; `stats`/`query`/`survey` tienen
    volumen de escritura menor y pueden migrar despues.
  - **Pendiente de seguimiento**: con `replicaCount: 1` fijo y sin horizontal scaling
    posible para estos 6 servicios, el unico margen real ante mas carga es vertical
    (CPU/memory request y limit por pod). Revisar metricas de Goldilocks/VPA (ver
    `infra/README.md`, ultima lectura 2026-06-30) especificamente para estos 6 servicios
    y subir sus recursos si la recomendacion lo justifica — sin replicas de respaldo, un
    pod subdimensionado que se queda sin CPU/memoria es un punto unico de falla mas
    sensible que en un servicio que si puede escalar horizontalmente.
- Reemplaza: `none` (extiende DEC-0010 — WAL sigue siendo correcto para el escenario
  actual de replica unica; no aplica a un escenario multi-pod).
