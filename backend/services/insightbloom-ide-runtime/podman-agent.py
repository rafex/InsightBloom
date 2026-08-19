#!/usr/bin/env python3
"""Runtime aislado para builds y publicaciones de contenedores de los IDE."""
import hmac
import http.server
import json
import os
import socketserver
import subprocess
import tempfile
import threading

BUILD_TIMEOUT_SECONDS = 150
RUN_TIMEOUT_SECONDS = 15
NAME_PREFIX = "insightbloom-pub-"
LOCK = threading.Lock()
API_KEY = os.environ.get("INTERNAL_API_KEY", "")

def run(args, timeout):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)

def build_and_run(content, host_port, container_port):
    name = f"{NAME_PREFIX}{host_port}"
    with tempfile.TemporaryDirectory(prefix="insightbloom-build-") as directory:
        file_path = os.path.join(directory, "Containerfile")
        with open(file_path, "w", encoding="utf-8") as output:
            output.write(content)
        built = run(["podman", "build", "-t", name, "-f", file_path, directory], BUILD_TIMEOUT_SECONDS)
        if built.returncode:
            return {"ok": False, "error": "podman_build_failed", "detail": built.stderr[-4000:]}
    run(["podman", "rm", "-f", name], RUN_TIMEOUT_SECONDS)
    args = ["podman", "run", "-d", "--name", name]
    if container_port > 0:
        args += ["-p", f"{host_port}:{container_port}"]
    args.append(name)
    started = run(args, RUN_TIMEOUT_SECONDS)
    if started.returncode:
        return {"ok": False, "error": "podman_run_failed", "detail": started.stderr[-4000:]}
    return {"ok": True}

class Handler(http.server.BaseHTTPRequestHandler):
    def send_json(self, status, value):
        body = json.dumps(value).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def authorized(self):
        supplied = self.headers.get("X-Internal-Api-Key", "")
        return bool(API_KEY) and hmac.compare_digest(supplied, API_KEY)

    def do_GET(self):
        if self.path == "/health":
            self.send_json(200, {"status": "ok"})
        else:
            self.send_json(404, {"error": "not_found"})

    def do_POST(self):
        if self.path != "/build":
            self.send_json(404, {"error": "not_found"})
            return
        if not self.authorized():
            self.send_json(403, {"error": "forbidden"})
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(size) or b"{}")
            content = body.get("containerfile", "")
            host_port = body.get("hostPort")
            container_port = int(body.get("containerPort", -1))
            if not isinstance(content, str) or not content or not isinstance(host_port, int) or host_port <= 0:
                raise ValueError
        except (ValueError, json.JSONDecodeError):
            self.send_json(400, {"error": "invalid_body"})
            return
        with LOCK:
            try:
                result = build_and_run(content, host_port, container_port)
            except subprocess.TimeoutExpired:
                self.send_json(500, {"error": "podman_timeout"})
                return
        self.send_json(200 if result["ok"] else 500, result)

    def log_message(self, fmt, *args):
        return

class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True

if __name__ == "__main__":
    port = int(os.environ.get("CONTROL_PORT", "9499"))
    Server(("0.0.0.0", port), Handler).serve_forever()
