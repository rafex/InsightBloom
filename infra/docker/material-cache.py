#!/usr/bin/env python3
"""Internal cache for public GitHub materials consumed by IDE sandboxes."""
import hashlib
import hmac
import http.server
import json
import os
import re
import shutil
import subprocess
import tarfile
import tempfile
import threading
import time
from pathlib import Path, PurePosixPath
from urllib.parse import unquote, urlsplit

ROOT = Path(os.environ.get("MATERIAL_CACHE_ROOT", "/data/materials"))
SYNC_TOKEN = os.environ.get("MATERIAL_CACHE_SYNC_TOKEN", "")
MAX_ARCHIVE_BYTES = int(os.environ.get("MATERIAL_CACHE_MAX_ARCHIVE_BYTES", str(128 * 1024 * 1024)))
MAX_CACHE_BYTES = int(os.environ.get("MATERIAL_CACHE_MAX_BYTES", str(700 * 1024 * 1024)))
SOURCE_TTL_SECONDS = int(os.environ.get("MATERIAL_CACHE_SOURCE_TTL_SECONDS", str(7 * 24 * 60 * 60)))
URL_RE = re.compile(r"^/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\.git)?$")
REF_RE = re.compile(r"^[A-Za-z0-9._/-]{1,128}$")
KEY_RE = re.compile(r"^[a-f0-9]{64}$")
REVISION_RE = re.compile(r"^(?:[a-f0-9]{40}|[a-f0-9]{64})$")
LOCKS: dict[str, threading.Lock] = {}
LOCKS_GUARD = threading.Lock()
CACHE_MUTATION_LOCK = threading.Lock()
SYNC_LOCK = threading.Lock()


class CacheError(ValueError):
    """A safe-to-report request or cache constraint error."""


def source_key(url: str, ref: str) -> str:
    return hashlib.sha256(f"{url}#{ref}".encode()).hexdigest()


def validate(url: str, ref: str) -> None:
    parsed = urlsplit(url)
    if (parsed.scheme != "https" or parsed.hostname != "github.com" or parsed.username
            or parsed.password or parsed.port not in (None, 443) or parsed.query or parsed.fragment
            or not URL_RE.fullmatch(parsed.path)):
        raise CacheError("only_public_github_https_allowed")
    if not ref or not REF_RE.fullmatch(ref) or ref.startswith("-") or ".." in ref:
        raise CacheError("invalid_ref")


def _safe_relative_path(value: str) -> str:
    if not value or "\\" in value or "\x00" in value:
        raise CacheError("invalid_material_path")
    path = PurePosixPath(value)
    if path.is_absolute() or any(part in ("", ".", "..") for part in value.split("/")):
        raise CacheError("invalid_material_path")
    return path.as_posix()


def _within(root: Path, target: Path) -> bool:
    try:
        target.relative_to(root)
        return True
    except ValueError:
        return False


def _tree_size(path: Path) -> int:
    total = 0
    for item in path.rglob("*"):
        if not item.is_symlink() and item.is_file():
            total += item.stat().st_size
    return total


def _cache_size() -> int:
    return _tree_size(ROOT)


def _extract_archive(archive_path: Path, destination: Path) -> None:
    """Extract Git's tar output without relying on Python-version-specific tar filters."""
    root = destination.resolve()
    pending_symlinks = []
    with tarfile.open(archive_path, "r:") as archive:
        members = archive.getmembers()
        for member in members:
            relative = _safe_relative_path(member.name.rstrip("/"))
            target = destination.joinpath(*PurePosixPath(relative).parts)
            if not _within(root, target.resolve(strict=False)):
                raise CacheError("unsafe_repository_archive")
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            if member.isfile():
                target.parent.mkdir(parents=True, exist_ok=True)
                source = archive.extractfile(member)
                if source is None:
                    raise CacheError("invalid_repository_archive")
                with source, target.open("xb") as output:
                    shutil.copyfileobj(source, output)
                os.chmod(target, member.mode & 0o777)
                continue
            if member.issym():
                pending_symlinks.append((target, member.linkname))
                continue
            raise CacheError("unsafe_repository_archive")

    for target, linkname in pending_symlinks:
        if not linkname or "\\" in linkname or PurePosixPath(linkname).is_absolute():
            raise CacheError("unsafe_repository_archive")
        resolved_link = (target.parent / linkname).resolve(strict=False)
        if not _within(root, resolved_link):
            raise CacheError("unsafe_repository_archive")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.symlink_to(linkname)


def _prune(base: Path, current_sha: str) -> None:
    """Keep the current and previous release; expire inactive sources by directory mtime."""
    now = time.time()
    for source in ROOT.iterdir():
        if not source.is_dir() or source.name.startswith("."):
            continue
        if source != base and now - source.stat().st_mtime > SOURCE_TTL_SECONDS:
            shutil.rmtree(source, ignore_errors=True)
            continue
        releases = source / "releases"
        if not releases.is_dir():
            continue
        candidates = sorted((entry for entry in releases.iterdir() if entry.is_dir()),
                            key=lambda entry: entry.stat().st_mtime, reverse=True)
        current_link = source / "current"
        linked_sha = current_link.resolve().name if current_link.is_symlink() else ""
        retained = {current_sha} if source == base else set()
        if linked_sha:
            retained.add(linked_sha)
        for release in candidates:
            if release.name in retained:
                continue
            if len(retained) < 2:
                retained.add(release.name)
                continue
            shutil.rmtree(release, ignore_errors=True)


def _archive_to_file(mirror: Path, revision: str, output: Path) -> None:
    process = subprocess.Popen(
        ["git", "--git-dir", str(mirror), "archive", revision],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    count = 0
    try:
        with output.open("wb") as archive:
            assert process.stdout is not None
            while chunk := process.stdout.read(64 * 1024):
                count += len(chunk)
                if count > MAX_ARCHIVE_BYTES:
                    process.terminate()
                    raise CacheError("repository_archive_exceeds_cache_limit")
                archive.write(chunk)
        stderr = process.communicate(timeout=30)[1]
        if process.returncode != 0:
            raise subprocess.CalledProcessError(process.returncode, process.args, stderr=stderr)
    except Exception:
        if process.poll() is None:
            process.kill()
        process.wait()
        output.unlink(missing_ok=True)
        raise


def sync(url: str, ref: str) -> tuple[str, str]:
    # A single admission path makes the global disk budget meaningful while a snapshot is
    # fetched/extracted, not just after concurrent jobs have already consumed the disk.
    with SYNC_LOCK:
        return _sync_locked(url, ref)


def _sync_locked(url: str, ref: str) -> tuple[str, str]:
    validate(url, ref)
    key = source_key(url, ref)
    with LOCKS_GUARD:
        lock = LOCKS.setdefault(key, threading.Lock())
    with lock:
        try:
            ref_check = ["git", "check-ref-format", ref] if ref.startswith("refs/") else [
                "git", "check-ref-format", "--branch", ref]
            subprocess.run(ref_check, check=True, timeout=10,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
            raise CacheError("invalid_ref") from exc
        base = ROOT / key
        mirror = base / "mirror.git"
        releases = base / "releases"
        if base.exists():
            current_link = base / "current"
            _prune(base, current_link.resolve().name if current_link.is_symlink() else "")
        # Reserve the largest permitted fetched pack plus the bounded Git archive/release.
        # This prevents the 1 GiB local-path claim from being used as an implicit unbounded
        # temporary workspace while a new source is synchronized.
        if _cache_size() + (2 * MAX_ARCHIVE_BYTES) > MAX_CACHE_BYTES:
            raise CacheError("material_cache_capacity_insufficient")
        base.mkdir(parents=True, exist_ok=True)
        if not mirror.exists():
            subprocess.run(["git", "init", "--bare", str(mirror)], check=True, timeout=30,
                           stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            subprocess.run(["git", "-C", str(mirror), "remote", "add", "origin", url], check=True,
                           timeout=10, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        fetch_command = [
            "git", "-C", str(mirror), "fetch", "--depth", "1", "--filter=blob:none", "origin",
            f"{ref}:refs/insightbloom/material-source"
        ]
        # Use a shell file-size ulimit for the isolated Git process rather than subprocess
        # preexec_fn (unsafe in this threaded HTTP server). POSIX ulimit uses 512-byte blocks.
        subprocess.run([
            "/bin/sh", "-c", 'ulimit -f "$1" || exit 125; shift; exec "$@"',
            "material-cache", str(max(1, MAX_ARCHIVE_BYTES // 512)), *fetch_command,
        ], check=True, timeout=180, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        revision = subprocess.check_output([
            "git", "-C", str(mirror), "rev-parse", "refs/insightbloom/material-source"
        ], text=True, timeout=10).strip()
        if not REVISION_RE.fullmatch(revision):
            raise CacheError("invalid_git_revision")

        release = releases / revision
        previous_revision = ""
        current_link = base / "current"
        if current_link.is_symlink():
            previous_revision = current_link.resolve().name
        if not release.exists():
            releases.mkdir(parents=True, exist_ok=True)
            with tempfile.TemporaryDirectory(dir=releases, prefix=".incoming-") as temporary:
                temporary_path = Path(temporary)
                archive = temporary_path / "repository.tar"
                _archive_to_file(mirror, revision, archive)
                extracted = temporary_path / "tree"
                extracted.mkdir()
                _extract_archive(archive, extracted)
                archive.unlink()
                (extracted / ".insightbloom-material-revision").write_text(revision + "\n", encoding="utf-8")
                if _tree_size(extracted) > MAX_ARCHIVE_BYTES:
                    raise CacheError("repository_exceeds_cache_limit")
                os.rename(extracted, release)

        # Serialize promotion/cleanup across source keys so parallel syncs cannot both pass
        # the global cache-size check and overrun the configured disk budget.
        with CACHE_MUTATION_LOCK:
            next_link = base / ".current-next"
            next_link.unlink(missing_ok=True)
            next_link.symlink_to(Path("releases") / revision)
            os.replace(next_link, current_link)
            base.touch()
            _prune(base, revision)
            if _cache_size() > MAX_CACHE_BYTES:
                if revision != previous_revision:
                    shutil.rmtree(release, ignore_errors=True)
                    if previous_revision and (releases / previous_revision).is_dir():
                        rollback = base / ".current-rollback"
                        rollback.unlink(missing_ok=True)
                        rollback.symlink_to(Path("releases") / previous_revision)
                        os.replace(rollback, current_link)
                    else:
                        current_link.unlink(missing_ok=True)
                    shutil.rmtree(mirror, ignore_errors=True)
                _prune(base, previous_revision)
                raise CacheError("material_cache_limit_reached")
        return key, revision


def resolve_material(key: str, revision: str, relative_path: str) -> Path:
    if not KEY_RE.fullmatch(key) or not REVISION_RE.fullmatch(revision):
        raise CacheError("invalid_material_revision")
    safe_path = _safe_relative_path(relative_path)
    release = (ROOT / key / "releases" / revision).resolve(strict=True)
    root = (ROOT / key / "releases").resolve(strict=True)
    if not _within(root, release) or not release.is_dir():
        raise CacheError("material_revision_not_found")
    target = (release / safe_path).resolve(strict=True)
    if not _within(release, target) or not (target.is_file() or target.is_dir() or target.is_symlink()):
        raise CacheError("material_path_not_found")
    (ROOT / key).touch()
    return target


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "InsightBloomMaterialCache/2"

    def _json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urlsplit(self.path)
        if parsed.path == "/health":
            self._json(200, {"status": "ok", "cacheBytes": _cache_size(), "cacheLimitBytes": MAX_CACHE_BYTES})
            return
        if parsed.path == "/metrics" and not parsed.query:
            payload = ("# HELP insightbloom_material_cache_bytes Bytes currently used by cached material files.\n"
                       "# TYPE insightbloom_material_cache_bytes gauge\n"
                       f"insightbloom_material_cache_bytes {_cache_size()}\n"
                       "# HELP insightbloom_material_cache_limit_bytes Configured cache usage ceiling.\n"
                       "# TYPE insightbloom_material_cache_limit_bytes gauge\n"
                       f"insightbloom_material_cache_limit_bytes {MAX_CACHE_BYTES}\n"
                       "# HELP insightbloom_material_cache_source_ttl_seconds Inactive source retention.\n"
                       "# TYPE insightbloom_material_cache_source_ttl_seconds gauge\n"
                       f"insightbloom_material_cache_source_ttl_seconds {SOURCE_TTL_SECONDS}\n").encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        if parsed.query or not parsed.path.startswith("/material/"):
            self.send_error(404)
            return
        try:
            parts = parsed.path.split("/", 4)
            if len(parts) != 5:
                raise CacheError("invalid_material_path")
            key, revision = parts[2], parts[3]
            relative = unquote(parts[4])
            target = resolve_material(key, revision, relative)
        except (CacheError, FileNotFoundError, OSError) as exc:
            message = str(exc) if isinstance(exc, CacheError) else "material_not_found"
            self._json(404, {"error": message})
            return
        try:
            self.send_response(200)
            self.send_header("Content-Type", "application/x-tar")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Connection", "close")
            self.end_headers()
            with tarfile.open(fileobj=self.wfile, mode="w|") as archive:
                if target.is_dir() and not target.is_symlink():
                    for child in sorted(target.iterdir(), key=lambda item: item.name):
                        archive.add(child, arcname=child.name, recursive=True)
                else:
                    archive.add(target, arcname=target.name, recursive=False)
        except (OSError, tarfile.TarError):
            # Headers/stream may already be sent; close the response rather than corrupting the tar.
            self.close_connection = True

    def do_POST(self):
        if urlsplit(self.path).path != "/sync":
            self.send_error(404)
            return
        supplied = self.headers.get("Authorization", "")
        expected = f"Bearer {SYNC_TOKEN}" if SYNC_TOKEN else ""
        if not expected or not hmac.compare_digest(supplied, expected):
            self._json(403, {"error": "forbidden"})
            return
        try:
            try:
                size = int(self.headers.get("Content-Length", "0"))
            except ValueError as exc:
                raise CacheError("invalid_request") from exc
            if size <= 0 or size > 8192:
                raise CacheError("invalid_request")
            body = json.loads(self.rfile.read(size))
            key, revision = sync(body["url"], body.get("ref") or "main")
            self._json(200, {"sourceKey": key, "revision": revision})
        except CacheError as exc:
            self._json(422, {"error": str(exc)})
        except (KeyError, TypeError, json.JSONDecodeError):
            self._json(400, {"error": "invalid_request"})
        except subprocess.CalledProcessError:
            self._json(502, {"error": "github_sync_failed"})
        except Exception:
            self._json(500, {"error": "material_sync_failed"})

    def do_PUT(self):
        self.send_error(405)

    def do_DELETE(self):
        self.send_error(405)

    def log_message(self, fmt, *args):
        print("material-cache:", fmt % args, flush=True)


if __name__ == "__main__":
    ROOT.mkdir(parents=True, exist_ok=True)
    http.server.ThreadingHTTPServer(("0.0.0.0", int(os.environ.get("PORT", "8092"))), Handler).serve_forever()
