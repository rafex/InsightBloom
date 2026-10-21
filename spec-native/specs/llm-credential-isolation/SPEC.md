# Credenciales LLM aisladas por organizador y evento

**Estado:** active
**Owner:** product owner
**Fecha:** 2026-10-21

## Objetivo

Permitir que organizadores configuren proveedores LLM sin que las claves abandonen el servicio dedicado que las almacena y usa. Las credenciales se aíslan por organizador y, opcionalmente, por evento y capacidad.

## Comportamiento

- Cada capacidad admite una clave predeterminada del organizador y una sobrescritura por evento.
- Precedencia: override del evento → credencial del organizador → IA deshabilitada. Las claves globales de plataforma no se usan como fallback.
- Solo el gateway descifra claves y se comunica con proveedores.
- El catálogo de proveedor/modelo es cerrado y administrado por el sistema; no se aceptan URLs arbitrarias.
- El API nunca devuelve claves, pistas parciales ni ciphertext. Reemplazo y borrado son operaciones explícitas.
- El organizador gestiona su propia clave; el propietario del evento o ADMIN gestiona el override.
- El gateway autentica servicios llamantes, verifica evento/capacidad, limita uso y audita metadatos sin prompts, claves ni cuerpos.
- El formato cifrado es versionado y permite recifrado durante rotación de la clave maestra dedicada.

## Capacidades en alcance

`chat`, `tutor`, `survey`, `seatLayout` y `email`.

## Fuera de alcance

- Migrar automáticamente claves globales existentes.
- Desplegar o habilitar el gateway en producción desde esta iniciativa.
- Guardar claves de proveedores en servicios consumidores o en el navegador más allá del envío de reemplazo por TLS.

## Requisitos de seguridad y UX

- AES-GCM con nonce aleatorio y clave maestra aleatoria dedicada, inyectada solo al gateway.
- Claves y cuerpos sensibles excluidos de logs, trazas, errores y telemetría.
- Autorización del lado servidor para cada operación de configuración y cada generación.
- Falla cerrada ante credenciales ausentes, identidad inválida, modelo no permitido, límites excedidos o proveedor no disponible.
- UI accesible y móvil que informa estado configurado, proveedor/modelo y fecha de cambio; nunca muestra la clave almacenada.

## Criterios de aceptación

- Persistencia cifrada y descifrado con clave correcta; rechazo de clave maestra incorrecta y ciphertext alterado.
- Rotación con recifrado y persistencia entre reinicios.
- Aislamiento de organizadores y eventos; verificación de permisos para cambios.
- Precedencia de credenciales y deshabilitación sin fallback global.
- Catálogo cerrado, límites, autenticación del servicio y auditoría solo de metadatos.
- Ninguna respuesta, excepción, log o snapshot de UI expone clave, fragmento o ciphertext.
- Consumidores ya no leen `/settings/ai/internal`; ese endpoint deja de distribuir credenciales.
- Pruebas locales/staging comprueban además trazas de proxy/APM; ningún sondeo ni cambio de producción.

## Despliegue

Desplegar apagado, provisionar secreto maestro y tokens de servicio de forma privada, migrar y validar consumidores en staging, aplicar políticas de red y auditoría, y solo entonces retirar el endpoint heredado.
