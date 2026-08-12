package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.HttpExchange.EventStream;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.ListNotificationsUseCase;
import dev.rafex.insightbloom.users.application.usecases.MarkNotificationReadUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.domain.model.Notification;
import dev.rafex.insightbloom.users.domain.services.NotificationStreamRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Campana de notificaciones del portal: listar, marcar leída, y un stream SSE por usuario para
 * empujarlas en vivo (ver SendNotificationUseCase, que es quien las inserta y las empuja). */
public class NotificationHandler extends BaseResourceHandler {
    private static final Logger LOGGER = Logger.getLogger(NotificationHandler.class.getName());
    private final ListNotificationsUseCase listNotificationsUseCase;
    private final MarkNotificationReadUseCase markNotificationReadUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final NotificationStreamRegistry streamRegistry;

    public NotificationHandler(final ListNotificationsUseCase listNotificationsUseCase,
                                final MarkNotificationReadUseCase markNotificationReadUseCase,
                                final ValidateTokenUseCase validateTokenUseCase,
                                final NotificationStreamRegistry streamRegistry) {
        this.listNotificationsUseCase = listNotificationsUseCase;
        this.markNotificationReadUseCase = markNotificationReadUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
        this.streamRegistry = streamRegistry;
    }

    @Override protected String basePath() { return "/api/v1/notifications"; }

    @Override protected List<Route> routes() {
        return List.of(
                Route.of("", Set.of("GET")),
                Route.of("/stream", Set.of("GET")),
                Route.of("/{uuid}/read", Set.of("POST")));
    }

    @Override public Set<String> supportedMethods() { return Set.of("GET", "POST"); }

    @Override public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/stream")) return handleStream(jx);
        return handleList(jx);
    }

    @Override public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/read")) return handleMarkRead(jx, jx.pathParam("uuid"));
        return false;
    }

    private boolean handleList(final JettyHttpExchange jx) {
        final var auth = requireUser(jx);
        if (auth == null) return true;
        try {
            final int limit = parseIntParam(jx, "limit", 20);
            final int offset = parseIntParam(jx, "offset", 0);
            final var result = listNotificationsUseCase.execute(auth.subjectUuid(), limit, offset);
            sendOk(jx, Map.of(
                    "items", result.items().stream().map(NotificationHandler::view).toList(),
                    "unreadCount", result.unreadCount()));
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "NotificationHandler: error inesperado en " + jx.path(), e);
            sendError(jx, 500, "internal_error", "No se pudieron cargar las notificaciones");
        }
        return true;
    }

    private boolean handleMarkRead(final JettyHttpExchange jx, final String uuid) {
        final var auth = requireUser(jx);
        if (auth == null) return true;
        try {
            final boolean marked = markNotificationReadUseCase.execute(uuid, auth.subjectUuid());
            sendOk(jx, Map.of("marked", marked));
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "NotificationHandler: error inesperado en " + jx.path(), e);
            sendError(jx, 500, "internal_error", "No se pudo marcar la notificación");
        }
        return true;
    }

    private boolean handleStream(final JettyHttpExchange jx) {
        final var auth = requireUser(jx);
        if (auth == null) return true;
        final String userUuid = auth.subjectUuid();
        final EventStream stream = jx.startEventStream();
        streamRegistry.register(userUuid, stream);
        stream.onClose(() -> streamRegistry.unregister(userUuid, stream));
        return true;
    }

    private ValidateTokenUseCase.ValidationResult requireUser(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        final String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return null; }
        final var validation = validateTokenUseCase.execute(token);
        if (!validation.valid() || !"user".equals(validation.kind())) {
            sendError(jx, 401, "token_invalid", "Invalid token");
            return null;
        }
        return validation;
    }

    private static int parseIntParam(final JettyHttpExchange jx, final String name, final int fallback) {
        final String raw = queryParam(jx, name);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    private static Map<String, Object> view(final Notification n) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("uuid", n.getUuid());
        map.put("type", n.getType());
        map.put("title", n.getTitle());
        map.put("body", n.getBody());
        map.put("linkUrl", n.getLinkUrl());
        map.put("createdAt", n.getCreatedAt().toString());
        map.put("readAt", n.getReadAt() == null ? null : n.getReadAt().toString());
        return map;
    }
}
