# AGENTS.md

Eres un agente operando en un repositorio SpecNative — InsightBloom.

## Qué es SpecNative

SpecNative codifica el contexto del proyecto en `spec-native/` para que los
agentes planifiquen e implementen sin reconstruir contexto desde el historial
de conversación. El repositorio es el contexto.

## Qué es InsightBloom

Plataforma que convierte mensajes de chat en nubes de palabras interactivas
para conferencias en vivo. Backend Java 25 (Ether 9.5.5, 6 microservicios),
frontend Vue 3 + D3.js + Leaflet, chat Python/FastAPI + WebSocket.

## Dónde está todo

Todo el contexto del proyecto vive en `spec-native/`.
Lee `spec-native/README.md` para el índice completo.

```
spec-native/
├── PRODUCT.md        ← qué problema, para quién, por qué
├── ARCHITECTURE.md   ← estructura del sistema (468 líneas)
├── STACK.md          ← tecnologías y restricciones
├── CONVENTIONS.md    ← reglas de código y naming
├── COMMANDS.md       ← comandos del proyecto
├── DECISIONS.md      ← 10 decisiones persistentes
├── ROADMAP.md        ← prioridades de mediano plazo
├── ROLES.md          ← roles de usuario y matriz de permisos
├── TRACEABILITY.md   ← vínculos entre artefactos
├── SESSION.md        ← estado activo de trabajo
├── specs/            ← especificaciones por iniciativa
├── tasks/            ← tareas ejecutables por iniciativa
├── workflows/        ← procedimientos operativos
└── pipelines/        ← contexto de CI/CD
```

El repositorio usa SpecNative Development v0.9. Además:
- `agents/` — artefactos operativos (SECURITY.md, DIAGNOSE.md)
- `docs/` — legacy (deprecado, migrado a spec-native/)
- `container/` — Docker Compose + Dockerfiles
- `infra/` — Helm charts para K3s
- `.specnative/` — infraestructura del framework (MCP server)

## Si vienes de otro agente

Antes de empezar, verifica si hay sesión activa:

```
Vía MCP:   resume()
Manual:    lee spec-native/SESSION.md
```

Si `SESSION.md` tiene `state = "idle"`, lee `spec-native/ROADMAP.md`.

## Flujo de trabajo

1. Si hay sesión activa: `resume()` o leer `SESSION.md`.
2. Si es nueva iniciativa: `context_snapshot()` → `start_initiative()`.
3. Implementar siguiendo `spec-native/workflows/IMPLEMENTATION.md`.
4. Actualizar tareas: `update_task(initiative, task_id, state)`.
5. Registrar decisiones: `log_decision(title, ctx, decision, cons)`.
6. Al pausar: `checkpoint(initiative, task, intent, next_steps)`.
7. Al cerrar: `close_initiative(initiative)`.

## Reglas de contexto

- Los archivos en MAYÚSCULAS son contexto para agentes.
- Los `README.md` enrutan; no reemplazan el contexto.
- Leer el mínimo contexto suficiente para la tarea.
- Actualizar el documento fuente de verdad, no un resumen paralelo.
- No duplicar información entre documentos.

## Separación semántica

| Si el dato... | Va en... |
|---------------|----------|
| Desaparece al cerrar la iniciativa | `specs/<iniciativa>/SPEC.md` |
| Debe respetarse en la próxima iniciativa | `DECISIONS.md` |
| Explica el producto | `PRODUCT.md` |
| Orienta prioridad temporal | `ROADMAP.md` |
| Describe la estructura del sistema | `ARCHITECTURE.md` |
| Define gates automatizados | `pipelines/CI.md` |
| Describe cómo el código llega a producción | `pipelines/CD.md` |

## Usando el MCP de SpecNative

Si el servidor MCP está configurado, dispone de herramientas tipadas
para `status()`, `validate()`, `resume()`, `checkpoint()`, `update_task()`,
`log_decision()`, `health_check()`, `start_initiative()`, `close_initiative()`.

## Estados obligatorios

- Specs: `draft` · `active` · `blocked` · `done` · `superseded`
- Tareas: `todo` · `in_progress` · `blocked` · `done`
- Decisiones: `proposed` · `accepted` · `deprecated` · `replaced`
- SESSION: `idle` · `in_progress` · `blocked` · `waiting_handoff`
