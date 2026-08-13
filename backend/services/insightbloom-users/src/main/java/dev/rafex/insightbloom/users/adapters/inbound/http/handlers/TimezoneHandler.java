package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.ListTimezonesUseCase;

import java.util.List;
import java.util.Set;

/** Public, read-only catalog used to populate the timezone picker when creating/editing a conference. */
public class TimezoneHandler extends BaseResourceHandler {

    private final ListTimezonesUseCase listTimezonesUseCase;

    public TimezoneHandler(final ListTimezonesUseCase listTimezonesUseCase) {
        this.listTimezonesUseCase = listTimezonesUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/timezones";
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
        try {
            sendOk(jx, listTimezonesUseCase.execute());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal server error");
        }
        return true;
    }
}
