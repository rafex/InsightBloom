# TRACEABILITY.md

Vínculos entre artefactos: specs, tareas, decisiones, archivos y validación.

---

| Artifact | Type | Links to | Notes |
|----------|------|----------|-------|
| SPEC-MAIN | spec | ARCHITECTURE.md, STACK.md, DECISIONS.md | Spec activa principal |
| SPEC-MOD-DASH | spec | SPEC-MAIN | Dashboard de moderación |
| SPEC-BACKEND-CONTRACTS | spec | ARCHITECTURE.md, DECISIONS.md | Contratos entre servicios |
| SPEC-EVENT-TYPES-CATALOG | spec | ARCHITECTURE.md, DECISIONS.md, ROADMAP.md, ROLES.md | Catalogo de tipos de evento admin-managed (draft) |
| DEC-0001 | decision | ARCHITECTURE.md | SQLite como persistencia |
| DEC-0002 | decision | ARCHITECTURE.md | Microservicios separados |
| DEC-0003 | decision | ARCHITECTURE.md | UUID externos, serial internos |
| DEC-0004 | decision | ARCHITECTURE.md | RelevanceScore visible |
| DEC-0005 | decision | PRODUCT.md | FriendlyId derivado |
| DEC-0006 | decision | COMMANDS.md | Makefile builder / Justfile runner |
| DEC-0007 | decision | ARCHITECTURE.md | Sincronización moderation→query |
| DEC-0008 | decision | STACK.md | Ether 9.5.5 como BOM |
| DEC-0009 | decision | ROLES.md | Login con password obligatorio |
| DEC-0010 | decision | STACK.md | SQLite WAL mode |
| DEC-0011 | decision | ROLES.md, backend/services/insightbloom-users | ADMIN role + multi-rol |
| DEC-0012 | decision | STACK.md, backend/services/insightbloom-presentations | Presentations Node.js |
| DEC-0013 | decision | STACK.md, pipelines/CD.md | OTP dual Twilio + Zoho |
| DEC-0014 | decision | STACK.md, backend/services/insightbloom-survey | LLM para survey |
| DEC-0015 | decision | spec-native/*, AGENTS.md, opencode.json | Migración SpecNative v0.7 |
| DEC-0016 | decision | SPEC-EVENT-TYPES-CATALOG | Catalogo de tipos de evento gateado por capacidades (proposed) |
| DEC-0017 | decision | SPEC-EVENT-TYPES-CATALOG, STACK.md | Jitsi/Excalidraw/drawio/Etherpad self-hosted en K3s (proposed) |
| DEC-0018 | decision | SPEC-EVENT-TYPES-CATALOG, backend/services/insightbloom-survey | SurveyJS como motor alternativo (proposed, licencia pendiente) |
| DEC-0019 | decision | SPEC-EVENT-TYPES-CATALOG, backend/services/insightbloom-users | seatmap-canvas como motor de mapa alternativo (proposed, mantenimiento pendiente) |
| DEC-0020 | decision | SPEC-EVENT-TYPES-CATALOG, infra/helm/charts/insightbloom | Instancias compartidas + HPA + TTL de datos para drawio/Etherpad/Jitsi/Excalidraw (accepted) |
| SEC-001 | security | agents/SECURITY.md | Auditoría de seguridad (7 hallazgos) |
| DIAG-001 | diagnostic | agents/DIAGNOSE.md | Diagnóstico del proyecto 2026-06-26 |

---

## Estado de iniciativas

| Iniciativa | Spec | Tareas (todo/in_progress/done) | Última actualización |
|-----------|------|-------------------------------|---------------------|
| main | specs/main/SPEC.md | — | active |
| moderation-dashboard | specs/moderation-dashboard/SPEC.md | — | done |
| backend-contracts | specs/backend-contracts/SPEC.md | — | draft |
| event-types-catalog | specs/event-types-catalog/SPEC.md | 0/0/0 (ver tasks/event-types-catalog/TASKS.md) | draft |

---

## Servicios y su documentación

| Servicio | Puerto | Documentado en |
|----------|--------|---------------|
| users | 8081 | ARCHITECTURE.md § insightbloom-users |
| ingest | 8082 | ARCHITECTURE.md § insightbloom-ingest |
| query | 8083 | ARCHITECTURE.md § insightbloom-query |
| moderation | 8084 | ARCHITECTURE.md § insightbloom-moderation |
| stats | 8085 | ARCHITECTURE.md § insightbloom-stats |
| survey | 8086 | ARCHITECTURE.md, STACK.md (DEC-0014) |
| presentations | 8091 | ARCHITECTURE.md, STACK.md (DEC-0012) |
| chat | 8090 | ARCHITECTURE.md, STACK.md (Python/FastAPI) |
| web | 80 | ARCHITECTURE.md (Vue 3 SPA) |
| cli | — | COMMANDS.md, ROLES.md |

---

_Actualizar al cerrar cada iniciativa._
