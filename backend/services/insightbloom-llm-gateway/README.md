# InsightBloom LLM Gateway

Servicio interno para custodiar credenciales de proveedor y ejecutar solicitudes LLM aprobadas. No exponerlo a Internet. Se mantiene deshabilitado si no están configuradas la clave maestra, las identidades de servicio y la clave de firma de contexto.

## Secretos de runtime

- `LLM_CREDENTIALS_MASTER_KEY_V1`: clave AES-256 aleatoria, codificada en Base64.
- `LLM_CREDENTIALS_ACTIVE_KEY_VERSION`: versión activa (por ejemplo `v1`).
- `LLM_GATEWAY_USERS_TOKEN`, `LLM_GATEWAY_CHAT_TOKEN`, `LLM_GATEWAY_SURVEY_TOKEN`: secretos independientes de alta entropía; el token de gestión solo se entrega a `users`.
- `LLM_GATEWAY_CONTEXT_SIGNING_KEY`: secreto de al menos 32 caracteres que solo debe compartir `users` y el gateway para tokens breves ligados a organizador, evento, capacidad y expiración.
- `LLM_GATEWAY_DATABASE`: ruta SQLite en un PVC dedicado con acceso exclusivo al gateway.

No usar `INTERNAL_API_KEY` para cifrado ni como identidad del gateway. Nunca imprimir variables secretas ni incluirlas en ConfigMaps.

## Rotación

1. Agregar una nueva clave como `LLM_CREDENTIALS_MASTER_KEY_V2` y mantener la clave anterior disponible.
2. Cambiar `LLM_CREDENTIALS_ACTIVE_KEY_VERSION=v2` en una ventana de mantenimiento del gateway.
3. Ejecutar `python scripts/reencrypt_credentials.py` con acceso de escritura exclusivo a la base.
4. Confirmar `reciphered_records` esperado y que los registros usan prefijo `v2:` sin consultar ni imprimir valores.
5. Reiniciar el servicio con la nueva clave activa, comprobar estado, y retirar la clave anterior solo después de confirmar que no quedan filas con prefijo antiguo.

La rotación se realiza offline y no tiene endpoint HTTP administrativo. El proveedor/modelo se selecciona desde `app/catalog.py`; ninguna URL llega desde la petición.

## Estado de migración

El gateway y su almacenamiento ya existen, pero la API de gestión de `users`, las credenciales de servicio/contexto, los consumidores y el despliegue restringido aún deben conectarse antes de habilitarlo. Hasta completar esa migración, conservar el endpoint heredado evita una interrupción de IA; no habilitar este gateway ni considerarlo listo para producción.
