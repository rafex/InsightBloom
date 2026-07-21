# Tasks: soporte aditivo de Slidev

## Estado

- Estado: `in_progress`
- Spec: `spec-native/specs/slidev-presentations/SPEC.md`
- Owner funcional: producto/plataforma
- Owners técnicos: `insightbloom-presentations`, `frontend/web`, CI/CD
- Regla de compatibilidad: Marp continúa siendo el proveedor predeterminado y
  no se migran artefactos existentes.

## Fase 0 — Spike y decisiones bloqueantes

### SLIDEV-001 — Confirmar versión y modelo de ejecución

- Estado: `in_progress`
- Owner: presentations
- Dependencias: ninguna
- Verificar Node soportado, versión de `@slidev/cli`, Chromium/Playwright,
  tamaño de imagen y tiempos de `build`/`export` con un fixture mínimo.
- Decidir si el exportador puede vivir en el servicio HTTP o si requiere un
  worker con cola y límites separados.
- Criterio de cierre: benchmark reproducible y decisión registrada en la spec.

### SLIDEV-002 — Definir el formato de carga del MVP

- Estado: `done`
- Owner: producto + presentations
- Dependencias: SLIDEV-001
- Especificar entrada `slides.md`, assets locales, temas allowlisted, cuota,
  tamaño máximo y archivos rechazados.
- Confirmar si se acepta un ZIP de proyecto o una carga directa de Markdown más
  assets; mantener un único contrato inicial reduce ambigüedad.
- Criterio de cierre: ejemplos válidos e inválidos y mensajes de error definidos.

### SLIDEV-003 — Validar seguridad de Slidev

- Estado: `in_progress`
- Owner: seguridad + presentations
- Dependencias: SLIDEV-002
- Probar traversal, symlinks, `package.json`, `vite.config`, componentes Vue,
  scripts, imports remotos, HTML y consumo de recursos.
- Decidir la frontera entre Markdown declarativo permitido y código que exige
  sandbox dedicado.
- Criterio de cierre: matriz de allowlist/denylist aprobada y pruebas de
  rechazo automatizadas.

## Fase 1 — Modelo y contrato compatible

### SLIDEV-004 — Persistir proveedor en la presentación activa

- Estado: `done`
- Owner: users/backend
- Dependencias: SLIDEV-002
- Añadir `presentationProvider` al manifiesto/status de la presentación, con
  valores `MARP` y `SLIDEV`. Una presentación sin ese dato se interpreta como
  `MARP`.
- Recibir el proveedor como parte explícita del upload; no convertirlo en una
  configuración general del evento.
- Mantener el contrato existente de `presentationSourceUrl` y no borrar datos
  de Marp hasta que una nueva carga termine correctamente.
- Criterio de cierre: migración, serialización, tests y compatibilidad de
  conferencias existentes.

### SLIDEV-005 — Diseñar manifest y layout de artefactos

- Estado: `in_progress`
- Owner: presentations
- Dependencias: SLIDEV-001, SLIDEV-002
- Definir `manifest.json`, hashes, versiones, estado activo y directorios
  temporales/activos.
- Diseñar reemplazo atómico y rollback del artefacto anterior.
- Criterio de cierre: contrato documentado y test de carga fallida que preserva
  la versión previa.

### SLIDEV-006 — Abstraer proveedores sin romper Marp

- Estado: `in_progress`
- Owner: presentations
- Dependencias: SLIDEV-005
- Extraer una interfaz/adaptador común para validar, construir, servir,
  previsualizar y exportar.
- Encapsular el flujo actual en `MarpProvider`; incorporar `SlidevProvider` sin
  cambiar rutas públicas innecesariamente.
- Criterio de cierre: tests Marp pasan contra el adaptador y el status informa
  proveedor/formats.

## Fase 2 — Integración Slidev en backend

### SLIDEV-007 — Implementar validación y build Slidev

- Estado: `in_progress`
- Owner: presentations
- Dependencias: SLIDEV-003, SLIDEV-006
- Añadir dependencias fijadas y ejecutar build con `--base` por conferencia.
- Construir artefacto de audiencia sin notas y artefacto de moderador con las
  capacidades autorizadas.
- Aplicar cuotas, timeouts, concurrencia, usuario no root, directorio temporal
  aislado y cleanup.
- Criterio de cierre: fixture mínimo listo y errores normalizados.

### SLIDEV-008 — Implementar exportaciones y caché

- Estado: `in_progress`
- Owner: presentations
- Dependencias: SLIDEV-001, SLIDEV-007
- Implementar PDF, PPTX y PNG con cache por hash de fuente/configuración.
- Exponer Markdown y ZIP fuente sólo al rol autorizado.
- Registrar descargas y fallos sin almacenar contenido sensible en logs.
- Criterio de cierre: cada formato se descarga y una segunda solicitud usa el
  artefacto cacheado.

### SLIDEV-009 — Implementar preview aislado

- Estado: `done`
- Owner: presentations
- Dependencias: SLIDEV-007
- Generar preview limitado a las primeras diapositivas sin servir el SPA
  completo a tokens sin acceso.
- Verificar visualmente que no contiene speaker notes, URLs privadas ni tokens.
- Criterio de cierre: pruebas de acceso público y de inspección de artefacto.

### SLIDEV-010 — Integrar navegación en vivo

- Estado: `in_progress`
- Owner: presentations
- Dependencias: SLIDEV-006, SLIDEV-007
- Implementar un adaptador para traducir el estado de navegación Slidev al
  evento común del WebSocket existente.
- Probar navegación, reconexión, refresh y dos sesiones de audiencia.
- No habilitar el remote control nativo de Slidev como bypass de autorización.
- Criterio de cierre: moderador y audiencia permanecen en la misma slide con
  ambos proveedores.

## Fase 3 — Frontend y experiencia de gestión

### SLIDEV-011 — Selector de proveedor en la carga

- Estado: `done`
- Owner: frontend/web
- Dependencias: SLIDEV-004, SLIDEV-006
- Añadir selector obligatorio Marp/Slidev junto al input del ZIP, ayuda del
  formato esperado, estado de build y errores accionables.
- Enviar el campo `presentationProvider` junto con el multipart del ZIP.
- Mantener el flujo y textos de Marp para usuarios actuales.
- Criterio de cierre: se puede cargar un ZIP Slidev desde el panel y la ruta
  Marp existente continúa funcionando.

### SLIDEV-012 — Visor, preview y descargas

- Estado: `in_progress`
- Owner: frontend/web
- Dependencias: SLIDEV-008, SLIDEV-009, SLIDEV-010
- Mantener una ruta pública única `/presentation` y mostrar controles según
  proveedor y permisos.
- Añadir acciones de exportación Slidev sin retirar PDF de Marp.
- Criterio de cierre: pruebas Vitest para URLs, permisos, status, preview y
  descargas.

## Fase 4 — CI/CD, operación y rollout

### SLIDEV-013 — CI del servicio de presentaciones

- Estado: `todo`
- Owner: CI
- Dependencias: SLIDEV-007, SLIDEV-008
- Añadir install reproducible, unit tests, fixture build y smoke export al
  workflow de presentations.
- Verificar cache npm, tamaño de artefactos y estabilidad de Chromium.
- Criterio de cierre: PR bloqueado ante regresión de Marp o fallo de Slidev.

### SLIDEV-014 — Imagen y valores de runtime

- Estado: `in_progress`
- Owner: platform
- Dependencias: SLIDEV-001, SLIDEV-007
- Ajustar Dockerfile, memoria temporal, límites y health checks; no modificar
  Helm directamente en este repositorio.
- Si se requieren recursos o variables nuevas, preparar el cambio equivalente
  en `InsightBloom-gitops` como trabajo separado.
- Criterio de cierre: imagen GHCR construye y smoke test local pasa.

### SLIDEV-015 — Rollout gradual y documentación

- Estado: `todo`
- Owner: plataforma + soporte
- Dependencias: SLIDEV-013, SLIDEV-014
- Mantener Marp como default y habilitar Slidev inicialmente para eventos de
  prueba o una feature flag controlada.
- Documentar diagnóstico, limpieza de temporales, exportaciones, límites y
  rollback a Marp.
- Validar en K3s comparando el tag desplegado, el pod y la reconciliación de
  FluxCD; no aplicar cambios manuales con kubectl.
- Criterio de cierre: checklist operativo ejecutado y sin regresiones de Marp.

## Dependencias y orden recomendado

```text
SLIDEV-001 → SLIDEV-002 → SLIDEV-003
                    └────→ SLIDEV-004 → SLIDEV-005 → SLIDEV-006
                                                   ├→ SLIDEV-007 → SLIDEV-008
                                                   │                 ├→ SLIDEV-009
                                                   │                 └→ SLIDEV-010
                                                   └→ SLIDEV-011 → SLIDEV-012
SLIDEV-007 + SLIDEV-008 → SLIDEV-013 → SLIDEV-014 → SLIDEV-015
```

## Definition of Done

- [ ] Marp sigue siendo el default y todos los tests de regresión pasan.
- [ ] Slidev puede cargarse, construirse, visualizarse y exportarse bajo los
  límites del MVP.
- [ ] No se exponen speaker notes, tokens, secretos ni artefactos privados.
- [ ] La navegación en vivo funciona con Marp y Slidev.
- [ ] El paquete de presentación no ejecuta dependencias arbitrarias del
  usuario.
- [ ] CI construye y prueba el servicio de presentaciones.
- [ ] La documentación distingue código/CI de despliegue GitOps.
- [ ] Existe rollback claro a Marp y la validación de K3s se hizo después de
  FluxCD, no mediante cambios manuales.
