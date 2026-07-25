package dev.rafex.insightbloom.query.adapters.outbound.usersclient;

import dev.rafex.insightbloom.query.domain.ports.ConferenceLifecyclePort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

public class HttpConferenceLifecycleClient implements ConferenceLifecyclePort {
    private final String usersBaseUrl;
    private final HttpClient httpClient;
    private final String internalApiKey;

    public HttpConferenceLifecycleClient(final String usersBaseUrl) {
        this.usersBaseUrl = usersBaseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.internalApiKey = System.getenv("INTERNAL_API_KEY");
    }

    @Override
    public boolean isActive(final String conferenceUuid) {
        try {
            final var builder = HttpRequest.newBuilder()
                    .uri(URI.create(usersBaseUrl + "/api/v1/conferences/" + conferenceUuid))
                    .timeout(Duration.ofSeconds(5));
            if (internalApiKey != null && !internalApiKey.isEmpty()) {
                builder.header("X-Internal-Auth", internalApiKey);
            }
            final HttpResponse<String> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;
            final var node = dev.rafex.ether.json.JsonUtils.codec().readTree(response.body()).path("data");
            if (!"ACTIVE".equals(node.path("status").asText(null))) return false;
            final String expiresAt = node.path("expiresAt").asText(null);
            if (expiresAt != null && !expiresAt.isBlank() && Instant.parse(expiresAt).isBefore(Instant.now())) return false;
            return true;
        } catch (final Exception e) {
            return false;
        }
    }
}
