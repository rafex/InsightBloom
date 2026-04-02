package dev.rafex.insightbloom.ingest.adapters.outbound.usersclient;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rafex.insightbloom.ingest.domain.ports.UsersPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
public class HttpUsersClient implements UsersPort {
    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper;
    public HttpUsersClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }
    @Override
    public ValidationResult validate(String token) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/auth/validate"))
                .header("Authorization","Bearer " + token)
                .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                var node = mapper.readTree(resp.body()).get("data");
                return new ValidationResult(
                    node.get("valid").asBoolean(), node.get("subjectUuid").asText(),
                    node.get("kind").asText(), node.get("role").asText()
                );
            }
        } catch (Exception e) { /* service unavailable */ }
        return new ValidationResult(false, null, null, null);
    }
}
