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
