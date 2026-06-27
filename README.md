# InsightBloom

Plataforma que convierte mensajes de chat en nubes de palabras interactivas
para conferencias en vivo. Backend Java 25 + Ether 9.5.5, frontend Vue 3 + D3.js,
chat Python/FastAPI + WebSocket, desplegado con Docker Compose y Helm sobre K3s.

## Inicio rápido

```bash
# Levantar todo el stack localmente
just container-dev

# Solo desarrollo (sin Docker)
just dev

# Pipeline CI completo
just ci
```

## Estructura

```
InsightBloom/
├── backend/               ← Microservicios Java 25 (Ether 9.5.5)
│   ├── common/            ← Código compartido
│   ├── contracts/         ← DTOs e interfaces
│   ├── services/          ← 6 microservicios (users, ingest, query, moderation, stats, survey)
│   └── cli/               ← CLI administrativo
├── frontend/web/          ← SPA Vue 3 + Vite + D3.js + Leaflet
├── chat/                  ← Servicio Python FastAPI + WebSocket (bot IA)
├── container/             ← Docker Compose + Dockerfiles multi-stage
├── infra/                 ← Helm charts para K3s
├── spec-native/           ← Contexto SpecNative Development v0.7
│   ├── PRODUCT.md         ← Problema, usuarios, objetivos
│   ├── ARCHITECTURE.md    ← Estructura del sistema
│   ├── STACK.md           ← Stack tecnológico
│   ├── specs/             ← Especificaciones por iniciativa
│   └── tasks/             ← Tareas ejecutables
├── agents/                ← Artefactos operativos (SECURITY.md, DIAGNOSE.md)
├── docs/                  ← Legacy (deprecado — ver spec-native/)
├── Makefile               ← Builder (compila, testea, lintea)
├── Justfile               ← Task runner (dev, deploy, demo, simulación)
└── AGENTS.md              ← Contrato operativo para agentes IA
```

## Navegación para agentes IA

Este repositorio sigue **SpecNative Development v0.7**.
El contexto del proyecto está versionado en `spec-native/`.

1. Lee [`AGENTS.md`](./AGENTS.md) — contrato operativo para agentes.
2. Lee [`spec-native/README.md`](./spec-native/README.md) — índice de contexto.
3. Carga solo el documento necesario para tu tarea.

## Documentación

| Documento | Ubicación |
|-----------|-----------|
| Producto y objetivos | [`spec-native/PRODUCT.md`](./spec-native/PRODUCT.md) |
| Arquitectura | [`spec-native/ARCHITECTURE.md`](./spec-native/ARCHITECTURE.md) |
| Stack tecnológico | [`spec-native/STACK.md`](./spec-native/STACK.md) |
| Convenciones | [`spec-native/CONVENTIONS.md`](./spec-native/CONVENTIONS.md) |
| Comandos | [`spec-native/COMMANDS.md`](./spec-native/COMMANDS.md) |
| Decisiones | [`spec-native/DECISIONS.md`](./spec-native/DECISIONS.md) |
| Roadmap | [`spec-native/ROADMAP.md`](./spec-native/ROADMAP.md) |
| Spec activa | [`spec-native/specs/main/SPEC.md`](./spec-native/specs/main/SPEC.md) |
| Diagnóstico | [`agents/DIAGNOSE.md`](./agents/DIAGNOSE.md) |
| Seguridad | [`agents/SECURITY.md`](./agents/SECURITY.md) |

## Licencia

Ver [`LICENSE`](./LICENSE).
