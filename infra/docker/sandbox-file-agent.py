#!/usr/bin/env python3
"""Agente de archivos para Pods de un solo asiento (Web o CLI, Fase 4 -- dashboard de
moderador). Es la versión mínima del control-port de sandbox-agent.py: esta imagen siempre es UN
solo asiento/UN solo usuario ("coder", no-root, sin privilegios especiales), así que no hace falta
nada de aprovisionamiento de cuentas ni watchdog de recursos -- solo listar/leer/escribir
archivos del workspace de este alumno, mismo protocolo HTTP que sandbox-agent.py (mismo
seatIndex=0 fijo en la URL, para que el lado Java trate ambas variantes de forma uniforme, ver
KubernetesPodClient.listFiles/readFile/writeFile).

Alcanzable SOLO desde insightbloom-users -- misma NetworkPolicy (segunda regla de Ingress,
puerto de control = basePort-1) que ya cubre TODOS los Pods de sandbox por igual
(KubernetesPodClient.ensureIngressPolicy), sea cual sea la imagen. Antes de esta Fase 4 nada
escuchaba en ese puerto en la imagen debian -- ahora si.

En las imágenes Web se lanza en background por code-ide-entrypoint.sh, ANTES del "exec
code-server"; en las imágenes CLI se lanza antes de ttyd. En ambos casos queda re-parentado a
dumb-init (PID 1), que reapea sus zombies igual que los de cualquier otro huérfano.
"""
import http.server
import re
import sandbox_file_api
import socketserver
import sys
import urllib.parse

WORKSPACE_ROOT = "/home/coder/workspace"


class Handler(http.server.BaseHTTPRequestHandler):
    def _send_json(self, status: int, payload: dict):
        import json
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_zip(self, body: bytes):
        self.send_response(200)
        self.send_header("Content-Type", "application/zip")
        self.send_header("Content-Disposition", 'attachment; filename="workspace.zip"')
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urllib.parse.urlsplit(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        path_param = query.get("path", [""])[0]

        if parsed.path == "/health":
            self._send_json(200, {"status": "ok"})
            return

        if re.fullmatch(r"/files/0", parsed.path):
            try:
                self._send_json(200, {"entries": sandbox_file_api.list_directory(WORKSPACE_ROOT, path_param)})
            except sandbox_file_api.PathTraversalError:
                self._send_json(400, {"error": "invalid_path"})
            except sandbox_file_api.NotFoundError:
                self._send_json(404, {"error": "not_found"})
            return

        if re.fullmatch(r"/file/0", parsed.path):
            try:
                self._send_json(200, sandbox_file_api.read_file(WORKSPACE_ROOT, path_param))
            except sandbox_file_api.PathTraversalError:
                self._send_json(400, {"error": "invalid_path"})
            except sandbox_file_api.NotFoundError:
                self._send_json(404, {"error": "not_found"})
            except sandbox_file_api.FileTooLargeError:
                self._send_json(413, {"error": "file_too_large"})
            except sandbox_file_api.NotTextFileError:
                self._send_json(415, {"error": "not_text_file"})
            return

        if re.fullmatch(r"/workspace/0/zip", parsed.path):
            try:
                self._send_zip(sandbox_file_api.build_workspace_zip(WORKSPACE_ROOT))
            except sandbox_file_api.NotFoundError:
                self._send_json(404, {"error": "not_found"})
            except sandbox_file_api.WorkspaceTooLargeError:
                self._send_json(413, {"error": "workspace_too_large"})
            return

        self._send_json(404, {"error": "not_found"})

    def do_PUT(self):
        parsed = urllib.parse.urlsplit(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        path_param = query.get("path", [""])[0]
        mtime_param = query.get("mtime", [None])[0]

        if not re.fullmatch(r"/file/0", parsed.path):
            self._send_json(404, {"error": "not_found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            content = self.rfile.read(length).decode("utf-8")
        except (ValueError, UnicodeDecodeError):
            self._send_json(400, {"error": "invalid_body"})
            return
        try:
            expected_mtime = float(mtime_param) if mtime_param is not None else None
            self._send_json(200, sandbox_file_api.write_file(WORKSPACE_ROOT, path_param, content, expected_mtime))
        except sandbox_file_api.PathTraversalError:
            self._send_json(400, {"error": "invalid_path"})
        except sandbox_file_api.FileConflictError:
            self._send_json(409, {"error": "file_conflict"})
        except (ValueError, TypeError):
            self._send_json(400, {"error": "invalid_mtime"})

    def log_message(self, fmt, *args):
        sys.stderr.write("sandbox-file-agent: " + (fmt % args) + "\n")


class ThreadingHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--control-port", type=int, required=True)
    args = parser.parse_args()

    server = ThreadingHTTPServer(("0.0.0.0", args.control_port), Handler)
    print(f"sandbox-file-agent: listening on :{args.control_port}", file=sys.stderr)
    server.serve_forever()


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # noqa: BLE001 -- best-effort: si este proceso muere, code-server
        # (el proceso principal del contenedor) sigue andando igual -- solo se pierde el visor
        # de archivos del moderador para este Pod puntual, no el IDE del alumno.
        print(f"sandbox-file-agent: fatal error: {e}", file=sys.stderr)
