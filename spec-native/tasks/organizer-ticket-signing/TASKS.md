+++
artifact_type = "task_file"
initiative = "organizer-ticket-signing"
spec_id = "SPEC-TICKET-ORG-SIGN-0001"
owner = "Codex"
state = "in_progress"
+++

# Tareas — Firma de boletos por organizador

**Spec:** [`spec-native/specs/organizer-ticket-signing/SPEC.md`](../../specs/organizer-ticket-signing/SPEC.md)

## Tareas

### TASK-OTS-0001 — Claves por evento y cliente firmante local

```toml
id = "TASK-OTS-0001"
title = "Claves por evento y cliente firmante local"
state = "done"
owner = "Codex"
dependencies = []
expected_files = ["backend/services/insightbloom-users", "frontend/web", "frontend/web/package.json", "spec-native/workflows/TICKET-OFFLINE-SIGNING.md"]
close_criteria = "El dueño puede crear, restaurar, respaldar cifradamente, registrar, rotar y revocar la clave Ed25519 del evento; el servidor conserva solo la pública y la prueba de posesión valida pertenencia al evento."
validation = ["CryptoKey privada no exportable guardada localmente", "Respaldo incorrecto rechazado", "Reto de posesión, owner ajeno y claves revocadas cubiertos"]
completion_evidence = ["Implementada clave Ed25519 por evento en WebCrypto, almacenamiento CryptoKey local no exportable, respaldo AES-GCM cifrado, restauración/validación y prueba de posesión. Suite Maven users completa, typecheck y build web pasan; validación E2E de staging sigue en TASK-OTS-0004."]
```

Implementar persistencia SQLite de claves públicas/evento, KID, estados y auditoría; API de registro/reto/rotación/revocación autorizada; UX accesible del firmante. Implementar módulo Rust/WASM para COSE determinista sin tratar WASM como vault; operar la privada mediante Web Crypto. La copia de seguridad se cifra localmente y nunca se transmite.

### TASK-OTS-0002 — Firma COSE individual en todos los flujos de emisión

```toml
id = "TASK-OTS-0002"
title = "Firma COSE individual en todos los flujos de emisión"
state = "done"
owner = "Codex"
dependencies = ["TASK-OTS-0001"]
expected_files = ["backend/services/insightbloom-users", "frontend/web/src/pages/dashboard/TicketManagementPage.vue", "frontend/web/src/pages/public/PublicTicketCheckoutPage.vue", "frontend/web/src/services/api/usersApi.ts"]
close_criteria = "Cada flujo manual, lote, operativo y público termina con un ticket firmado por la clave activa del evento; el backend verifica y persiste COSE antes de entregar el QR o enviar correo."
validation = ["Pending firma, éxito/fallo, reintentos idempotentes y claims alterados cubiertos", "Capacidad/asiento reservados mientras pendiente y liberados al cancelar", "Ninguna privada ni PII incluida en claims/logs"]
completion_evidence = ["La emisión manual, lote y pública crea PENDING_SIGNATURE; el QR/correo se difiere hasta firma COSE Ed25519 verificada por backend y guardada; incluye UX de cola y estado pendiente en checkout. Suite Maven users completa y typecheck/build web pasan."]
```

Crear cola de firmas con ticket `PENDING_SIGNATURE`, claims canónicos no personales, API de lectura/finalización y verificación Ed25519 backend. Mantener el firmante del organizador conectado durante la emisión; bloquear entrega y notificación hasta firmar; mostrar pendientes y fallos recuperables.

### TASK-OTS-0003 — Paquetes offline firmados y verificación en lectores

```toml
id = "TASK-OTS-0003"
title = "Paquetes offline firmados y verificación en lectores"
state = "done"
owner = "Codex"
dependencies = ["TASK-OTS-0001", "TASK-OTS-0002"]
expected_files = ["backend/services/insightbloom-users", "frontend/web/src/pages/dashboard/CheckInScannerPage.vue", "frontend/web/src/utils/ticketDeviceCrypto.ts", "spec-native/workflows/TICKET-OFFLINE-SIGNING.md"]
close_criteria = "El dueño firma y publica snapshot con vigentes, cancelaciones y vínculos de dispositivo; cada lector descarga/pinea clave pública online y valida paquete, firma COSE de boleto y QR del dispositivo offline; escaneos sincronizan idempotentemente."
validation = ["Snapshot vencido/alterado, keyId/clave incorrectos, ticket revocado/ausente y dispositivo no vinculado rechazados", "Paquete vence como máximo a 24 horas", "Duplicados entre lectores quedan marcados al sincronizar"]
completion_evidence = ["Implementados snapshots firmados por evento, verificación de boleto COSE + QR del dispositivo en scanner, expiración máxima 24h y sync offline que conserva paquetes conocidos hasta expirar. Suite Maven users completa; Rust tests y build WASM pasan; frontend typecheck/build web pasan."]
```

Persistir snapshots firmados por evento sin modificar sus bytes. Reemplazar la firma de paquete global por la firma de evento, conservar el bundle firmado y cola local del scanner, y actualizar la UX para preparación previa, expiración y limitación de lectores aislados.

### TASK-OTS-0004 — Compatibilidad, retiro del firmante global y guía operativa

> **Update 2026-10-24T15:30:04Z:** Corrección desplegada: commit fffef06ca0dbf614de21c321f4c5df3dc8e76ca0; workflow Publish web exitoso, Flux aplicó build-40106759762 y Deployment tiene 1/1 pods actualizados. El HTML público carga el bundle fffef06c y el SW nuevo incluye CheckInScannerPage-B2W3ndmk.js. No se pudo repetir navegación autenticada en CUA porque al recargar regresó a login y esa pestaña conservó la versión vieja del service worker; staging de lectores sigue pendiente.

> **Update 2026-10-24T15:23:25Z:** Corregido CheckInScannerPage: se añadieron al retorno de setup los estados/acciones usados por el template offline (offlineQueue, online, syncing, offlineBundle, offlineLoading, offlineExpiryLabel, prepareOffline y syncOffline). lint, typecheck y build pasan localmente; pendiente confirmar el despliegue web y navegación al check-in.

> **Update 2026-10-23T19:06:07Z:** Referencias activas a TICKET_OFFLINE_SIGNING_PRIVATE_KEY/PUBLIC_KEY y signer global retiradas del código/GitOps; guía operativa actualizada. Pendiente ejecutar staging con dos lectores, rotación/revocación y sincronización duplicada antes de cerrar el gate de salida.

```toml
id = "TASK-OTS-0004"
title = "Compatibilidad, retiro del firmante global y guía operativa"
state = "in_progress"
owner = "Codex"
dependencies = ["TASK-OTS-0003"]
expected_files = ["backend/services/insightbloom-users", "frontend/web", "infrastructure/charts/insightbloom/values.yaml", "spec-native/workflows/TICKET-OFFLINE-SIGNING.md", "spec-native/DECISIONS.md"]
close_criteria = "Legacy sigue online-only; staging confirma emisión, firma, paquete y check-in con dos lectores; retiradas TICKET_OFFLINE_SIGNING_PRIVATE_KEY/PUBLIC_KEY globales tras verificar que ningún workload las requiere."
validation = ["Suite Maven users", "Frontend typecheck/build", "Helm lint GitOps", "Staging verifica doble lector y marca colisiones"]
```

Documentar ciclo de vida/recuperación/rotación de clave, preparar ruta de reemisión de legacy, validar restauración de respaldo y staging. Retirar variables, secret refs y signer global después de migrar y verificar el flujo de evento.

### TASK-ORGANIZER-TICKET-0001 - Emisión automática de boletos con firma de InsightBloom

> **Update 2026-11-26T23:41:24Z:** Implementación de aplicación y GitOps completada localmente. Validaciones observadas: Maven users 433 tests; signer Python 3 tests; frontend typecheck/build; Helm lint/template; manifiesto SOPS descifrado y campos presentes; git diff --check. El checkout público/protegido usa TicketUseCase y en PLATFORM emite con firma antes de responder. No se desplegó el pod ni se ejecutó smoke test de clúster; eventos existentes conservan ORGANIZER hasta cambiar su modo.

> **Update 2026-11-26T23:08:58Z:** Implementing site-managed per-event Ed25519 signing through a dedicated internal gopass-backed signer pod. User approved automatic signatures for new events, completion of pending tickets on enablement, online-only validation, and organizer signing compatibility.

```toml
id = "TASK-ORGANIZER-TICKET-0001"
title = "Emisión automática de boletos con firma de InsightBloom"
state = "in_progress"
priority = "p1"
owner = "product owner"
labels = []
dependencies = []
expected_files = ["backend/services/insightbloom-ticket-signer", "backend/services/insightbloom-users", "frontend/web", "spec-native/specs/organizer-ticket-signing/SPEC.md"]
close_criteria = "Pod interno gopass provisionado y API de firma autenticada por service-to-service auth con acceso de red restringido a insightbloom-users; nunca retorna material privado; clave Ed25519 por evento. Modo configurable y default en eventos nuevos; boletos nuevos y pendientes se emiten como ISSUED automáticamente con correo una vez; QR incluye COSE Ed25519 y check-in online verifica firma, evento y estado DB; errores de signer producen rollback/no entregan boleto; eventos de firma de organizador conservan flujo; offline deshabilitado para modo InsightBloom."
validation = ["Pruebas de API del signer: auth/deny, validación de evento y carga, firma determinista, secreto nunca en respuesta.", "Pruebas backend para signer client, emisión, idempotencia/email, pendientes, verificación de firma y rollback de capacidad ante errores.", "Pruebas frontend para modo por evento, QR firmado y mensajes de emisión.", "mvn -f backend/services/pom.xml -pl insightbloom-users test", "npm --prefix frontend/web run typecheck", "npm --prefix frontend/web run build", "Helm lint/template e inspección de NetworkPolicy del pod signer."]
```

Agregar modo de firma automático configurable por evento, predeterminado para eventos nuevos y conservando la firma del organizador en eventos existentes. Un pod interno dedicado usa gopass para custodiar las claves privadas Ed25519 por evento y ofrece una API autenticada de firma consumible solo por insightbloom-users; la NetworkPolicy limita ingreso y salida del pod, y las claves privadas no salen del pod. InsightBloom firma boletos COSE al solicitarse, completa pendientes existentes al activar el modo, incluye la firma en el QR y valida firma, evento y estado en check-in online. El check-in offline no está disponible para eventos con firma de InsightBloom.
