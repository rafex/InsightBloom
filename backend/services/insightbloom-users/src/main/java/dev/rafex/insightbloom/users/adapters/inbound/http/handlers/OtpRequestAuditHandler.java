package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.domain.ports.OtpRequestAuditPort;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Admin-only query endpoint for internal OTP request investigations. */
public class OtpRequestAuditHandler extends BaseResourceHandler {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final OtpRequestAuditPort auditPort;
    private final ValidateTokenUseCase validateTokenUseCase;

    public OtpRequestAuditHandler(final OtpRequestAuditPort auditPort,
                                  final ValidateTokenUseCase validateTokenUseCase) {
        this.auditPort = auditPort;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    @Override protected String basePath() { return "/api/v1"; }
    @Override protected List<Route> routes() {
        return List.of(Route.of("/admin/auth/otp-audit", Set.of("GET")));
    }
    @Override public Set<String> supportedMethods() { return Set.of("GET"); }

    @Override public boolean get(final HttpExchange exchange) {
        final JettyHttpExchange jx = asJetty(exchange);
        if (!jx.path().endsWith("/admin/auth/otp-audit")) return false;
        final var auth = requireAdmin(jx);
        if (auth == null) return true;
        try {
            final int limit = boundedInt(jx.queryFirst("limit"), DEFAULT_LIMIT, 1, MAX_LIMIT);
            final int offset = boundedInt(jx.queryFirst("offset"), 0, 0, 1_000_000);
            final Instant requestedFrom = parseInstant(jx.queryFirst("from"));
            final Instant retentionFloor = Instant.now().minus(java.time.Duration.ofDays(30));
            final Instant from = requestedFrom == null || requestedFrom.isBefore(retentionFloor)
                    ? retentionFloor : requestedFrom;
            final Instant to = parseInstant(jx.queryFirst("to"));
            if (from != null && to != null && from.isAfter(to)) {
                sendError(jx, 400, "invalid_time_range", "from must be before to");
                return true;
            }
            final var filter = new OtpRequestAuditPort.Filter(from, to,
                    boundedString(jx.queryFirst("accountUuid"), 64),
                    boundedString(jx.queryFirst("clientIp"), 64),
                    boundedString(jx.queryFirst("outcome"), 40));
            final var page = auditPort.find(filter, limit, offset);
            sendOk(jx, Map.of("items", page.items(), "total", page.total(), "limit", limit, "offset", offset));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, "invalid_query", "Invalid audit query parameters");
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal server error");
        }
        return true;
    }

    private ValidateTokenUseCase.ValidationResult requireAdmin(final JettyHttpExchange jx) {
        final String authorization = jx.request().getHeaders().get("Authorization");
        final String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return null;
        }
        final var validation = validateTokenUseCase.execute(token);
        if (!validation.valid() || !"user".equals(validation.kind())) {
            sendError(jx, 401, "token_invalid", "Invalid token");
            return null;
        }
        final boolean admin = hasAdminRole(validation.role());
        if (!admin) {
            sendError(jx, 403, "admin_required", "Administrator role required");
            return null;
        }
        return validation;
    }

    static boolean hasAdminRole(final String rolesCsv) {
        return rolesCsv != null && java.util.Arrays.stream(rolesCsv.split(","))
                .map(String::trim).anyMatch("admin"::equalsIgnoreCase);
    }

    private static Instant parseInstant(final String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); }
        catch (final DateTimeParseException e) { throw new IllegalArgumentException("invalid_instant", e); }
    }

    private static int boundedInt(final String value, final int fallback, final int min, final int max) {
        if (value == null || value.isBlank()) return fallback;
        try {
            final int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) throw new IllegalArgumentException("out_of_range");
            return parsed;
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("invalid_integer", e);
        }
    }

    private static String boundedString(final String value, final int max) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > max) throw new IllegalArgumentException("query_too_long");
        return value.trim();
    }
}
