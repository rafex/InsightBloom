#!/usr/bin/env python3
"""Caché interno de repositorios públicos GitHub para los sandboxes.

No expone archivos por HTTP: los sandboxes los consumen exclusivamente mediante un
PVC RWX de solo lectura. Cada release se publica por rename atómico bajo `current`.
"""
import hashlib
import http.server
import json
import os
import re
import subprocess
import tempfile
import threading
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(os.environ.get("MATERIAL_CACHE_ROOT", "/data/materials"))
URL_RE = re.compile(r"^/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\.git)?$")
REF_RE = re.compile(r"^[A-Za-z0-9._/-]{1,128}$")
LOCKS: dict[str, threading.Lock] = {}
LOCKS_GUARD = threading.Lock()

def source_key(url: str, ref: str) -> str:
    return hashlib.md5(f"{url}#{ref}".encode()).hexdigest()  # no secreto; estable entre Java/Python

def validate(url: str, ref: str) -> None:
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.hostname != "github.com" or parsed.username or parsed.query or parsed.fragment or not URL_RE.match(parsed.path):
        raise ValueError("only_public_github_https_allowed")
    if not REF_RE.match(ref):
        raise ValueError("invalid_ref")

def sync(url: str, ref: str) -> tuple[str, str]:
    validate(url, ref)
    key = source_key(url, ref)
    with LOCKS_GUARD:
        lock = LOCKS.setdefault(key, threading.Lock())
    with lock:
        base = ROOT / key
        mirror = base / "mirror.git"
        releases = base / "releases"
        base.mkdir(parents=True, exist_ok=True)
        if not mirror.exists():
            subprocess.run(["git", "init", "--bare", str(mirror)], check=True, timeout=30)
            subprocess.run(["git", "-C", str(mirror), "remote", "add", "origin", url], check=True, timeout=10)
        # Una ref propia evita depender de FETCH_HEAD, que no existe en algunos clones bare.
        subprocess.run([
            "git", "-C", str(mirror), "fetch", "--depth", "1", "--filter=blob:none", "origin",
            f"{ref}:refs/insightbloom/material-source"
        ], check=True, timeout=180)
        sha = subprocess.check_output([
            "git", "-C", str(mirror), "rev-parse", "refs/insightbloom/material-source"
        ], text=True, timeout=10).strip()
        release = releases / sha
        if not release.exists():
            releases.mkdir(parents=True, exist_ok=True)
            with tempfile.TemporaryDirectory(dir=releases, prefix=".incoming-") as tmp:
                archive = Path(tmp) / "archive.tar"
                with archive.open("wb") as output:
                    subprocess.run(["git", "--git-dir", str(mirror), "archive", sha], check=True,
                                   stdout=output, timeout=120)
                subprocess.run(["tar", "-xf", str(Path(tmp) / "archive.tar"), "-C", tmp], check=True, timeout=60)
                os.unlink(Path(tmp) / "archive.tar")
                (Path(tmp) / ".insightbloom-material-revision").write_text(sha + "\n", encoding="utf-8")
                os.rename(tmp, release)
        tmp_link = base / ".current-next"
        tmp_link.unlink(missing_ok=True)
        tmp_link.symlink_to(Path("releases") / sha)
        os.replace(tmp_link, base / "current")
        return key, sha

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            self.send_response(200); self.end_headers(); self.wfile.write(b"ok\n"); return
        self.send_error(404)
    def do_POST(self):
        if self.path != "/sync": self.send_error(404); return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if size <= 0 or size > 8192: raise ValueError("invalid_request")
            body = json.loads(self.rfile.read(size))
            key, sha = sync(body["url"], body.get("ref") or "main")
            payload = json.dumps({"sourceKey": key, "revision": sha}).encode()
            self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(payload))); self.end_headers(); self.wfile.write(payload)
        except Exception as exc:  # cache failure must be observable but never executes repository content
            payload = json.dumps({"error": str(exc)}).encode()
            self.send_response(422); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(payload))); self.end_headers(); self.wfile.write(payload)
    def log_message(self, fmt, *args): print("material-cache:", fmt % args, flush=True)

if __name__ == "__main__":
    ROOT.mkdir(parents=True, exist_ok=True)
    http.server.ThreadingHTTPServer(("0.0.0.0", int(os.environ.get("PORT", "8092"))), Handler).serve_forever()
