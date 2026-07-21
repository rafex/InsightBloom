# Plan: modos de edición y distribución de materiales del evento

## Estado

- Estado: `in_progress`
- Tipo: plan de implementación
- Alcance actual: fase inicial implementada; persistencia y contrato de configuración del lienzo

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
exportación publicada. No se promete actualización en vivo mientras el
moderador dibuja; el moderador deberá guardar o publicar una nueva versión.

### Herramienta del evento

La configuración debe permitir habilitar una herramienta activa por evento y
dejar el modelo preparado para más de una herramienta si el producto decide
mostrar varias en el mismo evento:

```text
DRAWIO       -> diagramas XML
EXCALIDRAW   -> pizarra y escenas JSON
ETHERPAD     -> documento colaborativo y exportaciones
```

La modalidad (`INDEPENDENT` o `MODERATOR_ONLY`) debe aplicarse por herramienta,
no asumirse globalmente en el frontend. Esto permite que un evento tenga, por
ejemplo, Drawio sólo para el moderador y Etherpad en espacios independientes.

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
  enabled              boolean
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

- Confirmar si la creación permite una sola herramienta o varias por evento.
- Confirmar si el resultado del moderador se publica manualmente o también al
  guardar automáticamente.
- Definir la lista exacta de exportaciones garantizadas para cada herramienta.
- Definir el formato canónico de fuente de Etherpad.
- Confirmar si los asistentes en `MODERATOR_ONLY` ven el último snapshot al
  recargar o si existirá un botón explícito de actualizar.
- Confirmar el nombre y la política de la encuesta requerida para descargar.

### Fase 1 — Modelo, permisos y migración

- [x] Persistir `canvasTool` y `canvasAudienceMode` en `conferences`, con migración SQLite idempotente.
- [x] Añadir validación de valores permitidos y escritura restringida al creador del evento.
- [x] Evitar que el guardado de Drawio persista cambios de asistentes en las modalidades nuevas.
- [ ] Crear las tablas versionadas de artefactos del moderador.
- [ ] Añadir permisos para publicar fuentes por moderador, organizador o administrador autorizado.
- Definir estados y versionado de artefactos.
- Reutilizar la política existente de evento, registro y boleto.
- Crear la regla backend `materials_download_allowed` o equivalente para
  centralizar la verificación de encuesta completada.

### Fase 2 — Contratos API

Endpoints previstos:

- [ ] `GET /conferences/{id}/canvas-config` — configuración visible del evento.
- [x] `PUT /conferences/{id}/canvas-config` — configurar herramienta y modalidad.
- `GET /conferences/{id}/canvas/moderator` — obtener el borrador del moderador.
- `PUT /conferences/{id}/canvas/moderator` — guardar fuente nativa.
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

- Mantener el iframe `embed=1` y el protocolo JSON existente.
- Guardar el XML únicamente en el espacio del moderador.
- Añadir exportación controlada desde Drawio o mediante el adaptador del
  despliegue.
- En `MODERATOR_ONLY`, permitir a los asistentes leer sólo el snapshot
  publicado.
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

- [x] Agregar la selección de herramienta y modalidad en la creación/configuración
  del evento.
- [x] Mostrar sólo la herramienta seleccionada cuando el evento tiene una selección explícita.
- [x] Mostrar una advertencia en Drawio cuando el espacio del asistente no se persiste.
- Mostrar al moderador el editor de la herramienta elegida.
- Mostrar una advertencia visible en `INDEPENDENT`: “este espacio no se guarda
  y no lo verá el moderador”.
- Mostrar a asistentes el snapshot publicado en `MODERATOR_ONLY`.
- Añadir botón de guardar/publicar para el moderador.
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

- Los eventos nuevos pueden recibir la selección desde el formulario de creación.
- Los eventos existentes pueden configurarla desde `Configuración del evento`.
- La configuración viaja en el modelo de conferencia y sobrevive a una recarga de SQLite.
- El frontend limita la navegación a la herramienta seleccionada cuando existe una selección.
- Drawio deja de guardar en backend los cambios de asistentes al usar una modalidad nueva;
  el moderador/creador conserva el guardado.

Todavía no se ha implementado la persistencia de artefactos nativos/exportados, la publicación
de snapshots ni el ZIP condicionado por encuesta. Esas piezas siguen pendientes en las fases
3–8 y deben reutilizar la comprobación backend de encuesta existente.

## Criterio de cierre

Un evento puede elegir herramienta y modalidad. El moderador puede conservar su
fuente nativa y exportaciones; los asistentes pueden trabajar de forma
independiente sin que sus cambios se persistan, o visualizar el snapshot del
moderador sin editarlo. Tras responder la encuesta requerida, un participante
con acceso puede descargar un ZIP que contiene únicamente los materiales del
moderador en formatos nativos y exportados.
