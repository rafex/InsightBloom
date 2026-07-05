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
    private final String internalKey;
    public HttpModerationClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
        this.internalKey = System.getenv("INTERNAL_API_KEY");
    }
    @Override
    public EvaluationResult evaluate(String word, String detail, String conferenceUuid, String wordCanonical, String messageUuid, String wordText, String detailText, String authorUuid, String authorDisplayName) {
        try {
            var bodyMap = new java.util.HashMap<String, Object>();
            bodyMap.put("word", word != null ? word : "");
            bodyMap.put("detail", detail != null ? detail : "");
            bodyMap.put("conferenceUuid", conferenceUuid != null ? conferenceUuid : "");
            bodyMap.put("wordCanonical", wordCanonical != null ? wordCanonical : (word != null ? word : ""));
            bodyMap.put("messageUuid", messageUuid != null ? messageUuid : "");
            bodyMap.put("wordText", wordText != null ? wordText : "");
            bodyMap.put("detailText", detailText != null ? detailText : "");
            bodyMap.put("authorUuid", authorUuid != null ? authorUuid : "");
            bodyMap.put("authorDisplayName", authorDisplayName != null ? authorDisplayName : "");
            String body = mapper.writeValueAsString(bodyMap);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/evaluate"))
                .header("Content-Type","application/json");
            if (internalKey != null && !internalKey.isEmpty()) {
                builder.header("X-Internal-Auth", internalKey);
            }
            HttpRequest req = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                var node = mapper.readTree(resp.body());
                var data = node.get("data");
                boolean wb = data.get("wordBlocked").asBoolean();
                boolean db = data.get("detailBlocked").asBoolean();
                return new EvaluationResult(wb, db);
            }
            System.err.println("HttpModerationClient.evaluate failed: HTTP " + resp.statusCode() + " " + resp.body());
        } catch (Exception e) {
            System.err.println("HttpModerationClient.evaluate error: " + e);
        }
        return new EvaluationResult(false, false);
    }
}
