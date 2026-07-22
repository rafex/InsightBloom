# Runbook: videollamada Jitsi en blanco

## Propósito

Este runbook documenta el fallo de Jitsi que se repitió en producción y el procedimiento para diagnosticarlo sin confundirlo con errores de presentaciones, boletos o WebSocket de otros módulos.

La videollamada se carga desde [`VideoConferencePage.vue`](../../frontend/web/src/pages/conference/VideoConferencePage.vue) mediante la API JavaScript de Jitsi. Según la configuración, el origen puede ser:

- `8x8.vc` para JaaS, con JWT.
- `meet.jit.si` como alternativa pública.

## Incidente documentado

### Síntomas

- `/c/<evento>/video` muestra una pantalla vacía.
- La consola muestra un mensaje parecido a:

  ```text
  Refused to load the script https://8x8.vc/.../external_api.js
  because it violates the Content-Security-Policy directive "script-src ..."
  ```

- Network muestra bloqueado `external_api.js` desde `8x8.vc` o `meet.jit.si`.
- El componente no llega a crear `JitsiMeetExternalAPI`, por lo que el contenedor queda vacío.

### Causa raíz

La CSP de Nginx permitía scripts únicamente desde `'self'` y frames únicamente desde los servicios internos de InsightBloom. Aunque `connect-src` ya permitía HTTPS y WebSocket, eso no autoriza la carga de scripts ni la creación de iframes.

Por lo tanto, la integración fallaba antes de iniciar la sala. No era un problema de JWT, boleto, permisos del evento ni disponibilidad del servidor de vídeo.

### Corrección aplicada

En [`container/frontend/nginx.conf`](../../container/frontend/nginx.conf) se agregaron los orígenes de Jitsi únicamente a `script-src` y `frame-src`:

```text
https://8x8.vc
https://*.8x8.vc
https://meet.jit.si
https://*.meet.jit.si
```

También se agregó `container/frontend/nginx.conf` a las rutas disparadoras de [`publish-web.yml`](../../.github/workflows/publish-web.yml). Sin este cambio, modificar la configuración de Nginx no reconstruye la imagen web y el arreglo no llega al cluster.

Referencias de despliegue del incidente:

- `7daae25` — permite Jitsi en la CSP.
- `2b91ecf` — hace que cambios en `nginx.conf` disparen `publish-web`.
- Imagen validada: `ghcr.io/rafex/insightbloom-web:build-29894233295`.

## Diagnóstico paso a paso

### 1. Confirmar la versión desplegada

Revisar el SHA visible al pie de la aplicación. Si la pestaña muestra un SHA anterior, cerrar la pestaña y abrir de nuevo la ruta de vídeo con una recarga forzada (`Cmd + Shift + R`). Una pestaña antigua puede conservar la aplicación anterior mediante el service worker.

En Kubernetes:

```bash
KUBECONFIG=/ruta/al/kubeconfig kubectl -n insightbloom \
  get pod -o custom-columns='NAME:.metadata.name,IMAGE:.spec.containers[0].image,READY:.status.containerStatuses[0].ready' \
  | rg 'web|NAME'
```

El pod debe usar la imagen web correspondiente al último despliegue.

### 2. Revisar la CSP efectiva

```bash
curl -fsSI https://insightbloom.v1.rafex.cloud/c/<evento>/video \
  | rg -i 'content-security-policy|HTTP/'
```

La respuesta debe incluir `8x8.vc` y `meet.jit.si` en `script-src` y `frame-src`.

### 3. Probar que la API JavaScript está disponible

```bash
curl -fsSI https://8x8.vc/<app-id>/external_api.js
curl -fsSI https://meet.jit.si/external_api.js
```

La respuesta esperada es `200` y `content-type: application/javascript`. Un `200` aquí no elimina una CSP incorrecta: el navegador puede bloquear el recurso después de recibirlo.

### 4. Revisar DevTools

En Network filtrar por `external_api.js`:

| Resultado | Interpretación |
|---|---|
| Bloqueado por CSP | Falta el origen en `script-src` o el pod web todavía usa una imagen anterior. |
| 200, pero `JitsiMeetExternalAPI` no existe | Error de ejecución o recurso incorrecto; revisar Console. |
| 403 en `jaas-token` | Revisar sesión, rol, bloqueo de dispositivo y configuración JaaS; no hacer fallback público si el bloqueo es intencional. |
| 200 y API creada, pero iframe vacío | Revisar `frame-src`, errores del iframe y conectividad de Jitsi. |

### 5. Separar errores ajenos a Jitsi

Los mensajes de `/api/presentations/.../presentation/ws/audience` pertenecen al seguimiento en vivo de presentaciones. Pueden quedar visibles en DevTools si la consola conserva mensajes de una ruta anterior. Limpiar la consola y repetir la prueba desde `/video` antes de atribuirlos a Jitsi.

Abrir directamente un endpoint protegido de presentaciones sin `Authorization` o cookie puede devolver `ticket_required`; ese comportamiento es esperado y no diagnostica la videollamada.

## Checklist antes de cerrar un cambio de Jitsi

- [ ] `external_api.js` no aparece bloqueado por CSP.
- [ ] La CSP de producción contiene los cuatro orígenes de Jitsi en `script-src` y `frame-src`.
- [ ] `publish-web.yml` incluye `container/frontend/nginx.conf` en `paths`.
- [ ] CI pasa y `publish-web` publica una imagen nueva.
- [ ] Flux selecciona la etiqueta nueva y el pod web queda `Ready`.
- [ ] Se prueba `/c/<evento>/video` con una pestaña nueva y recarga forzada.
- [ ] Se prueba tanto JaaS (`8x8.vc`) como el fallback público (`meet.jit.si`) cuando ambos estén configurados en el ambiente.
- [ ] Se confirma que el error observado no corresponde a otro módulo por la URL y el iniciador mostrados en DevTools.

## Regla de seguridad

No reemplazar esta corrección por `script-src https:` o `frame-src https:` globales. La CSP debe mantener una allowlist explícita para Jitsi y conservar `object-src 'none'`, `base-uri 'self'` y el resto de restricciones existentes.
