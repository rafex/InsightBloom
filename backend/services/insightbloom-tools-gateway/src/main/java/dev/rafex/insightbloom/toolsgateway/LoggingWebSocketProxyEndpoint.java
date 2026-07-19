package dev.rafex.insightbloom.toolsgateway;

import dev.rafex.ether.websocket.core.WebSocketCloseStatus;
import dev.rafex.ether.websocket.core.WebSocketEndpoint;
import dev.rafex.ether.websocket.core.WebSocketSession;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Copia local de {@code ether-websocket-proxy-jetty12}'s {@code WebSocketProxyEndpoint} (misma
 * logica de conexion al backend: {@link WebSocketClient} de Jetty), reimplementada aca porque la
 * version publicada en Maven Central traga en silencio cualquier excepcion de la conexion saliente
 * (catch generico sin logging) -- eso dejo indiagnosticable por dias el 502/"WebSocket close 1006"
 * del IDE (2026-07-16): cero rastro en logs pese a que el fallo era real y reproducible. Publicar
 * un fix en la libreria implica un release nuevo a Maven Central (credenciales/proceso aparte);
 * mientras tanto se reimplementa localmente, mismo patron ya usado por
 * {@link JettyWebSocketEndpointBridge} para no depender de internals no reutilizables de ether.
 */
final class LoggingWebSocketProxyEndpoint implements WebSocketEndpoint {
    private static final Logger LOGGER = Logger.getLogger(LoggingWebSocketProxyEndpoint.class.getName());

    private final URI backendUri;
    private final Duration connectTimeout;

    LoggingWebSocketProxyEndpoint(final URI backendUri, final Duration connectTimeout) {
        this.backendUri = backendUri;
        this.connectTimeout = connectTimeout;
    }

    @Override
    public void onOpen(final WebSocketSession clientSession) throws Exception {
        // Diagnostico 2026-07-19: la conexion al backend de mas abajo (wsClient.connect +
        // backendFuture.get) tarda del orden de 1s en resolver (crear HttpClient/WebSocketClient,
        // handshake WS saliente) -- mientras tanto, Jetty (listener AutoDemanding, ver
        // JettyWebSocketEndpointBridge) ya puede estar despachando el PRIMER mensaje que mando el
        // cliente (para ttyd, el JSON de init con columns/rows que ttyd EXIGE para recien ahi
        // spawnear el pty) en un thread distinto, antes de que este metodo termine. Como el bridge
        // recien se guardaba en el atributo de sesion AL FINAL, ese primer mensaje llegaba a
        // onText/onBinary con bridge()==null y se descartaba en silencio -- confirmado en vivo:
        // el WebSocket quedaba abierto y con PING/PONG sano indefinidamente, pero ttyd nunca
        // recibia el init y jamas arrancaba el pty (cero proceso hijo, terminal en blanco para
        // siempre). Fix: publicar un PendingBridge desde el arranque de este metodo que ENCOLA
        // cualquier mensaje que llegue antes de que el backend este listo, y los reenvia en orden
        // apenas se resuelve.
        final var pending = new PendingBridge();
        clientSession.attribute("proxy-bridge", pending);

        final var httpClient = new HttpClient();
        final var wsClient = new WebSocketClient(httpClient);
        httpClient.setConnectTimeout(connectTimeout.toMillis());
        // Diagnostico 2026-07-19: confirmado con logging que PING/PONG funciona (backend recibe
        // pong ok) y que el idle timeout de la SESION WebSocket ya esta en 10min (ver mas abajo,
        // wsClient.setIdleTimeout) -- pese a eso la conexion seguia muriendo cada ~45s con
        // java.nio.channels.ClosedChannelException a nivel de canal TCP crudo. HttpClient (quien
        // realmente posee la Connection/EndPoint subyacente antes/durante el upgrade a WS) nunca
        // tuvo su propio idle timeout configurado -- puede estar reclamando la conexion fisica
        // por su cuenta, independiente del idle timeout a nivel de sesion WS.
        httpClient.setIdleTimeout(java.time.Duration.ofMinutes(10).toMillis());
        httpClient.start();

        try {
            // Mismo limite que el lado servidor (ver GatewayApplication.WS_MAX_MESSAGE_SIZE):
            // sin esto el default de Jetty (64KB) cierra la conexion al backend con 1009 en
            // cuanto code-server manda un frame binario grande de su canal de management.
            wsClient.setMaxBinaryMessageSize(GatewayApplication.WS_MAX_MESSAGE_SIZE);
            // Diagnostico 2026-07-19: a diferencia del lado servidor (ver GatewayApplication,
            // container.setIdleTimeout), este WebSocketClient (la mitad gateway->pod del proxy)
            // nunca tuvo su idle timeout configurado explicitamente -- corre con el default de
            // Jetty, desconocido/no verificado. Se iguala al mismo valor generoso del lado
            // servidor mientras se investiga si el cierre cada ~50s de la sesion con ttyd viene
            // de aca en vez de (o ademas de) PING/PONG.
            wsClient.setIdleTimeout(java.time.Duration.ofMinutes(10));
            wsClient.start();
            final var backendListener = new BackendSessionListener(clientSession);
            // Diagnostico 2026-07-19: el mensaje de init de ttyd (JSON con columns/rows, exigido
            // por su protocolo antes de spawnear el pty) SI llegaba al backend via este proxy
            // (confirmado con logging: onText lo recibe y PendingBridge lo reenvia), pero ttyd
            // jamas respondia ni arrancaba el proceso -- a diferencia de conectarse DIRECTO a
            // ttyd (bypaseando este gateway) con el mismo mensaje, que funcionaba siempre. La
            // diferencia real: la conexion directa negociaba el subprotocolo "Sec-WebSocket-Protocol:
            // tty" (ttyd lo exige para tratar el primer frame como su JSON de init en vez de
            // ignorarlo); esta conexion saliente gateway->backend nunca reenviaba el subprotocolo
            // que el NAVEGADOR le pidio a este gateway, asi que ttyd la trataba como conexion sin
            // protocolo reconocido y nunca arrancaba el pty pese a "aceptar" el handshake.
            final var upgradeRequest = new org.eclipse.jetty.websocket.client.ClientUpgradeRequest();
            final var requestedProtocol = clientSession.headerFirst("Sec-WebSocket-Protocol");
            if (requestedProtocol != null && !requestedProtocol.isBlank()) {
                upgradeRequest.setSubProtocols(requestedProtocol.split(",\\s*"));
            }
            final var backendFuture = wsClient.connect(backendListener, backendUri, upgradeRequest);
            backendFuture.get(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);

            final var backendSession = backendListener.backendSession;
            if (backendSession == null) {
                LOGGER.warning(() -> "websocket proxy: conexion al backend " + backendUri
                    + " no establecio sesion (backendSession null)");
                clientSession.close(WebSocketCloseStatus.SERVER_ERROR);
                return;
            }

            LOGGER.info(() -> "websocket proxy: conexion al backend " + backendUri + " establecida ok");
            // OJO: el atributo de sesion se queda con el PendingBridge para siempre (nunca se
            // reemplaza por el ProxyBridge) -- pending(session) siempre castea a PendingBridge, y
            // este ya delega directo al ProxyBridge real una vez adjuntado via attach().
            final var bridge = new ProxyBridge(backendSession, httpClient);
            pending.attach(bridge);
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "websocket proxy: fallo conectando al backend " + backendUri, e);
            pending.attachFailure();
            clientSession.close(WebSocketCloseStatus.SERVER_ERROR);
            httpClient.stop();
        }
    }

    @Override
    public void onText(final WebSocketSession session, final String message) throws Exception {
        LOGGER.info(() -> "websocket proxy: onText recibido, len=" + message.length());
        pending(session).onText(message);
    }

    @Override
    public void onBinary(final WebSocketSession session, final ByteBuffer message) throws Exception {
        LOGGER.info(() -> "websocket proxy: onBinary recibido, len=" + message.remaining());
        pending(session).onBinary(message);
    }

    @Override
    public void onClose(final WebSocketSession session, final WebSocketCloseStatus closeStatus) throws Exception {
        pending(session).onClose(closeStatus);
    }

    @Override
    public void onError(final WebSocketSession session, final Throwable error) {
        LOGGER.log(Level.WARNING, "websocket proxy: error en sesion cliente (backend " + backendUri + ")", error);
        pending(session).onClose(WebSocketCloseStatus.SERVER_ERROR);
    }

    @Override
    public Set<String> subprotocols() {
        return Set.of();
    }

    private static PendingBridge pending(final WebSocketSession session) {
        return (PendingBridge) session.attribute("proxy-bridge");
    }

    /**
     * Coordina la carrera entre los mensajes que el cliente manda apenas abre su WebSocket y la
     * conexion saliente al backend (que tarda ~1s en resolver, ver comentario en {@link #onOpen}).
     * Mientras el backend no este listo, encola en orden; apenas {@link #attach} corre, reenvia
     * todo lo encolado y de ahi en mas pasa a modo directo (sin overhead de sincronizar cada frame
     * despues del arranque).
     */
    private static final class PendingBridge {
        private final Object lock = new Object();
        private final Queue<Object> queued = new ArrayDeque<>();
        private ProxyBridge bridge;
        private boolean closed;
        private WebSocketCloseStatus closeStatus;

        void onText(final String message) {
            synchronized (lock) {
                if (bridge != null) {
                    LOGGER.info(() -> "websocket proxy: PendingBridge.onText envio directo (bridge ya listo)");
                    bridge.backend.sendText(message, Callback.NOOP);
                } else if (!closed) {
                    LOGGER.info(() -> "websocket proxy: PendingBridge.onText encolado (bridge aun no listo)");
                    queued.add(message);
                }
            }
        }

        void onBinary(final ByteBuffer message) {
            synchronized (lock) {
                if (bridge != null) {
                    bridge.backend.sendBinary(message, Callback.NOOP);
                } else if (!closed) {
                    queued.add(message);
                }
            }
        }

        void onClose(final WebSocketCloseStatus status) {
            synchronized (lock) {
                if (bridge != null) {
                    bridge.close(status);
                } else {
                    // El backend todavia no conecto -- se marca el cierre para que attach() lo
                    // cierre apenas resuelva, en vez de dejar una conexion saliente huerfana.
                    closed = true;
                    closeStatus = status;
                    queued.clear();
                }
            }
        }

        void attach(final ProxyBridge readyBridge) {
            final boolean shouldCloseImmediately;
            synchronized (lock) {
                if (closed) {
                    shouldCloseImmediately = true;
                } else {
                    shouldCloseImmediately = false;
                    LOGGER.info(() -> "websocket proxy: PendingBridge.attach drenando " + queued.size() + " mensaje(s) encolado(s)");
                    for (final Object item : queued) {
                        if (item instanceof String text) {
                            readyBridge.backend.sendText(text, Callback.NOOP);
                        } else {
                            readyBridge.backend.sendBinary((ByteBuffer) item, Callback.NOOP);
                        }
                    }
                    queued.clear();
                    bridge = readyBridge;
                }
            }
            if (shouldCloseImmediately) {
                readyBridge.close(closeStatus);
            }
        }

        void attachFailure() {
            synchronized (lock) {
                queued.clear();
                closed = true;
            }
        }
    }

    private static final class ProxyBridge {
        final Session backend;
        final HttpClient httpClient;

        ProxyBridge(final Session backend, final HttpClient httpClient) {
            this.backend = backend;
            this.httpClient = httpClient;
        }

        void close(final WebSocketCloseStatus status) {
            LOGGER.log(Level.WARNING, "websocket proxy: ProxyBridge.close() invocado, status=" + status
                + " thread=" + Thread.currentThread().getName(), new Exception("stack de diagnostico, no es un error real"));
            try {
                if (backend.isOpen()) {
                    backend.close(status.code(), status.reason(), Callback.NOOP);
                }
            } catch (final Exception ignored) {
            }
            try {
                httpClient.stop();
            } catch (final Exception ignored) {
            }
        }
    }

    /**
     * Debe ser {@code public} (no {@code private}): el {@code WebSocketClient} de Jetty conecta
     * reflexivamente los callbacks de este listener via {@code MethodHandles.publicLookup()},
     * igual que el servidor con {@link JettyWebSocketEndpointBridge} (ver su javadoc) -- mismo
     * bug, confirmado en logs de produccion: {@code IllegalAccessException: class is not public}
     * al intentar invocar {@code onWebSocketOpen} de esta clase cuando era {@code private}.
     */
    public static final class BackendSessionListener implements Session.Listener.AutoDemanding {
        final WebSocketSession clientSession;
        volatile Session backendSession;

        BackendSessionListener(final WebSocketSession clientSession) {
            this.clientSession = clientSession;
        }

        @Override
        public void onWebSocketOpen(final Session session) {
            this.backendSession = session;
        }

        // Diagnostico 2026-07-19: el cierre cada ~50s de la conexion con ttyd (1011 server_error)
        // persiste incluso con este PONG explicito -- logging temporal para confirmar si Jetty
        // esta invocando este metodo (JettyWebSocketFrameHandlerFactory.isOverridden decide si
        // auto-responde o delega aca) y si sendPong se completa sin error.
        @Override
        public void onWebSocketPing(final ByteBuffer payload) {
            LOGGER.info(() -> "websocket proxy: PING recibido del backend, backendSession=" + backendSession);
            if (backendSession != null) {
                backendSession.sendPong(payload, new Callback() {
                    @Override
                    public void succeed() {
                        LOGGER.info(() -> "websocket proxy: PONG enviado al backend ok");
                    }

                    @Override
                    public void fail(final Throwable x) {
                        LOGGER.log(Level.WARNING, "websocket proxy: fallo enviando PONG al backend", x);
                    }
                });
            }
        }

        @Override
        public void onWebSocketPong(final ByteBuffer payload) {
            LOGGER.info(() -> "websocket proxy: PONG recibido del backend (inesperado, ttyd no deberia mandar pong)");
        }

        @Override
        public void onWebSocketText(final String message) {
            if (clientSession.isOpen()) {
                clientSession.sendText(message);
            }
        }

        @Override
        public void onWebSocketBinary(final ByteBuffer payload, final Callback callback) {
            if (clientSession.isOpen()) {
                clientSession.sendBinary(payload);
            }
            callback.succeed();
        }

        @Override
        public void onWebSocketClose(final int statusCode, final String reason, final Callback callback) {
            LOGGER.info(() -> "websocket proxy: backend cerro la conexion statusCode=" + statusCode + " reason=" + reason);
            if (clientSession.isOpen()) {
                clientSession.close(WebSocketCloseStatus.of(statusCode, reason));
            }
            callback.succeed();
        }

        @Override
        public void onWebSocketError(final Throwable cause) {
            LOGGER.log(Level.WARNING, "websocket proxy: error en sesion backend", cause);
            if (clientSession.isOpen()) {
                clientSession.close(WebSocketCloseStatus.SERVER_ERROR);
            }
        }
    }
}
