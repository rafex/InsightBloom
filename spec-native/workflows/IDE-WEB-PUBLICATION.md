# Publicación temporal de páginas desde el IDE

## Objetivo

Permitir que un alumno publique el estado actual de su workspace para probar
una página web sin exponer el sandbox vivo, sus credenciales ni sus puertos
internos.

El flujo copia el workspace a una publicación estática temporal:

```text
IDE asignado
    ↓ ZIP interno del workspace
users: autorización del alumno y evento
    ↓
presentations: auditoría + extracción atómica
    ↓
https://preview-insightbloom.v1.rafex.cloud/p/<publication-id>/
```

## Controles de seguridad

Antes de publicar, `insightbloom-presentations` inspecciona el ZIP completo:

- máximo 1000 entradas, 250 MiB descomprimidos y 25 MiB por archivo;
- rechazo de rutas absolutas, `..`, symlinks y extensiones no estáticas;
- rechazo de secretos; `package.json`, lockfiles y configuraciones de build pueden existir en el
  workspace, pero se excluyen del artefacto público;
- exactamente un `index.html` en el artefacto;
- rechazo de `iframe`, `object`, `embed`, `portal`, `base`, formularios,
  handlers inline (`onclick`, etc.) y esquemas activos;
- rechazo en JavaScript de acceso a `document.cookie`, `window.parent`,
  `window.opener`, `document.domain`, Service Worker, `importScripts`,
  WebAssembly, `eval` y `new Function`;
- SVG sanitizado antes de guardarlo;
- hash SHA-256 por archivo y hash compuesto del artefacto;
- extracción en `DATA_DIR/tmp` y publicación mediante rename atómico;
- CSP propia del host publicado, sin `frame-src`, sin `connect-src` externo y
  con scripts y estilos limitados al mismo origen;
- `Cache-Control: no-store`, `X-Content-Type-Options: nosniff` y
  `Cross-Origin-Resource-Policy: cross-origin`;
- TTL de 5 minutos a 24 horas, por defecto 1 hora, con limpieza periódica;
- revocación explícita validando conferencia y propietario.

La URL es una capability temporal: quien la conozca puede verla hasta que
expire o sea revocada. No se debe usar para información privada permanente.

## Contrato interno

`users` expone al usuario autenticado:

```text
POST   /api/users/api/v1/conferences/{conferenceId}/sandbox/preview
DELETE /api/users/api/v1/conferences/{conferenceId}/sandbox/preview/{publicationId}
```

El primer endpoint requiere sesión válida, la capacidad `CODE_IDE` y un
sandbox asignado al usuario en ese evento. `users` descarga el ZIP del sandbox
mediante el orquestador interno y lo envía a:

```text
POST /internal/v1/previews
X-Internal-Api-Key: ...
X-Conference-Id: ...
X-Owner-Id: ...
X-Expires-In-Seconds: ...
Content-Type: application/zip
```

La publicación se devuelve con `publicationId`, `url`, `expiresAt`,
`artifactHash` y el número de archivos auditados.

## Uso desde los IDE

El mismo flujo está disponible desde el terminal de ambos IDE mediante el comando precargado
`insightbloom publish`. No se exige `package.json`: el comando toma una carpeta que contenga
`index.html`, empaqueta únicamente archivos regulares y deja al backend la auditoría definitiva.
La configuración opcional `insightbloom.json` puede declarar `publish.root` y `publish.entry`.
La guía de uso se copia al iniciar cada sandbox como
`.insightbloom/IDE-WEB-PUBLICATION.md`.

El CLI exige un subcomando: `publish` para crear la publicación o `revoke` para revocarla.
Ejecutar `insightbloom-publish.py` sin argumentos solo muestra la ayuda y el error de argumento
faltante; no es un fallo de la publicación.

```bash
export INSIGHTBLOOM_CONFERENCE_ID="UUID_DEL_EVENTO"
export INSIGHTBLOOM_TOKEN="TOKEN_DE_SESION"
insightbloom publish
insightbloom publish --root sitio
insightbloom revoke PUBLICATION_ID
```

## Configuración de despliegue

La única configuración funcional vive en
`InsightBloom-gitops/infrastructure/charts/insightbloom/values.yaml`:

```yaml
services:
  users:
    env:
      WORKSPACE_PREVIEW_TTL_SECONDS: "3600"
  presentations:
    env:
      PREVIEW_PUBLIC_BASE_URL: https://preview-insightbloom.v1.rafex.cloud/p
      PREVIEW_TTL_SECONDS: "3600"
```

El Ingress de `preview-insightbloom.v1.rafex.cloud` termina en el frontend;
Nginx reenvía solo `/p/` al servicio de presentaciones. Así se reutiliza la
NetworkPolicy del frontend y no se abre directamente el backend al tráfico
externo.

## Qué es Caddy

Caddy es otro servidor web/reverse proxy, similar a Nginx o HAProxy. Puede
terminar TLS automáticamente, servir archivos estáticos, aplicar headers y
reenviar tráfico. No se agrega en este flujo: InsightBloom ya usa HAProxy
Ingress para entrada de Kubernetes y Nginx en el frontend para el proxy
interno. Añadir Caddy aquí duplicaría responsabilidades y aumentaría la
superficie operativa sin aportar un control de seguridad necesario.

## Pruebas de regresión

```bash
cd backend/services/insightbloom-presentations
node --check server.js
npm test

cd ../../../frontend/web
npm run typecheck
npm run build

cd ../../../../InsightBloom-gitops
helm lint infrastructure/charts/insightbloom
helm template insightbloom infrastructure/charts/insightbloom >/tmp/insightbloom-rendered.yaml
```

Casos mínimos a probar en producción:

1. publicar un ZIP con `index.html` y `app.js` relativo;
2. rechazar `.env`, symlink, `iframe`, `document.cookie` y `javascript:`;
3. abrir la URL desde una pestaña nueva;
4. revocar y comprobar HTTP 404;
5. esperar el TTL y comprobar HTTP 410;
6. confirmar que el enlace no da acceso al IDE ni a archivos fuera de la raíz
   publicada.
