package dev.rafex.insightbloom.users.adapters.outbound.idepublisher;

import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.users.domain.model.ContainerBuildResult;
import dev.rafex.insightbloom.users.domain.ports.ContainerRuntimePublisher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Cliente interno hacia el Deployment de runtime Podman de los IDE. */
public final class HttpIdeRuntimePublisher implements ContainerRuntimePublisher {
    private final String baseUrl;
    private final String internalApiKey;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JacksonJsonCodec json = JacksonJsonCodec.defaultCodec();

    public HttpIdeRuntimePublisher(final String baseUrl, final String internalApiKey) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.internalApiKey = internalApiKey;
    }

    @Override
    public ContainerBuildResult buildAndRun(final String content, final int hostPort, final int containerPort) {
        if (baseUrl.isBlank() || internalApiKey == null || internalApiKey.isBlank()) {
            throw new IllegalStateException("ide_runtime_not_configured");
        }
        final String body = "{\"containerfile\":\"" + escape(content) + "\",\"hostPort\":" + hostPort
                + ",\"containerPort\":" + containerPort + "}";
        final HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/build"))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .header("X-Internal-Api-Key", internalApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        try {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                final var node = json.readTree(response.body());
                return ContainerBuildResult.failure(node.path("error").asText("container_build_failed"),
                        node.path("detail").asText(response.body()));
            }
            return ContainerBuildResult.ok();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ide_runtime_interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("ide_runtime_unavailable", e);
        }
    }

    private static String escape(final String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
