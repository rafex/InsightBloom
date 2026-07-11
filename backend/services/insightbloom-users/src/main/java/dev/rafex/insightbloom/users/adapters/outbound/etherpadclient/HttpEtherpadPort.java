package dev.rafex.insightbloom.users.adapters.outbound.etherpadclient;

import dev.rafex.insightbloom.users.domain.ports.EtherpadPort;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Cliente de la API HTTP de Etherpad (https://etherpad.org/doc/v1.9.7/#index_api_methods).
 * `createPad` devuelve `code: 1` si el pad ya existe — se trata igual que exito (idempotente),
 * no como error.
 */
public class HttpEtherpadPort implements EtherpadPort {
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient client;

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
    public void deletePad(final String padId) {
        if (baseUrl == null || baseUrl.isBlank()) return;
        call("deletePad", padId);
    }

    private void call(final String method, final String padId) {
        try {
            final String encodedPadId = URLEncoder.encode(padId, StandardCharsets.UTF_8);
            final String uri = "%s/api/1/%s?apikey=%s&padID=%s".formatted(baseUrl, method, apiKey, encodedPadId);
            final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (final Exception e) {
            // best-effort: si Etherpad no responde, la pestaña "Notas" degrada con un mensaje
            // claro en el frontend (NFR-006) en vez de romper el flujo del evento.
        }
    }
}
