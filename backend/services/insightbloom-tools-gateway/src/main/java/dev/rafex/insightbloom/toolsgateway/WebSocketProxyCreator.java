package dev.rafex.insightbloom.toolsgateway;

import dev.rafex.ether.websocket.proxy.jetty12.BackendResolver;
import dev.rafex.ether.websocket.proxy.jetty12.WebSocketProxyEndpoint;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.server.ServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.ServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.WebSocketCreator;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Crea el bridge de proxy de WebSocket hacia el pod real de la herramienta (drawio, Etherpad),
 * exigiendo la misma sesion de InsightBloom que {@link AuthGateHandler} exige para HTTP normal
 * (reutiliza {@link AuthGateHandler#checkAuth}, {@code SESSION_COOKIE} y {@code SESSION_TTL}
 * para no duplicar la logica de autenticacion). Montado sobre el mismo {@code Server} crudo via
 * {@code WebSocketUpgradeHandler.from(server, container -> container.addMapping("/*", this))}
 * en {@link GatewayApplication}.
 */
final class WebSocketProxyCreator implements WebSocketCreator {

    private static final java.util.logging.Logger LOGGER =
        java.util.logging.Logger.getLogger(WebSocketProxyCreator.class.getName());

    private final Map<String, String> routesByHost;
    private final AuthGateHandler authGate;

    WebSocketProxyCreator(final Map<String, String> routesByHost, final AuthGateHandler authGate) {
        this.routesByHost = routesByHost;
        this.authGate = authGate;
    }

    @Override
    public Object createWebSocket(final ServerUpgradeRequest request, final ServerUpgradeResponse response,
                                   final Callback callback) throws Exception {
        final String host = AuthGateHandler.hostOf(request);
        final boolean isIdeHost = authGate.isIdeHost(host);
        final String staticTarget = routesByHost.get(host);
        if (!isIdeHost && staticTarget == null) {
            LOGGER.warning(() -> "websocket rechazado: host no reconocido host=" + host + " path=" + request.getHttpURI().getPath());
            response.setStatus(502);
            callback.succeeded();
            return null;
        }

        // checkAuth ya loguea el motivo puntual del rechazo (ver AuthGateHandler.logAuthRejected).
        final AuthGateHandler.AuthResult auth = authGate.checkAuth(request, host, isIdeHost);
        if (!auth.authenticated()) {
            response.setStatus(401);
            callback.succeeded();
            return null;
        }
        if (auth.newSessionId() != null) {
            Response.addCookie(response, HttpCookie.build(AuthGateHandler.SESSION_COOKIE, auth.newSessionId())
                    .path("/")
                    .httpOnly(true)
                    .secure(true)
                    .sameSite(HttpCookie.SameSite.LAX)
                    .maxAge(AuthGateHandler.SESSION_TTL.toSeconds())
                    .build());
        }

        final String target = isIdeHost ? auth.dynamicTarget() : staticTarget;
        if (target == null) {
            LOGGER.warning(() -> "websocket rechazado: autenticado pero sin target (sandbox no disponible) host="
                + host + " path=" + request.getHttpURI().getPath());
            response.setStatus(502);
            callback.succeeded();
            return null;
        }

        final String wsTarget = target.replaceFirst("^http", "ws");
        final String rawQuery = request.getHttpURI().getQuery();
        final String path = request.getHttpURI().getPath();
        final URI backendUri = URI.create(wsTarget + path + (rawQuery == null ? "" : "?" + rawQuery));

        final WebSocketProxyEndpoint endpoint = new WebSocketProxyEndpoint(BackendResolver.fixed(backendUri));
        return new JettyWebSocketEndpointBridge(endpoint, path, queryParamsOf(rawQuery), headersOf(request));
    }

    private static Map<String, List<String>> queryParamsOf(final String rawQuery) {
        final Map<String, List<String>> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return params;
        for (final String pair : rawQuery.split("&")) {
            final int eq = pair.indexOf('=');
            final String key = eq < 0 ? pair : pair.substring(0, eq);
            final String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return params;
    }

    private static Map<String, List<String>> headersOf(final ServerUpgradeRequest request) {
        final Map<String, List<String>> headers = new LinkedHashMap<>();
        for (final HttpField field : request.getHeaders()) {
            headers.computeIfAbsent(field.getName(), k -> new ArrayList<>()).add(field.getValue());
        }
        return headers;
    }
}
