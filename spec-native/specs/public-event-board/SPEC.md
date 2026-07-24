# Cartelera pública de eventos

Estado: `active`

## Alcance

Los eventos incorporan un detalle público opcional, visibilidad y un cronograma en Markdown.

- `PRIVATE`: no aparece en la cartelera; el organizador distribuye boletos.
- `PUBLIC`: aparece en la cartelera; una persona autenticada puede solicitar un boleto si el tipo de evento exige ticketing.
- `HYBRID`: aparece en la cartelera y permite solicitudes públicas; el organizador puede seguir emitiendo boletos privados desde el dashboard.

La API pública solo devuelve un DTO reducido. No debe reutilizarse el objeto completo de `Conference` porque incluye configuración de sandboxes, IDE y otras propiedades internas.

## Rutas

- `GET /api/users/api/v1/conferences/public`: tarjetas de eventos activos públicos o híbridos.
- `GET /api/users/api/v1/conferences/public/{friendlyId}`: detalle público, flyer, ubicación, cronograma y disponibilidad.
- `POST /api/users/api/v1/conferences/public/{friendlyId}/tickets`: solicita y canjea un boleto para la cuenta autenticada; respeta aforo y ticketing.

El cronograma se limita a 12,000 caracteres y se renderiza sin HTML crudo ni enlaces con esquemas inseguros. La ubicación usa el componente existente basado en OpenStreetMap y el enlace externo lleva `noopener noreferrer`.

## Pendientes deliberados

- Resolver el nombre y perfil público del organizador desde un DTO de perfil, sin exponer correo ni identificadores sensibles.
- Agregar fotografía del organizador con validación de tipo, límite de tamaño y resize server-side; no se deben aceptar imágenes arbitrarias como HTML o SVG activo.
- Agregar catálogo y editor visual de la cartelera sin duplicar la fuente de verdad del evento.
