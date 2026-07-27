# Desplegar una API REST

Guía paso a paso para levantar una API con dos endpoints, probarla dentro del sandbox y
publicarla para que alguien de afuera la consuma. Si ya conocés el flujo y solo buscás la
referencia de comandos, mirá la pestaña "🌐 Publicar página web" (sección "Publicar backend/API").
Para el código base de un único endpoint en cada lenguaje, mirá las pestañas "Hello World: Java",
"Hello World: Python" y "Hello World: JS/TS".

Este ejemplo usa Python porque no necesita paso de compilación, pero el mismo flujo aplica igual
para Java y Node.

## 1. Escribir la API con dos endpoints

```bash
nvim api.py
```

```python
import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer

# El puerto lo asigna el sandbox, no lo elegís vos -- ver el paso 4.
PORT = int(os.environ.get("APP_PORT", "8000"))

TAREAS = [
    {"id": 1, "titulo": "Aprender InsightBloom", "hecha": False},
    {"id": 2, "titulo": "Desplegar mi primera API", "hecha": False},
]


class ApiHandler(BaseHTTPRequestHandler):
    def _json(self, status, data):
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/tareas":
            self._json(200, TAREAS)
        elif self.path == "/salud":
            self._json(200, {"estado": "ok"})
        else:
            self._json(404, {"error": "no encontrado"})

    def do_POST(self):
        if self.path == "/tareas":
            length = int(self.headers.get("Content-Length", 0))
            nueva = json.loads(self.rfile.read(length))
            nueva["id"] = len(TAREAS) + 1
            nueva["hecha"] = False
            TAREAS.append(nueva)
            self._json(201, nueva)
        else:
            self._json(404, {"error": "no encontrado"})


if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", PORT), ApiHandler)
    print(f"Escuchando en el puerto {PORT}")
    server.serve_forever()
```

`Esc`, `:wq`, Enter.

## 2. Correrla y probarla dentro del sandbox

```bash
python3 api.py
```

Abrí otra pestaña de terminal (`Ctrl+B` `"` en tmux para dividir horizontal, `Ctrl+B` `%` para
vertical) y probá los dos endpoints:

```bash
curl "http://localhost:$APP_PORT/tareas"
curl -X POST "http://localhost:$APP_PORT/tareas" -d '{"titulo":"Nueva tarea"}'
curl "http://localhost:$APP_PORT/salud"
```

Si algo no responde, confirmá que el servidor sigue corriendo en la primera pestaña (no se cerró
por un error) y que estás usando `$APP_PORT`, no un puerto fijo inventado.

## 3. Entender el puerto antes de publicar

La API tiene que escuchar en el puerto que te asignó el sandbox, disponible en la variable de
entorno `APP_PORT`:

```bash
echo $APP_PORT
```

No lo hardcodees ni asumas que siempre va a ser el mismo número — cada sandbox recibe el suyo.
Todos los ejemplos de este panel (Java, Python, Node) leen el puerto de esa variable con un valor
de respaldo (`8000`) solo para cuando todavía no publicaste.

## 4. Publicar

Con el servidor corriendo (dejalo levantado, no lo cierres):

```bash
insightbloom login   # si todavía no iniciaste sesión
insightbloom app-publish
```

La salida trae tres cosas que necesitás anotar: la **URL pública**, el **`accessToken`** y el
**`publicationId`**.

## 5. Consumirla desde afuera

Quien llame a la API tiene que mandar el token en cada request, con el header
`X-Preview-Token`:

```bash
curl -H "X-Preview-Token: TU_TOKEN" "https://app-insightbloom.v1.rafex.cloud/p/TU_PUBLICATION_ID/tareas"
```

Probalo desde tu propia computadora (fuera del sandbox) con la URL y el token que te dio el
comando — así confirmás que de verdad es accesible desde afuera, no solo dentro del IDE.

## 6. Actualizar o revocar

Solo hay **una** publicación de backend/API activa por sandbox. Volver a correr
`insightbloom app-publish` reemplaza la anterior — la URL y el token viejos dejan de funcionar de
inmediato, así que avisá a quien esté probando tu API antes de republicar.

Para cortar el acceso sin publicar una versión nueva:

```bash
insightbloom app-revoke PUBLICATION_ID
```

La publicación también expira sola después de un tiempo, aunque no la revoques a mano.

## Errores comunes

- **La API responde local (`curl localhost:$APP_PORT`) pero la URL pública da error**: revisá
  que copiaste bien el token y el `publicationId`; un token vencido o mal copiado da error de
  autenticación, no un error del servidor.
- **`Connection refused` al publicar**: el sandbox necesita las variables y políticas de red que
  habilita la publicación; si lo recreaste hace poco, recreá el sandbox de nuevo desde el
  Dashboard después de guardar tu workspace.
- **El servidor se cae apenas publicás**: la publicación no reinicia tu proceso — si el servidor
  ya se había caído por un error de código antes de publicar, seguirá caído después. Volvé a
  correrlo (`python3 api.py`) y confirmá que responde local antes de publicar de nuevo.
