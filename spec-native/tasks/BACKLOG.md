# Backlog técnico y UX/UI

## Presentaciones

- [x] **Reactivar gzip en `/api/presentations` después de validar el presenter.**
  Implementado en `container/frontend/nginx.conf`: el upstream entrega los
  assets sin comprimir y Nginx comprime solo respuestas textuales proxificadas,
  conservando la CSP específica de Slidev y el fallback legacy `presenter-assets`.

- [x] **Validar gzip de presentaciones después del despliegue.**
  Validado en producción: los assets textuales de presentaciones se entregan con
  `Content-Encoding: gzip` y el presenter carga correctamente.

  Criterios de cierre ya verificados:
  - desplegar una versión con la CSP específica de Slidev y `Accept-Encoding`
    controlado;
  - validar con una presentación nueva y otra generada antes del cambio;
  - comprobar en Network que `Content-Encoding`, `Content-Length` y el cuerpo
    coinciden para JS y CSS;
  - confirmar que el navegador carga todos los imports del presenter sin
    `NS_ERROR_CORRUPTED_CONTENT`;
  - conservar el fallback legacy `presenter-assets` durante la transición.

## UX/UI

- [x] Normalizar los estados visibles del boleto para que la UI no exponga enums en mayúsculas;
  el código del backend sigue intacto y la tarjeta usa etiquetas legibles (`Listo`/`Reservado`).
- [x] Corregir la semántica accesible del shell: `Salir` es un botón de acción y el aviso offline
  se anuncia como estado para lectores de pantalla, manteniendo el mismo diseño visual.
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
- [x] Extender la adopción de estados canónicos a superficies de moderación y soporte.
  `WorkspaceFileEditor` usa `LoadingState`, `EmptyState` y `FeedbackMessage`; el tutor IA
  y el certificado de encuesta usan `FeedbackMessage`, eliminando los mensajes de error
  locales equivalentes.
- [ ] Completar recorridos responsive, teclado, lector de pantalla y autenticados
  por rol.
  Avance: las pestañas de configuración del evento ya tienen semántica `tab`/`tabpanel`,
  navegación con flechas/Home/End y foco visible; sus controles tienen `label`/`for`, y
  las asignaciones y tablas se adaptan mejor a móvil. Las tablas de usuarios, tipos y roles
  tienen acciones y campos inline navegables con nombres accesibles. Las superficies de
  configuración, presentación, mapas, nubes y tutor IA conservan ahora el foco visible
  con `:focus-visible`; los menús desplegables compartidos soportan flechas, Home/End,
  Escape y retorno de foco. Falta recorrer el dashboard completo por rol en móvil y con
    lector de pantalla.
- [x] Adoptar `StatusBadge` en los estados operativos de sandboxes.
  La configuración del evento y el editor de moderación traducen las fases de Kubernetes
  (`Running`, `ContainerCreating`, `NotFound`, etc.) a etiquetas en español con tonos
  semánticos; los nombres de los ocupantes son visibles y el UUID queda en tooltip.
- [ ] Verificar que el commit validado sea el que está desplegado en producción.
