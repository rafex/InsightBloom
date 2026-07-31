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

- Fecha: 2026-07-21
- Estado: accepted
- Contexto:
  el motor de encuestas propio (`insightbloom-survey`) cubre tipos de
  pregunta basicos y calificacion LLM opcional (DEC-0014). Se quiere ofrecer
  SurveyJS como un motor independiente, igual que los motores alternativos de
  presentaciones: el moderador debe elegir el motor al iniciar la encuesta y
  no deben aparecer conflictos de compatibilidad entre ambos modelos.
- Decision:
  el moderador elige, antes de crear la primera pregunta, un `engine`
  (`NATIVE` o `SURVEYJS`). El motor queda fijo durante toda la vida de esa
  encuesta. No se convierten ni mezclan preguntas, respuestas, definiciones,
  resultados o reglas de calificacion entre motores. `SURVEYJS` usa solo
  `survey-core` + `survey-vue3-ui` como Form Library para renderizar esquemas
  JSON; la autoria inicial es controlada por InsightBloom.

  La Form Library de SurveyJS es MIT y gratuita. La integracion debe conservar
  sus avisos de copyright y licencia. `survey-creator-vue`, PDF Generator y
  Dashboard/Analytics requieren licencia comercial y quedan fuera de esta
  iniciativa; cualquier adopcion futura requiere una decision y presupuesto
  separados.

  La unica funcionalidad compartida es la sugerencia de preguntas con IA: se
  entrega como propuesta neutral, el moderador la revisa y la adapta al motor
  elegido antes de guardarla. No hay conversion automatica entre motores.
- Consecuencias:
  el organizador gana una alternativa de formularios mas rica sin perder el
  motor propio ya construido; la persistencia, API, render y resultados de
  `SURVEYJS` se mantienen separados de `NATIVE`; el esfuerzo de mantenimiento
  se duplica de forma explicita; la calificacion automatica con LLM (DEC-0014)
  sigue disponible solo para `NATIVE` en esta primera version; no se necesita
  licencia comercial para el alcance inicial.
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
- La modalidad de notas se separa de la topología: Etherpad es grupal por
  defecto (`COLLABORATIVE`) usando el pad del evento. Si el moderador elige
  notas individuales (`INDEPENDENT`), Users deriva un pad privado por
  usuario/evento con `ETHERPAD_PRIVATE_PAD_SECRET`; esos pads también se
  eliminan al ejecutar el TTL y nunca se incluyen en el ZIP de materiales.
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
  - **Auditoria de seguridad (2026-07-17): hallazgo critico confirmado en vivo y resuelto**.
    Cualquier Pod de `insightbloom-sandboxes` podia alcanzar el Service de CUALQUIER OTRO
    Pod del mismo namespace sin autenticacion (`curl` directo, 200 OK — probado con un pod
    "victima" real). Causa: la `NetworkPolicy` de egress existente permite trafico saliente
    entre pods del mismo namespace sin restriccion de puerto, y no existia ninguna
    `NetworkPolicy` de tipo `Ingress` que restringiera el trafico ENTRANTE — dado que
    `code-server` corre con `--auth none` y `ttyd` sin credencial (la autenticacion real
    vive solo en `insightbloom-tools-gateway`, en el borde), esto permitia a cualquier
    alumno acceder al IDE completo de otro alumno con solo el nombre del Service (patron
    predecible: `sandbox-<label>-<slot>-svc`). **Resuelto**: `KubernetesPodClient.
    ensureIngressPolicy()` crea una `NetworkPolicy` de Ingress que restringe el trafico
    entrante de todo sandbox al Pod del gateway unicamente (namespace + label
    `app.kubernetes.io/component=toolsgateway`), sobre el puerto publico del sandbox.
    Idempotente, se llama en cada `createSandbox`. De paso se corrigieron dos hallazgos
    relacionados introducidos en la misma sesion: `javadebug`/`pydebug`
    (`runtime-debug-helpers.sh`) bindeaban JDWP/debugpy a todas las interfaces (`*`/
    `0.0.0.0`) en vez de loopback — ambos protocolos no tienen autenticacion propia, asi
    que exponerlos fuera del Pod era RCE directo hacia cualquier otro alumno del mismo
    namespace mientras alguien debuggeaba. Corregido a `localhost`/`127.0.0.1` (el
    `launch.json` sembrado ya apuntaba a `localhost`, no requirio cambios de flujo).
  - **Nota pendiente (2026-07-17, decision no tomada)**: la raiz del hallazgo anterior es
    que *todos* los sandboxes de *todas* las conferencias activas viven en el mismo
    namespace `insightbloom-sandboxes`. La NetworkPolicy de Ingress agregada cierra el
    acceso no autenticado, pero un alumno de la conferencia A y uno de la conferencia B
    siguen compartiendo namespace (mismo blast radius si algun otro vector de aislamiento
    fallara a futuro). Dos caminos evaluados, ninguno implementado todavia: (a) namespace
    por conferencia — aislamiento real, mayor costo operativo (crear/borrar namespaces,
    RBAC por namespace, `NetworkPolicy` por namespace en vez de una sola); (b) mantener
    namespace compartido pero filtrar tambien por label `sandbox-conference` en la
    `NetworkPolicy` de Ingress (no solo namespace) — mucho mas barato de implementar, pero
    sigue siendo el mismo namespace de Kubernetes (mismos `ResourceQuota`/`LimitRange`
    compartidos, un alumno ruidoso de una conferencia puede afectar el scheduling de otra).
    **Sigue sin decidirse** — pendiente de revisitar si el numero de conferencias
    concurrentes crece lo suficiente como para justificar el costo de (a).
  - **Cambio de paradigma (2026-07-17, misma fecha, pedido explicito del usuario): fin del
    split `ide`/`runtime`, dos imagenes autocontenidas.** La Fase 4 (split en dos
    contenedores por Pod, bridge de terminal via `socat` en loopback) partia de la premisa
    de que `code-server` no necesitaba el toolchain de lenguajes instalado — separar ese
    peso en un contenedor `runtime` aparte. Esa premisa ya se habia roto ese mismo dia: la
    extension `redhat.java` necesitaba un JDK *local* en el contenedor `ide` para que el
    language server (jdt.ls) pudiera compilar/analizar mientras se escribe (el bridge JDWP
    solo sirve para *adjuntarse* a un proceso ya corriendo, no reemplaza al language
    server) — asi que el contenedor `ide` terminaba con Java igual, y la separacion solo
    aportaba superficie: una `NetworkPolicy` de loopback, un `socat`, y el hallazgo critico
    de esta misma auditoria (JDWP/debugpy sin auth, alcanzables entre Pods). Se reemplazan
    `Dockerfile.code-ide-server` + `Dockerfile.code-ide-runtime` por dos imagenes
    independientes y completas, cada Pod corre UNA sola (`KubernetesPodClient` ya no arma
    dos contenedores, ver `sandboxContainer` unico):
    - `Dockerfile.code-ide-debian` (Debian 12-slim): `code-server` + extensiones Java/
      Python/JavaScript + idioma español (`ms-ceintl.vscode-language-pack-es`,
      `--locale es`) — el modo "editor grafico" de siempre.
    - `Dockerfile.code-ide-neovim` (Alpine 3.21): `neovim`/`vim`/`lazygit`, servido por
      `ttyd` — el modo "100% terminal" que antes vivia dentro de la imagen `runtime`
      (sentinel `variant == "terminal-nvim"`, sin cambios en ese contrato).
    - Ambas imagenes traen el MISMO toolchain version-pinneado (pedido explicito): Java 25
      LTS Temurin `jdk-25.0.3+9` (build `linux`/glibc en Debian, build `alpine-linux`/musl
      en Alpine — Temurin publica los dos; SHA256 de ambos tarballs verificado
      independientemente antes de bakearlos, coincide con el que dio el usuario para el
      build glibc), Python 3.12.13 (via `python-build-standalone` de astral-sh — el mismo
      build exacto existe en variante glibc y musl, evita que cada imagen termine con un
      patch de Python distinto por casualidad de lo que trae cada distro), y Node.js LTS
      24.18.0 (glibc: tarball oficial de `nodejs.org`; musl: Alpine no tiene build oficial
      de Node en `nodejs.org`, se usa `unofficial-builds.nodejs.org`, el proyecto de
      comunidad que la propia documentacion de Node.js referencia para plataformas fuera
      de los builds oficiales). Todas las descargas van con `sha256sum -c` contra un hash
      fijado en el Dockerfile, no solo "confiar en HTTPS".
    - Herramientas de curso agregadas en las dos imagenes (pedido explicito: "sugiereme que
      otros paquetes deberian tener para dar cursos"): `bat`/`eza`/`fd`/`ripgrep`/`ncdu`
      (mejoras de cat/ls/find/grep/du), `jq`, `tmux`, `tree`, `httpie`, `shellcheck`,
      `build-essential`/`build-base` (compilar ejemplos nativos y modulos nativos de npm),
      `maven`, `git`, `fzf`, `bash-completion`, `unzip`, `less`+`man`. Paquetes de Python
      (`jupyter`, `numpy`, `pandas`, `matplotlib`, `flask`, `django`, `fastapi`, `pytest`,
      `black`, `pylint`) y de Node (`typescript`, `eslint`, `prettier`, `vite`, `webpack`,
      etc.) tambien pre-instalados globalmente, sin paso manual del instructor/alumno.
    - `KubernetesPodClient`: constructor colapsa `serverImage`/`runtimeImageBase` +
      `ideResources`/`runtimeResources` a `debianImage`/`neovimImage` + un unico
      `debianResources`/`neovimResources` (un solo contenedor por Pod, pero DOS sets de
      limites — uno por imagen, no uno compartido: code-server con jdt.ls y ttyd+nvim
      tienen perfiles de consumo muy distintos, colapsarlos a un unico default fue un error
      de la primera pasada de este refactor, corregido el mismo dia). Se elimina
      `RUNTIME_PORT`/`execLoopbackProbe` (ya no hay loopback intra-Pod que probar). El
      sentinel `IDE_MODE_TERMINAL_NVIM` sigue funcionando igual (selecciona imagen, ya no
      selecciona "con o sin segundo contenedor").
    - `UsersApplication`: env vars renombradas — `SANDBOX_SERVER_IMAGE`/
      `SANDBOX_RUNTIME_IMAGE_BASE` → `SANDBOX_DEBIAN_IMAGE`/`SANDBOX_NEOVIM_IMAGE`;
      `SANDBOX_IDE_*`/`SANDBOX_RUNTIME_*` → `SANDBOX_DEBIAN_*`/`SANDBOX_NEOVIM_*`
      (`_CPU_REQUEST`/`_MEMORY_REQUEST`/`_CPU_LIMIT`/`_MEMORY_LIMIT`). Defaults: Debian
      200m/640Mi request, 750m/1536Mi limit (carga pesada — extension host de VS Code Web +
      code-server + jdt.ls); Alpine 100m/512Mi request, 500m/1Gi limit (mas liviano — solo
      ttyd+nvim, aunque jdt.ls tambien puede activarse ahi al editar .java) — ambos dentro
      del tope de `LimitRange` del namespace, 2048Mi/1000m por Pod desde el ajuste de mas
      arriba en esta misma seccion.
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

### DEC-0025 - Pods "neovim" multi-alumno (varios usuarios Linux compartiendo un Pod)

- Fecha: 2026-07-17
- Contexto: pedido explicito del usuario -- los Pods `debian` (code-server) siguen 1:1
  (no se puede compartir un VS Code Web entre alumnos), pero los Pods `neovim`
  (Alpine, vim/neovim+ttyd) si deben poder alojar varios alumnos a la vez, cada uno con
  su propio usuario Linux real (`/home/{userUuid}/workspace`), mas un mecanismo que
  impida que uno acapare los recursos del Pod compartido a costa de sus companeros
  (loop infinito, fork bomb, intencional o no), registrando el incidente para que el
  organizador lo vea en el Dashboard.
- Decision: implementado completo en 3 fases (A: modelo de datos + ruteo multi-puerto;
  B: seat-agent, aprovisionamiento dinamico; C: watchdog + incidentes en Dashboard),
  todas verificadas en vivo con `docker build`/`docker run` reales (no solo tests
  unitarios) antes de darlas por cerradas.

  **Ruteo (Fase A)**: un `ttyd` por asiento, cada uno en su propio puerto dentro del
  mismo contenedor (`basePort + seatIndex`). El gateway resuelve `(pod, puerto)` en vez
  de solo `(pod)` -- `ResolveSandboxTargetUseCase` usa `Sandbox.seatPort(basePort)`.

  **Modelo**: `sandbox_assignments` gana `seat_index` (migracion con recreacion de tabla
  completa -- SQLite no permite `ALTER TABLE` para relajar un `UNIQUE` existente, el
  viejo `UNIQUE(conference_uuid, sandbox_slot)` pasa a
  `UNIQUE(conference_uuid, sandbox_slot, seat_index)`). `AssignSandboxUseCase`: en modo
  `terminal-nvim` la capacidad total pasa de `poolSize` a `poolSize * seatsPerPod` --
  primero intenta sumar un asiento a un Pod ya existente con lugar antes de abrir uno
  nuevo (`nextFreeSeat`). Cualquier otro modo (o `seatsPerPod` no configurado/1): cero
  cambios de conducta, cada slot admite un unico ocupante como siempre.

  **Asientos por pod**: default 4, configurable por conferencia
  (`Conference.sandboxSeatsPerPod`, mismo patron que `sandboxJvmHeapMb`), validado 1..10
  en `SetSandboxConfigUseCase`.

  **Aprovisionar usuario (Fase B) -- cuenta Linux REAL por alumno, no un pool fijo**:
  primer diseño evaluado fue un pool fijo de cuentas pre-creadas ("seat0".."seat9",
  pedido inicial del usuario para evitar que el seat-agent necesitara root) -- se
  descarto: no daba `/home/{userUuid}/workspace` literal como se pidio originalmente. El
  usuario propuso despues usar `sudo` con una regla de sudoers para que el agente
  (corriendo como usuario normal) pudiera crear cuentas -- tambien se descarto: `sudo`
  exige `allowPrivilegeEscalation: true` en el Pod (reabre CUALQUIER binario setuid/setgid
  de la imagen, no solo el caso puntual) y una politica en texto (sudoers) mas propensa a
  rendijas de inyeccion de argumentos que una lista corta de capabilities que el kernel
  aplica por syscall. Diseño final: el contenedor "sandbox" de un Pod multi-asiento corre
  con `runAsUser: 0` pero `capabilities: drop ALL, add [SETUID, SETGID, KILL, CHOWN,
  FOWNER]` -- root nominal, sin mas permiso real que exactamente lo que
  `adduser`/`chown`/cambiar de uid/matar procesos necesitan. `allowPrivilegeEscalation`
  se mantiene en `false`. El seat-agent (`sandbox-agent.py`, Python stdlib puro, sin
  dependencias) crea la cuenta real del alumno (`adduser -u {2000+seatIndex} -h
  /home/{userUuid} ...`) la primera vez que se le pide ese asiento, y cada `ttyd` que
  arranca DROPEA a ese uid especifico antes de `exec` (nunca corren como root, solo el
  agente administrador lo hace). Bug real encontrado y corregido en vivo: root sin
  `CAP_DAC_OVERRIDE` NO puede escribir dentro de un directorio de otro dueño aunque sea
  root -- hubo que crear los directorios (home/workspace/.config/nvim) ANTES de
  `adduser` (mientras root todavia es dueño de lo que crea), no despues.

  **Aislamiento de filesystem (2026-07-24)**: la primera versión dejaba los homes creados por
  Alpine en `0755`, por lo que un alumno podía atravesar y leer el workspace de otro aunque no
  pudiera escribirlo. `_ensure_seat_account` repara cuentas nuevas y existentes aplicando `0750`
  a `/home/{userUuid}`, `.config/nvim` y `workspace`: el grupo de control `coder` conserva la
  lectura necesaria para moderación, pero cada proceso `studentN` pierde sus grupos suplementarios
  antes de arrancar `ttyd` y no puede acceder a los homes vecinos. El agente deja `/home` en `0755`
  para resolver la ruta del asiento, nunca en modo escribible para alumnos.
  **Egress uniforme (2026-07-24)**: `HTTP_PROXY`/`HTTPS_PROXY` se inyectan siempre al crear un
  sandbox Web o CLI. El checkbox no controla la presencia de esas variables, sino la
  `NetworkPolicy` por evento que permite o bloquea el acceso al proxy interno. Así, activar o
  desactivar la salida no requiere recrear Pods existentes y ambos modos reciben exactamente la
  misma allowlist/blacklist de GitOps.
  `KubernetesPodClient.provisionSeat` llama al agente via HTTP interno con reintentos
  (~10s, cubre la carrera de que el Pod recien se esta agendando). Puerto de control
  (`basePort - 1`) alcanzable solo desde `insightbloom-users` (segunda regla de Ingress
  en `ensureIngressPolicy`, namespace+label propios, no los del gateway).

  **Vigilancia de recursos (Fase C)**: dos lineas de defensa. (1) `ulimit -u` (
  `RLIMIT_NPROC`, 100 por asiento) aplicado en el `preexec_fn` de cada `ttyd` antes de
  `exec` -- corta fork-bombs al instante, sin depender de polling. (2) Un watchdog en
  background dentro del mismo `sandbox-agent.py` que cada 5s lee `/proc/*/status` +
  `/proc/*/stat`, agrupa por UID de asiento, calcula CPU%/RSS, y compara contra el
  presupuesto "justo" (limite REAL del Pod, via Downward API `resourceFieldRef` --
  `POD_CPU_LIMIT_MILLICORES`/`POD_MEMORY_LIMIT_MIB` -- dividido entre asientos
  OCUPADOS ahora, no el maximo configurado, con 1.5x de tolerancia). Sostenido >45s (o
  >90% del limite de procesos, senal fuerte de fork-bomb en curso): mata el arbol de
  procesos del UID (`pkill -9 -u`) y reinicia un `ttyd` limpio para ese asiento
  (auto-recuperacion, el alumno se reconecta sin perder el asiento). Verificado en vivo:
  un `while true; do :; done` real fue detectado y terminado en la ventana esperada, con
  el `ttyd` respawneado automaticamente.

  Reporte de incidentes: `POST /internal/sandbox-incidents`, autenticado con
  `SANDBOX_INCIDENT_REPORT_KEY` via header `X-Sandbox-Incident-Key` -- DELIBERADAMENTE
  distinto de `INTERNAL_API_KEY` (ese secreto vive dentro de contenedores que un alumno
  puede leer via `env`, y protege TODOS los demas endpoints internos de la plataforma;
  filtrarlo aca le daria a cualquier alumno la llave de toda la superficie interna). El
  reporte es "mejor esfuerzo" (si el POST falla, solo se loguea localmente -- matar al
  asiento abusivo no puede depender de que insightbloom-users este alcanzable). Requiere
  una `NetworkPolicy` de Egress nueva (`sandbox-egress-incident-report`), independiente
  de `allowInternetEgress`/`internetEnabled` (el caso mas comun es sin internet
  habilitado, y el reporte de incidentes tiene que funcionar igual) -- restringida a un
  solo destino (namespace+label+puerto de `insightbloom-users`), no "internet abierto".
  Domain nuevo `SandboxIncident` (tabla `sandbox_incidents`), `RecordSandboxIncidentUseCase`
  + `ListSandboxIncidentsUseCase`, endpoint organizador-only
  `GET /{id}/sandbox-incidents` en `ConferenceHandler`, seccion nueva en
  `EditConferencePage.vue` (visible solo en modo `terminal-nvim`) listando cuando/quien/
  que paso -- mismo patron conceptual que moderacion de chat, implementado directo en
  `insightbloom-users` (no se reutilizo `insightbloom-moderation`, dominios distintos).

  **Debug remoto en modo multi-asiento**: `javadebug`/`pydebug` (puertos fijos
  5005/5678) chocaban entre alumnos del mismo Pod -- resuelto con puerto por asiento
  (`5005+SEAT_INDEX` / `5678+SEAT_INDEX`). `sandbox-agent.py` inyecta `SEAT_INDEX` en
  el ambiente de cada `ttyd` que arranca; `runtime-debug-helpers.sh` lo lee (default 0
  si no esta seteado -- imagen debian o neovim de un solo asiento, comportamiento
  identico al de siempre, mismos puertos 5005/5678 que ya trae `launch.json`).
  Limitacion aceptada, no resuelta: todos los asientos de un Pod comparten el mismo
  namespace de red (loopback incluido) -- el puerto distinto evita choques, no aisla
  a un alumno de poder conectarse al puerto de debug de otro si lo intenta a
  proposito (mismo nivel de confianza que ya existe entre asientos de un Pod
  compartido, no una regresion nueva).
- **Autocompletado semántico JavaScript/Node.js (2026-07-23):** el modo
  `terminal-nvim` usa `nvim-lspconfig` 2.3.0 (la última línea compatible con
  Neovim 0.10 de Alpine 3.21), `typescript-language-server`, `nvim-cmp` y
  `cmp-nvim-lsp`. El LSP se activa para JavaScript, JSX, TypeScript, TSX y
  JSON/JSONC, con raíz por `package.json`, `jsconfig.json`, `tsconfig.json` o
  `.git`. `@types/node` se instala durante el build en una ruta compartida
  inmutable; `seed-node-types.sh` la enlaza dentro de cada workspace efímero,
  incluido el flujo multi-asiento. No se usa Mason, Lazy ni instalación de
  paquetes en runtime: `fs`, `http`, `process`, `Buffer` y los demás tipos de
  Node están disponibles sin Internet.
- **Contrato LSP común para IDE Web y CLI (2026-07-23):** ambas imágenes
  precargan Java con `jdtls`, Python con `pyright-langserver`, JS/TS con
  `typescript-language-server`, HTML con `vscode-html-language-server` y CSS
  con `vscode-css-language-server`. code-server usa sus servicios integrados
  y extensiones oficiales; Neovim los inicia mediante `nvim-lspconfig` y
  `nvim-cmp`. Ningún servidor o plugin se descarga durante la sesión.
- **Egress GitHub-only (2026-07-23):** no se implementará una excepción por
  IP hardcodeada en `NetworkPolicy`; GitHub usa rangos y hosts de descarga que
  cambian. La salida controlada usa un proxy interno con allowlist FQDN, una
  lista negra con precedencia, una opción por evento y default-deny directo
  desde el sandbox. La configuración declarativa vive en GitOps
  (`app-config.yaml`) y su ConfigMap es una salida autogenerada. La validación
  pendiente es probar el flujo completo en el cluster.
- Consecuencias:
  - `SandboxOrchestrator` gano `provisionSeat` y `createSandbox` sumo dos parametros
    (`jvmHeapMb`, `seatsPerPod`) a lo largo de esta sesion -- toda implementacion/mock
    debe actualizarse si se agrega una nueva.
  - El namespace `insightbloom-sandboxes` necesita, al desplegar esta entrega, que la
    `NetworkPolicy` `sandbox-ingress-gateway-only` existente se borre y recree a mano
    una vez (`postIgnoringConflict` no la actualiza sola) para que tome el rango de
    puertos ampliado y la segunda regla (control del seat-agent) -- de lo contrario,
    Pods compartidos quedarian bloqueados por la policy vieja de un solo puerto/regla.
  - Nuevo env var obligatorio en produccion: `SANDBOX_INCIDENT_REPORT_KEY` (el default
    `"dev-only-change-me"` es deliberadamente inseguro, solo para desarrollo local).
- Reemplaza: `none` (extiende DEC-0023).

### DEC-0026 - Postmortem: terminal del IDE CLI en blanco (WebSocket proxy silencioso)

- Fecha: 2026-07-19
- Estado: accepted
- Contexto:
  la terminal del IDE CLI fallaba de forma intermitente y dificil de diagnosticar:
  segun el sintoma, se veia como corte de red a los ~50s, como perdida de estado del
  editor al reconectar, o como pantalla en blanco permanente con el WebSocket
  aparentemente sano (`onopen` disparaba, PING/PONG funcionaba). Cuatro causas
  independientes se superponian, cada una enmascarando a la siguiente hasta
  resolverse la anterior:
  1. Un HAProxy standalone a nivel de sistema operativo (fuera de k3s, terminador de
     TLS delante del Ingress del cluster) tenia `timeout client/server 50s` sin
     `timeout tunnel` -- cortaba cualquier WebSocket a los 50s exactos, sea o no
     idle. No vivia en ningun repo hasta este incidente.
  2. `ttyd` (terminal web del sandbox) lanzaba un proceso nuevo (`nvim`) en cada
     conexion -- cualquier corte breve perdia todo el estado del editor.
  3. Envolver `ttyd` con `tmux new-session -A` (para resolver el punto 2) parecia
     andar pero el server de `tmux` moria igual: nace compartiendo el process group
     del pty que `ttyd` controla, y el `SIGHUP` que `ttyd` manda al cerrar la
     conexion lo mataba junto al cliente antes de que terminara de independizarse.
  4. La causa de fondo real de la "pantalla en blanco": `insightbloom-tools-gateway`
     (proxy WebSocket generico delante de todas las herramientas) tenia dos bugs
     propios, invisibles porque el WebSocket seguia "abierto" y con PING/PONG sano
     pese a estar completamente muerto para datos reales:
     - La conexion saliente gateway-\>backend tarda ~1s en resolver (crear
       `HttpClient`/`WebSocketClient`, handshake WS saliente); mensajes del cliente
       que llegaban en esa ventana se descartaban en silencio
       (`onText`/`onBinary` con `bridge==null`). Para `ttyd`, el primer mensaje es
       el JSON de init (columns/rows) que exige antes de spawnear el pty -- si se
       pierde, el WebSocket queda abierto para siempre sin ningun dato.
     - La conexion saliente nunca reenviaba el subprotocolo de WebSocket
       (`Sec-WebSocket-Protocol`) que el cliente pidio -- backends que distinguen
       comportamiento por subprotocolo negociado (como `ttyd` con `tty`) aceptaban
       el handshake pero nunca procesaban nada.
  El diagnostico de (4) tomo la mayor parte del tiempo de este incidente porque el
  gateway no tenia forma de aislar el ciclo de vida de UNA conexion puntual en los
  logs (todo bajo el mismo logger, sin id de correlacion), y el PING/PONG en nivel
  INFO tapaba cualquier otra señal relevante.
- Decision:
  - `LoggingWebSocketProxyEndpoint` (`insightbloom-tools-gateway`) ahora:
    - Encola (`PendingBridge`) cualquier mensaje del cliente que llegue antes de que
      la conexion saliente al backend este lista, y lo reenvia en orden apenas
      resuelve -- en vez de descartarlo en silencio.
    - Reenvia el subprotocolo de WebSocket solicitado por el cliente a la conexion
      saliente via `ClientUpgradeRequest.setSubProtocols`.
    - Marca cada conexion con un `cid` corto (contador atomico) en todos sus logs,
      para poder aislar el ciclo de vida completo de una sesion con un solo grep.
    - Baja PING/PONG a nivel `FINE` (antes `INFO`, disparaba cada
      `--ping-interval` por cada conexion activa y tapaba el resto de los logs).
  - El HAProxy de host (fuera de k3s) se versiono por primera vez, en el repo
    `k3s-haproxy-setup` (antes solo vivia editado a mano en el servidor), con
    `timeout tunnel 1h` agregado.
  - `ttyd` ahora se lanza en dos pasos: primero `tmux new-session -d` en un proceso
    propio (ya terminado, sin pty de `ttyd` de por medio) que crea la sesion
    destacada; luego `ttyd` solo hace `tmux attach-session`, nunca `new-session`
    directamente -- asi el server de `tmux` nunca comparte process group con el pty
    que `ttyd` controla, y sobrevive a cualquier `SIGHUP`.
- Consecuencias:
  - Cualquier proxy WebSocket generico nuevo en este gateway debe encolar mensajes
    tempranos y reenviar el subprotocolo solicitado por default -- no asumir que
    "WebSocket abierto y con PING/PONG" significa "datos fluyendo".
  - El patron "crear sesion tmux aparte, ttyd solo attachea" es el que hay que
    replicar si se agrega otra variante de terminal-en-navegador sobre `ttyd`.
  - El HAProxy de host sigue sin automatizacion real (GitOps o timer de systemd que
    lo reconcilie) mas alla del respaldo versionado -- cambios futuros ahi requieren
    edicion manual + `systemctl reload haproxy` seguido de commitear el mismo cambio
    al repo, a mano, en ese orden.
- Reemplaza: `none`.

### DEC-0027 - Auto-sanado del aprovisionamiento de asientos (sin intervencion manual)

- Fecha: 2026-07-19
- Estado: accepted
- Contexto:
  con el bug del gateway resuelto (DEC-0026), el IDE CLI seguia fallando de forma
  intermitente con 502/conexion rechazada al abrirlo. Causa: `provisionSeat`
  (creacion del usuario Linux/ttyd de un asiento especifico en un Pod terminal-nvim
  compartido) reintentaba de forma SINCRONICA dentro del request HTTP de
  `GET /sandbox`, con un presupuesto de apenas 6 intentos x 2s (~12s). Un cold start
  real de Pod (pull de imagen + scheduling + arranque) puede tardar mas de dos
  minutos -- cuando el presupuesto se agotaba, la excepcion se propagaba como 500 en
  vez de caer en el flujo de polling/PENDING que el frontend ya sabia manejar,
  dejando el asiento atascado para siempre hasta reaprovisionarlo A MANO via
  `kubectl exec` (insostenible como operacion recurrente).
- Decision:
  - Nuevo metodo en `SandboxOrchestrator`: `ensureSeatReady` -- UN solo intento
    rapido (5s), nunca lanza excepcion, pensado para llamarse en cada poll en vez
    de bloquear con reintentos.
  - `AssignSandboxUseCase.execute()` ahora llama `ensureSeatReady` (no
    `provisionSeat`) en las tres ramas donde se aprovisiona un asiento, y en la
    rama de RECONEXION lo intenta en CADA llamada (antes solo si el Pod estaba
    totalmente ausente) -- efecto neto: cada `GET /sandbox` (incluido el polling
    automatico que ya hace `IdePage.vue` mientras el status es PENDING) reintenta
    solo, sin que nadie tenga que entrar a mano.
  - Nuevo metodo publico `AssignSandboxUseCase.isSeatFullyProvisioned(sandbox,
    userUuid)`, usado por `SandboxHandler` para calcular el status real: antes solo
    miraba `sandboxOrchestrator.isReady(pod)` (Pod con containers Ready), lo que
    podia devolver READY con el Pod sano pero el asiento especifico todavia sin
    aprovisionar. Ahora exige ambas cosas para Pods terminal-nvim multi-asiento.
  - Frontend (`IdePage.vue`): el estado PENDING ahora muestra
    `SandboxLoadingAnimation.vue` (astronauta animado con el logo de InsightBloom en
    el casco) en vez de un spinner de texto plano -- mismo timeout de polling de
    siempre (5 min), solo cambia que ahora SI llega a usarse en el caso de cold
    start real en vez de que el request explote antes de llegar ahi.
- Consecuencias:
  - `provisionSeat` (el metodo viejo, bloqueante con reintentos) queda sin uso real
    en el codigo de produccion pero se mantiene en la interfaz -- eliminarlo es
    limpieza aparte, no bloquea nada.
  - `isSeatFullyProvisioned` hace una consulta extra a `ConferenceRepository` por
    cada `GET /sandbox` (para saber `seatsPerPod`) -- costo aceptado, lectura local
    SQLite, mismo patron que ya usa el resto del handler.
  - Cualquier caso similar futuro (otro recurso que dependa de un Pod recien creado)
    deberia seguir el mismo patron: intento rapido no-bloqueante + status PENDING +
    polling del lado del cliente, no reintentos bloqueantes dentro del request.
- Reemplaza: `none`.

### DEC-0028 - Control de acceso por dispositivo, por evento (Jitsi/IDE)

- Fecha: 2026-07-19
- Estado: accepted
- Contexto:
  `GenerateJaasTokenUseCase` (token de Jitsi/JaaS) y `AssignSandboxUseCase` (asignacion de sandbox
  de IDE) eran completamente ciegos al dispositivo: un mismo `userUuid` podia pedir tokens de
  Jitsi ilimitadas veces desde cualquier cantidad de dispositivos simultaneos, y dos dispositivos
  con el mismo `userUuid` terminaban compartiendo trivialmente el mismo sandbox de IDE (sin ningun
  control). El pedido explicito fue reusar el fingerprint que ya se calculaba en el frontend
  (entonces un UUID en `localStorage`, ver DEC-0030 para su reemplazo por una huella real) para:
  limitar dispositivos activos por usuario, detectar/bloquear multicuenta compartiendo un mismo
  dispositivo, dejar auditoria, y bloquear acceso a Jitsi/IDE sin sesion-dispositivo vigente.
- Decision:
  - Nuevo `DeviceAccessGuard` (domain service), con `checkAndRegister(conferenceUuid, userUuid,
    tool, deviceFingerprint, conference)`, alcance **por conferencia** (no global):
    1. Dispositivo ya bloqueado en `device_blocks` para esta conferencia -> `Blocked`, sin contar
       de nuevo.
    2. Dispositivo nuevo para ese usuario+herramienta y ya al limite (`maxDevicesPerUser`, default
       2) -> revoca la sesion-dispositivo MAS VIEJA de ese usuario en esa herramienta (no rechaza
       el login nuevo, patron "un dispositivo activo a la vez" tipo streaming).
    3. Cuenta cuentas distintas activas compartiendo el fingerprint; si supera
       `maxAccountsPerDevice` (default 3) -> bloquea el dispositivo (`device_blocks`,
       `conference_uuid` incluido) y revoca TODAS sus sesiones activas en esa conferencia.
  - Tablas nuevas `tool_device_sessions` (una fila por dispositivo activo/revocado, por usuario,
    herramienta y conferencia) y `device_blocks` (bloqueos por conferencia, con cola de revision).
  - `Conference` gana `maxDevicesPerUser`/`maxAccountsPerDevice` (nullable, configurables por
    organizador en `ConferenceConfigPage.vue`, defaults en el guard si no se configuran).
  - Integrado en `GenerateJaasTokenUseCase.execute(...)` (nuevo parametro `deviceFingerprint`,
    retorno cambiado a `sealed interface JaasResult { NotConfigured, Blocked, Issued }` para no
    confundir "JaaS no configurado" con "dispositivo bloqueado") y en
    `AssignSandboxUseCase.execute(...)` (overload con `deviceFingerprint`, lanza
    `DeviceBlockedException` en bloqueo -> 403 `device_blocked`).
  - Frontend: header `X-Device-Fingerprint` agregado a `getJaasToken()`/`getSandbox()` en
    `usersApi.ts`; dashboard `/dashboard/conferences/{id}/device-blocks` (cola de revision, boton
    "Desbloquear") + item nuevo en el dropdown "Moderacion" de `ConferencesListPage.vue`.
- Consecuencias:
  - El fingerprint enviado en este momento era todavia el UUID de `localStorage` (ver DEC-0030) —
    el control funcionaba, pero la identificacion de dispositivo era debil (cualquiera podia
    "resetear" su dispositivo borrando datos del sitio). Se resolvio en DEC-0030.
  - El patron `DeviceAccessGuard` (revocar el mas viejo al exceder limite, bloquear+cola de
    revision al exceder cuentas compartidas) se reuso identico a nivel plataforma en DEC-0029.
- Reemplaza: `none`.

### DEC-0029 - Huella real (ThumbmarkJS) capturada en el login + control de abuso a nivel plataforma

- Fecha: 2026-07-19/20
- Estado: accepted
- Contexto:
  DEC-0028 resolvio el control por evento, pero seguia habiendo dos huecos: (1) el fingerprint
  seguia siendo un UUID de `localStorage`, no una huella real del navegador — trivial de resetear
  borrando datos del sitio; y (2) el control de abuso solo miraba Jitsi/IDE de UNA conferencia
  puntual, sin correlacionar nada entre eventos distintos ni proteger el login/registro en si. Un
  usuario podia repartir multicuenta entre varias conferencias para evadir el limite de una sola,
  y no existia ningun limite de sesiones simultaneas por usuario a nivel plataforma.
- Decision:
  - Se integro la libreria real `@thumbmarkjs/thumbmarkjs` (canvas, WebGL, audio, fuentes,
    hardware) en `frontend/web/src/services/auth/fingerprint.ts` — `new Thumbmark({timeout:
    3000}).get()`, con el UUID de `localStorage` como fallback si falla/tarda de mas.
  - El fingerprint ahora se captura en TODO login: `authStore.login()`, `loginAsGuest()` (ya lo
    mandaba, ahora con huella real) y `register()`. Se persiste en `tokens.device_fingerprint`
    (columna nueva, la fila que representa la sesion) y, para registro,
    `users.registration_device_fingerprint` (inmutable, fijado una sola vez).
  - Nuevo `PlatformDeviceGuard` (domain service), alcance **plataforma completa**, sin tabla de
    sesiones nueva -- reusa `tokens` (ya expira sola en 24h/8h, el conteo "se auto-limpia" solo):
    - `checkAndRegisterLogin(fingerprint, subjectUuid, kind, settings)`: si `kind==USER`, cuenta
      tokens activos de ese usuario; al alcanzar `maxSessionsPerUser` (default 3) revoca el mas
      viejo. Luego cuenta sujetos distintos compartiendo el fingerprint (tokens activos); si
      supera `maxAccountsPerDevice` (default 5) -> bloquea el dispositivo (`platform_device_blocks`,
      reason=MULTI_ACCOUNT) y revoca TODOS sus tokens activos (incluido el recien emitido).
    - `checkRegistration(fingerprint, settings)`: cuenta cuentas creadas desde ese fingerprint en
      24h (`countByRegistrationFingerprintSince`); al superar `maxRegistrationsPerDevicePerDay`
      (default 3) -> bloquea (reason=REGISTRATION_SPAM).
  - Integrado en `LoginUseCase`/`CreateGuestUseCase`/`RegisterUseCase`: en bloqueo, lanzan
    `PlatformDeviceBlockedException` -> `AuthHandler` responde 403 `platform_device_blocked`.
  - `PlatformSettings` (singleton ya existente, mismo que gobierna el kill-switch de IA del chat)
    gana los 3 umbrales, configurables desde `/dashboard/admin/device-access` (nuevo, admin-only) —
    misma pantalla lista dispositivos bloqueados a nivel plataforma con boton "Desbloquear".
- Consecuencias:
  - Un dispositivo puede pasar el control de plataforma (login normal) y aun asi ser bloqueado
    DENTRO de un evento especifico (DEC-0028) si abusa solo ahi, y viceversa — son capas
    independientes con sus propias tablas (`platform_device_blocks` vs `device_blocks`) y sus
    propias pantallas de revision.
  - `loginAsGuest()`/registro de nube de palabras ya tenian el parametro `deviceFingerprint` en su
    firma desde antes de esta iniciativa, pero ninguna pantalla lo completaba con un valor real —
    a partir de este cambio `loginAsGuest()` si lo manda (huella real, via `authStore`); el envio
    de dudas/temas de la nube de palabras sigue sin conectarlo (fuera de alcance).
- Reemplaza: `none`.

### DEC-0030 - Auditoria (no bloqueo) de fingerprint en cada request autenticado

- Fecha: 2026-07-20
- Estado: accepted
- Contexto:
  Con DEC-0028/DEC-0029, el fingerprint solo se validaba en dos momentos: login/registro (una vez)
  y Jitsi/IDE (por evento, en cada uso). El resto de las rutas autenticadas (dashboard, ver
  conferencias, etc.) nunca volvia a mirar el fingerprint — un token filtrado "pelado" (sin el
  contexto del navegador que lo genero: copiado de un log, un ticket de soporte, el Network tab)
  serviria igual desde cualquier lado sin ninguna señal de alerta. Se evaluo explicitamente
  bloquear en mismatch de cada request, y se descarto: ThumbmarkJS puede cambiar legitimamente
  entre requests (navegadores con foco en privacidad -- Firefox modo estricto, Brave, Tor Browser
  -- randomizan canvas/audio/WebGL a proposito para evadir fingerprinting), asi que bloquear
  hubiera generado deslogueos fantasma de usuarios legitimos sin frenar el escenario real de robo
  (un atacante con XSS en el mismo navegador de la victima puede recalcular la huella real igual).
- Decision:
  - Nuevo `DeviceFingerprintAuditor` (domain service, testeable, sin acceso a HTTP): compara el
    fingerprint entrante contra `Token.deviceFingerprint` (el guardado al login de esa sesion); si
    difieren, upsert en `device_fingerprint_flags` (una fila por SESION/token, no por request --
    incrementa `occurrence_count` en mismatches repetidos de la misma sesion en vez de duplicar
    filas). Nunca bloquea nada.
  - Adapter Jetty `DeviceFingerprintAuditHandler` (`Handler.Wrapper`), registrado como
    `JettyMiddleware` **global** al construir el server en `UsersApplication.java` (mecanismo del
    framework `ether-http-jetty12` que ya existia sin usar -- `JettyServerFactory.create(...)`
    acepta `List<JettyMiddleware>` como 6to argumento). Corre para TODA ruta sin tocar ninguno de
    los ~55 call-sites de `validateTokenUseCase.execute(token)` repartidos en 12 handlers
    (`ConferenceHandler` sola tiene 37). Envuelto en try/catch defensivo -- un fallo en la
    auditoria nunca debe romper el request real; siempre termina en `super.handle(...)`.
  - Frontend: interceptor global de `axios.interceptors.request.use` en `main.ts` adjunta
    `X-Device-Fingerprint` a toda llamada autenticada (si hay `ib_token` en `localStorage`) sin
    tocar cada funcion de `usersApi.ts`/`authApi.ts` individualmente.
  - Nueva seccion "Discrepancias de huella detectadas" en `/dashboard/admin/device-access`
    (listado + boton "Marcar revisado" -- nunca "desbloquear", porque nunca hubo bloqueo).
- Consecuencias:
  - El middleware agrega una consulta extra a `tokens` por request autenticado (duplica el trabajo
    que `ValidateTokenUseCase` hace igual despues, dentro del handler) -- costo aceptado a cambio
    de no tocar cada handler individualmente.
  - Este patron (middleware global via `JettyMiddleware`, antes sin usar en el proyecto) queda
    como el lugar recomendado para cualquier chequeo transversal futuro que deba correr en TODA
    ruta autenticada, en vez de repetir logica en cada handler.
  - Documentacion completa con diagramas de secuencia/flujo de las 3 capas (por evento, plataforma
    en login, auditoria por request) en `docs/device-fingerprinting.md` (marcado deprecado, ver
    nota en ese archivo -- la fuente de verdad es esta entrada de DECISIONS.md y
    `spec-native/specs/device-fingerprinting/SPEC.md`).
- Reemplaza: `none`.

### DEC-0031 - Publicación de Drawio con snapshot SVG y actualización best-effort

- Fecha: 2026-07-21
- Estado: accepted
- Contexto:
  En `MODERATOR_ONLY` los asistentes no deben recibir el editor de Drawio ni sus propios
  cambios. Necesitan, sin embargo, ver el material del moderador después de cada guardado.
- Decision:
  - El servicio Users conserva el XML nativo del moderador y el SVG publicado en la fila de la
    conferencia, junto con `diagram_version` y `diagram_updated_at`.
  - El editor solicita la exportación SVG mediante el protocolo JSON `postMessage` de Drawio y
    guarda ambas representaciones en una sola operación.
  - Los asistentes reciben únicamente el SVG en `MODERATOR_ONLY`. La actualización intenta ser
    inmediata mediante SSE autenticado; además existe polling de 30 segundos y un botón flotante
    de refresco.
  - El registro SSE vive en memoria del servicio Users. Se acepta porque la topología actual
    mantiene Users con SQLite y una sola instancia; si se escala horizontalmente, el bus deberá
    moverse a NATS u otro mecanismo distribuido.
- Consecuencias:
  - El SVG es la representación pública y no permite edición ni recuperación del XML desde la
    vista del asistente.
  - `EventSource` requiere el token `ib_token` en la query porque no permite headers Bearer; el
    endpoint valida el token antes de abrir el stream.
  - La publicación completa en artefactos versionados, más formatos y ZIP permanece pendiente.
- Reemplaza: `none`.

### DEC-0032 - Configuración centralizada del proveedor de IA desde el dashboard

- Fecha: 2026-07-24
- Estado: accepted
- Contexto:
  `chat`, `tutor`, `survey` y la generación asistida de mapas de asientos
  reutilizaban una API key, URL, modelo y prompt entregados por variables de
  despliegue. Eso obligaba a modificar GitOps para cambiar de proveedor y no
  permitía aislar modelos o prompts por capacidad.
- Decision:
  - `Dashboard → IA` contiene una pestaña por capacidad: `chat`, `tutor`,
    `survey` y `seat-layout`. Cada una tiene interruptor, URL base compatible
    con OpenAI, modelo, API key, prompt y temperatura propios.
  - `insightbloom-users` persiste los perfiles en `platform_settings`; cada API
    key se cifra con AES-GCM usando una clave derivada de `INTERNAL_API_KEY`.
  - El frontend solo recibe estado de configuración y una pista de los últimos
    cuatro caracteres de una clave explícitamente configurada. Los servicios
    consumidores consultan el contrato interno
    `/api/v1/settings/ai/internal` con `X-Internal-Auth`.
  - Un perfil no configurado puede heredar internamente el proveedor de chat
    como fallback, pero al guardarlo por primera vez debe recibir una clave
    propia; así no se obliga a compartir credenciales entre capacidades.
  - GitOps ya no contiene ni inyecta `LLM_PROVIDER_*` ni `ROBERTO_*`; el servicio
    usa defaults seguros sin clave y permanece sin llamadas hasta configurarse.
- Consecuencias:
  cambiar de proveedor no requiere redeploy; rotar la clave requiere acceso de
  administrador y la estabilidad de `INTERNAL_API_KEY`; si `users` no responde,
  las funciones de IA se deshabilitan temporalmente (fail-closed).
- Reemplaza: `DEC-0014` en lo relativo a la configuración del proveedor.

### DEC-0033 - Sesión compartida entre pestañas con expiración de una hora

- Fecha: 2026-07-31
- Estado: accepted
- Contexto:
  La sesión almacenada por pestaña obligaba a autenticarse de nuevo al abrir una
  herramienta en otra pestaña. Para permitir el flujo natural de un evento, la sesión
  debe compartirse, pero una credencial persistida no debe conservar la vigencia anterior
  de 24 horas para usuarios o 8 horas para invitados.
- Decision:
  - Los tokens de usuario e invitado expiran una hora después de emitirse o renovarse.
  - El frontend guarda el token y sus metadatos en `localStorage`, migra una sesión antigua
    de `sessionStorage` una sola vez y sincroniza login, logout, expiración y rotación entre
    pestañas mediante el evento `storage`.
  - La renovación es deslizante y best-effort: `useSessionManager` solo renueva cerca del
    vencimiento cuando detecta actividad reciente; sin actividad la sesión caduca.
  - Las pestañas coordinan la rotación con un lock efímero en `localStorage` para evitar que
    dos refresh simultáneos revoquen mutuamente el token vigente.
- Consecuencias:
  - Abrir varias pestañas del mismo perfil no requiere volver a iniciar sesión.
  - Un token inactivo queda inutilizable como máximo una hora después de su última emisión
    o renovación; logout y revocación siguen limpiando todas las pestañas.
  - `localStorage` aumenta la superficie de exposición frente a XSS, por lo que el TTL corto,
    la revocación del backend y el fingerprint de dispositivo siguen siendo controles
    obligatorios.
- Reemplaza: la decisión previa de mantener `ib_token` exclusivamente en `sessionStorage`.

### DEC-0034 - Acciones de entrega del IDE con permisos independientes

- Fecha: 2026-07-31
- Estado: accepted
- Contexto:
  El moderador debe decidir cuándo los asistentes pueden llevarse o publicar el trabajo del
  sandbox. Entrar al IDE no implica que puedan descargar el workspace, publicar una página
  temporal o exponer un backend/API.
- Decision:
  - `IDE_DOWNLOAD`, `IDE_PUBLISH_PAGE` e `IDE_PUBLISH_API` son claves independientes dentro del
    candado existente de herramientas.
  - Las tres acciones arrancan bloqueadas y el moderador puede liberarlas para todo el evento o
    para asistentes concretos desde `Moderación → Herramientas`.
  - La interfaz mantiene los botones visibles y deshabilitados con una explicación; abrir el IDE,
    copiar su URL y cambiar de modo permanecen disponibles.
  - `insightbloom-users` valida las tres claves en backend antes de ejecutar cada operación. Las
    revocaciones no se bloquean para que una publicación ya activa pueda retirarse.
- Consecuencias:
  - El control de cuándo se entrega o publica código queda explícito y auditable por asistente.
  - La matriz y la acción global existente pasan a gestionar 12 claves; no se crea otro sistema
    paralelo de permisos.
- Reemplaza: `none`.
