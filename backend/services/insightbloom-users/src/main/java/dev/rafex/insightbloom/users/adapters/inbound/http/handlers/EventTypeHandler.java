package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.CreateEventTypeUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListEventTypesUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetEventTypeActiveUseCase;
import dev.rafex.insightbloom.users.application.usecases.UpdateEventTypeUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.EventType;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catálogo de tipos de evento: lectura pública/autenticada del subconjunto activo (para el
 * selector del organizador) y administración completa (CRUD, activar/desactivar) restringida a
 * ADMIN. También expone el catálogo fijo de capacidades para que el frontend arme el formulario
 * de administración sin hardcodear opciones.
 */
public class EventTypeHandler extends BaseResourceHandler {

    private final ListEventTypesUseCase listEventTypesUseCase;
    private final CreateEventTypeUseCase createEventTypeUseCase;
    private final UpdateEventTypeUseCase updateEventTypeUseCase;
    private final SetEventTypeActiveUseCase setEventTypeActiveUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public EventTypeHandler(final ListEventTypesUseCase listEventTypesUseCase,
                             final CreateEventTypeUseCase createEventTypeUseCase,
                             final UpdateEventTypeUseCase updateEventTypeUseCase,
                             final SetEventTypeActiveUseCase setEventTypeActiveUseCase,
                             final ValidateTokenUseCase validateTokenUseCase) {
        this.listEventTypesUseCase = listEventTypesUseCase;
        this.createEventTypeUseCase = createEventTypeUseCase;
        this.updateEventTypeUseCase = updateEventTypeUseCase;
        this.setEventTypeActiveUseCase = setEventTypeActiveUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    public record EventTypeView(String uuid, String key, String name, String description,
                                 List<String> capabilities, boolean active) {}

    private static EventTypeView toView(final EventType e) {
        return new EventTypeView(e.getUuid(), e.getKey(), e.getName(), e.getDescription(),
                e.getCapabilities().stream().map(Enum::name).sorted().toList(), e.isActive());
    }

    @Override
    protected String basePath() {
        return "/api/v1/event-types";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/", Set.of("GET", "POST")),
                Route.of("/all", Set.of("GET")),
                Route.of("/{uuid}", Set.of("PUT")),
                Route.of("/{uuid}/active", Set.of("PUT")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST", "PUT");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        try {
            final boolean allRequested = jx.path().endsWith("/all");
            if (allRequested && !requireAdmin(jx)) return true;
            final List<EventTypeView> items = listEventTypesUseCase.execute(!allRequested).stream()
                    .map(EventTypeHandler::toView).toList();
            sendOk(jx, items);
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        if (!requireAdmin(jx)) return true;
        try {
            final var body = parseBody(jx);
            final EventType created = createEventTypeUseCase.execute(
                    (String) body.get("key"), (String) body.get("name"), (String) body.get("description"),
                    parseCapabilities(body.get("capabilities")));
            sendOk(jx, 201, toView(created));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    @Override
    public boolean put(final HttpExchange x) {
        final var jx = asJetty(x);
        if (!requireAdmin(jx)) return true;
        final String uuid = jx.pathParam("uuid");
        try {
            if (jx.path().endsWith("/active")) {
                final var body = parseBody(jx);
                final boolean active = Boolean.TRUE.equals(body.get("active"));
                sendOk(jx, toView(setEventTypeActiveUseCase.execute(uuid, active)));
                return true;
            }
            final var body = parseBody(jx);
            final EventType updated = updateEventTypeUseCase.execute(
                    uuid, (String) body.get("name"), (String) body.get("description"),
                    parseCapabilities(body.get("capabilities")));
            sendOk(jx, toView(updated));
        } catch (final IllegalArgumentException e) {
            sendError(jx, "event_type_not_found".equals(e.getMessage()) ? 404 : 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Set<EventCapability> parseCapabilities(final Object raw) {
        if (!(raw instanceof List<?> list)) return Set.of();
        return ((List<String>) list).stream().map(EventCapability::valueOf).collect(Collectors.toSet());
    }

    private boolean requireAdmin(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return false; }
        final var v = validateTokenUseCase.execute(token);
        if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
            sendError(jx, 403, "forbidden", "Only admins can manage event types");
            return false;
        }
        return true;
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }
}
