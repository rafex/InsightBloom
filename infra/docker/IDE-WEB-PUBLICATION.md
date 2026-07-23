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
export INSIGHTBLOOM_CONFERENCE_ID="UUID_DEL_EVENTO"
export INSIGHTBLOOM_TOKEN="TOKEN_DE_SESION"
insightbloom publish
```

El comando requiere una operación. Ejecutar solamente `insightbloom` o
`insightbloom-publish.py` no publica nada y muestra que falta el argumento
`command`. `publish` publica y `revoke` revoca una publicación existente.
También puedes invocar el script directamente:

```bash
insightbloom-publish.py publish
```

Para no dejar el token en el historial:

```bash
read -r INSIGHTBLOOM_TOKEN
export INSIGHTBLOOM_TOKEN
insightbloom publish --conference-id "UUID_DEL_EVENTO"
```

También se puede publicar una carpeta concreta:

```bash
insightbloom publish --root dist
```

El token y el UUID son credenciales de sesión; no los guardes en el workspace ni en
`insightbloom.json`. El CLI no obtiene credenciales por sí solo: el botón del Web IDE es la opción
recomendada cuando se quiere publicar sin copiar el token al terminal.

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
insightbloom revoke PUBLICATION_ID
```

La publicación no es un servidor de desarrollo: no soporta APIs, WebSockets, procesos persistentes
ni puertos arbitrarios. Para probar una API se debe usar el IDE internamente o solicitar después
un flujo separado con allowlist y exposición controlada.
