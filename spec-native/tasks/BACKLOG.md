# Backlog técnico y UX/UI

## Presentaciones

- [ ] **Reactivar gzip en `/api/presentations` después de validar el presenter.**
  Mantenerlo desactivado mientras se confirma que los assets JavaScript/CSS no
  vuelven a producir `NS_ERROR_CORRUPTED_CONTENT` en Firefox.

  Criterios de cierre:
  - desplegar una versión con la CSP específica de Slidev y `Accept-Encoding`
    controlado;
  - validar con una presentación nueva y otra generada antes del cambio;
  - comprobar en Network que `Content-Encoding`, `Content-Length` y el cuerpo
    coinciden para JS y CSS;
  - reactivar gzip solo para respuestas textuales y repetir la prueba en Firefox
    y Chromium;
  - conservar el fallback legacy `presenter-assets` durante la transición.

## UX/UI

- [ ] Migrar estilos `scoped` genéricos elegibles a tokens y componentes canónicos.
- [ ] Revisar los 80 colores hex documentados para detectar tokens reutilizables.
- [ ] Completar recorridos responsive, teclado, lector de pantalla y autenticados
  por rol.
- [ ] Verificar que el commit validado sea el que está desplegado en producción.
