# insightbloom-users

Microservicio de autenticacion, usuarios y conferencias.

## Puerto

8081

## Endpoints

- `POST /api/v1/auth/login` — login de organizador/moderador
- `POST /api/v1/auth/guest` — registro de invitado
- `GET  /api/v1/auth/validate` — validacion de token
- `POST /api/v1/conferences` — crear conferencia (organizer)
- `GET  /api/v1/conferences/{id}` — obtener por UUID
- `GET  /api/v1/conferences/by-friendly/{friendlyId}` — obtener por friendlyId

## Credenciales

- Login de usuarios registrados requiere `username` y `password`.
- El servicio ya no crea un usuario `admin` por seed.
- Los usuarios se crean o actualizan con el CLI `insightbloom-cli`.

## Build & run

```bash
./mvnw -f services/insightbloom-users/pom.xml package
./helpers-run/run-service.sh insightbloom-users
```
