# Hello World en Python

Esta imagen trae **Python 3.12** ya instalado, con `pip` listo para instalar librerías
(`numpy`, `pandas`, `flask`, `fastapi`, `pytest` ya vienen preinstalados).

## 1. Crear el archivo

```bash
nvim hola.py
```

Apretá `i` para entrar en modo Insertar y escribí:

```python
print("Hola mundo")
```

## 2. Guardar y salir

`Esc` para modo Normal, después `:wq` y Enter.

## 3. Ejecutar

```bash
python3 hola.py
```

Deberías ver:

```
Hola mundo
```

## Tips

- No hace falta compilar nada — Python se ejecuta directo.
- Si tu script necesita una librería que no está instalada: `pip install nombre-libreria`.
- Para proyectos más grandes, `virtualenv` ya está instalado si querés aislar dependencias por
  proyecto (`python3 -m venv .venv && source .venv/bin/activate`).
- `black` (formateador) y `pylint` (linter) ya están instalados si querés mantener el código
  prolijo.

## Hello World de API REST (con la librería estándar)

No hace falta Flask ni FastAPI para lo mínimo: `http.server`, de la librería estándar, alcanza.

```bash
nvim api_hola.py
```

```python
import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer

# El puerto NO se elige a mano: cuando publicás tu API con "insightbloom app-publish" (ver la
# sección "Publicar página web" -> backend/API), el sandbox ya te asignó un puerto y te lo pasa
# en la variable de entorno APP_PORT. Si no está definida (por ejemplo, mientras probás
# localmente antes de publicar), 8000 es un valor de respaldo.
PORT = int(os.environ.get("APP_PORT", "8000"))


class HolaHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/hello":
            body = json.dumps({"mensaje": "Hola mundo"}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()


if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", PORT), HolaHandler)
    print(f"Escuchando en el puerto {PORT}")
    server.serve_forever()
```

```bash
python3 api_hola.py
```

Probalo desde otra pestaña de terminal (`Ctrl+B` `"` en tmux para dividir, o `Ctrl+B` `%`):

```bash
curl "http://localhost:$APP_PORT/hello"
```

Para probarlo desde AFUERA del sandbox, primero publicalo (ver "Publicar página web" en este
mismo panel de ayuda, sección backend/API): `insightbloom app-publish` te da una URL pública y un
token. Con esos dos datos:

```bash
curl -H "X-Preview-Token: TU_TOKEN" "https://app-insightbloom.v1.rafex.cloud/p/TU_PUBLICATION_ID/hello"
```
