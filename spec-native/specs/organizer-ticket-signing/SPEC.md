+++
artifact_type = "spec"
id = "SPEC-TICKET-ORG-SIGN-0001"
state = "active"
owner = "product owner"
created_at = "2026-10-23"
updated_at = "2026-10-23"
replaces = "none"
related_tasks = ["TASK-OTS-0001", "TASK-OTS-0002", "TASK-OTS-0003", "TASK-OTS-0004"]
related_decisions = ["DEC-0043"]
artifacts = ["backend/services/insightbloom-users", "frontend/web", "spec-native/workflows/TICKET-OFFLINE-SIGNING.md"]
validation = ["mvn -f backend/services/pom.xml -pl insightbloom-users test", "npm --prefix frontend/web run typecheck", "npm --prefix frontend/web run build"]
+++

# Firma de boletos por organizador para validación offline

## Resumen

Cada boleto nuevo y cada snapshot offline de un evento quedan firmados con una clave Ed25519 bajo custodia local de su organizador; InsightBloom conserva solo la clave pública y las firmas.

## Problema

La emisión de boletos no lleva una firma verificable del organizador y el paquete offline depende de una clave privada global de la plataforma. El QR rotativo actual ya demuestra posesión de un dispositivo vinculado, pero no prueba por sí mismo la autenticidad de la emisión ni permite que el organizador controle la confianza offline.

## Objetivo

El dueño del evento puede firmar boletos durante la emisión online y preparar paquetes para check-in offline sin enviar su clave privada a InsightBloom. Un lector puede validar la firma del organizador, el estado incluido en el snapshot y la presentación firmada por el dispositivo del asistente.

## Alcance

**Incluye:** clave Ed25519 por evento; registro de clave pública con prueba de posesión; respaldo local cifrado; firmas COSE_Sign1 por boleto; cola para emisión pública y manual; snapshots offline firmados por el organizador; lector que verifica tres capas; revocación/rotación de claves; auditoría y soporte de legado.

**Excluye:** emitir o vender boletos sin conexión; custodiar o recuperar claves privadas de organizador; impedir doble uso entre lectores sin conexión entre sí; sustituir la firma P-256 y el vínculo de dispositivo existentes; certificar identidad civil del poseedor.

## Requisitos funcionales

- RF-1: El dueño del evento genera una clave de firma Ed25519 localmente, registra solo su clave pública y acredita posesión firmando un reto. ADMIN puede asistir operaciones, que quedan auditadas.
- RF-2: La clave privada permanece local; se conserva como `CryptoKey` no exportable en IndexedDB y se ofrece un respaldo cifrado descargable que el usuario protege con contraseña. Restaurar requiere la contraseña; InsightBloom no puede recuperar la clave.
- RF-3: El backend crea una emisión en estado `PENDING_SIGNATURE`, reserva capacidad/asiento y no entrega QR ni envía correo hasta recibir y verificar la firma COSE del evento. El cliente firmante del dueño procesa emisiones manuales, lotes y auto-registro mientras permanezca online.
- RF-4: La firma del boleto cubre una estructura canónica versionada con evento, boleto, emisor/clave, tipo o asiento, fecha de emisión y vigencia; excluye correo, nombre y otros datos personales. Reintentar una firma idéntica es idempotente; una firma distinta para claims ya finalizados se rechaza.
- RF-5: El dueño obtiene un snapshot con boletos vigentes/cancelados y vínculos a credenciales de dispositivo; firma y publica el paquete. El backend valida la firma pública, almacena exactamente los bytes firmados y sirve la versión vigente a lectores autorizados. El paquete vence como máximo a las 24 horas.
- RF-6: El lector fija la clave pública del evento obtenida en preparación autenticada online y offline comprueba firma de paquete, firma COSE del boleto, estado y asociación dispositivo-boleto, y QR P-256 rotativo con ventana actual de 45 segundos.
- RF-7: El check-in offline se guarda localmente y sincroniza idempotentemente; el backend conserva la transición conectada atómica y reporta conflictos entre lectores como duplicados.
- RF-8: Las APIs permiten registrar, rotar y revocar claves públicas; leer la cola de firmas; finalizar una emisión; crear/publicar un snapshot; descargarlo y sincronizar escaneos. Solo el dueño del evento o ADMIN puede operar las funciones de firma/publicación.
- RF-9: Boletos legacy continúan online con su compatibilidad existente y se identifican como no verificables offline hasta reemitirse. El paquete offline no utiliza clave privada global ni fallback de plataforma.

## Requisitos no funcionales

- RNF-1: Rust/WASM implementa serialización COSE determinista y validación de estructuras; Web Crypto ejecuta firma/verificación con claves no exportables. WASM no se considera aislamiento de secretos.
- RNF-2: Ningún endpoint, log, traza, auditoría o base activa de InsightBloom persiste, retorna o registra la clave privada, contraseña de respaldo ni material privado descifrado.
- RNF-3: Claims, algoritmo, `kid`, versión, evento, caducidad y perfil COSE se validan estrictamente; evitar ambigüedad de CBOR/JSON y firmas fuera de contexto.
- RNF-4: La operación es accesible por teclado/lector de pantalla y usable en móvil; indica sin ocultar pendientes de firma, clave activa, paquete obsoleto y conflictos offline.

## Criterios de aceptación

- Dado un evento sin clave, cuando su dueño inicia emisión protegida, entonces la interfaz exige crear/restaurar el firmante y no entrega boletos sin firma válida.
- Dado un boleto, cuando cambian evento, UUID, asiento, tipo, vigencia o claims firmados, entonces la verificación COSE falla.
- Dada una clave privada perdida, cuando el dueño restaura una copia con contraseña correcta, entonces puede firmar localmente; con contraseña incorrecta no se revela la privada ni se habilita el firmante.
- Dado un lector preparado antes del evento, cuando no tiene conexión, entonces valida boleto, snapshot y QR del dispositivo; rechaza paquetes alterados, expirados, boletos cancelados/ausentes y dispositivos no incluidos.
- Dado un QR válido presentado en dos lectores desconectados, cuando ambos sincronizan, entonces se conserva un check-in y el otro queda marcado como conflicto/duplicado.
- Dado un boleto legacy, cuando se intenta validarlo offline, entonces se rechaza con explicación y se conserva el flujo online legado.
- Dado un usuario que no es dueño ni ADMIN, cuando administra clave, firma emisiones o publica paquetes, entonces recibe 403 y se audita el rechazo sin secretos.

## Dependencias y riesgos

- Emisión, registro de asistentes y asignación de asientos siguen online. El cliente firmante del dueño debe procesar las solicitudes pendientes antes de desconectarse.
- La firma local del organizador requiere interacción entre el cliente firmante y la cola backend de emisiones; el envío del boleto y correo se posterga hasta completarse la firma.
- Un paquete offline no conoce cancelaciones posteriores a su descarga. Una clave revocada tampoco puede retirarse de lectores ya desconectados; vigencia máxima de 24 horas acota la exposición.
- Ningún lector desconectado de los demás puede evitar el doble uso global en tiempo real; sincronizar detecta, no deshace, el acceso físico ya concedido.
- Perder la clave y su respaldo impide firmar nuevas emisiones y snapshots; es una responsabilidad explícita del dueño.

## Plan de validación

- Pruebas de interoperabilidad COSE Rust/WASM ↔ Java, incluyendo vectores Ed25519, firma alterada, `kid`, claims canónicos y rechazo de perfil/versiones no admitidos.
- Pruebas backend de estados pendientes, reserva/reversión, firma incorrecta/duplicada, propietario ajeno, envío de correo solo después de firma y rotación/revocación.
- Pruebas frontend de generación/restauración y respaldo cifrado, ausencia de envío de privada, emisión manual/lote/auto-registro, estados accesibles y bloqueo al faltar firmante.
- Pruebas scanner online/offline de snapshot, boleto, vínculo P-256, caducidad, boleto cancelado, QR reproducido, cola local, sincronización y duplicados.
- Suite Maven de `insightbloom-users`, typecheck/build frontend y validación de chart/GitOps; prueba integrada en staging con dos lectores y evento de ensayo antes de retirar las variables globales de firma.
