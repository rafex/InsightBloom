package dev.rafex.insightbloom.ingest.adapters.outbound.queryclient;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rafex.insightbloom.ingest.domain.ports.QueryPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
public class HttpQueryClient implements QueryPort {
    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper;
    public HttpQueryClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }
    @Override
    public void update(UpdateRequest request) {
        try {
            String body = mapper.writeValueAsString(Map.ofEntries(
                Map.entry("conferenceUuid", request.conferenceUuid()),
                Map.entry("wordNormalized", request.wordNormalized()),
                Map.entry("wordCanonical", request.wordCanonical()),
                Map.entry("messageType", request.messageType()),
                Map.entry("relevanceScore", request.relevanceScore()),
                Map.entry("messageCount", request.messageCount()),
                Map.entry("messageUuid", request.messageUuid() != null ? request.messageUuid() : ""),
                Map.entry("authorLabel", request.authorLabel() != null ? request.authorLabel() : ""),
                Map.entry("authorKind", request.authorKind()),
                Map.entry("detailVisible", request.detailVisible() != null ? request.detailVisible() : ""),
                Map.entry("receivedAt", request.receivedAt()),
                Map.entry("wordVisible", request.wordVisible())
            ));
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/update"))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) { /* fire and forget */ }
    }
}
