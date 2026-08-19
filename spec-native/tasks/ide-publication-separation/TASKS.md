# Tareas — Separación de publicación para IDEs

Estado: in_progress

- [x] Crear `insightbloom-ide-publisher` con auditoría, almacenamiento, TTL,
  revocación y resolución de app-preview.
- [x] Crear `insightbloom-ide-runtime` con agente Podman, build/run y control
  interno autenticado.
- [x] Delegar desde users publicación estática, API y build de contenedores.
- [x] Resolver app-preview desde tools-gateway mediante publisher.
- [x] Separar imágenes/workflows, PVC, Service, NetworkPolicy y ServiceAccount
  en GitOps.
- [x] Mantener los prefijos públicos `/p/` y `app-preview`.
- [ ] Migrar publicaciones activas al PVC del publisher y ejecutar rollback drill.
- [ ] Completar pruebas de integración en cluster para capabilities y puertos.
- [ ] Confirmar rollout FluxCD y retirar definitivamente el upstream antiguo.
