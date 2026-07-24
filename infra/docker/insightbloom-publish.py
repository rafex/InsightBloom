#!/usr/bin/env python3
"""CLI minimo para publicar un sitio estatico desde un sandbox InsightBloom.

No instala dependencias y no ejecuta package.json. El backend vuelve a auditar el ZIP y publica
una copia temporal aislada; este cliente solo empaqueta la carpeta indicada y solicita el preview.
"""
from __future__ import annotations

import argparse
import getpass
import json
import os
from pathlib import Path
import sys
import tempfile
import urllib.error
import urllib.request
import zipfile

DEFAULT_API_BASE = "https://insightbloom.v1.rafex.cloud/api/users/api/v1"
DEFAULT_SESSION_FILE = Path("~/.config/insightbloom/session.json").expanduser()
CLI_VERSION = "2026.07-login-session"
MAX_FILES = 1000
MAX_BYTES = 250 * 1024 * 1024
EXCLUDED_DIRS = {".git", ".hg", ".svn", "node_modules", ".venv", "__pycache__", ".insightbloom"}
EXCLUDED_FILES = {
    "package.json", "package-lock.json", "pnpm-lock.yaml", "yarn.lock",
    "vite.config.js", "vite.config.ts", "webpack.config.js", "dockerfile",
}

CLI_EPILOG = """
Ejemplos:
  1) Publicar desde un sandbox (el evento se detecta automáticamente):
     insightbloom login
     insightbloom publish

  2) Publicar una carpeta concreta:
     insightbloom publish --root sitio

  3) Usar una carpeta concreta sin copiar el UUID del evento:
     insightbloom publish --root sitio

  4) Revocar una publicación:
     insightbloom revoke PUBLICATION_ID

Notas:
  - La carpeta publicada debe contener index.html.
  - package.json es opcional y nunca se ejecuta.
  - La publicación es temporal, estática y se vuelve a auditar en el servidor.
  - Dentro de un sandbox el evento se detecta automáticamente desde CONFERENCE_UUID.
  - En el primer uso puedes ejecutar `insightbloom login`; solo se guarda un token en
    ~/.config/insightbloom/session.json, nunca la contraseña ni dentro del workspace.
  - Si la sesión expira durante publish o revoke, el CLI solicita login una sola vez y reintenta.
  - También se conserva --token-prompt para introducir un token manual oculto.
  - También puedes consultar la ayuda específica con:
     insightbloom publish --help
     insightbloom revoke --help
"""


def fail(message: str, code: int = 2) -> None:
    print(f"insightbloom: {message}", file=sys.stderr)
    raise SystemExit(code)


class ApiError(Exception):
    """Error HTTP conservando el código para poder renovar una sesión una sola vez."""

    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


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


def session_file() -> Path:
    configured = os.environ.get("INSIGHTBLOOM_SESSION_FILE", "").strip()
    return Path(configured).expanduser() if configured else DEFAULT_SESSION_FILE


def read_saved_session() -> dict:
    path = session_file()
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        return {}
    except (OSError, json.JSONDecodeError):
        return {}
    return value if isinstance(value, dict) and isinstance(value.get("token"), str) else {}


def save_session(token: str, username: str, expires_at: str | None) -> None:
    path = session_file()
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        try:
            os.chmod(path.parent, 0o700)
        except OSError:
            pass
        path.write_text(json.dumps({
            "token": token,
            "username": username,
            "expiresAt": expires_at,
        }, ensure_ascii=False) + "\n", encoding="utf-8")
        os.chmod(path, 0o600)
    except OSError as exc:
        fail(f"no se pudo guardar la sesión fuera del workspace: {exc}", 1)


def clear_session() -> None:
    try:
        session_file().unlink(missing_ok=True)
    except OSError as exc:
        fail(f"no se pudo cerrar la sesión guardada: {exc}", 1)


def read_token(args: argparse.Namespace) -> str:
    # La sesión guardada es la vía normal. La variable queda como fallback explícito
    # para automatizaciones heredadas, no como requisito para login/publish interactivo.
    token = args.token or ""
    if args.token_prompt:
        token = getpass.getpass("Token de sesión (oculto): ").strip()
    elif args.token_stdin:
        token = sys.stdin.readline().strip()
    if not token:
        token = read_saved_session().get("token", "")
    if not token:
        token = os.environ.get("INSIGHTBLOOM_TOKEN", "")
    return token


def conference_id(args: argparse.Namespace) -> str:
    value = (
        args.conference_id
        or os.environ.get("CONFERENCE_UUID", "")
        or os.environ.get("INSIGHTBLOOM_CONFERENCE_ID", "")
    )
    if not value:
        fail("no se pudo detectar el evento; usa --conference-id UUID_DEL_EVENTO fuera del sandbox")
    return value


def response_error(exc: urllib.error.HTTPError) -> ApiError:
    raw = exc.read().decode("utf-8", errors="replace")
    try:
        detail = json.loads(raw)
    except json.JSONDecodeError:
        detail = {"error": raw or exc.reason}
    message = detail.get("message") or detail.get("error") or str(exc.reason)
    return ApiError(exc.code, str(message))


def request_json(method: str, url: str, token: str | None = None, body: bytes | None = None) -> dict:
    request = urllib.request.Request(url, data=body, method=method)
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    if body is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        raise response_error(exc) from exc
    except urllib.error.URLError as exc:
        raise ApiError(0, f"no se pudo contactar la API: {exc.reason}") from exc
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        raise ApiError(0, "la API devolvió una respuesta no válida")


def api_base() -> str:
    return os.environ.get("INSIGHTBLOOM_API_BASE_URL", DEFAULT_API_BASE).rstrip("/")


def login_session(api: str, username: str | None = None) -> str:
    try:
        login_name = username.strip() if username else input("Usuario o correo: ").strip()
    except (EOFError, KeyboardInterrupt):
        fail("inicio de sesión cancelado")
    if not login_name:
        fail("el usuario o correo no puede estar vacío")
    try:
        password = getpass.getpass("Contraseña (oculta): ")
    except (EOFError, KeyboardInterrupt):
        fail("inicio de sesión cancelado")
    if not password:
        fail("la contraseña no puede estar vacía")
    payload = json.dumps({"username": login_name, "password": password}).encode("utf-8")
    try:
        result = request_json("POST", f"{api}/auth/login", body=payload)
    except ApiError as exc:
        fail(f"no se pudo iniciar sesión ({exc.status or 'red'}): {exc.message}", 1)
    data = result.get("data", result) if isinstance(result, dict) else {}
    token = data.get("token") if isinstance(data, dict) else None
    if not isinstance(token, str) or not token.strip():
        fail("la API de inicio de sesión no devolvió un token", 1)
    save_session(token.strip(), login_name, data.get("expiresAt"))
    print(f"Sesión iniciada para {login_name}. Se guardó solo el token en {session_file()}.", file=sys.stderr)
    return token.strip()


def authenticated_request(args: argparse.Namespace, operation, api: str):
    token = read_token(args)
    if not token:
        token = login_session(api)
    try:
        return operation(token)
    except ApiError as exc:
        if exc.status != 401:
            fail(f"la API rechazó la solicitud ({exc.status or 'red'}): {exc.message}", 1)
        print("La sesión expiró o dejó de ser válida; inicia sesión nuevamente.", file=sys.stderr)
        token = login_session(api)
        try:
            return operation(token)
        except ApiError as retry_exc:
            fail(f"la API rechazó la solicitud después de iniciar sesión ({retry_exc.status or 'red'}): {retry_exc.message}", 1)


def publish(args: argparse.Namespace) -> None:
    workspace = Path(args.root).expanduser().resolve()
    publish_root, configured_entry = read_config(workspace)
    if configured_entry:
        requested_entry = publish_root / configured_entry
        if not requested_entry.is_file():
            fail(f"no existe publish.entry: {configured_entry}")
    zip_path, count, bytes_total = create_zip(publish_root)
    try:
        api = api_base()
        conference = conference_id(args)
        payload = zip_path.read_bytes()
        def send(token: str):
            request = urllib.request.Request(
                f"{api}/conferences/{conference}/sandbox/preview",
                data=payload,
                method="POST",
            )
            request.add_header("Authorization", f"Bearer {token}")
            request.add_header("Content-Type", "application/zip")
            try:
                with urllib.request.urlopen(request, timeout=90) as response:
                    raw = response.read().decode("utf-8")
                    try:
                        return json.loads(raw)
                    except json.JSONDecodeError as exc:
                        raise ApiError(0, "la API devolvió una respuesta no válida") from exc
            except urllib.error.HTTPError as exc:
                raise response_error(exc) from exc
            except urllib.error.URLError as exc:
                raise ApiError(0, f"no se pudo contactar la API: {exc.reason}") from exc

        result = authenticated_request(args, send, api)
        result["localFiles"] = count
        result["localBytes"] = bytes_total
        print(json.dumps(result, ensure_ascii=False, indent=2))
    finally:
        zip_path.unlink(missing_ok=True)


def revoke(args: argparse.Namespace) -> None:
    api = api_base()
    conference = conference_id(args)
    if not args.publication_id:
        fail("revoke requiere el publicationId devuelto por publish")
    result = authenticated_request(
        args,
        lambda token: request_json(
            "DELETE",
            f"{api}/conferences/{conference}/sandbox/preview/{args.publication_id}",
            token,
        ),
        api,
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))


def login(args: argparse.Namespace) -> None:
    login_session(api_base(), args.username)


def logout(_: argparse.Namespace) -> None:
    clear_session()
    print("Sesión local cerrada. La contraseña nunca se almacenó.")


def main() -> None:
    parser = argparse.ArgumentParser(
        prog="insightbloom",
        description="Publica un sitio estático temporal en InsightBloom",
        epilog=CLI_EPILOG,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--version", action="version", version=f"insightbloom {CLI_VERSION}")
    sub = parser.add_subparsers(dest="command")
    login_parser = sub.add_parser(
        "login",
        help="inicia sesión y guarda el token fuera del workspace",
        description="Solicita usuario y contraseña sin exponerlos en el historial.",
    )
    login_parser.add_argument("--username", help="usuario o correo; la contraseña siempre se solicita de forma oculta")
    login_parser.set_defaults(func=login)
    logout_parser = sub.add_parser(
        "logout",
        help="elimina el token guardado localmente",
        description="Borra la sesión guardada por el CLI; no revoca sesiones del servidor.",
    )
    logout_parser.set_defaults(func=logout)
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
    publish_parser.add_argument("--token", help="token puntual de sesión; normalmente usa insightbloom login")
    token_group = publish_parser.add_mutually_exclusive_group()
    token_group.add_argument("--token-prompt", action="store_true", help="solicita el token oculto, sin variable ni historial")
    token_group.add_argument("--token-stdin", action="store_true", help="lee el token desde stdin para automatizaciones")
    publish_parser.add_argument(
        "--conference-id",
        help="UUID del evento; dentro del sandbox se detecta automáticamente desde CONFERENCE_UUID",
    )
    publish_parser.set_defaults(func=publish)
    revoke_parser = sub.add_parser(
        "revoke",
        help="revoca una publicación propia",
        description="Revoca una URL temporal usando el publicationId devuelto por publish.",
        epilog="Ejemplo: insightbloom revoke PUBLICATION_ID",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    revoke_parser.add_argument("publication_id")
    revoke_parser.add_argument("--token", help="token puntual de sesión; normalmente usa insightbloom login")
    revoke_token_group = revoke_parser.add_mutually_exclusive_group()
    revoke_token_group.add_argument("--token-prompt", action="store_true", help="solicita el token oculto, sin variable ni historial")
    revoke_token_group.add_argument("--token-stdin", action="store_true", help="lee el token desde stdin para automatizaciones")
    revoke_parser.add_argument(
        "--conference-id",
        help="UUID del evento; dentro del sandbox se detecta automáticamente desde CONFERENCE_UUID",
    )
    revoke_parser.set_defaults(func=revoke)
    args = parser.parse_args()
    if not args.command:
        parser.print_help(sys.stderr)
        fail("falta el subcomando; usa 'insightbloom login', 'insightbloom publish' o 'insightbloom revoke PUBLICATION_ID'")
    args.func(args)


if __name__ == "__main__":
    main()
