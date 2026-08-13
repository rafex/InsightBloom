package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.CleanupExpiredAuditLogsUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.domain.ports.AuditLogQueryPort;
import dev.rafex.insightbloom.users.domain.ports.AuditLogQueryPort.AuditLogEntry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Endpoints para consulta de logs de auditoría:
 * - GET /users/me/audit-logs — ver qué acciones realizó el usuario (GDPR access)
 * - GET /admin/audit-logs — ver todos los logs (ADMIN only)
 * - GET /audit-logs/{resourceType}/{resourceId} — ver logs para un recurso específico
 * - POST /admin/audit-logs/cleanup — ejecutar cleanup manual (forzar purga de logs antiguos)
 */
public class AuditLogHandler extends BaseResourceHandler {
  private final AuditLogQueryPort auditLogQueryPort;
  private final CleanupExpiredAuditLogsUseCase cleanupUseCase;
  private final ValidateTokenUseCase validateTokenUseCase;

  public AuditLogHandler(final AuditLogQueryPort auditLogQueryPort,
                        final CleanupExpiredAuditLogsUseCase cleanupUseCase,
                        final ValidateTokenUseCase validateTokenUseCase) {
    this.auditLogQueryPort = auditLogQueryPort;
    this.cleanupUseCase = cleanupUseCase;
    this.validateTokenUseCase = validateTokenUseCase;
  }

  @Override protected String basePath() { return "/api/v1"; }

  @Override protected List<Route> routes() {
    return List.of(
        Route.of("/users/me/audit-logs", Set.of("GET")),
        Route.of("/admin/audit-logs", Set.of("GET", "POST")),
        Route.of("/audit-logs/{resourceType}/{resourceId}", Set.of("GET")));
  }

  @Override public Set<String> supportedMethods() { return Set.of("GET", "POST"); }

  @Override public boolean get(final HttpExchange x) {
    final var jx = asJetty(x);
    final String path = jx.path();

    if (path.contains("/users/me/audit-logs")) {
      return getMyAuditLogs(jx);
    } else if (path.contains("/admin/audit-logs")) {
      return getAllAuditLogs(jx);
    } else if (path.matches(".*/(audit-logs)/([^/]+)/([^/]+)")) {
      return getResourceAuditLogs(jx);
    }
    return false;
  }

  @Override public boolean post(final HttpExchange x) {
    final var jx = asJetty(x);
    final String path = jx.path();

    if (path.contains("/admin/audit-logs/cleanup")) {
      return cleanupAuditLogs(jx);
    }
    return false;
  }

  /** GET /users/me/audit-logs — ver acciones realizadas por el usuario autenticado (GDPR access right). */
  private boolean getMyAuditLogs(final JettyHttpExchange jx) {
    final var auth = requireUser(jx);
    if (auth == null) return true;

    try {
      final int limit = 20;  // Default limit, could be parameterized
      final int offset = 0;  // Default offset, could be parameterized

      final List<AuditLogEntry> logs = auditLogQueryPort.findByActor(auth.subjectUuid(), limit, offset);
      sendOk(jx, Map.of(
          "items", logs,
          "total", logs.size()
      ));
    } catch (final Exception e) {
      sendError(jx, 500, "internal_error", "Internal server error");
    }
    return true;
  }

  /** GET /admin/audit-logs — ver todos los logs de auditoría (ADMIN only). */
  private boolean getAllAuditLogs(final JettyHttpExchange jx) {
    final var auth = requireUser(jx);
    if (auth == null) return true;

    // TODO: Validar que usuario tiene rol ADMIN
    try {
      final int limit = 20;  // Default limit
      final int offset = 0;  // Default offset

      final List<AuditLogEntry> logs = auditLogQueryPort.findAll(limit, offset);
      sendOk(jx, Map.of(
          "items", logs,
          "total", logs.size()
      ));
    } catch (final Exception e) {
      sendError(jx, 500, "internal_error", "Internal server error");
    }
    return true;
  }

  /** GET /audit-logs/{resourceType}/{resourceId} — ver logs para un recurso específico. */
  private boolean getResourceAuditLogs(final JettyHttpExchange jx) {
    final var auth = requireUser(jx);
    if (auth == null) return true;

    try {
      final String resourceType = jx.pathParam("resourceType");
      final String resourceId = jx.pathParam("resourceId");

      if (resourceType == null || resourceId == null) {
        sendError(jx, 400, "bad_request", "Invalid path format");
        return true;
      }

      final int limit = 20;  // Default limit
      final int offset = 0;  // Default offset

      final List<AuditLogEntry> logs = auditLogQueryPort.findByResource(resourceType, resourceId, limit, offset);
      sendOk(jx, Map.of(
          "items", logs,
          "total", logs.size()
      ));
    } catch (final Exception e) {
      sendError(jx, 500, "internal_error", "Internal server error");
    }
    return true;
  }

  /** POST /admin/audit-logs/cleanup — ejecutar limpieza manual (purgar logs > 1 año). */
  private boolean cleanupAuditLogs(final JettyHttpExchange jx) {
    final var auth = requireUser(jx);
    if (auth == null) return true;

    // TODO: Validar que usuario tiene rol ADMIN
    try {
      final int deleted = cleanupUseCase.execute(Instant.now());
      sendOk(jx, Map.of("deleted", deleted));
    } catch (final Exception e) {
      sendError(jx, 500, "cleanup_failed", "Failed to cleanup audit logs");
    }
    return true;
  }

  private ValidateTokenUseCase.ValidationResult requireUser(final JettyHttpExchange jx) {
    final String auth = jx.request().getHeaders().get("Authorization");
    final String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
    if (token == null) {
      sendError(jx, 401, "token_missing", "Authorization required");
      return null;
    }
    final var validation = validateTokenUseCase.execute(token);
    if (!validation.valid() || !"user".equals(validation.kind())) {
      sendError(jx, 401, "token_invalid", "Invalid token");
      return null;
    }
    return validation;
  }

}
