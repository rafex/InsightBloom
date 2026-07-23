#!/usr/bin/env python3
"""CLI minimo para publicar un sitio estatico desde un sandbox InsightBloom.

No instala dependencias y no ejecuta package.json. El backend vuelve a auditar el ZIP y publica
una copia temporal aislada; este cliente solo empaqueta la carpeta indicada y solicita el preview.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys
import tempfile
import urllib.error
import urllib.request
import zipfile

DEFAULT_API_BASE = "https://insightbloom.v1.rafex.cloud/api/users/api/v1"
MAX_FILES = 1000
MAX_BYTES = 250 * 1024 * 1024
EXCLUDED_DIRS = {".git", ".hg", ".svn", "node_modules", ".venv", "__pycache__", ".insightbloom"}
EXCLUDED_FILES = {
    "package.json", "package-lock.json", "pnpm-lock.yaml", "yarn.lock",
    "vite.config.js", "vite.config.ts", "webpack.config.js", "dockerfile",
}

CLI_EPILOG = """
Ejemplos:
  1) Configurar credenciales y publicar el workspace actual:
     export INSIGHTBLOOM_CONFERENCE_ID=UUID_DEL_EVENTO
     export INSIGHTBLOOM_TOKEN=TOKEN_DE_SESION
     insightbloom publish

  2) Publicar una carpeta concreta:
     insightbloom publish --root sitio

  3) Evitar que el token quede en el historial del shell:
     read -rs INSIGHTBLOOM_TOKEN
     export INSIGHTBLOOM_TOKEN
     insightbloom publish --conference-id UUID_DEL_EVENTO

  4) Revocar una publicación:
     insightbloom revoke PUBLICATION_ID

Notas:
  - La carpeta publicada debe contener index.html.
  - package.json es opcional y nunca se ejecuta.
  - La publicación es temporal, estática y se vuelve a auditar en el servidor.
  - También puedes consultar la ayuda específica con:
     insightbloom publish --help
     insightbloom revoke --help
"""


def fail(message: str, code: int = 2) -> None:
    print(f"insightbloom: {message}", file=sys.stderr)
    raise SystemExit(code)


def read_config(root: Path) -> tuple[Path, str | None]:
    config_path = root / "insightbloom.json"
    if not config_path.is_file():
        return root, None
    try:
        config = json.loads(config_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"no se pudo leer {config_path.name}: {exc}")
    publish = config.get("publish", {}) if isinstance(config, dict) else {}
    if not isinstance(publish, dict):
        fail("insightbloom.json: publish debe ser un objeto")
    configured_root = publish.get("root", ".")
    entry = publish.get("entry")
    if not isinstance(configured_root, str) or not configured_root.strip():
        fail("insightbloom.json: publish.root debe ser una ruta relativa")
    candidate = (root / configured_root).resolve()
    try:
        candidate.relative_to(root.resolve())
    except ValueError:
        fail("insightbloom.json: publish.root no puede salir del workspace")
    if entry is not None and (not isinstance(entry, str) or Path(entry).is_absolute() or ".." in Path(entry).parts):
        fail("insightbloom.json: publish.entry debe ser una ruta relativa")
    return candidate, entry


def create_zip(root: Path) -> tuple[Path, int, int]:
    if not root.is_dir():
        fail(f"la carpeta publicada no existe: {root}")
    root = root.resolve()
    files: list[Path] = []
    total = 0
    for current, directories, names in os.walk(root, followlinks=False):
        directories[:] = sorted(d for d in directories if d not in EXCLUDED_DIRS and not d.startswith("."))
        for name in sorted(names):
            if name.startswith(".") or name.lower() in EXCLUDED_FILES:
                continue
            path = Path(current) / name
            if path.is_symlink() or not path.is_file():
                continue
            try:
                size = path.stat().st_size
            except OSError:
                continue
            total += size
            if total > MAX_BYTES:
                fail("el sitio supera el límite de 250 MiB")
            files.append(path)
            if len(files) > MAX_FILES:
                fail("el sitio supera el límite de 1000 archivos")
    if not any(path.name.lower() in {"index.html", "index.htm"} for path in files):
        fail("no se encontró index.html en la carpeta publicada")
    handle = tempfile.NamedTemporaryFile(prefix="insightbloom-site-", suffix=".zip", delete=False)
    zip_path = Path(handle.name)
    handle.close()
    try:
        with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for path in files:
                archive.write(path, path.relative_to(root).as_posix())
    except Exception:
        zip_path.unlink(missing_ok=True)
        raise
    return zip_path, len(files), total


def read_token(args: argparse.Namespace) -> str:
    token = args.token or os.environ.get("INSIGHTBLOOM_TOKEN", "")
    if args.token_stdin:
        token = sys.stdin.readline().strip()
    if not token:
        fail("falta INSIGHTBLOOM_TOKEN; usa una variable de entorno o --token-stdin")
    return token


def conference_id(args: argparse.Namespace) -> str:
    value = args.conference_id or os.environ.get("INSIGHTBLOOM_CONFERENCE_ID", "")
    if not value:
        fail("falta INSIGHTBLOOM_CONFERENCE_ID")
    return value


def request_json(method: str, url: str, token: str, body: bytes | None = None) -> dict:
    request = urllib.request.Request(url, data=body, method=method)
    request.add_header("Authorization", f"Bearer {token}")
    if body is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            detail = json.loads(raw)
        except json.JSONDecodeError:
            detail = {"error": raw or exc.reason}
        fail(f"la API rechazó la solicitud ({exc.code}): {detail.get('message') or detail.get('error')}", 1)
    except urllib.error.URLError as exc:
        fail(f"no se pudo contactar la API: {exc.reason}", 1)
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        fail("la API devolvió una respuesta no válida", 1)


def publish(args: argparse.Namespace) -> None:
    workspace = Path(args.root).expanduser().resolve()
    publish_root, configured_entry = read_config(workspace)
    if configured_entry:
        requested_entry = publish_root / configured_entry
        if not requested_entry.is_file():
            fail(f"no existe publish.entry: {configured_entry}")
    zip_path, count, bytes_total = create_zip(publish_root)
    try:
        token = read_token(args)
        conference = conference_id(args)
        api = os.environ.get("INSIGHTBLOOM_API_BASE_URL", DEFAULT_API_BASE).rstrip("/")
        payload = zip_path.read_bytes()
        request = urllib.request.Request(
            f"{api}/conferences/{conference}/sandbox/preview",
            data=payload,
            method="POST",
        )
        request.add_header("Authorization", f"Bearer {token}")
        request.add_header("Content-Type", "application/zip")
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                result = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            try:
                detail = json.loads(raw)
            except json.JSONDecodeError:
                detail = {"error": raw or exc.reason}
            fail(f"la API rechazó la publicación ({exc.code}): {detail.get('message') or detail.get('error')}", 1)
        except urllib.error.URLError as exc:
            fail(f"no se pudo contactar la API: {exc.reason}", 1)
        result["localFiles"] = count
        result["localBytes"] = bytes_total
        print(json.dumps(result, ensure_ascii=False, indent=2))
    finally:
        zip_path.unlink(missing_ok=True)


def revoke(args: argparse.Namespace) -> None:
    token = read_token(args)
    conference = conference_id(args)
    if not args.publication_id:
        fail("revoke requiere el publicationId devuelto por publish")
    api = os.environ.get("INSIGHTBLOOM_API_BASE_URL", DEFAULT_API_BASE).rstrip("/")
    result = request_json(
        "DELETE",
        f"{api}/conferences/{conference}/sandbox/preview/{args.publication_id}",
        token,
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(
        prog="insightbloom",
        description="Publica un sitio estático temporal en InsightBloom",
        epilog=CLI_EPILOG,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = parser.add_subparsers(dest="command")
    publish_parser = sub.add_parser(
        "publish",
        help="publica index.html y sus assets locales",
        description="Empaqueta y publica una copia temporal del sitio estático.",
        epilog=(
            "Ejemplo: insightbloom publish --root sitio\n"
            "La carpeta debe contener index.html."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    publish_parser.add_argument("--root", default=".", help="carpeta del sitio o workspace (default: .)")
    publish_parser.add_argument("--token", help="token de sesión; se recomienda INSIGHTBLOOM_TOKEN")
    publish_parser.add_argument("--token-stdin", action="store_true", help="lee el token desde stdin sin dejarlo en el historial")
    publish_parser.add_argument("--conference-id", help="UUID del evento; también INSIGHTBLOOM_CONFERENCE_ID")
    publish_parser.set_defaults(func=publish)
    revoke_parser = sub.add_parser(
        "revoke",
        help="revoca una publicación propia",
        description="Revoca una URL temporal usando el publicationId devuelto por publish.",
        epilog="Ejemplo: insightbloom revoke PUBLICATION_ID",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    revoke_parser.add_argument("publication_id")
    revoke_parser.add_argument("--token", help="token de sesión; se recomienda INSIGHTBLOOM_TOKEN")
    revoke_parser.add_argument("--token-stdin", action="store_true", help="lee el token desde stdin")
    revoke_parser.add_argument("--conference-id", help="UUID del evento; también INSIGHTBLOOM_CONFERENCE_ID")
    revoke_parser.set_defaults(func=revoke)
    args = parser.parse_args()
    if not args.command:
        parser.print_help(sys.stderr)
        fail("falta el subcomando; usa 'insightbloom publish' para publicar o 'insightbloom revoke PUBLICATION_ID' para revocar")
    args.func(args)


if __name__ == "__main__":
    main()
