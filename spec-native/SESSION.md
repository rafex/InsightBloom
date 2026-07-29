# SESSION — Design System Migration

> **Iniciativa**: Migración a design system unificado
> **Documento de referencia**: [`frontend/web/docs/DESIGN_SYSTEM_MIGRATION.md`](../frontend/web/docs/DESIGN_SYSTEM_MIGRATION.md)
> **Branch**: `agent/ui-ux-audit`
> **Creado**: 2026-07-28
> **Último checkpoint**: 2026-07-28 20:36 (aprox)

## Estado

| State    | Bloqueante | Próximo paso |
|----------|------------|-------------|
| `in_progress` | No | Continuar pendientes de validación responsive y accesibilidad; navegación, breadcrumbs, configuración y acciones administrativas ya consolidadas |

## Resumen de avance

### Done

| Fase | Ámbito | Archivos migrados | Cambios clave |
|------|--------|-------------------|---------------|
| **4.1** | `global.css` | 1 | Baseline `input`/`select`/`textarea` con tokens `--border-input`, `--radius-md`, `--color-surface`, `--color-text`, focus-visible, disabled |
| **4.2** | Alto tráfico (manual) | 3/8 | `LoginPage.vue` — 2 botones (`BaseButton size="lg"`, `variant="secondary"`) |
| | | | `RegisterPage.vue` — 3 botones (`BaseButton size="lg"` ×2, `variant="ghost"`). Conservado `.btn-primary-link` (router-link para Fase 4.4) |
| | | | `TicketPage.vue` — 3 botones (`BaseButton` ×2, `variant="secondary"`). `:loading` reemplaza spans condicionales. CSS huérfano eliminado |
| **4.2** | Dashboard/config | 11/12 | Páginas administrativas y de configuración migradas a `BaseButton`; queda `SpeakerPanelPage.vue`, que requiere recuperar primero el stash `pre-design-system-migration` |
| | | | `SurveyManagePage.vue` — 3 botones (`variant="primary"`, `size="sm"`, `variant="secondary"`), import y registro |

### NO migrar aún — solo router-links, se atienden en Fase 4.4

- `DashboardHome.vue` — todos los `.btn-primary`/`.btn-outline` son `<router-link>`, cero `<button>`
- `PublicEventDetailPage.vue` — `.btn-primary` son `<router-link>`; `.btn-outline` es `<span>`

### Carpeta stash

Hay un stash `pre-design-system-migration` con el archivo `SpeakerPanelPage.vue` que estaba sucio antes de empezar. Recuperar antes de trabajar ese archivo:
```bash
git stash list  # debería mostrar "pre-design-system-migration"
```

## Pendiente

La iteración UI/UX 2026-07-28 dejó implementados UX-TASK-001 a UX-TASK-019. La validación local comprobó overflow y focus/labels en superficies públicas; siguen pendientes la prueba autenticada completa del dashboard, recorridos funcionales con backend y verificación post-despliegue.

### Fase 4.2 — Alto tráfico

- [x] `src/pages/dashboard/ConferencesListPage.vue` — acción primaria `Abrir evento`, menús `Gestionar`/`Más`, eliminación separada y sin CSS `.btn-trash` huérfano
- [x] `src/pages/dashboard/NewConferencePage.vue` — botones reales migrados a `BaseButton`; router-links con `.btn-outline` se dejan para Fase 4.4
- [x] `src/pages/dashboard/EditConferencePage.vue` — botones reales migrados a `BaseButton`; router-link `.btn-outline` se deja para Fase 4.4

### Fase 4.2 — Dashboard/Config (12 archivos, 2 ya hechos parcialmente)

Los 2 hechos por el agente (`SurveyManagePage`, `TicketManagementPage`) pueden requerir limpieza de CSS residual (siguen apareciendo en `grep -rl '\.btn-primary\s*{'`).

- [x] `src/pages/dashboard/AdminAiSettingsPage.vue`
- [x] `src/pages/dashboard/AdminDeviceAccessPage.vue`
- [x] `src/pages/dashboard/AdminEgressPolicyPage.vue`
- [x] `src/pages/dashboard/EventTypesAdminPage.vue`
- [x] `src/pages/dashboard/RolesAdminPage.vue`
- [x] `src/pages/dashboard/CertificateSettingsPage.vue`
- [x] `src/pages/dashboard/CertificateEditorPage.vue`
- [x] `src/pages/dashboard/PresentationManagePage.vue`
- [x] `src/pages/dashboard/VenueMapEditorPage.vue`
- [ ] `src/pages/dashboard/SpeakerPanelPage.vue` ← **recuperar de stash primero**
- [x] `src/pages/dashboard/JoinConferencePage.vue`
- [x] `src/pages/profile/ProfilePage.vue`

### Fase 4.2 — Resto/Asistente (7 archivos)

- [ ] `src/pages/conference/PresentationPage.vue`
- [ ] `src/pages/conference/SurveyPage.vue`
- [ ] `src/pages/conference/IdePage.vue`
- [ ] `src/pages/public/NotFoundPage.vue`
- [ ] `src/components/SessionExpiryModal.vue`
- [ ] `src/components/VenueMapCanvasEditor.vue`
- [ ] `src/components/moderator/WorkspaceFileEditor.vue`

### Fase 4.3 — FormField migration

Priorizar las 4 páginas de alto tráfico con formularios reales:

- [ ] `src/pages/login/LoginPage.vue` — 2 campos (email, password)
- [ ] `src/pages/login/RegisterPage.vue` — 5+ campos (username, email, phone, password, social links)
- [ ] `src/pages/dashboard/NewConferencePage.vue` — ~15 campos
- [ ] `src/pages/dashboard/EditConferencePage.vue` — ~12 campos

### Fase 4.4 — Botones-enlace (`.link-btn` classes)

- [ ] Crear clases `.link-btn`, `.link-btn-primary`, `.link-btn-secondary` en `global.css` o `src/styles/link-buttons.css`
- [ ] Migrar `router-link.btn-primary` / `router-link.btn-outline` en `DashboardHome`, `PublicEventDetailPage`, `ConferencesListPage`, `NewConferencePage`, `EditConferencePage`

## Estrategia recomendada al retomar

1. **Terminar los 3 archivos de alto tráfico** (ConferencesList, NewConference, EditConference) manualmente — ya están leídos, hacer los edits puntuales
2. **Escribir un script Python** (`/tmp/ds_migrate.py`) que procese los ~19 archivos restantes de Phase 4.2 en lote:
   - Lee cada `.vue`, busca patrones `<button class="btn-*">` en el template Pug
   - Convierte a `BaseButton(variant="..." ...)` con el mapeo de la sección 2.1
   - Agrega `import BaseButton` y registro en `components: { ... }`
   - Limpia CSS residual de las clases migradas
   - El script es solo lectura/análisis; los edits se hacen con la herramienta Edit
3. **Para Phase 4.3**, usar `FormField.vue` (slot `{ id, describedBy }`) en Login, Register, NewConference, EditConference
4. **Para Phase 4.4**, crear las clases `.link-btn-*` con misma geometría que `BaseButton` (paddings/font-size/radius de `.s-sm`/`.s-md`/`.s-lg`), luego migrar los `router-link`

## Referencias rápidas

### Mapeo de clases → BaseButton (del doc §2.1)

| Clase vieja | BaseButton |
|-------------|------------|
| `.btn-primary` | `variant="primary"` (default) |
| `.btn-outline` / `.btn-secondary` | `variant="secondary"` |
| `.btn-danger` / `.btn-delete` / `.btn-trash` / `.btn-revoke` | `variant="danger"` |
| `.btn-ghost` / `.btn-ghost-sm` | `variant="ghost"` |
| padding 4-8px o font < 0.85rem | `size="sm"` |
| padding 12px+ vertical | `size="lg"` |
| default | `size="md"` (no especificar) |

### Contrato FormField (del doc §2.2)

```pug
FormField(label="Email" :error="emailError" hint="Usá el mismo con el que te registraste" required)
  template(#default="{ id, describedBy }")
    input(:id="id" :aria-describedby="describedBy" v-model="email" type="email")
```

Props: `label` (required), `hint`, `error`, `required`. Slot recibe `{ id, describedBy }` — ambos deben ir al input real para a11y.

### Verificación por página (del doc §6)

1. `npx vue-tsc --noEmit` — sin errores
2. `npm run build` — build limpio
3. Visual en 375px y 1280px+ contra `ModerationToolsPage.vue`
4. `npm run test`

### Criterio de terminado (del doc §7)

```bash
grep -rl '\.btn-primary\s*{' src/pages src/components  # debe ser 0
```
