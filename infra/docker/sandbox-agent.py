#!/usr/bin/env python3
"""Seat agent para Pods "neovim" multi-alumno (Fase B, 2026-07).

Corre como el proceso principal del contenedor "sandbox" cuando el Pod aloja mas de un
asiento (ver KubernetesPodClient.buildPodBody, terminalMode && effectiveSeats > 1). Reemplaza
al `ttyd` directo que usan los Pods de un solo asiento.

Por que este proceso corre como root (con capabilities limitadas, ver
KubernetesPodClient.containerSecurityContext): crear la cuenta Linux real de cada alumno
("/home/{userUuid}/workspace", un asiento = un usuario real, no una cuenta generica
pre-creada) y arrancar su `ttyd`+`nvim` bajo ese uid especifico requiere escribir en
/etc/passwd,/etc/shadow,/etc/group (adduser) y despues cambiar de uid/gid (setuid/setgid) antes
de exec -- el kernel de Linux solo permite esto si el proceso ya es root o tiene las
capabilities especificas (CAP_CHOWN/CAP_SETUID/CAP_SETGID). Deliberadamente NO se usa sudo: eso
exigiria `allowPrivilegeEscalation: true` en el Pod (reabre CUALQUIER binario setuid/setgid de
la imagen, no solo este) y una politica de sudoers en texto (mas facil de dejar una rendija de
inyeccion de argumentos que una lista corta de capabilities que el kernel aplica por syscall).
Ver DEC-0025 para la discusion completa.

Cada proceso hijo (`ttyd`, y todo lo que el alumno corra dentro) DROPEA esos privilegios antes
de `exec` (ver `_spawn_seat`) -- nunca corren como root, solo este agente lo hace, y solo
mientras administra cuentas/asientos (nunca ejecuta codigo del alumno el mismo).

Endpoints (solo alcanzables desde insightbloom-users, ver NetworkPolicy en
KubernetesPodClient.ensureIngressPolicy -- no hay token adicional, el limite de seguridad real
es esa policy):
  GET  /health           -> 200 si el agente esta vivo
  POST /seats/{index}    -> {"userUuid": "..."} crea (si no existe) la cuenta Linux real del
                             alumno y su ttyd. Idempotente: si ya esta corriendo, no hace nada.

Vigilancia de recursos (Fase C, DEC-0025): dos lineas de defensa contra un alumno que acapara
recursos del Pod compartido, a proposito o por accidente (loop infinito, fork-bomb):
  1. `ulimit -u` (RLIMIT_NPROC) por asiento, aplicado ANTES de exec en `_drop_privileges` -- un
     fork-bomb pega contra esto al instante (EAGAIN del kernel), sin depender de que nadie lo
     note a tiempo.
  2. Un watchdog en background (`_watchdog_loop`) que cada WATCHDOG_INTERVAL_SECONDS lee
     /proc/*/status y /proc/*/stat, agrupa por UID de asiento, y mata + reinicia el `ttyd` de
     cualquier asiento que sostenga uso de CPU/memoria muy por encima de su presupuesto justo
     (limite del Pod dividido por asientos activos). Reporta el incidente a
     insightbloom-users (mejor esfuerzo -- si el POST falla, solo se loguea localmente, nunca
     tumba el watchdog) para que el organizador lo vea en el Dashboard.
"""
import http.server
import json
import os
import pwd
import re
import resource
import sandbox_file_api
import signal
import socketserver
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

WORKSPACE_ROOT = "/home"
NVIM_CONFIG_SOURCE = "/etc/insightbloom/nvim-init.lua"
NODE_TYPES_SEEDER = "/usr/local/bin/seed-node-types.sh"
SEAT_UID_BASE = 2000
# ulimit -u por asiento (defensa dura contra fork-bombs, ver Fase C): un fork-bomb pega contra
# esto de inmediato (EAGAIN), sin depender de que el watchdog lo note a tiempo.
SEAT_MAX_PROCESSES = 100
# Formato real de UUID (dev.rafex.insightbloom.users.domain.model.User#uuid, java.util.UUID
# random). Validado ESTRICTO antes de usarlo en cualquier ruta de filesystem o argumento de
# comando -- este valor llega desde la red (body del POST), nunca confiar en el formato.
UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", re.IGNORECASE)

_lock = threading.Lock()
# index (int) -> {"process": subprocess.Popen, "userUuid": str, "uid": int}
_seats: dict[int, dict] = {}


def _seat_login_name(index: int) -> str:
    # Nombre de cuenta CORTO (no el UUID -- 36 caracteres excede el limite clasico de 32 de
    # username en la mayoria de los sistemas); el UUID real se usa para el HOME DIR, que no
    # tiene esa restriccion, mas abajo.
    return f"student{index}"


def _seat_uid(index: int) -> int:
    return SEAT_UID_BASE + index


def _seat_home(user_uuid: str) -> str:
    return f"{WORKSPACE_ROOT}/{user_uuid}"


def _drop_privileges(uid: int, gid: int):
    """preexec_fn: corre en el proceso HIJO, entre fork() y exec() -- dropea a un uid/gid no-root
    especifico antes de que el hijo ejecute nada real. El kernel limpia el capability set del
    proceso automaticamente al hacer setuid() lejos de 0, asi que el hijo (y todo lo que el
    alumno corra debajo) queda genuinamente sin privilegios, no solo con un UID distinto."""
    def _fn():
        try:
            resource.setrlimit(resource.RLIMIT_NPROC, (SEAT_MAX_PROCESSES, SEAT_MAX_PROCESSES))
        except (ValueError, resource.error):
            pass  # mejor esfuerzo -- no bloquear el arranque del asiento por esto
        os.setgid(gid)
        os.setgroups([gid])
        os.setuid(uid)
    return _fn


def _user_exists(uid: int) -> bool:
    try:
        pwd.getpwuid(uid)
        return True
    except KeyError:
        return False


def _ensure_seat_account(index: int, user_uuid: str):
    """Idempotente: si el agente se reinicio y este asiento ya tenia cuenta creada de una
    provision anterior, no vuelve a correr adduser (fallaria, uid/nombre ya existen).

    Orden importa: los directorios (home/workspace/.config/nvim) se crean ANTES de `adduser`,
    mientras el proceso (root, sin CAP_DAC_OVERRIDE) todavia es dueno de todo lo que crea. Si
    se creara el home DESPUES via `adduser -h`, quedaria con dueno {uid}:{uid} (no root), y el
    `os.makedirs` posterior para "workspace" fallaria con Permission denied -- root sin
    CAP_DAC_OVERRIDE no puede escribir dentro de un directorio de otro dueno aunque sea root
    (confirmado en vivo). `chown -R` al final entrega todo al alumno de una sola vez.
    """
    uid = _seat_uid(index)
    login = _seat_login_name(index)
    home = _seat_home(user_uuid)
    workspace = f"{home}/workspace"
    nvim_config_dir = f"{home}/.config/nvim"
    os.makedirs(workspace, exist_ok=True)
    os.makedirs(nvim_config_dir, exist_ok=True)
    if not _user_exists(uid):
        subprocess.run(["addgroup", "-g", str(uid), login], check=True)
        subprocess.run(
            ["adduser", "-D", "-u", str(uid), "-G", login, "-h", home, "-s", "/bin/bash", login],
            check=True,
        )
    nvim_init = f"{nvim_config_dir}/init.lua"
    if not os.path.exists(nvim_init) and os.path.exists(NVIM_CONFIG_SOURCE):
        with open(NVIM_CONFIG_SOURCE, "r", encoding="utf-8") as src, open(nvim_init, "w", encoding="utf-8") as dst:
            dst.write(src.read())
    subprocess.run(["chown", "-R", f"{uid}:{uid}", home], check=False)
    # El workspace es un volumen por asiento y no contiene los archivos creados
    # durante el build. Publicar los tipos precargados mediante enlaces mantiene
    # el autocompletado de Node.js/TypeScript sin instalar nada en runtime.
    #
    # Importante: este contenedor elimina CAP_DAC_OVERRIDE. Después del chown el
    # usuario root del agente ya no puede crear archivos dentro de un directorio
    # propiedad del alumno; en producción eso provocaba Permission denied en
    # /workspace/node_modules y el endpoint /seats/{index} devolvía 500. Ejecutar
    # el seeder como el dueño real conserva el hardening y deja los enlaces con el
    # UID/GID correctos desde el principio.
    subprocess.run(
        [NODE_TYPES_SEEDER, workspace],
        preexec_fn=_drop_privileges(uid, uid),
        check=True,
    )
    # El volumen /home se comparte entre los asientos del Pod. El chown evita que
    # otro usuario pueda escribir, pero por sí solo no evita que pueda atravesar y
    # leer el home de un compañero: adduser crea homes 0755 por defecto y los
    # archivos nuevos suelen quedar legibles por "other". Forzar 0750 en cada
    # frontera de confianza hace que el kernel bloquee tanto `ls` como la lectura
    # directa de /home/{otroUuid}/workspace desde el terminal del alumno. El
    # grupo `coder` conserva lectura para el agente de archivos/moderación; los
    # usuarios studentN no pertenecen a ese grupo.
    #
    # Se ejecuta también cuando la cuenta ya existe para reparar homes creados por
    # una versión anterior de la imagen sin tener que borrar el Pod completo.
    # Orden inverso a proposito: root pierde CAP_DAC_OVERRIDE (ver comentario de arriba), asi
    # que en cuanto "home" queda en 0750 root ya no puede ATRAVESARLO para llegar a los hijos
    # (CAP_FOWNER permite el chmod en si, pero no bypassea el permiso de traversal x). Cerrar
    # primero los hijos y "home" al final evita que el propio chmod se bloquee a si mismo a
    # mitad de camino -- confirmado en vivo: con el orden padre-primero, TODO seat_spawn_failed
    # con Permission denied en ".config" (2026-07-26).
    os.chmod(nvim_config_dir, 0o750)
    os.chmod(f"{home}/.config", 0o750)
    os.chmod(workspace, 0o750)
    os.chmod(home, 0o750)
    return uid, home


def _spawn_seat(index: int, base_port: int, user_uuid: str):
    uid, home = _ensure_seat_account(index, user_uuid)
    port = base_port + index
    workspace = f"{home}/workspace"
    # APP_PORT: puerto donde este asiento debe correr su backend/API REST si lo publica (ver
    # PublishAppPreviewUseCase, KubernetesPodClient.buildPodBody) -- mismo patron que SEAT_INDEX
    # ya usa runtime-debug-helpers.sh para los puertos de debug Java/Python por asiento.
    app_base_port = int(os.environ.get("APP_BASE_PORT", "9000"))
    seat_env = {**os.environ, "HOME": home, "USER": _seat_login_name(index), "SEAT_INDEX": str(index),
                "APP_PORT": str(app_base_port + index)}
    # Precreamos la sesion tmux DESTACADA (-d) en un proceso propio, separado del pty que ttyd va
    # a controlar -- si dejamos que ttyd arranque tmux directamente (ej. "ttyd ... tmux new-session
    # -A"), el server de tmux nace todavia compartiendo el process group del pty de ttyd durante su
    # arranque, y el SIGHUP que ttyd manda al cerrar el WebSocket (--close-signal) alcanza tambien
    # al server recien creado antes de que termine de independizarse -- confirmado en vivo: la
    # sesion desaparecia por completo ("tmux ls" -> "no server running") apenas se cortaba la
    # conexion, perdiendo el estado igual que sin tmux. Precrear la sesion en un proceso que ya
    # termino (sin pty de ttyd de por medio) y que ttyd solo haga "attach-session" resuelve la
    # carrera de raiz. Idempotente: si "main" ya existe (asiento reutilizado), tmux devuelve error
    # "duplicate session" y no pasa nada (check=False).
    subprocess.run(
        ["tmux", "new-session", "-d", "-s", "main", "-c", workspace, "nvim", "."],
        preexec_fn=_drop_privileges(uid, uid),
        env=seat_env,
        check=False,
    )
    process = subprocess.Popen(
        ["ttyd", "-p", str(port), "-W", "--ping-interval", "15",
         "tmux", "attach-session", "-t", "main"],
        preexec_fn=_drop_privileges(uid, uid),
        # SEAT_INDEX: leido por runtime-debug-helpers.sh (javadebug/pydebug) para que cada
        # asiento use su propio puerto de debug (5005+SEAT_INDEX / 5678+SEAT_INDEX) -- sin
        # esto, dos alumnos del mismo Pod debuggeando a la vez chocarian en el mismo puerto
        # fijo (limitacion conocida hasta este fix, ver DEC-0025).
        env=seat_env,
    )
    _seats[index] = {"process": process, "userUuid": user_uuid, "uid": uid}


# ---------------------------------------------------------------------------------------------
# Watchdog de recursos (Fase C) -- ver docstring del modulo.
# ---------------------------------------------------------------------------------------------
WATCHDOG_INTERVAL_SECONDS = 5
# Cuantos polls SEGUIDOS por encima del presupuesto antes de actuar (9*5s ~ 45s) -- suficiente
# margen para no penalizar un pico normal (ej. compilar un proyecto grande), pero corto para no
# dejar a los companeros de Pod sin recursos por mucho tiempo.
WATCHDOG_SUSTAINED_POLLS = 9
# Margen de tolerancia sobre el presupuesto "justo" (limite del Pod / asientos activos) antes de
# contar como abuso -- un asiento puede razonablemente usar mas que su parte proporcional un
# rato (los demas no siempre usan la suya a la vez).
CPU_TOLERANCE = 1.5
MEMORY_TOLERANCE = 1.5
_CLOCK_TICKS_PER_SEC = os.sysconf("SC_CLK_TCK")

# uid -> {"cpu_ticks": int, "over_budget_polls": int}
_watchdog_state: dict[int, dict] = {}


def _read_proc_uid_stats() -> dict:
    """Agrupa por UID: ticks de CPU acumulados (utime+stime, no delta -- el delta se calcula
    entre polls en _watchdog_loop), RSS en KB, y cantidad de procesos vivos."""
    stats: dict[int, dict] = {}
    for entry in os.listdir("/proc"):
        if not entry.isdigit():
            continue
        pid = entry
        try:
            with open(f"/proc/{pid}/status", "r", encoding="utf-8") as f:
                status = f.read()
            uid_line = next((l for l in status.splitlines() if l.startswith("Uid:")), None)
            if uid_line is None:
                continue
            uid = int(uid_line.split()[1])  # real uid (primer campo despues de "Uid:")
            if uid < SEAT_UID_BASE:
                continue  # no es un proceso de asiento -- ni agente (uid 0) ni sistema
            rss_line = next((l for l in status.splitlines() if l.startswith("VmRSS:")), None)
            rss_kb = int(rss_line.split()[1]) if rss_line else 0
            with open(f"/proc/{pid}/stat", "r", encoding="utf-8") as f:
                # Campos separados por espacio; el nombre de comando (campo 2) puede tener
                # espacios y esta entre parentesis -- se busca el ")" que lo cierra y se separan
                # los campos restantes desde ahi, para no romper el indice de utime/stime.
                stat_line = f.read()
            after_comm = stat_line.rsplit(")", 1)[1].split()
            utime, stime = int(after_comm[11]), int(after_comm[12])
            bucket = stats.setdefault(uid, {"cpu_ticks": 0, "rss_kb": 0, "nproc": 0})
            bucket["cpu_ticks"] += utime + stime
            bucket["rss_kb"] += rss_kb
            bucket["nproc"] += 1
        except (FileNotFoundError, ProcessLookupError, ValueError, IndexError):
            continue  # el proceso termino entre listdir() y la lectura -- normal, ignorar
    return stats


def _fair_share_budget():
    """Presupuesto "justo" por asiento activo, en (cpu_millicores, memory_mb) -- del limite
    TOTAL del Pod (via Downward API, ver KubernetesPodClient) dividido por la cantidad de
    asientos ocupados AHORA (no el maximo configurado -- si solo hay 2 de 4 asientos en uso,
    cada uno puede razonablemente usar la mitad del Pod, no un cuarto)."""
    active_seats = max(1, len(_seats))
    cpu_limit = int(os.environ.get("POD_CPU_LIMIT_MILLICORES", "0"))
    mem_limit = int(os.environ.get("POD_MEMORY_LIMIT_MIB", "0"))
    return cpu_limit / active_seats, mem_limit / active_seats


def _report_incident(seat_index: int, user_uuid: str, incident_type: str, detail: str):
    url = os.environ.get("SANDBOX_INCIDENT_REPORT_URL", "")
    key = os.environ.get("SANDBOX_INCIDENT_REPORT_KEY", "")
    if not url:
        print(f"sandbox-agent: incident (sin reportar, SANDBOX_INCIDENT_REPORT_URL no seteado): "
              f"seat={seat_index} user={user_uuid} type={incident_type} detail={detail}", file=sys.stderr)
        return
    payload = json.dumps({
        "conferenceUuid": os.environ.get("CONFERENCE_UUID", ""),
        "podName": os.environ.get("SANDBOX_POD_NAME", ""),
        "seatIndex": seat_index,
        "userUuid": user_uuid,
        "type": incident_type,
        "detail": detail,
    }).encode("utf-8")
    request = urllib.request.Request(
        url, data=payload, method="POST",
        headers={"Content-Type": "application/json", "X-Sandbox-Incident-Key": key},
    )
    try:
        urllib.request.urlopen(request, timeout=5).read()
    except (urllib.error.URLError, OSError) as e:
        # Mejor esfuerzo: si insightbloom-users no esta alcanzable (ej. NetworkPolicy de egress
        # todavia no propagada, o el servicio reiniciando), el watchdog sigue funcionando --
        # matar al asiento abusivo no puede depender de que el reporte tenga exito.
        print(f"sandbox-agent: fallo al reportar incidente: {e}", file=sys.stderr)


def _kill_and_respawn_seat(index: int, uid: int, reason: str, detail: str):
    seat = _seats.get(index)
    user_uuid = seat["userUuid"] if seat else "unknown"
    print(f"sandbox-agent: asiento {index} (uid {uid}, alumno {user_uuid}) excedio su "
          f"presupuesto de recursos ({reason}: {detail}) -- terminando y reiniciando", file=sys.stderr)
    subprocess.run(["pkill", "-9", "-u", str(uid)], check=False)
    _report_incident(index, user_uuid, reason, detail)
    with _lock:
        if seat is not None:
            try:
                _spawn_seat(index, Handler.base_port, user_uuid)
            except Exception as e:  # noqa: BLE001 -- no debe tumbar el watchdog
                print(f"sandbox-agent: fallo al reiniciar el asiento {index}: {e}", file=sys.stderr)


def _watchdog_loop():
    while True:
        time.sleep(WATCHDOG_INTERVAL_SECONDS)
        try:
            current = _read_proc_uid_stats()
            cpu_budget_millicores, mem_budget_mb = _fair_share_budget()
            with _lock:
                seat_by_uid = {s["uid"]: idx for idx, s in _seats.items()}
            for uid, bucket in current.items():
                seat_index = seat_by_uid.get(uid)
                if seat_index is None:
                    continue  # uid de un asiento que ya no esta activo (recien matado, etc.)
                prev = _watchdog_state.setdefault(uid, {"cpu_ticks": bucket["cpu_ticks"], "over_budget_polls": 0})
                delta_ticks = max(0, bucket["cpu_ticks"] - prev["cpu_ticks"])
                prev["cpu_ticks"] = bucket["cpu_ticks"]
                cpu_millicores_used = (delta_ticks / _CLOCK_TICKS_PER_SEC / WATCHDOG_INTERVAL_SECONDS) * 1000
                mem_mb_used = bucket["rss_kb"] / 1024
                # Techo duro de procesos (ver SEAT_MAX_PROCESSES / ulimit -u): si ya esta cerca
                # del limite, no hace falta esperar los WATCHDOG_SUSTAINED_POLLS -- es indicio
                # fuerte de fork-bomb en curso, actuar de inmediato.
                if bucket["nproc"] >= SEAT_MAX_PROCESSES * 0.9:
                    _kill_and_respawn_seat(seat_index, uid, "fork_bomb_suspected",
                                            f"{bucket['nproc']} procesos (limite {SEAT_MAX_PROCESSES})")
                    _watchdog_state.pop(uid, None)
                    continue
                over_cpu = cpu_budget_millicores > 0 and cpu_millicores_used > cpu_budget_millicores * CPU_TOLERANCE
                over_mem = mem_budget_mb > 0 and mem_mb_used > mem_budget_mb * MEMORY_TOLERANCE
                if over_cpu or over_mem:
                    prev["over_budget_polls"] += 1
                else:
                    prev["over_budget_polls"] = 0
                if prev["over_budget_polls"] >= WATCHDOG_SUSTAINED_POLLS:
                    reason = "cpu_abuse" if over_cpu else "memory_abuse"
                    detail = (f"cpu={cpu_millicores_used:.0f}m/{cpu_budget_millicores:.0f}m "
                              f"mem={mem_mb_used:.0f}Mi/{mem_budget_mb:.0f}Mi sostenido "
                              f"{WATCHDOG_SUSTAINED_POLLS * WATCHDOG_INTERVAL_SECONDS}s")
                    _kill_and_respawn_seat(seat_index, uid, reason, detail)
                    _watchdog_state.pop(uid, None)
            # Limpieza: uids que ya no tienen ningun proceso vivo no necesitan seguir en el
            # estado del watchdog (evita crecimiento sin limite en una sesion muy larga).
            for uid in list(_watchdog_state.keys()):
                if uid not in current:
                    _watchdog_state.pop(uid, None)
        except Exception as e:  # noqa: BLE001 -- el watchdog NUNCA debe morir por una excepcion
            # no prevista (un solo poll fallido no debe tumbar la vigilancia del resto de la
            # sesion del Pod).
            print(f"sandbox-agent: error en el watchdog (se ignora, sigue el proximo poll): {e}", file=sys.stderr)


class Handler(http.server.BaseHTTPRequestHandler):
    base_port: int = 0
    max_seats: int = 0

    def _send_json(self, status: int, payload: dict):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_zip(self, body: bytes):
        self.send_response(200)
        self.send_header("Content-Type", "application/zip")
        self.send_header("Content-Disposition", 'attachment; filename="workspace.zip"')
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _seat_workspace_root(self, index: int):
        """Devuelve la raiz del workspace del asiento, o None (con la respuesta de error ya
        enviada) si el asiento no existe/no esta aprovisionado todavia -- ningun endpoint de
        archivos tiene sentido antes de que el alumno tenga cuenta Linux real."""
        seat = _seats.get(index)
        if seat is None:
            self._send_json(404, {"error": "seat_not_provisioned"})
            return None
        return f"{_seat_home(seat['userUuid'])}/workspace"

    def do_GET(self):
        parsed = urllib.parse.urlsplit(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        path_param = (query.get("path", [""])[0])

        if parsed.path == "/health":
            self._send_json(200, {"status": "ok", "seats": len(_seats)})
            return

        match = re.fullmatch(r"/files/(\d+)", parsed.path)
        if match:
            root = self._seat_workspace_root(int(match.group(1)))
            if root is None:
                return
            try:
                self._send_json(200, {"entries": sandbox_file_api.list_directory(root, path_param)})
            except sandbox_file_api.PathTraversalError:
                self._send_json(400, {"error": "invalid_path"})
            except sandbox_file_api.NotFoundError:
                self._send_json(404, {"error": "not_found"})
            return

        match = re.fullmatch(r"/file/(\d+)", parsed.path)
        if match:
            root = self._seat_workspace_root(int(match.group(1)))
            if root is None:
                return
            try:
                self._send_json(200, sandbox_file_api.read_file(root, path_param))
            except sandbox_file_api.PathTraversalError:
                self._send_json(400, {"error": "invalid_path"})
            except sandbox_file_api.NotFoundError:
                self._send_json(404, {"error": "not_found"})
            except sandbox_file_api.FileTooLargeError:
                self._send_json(413, {"error": "file_too_large"})
            except sandbox_file_api.NotTextFileError:
                self._send_json(415, {"error": "not_text_file"})
            return

        match = re.fullmatch(r"/workspace/(\d+)/zip", parsed.path)
        if match:
            root = self._seat_workspace_root(int(match.group(1)))
            if root is None:
                return
            try:
                self._send_zip(sandbox_file_api.build_workspace_zip(root))
            except sandbox_file_api.NotFoundError:
                self._send_json(404, {"error": "not_found"})
            except sandbox_file_api.WorkspaceTooLargeError:
                self._send_json(413, {"error": "workspace_too_large"})
            return

        self._send_json(404, {"error": "not_found"})

    def do_PUT(self):
        parsed = urllib.parse.urlsplit(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        path_param = (query.get("path", [""])[0])
        mtime_param = query.get("mtime", [None])[0]

        match = re.fullmatch(r"/file/(\d+)", parsed.path)
        if not match:
            self._send_json(404, {"error": "not_found"})
            return
        index = int(match.group(1))
        root = self._seat_workspace_root(index)
        if root is None:
            return
        # El proceso corre como root SIN CAP_DAC_OVERRIDE (ver _drop_privileges): puede leer el
        # workspace del alumno (permisos de "other") pero no escribirlo -- write_file necesita el
        # uid del asiento para escribir via un subproceso que dropea privilegios (ver
        # sandbox_file_api._write_as_user), la misma cuenta Linux real que ya usa su ttyd.
        seat = _seats[index]
        try:
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length)
            content = raw.decode("utf-8")
        except (ValueError, UnicodeDecodeError):
            self._send_json(400, {"error": "invalid_body"})
            return
        try:
            expected_mtime = float(mtime_param) if mtime_param is not None else None
            result = sandbox_file_api.write_file(
                root, path_param, content, expected_mtime, run_as=(seat["uid"], seat["uid"]))
            self._send_json(200, result)
        except sandbox_file_api.PathTraversalError:
            self._send_json(400, {"error": "invalid_path"})
        except sandbox_file_api.FileConflictError:
            self._send_json(409, {"error": "file_conflict"})
        except (ValueError, TypeError):
            self._send_json(400, {"error": "invalid_mtime"})

    def do_POST(self):
        match = re.fullmatch(r"/seats/(\d+)", self.path)
        if not match:
            self._send_json(404, {"error": "not_found"})
            return
        index = int(match.group(1))
        if index < 0 or index >= self.max_seats:
            self._send_json(400, {"error": "seat_index_out_of_range"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length) or b"{}")
        except (ValueError, json.JSONDecodeError):
            self._send_json(400, {"error": "invalid_body"})
            return
        user_uuid = body.get("userUuid", "")
        if not UUID_RE.match(user_uuid):
            self._send_json(400, {"error": "invalid_user_uuid"})
            return
        with _lock:
            existing = _seats.get(index)
            if existing is not None and existing["process"].poll() is None:
                self._send_json(200, {"status": "already_running", "seatIndex": index,
                                       "userUuid": existing["userUuid"], "port": self.base_port + index})
                return
            try:
                _spawn_seat(index, self.base_port, user_uuid)
            except Exception as e:  # noqa: BLE001 -- nunca debe tumbar el agente entero
                self._send_json(500, {"error": "seat_spawn_failed", "detail": str(e)})
                return
        self._send_json(201, {"status": "created", "seatIndex": index,
                               "userUuid": user_uuid, "port": self.base_port + index})

    def log_message(self, fmt, *args):  # silencia el logging default de BaseHTTPRequestHandler
        sys.stderr.write("sandbox-agent: " + (fmt % args) + "\n")


class ThreadingHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-port", type=int, required=True)
    parser.add_argument("--max-seats", type=int, required=True)
    args = parser.parse_args()

    Handler.base_port = args.base_port
    Handler.max_seats = args.max_seats
    control_port = args.base_port - 1

    # /home es un emptyDir compartido por todos los asientos. Debe poder recorrerse
    # para que el agente encuentre el UUID solicitado, pero nunca ser escribible por
    # los grupos heredados del fsGroup del Pod. Cada home individual se cierra a 0750
    # en _ensure_seat_account. El grupo `coder` es el único grupo de control que
    # puede atravesar esos directorios; los procesos de alumnos pierden todos sus
    # grupos suplementarios antes de ejecutar ttyd.
    os.chmod(WORKSPACE_ROOT, 0o755)

    def _reap_zombies(*_):
        while True:
            try:
                pid, _status = os.waitpid(-1, os.WNOHANG)
                if pid == 0:
                    break
            except ChildProcessError:
                break

    signal.signal(signal.SIGCHLD, _reap_zombies)

    watchdog_thread = threading.Thread(target=_watchdog_loop, daemon=True)
    watchdog_thread.start()

    server = ThreadingHTTPServer(("0.0.0.0", control_port), Handler)
    print(f"sandbox-agent: listening on :{control_port}, seats {args.base_port}..{args.base_port + args.max_seats - 1}",
          file=sys.stderr)
    server.serve_forever()


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # noqa: BLE001 -- fallo de arranque (ej. puerto ocupado): no hay
        # nada util que este proceso pueda seguir sirviendo, se deja morir y que Kubernetes
        # reporte el contenedor como fallido (mejor una senal clara que un agente zombie).
        print(f"sandbox-agent: fatal error: {e}", file=sys.stderr)
        sys.exit(1)
