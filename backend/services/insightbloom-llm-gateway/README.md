# InsightBloom LLM Gateway

Servicio interno para custodiar credenciales de proveedor y ejecutar solicitudes LLM aprobadas. No exponerlo a Internet. Se mantiene deshabilitado si no están configuradas la clave maestra, las identidades de servicio y la clave de firma de contexto.

## Secretos de runtime

- `LLM_CREDENTIALS_MASTER_KEY_V1`: clave AES-256 aleatoria, codificada en Base64.
- `LLM_CREDENTIALS_ACTIVE_KEY_VERSION`: versión activa (por ejemplo `v1`).
- `LLM_GATEWAY_USERS_TOKEN`, `LLM_GATEWAY_CHAT_TOKEN`, `LLM_GATEWAY_SURVEY_TOKEN`: secretos independientes de alta entropía. El token de gestión solo se entrega a `users`; `chat` también lo presenta a `users` para recibir una aserción firmada de evento, sin acceso a la clave de firma.
- `LLM_GATEWAY_CONTEXT_SIGNING_KEY`: secreto de al menos 32 caracteres compartido por `users`, `survey` y el gateway para tokens breves ligados a evento, capacidad y expiración. `chat` recibe su aserción breve desde `users`; nunca recibe esta clave.
- `LLM_GATEWAY_DATABASE`: ruta SQLite en un PVC dedicado con acceso exclusivo al gateway.

No usar `INTERNAL_API_KEY` para cifrado ni como identidad del gateway. Nunca imprimir variables secretas ni incluirlas en ConfigMaps.

## Rotación

1. Agregar una nueva clave como `LLM_CREDENTIALS_MASTER_KEY_V2` y mantener la clave anterior disponible.
2. Cambiar `LLM_CREDENTIALS_ACTIVE_KEY_VERSION=v2` en una ventana de mantenimiento del gateway.
3. Ejecutar `python scripts/reencrypt_credentials.py` con acceso de escritura exclusivo a la base.
4. Confirmar `reciphered_records` esperado y que los registros usan prefijo `v2:` sin consultar ni imprimir valores.
5. Reiniciar el servicio con la nueva clave activa, comprobar estado, y retirar la clave anterior solo después de confirmar que no quedan filas con prefijo antiguo.

La rotación se realiza offline y no tiene endpoint HTTP administrativo. Cada evento puede usar proveedor/modelo del catálogo o una URL OpenAI-compatible HTTPS validada contra destinos privados y redirecciones.

## Estado de migración

La API de gestión de evento, la interfaz y los consumidores `users`, `survey` y `chat` ya llaman al gateway. El arranque de `users` borra la configuración global activa y el repositorio dejó de leerla o escribirla. El gateway sigue apagado en producción hasta provisionar secretos dedicados, actualizar GitOps, revisar límites de red/observabilidad y validar en staging. No restaurar claves globales desde backups históricos.
