# Plan: incorporar SurveyJS Vue 3 como segundo modo de encuestas

## Estado

- Estado: `todo`
- Tipo: plan de implementación
- Alcance: incorporar `survey-vue3-ui` sin reemplazar el flujo actual
- Referencia: [documentación oficial de SurveyJS para Vue 3](https://surveyjs.io/form-library/documentation/get-started-vue)

## Objetivo

Permitir que un moderador cree y publique encuestas de dos maneras:

1. El editor y modelo actuales, que permanecen intactos y son el modo por defecto.
2. Un nuevo modo basado en definiciones JSON de SurveyJS, con su propio renderizado, persistencia y envío de respuestas.

Ambos modos deben respetar las reglas actuales de acceso: cuando el evento requiere boleto, el servidor debe validar registro y boleto antes de aceptar una respuesta. La IA debe seguir ayudando a sugerir preguntas, pero siempre como borrador sujeto a revisión y aprobación del moderador.

## Análisis del estado actual

El sistema existente tiene un modelo normalizado por pregunta y respuesta:

- `SurveyQuestion` soporta `RATING`, `TEXT`, `MULTIPLE_CHOICE`, `OPEN_GRADED`, `CODE_GRADED`, `CANVAS_DRAWING` y `DRAG_DROP`.
- `SurveyResponse` guarda una respuesta por pregunta y alimenta resultados, gráficas y calificación.
- `SurveyManagePage.vue` permite crear, editar, desactivar, calificar y consultar resultados.
- `SurveyPage.vue` renderiza controles propios para los tipos actuales.
- La IA ya sugiere preguntas y puede mejorarlas mediante los endpoints existentes.
- El envío de respuestas ya está protegido en backend por el acceso del participante al evento.

La integración no debe intentar convertir todo el modelo actual a SurveyJS. Las preguntas de dibujo, código y algunas preguntas calificables no tienen una equivalencia directa y deben seguir funcionando por el flujo actual.

## Decisión técnica propuesta

Agregar una definición de encuesta a nivel de evento, independiente de las filas existentes de `survey_questions`:

```text
SurveyDefinition
  uuid
  conferenceUuid
  mode: LEGACY | SURVEYJS
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

La definición SurveyJS debe almacenarse en backend, no únicamente en el navegador. Para la primera versión se recomienda una sola definición activa por evento; si no existe una definición SurveyJS publicada, el evento continúa usando el modelo actual.

Las respuestas SurveyJS deben almacenarse en una tabla nueva de envíos completos, por ejemplo `survey_submissions`, con el JSON recibido, la versión de la definición y el usuario autenticado. Esto evita romper los resultados históricos actuales y permite soportar tipos de SurveyJS que aún no existen en `SurveyResponse`.

## Qué aporta `survey-vue3-ui` y qué queda fuera

`survey-vue3-ui` es la librería de renderizado de formularios SurveyJS: recibe un modelo JSON, crea un `Model`, lo muestra con `SurveyComponent` y expone el resultado mediante `survey.data` al completar. También ofrece paginación, validación, lógica condicional, temas y localización.

No es por sí misma un editor visual completo de arrastrar y soltar. Si después se requiere un constructor no-code, debe evaluarse por separado `survey-creator-vue`, incluyendo licenciamiento, bundle y experiencia de autoría. Esta integración inicial se limita a `survey-vue3-ui` y a una autoría controlada dentro de InsightBloom.

## Compatibilidad entre modos

| Concepto actual | SurveyJS inicial | Tratamiento |
|---|---|---|
| `RATING` | `rating` | Adaptación directa para resultados básicos |
| `TEXT` | `text` o `comment` | Adaptación directa |
| `MULTIPLE_CHOICE` | `radiogroup`, `checkbox`, opcionalmente `dropdown` | Adaptación directa con normalización de opciones |
| `OPEN_GRADED` | `comment` | Mantener como respuesta JSON; definir calificación en una fase posterior |
| `CODE_GRADED` | Sin equivalencia nativa segura | Permanecer en modo actual |
| `CANVAS_DRAWING` | Sin equivalencia inicial | Permanecer en modo actual |
| `DRAG_DROP` | `ranking` u otro tipo compatible | Soportar sólo en SurveyJS hasta contar con un adaptador de resultados |
| lógica condicional, páginas y paneles | elementos SurveyJS | Disponibles en el nuevo modo; no convertir silenciosamente al modelo actual |

La vista de resultados debe indicar el modo de la encuesta. Los resultados actuales, calificación y gráficas existentes se conservan para `LEGACY`; para `SURVEYJS` se agrega un adaptador que cubra primero rating, selección y texto, además de permitir exportar la respuesta JSON completa.

## Plan de trabajo

### Fase 0: cerrar el contrato funcional

- Confirmar que el modo actual seguirá siendo el predeterminado.
- Definir si un evento puede tener sólo una encuesta activa o varias definiciones/versiones publicadas.
- Definir la política de una respuesta por participante: mantener el comportamiento actual o permitir reenvío controlado.
- Delimitar el catálogo inicial de tipos SurveyJS permitidos.
- Definir si la primera versión SurveyJS incluirá preguntas abiertas calificables o sólo encuestas de opinión y evaluación no calificable.
- Registrar estas decisiones en la especificación de la iniciativa antes de migrar tablas.

### Fase 1: modelo y migraciones backend

- Crear la entidad/repositorio para `SurveyDefinition` y su historial de versiones o revisiones.
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
- `POST /api/v1/conferences/{conferenceId}/survey/submissions`: recibir respuestas SurveyJS.

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
- Mantener `SurveyPage.vue` para el modo actual.
- Seleccionar el renderer según `mode`, con fallback seguro a `LEGACY` cuando no haya definición SurveyJS publicada.
- Crear una pestaña o ruta adicional en `SurveyManagePage.vue` para elegir “Editor actual” o “SurveyJS”.
- Implementar una autoría guiada: catálogo de elementos permitidos, edición de propiedades relevantes, vista previa y guardado de borrador.
- Evitar exponer un editor JSON sin límites como única interfaz; si se incluye para soporte avanzado, debe tener validación, vista previa y mensajes de error claros.
- Permitir cambiar de modo sólo desde la administración del evento y advertir cuando el cambio no migre preguntas o respuestas automáticamente.

### Fase 5: integración de IA

- Conservar los endpoints actuales de sugerencia y mejora para el modo `LEGACY`.
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

- Agregar un adaptador backend que convierta ese contrato a elementos SurveyJS (`name`, `title`, `type`, `choices`, `isRequired`).
- Ofrecer una acción separada para “generar borrador SurveyJS” a partir de la presentación, pero devolverlo siempre como borrador sin publicar.
- Validar el resultado de la IA con la misma whitelist y reglas que una definición manual.
- Mostrar al moderador la fuente de la sugerencia, permitir aceptar, editar o rechazar cada pregunta y evitar publicación automática.
- Mantener el contexto actual de la presentación, aplicando límites de tamaño y sanitización antes de enviarlo al proveedor LLM.
- No permitir que instrucciones encontradas dentro de la presentación alteren permisos, endpoints o reglas de publicación.

### Fase 6: resultados y operación

- Implementar un `SurveyJsResultAdapter` para rating, selección única/múltiple y texto.
- Agregar conteos, porcentajes, respuestas libres y exportación del JSON original.
- Definir una presentación explícita para tipos sin analítica agregada; no descartarlos silenciosamente.
- Mantener la calificación y el flujo de respuestas individuales actuales para encuestas `LEGACY`.
- Evaluar en una fase posterior la calificación asistida por IA de respuestas SurveyJS abiertas o de código.
- Añadir métricas de carga de definición, errores de validación, envíos aceptados/rechazados y tasa de finalización por modo.

### Fase 7: pruebas y despliegue gradual

- Pruebas unitarias de normalización, validación de schema y adaptación de resultados.
- Pruebas de API para borrador, validación, publicación, versionado, acceso con/sin boleto, definición expirada y respuestas duplicadas.
- Pruebas frontend para renderizado, validación obligatoria, lógica condicional, envío y estados de error.
- Pruebas de regresión que demuestren que el editor y renderer actuales siguen funcionando sin cambios.
- Prueba end-to-end con un evento piloto y una encuesta con rating, selección, texto y varias páginas.
- Activar primero mediante feature flag para moderadores o eventos seleccionados.
- Mantener `LEGACY` como fallback y plan de reversión mientras se observa el piloto.

## Criterios de aceptación

- Un moderador puede crear y publicar una encuesta SurveyJS sin perder las encuestas existentes.
- Un participante con acceso puede verla y completarla; una persona sin registro o boleto recibe el bloqueo actual del servidor.
- Una definición publicada queda versionada y no cambia por editar posteriormente el borrador.
- Las respuestas se guardan asociadas a evento, usuario y versión de definición.
- La IA puede sugerir preguntas para ambos modos, pero el moderador debe aprobarlas antes de publicar.
- Las preguntas actuales de código y dibujo continúan disponibles en el modo actual.
- Los resultados existentes no se migran ni se rompen; el modo SurveyJS muestra como mínimo analítica para rating, selección y texto.
- Una definición inválida, demasiado grande o con tipos no permitidos no puede publicarse ni procesarse.

## Fuera de alcance inicial

- Reemplazar `SurveyPage.vue` o `SurveyManagePage.vue`.
- Migración automática de todas las encuestas históricas al formato SurveyJS.
- Implementar de inmediato un constructor drag-and-drop completo con `survey-creator-vue`.
- Replicar desde el primer corte toda la calificación de código, dibujo y tipos personalizados.
- Permitir que el frontend omita las reglas de boleto, registro o expiración del evento.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Dos modelos de encuesta aumentan la complejidad | Seleccionar el modo explícitamente y mantener adaptadores pequeños y versionados |
| Resultados incompletos para tipos avanzados | Definir un catálogo inicial y conservar siempre la respuesta JSON original |
| Definiciones inseguras generadas o editadas por usuarios | Validación server-side, whitelist, límites y publicación por moderador |
| Cambios visuales por estilos SurveyJS | Aislar estilos y validar las pantallas existentes en regresión visual |
| Confusión entre Form Library y constructor visual | Documentar que `survey-vue3-ui` renderiza JSON; evaluar el Creator sólo como iniciativa aparte |
| Dependencia de licenciamiento o cambios del proveedor | Revisar licencia, versión fijada y compatibilidad antes del despliegue productivo |

## Orden recomendado de implementación

1. Contrato funcional, feature flag y modelo de definición.
2. Migraciones y API de borrador/publicación/envío.
3. Validación y seguridad server-side.
4. Renderer SurveyJS para participantes.
5. Autoría SurveyJS para moderadores.
6. Adaptación de IA y resultados.
7. Pruebas end-to-end, piloto y despliegue gradual.

