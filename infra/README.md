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

## K3s: dimensionamiento por Goldilocks

El despliegue de Kubernetes usa el chart `infra/helm/charts/insightbloom`.
El namespace operativo actual en Server1 es `insightbloom`.

El 2026-06-30 se ajustaron los recursos del chart con base en Goldilocks/VPA en
modo recomendacion. La lectura mostro que los servicios de InsightBloom estaban
sobredimensionados en CPU:

- Uso actual observado: 1m-2m CPU por servicio.
- Recomendacion Goldilocks: 15m CPU target, 16m CPU upper en la mayoria.
- Memoria observada: 10Mi-71Mi segun servicio.
- Recomendacion Goldilocks: 100Mi target y upper entre 100Mi y 158Mi.

Configuracion aplicada:

- Servicios backend por defecto:
  - CPU request: `25m`
  - CPU limit: `200m`
  - Memory request: `128Mi`
  - Memory limit: `512Mi`
- `web`:
  - CPU request: `25m`
  - CPU limit: `100m`
  - Memory request: `64Mi`
  - Memory limit: `128Mi`
- `nats`:
  - CPU request: `15m`
  - CPU limit: `100m`
  - Memory request: `64Mi`
  - Memory limit: `128Mi`
- HPA:
  - `minReplicas: 1`
  - `maxReplicas: 5`
  - `targetCPUUtilizationPercentage: 60`

La intencion es correr mas pods pequenos en vez de pocos pods grandes. Esto
reduce CPU reservada y permite que el HPA escale antes cuando haya carga real.

Validacion operativa en Server1:

```bash
ssh my-k3s 'sudo kubectl get hpa -n insightbloom'
ssh my-k3s 'sudo kubectl top pods -n insightbloom --containers'
ssh my-k3s 'sudo kubectl get vpa -n insightbloom'
```

Despues de cada despliegue, espera al menos unas horas antes de tomar una nueva
decision de recursos con Goldilocks. Para recomendaciones mas confiables, usa
una ventana de 24 a 48 horas con trafico representativo.
