# Tareas: Preparación de materiales para IDEs

- [x] Crear caché GitHub público con releases atómicos por SHA.
- [x] Añadir configuración de fuente y bootstrap por evento, validada y persistida.
- [x] Ejecutar preparador no privilegiado en Web, CLI y agentes multi-asiento.
- [x] Exponer estado y diagnóstico dentro del workspace sin bloquear el IDE.
- [x] Declarar servicio, políticas de red y workflow de imagen mediante GitOps.
- [x] Mover el almacenamiento del caché a PVC local `local-path` RWO de 1 GiB.
- [x] Añadir descarga HTTP interna de archivos/subárboles fijada por SHA y protegida contra rutas inválidas.
- [x] Añadir token interno exclusivo para `/sync`; sandboxes solo leen por red interna y no montan el PVC.
- [x] Añadir límites de archivo y de caché, limpieza de revisiones y expiración de fuentes inactivas.
- [x] Actualizar los IDEs para descargar scripts versionados y materiales con `material-copy`.
- [x] Conservar el PVC Longhorn legado durante el corte, sin retirarlo ni tocar datos activos.
- [ ] Ejecutar pruebas, validar Helm y reconciliar Flux en clúster.
- [ ] Confirmar que ningún Pod monta el claim Longhorn legado y retirarlo en un cambio GitOps posterior.
