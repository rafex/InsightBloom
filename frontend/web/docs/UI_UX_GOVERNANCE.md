# Gobierno del sistema UI — InsightBloom Web

Este documento es el contrato para extender la interfaz. Su objetivo es que una pantalla
nueva reutilice el sistema existente en lugar de crear otra variante visual.

## Fuentes canónicas

| Necesidad | Recurso obligatorio |
|---|---|
| Tipografía de producto | `src/styles/global.css` + `@font-face` de Assistant en `src/assets/fonts/assistant/` |
| Colores, estados y superficies | Tokens `--color-*` de `src/styles/global.css` |
| Tipografía UI/código | `--font-family-sans` y `--font-family-mono` |
| Botones | `src/components/ui/BaseButton.vue` |
| Enlaces con apariencia de botón | `.link-btn-*` de `src/styles/global.css` |
| Campos | `src/components/ui/FormField.vue` + baseline global de `input`, `select`, `textarea` |
| Confirmaciones | `src/components/ui/BaseModal.vue` |
| Notificaciones | `src/components/ui/AppToast.vue` |
| Menús de acciones | `src/components/DropdownMenu.vue` |
| Navegación contextual | `src/components/DashboardBreadcrumb.vue` |

## Reglas de diseño

1. Antes de crear un componente, buscar primero en este catálogo y en
   `src/components/ui/`. Si existe una capacidad equivalente, se extiende el componente
   canónico con una prop o slot; no se copia su CSS.
2. Una página nueva no puede definir `.btn-primary`, `.btn-secondary`, `.btn-danger`,
   `.btn-ghost` ni variantes `.link-btn-*` locales.
3. Los colores nuevos se agregan como tokens semánticos en `global.css`, no como hex dentro
   de un `<style scoped>`. Un color de mapa, visualización o tema editorial debe documentar
   su excepción junto al código.
4. Los formularios nuevos usan `FormField`, conservan `id` y `aria-describedby` del slot, y
   no redefinen la geometría global de los controles sin una excepción aprobada.
5. La familia UI es Assistant. Se permite `--font-family-mono` para código, identificadores y
   contenido técnico, y una fuente editorial solo cuando la página declara un tema de lectura.
6. Todo cambio que agregue una excepción debe actualizar este documento y explicar por qué la
   reutilización no es semánticamente correcta.

## Gate automatizado

`npm run lint:ui-governance` inspecciona `src/pages` y `src/components` en CI. Actualmente
reporta **1,265 colores hex locales**, frente al baseline histórico de 1,278; el límite solo
puede bajar. También falla si reaparecen selectores canónicos de botones en estilos locales.

El gate es una baranda de transición, no sustituye la migración. Cada lote debe reducir el
conteo y mover los colores compartidos a tokens. Las redefiniciones históricas de cinco
archivos están temporalmente en allowlist y se imprimen como deuda; una redefinición nueva sí
rompe CI. No se debe aumentar el baseline ni la allowlist para ocultar una regresión.

## Flujo para un componente nuevo

1. Describir la necesidad y verificar el catálogo.
2. Reutilizar un componente existente; si falta una variante, agregarla allí con prueba.
3. Consumir tokens semánticos, no valores hex locales.
4. Añadir la pantalla usando el componente canónico.
5. Ejecutar `lint`, `lint:ui-governance`, `typecheck`, `test` y `build`.
6. Actualizar el inventario y registrar la excepción si aplica.

La revisión de UI debe rechazar una redefinición cuando la misma interacción ya está cubierta
por el catálogo.
