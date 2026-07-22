# TASKS — certificate-editor

## Fase 1 — Contrato y persistencia

- [x] Persistir el motor de certificado por evento (`INHOUSE` por defecto o
      `HTML_CHROME`) y migrar conferencias existentes sin romperlas.
- [x] Permitir seleccionar el motor al crear un evento y cambiarlo desde la
      configuración del evento.
- [x] Catálogo de plantillas base y variables permitidas.
- [x] Tabla `certificate_templates` por conferencia.
- [x] Permiso `MANAGE_CERTIFICATE` para el rol de evento `moderator` y
      migración idempotente de bases existentes.
- [x] API autenticada de catálogo, lectura y guardado por evento.

## Fase 2 — Render y edición

- [x] Endpoint interno de renderizado con Playwright + Chromium.
- [x] Sanitización de documento, texto, estilos e imágenes.
- [x] Editor visual MVP con preview, bloques, variables y estilos básicos.
- [x] Enlace de certificado desde el listado de eventos.
- [x] Mantener la opción global `Diseño de certificado` como respaldo legacy y
      abrir la configuración legacy propia desde la acción del evento cuando
      el motor sea `INHOUSE`.
- [x] Fallback PDFBox para eventos sin plantilla.

## Fase 3 — Validación pendiente

- [ ] Añadir tests de autorización para admin, propietario, host, moderator y
      usuario sin asignación.
- [ ] Añadir tests de rechazo de scripts, URLs externas, demasiados bloques y
      documentos sobredimensionados.
- [ ] Ejecutar build Docker del servicio de presentaciones con Chromium y
      prueba de integración de descarga PDF en K3s.
- [ ] Añadir arrastre con mouse/touch y carga de imágenes de logo como data URL
      desde el editor, manteniendo la misma lista blanca.
