# Publicación de lienzos del moderador y notas del evento

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
- Etherpad es grupal (`COLLABORATIVE`) por defecto. En `INDEPENDENT`, cada
  asistente recibe un pad privado calculado por Users, puede exportarlo mientras
  el evento está vigente y el job TTL lo elimina después.
- El ZIP de materiales contiene sólo publicaciones del moderador y el pad
  grupal; nunca contiene pads privados.

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
3. El wrapper ignora el `onChange` del ciclo de carga y los cambios de viewport
   mientras la lista de elementos siga igual; así una escena inicial vacía no
   puede sobrescribir una publicación.
4. Después de una modificación real, `onChange` serializa la escena con
   `serializeAsJSON` y genera el SVG con `exportToSvg`.
5. Una escritura con debounce envía ambos valores a
   `PUT /conferences/{id}/whiteboard`.
6. Users incrementa la versión y publica el evento en
   `GET /conferences/{id}/whiteboard/stream`.
7. El asistente en `MODERATOR_ONLY` sólo muestra el SVG guardado y actualiza
   por SSE, polling y botón flotante.

La instancia controlada no crea una capa WebSocket propia. La documentación
oficial de [Excalidraw](https://docs.excalidraw.com/) y su
[paquete embebible](https://github.com/excalidraw/excalidraw) son la referencia
para el formato de escena y las exportaciones compatibles.

### Requisito visual del editor Excalidraw

El editor controlado necesita una cadena de alturas explícita. No alcanza con
que `.whiteboard-page` use `flex`: el host `editor-shell` y el nodo raíz
`.excalidraw` también deben ocupar el área disponible. La corrección vigente
está en `frontend/web/src/pages/conference/WhiteboardPage.vue`:

```css
.editor-shell {
  flex: 1 1 auto;
  height: calc(100vh - 112px);
  min-height: 480px;
  width: 100%;
  overflow: hidden;
}

.editor-shell :deep(.excalidraw) {
  width: 100%;
  height: 100%;
  min-height: 480px;
}
```

Cuando el moderador ve una pantalla vacía, pero la red muestra los bundles,
fuentes y recursos de Excalidraw con estado `200`, el primer diagnóstico debe
ser el tamaño calculado de estos dos elementos. En DevTools, el host debe
tener un ancho y una altura mayores que cero, y el nodo `.excalidraw` debe
ocupar el host. No se debe interpretar ese síntoma como un fallo de API ni
volver a cargar el editor externo en un iframe.

La vista del asistente es distinta: en `MODERATOR_ONLY` no monta Excalidraw,
sino que muestra `publishedSvg`. Para comprobar que la publicación está
completa, la respuesta de `GET /whiteboard` debe contener una escena con
elementos y un SVG publicado cuyo `viewBox` corresponda al dibujo; un SVG
vacío de `20×20` indica que se guardó la escena inicial y no una modificación
real.

## Flujo de notas Etherpad

1. La configuración del evento guarda una entrada `CanvasConfig` para
   `ETHERPAD`. Si no se selecciona una modalidad, la normalización usa
   `COLLABORATIVE`.
2. `GET /conferences/{id}/notes` valida el token y devuelve un pad ya resuelto.
   En modo grupal el `padId` es el UUID del evento; en modo individual es un
   identificador derivado de evento + usuario + `ETHERPAD_PRIVATE_PAD_SECRET`.
   Ese secreto debe permanecer estable; si no se define, Users usa como
   compatibilidad el valor de `ETHERPAD_API_KEY`.
3. El navegador sólo construye la URL de Etherpad con el `padId` devuelto y el
   token de sesión. Nunca recibe la API key de Etherpad ni puede enviar un
   `padId` arbitrario.
4. `GET /conferences/{id}/notes/export?format=txt|html` vuelve a resolver el
   pad según el token y lee `getText`/`getHTML` desde backend. La respuesta se
   descarga como archivo, no como una copia persistente en SQLite.
5. `GET /conferences/{id}/materials.zip` lee únicamente el pad grupal y agrega
   `moderator/etherpad/source.html`, `export.html` y `export.txt` si tienen
   contenido. Los pads individuales se omiten de forma explícita.
6. `PurgeExpiredEventNotesUseCase` lista y elimina el pad grupal y todos los
   pads con el prefijo privado del evento después de la ventana TTL.

### Diagnóstico de notas

| Síntoma | Comprobación |
|---|---|
| Todas las personas editan el mismo documento cuando se esperaba privacidad | Revisar `canvasConfigs`/`canvasAudienceMode` y confirmar `ETHERPAD: INDEPENDENT`; la configuración se aplica desde Dashboard. |
| Un asistente recibe las notas de otro | Inspeccionar la respuesta de `GET /notes`: en modo individual debe contener un `padId` con `--private--`; no debe existir un `padId` elegido desde el frontend. |
| Exportación vacía o falla | Confirmar que el pad existe, revisar `getText`/`getHTML` del adaptador y que la API key sólo esté configurada en Users. |
| El ZIP no contiene notas | Confirmar que la modalidad es `COLLABORATIVE` y que el pad grupal ya tiene contenido; los pads individuales se excluyen por diseño. |
| Las notas desaparecieron antes de exportarse | Revisar el TTL y los logs de `event-notes-purge-scheduler`; la expectativa es exportar antes de la purga posterior al vencimiento. |

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
| El asistente ve un lienzo vacío | Revisar respuesta de `GET`: `sceneJson.elements` no debe ser `[]` después de dibujar y el SVG no debe ser el export de `20×20` de la escena inicial; confirmar también la respuesta de `PUT`. |
| No llegan cambios | Revisar SSE, luego polling y el botón de refresco; la persistencia debe funcionar aun sin SSE. |
| Drawio muestra `Not a diagram file` | Confirmar que la solicitud usa `action: "snapshot"`, no el export genérico del host. |
| Excalidraw no publica | Confirmar que la instancia controlada dispara `onChange` después de una modificación real (no sólo durante la carga), que el debounce termina y que el JSON y SVG pasan los límites del backend. |
| El moderador ve Excalidraw vacío aunque sus recursos cargan con `200` | Inspeccionar el tamaño computado de `.editor-shell` y `.editor-shell .excalidraw`; ambos deben tener ancho y alto mayores que cero. Conservar `height: calc(100vh - 112px)`, `min-height: 480px` y `height: 100%` en el root de Excalidraw. |
| El asistente ve el estado anterior o una imagen vacía | Consultar `GET /whiteboard`, revisar `version`, `sceneJson.elements` y `publishedSvg`; confirmar un `PUT /whiteboard` posterior al dibujo y que el SSE/polling provoque otra lectura. El dibujo local no recuperable debe volver a realizarse y publicarse. |
| El código funciona local pero el sitio no cambia | Revisar GHCR, la reconciliación de ImagePolicy y el commit generado por Flux en el repositorio GitOps. |

## Procedimiento de recuperación

1. Identificar la identidad: el moderador debe tener el rol de organizador o
   administrador; el asistente debe tener el rol de asistente. El asistente no
   debe recibir la instancia editable.
2. En la vista del moderador, comprobar en DevTools que los assets de
   Excalidraw cargan y que `.editor-shell` y `.excalidraw` tienen dimensiones
   positivas.
3. Dibujar un elemento nuevo y esperar a que termine el debounce. Confirmar
   un `PUT /whiteboard` con `sceneJson` y `publishedSvg`.
4. En la vista del asistente, confirmar `GET /whiteboard` con una versión mayor,
   un `sceneJson.elements` no vacío y un `publishedSvg` visible. Confirmar que
   el stream SSE esté conectado; si no lo está, usar el botón flotante o
   esperar el polling.
5. Si la imagen ya se publicó pero el navegador conserva el bundle anterior,
   hacer una recarga forzada y verificar el hash `latest` mostrado por la
   aplicación.
6. Si funciona localmente pero no en producción, reconciliar Flux y comprobar
   el Deployment antes de volver a probar. El commit de aplicación por sí
   solo no implica que el cluster ya esté ejecutando la corrección.

La regresión de visibilidad quedó corregida en el commit `bcdcc11`; la
protección contra la publicación prematura de una escena vacía quedó en
`248f5b5`. Ambos cambios deben conservarse juntos: el primero hace visible el
editor y el segundo evita que su carga inicial sobrescriba la publicación.
