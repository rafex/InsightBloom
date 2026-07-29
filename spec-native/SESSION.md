# SESSION — Design System Migration

> **Iniciativa**: Migración a design system unificado
> **Documento de referencia**: [`frontend/web/docs/DESIGN_SYSTEM_MIGRATION.md`](../frontend/web/docs/DESIGN_SYSTEM_MIGRATION.md)
> **Branch**: `main`
> **Creado**: 2026-07-28
> **Último checkpoint**: 2026-07-29 (estados de carga canónicos)

## Estado

| State    | Bloqueante | Próximo paso |
|----------|------------|-------------|
| `in_progress` | No | Continuar migración de superficies elegibles y validar recorridos autenticados responsive por rol |

## Resumen de avance

### Done

| Fase | Ámbito | Archivos migrados | Cambios clave |
|------|--------|-------------------|---------------|
| **4.1** | `global.css` | 1 | Baseline `input`/`select`/`textarea` con tokens `--border-input`, `--radius-md`, `--color-surface`, `--color-text`, focus-visible, disabled |
| **4.2** | Alto tráfico (manual) | 3/8 | `LoginPage.vue` — 2 botones (`BaseButton size="lg"`, `variant="secondary"`) |
| | | | `RegisterPage.vue` — 3 botones (`BaseButton size="lg"` ×2, `variant="ghost"`). Conservado `.btn-primary-link` (router-link para Fase 4.4) |
| | | | `TicketPage.vue` — 3 botones (`BaseButton` ×2, `variant="secondary"`). `:loading` reemplaza spans condicionales. CSS huérfano eliminado |
| **4.2** | Dashboard/config | 12/12 | Páginas administrativas y de configuración migradas a `BaseButton` y `link-btn-*` |
| | | | `SurveyManagePage.vue` — 3 botones (`variant="primary"`, `size="sm"`, `variant="secondary"`), import y registro |
| **Fundación** | Tipografía y gobierno | Completado | `@font-face` local para Assistant, tokens semánticos ampliados, catálogo de componentes y gate `lint:ui-governance` en CI |
| **Gobierno visual** | Inventario de scoped y excepciones de color | Completado | El gate clasifica 79 estilos scoped, fija las 80 excepciones hex en 6 superficies y ya no permite redefiniciones legacy de selectores canónicos |
| **Gobierno de componentes** | Acciones administrativas restantes | Completado | `ConferenceConfigPage` usa `BaseButton` para eliminar/recrear sandboxes y quitar roles; `SurveyManagePage` usa variantes canónicas para editar/eliminar preguntas y confirmar/purgar respuestas |
| **Gobierno de componentes** | Estado de guardado y tablas | En progreso | `SaveState.vue` centraliza `Sin cambios`/`Cambios pendientes`/`Guardando`/`Guardado`; `.table-scroll` tiene baseline global y las tablas administrativas conservan tarjetas responsive |
| **Gobierno de componentes** | Estados semánticos | En progreso | `StatusBadge.vue` centraliza estados de usuarios, roles y detalle de usuario con tonos semánticos reutilizables |
| **Gobierno de componentes** | Estados vacíos | En progreso | `EmptyState.vue` centraliza mensajes y acciones de ausencia de datos en usuarios, roles, tipos de evento, eventos, dispositivos y reservas |
| **Gobierno visual** | Utilidades transversales | En progreso | `.loading-text` y el color base de `.field-hint` viven en `global.css`; las pantallas conservan solo sus márgenes y tamaños específicos |
| **Gobierno de componentes** | Estados de carga | En progreso | `LoadingState.vue` unifica la espera de Diagramas, Notas colaborativas, Videollamada y Pizarra con semántica `status` y `aria-live` |
| **Gobierno visual** | Shell y overlays | En progreso | Header, menús, modales, confirmaciones y overlays administrativos consumen tokens de superficie, sombra y scrim |
| **Gobierno visual** | Iconografía administrativa | En progreso | `UiIcon.vue` aporta SVGs lineales para métricas del Panel y herramientas del evento; elimina emojis de indicadores operativos sin modificar contenido editorial |
| **Recorridos por rol** | Moderador event-scoped | En progreso | El panel detecta eventos asignados aunque el rol global sea `ATTENDEE`; se mantienen ocultas acciones de propietario |
| **UX evento** | QR, herramientas y cabecera | Completado | El QR del listado apunta siempre a `/c/{friendlyId}/ticket`; se retiraron botones internos de QR; se agregó Cronograma y se ordenaron las herramientas; la cabecera inicia compacta y se mantiene así al cambiar de ruta |

### NO migrar aún — solo router-links, se atienden en Fase 4.4

- `DashboardHome.vue` — todos los `.btn-primary`/`.btn-outline` son `<router-link>`, cero `<button>`
- `PublicEventDetailPage.vue` — `.btn-primary` son `<router-link>`; `.btn-outline` es `<span>`

### Carpeta stash

El stash `pre-design-system-migration` conserva un estado histórico de `SpeakerPanelPage.vue`; no
es necesario recuperarlo para la implementación vigente.
```bash
git stash list  # debería mostrar "pre-design-system-migration"
```

## Pendiente

La iteración UI/UX 2026-07-28 dejó implementados UX-TASK-001 a UX-TASK-019. La validación local comprobó overflow y focus/labels en superficies públicas; siguen pendientes la prueba autenticada completa del dashboard, recorridos funcionales con backend y verificación post-despliegue. Las tandas del 2026-07-29 añadieron el estado de guardado canónico, el baseline global de tablas, tokens de overlays, detección de asignaciones event-scoped para moderadores e iconografía SVG reutilizable para el Panel y las herramientas del evento.

La migración de colores continúa: el gate mide 80 literales hex locales (baseline histórico
1,278), todos fijados en excepciones documentadas de mapas, ilustraciones y certificados. Las nuevas
pantallas deben usar tokens y componentes canónicos; el gate ya no permite redefiniciones legacy.

### Fase 4.2 — Alto tráfico

- [x] `src/pages/dashboard/ConferencesListPage.vue` — acción primaria `Abrir evento`, menús `Gestionar`/`Más`, eliminación separada y sin CSS `.btn-trash` huérfano
- [x] `src/pages/dashboard/NewConferencePage.vue` — botones reales migrados a `BaseButton`; router-links con `.btn-outline` se dejan para Fase 4.4
- [x] `src/pages/dashboard/EditConferencePage.vue` — botones reales migrados a `BaseButton`; router-link `.btn-outline` se deja para Fase 4.4

### Fase 4.2 — Dashboard/Config (12 archivos, 2 ya hechos parcialmente)

Las acciones comunes están migradas; se conservan tabs, flechas e iconos compactos como controles especializados.

- [x] `src/pages/dashboard/AdminAiSettingsPage.vue`
- [x] `src/pages/dashboard/AdminDeviceAccessPage.vue`
- [x] `src/pages/dashboard/AdminEgressPolicyPage.vue`
- [x] `src/pages/dashboard/EventTypesAdminPage.vue`
- [x] `src/pages/dashboard/RolesAdminPage.vue`
- [x] `src/pages/dashboard/CertificateSettingsPage.vue`
- [x] `src/pages/dashboard/CertificateEditorPage.vue`
- [x] `src/pages/dashboard/PresentationManagePage.vue`
- [x] `src/pages/dashboard/VenueMapEditorPage.vue`
- [x] `src/pages/dashboard/SpeakerPanelPage.vue`
- [x] `src/pages/dashboard/JoinConferencePage.vue`
- [x] `src/pages/profile/ProfilePage.vue`

### Fase 4.2 — Resto/Asistente (7 archivos)

- [x] `src/pages/conference/PresentationPage.vue`
- [x] `src/pages/conference/SurveyPage.vue`
- [x] `src/pages/conference/IdePage.vue`
- [x] `src/pages/public/NotFoundPage.vue`
- [x] `src/components/SessionExpiryModal.vue`
- [x] `src/components/VenueMapCanvasEditor.vue`
- [x] `src/components/moderator/WorkspaceFileEditor.vue`

La adopción de componentes base está completada en estas superficies; todavía queda migrar sus
colores locales restantes a tokens, excepto paletas propias de mapas, SVG, código y temas
editoriales.

### Fase 4.3 — FormField migration

Priorizar las 4 páginas de alto tráfico con formularios reales:

- [x] `src/pages/login/LoginPage.vue` — 2 campos (email, password)
- [x] `src/pages/login/RegisterPage.vue` — 5+ campos (username, email, phone, password, social links)
- [x] `src/pages/dashboard/NewConferencePage.vue` — ~15 campos
- [x] `src/pages/dashboard/EditConferencePage.vue` — ~12 campos

### Fase 4.4 — Botones-enlace (`.link-btn` classes)

- [x] Crear clases `.link-btn`, `.link-btn-primary`, `.link-btn-secondary` en `global.css`
- [x] Migrar los enlaces de `DashboardHome`, `PublicEventDetailPage`, `ConferencesListPage`,
  `NewConferencePage` y `EditConferencePage` a `link-btn-*`

### Bloque completado en este checkpoint

- [x] `LoginPage` — las acciones principales de contraseña y código por correo quedan
  centradas en la tarjeta tanto en escritorio como en móvil; se conserva `BaseButton` como
  único componente visual de acción.
- [x] Migrar enlaces y acciones de `DashboardHome`, `ConferenceConfigPage`, `SpeakerPanelPage`,
  `PresentationManagePage`, `SurveyPage` e `IdePage` a `link-btn-*`/`BaseButton`.
- [x] Registrar `BaseButton` explícitamente en `IdePage`.

## Estrategia recomendada al retomar

1. **Revisar los controles especializados restantes** de mapas, editores y herramientas embebidas, sin forzarlos dentro de `BaseButton` cuando su geometría sea parte de la interacción.
2. **Reducir colores locales** fuera de paletas propias de mapas, SVG, código y temas editoriales, actualizando el baseline solo con evidencia.
3. **Completar validación visual** en 375px y 1280px+ y recorridos autenticados con backend.
4. **Escribir un script Python** (`/tmp/ds_migrate.py`) que procese los ~19 archivos restantes de Phase 4.2 en lote:
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
