# PLANNING.md

Cómo planificar iniciativas en InsightBloom.

## Flujo

1. Leer [`PRODUCT.md`](../PRODUCT.md) — confirmar que la iniciativa resuelve un problema real.
2. Revisar [`ROADMAP.md`](../ROADMAP.md) — validar prioridad y coherencia temporal.
3. Leer [`DECISIONS.md`](../DECISIONS.md) — respetar tradeoffs persistentes.
4. Crear spec en [`specs/<iniciativa>/SPEC.md`](../specs/) usando el template.
5. Derivar tareas en `tasks/<iniciativa>/TASKS.md`.
6. Asignar owner, dependencias y criterios de cierre por tarea.
7. Actualizar [`TRACEABILITY.md`](../TRACEABILITY.md).

## Template de spec

```markdown
# SPEC: [Nombre]

## Initiative
[nombre-en-kebab-case]

## Status
draft

## Summary
[2-3 oraciones]

## Problem
[Qué fricción dispara esta iniciativa]

## Objective
[Qué debe ser verdad al completar]

## Scope
### Includes
- [ítem]
### Excludes
- [ítem]

## Functional Requirements
- FR-001: [requisito]

## Non-functional Requirements
- NFR-001: [requisito]

## Acceptance Criteria
### Scenario 1
- **Given** [precondición]
- **When** [acción]
- **Then** [resultado esperado]

## Dependencies
- [dependencia]

## Risks
- [riesgo] — mitigación: [plan]

## Execution Plan
→ `tasks/<iniciativa>/TASKS.md`

## Validation Plan
[Cómo se valida]
```

## Criterio de inicio

Una iniciativa está lista para iniciar cuando:
- [ ] Spec aprobada (`state: active`).
- [ ] Tareas derivadas en `TASKS.md`.
- [ ] Dependencias externas resueltas o plan de mitigación definido.
- [ ] Owner asignado.
