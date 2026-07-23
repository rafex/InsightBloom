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
    private static final int MAX_ATTEMPTS = 2;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(35);
    private static final Duration RETRY_DELAY = Duration.ofMillis(150);
    private final String presentationsUrl;
    private final String internalApiKey;
    // El renderer Node/Express atiende HTTP/1.1. No dejar que el cliente intente
    // negociar HTTP/2 (h2c) contra el Service de Kubernetes: en ese caso Java
    // puede cerrar la conexión antes de recibir los headers y el certificado
    // termina expuesto como un 502 aunque Chromium esté sano.
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final JacksonJsonCodec json = JacksonJsonCodec.defaultCodec();

    public HttpCertificateRenderer(final String presentationsUrl, final String internalApiKey) {
        this.presentationsUrl = presentationsUrl;
        this.internalApiKey = internalApiKey;
    }

    @Override public byte[] render(final String documentJson, final Map<String, Object> data) {
        if (presentationsUrl == null || presentationsUrl.isBlank() || internalApiKey == null || internalApiKey.isBlank()) {
            throw new IllegalStateException("certificate_renderer_not_configured");
        }
        final String body = json.toJson(Map.of("documentJson", documentJson, "data", data));
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(presentationsUrl.replaceAll("/$", "") + "/internal/v1/certificates/render"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-Internal-Api-Key", internalApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        IllegalStateException lastNetworkFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                final HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) return response.body();

                final IllegalStateException failure = new IllegalStateException(
                        "certificate_renderer_failed_" + response.statusCode());
                if (attempt < MAX_ATTEMPTS && response.statusCode() >= 500) {
                    lastNetworkFailure = failure;
                    pauseBeforeRetry();
                    continue;
                }
                throw failure;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("certificate_renderer_interrupted", e);
            } catch (java.io.IOException e) {
                lastNetworkFailure = new IllegalStateException("certificate_renderer_failed_network", e);
                if (attempt < MAX_ATTEMPTS) {
                    pauseBeforeRetry();
                    continue;
                }
                throw lastNetworkFailure;
            }
        }
        throw lastNetworkFailure == null
                ? new IllegalStateException("certificate_renderer_failed")
                : lastNetworkFailure;
    }

    private static void pauseBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("certificate_renderer_interrupted", e);
        }
    }
}
