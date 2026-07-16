package dev.rafex.insightbloom.stats.it;

import dev.rafex.ether.json.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Test de integracion (Fase 5, corre solo con `mvn verify -Pintegration`, nunca en el
 * `mvn verify` normal): asume que insightbloom-stats esta corriendo de verdad detrás de
 * {@code infra/compose/local.yml} (puerto configurable via -Dit.stats.baseUrl, default
 * localhost:8085).
 */
class VersionIT {

    private static final String BASE_URL = System.getProperty("it.stats.baseUrl", "http://localhost:8085");

    @Test
    void versionEndpointRespondsWithServiceName() throws Exception {
        final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        final HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/version"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        final var node = JacksonJsonCodec.defaultCodec().readTree(response.body());
        final var service = JacksonJsonCodec.defaultCodec().at(node, "/data/service");
        assertFalse(service.isMissingNode(), "esperaba /data/service en la respuesta: " + response.body());
        assertEquals("insightbloom-stats", service.asText());
    }
}
