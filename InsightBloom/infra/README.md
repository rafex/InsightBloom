# Infra

Infraestructura de InsightBloom.

- `docker/`: Dockerfiles para servicios Java y frontend.
- `compose/`: Docker Compose para desarrollo local.
- `scripts/`: scripts de infraestructura.
- `helm/charts/`: charts de despliegue en Kubernetes.

## Local

```bash
docker compose -f infra/compose/local.yml up --build
docker compose -f infra/compose/local.yml down
```
