package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.GetChatAiSettingUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListDeviceFingerprintFlagsUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListPlatformDeviceBlocksUseCase;
import dev.rafex.insightbloom.users.application.usecases.ReviewDeviceFingerprintFlagUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetChatAiSettingUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetChatSettingsUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetAiSettingsUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetDeviceAccessSettingsUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetGlobalEgressPolicyUseCase;
import dev.rafex.insightbloom.users.application.usecases.UnblockPlatformDeviceUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.domain.model.DeviceFingerprintFlag;
import dev.rafex.insightbloom.users.domain.model.PlatformDeviceBlock;
import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.model.AiProviderSettings;
import dev.rafex.insightbloom.users.domain.services.AiPromptCatalog;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuracion de plataforma, admin-only salvo donde se indique lo contrario:
 * - /chat-ai/public: kill switch de IA en el chat (GET publico, solo devuelve un booleano).
 * - /chat-ai: configuración completa admin-only.
 * - /device-access: umbrales de PlatformDeviceGuard (GET/PUT admin-only).
 * - /device-blocks: cola de revision de dispositivos bloqueados a nivel plataforma (GET/POST
 *   admin-only) -- ver PlatformDeviceGuard.
 */
public class PlatformSettingsHandler extends BaseResourceHandler {

    private final GetChatAiSettingUseCase getChatAiSettingUseCase;
    private final SetChatAiSettingUseCase setChatAiSettingUseCase;
    private final SetChatSettingsUseCase setChatSettingsUseCase;
    private final SetAiSettingsUseCase setAiSettingsUseCase;
    private final SetDeviceAccessSettingsUseCase setDeviceAccessSettingsUseCase;
    private final SetGlobalEgressPolicyUseCase setGlobalEgressPolicyUseCase;
    private final ListPlatformDeviceBlocksUseCase listPlatformDeviceBlocksUseCase;
    private final UnblockPlatformDeviceUseCase unblockPlatformDeviceUseCase;
    private final ListDeviceFingerprintFlagsUseCase listDeviceFingerprintFlagsUseCase;
    private final ReviewDeviceFingerprintFlagUseCase reviewDeviceFingerprintFlagUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public PlatformSettingsHandler(final GetChatAiSettingUseCase getChatAiSettingUseCase,
                                    final SetChatAiSettingUseCase setChatAiSettingUseCase,
                                    final SetChatSettingsUseCase setChatSettingsUseCase,
                                    final SetAiSettingsUseCase setAiSettingsUseCase,
                                    final SetDeviceAccessSettingsUseCase setDeviceAccessSettingsUseCase,
                                    final SetGlobalEgressPolicyUseCase setGlobalEgressPolicyUseCase,
                                    final ListPlatformDeviceBlocksUseCase listPlatformDeviceBlocksUseCase,
                                    final UnblockPlatformDeviceUseCase unblockPlatformDeviceUseCase,
                                    final ListDeviceFingerprintFlagsUseCase listDeviceFingerprintFlagsUseCase,
                                    final ReviewDeviceFingerprintFlagUseCase reviewDeviceFingerprintFlagUseCase,
                                    final ValidateTokenUseCase validateTokenUseCase) {
        this.getChatAiSettingUseCase = getChatAiSettingUseCase;
        this.setChatAiSettingUseCase = setChatAiSettingUseCase;
        this.setChatSettingsUseCase = setChatSettingsUseCase;
        this.setAiSettingsUseCase = setAiSettingsUseCase;
        this.setDeviceAccessSettingsUseCase = setDeviceAccessSettingsUseCase;
        this.setGlobalEgressPolicyUseCase = setGlobalEgressPolicyUseCase;
        this.listPlatformDeviceBlocksUseCase = listPlatformDeviceBlocksUseCase;
        this.unblockPlatformDeviceUseCase = unblockPlatformDeviceUseCase;
        this.listDeviceFingerprintFlagsUseCase = listDeviceFingerprintFlagsUseCase;
        this.reviewDeviceFingerprintFlagUseCase = reviewDeviceFingerprintFlagUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/settings";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/chat-ai", Set.of("GET", "PUT")),
                Route.of("/chat-ai/public", Set.of("GET")),
                Route.of("/ai", Set.of("GET", "PUT")),
                Route.of("/ai/catalog", Set.of("GET")),
                // "/ai/internal" DEBE ir antes que la plantilla "/ai/{capability}": RouteMatcher
                // devuelve la primera ruta cuyo PATH matchea (sin mirar el metodo todavia), y
                // "{capability}" matchea cualquier segmento literal incluido "internal" -- con el
                // orden invertido, todo GET /ai/internal caia en la ruta PUT-only de {capability}
                // y devolvia 405 antes de llegar aca (confirmado en vivo: GroqLlmClient trataba
                // ese 405 como "ai_settings_unavailable" y el survey terminaba en 503).
                Route.of("/ai/internal", Set.of("GET")),
                Route.of("/ai/{capability}", Set.of("PUT")),
                Route.of("/device-access", Set.of("GET", "PUT")),
                Route.of("/egress-policy", Set.of("GET", "PUT")),
                Route.of("/device-blocks", Set.of("GET")),
                Route.of("/device-blocks/{blockId}/unblock", Set.of("POST")),
                Route.of("/device-fingerprint-flags", Set.of("GET")),
                Route.of("/device-fingerprint-flags/{flagId}/review", Set.of("POST")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "PUT", "POST");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        if (pathEndsWith(jx, "/ai/internal")) {
            if (!validInternalAuth(jx)) {
                sendError(jx, 401, "internal_auth_required", "Internal authentication required");
            } else {
                sendOk(jx, toInternalView(getChatAiSettingUseCase.execute()));
            }
            return true;
        }
        final String path = jx.path();
        if (path.endsWith("/ai/catalog")) return handleAiCatalog(jx);
        if (path.endsWith("/chat-ai/public")) {
            try {
                sendOk(jx, Map.of("chatAiEnabled", getChatAiSettingUseCase.execute().isChatAiEnabled()));
            } catch (final Exception e) {
                sendError(jx, 500, "internal_error", "Could not load chat setting");
            }
            return true;
        }
        if (path.endsWith("/device-access")) return handleGetDeviceAccess(jx);
        if (path.endsWith("/egress-policy")) return handleGetEgressPolicy(jx);
        if (path.endsWith("/device-blocks")) return handleListDeviceBlocks(jx);
        if (path.endsWith("/device-fingerprint-flags")) return handleListDeviceFingerprintFlags(jx);
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
                sendError(jx, 403, "forbidden", "Only admins can view chat settings");
                return true;
            }
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
        if (jx.path().endsWith("/device-access")) return handleSetDeviceAccess(jx);
        if (jx.path().endsWith("/egress-policy")) return handleSetEgressPolicy(jx);
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
                sendError(jx, 403, "forbidden", "Only admins can change platform settings");
                return true;
            }
            final var body = parseBody(jx);
            if (jx.path().endsWith("/ai")) {
                sendOk(jx, toView(setAiSettingsUseCase.execute("chat", providerUpdate(body, "chatAiEnabled",
                        "aiBaseUrl", "aiModel", "aiApiKey", "clearApiKey", "chatSystemPrompt", "chatGuardrails",
                        "chatTemperature"))));
                return true;
            }
            if (jx.path().contains("/ai/")) {
                final String capability = jx.path().substring(jx.path().lastIndexOf("/ai/") + 4);
                sendOk(jx, toView(setAiSettingsUseCase.execute(capability, providerUpdate(body,
                        "enabled", "baseUrl", "model", "apiKey", "clearApiKey", "systemPrompt", "guardrails",
                        "temperature"))));
                return true;
            }
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

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/unblock")) return handleUnblockDevice(jx, jx.pathParam("blockId"));
        if (jx.path().endsWith("/review")) return handleReviewDeviceFingerprintFlag(jx, jx.pathParam("flagId"));
        sendError(jx, 404, "not_found", "Endpoint not found");
        return true;
    }

    private boolean handleAiCatalog(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
                sendError(jx, 403, "forbidden", "Only admins can view the AI prompt catalog");
                return true;
            }
            sendOk(jx, Map.of("variables", AiPromptCatalog.variables().stream()
                    .map(item -> Map.of("key", item.key(), "label", item.label(), "example", item.example(),
                            "autoIncludedIn", item.autoIncludedIn()))
                    .toList()));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetDeviceAccess(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
                sendError(jx, 403, "forbidden", "Only admins can view device access settings");
                return true;
            }
            sendOk(jx, toDeviceAccessView(getChatAiSettingUseCase.execute()));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleSetDeviceAccess(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
                sendError(jx, 403, "forbidden", "Only admins can change device access settings");
                return true;
            }
            final var body = parseBody(jx);
            final Integer maxAccountsPerDevice = (Integer) body.get("maxAccountsPerDevice");
            final Integer maxSessionsPerUser = (Integer) body.get("maxSessionsPerUser");
            final Integer maxRegistrationsPerDevicePerDay = (Integer) body.get("maxRegistrationsPerDevicePerDay");
            final var settings = setDeviceAccessSettingsUseCase.execute(
                    maxAccountsPerDevice, maxSessionsPerUser, maxRegistrationsPerDevicePerDay);
            sendOk(jx, toDeviceAccessView(settings));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetEgressPolicy(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
                sendError(jx, 403, "forbidden", "Only admins can view the global egress policy");
                return true;
            }
            sendOk(jx, toEgressPolicyView(getChatAiSettingUseCase.execute()));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleSetEgressPolicy(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
                sendError(jx, 403, "forbidden", "Only admins can change the global egress policy");
                return true;
            }
            final var body = parseBody(jx);
            final String allowedHosts = (String) body.get("allowedHosts");
            final String blockedHosts = (String) body.get("blockedHosts");
            final var settings = setGlobalEgressPolicyUseCase.execute(allowedHosts, blockedHosts);
            sendOk(jx, toEgressPolicyView(settings));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private static Map<String, Object> toEgressPolicyView(final PlatformSettings s) {
        final Map<String, Object> view = new java.util.HashMap<>();
        view.put("allowedHosts", s.getEgressAllowedHosts());
        view.put("blockedHosts", s.getEgressBlockedHosts());
        return view;
    }

    private boolean handleListDeviceBlocks(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
                sendError(jx, 403, "forbidden", "Only admins can view platform device blocks");
                return true;
            }
            final List<PlatformDeviceBlock> blocks = listPlatformDeviceBlocksUseCase.execute();
            sendOk(jx, blocks.stream().map(PlatformSettingsHandler::toBlockView).toList());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleUnblockDevice(final JettyHttpExchange jx, final String blockId) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
                sendError(jx, 403, "forbidden", "Only admins can unblock devices");
                return true;
            }
            unblockPlatformDeviceUseCase.execute(blockId, v.subjectUuid());
            sendOk(jx, Map.of("unblocked", true));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleListDeviceFingerprintFlags(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
                sendError(jx, 403, "forbidden", "Only admins can view device fingerprint flags");
                return true;
            }
            final List<DeviceFingerprintFlag> flags = listDeviceFingerprintFlagsUseCase.execute();
            sendOk(jx, flags.stream().map(PlatformSettingsHandler::toFlagView).toList());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleReviewDeviceFingerprintFlag(final JettyHttpExchange jx, final String flagId) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
                sendError(jx, 403, "forbidden", "Only admins can review device fingerprint flags");
                return true;
            }
            reviewDeviceFingerprintFlagUseCase.execute(flagId, v.subjectUuid());
            sendOk(jx, Map.of("reviewed", true));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private static Map<String, Object> toView(final PlatformSettings s) {
        final Map<String, Object> view = new java.util.HashMap<>();
        view.put("providers", Map.of(
                "chat", providerView(s.getChatAi()),
                "tutor", providerView(s.getTutorAi()),
                "survey", providerView(s.getSurveyAi()),
                "seatLayout", providerView(s.getSeatLayoutAi()),
                "email", providerView(s.getEmailAi())));
        // Campos legacy para clientes antiguos; el dashboard nuevo usa providers.
        view.put("chatAiEnabled", s.getChatAi().isEnabled());
        view.put("chatSystemPrompt", s.getChatAi().getSystemPrompt());
        view.put("chatGuardrails", s.getChatAi().getGuardrails());
        view.put("chatTemperature", s.getChatAi().getTemperature());
        view.put("aiBaseUrl", s.getChatAi().getBaseUrl());
        view.put("aiModel", s.getChatAi().getModel());
        view.put("aiApiKeyConfigured", hasKey(s.getChatAi()));
        view.put("aiApiKeyHint", keyHint(s.getChatAi().getApiKey()));
        return view;
    }

    private static Map<String, Object> toInternalView(final PlatformSettings s) {
        final Map<String, Object> view = new java.util.HashMap<>();
        view.put("providers", Map.of(
                "chat", internalProviderView(s.getChatAi()),
                "tutor", internalProviderView(s.getTutorAi()),
                "survey", internalProviderView(s.getSurveyAi()),
                "seatLayout", internalProviderView(s.getSeatLayoutAi()),
                "email", internalProviderView(s.getEmailAi())));
        return view;
    }

    private static SetAiSettingsUseCase.ProviderUpdate providerUpdate(final Map<String, Object> body,
                                                                        final String enabledKey,
                                                                        final String baseUrlKey,
                                                                        final String modelKey,
                                                                        final String apiKeyKey,
                                                                        final String clearKey,
                                                                        final String promptKey,
                                                                        final String guardrailsKey,
                                                                        final String temperatureKey) {
        final boolean enabled = !Boolean.FALSE.equals(body.get(enabledKey));
        final String baseUrl = body.get(baseUrlKey) instanceof String value ? value : null;
        final String model = body.get(modelKey) instanceof String value ? value : null;
        final String apiKey = body.get(apiKeyKey) instanceof String value ? value : null;
        final String prompt = body.get(promptKey) instanceof String value ? value : null;
        final String guardrails = body.get(guardrailsKey) instanceof String value ? value : null;
        final Double temperature = body.get(temperatureKey) instanceof Number n ? n.doubleValue() : null;
        return new SetAiSettingsUseCase.ProviderUpdate(enabled, baseUrl, model, apiKey,
                Boolean.TRUE.equals(body.get(clearKey)), prompt, guardrails, temperature);
    }

    private static Map<String, Object> providerView(final AiProviderSettings p) {
        final Map<String, Object> view = new java.util.HashMap<>();
        view.put("configured", p.isConfigured());
        view.put("enabled", p.isEnabled());
        view.put("baseUrl", p.getBaseUrl());
        view.put("model", p.getModel());
        view.put("systemPrompt", p.getSystemPrompt());
        view.put("guardrails", p.getGuardrails());
        view.put("temperature", p.getTemperature());
        // An unconfigured profile may temporarily inherit the chat provider internally,
        // but that fallback must not look like an explicitly configured credential in
        // the administrative UI.
        view.put("apiKeyConfigured", p.isConfigured() && hasKey(p));
        view.put("apiKeyHint", p.isConfigured() ? keyHint(p.getApiKey()) : null);
        return view;
    }

    private static Map<String, Object> internalProviderView(final AiProviderSettings p) {
        final Map<String, Object> view = new java.util.HashMap<>(providerView(p));
        view.put("apiKey", p.getApiKey());
        return view;
    }

    private static boolean hasKey(final AiProviderSettings p) {
        return p.getApiKey() != null && !p.getApiKey().isBlank();
    }

    private static String keyHint(final String key) {
        return key == null || key.isBlank() ? null : "••••" + key.substring(Math.max(0, key.length() - 4));
    }

    private static boolean pathEndsWith(final JettyHttpExchange jx, final String suffix) {
        return jx.path().endsWith(suffix);
    }

    private static Map<String, Object> toDeviceAccessView(final PlatformSettings s) {
        final Map<String, Object> view = new java.util.HashMap<>();
        view.put("maxAccountsPerDevice", s.getMaxAccountsPerDevice());
        view.put("maxSessionsPerUser", s.getMaxSessionsPerUser());
        view.put("maxRegistrationsPerDevicePerDay", s.getMaxRegistrationsPerDevicePerDay());
        return view;
    }

    private static Map<String, Object> toBlockView(final PlatformDeviceBlock b) {
        final Map<String, Object> view = new java.util.HashMap<>();
        view.put("uuid", b.getUuid());
        view.put("deviceFingerprint", b.getDeviceFingerprint());
        view.put("reason", b.getReason().name());
        view.put("relatedCount", b.getRelatedCount());
        view.put("blockedAt", b.getBlockedAt().toString());
        view.put("unblockedAt", b.getUnblockedAt() != null ? b.getUnblockedAt().toString() : null);
        view.put("unblockedBy", b.getUnblockedBy());
        return view;
    }

    private static Map<String, Object> toFlagView(final DeviceFingerprintFlag f) {
        final Map<String, Object> view = new java.util.HashMap<>();
        view.put("uuid", f.getUuid());
        view.put("subjectUuid", f.getSubjectUuid());
        view.put("subjectKind", f.getSubjectKind());
        view.put("loginFingerprint", f.getLoginFingerprint());
        view.put("lastSeenFingerprint", f.getLastSeenFingerprint());
        view.put("occurrenceCount", f.getOccurrenceCount());
        view.put("firstSeenAt", f.getFirstSeenAt().toString());
        view.put("lastSeenAt", f.getLastSeenAt().toString());
        view.put("reviewedAt", f.getReviewedAt() != null ? f.getReviewedAt().toString() : null);
        view.put("reviewedBy", f.getReviewedBy());
        return view;
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }
}
