package dev.rafex.insightbloom.users.adapters.outbound.cascade;

import dev.rafex.insightbloom.users.domain.ports.CascadeDeletePort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class HttpCascadeDeleteClient implements CascadeDeletePort {
    private final HttpClient httpClient;
    private final List<String> deleteUrls;
    private final String internalApiKey;

    public HttpCascadeDeleteClient(final String ingestUrl, final String queryUrl, final String moderationUrl,
                                    final String presentationsUrl, final String surveyUrl,
                                    final String internalApiKey) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.internalApiKey = internalApiKey;
        this.deleteUrls = List.of(
                ingestUrl + "/api/v1/conferences/%s/messages",
                queryUrl + "/api/v1/conferences/%s/cloud",
                moderationUrl + "/api/v1/conferences/%s/moderation",
                presentationsUrl + "/api/v1/conferences/%s/presentation",
                surveyUrl + "/api/v1/conferences/%s/survey");
    }

    @Override
    public void deleteConferenceData(final String conferenceUuid) {
        for (final String template : deleteUrls) {
            try {
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(String.format(template, conferenceUuid)))
                        .timeout(Duration.ofSeconds(10))
                        .header("X-Internal-Api-Key", internalApiKey)
                        .DELETE()
                        .build();
                httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (final Exception e) {
                System.err.println("cascade_delete_failed url=" + template + " conference=" + conferenceUuid
                        + " error=" + e.getMessage());
            }
        }
    }
}
