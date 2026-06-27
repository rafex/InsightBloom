# tasks/

Tareas ejecutables por iniciativa.

## Estructura

```
tasks/
├── README.md           ← este archivo
└── <iniciativa>/
    └── TASKS.md         ← plan de tareas
```

## Cómo crear tareas

1. Tener una spec en `spec-native/specs/<iniciativa>/SPEC.md`.
2. Usar el prompt `plan_tasks(initiative)` del MCP.
3. O crear manualmente `TASKS.md` siguiendo el template.

## Template de tarea

```markdown
### TASK-XXXX: [Título]

**Estado:** todo | in_progress | blocked | done
**Owner:** [nombre]
**Dependencias:** [TASK-XXXX, ...]
**Archivos esperados:** `src/ruta/archivo.ext`
**Criterio de cierre:** [condición observable]
**Validación:** `[comando]`
```

## Estados

| Estado | Significado |
|--------|-------------|
| `todo` | No iniciada |
| `in_progress` | En implementación activa |
| `blocked` | Dependencia externa pendiente |
| `done` | Criterio de cierre cumplido y validado |
