package dev.rafex.insightbloom.toolsgateway;

import dev.rafex.ether.websocket.core.WebSocketCloseStatus;
import dev.rafex.ether.websocket.core.WebSocketSession;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapta una {@link org.eclipse.jetty.websocket.api.Session} nativa de Jetty a la interfaz
 * {@link WebSocketSession} de ether-websocket-core, para poder reusar {@link WebSocketProxyCreator}
 * (el proxy de ether-websocket-proxy-jetty12) sin ceder la construccion del {@code Server} completo
 * a las factories de alto nivel de ether (que crean su propio Server, incompatibles con el
 * Server crudo + AuthGateHandler ya existente de este gateway).
 */
final class EtherWebSocketSessionAdapter implements WebSocketSession {
    private final org.eclipse.jetty.websocket.api.Session nativeSession;
    private final String id = UUID.randomUUID().toString();
    private final String path;
    private final Map<String, List<String>> queryParams;
    private final Map<String, List<String>> headers;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    EtherWebSocketSessionAdapter(final org.eclipse.jetty.websocket.api.Session nativeSession,
                                  final String path,
                                  final Map<String, List<String>> queryParams,
                                  final Map<String, List<String>> headers) {
        this.nativeSession = nativeSession;
        this.path = path;
        this.queryParams = queryParams;
        this.headers = headers;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String subprotocol() {
        return nativeSession.getUpgradeResponse().getAcceptedSubProtocol();
    }

    @Override
    public boolean isOpen() {
        return nativeSession.isOpen();
    }

    @Override
    public String pathParam(final String name) {
        return null;
    }

    @Override
    public String queryFirst(final String name) {
        final List<String> values = queryParams.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    @Override
    public List<String> queryAll(final String name) {
        return queryParams.getOrDefault(name, List.of());
    }

    @Override
    public String headerFirst(final String name) {
        for (final var entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                final List<String> values = entry.getValue();
                return values.isEmpty() ? null : values.get(0);
            }
        }
        return null;
    }

    @Override
    public Object attribute(final String name) {
        return attributes.get(name);
    }

    @Override
    public void attribute(final String name, final Object value) {
        attributes.put(name, value);
    }

    @Override
    public Map<String, String> pathParams() {
        return Map.of();
    }

    @Override
    public Map<String, List<String>> queryParams() {
        return queryParams;
    }

    @Override
    public Map<String, List<String>> headers() {
        return headers;
    }

    @Override
    public CompletionStage<Void> sendText(final String text) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        nativeSession.sendText(text, callbackOf(future));
        return future;
    }

    @Override
    public CompletionStage<Void> sendBinary(final ByteBuffer payload) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        nativeSession.sendBinary(payload, callbackOf(future));
        return future;
    }

    @Override
    public CompletionStage<Void> close(final WebSocketCloseStatus status) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        nativeSession.close(status.code(), status.reason(), callbackOf(future));
        return future;
    }

    private static org.eclipse.jetty.websocket.api.Callback callbackOf(final CompletableFuture<Void> future) {
        return org.eclipse.jetty.websocket.api.Callback.from(
                () -> future.complete(null),
                future::completeExceptionally);
    }
}
