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

Dentro del sandbox, el CLI ya está configurado para usar el API interno de InsightBloom. No se
requiere habilitar Internet ni exportar `INSIGHTBLOOM_API_BASE_URL`, `INSIGHTBLOOM_CONFERENCE_ID` o
`INSIGHTBLOOM_TOKEN`: el evento se identifica con `CONFERENCE_UUID` y la capability se guarda fuera
del workspace.

El comando necesita un subcomando. Ejecutar solo
`insightbloom-publish.py` muestra el error `the following arguments are
required: command`; eso significa que falta indicar la operación.

El botón **Publicar página temporal** del Web IDE ya envía la sesión automáticamente. Si usás el
terminal, la plataforma inyecta el UUID del evento en `CONFERENCE_UUID` y entrega una capability
corta en `~/.config/insightbloom/sandbox-token`; el CLI puede publicar sin copiar credenciales al
workspace:

```bash
insightbloom login
```

Después publica el workspace actual, que debe contener `index.html`:

```bash
insightbloom publish
```

Si aparece `Connection refused`, recrea el sandbox desde el Dashboard después de guardar tu
workspace; los Pods existentes no reciben automáticamente las nuevas variables y políticas de
red. El comando correcto es `publish`, no `publis`.

También puedes llamar directamente al script:

```bash
insightbloom-publish.py publish --token-prompt
```

Para introducir el token sin mostrarlo en pantalla, ni guardarlo en variables o en el historial:

```bash
insightbloom publish --token-prompt
```

La capability se guarda fuera del workspace en `~/.config/insightbloom/sandbox-token` con permisos
`0600` y solo permite publicar el sandbox asignado. Si no existe o caduca, el CLI usa la sesión de
`~/.config/insightbloom/session.json`; las sesiones OTP conservan `authMethod: "otp_email"` y no
solicitan contraseña al renovar. Para usar un token puntual sin guardar sesión, utiliza
`--token-prompt`. Fuera de un sandbox se puede usar `--conference-id UUID_DEL_EVENTO`.

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

Esta publicación es una copia estática: no admite APIs, WebSockets ni procesos persistentes. Para
publicar un backend/API vivo, seguí leyendo.

## Publicar backend/API (proceso vivo)

A diferencia de lo anterior, esto expone tu proceso corriendo de verdad dentro del sandbox — sirve
para que alguien de afuera pruebe una API que estás desarrollando mientras la tenés levantada.

### El puerto: `$APP_PORT`

Tu servidor tiene que escuchar en el puerto de la variable de entorno `APP_PORT` (no elijas un
puerto fijo vos mismo). Revisá el valor con:

```bash
echo $APP_PORT
```

En Java, leelo con `System.getenv("APP_PORT")`; en Python, con `os.environ.get("APP_PORT")`; en
Node.js/JavaScript, con `process.env.APP_PORT`. Ver ejemplos completos en las pestañas
"Hello World: Java", "Hello World: Python" y "Hello World: JS/TS" de este panel.

### Publicar

```bash
insightbloom login   # si todavía no iniciaste sesión
insightbloom app-publish
```

La salida incluye la URL pública, un `accessToken` y un ejemplo de `curl` listo para copiar. Quien
consuma la URL necesita mandar el token en cada request:

```bash
curl -H "X-Preview-Token: TU_TOKEN" "https://app-insightbloom.v1.rafex.cloud/p/TU_PUBLICATION_ID/tu-ruta"
```

Solo hay una publicación de backend/API activa por sandbox: publicar de nuevo reemplaza la
anterior (la URL y el token viejos dejan de funcionar). La publicación expira automáticamente; si
querés cortarla antes:

```bash
insightbloom app-revoke PUBLICATION_ID
```
