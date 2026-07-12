# SPEC: IDE web en sandbox por asistente (capacidad CODE_IDE)

## Initiative
code-ide-sandboxes

## Status
draft

## Summary
Agrega la capacidad `CODE_IDE`, dejada explícitamente fuera de
`event-types-catalog` (ver nota en `tasks/event-types-catalog/TASKS.md` y
DEC-0016) hasta definir sus reglas de seguridad de ejecución. Cada
asistente de un taller (`workshop`) recibe un IDE completo en el
navegador (code-server) corriendo en un pod propio dentro de un **pool
fijo** dimensionado por el organizador al configurar el evento — sin API
de creación de pods en runtime, sin RBAC delegado, superficie de ataque
mínima. Soporta Python, Java y HTML/JS/CSS con git, make y just
preinstalados; egress a internet configurable por taller (bandera, no
fija); una imagen base mínima con lista de paquetes adicional declarativa
por taller; visor de tablas SQLite; descarga del código en zip; y flujo
de git completo (init local + push a un remoto que el alumno se lleva, y
opción de partir de un remoto que el profesor comparte).

## Problem
`event-types-catalog` (DEC-0016) definió `workshop` como un tipo de
evento sin `CODE_IDE` porque ejecutar código arbitrario de asistentes
anónimos/registrados es la única capacidad de la plataforma con
superficie de ataque real hacia el propio cluster (todo lo demás son
iframes a herramientas ya aisladas — drawio/Etherpad/Excalidraw — o
llamadas a APIs con datos estructurados). Sin reglas de seguridad
explícitas, un IDE con terminal real y ejecución de código es la puerta
de entrada más peligrosa que se podría añadir a InsightBloom: fuga de
datos del namespace, pivoting hacia otros pods, o abuso del cluster para
minado/ataques salientes si se permite egress sin control.

## Objective
Un organizador puede crear un evento tipo `workshop` con `CODE_IDE`
habilitada, elegir el lenguaje principal (define la imagen base) y un
tamaño de pool de sandboxes; cada asistente que se une recibe una URL de
IDE propia (a través de `insightbloom-tools-gateway`, mismo patrón de
sesión que drawio/Etherpad/Excalidraw); el organizador puede alternar el
acceso a internet del taller en cualquier momento sin reiniciar
sandboxes activos; cada asistente trabaja con git desde el primer
momento (repo local ya inicializado, o clonado de un remoto que el
profesor comparte), puede descargar su código como zip, y puede conectar
un remoto propio para llevárselo; y todo el diseño respeta el hardening
non-root ya aplicado al resto de la plataforma (DEC reciente de
hardening de imágenes).

## Scope
### Includes
- Nueva capacidad `CODE_IDE` en el enum `EventCapability` (mismo patrón
  ya usado para las demás — ver DEC-0016), habilitable en el tipo de
  evento `workshop`.
- Imagen Docker propia `insightbloom-sandbox` basada en una distro
  mínima (Alpine), con code-server + git + make + just + sqlite3
  preinstalados, usuario no-root fijo (uid 1000, mismo estándar del
  resto de la plataforma), y **tres variantes de lenguaje** construidas
  desde la misma base: `python`, `java` (JDK), `web` (Node + navegador
  headless para preview, sin JDK/Python pesados). El tipo de evento
  elige la variante al configurar el taller.
- Extensión de VS Code para visor de tablas SQLite incluida por defecto
  en la imagen (marketplace Open VSX, sin llamada a la nube de
  Microsoft — ver Risks).
- **Lista de software adicional declarativa por taller**: campo de texto
  (una entrada por línea, ej. paquetes `apt`/`pip`/`npm` según la
  variante) que el organizador define al crear/editar el taller; se
  instala una sola vez al aprovisionar el pool de ese evento (no en cada
  sesión), como una capa extra sobre la imagen base — ver Non-Goals para
  lo que esto NO cubre.
- **Pool fijo por evento**: al habilitar `CODE_IDE`, el organizador
  define `sandbox_pool_size` (entero, con un máximo configurable a nivel
  de plataforma). Helm/el backend pre-crea ese número de pods de
  sandbox para el evento; el backend solo **asigna** un sandbox libre a
  cada asistente que se une (primer-uno-libre), nunca crea pods en
  runtime. Ningún componente de aplicación tiene RBAC para crear/borrar
  pods de Kubernetes.
- **Bandera de egress a internet por taller, cambiable en cualquier
  momento**: el organizador puede alternar `internet_enabled` desde la
  configuración del evento; el cambio se aplica a los sandboxes activos
  de ese evento sin necesidad de recrearlos (ver Non-Functional
  Requirements sobre el mecanismo).
- **Namespace dedicado** (`insightbloom-sandboxes`) con
  `NetworkPolicy` deny-all por defecto entre sandboxes y el resto del
  cluster (no ven la base de datos ni otros servicios de InsightBloom);
  egress a internet gateado por la bandera de arriba, vía un proxy con
  allowlist (PyPI, npm registry, Maven Central, GitHub) cuando está
  habilitada — nunca egress abierto sin filtro.
- `ResourceQuota`/`LimitRange` por sandbox (CPU/memoria/disco efímero) y
  a nivel de namespace, dimensionados según el análisis de capacidad ya
  hecho (ver Dependencies).
- Acceso a cada sandbox exclusivamente vía `insightbloom-tools-gateway`
  (mismo patrón de sesión `ib_token`/`ib_gw` que las demás herramientas,
  DEC-0022) — sin Ingress público directo al pod del sandbox. **Esta es
  la única puerta de entrada real**: solo un usuario registrado con
  sesión válida de InsightBloom llega a ver un sandbox; sin sesión, la
  misma página 401 "inicia sesión" que ya muestran drawio/Etherpad/
  Excalidraw (ver Functional Requirements, FR-011). El password propio
  de code-server (si se usa) es un secreto interno generado por el
  backend por sandbox, nunca expuesto ni gestionado por el usuario — el
  gateway lo inyecta de forma transparente al proxear la sesión ya
  autenticada; no es una segunda pantalla de login ni una credencial que
  el asistente deba recordar.
- Extensión del gateway para soportar upgrade a WebSocket (requisito
  técnico duro: VS Code web no tiene fallback sin websocket, a
  diferencia del socket.io de Etherpad) — ver Dependencies.
- Git de punta a punta dentro del sandbox: repo inicializado por
  defecto en el workspace; el profesor puede indicar un remoto git
  público (HTTPS, sin credenciales embebidas) del que el sandbox clona
  al aprovisionarse; el alumno puede agregar su propio remoto (ej. su
  fork) y hacer push con sus propias credenciales (nunca credenciales
  del organizador ni de la plataforma).
- Botón "Descargar código" que empaqueta el workspace del sandbox
  (excluyendo `.git` opcionalmente) en un zip servido a través del
  gateway.
- TTL de sandboxes por evento (mismo mecanismo de purga que
  drawio/Etherpad, DEC-0020): al vencer el evento, los pods del pool se
  destruyen y el namespace vuelve a estado vacío.

### Excludes / Non-Goals
- Ejecución de código fuera de un contenedor completo (sin WASM, sin
  API de ejecución tipo Judge0/Piston) — el requisito de git/make/
  compilación real exige una terminal real, no cabe en un sandbox
  puramente client-side.
- Pool **dinámico** (creación de pods bajo demanda vía RBAC delegado) —
  deliberadamente fuera de esta iniciativa: el pool fijo cubre la escala
  actual (ver Dependencies) con superficie de ataque mínima; el
  dinámico es una iteración futura si se supera esa escala.
- Persistencia del workspace entre sesiones del mismo asistente más allá
  de la duración del evento — el sandbox vive en `emptyDir`, se pierde
  al purgarse; el alumno debe descargar el zip o hacer push a su propio
  remoto antes de que termine el taller.
- Instalación de software **en vivo** durante la sesión más allá de lo
  que ya provee la variante de imagen + la lista declarativa del
  organizador (el asistente no tiene sudo ni permisos para instalar
  paquetes de sistema arbitrarios; sí puede usar `pip install`/`npm
  install` en su propio directorio de usuario si `internet_enabled` lo
  permite).
- Colaboración en tiempo real dentro del mismo sandbox (tipo Live Share)
  — cada asistente tiene su propio sandbox aislado, no hay edición
  compartida.
- Autenticación git federada (OAuth de GitHub/GitLab dentro del
  sandbox) — el alumno usa sus propias credenciales (token/SSH key que
  el pega manualmente), la plataforma no gestiona ni ve esas
  credenciales.

## Functional Requirements
- FR-001: al configurar un evento `workshop`, el organizador puede
  habilitar `CODE_IDE`, elegir la variante de lenguaje
  (`python`|`java`|`web`) y definir `sandbox_pool_size`.
- FR-002: el organizador puede definir una lista de paquetes adicionales
  (texto plano, una entrada por línea) que se instalan una sola vez al
  aprovisionar el pool del evento.
- FR-003: el organizador puede indicar una URL de repositorio git
  público del que cada sandbox clona su contenido inicial al
  aprovisionarse; si no se indica, el sandbox arranca con un repo git
  vacío ya inicializado.
- FR-004: el organizador puede alternar `internet_enabled` para el
  evento en cualquier momento; el cambio se refleja en los sandboxes
  activos de ese evento (ver NFR-002 sobre el mecanismo).
- FR-005: un asistente que se une a un taller con `CODE_IDE` habilitada
  recibe la asignación de un sandbox libre del pool y una URL para
  abrirlo (vía el gateway, con su sesión ya validada).
- FR-006: si no queda ningún sandbox libre en el pool, el asistente ve
  un mensaje claro ("taller lleno") en vez de un error genérico.
- FR-007: el sandbox incluye una extensión de VS Code para visualizar
  tablas de una base SQLite dentro del workspace.
- FR-008: el asistente puede descargar su workspace completo como un
  archivo zip desde el propio IDE.
- FR-009: al vencer el evento (mismo TTL que drawio/Etherpad), todos los
  pods del pool de ese evento se destruyen automáticamente.
- FR-010: ningún sandbox tiene alcance de red hacia otros pods de
  InsightBloom (base de datos, otros microservicios) bajo ninguna
  configuración de `internet_enabled`.
- FR-011: solo un usuario **registrado y con sesión válida** de
  InsightBloom puede llegar a ver o usar un sandbox — se reutiliza
  exactamente la misma autenticación que ya gatea drawio/Etherpad/
  Excalidraw (`ib_token`/`ib_gw` vía `insightbloom-tools-gateway`,
  DEC-0022), sin un sistema de login independiente para el IDE. Un
  visitante sin sesión que llegue a la URL directa del gateway para un
  sandbox ve la misma página de "inicia sesión", nunca el IDE.

## Non-functional Requirements
- NFR-001: ningún componente de la plataforma (backend, gateway,
  frontend) tiene permisos RBAC de Kubernetes para crear o eliminar
  pods en runtime — el pool se aprovisiona vía Helm/plantillas
  declarativas al configurar el evento, con un límite máximo de
  `sandbox_pool_size` por evento y un límite global de sandboxes
  concurrentes a nivel de plataforma (protege la capacidad del nodo).
- NFR-002: alternar `internet_enabled` debe aplicarse sin recrear el
  pod del sandbox — implementado como una `NetworkPolicy` por evento
  que el backend actualiza (un solo recurso declarativo, no requiere
  permisos de crear/borrar pods), o un feature flag leído por un
  egress-proxy sidecar; se decide el mecanismo exacto en el Execution
  Plan, pero en ningún caso implica RBAC de pods.
- NFR-003: todos los pods de sandbox siguen el mismo estándar de
  hardening ya aplicado a Excalidraw y el resto de la plataforma:
  `runAsNonRoot`, uid fijo no-root, `allowPrivilegeEscalation: false`,
  `capabilities: drop: [ALL]`, `seccompProfile: RuntimeDefault`.
- NFR-004: el gateway (`insightbloom-tools-gateway`) debe soportar
  upgrade a WebSocket antes de enrutar sandboxes — código actual usa
  `java.net.http.HttpClient` sin soporte de Upgrade (ver DEC-0022);
  esta iniciativa requiere extenderlo (Jetty 12 sí soporta proxy de
  WebSocket a nivel de servidor).
- NFR-005: el dimensionamiento de recursos por sandbox (CPU/memoria)
  debe basarse en el consumo real medido: ~1 CPU / hasta 1.5 GB para
  la variante `java` (la más pesada, por el language server + JVM),
  ~1 CPU / hasta 800 MB para `python`/`web`.
- NFR-006: la lista de paquetes adicionales (FR-002) se ejecuta solo
  durante el aprovisionamiento del pool (init container o build-time
  de una capa de imagen efímera), nunca durante la sesión activa del
  asistente — evita que la instalación de paquetes sea un vector de
  ejecución arbitraria en caliente.

## Acceptance Criteria
### Scenario 1 — Organizador configura un taller de Python con pool de 15
- **Given** un organizador editando un evento `workshop`
- **When** habilita `CODE_IDE`, elige variante `python` y define
  `sandbox_pool_size = 15`
- **Then** el sistema aprovisiona 15 sandboxes Python para ese evento,
  ninguno accesible públicamente sin sesión.

### Scenario 2 — Asistente recibe su sandbox
- **Given** un asistente con sesión válida en un taller con `CODE_IDE`
  habilitada y sandboxes libres
- **When** entra a la pestaña de IDE
- **Then** se le asigna un sandbox libre y ve su IDE (code-server)
  cargado en el navegador, con git ya inicializado.

### Scenario 3 — Taller lleno
- **Given** un taller cuyo pool de sandboxes está completamente asignado
- **When** un nuevo asistente intenta entrar a la pestaña de IDE
- **Then** ve un mensaje claro de "taller lleno", sin error genérico ni
  caída del gateway.

### Scenario 4 — Organizador desactiva internet a mitad del taller
- **Given** un taller en curso con `internet_enabled = true`
- **When** el organizador lo cambia a `false` desde la configuración
  del evento
- **Then** los sandboxes activos pierden acceso a internet sin
  reiniciarse ni perder el workspace del asistente.

### Scenario 5 — Alumno descarga su código
- **Given** un asistente trabajando en su sandbox
- **When** presiona "Descargar código"
- **Then** recibe un zip con su workspace actual a través del gateway.

### Scenario 6 — Aislamiento de red confirmado
- **Given** cualquier sandbox del pool, con `internet_enabled` en
  cualquier valor
- **When** intenta alcanzar la IP interna de `insightbloom-users` o
  cualquier otro Service de InsightBloom
- **Then** la conexión es rechazada por la `NetworkPolicy` del
  namespace `insightbloom-sandboxes`.

### Scenario 7 — Sin sesión no hay acceso al IDE
- **Given** un visitante sin sesión de InsightBloom (o con sesión
  vencida) que obtiene o adivina la URL directa de un sandbox a través
  del gateway
- **When** intenta abrirla
- **Then** ve la misma página de "inicia sesión en InsightBloom" que ya
  muestran drawio/Etherpad/Excalidraw, sin llegar nunca al IDE ni al pod
  del sandbox — no existe una vía de acceso al sandbox que no pase por
  la sesión de la plataforma.

## Dependencies
- `event-types-catalog` (DEC-0016): `CODE_IDE` se agrega al mismo enum
  `EventCapability` y sigue el mismo patrón de gating por tipo de
  evento.
- `insightbloom-tools-gateway` (DEC-0022): reutilizado como único punto
  de acceso autenticado a los sandboxes; requiere la extensión de
  WebSocket (NFR-004) antes de poder enrutar code-server.
- Hardening non-root de imágenes (sesión reciente, ver DECISIONS.md):
  la imagen `insightbloom-sandbox` sigue el mismo estándar desde el
  día uno, no como corrección posterior.
- Análisis de capacidad del nodo (evaluado en conversación, no
  documentado previamente): nodo k3s actual con 12 CPU/32 GB soporta
  ~15-18 sandboxes Java concurrentes o ~25-30 Python/web sin hardware
  adicional; escala mayor requiere unir un nodo agente temporal
  (dimensionamiento fuera del alcance de esta SPEC, es una decisión
  operativa por evento).

## Risks
- Ejecución de código de terceros es, por definición, la superficie de
  ataque más sensible de toda la plataforma — mitigación: pool fijo sin
  RBAC de pods (NFR-001), NetworkPolicy deny-all (FR-010), hardening
  non-root (NFR-003), y egress con allowlist en vez de abierto cuando
  `internet_enabled = true`.
- `internet_enabled = true` con allowlist reduce pero no elimina el
  riesgo de exfiltración de datos hacia dominios permitidos (ej. subir
  contenido sensible a un gist de GitHub) — mitigación: estos sandboxes
  no tienen acceso a ningún dato de InsightBloom aparte del propio
  workspace del asistente (FR-010), así que no hay datos sensibles de
  la plataforma que exfiltrar.
- La instalación de paquetes adicionales declarados por el organizador
  (FR-002) podría incluir un paquete malicioso si el organizador mismo
  actúa de mala fe — mitigación: el organizador ya es una identidad
  registrada y responsable de su propio evento (mismo nivel de
  confianza que ya se deposita en quien sube un flyer o un plan de
  certificado); no es un vector nuevo respecto a lo que ya puede hacer
  hoy con contenido propio.
- Un pool fijo mal dimensionado (`sandbox_pool_size` muy alto) podría
  agotar la capacidad del nodo y degradar el resto de la plataforma —
  mitigación (NFR-001): límite máximo de plataforma sobre
  `sandbox_pool_size` y sobre el total de sandboxes concurrentes,
  validado contra la capacidad real del nodo antes de aprovisionar.
- La extensión SQLite viewer y cualquier otra extensión preinstalada
  deben venir del marketplace Open VSX (no del Marketplace oficial de
  Microsoft, cuyos términos de uso no permiten forks/distribuciones de
  VS Code como code-server) — mitigación: usar solo extensiones
  publicadas en Open VSX, empaquetadas en la imagen en build-time.

## Execution Plan
-> `tasks/code-ide-sandboxes/TASKS.md`

## Validation Plan
- Manual: crear un taller con pool de 3, unir 3 asistentes distintos,
  confirmar que cada uno ve su propio sandbox aislado con git
  funcionando; intentar un cuarto asistente y confirmar el mensaje de
  "taller lleno"; alternar `internet_enabled` y confirmar el cambio de
  comportamiento sin reiniciar sesiones; intentar `curl`/`nc` desde un
  sandbox hacia `insightbloom-users` interno y confirmar rechazo.
- Automatizado: tests de los nuevos casos de uso (asignación de
  sandbox libre, agotamiento de pool, toggle de `internet_enabled`)
  con fakes de repositorio, igual que el resto de la suite de
  `insightbloom-users`.
- Evidencia esperada: `helm template` renderiza el namespace
  `insightbloom-sandboxes` con NetworkPolicy deny-all + allowlist
  condicional; verificación en vivo (`kubectl exec` en un sandbox) de
  que no alcanza ningún Service interno de InsightBloom.
