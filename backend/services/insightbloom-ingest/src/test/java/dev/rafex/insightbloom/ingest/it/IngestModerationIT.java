package dev.rafex.insightbloom.ingest.it;

import com.fasterxml.jackson.databind.JsonNode;
import dev.rafex.ether.json.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de integracion (Fase 5, corre solo con `mvn verify -Pintegration`): prueba que
 * insightbloom-ingest realmente habla con insightbloom-moderation via HTTP real dentro
 * del stack de {@code infra/compose/local.yml} -- algo que ningun test unitario mockeado
 * de este repo verifica hoy (el HttpModerationClient siempre esta mockeado).
 *
 * Cubre dos capas:
 * 1. El flujo publico completo: POST /api/v1/messages en ingest, sin auth (permitido como
 *    guest), y confirma que la respuesta incluye un status real de ContentStatus.
 * 2. La llamada interna de la que depende ese flujo: POST /internal/evaluate/ en
 *    moderation con el header X-Internal-Auth -- si INTERNAL_API_KEY no coincide entre
 *    ambos servicios (BaseResourceHandler.validInternalAuth falla-cerrado), esto responde
 *    403 en vez de 200, lo cual el flujo (1) por si solo no distingue con claridad porque
 *    IngestMessageUseCase cae a "mensaje permitido" si moderation no es alcanzable.
 */
class IngestModerationIT {

    private static final String INGEST_BASE_URL = System.getProperty("it.ingest.baseUrl", "http://localhost:8082");
    private static final String MODERATION_BASE_URL = System.getProperty("it.moderation.baseUrl", "http://localhost:8084");
    private static final String INTERNAL_API_KEY = System.getProperty("it.internalApiKey", "local-dev-internal-key");
    private static final Set<String> VALID_STATUSES = Set.of("visible", "censurado_auto", "censurado_manual");

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void publicMessageIngestReturnsRealModerationVerdict() throws Exception {
        final String conferenceId = "it-test-" + UUID.randomUUID();
        final String body = JacksonJsonCodec.defaultCodec().toJson(Map.of(
                "conferenceId", conferenceId,
                "author", Map.of("displayName", "IT Test"),
                "message", Map.of("type", "doubt", "word", "prueba-it", "detail", "mensaje de integracion")));

        final HttpRequest request = HttpRequest.newBuilder(URI.create(INGEST_BASE_URL + "/api/v1/messages"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode(), "respuesta inesperada: " + response.body());
        final JsonNode node = JacksonJsonCodec.defaultCodec().readTree(response.body());
        final var messageId = JacksonJsonCodec.defaultCodec().at(node, "/data/messageId");
        final var status = JacksonJsonCodec.defaultCodec().at(node, "/data/status");
        assertFalse(messageId.isMissingNode() || messageId.asText().isBlank(), "esperaba messageId: " + response.body());
        assertTrue(VALID_STATUSES.contains(status.asText()), "status inesperado: " + status.asText());
    }

    @Test
    void internalEvaluateAcceptsTheSharedKeyIngestUses() throws Exception {
        final String body = JacksonJsonCodec.defaultCodec().toJson(Map.of(
                "word", "prueba-it", "detail", "mensaje de integracion",
                "conferenceUuid", "it-test-" + UUID.randomUUID(),
                "wordCanonical", "prueba-it", "messageUuid", UUID.randomUUID().toString(),
                "wordText", "prueba-it", "detailText", "mensaje de integracion",
                "authorUuid", "anonymous", "authorDisplayName", "IT Test"));

        final HttpRequest request = HttpRequest.newBuilder(URI.create(MODERATION_BASE_URL + "/internal/evaluate/"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Internal-Auth", INTERNAL_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                "moderation rechazo el X-Internal-Auth -- INTERNAL_API_KEY no coincide entre ingest y moderation "
                        + "en infra/compose/local.yml: " + response.body());
        final JsonNode node = JacksonJsonCodec.defaultCodec().readTree(response.body());
        final var wordBlocked = JacksonJsonCodec.defaultCodec().at(node, "/data/wordBlocked");
        assertFalse(wordBlocked.isMissingNode(), "esperaba /data/wordBlocked: " + response.body());
    }
}
