# insightbloom-cli

Herramienta de línea de comandos para administrar usuarios de InsightBloom
directamente sobre la base de datos SQLite del servicio `insightbloom-users`.

---

## Compilar

```bash
# Desde la raíz del repositorio
just cli-build

# O directamente con Maven
./mvnw -f backend/cli/insightbloom-cli/pom.xml clean package -DskipTests
```

El JAR resultante queda en:

```
backend/cli/insightbloom-cli/target/insightbloom-cli-0.1.0-SNAPSHOT.jar
```

---

## Comando: `create-user`

Crea un usuario nuevo o actualiza uno existente por `username`.
La operación es idempotente: si el usuario ya existe, actualiza su
`password_hash`, `role`, `display_name` y `email`.

### Sintaxis

```
java -jar insightbloom-cli.jar create-user [opciones]
```

### Opciones

| Opción | Tipo | Requerido | Default | Descripción |
|---|---|:---:|---|---|
| `--db` | ruta | no | `users.db` | Ruta al archivo SQLite del servicio users |
| `--username` | texto | **sí** | — | Nombre de usuario para login |
| `--password` | texto | **sí** | — | Contraseña en texto plano (se hashea con SHA-256) |
| `--role` | enum | no | `ORGANIZER` | `ORGANIZER` o `MODERATOR` |
| `--display-name` | texto | no | `username` | Nombre visible en el dashboard |
| `--email` | texto | no | — | Correo electrónico (opcional) |

### Vía Justfile

```bash
just create-user -- --username <u> --password <p> [--role ORGANIZER|MODERATOR] [--db <ruta>]
```

### Vía Makefile

```bash
make create-user ARGS="--username <u> --password <p> [--role ORGANIZER|MODERATOR]"
```

---

## Ejemplos

### Crear el primer organizador

```bash
just create-user -- \
  --username rafex \
  --password "mipassword123" \
  --role ORGANIZER \
  --display-name "Rafex" \
  --email rafex@example.com
```

### Crear un moderador

```bash
just create-user -- \
  --username maria \
  --password "abc123" \
  --role MODERATOR \
  --display-name "María López"
```

### Apuntar a una base de datos en ruta específica

```bash
just create-user -- \
  --db /var/lib/insightbloom/users.db \
  --username admin2 \
  --password "nueva-clave" \
  --role ORGANIZER
```

### Actualizar la contraseña del admin de seed

El usuario `admin` se crea sin contraseña al arrancar el servicio por primera
vez. Para protegerlo:

```bash
just create-user -- \
  --username admin \
  --password "clave-segura-2026" \
  --role ORGANIZER
```

### Salida esperada

```
✓ Usuario creado
  uuid:     e192ff37-1c4b-4626-919c-f3e931537b8e
  username: rafex
  role:     ORGANIZER
  db:       users.db
```

Si el usuario ya existía:

```
✓ Usuario actualizado
  uuid:     00000000-0000-0000-0000-000000000001
  username: admin
  role:     ORGANIZER
  db:       users.db
```

---

## Seguridad del password

- La contraseña se hashea con **SHA-256** antes de persistirse.
- El texto plano nunca se almacena ni se loguea.
- El servicio `insightbloom-users` valida el hash en cada login.
- Usuarios del seed sin `password_hash` aceptan cualquier contraseña hasta
  que se les asigne una con este CLI.

---

## Dónde está el archivo `users.db`

| Modo de ejecución | Ruta por defecto |
|---|---|
| `just dev` (proceso local) | `./users.db` (directorio de trabajo) |
| Docker Compose | volumen `users-data` montado en `/data/users.db` |
| Producción (Helm) | configurable vía variable `DB_PATH` |

Para acceder al archivo de un contenedor Docker en ejecución:

```bash
docker compose -f infra/compose/local.yml cp insightbloom-users:/data/users.db ./users.db
just create-user -- --db ./users.db --username nueva --password abc --role MODERATOR
docker compose -f infra/compose/local.yml cp ./users.db insightbloom-users:/data/users.db
```

---

## Ayuda integrada

```bash
java -jar backend/cli/insightbloom-cli/target/insightbloom-cli-0.1.0-SNAPSHOT.jar --help
```

---

## Referencias

- [ROLES.md](../../docs/ROLES.md) — descripción completa de roles y permisos
- [SPEC.md](../../docs/SPEC.md) — requisitos del sistema de autenticación
