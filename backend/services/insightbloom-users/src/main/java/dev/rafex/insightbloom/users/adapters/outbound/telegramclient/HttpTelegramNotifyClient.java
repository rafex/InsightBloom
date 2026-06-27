package dev.rafex.insightbloom.users.adapters.outbound.telegramclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rafex.insightbloom.users.domain.ports.TelegramNotifyPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class HttpTelegramNotifyClient implements TelegramNotifyPort {
    private final String telegramServiceUrl;
    private final String internalApiKey;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public HttpTelegramNotifyClient(final String telegramServiceUrl, final String internalApiKey) {
        this.telegramServiceUrl = telegramServiceUrl;
        this.internalApiKey = internalApiKey;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    @Override
    public void notifyConference(final String conferenceUuid, final String message) {
        try {
            final String body = mapper.writeValueAsString(Map.of(
                    "conferenceUuid", conferenceUuid != null ? conferenceUuid : "",
                    "message", message != null ? message : ""));
            final HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(telegramServiceUrl + "/internal/notify"))
                    .header("Content-Type", "application/json");
            if (internalApiKey != null && !internalApiKey.isEmpty()) {
                builder.header("X-Internal-Auth", internalApiKey);
            }
            final HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (final Exception e) {
            // best-effort: la respuesta ya quedó guardada aunque la notificación a Telegram falle
        }
    }
}
