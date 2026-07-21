# Plan: modos de edición y distribución de materiales del evento

## Estado

- Estado: `in_progress`
- Tipo: plan de implementación
- Alcance actual: configuración múltiple persistida, publicación de Drawio/Excalidraw y notas Etherpad grupales o individuales implementadas

## Objetivo

Permitir que, al crear o configurar un evento, el moderador defina cómo se
utilizarán las herramientas de trabajo visual y documental:

- Drawio.
- Excalidraw.
- Etherpad.

La elección debe controlar quién puede editar y qué resultado se conserva. No
se implementará colaboración multiusuario en tiempo real mediante una nueva
capa WebSocket.

Drawio y Excalidraw conservan para distribución únicamente la publicación del
moderador. Etherpad tiene una modalidad grupal por defecto y una modalidad
individual opcional: los pads individuales se conservan durante el evento,
permiten exportación al asistente y se purgan después del vencimiento.

## Decisión funcional

### Modalidades del evento

| Modalidad | Quién edita | Qué ven los asistentes | Qué se persiste |
|---|---|---|---|
| `INDEPENDENT` | Moderador y asistentes, cada uno en su propio espacio | Su propio espacio de trabajo; no ven ni modifican el de otros | Sólo el espacio del moderador |
| `MODERATOR_ONLY` | Sólo el moderador | El último resultado publicado por el moderador, como contenido no editable | Fuente nativa y exportaciones del moderador |
| `COLLABORATIVE` | Moderador y asistentes sobre el mismo pad de Etherpad | El documento grupal en vivo | El pad grupal mientras dura el evento; se incorpora al ZIP |

`INDEPENDENT` no significa colaboración: no habrá cursores compartidos,
presencia, sincronización, resolución de conflictos ni comunicación entre los
lienzos de los asistentes.

En `MODERATOR_ONLY`, el resultado que reciben los asistentes será una captura o
exportación publicada. El guardado/autoguardado del moderador publica una nueva
versión de Drawio; el asistente intenta recibirla mediante SSE y también cuenta
con polling y un botón flotante de actualización como respaldo. Esto no es
colaboración en vivo: nunca se envían las ediciones del asistente ni se muestra
el editor de Drawio en su vista.

### Herramientas del evento

La configuración permite habilitar varias herramientas en el mismo evento. Cada
herramienta tiene su propia modalidad, por lo que no existe una modalidad global
que obligue a Drawio, Excalidraw y Etherpad a comportarse igual:

```text
DRAWIO       -> diagramas XML
EXCALIDRAW   -> pizarra y escenas JSON
ETHERPAD     -> documento colaborativo y exportaciones
```

La modalidad se persiste por herramienta y se aplica de forma independiente en
el frontend. `COLLABORATIVE` sólo es válida para Etherpad y es el valor por
defecto de las notas. Esto permite que un evento tenga Drawio y Excalidraw sólo
para el moderador y Etherpad grupal o individual.

Si no se selecciona ninguna herramienta explícitamente, se mantiene el modo
legado: se muestran las herramientas habilitadas por el tipo de evento.

## Persistencia y propiedad de los datos

### Material del moderador

Para cada herramienta habilitada, el sistema conservará:

1. La fuente nativa editable.
2. Las exportaciones generadas por la herramienta.
3. La versión publicada para los asistentes.
4. Metadatos de auditoría: evento, herramienta, autor, fecha, versión y estado.

La fuente nativa debe permanecer en el formato de la herramienta:

| Herramienta | Fuente nativa propuesta | Exportaciones iniciales |
|---|---|---|
| Drawio | XML `.drawio` o `.xml` | SVG, PNG y PDF, si están disponibles en el despliegue |
| Excalidraw | JSON `.excalidraw` | SVG, PNG y PDF, si están disponibles en el despliegue |
| Etherpad | Snapshot fuente obtenido mediante su API, con formato canónico definido por el adaptador | HTML, texto y PDF; otros formatos según el API habilitado |

Etherpad no debe tratarse como si tuviera un único archivo nativo universal. El
adaptador debe definir cuál es la fuente editable canónica y conservar además
la respuesta original o snapshot necesario para reproducir las exportaciones.

### Trabajo de los asistentes

- Cada asistente tendrá un pad privado derivado por backend cuando Etherpad esté
  en `INDEPENDENT`. El navegador nunca puede elegir el `padId` de otra persona.
- El pad privado vive durante la vigencia del evento y la purga posterior
  configurada; la UI debe ofrecer TXT y HTML antes de esa purga.
- Los pads privados no se guardan en SQLite, no se mezclan con el pad grupal y
  nunca se agregan al ZIP de materiales.
- Si el asistente abandona el evento sin exportar, el contenido puede perderse
  cuando se ejecute la purga; esto debe estar indicado explícitamente.

La única excepción es el resultado del moderador, que sí será persistente y
descargable.

## Descarga de materiales y exportación de notas

El ZIP de materiales está disponible para un token válido con acceso al evento
y sigue la política de acceso del evento ticketed:

- Moderador, organizador y administradores autorizados: pueden obtenerlo según
  sus permisos operativos.
- Participante: debe tener sesión válida y acceso al evento mediante el boleto
  cuando el evento sea ticketed.
- La comprobación se realiza en backend; ocultar el botón en frontend no es una
  medida de seguridad suficiente.

El ZIP se generará bajo demanda o desde un artefacto versionado y tendrá una
estructura estable, por ejemplo:

```text
event-materials-<friendly-id>.zip
├── manifest.json
├── moderator/
│   ├── drawio/       # source.drawio, export.svg, export.png si existe publicación
│   ├── excalidraw/   # source.excalidraw, export.svg, export.png si existe publicación
│   └── etherpad/     # sólo notas grupales: source.html, export.html, export.txt
└── README.txt
```

Sólo deben incluirse las carpetas de las herramientas habilitadas y los
formatos que realmente existan. `manifest.json` debe indicar versión, fecha de
publicación, herramienta, modalidad y lista de archivos.

El ZIP no debe incluir:

- contenido de los lienzos independientes de asistentes;
- identificadores, correos o datos personales de otros participantes;
- borradores del moderador que aún no hayan sido publicados, salvo que el
  moderador solicite explícitamente el paquete administrativo;
- tokens, URLs privadas con credenciales o claves de integración.

## Arquitectura propuesta

### Configuración de evento

Agregar una configuración de herramientas a nivel de evento, separada de la
autorización general del evento:

```text
EventCanvasConfig
  conferenceUuid
  tool                 DRAWIO | EXCALIDRAW | ETHERPAD
  audienceMode         INDEPENDENT | MODERATOR_ONLY | COLLABORATIVE
  moderatorSourceId    nullable
  publishedVersion     nullable
  createdAt
  updatedAt
```

Si se habilitan varias herramientas, habrá una fila por herramienta. La
configuración debe quedar versionada o registrar al menos la versión publicada
para que una descarga futura sea reproducible.

### Artefactos del moderador

Crear un modelo de artefactos desacoplado de la integración visual:

```text
ModeratorCanvasArtifact
  uuid
  conferenceUuid
  tool
  nativeFormat
  nativePayload or storageKey
  exportedFormats
  version
  status                 DRAFT | PUBLISHED | SUPERSEDED
  createdBy
  updatedAt
  publishedAt
```

Para archivos pequeños se puede conservar el contenido en SQLite según los
límites actuales. Para PDFs, imágenes o documentos grandes se debe evaluar el
almacenamiento de archivos existente y guardar sólo referencias en SQLite.

### Adaptadores por herramienta

Cada herramienta tendrá un adaptador con las mismas responsabilidades:

```text
loadModeratorSource()
saveModeratorSource()
exportNativeSource()
publishVersion()
readPublishedVersion()
```

El transporte común puede ser HTTP y `postMessage` donde la herramienta lo
permita. No se requiere WebSocket para estas modalidades.

## Plan de implementación

### Fase 0 — Cerrar decisiones de producto

- [x] Confirmar que la creación permite varias herramientas por evento.
- [x] Confirmar que el guardado/autoguardado del moderador publica también el
  snapshot visible para asistentes.
- [x] Confirmar que Etherpad es grupal por defecto y que el modo individual
  expira con el evento y se puede exportar.
- [x] Definir el ZIP de materiales como descarga de fuentes y publicaciones
  grupales, excluyendo pads privados.
- Definir la lista exacta de exportaciones garantizadas para cada herramienta.
- Definir el formato canónico de fuente de Etherpad.
- [x] Confirmar que los asistentes en `MODERATOR_ONLY` ven el último snapshot al
  recargar y reciben actualizaciones por SSE/polling, con botón explícito de
  actualizar.
- Confirmar el nombre y la política de la encuesta requerida para descargar.

### Fase 1 — Modelo, permisos y migración

- [x] Persistir `canvasConfigs` por herramienta en SQLite, con migración idempotente desde `canvasTool` y `canvasAudienceMode` legacy.
- [x] Añadir validación de valores permitidos y escritura restringida al creador del evento.
- [x] Evitar que el guardado de Drawio persista cambios de asistentes en las modalidades nuevas, usando su modalidad individual.
- [ ] Crear las tablas versionadas de artefactos del moderador.
- [ ] Añadir permisos para publicar fuentes por moderador, organizador o administrador autorizado.
- Definir estados y versionado de artefactos.
- Reutilizar la política existente de evento, registro y boleto.
- Crear la regla backend `materials_download_allowed` o equivalente para
  centralizar la verificación de encuesta completada.

### Fase 2 — Contratos API

Endpoints previstos:

- [ ] `GET /conferences/{id}/canvas-config` — configuración visible del evento.
- [x] `PUT /conferences/{id}/canvas-config` — configurar varias herramientas y modalidad individual por herramienta.
- [ ] `GET /conferences/{id}/canvas/moderator` — obtener el borrador del moderador.
- [ ] `PUT /conferences/{id}/canvas/moderator` — guardar fuente nativa.
- [x] `GET/PUT /conferences/{id}/diagram` — contrato existente extendido con
  XML nativo, SVG publicado, versión y fecha de actualización.
- [x] `GET /conferences/{id}/diagram/stream` — SSE autenticado para notificar
  nuevas versiones publicadas; el token se recibe como `ib_token` porque
  `EventSource` no permite headers personalizados.
- `POST /conferences/{id}/canvas/moderator/export` — generar exportaciones.
- `POST /conferences/{id}/canvas/moderator/publish` — publicar snapshot.
- `GET /conferences/{id}/canvas/published` — leer el resultado público del evento.
- `GET /conferences/{id}/materials.zip` — descargar el paquete condicionado.

Requisitos de API:

- validar permisos en cada operación de escritura;
- validar que el evento y la herramienta estén habilitados;
- rechazar payloads por encima de límites de tamaño;
- no aceptar `userUuid` de propiedad del cliente para auditoría;
- devolver estado de versión y fecha de publicación;
- hacer la descarga idempotente y auditable.

### Fase 3 — Integración de Drawio

- [x] Mantener el iframe `embed=1` y el protocolo JSON existente.
- [x] Guardar el XML únicamente en el espacio del moderador.
- [x] Solicitar exportación SVG controlada mediante el protocolo `postMessage`
  de Drawio y persistirla junto con la fuente nativa.
- [x] En `MODERATOR_ONLY`, permitir a los asistentes leer sólo el snapshot SVG
  publicado, sin cargar un iframe editable.
- [x] Notificar nuevas versiones por SSE dentro de la instancia de Users y
  mantener polling/botón de refresco como respaldo.
- En `INDEPENDENT`, crear un estado inicial para cada asistente sin guardar
  sus cambios en backend.
- Revisar concurrencia de guardados del moderador y usar versión/ETag para no
  perder cambios accidentales.

### Fase 4 — Integración de Excalidraw

- No activar colaboración nativa ni desplegar un servidor WebSocket.
- [x] Sustituir el iframe localStorage por una instancia controlable que permita obtener la escena nativa del moderador.
- [x] Guardar `.excalidraw` sólo para el moderador.
- [x] Generar y publicar SVG mediante una API compatible con la versión instalada.
- En `INDEPENDENT`, inicializar cada cliente con una escena independiente y
  no enviar sus cambios al servidor.
- En `MODERATOR_ONLY`, servir a asistentes el último export publicado, no un
  editor editable.
- Validar el aislamiento de origen, la comunicación `postMessage` y la
  ausencia de tokens en URLs exportadas.

### Fase 5 — Integración de Etherpad

- [x] Mantener el pad grupal como fuente persistente por evento.
- [x] Definir snapshot canónico mediante `getText` y `getHTML` del API de Etherpad.
- [x] En `INDEPENDENT`, crear un pad privado determinista por evento/usuario sin
  aceptar `padId` desde el frontend; purgarlo después del vencimiento.
- [x] Ofrecer exportación TXT/HTML al usuario de notas individuales.
- [x] Evitar incluir la API key de Etherpad en frontend, URLs o ZIP.
- [ ] Exponer formatos adicionales de Etherpad si el despliegue los requiere.

### Fase 6 — Frontend y experiencia de evento

- [x] Agregar selección múltiple de herramientas y modalidad independiente por
  herramienta en la creación/configuración del evento.
- [x] Mostrar sólo la herramienta seleccionada cuando el evento tiene una selección explícita.
- [x] Mostrar una advertencia en Drawio cuando el espacio del asistente no se persiste.
- Mostrar al moderador el editor de la herramienta elegida.
- Mostrar una advertencia visible en `INDEPENDENT`: “este espacio no se guarda
  y no lo verá el moderador”.
- [x] Mostrar a asistentes el snapshot publicado en `MODERATOR_ONLY`.
- [x] El guardado/autoguardado del moderador publica el SVG; la interfaz indica
  el estado de publicación.
- [x] Añadir actualización automática por SSE/polling y botón flotante de
  refresco para asistentes.
- [x] Añadir botón de descarga del ZIP; el backend valida el acceso al evento.
- Mantener los permisos de boleto y las áreas privadas ya definidas para el
  evento.

### Fase 7 — ZIP, exportación y limpieza

- [x] Construir el `manifest.json` y empaquetar sólo artefactos publicados del
  moderador y notas grupales.
- [x] Definir nombres de archivo estables y seguros.
- [x] Convertir SVG publicado a PNG para Drawio y Excalidraw.
- [ ] Aplicar límites de tamaño y tiempo de generación.
- [ ] Registrar quién descargó el ZIP, cuándo y qué versión recibió.
- [x] Aplicar la política TTL existente a notas Etherpad grupales y privadas del
  evento, sin borrar antes de que termine la ventana de descarga definida.
- [x] No incluir pads privados de asistentes en el ZIP.

### Fase 8 — Pruebas y despliegue gradual

Backend:

- configuración por herramienta y modalidad;
- moderador puede guardar y publicar;
- asistente no puede guardar material del moderador;
- pads privados de Etherpad viven sólo durante el evento y son exportables;
- snapshot publicado sólo es legible para asistentes;
- descarga permitida con acceso válido al evento;
- ZIP no contiene datos de asistentes;
- fuentes nativas y exportaciones abren correctamente;
- versionado evita sobreescrituras accidentales;
- expiración y limpieza no afectan el material dentro de la ventana permitida.

Frontend/E2E:

- crear evento con Drawio, Excalidraw y Etherpad;
- cambiar entre `INDEPENDENT` y `MODERATOR_ONLY`;
- verificar que dos asistentes no vean los cambios del otro;
- verificar que el moderador sí pueda guardar y recuperar su material;
- publicar una versión y verla como asistente;
- responder la encuesta y descargar el ZIP;
- intentar descargar antes de responder y recibir un mensaje accionable;
- revisar el ZIP con las herramientas nativas y exportadas esperadas.

## Fuera de alcance

- colaboración en tiempo real entre participantes;
- WebSocket, presencia, cursores compartidos o CRDT;
- persistencia posterior al TTL de las notas individuales de asistentes;
- revisión o calificación de los dibujos de asistentes;
- mezcla automática de trabajos individuales;
- edición del material del moderador por asistentes;
- exportaciones que no soporte de forma confiable el despliegue seleccionado;
- reemplazar Drawio, Excalidraw o Etherpad por otra herramienta.

## Progreso de implementación — 2026-07-21

La primera rebanada vertical ya está integrada en backend y frontend:

- Los eventos nuevos pueden recibir varias herramientas y una modalidad distinta para cada una desde el formulario de creación.
- Los eventos existentes pueden actualizar esa selección desde `Configuración del evento`.
- La configuración viaja en el modelo de conferencia y sobrevive a una recarga de SQLite.
- El frontend limita la navegación a la herramienta seleccionada cuando existe una selección.
- Drawio deja de guardar en backend los cambios de asistentes al usar su modalidad individual;
  el moderador/creador conserva el guardado.

La rebanada de Drawio también persiste la fuente XML y un SVG publicado, con
versión/fecha, y expone un stream SSE para que las vistas de asistentes
actualicen la imagen sin cargar el editor. El polling y el botón flotante
permiten recuperar el estado si el stream no está disponible. El stream es un
bus local del servicio Users; es apropiado mientras Users se mantenga con la
topología actual de una instancia SQLite y no sustituye un bus distribuido.

El contrato operativo y sus gates de verificación están documentados en
[`workflows/CANVAS-PUBLICATION.md`](../workflows/CANVAS-PUBLICATION.md). La
rebanada equivalente de Excalidraw usa una instancia controlada del paquete
embebible, porque el iframe externo no permite recuperar de forma fiable su
escena desde InsightBloom.

Etherpad ahora usa `COLLABORATIVE` por defecto. En `INDEPENDENT`, Users calcula
un pad privado a partir del secreto del servicio, el evento y el usuario; el
frontend sólo recibe el pad ya resuelto. El job de purga elimina el pad grupal
y todos los pads privados derivados después del TTL del evento. Antes de la
purga, el asistente puede exportar sus notas como TXT o HTML.

`GET /conferences/{id}/materials.zip` genera bajo demanda un ZIP con
`source.drawio`/`source.excalidraw`, sus SVG y PNG publicados, y las notas
grupales de Etherpad como HTML/TXT. Los pads individuales quedan fuera por
diseño. La UI de Notas expone la descarga y el backend vuelve a validar el
acceso al evento.

## Criterio de cierre

Un evento puede elegir varias herramientas, cada una con su modalidad. Etherpad
es grupal por defecto; opcionalmente cada asistente puede tener notas privadas
temporales y exportarlas. El moderador conserva sus fuentes y publicaciones de
Drawio/Excalidraw. Un participante con acceso puede descargar un ZIP que
contiene únicamente esas publicaciones y las notas grupales, nunca las notas
individuales de otros usuarios.
