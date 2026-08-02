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

## CI/CD y resiliencia de builds

- [ ] **Desacoplar el bootstrap de BuildKit de Docker Hub.**
  Posible mejora futura para `publish-code-ide.yml`: espejar una versión fijada
  de `moby/buildkit` en un paquete controlado de GHCR (idealmente por digest) y
  configurar `docker/setup-buildx-action` con `driver-opts.image` apuntando a
  ese espejo. La autenticación al GHCR debe ocurrir antes de iniciar Buildx.
  Esta mejora queda documentada, pero no se implementa todavía: el fallo
  observado fue una indisponibilidad transitoria de Docker Hub durante el
  bootstrap, no un error del Dockerfile.

  Criterios para promoverla a tarea ejecutable:
  - publicar y verificar el espejo de BuildKit con permisos de lectura para
    GitHub Actions;
  - fijar la referencia por versión o digest y documentar su renovación;
  - ejecutar las tres variantes de `code-ide` y comprobar que Buildx arranca
    sin consultar Docker Hub;
  - conservar una estrategia operativa de reintento para indisponibilidades
    transitorias del registro.

## Observabilidad futura

- [ ] **Plataforma central de logs y trazabilidad distribuida.**
  Iniciativa futura; no iniciar implementación todavía. Debe recibir y
  consultar logs de los servicios Java, Python y Node.js, generar un
  `trace_id` capaz de seguir una petición entre APIs, WebSockets, NATS,
  presentaciones y sandboxes, y ofrecer una exportación NDJSON para consultar
  los resultados con `lnav`.

  Componentes incluidos en el alcance:
  - Java: `insightbloom-users`, `ingest`, `query`, `moderation`, `stats`,
    `survey`, `tools-gateway`, `insightbloom-cli`, `common` y `contracts`.
  - Python: `chat`, `telegram` y `infra/egress-proxy`.
  - Node.js: `insightbloom-presentations`.

  La decisión de arquitectura queda abierta entre:
  1. Vector o Fluent Bit como `DaemonSet` + Loki/Grafana + exportador para
     `lnav`.
  2. OpenTelemetry Collector + Tempo/Loki/Grafana, con instrumentación OTLP
     en Java, Python y Node.js.

  La primera evaluación debe preferir colectores por nodo sobre sidecars en
  cada Pod; los sidecars solo se justificarían para logs internos escritos en
  archivos. Antes de convertirlo en una tarea ejecutable hay que definir el
  esquema JSON, propagación W3C `traceparent`, contexto en NATS/WebSockets,
  redacción de secretos y código, retención, muestreo, RBAC por conferencia,
  almacenamiento y pruebas de trazabilidad extremo a extremo.

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
- [x] Adoptar `StatusBadge` en los estados operativos de sandboxes y boletos.
  La configuración del evento y el editor de moderación traducen las fases de Kubernetes
  (`Running`, `ContainerCreating`, `NotFound`, etc.) a etiquetas en español con tonos
  semánticos; los nombres de los ocupantes son visibles y el UUID queda en tooltip. El boleto
  del asistente reutiliza el mismo componente para mostrar `Reservado`/`Listo` sin exponer enums.
- [ ] Verificar que el commit validado sea el que está desplegado en producción.
