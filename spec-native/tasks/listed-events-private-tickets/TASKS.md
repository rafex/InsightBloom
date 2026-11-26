# TASKS.md

```toml
artifact_type = "task_file"
initiative = "listed-events-private-tickets"
spec_id = "SPEC-LISTED-PRIVATE-TICKETS"
owner = "product owner"
state = "todo"
```

## Tareas

### TASK-LISTED-EVENTS-PR-0001 - Verificar el modo de boletos privados de punta a punta

```toml
id = "TASK-LISTED-EVENTS-PR-0001"
title = "Verificar el modo de boletos privados de punta a punta"
state = "todo"
priority = "p2"
owner = "product owner"
labels = []
dependencies = []
expected_files = []
close_criteria = "Los recorridos funcionan con un anfitrión y dos asistentes, incluidos token revocado, solicitud duplicada y último cupo."
validation = ["Recorrido web en staging", "Pruebas de emisión de users", "Typecheck y build frontend"]
```

Validar en staging cartelera, ficha, enlace y QR privados, OTP, emisión automática, aforo y entrega tras firma.
