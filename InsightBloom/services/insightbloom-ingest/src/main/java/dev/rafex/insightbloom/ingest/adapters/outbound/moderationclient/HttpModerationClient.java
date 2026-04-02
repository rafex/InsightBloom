package dev.rafex.insightbloom.ingest.adapters.outbound.moderationclient;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rafex.insightbloom.ingest.domain.ports.ModerationPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
public class HttpModerationClient implements ModerationPort {
    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper;
    public HttpModerationClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }
    @Override
    public EvaluationResult evaluate(String word, String detail) {
        try {
            String body = mapper.writeValueAsString(Map.of("word", word != null ? word : "", "detail", detail != null ? detail : ""));
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/evaluate"))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                var node = mapper.readTree(resp.body());
                var data = node.get("data");
                boolean wb = data.get("wordBlocked").asBoolean();
                boolean db = data.get("detailBlocked").asBoolean();
                return new EvaluationResult(wb, db);
            }
        } catch (Exception e) { /* service unavailable */ }
        return new EvaluationResult(false, false);
    }
}
