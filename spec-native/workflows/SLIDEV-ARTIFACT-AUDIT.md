# Auditoría de artefactos JavaScript de Slidev

## Estado

Exploratorio. Este documento define el mecanismo de seguridad para evaluar la
aceptación futura de ZIP de Slidev ya compilados. El prototipo ejecutable está
en `backend/services/insightbloom-presentations/tools/audit-slidev-artifact.js`
y se puede probar con `npm run audit:slidev -- archivo.zip --json`.
El endpoint sólo procesa el formato cuando `SLIDEV_FAT_ENABLED=true`; los
warnings se pueden permitir explícitamente con `SLIDEV_FAT_ALLOW_WARNINGS=true`.
La ausencia de esas variables mantiene el formato deshabilitado.

## Conclusión principal

Un análisis estático de un bundle JavaScript no puede demostrar que una
presentación sea segura. El bundle oficial de Slidev ya contiene patrones como
`fetch`, `localStorage`, `innerHTML` e imports dinámicos. Un escáner que bloquee
cualquier coincidencia produciría falsos positivos y un escáner que sólo busque
`eval` no sería suficiente.

Por eso el control debe tener cuatro capas:

```text
ZIP → allowlist estructural → auditoría estática contextual
    → procedencia/firma → runtime aislado con CSP
```

La auditoría reduce riesgo y genera evidencia; el aislamiento evita que un
artefacto comprometido acceda a cookies, API o al DOM de InsightBloom.

## Amenazas consideradas

- JavaScript añadido para leer tokens, cookies o datos del evento.
- Exfiltración mediante `fetch`, `WebSocket`, `sendBeacon`, imágenes o fuentes.
- Escape del iframe mediante `window.top`, `parent`, `opener` o navegación.
- XSS persistente a través de HTML, SVG, `iframe`, `object` o `embed`.
- Service workers, WebAssembly o imports remotos que amplíen la superficie.
- ZIP con traversal, symlinks, archivos ocultos, mapas de fuente o payloads
  sobredimensionados.
- Sustitución de una dependencia legítima por un bundle alterado aunque el
  nombre y la versión declarados parezcan correctos.

## Capa 1: contrato estructural del ZIP

Para el formato `SLIDEV_PREBUILT`, generado por
`just slidev-insightbloom-fat-zip`, el paquete debe contener únicamente:

```text
slidev-artifact.json
dist/index.html
dist/assets/...
exports/presentation.pdf       # opcional
previews/slide-1.png           # opcional, repetido por diapositiva
```

Debe rechazarse antes de extraer o publicar si contiene:

- `source/`, `node_modules/`, `package.json` o configuraciones de build;
- componentes `.vue`, TypeScript, scripts de shell o archivos de proyecto;
- symlinks, rutas absolutas o entradas `../`;
- source maps `.map`, salvo que exista una decisión explícita para guardarlos
  fuera del artefacto público;
- HTML fuera de `dist/`, o cualquier archivo ejecutable fuera de `dist/`;
- referencias a recursos externos que no estén en una allowlist declarada.

El límite de tamaño, número de entradas y bytes descomprimidos debe ser menor o
igual al del upload actual. El manifiesto debe declarar `engine`, versión,
entrada, base relativa, hashes y exportaciones disponibles.

## Capa 2: auditoría estática contextual

El auditor nunca ejecuta el JavaScript. Lee el ZIP en un directorio temporal,
calcula SHA-256 por archivo y produce un informe JSON. Cada hallazgo contiene
regla, archivo, línea aproximada, evidencia, severidad y decisión.

### Hallazgos bloqueantes

| Regla | Patrón | Razón |
|---|---|---|
| `JS-AUTH-001` | `document.cookie`, lectura de tokens, `Authorization` literal | acceso directo a credenciales |
| `JS-ESC-001` | `window.top`, `window.parent`, `opener`, navegación superior | escape del aislamiento |
| `JS-PLAT-001` | `navigator.serviceWorker`, `WebAssembly`, `importScripts` | persistencia o ejecución adicional |
| `JS-IMPORT-001` | `import()` absoluto, remoto o con URL no relativa | carga de código fuera del artefacto |
| `JS-DOM-001` | URL `javascript:` activa | ejecución activa fuera del runtime esperado |
| `JS-SOURCE-001` | source map público o código fuente no permitido | exposición de contenido y bypass de revisión |

El prototipo usa reglas conservadoras sobre texto y sirve para explorar el
flujo y detectar casos obvios. Antes de convertirlo en gate de producción, las
reglas deben migrarse a un parser/lexer ECMAScript; no se debe depender
solamente de `grep`, porque el bundle está minificado y puede contener cadenas
que no representan una llamada ejecutable.

### Hallazgos de advertencia

| Regla | Patrón | Tratamiento |
|---|---|---|
| `JS-EXEC-001` | `eval`, `new Function`, `Function(...)` | comparar contra el baseline oficial de Slidev |
| `JS-NET-001` | `fetch`, XHR, WebSocket, EventSource, `sendBeacon` o URLs externas | validar contra la CSP y el baseline |
| `JS-DOM-002` | `innerHTML`, `outerHTML`, `insertAdjacentHTML` | advertir; bloquear si proviene del código propio de la presentación |
| `JS-DOM-003` | `iframe`, `object`, `embed` o creación dinámica equivalente | revisar origen; no permitir navegación arbitraria |
| `JS-STORE-001` | `localStorage`, `sessionStorage` | advertir; permitir sólo para estado local de Slidev |
| `JS-IMPORT-002` | import dinámico relativo dentro de `dist/assets` | permitir y registrar |
| `JS-NET-002` | WebSocket de sincronización de InsightBloom | permitir sólo hacia la ruta exacta de presentación |
| `JS-EXT-001` | fuentes o imágenes CDN | advertir; preferir assets locales |

El bundle oficial de Slidev debe registrarse como baseline por versión. Los
chunks de runtime que coincidan por hash con ese baseline pueden quedar en
allowlist. Los chunks nuevos o alterados se analizan como código de la
presentación, aunque el archivo se llame `index-*.js`.

## Capa 3: procedencia y firma

La protección más fuerte para un ZIP precompilado es no confiar únicamente en
lo que declara quien lo sube.

El builder autorizado debe generar:

```json
{
  "engine": "slidev",
  "engineVersion": "52.18.0",
  "artifactFormat": "static",
  "buildId": "ci-12345",
  "base": "relative",
  "files": {
    "dist/index.html": "sha256:...",
    "dist/assets/index-abc.js": "sha256:..."
  },
  "signature": {
    "algorithm": "ed25519",
    "keyId": "slidev-builder-production",
    "value": "..."
  }
}
```

InsightBloom debe verificar la firma con una clave pública embebida en la
configuración del servicio. Un ZIP sin firma se puede aceptar sólo en modo
cuarentena o para pruebas, nunca como artefacto público normal.

La firma no sustituye la auditoría: el pipeline que firma debe ejecutar el
auditor sobre la fuente y sobre el resultado final, y conservar el informe como
artefacto de CI.

## Capa 4: aislamiento de ejecución

El artefacto debe servirse desde un origen dedicado, por ejemplo
`slides-static.v1.rafex.cloud`, sin cookies de InsightBloom. El frontend lo
carga en un iframe con:

```html
<iframe sandbox="allow-scripts allow-presentation"></iframe>
```

No se debe añadir `allow-same-origin`, `allow-top-navigation`, `allow-popups` ni
`allow-forms` salvo una necesidad documentada.

La respuesta debe incluir una CSP similar a:

```text
default-src 'none';
base-uri 'none';
object-src 'none';
form-action 'none';
frame-ancestors https://insightbloom.v1.rafex.cloud;
script-src 'self';
style-src 'self' 'unsafe-inline';
img-src 'self' data: blob:;
font-src 'self' data:;
connect-src wss://insightbloom.v1.rafex.cloud;
```

El `connect-src` debe limitarse a la ruta de sincronización autorizada. Las
fuentes, imágenes y favicons externos deben empaquetarse localmente o aprobarse
de forma explícita.

Con este diseño, incluso un bundle que pase por alto el auditor no obtiene las
cookies HttpOnly de InsightBloom ni acceso al DOM padre. La autenticación de
audiencia y del moderador continúa ocurriendo en la aplicación anfitriona y en
el WebSocket, no en el JavaScript subido.

## Decisión del auditor

El endpoint no reemplaza el activo hasta terminar las validaciones. En el flujo
actual, el flag `SLIDEV_FAT_ALLOW_WARNINGS` permite publicar un artefacto con
warnings registrados; en un entorno de alta seguridad debe permanecer apagado
hasta disponer de baseline y firma:

| Resultado | Condición | Acción |
|---|---|---|
| `REJECT` | regla bloqueante, ZIP inválido o firma inválida | eliminar staging y devolver error seguro |
| `QUARANTINE` | sin firma, advertencias nuevas o runtime no conocido | no publicar, salvo que `SLIDEV_FAT_ALLOW_WARNINGS=true` |
| `ACCEPT` | allowlist, auditoría, firma y CSP válidas | publicar atómicamente |

El informe no debe incluir el contenido completo del bundle en logs. Debe
registrar conferencia, proveedor, versión, hash, reglas activadas y decisión.

## Aplicación al ZIP de prueba

`slidev-en-10-minutos-slidev.zip` no sería aceptable tal como está porque mezcla
un `dist/` compilado con `source/`, `package.json`, `vite.config.ts` y
`Counter.vue`. Para probar el auditor se debe crear un ZIP sólo con `dist/` y
el manifiesto.

El comando de prueba no publica ni modifica la presentación:

```bash
cd backend/services/insightbloom-presentations
npm run audit:slidev -- /ruta/a/presentacion.zip --json > audit-report.json
```

`REJECT` devuelve código de salida `1`; `ACCEPT` devuelve `0`. Un resultado con
advertencias queda en `QUARANTINE` para revisión. La propiedad
`signature: not_checked` es intencional: la verificación Ed25519 todavía debe
integrarse con el builder autorizado antes de aceptar artefactos públicos.

El dist de Slidev contiene usos legítimos de `localStorage`, `fetch`,
`innerHTML` e imports dinámicos. Esos hallazgos deben aparecer como advertencias
o compararse contra el baseline oficial; no deben bloquear automáticamente la
presentación.

## Orden recomendado de implementación

1. Implementar el auditor estructural y JSON sin habilitar publicación.
2. Generar el baseline firmado de Slidev `52.18.0`.
3. Ejecutar el auditor sobre el ZIP de prueba y revisar falsos positivos.
4. Separar el origen estático y aplicar CSP/iframe sandbox.
5. Habilitar `SLIDEV_PREBUILT` sólo con firma válida.
6. Añadir pruebas con bundles que intenten leer cookies, hacer egress,
   registrar service workers, escapar del iframe y usar imports remotos.

## Limitaciones explícitas

- Un análisis estático no prueba ausencia de vulnerabilidades.
- Una firma sólo prueba quién construyó el artefacto, no que el contenido sea
  benigno si el pipeline fue comprometido.
- CSP y sandbox son la defensa principal; el auditor es defensa en profundidad.
- Si se quieren aceptar componentes Vue o código arbitrario, se necesita un
  builder/sandbox separado con políticas de red y recursos; no debe relajarse
  este mecanismo en el proceso HTTP principal.
