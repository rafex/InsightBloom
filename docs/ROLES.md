# ROLES.md

Referencia de roles, permisos y ciclo de vida de usuarios en InsightBloom.

## Roles disponibles

### ORGANIZER

El organizador es el propietario operativo de una conferencia.

Capacidades:

- Crear conferencias y obtener el `friendlyId` para compartir.
- Acceder al dashboard de moderación.
- Censurar, restaurar y editar palabras y mensajes.
- Enviar mensajes como cualquier participante.
- Consultar nubes de dudas, temas y timelines.
- Ver estadísticas de relevancia de la conferencia.

Restricciones:

- Solo puede crearse a través del CLI de administración o del seed inicial.
- No existe registro público de organizadores.

---

### MODERATOR

El moderador apoya la gestión en vivo de la conferencia.

Capacidades:

- Acceder al dashboard de moderación (lectura y escritura).
- Censurar, restaurar y editar palabras y mensajes.
- Consultar nubes de dudas, temas y timelines.
- Enviar mensajes como participante.

Restricciones:

- No puede crear conferencias.
- Debe ser creado por un administrador mediante el CLI.

---

### GUEST

El invitado es cualquier participante de la audiencia.

Capacidades:

- Enviar mensajes (`/duda`, `/tema`) en una conferencia activa.
- Consultar nubes de dudas, temas y timelines.
- Ver el timeline de palabras individuales.

Restricciones:

- No puede acceder al dashboard de moderación.
- No puede censurar ni restaurar contenido.
- No puede crear conferencias.
- Su identidad se basa en un fingerprint de dispositivo (ThumbmarkJS).
- El token de invitado se emite automáticamente al acceder a `/c/{friendlyId}`.

---

## Matriz de permisos por endpoint

| Endpoint | ORGANIZER | MODERATOR | GUEST |
|---|:---:|:---:|:---:|
| `POST /auth/login` | ✅ | ✅ | — |
| `POST /auth/guest` | — | — | ✅ |
| `GET /auth/validate` | ✅ | ✅ | ✅ |
| `POST /conferences` | ✅ | — | — |
| `GET /conferences/{id}` | ✅ | ✅ | — |
| `GET /conferences/by-friendly/{id}` | ✅ | ✅ | ✅ |
| `POST /messages` | ✅ | ✅ | ✅ |
| `POST /webhooks/messages` | webhook token | webhook token | — |
| `GET /messages/{id}` | ✅ | ✅ | — |
| `GET /cloud/doubts` | ✅ | ✅ | ✅ |
| `GET /cloud/topics` | ✅ | ✅ | ✅ |
| `GET /words/{word}/timeline` | ✅ | ✅ | ✅ |
| `GET /moderation/messages` | ✅ | ✅ | — |
| `GET /moderation/words` | ✅ | ✅ | — |
| `POST /moderation/messages/{id}/censor` | ✅ | ✅ | — |
| `POST /moderation/messages/{id}/restore` | ✅ | ✅ | — |
| `PATCH /moderation/messages/{id}` | ✅ | ✅ | — |
| `POST /moderation/words/{id}/censor` | ✅ | ✅ | — |
| `POST /moderation/words/{id}/restore` | ✅ | ✅ | — |
| `PATCH /moderation/words/{id}` | ✅ | ✅ | — |
| `GET /stats/overview` | ✅ | ✅ | — |
| `GET /stats/relevance` | ✅ | ✅ | — |

---

## Ciclo de vida de un usuario

```
CLI create-user
      │
      ▼
  users.db ──► ACTIVE ──► (token emitido en login)
                │
                ▼ (futuro)
             INACTIVE
```

- Los usuarios creados por CLI arrancan en estado `ACTIVE`.
- El seed inicial (`admin`) arranca sin `password_hash`; acepta cualquier
  contraseña hasta que se actualice con el CLI.
- Los invitados (GUEST) no tienen fila en la tabla `users`; usan
  `guest_users` y tokens de tipo `GUEST`.

---

## Cómo crear usuarios

Ver [`backend/cli/insightbloom-cli/README.md`](../backend/cli/insightbloom-cli/README.md).

## Relación con el esquema de base de datos

```
users
  uuid          TEXT  — identificador expuesto entre servicios
  username      TEXT  — nombre de login
  display_name  TEXT  — nombre visible en la UI
  email         TEXT  — opcional
  role          TEXT  — ORGANIZER | MODERATOR | GUEST
  status        TEXT  — ACTIVE | INACTIVE
  password_hash TEXT  — SHA-256 del password; NULL = sin restricción (seed)
  created_at    TEXT  — ISO-8601 UTC
  updated_at    TEXT  — ISO-8601 UTC
```
