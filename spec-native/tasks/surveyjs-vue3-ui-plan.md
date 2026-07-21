# Plan: incorporar SurveyJS Vue 3 como motor independiente de encuestas

## Estado

- Estado: `todo`
- Tipo: plan de implementación
- Alcance: incorporar `survey-core` + `survey-vue3-ui` como motor separado, sin mezclarlo con el flujo actual
- Referencia: [documentación oficial de SurveyJS para Vue 3](https://surveyjs.io/form-library/documentation/get-started-vue)

## Objetivo

Permitir que un moderador cree y publique encuestas seleccionando un motor desde el inicio:

1. El motor y editor actuales de InsightBloom (`NATIVE`).
2. SurveyJS Form Library (`SURVEYJS`), basado en definiciones JSON y con su propio renderizado, persistencia y envío de respuestas.

El motor queda fijo para esa encuesta desde su creación. No se convierten preguntas,
respuestas, resultados ni configuraciones entre motores. La única funcionalidad
compartida será la sugerencia de preguntas con IA, que se presenta al moderador y
se transforma al formato del motor elegido antes de guardarse.

Ambos modos deben respetar las reglas actuales de acceso: cuando el evento requiere boleto, el servidor debe validar registro y boleto antes de aceptar una respuesta. La IA debe seguir ayudando a sugerir preguntas, pero siempre como borrador sujeto a revisión y aprobación del moderador.

## Análisis del estado actual

El sistema existente tiene un modelo normalizado por pregunta y respuesta:

- `SurveyQuestion` soporta `RATING`, `TEXT`, `MULTIPLE_CHOICE`, `OPEN_GRADED`, `CODE_GRADED`, `CANVAS_DRAWING` y `DRAG_DROP`.
- `SurveyResponse` guarda una respuesta por pregunta y alimenta resultados, gráficas y calificación.
- `SurveyManagePage.vue` permite crear, editar, desactivar, calificar y consultar resultados.
- `SurveyPage.vue` renderiza controles propios para los tipos actuales.
- La IA ya sugiere preguntas y puede mejorarlas mediante los endpoints existentes.
- El envío de respuestas ya está protegido en backend por el acceso del participante al evento.

La integración no debe intentar convertir el modelo actual a SurveyJS. Las preguntas
de dibujo, código y las reglas de calificación del motor propio siguen perteneciendo
exclusivamente a `NATIVE`; SurveyJS utiliza sus propios tipos y reglas.

## Decisión técnica propuesta

Para `SURVEYJS`, agregar una definición de encuesta independiente de las filas
existentes de `survey_questions`:

```text
SurveyDefinition
  uuid
  conferenceUuid
  engine: SURVEYJS
  schemaJson
  schemaVersion
  status: DRAFT | PUBLISHED | ARCHIVED
  source: MANUAL | AI_ASSISTED | IMPORTED
  createdBy
  updatedBy
  createdAt
  updatedAt
  publishedAt
```

La selección `NATIVE` o `SURVEYJS` ocurre antes de crear la primera pregunta y no se
puede cambiar dentro de la misma encuesta. La definición SurveyJS debe almacenarse
en backend, no únicamente en el navegador. Para la primera versión se recomienda una
sola definición activa por evento; si el evento usa `NATIVE`, no se crea ni consulta
una definición SurveyJS.

Las respuestas SurveyJS deben almacenarse en una tabla nueva de envíos completos, por ejemplo `survey_submissions`, con el JSON recibido, la versión de la definición y el usuario autenticado. Esto evita romper los resultados históricos actuales y permite soportar tipos de SurveyJS que aún no existen en `SurveyResponse`.

## Qué aporta SurveyJS Form Library y qué queda fuera

`survey-core` + `survey-vue3-ui` son la librería MIT de renderizado de formularios
SurveyJS: reciben un modelo JSON, crean un `Model`, lo muestran con
`SurveyComponent` y exponen el resultado mediante `survey.data` al completar.
También ofrecen paginación, validación, lógica condicional, temas y localización.
La integración debe conservar los avisos de copyright y licencia MIT.

Esta iniciativa no integra `survey-creator-vue`, `survey-pdf-generator` ni
`survey-analytics`/Dashboard. Esos componentes requieren licencia comercial.
La autoría inicial será controlada dentro de InsightBloom, apoyada por las
sugerencias de IA; la incorporación de un editor visual comercial queda como una
iniciativa futura separada.

## Separación entre motores

| Área | `NATIVE` | `SURVEYJS` |
|---|---|---|
| Definición | Filas de `survey_questions` | JSON de SurveyJS |
| Renderizado | Controles propios de `SurveyPage.vue` | `SurveyComponent` |
| Respuestas | `survey_responses` normalizadas | `survey_submissions` con JSON completo |
| Resultados | Resultados y calificación actuales | Vista/adapter específico de SurveyJS |
| Tipos de pregunta | Catálogo actual, incluido código y dibujo | Catálogo permitido de SurveyJS |
| Conversión entre motores | No aplica | No aplica |

La administración debe indicar el motor de la encuesta y mostrar el resultado con el
flujo correspondiente. No se reutilizan silenciosamente tablas, tipos o reglas de
calificación entre motores.

## Plan de trabajo

### Fase 0: cerrar el contrato funcional

- Exigir la selección de `NATIVE` o `SURVEYJS` antes de crear la primera pregunta.
- Mantener el motor seleccionado fijo durante toda la vida de la encuesta.
- Confirmar que no habrá conversión ni mezcla de preguntas, respuestas o resultados.
- Definir si un evento puede tener sólo una encuesta activa o varias definiciones/versiones publicadas.
- Definir la política de una respuesta por participante: mantener el comportamiento actual o permitir reenvío controlado.
- Delimitar el catálogo inicial de tipos SurveyJS permitidos.
- Definir el catálogo inicial de tipos SurveyJS y su analítica específica.
- Definir cómo se autoriza y conserva el uso futuro de componentes comerciales.
- Registrar estas decisiones en la especificación de la iniciativa antes de migrar tablas.

### Fase 1: modelo y migraciones backend

- Crear la entidad/repositorio para `SurveyDefinition` y su historial de versiones o revisiones; sólo aplica a `SURVEYJS`.
- Crear la persistencia de `survey_submissions` con:
  - `conference_uuid`, `definition_uuid` y `definition_version`;
  - `user_uuid` derivado del token;
  - `answers_json` y metadatos mínimos de envío;
  - timestamps, estado e idempotency key si se requiere evitar duplicados.
- Agregar índices por evento, definición, versión y usuario.
- Mantener sin cambios las tablas actuales de preguntas y respuestas.
- Añadir límites de tamaño, profundidad y cantidad de elementos JSON para evitar definiciones o respuestas abusivas.

### Fase 2: API de definiciones

Agregar endpoints protegidos por propiedad/moderación del evento, siguiendo el estilo existente:

- `GET /api/v1/conferences/{conferenceId}/survey/definition`: obtener la definición publicada que debe renderizar el participante.
- `GET /api/v1/conferences/{conferenceId}/survey/definition/draft`: obtener el borrador del moderador.
- `PUT /api/v1/conferences/{conferenceId}/survey/definition`: guardar o actualizar el borrador.
- `POST /api/v1/conferences/{conferenceId}/survey/definition/validate`: validar sin publicar.
- `POST /api/v1/conferences/{conferenceId}/survey/definition/publish`: validar y publicar una versión inmutable.
- `POST /api/v1/conferences/{conferenceId}/survey/submissions`: recibir respuestas SurveyJS; las rutas actuales de `NATIVE` permanecen sin cambios.

El backend debe validar en publicación y en envío. El cliente nunca debe decidir por sí solo si una persona tiene acceso.

### Fase 3: validación y seguridad de SurveyJS

- Permitir únicamente tipos de pregunta incluidos en la lista blanca del producto.
- Validar nombres únicos de elementos, longitud de textos, cantidad de opciones y reglas de obligatoriedad.
- Rechazar funciones, scripts, HTML no permitido y expresiones que permitan ejecutar lógica arbitraria.
- Validar que cada respuesta pertenezca a un elemento de la definición publicada y que la versión enviada siga vigente.
- Derivar el usuario del token; no confiar en un `userUuid` enviado desde el navegador.
- Reutilizar la validación actual de registro y boleto para `POST /survey/submissions`.
- Aplicar la misma política de expiración y acceso del evento a la lectura de la encuesta y al envío.
- Registrar quién creó, modificó, publicó o generó con IA cada definición.

### Fase 4: frontend y experiencia de moderación

- Instalar `survey-vue3-ui` y sus dependencias en `frontend/web`.
- Cargar los estilos de SurveyJS de forma controlada para no alterar el editor actual.
- Crear un `SurveyJsRenderer.vue` que:
  - cargue la definición publicada;
  - cree el `Model` de SurveyJS;
  - renderice con `SurveyComponent`;
  - envíe `survey.data` al endpoint nuevo al completar;
  - muestre estados de carga, error, boleto requerido, enviado y encuesta cerrada.
- Mantener `SurveyPage.vue` para el motor `NATIVE`.
- Seleccionar el renderer según `engine`; si una encuesta `SURVEYJS` no tiene
  definición publicada, mostrar un estado de error controlado y no degradarla
  silenciosamente a `NATIVE`.
- Crear la selección obligatoria de motor antes de abrir el editor de preguntas.
- Mantener separadas las áreas de administración `NATIVE` y `SURVEYJS`.
- Implementar una autoría guiada: catálogo de elementos permitidos, edición de propiedades relevantes, vista previa y guardado de borrador.
- Evitar exponer un editor JSON sin límites como única interfaz; si se incluye para soporte avanzado, debe tener validación, vista previa y mensajes de error claros.
- Permitir cambiar de modo sólo desde la administración del evento y advertir cuando el cambio no migre preguntas o respuestas automáticamente.

### Fase 5: integración de IA

- Conservar los endpoints actuales de sugerencia y mejora como servicio compartido para ambos motores.
- Definir un contrato neutral de sugerencia:

```json
{
  "text": "Pregunta sugerida",
  "type": "MULTIPLE_CHOICE",
  "options": ["Opción A", "Opción B"],
  "required": false,
  "referenceAnswer": null
}
```

- Adaptar la sugerencia aprobada al formato del motor seleccionado: pregunta nativa para `NATIVE` o elemento JSON para `SURVEYJS`.
- Ofrecer una acción de “sugerir preguntas” dentro de cada editor; nunca copiar automáticamente preguntas del otro motor.
- Validar el resultado de la IA con las reglas del motor seleccionado.
- Mostrar al moderador la fuente de la sugerencia, permitir aceptar, editar o rechazar cada pregunta y evitar publicación automática.
- Mantener el contexto actual de la presentación, aplicando límites de tamaño y sanitización antes de enviarlo al proveedor LLM.
- No permitir que instrucciones encontradas dentro de la presentación alteren permisos, endpoints o reglas de publicación.

### Fase 6: resultados y operación

- Implementar resultados específicos de SurveyJS sin reutilizar el agregador de `NATIVE`.
- Agregar conteos, porcentajes, respuestas libres y exportación del JSON original.
- Definir una presentación explícita para tipos sin analítica agregada; no descartarlos silenciosamente.
- Mantener la calificación y el flujo de respuestas individuales actuales para encuestas `NATIVE`.
- La calificación LLM existente continúa siendo exclusiva de `NATIVE` en esta fase.
- Añadir métricas de carga de definición, errores de validación, envíos aceptados/rechazados y tasa de finalización por modo.

### Fase 7: pruebas y despliegue gradual

- Pruebas unitarias de normalización, validación de schema y adaptación de resultados.
- Pruebas de API para borrador, validación, publicación, versionado, acceso con/sin boleto, definición expirada y respuestas duplicadas.
- Pruebas frontend para renderizado, validación obligatoria, lógica condicional, envío y estados de error.
- Pruebas de regresión que demuestren que el editor y renderer actuales siguen funcionando sin cambios.
- Prueba end-to-end con un evento piloto y una encuesta con rating, selección, texto y varias páginas.
- Activar primero mediante feature flag para moderadores o eventos seleccionados.
- Mantener `NATIVE` como plan de reversión para encuestas nuevas mientras se observa el piloto; las encuestas `SURVEYJS` siguen usando exclusivamente su engine fijado.

## Criterios de aceptación

- Un moderador debe elegir el motor antes de crear la primera pregunta.
- Un moderador puede crear y publicar una encuesta SurveyJS sin perder las encuestas existentes.
- Un participante con acceso puede verla y completarla; una persona sin registro o boleto recibe el bloqueo actual del servidor.
- Una definición publicada queda versionada y no cambia por editar posteriormente el borrador.
- Las respuestas se guardan asociadas a evento, usuario y versión de definición.
- La IA puede sugerir preguntas para ambos motores, pero el moderador debe aprobarlas antes de guardarlas o publicarlas.
- Las preguntas actuales de código y dibujo continúan disponibles en el modo actual.
- Los resultados existentes no se migran ni se rompen; el modo SurveyJS muestra como mínimo analítica para rating, selección y texto.
- Una definición inválida, demasiado grande o con tipos no permitidos no puede publicarse ni procesarse.

## Fuera de alcance inicial

- Reemplazar `SurveyPage.vue` o `SurveyManagePage.vue`.
- Migración automática de todas las encuestas históricas al formato SurveyJS.
- Integrar `survey-creator-vue`, PDF Generator o Dashboard comerciales.
- Replicar desde el primer corte toda la calificación de código, dibujo y tipos personalizados.
- Permitir que el frontend omita las reglas de boleto, registro o expiración del evento.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Dos modelos de encuesta aumentan la complejidad | Seleccionar el modo explícitamente y mantener adaptadores pequeños y versionados |
| Resultados incompletos para tipos avanzados | Definir un catálogo inicial y conservar siempre la respuesta JSON original |
| Definiciones inseguras generadas o editadas por usuarios | Validación server-side, whitelist, límites y publicación por moderador |
| Cambios visuales por estilos SurveyJS | Aislar estilos y validar las pantallas existentes en regresión visual |
| Confusión entre Form Library y constructor visual | Documentar que `survey-vue3-ui` sólo renderiza JSON; el Creator queda fuera de esta iniciativa |
| Mezcla accidental entre motores | Fijar el engine al crear la encuesta y mantener persistencia, API, render y resultados separados |
| Uso incorrecto de componentes comerciales | Mantener fuera Creator, PDF Generator y Dashboard hasta aprobar licencia comercial |

## Orden recomendado de implementación

1. Selección obligatoria de engine y contrato funcional.
2. Migraciones y API separadas para `SURVEYJS`.
3. Validación y seguridad server-side.
4. Renderer SurveyJS para participantes.
5. Autoría controlada SurveyJS para moderadores.
6. Integración de sugerencias de IA en ambos editores.
7. Resultados específicos, pruebas end-to-end y despliegue gradual.
