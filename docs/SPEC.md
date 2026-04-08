# SPEC.md

Spec activa o spec general del trabajo en curso.

## Resumen

Construir una primera version del sistema InsightBloom para capturar dudas y
temas enviados por chat mediante comandos, agregarlos por palabra clave y
visualizarlos en nubes de palabras separadas e interactivas con detalle
navegable y moderacion operable en vivo.

## Problema

En una conferencia, leer un flujo completo de mensajes no permite detectar
rapidamente patrones. Hace falta una representacion agregada que conserve
contexto y permita explorar el detalle detras de cada palabra relevante.

## Objetivo

Disponer de una interfaz web donde el conferencista vea nubes de palabras
separadas para dudas y temas, alimentadas por acciones `/duda` y `/tema`, y
pueda seleccionar una palabra para consultar los detalles en un timeline
cronologico. En paralelo, el moderador debe contar con un dashboard para
revisar, censurar, restaurar y editar casos no resueltos por la barrera
automatica.

## Alcance

- Incluye:
  portal de entrada para crear conferencias;
  captura de acciones `/duda` y `/tema`;
  registro de palabra y detalle;
  procesamiento en microservicios separados desde el dia uno;
  ingreso por webhook y API REST;
  autenticacion basada en token para usuarios registrados e invitados;
  censura de palabras o terminos excluidos de la nube;
  medicion inicial de intencion del mensaje;
  dashboard de moderacion para revisar y censurar manualmente;
  visualizacion de nubes de palabras separadas con D3.js;
  vista de detalle por palabra;
  ordenamiento cronologico por llegada;
  fusion de singular/plural y normalizacion de palabra;
  soporte de automatizacion local con `Justfile`, `Makefile`,
  `helpers-build` y `helpers-run`;
  base de CI/CD con GitHub Actions y soporte de despliegue con Helm.
- Excluye:
  moderacion avanzada;
  analitica semantica;
  integracion definitiva con un proveedor concreto de chat.

## Requisitos funcionales

- RF-1:
  el sistema debe aceptar mensajes ya parseados correspondientes a acciones
  `/duda "palabra" "detalle"` y `/tema "palabra" "detalle"`.
- RF-2:
  cada registro debe conservar al menos tipo de accion, palabra, detalle,
  autor, timestamp, conferencia y dispositivo.
- RF-3:
  un organizador autenticado debe poder crear una conferencia indicando su
  nombre.
- RF-4:
  cada conferencia debe tener un UUID interno y un identificador amigable
  corto para acceso rapido.
- RF-5:
  la UI debe mostrar nubes separadas de dudas y temas, cada una con un
  titulo visible que identifique su categoria.
- RF-6:
  el peso visual de cada palabra debe reflejar una relevancia calculada a
  partir de conteo, tipo e intencion.
- RF-7:
  al seleccionar una palabra, la UI debe mostrar un timeline de mensajes.
- RF-8:
  el timeline debe ordenar los mensajes por llegada, del primero al ultimo.
- RF-9:
  el sistema debe permitir excluir palabras censuradas o terminos marcados
  como no aptos para aparecer en la nube.
- RF-10:
  el sistema debe asignar una intencion inicial a la palabra y otra al
  detalle, segun reglas definidas para el PoC.
  Las categorias iniciales son:
  palabra -> `pregunta`, `idea`, `duda`, `interes`;
  detalle -> `pregunta`, `preocupacion`, `critica`, `propuesta`.
- RF-11:
  el sistema debe ofrecer un dashboard donde el moderador pueda ver palabras,
  mensajes, estado de censura, intencion estimada, autor, hora y dispositivo.
- RF-12:
  el moderador debe poder censurar manualmente palabras o mensajes que no
  hayan sido bloqueados por la barrera automatica.
- RF-13:
  la censura manual debe reflejarse en la nube y en las vistas derivadas sin
  eliminar fisicamente el registro del PoC.
- RF-14:
  si el detalle es censurable y la palabra no lo es, el detalle visible debe
  reemplazarse por `detalle censurado`.
- RF-15:
  el moderador debe poder restaurar contenido censurado y editar la palabra
  o el mensaje desde el dashboard. Las ediciones conservan solo el valor
  actual, sin historial.
- RF-16:
  las palabras censuradas deben desaparecer totalmente de la nube publica y
  permanecer visibles solo en el dashboard de moderacion.
- RF-17:
  usuarios registrados, invitados o nuevos deben autenticarse mediante
  tokens emitidos o validados por el sistema de usuarios del PoC.
- RF-18:
  los invitados solo pueden enviar mensajes y no pueden moderar.
- RF-19:
  el dashboard de moderacion debe soportar paginacion desde el PoC.

## Requisitos no funcionales

- RNF-1:
  frontend implementado con JavaScript sobre Vite y Vue.
- RNF-2:
  backend implementado con Java 25 y Jetty 12 via `ether-http-jetty12`.
- RNF-2.1:
  cada microservicio backend debe seguir arquitectura hexagonal con dominio,
  puertos y adaptadores claramente separados.
- RNF-3:
  la experiencia visual debe ser legible y usable en contexto de
  presentacion en vivo.
- RNF-4:
  la arquitectura debe permitir cambiar mas adelante la fuente de eventos de
  chat sin reescribir la UI.
- RNF-5:
  para el PoC, la persistencia debe implementarse con SQLite y mantener
  ownership por microservicio.
- RNF-6:
  las reglas de censura e intencion deben ser explicables y suficientemente
  simples para validacion manual durante el PoC.
- RNF-7:
  el dashboard debe permitir intervencion rapida en contexto de conferencia,
  sin flujos largos ni dependencia de procesos offline.
- RNF-8:
  la persistencia del PoC es efimera y puede perderse al reiniciar los
  contenedores.
- RNF-9:
  los identificadores expuestos entre servicios y APIs deben ser UUID, pero
  las tablas internas pueden usar ids seriales incrementales.
- RNF-10:
  el repositorio debe dejar rutas operativas claras para build, run, CI y
  despliegue local.

## Criterios de aceptacion

- Dado un conjunto de mensajes validos con `/duda` y `/tema`,
  cuando el frontend carga la nube,
  entonces muestra las palabras agregadas con una representacion visual.
- Dada una conferencia creada por un organizador autenticado,
  cuando el sistema confirma el alta,
  entonces debe existir un UUID interno y un identificador amigable para
  acceso rapido.
- Dada una palabra visible en la nube,
  cuando el usuario la selecciona,
  entonces puede ver el detalle asociado en formato timeline.
- Dado que varios mensajes comparten la misma palabra,
  cuando se abre el detalle,
  entonces los mensajes de esa palabra aparecen en orden cronologico desde el
  primero hasta el ultimo recibido.
- Dada una palabra incluida en una lista de censura,
  cuando el sistema procesa el mensaje,
  entonces esa palabra no debe formar parte de la nube visible.
- Dado un mensaje valido,
  cuando el sistema lo registra,
  entonces debe quedar asociado a una categoria inicial de intencion para la
  palabra y otra para el detalle.
- Dado un termino con doble sentido no detectado por la barrera automatica,
  cuando el moderador lo censura desde el dashboard,
  entonces deja de formar parte de la nube visible.
- Dado un mensaje censurado manualmente,
  cuando se consultan vistas derivadas,
  entonces el sistema respeta el estado de censura sin borrar el registro.
- Dado un detalle censurable asociado a una palabra permitida,
  cuando el mensaje se hace visible,
  entonces el detalle debe mostrarse como `detalle censurado`.
- Dado un dashboard con multiples resultados,
  cuando el moderador navega la lista,
  entonces debe poder avanzar por paginas de resultados.

## Dependencias y riesgos

- Riesgo:
  la topologia de microservicios puede resultar demasiado pesada para el PoC.
- Riesgo:
  la fusion singular/plural puede producir una forma visible no deseada.
- Riesgo:
  SQLite puede tensionarse si el volumen de escrituras concurrentes reales
  supera lo esperado para una conferencia normal.
- Riesgo:
  si la censura se define de forma demasiado agresiva, la nube puede perder
  señal util del publico.
- Riesgo:
  si las categorias de intencion no se definen con claridad, el PoC puede
  producir datos de poco valor analitico.
- Riesgo:
  si el dashboard no permite moderacion suficientemente rapida, el control
  manual perdera utilidad durante una charla en vivo.
- Riesgo:
  el identificador amigable de conferencia puede colisionar o resultar poco
  usable si no se define una regla clara de generacion.

## Plan de validacion

- Test manual:
  cargar mensajes de ejemplo y verificar nubes separadas, dashboard y
  timeline.
- Test automatizado:
  pruebas del parser de comandos, agregacion por palabra, censura,
  clasificacion de intencion, fusion singular/plural, autenticacion por
  token, creacion de conferencia, paginacion, censura manual y orden del
  timeline.
- Evidencia esperada:
  una palabra como `IA` debe aparecer en la nube y su seleccion debe mostrar
  detalles como `la IA reemplaza personas?`;
  una palabra censurada no debe aparecer en la nube;
  una censura manual aplicada desde dashboard debe reflejarse en la vista;
  `servicio` y `servicios` deben consolidarse en una sola palabra visible.

## Modelo de datos propuesto

### Convenciones

- Cada tabla usa un `id` interno entero autoincremental.
- Cada entidad expuesta fuera del servicio usa un `uuid`.
- Los timestamps se manejan en UTC con formato ISO-8601.
- Los enums se guardan como texto legible en el PoC.

### `insightbloom-users`

- `users`
  `id`, `uuid`, `username`, `display_name`, `email`, `role`, `status`,
  `created_at`, `updated_at`
- `guest_users`
  `id`, `uuid`, `display_name`, `device_fingerprint`, `conference_uuid`,
  `created_at`
- `tokens`
  `id`, `uuid`, `user_uuid`, `guest_user_uuid`, `token_kind`, `token_hash`,
  `expires_at`, `created_at`, `revoked_at`
- `conferences`
  `id`, `uuid`, `friendly_id`, `name`, `created_by_user_uuid`, `status`,
  `created_at`, `updated_at`

### `insightbloom-ingest`

- `messages`
  `id`, `uuid`, `conference_uuid`, `author_uuid`, `author_kind`,
  `device_fingerprint`, `message_type`, `source_type`, `received_at`,
  `word_original`, `word_normalized`, `word_canonical`, `word_intent`,
  `detail_original`, `detail_visible`, `detail_intent`, `word_status`,
  `detail_status`, `is_visible`, `created_at`, `updated_at`

### `insightbloom-moderation`

- `moderation_words`
  `id`, `uuid`, `conference_uuid`, `word_normalized`, `word_canonical`,
  `status`, `reason`, `edited_value`, `updated_by_user_uuid`, `updated_at`
- `moderation_messages`
  `id`, `uuid`, `message_uuid`, `conference_uuid`, `word_status`,
  `detail_status`, `reason`, `edited_word_value`, `edited_detail_value`,
  `updated_by_user_uuid`, `updated_at`
- `blocked_terms`
  `id`, `uuid`, `scope`, `term_normalized`, `status`, `created_by_user_uuid`,
  `created_at`, `updated_at`

### `insightbloom-query`

- `cloud_words`
  `id`, `uuid`, `conference_uuid`, `message_type`, `word_normalized`,
  `word_canonical`, `relevance_score`, `message_count`, `first_seen_at`,
  `last_seen_at`, `is_visible`, `updated_at`
- `word_timeline`
  `id`, `uuid`, `conference_uuid`, `word_normalized`, `message_uuid`,
  `message_type`, `author_label`, `author_kind`, `detail_visible`,
  `received_at`, `is_visible`, `updated_at`

### `insightbloom-stats`

- `word_stats`
  `id`, `uuid`, `conference_uuid`, `message_type`, `word_normalized`,
  `word_canonical`, `count_total`, `count_visible`, `count_censored`,
  `score_type`, `score_intent`, `relevance_score`, `updated_at`

## Payloads JSON propuestos

### Alta de conferencia

```json
{
  "name": "Conferencia IA 2026"
}
```

Respuesta:

```json
{
  "conferenceId": "2c6a1c1d-6f31-4fd2-8e1d-0b73b2c59a55",
  "friendlyId": "ia2026",
  "name": "Conferencia IA 2026",
  "status": "active"
}
```

### Alta de mensaje parseado

```json
{
  "conferenceId": "2c6a1c1d-6f31-4fd2-8e1d-0b73b2c59a55",
  "author": {
    "userId": "c7ab8c79-0aa1-4d66-98a5-6c8ea5f4b8d2",
    "kind": "guest",
    "displayName": "Invitado 14"
  },
  "device": {
    "fingerprint": "thumbmarkjs:9f0f6d2a"
  },
  "message": {
    "type": "doubt",
    "word": "IA",
    "detail": "¿la IA reemplaza personas?"
  },
  "source": {
    "type": "rest"
  },
  "receivedAt": "2026-04-01T21:10:00Z"
}
```

### Item de nube

```json
{
  "word": "servicio",
  "normalizedWord": "servicio",
  "messageType": "topic",
  "relevanceScore": 25.4,
  "messageCount": 25
}
```

### Item de timeline

```json
{
  "messageId": "fe4dc9e0-0fdf-4786-bd8c-10f0bcf9f20d",
  "author": {
    "displayName": "Invitado 14",
    "kind": "guest"
  },
  "detail": "detalle censurado",
  "receivedAt": "2026-04-01T21:10:00Z"
}
```

### Resultado de moderacion

```json
{
  "messageId": "fe4dc9e0-0fdf-4786-bd8c-10f0bcf9f20d",
  "wordStatus": "visible",
  "detailStatus": "censurado_manual",
  "reason": "doble sentido detectado por moderacion",
  "editedDetailValue": "detalle censurado"
}
```

## Endpoints propuestos

### `insightbloom-users`

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/guest`
- `GET /api/v1/auth/validate`
- `POST /api/v1/conferences`
- `GET /api/v1/conferences/{conferenceId}`
- `GET /api/v1/conferences/by-friendly/{friendlyId}`

### `insightbloom-ingest`

- `POST /api/v1/messages`
- `POST /api/v1/webhooks/messages`
- `GET /api/v1/messages/{messageId}`

### `insightbloom-query`

- `GET /api/v1/conferences/{conferenceId}/cloud/doubts`
- `GET /api/v1/conferences/{conferenceId}/cloud/topics`
- `GET /api/v1/conferences/{conferenceId}/words/{word}/timeline?type=doubt`
- `GET /api/v1/conferences/{conferenceId}/words/{word}/timeline?type=topic`

### `insightbloom-moderation`

- `GET /api/v1/conferences/{conferenceId}/moderation/messages?page=1&pageSize=50`
- `GET /api/v1/conferences/{conferenceId}/moderation/words?page=1&pageSize=50`
- `POST /api/v1/moderation/messages/{messageId}/censor`
- `POST /api/v1/moderation/messages/{messageId}/restore`
- `PATCH /api/v1/moderation/messages/{messageId}`
- `POST /api/v1/moderation/words/{wordId}/censor`
- `POST /api/v1/moderation/words/{wordId}/restore`
- `PATCH /api/v1/moderation/words/{wordId}`

### `insightbloom-stats`

- `GET /api/v1/conferences/{conferenceId}/stats/overview`
- `GET /api/v1/conferences/{conferenceId}/stats/relevance`

## Convenciones HTTP propuestas

### Respuesta exitosa sin paginacion

```json
{
  "data": {},
  "meta": {
    "requestId": "6d844b33-2fb4-4d68-81ca-0fc7650f2dd0",
    "timestamp": "2026-04-01T21:10:00Z"
  }
}
```

### Respuesta exitosa con paginacion

```json
{
  "data": [],
  "meta": {
    "requestId": "6d844b33-2fb4-4d68-81ca-0fc7650f2dd0",
    "timestamp": "2026-04-01T21:10:00Z",
    "page": 1,
    "pageSize": 50,
    "total": 120,
    "totalPages": 3
  }
}
```

### Error

```json
{
  "error": {
    "code": "conference_not_found",
    "message": "Conference was not found.",
    "details": {}
  },
  "meta": {
    "requestId": "6d844b33-2fb4-4d68-81ca-0fc7650f2dd0",
    "timestamp": "2026-04-01T21:10:00Z"
  }
}
```

### Codigos HTTP esperados

- `200` para consultas exitosas.
- `201` para creacion de conferencia, guest token y mensaje aceptado.
- `400` para payload invalido.
- `401` para token ausente o invalido.
- `403` para rol sin permiso suficiente.
- `404` para recurso no encontrado.
- `409` para conflicto de `friendlyId` o estado incompatible.
- `422` para reglas de negocio incumplidas.

## Matriz de permisos por rol y endpoint

### Roles

- `organizer`
- `moderator`
- `guest`

### Permisos

- `POST /api/v1/auth/login`
  organizer, moderator
- `POST /api/v1/auth/guest`
  guest
- `GET /api/v1/auth/validate`
  organizer, moderator, guest
- `POST /api/v1/conferences`
  organizer
- `GET /api/v1/conferences/{conferenceId}`
  organizer, moderator
- `GET /api/v1/conferences/by-friendly/{friendlyId}`
  organizer, moderator, guest
- `POST /api/v1/messages`
  organizer, moderator, guest
- `POST /api/v1/webhooks/messages`
  webhook token del sistema origen
- `GET /api/v1/messages/{messageId}`
  organizer, moderator
- `GET /api/v1/conferences/{conferenceId}/cloud/doubts`
  organizer, moderator, guest
- `GET /api/v1/conferences/{conferenceId}/cloud/topics`
  organizer, moderator, guest
- `GET /api/v1/conferences/{conferenceId}/words/{word}/timeline`
  organizer, moderator, guest
- `GET /api/v1/conferences/{conferenceId}/moderation/messages`
  organizer, moderator
- `GET /api/v1/conferences/{conferenceId}/moderation/words`
  organizer, moderator
- `POST /api/v1/moderation/messages/{messageId}/censor`
  organizer, moderator
- `POST /api/v1/moderation/messages/{messageId}/restore`
  organizer, moderator
- `PATCH /api/v1/moderation/messages/{messageId}`
  organizer, moderator
- `POST /api/v1/moderation/words/{wordId}/censor`
  organizer, moderator
- `POST /api/v1/moderation/words/{wordId}/restore`
  organizer, moderator
- `PATCH /api/v1/moderation/words/{wordId}`
  organizer, moderator
- `GET /api/v1/conferences/{conferenceId}/stats/overview`
  organizer, moderator
- `GET /api/v1/conferences/{conferenceId}/stats/relevance`
  organizer, moderator

## Regla de `friendlyId`

- Se genera automaticamente al crear una conferencia.
- Se deriva del nombre aplicando:
  minusculas, sin acentos, reemplazo de espacios por `-`, solo
  `[a-z0-9-]`.
- Longitud objetivo: entre 4 y 24 caracteres.
- Si el slug base queda vacio, usar prefijo `conf`.
- Si hay colision, concatenar sufijo corto incremental:
  `-2`, `-3`, `-4`.
- El `friendlyId` es estable despues de creado.

Ejemplo:

- `Conferencia IA 2026` -> `conferencia-ia-2026`
- colision -> `conferencia-ia-2026-2`

## Formula de `relevanceScore`

Para el PoC:

`relevanceScore = visibleCount * typeWeight * averageIntentWeight`

### Factores

- `visibleCount`:
  numero de mensajes visibles asociados a la palabra.
- `typeWeight`:
  `doubt = 1.2`
  `topic = 1.0`
- `averageIntentWeight`:
  promedio de pesos de intencion de la palabra visible.

### Pesos de intencion de palabra

- `pregunta = 1.15`
- `idea = 1.00`
- `duda = 1.25`
- `interes = 1.10`

### Regla de desempate

Si dos palabras tienen el mismo `relevanceScore`, ordenar por:

1. `visibleCount` descendente
2. `firstSeenAt` ascendente
3. `wordCanonical` ascendente

La formula usa solo palabras visibles. Contenido censurado no suma a la
relevancia visible de la nube publica.

## Politica de recalculo tras edicion o censura

- Censura manual de palabra:
  la palabra desaparece de la nube publica inmediatamente y `query` debe
  excluirla en la siguiente lectura.
- Restauracion manual de palabra:
  la palabra vuelve a ser elegible para nube y timeline en la siguiente
  lectura.
- Edicion manual de palabra:
  se recalculan normalizacion, forma canonica, agregados y `relevanceScore`.
- Edicion manual de detalle:
  se recalcula censura automatica del detalle e intencion del detalle.
- Censura de detalle:
  no elimina la palabra de la nube si la palabra sigue visible.
- Todo cambio de moderacion debe disparar actualizacion de `stats` y `query`
  por HTTP sincrono en el PoC.
- No existe historial de versiones; solo el estado actual consolidado.

## Rutas frontend propuestas

- `/`
  portal de entrada y acceso rapido por `friendlyId`
- `/login`
  acceso de organizador o moderador
- `/c/{friendlyId}`
  home publica de la conferencia
- `/c/{friendlyId}/doubts`
  nube publica de dudas
- `/c/{friendlyId}/topics`
  nube publica de temas
- `/c/{friendlyId}/words/:word`
  timeline de una palabra segun la categoria activa
- `/dashboard`
  portal privado del organizador
- `/dashboard/conferences/new`
  creacion de conferencia
- `/dashboard/conferences/:conferenceId/moderation/messages`
  moderacion paginada de mensajes
- `/dashboard/conferences/:conferenceId/moderation/words`
  moderacion paginada de palabras

## Supuestos por validar

- La implementacion exacta de guards de frontend se definira al construir el
  portal web.
