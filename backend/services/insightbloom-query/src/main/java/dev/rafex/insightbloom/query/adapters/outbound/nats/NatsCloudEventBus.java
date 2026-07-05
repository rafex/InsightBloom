package dev.rafex.insightbloom.query.adapters.outbound.nats;

import dev.rafex.ether.json.JsonCodec;
import dev.rafex.insightbloom.query.domain.model.MessageType;
import dev.rafex.insightbloom.query.domain.ports.CloudEventBus;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

public class NatsCloudEventBus implements CloudEventBus {

    private final Connection connection;
    private final JsonCodec codec;

    public NatsCloudEventBus(final String natsUrl, final String natsToken, final JsonCodec codec) throws Exception {
        this.codec = codec;
        final Options.Builder builder = new Options.Builder()
                .server(natsUrl)
                .connectionTimeout(Duration.ofSeconds(5))
                .maxReconnects(-1);
        if (natsToken != null && !natsToken.isBlank()) {
            builder.token(natsToken);
        }
        this.connection = Nats.connect(builder.build());
    }

    @Override
    public void publish(final String conferenceUuid, final MessageType type, final Map<String, Object> payload) {
        final byte[] body = codec.toJson(payload).getBytes(StandardCharsets.UTF_8);
        connection.publish(subject(conferenceUuid, type), body);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AutoCloseable subscribe(final String conferenceUuid, final MessageType type,
                                    final Consumer<Map<String, Object>> onUpdate) {
        final Dispatcher dispatcher = connection.createDispatcher(msg -> {
            try {
                final Map<String, Object> payload = codec.readValue(bodyOf(msg), Map.class);
                onUpdate.accept(payload);
            } catch (final Exception ignored) {
                // Malformed message from a mismatched deploy — drop it, don't kill the stream.
            }
        });
        dispatcher.subscribe(subject(conferenceUuid, type));
        return () -> connection.closeDispatcher(dispatcher);
    }

    private static String bodyOf(final Message msg) {
        return new String(msg.getData(), StandardCharsets.UTF_8);
    }

    private static String subject(final String conferenceUuid, final MessageType type) {
        return "cloud." + conferenceUuid + "." + type.name();
    }
}
