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
import dev.rafex.insightbloom.users.application.usecases.SetDeviceAccessSettingsUseCase;
import dev.rafex.insightbloom.users.application.usecases.UnblockPlatformDeviceUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.domain.model.DeviceFingerprintFlag;
import dev.rafex.insightbloom.users.domain.model.PlatformDeviceBlock;
import dev.rafex.insightbloom.users.domain.model.PlatformSettings;

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
    private final SetDeviceAccessSettingsUseCase setDeviceAccessSettingsUseCase;
    private final ListPlatformDeviceBlocksUseCase listPlatformDeviceBlocksUseCase;
    private final UnblockPlatformDeviceUseCase unblockPlatformDeviceUseCase;
    private final ListDeviceFingerprintFlagsUseCase listDeviceFingerprintFlagsUseCase;
    private final ReviewDeviceFingerprintFlagUseCase reviewDeviceFingerprintFlagUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public PlatformSettingsHandler(final GetChatAiSettingUseCase getChatAiSettingUseCase,
                                    final SetChatAiSettingUseCase setChatAiSettingUseCase,
                                    final SetChatSettingsUseCase setChatSettingsUseCase,
                                    final SetDeviceAccessSettingsUseCase setDeviceAccessSettingsUseCase,
                                    final ListPlatformDeviceBlocksUseCase listPlatformDeviceBlocksUseCase,
                                    final UnblockPlatformDeviceUseCase unblockPlatformDeviceUseCase,
                                    final ListDeviceFingerprintFlagsUseCase listDeviceFingerprintFlagsUseCase,
                                    final ReviewDeviceFingerprintFlagUseCase reviewDeviceFingerprintFlagUseCase,
                                    final ValidateTokenUseCase validateTokenUseCase) {
        this.getChatAiSettingUseCase = getChatAiSettingUseCase;
        this.setChatAiSettingUseCase = setChatAiSettingUseCase;
        this.setChatSettingsUseCase = setChatSettingsUseCase;
        this.setDeviceAccessSettingsUseCase = setDeviceAccessSettingsUseCase;
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
                Route.of("/device-access", Set.of("GET", "PUT")),
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
        final String path = jx.path();
        if (path.endsWith("/chat-ai/public")) {
            try {
                sendOk(jx, Map.of("chatAiEnabled", getChatAiSettingUseCase.execute().isChatAiEnabled()));
            } catch (final Exception e) {
                sendError(jx, 500, "internal_error", "Could not load chat setting");
            }
            return true;
        }
        if (path.endsWith("/device-access")) return handleGetDeviceAccess(jx);
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
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
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

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/unblock")) return handleUnblockDevice(jx, jx.pathParam("blockId"));
        if (jx.path().endsWith("/review")) return handleReviewDeviceFingerprintFlag(jx, jx.pathParam("flagId"));
        sendError(jx, 404, "not_found", "Endpoint not found");
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
        view.put("chatAiEnabled", s.isChatAiEnabled());
        view.put("chatSystemPrompt", s.getChatSystemPrompt());
        view.put("chatTemperature", s.getChatTemperature());
        return view;
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
