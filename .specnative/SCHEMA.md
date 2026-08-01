# SCHEMA.md

Contrato mínimo del framework SpecNative Development v0.9.

## Objetivo

Definir qué documentos son obligatorios, qué rol cumple cada uno y
qué estados o campos mínimos deben existir para reducir ambigüedad.

## Documentos obligatorios

- `AGENTS.md`
- `spec-native/README.md`
- `spec-native/PRODUCT.md`
- `spec-native/ARCHITECTURE.md`
- `spec-native/STACK.md`
- `spec-native/CONVENTIONS.md`
- `spec-native/COMMANDS.md`
- `spec-native/DECISIONS.md`
- `spec-native/decisions/` (índice y artefactos de decisión)
- `spec-native/ROADMAP.md`
- `spec-native/TRACEABILITY.md`
- `spec-native/SESSION.md`
- `spec-native/specs/` (al menos una spec)
- `spec-native/tasks/README.md`
- `spec-native/workflows/README.md`
- `spec-native/pipelines/README.md`

## Documentos opcionales

- `spec-native/tasks/<iniciativa>/TASKS.md`
- `spec-native/intake/IDEAS.md`
- `spec-native/backlog/README.md`
- `spec-native/workflows/PLANNING.md`
- `spec-native/workflows/REVIEW.md`
- specs separadas por iniciativa en `spec-native/specs/`
- `exports/*.json` generados por tooling externo

## Infraestructura del framework (`.specnative/`)

- `SCHEMA.md` — este archivo; contrato del framework
- `CLI.md` — referencia del CLI (`specnative.py`) y el servidor MCP
- `MCP.md` — configuración del servidor MCP por agente (v0.9)

## Ownership documental

- Problema y objetivos: `spec-native/PRODUCT.md`
- Dirección temporal: `spec-native/ROADMAP.md`
- Restricciones del sistema: `spec-native/ARCHITECTURE.md`, `spec-native/STACK.md`
- Reglas operativas: `spec-native/CONVENTIONS.md`, `spec-native/COMMANDS.md`
- Contrato del framework: `.specnative/SCHEMA.md`
- Cambio requerido: `spec-native/specs/**/SPEC.md`
- Descomposición ejecutable: `spec-native/tasks/**/TASKS.md`
- Decisiones persistentes: `spec-native/DECISIONS.md`
- Relaciones entre artefactos: `spec-native/TRACEABILITY.md`
- Gates de CI y proceso de CD: `spec-native/pipelines/CI.md`, `spec-native/pipelines/CD.md`
- Estado activo de trabajo: `spec-native/SESSION.md`

## Estados obligatorios

### Specs

Toda spec debe declarar: `ID`, `Estado`, `Owner`, `Fecha de creación`, `Última actualización`.

Estados: `draft` · `active` · `blocked` · `done` · `superseded`

### Tareas

Toda tarea debe declarar: `ID`, `Title`, `State`, `Owner`, `Criterio de cierre`.

Estados: `todo` · `in_progress` · `blocked` · `done`

### Decisiones

Toda decisión debe declarar: `ID`, `Fecha`, `Estado`, `Contexto`, `Decisión`, `Consecuencias`.

Estados: `proposed` · `accepted` · `deprecated` · `replaced`

### SESSION.md

Campos mínimos: `state`, `agent`, `initiative`, `task`, `intent`, `last_updated`.

Estados: `idle` · `in_progress` · `blocked` · `waiting_handoff`

## Reglas de trazabilidad

Toda iniciativa debe permitir navegar:
1. De la spec a sus tareas
2. De las tareas a la validación
3. De la spec o tareas a decisiones persistentes
4. De los artefactos a los archivos o cambios principales

## Regla de validación

Antes de cerrar una iniciativa: estado final consistente, validación definida o ejecutada,
trazabilidad mínima registrada, sin contradicciones entre spec, tareas y decisiones.

## Metadata TOML (opcional)

Los bloques TOML son opcionales. El contrato base es documental.
Agrega TOML cuando quieras que `validate`, `status` y `export` del CLI funcionen automáticamente.
