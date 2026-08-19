package dev.rafex.insightbloom.users.adapters.outbound.idepublisher;

import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.users.domain.ports.WorkspacePreviewPublisher;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/** Cliente interno al publicador aislado de artefactos y destinos de los IDE. */
public final class HttpWorkspacePreviewPublisher implements WorkspacePreviewPublisher {
    private final String publisherUrl;
    private final String internalApiKey;
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final JacksonJsonCodec json = JacksonJsonCodec.defaultCodec();

    public HttpWorkspacePreviewPublisher(final String publisherUrl, final String internalApiKey) {
        this.publisherUrl = publisherUrl;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public PreviewPublication publish(final String conferenceUuid, final String ownerUuid,
                                     final byte[] workspaceZip, final long ttlSeconds) {
        requireConfigured();
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/internal/v1/previews"))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/zip")
                .header("X-Internal-Api-Key", internalApiKey)
                .header("X-Conference-Id", conferenceUuid)
                .header("X-Owner-Id", ownerUuid)
                .header("X-Expires-In-Seconds", Long.toString(ttlSeconds))
                .POST(HttpRequest.BodyPublishers.ofByteArray(workspaceZip))
                .build();
        try {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                throw new IllegalStateException(mapFailure(response.statusCode(), response.body()));
            }
            final var root = json.readTree(response.body());
            return new PreviewPublication(
                    root.path("publicationId").asText(),
                    root.path("url").asText(),
                    Instant.parse(root.path("expiresAt").asText()),
                    root.path("artifactHash").asText(),
                    root.path("files").asInt());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("preview_publisher_interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("preview_publisher_unavailable", e);
        }
    }

    @Override
    public void revoke(final String conferenceUuid, final String ownerUuid, final String publicationId) {
        requireConfigured();
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/internal/v1/previews/" + publicationId))
                .timeout(Duration.ofSeconds(10))
                .header("X-Internal-Api-Key", internalApiKey)
                .header("X-Conference-Id", conferenceUuid)
                .header("X-Owner-Id", ownerUuid)
                .DELETE()
                .build();
        try {
            final HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200 && response.statusCode() != 404) {
                throw new IllegalStateException(mapFailure(response.statusCode(), ""));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("preview_publisher_interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("preview_publisher_unavailable", e);
        }
    }

    /** Registra un destino de API que ya fue validado por users y vive en el sandbox asignado. */
    public void registerAppPreview(final String conferenceUuid, final String ownerUuid,
                                   final String publicationId, final String podName,
                                   final int targetPort, final String accessToken,
                                   final Instant expiresAt) {
        requireConfigured();
        final String body = "{\"conferenceId\":\"" + escape(conferenceUuid)
                + "\",\"ownerId\":\"" + escape(ownerUuid)
                + "\",\"publicationId\":\"" + escape(publicationId)
                + "\",\"podName\":\"" + escape(podName)
                + "\",\"targetPort\":" + targetPort
                + ",\"accessToken\":\"" + escape(accessToken)
                + "\",\"expiresAt\":\"" + escape(expiresAt.toString()) + "\"}";
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/internal/v1/app-previews"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Internal-Api-Key", internalApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) throw new IllegalStateException(mapFailure(response.statusCode(), response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("preview_publisher_interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("preview_publisher_unavailable", e);
        }
    }

    public void revokeAppPreview(final String conferenceUuid, final String ownerUuid, final String publicationId) {
        requireConfigured();
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/internal/v1/app-previews/" + publicationId))
                .timeout(Duration.ofSeconds(10))
                .header("X-Internal-Api-Key", internalApiKey)
                .header("X-Conference-Id", conferenceUuid)
                .header("X-Owner-Id", ownerUuid)
                .DELETE().build();
        try {
            final HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200 && response.statusCode() != 404) throw new IllegalStateException(mapFailure(response.statusCode(), ""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("preview_publisher_interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("preview_publisher_unavailable", e);
        }
    }

    private String baseUrl() { return publisherUrl.replaceAll("/$", ""); }

    private void requireConfigured() {
        if (publisherUrl == null || publisherUrl.isBlank() || internalApiKey == null || internalApiKey.isBlank()) {
            throw new IllegalStateException("preview_publisher_not_configured");
        }
    }

    private static String escape(final String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String mapFailure(final int status, final String body) {
        if (status == 403) return "preview_publisher_forbidden";
        if (status == 422) return artifactRejection(body);
        if (status >= 500) return "preview_publisher_unavailable";
        return "preview_publisher_failed_" + status;
    }

    /** Conserva el diagnóstico útil, pero omite evidence porque podría contener secretos. */
    private String artifactRejection(final String body) {
        final StringBuilder details = new StringBuilder();
        try {
            final JsonNode issues = json.readTree(body).path("issues");
            if (issues.isArray()) {
                for (final JsonNode item : issues) {
                    if (details.length() > 0) details.append(" | ");
                    details.append(item.path("rule").asText("UNKNOWN"))
                            .append(" en ").append(item.path("file").asText("archivo"))
                            .append(": ").append(item.path("message").asText("contenido no permitido"));
                    if (details.length() >= 900) break;
                }
            }
        } catch (RuntimeException ignored) {
            // Mantener el código estable si el publicador devuelve un cuerpo no JSON.
        }
        return details.length() == 0 ? "preview_artifact_rejected" : "preview_artifact_rejected:" + details;
    }
}
