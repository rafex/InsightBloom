# Publicar una página web

Puedes publicar una copia temporal y segura de tu sitio estático para probarlo
desde fuera del IDE. La publicación es una instantánea del workspace: no
expone el sandbox, no abre sus puertos y no ejecuta `package.json`.

## Requisitos

- Debe existir un `index.html` dentro de la carpeta que vas a publicar.
- Puedes usar CSS, JavaScript, imágenes y fuentes locales.
- `package.json` es opcional. No necesitas instalar dependencias para publicar.
- La publicación es temporal y la URL se puede revocar.

Antes de publicar revisa que no hayas dejado contraseñas, tokens, claves SSH o
archivos `.env` en el sitio. El servidor vuelve a auditar el ZIP y excluye
metadatos de build, pero nunca publiques secretos intencionalmente.

## Desde el Web IDE

Pulsa **🌐 Publicar página temporal** en la pantalla del IDE. Se mostrará una
URL pública temporal que puedes abrir o copiar. También podrás revocarla desde
el resultado de la publicación.

## Desde el terminal CLI

El comando necesita un subcomando. Ejecutar solo
`insightbloom-publish.py` muestra el error `the following arguments are
required: command`; eso significa que falta indicar la operación.

El botón **Publicar página temporal** del Web IDE ya envía la sesión automáticamente. Si usás el
terminal, la plataforma inyecta el UUID del evento en `CONFERENCE_UUID` y el CLI puede iniciar
sesión sin copiar credenciales al workspace:

```bash
insightbloom login
```

Después publica el workspace actual, que debe contener `index.html`:

```bash
insightbloom publish
```

También puedes llamar directamente al script:

```bash
insightbloom-publish.py publish --token-prompt
```

Para introducir el token sin mostrarlo en pantalla, ni guardarlo en variables o en el historial:

```bash
insightbloom publish --token-prompt
```

El token se guarda únicamente fuera del workspace en `~/.config/insightbloom/session.json` y la
contraseña nunca se almacena. Si el token caduca, el comando solicita login de nuevo y reintenta
una sola vez. Para usar un token puntual sin guardar sesión, utiliza `--token-prompt`. Fuera de un
sandbox se puede usar `--conference-id UUID_DEL_EVENTO`; dentro del IDE no hace falta.

Para publicar una carpeta concreta:

```bash
insightbloom publish --root sitio --token-prompt
```

El resultado incluye el `publicationId`, la URL temporal, la fecha de
expiración y el hash del artefacto publicado.

## Configuración opcional

Si tu sitio está dentro de una carpeta, puedes crear `insightbloom.json` en la
raíz del workspace:

```json
{
  "publish": {
    "root": "sitio",
    "entry": "index.html"
  }
}
```

`root` siempre es relativo al workspace. `entry` sirve como comprobación local;
el servidor seguirá exigiendo un `index.html` válido dentro del ZIP.

## Revocar una publicación

Usa el `publicationId` que devolvió el comando:

```bash
insightbloom revoke PUBLICATION_ID --token-prompt
```

La publicación no es un servidor de desarrollo: no admite APIs, WebSockets,
procesos persistentes ni puertos arbitrarios. Para probar esos servicios usa
el sandbox del IDE; la publicación web está pensada para HTML estático.
