# SPEC: Editor de correo con soporte Markdown/HTML/Texto plano + Asistente IA

## Initiative
email-compose-editor

## Status
active

## Summary
Reemplazar el textarea plano de envío de correos a inscritos por un editor con soporte para Markdown, HTML y texto plano, incluyendo preview toggle y un asistente de IA independiente para redaccion de borradores.

## Problem
El organizador solo puede escribir texto plano en el campo de mensaje al comunicarse con inscritos. No puede usar formato (negritas, listas, encabezados) ni obtener ayuda de IA para redactar el correo. Esto limita la calidad de las comunicaciones y genera friccion cuando el organizador no sabe que escribir.

## Objective
Al completar, el organizador puede:
1. Elegir entre Markdown, HTML o texto plano como formato del mensaje.
2. Previsualizar el resultado renderizado antes de enviar (toggle).
3. Solicitar un borrador al asistente IA describiendo el proposito del email.
4. El email llega al inscrito con el formato aplicado (HTML renderizado).

## Scope
### Includes
- Editor con selector de formato (Markdown / HTML / Texto plano)
- Preview toggle (vista previa renderizada)
- Boton de asistente IA para generar borradores
- Configuracion independiente de LLM para emails (capability `email`)
- Sanitizacion HTML (solo tags semanticos: p, h1-h6, ul, ol, li, a, strong, em, img, br, hr, blockquote, code, pre)
- Conversion Markdown→HTML en frontend (usando `marked` existente)
- Endpoint backend para generacion de borradores via IA
- Campo `format` en el payload de envio

### Excludes
- Editor WYSIWYG visual (solo textarea con preview)
- Soporte para attachments/imagenes inline en el editor
- Plantillas de email predefinidas (la plantilla base AttendeeEmailTemplate se mantiene)
- Cambios en otros flujos de email (tickets, OTP, recordatorios)

## Functional Requirements
- FR-001: El editor muestra 3 tabs de formato: Markdown, HTML, Texto plano.
- FR-002: El formato por defecto es Markdown.
- FR-003: El toggle de preview muestra el resultado renderizado segun el formato seleccionado.
- FR-004: El boton "Asistente IA" abre un panel inline donde el organizador describe el proposito y recibe un borrador.
- FR-005: El asistente IA usa una configuracion de LLM independiente (capability `email`).
- FR-006: El preview de HTML sanitiza tags no semanticos (whitelist).
- FR-007: El preview de Markdown convierte a HTML via `marked`.
- FR-008: El preview de texto plano muestra con saltos de linea preservados.
- FR-009: Al enviar, el frontend convierte Markdown→HTML y sanitiza HTML antes de enviar al backend.
- FR-010: El backend recibe el campo `format` (default `"text"` para backward compat).
- FR-011: El backend sanitiza HTML recibido (whitelist de tags semanticos) antes de insertar en la plantilla.
- FR-012: La plantilla `AttendeeEmailTemplate` inserta HTML sanitizado directamente (sin escapeHtml) cuando el formato es HTML.
- FR-013: El endpoint `POST /{id}/email/draft` genera un borrador via IA y retorna `{ draft }`.
- FR-014: El endpoint de draft valida token + permisos de gestion de tickets.

## Non-functional Requirements
- NFR-001: El preview se renderiza en <100ms para mensajes de hasta 4000 caracteres.
- NFR-002: El endpoint de draft responde en <15s (timeout del LLM).
- NFR-003: La sanitizacion HTML no permite tags `<script>`, `<style>`, `<iframe>`, `<html>`, `<head>`, `<body>`, ni atributos `on*`.
- NFR-004: La configuracion de IA para emails es independiente de las demas capabilities (chat, tutor, survey, seat-layout).
- NFR-005: El campo `format` es opcional en el payload; default `"text"` garantiza backward compat.

## Acceptance Criteria
### Scenario 1 — Enviar email en formato Markdown
- **Given** el organizador selecciona formato Markdown y escribe `**Hola** a todos`
- **When** hace click en Vista previa
- **Then** se muestra `<strong>Hola</strong> a todos` renderizado

### Scenario 2 — Enviar email en formato HTML
- **Given** el organizador selecciona HTML y escribe `<p>Hola</p><script>alert(1)</script>`
- **When** hace click en Vista previa
- **Then** se muestra `Hola` y el `<script>` es eliminado

### Scenario 3 — Asistente IA genera borrador
- **Given** el organizador hace click en "Asistente IA" y escribe "Recordar que el evento cambio de sala"
- **When** hace click en "Generar borrador"
- **Then** recibe un texto profesional que puede insertar en el editor

### Scenario 4 — Backward compatibilidad
- **Given** una llamada API existente sin campo `format`
- **When** el backend procesa el request
- **Then** usa `format="text"` por defecto y el comportamiento es identico al actual

### Scenario 5 — Sanitizacion de tags prohibidos
- **Given** el organizador escribe HTML con `<html><head><body><script><style>`
- **When** el backend procesa el mensaje
- **Then** solo se insertan tags de la whitelist en la plantilla de email

## Dependencies
- `marked` ^18.0.6 (ya instalado en frontend)
- `GroqLlmClient` / `LlmPort` (patron existente en backend)
- `AttendeeEmailTemplate` (plantilla existente a modificar)
- `PlatformSettings` + `AdminAiSettingsPage` (patron de capabilities IA existente)

## Risks
- Sanitizacion HTML insuficiente permite XSS en emails — mitigacion: whitelist estricta + testing con payloads maliciosos
- Timeout del LLM genera mala UX — mitigacion: timeout de 15s + feedback de error claro
- Markdown malformado genera HTML roto — mitigacion: `marked` maneja errores gracefully

## Execution Plan
→ `tasks/email-compose-editor/TASKS.md`

## Validation Plan
1. Tests unitarios: sanitizacion HTML, render por formato, use case de draft
2. Tests de integracion: endpoint de draft, envio con cada formato
3. Test manual: enviar email real con cada formato y verificar que llega correctamente
4. Verificar backward compat: llamada sin `format` funciona como texto plano
