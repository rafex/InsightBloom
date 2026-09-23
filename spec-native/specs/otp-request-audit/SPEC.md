# SPEC: Trazabilidad de solicitudes OTP

## Initiative
otp-request-audit

## Status
active

## Summary
Registrar solicitudes de login OTP para investigar abuso y problemas de entrega sin revelar al
solicitante si una cuenta existe o es elegible. El endpoint externo mantiene una respuesta
genérica; los detalles operativos quedan disponibles solo para administradores.

## Requirements

- Para toda solicitud bien formada, la respuesta pública es `200` con el mismo cuerpo genérico,
  tanto para cuentas inexistentes/no elegibles como para entregas aceptadas o fallidas.
- No enviar correo si la cuenta no existe, no está activa, no usa `OTP_EMAIL`, carece de email,
  el proveedor no está configurado o alcanzó tres códigos entregados en una hora.
- Los códigos pendientes de aceptación SMTP no son verificables ni consumen el límite. Un rechazo
  SMTP invalida el código pendiente y se registra internamente como fallo.
- Persistir fecha, IP confiable del cliente, User-Agent, UUID de cuenta cuando exista y resultado.
  No persistir el identificador recibido ni el código OTP en la auditoría.
- La IP se obtiene del último salto IPv4/IPv6 que agrega el Ingress confiable; los prefijos
  proporcionados por el cliente no se consideran fuente de verdad. Si no hay salto válido, usar
  la dirección remota de la conexión.
- Eliminar filas con más de 30 días y limitar la consulta a administradores, con paginación y
  filtros acotados.
- Mantener la cuota independiente de la escritura de auditoría para que una falla de logging no
  permita evadir el límite de envío.

## API

`GET /api/users/api/v1/admin/auth/otp-audit?from=&to=&accountUuid=&clientIp=&outcome=&limit=&offset=`

Requiere token de usuario con el rol exacto `admin`; tokens ausentes/inválidos reciben 401 y otros
roles reciben 403. El endpoint devuelve `items`, `total`, `limit` y `offset`. Las fechas consultables
se acotan a la ventana de retención.

## Privacy and operations

La IP y el User-Agent son datos operativos potencialmente personales; el TTL máximo es 30 días.
Los estados distinguen `account_not_found`, `account_inactive`, `otp_not_enabled`, `email_missing`,
`mail_provider_disabled`, `rate_limited`, `smtp_accepted`, `smtp_failed` y `request_failed`.
`smtp_accepted` significa que el proveedor aceptó el mensaje, no que llegó a la bandeja.
