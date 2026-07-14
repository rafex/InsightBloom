# Code-IDE Sandbox Docker Images

## Estructura (Fase 4: contenedores separados `ide` + `runtime`)

Cada sandbox corre dos contenedores en el mismo Pod:

- **Dockerfile.code-ide-server**: contenedor `ide` — Debian, solo code-server (instalado desde el
  release standalone oficial, sin npm), extensiones de las 3 variantes preinstaladas (imagen única
  y universal, no una por variante). Expone el puerto 8080 (servido al usuario vía el gateway).
- **Dockerfile.code-ide-runtime.python / .java / .web**: contenedor `runtime` — Alpine, solo el
  toolchain de cada lenguaje, sin code-server. Expone un shell vía `socat` (PTY-over-TCP) en
  `127.0.0.1:7681`, alcanzable únicamente por loopback intra-Pod desde el contenedor `ide` (nunca
  se expone vía Service/Ingress).

La terminal integrada de code-server (contenedor `ide`) se conecta a ese `socat` del contenedor
`runtime` — ver el perfil de terminal baked-in en `code-ide-settings.json`. Esto significa que
comandos ejecutados en la terminal (compilar, correr tests, `python3`, `mvn`, `npm run dev`, etc.)
corren en el contenedor `runtime`, no en `ide`.

## Build y push

```bash
# Contenedor "ide" (Debian, code-server)
docker build -f infra/docker/Dockerfile.code-ide-server -t insightbloom-code-ide-server:latest .

# Contenedores "runtime" por variante (Alpine, toolchain)
docker build -f infra/docker/Dockerfile.code-ide-runtime.python -t insightbloom-code-ide-runtime:python .
docker build -f infra/docker/Dockerfile.code-ide-runtime.java -t insightbloom-code-ide-runtime:java .
docker build -f infra/docker/Dockerfile.code-ide-runtime.web -t insightbloom-code-ide-runtime:web .
```

Ninguna de las 4 imágenes depende de otra vía `FROM` — se pueden construir en paralelo (así lo
hace el workflow de CI, `build-and-push-code-ide`, como matriz).

## Code-server Configuration

- **Puerto**: 8080 (expuesto en el contenedor `ide`)
- **Autenticación**: `--disable-auth` — delegada al gateway via `ib_token` (DEC-0022)
- **Bind address**: 0.0.0.0 (accesible desde el gateway)
- **Workspace**: `/home/coder/workspace` (volumen compartido entre `ide` y `runtime`)
- **Database**: `/home/coder/db` (volumen compartido, para SQLite)
- **Terminal**: `socat STDIO TCP:127.0.0.1:7681` hacia el contenedor `runtime` (perfil por
  defecto, ver `code-ide-settings.json`)

## Extensiones

Todas preinstaladas en la imagen `ide` (universal, no por variante):
Java+Maven (`vscjava.*`), Python+Pylance+debugpy (`ms-python.*`), y el pack web
(Prettier/ESLint/Volar/React/Tailwind/HTML-CSS).

## Notas de seguridad

- Ambos contenedores corren como usuario no-root `coder` (uid/gid 1000, coinciden para que el
  volumen `workspace` compartido tenga permisos consistentes).
- El contenedor `ide` **no tiene toolchains de lenguaje** (java/python/node no viven ahí).
- El contenedor `runtime` **no tiene código de VS Code ni acceso a la API de Kubernetes**
  (`automountServiceAccountToken: false` a nivel Pod).
- La terminal remota usa `socat` sobre loopback intra-Pod — no requiere RBAC nuevo, ServiceAccount
  token, ni el binario `kubectl` empaquetado en ninguna imagen. Ese tráfico ni siquiera atraviesa
  el CNI (las `NetworkPolicy` no aplican a loopback intra-Pod).
- NetworkPolicy en Kubernetes niega egress por defecto para el Pod completo (TASK-0050).
- SQLite funciona localmente sin red (seguro por diseño).
- Git remote requiere credenciales explícitas del alumno o token del profesor.

## Base de datos SQLite

SQLite se incluye en el contenedor `runtime`. Los alumnos pueden:
1. Crear DBs en `/home/coder/db/` (compartido con `ide` vía volumen)
2. Usar code-server + extensión SQLite para explorar tablas
3. Descargar la DB como parte del ZIP de código (TASK-0034)

Ejemplo:
```bash
sqlite3 /home/coder/db/myapp.db
> CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT);
> INSERT INTO users (name) VALUES ('Alice');
> SELECT * FROM users;
```
