#!/usr/bin/env python3
"""Small, dependency-free HTTP CONNECT proxy for sandbox egress.

The proxy is deliberately host based: Kubernetes NetworkPolicy cannot safely
express an FQDN allowlist. A request is accepted only when its hostname is in
the allowlist and is not in the denylist. Private/reserved destinations are
rejected even if somebody accidentally adds them to the allowlist.

Policy source (2026-07): this proxy has no database and no Kubernetes access
of its own -- for every new connection it asks insightbloom-users "what is
the effective allow/block list for the sandbox at THIS source IP" (global
list unioned with that event's own list, block always wins -- see
ResolveEgressPolicyUseCase on the Java side). The answer is cached in memory
per source IP with a short TTL so a change to the global blocklist reaches
every running sandbox almost immediately without touching this pod.

Resilience: if insightbloom-users is briefly unreachable (a routine deploy,
for example) this proxy keeps serving the LAST KNOWN policy for an IP it has
already resolved, for a bounded window -- a rolling restart of one service
must not cut internet access for every sandbox in the cluster. An IP that
was never resolved successfully has no fallback and is denied (fail-closed
for the genuinely unknown case).
"""

from __future__ import annotations

import http.client
import ipaddress
import json
import logging
import os
import selectors
import socket
import socketserver
import threading
import time
from dataclasses import dataclass, field
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


@dataclass
class PolicyEntry:
    fetched_at: float
    known: bool  # False = insightbloom-users respondio "no existe sandbox con esa IP" (404)
    internet_enabled: bool = False
    allowed: frozenset[str] = field(default_factory=frozenset)
    blocked: frozenset[str] = field(default_factory=frozenset)


class Policy:
    """Resuelve la politica de egress consultando a insightbloom-users por IP de origen.

    ``fetch_fn`` es inyectable (recibe sourceIp, retorna un dict o None si no se pudo
    contactar al servicio) -- separa la logica de cache/fail-open de la llamada HTTP real
    para poder testear ambas por separado sin un servidor de verdad.
    """

    def __init__(self, fetch_fn=None, cache_ttl_seconds: float | None = None,
                 stale_max_seconds: float | None = None) -> None:
        self.fetch_fn = fetch_fn or self._http_fetch
        self.cache_ttl_seconds = cache_ttl_seconds if cache_ttl_seconds is not None else float(
            os.getenv("EGRESS_POLICY_CACHE_TTL_SECONDS", "10"))
        self.stale_max_seconds = stale_max_seconds if stale_max_seconds is not None else float(
            os.getenv("EGRESS_POLICY_STALE_MAX_SECONDS", "300"))
        self.users_base_url = os.getenv(
            "USERS_INTERNAL_URL", "http://insightbloom-users.insightbloom.svc.cluster.local:8081")
        self.internal_auth_key = os.getenv("INTERNAL_API_KEY", "")
        self._cache: dict[str, PolicyEntry] = {}
        self._lock = threading.Lock()

    def _http_fetch(self, source_ip: str) -> dict | None:
        parsed = urlsplit(self.users_base_url)
        try:
            connection = http.client.HTTPConnection(parsed.hostname, parsed.port or 80, timeout=3)
            try:
                connection.request(
                    "GET", f"/internal/egress-policy?sourceIp={source_ip}",
                    headers={"X-Internal-Auth": self.internal_auth_key})
                response = connection.getresponse()
                body = response.read()
            finally:
                connection.close()
        except OSError as e:
            LOG.warning("egress-policy: no se pudo contactar insightbloom-users: %s", e)
            return None
        if response.status == 404:
            return {"known": False}
        if response.status != 200:
            LOG.warning("egress-policy: insightbloom-users respondio %s para %s", response.status, source_ip)
            return None
        try:
            payload = json.loads(body)
        except ValueError:
            return None
        # Contrato: ApiResponse envuelve el payload real en "data" (ver BaseResourceHandler).
        data = payload.get("data", payload)
        return {
            "known": True,
            "internetEnabled": bool(data.get("internetEnabled", False)),
            "allowed": data.get("allowed") or [],
            "blocked": data.get("blocked") or [],
        }

    def _resolve(self, source_ip: str) -> PolicyEntry | None:
        now = time.time()
        with self._lock:
            cached = self._cache.get(source_ip)
        if cached is not None and now - cached.fetched_at < self.cache_ttl_seconds:
            return cached

        raw = self.fetch_fn(source_ip)
        if raw is None:
            # insightbloom-users no respondio -- fail-open con la ultima politica conocida,
            # pero solo dentro de una ventana acotada (ver docstring del modulo).
            if cached is not None and now - cached.fetched_at < self.stale_max_seconds:
                return cached
            return None

        entry = PolicyEntry(
            fetched_at=now,
            known=raw.get("known", True),
            internet_enabled=bool(raw.get("internetEnabled", False)),
            allowed=frozenset(h.lower().rstrip(".") for h in raw.get("allowed", [])),
            blocked=frozenset(h.lower().rstrip(".") for h in raw.get("blocked", [])))
        with self._lock:
            self._cache[source_ip] = entry
        return entry

    def permits(self, source_ip: str, host: str, port: int) -> bool:
        host = host.lower().rstrip(".")
        if port not in ALLOWED_PORTS or not host or ":" in host:
            return False
        try:
            ipaddress.ip_address(host)
            # Host rules are intentionally DNS-only. This prevents bypassing
            # a domain policy with a raw public/private IP address.
            return False
        except ValueError:
            pass

        entry = self._resolve(source_ip)
        if entry is None or not entry.known or not entry.internet_enabled:
            return False
        if any(_host_matches(host, rule) for rule in entry.blocked):
            return False
        return any(_host_matches(host, rule) for rule in entry.allowed)

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
        if not POLICY.permits(self.client_address[0], host, port):
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
        LOG.info("egress proxy listening on %s; policy source=%s", listen_port, POLICY.users_base_url)
        server.serve_forever()


if __name__ == "__main__":
    main()
