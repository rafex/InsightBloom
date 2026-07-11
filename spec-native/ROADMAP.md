# ROADMAP.md

Direccion del proyecto en el tiempo. Actualizado post-PoC, fase producto.

## Ahora (activo)

- Documentar el sistema con SpecNative v0.7 (`spec-native/` completo).
- Estabilizar el pipeline CI/CD (9 servicios, GHCR, K3s).
- Cerrar hallazgos de seguridad pendientes del SECURITY.md.
- Mejorar cobertura de tests (backend Java, chat Python, frontend).

## Despues (próximo)

- Migrar SQLite a PostgreSQL con migraciones versionadas.
- Añadir tests de integración entre servicios (ingest→moderation→query).
- Mejorar la normalizacion y agrupacion de palabras.
- Evolucionar la medicion de intencion a un modelo mas rico.
- Añadir soporte de sugerencias de moderacion mas sofisticadas (LLM-assisted).

## Mas adelante (futuro)

- Catalogo de tipos de evento administrado por `ADMIN` (conferencia, taller,
  standup, concierto...), cada uno habilitando ciertas capacidades de la
  plataforma: boletos, mapa de asientos, encuestas, presentaciones, nube de
  palabras, chat, videollamada/transmision (Jitsi publico `meet.jit.si` o
  self-hosted en K3s), pizarra colaborativa (Excalidraw self-hosted),
  diagramas (drawio self-hosted), notas colaborativas (Etherpad
  self-hosted), un motor de encuestas alternativo con editor visual
  (SurveyJS, ademas del motor propio) y un motor de mapa de asientos
  alternativo para recintos con distribucion real de filas/butacas
  (seatmap-canvas, ademas del editor de marcadores libres actual). Ver
  `specs/event-types-catalog/SPEC.md` (draft).
- IDE web con ejecucion aislada de codigo (Java, Python, JavaScript, Rust) y
  previsualizacion estatica HTML/CSS, como una capacidad mas del catalogo
  de tipos de evento (ej. para "Taller"). Bloqueado hasta definir las reglas
  de seguridad de ejecucion de codigo — sin spec todavia. Nota de direccion
  a evaluar cuando se priorice: `code-server` (coder/code-server) vs
  `openvscode-server` (gitpod-io/openvscode-server) como base del IDE en
  vez de un motor de ejecucion a medida por lenguaje (ver Excludes de
  `specs/event-types-catalog/SPEC.md`).
- Motor de certificados alternativo con **pdfme** (generador + diseñador
  visual de plantillas PDF, MIT), como alternativa al configurador de
  certificados actual. Capacidad `CERTIFICATE_PDFME` reservada en el
  catalogo de `event-types-catalog`, sin iniciativa de implementacion
  propia todavia.
- Analitica avanzada de participacion.
- Recomendaciones o agrupaciones semanticas avanzadas.
- Soporte multi-conferencia desde una misma interfaz de organizador.
- Dashboard de operaciones (métricas de uso, health, alertas).
- Observabilidad (logs centralizados, métricas, tracing).

## Completed

- Nubes de palabras interactivas con D3.js (dudas + temas separadas).
- Timeline cronologico por palabra con detalle de mensajes.
- Dashboard de moderacion (censura manual + barrera automatica).
- Autenticacion JWT con roles (ADMIN, ORGANIZER, MODERATOR, GUEST).
- Fingerprint de dispositivo con ThumbmarkJS para guests.
- Bot de chat IA Roberto (DeepSeek/LLM) con WebSocket.
- Encuestas con soporte LLM opcional y certificados.
- Presentaciones de slides via Marp Markdown → HTML.
- Admin de usuarios (listar, editar, banear, soft-delete).
- Roles multiples por usuario (ej. ORGANIZER + ADMIN).
- OTP via Twilio SMS y Zoho email.
- Perfil de usuario editable.
- CLI administrativo (`insightbloom-cli create-user`).
- Helm charts para despliegue en K3s.
- Docker Compose con 9 servicios, healthchecks, volumenes.
- CI/CD con GitHub Actions (build, test, publish, deploy).
- Migracion de documentacion a SpecNative v0.7.
- Reservas de boletos gratuitas con QR y check-in (modo GENERAL, aforo).
- Reserva de asiento especifico via mapa del recinto (modo SEATED).

## No hacer por ahora

- Cobro o pagos sobre reservas/boletos.
- Clustering semantico avanzado por IA.
