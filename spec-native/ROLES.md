# ROLES.md

Referencia de roles, permisos y ciclo de vida de usuarios en InsightBloom.

## Roles disponibles

### ADMIN

El administrador tiene control total sobre la plataforma y todos los usuarios.

Capacidades:

- Todas las capacidades de ORGANIZER (hereda).
- Gestionar usuarios: listar, editar, banear (soft-delete), restaurar.
- Ver el dashboard de administracion de usuarios.
- Configurar certificados por conferencia.

Restricciones:

- Solo puede crearse a traves del CLI de administracion.
- El rol ADMIN puede combinarse con ORGANIZER (multi-rol).

---

### ORGANIZER

El organizador es el propietario operativo de una conferencia.

Capacidades:

- Crear conferencias y obtener el `friendlyId` para compartir.
- Acceder al dashboard de moderacion.
- Censurar, restaurar y editar palabras y mensajes.
- Gestionar encuestas (crear, ver resultados, emitir certificados).
- Subir presentaciones (Marp Markdown) para la conferencia.
- Enviar mensajes como cualquier participante.
- Consultar nubes de dudas, temas y timelines.
- Ver estadisticas de relevancia de la conferencia.
- Ver dashboard home con tarjetas de resumen.

Restricciones:

- Solo puede crearse a traves del CLI de administracion.
- No puede gestionar otros usuarios del sistema (requiere ADMIN).

---

### MODERATOR

El moderador apoya la gestion en vivo de la conferencia.

Capacidades:

- Acceder al dashboard de moderacion (lectura y escritura).
- Censurar, restaurar y editar palabras y mensajes.
- Consultar nubes de dudas, temas y timelines.
- Enviar mensajes como participante.

Restricciones:

- No puede crear conferencias.
- No puede gestionar encuestas ni presentaciones.
- No puede gestionar usuarios.
- Debe ser creado por un administrador mediante el CLI.

---

### GUEST

El invitado es cualquier participante de la audiencia.

Capacidades:

- Enviar mensajes (`/duda`, `/tema`) en una conferencia activa.
- Consultar nubes de dudas, temas y timelines.
- Ver el timeline de palabras individuales.
- Responder encuestas.
- Ver presentaciones de slides.

Restricciones:

- No puede acceder al dashboard de moderacion.
- No puede censurar ni restaurar contenido.
- No puede crear conferencias.
- Su identidad se basa en un fingerprint de dispositivo (ThumbmarkJS).
- El token de invitado se emite automaticamente al acceder a `/c/{friendlyId}`.

---

## Matriz de permisos por endpoint

| Endpoint | ADMIN | ORGANIZER | MODERATOR | GUEST |
|---|:---:|:---:|:---:|:---:|
| `POST /auth/login` | ✅ | ✅ | ✅ | — |
| `POST /auth/guest` | — | — | — | ✅ |
| `GET /auth/validate` | ✅ | ✅ | ✅ | ✅ |
| `POST /conferences` | ✅ | ✅ | — | — |
| `GET /conferences/{id}` | ✅ | ✅ | ✅ | — |
| `GET /conferences/by-friendly/{id}` | ✅ | ✅ | ✅ | ✅ |
| `POST /messages` | ✅ | ✅ | ✅ | ✅ |
| `POST /webhooks/messages` | webhook | webhook | webhook | — |
| `GET /messages/{id}` | ✅ | ✅ | ✅ | — |
| `GET /cloud/doubts` | ✅ | ✅ | ✅ | ✅ |
| `GET /cloud/topics` | ✅ | ✅ | ✅ | ✅ |
| `GET /words/{word}/timeline` | ✅ | ✅ | ✅ | ✅ |
| `GET /moderation/messages` | ✅ | ✅ | ✅ | — |
| `GET /moderation/words` | ✅ | ✅ | ✅ | — |
| `POST /moderation/messages/{id}/censor` | ✅ | ✅ | ✅ | — |
| `POST /moderation/messages/{id}/restore` | ✅ | ✅ | ✅ | — |
| `PATCH /moderation/messages/{id}` | ✅ | ✅ | ✅ | — |
| `POST /moderation/words/{id}/censor` | ✅ | ✅ | ✅ | — |
| `POST /moderation/words/{id}/restore` | ✅ | ✅ | ✅ | — |
| `PATCH /moderation/words/{id}` | ✅ | ✅ | ✅ | — |
| `GET /stats/overview` | ✅ | ✅ | ✅ | — |
| `GET /stats/relevance` | ✅ | ✅ | ✅ | — |
| `GET /survey/{id}` | ✅ | ✅ | ✅ | ✅ |
| `POST /survey/{id}/respond` | ✅ | ✅ | ✅ | ✅ |
| `POST /survey` | ✅ | ✅ | — | — |
| `GET /survey/{id}/results` | ✅ | ✅ | — | — |
| `POST /presentations/upload` | ✅ | ✅ | — | — |
| `GET /presentations/{id}/slides` | ✅ | ✅ | ✅ | ✅ |
| `GET /admin/users` | ✅ | — | — | — |
| `PATCH /admin/users/{id}` | ✅ | — | — | — |
| `POST /admin/users/{id}/ban` | ✅ | — | — | — |
| `POST /admin/users/{id}/restore` | ✅ | — | — | — |
| `GET /certificates/settings` | ✅ | ✅ | — | — |
| `PATCH /certificates/settings` | ✅ | ✅ | — | — |
| `GET /profile` | ✅ | ✅ | ✅ | — |
| `PATCH /profile` | ✅ | ✅ | ✅ | — |

---

## Ciclo de vida de un usuario

```
CLI create-user
      │
      ▼
  users.db ──► ACTIVE ──► (token emitido en login)
                 │
                 ▼
              BANNED (soft-delete, restaurable por ADMIN)
```

- Los usuarios creados por CLI arrancan en estado `ACTIVE`.
- No existe seed administrativo con contrasena por defecto (DEC-0009).
- Todo usuario ADMIN, ORGANIZER o MODERATOR debe crearse explicitamente con el CLI.
- Un usuario puede tener multiples roles (ej. `ORGANIZER,ADMIN`).
- El rol ADMIN hereda todas las capacidades de ORGANIZER.
- Los invitados (GUEST) no tienen fila en la tabla `users`; usan
  `guest_users` y tokens de tipo `GUEST`.
- El OTP (Twilio SMS o Zoho email) es opcional para verificacion adicional.

## Como crear usuarios

```bash
# CLI local
just create-user -- --username admin --password s3cr3t --role ORGANIZER,ADMIN

# CLI en K3s
just k3s-create-user -- --username admin --password s3cr3t --role ORGANIZER,ADMIN
```

Ver [`backend/cli/insightbloom-cli/README.md`](../backend/cli/insightbloom-cli/README.md).

## Relacion con el esquema de base de datos

```
users
  uuid          TEXT    — identificador expuesto entre servicios
  username      TEXT    — nombre de login
  display_name  TEXT    — nombre visible en la UI
  email         TEXT    — opcional
  roles         TEXT    — lista separada por comas: ORGANIZER,ADMIN,MODERATOR
  status        TEXT    — ACTIVE | BANNED
  password_hash TEXT    — SHA-256 del password
  created_at    TEXT    — ISO-8601 UTC
  updated_at    TEXT    — ISO-8601 UTC
```
