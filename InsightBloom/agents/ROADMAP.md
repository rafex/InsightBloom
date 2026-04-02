# ROADMAP.md

Direccion del proyecto en el tiempo.

## Ahora

- Definir el contexto base del producto y la primera spec operativa.
- Construir el frontend inicial con nubes separadas de dudas y temas,
  timeline y dashboard de moderacion.
- Diseñar el dashboard de moderacion para revision y censura manual.
- Definir y construir los microservicios iniciales del PoC.
- Cerrar el contrato inicial de datos entre frontend y backend.
- Definir reglas iniciales de censura e intencion para el PoC.
- Definir autenticacion por token para usuarios registrados e invitados.
- Definir la capa operativa del repo: tasks locales, scripts, CI y Helm.

## Despues

- Integrar la fuente real de chat o el mecanismo de ingreso definitivo.
- Mejorar la normalizacion y agrupacion de palabras.
- Evolucionar la medicion de intencion a un modelo mas rico si demuestra
  valor.
- Añadir apoyo de sugerencias de moderacion mas sofisticadas.

## Mas adelante

- Moderacion y curacion de contenido.
- Analitica adicional de participacion.
- Recomendaciones o agrupaciones semanticas mas avanzadas.
- Persistencia duradera mas alla del ciclo de vida del contenedor.

## No hacer por ahora

- Autenticacion y roles avanzados.
- Multievento y multisesion complejos.
- IA generativa o clasificacion automatica antes de validar el flujo base.
