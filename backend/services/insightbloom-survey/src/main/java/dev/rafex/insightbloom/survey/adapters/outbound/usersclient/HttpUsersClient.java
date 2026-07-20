package dev.rafex.insightbloom.survey.adapters.outbound.usersclient;

import dev.rafex.insightbloom.survey.domain.ports.UsersPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class HttpUsersClient implements UsersPort {
    private final String baseUrl;
    private final HttpClient httpClient;
    private final String internalApiKey;

    public HttpUsersClient(final String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.internalApiKey = System.getenv("INTERNAL_API_KEY");
    }

    @Override
    public ValidationResult validate(final String token) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/auth/validate"))
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return new ValidationResult(false, null, null, null);
            final var node = dev.rafex.ether.json.JsonUtils.codec().readTree(response.body()).path("data");
            return new ValidationResult(
                    node.path("valid").asBoolean(false), node.path("subjectUuid").asText(null),
                    node.path("kind").asText(null), node.path("role").asText(null));
        } catch (final Exception e) {
            return new ValidationResult(false, null, null, null);
        }
    }

    @Override
    public Optional<String> getDisplayName(final String userUuid) {
        try {
            final var builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/users/" + userUuid))
                    .timeout(Duration.ofSeconds(5));
            // GET /users/{uuid} ahora exige autenticacion (ver auditoria de seguridad 2026-07) --
            // esta es una llamada servicio-a-servicio de confianza (mostrar el nombre del autor en
            // resultados de encuesta para el organizador), no tiene el token del usuario a mano.
            if (internalApiKey != null && !internalApiKey.isEmpty()) {
                builder.header("X-Internal-Auth", internalApiKey);
            }
            final HttpResponse<String> response = httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();
            final var node = dev.rafex.ether.json.JsonUtils.codec().readTree(response.body()).path("data");
            final String displayName = node.path("displayName").asText(null);
            return Optional.ofNullable(displayName);
        } catch (final Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isConferenceOwner(final String conferenceUuid, final String userUuid) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/conferences/" + conferenceUuid))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;
            final var node = dev.rafex.ether.json.JsonUtils.codec().readTree(response.body()).path("data");
            return userUuid.equals(node.path("createdByUserUuid").asText(null));
        } catch (final Exception e) {
            return false;
        }
    }
}
