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

    public HttpPresentationsClient(final String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public Optional<String> fetchMarkdown(final String conferenceId) {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/conferences/" + conferenceId + "/presentation/markdown"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();
            return Optional.of(response.body());
        } catch (final java.io.IOException | InterruptedException e) {
            return Optional.empty();
        }
    }
}
