package dev.rafex.insightbloom.toolsgateway;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;


import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gate de sesion + proxy inverso hacia las herramientas self-hosted (drawio, Excalidraw,
 * Etherpad, code-server). Rutea por el header Host (mismo host publico de siempre, ej.
 * drawio-insightbloom.v1.rafex.cloud) hacia el Service interno correspondiente, pero antes
 * exige una sesion valida de InsightBloom: sin ella, ni siquiera se reenvia el request al pod
 * real — asi pegar la URL publica directamente en el navegador (bypaseando el frontend de
 * InsightBloom) ya no da acceso a nadie sin sesion, cierre que un chequeo solo en el
 * frontend/backend de la app no puede dar por si mismo (ver DEC pendiente de documentar).
 *
 * El reenvio HTTP usa {@link HttpClient} de request/response estándar, que no soporta upgrade
 * a WebSocket (Etherpad socket.io, code-server) — eso lo maneja {@link WebSocketProxyCreator}
 * en paralelo, montado sobre el mismo {@code Server} vía
 * {@code WebSocketUpgradeHandler.from(server, ...)} (ver {@link GatewayApplication}),
 * reutilizando {@link #checkAuth(Request)} para exigir la misma sesion de InsightBloom antes
 * del upgrade (ver postmortem TASK-0020, resuelto con ether-websocket-proxy-jetty12 9.5.5).
 */
final class AuthGateHandler extends Handler.Abstract {

    private static final Logger LOGGER = Logger.getLogger(AuthGateHandler.class.getName());
    static final String SESSION_COOKIE = "ib_gw";
    static final Duration SESSION_TTL = Duration.ofHours(4);
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "content-length", "host");

    private final Map<String, String> routesByHost;
    private final String authValidateUrl;
    private final String loginUrl;
    private final SessionCache sessionCache = new SessionCache();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    AuthGateHandler(final Map<String, String> routesByHost, final String authValidateUrl, final String loginUrl) {
        this.routesByHost = routesByHost;
        this.authValidateUrl = authValidateUrl;
        this.loginUrl = loginUrl;
    }

    @Override
    public boolean handle(final Request request, final Response response, final Callback callback) {
        if ("/health".equals(request.getHttpURI().getPath())) {
            writeSimpleResponse(request, response, callback, 200, "OK");
            return true;
        }
        final String host = hostOf(request);
        final String target = routesByHost.get(host);
        if (target == null) {
            writeSimpleResponse(request, response, callback, 502, "Herramienta no reconocida.");
            return true;
        }

        final AuthResult auth = checkAuth(request);
        if (!auth.authenticated()) {
            writeLoginRequired(request, response, callback);
            return true;
        }
        if (auth.newSessionId() != null) {
            Response.addCookie(response, HttpCookie.build(SESSION_COOKIE, auth.newSessionId())
                    .path("/")
                    .httpOnly(true)
                    .secure(true)
                    .sameSite(HttpCookie.SameSite.LAX)
                    .maxAge(SESSION_TTL.toSeconds())
                    .build());
        }

        try {
            proxy(request, response, callback, target);
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "proxy failed for host=" + host, e);
            writeSimpleResponse(request, response, callback, 502, "No se pudo contactar la herramienta.");
        }
        return true;
    }

    record AuthResult(boolean authenticated, String newSessionId) {}

    AuthResult checkAuth(final Request request) {
        for (final HttpCookie cookie : Request.getCookies(request)) {
            if (SESSION_COOKIE.equals(cookie.getName()) && sessionCache.isValid(cookie.getValue())) {
                return new AuthResult(true, null);
            }
        }
        final String token = queryParam(request, "ib_token");
        if (token == null || token.isBlank()) {
            return new AuthResult(false, null);
        }
        if (isTokenValid(token)) {
            return new AuthResult(true, sessionCache.mint(SESSION_TTL));
        }
        return new AuthResult(false, null);
    }

    private boolean isTokenValid(final String token) {
        try {
            final HttpRequest validateRequest = HttpRequest.newBuilder(URI.create(authValidateUrl))
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            final HttpResponse<Void> resp = httpClient.send(validateRequest, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "token validation call failed", e);
            return false;
        }
    }

    private void proxy(final Request request, final Response response, final Callback callback, final String target)
            throws IOException, InterruptedException {
        final String rawQuery = request.getHttpURI().getQuery();
        final String query = stripIbToken(rawQuery);
        final String uri = target + request.getHttpURI().getPath() + (query == null ? "" : "?" + query);

        final HttpRequest.Builder upstreamBuilder = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(30));
        for (final HttpField field : request.getHeaders()) {
            if (!HOP_BY_HOP_HEADERS.contains(field.getLowerCaseName())) {
                upstreamBuilder.header(field.getName(), field.getValue());
            }
        }
        final String method = request.getMethod();
        final HttpRequest.BodyPublisher body = ("GET".equals(method) || "HEAD".equals(method))
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofInputStream(() -> Request.asInputStream(request));
        upstreamBuilder.method(method, body);

        // Se buferea la respuesta completa en memoria antes de escribirla: los assets de estas
        // herramientas (JS/CSS/HTML) son de tamaño modesto, y esto evita la ambiguedad de
        // framing de Content-Length/chunked al mezclar HttpClient (upstream) con el streaming
        // Content.Sink de Jetty 12 al escribir antes de conocer el tamano total.
        final HttpResponse<byte[]> upstreamResponse = sendWithRetry(upstreamBuilder.build());

        response.setStatus(upstreamResponse.statusCode());
        final HttpFields.Mutable outHeaders = response.getHeaders();
        upstreamResponse.headers().map().forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                for (final String value : values) outHeaders.add(name, value);
            }
        });
        final byte[] bodyBytes = upstreamResponse.body();
        outHeaders.put(HttpHeader.CONTENT_LENGTH, Long.toString(bodyBytes.length));

        response.write(true, java.nio.ByteBuffer.wrap(bodyBytes), callback);
    }

    /**
     * El pool de conexiones de {@link HttpClient} reutiliza conexiones keep-alive hacia el
     * upstream (ej. Etherpad/Node); si el servidor cierra una conexion inactiva justo antes de
     * que el pool la reuse, el intento falla con {@code EOFException}/"header parser received
     * no bytes" (carrera clasica de conexion obsoleta, ver logs de produccion 2026-07-12/13).
     * Un reintento agarra una conexion nueva y resuelve la carrera. Solo se reintenta para
     * metodos idempotentes sin cuerpo: el cuerpo de POST/PUT viene de un InputStream de la
     * request original que ya se habria consumido parcialmente en el primer intento.
     */
    private HttpResponse<byte[]> sendWithRetry(final HttpRequest upstreamRequest)
            throws IOException, InterruptedException {
        final boolean retryable = "GET".equals(upstreamRequest.method()) || "HEAD".equals(upstreamRequest.method());
        try {
            return httpClient.send(upstreamRequest, HttpResponse.BodyHandlers.ofByteArray());
        } catch (final IOException e) {
            if (!retryable) throw e;
            return httpClient.send(upstreamRequest, HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private static String stripIbToken(final String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return rawQuery;
        final StringBuilder sb = new StringBuilder();
        for (final String pair : rawQuery.split("&")) {
            if (pair.startsWith("ib_token=")) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(pair);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String queryParam(final Request request, final String name) {
        final String query = request.getHttpURI().getQuery();
        if (query == null) return null;
        for (final String pair : query.split("&")) {
            final int eq = pair.indexOf('=');
            if (eq < 0) continue;
            final String key = pair.substring(0, eq);
            if (key.equals(name)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    static String hostOf(final Request request) {
        final String hostHeader = request.getHeaders().get(HttpHeader.HOST);
        if (hostHeader == null) return "";
        final int colon = hostHeader.indexOf(':');
        return colon >= 0 ? hostHeader.substring(0, colon) : hostHeader;
    }

    private void writeLoginRequired(final Request request, final Response response, final Callback callback) {
        final String html = """
                <!doctype html><html lang="es"><head><meta charset="utf-8">
                <title>Inicia sesión</title>
                <style>body{font-family:system-ui,sans-serif;background:#1e1b4b;color:#fff;
                display:flex;align-items:center;justify-content:center;height:100vh;margin:0}
                .box{background:#fff;color:#1e1b4b;padding:32px 40px;border-radius:16px;
                max-width:420px;text-align:center;box-shadow:0 12px 32px rgba(0,0,0,.35)}
                a{display:inline-block;margin-top:16px;background:#4f46e5;color:#fff;
                padding:10px 24px;border-radius:8px;text-decoration:none;font-weight:600}
                </style></head><body><div class="box">
                <h2>Inicia sesión en InsightBloom</h2>
                <p>Esta herramienta solo está disponible para asistentes con sesión activa.</p>
                <a href="%s">Ir a InsightBloom</a>
                </div></body></html>
                """.formatted(loginUrl);
        writeSimpleResponse(request, response, callback, 401, html);
    }

    private void writeSimpleResponse(final Request request, final Response response, final Callback callback,
                                      final int status, final String bodyOrHtml) {
        final byte[] bytes = bodyOrHtml.getBytes(StandardCharsets.UTF_8);
        response.setStatus(status);
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/html; charset=utf-8");
        response.getHeaders().put(HttpHeader.CONTENT_LENGTH, Long.toString(bytes.length));
        response.write(true, java.nio.ByteBuffer.wrap(bytes), callback);
    }

}
