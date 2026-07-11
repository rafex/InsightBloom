# SPEC: Catalogo de tipos de evento administrado por ADMIN

## Initiative
event-types-catalog

## Status
draft

## Summary
Generalizar `Conference` a `Event` sin fijar un enum cerrado de tipos en
codigo. El rol `ADMIN` administra un catalogo de **tipos de evento**
(conferencia, taller, standup, concierto, tocada, ...); cada tipo declara
que **capacidades** ("tecnologias") de la plataforma habilita (boletos con
aforo, mapa de asientos, encuestas, presentaciones, chat, nube de palabras,
videollamada/transmision via Jitsi, pizarra colaborativa Excalidraw,
diagramas drawio, notas colaborativas Etherpad, encuestas con motor
alternativo SurveyJS, mapa de asientos con motor alternativo
seatmap-canvas para recintos con distribucion real de filas/butacas, y a
futuro IDE de codigo). El organizador elige un tipo del catalogo al crear
un evento; la UI y las APIs se adaptan segun las capacidades activas de ese
tipo, en vez de comparar contra un tipo hardcodeado.

## Problem
Hoy `Conference` es el unico concepto de evento posible. Con la llegada de
boletos digitales (GENERAL y SEATED) tiene sentido soportar mas formatos:
standup, concierto, tocada, taller, etc. Fijar estos tipos como un enum en
codigo obligaria a un release por cada tipo nuevo. El organizador tampoco
deberia decidir que "tecnologias" trae cada tipo — eso es una decision de
plataforma que le corresponde al `ADMIN`. Ademas, varios de estos formatos
(taller, standup remoto, evento con transmision) necesitan herramientas de
colaboracion en vivo que la plataforma no ofrece hoy: videollamada,
pizarra, diagramas y notas compartidas.

## Objective
El `ADMIN` puede crear, editar y desactivar tipos de evento desde el
dashboard, marcando que capacidades trae cada uno (de un catalogo fijo de
capacidades que si vive en codigo). El organizador, al crear un evento,
elige entre los tipos de evento activos. La vista del evento y las rutas
del backend muestran/permiten solo lo que la capacidad del tipo habilita,
sin que el codigo compare contra nombres de tipo especificos.

## Scope
### Includes
- Catalogo de capacidades fijo en codigo (la "lista de tecnologias
  disponibles"), ampliable en un release futuro: `TICKETING_GENERAL`
  (aforo), `TICKETING_SEATED` (mapa de asientos), `SURVEY`, `PRESENTATION`,
  `WORD_CLOUD` (dudas/temas), `CHAT_BOT`, `VIDEO_CONFERENCE` (Jitsi),
  `WHITEBOARD` (Excalidraw), `DIAGRAMMING` (drawio),
  `COLLAB_NOTES` (Etherpad). `CODE_IDE` y `CERTIFICATE_PDFME` quedan
  reservados en el catalogo para una iteracion futura (ver Excludes) — se
  nombran aqui para que el `ADMIN` entienda que existen aunque todavia no
  esten disponibles para ningun tipo de evento.
- Integracion de videollamada/transmision via **Jitsi**, con dos modos
  soportados y seleccionables: instancia publica `meet.jit.si` (sin
  infraestructura propia, limites y marca de Jitsi) y una instancia propia
  autoalojada en el K3s del proyecto (sin limites de la instancia publica,
  requiere el Helm chart de Jitsi Meet).
- Integracion de pizarra colaborativa via una instancia propia de
  **Excalidraw** autoalojada en K3s (editor + servidor de colaboracion en
  vivo).
- Integracion de diagramas via una instancia propia de **drawio**
  (diagrams.net) autoalojada en K3s, embebida como editor.
- Integracion de notas colaborativas via una instancia propia de
  **Etherpad** autoalojada en K3s, con un pad por evento creado via su API
  HTTP.
- Motor de encuestas alternativo via **SurveyJS**: el organizador elige, por
  encuesta, si la construye con el editor propio actual de InsightBloom
  (`NATIVE`) o con el editor visual de SurveyJS (`SURVEYJS`, drag-and-drop,
  mas tipos de pregunta que el motor propio). Ambos motores guardan sus
  datos en el mismo backend `insightbloom-survey`; SurveyJS es una libreria
  de frontend (sin servidor propio que auto-alojar), la definicion de la
  encuesta se persiste como el JSON schema nativo de SurveyJS.
- Motor de mapa de asientos alternativo via **seatmap-canvas**
  (`alisaitteke/seatmap-canvas`): para `TICKETING_SEATED`, el organizador
  elige entre el editor propio actual (`FREEFORM`, marcadores libres sobre
  una imagen del recinto, ya construido en la Fase 2 de ticketing) o
  `SEATMAP_CANVAS`, pensado para recintos con distribucion real de
  filas/secciones/butacas numeradas (teatros, auditorios con asientos
  fijos, salas de cine). `FREEFORM` sigue siendo mejor para recintos sin
  layout fijo (mesas libres, espacios abiertos); `SEATMAP_CANVAS` es mejor
  cuando el recinto realmente tiene asientos numerados que reservar segun
  la compra.
- Entidad `EventType` administrada por `ADMIN`: nombre, slug, descripcion,
  lista de capacidades activas, activo/inactivo.
- Dos tipos de evento sembrados por defecto y editables despues:
  "Conferencia" (todas las capacidades actuales) y "Taller" (mismas
  capacidades de conferencia; sin `CODE_IDE` todavia, ver Excludes).
- `Conference` gana una referencia a `EventType` (`event_type_key`), con
  compatibilidad hacia atras: todo evento existente sin tipo explicito
  apunta al tipo sembrado "Conferencia".
- Endpoint y UI de administracion de tipos de evento (`ADMIN`-only).
- Selector de tipo de evento al crear/editar un evento (organizer), listando
  solo tipos activos.
- Las pestañas/paginas existentes (boletos, encuestas, presentaciones, nube
  de palabras, chat) se muestran u ocultan segun la capacidad del tipo del
  evento, no segun un nombre de tipo hardcodeado.
### Excludes
- IDE web y ejecucion de codigo (capacidad `CODE_IDE`): queda fuera de esta
  iniciativa. Se definira en una iniciativa separada una vez que las reglas
  de seguridad de ejecucion esten decididas (ver Dependencies). Nota de
  direccion (sin decidir, solo registrada para no perderla): en vez del
  enfoque original de un microservicio propio orquestando Jobs de
  ejecucion por snippet (`insightbloom-runtime`, ver version anterior de
  este spec), evaluar levantar un **IDE completo en el navegador** por
  asistente/evento usando `code-server` (coder/code-server) u
  `openvscode-server` (gitpod-io/openvscode-server) — ambos exponen VS Code
  completo via HTTP dentro de un contenedor, con terminal integrada, lo que
  resuelve ejecucion multi-lenguaje y previsualizacion HTML/CSS (via
  port-forward del propio servidor de desarrollo) sin construir un motor de
  ejecucion a medida. Esta evaluacion no cambia el requisito de fondo:
  sigue siendo ejecucion de codigo no confiable y sigue bloqueada hasta
  definir las reglas de seguridad (aislamiento por pod efimero, sin red
  saliente, limites de recursos, mismo espiritu que el resto de esta
  iniciativa).
- Cobro o pagos sobre boletos (ya excluido en la iniciativa de ticketing).
- Permitir que el organizador cree capacidades nuevas — las capacidades son
  un catalogo cerrado de plataforma, solo los tipos de evento (que capacidad
  combinan) son administrables.
- Migrar datos de conferencias existentes a un nuevo tipo distinto de
  "Conferencia" — el `ADMIN` puede reclasificarlas manualmente si quiere,
  pero no es parte de esta iniciativa.
- Grabacion o almacenamiento de las videollamadas de Jitsi.
- Autenticacion JWT de Jitsi self-hosted para restringir moderadores (queda
  como riesgo/mejora futura; la primera version usa salas abiertas por
  nombre de sala derivado del evento, ver Risks).
- Persistencia de diagramas de drawio en el backend propio — la primera
  version embebe el editor sin guardar el archivo en InsightBloom (el
  usuario exporta/descarga desde la propia UI de drawio).
- Migrar encuestas `NATIVE` existentes a `SURVEYJS` o viceversa — el motor
  se elige al crear la encuesta y no es editable despues (evita traducir
  entre dos modelos de pregunta distintos).
- Calificacion automatica con LLM (DEC-0014) para encuestas `SURVEYJS` en
  esta primera version — sigue disponible solo para el motor `NATIVE`.
- Migrar un evento `FREEFORM` existente a `SEATMAP_CANVAS` o viceversa — el
  motor de mapa de asientos se elige al activar `TICKETING_SEATED` y no es
  editable despues sin borrar y recrear el mapa (evita traducir marcadores
  libres a filas/butacas numeradas y viceversa).
- Categorias de precio o tarifas diferenciadas por seccion en
  `SEATMAP_CANVAS` — la primera version solo distingue asiento libre/tomado,
  igual que `FREEFORM` hoy (sin pagos, ver ROADMAP "No hacer por ahora").
- `CERTIFICATE_PDFME`: motor alternativo de certificados de participacion
  via **pdfme** (generador + diseñador visual de plantillas PDF, MIT,
  sin las dudas de licencia de SurveyJS), como alternativa al configurador
  de certificados actual (`CertificateSettingsPage.vue` +
  `GenerateCertificateUseCase`). Se nombra en el catalogo de capacidades
  para dejar reservado el espacio, pero **su implementacion queda fuera de
  esta iniciativa** — se detalla en una iniciativa separada cuando se
  priorice. La idea (a validar en esa iniciativa futura): el organizador
  diseña la plantilla del certificado arrastrando campos sobre un PDF base
  con el editor visual de pdfme (`@pdfme/ui`), en vez de los campos fijos
  actuales (logo, tipografia, colores, mostrar/ocultar sede y fecha); la
  generacion final sigue siendo efimera (no se persiste el PDF, mismo
  criterio que hoy), solo cambia como se define el layout.

## Functional Requirements
- FR-001: un usuario con rol `ADMIN` puede crear un tipo de evento con
  nombre, slug unico y una lista de capacidades elegidas del catalogo fijo.
- FR-002: un `ADMIN` puede editar el nombre, descripcion y capacidades de un
  tipo de evento existente, y activarlo/desactivarlo.
- FR-003: desactivar un tipo de evento no afecta a los eventos ya creados
  con ese tipo; solo deja de aparecer como opcion para eventos nuevos.
- FR-004: al crear un evento, el organizador elige un tipo entre los tipos
  de evento activos (no puede escribir uno libre).
- FR-005: todo evento existente antes de esta iniciativa se comporta como
  si tuviera el tipo sembrado "Conferencia", sin requerir migracion manual.
- FR-006: el organizador puede cambiar el tipo de un evento despues de
  creado, siempre que el tipo destino siga activo.
- FR-007: la vista del evento (frontend) solo muestra pestañas cuya
  capacidad este activa en el tipo de evento actual (ej. la pestaña
  "Boletos"/"Mi boleto" solo aparece si el tipo tiene `TICKETING_GENERAL` o
  `TICKETING_SEATED`).
- FR-008: las rutas HTTP que dependen de una capacidad (ej. reservar boleto,
  gestionar encuesta) deben rechazar la operacion con un error claro si el
  tipo de evento actual no tiene esa capacidad activa.
- FR-009: el catalogo de capacidades disponibles (no los tipos, las
  capacidades en si) se expone via un endpoint de solo lectura para que el
  `ADMIN` pueda armar el formulario de tipos sin hardcodear opciones en el
  frontend.
- FR-010: si el evento tiene la capacidad `VIDEO_CONFERENCE`, el organizador
  elige el proveedor de Jitsi (`meet.jit.si` publico o instancia self-hosted
  del K3s del proyecto) al configurar el evento; la vista del evento muestra
  una pestaña "Videollamada" que embebe la sala correspondiente.
- FR-011: el nombre de sala de Jitsi se deriva de forma deterministica del
  `uuid` del evento (ej. `insightbloom-{uuid}`), para que todos los
  asistentes lleguen a la misma sala sin coordinacion manual.
- FR-012: si el evento tiene la capacidad `WHITEBOARD`, la vista del evento
  muestra una pestaña "Pizarra" que embebe una sala de la instancia propia
  de Excalidraw, tambien derivada del `uuid` del evento.
- FR-013: si el evento tiene la capacidad `DIAGRAMMING`, la vista del evento
  muestra una pestaña "Diagramas" que embebe la instancia propia de drawio.
- FR-014: si el evento tiene la capacidad `COLLAB_NOTES`, el backend crea
  (de forma perezosa, en el primer acceso) un pad de Etherpad con
  `padID = event.uuid` via la API HTTP de Etherpad, y la vista del evento
  muestra una pestaña "Notas" que lo embebe.
- FR-015: el `ADMIN` configura, a nivel de plataforma (no por evento), las
  URLs base de las instancias self-hosted (Jitsi propio, Excalidraw, drawio,
  Etherpad con su API key) via variables de entorno, siguiendo el mismo
  mecanismo ya usado para `LLM_PROVIDER_BASE_URL` u otras integraciones
  externas.
- FR-016: si el evento tiene la capacidad `SURVEY`, el organizador elige el
  motor al crear cada encuesta: `NATIVE` (editor propio actual) o
  `SURVEYJS` (editor visual SurveyJS Creator).
- FR-017: una encuesta `SURVEYJS` persiste su definicion como el JSON
  schema nativo de SurveyJS (`survey-core`) en `insightbloom-survey`, y sus
  respuestas se guardan con la misma asociacion evento/asistente que las
  encuestas `NATIVE`.
- FR-018: el asistente responde una encuesta `SURVEYJS` a traves del
  componente `Survey` (render) de SurveyJS embebido en la misma pagina de
  encuesta existente, sin una URL ni flujo separado.
- FR-019: los resultados de una encuesta `SURVEYJS` se listan en el mismo
  dashboard de resultados que las encuestas `NATIVE`, aunque el detalle
  pregunta-por-pregunta pueda diferir en formato dado que el modelo de
  pregunta de SurveyJS es mas amplio que el del motor propio.
- FR-020: al activar `TICKETING_SEATED` para un evento, el organizador
  elige el motor de mapa de asientos: `FREEFORM` (editor de marcadores
  libres ya existente) o `SEATMAP_CANVAS` (filas, secciones y butacas
  numeradas via `seatmap-canvas`).
- FR-021: con motor `SEATMAP_CANVAS`, el organizador define la
  distribucion del recinto (filas, cantidad de asientos por fila,
  numeracion, secciones opcionales) en vez de hacer clic libre sobre una
  imagen; la definicion se persiste como la configuracion nativa de
  `seatmap-canvas` (filas/secciones), no como pares de coordenadas x/y
  sueltos (a diferencia de `FREEFORM`).
- FR-022: con motor `SEATMAP_CANVAS`, el asistente ve el mismo mapa con
  asientos ocupados deshabilitados/en gris y libres seleccionables, y la
  reserva sigue el mismo flujo de `ReserveSeatUseCase` ya existente (mismo
  `UNIQUE(conference_uuid, seat_uuid)` para la concurrencia, ver DEC en
  `docs`/`spec-native` de la iniciativa de ticketing) — el motor solo
  cambia como se define y renderiza el layout, no como se reserva.
- FR-023: el organizador puede exportar/reutilizar la distribucion de un
  recinto `SEATMAP_CANVAS` para crear otro evento en el mismo lugar, sin
  tener que redefinir filas y butacas cada vez (ej. un recinto fijo con
  eventos recurrentes).

## Non-functional Requirements
- NFR-001: seguir el patron ya usado en la tabla `conferences` para
  compatibilidad hacia atras (migracion idempotente try/catch, valor por
  defecto que preserva el comportamiento actual).
- NFR-002: el catalogo de capacidades vive en un solo lugar en codigo
  (backend) y se reutiliza tanto para validar tipos de evento como para
  gatear rutas — no debe duplicarse la lista en frontend y backend de forma
  independiente (el frontend la consulta via FR-009).
- NFR-003: la administracion de tipos de evento requiere rol `ADMIN`,
  reutilizando el mecanismo de autorizacion ya existente (DEC-0011).
- NFR-004: agregar una capacidad nueva al catalogo fijo debe ser un cambio
  acotado (un enum + su gate correspondiente), sin tocar el modelo de
  `EventType` ni la tabla que lo persiste.
- NFR-005: las instancias self-hosted (Jitsi propio, Excalidraw, drawio,
  Etherpad) se despliegan como charts Helm independientes bajo
  `infra/helm/charts/`, siguiendo el mismo patron de despliegue K3s ya usado
  para el resto de la plataforma (no se adoptan SaaS de pago para estas
  herramientas).
- NFR-006: si una integracion self-hosted no esta configurada o no responde,
  la pestaña correspondiente debe degradar con un mensaje claro (ej. "la
  pizarra no esta disponible en este momento") sin afectar el resto del
  evento, mismo criterio que NFR-005 de la iniciativa de ticketing.
- NFR-007: ninguna credencial (API key de Etherpad, JWT secret de Jitsi
  self-hosted) se versiona en el repositorio; se inyecta via variables de
  entorno y Kubernetes secrets, igual que Twilio/Zoho (DEC-0013).

## Acceptance Criteria
### Scenario 1 — Compatibilidad hacia atras
- **Given** una conferencia creada antes de esta iniciativa
- **When** se consulta despues del cambio
- **Then** aparece con el tipo "Conferencia" y todas sus capacidades
  actuales siguen funcionando exactamente igual.

### Scenario 2 — Admin crea un tipo de evento nuevo
- **Given** un usuario con rol `ADMIN`
- **When** crea el tipo "Standup" con capacidades `TICKETING_GENERAL` y
  `CHAT_BOT` unicamente
- **Then** el tipo aparece disponible para organizadores al crear un evento,
  y un evento de tipo "Standup" no muestra pestañas de encuesta ni
  presentacion.

### Scenario 3 — Organizador elige tipo de evento
- **Given** un organizador autenticado creando un evento
- **When** abre el selector de tipo de evento
- **Then** solo ve los tipos activos, con su nombre y (opcionalmente) que
  capacidades incluye.

### Scenario 4 — Capacidad desactivada bloquea la ruta
- **Given** un evento de un tipo sin `TICKETING_SEATED`
- **When** se intenta llamar al endpoint de definir mapa de asientos
- **Then** el sistema responde con un error claro de capacidad no
  disponible, sin ejecutar la operacion.

### Scenario 5 — Desactivar un tipo no rompe eventos existentes
- **Given** un tipo de evento "Taller" con eventos ya creados
- **When** el `ADMIN` lo desactiva
- **Then** los eventos existentes de tipo "Taller" siguen funcionando igual;
  solo deja de ofrecerse como opcion para eventos nuevos.

### Scenario 6 — Videollamada con Jitsi publico
- **Given** un evento con capacidad `VIDEO_CONFERENCE` configurado con
  proveedor `meet.jit.si`
- **When** un asistente abre la pestaña "Videollamada"
- **Then** se une a la sala derivada del `uuid` del evento en `meet.jit.si`
  sin necesidad de configuracion adicional.

### Scenario 7 — Videollamada con Jitsi self-hosted
- **Given** un evento con capacidad `VIDEO_CONFERENCE` configurado con
  proveedor self-hosted, y la instancia propia desplegada en K3s
- **When** un asistente abre la pestaña "Videollamada"
- **Then** se une a la sala derivada del `uuid` del evento en la instancia
  propia, sin salir del dominio de la plataforma.

### Scenario 8 — Integracion self-hosted no disponible
- **Given** un evento con capacidad `WHITEBOARD` activa pero la instancia de
  Excalidraw self-hosted esta caida o no configurada
- **When** un asistente abre la pestaña "Pizarra"
- **Then** ve un mensaje claro de indisponibilidad en vez de un error crudo
  o una pantalla en blanco, y el resto del evento sigue funcionando.

### Scenario 9 — Encuesta construida con SurveyJS
- **Given** un organizador creando una encuesta para un evento con capacidad
  `SURVEY`
- **When** elige el motor `SURVEYJS` y arma la encuesta con el editor visual
- **Then** el asistente responde la encuesta con el componente de render de
  SurveyJS, y las respuestas quedan asociadas al evento y al asistente igual
  que con el motor `NATIVE`.

### Scenario 10 — Mapa de asientos real con seatmap-canvas
- **Given** un organizador configurando `TICKETING_SEATED` para un teatro
  con filas y butacas numeradas reales
- **When** elige el motor `SEATMAP_CANVAS` y define filas/secciones en vez
  de marcadores libres
- **Then** el asistente ve el mismo layout de filas/butacas al reservar, con
  los asientos ya tomados deshabilitados, y la reserva usa el mismo
  mecanismo de concurrencia (`UNIQUE` + 409 `seat_already_taken`) que
  `FREEFORM`.

## Dependencies
- Rol `ADMIN` ya existente (DEC-0011) — se reutiliza tal cual, sin nuevos
  roles.
- La capacidad `CODE_IDE` (IDE web + ejecucion de codigo) queda **bloqueada**
  hasta que el usuario defina las reglas de seguridad de ejecucion; cuando
  eso ocurra, se agrega como una iniciativa separada que solo necesita sumar
  una capacidad al catalogo fijo y su gate correspondiente — no requiere
  tocar `EventType` ni esta iniciativa de nuevo. Cuando esa iniciativa se
  abra, evaluar como punto de partida `code-server` vs `openvscode-server`
  en vez de (o ademas de) el motor de Jobs por lenguaje original:
  - `code-server` (coder/code-server): mas adopcion, imagen oficial
    mantenida por Coder, soporta extensiones del Open VSX Registry,
    licencia MIT.
  - `openvscode-server` (gitpod-io/openvscode-server): mantenido por
    Gitpod, mismo enfoque (VS Code servido via HTTP), tambien MIT,
    historicamente mas cercano al upstream de VS Code en como empaqueta el
    servidor web.
  Ninguno resuelve por si solo el aislamiento de ejecucion (siguen
  ejecutando codigo del asistente dentro del contenedor) — la decision de
  aislamiento (pod efimero, sin red, limites de recursos) sigue siendo
  necesaria independientemente de cual se elija.
- La capacidad `CERTIFICATE_PDFME` queda **reservada, sin iniciativa propia
  todavia**; se abre cuando se priorice, y solo necesita sumar el enum al
  catalogo fijo mas su propia spec de implementacion (motor de certificado,
  editor de plantillas, migracion desde `CertificateSettings` actual) — no
  requiere tocar `EventType` ni esta iniciativa de nuevo.
- Despliegue de 4 instancias self-hosted nuevas en el K3s del proyecto:
  Jitsi Meet, Excalidraw (+ su servidor de colaboracion), drawio, Etherpad.
  Cada una necesita su propio Helm chart, recursos de computo dedicados y,
  en el caso de Etherpad, una API key generada al desplegar.
- Cuenta/dominio de `meet.jit.si` no requiere credenciales (uso publico),
  pero esta sujeto a los limites y terminos de servicio de 8x8/Jitsi.
- Paquetes npm de SurveyJS (`survey-core`, `survey-js-ui` para el render,
  `survey-creator-core` + `survey-creator-js` para el editor visual). Las
  librerias core (form/render) son MIT y libres de usar; **verificar antes
  de implementar** la licencia vigente de `survey-creator-*` (historicamente
  tiene un limite de uso gratuito con marca de agua fuera de ese limite,
  sujeto a cambios de SurveyJS) — si aplica un costo o restriccion, decidir
  si el `ADMIN` puede activar `SURVEYJS` igual y aceptar la marca de agua,
  o si se pospone hasta confirmar los terminos.
- Paquete npm `seatmap-canvas` (`alisaitteke/seatmap-canvas`, MIT) —
  verificar antes de implementar que el proyecto siga mantenido/compatible
  con la version de Vue/Vite del frontend (es una libreria orientada a
  canvas vanilla-JS, no un componente Vue nativo; puede requerir un wrapper
  delgado en `SeatMapCanvasPicker.vue` similar al ya usado para `qrcode` o
  `qr-scanner`).

## Risks
- Si el catalogo de capacidades crece mucho, la matriz tipo x capacidad
  puede volverse dificil de razonar para el `ADMIN` — mitigacion: agrupar
  capacidades por categoria en la UI de administracion.
- Gatear rutas por capacidad en tiempo de request agrega una consulta extra
  (tipo de evento) por operacion sensible — mitigacion: cachear el tipo de
  evento en memoria del proceso con invalidacion simple al editarlo, si el
  costo se vuelve medible.
- Cambiar el tipo de un evento despues de tener boletos emitidos podria
  dejar capacidades "huerfanas" (ej. reservas SEATED en un evento que pasa a
  un tipo sin `TICKETING_SEATED`) — mitigacion: bloquear el cambio de tipo
  si hay datos incompatibles activos, mismo criterio ya usado en
  `SetSeatingModeUseCase` para `SEATED`.
- Salas de Jitsi/Excalidraw sin autenticacion son accesibles por cualquiera
  que adivine o reciba el nombre de sala derivado del `uuid` — mitigacion:
  el `uuid` del evento ya es un identificador no adivinable; si se requiere
  mas control (moderador vs asistente) se evalua JWT de Jitsi en una
  iteracion posterior (ver Excludes).
- Cuatro instancias self-hosted nuevas aumentan la superficie operativa
  (mas Helm charts, mas pods para monitorear, mas consumo de recursos del
  cluster) — mitigacion: desplegarlas con replicas minimas y limites de
  recursos conservadores, igual que el resto de la plataforma.
- La instancia publica `meet.jit.si` puede aplicar rate limiting o cambiar
  sus terminos de servicio sin aviso, al ser un servicio de terceros fuera
  de control del proyecto — mitigacion: dejar el self-hosted como
  alternativa ya soportada desde el dia uno (no como plan B tardio).
- Mantener dos motores de encuesta (`NATIVE` y `SURVEYJS`) duplica el
  esfuerzo de mantenimiento futuro (dos formatos de pregunta, dos
  renderizados) — mitigacion: `SURVEYJS` no reemplaza al motor propio, es
  una opcion adicional; si con el tiempo un motor concentra casi todo el
  uso, se puede evaluar deprecar el otro sin romper encuestas ya creadas
  (el motor es fijo por encuesta, ver Excludes).
- El editor visual `survey-creator-*` puede tener condiciones de licencia
  que cambien la viabilidad de usarlo gratis en produccion — mitigacion:
  confirmar la licencia vigente antes de iniciar la implementacion (ver
  Dependencies), y si no es viable, ofrecer `SURVEYJS` solo con el render
  (`survey-core`/`survey-js-ui`) contra un JSON schema editado a mano o
  importado, sin el editor visual.
- `seatmap-canvas` es un proyecto de un solo mantenedor (no un producto con
  soporte comercial) — mitigacion: si el proyecto queda abandonado o
  incompatible, `FREEFORM` sigue siendo el motor por defecto y totalmente
  soportado; `SEATMAP_CANVAS` queda como opcion adicional, no como
  reemplazo obligatorio.
- Definir la distribucion de un recinto real (filas, numeracion, secciones)
  puede tomar mas tiempo al organizador que el editor `FREEFORM` de clic
  libre — mitigacion: soportar reutilizar/clonar una distribucion ya creada
  para otro evento en el mismo recinto (FR-023).

## Complexity Ranking & Execution Order

Análisis de complejidad técnica de las seis iniciativas activas de esta especificación, ordenadas de menos a más compleja. Esta clasificación es fundamental para secuenciar la implementación y para evaluar el riesgo operativo/de infraestructura de cada uno.

### 1. EventType Catalog (10 tareas) — **La más fácil**
Patrón completamente conocido en el codebase:
- `EventType` entity + `SqliteEventTypeRepository` = calcado de `Conference` (repository pattern ya probado).
- Casos de uso CRUD reutilizan el patron `*UseCase` + validaciones de negocio existentes.
- Rutas HTTP = patron `*Handler` probado cien veces (validar token, 401 si invalido, ejecutar use case, responder 200/error).
- Seed/migracion = try/catch ignore de SQLite, mismo que `conferences`.
- Es prerequisito de todo lo demás (sin EventType no hay gates de capacidad).

**Riesgo:** bajo.

### 2. drawio + Etherpad
**drawio:** sencillo porque:
- Un Helm chart de una imagen oficial (diagrams.net), sin personalizacion compleja.
- Un iframe que embebe `https://drawio-url/#?embed=1&noExitBtn=1` (el editor sin botones de salida).
- No requiere persistencia en el backend (el usuario exporta/descarga desde drawio mismo).
- Una pestaña que carga el iframe, sin state complejo.

**Etherpad:** un poco más que drawio pero sin sorpresas:
- Helm chart + API key generada al desplegar.
- Un cliente HTTP pequeño que llama a `/api/1.2.1/createPad?padID=event-uuid` (lazy pad creation).
- Un iframe que embebe el pad `https://etherpad-url/p/event-uuid`.
- Estado: una columna de URL base de Etherpad (env var) y un try/catch en el handler si no responde.

**Complejidad:** media-baja. No hay UX riesgosa (no es un editor custom), no hay almacenamiento propio de datos en esos servicios.

### 3. Excalidraw Self-hosted
Más fricción de la que aparenta inicialmente:
- La colaboracion en vivo requiere un segundo servicio: `excalidraw-room` (un servicio WebSocket separado para compartir cambios entre sesiones).
- La imagen oficial de Excalidraw no viene configurada para apuntar a un room server propio — suele requerir un rebuild con variables de entorno en build-time, o un reverse proxy + rewriting de URLs.
- Hay dos caminos: (a) pre-buildear la imagen de Excalidraw con la URL del room server baked-in, o (b) servir Excalidraw desde un reverse proxy que reescriba las llamadas al room server.
- El iframe es sencillo (`<iframe src="https://excalidraw-url/#room=..."></iframe>`), pero la configuracion previa no lo es.
- Testing: dos sesiones concurrentes editando la misma pizarra, confirmando que los cambios se propagan en vivo — sin esto, parece funcionar pero la colaboracion esta rota.

**Complejidad:** media. Riesgo operativo en la configuracion de Helm + build de imagen.

### 4. SurveyJS (5 tareas) — **Complejidad media**
Dos costos reales:
1. **Incógnita de licencia:** `survey-creator-core` y `survey-creator-js` (el editor visual drag-and-drop) tienen historicamente un modelo de uso gratuito con marca de agua. Hay que confirmar la licencia vigente antes de iniciar implementacion (TASK-0050 puede cambiar de alcance según el resultado).
2. **Peso del bundle:** SurveyJS es una libreria grande (survey-core + survey-js-ui + opcionalemente survey-creator). El bundle final del frontend puede aumentar significativamente. No hay forma de evitarlo si se quiere el editor visual — la alternativa es ofrecer solo el render (sin editor) y que los organizadores editen JSON a mano, pero eso es menos util.

El resto es recto: persistencia de la definicion como JSON schema de SurveyJS en la BD existente (`insightbloom-survey`), render via componente de SurveyJS en la misma pagina de encuesta actual, resultados en el mismo dashboard de resultados (con posible diferencia de formato en el detalle).

**Complejidad media, con riesgo no-tecnico (licencia).**

### 5. seatmap-canvas (6 tareas) — **Complejidad media-alta, con riesgo de dependencia**
Depende de una libreria muy especializada:
- `seatmap-canvas` (alisaitteke/seatmap-canvas, vanilla-JS/D3) es un proyecto de un solo mantenedor, no un producto con soporte comercial.
- Necesita un wrapper delgado en Vue (similar al ya hecho para `qrcode` o `qr-scanner`).
- El modelo de datos (filas/secciones/butacas numeradas) es muy distinto al actual (pares x/y sueltos). Hay que mapear entre ambos sin romper compatibilidad con `FREEFORM` ni con `ReserveSeatUseCase`.
- Una falla temprana (TASK-0060, verificacion de mantenimiento) puede descartar la libreria completamente, obligando a evaluar alternativas (construir custom, usar otra libreria).
- La configuracion del recinto es mas compleja que `FREEFORM` (no solo clic libre) — hay que interfacear con filas/secciones como concepto, no como coordenadas.

**Complejidad media-alta, con riesgo de dependencia (single maintainer).**

### 6. Jitsi Self-hosted — **La más compleja de las iniciativas activas**
El riesgo es principalmente **infraestructura, no codigo**:
- Jitsi Meet no es un pod simple: son 4-5 componentes:
  - **web**: el frontend Vue/React (el UI que ve el usuario).
  - **prosody**: XMPP server para la señalización/presencia.
  - **jicofo**: Jitsi Conference Focus, orquesta el flujo de medios.
  - **JVB** (Jitsi Videobridge): RTC media router, procesa audio/video en tiempo real.
- El **JVB necesita UDP puerto 10000 expuesto** a la red publica (o al menos a los clientes). En K3s significa:
  - `hostPort: 10000` (inseguro — cada pod que intente usarlo falla si el puerto ya está ocupado).
  - `NodePort` tipo UDP + configuracion de red del cluster.
  - Dentro de esto, hay STUN/TURN: si los clientes no pueden ver directamente el JVB (NAT, firewall), necesitan un servidor TURN para relayar el media.
- La primera version usa `meet.jit.si` publico (trivial, solo iframe), lo que permite validar el flujo de capacidades + UI.
- El self-hosted queda **al final** porque si falla, todavia hay una ruta publica funcional (`meet.jit.si`), y hay tiempo para resolver la configuracion de red.

**Complejidad alta, con riesgo de infraestructura (NAT, STUN/TURN, cluster networking).**

### Orden de ejecución recomendado

**1. EventType Catalog** (Fase 0 + 1) — fundación sin la cual nada funciona.  
**2. drawio** (parte de Fase 3 + 4) — mas facil que Etherpad, demuestra capability gating temprano.  
**3. Etherpad** (parte de Fase 3 + 4) — poco mas que drawio.  
**4. Jitsi público** (parte de Fase 3 + 4, solo iframe de meet.jit.si) — **adelantarlo de su fase**, antes de tocarel self-hosted. Con EventType + drawio/Etherpad/Jitsi-público ya tienes valor demostrable rápido: un tipo de evento "Taller remoto" completamente funcional sin tocar infraestructura riesgosa.  
**5. Excalidraw** — colaboracion en vivo, pero con friccion de configuracion. Ya hay confianza en Helm charts tras drawio/Etherpad/Jitsi.  
**6. SurveyJS** — encuestas alternativas. Riesgo de licencia resuelto en TASK-0050; si hay problema, se posterga.  
**7. seatmap-canvas** — verificacion de mantenimiento (TASK-0060) puede descartarla, pero `FREEFORM` ya es totalmente funcional como fallback.  
**8. Jitsi Self-hosted** — **al final**, cuando la infraestructura del cluster este estable y haya tiempo para resolver NAT/TURN. La ruta publica `meet.jit.si` sigue funcionando como fallback.

Esta secuencia tiene una ventaja crítica: **rapidez de valor demostrable** (pasos 1-4 = tipo de evento funcional en ~3-4 semanas) antes de tocar lo riesgoso (infraestructura, dependencias frágiles).

## Execution Plan
-> `tasks/event-types-catalog/TASKS.md`

## Validation Plan
- Manual: crear un tipo de evento nuevo con un subconjunto de capacidades,
  crear un evento de ese tipo, confirmar que solo aparecen las pestañas
  correspondientes y que las rutas de capacidades no incluidas responden con
  error claro.
- Automatizado: tests de dominio del nuevo `EventTypeRepository` y sus casos
  de uso (crear, editar, activar/desactivar), tests de gate de capacidad en
  los handlers existentes (boletos/encuesta/presentacion), y verificacion de
  que ninguna conferencia existente cambia de comportamiento (regresion 0 en
  la suite actual de `insightbloom-users`).
- Evidencia esperada: la suite completa de backend y frontend sigue en verde
  despues de introducir el catalogo; un tipo de evento sin `TICKETING_*`
  oculta por completo el flujo de boletos.
- Manual (integraciones): unirse a la sala de Jitsi publico y a la de Jitsi
  self-hosted desde dos navegadores distintos y confirmar que se ven/oyen
  entre si; abrir la pizarra Excalidraw desde dos sesiones y confirmar
  edicion colaborativa en vivo; abrir drawio y confirmar que el editor
  carga; crear un pad de Etherpad para un evento y confirmar que dos
  sesiones editan el mismo documento en tiempo real; crear una encuesta
  `SURVEYJS` con el editor visual, responderla como asistente y confirmar
  que la respuesta aparece en el dashboard de resultados junto a las
  encuestas `NATIVE`; definir un recinto con `SEATMAP_CANVAS` (filas y
  butacas), reservar un asiento desde dos sesiones al mismo tiempo y
  confirmar que exactamente una tiene exito (mismo criterio de concurrencia
  que `FREEFORM`).
