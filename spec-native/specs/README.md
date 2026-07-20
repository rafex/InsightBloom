# specs/ — Iniciativas

Cada iniciativa tiene su propia carpeta con `SPEC.md`.

## Iniciativas activas

| ID | Iniciativa | Estado | Owner |
|----|-----------|--------|-------|
| — | [main](./main/SPEC.md) | active | team |
| — | [moderation-dashboard](./moderation-dashboard/SPEC.md) | draft | team |
| — | [backend-contracts](./backend-contracts/SPEC.md) | draft | team |
| — | [event-types-catalog](./event-types-catalog/SPEC.md) | draft | team |
| — | [event-roles](./event-roles/SPEC.md) | draft | team |
| — | [code-ide-sandboxes](./code-ide-sandboxes/SPEC.md) | draft | team |
| — | [device-fingerprinting](./device-fingerprinting/SPEC.md) | active | team |

## Cómo crear una nueva iniciativa

```bash
mkdir -p spec-native/specs/<iniciativa>
cp .specnative/templates/specs/default.md spec-native/specs/<iniciativa>/SPEC.md
```

Luego completa la spec siguiendo [`workflows/PLANNING.md`](../workflows/PLANNING.md).
