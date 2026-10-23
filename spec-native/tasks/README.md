# tasks/

Tareas ejecutables por iniciativa.

## Estructura

```
tasks/
├── README.md           ← este archivo
├── security-readiness/  ← preparación para autorregistro público
│   └── TASKS.md
├── beta-organizer-invitations/ ← acceso controlado de organizadores beta
│   └── TASKS.md
├── llm-credential-isolation/ ← aislamiento de claves LLM por organizador y evento
│   └── TASKS.md
├── ticket-device-binding/ ← credenciales criptográficas por boleto y check-in offline
│   └── TASKS.md
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
