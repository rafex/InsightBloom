package dev.rafex.insightbloom.moderation.adapters.outbound.notifyclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rafex.insightbloom.moderation.domain.ports.NotifyPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class HttpNotifyClient implements NotifyPort {
    private final String usersBaseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public HttpNotifyClient(final String usersBaseUrl) {
        this.usersBaseUrl = usersBaseUrl;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    @Override
    public void notifyDoubtAnswered(final String authorUuid, final String conferenceUuid,
                                     final String question, final String answer) {
        try {
            final String body = mapper.writeValueAsString(Map.of(
                    "authorUuid", authorUuid != null ? authorUuid : "",
                    "conferenceUuid", conferenceUuid != null ? conferenceUuid : "",
                    "question", question != null ? question : "",
                    "answer", answer != null ? answer : ""));
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(usersBaseUrl + "/api/v1/notify/doubt-answered"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (final Exception e) {
            // best-effort: an answer is still saved even if the email notification fails
        }
    }
}
