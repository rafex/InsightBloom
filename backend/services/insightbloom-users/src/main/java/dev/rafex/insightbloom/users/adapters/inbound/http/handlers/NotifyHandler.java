package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.NotifyDoubtAnsweredUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Internal, service-to-service endpoint: triggered by insightbloom-moderation. */
public class NotifyHandler extends BaseResourceHandler {

    private final NotifyDoubtAnsweredUseCase notifyDoubtAnsweredUseCase;

    public NotifyHandler(final NotifyDoubtAnsweredUseCase notifyDoubtAnsweredUseCase) {
        this.notifyDoubtAnsweredUseCase = notifyDoubtAnsweredUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/notify";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/doubt-answered", Set.of("POST")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("POST");
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        if (!validInternalAuth(jx)) {
            sendError(jx, 403, "forbidden", "Internal access only");
            return true;
        }
        try {
            final var body = parseBody(jx);
            final String authorUuid = optionalText(body, "authorUuid", 100);
            final String conferenceUuid = requiredText(body, "conferenceUuid", 100);
            final String question = requiredText(body, "question", 10_000);
            final String answer = requiredText(body, "answer", 10_000);
            if (!authorUuid.isBlank()) UUID.fromString(authorUuid);
            UUID.fromString(conferenceUuid);
            notifyDoubtAnsweredUseCase.execute(new NotifyDoubtAnsweredUseCase.Request(
                    authorUuid, conferenceUuid, question, answer));
            sendOk(jx, Map.of("status", "queued"));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, "invalid_request", "Invalid notification payload");
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Notification could not be queued");
        }
        return true;
    }

    private static String requiredText(final Map<String, Object> body, final String key, final int maxLength) {
        final Object value = body.get(key);
        if (!(value instanceof String text) || text.isBlank() || text.length() > maxLength) {
            throw new IllegalArgumentException("invalid_" + key);
        }
        return text.trim();
    }

    private static String optionalText(final Map<String, Object> body, final String key, final int maxLength) {
        final Object value = body.get(key);
        if (value == null) return "";
        if (!(value instanceof String text) || text.length() > maxLength) {
            throw new IllegalArgumentException("invalid_" + key);
        }
        return text.trim();
    }
}
