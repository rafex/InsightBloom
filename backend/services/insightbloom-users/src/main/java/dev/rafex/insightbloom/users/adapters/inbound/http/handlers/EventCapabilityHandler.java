package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.domain.model.EventCapability;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Catálogo fijo de capacidades de plataforma (de solo lectura), usado por el ADMIN para armar el formulario de tipos de evento sin hardcodear opciones en el frontend. */
public class EventCapabilityHandler extends BaseResourceHandler {

    @Override
    protected String basePath() {
        return "/api/v1/event-capabilities";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/", Set.of("GET")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        sendOk(jx, Arrays.stream(EventCapability.values()).map(Enum::name).toList());
        return true;
    }
}
