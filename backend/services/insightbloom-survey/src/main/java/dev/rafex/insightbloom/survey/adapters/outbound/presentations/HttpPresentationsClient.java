package dev.rafex.insightbloom.survey.adapters.outbound.presentations;

import dev.rafex.insightbloom.survey.domain.ports.PresentationsPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class HttpPresentationsClient implements PresentationsPort {
    private final String baseUrl;
    private final HttpClient httpClient;
    private final String internalApiKey;

    public HttpPresentationsClient(final String baseUrl) {
        this.baseUrl = baseUrl;
        // HTTP_1_1 forzado: el servidor de presentaciones es Express/Node, que no
        // negocia el upgrade a h2c que el cliente JDK intenta por defecto (HTTP_2),
        // dejando la conexion colgada/fallida sin excepcion visible.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.internalApiKey = System.getenv("INTERNAL_API_KEY");
    }

    @Override
    public Optional<String> fetchMarkdown(final String conferenceId) {
        final var builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/conferences/" + conferenceId + "/presentation/markdown"))
                .timeout(Duration.ofSeconds(10));
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            builder.header("X-Internal-API-Key", internalApiKey);
        }
        final HttpRequest request = builder.GET().build();
        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();
            return Optional.of(response.body());
        } catch (final java.io.IOException | InterruptedException e) {
            System.err.println("HttpPresentationsClient.fetchMarkdown failed for " + conferenceId + ": " + e);
            return Optional.empty();
        }
    }
}
