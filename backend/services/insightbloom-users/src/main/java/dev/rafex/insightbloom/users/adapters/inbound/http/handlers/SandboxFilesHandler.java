package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.GetConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListWorkspaceFilesUseCase;
import dev.rafex.insightbloom.users.application.usecases.ReadWorkspaceFileUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.application.usecases.WriteWorkspaceFileUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dashboard de moderador (Fase 4, DEC-0025 area): visor/editor de archivos del workspace de UN
 * alumno -- separado de {@link SandboxHandler} (que mezcla rutas del propio alumno con las de
 * organizador) para no seguir cargando ese archivo. Todas las rutas son organizer-only, mismo
 * guard inline {@code isOrganizerOrAdmin} que ya usa el resto del area de sandboxes
 * (ConferenceHandler), no {@code EventPermissionGuard}.
 */
public class SandboxFilesHandler extends BaseResourceHandler {
    private final ValidateTokenUseCase validateTokenUseCase;
    private final ListWorkspaceFilesUseCase listWorkspaceFilesUseCase;
    private final ReadWorkspaceFileUseCase readWorkspaceFileUseCase;
    private final WriteWorkspaceFileUseCase writeWorkspaceFileUseCase;
    private final GetConferenceUseCase getConferenceUseCase;

    public SandboxFilesHandler(final ValidateTokenUseCase validateTokenUseCase,
                                final ListWorkspaceFilesUseCase listWorkspaceFilesUseCase,
                                final ReadWorkspaceFileUseCase readWorkspaceFileUseCase,
                                final WriteWorkspaceFileUseCase writeWorkspaceFileUseCase,
                                final GetConferenceUseCase getConferenceUseCase) {
        this.validateTokenUseCase = validateTokenUseCase;
        this.listWorkspaceFilesUseCase = listWorkspaceFilesUseCase;
        this.readWorkspaceFileUseCase = readWorkspaceFileUseCase;
        this.writeWorkspaceFileUseCase = writeWorkspaceFileUseCase;
        this.getConferenceUseCase = getConferenceUseCase;
    }

    @Override
    public String basePath() {
        return "/api/v1/conferences";
    }

    @Override
    public List<Route> routes() {
        return List.of(
            Route.of("/{id}/sandbox/files", Set.of("GET")),
            Route.of("/{id}/sandbox/file", Set.of("GET", "PUT"))
        );
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "PUT");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/sandbox/files")) {
            return handleListFiles(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox/file")) {
            return handleReadFile(jx, jx.pathParam("id"));
        }
        return false;
    }

    @Override
    public boolean put(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/sandbox/file")) {
            return handleWriteFile(jx, jx.pathParam("id"));
        }
        return false;
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    private static boolean isOrganizerOrAdmin(final String role) {
        return legacyRoleHasAny(role, "organizer", "admin");
    }

    /** @return null (con el error ya enviado) si el request no esta autorizado -- exige rol
     *  organizer/admin Y que el caller sea dueño real de esta conferencia (o admin plataforma). */
    private String requireOrganizer(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return null;
        }
        final var v = validateTokenUseCase.execute(token);
        if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
            sendError(jx, 403, "forbidden", "Only organizers can view sandbox files");
            return null;
        }
        final boolean platformAdmin = legacyRoleHasAny(v.role(), "admin");
        if (!platformAdmin) {
            final var conference = getConferenceUseCase.byId(conferenceId);
            if (conference.isEmpty() || !conference.get().getCreatedByUserUuid().equals(v.subjectUuid())) {
                sendError(jx, 403, "forbidden", "You are not the organizer of this conference");
                return null;
            }
        }
        return token;
    }

    private boolean handleListFiles(final JettyHttpExchange jx, final String conferenceId) {
        if (requireOrganizer(jx, conferenceId) == null) return true;
        final String userUuid = queryParam(jx, "userUuid");
        final String path = queryParam(jx, "path");
        try {
            final var entries = listWorkspaceFilesUseCase.execute(conferenceId, userUuid, path);
            sendOk(jx, 200, Map.of("entries", entries));
        } catch (final IllegalArgumentException e) {
            sendError(jx, mapErrorStatus(e.getMessage()), e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleReadFile(final JettyHttpExchange jx, final String conferenceId) {
        if (requireOrganizer(jx, conferenceId) == null) return true;
        final String userUuid = queryParam(jx, "userUuid");
        final String path = queryParam(jx, "path");
        try {
            final var content = readWorkspaceFileUseCase.execute(conferenceId, userUuid, path);
            sendOk(jx, 200, Map.of("content", content.content(), "mtime", content.mtime()));
        } catch (final IllegalArgumentException e) {
            sendError(jx, mapErrorStatus(e.getMessage()), e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleWriteFile(final JettyHttpExchange jx, final String conferenceId) {
        if (requireOrganizer(jx, conferenceId) == null) return true;
        final String userUuid = queryParam(jx, "userUuid");
        final String path = queryParam(jx, "path");
        try {
            final var body = parseBody(jx);
            final String content = (String) body.get("content");
            final Boolean force = (Boolean) body.get("force");
            final Object rawMtime = body.get("mtime");
            final Double expectedMtime = (force != null && force) ? null
                    : (rawMtime instanceof Number n ? n.doubleValue() : null);
            final double newMtime = writeWorkspaceFileUseCase.execute(conferenceId, userUuid, path, content, expectedMtime);
            sendOk(jx, 200, Map.of("mtime", newMtime));
        } catch (final IllegalArgumentException e) {
            sendError(jx, mapErrorStatus(e.getMessage()), e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private static int mapErrorStatus(final String message) {
        if ("sandbox_not_assigned".equals(message)) return 404;
        if ("file_not_found".equals(message)) return 404;
        if ("file_conflict".equals(message)) return 409;
        return 400;
    }
}
