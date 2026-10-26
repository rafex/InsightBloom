# TASKS.md

```toml
artifact_type = "task_file"
initiative = "llm-profile-inheritance"
spec_id = "SPEC-LPI"
owner = "Codex"
state = "todo"
```

## Tareas

### TASK-LLM-PROFILE-INHE-0001 - Implementar perfiles, herencia y delegación IA segura

> **Update 2026-10-26T15:24:58Z:** Implementación local completa; Gateway 18 tests, users Maven reactor, frontend 190 tests/typecheck/lint/UI governance/build y Helm lint/render aprobados. Staging real y activación del callback siguen pendientes; el chart mantiene LLM_USERS_RESOLUTION_URL vacío hasta desplegar users.

```toml
id = "TASK-LLM-PROFILE-INHE-0001"
title = "Implementar perfiles, herencia y delegación IA segura"
state = "in_progress"
priority = "p2"
owner = "Codex"
labels = []
dependencies = []
expected_files = []
close_criteria = "Perfiles cifrados, cuatro modos, resolución autorizada por solicitud, UI compartida y pruebas locales aprobadas; registrar gate staging."
validation = ["Gateway pytest", "Users Maven tests", "Frontend typecheck/lint/build/tests", "Helm lint y recorrido staging"]
```

Implementar SPEC-LPI en gateway, users y UI.

