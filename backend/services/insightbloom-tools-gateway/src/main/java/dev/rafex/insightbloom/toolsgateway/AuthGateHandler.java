package dev.rafex.insightbloom.toolsgateway;

import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.ether.json.JsonCodec;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
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
    private static final JsonCodec JSON_CODEC = JacksonJsonCodec.defaultCodec();
    static final String SESSION_COOKIE = "ib_gw";
    static final Duration SESSION_TTL = Duration.ofHours(4);
    private static final int MAX_RETRIES = 2;
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "content-length", "host");

    private final Map<String, String> routesByHost;
    private final String authValidateUrl;
    private final String loginUrl;
    private final String ideHost;
    private final String sandboxResolveUrl;
    private final String internalApiKey;
    private final Set<String> allowedOrigins;
    private final SessionCache sessionCache = new SessionCache();
    private volatile HttpClient httpClient = newProxyHttpClient();

    AuthGateHandler(final Map<String, String> routesByHost, final String authValidateUrl, final String loginUrl) {
        this(routesByHost, authValidateUrl, loginUrl, null, null, null);
    }

    /**
     * Fase 3b del IDE: {@code ideHost}/{@code sandboxResolveUrl}/{@code internalApiKey} habilitan
     * ruteo dinamico por-sesion (el sandbox de cada usuario, a diferencia del target fijo por
     * host que usan drawio/Etherpad/Excalidraw) — si {@code ideHost} es null, el gateway se
     * comporta exactamente igual que antes (solo targets estaticos de {@code routesByHost}).
     */
    AuthGateHandler(final Map<String, String> routesByHost, final String authValidateUrl, final String loginUrl,
                     final String ideHost, final String sandboxResolveUrl, final String internalApiKey) {
        this.routesByHost = routesByHost;
        this.authValidateUrl = authValidateUrl;
        this.loginUrl = loginUrl;
        this.ideHost = ideHost;
        this.sandboxResolveUrl = sandboxResolveUrl;
        this.internalApiKey = internalApiKey;
        this.allowedOrigins = buildAllowedOrigins(routesByHost, ideHost, loginUrl);
    }

    /**
     * Auditoria de seguridad 2026-07-17: {@code SameSite=NONE} (necesario para el extension host
     * sandboxeado de code-server, ver el comentario junto a {@code sameSite(...)} mas abajo) deja
     * la cookie de sesion disponible para requests cross-site — sin validar {@code Origin}, un
     * sitio malicioso podria abrir un WebSocket o disparar un fetch/form-POST hacia este gateway
     * desde el navegador de la victima, con la cookie adjuntada automaticamente (Cross-Site
     * WebSocket Hijacking / CSRF). Esta allowlist junta: el origen de {@code loginUrl} (la app
     * principal de InsightBloom, que embebe algunas de estas herramientas via iframe) y cada host
     * de {@code routesByHost} + {@code ideHost} (para navegacion/reload directo sobre la propia
     * herramienta). Ver {@link #isOriginAllowed}.
     */
    private static Set<String> buildAllowedOrigins(final Map<String, String> routesByHost, final String ideHost,
                                                     final String loginUrl) {
        final Set<String> origins = new HashSet<>();
        for (final String host : routesByHost.keySet()) {
            origins.add("https://" + host);
        }
        if (ideHost != null) {
            origins.add("https://" + ideHost);
        }
        if (loginUrl != null) {
            try {
                final URI parsed = URI.create(loginUrl);
                if (parsed.getScheme() != null && parsed.getHost() != null) {
                    origins.add(parsed.getScheme() + "://" + parsed.getHost());
                }
            } catch (final IllegalArgumentException ignored) {
                // loginUrl mal formado: no agrega nada, el resto de la allowlist sigue valida.
            }
        }
        return origins;
    }

    /**
     * {@code lenient=true} (requests HTTP normales): un {@code Origin} ausente se permite --
     * muchos navegadores no lo mandan en una navegacion top-level GET normal (pegar la URL,
     * click en un link), que es como el usuario realmente abre el IDE. Si esta presente pero no
     * matchea la allowlist, se rechaza (cubre fetch/XHR/form-POST cross-site con Origin real).
     * {@code lenient=false} (upgrade a WebSocket): el header {@code Origin} SIEMPRE esta presente
     * en un handshake de WebSocket real de navegador (parte del protocolo, RFC 6455) -- su
     * ausencia ya es sospechosa, asi que se exige y se rechaza si falta o no matchea.
     */
    boolean isOriginAllowed(final String originHeader, final boolean lenient) {
        if (originHeader == null || originHeader.isBlank()) {
            return lenient;
        }
        return allowedOrigins.contains(originHeader);
    }

    @Override
    public boolean handle(final Request request, final Response response, final Callback callback) {
        if ("/health".equals(request.getHttpURI().getPath())) {
            writeSimpleResponse(request, response, callback, 200, "OK");
            return true;
        }
        if ("/version".equals(request.getHttpURI().getPath())) {
            final Properties gp = new Properties();
            try (var is = getClass().getClassLoader().getResourceAsStream("git.properties")) {
                if (is != null) gp.load(is);
            } catch (final IOException ignored) { }
            final String json = "{\"service\":\"tools-gateway\""
                + ",\"version\":\"" + System.getenv().getOrDefault("APP_VERSION", "dev") + "\""
                + ",\"gitSha\":\"" + gp.getProperty("git.commit.id.abbrev", "unknown") + "\""
                + ",\"buildTime\":\"" + gp.getProperty("git.build.time", "unknown") + "\"}";
            writeSimpleResponse(request, response, callback, 200, json);
            return true;
        }
        final String host = hostOf(request);
        final boolean isIdeHost = host.equals(ideHost);
        final String staticTarget = routesByHost.get(host);
        if (!isIdeHost && staticTarget == null) {
            writeSimpleResponse(request, response, callback, 502, "Herramienta no reconocida.");
            return true;
        }

        final String origin = request.getHeaders().get(HttpHeader.ORIGIN);
        if (!isOriginAllowed(origin, true)) {
            LOGGER.warning(() -> "auth rechazada por Origin no permitido host=" + host + " origin=" + origin);
            writeSimpleResponse(request, response, callback, 403, "Origen no permitido.");
            return true;
        }

        final AuthResult auth = checkAuth(request, host, isIdeHost);
        if (!auth.authenticated()) {
            writeLoginRequired(request, response, callback);
            return true;
        }
        // SameSite=NONE (no LAX): el extension host de code-server corre en un iframe/worker
        // sandboxeado con origen opaco (webWorkerExtensionHostIframe.html); confirmado en vivo
        // 2026-07-17 que sus requests (incl. el WS de conexion del extension host) llegaban sin
        // la cookie de sesion con SameSite=LAX -- Chrome las trata como cross-site por el origen
        // opaco del sandbox, aunque la URL sea el mismo host. Sigue siendo Secure+HttpOnly.
        if (auth.newSessionId() != null) {
            Response.addCookie(response, HttpCookie.build(SESSION_COOKIE, auth.newSessionId())
                    .path("/")
                    .httpOnly(true)
                    .secure(true)
                    .sameSite(HttpCookie.SameSite.NONE)
                    .maxAge(SESSION_TTL.toSeconds())
                    .build());
        }

        final String target = isIdeHost ? auth.dynamicTarget() : staticTarget;
        if (target == null) {
            writeSimpleResponse(request, response, callback, 502, "Sandbox no disponible.");
            return true;
        }

        try {
            proxy(request, response, callback, target);
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "proxy failed for host=" + host, e);
            writeSimpleResponse(request, response, callback, 502, "No se pudo contactar la herramienta.");
        }
        return true;
    }

    record AuthResult(boolean authenticated, String newSessionId, String dynamicTarget) {}

    boolean isIdeHost(final String host) {
        return host.equals(ideHost);
    }

    String staticTarget(final String host) {
        return routesByHost.get(host);
    }

    AuthResult checkAuth(final Request request, final String host, final boolean isIdeHost) {
        final String clientIp = Request.getRemoteAddr(request);
        if (isRateLimited(clientIp)) {
            LOGGER.warning(() -> "auth rechazada por rate limit ip=" + clientIp + " host=" + host);
            return new AuthResult(false, null, null);
        }
        final String token = queryParam(request, "ib_token");
        // Host del IDE: si la URL trae ib_token+conferenceId explicitos, SIEMPRE re-resuelve el
        // target en vez de confiar en una cookie de sesion existente -- necesario para cuando el
        // alumno cambia de variante (Web->CLI o viceversa, ver IdePage.vue "Cambiar de modo"): la
        // cookie vieja sigue siendo "valida" pero su dynamicTarget cacheado apunta al Service de
        // la variante ANTERIOR, que ya fue borrado (incidente 2026-07-19: "no se pudo contactar
        // la herramienta" -- UnresolvedAddressException contra un Service inexistente). Los
        // sub-recursos de la misma sesion (JS, WS upgrade, etc.) no llevan estos query params y
        // siguen resolviendo por cookie normalmente, mas abajo.
        if (isIdeHost && token != null && !token.isBlank()) {
            final String conferenceId = queryParam(request, "conferenceId");
            if (conferenceId != null && !conferenceId.isBlank()) {
                final String target = resolveSandboxTarget(token, conferenceId);
                if (target == null) {
                    logAuthRejected(request, host, false, "resolveSandboxTarget devolvio null (token invalido o sin sandbox activo)");
                    return new AuthResult(false, null, null);
                }
                LOGGER.info(() -> "auth ok (ib_token+conferenceId, re-resolviendo sesion) host=" + host
                    + " conferenceId=" + conferenceId + " dynamicTarget=" + target);
                return new AuthResult(true, sessionCache.mint(SESSION_TTL, target), target);
            }
        }
        boolean sawSessionCookie = false;
        for (final HttpCookie cookie : Request.getCookies(request)) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                sawSessionCookie = true;
                final String dynamicTarget = sessionCache.dynamicTarget(cookie.getValue());
                if (sessionCache.isValid(cookie.getValue())) {
                    if (isIdeHost) {
                        LOGGER.info(() -> "auth ok (cookie de sesion) host=" + host
                            + " path=" + request.getHttpURI().getPath() + " dynamicTarget=" + dynamicTarget);
                    }
                    return new AuthResult(true, null, dynamicTarget);
                }
            }
        }
        if (token == null || token.isBlank()) {
            logAuthRejected(request, host, sawSessionCookie, "sin cookie de sesion valida ni ib_token en query");
            return new AuthResult(false, null, null);
        }
        if (isIdeHost) {
            logAuthRejected(request, host, sawSessionCookie, "ib_token presente pero falta conferenceId en query");
            return new AuthResult(false, null, null);
        }
        if (isTokenValid(token)) {
            return new AuthResult(true, sessionCache.mint(SESSION_TTL), null);
        }
        logAuthRejected(request, host, sawSessionCookie, "ib_token invalido segun AUTH_VALIDATE_URL");
        return new AuthResult(false, null, null);
    }

    /**
     * Diagnostico agregado 2026-07-16: los 401 de sub-recursos del extension host de code-server
     * (ej. extension.js, mergeConflictMain.js) no dejaban rastro alguno en logs -- WebSocketProxyCreator
     * tampoco logueaba sus rechazos silenciosos (401/502). Esto deja evidencia real de por que
     * checkAuth rechaza cada request, en vez de tener que adivinar contra los logs vacios.
     */
    private void logAuthRejected(final Request request, final String host, final boolean sawSessionCookie, final String reason) {
        LOGGER.warning(() -> "auth rechazada host=" + host + " path=" + request.getHttpURI().getPath()
                + " cookieDeSesionPresente=" + sawSessionCookie + " motivo=" + reason);
        recordAuthFailure(Request.getRemoteAddr(request));
    }

    private static final int RATE_LIMIT_MAX_FAILURES = 20;
    private static final long RATE_LIMIT_WINDOW_MILLIS = 60_000;
    private final Map<String, FailureWindow> authFailuresByIp = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Auditoria de seguridad 2026-07-17: no habia ningun limite a intentos repetidos de
     * ib_token/sesion invalidos -- un intento de fuerza bruta (aunque de baja probabilidad dado
     * el tamano del token) no tenia friccion. Ventana fija simple por IP, en memoria (mismo
     * trade-off que {@link SessionCache}: no compartido entre replicas si el gateway alguna vez
     * escala a mas de un pod, aceptable para el volumen actual). No afecta reconexiones
     * legitimas del navegador: 20 fallos/minuto es muchisimo mas que cualquier patron normal de
     * reconexion (el propio VS Code Web reintenta cada pocos segundos, pero con un token valido
     * que autentica en el primer intento).
     */
    private static final class FailureWindow {
        private long windowStart = System.currentTimeMillis();
        private int count;
    }

    private boolean isRateLimited(final String clientIp) {
        if (clientIp == null) return false;
        final FailureWindow window = authFailuresByIp.computeIfAbsent(clientIp, k -> new FailureWindow());
        synchronized (window) {
            resetIfWindowExpired(window);
            return window.count >= RATE_LIMIT_MAX_FAILURES;
        }
    }

    private void recordAuthFailure(final String clientIp) {
        if (clientIp == null) return;
        final FailureWindow window = authFailuresByIp.computeIfAbsent(clientIp, k -> new FailureWindow());
        synchronized (window) {
            resetIfWindowExpired(window);
            window.count++;
        }
    }

    private static void resetIfWindowExpired(final FailureWindow window) {
        final long now = System.currentTimeMillis();
        if (now - window.windowStart > RATE_LIMIT_WINDOW_MILLIS) {
            window.windowStart = now;
            window.count = 0;
        }
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

    /** null si el token es invalido, el usuario no tiene sandbox activo, o el llamado falla. */
    private String resolveSandboxTarget(final String token, final String conferenceId) {
        if (sandboxResolveUrl == null || internalApiKey == null) return null;
        try {
            final String uri = sandboxResolveUrl
                    + "?token=" + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8)
                    + "&conferenceId=" + java.net.URLEncoder.encode(conferenceId, StandardCharsets.UTF_8);
            final HttpRequest resolveRequest = HttpRequest.newBuilder(URI.create(uri))
                    .header("X-Internal-Auth", internalApiKey)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            final HttpResponse<String> resp = httpClient.send(resolveRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                LOGGER.warning(() -> "resolveSandboxTarget: " + sandboxResolveUrl + " respondio "
                    + resp.statusCode() + " para conferenceId=" + conferenceId);
                return null;
            }
            final var node = JSON_CODEC.readTree(resp.body());
            final var target = JSON_CODEC.at(node, "/data/target");
            if (target.isMissingNode() || target.isNull()) {
                LOGGER.warning(() -> "resolveSandboxTarget: respuesta 200 sin data/target para conferenceId="
                    + conferenceId + " body=" + resp.body());
                return null;
            }
            return target.asText();
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "sandbox target resolution failed", e);
            return null;
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
        if (upstreamResponse.statusCode() >= 400) {
            LOGGER.warning(() -> "proxy: upstream " + uri + " respondio " + upstreamResponse.statusCode());
        }

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
     * Se reintenta con el mismo {@code httpClient} hasta {@link #MAX_RETRIES} veces; si todos
     * los reintentos fallan, se crea un {@link HttpClient} fresco (pool virgen, TCP nuevo) y
     * se reemplaza el cliente compartido. Solo se reintenta para metodos idempotentes sin
     * cuerpo: el cuerpo de POST/PUT viene de un InputStream de la request original que ya se
     * habria consumido parcialmente en el primer intento.
     */
    private HttpResponse<byte[]> sendWithRetry(final HttpRequest upstreamRequest)
            throws IOException, InterruptedException {
        final boolean retryable = "GET".equals(upstreamRequest.method()) || "HEAD".equals(upstreamRequest.method());
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return httpClient.send(upstreamRequest, HttpResponse.BodyHandlers.ofByteArray());
            } catch (final IOException e) {
                if (!retryable) throw e;
            }
        }
        LOGGER.info("stale connection not recovered after " + MAX_RETRIES
                + " retries, creating fresh HttpClient");
        final HttpClient freshClient = newProxyHttpClient();
        final var result = freshClient.send(upstreamRequest, HttpResponse.BodyHandlers.ofByteArray());
        httpClient = freshClient;
        return result;
    }

    /**
     * Fuerza HTTP/1.1: por defecto {@link HttpClient} intenta negociar HTTP/2 en texto plano
     * (h2c) incluso contra destinos {@code http://} — las herramientas self-hosted que este
     * gateway proxea (Etherpad/drawio/Excalidraw/code-server) son servidores HTTP/1.1 planos
     * que no manejan ese intento de upgrade, y cierran la conexion sin responder. Eso produce
     * exactamente {@code EOFException}/"header parser received no bytes" -- reproducido incluso
     * con un {@link HttpClient} recien creado (pool vacio, conexion nueva), lo que descarta que
     * el problema fuera reutilizar una conexion keep-alive obsoleta (la teoria de los fixes
     * anteriores de este archivo). Confirmado en produccion 2026-07-16: un `wget` liso desde el
     * mismo pod al mismo destino funcionaba siempre; solo este HttpClient fallaba.
     */
    private static HttpClient newProxyHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
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
