package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.CountAttendeesUseCase;
import dev.rafex.insightbloom.users.application.usecases.CreateConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.GenerateCertificateUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetConferenceHistoryUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.JoinConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConferenceHandler extends BaseResourceHandler {

    private final CreateConferenceUseCase createConferenceUseCase;
    private final GetConferenceUseCase getConferenceUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final JoinConferenceUseCase joinConferenceUseCase;
    private final GetConferenceHistoryUseCase getConferenceHistoryUseCase;
    private final GenerateCertificateUseCase generateCertificateUseCase;
    private final CountAttendeesUseCase countAttendeesUseCase;

    public ConferenceHandler(final CreateConferenceUseCase createConferenceUseCase,
                             final GetConferenceUseCase getConferenceUseCase,
                             final ValidateTokenUseCase validateTokenUseCase,
                             final JoinConferenceUseCase joinConferenceUseCase,
                             final GetConferenceHistoryUseCase getConferenceHistoryUseCase,
                             final GenerateCertificateUseCase generateCertificateUseCase,
                             final CountAttendeesUseCase countAttendeesUseCase) {
        this.createConferenceUseCase = createConferenceUseCase;
        this.getConferenceUseCase = getConferenceUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
        this.joinConferenceUseCase = joinConferenceUseCase;
        this.getConferenceHistoryUseCase = getConferenceHistoryUseCase;
        this.generateCertificateUseCase = generateCertificateUseCase;
        this.countAttendeesUseCase = countAttendeesUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/conferences";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/", Set.of("GET", "POST")),
                Route.of("/by-friendly/{friendlyId}", Set.of("GET")),
                Route.of("/by-short/{shortCode}", Set.of("GET")),
                Route.of("/join", Set.of("POST")),
                Route.of("/history", Set.of("GET")),
                Route.of("/{id}/certificate", Set.of("GET")),
                Route.of("/{id}/attendees/count", Set.of("GET")),
                Route.of("/{id}", Set.of("GET", "DELETE")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST", "DELETE");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        final String path = jx.path();
        if (path.contains("/by-friendly/")) {
            return handleGetByFriendly(jx, jx.pathParam("friendlyId"));
        }
        if (path.contains("/by-short/")) {
            return handleGetByShortCode(jx, jx.pathParam("shortCode"));
        }
        if (path.endsWith("/history")) {
            return handleHistory(jx);
        }
        if (path.endsWith("/certificate")) {
            return handleCertificate(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/attendees/count")) {
            return handleAttendeesCount(jx, jx.pathParam("id"));
        }
        final String id = jx.pathParam("id");
        if (id != null) {
            return handleGetById(jx, id);
        }
        return handleList(jx);
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/join")) {
            return handleJoin(jx);
        }
        return handleCreate(jx);
    }

    @Override
    public boolean delete(final HttpExchange x) {
        final var jx = asJetty(x);
        return handleDelete(jx, jx.pathParam("id"));
    }

    private boolean handleList(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            sendOk(jx, 200, getConferenceUseCase.byUser(v.subjectUuid()));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleCreate(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can create conferences");
                return true;
            }
            final var body = parseBody(jx);
            final Double latitude = body.get("latitude") instanceof Number n ? n.doubleValue() : null;
            final Double longitude = body.get("longitude") instanceof Number n ? n.doubleValue() : null;
            final var result = createConferenceUseCase.execute(new CreateConferenceUseCase.CreateRequest(
                    (String) body.get("name"), v.subjectUuid(), (String) body.get("expiresAt"),
                    latitude, longitude, (String) body.get("eventDate"), (String) body.get("venue"),
                    (String) body.get("startTime"), (String) body.get("endTime")));
            sendOk(jx, 201, result);
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetById(final JettyHttpExchange jx, final String id) {
        try {
            getConferenceUseCase.byId(id).ifPresentOrElse(
                    c -> sendOk(jx, 200, c),
                    () -> sendError(jx, 404, "conference_not_found", "Conference not found"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetByFriendly(final JettyHttpExchange jx, final String friendlyId) {
        try {
            getConferenceUseCase.byFriendlyId(friendlyId).ifPresentOrElse(
                    c -> sendOk(jx, 200, c),
                    () -> sendError(jx, 404, "conference_not_found", "Conference not found"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetByShortCode(final JettyHttpExchange jx, final String shortCode) {
        try {
            getConferenceUseCase.byShortCode(shortCode).ifPresentOrElse(
                    c -> sendOk(jx, 200, c),
                    () -> sendError(jx, 404, "conference_not_found", "Conference not found"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleJoin(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            final var body = parseBody(jx);
            final String identifier = (String) body.get("identifier");
            final var conference = joinConferenceUseCase.execute(v.subjectUuid(), identifier);
            sendOk(jx, 200, conference);
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Esta conferencia ya no se encuentra disponible");
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleHistory(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            sendOk(jx, 200, getConferenceHistoryUseCase.execute(v.subjectUuid()));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleDelete(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            final boolean deleted = getConferenceUseCase.delete(id, v.subjectUuid());
            if (deleted) {
                sendOk(jx, 200, Map.of("deleted", true));
            } else {
                sendError(jx, 404, "not_found", "Conference not found or not owned by you");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleCertificate(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || "guest".equals(v.kind())) {
                sendError(jx, 403, "forbidden", "You must be a verified user to download a certificate");
                return true;
            }
            final var result = generateCertificateUseCase.execute(conferenceId, v.subjectUuid(), token);
            jx.response().setStatus(200);
            jx.response().getHeaders().put("Content-Type", "application/pdf");
            jx.response().getHeaders().put("Content-Disposition", "attachment; filename=\"" + result.fileName() + "\"");
            jx.response().write(true, ByteBuffer.wrap(result.pdfBytes()), jx.callback());
        } catch (final IllegalStateException e) {
            sendError(jx, 409, e.getMessage(), "Debes completar la encuesta antes de descargar tu certificado");
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleAttendeesCount(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can view attendee counts");
                return true;
            }
            sendOk(jx, Map.of("count", countAttendeesUseCase.execute(conferenceId)));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private static boolean isOrganizerOrAdmin(final String role) {
        return role != null && (role.contains("organizer") || role.contains("admin"));
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

}
