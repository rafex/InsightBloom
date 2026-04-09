package dev.rafex.insightbloom.moderation.adapters.outbound.queryclient;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rafex.insightbloom.moderation.domain.ports.QueryPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
public class HttpQueryPort implements QueryPort {
    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper;
    public HttpQueryPort(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }
    @Override
    public void setWordVisibility(String conferenceUuid, String wordNormalized, boolean visible) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                "conferenceUuid", conferenceUuid != null ? conferenceUuid : "",
                "wordNormalized", wordNormalized != null ? wordNormalized : "",
                "visible", visible));
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/visibility"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) { /* best-effort */ }
    }
    @Override
    public void setMessageVisibility(String messageUuid, boolean visible) {
        try {
            String body = mapper.writeValueAsString(Map.of("messageUuid", messageUuid, "visible", visible));
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/message-visibility"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) { /* best-effort */ }
    }
}
