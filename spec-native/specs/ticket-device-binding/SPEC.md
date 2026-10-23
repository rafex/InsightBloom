# Vinculación criptográfica de boletos a dispositivos

## Estado

**active** — implementación en progreso.

## Problema

Los boletos actuales incluyen un `ticketCode` UUID en un QR estático. Cualquiera que copie el QR antes del canje puede intentar reclamar o presentar el boleto. La huella Thumbmark y su fallback `localStorage` son señales variables y copiables; no prueban posesión del dispositivo.

## Resultado esperado

Un asistente puede reclamar y presentar un boleto sin crear una cuenta permanente. Cada boleto nuevo puede quedar ligado a una o más claves criptográficas generadas en los dispositivos autorizados. La huella de navegador solo se conserva como señal auxiliar de auditoría.

## Requisitos

- El comprador fija por boleto un máximo de 1 a 5 dispositivos; el default es 1.
- El comprador elige el método de primera asignación: OTP al correo destinatario o enlace secreto de un solo uso. El enlace tiene menor garantía si se filtra.
- El cliente genera claves ECDSA P-256 con Web Crypto; la clave privada es no exportable y se conserva localmente. El servidor almacena únicamente claves públicas y metadatos.
- La presentación usa un código rotativo firmado con la clave del dispositivo; códigos estáticos siguen aceptándose solo para boletos legado no migrados.
- Reemplazar un dispositivo requiere aprobación desde uno existente, OTP al correo asignado o acción autenticada del comprador. Se revocan las credenciales reemplazadas y se aplica el límite por boleto.
- El lector de check-in descarga un paquete firmado y acotado de validación antes del evento, valida sin conexión y guarda los check-ins pendientes para sincronizarlos. Se advierte que lectores desconectados no pueden evitar duplicados en tiempo real; las colisiones se auditan al sincronizar.
- La firma offline usa una clave Ed25519 dedicada provisionada como secreto de `insightbloom-users`; el endpoint responde 503 hasta que la pareja privada/pública esté disponible. Procedimiento: [`workflows/TICKET-OFFLINE-SIGNING.md`](../../workflows/TICKET-OFFLINE-SIGNING.md).
- Los boletos existentes se migran solo tras OTP al correo ya registrado o reemisión controlada. Boletos no verificables permanecen en modo legado hasta ser reemplazados.
- API, correo y logs nunca exponen claves privadas, OTP ni tokens de un solo uso en claro después de su entrega inicial.

## Fuera de alcance

- Identificación civil del asistente o garantía contra un atacante que controla el dispositivo ya vinculado.
- Bloqueo basado en fingerprint, IP, User-Agent o características de hardware.
- Garantía de unicidad de check-in entre lectores aislados de la red.

## Aceptación

- Registro accountless con OTP o link único, reemplazo y revocación, límites por boleto y rechazo de replay.
- Check-in online verifica firma, vigencia, estado y uso atómico.
- Scanner offline valida paquete y código rotativo, persiste su cola y detecta duplicados al sincronizar.
- La migración de boletos antiguos requiere prueba de correo o reemisión y mantiene trazabilidad de legado.
- Pruebas automatizadas cubren fallos de autenticación, carreras de registro/check-in, credenciales revocadas, paquete offline alterado y colisiones de sync.
