# Migración a design system unificado — InsightBloom Web

> Dirigido a quien (persona o agente de IA) ejecute esta migración. Es un plan de
> ejecución, no solo un diagnóstico: incluye el inventario exacto de archivos, el
> contrato de los componentes ya existentes, el orden de migración sugerido, una
> plantilla de checklist por página y los criterios de aceptación. Ver también
> [`RESPONSIVE_AUDIT.md`](./RESPONSIVE_AUDIT.md) sección P0, que es el origen de este
> documento.

## 1. Problema y causa raíz

El usuario reportó, navegando el sitio real: *"selects, inputs de formularios son
diferentes y algunos botones también son diferentes"*. Confirmado con conteo exacto
sobre el código (2026-07-28):

- **29 archivos** (`src/pages/**` + 3 en `src/components/**`) definen su **propia**
  clase `.btn-primary` en `<style scoped>`, con variaciones reales entre sí:
  - Padding: `8px 18px` vs `10px 22px` vs sin especificar (hereda del navegador).
  - Font-size: `0.875rem` vs `0.95rem` vs `1rem`.
  - Algunas variantes incluyen `border-radius: 8px` explícito, otras no.
  - El color de fondo (`#4f46e5`) es consistente en casi todas — el problema no es el
    color, es la geometría (tamaño, padding, radio).
- **152 usos crudos** de `input`/`select`/`textarea` en plantillas Pug, la inmensa
  mayoría sin pasar por ningún componente compartido — cada uno hereda el estilo que
  esa página en particular le haya dado (o el estilo nativo del navegador si la página
  no le dio ninguno), porque **`global.css` no define ningún baseline** para estas
  etiquetas (hay un token `--border-input: 1.5px solid #d1d5db` ya definido, pero
  nunca aplicado globalmente).
- El proyecto **ya construyó la solución** en la auditoría UX del 2026-07-26
  (`BaseButton.vue`, `FormField.vue`, ver sección 2) pero la adopción quedó casi en
  cero: **solo 1 de 50 páginas** (`ModerationToolsPage.vue`) usa `BaseButton`, y
  **ninguna** usa `FormField`.

Esto es exactamente lo que produce la sensación de inconsistencia: cada pantalla se
parece a las demás pero nunca es pixel-idéntica.

## 2. Contrato de los componentes existentes (no rediseñar, reutilizar)

Ambos componentes ya están implementados, probados visualmente en al menos una página,
y usan los tokens de `global.css`. **No hace falta crear nada nuevo** para botones y
para el wrapper de campo — solo falta (a) adoptarlos en el resto de las páginas y (b)
agregar el baseline que falta para `input`/`select`/`textarea` en sí.

### 2.1 `BaseButton.vue` (`src/components/ui/BaseButton.vue`)

```pug
BaseButton(variant="primary" size="md" :loading="saving" :disabled="!valid" @click="save") Guardar
```

- **Props**: `variant` (`primary` | `secondary` | `danger` | `ghost`, default `primary`),
  `size` (`sm` | `md` | `lg`, default `md`), `type` (default `button`), `disabled`,
  `loading` (muestra spinner + deshabilita automáticamente).
- **Uso de ejemplo real ya en el código**: `src/pages/dashboard/ModerationToolsPage.vue:13`.
- Mapeo desde las clases viejas más comunes:
  - `.btn-primary` → `BaseButton` (variant por defecto `primary`).
  - `.btn-outline` / `.btn-secondary` → `variant="secondary"`.
  - `.btn-danger` / `.btn-delete` / `.btn-trash` / `.btn-revoke` → `variant="danger"`.
  - `.btn-ghost` / `.btn-ghost-sm` → `variant="ghost"`.
  - Tamaños: si la clase vieja tenía `padding` chico (`4-8px`) o `font-size` < 0.85rem →
    `size="sm"`; si era notablemente grande (`12px+` de padding vertical) → `size="lg"`;
    si no estás seguro, `size="md"` (el default) es el caso común.
- **Ojo con botones-enlace**: algunas páginas usan `a.btn-primary` (un `<a>` con esa
  clase, no un `<button>`) para navegación con `router-link`/`href`. `BaseButton` es un
  `<button>`, no sirve directo para eso. Para esos casos, mantener el elemento `a`/
  `router-link` pero aplicarle una clase utilitaria nueva compartida en vez de una
  redefinición local (ver tarea 4.4 más abajo) — no forzar `BaseButton` donde
  semánticamente corresponde un link.

### 2.2 `FormField.vue` (`src/components/ui/FormField.vue`)

```pug
FormField(label="Correo electrónico" :error="emailError" hint="Usá el mismo con el que te registraste" required)
  template(#default="{ id, describedBy }")
    input(:id="id" :aria-describedby="describedBy" v-model="email" type="email")
```

- **Props**: `label` (requerido), `hint`, `error`, `required`.
- El slot por defecto recibe `{ id, describedBy }` — el input real DEBE usar esos dos
  atributos (`:id="id"` y `:aria-describedby="describedBy"`) para que el label y el
  mensaje de error queden asociados correctamente (accesibilidad: sin esto, un lector
  de pantalla no anuncia el error al usuario).
- `FormField` **no estiliza el input en sí** — solo el label/hint/error alrededor. El
  input sigue necesitando el baseline de la tarea 4.1.
- No tiene ejemplo real en el código todavía (adopción = 0) — la primera página que lo
  use es la que fija el patrón para las siguientes.

## 3. Objetivo y no-objetivos

**Objetivo**: que cualquier botón primario, secundario, de peligro o fantasma, y
cualquier campo de formulario (label + input/select/textarea + hint/error) del panel
de InsightBloom se vea y se comporte igual sin importar en qué página esté.

**No-objetivos** (fuera de alcance de esta migración):
- No tocar las páginas que embeben herramientas de terceros (Excalidraw, Jitsi,
  drawio/mermaid, Etherpad, Monaco/ttyd) — esas herramientas tienen su propia UI, no la
  de InsightBloom.
- No rediseñar la paleta de colores ni la tipografía — los tokens de `global.css` ya
  están bien y no cambian.
- No es una reescritura de layout/estructura de página, solo de los controles de
  formulario y botones dentro de cada una.

## 4. Plan de ejecución

### Fase 4.1 — Baseline global para inputs/selects/textareas (bloqueante, hacer primero)

Agregar a `global.css` (junto a los demás tokens, después del bloque `:focus-visible`)
un estilo base usando los tokens que YA existen:

```css
input, select, textarea {
  font: inherit;
  padding: 10px 12px;
  border: var(--border-input);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-text);
}
input:focus-visible, select:focus-visible, textarea:focus-visible {
  border-color: var(--color-primary);
}
input:disabled, select:disabled, textarea:disabled {
  background: var(--color-bg);
  color: var(--color-text-muted);
  cursor: not-allowed;
}
```

Esto es una red de seguridad: cualquier página que NUNCA se migre a `FormField` igual
hereda un look consistente. Verificar después de este cambio que ninguna página quede
visualmente rota (screenshot rápido de 3-4 páginas al azar) — es un cambio global de
alto alcance, aunque de bajo riesgo porque son solo valores por defecto que las reglas
`scoped` de cada página ya sobrescriben si son más específicas.

### Fase 4.2 — Migrar botones, página por página

Lista completa de los 29 archivos con `.btn-primary` local (generada con
`grep -rl '\.btn-primary\s*{' src/pages src/components`):

**Alto tráfico (migrar primero)**:
- [ ] `src/pages/login/LoginPage.vue`
- [ ] `src/pages/login/RegisterPage.vue`
- [ ] `src/pages/dashboard/DashboardHome.vue`
- [ ] `src/pages/dashboard/ConferencesListPage.vue`
- [ ] `src/pages/dashboard/NewConferencePage.vue`
- [ ] `src/pages/dashboard/EditConferencePage.vue`
- [ ] `src/pages/public/PublicEventDetailPage.vue`
- [ ] `src/pages/conference/TicketPage.vue`

**Dashboard / configuración (segunda tanda)**:
- [ ] `src/pages/dashboard/TicketManagementPage.vue`
- [ ] `src/pages/dashboard/SurveyManagePage.vue`
- [ ] `src/pages/dashboard/AdminAiSettingsPage.vue`
- [ ] `src/pages/dashboard/AdminDeviceAccessPage.vue`
- [ ] `src/pages/dashboard/AdminEgressPolicyPage.vue`
- [ ] `src/pages/dashboard/EventTypesAdminPage.vue`
- [ ] `src/pages/dashboard/RolesAdminPage.vue`
- [ ] `src/pages/dashboard/CertificateSettingsPage.vue`
- [ ] `src/pages/dashboard/CertificateEditorPage.vue`
- [ ] `src/pages/dashboard/PresentationManagePage.vue`
- [ ] `src/pages/dashboard/VenueMapEditorPage.vue`
- [ ] `src/pages/dashboard/SpeakerPanelPage.vue`
- [ ] `src/pages/dashboard/JoinConferencePage.vue`
- [ ] `src/pages/profile/ProfilePage.vue`

**Vistas del asistente / resto**:
- [ ] `src/pages/conference/PresentationPage.vue`
- [ ] `src/pages/conference/SurveyPage.vue`
- [ ] `src/pages/conference/IdePage.vue`
- [ ] `src/pages/public/NotFoundPage.vue`
- [ ] `src/components/SessionExpiryModal.vue`
- [ ] `src/components/VenueMapCanvasEditor.vue`
- [ ] `src/components/moderator/WorkspaceFileEditor.vue`

**Por archivo, el procedimiento es**:
1. Abrir el archivo, buscar `.btn-primary` (y `.btn-outline`/`.btn-danger`/`.btn-ghost`
   si tiene) tanto en el `<style>` como en el template.
2. Reemplazar cada `button.btn-primary(...)` / `button.btn-outline(...)` etc. por
   `BaseButton(variant="..." ...)` (ver mapeo en 2.1). Mover los atributos existentes
   (`:disabled`, `@click`, `type`, etc.) tal cual al componente.
3. Borrar la regla CSS local `.btn-primary { ... }` (y las otras variantes migradas)
   del `<style scoped>` del archivo — si después de borrar queda una regla huérfana
   sin ningún selector que la use, también se borra.
4. Importar y registrar `BaseButton` (`import BaseButton from '@/components/ui/BaseButton.vue'`,
   agregarlo a `components: { ... }`).
5. Compilar (`npx vue-tsc --noEmit`) y verificar visualmente esa página en el
   navegador (ver sección 6) antes de pasar a la siguiente.
6. Commit por página o por grupo pequeño de páginas relacionadas — no un commit único
   gigante con las 29, para que un review o un revert sean manejables.

### Fase 4.3 — Migrar formularios a `FormField`

Empezar por las páginas de la lista "alto tráfico" de 4.2 que además tengan
formularios reales con varios campos (no un solo input suelto): `LoginPage.vue`,
`RegisterPage.vue`, `NewConferencePage.vue`, `EditConferencePage.vue`.

Por cada campo `label + input` (o `select`/`textarea`) suelto en el template:
1. Envolver en `FormField(label="..." :error="..." hint="...")`.
2. Pasar el slot con `template(#default="{ id, describedBy }")` y agregar `:id="id"`
   `:aria-describedby="describedBy"` al input real.
3. Si la página mostraba el mensaje de error en un `<p>` separado después del input,
   ese texto pasa a la prop `error` de `FormField` (y se borra el `<p>` viejo).
4. Igual que en 4.2: borrar el CSS local de label/hint/error que quede huérfano,
   compilar, verificar visualmente, commit.

**No es necesario migrar los 152 usos en una sola pasada** — esto puede (y probablemente
debe) hacerse en varios PRs a lo largo de varias sesiones. Priorizar formularios que el
usuario final llena seguido (login, registro, alta de evento) sobre paneles de admin de
uso esporádico.

### Fase 4.4 — Botones-enlace (`<a>`/`router-link` con pinta de botón)

Para los casos identificados en 2.1 (navegación, no acción — `router-link.btn-primary`,
`a.btn-outline`, etc.) que no pueden migrar a `BaseButton` porque no es un `<button>`:
crear en `global.css` (o en un nuevo `src/styles/link-buttons.css` importado desde
`main.ts`) un set chico de clases utilitarias compartidas —
`.link-btn`, `.link-btn-primary`, `.link-btn-secondary` — con la MISMA geometría que
`BaseButton` (mismos paddings/font-size/radius por tamaño), y migrar esas páginas a
usar esas clases compartidas en vez de sus `.btn-primary`/`.btn-outline` locales.

## 5. Riesgos y cosas a verificar con cuidado

- **`disabled` en botones de submit**: `BaseButton` ya deshabilita automáticamente
  cuando `loading=true`; si una página vieja combinaba manualmente
  `:disabled="saving || !valid"`, ese binding se mantiene igual (pasa directo a la
  prop `disabled` de `BaseButton`) — no depender solo de `loading` si la condición de
  disabled es más rica que "está guardando".
- **Iconos/emoji dentro del botón** (`✨`, `🔒`, etc.): van dentro del slot por defecto
  de `BaseButton` tal cual, sin cambios.
- **Estilos de hover/active muy específicos** que alguna página tuviera además del
  color base (por ejemplo, una transición o sombra particular) se pierden al migrar a
  `BaseButton` — si una página realmente necesita una variante visual distinta, es
  mejor señal para agregar una nueva prop/variant a `BaseButton` (extender el
  componente compartido) que reintroducir CSS local divergente.
- **Tests existentes**: correr `npm run test` después de cada tanda de páginas
  migradas — hay tests de componentes/API que no deberían verse afectados por esto,
  pero si alguno referencia selectores CSS específicos (`.btn-primary`) en vez de rol/
  texto, puede requerir actualización.

## 6. Cómo verificar cada página migrada

1. `npx vue-tsc --noEmit` — sin errores de tipos.
2. `npm run build` — build limpio.
3. Con el sitio corriendo (`preview_start` + Browser pane, o `npm run dev` local):
   abrir la página migrada en 375px (móvil) y 1280px+ (escritorio), confirmar que el
   botón/campo se ve igual que en `ModerationToolsPage.vue` (la referencia ya migrada)
   y que el flujo (click, submit, mostrar error) sigue funcionando igual que antes.
4. Si la página tenía un test unitario relacionado a esos botones/campos, correrlo.

## 7. Criterio de "terminado"

- Cero archivos con `grep -rl '\.btn-primary\s*{' src/pages src/components` (hoy: 29).
- Al menos los formularios de alto tráfico (login, registro, alta de evento, edición de
  evento) usan `FormField` para cada campo.
- `global.css` tiene el baseline de `input`/`select`/`textarea` de la fase 4.1.
- Ninguna regresión visual ni de accesibilidad (foco, `aria-describedby`) en las
  páginas migradas, confirmada con el checklist de la sección 6.
