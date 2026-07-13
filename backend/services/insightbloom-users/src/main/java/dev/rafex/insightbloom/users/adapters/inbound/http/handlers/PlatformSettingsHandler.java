package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.GetChatAiSettingUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetChatAiSettingUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetChatSettingsUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kill switch de IA en el chat: GET es publico (el bot lo consulta sin autenticar, es solo un
 * booleano sin datos sensibles) para poder cortar rapido el uso de IA ante un intento de abuso
 * sin depender de un redeploy; PUT es admin-only (mismo guard que RoleHandler/EventTypeHandler).
 */
public class PlatformSettingsHandler extends BaseResourceHandler {

    private final GetChatAiSettingUseCase getChatAiSettingUseCase;
    private final SetChatAiSettingUseCase setChatAiSettingUseCase;
    private final SetChatSettingsUseCase setChatSettingsUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public PlatformSettingsHandler(final GetChatAiSettingUseCase getChatAiSettingUseCase,
                                    final SetChatAiSettingUseCase setChatAiSettingUseCase,
                                    final SetChatSettingsUseCase setChatSettingsUseCase,
                                    final ValidateTokenUseCase validateTokenUseCase) {
        this.getChatAiSettingUseCase = getChatAiSettingUseCase;
        this.setChatAiSettingUseCase = setChatAiSettingUseCase;
        this.setChatSettingsUseCase = setChatSettingsUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/settings";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/chat-ai", Set.of("GET", "PUT")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "PUT");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        try {
            final var settings = getChatAiSettingUseCase.execute();
            sendOk(jx, toView(settings));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    @Override
    public boolean put(final HttpExchange x) {
        final var jx = asJetty(x);
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || v.role() == null || !v.role().contains("admin")) {
                sendError(jx, 403, "forbidden", "Only admins can change platform settings");
                return true;
            }
            final var body = parseBody(jx);
            final boolean chatAiEnabled = !Boolean.FALSE.equals(body.get("chatAiEnabled"));
            setChatAiSettingUseCase.execute(chatAiEnabled);
            final String chatSystemPrompt = (String) body.get("chatSystemPrompt");
            final Double chatTemperature = body.get("chatTemperature") instanceof Number n ? n.doubleValue() : null;
            final var settings = setChatSettingsUseCase.execute(chatSystemPrompt, chatTemperature);
            sendOk(jx, toView(settings));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private static Map<String, Object> toView(final dev.rafex.insightbloom.users.domain.model.PlatformSettings s) {
        final Map<String, Object> view = new java.util.HashMap<>();
        view.put("chatAiEnabled", s.isChatAiEnabled());
        view.put("chatSystemPrompt", s.getChatSystemPrompt());
        view.put("chatTemperature", s.getChatTemperature());
        return view;
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }
}
