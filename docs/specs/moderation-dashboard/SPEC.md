# SPEC.md

## Resumen

Definir el dashboard desde el cual organizadores y moderadores revisan
palabras y mensajes, aplican censura manual, restauran y editan contenido.

## Incluye

- Estados `visible`, `censurado_auto`, `censurado_manual`,
  `pendiente_revision`.
- Paginacion de listas.
- Edicion sin historial.
- Recalculo y propagacion de cambios a `stats` y `query`.

## Requisitos clave

- Una palabra censurada desaparece de la nube publica.
- Un detalle censurable puede reemplazarse por `detalle censurado`.
- Moderacion debe ser operable en vivo durante una conferencia.
