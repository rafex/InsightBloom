# Plan: boletos emitidos y acceso controlado a eventos

## Estado

**implemented** — alcance núcleo implementado y validado; quedan fuera pagos y
emisión masiva.

Este plan redefine el flujo actual de reservas para que el acceso a un evento
con boletos dependa de un boleto emitido y posteriormente canjeado.

## Cambio principal

### Flujo actual

El participante se une a la conferencia y el sistema intenta crearle una
reserva/boleto automáticamente.

### Flujo objetivo

1. Un moderador autorizado emite un boleto.
2. El boleto se entrega por correo o se comparte directamente mediante QR.
3. El participante canjea el boleto usando el QR o escribiendo su UUID v4.
4. El boleto queda asociado a una cuenta existente o a una identidad de invitado temporal.
5. El participante obtiene acceso a las áreas privadas del evento.

En un evento configurado con boletos, la única área pública es la vista previa
de presentación con las primeras cinco diapositivas. Todo el resto del evento
requiere una cuenta registrada y un boleto canjeado.

## Política formal de áreas

| Área | Acceso | Regla |
|---|---|---|
| `presentation_preview` | Público | Solo primeras 5 diapositivas |
| `event_info` | Privado | Requiere registro y boleto |
| `ticket_claim` | Flujo de acceso | Permite registrarse e ingresar/canjear boleto |
| `cloud` | Privado | Requiere registro y boleto |
| `presentation_full` | Privado | Requiere registro y boleto |
| `survey`, `video`, `whiteboard`, `diagrams`, `notes`, `ide` | Privado | Requiere registro y boleto |

## Reglas de negocio

- El moderador del evento puede emitir boletos.
- Organizadores y administradores tienen acceso operativo sin consumir boleto.
- El participante necesita boleto aunque tenga cuenta registrada.
- El boleto puede ser transferible hasta el momento del primer canje.
- Un boleto solo puede canjearse una vez.
- El canje acepta:
  - el contenido recibido desde un código QR;
  - un UUID v4 escrito manualmente.
- El QR contiene únicamente el UUID v4 del boleto y no datos personales.
- El canje sin sesión crea acceso como invitado temporal; con sesión lo asocia a la cuenta existente.
- El boleto canjeado habilita acceso, pero el check-in físico se mantiene como
  una operación separada.
- Un boleto revocado o expirado no puede habilitar acceso.
- La expiración automática ocurre 5 horas después del inicio del evento; se
  calcula con `eventDate`, `startTime` y la zona horaria configurada.

## Estados del boleto

```text
ISSUED -> CLAIMED -> CHECKED_IN
   \         \
    -> REVOKED  -> REVOKED
```

- `ISSUED`: emitido, todavía no asociado a un participante.
- `CLAIMED`: canjeado y asociado a una cuenta o invitado.
- `CHECKED_IN`: asistencia confirmada mediante escaneo o validación manual.
- `REVOKED`: invalidado por el moderador u organizador.
- `EXPIRED`: han transcurrido 5 horas desde el inicio del evento, calculado
  con la fecha, hora y zona horaria de la conferencia.

## Fase 1 — Reglas, permisos y política de acceso

- Confirmar el modo del evento: abierto o con boletos obligatorios.
- Definir las áreas públicas y privadas de cada conferencia: únicamente
  `presentation_preview` es público y su límite es de 5 diapositivas.
- Introducir permisos de evento para emisión y check-in, idealmente:
  - `MANAGE_TICKETS`;
  - `CHECK_IN`.
- Mantener bypass para organizador, administrador y personal autorizado.
- Centralizar la política de acceso para evitar validaciones dispersas en cada
  pantalla o endpoint.
- Definir el error de backend `ticket_required` para acceso privado sin boleto.

## Fase 2 — Modelo de datos y migración

Crear una entidad `Ticket` independiente de `Reservation` con:

- `uuid` — UUID v4 público del boleto;
- `conference_uuid`;
- `ticket_code` o representación equivalente del UUID v4;
- payload QR basado en el UUID v4 del boleto;
- estado;
- usuario o invitado asociado;
- email/nombre opcional del destinatario;
- usuario que emitió el boleto;
- asiento opcional;
- fechas de emisión, canje, check-in, revocación y expiración.

Restricciones:

- UUID del boleto único;
- payload QR único y no reutilizable;
- un canje por boleto;
- índices por conferencia, estado y usuario;
- unicidad de reserva por conferencia y usuario para evitar dobles emisiones
  concurrentes en el modelo legado.

Migración:

- conservar reservas y asientos existentes durante la transición;
- convertir reservas actuales en boletos reclamados cuando corresponda;
- conservar la reserva legacy más antigua por usuario y conferencia antes de
  instalar la restricción de unicidad;
- desactivar el auto-boleto al hacer `join` para eventos que usen el modelo nuevo;
- crear migraciones SQLite idempotentes;
- permitir activación gradual mediante las capacidades de ticketing del tipo de evento.

## Fase 3 — Backend de emisión y canje

Casos de uso previstos en `insightbloom-users`:

- `TicketUseCase` cubre emisión, listado, canje, acceso, revocación y check-in;
- el envío de correo reutiliza el puerto de email existente.

Endpoints previstos:

- `POST /conferences/{id}/tickets` — emitir boleto;
- `GET /conferences/{id}/tickets` — listar boletos;
- `POST /conferences/{id}/tickets/claim` — canjear QR o UUID v4;
- `POST /conferences/{id}/tickets/{ticket}/revoke` — revocar;
- `POST /conferences/{id}/tickets/check-in` — registrar asistencia;
- `GET /conferences/{id}/access` — consultar acceso efectivo.

El endpoint `POST /tickets/claim` debe aceptar un único campo de entrada que
pueda contener:

1. el payload recibido desde el QR; o
2. un UUID v4 escrito manualmente.

El backend debe normalizar ambos formatos al mismo boleto, validar conferencia,
estado y uso previo, y responder con un resultado explícito de canje.

## Fase 4 — Panel del moderador

Agregar una sección de boletos para:

- emitir un boleto individual;
- indicar destinatario opcional;
- mostrar el QR;
- copiar el UUID v4;
- compartir el QR;
- enviar el boleto por correo;
- listar estados;
- revocar boletos;
- buscar por UUID, email, usuario o estado;
- abrir el flujo de check-in.

## Fase 5 — Flujo del participante

Crear una ruta pública de canje que permita:

1. abrir el enlace asociado al QR;
2. leer el QR desde cámara cuando el navegador lo permita;
3. introducir manualmente el UUID v4;
4. continuar como invitado o iniciar sesión/registrarse;
5. asociar el boleto al usuario;
6. mostrar el boleto activo y el estado de acceso.

La navegación del evento debe mostrar sin boleto únicamente la vista previa de
presentación. El resto de las rutas se bloquea con `ticket_required` hasta que
existan registro y canje exitosos.

## Fase 6 — Correo, QR y check-in

- Generar el UUID v4 en backend y usarlo como payload del QR/enlace de canje.
- Enviar correo mediante el puerto de email existente, sin hacer fallar la
  emisión si el correo no está disponible.
- Permitir compartir directamente el QR desde el panel.
- Hacer que escáner y entrada manual utilicen la misma validación backend.
- Mantener check-in idempotente y rechazar el segundo uso.
- Ejecutar un proceso periódico cada 5 minutos que marque `ISSUED` y `CLAIMED`
  como `EXPIRED` al superar las 5 horas desde el inicio del evento.

## Fase 7 — Pruebas y validación

### Backend

- emisión autorizada por moderador del evento;
- rechazo de usuarios sin permiso;
- canje desde QR;
- canje usando UUID v4 manual;
- canje con sesión existente;
- canje como invitado;
- doble canje;
- boleto revocado o expirado;
- boleto de otra conferencia;
- acceso privado sin boleto;
- bypass de personal autorizado;
- concurrencia y aforo;
- check-in repetido.

### Frontend y E2E

- emitir y visualizar QR;
- copiar UUID v4;
- compartir el QR y copiar UUID v4;
- canjear sin sesión;
- canjear con sesión;
- mostrar únicamente las 5 diapositivas públicas;
- bloquear todas las demás áreas hasta completar registro y canje;
- escanear y registrar check-in.

## Fuera de alcance inicial

- pagos;
- reventa o marketplace;
- transferencia posterior al canje;
- precios y tipos de boleto;
- emisión masiva mediante CSV;
- integración con proveedores externos de ticketing.

## Criterio de cierre

Un moderador puede emitir y compartir un boleto; un participante puede canjearlo
con QR o UUID v4, con o sin sesión; solo puede ver las primeras 5 diapositivas
sin acceso; todo lo demás rechaza participantes sin registro y boleto; y
check-in, revocación y concurrencia están cubiertos por pruebas automatizadas.
