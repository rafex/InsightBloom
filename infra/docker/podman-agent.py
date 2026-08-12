#!/usr/bin/env python3
"""Agente de control del pod Podman compartido (Fase 4b, MVP, 2026-08).

Corre como proceso principal del contenedor "podman-runtime" (ver
KubernetesPodClient.buildPodmanPodBody) -- a diferencia de sandbox-agent.py/sandbox-file-agent.py
(por-alumno, IDE Web/CLI), este agente sirve a TODOS los alumnos que publican un contenedor
mientras el pod compartido este vivo: cada publicacion corre en su PROPIO nombre de contenedor
Podman (namespaced por puerto), sin pisarse entre si.

Por que corre como root (uid 0) DENTRO del pod: Podman necesita crear sus propios contenedores
anidados (un segundo nivel de aislamiento, independiente del hostUsers:false del pod -- ver
KubernetesPodClient.podmanContainerSecurityContext). Es seguro porque hostUsers:false ya remapea
TODO el rango de UID del pod (incluido el 0) a un rango sin privilegios reales en el nodo -- sin
ese remapeo, esto seria inaceptable (mismo razonamiento que sandbox-agent.py para el seat-agent,
ver DEC-0025, pero un nivel mas: aca el problema no es dropear privilegios a un alumno, es que
Podman rootless funcione en absoluto).

El Containerfile que llega por HTTP YA fue validado (ContainerfileValidator + ResolveImagePolicyUseCase,
en insightbloom-users) contra la whitelist/blacklist de imagenes ANTES de llegar aca -- este agente
no vuelve a validar nada, confia en el caller (mismo criterio que sandbox-agent.py confia en la
NetworkPolicy en vez de un token de aplicacion: el limite de seguridad real es que solo
insightbloom-users puede alcanzar este puerto, ver ensureIngressPolicy).

Endpoints:
  GET  /health          -> 200 si el agente esta vivo
  POST /build            -> {"containerfile": "...", "hostPort": N, "containerPort": M}
                             corre "podman build" + "podman run -d -p hostPort:containerPort"
                             (sin -p si containerPort <= 0). Sincronico -- ver timeout del lado
                             Java (KubernetesPodClient.buildAndRunContainer, 170s).
"""
import http.server
import json
import socketserver
import subprocess
import sys
import tempfile
import threading

BUILD_TIMEOUT_SECONDS = 150
RUN_TIMEOUT_SECONDS = 15
CONTAINER_NAME_PREFIX = "insightbloom-pub-"

_lock = threading.Lock()


def _container_name(host_port: int) -> str:
    return f"{CONTAINER_NAME_PREFIX}{host_port}"


def _run(args: list[str], timeout: int) -> subprocess.CompletedProcess:
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)


def _build_and_run(containerfile_content: str, host_port: int, container_port: int) -> dict:
    name = _container_name(host_port)
    with tempfile.TemporaryDirectory(prefix="insightbloom-build-") as build_dir:
        containerfile_path = f"{build_dir}/Containerfile"
        with open(containerfile_path, "w", encoding="utf-8") as f:
            f.write(containerfile_content)

        build = _run(
            ["podman", "build", "-t", name, "-f", containerfile_path, build_dir],
            BUILD_TIMEOUT_SECONDS,
        )
        if build.returncode != 0:
            return {"ok": False, "error": "podman_build_failed", "detail": build.stderr[-4000:]}

    # Idempotente: una republicacion en el mismo puerto reemplaza el contenedor anterior en vez
    # de fallar por "name already in use" (mismo espiritu que PublishAppPreviewUseCase, que
    # reemplaza la publicacion previa del mismo alumno).
    _run(["podman", "rm", "-f", name], RUN_TIMEOUT_SECONDS)

    run_args = ["podman", "run", "-d", "--name", name]
    if container_port > 0:
        run_args += ["-p", f"{host_port}:{container_port}"]
    run_args.append(name)
    run = _run(run_args, RUN_TIMEOUT_SECONDS)
    if run.returncode != 0:
        return {"ok": False, "error": "podman_run_failed", "detail": run.stderr[-4000:]}

    return {"ok": True}


class Handler(http.server.BaseHTTPRequestHandler):
    def _send_json(self, status: int, payload: dict):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self._send_json(200, {"status": "ok"})
            return
        self._send_json(404, {"error": "not_found"})

    def do_POST(self):
        if self.path != "/build":
            self._send_json(404, {"error": "not_found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length) or b"{}")
        except (ValueError, json.JSONDecodeError):
            self._send_json(400, {"error": "invalid_body"})
            return

        containerfile_content = body.get("containerfile", "")
        host_port = body.get("hostPort")
        container_port = body.get("containerPort", -1)
        if not containerfile_content or not isinstance(host_port, int) or host_port <= 0:
            self._send_json(400, {"error": "invalid_body"})
            return

        # Un build a la vez -- MVP, evita que dos publicaciones concurrentes saturen CPU/memoria
        # del pod compartido en simultaneo (mismo espiritu conservador que el resto de Fase 4b:
        # probar que funciona antes de optimizar throughput).
        with _lock:
            try:
                result = _build_and_run(containerfile_content, host_port, int(container_port))
            except subprocess.TimeoutExpired:
                self._send_json(500, {"error": "podman_timeout", "detail": "build o run excedio el tiempo limite"})
                return
            except Exception as e:  # noqa: BLE001 -- nunca debe tumbar el agente
                self._send_json(500, {"error": "podman_agent_error", "detail": str(e)})
                return

        if not result["ok"]:
            self._send_json(500, {"error": result["error"], "detail": result["detail"]})
            return
        self._send_json(200, {"status": "running"})

    def log_message(self, fmt, *args):  # silencia el logging default de BaseHTTPRequestHandler
        sys.stderr.write("podman-agent: " + (fmt % args) + "\n")


class ThreadingHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


def main():
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--control-port", type=int, required=True)
    parser.add_argument("--app-base-port", type=int, required=True)
    parser.add_argument("--max-publications", type=int, required=True)
    args = parser.parse_args()

    server = ThreadingHTTPServer(("0.0.0.0", args.control_port), Handler)
    print(
        f"podman-agent: listening on :{args.control_port}, publications "
        f"{args.app_base_port}..{args.app_base_port + args.max_publications - 1}",
        file=sys.stderr,
    )
    server.serve_forever()


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # noqa: BLE001 -- fallo de arranque: no hay nada util que este
        # proceso pueda seguir sirviendo, se deja morir y que Kubernetes reporte el contenedor
        # como fallido.
        print(f"podman-agent: fatal error: {e}", file=sys.stderr)
        sys.exit(1)
