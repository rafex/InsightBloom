package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.ChangePasswordUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetUserProfileUseCase;
import dev.rafex.insightbloom.users.application.usecases.UpdateProfileUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserProfileHandler extends BaseResourceHandler {

    private final GetUserProfileUseCase getUserProfileUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;

    public UserProfileHandler(final GetUserProfileUseCase getUserProfileUseCase,
                              final UpdateProfileUseCase updateProfileUseCase,
                              final ValidateTokenUseCase validateTokenUseCase,
                              final ChangePasswordUseCase changePasswordUseCase) {
        this.getUserProfileUseCase = getUserProfileUseCase;
        this.updateProfileUseCase = updateProfileUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
        this.changePasswordUseCase = changePasswordUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/users";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/{uuid}", Set.of("GET", "PUT")),
                Route.of("/{uuid}/password", Set.of("POST")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "PUT", "POST");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        final String uuid = jx.pathParam("uuid");
        try {
            if (!validInternalAuth(jx)) {
                final String token = extractToken(jx);
                if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
                final var v = validateTokenUseCase.execute(token);
                if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
                if (!v.subjectUuid().equals(uuid) && !isOrganizerOrAdmin(v.role())) {
                    sendError(jx, 403, "forbidden", "You can only view your own profile");
                    return true;
                }
            }
            final var profile = getUserProfileUseCase.execute(uuid);
            if (profile.isPresent()) {
                sendOk(jx, 200, profile.get());
            } else {
                sendError(jx, 404, "user_not_found", "User not found");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    @Override
    public boolean put(final HttpExchange x) {
        final var jx = asJetty(x);
        final String uuid = jx.pathParam("uuid");
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !v.subjectUuid().equals(uuid)) {
                sendError(jx, 403, "forbidden", "You can only edit your own profile");
                return true;
            }
            final var body = parseBody(jx);
            final var result = updateProfileUseCase.execute(uuid, new UpdateProfileUseCase.Request(
                    (String) body.get("firstName"), (String) body.get("lastName")));
            if (result.isPresent()) {
                sendOk(jx, 200, result.get());
            } else {
                sendError(jx, 404, "user_not_found", "User not found");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        final String uuid = jx.pathParam("uuid");
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !v.subjectUuid().equals(uuid)) {
                sendError(jx, 403, "forbidden", "You can only change your own password");
                return true;
            }
            final var body = parseBody(jx);
            final boolean changed = changePasswordUseCase.execute(uuid, new ChangePasswordUseCase.Request(
                    (String) body.get("currentPassword"), (String) body.get("newPassword")));
            if (changed) {
                sendOk(jx, 200, Map.of("status", "changed"));
            } else {
                sendError(jx, 400, "invalid_current_password", "La contraseña actual es incorrecta");
            }
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    private static boolean isOrganizerOrAdmin(final String role) {
        return legacyRoleHasAny(role, "organizer", "admin");
    }

}
