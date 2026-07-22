package dev.rafex.insightbloom.users.adapters.outbound.presentationsclient;

import dev.rafex.ether.json.JacksonJsonCodec;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Cliente interno: Playwright vive en el servicio Node de presentaciones. */
public final class HttpCertificateRenderer implements CertificateRenderer {
    private final String presentationsUrl;
    private final String internalApiKey;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JacksonJsonCodec json = JacksonJsonCodec.defaultCodec();

    public HttpCertificateRenderer(final String presentationsUrl, final String internalApiKey) {
        this.presentationsUrl = presentationsUrl;
        this.internalApiKey = internalApiKey;
    }

    @Override public byte[] render(final String documentJson, final Map<String, Object> data) {
        if (presentationsUrl == null || presentationsUrl.isBlank() || internalApiKey == null || internalApiKey.isBlank()) {
            throw new IllegalStateException("certificate_renderer_not_configured");
        }
        try {
            final String body = json.toJson(Map.of("documentJson", documentJson, "data", data));
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(presentationsUrl.replaceAll("/$", "") + "/internal/v1/certificates/render"))
                    .timeout(Duration.ofSeconds(35))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            final HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IllegalStateException("certificate_renderer_failed_" + response.statusCode());
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("certificate_renderer_interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("certificate_renderer_failed", e);
        }
    }
}
