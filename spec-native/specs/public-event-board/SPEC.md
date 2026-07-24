# Cartelera pública de eventos

Estado: `active`

## Alcance

Los eventos incorporan un detalle público opcional, visibilidad y un cronograma en Markdown.

- `PRIVATE`: no aparece en la cartelera; el organizador distribuye boletos.
- `PUBLIC`: aparece en la cartelera; una persona autenticada puede solicitar un boleto si el tipo de evento exige ticketing.
- `HYBRID`: aparece en la cartelera y permite solicitudes públicas; el organizador puede seguir emitiendo boletos privados desde el dashboard.

La API pública solo devuelve un DTO reducido. No debe reutilizarse el objeto completo de `Conference` porque incluye configuración de sandboxes, IDE y otras propiedades internas.

## Catálogo visual de la cartelera

El organizador puede elegir un preset visual para cada evento desde el editor del evento:

- `CLASSIC`: información clara y equilibrada.
- `EDITORIAL`: flyer protagonista y tratamiento visual de revista.
- `MINIMAL`: diseño ligero y directo.

El preset se guarda como `publicTheme` en la conferencia y solo modifica la presentación de la
cartelera y de la ficha pública. No guarda HTML, CSS ni contenido paralelo: nombre, detalle, flyer,
cronograma, ubicación, aforo y organizador siguen siendo los campos canónicos del evento. Si llega
un valor desconocido, el backend usa `CLASSIC` para mantener compatibilidad con eventos antiguos y
no permitir estilos arbitrarios.

## Rutas

- `GET /api/users/api/v1/conferences/public`: tarjetas de eventos activos públicos o híbridos.
- `GET /api/users/api/v1/conferences/public/{friendlyId}`: detalle público, flyer, ubicación, cronograma y disponibilidad.
- `POST /api/users/api/v1/conferences/public/{friendlyId}/tickets`: solicita y canjea un boleto para la cuenta autenticada; respeta aforo y ticketing.

El cronograma se limita a 12,000 caracteres y se renderiza sin HTML crudo ni enlaces con esquemas inseguros. La ubicación usa el componente existente basado en OpenStreetMap y el enlace externo lleva `noopener noreferrer`.

## Perfil público del organizador

El DTO público incluye el nombre visible y una fotografía opcional del organizador. La foto se
puede administrar desde `Mi perfil`; solo se aceptan PNG/JPEG, se validan con `ImageIO`, se
rechazan imágenes mayores de 4096px o 1.5 MB y se normalizan a JPEG de máximo 512px antes de
guardarlas. No se acepta SVG ni HTML como avatar y nunca se publica correo, UUID o roles.

La cartelera muestra ese perfil únicamente como identidad visual del evento. El catálogo de presets
no permite introducir HTML/CSS arbitrario y no debe convertirse en una segunda fuente de verdad.
