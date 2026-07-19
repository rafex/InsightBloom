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
        final var httpClient = new HttpClient();
        final var wsClient = new WebSocketClient(httpClient);
        httpClient.setConnectTimeout(connectTimeout.toMillis());
        httpClient.start();

        try {
            // Mismo limite que el lado servidor (ver GatewayApplication.WS_MAX_MESSAGE_SIZE):
            // sin esto el default de Jetty (64KB) cierra la conexion al backend con 1009 en
            // cuanto code-server manda un frame binario grande de su canal de management.
            wsClient.setMaxBinaryMessageSize(GatewayApplication.WS_MAX_MESSAGE_SIZE);
            wsClient.start();
            final var backendListener = new BackendSessionListener(clientSession);
            final var backendFuture = wsClient.connect(backendListener, backendUri);
            backendFuture.get(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);

            final var backendSession = backendListener.backendSession;
            if (backendSession == null) {
                LOGGER.warning(() -> "websocket proxy: conexion al backend " + backendUri
                    + " no establecio sesion (backendSession null)");
                clientSession.close(WebSocketCloseStatus.SERVER_ERROR);
                return;
            }

            LOGGER.info(() -> "websocket proxy: conexion al backend " + backendUri + " establecida ok");
            final var bridge = new ProxyBridge(backendSession, httpClient);
            clientSession.attribute("proxy-bridge", bridge);
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "websocket proxy: fallo conectando al backend " + backendUri, e);
            clientSession.close(WebSocketCloseStatus.SERVER_ERROR);
            httpClient.stop();
        }
    }

    @Override
    public void onText(final WebSocketSession session, final String message) throws Exception {
        final var bridge = bridge(session);
        if (bridge != null) {
            bridge.backend.sendText(message, Callback.NOOP);
        }
    }

    @Override
    public void onBinary(final WebSocketSession session, final ByteBuffer message) throws Exception {
        final var bridge = bridge(session);
        if (bridge != null) {
            bridge.backend.sendBinary(message, Callback.NOOP);
        }
    }

    @Override
    public void onClose(final WebSocketSession session, final WebSocketCloseStatus closeStatus) throws Exception {
        final var bridge = bridge(session);
        if (bridge != null) {
            bridge.close(closeStatus);
        }
    }

    @Override
    public void onError(final WebSocketSession session, final Throwable error) {
        LOGGER.log(Level.WARNING, "websocket proxy: error en sesion cliente (backend " + backendUri + ")", error);
        final var bridge = bridge(session);
        if (bridge != null) {
            bridge.close(WebSocketCloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public Set<String> subprotocols() {
        return Set.of();
    }

    private static ProxyBridge bridge(final WebSocketSession session) {
        return (ProxyBridge) session.attribute("proxy-bridge");
    }

    private static final class ProxyBridge {
        final Session backend;
        final HttpClient httpClient;

        ProxyBridge(final Session backend, final HttpClient httpClient) {
            this.backend = backend;
            this.httpClient = httpClient;
        }

        void close(final WebSocketCloseStatus status) {
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

        // Jetty NO responde PING con PONG automaticamente (Session.Listener.onWebSocketPing es
        // no-op por defecto, ver javadoc de la interfaz) -- sin este override, los PING que ttyd
        // manda cada 15s (--ping-interval, ver sandbox-agent.py) nunca recibian PONG, y ttyd
        // cerraba la conexion (1011 server_error) tras ~3 intentos sin respuesta (~45-50s).
        // Confirmado en vivo (2026-07-19): la terminal del IDE CLI se quedaba en negro cada ~50s
        // en loop de reconexion.
        @Override
        public void onWebSocketPing(final ByteBuffer payload) {
            if (backendSession != null) {
                backendSession.sendPong(payload, Callback.NOOP);
            }
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
