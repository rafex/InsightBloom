package dev.rafex.insightbloom.toolsgateway;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.ether.json.JsonCodec;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proxy inverso para backends/API REST que un alumno publica desde su sandbox (ver
 * PublishAppPreviewUseCase en insightbloom-users) -- deliberadamente una clase SEPARADA de
 * {@link AuthGateHandler} en vez de otra rama dentro de esa misma clase: el modelo de
 * autenticación es distinto de raíz (un {@code accessToken} por publicación con su propio TTL,
 * no una sesión de InsightBloom con cookie+login), y el cliente típico no es un navegador con
 * sesión (Postman, un grader, un script) sino cualquier consumidor HTTP -- mezclar ambos modelos
 * en un solo checkAuth() habría sido más riesgoso que la duplicación puntual del mecanismo de
 * proxy (HTTP/1.1 forzado + reintento ante conexión obsoleta, ver comentarios en
 * {@link AuthGateHandler#proxy}, con los mismos fixes aplicados acá).
 *
 * Ruta esperada: {@code /p/{publicationId}/resto/del/path}, header {@code X-Preview-Token} con
 * el token emitido al publicar. El prefijo {@code /p/{publicationId}} se recorta antes de
 * reenviar -- el proceso del alumno ve rutas normales de su propia app, no sabe nada del gateway.
 */
final class AppPreviewGateHandler extends Handler.Abstract {
    private static final Logger LOGGER = Logger.getLogger(AppPreviewGateHandler.class.getName());
    private static final JsonCodec JSON_CODEC = JacksonJsonCodec.defaultCodec();
    private static final Pattern PATH_PATTERN = Pattern.compile("^/p/([0-9a-fA-F-]{36})(/.*)?$");
    private static final int MAX_RETRIES = 2;
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "content-length", "host",
            "x-preview-token");
    private static final String ACCEPT_ENCODING = "accept-encoding";

    private final String resolveUrl;
    private final String internalApiKey;
    private volatile HttpClient httpClient = newProxyHttpClient();

    AppPreviewGateHandler(final String resolveUrl, final String internalApiKey) {
        this.resolveUrl = resolveUrl;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public boolean handle(final Request request, final Response response, final Callback callback) {
        if ("/health".equals(request.getHttpURI().getPath())) {
            writeSimpleResponse(request, response, callback, 200, "OK");
            return true;
        }
        final Matcher matcher = PATH_PATTERN.matcher(request.getHttpURI().getPath());
        if (!matcher.matches()) {
            writeSimpleResponse(request, response, callback, 404, "Publicación no encontrada.");
            return true;
        }
        final String publicationId = matcher.group(1);
        final String forwardPath = matcher.group(2) == null || matcher.group(2).isEmpty() ? "/" : matcher.group(2);
        final String token = request.getHeaders().get("X-Preview-Token");
        if (token == null || token.isBlank()) {
            writeSimpleResponse(request, response, callback, 401, "Falta el header X-Preview-Token.");
            return true;
        }
        final String target = resolveTarget(publicationId, token);
        if (target == null) {
            writeSimpleResponse(request, response, callback, 404,
                    "Publicación no encontrada, expirada, o token inválido.");
            return true;
        }
        try {
            proxy(request, response, callback, target, forwardPath);
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "app-preview proxy failed for publicationId=" + publicationId, e);
            writeSimpleResponse(request, response, callback, 502, "No se pudo contactar la aplicación publicada.");
        }
        return true;
    }

    /** null si la publicación no existe, expiró, el token no coincide, o el llamado falla. */
    private String resolveTarget(final String publicationId, final String token) {
        if (resolveUrl == null || internalApiKey == null) return null;
        try {
            final String uri = resolveUrl
                    + "?publicationId=" + URLEncoder.encode(publicationId, StandardCharsets.UTF_8)
                    + "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            final HttpRequest resolveRequest = HttpRequest.newBuilder(URI.create(uri))
                    .header("X-Internal-Auth", internalApiKey)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            final HttpResponse<String> resp = httpClient.send(resolveRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            final var node = JSON_CODEC.readTree(resp.body());
            final var targetNode = JSON_CODEC.at(node, "/data/target");
            return targetNode.isMissingNode() || targetNode.isNull() ? null : targetNode.asText();
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "app-preview target resolution failed", e);
            return null;
        }
    }

    /** Mismo mecanismo (bufferizado, HTTP/1.1, reintento ante EOF) que {@link AuthGateHandler#proxy}. */
    private void proxy(final Request request, final Response response, final Callback callback,
                        final String target, final String forwardPath) throws IOException, InterruptedException {
        final String query = request.getHttpURI().getQuery();
        final String uri = target + forwardPath + (query == null ? "" : "?" + query);

        final HttpRequest.Builder upstreamBuilder = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(30));
        for (final HttpField field : request.getHeaders()) {
            if (!HOP_BY_HOP_HEADERS.contains(field.getLowerCaseName())
                    && !ACCEPT_ENCODING.equals(field.getLowerCaseName())) {
                upstreamBuilder.header(field.getName(), field.getValue());
            }
        }
        upstreamBuilder.header("Accept-Encoding", "identity");
        final String method = request.getMethod();
        final HttpRequest.BodyPublisher body = ("GET".equals(method) || "HEAD".equals(method))
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofInputStream(() -> Request.asInputStream(request));
        upstreamBuilder.method(method, body);

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
        final HttpClient freshClient = newProxyHttpClient();
        final var result = freshClient.send(upstreamRequest, HttpResponse.BodyHandlers.ofByteArray());
        httpClient = freshClient;
        return result;
    }

    private static HttpClient newProxyHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    private void writeSimpleResponse(final Request request, final Response response, final Callback callback,
                                      final int status, final String body) {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        response.setStatus(status);
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain; charset=utf-8");
        response.getHeaders().put(HttpHeader.CONTENT_LENGTH, Long.toString(bytes.length));
        response.write(true, java.nio.ByteBuffer.wrap(bytes), callback);
    }
}
