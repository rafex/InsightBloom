# CD.md

Proceso de entrega y ambientes para InsightBloom.

## Ambientes

| Ambiente | Propósito | Infra | Trigger |
|----------|-----------|-------|---------|
| **Local** | Desarrollo | Docker Compose (`container/compose.yml`) | Manual (`just container-dev`) |
| **K3s (mvps)** | Demo/staging | K3s + Helm | Manual o CI (`just deploy-k3s`) |
| **Producción** | — | Pendiente definir | — |

## Proceso de release

1. Build de imágenes Docker:
   ```bash
   docker compose -f container/compose.yml build
   ```
2. Push a registry (GitHub Container Registry):
   - Workflow: `.github/workflows/publish_container.yml`
3. Deploy a K3s:
   ```bash
   helm upgrade --install insightbloom infra/helm/charts/insightbloom \
     --namespace mvps \
     --set image.tag=<tag> \
     --atomic --wait --timeout 10m
   ```
4. Verificar health:
   ```bash
   kubectl get pods -n mvps
   kubectl logs -n mvps deployment/insightbloom-users
   ```

## Gates de promoción

- [ ] CI verde en el commit a desplegar.
- [ ] Imágenes Docker construidas y publicadas.
- [ ] Helm lint sin errores (`helm lint infra/helm/charts/*`).
- [ ] Despliegue canario o dry-run si aplica.

## Rollback

```bash
helm rollback insightbloom --namespace mvps
```

## Configuración de secretos

Los secretos se gestionan con variables de entorno, nunca en el repositorio:
- `DEEPSEEK_API_KEY` — requerido para el servicio chat
- `CHAT_SECRET_KEY` — clave de cifrado para tokens del chat
- `ADMIN_PASS` — usado por scripts de demo/simulación

Ver `.gitignore` para la política de exclusión de secretos.
