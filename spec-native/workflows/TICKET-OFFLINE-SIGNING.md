# Operación de firma de boletos y preparación offline

## Antes de emitir

1. El dueño abre **Configuración del evento → Acceso → Firma del organizador** desde el navegador donde conservará la clave.
2. Crea una clave Ed25519 propia para el evento y descarga el respaldo cifrado con contraseña. Guarda el archivo y la contraseña fuera de InsightBloom. La plataforma no puede recuperar la clave.
3. Restaura ese respaldo en un navegador nuevo solo si hace falta y comprueba que corresponde a la clave pública activa del evento.
4. Mantén el navegador con la clave desbloqueada y la sesión del organizador disponible mientras se crean boletos. La lista de pendientes se actualiza manualmente con **Actualizar cola**. Firma cada emisión; el QR y el correo solo se habilitan después de verificar la firma.

## Preparar lectores

1. Cuando ya no haya emisiones pendientes, el dueño selecciona **Firmar paquete para lectores offline**. El paquete contiene el estado conocido de boletos, cancelaciones, claves públicas de organizador y dispositivos vinculados; no contiene datos personales ni claves privadas.
2. Cada lector autorizado abre el scanner del evento mientras tiene conexión y selecciona **Preparar lector sin conexión**. El lector verifica la firma del organizador y conserva el paquete hasta su expiración, con un máximo de 24 horas.
3. Completa esta descarga antes de perder conexión. Las cancelaciones, revocaciones y boletos emitidos después de la descarga no estarán reflejados en lectores aislados.
4. Al reconectarse, sincroniza la cola desde el mismo lector. El backend aplica el check-in de forma idempotente y señala boletos usados por lectores desconectados entre sí.

## Rotación, revocación y recuperación

- Rotar o revocar una clave impide nuevas firmas. Después de registrar la siguiente clave, prepara y descarga un paquete nuevo para todos los lectores.
- Un lector ya desconectado puede confiar en el paquete anterior hasta su expiración; la revocación no se propaga mágicamente sin conexión.
- Si el dueño pierde el navegador, restaura la clave desde el respaldo cifrado en un navegador autorizado. Si pierde también el respaldo o la contraseña, revoca la clave y registra otra. Los boletos ya emitidos conservan su firma histórica pública.
- Los boletos legacy siguen el flujo online existente y no se aceptan en modo offline hasta que tengan firma válida y dispositivo vinculado.

## Límites operativos

- Las emisiones y el registro de asistentes requieren conexión. El organizador debe tener el cliente firmante disponible para finalizar cada emisión antes de desconectarse.
- La clave privada nunca se provisiona en Kubernetes, variables de entorno, el backend ni los lectores. No se configura `TICKET_OFFLINE_SIGNING_PRIVATE_KEY` ni una clave privada global de plataforma.
- Los lectores aislados no pueden impedir por sí solos que otra unidad acepte el mismo boleto. La sincronización posterior detecta el conflicto, pero no revierte el acceso ya concedido.
- Antes de producción, validar en staging emisión pública/manual, firma, expiración, revocación, dos lectores y sincronización duplicada.
