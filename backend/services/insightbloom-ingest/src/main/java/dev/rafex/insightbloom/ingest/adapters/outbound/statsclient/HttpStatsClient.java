package dev.rafex.insightbloom.ingest.adapters.outbound.statsclient;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rafex.insightbloom.ingest.domain.ports.StatsPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
public class HttpStatsClient implements StatsPort {
    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String internalKey;
    public HttpStatsClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
        this.internalKey = System.getenv("INTERNAL_API_KEY");
    }
    @Override
    public void recalc(RecalcRequest request) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                "conferenceUuid", request.conferenceUuid(),
                "wordNormalized", request.wordNormalized(),
                "wordCanonical", request.wordCanonical(),
                "messageType", request.messageType(),
                "wordIntent", request.wordIntent() != null ? request.wordIntent() : "INTERES",
                "visible", request.visible()
            ));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/recalc"))
                .header("Content-Type","application/json");
            if (internalKey != null && !internalKey.isEmpty()) {
                builder.header("X-Internal-Auth", internalKey);
            }
            HttpRequest req = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) { /* fire and forget */ }
    }
}
