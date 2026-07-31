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

- [x] Migrar estilos `scoped` genéricos elegibles a tokens y componentes canónicos.
  Auditoría cerrada: los controles reutilizables de tema y lienzo de creación/edición/configuración,
  el layout de nubes de dudas/temas y las acciones de tablas administrativas ahora viven en estilos
  compartidos. El inventario pasó de 87 a 85 archivos `scoped`; los restantes están clasificados como
  componentes canónicos, pantallas de dominio, shell, visualización, herramientas embebidas o
  componentes compartidos y no contienen candidatos genéricos pendientes identificados por la revisión.
- [x] Revisar los 80 colores hex documentados para detectar tokens reutilizables.
  Se migraron 65 literales de los pines SVG de mapas y de la ilustración de carga del
  sandbox a tokens globales; permanecen 8 literales intencionales en el lienzo de mapa
  y certificados.
- [ ] Completar recorridos responsive, teclado, lector de pantalla y autenticados
  por rol.
  Avance: las pestañas de configuración del evento ya tienen semántica `tab`/`tabpanel`,
  navegación con flechas/Home/End y foco visible; las tablas de usuarios, tipos y roles
  tienen acciones y campos inline navegables con nombres accesibles. Falta recorrer el
  dashboard completo por rol en móvil y con lector de pantalla.
- [ ] Verificar que el commit validado sea el que está desplegado en producción.
