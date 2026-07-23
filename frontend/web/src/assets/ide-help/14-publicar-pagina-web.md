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

Primero define el evento y tu token de sesión:

```bash
export INSIGHTBLOOM_CONFERENCE_ID="UUID_DEL_EVENTO"
export INSIGHTBLOOM_TOKEN="TOKEN_DE_SESION"
```

Publica el workspace actual, que debe contener `index.html`:

```bash
insightbloom publish
```

También puedes llamar directamente al script:

```bash
insightbloom-publish.py publish
```

Para publicar una carpeta concreta:

```bash
insightbloom publish --root sitio
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
insightbloom revoke PUBLICATION_ID
```

La publicación no es un servidor de desarrollo: no admite APIs, WebSockets,
procesos persistentes ni puertos arbitrarios. Para probar esos servicios usa
el sandbox del IDE; la publicación web está pensada para HTML estático.
