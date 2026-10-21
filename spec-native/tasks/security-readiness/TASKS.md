# Tareas — preparación de seguridad para registro público

**Estado:** in_progress
**Owner:** propietario del producto (`rafex`)
**Registro de riesgos:** repositorio privado `rafex/InsightBloom-gitops/docs/security/`

### SEC-01: Completar autorización por rol y evento

**Estado:** in_progress
**Owner:** backend
**Dependencias:** —
**Archivos esperados:** pruebas de contrato de `insightbloom-users`, `insightbloom-survey`, `insightbloom-query` y servicios con datos de evento
**Criterio de cierre:** visitante, asistente, moderador, organizador y administrador solo acceden a las capacidades y eventos permitidos; casos de escalamiento y evento ajeno fallan de forma consistente. Ya hay pruebas unitarias de permisos/roles y un E2E que comprueba el rol ATTENDEE por defecto; falta la matriz completa y el E2E Python no está conectado a CI.
**Validación:** suite local/staging; no ejecutar contra producción.

### SEC-02: Demostrar aislamiento multiusuario del IDE

**Estado:** todo
**Owner:** IDE + plataforma
**Dependencias:** —
**Archivos esperados:** evidencia de ejecución en staging y, si hace falta, pruebas del agente sandbox
**Criterio de cierre:** dos usuarios en un mismo Pod y en Pods distintos no pueden listar, atravesar, leer ni alterar workspaces ajenos; no comparten procesos ni credenciales.
**Validación:** escenario staging aprobado y registrado sin datos reales.

### SEC-03: Definir y probar autorregistro de organizadores

**Estado:** in_progress
**Owner:** producto + backend
**Dependencias:** SEC-01
**Archivos esperados:** diseño de flujo de cuenta/rol, cambios de API/UI por iniciativa separada
**Criterio de cierre:** correo verificado, emisión de rol controlada, límite de creación y frecuencia, suspensión, recuperación y auditoría. El registro actual crea ATTENDEE sin verificar email; existe un endpoint OTP separado y la asignación de roles por evento rechaza roles de alcance plataforma. Diseñar el flujo que convierte esa cuenta en organizador.
**Validación:** pruebas locales/staging con casos normales y abuso simulado.

### SEC-04: Endurecer gates de CI y cadena de suministro

**Estado:** in_progress
**Owner:** plataforma
**Dependencias:** —
**Archivos esperados:** workflows CI/CD, reglas de excepciones, inventario de imágenes
**Criterio de cierre:** conservar `audit-ci` bloqueante para presentations/publisher; resolver o documentar con responsable y expiración las auditorías no bloqueantes de chat, telegram y frontend; análisis de secretos y código deja resultado revisable. La revalidación encontró gates parciales, no ausencia total.
**Validación:** ejecución de CI en rama de prueba.

### SEC-05: Confirmar TLS y postura efectiva de producción

**Estado:** in_progress
**Owner:** plataforma
**Dependencias:** —
**Archivos esperados:** evidencia de configuración efectiva, sin valores secretos
**Criterio de cierre:** HTTPS y redirección están documentados para cada host público; cualquier tramo sin cifrado queda dentro de un mismo host/nodo de confianza; permisos, red y recursos efectivos coinciden con los controles esperados. Revalidación 2026-10-20: HAProxy externo está activo en 443, carga certificados SAN correctos para los hosts de InsightBloom y redirige HTTP a HTTPS; reenvía por loopback a NodePort del único nodo K3s. Sin handshake externo. Quedan como hardening definir versión/cifrados mínimos, HSTS y renovación de certificados.
**Validación:** lectura de configuración autorizada en host y clúster; sin solicitudes de prueba a endpoints.

### SEC-06: Probar recuperación de datos

**Estado:** todo
**Owner:** plataforma + producto
**Dependencias:** —
**Archivos esperados:** runbook de restauración y resultados de staging
**Criterio de cierre:** restauración verificable, integridad funcional y objetivos RPO/RTO aprobados.
**Validación:** restaurar una copia en staging; nunca modificar producción.

### SEC-07: Habilitar reporte privado y definir soporte/abuso

**Estado:** in_progress
**Owner:** propietario del producto
**Dependencias:** habilitación de reportes privados en GitHub
**Archivos esperados:** `SECURITY.md`, especificación del formulario y cola privada en tracker
**Criterio de cierre:** vulnerabilidades se reportan privadamente en GitHub; soporte/abuso se enruta aparte a triage privado; no se promete SLA público.
**Validación:** confirmar opción privada habilitada y probar el enrutamiento sin datos reales.

### SEC-08: Preparar respuesta a incidentes y abuso

**Estado:** todo
**Owner:** propietario del producto
**Dependencias:** SEC-07
**Archivos esperados:** runbooks privados de contención, recuperación y comunicación
**Criterio de cierre:** escenarios de fuga entre eventos, cuenta comprometida, fraude, aislamiento IDE, secreto filtrado, abuso de recursos e indisponibilidad tienen responsable y pasos ensayados.
**Validación:** ejercicio tabletop o staging, sin acciones sobre producción.
