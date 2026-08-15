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

El sandbox recibe una ruta interna y restringida al API de InsightBloom para el login y la
publicación. No necesitas habilitar Internet ni copiar `INSIGHTBLOOM_API_BASE_URL`,
`INSIGHTBLOOM_CONFERENCE_ID` o `INSIGHTBLOOM_TOKEN`; el UUID del evento ya se inyecta como
`CONFERENCE_UUID`; la capability de publicación se guarda fuera del workspace en
`~/.config/insightbloom/sandbox-token`.

```bash
# Dentro del sandbox, el evento se detecta automáticamente desde CONFERENCE_UUID.
# Dentro del sandbox usa la capability instalada automáticamente. Fuera del sandbox solicita
# credenciales de forma oculta; si la cuenta tiene OTP, solicita el código enviado por correo.
insightbloom login
insightbloom publish
```

Si tu cuenta está configurada para enviar un código por correo, puedes indicar el método desde
el inicio para evitar el prompt de contraseña:

```bash
insightbloom login --otp
insightbloom publish --otp
```

El modo `--otp` también está disponible en `revoke`, `app-publish`, `app-revoke`,
`container-publish` y `container-revoke`.

Si al ejecutar `login` o `publish` aparece `Connection refused`, el sandbox fue creado con una
imagen o configuración anterior. Guarda tus archivos y pulsa **Recrear** en el Dashboard para que
reciba la ruta interna y las políticas de red actuales. `publis` no es un subcomando válido; la
operación correcta es `publish`.

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
inyecta como `CONFERENCE_UUID`. La capability y la sesión del CLI se guardan fuera del workspace con
permisos restrictivos. La capability solo permite publicar el sandbox, conferencia y asiento que
la recibieron; la contraseña y el código OTP nunca se guardan. Si caduca, una sesión iniciada con
`--otp` vuelve a solicitar código sin pedir contraseña.

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

Esta publicación es una copia estática: no soporta APIs, WebSockets ni procesos persistentes. Para
publicar un backend/API vivo, escuchando en `$APP_PORT`, usa `insightbloom app-publish` — ver
`IDE-APP-PUBLICATION.md`.
