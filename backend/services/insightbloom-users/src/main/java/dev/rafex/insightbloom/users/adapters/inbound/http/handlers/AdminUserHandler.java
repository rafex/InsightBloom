package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.contracts.ApiMeta;
import dev.rafex.insightbloom.users.application.usecases.AdminUpdateUserUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListUsersUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetUserStatusUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Gestión de usuarios para administradores: listar, editar, banear/reactivar, eliminar lógicamente. */
public class AdminUserHandler extends BaseResourceHandler {

    private final ListUsersUseCase listUsersUseCase;
    private final AdminUpdateUserUseCase adminUpdateUserUseCase;
    private final SetUserStatusUseCase setUserStatusUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public AdminUserHandler(final ListUsersUseCase listUsersUseCase,
                             final AdminUpdateUserUseCase adminUpdateUserUseCase,
                             final SetUserStatusUseCase setUserStatusUseCase,
                             final ValidateTokenUseCase validateTokenUseCase) {
        this.listUsersUseCase = listUsersUseCase;
        this.adminUpdateUserUseCase = adminUpdateUserUseCase;
        this.setUserStatusUseCase = setUserStatusUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    public record UserView(String uuid, String username, String displayName, String email, String phone,
                            String role, String status, String firstName, String lastName,
                            boolean emailVerified, boolean phoneVerified, String createdAt) {}

    private static UserView toView(final User u) {
        return new UserView(u.getUuid(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.getPhone(),
                u.getRole().name(), u.getStatus().name(), u.getFirstName(), u.getLastName(),
                u.isEmailVerified(), u.isPhoneVerified(), u.getCreatedAt().toString());
    }

    @Override
    protected String basePath() {
        return "/api/v1/admin/users";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/", Set.of("GET")),
                Route.of("/{uuid}", Set.of("PUT")),
                Route.of("/{uuid}/ban", Set.of("POST")),
                Route.of("/{uuid}/unban", Set.of("POST")),
                Route.of("/{uuid}/delete", Set.of("POST")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "PUT", "POST");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        if (!requireAdmin(jx)) return true;
        try {
            final int page = parseIntParam(queryParam(jx, "page"), 1);
            final int pageSize = parseIntParam(queryParam(jx, "pageSize"), 50);
            final var result = listUsersUseCase.execute(page, pageSize);
            final List<UserView> items = result.items().stream().map(AdminUserHandler::toView).toList();
            sendOk(jx, 200, items, ApiMeta.paged(UUID.randomUUID().toString(), result.page(), result.pageSize(), result.total()));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    @Override
    public boolean put(final HttpExchange x) {
        final var jx = asJetty(x);
        if (!requireAdmin(jx)) return true;
        try {
            final var body = parseBody(jx);
            final var updated = adminUpdateUserUseCase.execute(jx.pathParam("uuid"), new AdminUpdateUserUseCase.Request(
                    (String) body.get("displayName"), (String) body.get("email"), (String) body.get("phone"),
                    (String) body.get("role"), (String) body.get("firstName"), (String) body.get("lastName")));
            sendOk(jx, toView(updated));
        } catch (final IllegalArgumentException e) {
            sendError(jx, "user_not_found".equals(e.getMessage()) ? 404 : 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        if (!requireAdmin(jx)) return true;
        final String path = jx.path();
        final String uuid = jx.pathParam("uuid");
        try {
            if (path.endsWith("/ban")) {
                sendOk(jx, toView(setUserStatusUseCase.execute(uuid, UserStatus.BANNED)));
                return true;
            }
            if (path.endsWith("/unban")) {
                sendOk(jx, toView(setUserStatusUseCase.execute(uuid, UserStatus.ACTIVE)));
                return true;
            }
            if (path.endsWith("/delete")) {
                sendOk(jx, toView(setUserStatusUseCase.execute(uuid, UserStatus.DELETED)));
                return true;
            }
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), e.getMessage());
            return true;
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
            return true;
        }
        sendError(jx, 404, "not_found", "Endpoint not found");
        return true;
    }

    private boolean requireAdmin(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return false; }
        final var v = validateTokenUseCase.execute(token);
        if (!v.valid() || !"admin".equals(v.role())) {
            sendError(jx, 403, "forbidden", "Only admins can manage users");
            return false;
        }
        return true;
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    private static int parseIntParam(final String value, final int defaultValue) {
        if (value == null) return defaultValue;
        try { return Integer.parseInt(value); } catch (final NumberFormatException e) { return defaultValue; }
    }
}
