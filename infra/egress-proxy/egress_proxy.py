#!/usr/bin/env python3
"""Small, dependency-free HTTP CONNECT proxy for sandbox egress.

The proxy is deliberately host based: Kubernetes NetworkPolicy cannot safely
express an FQDN allowlist.  A request is accepted only when its hostname is in
the allowlist and is not in the denylist.  Private/reserved destinations are
rejected even if somebody accidentally adds them to the allowlist.
"""

from __future__ import annotations

import http.client
import ipaddress
import logging
import os
import selectors
import socket
import socketserver
from pathlib import Path
from urllib.parse import urlsplit
from http.server import BaseHTTPRequestHandler


LOG = logging.getLogger("insightbloom-egress-proxy")
MAX_BODY_BYTES = 16 * 1024 * 1024
ALLOWED_PORTS = {80, 443}


def _host_matches(host: str, rule: str) -> bool:
    rule = rule.lower().rstrip(".")
    if rule.startswith("*."):
        suffix = rule[1:]
        return host.endswith(suffix) and host != suffix[1:]
    return host == rule


class Policy:
    def __init__(self, config_dir: str | os.PathLike[str] | None = None) -> None:
        # Kubernetes proyecta ConfigMaps como archivos y actualiza el volumen
        # cuando Flux aplica una nueva versión. Leer estos archivos al evaluar
        # cada destino evita depender de un restart para cambiar la política.
        self.config_dir = Path(config_dir or os.getenv(
            "EGRESS_PROXY_CONFIG_DIR", "/etc/insightbloom-config"))
        self.allowed: set[str] = set()
        self.blocked: set[str] = set()
        self.reload()

    def _value(self, name: str, default: str = "") -> str:
        try:
            return (self.config_dir / name).read_text(encoding="utf-8").strip()
        except OSError:
            return os.getenv(name, default)

    def reload(self) -> None:
        self.allowed = {item.strip().lower().rstrip(".") for item in
                        self._value("EGRESS_PROXY_ALLOWED_HOSTS").split(",") if item.strip()}
        self.blocked = {item.strip().lower().rstrip(".") for item in
                        self._value("EGRESS_PROXY_BLOCKED_HOSTS").split(",") if item.strip()}

    def permits(self, host: str, port: int) -> bool:
        self.reload()
        host = host.lower().rstrip(".")
        if port not in ALLOWED_PORTS or not host or ":" in host:
            return False
        try:
            address = ipaddress.ip_address(host)
            # Host rules are intentionally DNS-only.  This prevents bypassing
            # a domain policy with a raw public/private IP address.
            return False
        except ValueError:
            pass
        if any(_host_matches(host, rule) for rule in self.blocked):
            return False
        return any(_host_matches(host, rule) for rule in self.allowed)

    def safe_addresses(self, host: str, port: int) -> list[tuple[int, int, int, str, tuple]]:
        addresses = socket.getaddrinfo(host, port, type=socket.SOCK_STREAM)
        safe = []
        for family, socktype, proto, canonname, sockaddr in addresses:
            address = ipaddress.ip_address(sockaddr[0])
            if address.is_private or address.is_loopback or address.is_link_local \
                    or address.is_reserved or address.is_multicast or address.is_unspecified:
                continue
            safe.append((family, socktype, proto, canonname, sockaddr))
        return safe


POLICY = Policy()


def _split_host_port(value: str, default_port: int) -> tuple[str, int] | None:
    if not value or value.count(":") > 1:
        return None
    if ":" in value:
        host, raw_port = value.rsplit(":", 1)
        try:
            port = int(raw_port)
        except ValueError:
            return None
    else:
        host, port = value, default_port
    return host.strip("[]").lower().rstrip("."), port


class ProxyHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt: str, *args: object) -> None:
        LOG.info("%s %s", self.client_address[0], fmt % args)

    def _reject(self, status: int, message: str) -> None:
        body = (message + "\n").encode()
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)

    def _upstream(self, host: str, port: int) -> socket.socket | None:
        if not POLICY.permits(host, port):
            self._reject(403, "egress destination is not allowed")
            return None
        try:
            addresses = POLICY.safe_addresses(host, port)
        except socket.gaierror:
            self._reject(502, "egress destination could not be resolved")
            return None
        for family, socktype, proto, _canonname, sockaddr in addresses:
            try:
                upstream = socket.socket(family, socktype, proto)
                upstream.settimeout(15)
                upstream.connect(sockaddr)
                upstream.settimeout(None)
                return upstream
            except OSError:
                try:
                    upstream.close()
                except UnboundLocalError:
                    pass
        self._reject(502, "egress destination is unreachable")
        return None

    def do_CONNECT(self) -> None:
        target = _split_host_port(self.path, 443)
        if target is None:
            self._reject(400, "invalid CONNECT target")
            return
        host, port = target
        upstream = self._upstream(host, port)
        if upstream is None:
            return
        self.send_response(200, "Connection Established")
        self.end_headers()
        self.connection.setblocking(False)
        upstream.setblocking(False)
        selector = selectors.DefaultSelector()
        selector.register(self.connection, selectors.EVENT_READ, upstream)
        selector.register(upstream, selectors.EVENT_READ, self.connection)
        try:
            while True:
                events = selector.select(timeout=300)
                if not events:
                    break
                for key, _ in events:
                    data = key.fileobj.recv(64 * 1024)
                    if not data:
                        return
                    key.data.sendall(data)
        except OSError:
            pass
        finally:
            selector.close()
            upstream.close()

    def do_GET(self) -> None:
        self._forward_http()

    def do_HEAD(self) -> None:
        self._forward_http()

    def do_POST(self) -> None:
        self._forward_http()

    def do_PUT(self) -> None:
        self._forward_http()

    def do_PATCH(self) -> None:
        self._forward_http()

    def do_DELETE(self) -> None:
        self._forward_http()

    def _forward_http(self) -> None:
        parsed = urlsplit(self.path)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            self._reject(400, "proxy requires an absolute HTTP URL")
            return
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
        upstream = self._upstream(parsed.hostname, port)
        if upstream is None:
            return
        body = None
        raw_length = self.headers.get("Content-Length")
        if raw_length:
            try:
                length = int(raw_length)
            except ValueError:
                upstream.close()
                self._reject(400, "invalid content length")
                return
            if length < 0 or length > MAX_BODY_BYTES:
                upstream.close()
                self._reject(413, "request body too large")
                return
            body = self.rfile.read(length)
        headers = {key: value for key, value in self.headers.items()
                   if key.lower() not in {"proxy-connection", "connection", "keep-alive"}}
        headers["Host"] = parsed.netloc
        path = parsed.path or "/"
        if parsed.query:
            path += "?" + parsed.query
        connection = http.client.HTTPConnection(parsed.hostname, port, timeout=15)
        connection.sock = upstream
        try:
            connection.request(self.command, path, body=body, headers=headers)
            response = connection.getresponse()
            payload = response.read(MAX_BODY_BYTES + 1)
            if len(payload) > MAX_BODY_BYTES:
                self._reject(502, "upstream response too large")
                return
            self.send_response(response.status, response.reason)
            for key, value in response.getheaders():
                if key.lower() not in {"connection", "transfer-encoding", "content-length"}:
                    self.send_header(key, value)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        except (OSError, http.client.HTTPException):
            self._reject(502, "upstream request failed")
        finally:
            connection.close()


class ThreadedProxy(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main() -> None:
    logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(message)s")
    listen_port = int(os.getenv("EGRESS_PROXY_LISTEN_PORT", "3128"))
    with ThreadedProxy(("0.0.0.0", listen_port), ProxyHandler) as server:
        LOG.info("egress proxy listening on %s; allowed=%s blocked=%s", listen_port,
                 sorted(POLICY.allowed), sorted(POLICY.blocked))
        server.serve_forever()


if __name__ == "__main__":
    main()
