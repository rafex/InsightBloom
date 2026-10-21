# Preparación de seguridad para registro público de organizadores

**Estado:** active
**Owner:** propietario del producto
**Fecha:** 2026-10-20

## Objetivo

Determinar y demostrar qué controles necesita InsightBloom antes de aceptar cuentas de organizador por autorregistro y permitirles crear eventos.

## Alcance

- Autenticación, asignación de roles y autorización por evento.
- Privacidad de datos de asistentes y contenido de eventos.
- Flujos públicos, sesiones y tokens.
- Cargas de presentaciones y aislamiento de IDE/sandboxes.
- CI/CD, dependencias, secretos, imágenes y permisos Kubernetes.
- Abuso de registro, eventos, correo y recursos.
- Respaldo, restauración, observabilidad, contención y comunicación.
- Formulario de soporte/abuso separado del canal privado de vulnerabilidades.

La evaluación de producción es de solo lectura. No se sondean endpoints, no se explotan fallos y no se cambian recursos productivos. Las pruebas activas se ejecutan únicamente en local o staging.

## Criterios de aceptación

1. Reconciliar cada hallazgo de la auditoría del 2026-07-25 con el código y despliegue actuales.
2. Completar pruebas locales/staging de autorización entre roles y eventos, respuestas públicas, autorregistro, límites de abuso y aislamiento de al menos dos usuarios del IDE.
3. Demostrar límites de creación de eventos, verificación de correo, suspensión y recuperación de cuenta.
4. Verificar TLS del entorno efectivo y operación de canales privados de soporte y vulnerabilidades.
5. Restaurar una copia en staging y acordar RPO/RTO.
6. Completar procedimientos de contención, recuperación y comunicación.

No habilitar autorregistro hasta que todos los criterios estén satisfechos.

## Referencia privada

La evidencia detallada, severidades, responsables e incidentes plausibles se mantienen en el repositorio privado `rafex/InsightBloom-gitops`, carpeta `docs/security/`.
