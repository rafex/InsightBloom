# Provisionar la firma de paquetes offline de check-in

`GET /{event}/tickets/offline-bundle` solo firma un paquete cuando `insightbloom-users` tiene una pareja Ed25519. Sin esas variables el servicio falla cerrado con `503 offline_signing_not_configured`.

## Generar una pareja

Ejecuta estos comandos en una estación confiable. Conserva el archivo privado en el gestor de secretos; no lo subas a Git ni lo pegues en tickets o logs.

```sh
openssl genpkey -algorithm Ed25519 -out ticket-offline-private.pem
openssl pkey -in ticket-offline-private.pem -outform DER | base64 | tr -d '\n'
openssl pkey -in ticket-offline-private.pem -pubout -outform DER | base64 | tr -d '\n'
```

El primer valor va en `TICKET_OFFLINE_SIGNING_PRIVATE_KEY` y el segundo en `TICKET_OFFLINE_SIGNING_PUBLIC_KEY`. Guárdalos en las claves `ticket-offline-signing-private-key` y `ticket-offline-signing-public-key` del Secret `insightbloom-users-secrets`; el chart del servicio los lee como variables opcionales. El backend valida que ambas claves correspondan antes de firmar.

## Validar antes de habilitar lectores

1. Reinicia solo el workload `users` después de actualizar el Secret.
2. Descarga un paquete desde el lector autenticado y verifica que el endpoint responda `200`.
3. Confirma que quitar o alterar cualquier clave vuelve a producir `503` y no firma datos.
4. Comprueba que el paquete no incluya claves privadas ni información personal; solo UUID de boleto/credencial y clave pública.
5. Regenera y provisiona una pareja para rotarla. Los paquetes anteriores dejarán de sincronizar con la clave pública nueva, así que sincroniza las colas offline antes de rotar.

El paquete expira a las 24 horas. La cola offline queda en el navegador del lector y conserva UUID, firma y hora local de escaneo hasta sincronizar.
