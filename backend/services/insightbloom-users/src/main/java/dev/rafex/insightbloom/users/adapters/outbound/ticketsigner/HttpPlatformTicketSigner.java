package dev.rafex.insightbloom.users.adapters.outbound.ticketsigner;

import dev.rafex.insightbloom.users.application.usecases.PlatformTicketSigner;
import dev.rafex.ether.json.JsonUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/** Authenticated users-to-signer client. The private key is never part of this protocol. */
public final class HttpPlatformTicketSigner implements PlatformTicketSigner {
    private final URI endpoint;
    private final String token;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public HttpPlatformTicketSigner(final String baseUrl, final String token) {
        this.endpoint = URI.create(baseUrl.replaceAll("/$", "") + "/internal/v1/tickets/sign");
        this.token = token;
    }

    @Override public Signed sign(final String conferenceUuid, final String canonicalPayload) {
        if (token == null || token.isBlank()) throw new IllegalStateException("ticket_signer_not_configured");
        try {
            final String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            final String body = JsonUtils.toJson(Map.of("eventUuid", conferenceUuid, "payload", payload));
            final HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("ticket_signer_unavailable");
            final var json = JsonUtils.codec().readTree(response.body());
            return new Signed(json.path("keyId").asText(), json.path("publicKeySpki").asText(),
                    json.path("signature").asText());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ticket_signer_unavailable", e);
        } catch (final Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("ticket_signer_unavailable", e);
        }
    }
}
