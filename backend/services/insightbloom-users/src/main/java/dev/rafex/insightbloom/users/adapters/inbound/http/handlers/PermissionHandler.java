package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.domain.model.Permission;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Catálogo fijo de permisos (de solo lectura), usado por quien administra roles para armar el formulario sin hardcodear opciones en el frontend. */
public class PermissionHandler extends BaseResourceHandler {

    @Override
    protected String basePath() {
        return "/api/v1/permissions";
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
        sendOk(jx, Arrays.stream(Permission.values()).map(Enum::name).toList());
        return true;
    }
}
