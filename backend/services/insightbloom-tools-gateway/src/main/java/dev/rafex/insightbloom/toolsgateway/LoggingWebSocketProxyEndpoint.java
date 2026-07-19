package dev.rafex.insightbloom.toolsgateway;

import dev.rafex.ether.websocket.core.WebSocketCloseStatus;
import dev.rafex.ether.websocket.core.WebSocketEndpoint;
import dev.rafex.ether.websocket.core.WebSocketSession;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Copia local de {@code ether-websocket-proxy-jetty12}'s {@code WebSocketProxyEndpoint} (misma
 * logica de conexion al backend: {@link WebSocketClient} de Jetty), reimplementada aca porque la
 * version publicada en Maven Central traga en silencio cualquier excepcion de la conexion saliente
 * (catch generico sin logging).
 *
 * <p>Cada conexion se identifica en los logs con un {@code cid=N} corto (ver {@link #nextConnectionId})
 * para poder aislar el ciclo de vida completo de una sesion puntual con un solo grep, sin tener que
 * correlacionar por timestamp entre lineas de distintas conexiones concurrentes.
 */
final class LoggingWebSocketProxyEndpoint implements WebSocketEndpoint {
    private static final Logger LOGGER = Logger.getLogger(LoggingWebSocketProxyEndpoint.class.getName());
    private static final AtomicLong CONNECTION_SEQUENCE = new AtomicLong();

    private final URI backendUri;
    private final Duration connectTimeout;

    LoggingWebSocketProxyEndpoint(final URI backendUri, final Duration connectTimeout) {
        this.backendUri = backendUri;
        this.connectTimeout = connectTimeout;
    }

    private static long nextConnectionId() {
        return CONNECTION_SEQUENCE.incrementAndGet();
    }

    @Override
    public void onOpen(final WebSocketSession clientSession) throws Exception {
        final long cid = nextConnectionId();
        // La conexion saliente (crear HttpClient/WebSocketClient, handshake WS contra el backend)
        // tarda del orden de segundos en resolver -- mientras tanto Jetty ya puede despachar
        // mensajes del cliente en otro thread. PendingBridge encola cualquier mensaje que llegue
        // antes de que el backend este listo y los reenvia en orden apenas se resuelve (sin esto,
        // se pierden en silencio: para protocolos donde el PRIMER mensaje es obligatorio para que
        // el backend arranque -- ej. ttyd exige un JSON de init con columns/rows para spawnear el
        // pty -- la conexion queda "abierta" para siempre pero sin ningun dato, indistinguible de
        // un timeout de red. Ver DEC-0026 en spec-native/DECISIONS.md para el postmortem completo).
        final var pending = new PendingBridge(cid);
        clientSession.attribute("proxy-bridge", pending);

        final var httpClient = new HttpClient();
        final var wsClient = new WebSocketClient(httpClient);
        httpClient.setConnectTimeout(connectTimeout.toMillis());
        // HttpClient (dueño de la Connection/EndPoint subyacente antes/durante el upgrade a WS)
        // necesita su propio idle timeout -- el de la sesion WS (wsClient.setIdleTimeout) no cubre
        // esta capa.
        httpClient.setIdleTimeout(Duration.ofMinutes(10).toMillis());
        httpClient.start();

        try {
            // Mismo limite que el lado servidor (ver GatewayApplication.WS_MAX_MESSAGE_SIZE): el
            // default de Jetty (64KB) cierra la conexion con 1009 en cuanto code-server manda un
            // frame binario grande de su canal de management.
            wsClient.setMaxBinaryMessageSize(GatewayApplication.WS_MAX_MESSAGE_SIZE);
            wsClient.setIdleTimeout(Duration.ofMinutes(10));
            wsClient.start();
            final var backendListener = new BackendSessionListener(clientSession, cid);
            // El subprotocolo que el cliente pidio (ej. "tty" para ttyd) debe reenviarse a la
            // conexion saliente -- sin esto, backends que distinguen comportamiento por
            // subprotocolo negociado (ttyd solo interpreta el primer frame como su JSON de init
            // si "tty" fue negociado) aceptan el handshake pero nunca procesan nada, indistinguible
            // de una conexion sana. Ver DEC-0026.
            final var upgradeRequest = new ClientUpgradeRequest();
            final var requestedProtocol = clientSession.headerFirst("Sec-WebSocket-Protocol");
            if (requestedProtocol != null && !requestedProtocol.isBlank()) {
                upgradeRequest.setSubProtocols(requestedProtocol.split(",\\s*"));
            }
            LOGGER.info(() -> "websocket proxy cid=" + cid + ": conectando a " + backendUri
                + " subprotocolo-solicitado=" + requestedProtocol);
            final var backendFuture = wsClient.connect(backendListener, backendUri, upgradeRequest);
            backendFuture.get(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);

            final var backendSession = backendListener.backendSession;
            if (backendSession == null) {
                LOGGER.warning(() -> "websocket proxy cid=" + cid + ": conexion al backend " + backendUri
                    + " no establecio sesion (backendSession null)");
                clientSession.close(WebSocketCloseStatus.SERVER_ERROR);
                return;
            }

            LOGGER.info(() -> "websocket proxy cid=" + cid + ": conectado a " + backendUri
                + " subprotocolo-aceptado=" + backendSession.getUpgradeResponse().getAcceptedSubProtocol());
            // El atributo de sesion se queda con el PendingBridge para siempre (nunca se reemplaza
            // por el ProxyBridge): pending(session) siempre castea a PendingBridge, y este ya
            // delega directo al ProxyBridge real una vez adjuntado via attach().
            final var bridge = new ProxyBridge(backendSession, httpClient, cid);
            pending.attach(bridge);
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "websocket proxy cid=" + cid + ": fallo conectando al backend " + backendUri, e);
            pending.attachFailure();
            clientSession.close(WebSocketCloseStatus.SERVER_ERROR);
            httpClient.stop();
        }
    }

    @Override
    public void onText(final WebSocketSession session, final String message) throws Exception {
        pending(session).onText(message);
    }

    @Override
    public void onBinary(final WebSocketSession session, final ByteBuffer message) throws Exception {
        pending(session).onBinary(message);
    }

    @Override
    public void onClose(final WebSocketSession session, final WebSocketCloseStatus closeStatus) throws Exception {
        pending(session).onClose(closeStatus);
    }

    @Override
    public void onError(final WebSocketSession session, final Throwable error) {
        LOGGER.log(Level.WARNING, "websocket proxy: error en sesion cliente (backend " + backendUri + "): " + error, error);
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
     * conexion saliente al backend (ver comentario en {@link #onOpen}). Mientras el backend no
     * este listo, encola en orden; apenas {@link #attach} corre, reenvia todo lo encolado y de ahi
     * en mas pasa a modo directo.
     */
    private static final class PendingBridge {
        private final long cid;
        private final Object lock = new Object();
        private final Queue<Object> queued = new ArrayDeque<>();
        private ProxyBridge bridge;
        private boolean closed;
        private WebSocketCloseStatus closeStatus;

        PendingBridge(final long cid) {
            this.cid = cid;
        }

        void onText(final String message) {
            synchronized (lock) {
                if (bridge != null) {
                    bridge.backend.sendText(message, Callback.NOOP);
                } else if (!closed) {
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
                    if (!queued.isEmpty()) {
                        LOGGER.info(() -> "websocket proxy cid=" + cid + ": reenviando " + queued.size()
                            + " mensaje(s) que llegaron antes de que el backend estuviera listo");
                    }
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
        final long cid;

        ProxyBridge(final Session backend, final HttpClient httpClient, final long cid) {
            this.backend = backend;
            this.httpClient = httpClient;
            this.cid = cid;
        }

        void close(final WebSocketCloseStatus status) {
            // 1000 (normal) / 1001 (going away) son cierres esperados (el usuario cierra la
            // pestaña, navega afuera, etc) -- no ameritan mas que un rastro breve. Cualquier otro
            // codigo (1006, 1011, SERVER_ERROR...) es la señal que de verdad importa cuando algo
            // se corta de forma anormal, asi que se loguea mas fuerte.
            final boolean normal = status.code() == 1000 || status.code() == 1001;
            if (normal) {
                LOGGER.fine(() -> "websocket proxy cid=" + cid + ": cierre normal " + status);
            } else {
                LOGGER.warning(() -> "websocket proxy cid=" + cid + ": cierre anormal " + status);
            }
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
     * igual que el servidor con {@link JettyWebSocketEndpointBridge} (ver su javadoc) -- un lookup
     * publico sobre una clase no-publica no tiene acceso a sus miembros aunque los metodos
     * individuales sean {@code public}.
     */
    public static final class BackendSessionListener implements Session.Listener.AutoDemanding {
        final WebSocketSession clientSession;
        final long cid;
        volatile Session backendSession;

        BackendSessionListener(final WebSocketSession clientSession, final long cid) {
            this.clientSession = clientSession;
            this.cid = cid;
        }

        @Override
        public void onWebSocketOpen(final Session session) {
            this.backendSession = session;
        }

        // Jetty no responde PING con PONG por defecto en el WebSocketClient saliente -- hay que
        // hacerlo explicito. Nivel FINE: dispara cada --ping-interval (15s con ttyd) por cada
        // conexion activa, en INFO tapa cualquier otra cosa relevante en los logs.
        @Override
        public void onWebSocketPing(final ByteBuffer payload) {
            LOGGER.fine(() -> "websocket proxy cid=" + cid + ": PING del backend");
            if (backendSession != null) {
                backendSession.sendPong(payload, new Callback() {
                    @Override
                    public void succeed() {
                        LOGGER.fine(() -> "websocket proxy cid=" + cid + ": PONG enviado ok");
                    }

                    @Override
                    public void fail(final Throwable x) {
                        LOGGER.log(Level.WARNING, "websocket proxy cid=" + cid + ": fallo enviando PONG al backend", x);
                    }
                });
            }
        }

        @Override
        public void onWebSocketPong(final ByteBuffer payload) {
            LOGGER.fine(() -> "websocket proxy cid=" + cid + ": PONG del backend (inesperado)");
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
            LOGGER.info(() -> "websocket proxy cid=" + cid + ": backend cerro la conexion statusCode="
                + statusCode + " reason=" + reason);
            if (clientSession.isOpen()) {
                clientSession.close(WebSocketCloseStatus.of(statusCode, reason));
            }
            callback.succeed();
        }

        @Override
        public void onWebSocketError(final Throwable cause) {
            LOGGER.log(Level.WARNING, "websocket proxy cid=" + cid + ": error en sesion backend", cause);
            if (clientSession.isOpen()) {
                clientSession.close(WebSocketCloseStatus.SERVER_ERROR);
            }
        }
    }
}
