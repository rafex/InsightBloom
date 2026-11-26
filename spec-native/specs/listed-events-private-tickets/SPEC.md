+++
artifact_type = "spec"
id = "SPEC-LISTED-PRIVATE-TICKETS"
state = "active"
owner = "product owner"
created_at = "2026-11-26"
updated_at = "2026-11-26"
related_decisions = ["DEC-0042", "DEC-0043"]
+++

# Eventos visibles con boletos privados

## Initiative
listed-events-private-tickets

## Status
active

## Objective

El anfitrión puede publicar la ficha de un evento en la cartelera y controlar por separado la entrega de boletos. La ficha muestra el flyer, el cronograma y los datos públicos del evento. El acceso al evento sigue sujeto a los permisos y al boleto exigido por su tipo.

## Functional Requirements

- FR-001: Añadir **Visible · boletos privados** al crear o editar eventos con boletos. Los modos Privado, Público e Híbrido conservan su comportamiento anterior. La cartelera, la ficha pública y la vista previa del flyer reconocen el modo nuevo.
- FR-002: Mantener la emisión directa desde el panel de boletos con el flujo existente. La ficha muestra **Boletos por invitación** y el backend rechaza la adquisición pública por API para este modo.
- FR-003: El dueño o ADMIN puede activar un enlace de solicitud desde el panel de boletos, copiarlo o descargar su QR. El QR contiene la misma URL. El enlace admite múltiples asistentes, tiene vencimiento y puede rotarse o revocarse. Está desactivado inicialmente.
- FR-004: El enlace abre el formulario de solicitud. Una cuenta con correo verificado solicita su boleto y se reserva el cupo automáticamente. Para una cuenta con correo sin verificar, completar el OTP en la pantalla reintenta la emisión de inmediato. No hay cola de aprobación. Se reutiliza la firma del organizador y la vinculación del dispositivo del flujo normal.
- FR-005: Un enlace revocado, vencido o asociado a un evento inactivo rechaza nuevas solicitudes. Los boletos ya iniciados conservan su ciclo de emisión y validez.
- FR-006: El autoservicio por enlace se aplica a boletos generales gratuitos con aforo definido. La asignación manual admite eventos con asientos y permite coordinar fuera de la plataforma los boletos con precio.

## Non-functional Requirements

- NFR-001: Generar tokens aleatorios ligados al evento y guardar solo su hash. Evitar enviarlos en la ruta o en parámetros de consulta, y excluirlos de logs, analítica y auditoría. Usar un fragmento en el enlace, enviarlo mediante POST y retirarlo de la barra del navegador al abrirlo. Una visita GET o una vista previa del QR no crea solicitudes.
- NFR-002: Usar el aforo y deduplicar boletos activos por evento y destinatario en las solicitudes concurrentes. Registrar generación y revocación sin tokens. El boleto conserva la identidad de quien solicitó y su estado.
- NFR-003: Mostrar estados legibles en móvil y accesibles por teclado y lector de pantalla. La ficha pública no expone correos, solicitudes, enlace de solicitud ni datos de boletos de terceros.

## Acceptance Criteria

- Un visitante puede ver la ficha y el flyer sin cuenta. La UI y el endpoint público bloquean la emisión en el modo nuevo, aun cuando las ventas estén habilitadas.
- El anfitrión puede emitir directamente, o compartir un QR o URL, emitir automáticamente al comprobar correo y aforo. El asistente distingue enlace inválido, aforo agotado, reserva pendiente de firma y boleto disponible.
- Revocar o rotar un enlace impide que su URL anterior cree solicitudes. Escanear o reenviar el QR no permite entrar ni usar un boleto.
- Eventos sin boletos y eventos con modos anteriores mantienen sus reglas. Los límites de cupo y la firma configurada siguen aplicándose a la emisión.

## Execution Plan

1. Separar en el backend la publicación de la ficha y el permiso de adquisición pública. Extender el contrato de cartelera y la selección de modo.
2. Persistir enlaces y reutilizar la persistencia de boletos en users. Implementar autorización, verificación de correo, revocación y emisión idempotente.
3. Añadir los formularios, los estados de emisión y los controles para compartir el enlace y QR.
4. Verificar en staging el recorrido público, la asignación directa y el ciclo de solicitud y emisión con cuentas de eventos distintos. Verificar también la asignación directa en eventos con asientos.

## Validation Plan

Cubrir acceso anónimo a la ficha, rechazo de la compra pública, enlace inválido o vencido, revocación, intentos entre eventos, solicitudes duplicadas, solicitudes concurrentes, agotamiento de aforo y emisión con firma pendiente. Comprobar el flujo con QR desde un móvil y que las rutas de acceso exijan el boleto.
