# MCP.md — SpecNative MCP Server v0.7

Configuración del servidor MCP para InsightBloom.

## Instalación

El servidor MCP se instala automáticamente en `.specnative/`:

```
.specnative/specnative_mcp.py   ← servidor MCP
.specnative/.venv/              ← venv con dependencias (mcp, etc.)
```

Para instalar o reinstalar:

```bash
curl -sSL https://github.com/rafex/SpecNative-Development/releases/latest/download/install.py \
  | python3 - --target . --reinstall
```

## Configuración por agente

### Claude Code

```bash
claude mcp add specnative \
  "$(pwd)/.specnative/.venv/bin/python3" \
  "$(pwd)/.specnative/specnative_mcp.py" \
  -- --repo "$(pwd)"
```

### OpenCode

Configurado en `opencode.json` (generado automáticamente):

```json
{
  "$schema": "https://opencode.ai/config.json",
  "instructions": ["AGENTS.md", "spec-native/README.md"],
  "mcp": {
    "specnative": {
      "type": "local",
      "enabled": true,
      "command": ["./.specnative/.venv/bin/python3", "./.specnative/specnative_mcp.py"]
    }
  }
}
```

### Codex CLI

```toml
[mcp_servers.specnative]
command = "/ruta/a/InsightBloom/.specnative/.venv/bin/python3"
args = ["/ruta/a/InsightBloom/.specnative/specnative_mcp.py", "--repo", "/ruta/a/InsightBloom"]
type = "stdio"
```

## Recursos MCP

| URI | Documento |
|-----|-----------|
| `spec://agents` | `AGENTS.md` |
| `spec://session` | `spec-native/SESSION.md` |
| `spec://context/product` | `spec-native/PRODUCT.md` |
| `spec://context/architecture` | `spec-native/ARCHITECTURE.md` |
| `spec://context/stack` | `spec-native/STACK.md` |
| `spec://context/conventions` | `spec-native/CONVENTIONS.md` |
| `spec://context/commands` | `spec-native/COMMANDS.md` |
| `spec://context/decisions` | `spec-native/DECISIONS.md` |
| `spec://context/roadmap` | `spec-native/ROADMAP.md` |
| `spec://context/traceability` | `spec-native/TRACEABILITY.md` |
| `spec://pipelines/ci` | `spec-native/pipelines/CI.md` |
| `spec://pipelines/cd` | `spec-native/pipelines/CD.md` |
| `spec://schema` | `.specnative/SCHEMA.md` |

## Herramientas clave

- `resume()` / `checkpoint()` — continuidad entre agentes
- `status()` / `validate()` — salud del repositorio
- `health_check()` / `suggest_next()` — diagnóstico y recomendaciones
- `start_initiative()` / `plan_tasks()` / `close_initiative()` — ciclo de vida de iniciativas
- `log_decision()` — registro de decisiones persistentes
