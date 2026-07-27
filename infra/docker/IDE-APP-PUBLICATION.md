# Publicar un backend/API en vivo

A diferencia de la publicación de página web (ver `IDE-WEB-PUBLICATION.md`), esto **no** es una
copia estática: expone tu proceso vivo, corriendo dentro del sandbox, a través de una URL pública
que el gateway proxea en tiempo real. Sirve para que alguien de afuera (un compañero, un
evaluador, Postman) pruebe una API REST que estás desarrollando, mientras la tenés corriendo.

## Requisitos

- Tu servidor debe escuchar en el puerto de la variable de entorno `$APP_PORT` (revisa el valor
  con `env | grep APP_PORT`), no en un puerto fijo elegido por vos.
- Solo una publicación activa por sandbox: publicar de nuevo reemplaza la anterior (la URL y el
  token viejos dejan de funcionar).
- La publicación tiene una expiración; después de ese tiempo la URL deja de responder aunque tu
  proceso siga corriendo. También podés revocarla antes manualmente.

## Acceso: token, no sesión

Quien consuma la URL pública **no necesita** una sesión de InsightBloom, pero sí necesita el
`accessToken` que se genera al publicar, enviado en cada request como header:

```
X-Preview-Token: <el token que te dio insightbloom app-publish>
```

Cualquiera que tenga la URL *y* el token puede usar tu API mientras la publicación esté activa.
No compartas el token en un canal público si no querés que cualquiera lo use.

## Web IDE

Abre el panel **IDE** y pulsa **Publicar backend/API**. Muestra la URL, el token de acceso y la
fecha de expiración, con botones para copiar cada uno y para revocar.

## CLI IDE (Neovim)

```bash
# Dentro del sandbox, el evento se detecta automáticamente desde CONFERENCE_UUID.
insightbloom login   # si todavía no iniciaste sesión
insightbloom app-publish
```

La salida incluye la URL, el `accessToken`, y un ejemplo de `curl` listo para copiar:

```bash
curl -H "X-Preview-Token: <token>" https://app-insightbloom.v1.rafex.cloud/p/<publicationId>/...
```

Para revocar:

```bash
insightbloom app-revoke PUBLICATION_ID
```

## Diferencia con "Publicar página temporal"

| | `publish` (página web) | `app-publish` (backend/API) |
|---|---|---|
| Qué expone | Una copia estática (snapshot), auditada por el servidor | Tu proceso vivo, tal cual está corriendo |
| Requiere | `index.html` en el workspace | Un proceso escuchando en `$APP_PORT` |
| Acceso | URL pública, sin token | URL pública + `X-Preview-Token` |
| WebSockets/APIs con estado | No soportado | Sí, es tu proceso real respondiendo |

## Red del sandbox

Publicar un backend no abre ni cambia reglas de red de *entrada* a internet — solo agrega una
ruta de *salida* controlada por el gateway hacia tu puerto. El acceso a internet desde tu proceso
(si tu backend llama APIs externas) sigue sujeto a la NetworkPolicy/proxy de egress del evento,
igual que el resto del sandbox.
