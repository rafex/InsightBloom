# SESSION.md

Estado activo de trabajo para continuidad multi-agente.

---

## Estado actual

- **Iniciativa activa**: `slidev-presentations`
- **Branch**: `main`
- **Último agente**: Codex
- **Última acción**: Integración inicial de Marp/Slidev en carga, build, visor,
  modo presentador y navegación en vivo.

---

## Contexto de la sesión

InsightBloom conserva Marp como motor compatible y añade Slidev como proveedor
seleccionable por carga. El MVP acepta ZIP controlados, genera una SPA Slidev
con base por conferencia, la protege con el acceso existente y traduce sus
rutas de navegación al WebSocket de presentaciones. El CD continúa fuera de
este repositorio, en `InsightBloom-gitops`, reconciliado por FluxCD.

## Próximos pasos

- [ ] Completar exportación PPTX y caché por hash de Slidev
- [ ] Añadir pruebas automatizadas de ZIP malicioso, regresión Marp y WebSocket
- [ ] Validar el Docker build del servicio cuando Podman esté disponible
- [ ] Reconciliar el tag de imagen en `InsightBloom-gitops` mediante FluxCD
- [ ] Entregar a los agentes de presentaciones el formato de ZIP definido en
  `workflows/SLIDEV-PACKAGING.md`

## Notas

- `docs/` se mantiene como referencia legacy con aviso de deprecación.
- `agents/` mantiene artefactos operativos (SECURITY.md, DIAGNOSE.md).
- Los documentos migrados conservan su contenido original; se actualizarán progresivamente.
