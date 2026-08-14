package dev.rafex.insightbloom.users.adapters.outbound.etherpadclient;

import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.users.domain.ports.EtherpadPort;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente de la API HTTP de Etherpad (https://etherpad.org/doc/v1.9.7/#index_api_methods).
 * `createPad` devuelve `code: 1` si el pad ya existe — se trata igual que exito (idempotente),
 * no como error.
 *
 * SECURITY: se intentó pasar el apikey solo por header HTTP Authorization (Bearer) para no
 * exponerlo en URLs/logs, pero se confirmó en vivo (2026-08-14) que con
 * authenticationMethod=apikey (ver InsightBloom-gitops/.../etherpad-deployment.yaml) Etherpad
 * ignora el header Authorization por completo -- su checkAccess solo mira `?apikey=` como query
 * param. Volvió a usarse el query param como único mecanismo que Etherpad realmente acepta; como
 * defensa en profundidad, sanitizeUrlForLogging() enmascara el apikey si la URL llegara a
 * loguearse por cualquier razón (nunca se loguea hoy, pero la mantenemos por si acaso).
 */
public class HttpEtherpadPort implements EtherpadPort {
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient client;
    private final JacksonJsonCodec jsonCodec = JacksonJsonCodec.defaultCodec();

    public HttpEtherpadPort(final String baseUrl, final String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.client = HttpClient.newHttpClient();
    }

    @Override
    public void ensurePadExists(final String padId) {
        if (baseUrl == null || baseUrl.isBlank()) return;
        call("createPad", padId);
    }

    @Override
    public PadContent readPad(final String padId) {
        if (baseUrl == null || baseUrl.isBlank()) return new PadContent("", "");
        return new PadContent(callContent("getText", padId, "text"), callContent("getHTML", padId, "html"));
    }

    @Override
    public void deletePad(final String padId) {
        if (baseUrl == null || baseUrl.isBlank()) return;
        call("deletePad", padId);
    }

    @Override
    public String getReadOnlyId(final String padId) {
        if (baseUrl == null || baseUrl.isBlank()) return padId;
        return callContent("getReadOnlyID", padId, "readOnlyID");
    }

    @Override
    public void deletePadsForConference(final String conferenceUuid) {
        if (baseUrl == null || baseUrl.isBlank()) return;
        final String privatePrefix = conferenceUuid + "--private--";
        for (final String padId : listAllPads()) {
            if (padId.equals(conferenceUuid) || padId.startsWith(privatePrefix)) deletePad(padId);
        }
    }

    private List<String> listAllPads() {
        try {
            final String uri = "%s/api/1/listAllPads?apikey=%s".formatted(baseUrl, encoded(apiKey));
            final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) return List.of();
            final var pads = jsonCodec.readTree(response.body()).path("data").path("padIDs");
            final List<String> result = new ArrayList<>();
            pads.forEach(node -> result.add(node.asText()));
            return result;
        } catch (final Exception e) {
            return List.of();
        }
    }

    private String callContent(final String method, final String padId, final String field) {
        try {
            final String uri = "%s/api/1/%s?padID=%s&apikey=%s".formatted(baseUrl, method, encoded(padId), encoded(apiKey));
            final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) throw new IllegalStateException("etherpad_export_failed");
            return jsonCodec.readTree(response.body()).path("data").path(field).asText("");
        } catch (final java.io.IOException | InterruptedException e) {
            throw new IllegalStateException("etherpad_export_failed", e);
        }
    }

    private void call(final String method, final String padId) {
        try {
            final String uri = "%s/api/1/%s?padID=%s&apikey=%s".formatted(baseUrl, method, encoded(padId), encoded(apiKey));
            final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (final Exception e) {
            // best-effort: si Etherpad no responde, la pestaña "Notas" degrada con un mensaje
            // claro en el frontend (NFR-006) en vez de romper el flujo del evento.
        }
    }

    private static String encoded(final String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /**
     * Enmascara la API key en una URL si está presente como query parameter.
     * Ninguno de los métodos de esta clase loguea la URL hoy; esto es únicamente
     * defensa en profundidad por si algún día se agrega logging de requests salientes.
     */
    public static String sanitizeUrlForLogging(final String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        // Reemplaza apikey=ACTUAL_VALUE con apikey=***
        return url.replaceAll("apikey=[^&]+", "apikey=***").replaceAll("apikey=$", "apikey=***");
    }
}
