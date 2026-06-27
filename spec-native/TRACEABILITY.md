# TRACEABILITY.md

Vínculos entre artefactos: specs, tareas, decisiones, archivos y validación.

---

| Artifact | Type | Links to | Notes |
|----------|------|----------|-------|
| SPEC-MAIN | spec | ARCHITECTURE.md, STACK.md, DECISIONS.md | Spec activa principal |
| SPEC-MOD-DASH | spec | SPEC-MAIN | Dashboard de moderación |
| SPEC-BACKEND-CONTRACTS | spec | ARCHITECTURE.md, DECISIONS.md | Contratos entre servicios |
| DEC-0001 | decision | ARCHITECTURE.md | SQLite como persistencia PoC |
| DEC-0002 | decision | ARCHITECTURE.md | Microservicios separados |
| DEC-0003 | decision | ARCHITECTURE.md | UUID externos, serial internos |
| DEC-0004 | decision | ARCHITECTURE.md | RelevanceScore visible |
| DEC-0005 | decision | PRODUCT.md | FriendlyId derivado |
| DEC-0006 | decision | COMMANDS.md | Makefile builder / Justfile runner |
| DEC-0007 | decision | ARCHITECTURE.md | Sincronización moderation→query |
| DEC-0008 | decision | STACK.md | Ether 9.5.5 como BOM |
| DEC-0009 | decision | ROLES.md | Login con password obligatorio |
| DEC-0010 | decision | STACK.md | SQLite WAL mode |
| SEC-001 | security | agents/SECURITY.md | Auditoría de seguridad (7 hallazgos) |
| DIAG-001 | diagnostic | agents/DIAGNOSE.md | Diagnóstico del proyecto 2026-06-26 |

---

## Estado de iniciativas

| Iniciativa | Spec | Tareas (todo/in_progress/done) | Última actualización |
|-----------|------|-------------------------------|---------------------|
| main | specs/main/SPEC.md | — | active |
| moderation-dashboard | specs/moderation-dashboard/SPEC.md | — | draft |
| backend-contracts | specs/backend-contracts/SPEC.md | — | draft |
| specnative-migration | — | — | 2026-06-26 |

---

_Actualizar al cerrar cada iniciativa._
