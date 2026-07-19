"""API de archivos del workspace, compartida por sandbox-agent.py (imagen neovim/CLI, multi-
asiento) y sandbox-file-agent.py (imagen debian/Web, un solo asiento) -- Fase 4 del dashboard de
moderador (DEC-0025 area): listar/leer/escribir archivos dentro del workspace de UN alumno, para
el visor Monaco liviano del organizador (ver ListWorkspaceFilesUseCase/ReadWorkspaceFileUseCase/
WriteWorkspaceFileUseCase en insightbloom-users).

Alcanzable SOLO desde insightbloom-users (mismo puerto de control y NetworkPolicy que ya usa
sandbox-agent.py para /seats -- ver KubernetesPodClient.ensureIngressPolicy, segunda regla de
Ingress). Nada de esto se expone al alumno ni al gateway publico.

Seguridad: todo path pedido por la red se resuelve SIEMPRE contra una raiz fija (el workspace de
UN alumno) y se rechaza si el resultado resuelto (realpath, sigue symlinks) queda fuera de esa
raiz -- bloquea "../../etc/passwd" y symlinks armados a mano por el propio alumno apuntando fuera
de su home.
"""
from __future__ import annotations

import io
import os
import zipfile

# Techos deliberadamente chicos -- este es un visor "super ligero" para inspeccionar/corregir
# archivos de curso (.java/.py/.js chicos), no un explorador de archivos de proposito general.
MAX_LIST_ENTRIES = 2000
MAX_FILE_BYTES = 2 * 1024 * 1024  # 2 MiB
# Tope del ZIP completo del workspace (descarga del alumno, ver GenerateWorkspaceDownloadUrlUseCase)
# -- mas generoso que MAX_FILE_BYTES porque es la suma de TODO el workspace, no un archivo
# individual, pero sigue acotado para no dejar que un alumno tire abajo su propio Pod (limite de
# memoria del contenedor) armando un zip gigante en memoria.
MAX_ZIP_BYTES = 50 * 1024 * 1024  # 50 MiB


class PathTraversalError(Exception):
    pass


class NotFoundError(Exception):
    pass


class FileConflictError(Exception):
    """El archivo cambio en el filesystem desde que el caller leyo su mtime -- ver write_file."""
    pass


class FileTooLargeError(Exception):
    pass


class NotTextFileError(Exception):
    pass


class WorkspaceTooLargeError(Exception):
    pass


def resolve_safe_path(root: str, requested_path: str) -> str:
    """Resuelve `requested_path` (relativo, viene de la red) contra `root` (raiz fija del
    workspace de un alumno) y devuelve el path absoluto real -- sigue symlinks (realpath) para
    que un symlink armado a mano por el alumno apuntando fuera de su home tambien se rechace,
    no solo un "../" literal en el string."""
    requested_path = (requested_path or "").lstrip("/")
    root_real = os.path.realpath(root)
    candidate = os.path.realpath(os.path.join(root_real, requested_path))
    if candidate != root_real and not candidate.startswith(root_real + os.sep):
        raise PathTraversalError(f"path fuera de la raiz permitida: {requested_path}")
    return candidate


def list_directory(root: str, requested_path: str = "") -> list[dict]:
    """Listado recursivo (con techo MAX_LIST_ENTRIES) de `requested_path` dentro de `root`.
    Cada entrada es relativa a `root` (nunca expone el path absoluto real del filesystem)."""
    root_real = os.path.realpath(root)
    base = resolve_safe_path(root, requested_path)
    if not os.path.isdir(base):
        raise NotFoundError(f"no es un directorio: {requested_path}")

    entries: list[dict] = []
    for dirpath, dirnames, filenames in os.walk(base):
        # No descender a directorios ocultos (.git, .config, etc.) -- ruido irrelevante para el
        # curso y potencialmente sensible (credenciales de git, config del editor).
        dirnames[:] = [d for d in dirnames if not d.startswith(".")]
        for name in sorted(dirnames) + sorted(filenames):
            if name.startswith("."):
                continue
            abs_entry = os.path.join(dirpath, name)
            # Relativo a la raiz RESUELTA (root_real), no al string original de "root" -- si
            # "root" llega con un symlink en el medio (comun, ej. macOS /var -> /private/var, o
            # cualquier montaje de volumen que Kubernetes resuelva distinto), relpath contra el
            # string sin resolver arma un path relativo absurdo lleno de "../".
            rel_entry = os.path.relpath(abs_entry, root_real)
            try:
                st = os.stat(abs_entry)
            except OSError:
                continue
            entries.append({
                "path": rel_entry,
                "isDirectory": os.path.isdir(abs_entry),
                "mtime": st.st_mtime,
                "sizeBytes": st.st_size if not os.path.isdir(abs_entry) else 0,
            })
            if len(entries) >= MAX_LIST_ENTRIES:
                return entries
    return entries


def read_file(root: str, requested_path: str) -> dict:
    abs_path = resolve_safe_path(root, requested_path)
    if not os.path.isfile(abs_path):
        raise NotFoundError(f"no existe: {requested_path}")
    st = os.stat(abs_path)
    if st.st_size > MAX_FILE_BYTES:
        raise FileTooLargeError(f"archivo demasiado grande ({st.st_size} bytes)")
    with open(abs_path, "rb") as f:
        raw = f.read()
    try:
        content = raw.decode("utf-8")
    except UnicodeDecodeError as e:
        raise NotTextFileError("el archivo no es texto UTF-8 (binario)") from e
    return {"content": content, "mtime": st.st_mtime}


def write_file(root: str, requested_path: str, content: str, expected_mtime: float | None) -> dict:
    """Si `expected_mtime` viene y ya no coincide con el mtime real (el alumno edito el archivo
    entre que el moderador lo leyo y lo guardo), lanza FileConflictError en vez de pisarlo --
    ver WriteWorkspaceFileUseCase para el flujo de "Guardar de todas formas" del lado Java."""
    abs_path = resolve_safe_path(root, requested_path)
    if os.path.exists(abs_path) and expected_mtime is not None:
        current_mtime = os.stat(abs_path).st_mtime
        # Comparacion EXACTA a proposito, no con tolerancia -- dos escrituras reales (alumno y
        # moderador) pueden caer dentro del mismo milisegundo, que es justo el caso que esto
        # existe para detectar; cualquier tolerancia mayor que el ruido de punto flotante del
        # propio float (st_mtime ya es un IEEE-754 double, sobrevive intacto el viaje a JSON y
        # de vuelta) terminaria enmascarando conflictos reales en vez de solo ruido.
        if current_mtime != expected_mtime:
            raise FileConflictError(
                f"el archivo cambio desde que se leyo (actual={current_mtime}, esperado={expected_mtime})")
    os.makedirs(os.path.dirname(abs_path), exist_ok=True)
    with open(abs_path, "wb") as f:
        f.write(content.encode("utf-8"))
    return {"mtime": os.stat(abs_path).st_mtime}


def build_workspace_zip(root: str) -> bytes:
    """Arma un .zip en memoria con TODO el workspace (mismas exclusiones de archivos/carpetas
    ocultas que list_directory) -- descarga completa que el alumno pide desde el IDE, ver
    GenerateWorkspaceDownloadUrlUseCase/DownloadWorkspaceZipUseCase del lado Java."""
    root_real = os.path.realpath(root)
    if not os.path.isdir(root_real):
        raise NotFoundError("workspace no encontrado")

    buffer = io.BytesIO()
    total_bytes = 0
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as zf:
        for dirpath, dirnames, filenames in os.walk(root_real):
            dirnames[:] = [d for d in dirnames if not d.startswith(".")]
            for name in filenames:
                if name.startswith("."):
                    continue
                abs_entry = os.path.join(dirpath, name)
                try:
                    size = os.path.getsize(abs_entry)
                except OSError:
                    continue
                total_bytes += size
                if total_bytes > MAX_ZIP_BYTES:
                    raise WorkspaceTooLargeError(f"workspace demasiado grande (> {MAX_ZIP_BYTES} bytes)")
                rel_entry = os.path.relpath(abs_entry, root_real)
                try:
                    zf.write(abs_entry, rel_entry)
                except OSError:
                    continue
    return buffer.getvalue()
