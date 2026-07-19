# Changelog

## [v3.20260719] — 2026-07-19

### Features

- **ide,dashboard**: IDE dual Web/CLI con editor Monaco, y rediseño de dashboard de eventos/usuarios
- **ide**: panel de ayuda de Neovim flotante sobre el IDE (Web y CLI)
- **users**: aforo obligatorio en todo evento, con alertas graduadas según capacidad
- **users**: unificar la inscripción a un evento en el boleto (Reservation)
- **dashboard**: Fase 5 — tabla de Usuarios con filtros server-side y vista de detalle
- **dashboard**: separar "Editor" de "Configuración" en las acciones de eventos

### Fixes

- **gateway**: responder PING con PONG en el proxy WebSocket (ttyd se caía cada ~50s)
- **gateway**: idle timeout explícito en el ServerConnector (causa raíz real)
- **gateway**: idle timeout explícito en el HttpClient del proxy websocket
- **gateway**: idle timeout explícito en el WebSocketClient del proxy + diagnóstico de PING/PONG
- **gateway**: subir idle timeout del websocket proxy de 30s a 10min
- **gateway**: encolar mensajes del cliente hasta que el bridge al backend esté listo
- **gateway**: no pisar el atributo de sesión con ProxyBridge (ClassCastException)
- **gateway**: reenviar el subprotocolo de WebSocket del cliente al backend
- **gateway**: re-resolver el target del IDE en vez de confiar en la cookie vieja
- **gateway**: kill switch para el Service Worker huérfano de code-server
- **sandbox**: auto-sanar aprovisionamiento de asientos + animación de carga
- **sandbox**: escribir archivos del workspace como el usuario del asiento
- **sandbox**: precrear sesión tmux fuera del pty de ttyd (persistencia real)
- **sandbox**: persistir sesión de nvim en tmux entre reconexiones de ttyd
- **sandbox**: refrescar el vencimiento al reconectarse a un sandbox activo
- **sandbox**: reconectar al propio sandbox aunque el pool esté lleno
- **sandbox**: exponer el puerto de control del seat-agent en el Service del Pod CLI
- **sandbox**: el puerto de control también falta en el Service de pods Web
- **sandbox**: loguear excepciones en SandboxHandler antes de responder 500
- **dashboard**: unificar nav de herramientas con el esquema Modos/Acciones
- **dashboard**: unificar breadcrumb con la nueva estructura de UI/UX
- **dashboard**: sidebar "Inicio" pegado + descarga de workspace 404
- **dashboard**: Editor Monaco lleva a una página dedicada de moderación, no a Editar evento
- **ide**: sacar la página del IDE de ConferencePage.vue (header tapaba el panel)

### Refactors

- **gateway**: logging permanente por-conexión (cid) + postmortem DEC-0026

### Chores

- **gateway**: quitar logging de diagnóstico por-mensaje (ya cumplió su propósito)
