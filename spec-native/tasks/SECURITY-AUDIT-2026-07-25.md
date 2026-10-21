# Revisión de seguridad — resumen público

**Fecha de la revisión original:** 2026-07-25
**Actualización de estado del código:** 2026-10-20

La auditoría identificó problemas en controles de acceso, manejo de tokens y aislamiento de entornos. Las correcciones principales se incorporaron después de la revisión. La validación automatizada de autorización y la comprobación de aislamiento de dos usuarios en el IDE siguen pendientes; no se consideran cerradas por la presencia del cambio en código.

## Estado resumido

| Área | Estado en el código actual | Pendiente |
|---|---|---|
| Respuestas públicas de eventos | Corregido en código | Añadir y ejecutar pruebas de contrato |
| Tokens de sesión en operaciones de escritura | Corregido en código | Añadir pruebas por método y revisar tratamiento de logs |
| Aislamiento de usuarios del IDE | Sin evidencia suficiente | Verificar dos usuarios en staging |
| Secreto interno con fallback de desarrollo | Corregido en código | Confirmar configuración en el entorno desplegado |
| Preguntas internas de encuesta | Corregido en código | Completar pruebas de autorización por evento/rol |
| Ciclo de vida y privacidad de eventos | Parcial | Acordar y probar política de lectura para eventos privados |
| Descarga de workspaces | Corregido en código | Añadir pruebas de firma, vencimiento y uso único |

## Próximo gate

Antes de habilitar el autorregistro de organizadores, completar la matriz de autorización, demostrar aislamiento multiusuario del IDE y validar las medidas contra abuso y la recuperación de datos. La evaluación de preparación y su evidencia operativa se mantiene en el tracker privado asociado a `InsightBloom-gitops`.

La versión actual de este resumen omite rutas de API, ejemplos de respuestas, detalles de configuración interna y procedimientos de reproducción. Las versiones históricas siguen presentes en el historial público del repositorio.
