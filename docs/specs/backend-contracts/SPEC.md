# SPEC.md

## Resumen

Definir los contratos del backend del PoC para `users`, `ingest`, `query`,
`moderation` y `stats`.

## Incluye

- Arquitectura hexagonal por servicio.
- Modelo de datos por microservicio.
- Contratos HTTP y payloads JSON.
- Matriz de permisos por rol y endpoint.
- Convencion de errores y paginacion.
- Regla de `friendlyId`.
- Formula de `relevanceScore`.
- Politica de recalc por edicion o censura.

## Requisitos clave

- APIs externas exponen UUID, no ids internos.
- Invitados solo envian mensajes.
- Moderacion afecta nube y timeline sin borrar fisicamente los registros.
- El PoC usa HTTP sincrono entre microservicios.
