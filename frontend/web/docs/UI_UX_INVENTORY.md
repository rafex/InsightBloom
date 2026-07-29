# Inventario UI/UX — InsightBloom

Fecha de corte: 2026-07-28 20:40

Este inventario acompaña la auditoría `spec-native/tasks/UI-UX-AUDIT-2026-07-28.md` y registra los
elementos canónicos que deben usarse en nuevas pantallas y migraciones.

## Fundaciones

| Categoría | Fuente canónica | Estado |
|---|---|---|
| Colores y estados | `src/styles/global.css` (`--color-*`) | Consolidado |
| Tipografía UI | `src/styles/global.css` (`@font-face`, `--font-family-sans`) | Consolidado |
| Tipografía técnica | `src/styles/global.css` (`--font-family-mono`) | Consolidado |
| Espaciado | `src/styles/global.css` (`--space-*`) | Consolidado |
| Radios y sombras | `src/styles/global.css` (`--radius-*`, `--shadow-*`) | Consolidado |
| Campos | `src/components/ui/FormField.vue` + baseline global | Disponible |
| Botones | `src/components/ui/BaseButton.vue` | Disponible |
| Botones-enlace | clases `.link-btn-*` en `src/styles/global.css` | Disponible |
| Modales | `src/components/ui/BaseModal.vue` | Disponible |
| Interruptores | `src/components/ui/ToggleSwitch.vue` | Disponible |
| Notificaciones | `src/components/ui/AppToast.vue` | Disponible |
| Menús de acciones | `src/components/DropdownMenu.vue` | Disponible |
| Breadcrumbs | `src/components/DashboardBreadcrumb.vue` | Consolidado |

## Reglas de uso

- Una sección debe tener una sola acción primaria (`BaseButton` sin `variant` o
  `link-btn-primary`).
- Acciones de apoyo usan `variant="secondary"`; navegación de baja frecuencia usa `ghost`.
- Eliminar, revocar, banear o purgar usa `danger` y requiere confirmación.
- Todo control iconográfico debe conservar texto visible, `title` o `aria-label`.
- Todo control interactivo debe conservar `:focus-visible`; no se debe usar `outline: none` sin
  un reemplazo accesible.
- Los formularios nuevos deben usar `FormField` para asociar etiqueta, hint y error al control.

## Evidencia de adopción al corte

- 30 archivos usan `BaseButton`.
- No quedan definiciones locales de `.btn-primary {` en `src/pages` o `src/components`.
- `global.css` contiene los tokens compartidos, `@font-face` local para Assistant y baseline para
  `input`, `select` y `textarea`.
- `DashboardBreadcrumb` ya no agrega un `Panel` duplicado y expone `aria-current` en el crumb actual.
- La lista de eventos cambia a tarjetas apiladas en viewport angosto y fue comprobada sin overflow
  horizontal en 375, 768 y 1280 px.

## Límites conocidos

La migración de `FormField` sigue siendo gradual; las pantallas existentes que aún usan grupos
locales se mantienen funcionales y deben migrarse por lotes separados. La prueba completa del
dashboard autenticado requiere una sesión de prueba; no se versionan credenciales ni tokens.

Las reglas de incorporación y el gate de CI están en
[`UI_UX_GOVERNANCE.md`](./UI_UX_GOVERNANCE.md).
