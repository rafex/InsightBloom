# pipelines/

Contexto de integración continua y entrega continua.

La integración continua vive en este repositorio. La entrega continua del
cluster vive en `/Users/rafex/repository/github/rafex/InsightBloom-gitops` y la
reconciliación la ejecuta FluxCD en `k3s-server1`.

## Documentos

| Archivo | Propósito |
|---------|-----------|
| [`CI.md`](./CI.md) | Gates de integración continua — qué se valida en cada push/PR |
| [`CD.md`](./CD.md) | Proceso de entrega — ambientes, releases, rollback |

## Relación con CI/CD real

Estos documentos describen el *qué* y el *por qué* de los pipelines.
La implementación concreta vive en `.github/workflows/`.
