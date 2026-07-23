# Publicar una página web temporal

El IDE puede publicar una copia estática temporal del workspace. La copia pasa por la auditoría
del backend y se sirve desde un origen aislado; el sandbox vivo nunca queda expuesto directamente.

## Requisitos

- Debe existir un `index.html`.
- CSS, JavaScript, imágenes, fuentes y otros assets locales se pueden publicar.
- `package.json` es opcional: no se ejecuta y no es necesario para publicar.
- No se publican `node_modules`, `.git`, archivos ocultos, secretos ni archivos de configuración de
  build. El servidor vuelve a validar el ZIP aunque se use el botón del Web IDE o el CLI.
- La publicación es temporal y se puede revocar. La duración la controla la configuración del
  servicio; no se puede ampliar desde el sandbox.

## Web IDE

Abre el panel **IDE** y pulsa **Publicar página temporal**. El botón toma una instantánea del
workspace, la envía al backend, muestra la URL aislada y permite copiarla o revocarla.

## CLI IDE (Neovim)

El comando está precargado en la imagen y no instala paquetes:

```bash
# Dentro del sandbox, el evento se detecta automáticamente desde CONFERENCE_UUID.
# En el primer uso solicita usuario y contraseña de forma oculta.
insightbloom login
insightbloom publish
```

El comando requiere una operación. Ejecutar solamente `insightbloom` o
`insightbloom-publish.py` no publica nada y muestra que falta el argumento
`command`. `publish` publica y `revoke` revoca una publicación existente.
También puedes invocar el script directamente:

```bash
insightbloom-publish.py publish --token-prompt
```

Si necesitas usar un token puntual sin guardarlo en la sesión local:

```bash
insightbloom publish --token-prompt
```

También se puede publicar una carpeta concreta:

```bash
insightbloom publish --root dist --token-prompt
```

El UUID del evento ya no se considera una credencial manual dentro del sandbox: la plataforma lo
inyecta como `CONFERENCE_UUID`. La sesión del CLI se guarda fuera del workspace en
`~/.config/insightbloom/session.json` con permisos restrictivos; solo contiene el token y su fecha
de expiración. La contraseña nunca se guarda. Si el token caduca, `publish` y `revoke` solicitan
login nuevamente y repiten la solicitud una sola vez.

## Configuración opcional

No se necesita `package.json`. Si el proyecto tiene una estructura distinta, se puede crear
`insightbloom.json`:

```json
{
  "publish": {
    "root": "dist",
    "entry": "index.html"
  }
}
```

`root` es relativo al workspace. `entry` es una comprobación local; el backend sigue exigiendo un
`index.html` dentro del ZIP. Un `package.json` existente no convierte el proyecto en obligatorio
ni se publica.

## Revocar

```bash
insightbloom revoke PUBLICATION_ID --token-prompt
```

La publicación no es un servidor de desarrollo: no soporta APIs, WebSockets, procesos persistentes
ni puertos arbitrarios. Para probar una API se debe usar el IDE internamente o solicitar después
un flujo separado con allowlist y exposición controlada.
