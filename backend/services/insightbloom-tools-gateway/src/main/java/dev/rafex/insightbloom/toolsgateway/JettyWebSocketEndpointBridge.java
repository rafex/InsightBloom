package dev.rafex.insightbloom.toolsgateway;

import dev.rafex.ether.websocket.core.WebSocketCloseStatus;
import dev.rafex.ether.websocket.core.WebSocketEndpoint;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Traduce los callbacks nativos de Jetty ({@link Session.Listener}) a la interfaz portable
 * {@link WebSocketEndpoint} de ether-websocket-core, para que el proxy de
 * ether-websocket-proxy-jetty12 pueda montarse directamente sobre el {@code Server} crudo
 * de este gateway sin depender de las factories de alto nivel de ether (que construyen su propio
 * Server). Reimplementa localmente el rol de {@code JettyWebSocketEndpointAdapter} de la libreria
 * (package-private, no reutilizable desde aqui) usando solo API publica.
 */
final class JettyWebSocketEndpointBridge implements Session.Listener.AutoDemanding {
    private static final Logger LOGGER = Logger.getLogger(JettyWebSocketEndpointBridge.class.getName());

    private final WebSocketEndpoint endpoint;
    private final String path;
    private final Map<String, List<String>> queryParams;
    private final Map<String, List<String>> headers;
    private volatile Session nativeSession;
    private volatile EtherWebSocketSessionAdapter session;

    JettyWebSocketEndpointBridge(final WebSocketEndpoint endpoint, final String path,
                                  final Map<String, List<String>> queryParams,
                                  final Map<String, List<String>> headers) {
        this.endpoint = endpoint;
        this.path = path;
        this.queryParams = queryParams;
        this.headers = headers;
    }

    @Override
    public void onWebSocketOpen(final Session nativeSession) {
        this.nativeSession = nativeSession;
        this.session = new EtherWebSocketSessionAdapter(nativeSession, path, queryParams, headers);
        try {
            endpoint.onOpen(session);
        } catch (final Exception e) {
            handleFailure(e);
        }
    }

    @Override
    public void onWebSocketText(final String message) {
        try {
            endpoint.onText(session, message);
        } catch (final Exception e) {
            handleFailure(e);
        }
    }

    @Override
    public void onWebSocketBinary(final ByteBuffer payload, final Callback callback) {
        try {
            endpoint.onBinary(session, payload);
            callback.succeed();
        } catch (final Exception e) {
            callback.fail(e);
            LOGGER.log(Level.WARNING, "websocket proxy binary frame failed", e);
        }
    }

    @Override
    public void onWebSocketClose(final int statusCode, final String reason, final Callback callback) {
        try {
            if (session != null) {
                endpoint.onClose(session, WebSocketCloseStatus.of(statusCode, reason));
            }
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "websocket proxy onClose failed", e);
        } finally {
            callback.succeed();
        }
    }

    @Override
    public void onWebSocketError(final Throwable cause) {
        endpoint.onError(session, cause);
    }

    private void handleFailure(final Exception e) {
        LOGGER.log(Level.WARNING, "websocket proxy bridge error", e);
        if (nativeSession != null) {
            nativeSession.close(1011, "proxy error", Callback.NOOP);
        }
    }
}
