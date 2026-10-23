# Tareas — Vinculación de boletos a dispositivos

**Spec:** [`spec-native/specs/ticket-device-binding/SPEC.md`](../../specs/ticket-device-binding/SPEC.md)

### TASK-0001: Persistencia, credenciales y enrolamiento de dispositivos

**Estado:** done  
**Owner:** Codex  
**Dependencias:** —  
**Archivos esperados:** backend `insightbloom-users`, frontend de tickets y scanner  
**Criterio de cierre:** configuración por boleto, hash de tokens/OTP, emisión/validación de claves públicas, límites y reemplazo funcionan con actualización SQLite idempotente; secretos nunca se retornan ni registran.  
**Validación:** `SqliteTicketDeviceRepositoryTest` cubre consumo único, límite y reemplazo; `TicketDeviceCryptoTest` cubre JWK pública P-256 y firma Web Crypto.

### TASK-0002: Código rotativo y check-in conectado

**Estado:** done  
**Owner:** Codex  
**Dependencias:** TASK-0001  
**Archivos esperados:** `TicketPage.vue`, `CheckInScannerPage.vue`, APIs y casos de uso de ticketing  
**Criterio de cierre:** la firma del dispositivo autoriza check-in de boletos protegidos; el endpoint actual conserva compatibilidad explícita solo para boletos legado; la actualización concurrente sigue siendo atómica.  
**Validación:** firma y rechazo del QR estático de boletos protegidos; check-in conserva la transición atómica existente. La suite del servicio corre completa.

### TASK-0003: Verificación y sincronización offline

**Estado:** in_progress  
**Owner:** Codex  
**Dependencias:** TASK-0001, TASK-0002  
**Archivos esperados:** backend de check-in, `CheckInScannerPage.vue`, almacenamiento local y APIs  
**Criterio de cierre:** lector autorizado descarga el paquete firmado, verifica firmas sin red, encola check-ins y sincroniza; paquete alterado/revocado se rechaza según el snapshot y duplicados se marcan al sincronizar con advertencia visible.  
**Validación:** lector guarda y verifica paquete Ed25519, valida presentaciones, encola y sincroniza con marca de duplicados. Pendiente provisionar la pareja del Secret y completar validación en staging.

### TASK-0004: Gestión de emisión, UX de asistentes y migración legado

**Estado:** in_progress  
**Owner:** Codex  
**Dependencias:** TASK-0001, TASK-0002  
**Archivos esperados:** `TicketManagementPage.vue`, `TicketPage.vue`, tipos y cliente API  
**Criterio de cierre:** comprador configura 1–5 dispositivos y modo OTP/link por boleto; cada boleto de un lote permite asignación propia; asistente accountless puede vincular y recuperar; los existentes solo se migran con OTP o reemisión y el resto se identifica como legado. UX accesible móvil/teclado.  
**Validación:** emisión, activación, dispositivo y checkout migran boletos nuevos; legacy queda marcado y requiere reemisión del organizador. Pendiente validar recorridos UI en staging y pulir la reemisión operativa de legacy.
