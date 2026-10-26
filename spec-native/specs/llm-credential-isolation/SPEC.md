# Configuración LLM aislada por evento y herramienta

**Estado:** superseded por [SPEC-LPI](../llm-profile-inheritance/SPEC.md)
**Owner:** product owner
**Fecha:** 2026-10-21

## Objetivo

Permitir al propietario de un evento y a ADMIN configurar por separado el proveedor, endpoint, modelo y API key que usa cada herramienta. Cada evento administra sus propias credenciales; no existen credenciales LLM globales, predeterminadas ni heredadas de organizador.

## Comportamiento

- Las capacidades son `chat`, `tutor`, `survey`, `seatLayout` y `email`.
- Cada capacidad obtiene exclusivamente la configuración guardada para ese evento. Si no está configurada, la herramienta opera sin IA.
- Se puede elegir proveedor/modelo del catálogo aprobado o una URL personalizada compatible con OpenAI y el ID de modelo.
- El endpoint personalizado requiere HTTPS público, DNS global y ausencia de redirecciones; se valida y fija la dirección resuelta al conectar para reducir SSRF y DNS rebinding.
- El gateway dedicado es el único componente que descifra claves y llama al proveedor. Las claves se almacenan cifradas con AES-GCM y una clave maestra exclusiva del gateway.
- Las rutas de estado, escritura y borrado autorizan al propietario del evento o a ADMIN. Toda operación se audita con actor, evento, capacidad y acción, sin valores secretos.
- Las respuestas de estado muestran proveedor, modelo, endpoint y condición configurada; nunca devuelven API key, fragmentos ni ciphertext.
- No se copian las credenciales globales existentes. Se eliminan del almacenamiento activo al inicializar `users`; backups históricos permanecen según su retención y no se reactivan al restaurar.
- No existe fallback entre herramienta, evento u organizador. Credenciales ausentes implican IA deshabilitada.

## Requisitos de seguridad y UX

- La clave solo viaja desde el navegador al backend autenticado y luego al gateway por canal de servicio; los logs, errores y trazas no deben capturar secretos ni cuerpos.
- Reemplazar requiere ingresar una nueva clave; borrar es explícito. El campo se limpia tras guardar.
- URL personalizada: rechazar HTTP, userinfo, query/fragment, loopback, rangos privados/reservados y metadatos; resolver DNS al conectar, exigir que todas las respuestas sean públicas, fijar la IP para TLS/HTTP y no seguir redirects.
- La interfaz debe ser móvil, operable por teclado y comunicar estado, proveedor/modelo, URL y cómo reemplazar o eliminar la clave sin revelar su valor.

## Criterios de aceptación

- Dueño/ADMIN puede consultar estado, configurar, reemplazar y borrar credenciales por evento y herramienta.
- Otro usuario/evento no puede leer ni cambiar la configuración; ADMIN queda auditado como cualquier actor.
- Cada herramienta usa solo su configuración de evento; no hay claves globales ni herencia.
- URL HTTP, destinos privados/reservados, redirects y resolución DNS no pública son rechazados; URLs HTTPS públicas compatibles funcionan.
- Las credenciales globales están ausentes del almacenamiento activo y no se copian ni reactivan.
- Pruebas cubren cifrado, persistencia, aislamiento, roles, redacción, endpoint personalizado y cada consumidor.

## Despliegue

La implementación local no habilita el gateway en producción. Antes de activarlo se deben provisionar por canal privado la clave maestra y tokens, configurar NetworkPolicy y observabilidad redactada, y validar en staging la integración, los consumidores y el borrado de datos activos.
