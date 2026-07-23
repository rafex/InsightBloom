package dev.rafex.insightbloom.users.adapters.outbound.surveyclient;

import dev.rafex.insightbloom.users.domain.ports.SurveyPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpSurveyClient implements SurveyPort {
    private final String baseUrl;
    private final HttpClient httpClient;

    public HttpSurveyClient(final String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public boolean hasResponded(final String conferenceUuid, final String token) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/conferences/" + conferenceUuid + "/survey/responded"))
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("survey_service_unavailable_" + response.statusCode());
            }
            final var node = dev.rafex.ether.json.JsonUtils.codec().readTree(response.body()).path("data");
            return node.path("responded").asBoolean(false);
        } catch (final Exception e) {
            if (e instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("survey_service_unavailable", e);
        }
    }
}
