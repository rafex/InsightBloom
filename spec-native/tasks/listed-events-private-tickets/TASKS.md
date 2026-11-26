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

### TASK-LISTED-EVENTS-PR-0002 - Corregir el layout responsive del checkout de boletos privados

```toml
id = "TASK-LISTED-EVENTS-PR-0002"
title = "Corregir el layout responsive del checkout de boletos privados"
state = "done"
priority = "p2"
owner = "Codex"
labels = []
dependencies = []
expected_files = []
close_criteria = "En desktop las tarjetas no se solapan ni desbordan; resumen, total e instrucciones mantienen ancho legible; las acciones de iniciar sesión y crear cuenta se muestran completas y consistentes. En móvil los paneles se apilan, sin scroll horizontal y con botones de ancho completo."
validation = ["Revisar visualmente layout en 1440px, 1024px, 768px y 390px", "Ejecutar typecheck/build del frontend si el entorno lo permite"]
completion_evidence = ["Corregido PublicTicketCheckoutPage.vue: las columnas tienen mínimos explícitos cero, el checkout amplía y limita mejor su ancho, el footer apila estado y acciones, y los paneles se apilan desde 860px. `npm run typecheck` y `npm run build` terminaron con código 0; `git diff --check` limpio."]
```

La tarjeta de resumen invade el panel lateral y el footer del checkout comprime el texto y las acciones en anchos intermedios. Ajustar el grid para respetar el espacio disponible, agrupar/ordenar las acciones y apilar los paneles cuando no quepan cómodamente.
