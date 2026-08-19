# Separación de publicación para IDEs

Estado: active

## Objetivo

Separar la entrega de presentaciones de las publicaciones originadas en los
IDE Web/CLI. `insightbloom-presentations` conserva únicamente Marp/Slidev,
renderizado, miniaturas, PDF y WebSocket de presentación. La publicación de
sitios y el registro de destinos de API pasan a `insightbloom-ide-publisher`;
los builds y procesos Podman pasan a `insightbloom-ide-runtime`.

## Límites y contratos

- `insightbloom-users` mantiene autenticación, permisos, asignación de sandbox
  y compatibilidad de las rutas públicas. Autoriza la operación y delega el
  ZIP/API al publisher o el build al runtime.
- `insightbloom-ide-publisher` audita ZIPs, almacena publicaciones estáticas,
  registra destinos vivos y resuelve el destino para tools-gateway.
- `insightbloom-ide-runtime` recibe un `Containerfile` validado, construye y
  ejecuta el contenedor en un Podman dedicado y expone el rango reservado
  `9500-9509`.
- `tools-gateway` consulta al publisher con la clave interna y solo recibe un
  destino ya validado por `publicationId` y token de publicación.
- Las capabilities firmadas se limitan a conferencia, sandbox, asiento,
  operación y expiración; nunca se colocan en URLs, query strings o respuestas
  públicas.

## Compatibilidad y migración

Las URLs `/p/<publicationId>/` y `/p/<publicationId>/...`, así como el host
actual de app-preview, permanecen vigentes. El Ingress/Nginx conserva los
prefijos y cambia únicamente el upstream al publisher. Las publicaciones
activas deben copiarse al PVC del publisher antes de retirar el volumen de
presentaciones. Durante el rollout se mantiene un cambio reversible de
upstream hacia el servicio anterior.

## Criterios de aceptación

- El pod de presentaciones no contiene rutas `/p` de IDE ni `/internal/v1/previews`.
- Publisher y runtime tienen imágenes, PVC, health checks, métricas y workflows
  independientes.
- Una capability vencida, manipulada o de otra conferencia/sandbox/asiento es
  rechazada; el flujo normal `Authorization: Bearer` sigue funcionando.
- Publicar, resolver, revocar y expirar sitio, API y contenedor tienen pruebas
  unitarias e integración documentadas.
