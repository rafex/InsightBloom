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
    public EvaluationResult evaluate(String word, String detail, String conferenceUuid, String wordCanonical, String messageUuid, String wordText, String detailText) {
        try {
            var bodyMap = new java.util.HashMap<String, Object>();
            bodyMap.put("word", word != null ? word : "");
            bodyMap.put("detail", detail != null ? detail : "");
            bodyMap.put("conferenceUuid", conferenceUuid != null ? conferenceUuid : "");
            bodyMap.put("wordCanonical", wordCanonical != null ? wordCanonical : (word != null ? word : ""));
            bodyMap.put("messageUuid", messageUuid != null ? messageUuid : "");
            bodyMap.put("wordText", wordText != null ? wordText : "");
            bodyMap.put("detailText", detailText != null ? detailText : "");
            String body = mapper.writeValueAsString(bodyMap);
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
