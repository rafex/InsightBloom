# Auditoría de responsividad y UX/UI — InsightBloom Web

> Documento vivo. Se actualiza cada vez que se revisa o corrige un ítem. No es un
> reporte de una sola vez: es el backlog de responsividad + consistencia visual del
> frontend.

- **Última actualización:** 2026-07-28
- **Alcance:** `frontend/web` (SPA Vue 3), breakpoints objetivo: móvil (~375px),
  tablet (~768px) y escritorio (≥1280px). Excluye a propósito las páginas que embeben
  herramientas de terceros (Excalidraw, Jitsi, drawio/mermaid, Etherpad, Monaco/ttyd) —
  ver sección P3.
- **Metodología:**
  1. Navegación real en el sitio en producción (`insightbloom.v1.rafex.cloud`) con el
     panel de navegador en 375×812 (móvil), 768×1024 (tablet) y 1440×900 (escritorio).
  2. Primera pasada (2026-07-27): solo páginas públicas (login, cartelera, detalle de
     evento, checkout) porque no había sesión autenticada disponible; el dashboard se
     auditó de forma estática (grep de `@media`, anchos fijos, `flex-wrap`).
  3. Segunda pasada (2026-07-28), con sesión autenticada real: se recorrieron Panel,
     Eventos, "Nuevo evento" y Configuración del evento (con sus 6 pestañas) en 375px,
     click por click, revisando además los inputs/selects/textareas de cada formulario.
     Las credenciales usadas para esta sesión fueron rotadas por el dueño de la cuenta
     al terminar la auditoría — ver `feedback-per-service-config-independence` no aplica
     acá, es solo una nota de higiene de acceso, no un hallazgo de producto.

## Resumen ejecutivo

El sitio parte de una base sólida: hay tokens de diseño globales, un patrón consistente
de `overflow-x: auto` para tablas, un sidebar de dashboard con hamburguesa colapsable a
768px, y tanto las páginas públicas (login, cartelera, detalle de evento, checkout) como
las principales del dashboard (Panel, Eventos, Nuevo evento, Configuración) se probaron
en vivo en 375px y se ven y funcionan bien en su estructura general. Esto **no es un
sitio roto en móvil** — el trabajo pendiente es incremental, no una reescritura.

El hallazgo más importante de esta segunda pasada **no es de responsividad sino de
consistencia visual**: ver P0 abajo. El backlog completo está priorizado por impacto.

## Fortalezas ya presentes (para no repetir trabajo)

- Tokens CSS globales en [`global.css`](../src/styles/global.css) (Fase UX F1.1).
- Componentes base (`BaseButton`, `BaseModal`, `AppToast`, `FormField`) con estilos
  consistentes (Fase UX F1.2).
- Patrón `.table-scroll { overflow-x: auto }` aplicado en todas las tablas de admin
  (`AdminUsersPage`, `RolesAdminPage`, `EventTypesAdminPage`, `ConferenceConfigPage`).
- `ConferencesListPage.vue` convierte la tabla completa en tarjetas apiladas por debajo
  de 768px (`@media (max-width: 768px)`), incluyendo un breakpoint extra en 380px.
- `DashboardLayout.vue` tiene sidebar colapsable con botón hamburguesa y backdrop desde
  768px hacia abajo — patrón correcto, no hace falta tocarlo.
- `ConferencePage.vue` (vista del asistente, la página pública más usada desde el
  teléfono) ya tiene su propio `@media (max-width: 640px)` para el tab bar con scroll
  horizontal (Fase UX F2.4).
- `AppHeader.vue` tiene un `@media (max-width: 480px)` propio.

## Backlog priorizado

Formato de cada ítem: página/componente, qué se rompe o qué falta verificar, y una
propuesta de solución. Marcar `[x]` al resolver y agregar la fecha + commit.

### P0 — Consistencia visual de formularios y botones (no es un bug de responsividad)

Reportado directamente por el usuario tras recorrer varias pantallas: "selects, inputs
de formulario son diferentes y algunos botones también son diferentes". Confirmado con
un conteo real sobre el código (2026-07-28):

- `global.css` **no tiene ningún estilo base** para `input`, `select` ni `textarea` —
  cada página define el suyo (o hereda el look nativo del navegador tal cual).
- **26 de 50 páginas** (`grep -rl '\.btn-primary\s*{' src/pages`) definen su **propia**
  clase `.btn-primary` en `<style scoped>`, con paddings (`8px 18px` vs `10px 22px`) y
  tamaños de fuente (`0.875rem` vs `0.95rem` vs `1rem`) ligeramente distintos entre sí.
  El color de fondo (`#4f46e5`) coincide en casi todas, pero el tamaño/padding no.
- El proyecto **ya tiene** los componentes compartidos para esto — `BaseButton.vue`,
  `FormField.vue` (Fase UX F1.2) — pero la adopción es prácticamente nula: **solo 1
  página usa `BaseButton`** y **ninguna usa `FormField`**.

Esto explica exactamente lo que se ve: cada pantalla "casi" se parece a las demás pero
nunca es pixel-idéntica, porque no hay una fuente única de verdad para estos controles.

**No se aborda en esta pasada** — migrar 50 páginas de inputs/selects/botones sueltos a
`FormField`/`BaseButton` es un rediseño con superficie grande (cada página cambia su
DOM y hay que revisar que ningún `v-model`/evento se rompa), no un fix puntual. El plan
de ejecución completo (inventario exacto de los 29 archivos, contrato de los
componentes, orden de migración, checklist y criterios de aceptación) está en
[`DESIGN_SYSTEM_MIGRATION.md`](./DESIGN_SYSTEM_MIGRATION.md) — pensado para que otra
sesión (humana o de IA) lo ejecute sin tener que redescubrir el contexto.

### P1 — Confirmado, con arreglo concreto

- [x] **`NewConferencePage.vue` — fila "Precio y moneda del boleto" desborda en 375px.**
  `.price-row { grid-template-columns: 1fr 1fr }` sin `@media`; el `<select>` de moneda
  ("MXN — Peso mexicano") es más ancho que su columna de 1fr y se corta contra el borde
  derecho de la pantalla (confirmado visualmente, con scroll horizontal visible).
  **Corregido en esta auditoría** (2026-07-28): `@media (max-width: 480px) { .price-row
  { grid-template-columns: 1fr } }`, mismo patrón que ya usaba `.coords-row` en el
  mismo archivo.
- [x] **`ConferenceConfigPage.vue` — placeholder literal `&#10;` en vez de salto de
  línea.** En la pestaña Red, el textarea de "Lista blanca adicional" tenía
  `placeholder="un-dominio-extra.com&#10;*.otro-dominio.org")` escrito en Pug — Pug no
  decodifica entidades HTML dentro de un atributo de string, así que el usuario veía el
  texto literal `&#10;` en el placeholder en vez de un salto de línea. **Corregido en
  esta auditoría** (2026-07-28): cambiado a `\n` (sintaxis de string JS, que sí funciona
  en un atributo de Pug).
- [x] **`TicketManagementPage.vue` — fila de boleto sin `flex-wrap`.** `.ticket-row` y
  `.row-actions` eran `display:flex` sin `flex-wrap`. Desde que se agregó el botón
  "Reenviar" (2026-07-27) cada fila tiene 4 botones (QR, Copiar UUID, Reenviar,
  Revocar) + el bloque de texto del boleto compitiendo por ancho en una sola línea —
  en 375px esto desborda o aprieta los botones. **Corregido en esta auditoría**
  (2026-07-28): se agregó `flex-wrap: wrap` a ambas reglas y `justify-content:
  flex-end` a `.row-actions` para que los botones se acomoden en una segunda línea en
  vez de desbordar.

### P2 — Verificar visualmente en el sitio real (no se pudo confirmar sin sesión)

Páginas del dashboard sin ningún `@media` propio. La mayoría usa `flex-wrap` y
`max-width` fluido, así que es probable que degraden razonablemente bien, pero no está
confirmado con captura real en 375px. Revisar en el teléfono o con sesión de prueba:

- [x] `ConferenceConfigPage.vue` — **confirmado en vivo en 375px (2026-07-28)**: las 6
  pestañas (General/Herramientas/IDE y sandboxes/Acceso y roles/IA/Red) envuelven bien,
  los formularios se apilan correctamente. Los dos bugs reales que sí tenía (fila de
  precio y placeholder roto) están en P1, ya corregidos. Quedan sin confirmar en vivo
  las subsecciones de sandbox/incidentes/mapa de asientos dentro de esta misma página
  (no se llegó a esas pestañas en esta pasada).
- [ ] `ModerationToolsPage.vue` (candado por herramienta) — tarjetas por herramienta con
  toggle + lista expandible de asistentes; confirmar que la lista de asistentes no
  desborda en 375px.
- [ ] `ModerationIdePage.vue`, `ModerationWordsPage.vue`
- [ ] `AdminDeviceAccessPage.vue`, `DeviceBlocksPage.vue`, `AdminEgressPolicyPage.vue`
- [ ] `VenueMapEditorPage.vue` — editor de mapa de asientos; por naturaleza (dibujar un
  plano) es candidato fuerte a necesitar una advertencia de "mejor en escritorio" en vez
  de intentar que el editor completo funcione en el teléfono.
- [ ] `UserDetailPage.vue` — usa `grid-template-columns: repeat(auto-fit, minmax(...))`,
  debería degradar bien solo, pero sin confirmar.
- [ ] `CheckInScannerPage.vue` — usa la cámara del dispositivo para escanear QR; es
  justamente una pantalla que se va a usar MÁS en teléfono que en escritorio, así que
  amerita prioridad más alta para probarla en un dispositivo real aunque el CSS estático
  se vea razonable (`max-width: 480px`, contenedor de cámara con `aspect-ratio: 1`).

### P3 — Herramientas embebidas de terceros (limitación conocida, no un bug de CSS)

Estas páginas envuelven una herramienta externa (Excalidraw, Jitsi, drawio/mermaid,
Etherpad, Monaco/code-server/ttyd) dentro de un contenedor fluido. El contenedor de
InsightBloom ya es responsivo (`width:100%`, `height: calc(100vh - ...)`), pero la
experiencia *dentro* del iframe/canvas depende de qué tan bien esa herramienta de
terceros funciona en pantallas táctiles pequeñas — eso está fuera del control del CSS
de este repo.

- [ ] `WhiteboardPage.vue` (Excalidraw) — usable en tablet, probablemente incómodo en
  teléfono por el tamaño de los controles de dibujo.
- [ ] `VideoConferencePage.vue` (Jitsi) — Jitsi ya tiene su propia UI responsiva; el
  `.takeover-toolbar` de InsightBloom que se superpone (`position: absolute`) sí debería
  confirmarse que no tapa controles críticos de Jitsi en pantallas chicas.
- [ ] `DiagrammingPage.vue` (drawio/mermaid)
- [ ] `CollabNotesPage.vue` (Etherpad)
- [ ] `IdeSessionPage.vue` (Monaco / code-server / ttyd)

**Propuesta**: en vez de intentar "arreglar" estas cinco páginas para que el editor
completo sea cómodo en un teléfono de 375px (esfuerzo alto, valor dudoso — nadie
programa cómodamente en un teléfono), considerar agregar un aviso liviano tipo "Esta
herramienta se usa mejor en tablet o escritorio" cuando `window.innerWidth < 480`, en
vez de dejar que el usuario descubra la limitación solo. Decisión pendiente del
producto, no una corrección de CSS.

### P4 — Pulido menor / no bloqueante

- [ ] **Tarjetas de evento con flyer recortado** (`PublicEventsPage.vue`,
  `.event-flyer { height: 170px; object-fit: cover }`). Es un recorte de vista previa
  intencional (mismo patrón en todas las tarjetas), pero si el flyer tiene texto
  importante cerca del borde inferior, se corta a media frase — es una decisión de
  contenido/diseño de la miniatura, no un bug de responsividad; se anota acá porque se
  observó durante la auditoría, pero no se propone tocar el CSS sin decidir primero si
  se quiere gradiente de desvanecido, otro `object-position`, o dejarlo así.
- [ ] Confirmar `AppHeader.vue` en 320px (iPhone SE / gama baja) — el `@media` actual
  corta en 480px; en 320px el badge "vlatest · be0011d" (versión de build, visible en
  todos los ambientes) puede quedar muy apretado contra "Entrar". Revisar si ese badge
  debería ocultarse por completo en pantallas angostas.

## Cómo seguir iterando este documento

1. Al resolver un ítem, marcarlo `[x]`, agregar fecha y el hash del commit.
2. Al encontrar un ítem nuevo (en código o probando en un dispositivo real), agregarlo
   en la sección de prioridad que corresponda con el archivo y la línea/selector CSS
   exactos.
3. Cuando un ítem de P2 se confirme visualmente (con o sin bug), moverlo a P1 (si hay
   arreglo) o borrarlo de la lista (si ya se ve bien) — no dejar ítems "verificar" para
   siempre.
4. Antes de cerrar todo P2, priorizar `CheckInScannerPage.vue` por ser la pantalla con
   mayor uso real desde teléfono.
