# archetypes/

Archetypes propios de InsightBloom.

## Cómo crear un archetype

1. Crea una carpeta: `.specnative/archetypes/<nombre>/`
2. Crea `archetype.toml` con metadata
3. Agrega los documentos `.md` que componen el archetype

### Formato de `archetype.toml`

```toml
[archetype]
name = "nombre-del-archetype"
version = "0.1.0"
description = "Descripción breve del archetype"
stack = ["java", "vue", "python"]
```

### Documentos incluibles

Cada documento `.md` en la carpeta del archetype se copia a `spec-native/`
cuando se aplica el archetype. Nombres válidos:

- `PRODUCT.md`
- `ARCHITECTURE.md`
- `STACK.md`
- `CONVENTIONS.md`
- `COMMANDS.md`
- `DECISIONS.md`
- `ROADMAP.md`

## Archetypes disponibles

| Nombre | Descripción | Stack |
|--------|-------------|-------|
| (built-in) `java-hexagonal` | Java 21 + Spring Boot 3 + Hexagonal | Java |
| (pendiente) `insightbloom-microservice` | Microservicio Java 25 + Ether 9.5 + SQLite | Java |

## Built-in archetypes

El MCP incluye `java-hexagonal` como archetype built-in. Los archetypes
propios en esta carpeta se fusionan con los built-in y tienen precedencia.
