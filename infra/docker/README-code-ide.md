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
  Java/Python/JavaScript/HTML/CSS e idioma español.
- **`terminal-nvim`**: `Dockerfile.code-ide-neovim` — Neovim configurado como IDE (explorador de
  archivos, autocompletado semántico de JavaScript/TypeScript/Python/HTML/CSS y Java, LSP de
  JavaScript/TypeScript via `typescript-language-server`, Python via `pyright-langserver`, HTML
  via `vscode-html-language-server`, CSS via `vscode-css-language-server` y Java via `jdtls`, servido por `ttyd`
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
de Node (`typescript`, `typescript-language-server`, `eslint`, `prettier`, `@types/node`, `vite`, `webpack`, `@vue/cli`, `create-react-app`)
tambien pre-instalados — nada requiere setup manual del instructor/alumno.

### LSP y autocompletado semántico en ambos IDE

Los dos modos incluyen el mismo contrato de servidores, aunque cada editor los integra de forma
distinta:

| Lenguaje | Servidor precargado | Web IDE (code-server) | CLI IDE (Neovim) |
|---|---|---|---|
| Java | `jdtls` | `redhat.java` incorpora el language server | `jdtls` del paquete Alpine + `nvim-jdtls` |
| Python | `pyright-langserver` | extensión `ms-pyright.pyright` | `pyright-langserver` vía `nvim-lspconfig` |
| JS/TS | `typescript-language-server` | servicio TypeScript integrado de VS Code | `typescript-language-server` vía `nvim-lspconfig` |
| HTML | `vscode-html-language-server` | servicio HTML integrado + HTML/CSS extension | `vscode-html-language-server` vía `nvim-lspconfig` |
| CSS | `vscode-css-language-server` | servicio CSS integrado + HTML/CSS extension | `vscode-css-language-server` vía `nvim-lspconfig` |

El CLI añade `nvim-cmp`, `cmp-nvim-lsp`, `LuaSnip`, `nvim-tree` y `nvim-lspconfig` 2.3.0
(compatible con Neovim 0.10). La configuración se activa para `.js`, `.jsx`, `.ts`, `.tsx`,
`package.json`, `jsconfig.json` y `tsconfig.json`; detecta la raíz mediante esos archivos o
`.git`. Los tipos incluyen módulos de Node como `fs`, `http`, `process` y `Buffer`.

Los binarios npm también están presentes en el Web IDE para que las tareas de terminal, scripts
de CI locales y diagnósticos manuales usen las mismas versiones: `pyright`,
`typescript-language-server` y `vscode-langservers-extracted`.

Como `/home/*/workspace` es un volumen efímero, la imagen guarda una copia inmutable de los
tipos en `/usr/local/share/insightbloom-node-types`. `seed-node-types.sh` crea enlaces dentro
del workspace al arrancar el asiento, tanto en el modo de un solo usuario como en el agente
multi-asiento. El script no ejecuta `npm install`, no usa Mason/Lazy y no requiere Internet.

## Publicación de páginas web

Ambos IDE incluyen la guía visible `.insightbloom/IDE-WEB-PUBLICATION.md` y el comando
`insightbloom`. Publicar no obliga a usar `package.json`: solo se necesita un `index.html` y sus
assets locales. El backend crea un snapshot temporal, excluye metadatos de build y vuelve a
auditar el contenido antes de servirlo desde un origen aislado.

En el Web IDE se usa el botón **Publicar página temporal**. En el CLI se usan credenciales de la
sesión actual, sin guardar el token en el proyecto:

```bash
export INSIGHTBLOOM_CONFERENCE_ID="UUID_DEL_EVENTO"
export INSIGHTBLOOM_TOKEN="TOKEN_DE_SESION"
insightbloom publish                 # publica el workspace actual
insightbloom publish --root dist     # publica una carpeta concreta
insightbloom revoke PUBLICATION_ID   # revoca la URL temporal
```

Un `insightbloom.json` es opcional para declarar la raíz publicada:

```json
{"publish":{"root":"dist","entry":"index.html"}}
```

El comando no ejecuta scripts de npm ni requiere Internet. La publicación es solo para sitios
estáticos: APIs, WebSockets, procesos persistentes y puertos arbitrarios quedan fuera de este
flujo. La duración de la URL la controla el servicio y la documentación completa se siembra en el
workspace al iniciar cada sandbox.

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
- `NetworkPolicy` de egress niega salida por defecto para el Pod completo (TASK-0050). Una
  `NetworkPolicy` estándar no puede expresar una allowlist por dominio. Cuando el organizador
  habilita la salida controlada, el Pod solo llega a `insightbloom-egress-proxy`; la proxy
  aplica `EGRESS_PROXY_ALLOWED_HOSTS` y `EGRESS_PROXY_BLOCKED_HOSTS`, con la lista negra
  teniendo precedencia. No se abre una ruta directa desde el sandbox a Internet.
- SQLite funciona localmente sin red (seguro por diseño).
- Git remote requiere credenciales explícitas del alumno o token del profesor.

### Excepción de red únicamente para GitHub

Sí es posible permitir que un sandbox descargue repositorios de GitHub manteniendo Internet
bloqueado para todo lo demás, pero no como una sola regla de `NetworkPolicy` por hostname. El
diseño recomendado es:

1. El organizador activa la salida controlada para el evento.
2. El sandbox recibe `HTTP_PROXY`/`HTTPS_PROXY` y solo puede conectar al proxy interno.
3. El proxy permite únicamente los hosts declarados en `EGRESS_PROXY_ALLOWED_HOSTS` (`github.com`,
   `api.github.com`, `codeload.github.com`, `raw.githubusercontent.com`,
   `objects.githubusercontent.com` y los hosts de assets que el flujo requiera).
4. El proxy rechaza otros dominios, registra evento/usuario/repositorio y limita tamaño,
   método y redirecciones.
5. La `NetworkPolicy` mantiene bloqueado el acceso directo desde el sandbox a Internet y a los
  servicios internos.

La configuración vive en GitOps: `infrastructure/config/app-config.yaml` es la fuente
declarativa; `app-config-cm.yaml` es la salida operativa autogenerada. `blocked_hosts` se
evalúa antes de `allowed_hosts` y los destinos privados/reservados se rechazan aunque se
introduzcan accidentalmente en la lista blanca.

Permitir los rangos publicados por GitHub directamente sería solo una mitigación temporal: no
cubre de forma estable todos los redirects/CDN, permite más servicios de GitHub de los
necesarios y requiere actualización operativa.

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
