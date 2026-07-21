# Publicación de lienzos del moderador

Este documento es el contrato operativo para Drawio y Excalidraw cuando un
evento usa `MODERATOR_ONLY`. Su objetivo es evitar que una modificación del
flujo de publicación vuelva a mostrar un editor a los asistentes o rompa la
actualización del material publicado.

## Invariantes

- El moderador conserva la fuente nativa editable y una representación
  publicada para lectura.
- El asistente en `MODERATOR_ONLY` nunca carga el editor de la herramienta: sólo
  recibe la última exportación publicada.
- En `INDEPENDENT`, el asistente trabaja en una instancia local y sus cambios
  no se envían al backend.
- La actualización en vivo es una notificación de nueva versión, no
  colaboración: no hay cursores, presencia, CRDT ni WebSocket de edición.
- El servicio Users valida evento, boleto, capacidad y autor antes de aceptar
  una escritura.

## Flujo de Drawio

El editor se carga como iframe con el protocolo `postMessage` de Drawio:

1. La página solicita `GET /conferences/{id}/diagram`.
2. El iframe anuncia `init` o `ready`.
3. La página envía `load` con el XML guardado.
4. El moderador guarda o activa el autoguardado y la página recibe el XML.
5. La página solicita exactamente `{ action: "snapshot" }`.
6. Drawio devuelve un evento `export` con el SVG como data URI.
7. La página envía `PUT /conferences/{id}/diagram` con XML y SVG.
8. Users incrementa la versión, persiste la fecha y publica un evento SSE.
9. El asistente actualiza la imagen; polling y el botón flotante son el
   respaldo si SSE no está disponible.

La acción `snapshot` es deliberada. En la versión desplegada de Drawio, usar
el camino genérico `export` desde el host produjo errores como `Not a diagram
file` y `O.substring is not a function`; no debe reemplazarse sin probar el
protocolo de la imagen desplegada.

## Flujo de Excalidraw

La aplicación Excalidraw externa no ofrece el mismo contrato de embed que
Drawio: su protocolo interno de escena está restringido al origen de la
aplicación oficial y la escena normal se mantiene en el `localStorage` de su
propio origen. Por eso la página no intenta leer el iframe ni su storage.

La pizarra usa una instancia controlada de `@excalidraw/excalidraw` dentro de
InsightBloom:

1. La página solicita `GET /conferences/{id}/whiteboard`.
2. El moderador recibe la escena JSON nativa `.excalidraw`.
3. `onChange` serializa la escena con `serializeAsJSON` y genera el SVG con
   `exportToSvg`.
4. Una escritura con debounce envía ambos valores a
   `PUT /conferences/{id}/whiteboard`.
5. Users incrementa la versión y publica el evento en
   `GET /conferences/{id}/whiteboard/stream`.
6. El asistente en `MODERATOR_ONLY` sólo muestra el SVG guardado y actualiza
   por SSE, polling y botón flotante.

La instancia controlada no crea una capa WebSocket propia. La documentación
oficial de [Excalidraw](https://docs.excalidraw.com/) y su
[paquete embebible](https://github.com/excalidraw/excalidraw) son la referencia
para el formato de escena y las exportaciones compatibles.

## Gates para no regresar

### Código local

Ejecutar desde la raíz de InsightBloom:

```bash
./mvnw -pl backend/services/insightbloom-users -am test
cd frontend/web
npm test
npm run typecheck
npm run build
npx eslint src/pages/conference/DiagrammingPage.vue src/pages/conference/WhiteboardPage.vue src/components/ExcalidrawEditor.ts
```

El smoke test manual debe comprobar ambas identidades:

- moderador: puede editar y guardar la fuente nativa;
- asistente: no encuentra un iframe/editor editable, ve la exportación, recibe
  una nueva versión y puede forzar refresco.

Para Drawio, la captura de red debe mostrar `PUT /diagram` con XML y SVG. Para
Excalidraw debe mostrar `PUT /whiteboard` con `sceneJson` y `publishedSvg`.

### CI, GHCR y FluxCD

Este repositorio construye, prueba y publica imágenes en GHCR. El despliegue no
se hace aquí: FluxCD consume los manifiestos de
`/Users/rafex/repository/github/rafex/InsightBloom-gitops`.

Después de que CI publique la imagen, verificar el despliegue en K3s:

```bash
export KUBECONFIG=~/.kube/config_k3s_server1
flux reconcile image repository insightbloom-web -n insightbloom
flux reconcile image policy insightbloom-web -n insightbloom
flux reconcile image update insightbloom -n insightbloom
flux reconcile kustomization insightbloom -n flux-system --with-source
kubectl rollout status deployment/insightbloom-web -n insightbloom --timeout=90s
kubectl get deployment insightbloom-web -n insightbloom \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

No se debe declarar “desplegado” sólo porque el commit está en `main`: hay que
confirmar el tag de imagen en el Deployment y repetir el smoke test contra el
sitio.

## Fallos conocidos y diagnóstico rápido

| Síntoma | Comprobación |
|---|---|
| El asistente ve el editor | Revisar que el modo sea `MODERATOR_ONLY` y que el template no monte la instancia editable para asistentes. |
| El asistente ve un lienzo vacío | Revisar respuesta de `GET`, que la exportación no sea nula y la respuesta de `PUT`. |
| No llegan cambios | Revisar SSE, luego polling y el botón de refresco; la persistencia debe funcionar aun sin SSE. |
| Drawio muestra `Not a diagram file` | Confirmar que la solicitud usa `action: "snapshot"`, no el export genérico del host. |
| Excalidraw no publica | Confirmar que la instancia controlada dispara `onChange`, que el debounce termina y que el JSON y SVG pasan los límites del backend. |
| El código funciona local pero el sitio no cambia | Revisar GHCR, la reconciliación de ImagePolicy y el commit generado por Flux en el repositorio GitOps. |
