# SESSION.md

Estado activo de trabajo para continuidad multi-agente.

---

## Estado actual

- **Iniciativa activa**: migración SpecNative v0.7
- **Branch**: `feature/specnative-migration`
- **Último agente**: @opencode
- **Última acción**: Migración de estructura de documentación de `docs/` a `spec-native/`

---

## Contexto de la sesión

Migrando el proyecto InsightBloom de una estructura de documentación ad-hoc (`docs/`)
al estándar SpecNative Development v0.7 (`spec-native/`). El proyecto ha superado
la fase PoC y requiere una base documental escalable para desarrollo multi-agente.

## Próximos pasos

- [ ] Instalar MCP server en `.specnative/specnative_mcp.py`
- [ ] Derivar tareas desde SPEC.md actual
- [ ] Actualizar CI/CD pipelines en `pipelines/CI.md`
- [ ] Poblar `tasks/` con el plan de implementación activo
- [ ] Validar integridad con `specnative validate`

## Notas

- `docs/` se mantiene como referencia legacy con aviso de deprecación.
- `agents/` mantiene artefactos operativos (SECURITY.md, DIAGNOSE.md).
- Los documentos migrados conservan su contenido original; se actualizarán progresivamente.
