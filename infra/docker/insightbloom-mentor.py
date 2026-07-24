#!/usr/bin/env python3
"""Cliente CLI del tutor IA de programación de InsightBloom.

La sesión se guarda fuera del workspace, igual que el publicador. La clave del LLM
nunca se copia al sandbox: el CLI solo llama al endpoint autenticado del evento.
"""
from __future__ import annotations

import argparse
import getpass
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request

DEFAULT_USERS_API = "https://insightbloom.v1.rafex.cloud/api/users/api/v1"
DEFAULT_SURVEY_API = "https://insightbloom.v1.rafex.cloud/api/survey/api/v1"
SESSION_FILE = Path("~/.config/insightbloom/session.json").expanduser()
MAX_CODE = 12_000


class ApiError(Exception):
    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


def fail(message: str, code: int = 2) -> None:
    print(f"insightbloom: {message}", file=sys.stderr)
    raise SystemExit(code)


def session_path() -> Path:
    return Path(os.environ.get("INSIGHTBLOOM_SESSION_FILE", str(SESSION_FILE))).expanduser()


def saved_token() -> str:
    try:
        data = json.loads(session_path().read_text(encoding="utf-8"))
        return data.get("token", "") if isinstance(data, dict) else ""
    except (OSError, json.JSONDecodeError):
        return ""


def save_token(token: str, username: str) -> None:
    path = session_path()
    try:
        path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        path.write_text(json.dumps({"token": token, "username": username}, ensure_ascii=False) + "\n", encoding="utf-8")
        os.chmod(path, 0o600)
    except OSError as exc:
        fail(f"no se pudo guardar la sesión fuera del workspace: {exc}", 1)


def api_json(method: str, url: str, token: str | None = None, payload: dict | None = None) -> dict:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload is not None else None
    request = urllib.request.Request(url, data=body, method=method)
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    if body:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            detail = json.loads(raw)
        except json.JSONDecodeError:
            detail = {}
        raise ApiError(exc.code, detail.get("message") or detail.get("error") or str(exc.reason)) from exc
    except urllib.error.URLError as exc:
        raise ApiError(0, f"no se pudo contactar la API: {exc.reason}") from exc
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        raise ApiError(0, "la API devolvió una respuesta no válida") from exc


def login() -> str:
    try:
        username = input("Usuario o correo: ").strip()
        password = getpass.getpass("Contraseña (oculta): ")
    except (EOFError, KeyboardInterrupt):
        fail("inicio de sesión cancelado")
    if not username or not password:
        fail("usuario y contraseña son obligatorios")
    try:
        result = api_json("POST", f"{os.environ.get('INSIGHTBLOOM_USERS_API', DEFAULT_USERS_API)}/auth/login",
                          payload={"username": username, "password": password})
    except ApiError as exc:
        fail(f"no se pudo iniciar sesión ({exc.status or 'red'}): {exc.message}", 1)
    data = result.get("data", result)
    token = data.get("token") if isinstance(data, dict) else None
    if not isinstance(token, str) or not token.strip():
        fail("la API de inicio de sesión no devolvió un token", 1)
    save_token(token.strip(), username)
    print(f"Sesión iniciada. Token guardado fuera del workspace en {session_path()}.", file=sys.stderr)
    return token.strip()


def conference_id(args: argparse.Namespace) -> str:
    value = args.conference_id or os.environ.get("CONFERENCE_UUID") or os.environ.get("INSIGHTBLOOM_CONFERENCE_ID")
    if not value:
        fail("no se detectó el evento; usa --conference-id UUID fuera del sandbox")
    return value


def token_for(args: argparse.Namespace) -> str:
    if args.token_prompt:
        return getpass.getpass("Token de sesión (oculto): ").strip()
    return args.token or saved_token() or os.environ.get("INSIGHTBLOOM_TOKEN", "") or login()


def load_code(path: str | None) -> tuple[str, str]:
    if not path:
        return "", ""
    file_path = Path(path).expanduser().resolve()
    if file_path.name.startswith(".env") or file_path.suffix.lower() in {".pem", ".key", ".p12", ".pfx"}:
        fail("por seguridad no se permite enviar archivos de credenciales al tutor")
    try:
        return file_path.name, file_path.read_text(encoding="utf-8")[:MAX_CODE]
    except OSError as exc:
        fail(f"no se pudo leer el archivo: {exc}")


def ask(args: argparse.Namespace, token: str, history: list[dict[str, str]], message: str, file_name: str, code: str) -> tuple[str, str]:
    url = f"{os.environ.get('INSIGHTBLOOM_SURVEY_API', DEFAULT_SURVEY_API)}/conferences/{conference_id(args)}/mentor/chat"
    payload = {"message": message, "history": history}
    if file_name:
        payload["fileName"] = file_name
    if code:
        payload["codeContext"] = code
    try:
        result = api_json("POST", url, token, payload)
    except ApiError as exc:
        if exc.status != 401:
            fail(f"la API rechazó la consulta ({exc.status or 'red'}): {exc.message}", 1)
        print("La sesión expiró; inicia sesión nuevamente.", file=sys.stderr)
        token = login()
        try:
            result = api_json("POST", url, token, payload)
        except ApiError as retry:
            fail(f"la API rechazó la consulta después de iniciar sesión ({retry.status or 'red'}): {retry.message}", 1)
    data = result.get("data", result)
    reply = data.get("reply") if isinstance(data, dict) else None
    if not isinstance(reply, str) or not reply.strip():
        fail("el tutor no devolvió una respuesta", 1)
    return token, reply.strip()


def mentor(args: argparse.Namespace) -> None:
    token = token_for(args)
    file_name, code = load_code(args.file)
    history: list[dict[str, str]] = []
    if args.message:
        token, reply = ask(args, token, history, args.message, file_name, code)
        print(reply)
        return
    print("Tutor IA activo. Escribe una pregunta; Ctrl-D para salir.")
    while True:
        try:
            message = input("\nTú> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return
        if not message:
            continue
        token, reply = ask(args, token, history[-8:], message, file_name, code)
        print(f"\nTutor> {reply}")
        history.extend([{"role": "user", "content": message}, {"role": "assistant", "content": reply}])


def main() -> None:
    parser = argparse.ArgumentParser(
        prog="insightbloom mentor",
        description="Consulta el tutor IA pedagógico del evento sin exponer la clave del LLM.",
        epilog="Ejemplos: insightbloom mentor\n  insightbloom mentor '¿Qué debería revisar primero?' --file src/app.js\n  insightbloom mentor --token-prompt",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = parser.add_subparsers(dest="command")
    login_parser = sub.add_parser("login", help="inicia sesión y guarda el token fuera del workspace")
    login_parser.set_defaults(func=lambda _: login())
    mentor_parser = sub.add_parser("mentor", help="abre el chat del tutor IA")
    mentor_parser.add_argument("message", nargs="?", help="pregunta única; sin ella abre modo interactivo")
    mentor_parser.add_argument("--file", help="archivo actual para incluir como contexto")
    mentor_parser.add_argument("--token", help="token puntual; normalmente no es necesario")
    mentor_parser.add_argument("--token-prompt", action="store_true", help="solicita el token oculto sin guardarlo")
    mentor_parser.add_argument("--conference-id", help="UUID del evento fuera del sandbox")
    mentor_parser.set_defaults(func=mentor)
    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        return
    args.func(args)


if __name__ == "__main__":
    main()
