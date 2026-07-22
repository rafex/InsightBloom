# SPEC: Certificados por evento y editor visual

## Initiative
certificate-editor

## Status
active

## Objective

Permitir que ADMIN, ORGANIZER/propietario y MODERATOR con permiso de evento
creen un certificado específico para cada evento. El usuario elige una
plantilla base del catálogo, la enriquece con bloques y variables, y el
certificado se genera bajo demanda en PDF.

## Design

- El catálogo base vive en código y ofrece `classic`, `modern` y `minimal`.
- Cada evento puede guardar una plantilla en `certificate_templates`.
- El documento persistido es JSON controlado: `page` y hasta 100 `blocks`.
- El frontend previsualiza el mismo modelo y permite editar posiciones,
  tamaños, colores y texto.
- El servicio de usuarios mantiene la autorización y los datos; el servicio
  de presentaciones ejecuta Playwright + Chromium mediante un endpoint interno
  autenticado por `X-Internal-Api-Key`.
- No se acepta HTML, JavaScript, CSS arbitrario ni URLs remotas en la plantilla.
  El renderizador usa una lista blanca de estilos, texto escapado e imágenes
  `data:image/*;base64`.
- Si un evento no tiene plantilla guardada, se conserva el PDFBox actual para
  compatibilidad con eventos existentes.

## Permissions

- ADMIN: bypass de permisos y edición de cualquier evento.
- ORGANIZER: puede editar el evento que creó; además puede editar eventos donde
  tenga asignación de evento con `MANAGE_CERTIFICATE`.
- MODERATOR: puede editar únicamente eventos donde su rol de evento tenga
  `MANAGE_CERTIFICATE`.
- La configuración global de plataforma continúa siendo ADMIN-only y funciona
  como respaldo del generador legado.

## Variables disponibles

### Participante

`participant.displayName`, `participant.firstName`, `participant.lastName`,
`participant.email`, `participant.username`, `participant.uuid`.

### Evento

`event.name`, `event.displayName`, `event.friendlyId`, `event.uuid`,
`event.date`, `event.startTime`, `event.endTime`, `event.venue`,
`event.timezone`.

### Plataforma

`platform.name`, `platform.website`, `platform.email`, `platform.github`,
`platform.linkedin`, `platform.telegram`.

### Certificado

`certificate.issuedDate`, `certificate.id`.

## Acceptance criteria

1. Un usuario autorizado puede abrir `/dashboard/conferences/{id}/certificate`,
   elegir un diseño base y guardar cambios.
2. Un usuario no autorizado recibe `403` y no puede modificar el documento.
3. La descarga de certificado de un participante que respondió la encuesta
   usa la plantilla del evento y devuelve PDF generado por Chromium.
4. Las plantillas no pueden ejecutar scripts, realizar requests externos ni
   inyectar HTML en el PDF.
5. Los eventos sin plantilla siguen generando su certificado PDFBox.
6. El catálogo y la lista de variables se entregan por API, evitando que el
   frontend invente nombres no soportados.
