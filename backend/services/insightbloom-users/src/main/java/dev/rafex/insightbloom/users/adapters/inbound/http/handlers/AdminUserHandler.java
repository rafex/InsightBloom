package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.contracts.ApiMeta;
import dev.rafex.insightbloom.users.application.usecases.AdminUpdateUserUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListUsersUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListUserReservationsUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetUserStatusUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
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
    private final dev.rafex.insightbloom.users.domain.ports.UserRepository userRepository;
    private final ListUserReservationsUseCase listUserReservationsUseCase;

    public AdminUserHandler(final ListUsersUseCase listUsersUseCase,
                             final AdminUpdateUserUseCase adminUpdateUserUseCase,
                             final SetUserStatusUseCase setUserStatusUseCase,
                             final ValidateTokenUseCase validateTokenUseCase,
                             final dev.rafex.insightbloom.users.domain.ports.UserRepository userRepository,
                             final ListUserReservationsUseCase listUserReservationsUseCase) {
        this.listUsersUseCase = listUsersUseCase;
        this.adminUpdateUserUseCase = adminUpdateUserUseCase;
        this.setUserStatusUseCase = setUserStatusUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
        this.userRepository = userRepository;
        this.listUserReservationsUseCase = listUserReservationsUseCase;
    }

    public record UserView(String uuid, String username, String displayName, String email, String phone,
                            String roles, String status, String firstName, String lastName,
                            boolean emailVerified, boolean phoneVerified, String createdAt, String lastLoginAt) {}

    private static UserView toView(final User u) {
        return new UserView(u.getUuid(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.getPhone(),
                UserRole.toCsv(u.getRoles()).toUpperCase(), u.getStatus().name(), u.getFirstName(), u.getLastName(),
                u.isEmailVerified(), u.isPhoneVerified(), u.getCreatedAt().toString(),
                u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : null);
    }

    @Override
    protected String basePath() {
        return "/api/v1/admin/users";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/", Set.of("GET")),
                Route.of("/{uuid}", Set.of("GET", "PUT")),
                Route.of("/{uuid}/reservations", Set.of("GET")),
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
        final String uuid = jx.pathParam("uuid");
        if (uuid != null) {
            if (jx.path().endsWith("/reservations")) {
                return handleGetReservations(jx, uuid);
            }
            return handleGetOne(jx, uuid);
        }
        try {
            final int page = parseIntParam(queryParam(jx, "page"), 1);
            final int pageSize = parseIntParam(queryParam(jx, "pageSize"), 50);
            final UserStatus status = parseEnumParam(UserStatus.class, queryParam(jx, "status"));
            final UserRole role = parseEnumParam(UserRole.class, queryParam(jx, "role"));
            final String sort = queryParam(jx, "sort");
            final var result = listUsersUseCase.execute(page, pageSize, status, role, sort);
            final List<UserView> items = result.items().stream().map(AdminUserHandler::toView).toList();
            sendOk(jx, 200, items, ApiMeta.paged(UUID.randomUUID().toString(), result.page(), result.pageSize(), result.total()));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetOne(final JettyHttpExchange jx, final String uuid) {
        final var user = userRepository.findByUuid(uuid);
        if (user.isPresent()) {
            sendOk(jx, toView(user.get()));
        } else {
            sendError(jx, 404, "not_found", "User not found");
        }
        return true;
    }

    private boolean handleGetReservations(final JettyHttpExchange jx, final String uuid) {
        try {
            sendOk(jx, 200, listUserReservationsUseCase.execute(uuid));
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
                    (String) body.get("roles"), (String) body.get("firstName"), (String) body.get("lastName")));
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
        if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
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

    private static <E extends Enum<E>> E parseEnumParam(final Class<E> enumType, final String value) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(enumType, value.toUpperCase()); } catch (final IllegalArgumentException e) { return null; }
    }
}
