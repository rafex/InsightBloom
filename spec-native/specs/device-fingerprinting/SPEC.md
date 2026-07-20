# SPEC: Control de acceso por huella de dispositivo (device fingerprinting)

## Initiative
device-fingerprinting

## Status
active

## Summary
InsightBloom controla malos usos de la plataforma (multicuenta, evasión de límites, sesiones
robadas) usando una huella real de dispositivo (`@thumbmarkjs/thumbmarkjs`: canvas, WebGL, audio,
fuentes, hardware), capturada en cada login/registro y reusada en tres capas independientes: por
evento (Jitsi/IDE), a nivel plataforma (login/registro), y auditoría pasiva en cada request
autenticado. Ver DEC-0028, DEC-0029 y DEC-0030 en [`DECISIONS.md`](../../DECISIONS.md) para el
detalle de cada capa.

## Problem
Antes de esta iniciativa, el "fingerprint de dispositivo" del proyecto era un UUID aleatorio
persistido en `localStorage` (no una huella real), usado solo en dos llamadas puntuales (pedir
token de Jitsi, pedir sandbox de IDE) y nunca capturado en el login. Eso dejaba varios huecos:

- Ningún control correlacionaba abuso entre distintas conferencias (multicuenta repartida entre
  eventos para evadir el límite de uno solo).
- No existía límite de sesiones simultáneas por usuario.
- No había control alguno de spam de registro de cuentas.
- Un token robado "pelado" (sin el navegador que lo generó — filtrado en un log, un ticket de
  soporte, copiado del Network tab) servía igual desde cualquier lado para cualquier ruta
  autenticada, sin ninguna señal de alerta.

## Objective
- Reemplazar el UUID falso por una huella real (ThumbmarkJS), capturada en cada login (usuario,
  invitado) y registro de cuenta.
- Mantener el control existente por evento (Jitsi/IDE dentro de una conferencia) intacto.
- Agregar control de abuso a nivel plataforma (límite de sesiones por usuario, multicuenta entre
  eventos, spam de registro), con bloqueo real pero siempre revisable por un `system_admin`.
- Agregar visibilidad (sin bloquear) de discrepancias de huella dentro de una misma sesión ya
  logueada, en cada request autenticado — señal de auditoría para sospecha de robo de sesión, sin
  arriesgar deslogueos fantasma por randomización legítima de navegadores con foco en privacidad.

## Scope
### Includes
- Integración real de ThumbmarkJS (`frontend/web/src/services/auth/fingerprint.ts`), con fallback
  al UUID de `localStorage` si la librería falla o supera el timeout.
- `PlatformDeviceGuard` (dominio, `insightbloom-users`): límite de sesiones simultáneas por
  usuario, límite de cuentas distintas por dispositivo, límite de registros por dispositivo/día —
  todos a nivel plataforma, integrados en `LoginUseCase`, `CreateGuestUseCase`, `RegisterUseCase`.
- `DeviceFingerprintAuditor` + middleware Jetty global (`DeviceFingerprintAuditHandler`): compara
  el fingerprint de cada request autenticado contra el del login de esa sesión; solo audita
  (upsert por sesión), nunca bloquea.
- Panel `/dashboard/admin/device-access`: umbrales configurables, cola de revisión de bloqueos de
  plataforma, cola de revisión de discrepancias de huella detectadas.
- `DeviceAccessGuard` por evento (Jitsi/IDE) — **ya existía** antes de esta iniciativa, sin cambios
  de comportamiento; se documenta acá para dejar clara la relación con las capas nuevas.

### Non-Goals (por ahora)
- Bloqueo duro por mismatch de fingerprint en requests individuales (deliberadamente descartado —
  ver DEC-0030, "Consecuencias").
- Cobertura de herramientas fuera de Jitsi/IDE en el control por evento (Diagramas, Pizarra,
  Notas, Encuestas siguen sin ningún control de dispositivo).
- Integración con la API paga de ThumbmarkJS (que sube la unicidad de ~80% a ~99% agregando señales
  de servidor — TLS handshake, headers, IP).

## Risks
- ThumbmarkJS no es 100% estable entre sesiones: navegadores con foco en privacidad (Firefox modo
  estricto, Brave, Tor Browser) randomizan canvas/audio/WebGL a propósito — de ahí que el control
  por request sea solo auditoría, nunca bloqueo (ver DEC-0030).
- El middleware global (`JettyMiddleware`) agrega una consulta extra a `tokens` por cada request
  autenticado (duplica el trabajo que `ValidateTokenUseCase` hace igual en el handler) — aceptado
  como costo razonable en esta escala a cambio de no tocar ~55 call-sites repartidos en 12
  handlers.
- Compus compartidas de laboratorio son un caso real y ambiguo para la detección de multicuenta —
  por eso todo bloqueo de plataforma queda en cola de revisión humana, nunca es un ban permanente
  automático.

## Referencias
- Documentación con diagramas de flujo (mermaid): ver `spec-native/DECISIONS.md` (DEC-0028 a
  DEC-0030) para el detalle técnico completo de cada capa, y el código fuente referenciado ahí.
- Código clave: `frontend/web/src/services/auth/fingerprint.ts`,
  `backend/services/insightbloom-users/.../domain/services/DeviceAccessGuard.java`,
  `.../PlatformDeviceGuard.java`, `.../DeviceFingerprintAuditor.java`,
  `.../adapters/inbound/http/middleware/DeviceFingerprintAuditHandler.java`.
