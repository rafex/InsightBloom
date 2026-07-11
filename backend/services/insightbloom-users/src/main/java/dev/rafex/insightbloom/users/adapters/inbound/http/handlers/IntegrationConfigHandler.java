package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;

import java.util.List;
import java.util.Set;

/**
 * Expone, de solo lectura y sin autenticacion, las URLs publicas de las integraciones
 * self-hosted configuradas a nivel de plataforma (drawio, Excalidraw, Etherpad, Jitsi propio).
 * Nunca expone credenciales (ej. la API key de Etherpad) — solo URLs base, que el frontend usa
 * para armar los <iframe> de cada pestaña colaborativa. Una URL vacia significa que esa
 * integracion no esta configurada en este despliegue.
 */
public class IntegrationConfigHandler extends BaseResourceHandler {

    private final String drawioBaseUrl;
    private final String etherpadBaseUrl;

    public IntegrationConfigHandler(final String drawioBaseUrl, final String etherpadBaseUrl) {
        this.drawioBaseUrl = drawioBaseUrl;
        this.etherpadBaseUrl = etherpadBaseUrl;
    }

    public record IntegrationConfigView(String drawioBaseUrl, String etherpadBaseUrl) {}

    @Override
    protected String basePath() {
        return "/api/v1/integrations";
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
        sendOk(jx, new IntegrationConfigView(drawioBaseUrl, etherpadBaseUrl));
        return true;
    }
}
