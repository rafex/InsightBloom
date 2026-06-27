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

## No hacer por ahora

- Ticketing, pagos o registro masivo de asistentes.
- Clustering semantico avanzado por IA.
