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

El certificado de asistencia se previsualiza mediante una URL `blob:` dentro de
un `iframe`. Si la consola muestra un mensaje `frame-src` que enumera dominios
de Jitsi pero identifica como recurso bloqueado una URL `blob:`, no es un fallo
de Jitsi: es la CSP del frontend sin `blob:` en `frame-src`. La política debe
permitir `blob:` para esa previsualización, sin convertir `frame-src` en una
allowlist global de cualquier origen.

Abrir directamente un endpoint protegido de presentaciones sin `Authorization` o cookie puede devolver `ticket_required`; ese comportamiento es esperado y no diagnostica la videollamada.

## Acceso restringido por boleto

### Invitación personalizada de InsightBloom

La URL que debe configurarse como base de invitación en JaaS es:

```text
https://insightbloom.v1.rafex.cloud/jitsi
```

JaaS puede mostrar una URL con el identificador privado de la sala (`vpaas-magic-cookie-...`).
Esa URL no debe compartirse como mecanismo de acceso de asistentes. La integración configura
`brandingRoomAlias` con el `friendlyId` del evento para que la invitación vuelva a
`/jitsi/<evento>`.

La ruta propia realiza este flujo:

1. Si no existe una sesión de usuario, muestra una página de error con enlaces para iniciar
   sesión o crear una cuenta.
2. El frontend llama a
   `GET /api/users/api/v1/conferences/by-friendly/<evento>/jitsi-access` con el Bearer token.
3. El backend resuelve el evento, comprueba que tenga `VIDEO_CONFERENCE` y valida el boleto
   operativo/vigente mediante `TicketUseCase.hasAccess`.
4. Sólo si la respuesta es `200` se navega a `/c/<evento>/video`, que vuelve a validar el acceso
   antes de solicitar el JWT de JaaS.
5. Si falta el boleto, la pantalla muestra `ticket_required`; si el evento no existe o no tiene
   videollamada, no se carga ningún iframe de JaaS.

El endpoint de acceso no devuelve el JWT. Tampoco se acepta un token de invitado (`guest`). El
creador y los moderadores asignados pasan por la misma regla porque reciben boletos operativos
contados y protegidos contra revocación.

Dentro del iframe se oculta el botón `Invite more people`. Esto reduce el riesgo de que alguien
copie el enlace del proveedor. No sustituye la validación backend: un enlace directo al proveedor
siempre debe considerarse no autorizado para asistentes.

Copiar el enlace de una sala pública de `meet.jit.si` no puede quedar restringido
por InsightBloom: el enlace salta el frontend y Jitsi público no conoce los
boletos del evento. Una contraseña fija visible en el dashboard tampoco es una
frontera suficiente; puede copiarse, reenviarse y permanecer válida después de
que se revoque un boleto.

La estrategia recomendada para producción es:

1. Usar JaaS (`8x8.vc`) o una instancia Jitsi propia con autenticación JWT.
2. Antes de emitir el JWT, validar sesión, evento, capacidad de videollamada,
   boleto operativo vigente y control de dispositivo. El creador recibe un
   boleto operativo contado y los moderadores asignados consumen otro; esos
   boletos no se pueden revocar.
3. Emitir un JWT corto y limitado literalmente a la sala de ese evento; el
   JWT del moderador lleva `moderator=true` y el de un asistente no.
4. No exponer el enlace crudo de Jitsi como enlace de acceso; el enlace público
   debe llevar a `/jitsi/<evento>`, que solicita el token por usuario y luego entra a
   `/c/<evento>/video`.
5. En eventos ticketed, no usar `meet.jit.si` como fallback silencioso cuando
   JaaS no esté configurado: mostrar que la videollamada no está disponible o
   configurar el proveedor seguro.

El endpoint de JaaS firma una sala concreta, distingue al moderador e incorpora
la validación de boleto antes de emitir el JWT. La clave compartida
solo tendría sentido como credencial adicional del anfitrión, nunca como
autorización principal de asistentes, y no debe aparecer en URLs ni en el JWT
del público.

Para una instancia propia de Jitsi, la alternativa equivalente es autenticación
JWT más lobby/espera de moderador. Debe configurarse en Prosody/Jitsi, no solo
en `configOverwrite` del iframe; cambiar el nombre de sala o esconder el botón
de compartir no constituye control de acceso.

## Errores de Amplitude

Los mensajes sobre `https://api2.amplitude.com/2/httpapi`, `NetworkError` y
`exceeded retry count` proceden de la telemetría del cliente de Jitsi/JaaS.
Suelen aparecer por bloqueadores de privacidad, DNS, red corporativa o una
política CORS del tercero. No son la autorización de la sala ni la causa de
que el iframe no cargue. InsightBloom no puede corregir el CORS de Amplitude
desde su backend; tampoco conviene abrir más `connect-src` para ocultarlo.

Si se desea cero ruido en consola, la vía correcta es desactivar u optar por la
telemetría desde la configuración soportada por el proveedor de Jitsi/JaaS. Si
el proveedor no ofrece esa opción para el despliegue, se debe tratar como
telemetría best-effort y verificar funcionalidad con los errores filtrados por
`api2.amplitude.com`.

## Checklist antes de cerrar un cambio de Jitsi

- [ ] `external_api.js` no aparece bloqueado por CSP.
- [ ] La CSP de producción contiene los cuatro orígenes de Jitsi en `script-src` y `frame-src`.
- [ ] `publish-web.yml` incluye `container/frontend/nginx.conf` en `paths`.
- [ ] CI pasa y `publish-web` publica una imagen nueva.
- [ ] Flux selecciona la etiqueta nueva y el pod web queda `Ready`.
- [ ] Se prueba `/c/<evento>/video` con una pestaña nueva y recarga forzada.
- [ ] Se prueba JaaS (`8x8.vc`) y se confirma que el fallback público solo se usa en eventos sin boletos.
- [ ] En eventos ticketed, el endpoint de JaaS rechaza a un usuario autenticado sin boleto.
- [ ] El creador y los moderadores asignados reciben boletos operativos contados y no revocables.
- [ ] En producción ticketed no existe fallback silencioso a una sala pública.
- [ ] JaaS tiene configurada como base de invitación `https://insightbloom.v1.rafex.cloud/jitsi`.
- [ ] `/jitsi/<evento>` devuelve error sin sesión, con `ticket_required` sin boleto y redirige a
      `/c/<evento>/video` sólo con acceso autorizado.
- [ ] La interfaz embebida no muestra `Invite more people`.
- [ ] Se confirma que el error observado no corresponde a otro módulo por la URL y el iniciador mostrados en DevTools.

## Regla de seguridad

No reemplazar esta corrección por `script-src https:` o `frame-src https:` globales. La CSP debe mantener una allowlist explícita para Jitsi y conservar `object-src 'none'`, `base-uri 'self'` y el resto de restricciones existentes.
