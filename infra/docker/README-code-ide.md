# Code-IDE Sandbox Docker Images

## Dos modos de IDE, dos imagenes autocontenidas

Cambio de paradigma 2026-07-17: cada sandbox corre **un unico contenedor**, con el toolchain
completo (Java 25 Temurin + Python 3.12 + Node 24 LTS + herramientas de curso) instalado
directamente en la imagen. Ya no existe el split `ide`/`runtime` de la Fase 4 (dos contenedores
por Pod, bridge de terminal via `socat` en loopback) — ver DEC-0023 en `spec-native/DECISIONS.md`
para el porque completo.

Cada conferencia elige un modo de IDE (campo `sandboxVariant`, ver `EditConferencePage.vue`),
que ahora selecciona directamente la imagen:

- **`code-server`** (default, valor vacío o cualquier valor historico `python`/`java`/`web`):
  `Dockerfile.code-ide-debian` — VS Code completo en el navegador, con extensiones de
  Java/Python/JavaScript e idioma español.
- **`terminal-nvim`**: `Dockerfile.code-ide-neovim` — Neovim configurado como IDE (explorador de
  archivos, autocompletado, LSP de Java via `jdtls`, syntax highlighting), servido por `ttyd`
  (terminal web sobre WebSocket). Mas liviano en RAM/CPU y en tiempo de arranque en el navegador
  (sin el JS de VS Code Web que descargar). Ver `nvim-init.lua` para la config completa.

`KubernetesPodClient.buildPodBody` decide que imagen usar segun este valor; el resto del
pipeline (gateway, `SandboxHandler`, `IdePage.vue`) es agnostico al modo, proxea HTTP/WS al
Service del Pod sin saber si hay VS Code o una terminal detras.

## Toolchain (identico y version-pinneado en las dos imagenes)

| Componente | Version | Origen |
|---|---|---|
| Java | 25 LTS Temurin `jdk-25.0.3+9` | tarball oficial Adoptium (glibc en Debian, musl "alpine-linux" en Alpine), SHA256 verificado |
| Python | 3.12.13 | `python-build-standalone` (astral-sh) — mismo build exacto en variante glibc y musl |
| Node.js | 24.18.0 LTS | Debian: tarball oficial `nodejs.org`; Alpine: `unofficial-builds.nodejs.org` (nodejs.org no publica builds musl oficiales) |

Todas las descargas se verifican con `sha256sum -c` contra un hash fijado en el Dockerfile
(`ARG *_SHA256`), no solo "confiar en HTTPS".

## Herramientas de curso (pedido explicito, ver DEC-0023)

Ademas del toolchain de lenguajes, ambas imagenes incluyen: `git`, `fzf`, `bash-completion`,
`bat`/`eza`/`fd`/`ripgrep`/`ncdu` (mejoras de cat/ls/find/grep/du), `jq`, `tmux`, `tree`,
`httpie`, `shellcheck`, `build-essential`/`build-base`, `maven`, `unzip`, `less`+`man`, y
`opencode` (CLI de agente de codigo IA). Paquetes globales de Python (`jupyter`, `numpy`,
`pandas`, `matplotlib`, `flask`, `django`, `fastapi`, `pytest`, `black`, `pylint`, `debugpy`) y
de Node (`typescript`, `eslint`, `prettier`, `vite`, `webpack`, `@vue/cli`, `create-react-app`)
tambien pre-instalados — nada requiere setup manual del instructor/alumno.

## Estructura de las imagenes

- **Dockerfile.code-ide-debian**: Debian 12-slim, `code-server` (release standalone oficial, sin
  npm) + toolchain completo + extensiones Java/Python/Web + idioma español
  (`ms-ceintl.vscode-language-pack-es`, activado via `--locale es`) + `sst-dev.opencode`. Expone
  el puerto 8080 (servido al usuario vía el gateway).
- **Dockerfile.code-ide-neovim**: Alpine 3.21, `neovim`/`vim`/`lazygit` + toolchain completo +
  `ttyd` (sirve `nvim` sobre `/home/coder/workspace` directo en el puerto público del Service).

Ninguna de las 2 imágenes depende de otra vía `FROM` ni se ejecutan juntas en un mismo Pod — se
construyen en paralelo (así lo hace el workflow de CI, `build-and-push-code-ide`, como matriz).

## Build y push

```bash
# Imagen "debian" (code-server, editor grafico)
docker build -f infra/docker/Dockerfile.code-ide-debian -t insightbloom-code-ide-debian:latest .

# Imagen "neovim" (terminal 100%, ttyd + nvim)
docker build -f infra/docker/Dockerfile.code-ide-neovim -t insightbloom-code-ide-neovim:latest .
```

## Code-server Configuration

- **Puerto**: 8080
- **Autenticación**: `--auth none` — delegada al gateway via `ib_token` (DEC-0022)
- **Bind address**: 0.0.0.0 (accesible desde el gateway)
- **Workspace**: `/home/coder/workspace` (volumen `emptyDir` del Pod)
- **Database**: `/home/coder/db` (volumen `emptyDir` del Pod, para SQLite)
- **Terminal**: shell local del propio contenedor (ya no hay bridge via `socat`)

## Extensiones (imagen `code-ide-debian`)

Java+Maven (`redhat.java` + `vscjava.*`, separadas porque el pack `vscjava.extension-pack-for-
java` no esta en open-vsx.org), Python (`ms-python.python` + `ms-pyright.pyright` en vez de
Pylance, tampoco en open-vsx), el pack web (Prettier/ESLint/Volar/React/Tailwind/HTML-CSS),
paquete de idioma español (`ms-ceintl.vscode-language-pack-es`) y `sst-dev.opencode`. Todas
fijadas a una version explicita salvo el language pack (ver comentario en el Dockerfile).

## Debug remoto (Java/Python)

El editor/terminal y el proceso a debuggear ahora corren en el MISMO contenedor (ya no hay
`ide`/`runtime` separados) — el flujo de adjuntar-remoto se mantiene porque sigue siendo la
forma mas simple de depurar un programa que el alumno ya arranco a mano:

1. En la terminal, correr `javadebug MiClase` o `pydebug script.py` (wrappers en
   `runtime-debug-helpers.sh`) en vez del comando normal.
2. En el editor, "Run and Debug" -> elegir "Adjuntar a Java (puerto 5005)" o "Adjuntar a Python
   (puerto 5678)" (ya sembrado en `.vscode/launch.json` por `code-ide-entrypoint.sh`) -> F5.

`javadebug`/`pydebug` bindean JDWP/debugpy a `localhost`/`127.0.0.1` (no `*`/`0.0.0.0`): ninguno
de los dos protocolos tiene autenticacion propia, exponerlos fuera del Pod seria ejecucion de
codigo arbitrario alcanzable por cualquier otro sandbox del namespace.

## Notas de seguridad

- El contenedor corre como usuario no-root `coder` (uid/gid 1000).
- Sin acceso a la API de Kubernetes (`automountServiceAccountToken: false` a nivel Pod).
- `NetworkPolicy` de Ingress restringe el trafico ENTRANTE a cada sandbox al Pod del gateway
  unicamente (namespace + label) — bloquea acceso Pod-a-Pod entre sandboxes de distintos
  alumnos (ver DEC-0023, auditoria de seguridad 2026-07-17).
- `NetworkPolicy` de egress niega salida por defecto para el Pod completo (TASK-0050), se
  reabre explicitamente por conferencia si `internetEnabled=true`.
- SQLite funciona localmente sin red (seguro por diseño).
- Git remote requiere credenciales explícitas del alumno o token del profesor.

## Base de datos SQLite

SQLite se incluye en la imagen. Los alumnos pueden:
1. Crear DBs en `/home/coder/db/`
2. Usar code-server + extensión SQLite para explorar tablas
3. Descargar la DB como parte del ZIP de código (TASK-0034)

Ejemplo:
```bash
sqlite3 /home/coder/db/myapp.db
> CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT);
> INSERT INTO users (name) VALUES ('Alice');
> SELECT * FROM users;
```
