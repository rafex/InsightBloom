# IMPLEMENTATION.md

Cómo implementar tareas en InsightBloom.

## Orden de lectura antes de implementar

1. `README.md` local — navegación del repositorio.
2. [`PRODUCT.md`](../PRODUCT.md) — entender el problema y usuarios.
3. [`ARCHITECTURE.md`](../ARCHITECTURE.md) — entender módulos y límites.
4. [`STACK.md`](../STACK.md) — conocer tecnologías y restricciones.
5. [`CONVENTIONS.md`](../CONVENTIONS.md) — seguir reglas de código.
6. [`DECISIONS.md`](../DECISIONS.md) — respetar tradeoffs persistentes.
7. `specs/<iniciativa>/SPEC.md` — entender qué construir.
8. `tasks/<iniciativa>/TASKS.md` — plan ejecutable.

## Flujo de implementación

1. Crear worktree con skill `worktree`.
2. Leer la tarea asignada en `TASKS.md`.
3. Explorar el codebase relevante con `@explore`.
4. Si la tarea involucra nuevos módulos → skill `architect`.
5. Implementar siguiendo arquitectura hexagonal (backend) o separación de concerns (frontend).
6. Escribir tests según [`CONVENTIONS.md`](../CONVENTIONS.md).
7. Ejecutar gates de CI localmente:
   ```bash
   make build && make test && make lint
   ```
8. Validar contra criterios de aceptación de la spec.
9. Commit siguiendo Conventional Commits.
10. Actualizar estado de tarea en `TASKS.md` (`in_progress` → `done`).

## Backend (Java + Ether)

- Respetar paquete `dev.rafex.insightbloom.{servicio}`.
- Arquitectura hexagonal: `domain` → `application` → `adapters` → `bootstrap`.
- Los handlers HTTP no contienen lógica de negocio.
- Las dependencias se inyectan por constructor (wiring manual).
- SQLite con `PRAGMA journal_mode=WAL` y `busy_timeout=5000`.

## Frontend (Vue 3 + Vite)

- Separar `pages`, `features`, `components`, `services`.
- Las rutas en `src/app/router/`.
- La lógica de negocio no se dispersa en componentes visuales.
- Sin TypeScript (por decisión de diseño — DEC-0008 en STACK.md).

## Chat (Python + FastAPI)

- Separar `routers/`, `services/`, `models/`.
- Usar type hints en funciones públicas.
- Validar schemas con Pydantic.
- Tests con pytest + pytest-asyncio.

## Registro de decisiones

Si la implementación produce un tradeoff que debe persistir:
```bash
# Vía MCP
record_decision "Título" "Contexto" "Decisión" "Consecuencias"

# O manualmente en DECISIONS.md
```

Solo registrar si la decisión debe condicionar iniciativas futuras.
