# Auditoría de responsividad — InsightBloom Web

> Documento vivo. Se actualiza cada vez que se revisa o corrige un ítem. No es un
> reporte de una sola vez: es el backlog de responsividad del frontend.

- **Última actualización:** 2026-07-28
- **Alcance:** `frontend/web` (SPA Vue 3), breakpoints objetivo: móvil (~375px),
  tablet (~768px) y escritorio (≥1280px).
- **Metodología:**
  1. Navegación real en el sitio en producción (`insightbloom.v1.rafex.cloud`) con el
     panel de navegador en 375×812 (móvil), 768×1024 (tablet) y 1440×900 (escritorio),
     sobre las páginas públicas (login, cartelera, detalle de evento, checkout).
  2. Auditoría estática del código: se revisaron las 74 páginas de `src/pages` buscando
     `@media`, anchos fijos en `px`, `flex-wrap`, y el patrón `overflow-x:auto` para tablas.
     Las páginas del dashboard/moderador no se pudieron probar en vivo en esta pasada
     porque requieren sesión autenticada y no había credenciales disponibles — quedan
     marcadas como "verificar visualmente" en vez de "confirmado en navegador".

## Resumen ejecutivo

El sitio parte de una base sólida: hay tokens de diseño globales, un patrón consistente
de `overflow-x: auto` para tablas, un sidebar de dashboard con hamburguesa colapsable a
768px, y las páginas públicas más importantes (login, cartelera de eventos, detalle de
evento, checkout) se probaron en vivo en los tres breakpoints y se ven y funcionan bien.
Esto **no es un sitio roto en móvil** — el trabajo pendiente es incremental, no una
reescritura.

El backlog de abajo son las brechas reales encontradas, priorizadas por impacto.

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

### P1 — Confirmado, con arreglo concreto

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

- [ ] `ConferenceConfigPage.vue` — la página más grande y compleja del dashboard (~1000
  líneas, muchas subsecciones: sandbox, incidentes, asientos, acceso por dispositivo,
  red). Tiene `.table-scroll` y `flex-wrap` en las filas, pero nunca se confirmó en
  pantalla angosta real.
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
