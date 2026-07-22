# Presentaciones offline del moderador

## Objetivo

El moderador puede preparar una presentación para usarla sin conexión en el
mismo navegador. El modo offline no cambia el modo público ni convierte el
service worker PWA global en una caché de presentaciones.

El flujo es:

```text
moderador autenticado
  → manifiesto firmado por Presentations
  → descarga autorizada de archivos
  → hash SHA-256 de cada archivo
  → fragmentos de 1 MiB cifrados con AES-GCM
  → IndexedDB + CryptoKey no extraíble
  → service worker con scope aleatorio
  → visor local hasta la expiración
```

## Configuración obligatoria

Se necesitan dos mitades de la misma identidad Ed25519:

- `OFFLINE_MANIFEST_PRIVATE_KEY` en el servicio backend
  `insightbloom-presentations`. Es una clave PEM privada y debe vivir en el
  secreto de runtime del despliegue, nunca en Git ni en el frontend.
- `OFFLINE_MANIFEST_PUBLIC_KEY` como secreto de Actions usado por
  `publish-web.yml`. Debe ser la clave pública SPKI DER codificada en Base64,
  sin encabezados PEM. Vite la embebe como
  `VITE_OFFLINE_MANIFEST_PUBLIC_KEY` durante el build.

Para generar una pareja nueva:

```bash
openssl genpkey -algorithm Ed25519 -out offline-manifest-private.pem
openssl pkey -in offline-manifest-private.pem -pubout -outform DER \
  | base64 | tr -d '\\n'
```

El segundo comando produce el valor de `OFFLINE_MANIFEST_PUBLIC_KEY`. La
privada debe entregarse al gestor de secretos como contenido PEM, conservando
los saltos de línea. Rotar la pareja invalida las firmas de manifiestos
anteriores; hacerlo sólo cuando se hayan limpiado los paquetes offline de los
moderadores.

`OFFLINE_PRESENTATION_TTL_MS` es opcional. El backend lo limita a entre una
hora y siete días; por defecto es 24 horas. Es la vigencia máxima local del
paquete, no una forma de extender la vigencia del evento ni del usuario.

## Autorización y aislamiento

`GET /api/v1/conferences/:id/presentation/offline-manifest` requiere el mismo
permiso de administrar la presentación que una subida. El endpoint sólo
devuelve metadatos, hashes, expiración y firma; nunca devuelve el contenido.

El navegador descarga los archivos usando el acceso normal de presentación y
los transforma localmente. El service worker sólo intercepta rutas de la forma:

```text
/offline-presentations/<packageId>/presentation/<archivo>
```

`<packageId>` es aleatorio por preparación. No hay handler global de `fetch`,
no se cachean respuestas en Workbox y el contenido queda fuera de las rutas
públicas normales. La vista normal conserva su control de boleto, rol y
cookies.

## Integridad y expiración

- El manifiesto se firma con Ed25519 y el frontend verifica la firma antes de
  descargar.
- Cada archivo se comprueba contra el SHA-256 recibido antes y después de
  cifrarlo.
- Cada fragmento usa un IV aleatorio AES-GCM de 96 bits.
- El service worker vuelve a validar la expiración y el hash antes de servir un
  archivo.
- La comparación de tiempo `now < expiresAt` usa el módulo WASM confiable de
  InsightBloom (`public/offline-integrity.wasm`). El WASM no proviene del ZIP
  de Slidev y no debe reemplazarse por un archivo subido por usuarios.

## Operación en la interfaz

En `Presentar`, el moderador autenticado ve:

1. `Preparar offline`: descarga, verifica y cifra el paquete.
2. `Abrir offline`: abre la copia almacenada en el dispositivo.
3. Si la página carga sin red, intenta abrir automáticamente el paquete
   vigente del mismo `conferenceId` y `userUuid`.

La navegación local funciona, pero no hay sincronización WebSocket con la
audiencia mientras no exista red. Al volver online, el moderador debe abrir de
nuevo el modo normal para reconectar el seguimiento de audiencia.

## Límites de seguridad

Esto reduce exposición accidental y permite revocar por tiempo; no es DRM.
Un administrador del dispositivo o un proceso con control del perfil del
navegador puede inspeccionar la memoria, IndexedDB, DevTools o capturar la
pantalla. La `CryptoKey` no extraíble evita exportar directamente la clave
mediante la Web Crypto API, pero no protege contra un usuario que controla el
navegador mientras la presentación está desbloqueada.

Por esa razón:

- sólo se habilita para moderadores autorizados;
- no se permite contenido subido que registre otro service worker o WASM;
- no se debe almacenar el token de sesión dentro del paquete;
- la clave privada nunca se compila en frontend ni se registra en logs;
- el paquete debe eliminarse al cerrar el evento o al revocar la sesión, como
  tarea operativa futura si se requiere borrado remoto inmediato.

## Diagnóstico

Errores esperados:

| Error | Causa | Acción |
|---|---|---|
| `offline_not_configured` | falta la clave privada del backend o la pública del frontend | configurar ambos secretos y reconstruir web/backend |
| `offline_manifest_signature_invalid` | parejas de claves distintas o manifiesto alterado | verificar secretos y hash de la imagen desplegada |
| `offline_service_worker_unsupported` | navegador sin Service Worker/IndexedDB | usar navegador compatible y contexto HTTPS |
| `offline_presentation_expired` | TTL vencido | preparar un paquete nuevo con conexión |
| `offline package locked` | el worker todavía no recibió la CryptoKey | recargar y pulsar `Abrir offline` |
| pantalla vacía | asset faltante o base de URLs no reescrita | revisar hashes del manifiesto y que los assets sean locales |

Pruebas locales:

```bash
node - <<'NODE'
const fs = require('fs')
WebAssembly.instantiate(fs.readFileSync('frontend/web/public/offline-integrity.wasm'))
  .then(({ instance }) => {
    if (instance.exports.is_valid_until(1n, 2n) !== 1) process.exit(1)
    if (instance.exports.is_valid_until(2n, 1n) !== 0) process.exit(1)
    console.log('offline-integrity.wasm OK')
  })
NODE
npm --prefix frontend/web run typecheck
npm --prefix frontend/web run lint
npm --prefix backend/services/insightbloom-presentations run test:audit:slidev
```
