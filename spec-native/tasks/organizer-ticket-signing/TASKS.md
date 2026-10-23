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
