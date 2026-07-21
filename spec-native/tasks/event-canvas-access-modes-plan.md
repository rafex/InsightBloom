# Plan: modos de edición y distribución de materiales del evento

## Estado

- Estado: `in_progress`
- Tipo: plan de implementación
- Alcance actual: configuración múltiple persistida y primera rebanada de publicación de Drawio implementada

## Objetivo

Permitir que, al crear o configurar un evento, el moderador defina cómo se
utilizarán las herramientas de trabajo visual y documental:

- Drawio.
- Excalidraw.
- Etherpad.

La elección debe controlar quién puede editar y qué resultado se conserva. No
se implementará colaboración multiusuario en tiempo real mediante una nueva
capa WebSocket.

Las únicas ediciones que se persistirán para distribución serán las realizadas
por el moderador. Los trabajos independientes de los asistentes serán
temporales y no formarán parte del material descargable.

## Decisión funcional

### Modalidades del evento

| Modalidad | Quién edita | Qué ven los asistentes | Qué se persiste |
|---|---|---|---|
| `INDEPENDENT` | Moderador y asistentes, cada uno en su propio espacio | Su propio espacio de trabajo; no ven ni modifican el de otros | Sólo el espacio del moderador |
| `MODERATOR_ONLY` | Sólo el moderador | El último resultado publicado por el moderador, como contenido no editable | Fuente nativa y exportaciones del moderador |

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

La modalidad (`INDEPENDENT` o `MODERATOR_ONLY`) se persiste por herramienta y
se aplica de forma independiente en el frontend. Esto permite que un evento
tenga, por ejemplo, Drawio y Excalidraw sólo para el moderador y Etherpad en
espacios independientes.

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

- Cada asistente tendrá un espacio independiente cuando la modalidad sea
  `INDEPENDENT`.
- Ese espacio se mantendrá sólo en el navegador o en la sesión de trabajo.
- No se guardará en SQLite, almacenamiento de objetos, Etherpad ni en el ZIP.
- No habrá historial, recuperación entre dispositivos ni consulta posterior
  por parte del moderador.
- Si el asistente abandona la página o cambia de dispositivo, el trabajo puede
  perderse; la UI debe indicarlo explícitamente.

La única excepción es el resultado del moderador, que sí será persistente y
descargable.

## Descarga condicionada por encuesta

El ZIP de materiales seguirá la misma política de acceso que la descarga de la
presentación actual:

- Moderador, organizador y administradores autorizados: pueden obtenerlo según
  sus permisos operativos.
- Participante: debe tener sesión válida, acceso al evento mediante registro y
  boleto, y haber respondido la encuesta requerida.
- Sin encuesta completada: el endpoint debe rechazar la descarga aunque el
  navegador muestre el botón.
- La comprobación debe realizarse en backend; ocultar el botón en frontend no
  es una medida de seguridad suficiente.

El ZIP se generará bajo demanda o desde un artefacto versionado y tendrá una
estructura estable, por ejemplo:

```text
event-materials-<friendly-id>.zip
├── manifest.json
├── moderator/
│   ├── drawio/
│   │   ├── source.drawio
│   │   ├── export.svg
│   │   ├── export.png
│   │   └── export.pdf
│   ├── excalidraw/
│   │   ├── source.excalidraw
│   │   ├── export.svg
│   │   ├── export.png
│   │   └── export.pdf
│   └── etherpad/
│       ├── source.html
│       ├── export.txt
│       ├── export.html
│       └── export.pdf
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
  audienceMode         INDEPENDENT | MODERATOR_ONLY
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
- Sustituir gradualmente el iframe localStorage por un wrapper o una instancia
  controlable que permita obtener la escena nativa del moderador.
- Guardar `.excalidraw` sólo para el moderador.
- Generar SVG/PNG/PDF mediante las APIs o exportadores compatibles con la
  versión instalada.
- En `INDEPENDENT`, inicializar cada cliente con una escena independiente y
  no enviar sus cambios al servidor.
- En `MODERATOR_ONLY`, servir a asistentes el último export publicado, no un
  editor editable.
- Validar el aislamiento de origen, la comunicación `postMessage` y la
  ausencia de tokens en URLs exportadas.

### Fase 5 — Integración de Etherpad

- Mantener el pad del moderador como fuente persistente.
- Definir un snapshot canónico y obtenerlo mediante el API de Etherpad.
- Generar los formatos de descarga permitidos por el despliegue.
- En `INDEPENDENT`, crear o presentar espacios separados sin persistir los
  cambios de los asistentes; eliminar o dejar expirar esos espacios según la
  política de datos efímeros.
- En `MODERATOR_ONLY`, presentar el snapshot/exportación publicada en modo
  lectura.
- Evitar incluir la API key de Etherpad en frontend, URLs o ZIP.

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
- Añadir botón de descarga del ZIP sólo cuando el backend confirme que el
  usuario cumple la encuesta.
- Mostrar motivo de bloqueo cuando la encuesta aún no está respondida y enlazar
  a la encuesta.
- Mantener los permisos de boleto y las áreas privadas ya definidas para el
  evento.

### Fase 7 — ZIP, exportación y limpieza

- Construir el `manifest.json` y empaquetar sólo artefactos del moderador.
- Definir nombres de archivo estables y seguros.
- Aplicar límites de tamaño y tiempo de generación.
- Registrar quién descargó el ZIP, cuándo y qué versión recibió.
- Aplicar la política TTL existente a borradores y materiales efímeros del
  evento, sin borrar antes de que termine la ventana de descarga definida.
- No conservar espacios independientes de asistentes después de abandonar o
  cerrar el evento.

### Fase 8 — Pruebas y despliegue gradual

Backend:

- configuración por herramienta y modalidad;
- moderador puede guardar y publicar;
- asistente no puede guardar material del moderador;
- espacios independientes no crean registros persistentes;
- snapshot publicado sólo es legible para asistentes;
- descarga rechazada antes de completar la encuesta;
- descarga permitida después de completar la encuesta y tener acceso;
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
- persistencia de trabajos independientes de asistentes;
- revisión o calificación de los dibujos de asistentes;
- mezcla automática de trabajos individuales;
- edición del material del moderador por asistentes;
- exportaciones que no soporte de forma confiable el despliegue seleccionado;
- reemplazar Drawio, Excalidraw o Etherpad por otra herramienta.

## Progreso de implementación — 2026-07-20

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

Todavía no se han implementado las tablas versionadas de artefactos, las
exportaciones adicionales de Drawio, Excalidraw/Etherpad ni el ZIP condicionado
por encuesta. Esas piezas siguen pendientes en las fases 1–8 y deben reutilizar
la comprobación backend de encuesta existente.

## Criterio de cierre

Un evento puede elegir varias herramientas, cada una con su modalidad. El moderador puede conservar su
fuente nativa y exportaciones; los asistentes pueden trabajar de forma
independiente sin que sus cambios se persistan, o visualizar el snapshot del
moderador sin editarlo. Tras responder la encuesta requerida, un participante
con acceso puede descargar un ZIP que contiene únicamente los materiales del
moderador en formatos nativos y exportados.
