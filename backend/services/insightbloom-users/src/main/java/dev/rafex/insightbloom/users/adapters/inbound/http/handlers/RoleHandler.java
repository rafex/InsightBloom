package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.CreateRoleUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListRolesUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetRoleActiveUseCase;
import dev.rafex.insightbloom.users.application.usecases.UpdateRoleUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.model.Role;
import dev.rafex.insightbloom.users.domain.model.RoleScope;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catálogo de roles administrables (DEC-0021): lectura pública/autenticada del subconjunto
 * activo, filtrable por alcance (para el selector del Host al asignar roles de evento), y
 * administración completa (CRUD, activar/desactivar) restringida a `MANAGE_USERS` (hoy: rol
 * admin, ver NFR-002).
 */
public class RoleHandler extends BaseResourceHandler {

    private final ListRolesUseCase listRolesUseCase;
    private final CreateRoleUseCase createRoleUseCase;
    private final UpdateRoleUseCase updateRoleUseCase;
    private final SetRoleActiveUseCase setRoleActiveUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public RoleHandler(final ListRolesUseCase listRolesUseCase,
                        final CreateRoleUseCase createRoleUseCase,
                        final UpdateRoleUseCase updateRoleUseCase,
                        final SetRoleActiveUseCase setRoleActiveUseCase,
                        final ValidateTokenUseCase validateTokenUseCase) {
        this.listRolesUseCase = listRolesUseCase;
        this.createRoleUseCase = createRoleUseCase;
        this.updateRoleUseCase = updateRoleUseCase;
        this.setRoleActiveUseCase = setRoleActiveUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    public record RoleView(String uuid, String key, String name, String description, String scope,
                            List<String> permissions, boolean active) {}

    private static RoleView toView(final Role r) {
        return new RoleView(r.getUuid(), r.getKey(), r.getName(), r.getDescription(), r.getScope().name(),
                r.getPermissions().stream().map(Enum::name).sorted().toList(), r.isActive());
    }

    @Override
    protected String basePath() {
        return "/api/v1/roles";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/", Set.of("GET", "POST")),
                Route.of("/all", Set.of("GET")),
                Route.of("/{uuid}", Set.of("PUT")),
                Route.of("/{uuid}/active", Set.of("PUT")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST", "PUT");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        try {
            final boolean allRequested = jx.path().endsWith("/all");
            if (allRequested && !requireManageUsers(jx)) return true;
            final RoleScope scope = parseScope(queryParam(jx, "scope"));
            final List<RoleView> items = listRolesUseCase.execute(!allRequested, scope).stream()
                    .map(RoleHandler::toView).toList();
            sendOk(jx, items);
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal server error");
        }
        return true;
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        if (!requireManageUsers(jx)) return true;
        try {
            final var body = parseBody(jx);
            final Role created = createRoleUseCase.execute(
                    (String) body.get("key"), (String) body.get("name"), (String) body.get("description"),
                    parseScope((String) body.get("scope")), parsePermissions(body.get("permissions")));
            sendOk(jx, 201, toView(created));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal server error");
        }
        return true;
    }

    @Override
    public boolean put(final HttpExchange x) {
        final var jx = asJetty(x);
        if (!requireManageUsers(jx)) return true;
        final String uuid = jx.pathParam("uuid");
        try {
            if (jx.path().endsWith("/active")) {
                final var body = parseBody(jx);
                final boolean active = Boolean.TRUE.equals(body.get("active"));
                sendOk(jx, toView(setRoleActiveUseCase.execute(uuid, active)));
                return true;
            }
            final var body = parseBody(jx);
            final Role updated = updateRoleUseCase.execute(
                    uuid, (String) body.get("name"), (String) body.get("description"),
                    parsePermissions(body.get("permissions")));
            sendOk(jx, toView(updated));
        } catch (final IllegalArgumentException e) {
            sendError(jx, "role_not_found".equals(e.getMessage()) ? 404 : 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal server error");
        }
        return true;
    }

    private static RoleScope parseScope(final String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return RoleScope.valueOf(raw); } catch (final IllegalArgumentException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static Set<Permission> parsePermissions(final Object raw) {
        if (!(raw instanceof List<?> list)) return Set.of();
        return ((List<String>) list).stream().map(Permission::valueOf).collect(Collectors.toSet());
    }

    private boolean requireManageUsers(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return false; }
        final var v = validateTokenUseCase.execute(token);
        if (!v.valid() || !legacyRoleHasAny(v.role(), "admin")) {
            sendError(jx, 403, "forbidden", "Only admins can manage roles");
            return false;
        }
        return true;
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }
}
