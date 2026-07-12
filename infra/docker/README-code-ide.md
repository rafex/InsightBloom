# Code-IDE Sandbox Docker Images

## Estructura

- **Dockerfile.code-ide**: Base Alpine minimal con code-server, SQLite, Git (TASK-0010)
- **Dockerfile.code-ide.python** (TASK-0011): Extiende base con Python, pip, dependencias comunes
- **Dockerfile.code-ide.java** (TASK-0011): Extiende base con JDK, Maven
- **Dockerfile.code-ide.web** (TASK-0011): Extiende base con Node.js, npm (completo)

## Build y push

```bash
# Base (TASK-0010)
docker build -f infra/docker/Dockerfile.code-ide -t insightbloom-code-ide:base .

# Variantes (TASK-0011)
docker build -f infra/docker/Dockerfile.code-ide.python -t insightbloom-code-ide:python .
docker build -f infra/docker/Dockerfile.code-ide.java -t insightbloom-code-ide:java .
docker build -f infra/docker/Dockerfile.code-ide.web -t insightbloom-code-ide:web .

# Push a registry (ej. ECR, Docker Hub, etc.)
docker tag insightbloom-code-ide:python <registry>/insightbloom-code-ide:python
docker push <registry>/insightbloom-code-ide:python
```

## Code-server Configuration

- **Puerto**: 8080 (expuesto)
- **Autenticación**: `--disable-auth` — delegada al gateway via `ib_token` (DEC-0022)
- **Bind address**: 0.0.0.0 (accesible desde el gateway)
- **Workspace**: `/home/coder/workspace`
- **Database**: `/home/coder/db` (para SQLite)

## Extensiones recomendadas (TASK-0013+)

- SQLite (para viewer de tablas): `alexcvzz.vscode-sqlite`
- Git integración: nativa
- Python: `ms-python.python` (variante python)
- Java: `Extension Pack for Java` (variante java)
- Web: ya incluye Node.js/npm

Las extensiones se instalan vía:
```bash
code-server --install-extension <extension-id>
```

O se pre-instalan en el Dockerfile durante la construcción.

## Notas de seguridad

- Usuario no-root (`coder`, UID 1000)
- No expone SSH
- No expone Docker socket
- NetworkPolicy en Kubernetes niega egress por defecto (TASK-0050)
- SQLite funciona localmente sin red (seguro por diseño)
- Git remote requiere credenciales explícitas del alumno o token del profesor

## Base de datos SQLite

SQLite se incluye en la imagen Alpine. Los alumnos pueden:
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

## Próximos pasos

- TASK-0011: Crear variantes (python, java, web)
- TASK-0012: Helm chart para pool-fixed en Kubernetes
- TASK-0013: Tests de build y verificación de vulnerabilidades
