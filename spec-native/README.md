# spec-native/ — Contexto del proyecto

Índice de navegación del contexto SpecNative Development v0.9.

## Documentos core

| Archivo | Propósito | Ownership |
|---------|-----------|-----------|
| [`PRODUCT.md`](./PRODUCT.md) | Problema, usuarios, objetivos, valor diferencial | Producto |
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | Módulos, límites, flujos de datos, restricciones | Arquitectura |
| [`STACK.md`](./STACK.md) | Stack tecnológico y restricciones | Tecnología |
| [`CONVENTIONS.md`](./CONVENTIONS.md) | Reglas de código, naming, testing, commits | Operación |
| [`COMMANDS.md`](./COMMANDS.md) | Comandos del proyecto (build, test, lint, run) | Operación |
| [`DECISIONS.md`](./DECISIONS.md) | Tradeoffs persistentes (10 decisiones) | Arquitectura |
| [`ROADMAP.md`](./ROADMAP.md) | Dirección temporal del proyecto | Producto |
| [`ROLES.md`](./ROLES.md) | Roles de usuario y matriz de permisos | Producto |
| [`TRACEABILITY.md`](./TRACEABILITY.md) | Vínculos entre artefactos | Gobernanza |
| [`SESSION.md`](./SESSION.md) | Estado activo de trabajo (continuidad multi-agente) | Operación |

## Iniciativas

| Iniciativa | Spec | Tareas | Estado |
|-----------|------|--------|--------|
| Main (spec activa) | [`specs/main/SPEC.md`](./specs/main/SPEC.md) | — | active |
| Moderation Dashboard | [`specs/moderation-dashboard/SPEC.md`](./specs/moderation-dashboard/SPEC.md) | — | draft |
| Backend Contracts | [`specs/backend-contracts/SPEC.md`](./specs/backend-contracts/SPEC.md) | — | draft |
| Device Fingerprinting | [`specs/device-fingerprinting/SPEC.md`](./specs/device-fingerprinting/SPEC.md) | — | active |
| Slidev Presentations | [`specs/slidev-presentations/SPEC.md`](./specs/slidev-presentations/SPEC.md) | [`tasks/slidev-presentations/TASKS.md`](./tasks/slidev-presentations/TASKS.md) | active |
| Event Certificate Editor | [`specs/certificate-editor/SPEC.md`](./specs/certificate-editor/SPEC.md) | [`tasks/certificate-editor/TASKS.md`](./tasks/certificate-editor/TASKS.md) | active |
| IDE Publication Separation | [`specs/ide-publication-separation/SPEC.md`](./specs/ide-publication-separation/SPEC.md) | [`tasks/ide-publication-separation/TASKS.md`](./tasks/ide-publication-separation/TASKS.md) | active |

## Workflows

- [`workflows/PLANNING.md`](./workflows/PLANNING.md) — Cómo planificar iniciativas
- [`workflows/IMPLEMENTATION.md`](./workflows/IMPLEMENTATION.md) — Cómo implementar tareas
- [`workflows/REVIEW.md`](./workflows/REVIEW.md) — Cómo revisar contra spec
- [`workflows/CANVAS-PUBLICATION.md`](./workflows/CANVAS-PUBLICATION.md) — Contrato y verificación de publicación de Drawio/Excalidraw
- [`workflows/SLIDEV-PACKAGING.md`](./workflows/SLIDEV-PACKAGING.md) — Formato, generación y validación del ZIP de Slidev
- [`workflows/SLIDEV-ARTIFACT-AUDIT.md`](./workflows/SLIDEV-ARTIFACT-AUDIT.md) — Auditoría y aislamiento de artefactos Slidev precompilados
- [`workflows/PRESENTATION-OFFLINE.md`](./workflows/PRESENTATION-OFFLINE.md) — Paquetes offline cifrados y exclusivos del moderador
- [`workflows/JITSI-TROUBLESHOOTING.md`](./workflows/JITSI-TROUBLESHOOTING.md) — Diagnóstico y prevención de fallos de videollamada Jitsi
- [`workflows/IDE-WEB-PUBLICATION.md`](./workflows/IDE-WEB-PUBLICATION.md) — Auditoría y publicación temporal de páginas del IDE

## Pipelines

- [`pipelines/CI.md`](./pipelines/CI.md) — Gates de integración continua
- [`pipelines/CD.md`](./pipelines/CD.md) — Proceso de entrega y ambientes

## Reglas de navegación

1. Entra por el `README.md` de la carpeta actual.
2. Carga solo el contexto necesario para la tarea.
3. Actualiza el documento fuente de verdad, no un resumen paralelo.
4. Respeta el ownership documental: cada dato vive en un solo archivo.
