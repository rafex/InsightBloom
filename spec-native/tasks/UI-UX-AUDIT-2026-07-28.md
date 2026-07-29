# Auditoría UI/UX del portal InsightBloom

**Fecha:** 2026-07-28  
**Alcance:** dashboard autenticado, gestión de eventos, configuración, IA, certificados, usuarios y roles  
**Versión observada:** `vlatest · 05f0826`  
**Tipo de documento:** diagnóstico y backlog de iteración

## Resumen ejecutivo

La plataforma es funcional y cuenta con una cobertura amplia de operaciones, pero la interfaz ha crecido por acumulación de funcionalidades. El principal problema no es un color aislado: es la falta de una jerarquía común para navegación, acciones, configuración y estados.

Esto produce tres efectos:

1. El usuario no siempre sabe dónde iniciar una tarea.
2. Varias pantallas ofrecen dos caminos para la misma operación.
3. Las acciones importantes y destructivas tienen una prioridad visual demasiado parecida.

### Valoración inicial

| Área | Valoración | Observación |
|---|---:|---|
| Consistencia visual | 5/10 | Hay variantes de botones, colores, radios, sombras, chips e iconos. |
| Arquitectura de información | 5/10 | Se mezclan funciones globales, de evento y operativas. |
| Jerarquía de acciones | 4/10 | Las tablas muestran demasiadas acciones al mismo nivel. |
| Configuración | 5/10 | Las pestañas ayudan, pero todavía existen ámbitos mezclados y guardados parciales. |
| Responsive y densidad | 5/10 | Algunas tablas fuerzan desplazamiento horizontal y comprimen acciones. |
| Madurez UI/UX general | 5/10 | Buena cobertura funcional, pendiente de consolidación visual y conceptual. |

## Evidencia revisada

Se recorrieron, en modo lectura, las siguientes áreas:

- `/dashboard`
- `/dashboard/conferences`
- `/dashboard/conferences/{uuid}/config`
- `/dashboard/conferences/{uuid}/certificate`
- `/dashboard/admin/users`
- `/dashboard/admin/roles`
- `/dashboard/admin/ai`
- `/dashboard/admin/ai/tutor`
- `/dashboard/conferences/new`

## Hallazgos priorizados

### P0 — Corregir antes de seguir agregando variantes

#### UX-001 — Navegación global duplicada

**Problema:** `Panel` e `Inicio` llevan al mismo dashboard. `Cartelera` y `Eventos` aparecen en el mismo menú sin diferenciar claramente la vista pública de la gestión administrativa.

**Impacto:** el usuario no sabe si está entrando a ver eventos públicos o a administrarlos.

**Corrección propuesta:**

- Mantener una sola entrada para el dashboard: `Inicio`.
- Agrupar `Eventos` y `Cartelera` bajo una sección de eventos.
- Diferenciar visualmente `Cartelera pública` de `Mis eventos`.
- Reservar `Panel` para el logotipo o encabezado, no como segundo enlace al inicio.

#### UX-002 — Demasiadas acciones en cada evento

**Problema:** la tabla de eventos concentra Presentación, Público, Certificado, Boletos, Herramientas, Editor, Configuración, Check-in, Desactivar y Eliminar.

También aparece `Presentación` en más de un lugar, lo que hace difícil distinguir entre administrar una presentación y presentarla en vivo.

**Impacto:** baja descubribilidad, errores de selección y tabla difícil de usar en pantallas pequeñas.

**Corrección propuesta:**

- Acción principal: `Abrir evento`.
- Acción secundaria: menú `Gestionar`.
- Menú `Más` para operaciones poco frecuentes.
- Separar visualmente acciones destructivas.
- Mover el resto de operaciones a un espacio contextual del evento.

#### UX-003 — Configuración de Tutor IA duplicada

**Problema:** existe configuración de Tutor IA dentro del evento y también en IA global.

**Impacto:** no queda claro qué configuración tiene prioridad ni dónde debe modificarla el administrador u organizador.

**Corrección propuesta:**

- Proveedor, modelo, URL y secreto únicamente en `IA` global.
- Objetivo, contexto, modo socrático y límites únicamente en la configuración del evento.
- Mostrar explícitamente el alcance: `Configuración global` o `Configuración de este evento`.
- No mostrar ni editar API keys desde la configuración del evento.

#### UX-004 — Certificados globales y certificados por evento parecen dos herramientas principales

**Problema:** el menú lateral muestra `Certificados`, pero el certificado real se diseña desde la acción del evento. La página global indica que es un respaldo, aunque visualmente no parece una herramienta secundaria.

**Corrección propuesta:**

- Renombrar la pantalla global a `Plantilla global`.
- Moverla a `Administración avanzada` o dejarla como fallback claramente identificado.
- Mantener `Certificado` como acción contextual dentro de cada evento.

#### UX-005 — La versión desplegada no representa siempre la versión local más reciente

**Problema:** durante la revisión, el portal mostraba `vlatest · 05f0826`, mientras el repositorio local contenía cambios posteriores relacionados con la reorganización de IA.

**Impacto:** se evalúan comportamientos que pueden no coincidir con el código actual y se dificulta validar correcciones.

**Corrección propuesta:**

- Mostrar commit, fecha de build y ambiente con mayor claridad.
- Añadir una comprobación de despliegue en el flujo de entrega.
- Mantener un checklist de validación post-despliegue.

### P1 — Corregir en la primera iteración visual

#### UX-006 — Falta un sistema de diseño visible y compartido

Se observan variantes de:

- Botón primario, secundario y terciario.
- Color principal y color de alerta.
- Radios y sombras.
- Tamaños tipográficos.
- Chips de estado.
- Iconos y emojis.
- Mensajes de error, éxito e información.

**Corrección propuesta:** definir tokens y componentes compartidos antes de rediseñar cada pantalla.

#### UX-007 — Configuración del evento mezcla demasiados ámbitos

Actualmente una misma pantalla contiene tipo de evento, boletos, herramientas, IDE, sandboxes, red, roles e IA.

**Corrección propuesta:** conservar pestañas, pero con una arquitectura explícita:

- General
- Contenido y herramientas
- Acceso y boletos
- IDE y sandboxes
- Roles y moderación
- IA
- Red

Cada pestaña debe tener un solo objetivo y un patrón de guardado consistente.

#### UX-008 — Guardados fragmentados sin estado global

Cada sección tiene botones como `Guardar tipo de evento`, `Guardar configuración de boletos`, `Guardar configuración del IDE` y otros.

**Riesgo:** el usuario puede abandonar la página sin saber qué partes se guardaron.

**Corrección propuesta:**

- Mostrar estado `Sin cambios`, `Cambios pendientes`, `Guardando` y `Guardado`.
- Confirmar al cambiar de pestaña cuando existan cambios pendientes.
- Mantener botones locales solo cuando la sección realmente sea independiente.

#### UX-009 — Tablas demasiado densas

La tabla de eventos y la de usuarios tienen muchas acciones y, en viewport reducido, requieren desplazamiento horizontal.

**Corrección propuesta:**

- Reducir columnas visibles.
- Convertir acciones secundarias en menús.
- Usar tarjetas responsivas en móvil.
- Mantener la información principal siempre visible: nombre, estado, fecha y acción principal.

#### UX-010 — Roles y usuarios tienen acciones con la misma prioridad visual

En usuarios aparecen `Editar`, `Banear` y `Eliminar` juntos. En roles aparecen `Editar` y `Desactivar` con el mismo peso.

**Corrección propuesta:**

- `Editar`: acción secundaria.
- `Banear`, `Desactivar`: acción de peligro reversible, con confirmación.
- `Eliminar`: acción destructiva, separada y protegida.
- Mostrar el estado mediante un badge consistente.

### P2 — Pulido y accesibilidad

#### UX-011 — Uso irregular de emojis e iconos

Los emojis funcionan como atajos visuales, pero no forman un sistema consistente y cambian el peso visual entre pantallas.

**Corrección propuesta:** usar un conjunto único de iconos SVG con etiquetas accesibles. Mantener emojis solo cuando sean parte deliberada del lenguaje del producto.

#### UX-012 — Breadcrumbs repetitivos

Los breadcrumbs repiten información que ya aparece en la navegación lateral y en encabezados locales.

**Corrección propuesta:**

- Usarlos solo en vistas profundas.
- No repetir el nombre de la pestaña activa como texto redundante.
- Mantener el patrón `Eventos / evento / sección`.

#### UX-013 — Mensajería y estados de carga deben ser uniformes

La aplicación maneja estados de carga, error, vacío y éxito, pero no todos tienen la misma estructura visual ni el mismo nivel de detalle.

**Corrección propuesta:** crear componentes compartidos para:

- Estado vacío.
- Error recuperable.
- Error de permisos.
- Guardado exitoso.
- Carga inicial.
- Operación en progreso.

## Arquitectura de información propuesta

### Menú global

```text
Inicio
Eventos
├── Mis eventos
└── Cartelera pública
Plataforma
├── Usuarios
├── Roles
├── Tipos de evento
├── IA
├── Control de red
└── Acceso por dispositivo
Mi perfil
```

`Plantilla global` y otras funciones legacy deben quedar en `Plataforma` o `Administración avanzada`, no en el mismo nivel que el flujo principal de eventos.

### Espacio de trabajo de un evento

```text
Resumen
Contenido
├── Presentación
├── Encuesta
├── Diagramas
├── Pizarra
└── Notas
Acceso
├── Boletos
└── Check-in
Certificado
Moderación
Configuración
```

La cabecera del evento debe mostrar nombre, estado, fecha y una sola navegación contextual. No debe combinar breadcrumb, pestañas de página y pestañas de configuración sin indicar la relación entre ellas.

## Sistema visual propuesto

### Jerarquía de botones

| Variante | Uso |
|---|---|
| Primario sólido | Una acción principal por sección. |
| Secundario | Acción alternativa o de apoyo. |
| Terciario/texto | Navegación o acción de baja frecuencia. |
| Peligro | Eliminar, desactivar, revocar o banear. |
| Icono | Solo para acciones conocidas; siempre con tooltip y etiqueta accesible. |

### Componentes base a consolidar

- `AppButton`
- `AppTabs`
- `AppBreadcrumbs`
- `StatusBadge`
- `ActionMenu`
- `SectionCard`
- `SaveState`
- `EmptyState`
- `ErrorState`
- `ConfirmDialog`

### Tokens mínimos

- Colores de marca, superficie, texto, borde, éxito, advertencia y peligro.
- Escala tipográfica.
- Escala de espaciado.
- Radios de borde.
- Elevaciones.
- Alturas de controles.
- Estados hover, focus, disabled y loading.

## Backlog incremental

### Fase 1 — Fundaciones

- [ ] **UX-TASK-001:** inventariar colores, tipografías, botones, badges, cards, tabs y mensajes existentes.
- [ ] **UX-TASK-002:** definir tokens visuales compartidos.
- [ ] **UX-TASK-003:** crear o consolidar componentes base.
- [ ] **UX-TASK-004:** agregar estados de focus y etiquetas accesibles.

### Fase 2 — Shell y navegación

- [x] **UX-TASK-005:** eliminar la duplicidad `Panel`/`Inicio`.
- [x] **UX-TASK-006:** agrupar menú global por dominio.
- [x] **UX-TASK-007:** definir navegación contextual de evento.
- [ ] **UX-TASK-008:** simplificar breadcrumbs.

### Fase 3 — Eventos

- [x] **UX-TASK-009:** rediseñar la tabla de eventos con acción principal y menús.
- [x] **UX-TASK-010:** separar acciones destructivas.
- [x] **UX-TASK-011:** eliminar la repetición de `Presentación`.
- [ ] **UX-TASK-012:** crear versión responsiva de la lista.

### Fase 4 — Configuración

- [x] **UX-TASK-013:** reorganizar pestañas por dominio.
- [ ] **UX-TASK-014:** definir estrategia de guardado y cambios pendientes.
- [ ] **UX-TASK-015:** eliminar la duplicidad del Tutor IA.
- [x] **UX-TASK-016:** mover la plantilla global de certificados a una ubicación secundaria.

### Fase 5 — Tablas y acciones administrativas

- [ ] **UX-TASK-017:** aplicar jerarquía visual a usuarios y roles.
- [ ] **UX-TASK-018:** normalizar confirmaciones para banear, desactivar y eliminar.
- [ ] **UX-TASK-019:** normalizar filtros, orden y estados.

### Fase 6 — Validación

- [ ] **UX-TASK-020:** prueba responsive en desktop, tablet y móvil.
- [ ] **UX-TASK-021:** prueba de teclado y lector de pantalla.
- [ ] **UX-TASK-022:** prueba de recorridos principales con organizador, moderador y asistente.
- [ ] **UX-TASK-023:** verificar que la versión desplegada corresponda al commit validado.

## Criterios de aceptación globales

- Cada pantalla tiene una única acción primaria claramente identificable.
- No existen dos enlaces visibles que lleven al mismo destino sin una razón explícita.
- Las funciones globales y las funciones del evento están separadas.
- Las acciones destructivas no compiten visualmente con las acciones normales.
- Las tablas principales funcionan sin desplazamiento horizontal en viewport móvil.
- Todos los controles tienen estados hover, focus, disabled y loading consistentes.
- El usuario puede saber si una configuración está guardada o tiene cambios pendientes.
- El Tutor IA y los certificados tienen una única fuente de configuración por ámbito.
- El build desplegado muestra el commit que fue validado.

## Nota de alcance

Este documento registra una evaluación UI/UX basada en la versión observada del portal. No modifica datos, configuración de eventos ni código de aplicación. Debe utilizarse como base para iteraciones pequeñas y verificables.
