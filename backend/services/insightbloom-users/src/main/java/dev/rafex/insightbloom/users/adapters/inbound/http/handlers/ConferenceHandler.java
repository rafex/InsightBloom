package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.insightbloom.users.application.usecases.EnsureUnassignedSandboxUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetSandboxConfigUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetSandboxInternetUseCase;
import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.CancelReservationUseCase;
import dev.rafex.insightbloom.users.application.usecases.CheckInTicketUseCase;
import dev.rafex.insightbloom.users.application.usecases.CountAttendeesUseCase;
import dev.rafex.insightbloom.users.application.usecases.CountRegisteredAttendeesUseCase;
import dev.rafex.insightbloom.users.application.usecases.CountUniqueRegisteredAttendeesUseCase;
import dev.rafex.insightbloom.users.application.usecases.CreateConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.DefineVenueSeatsUseCase;
import dev.rafex.insightbloom.users.application.usecases.GenerateCertificateUseCase;
import dev.rafex.insightbloom.users.application.usecases.GenerateSeatLayoutUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetConferenceHistoryUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetConferenceSeatMapUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetDownloadCountsUseCase;
import dev.rafex.insightbloom.users.application.usecases.GenerateJaasTokenUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetEventDiagramUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetMyTicketUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetOrCreateEventPadUseCase;
import dev.rafex.insightbloom.users.application.usecases.AssignEventRoleUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListEventRolesUseCase;
import dev.rafex.insightbloom.users.application.usecases.RemoveEventRoleUseCase;
import dev.rafex.insightbloom.users.application.usecases.JoinConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListReservationsUseCase;
import dev.rafex.insightbloom.users.application.usecases.RecordDownloadUseCase;
import dev.rafex.insightbloom.users.application.usecases.ReserveGeneralUseCase;
import dev.rafex.insightbloom.users.application.usecases.ReserveSeatUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetEventTypeUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetSeatingModeUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetVenueMapUseCase;
import dev.rafex.insightbloom.users.application.usecases.SaveEventDiagramUseCase;
import dev.rafex.insightbloom.users.application.usecases.UpdateConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.services.EventCapabilityGuard;

import java.util.ArrayList;

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
    private final CountRegisteredAttendeesUseCase countRegisteredAttendeesUseCase;
    private final CountUniqueRegisteredAttendeesUseCase countUniqueRegisteredAttendeesUseCase;
    private final UpdateConferenceUseCase updateConferenceUseCase;
    private final RecordDownloadUseCase recordDownloadUseCase;
    private final GetDownloadCountsUseCase getDownloadCountsUseCase;
    private final SetSeatingModeUseCase setSeatingModeUseCase;
    private final ReserveGeneralUseCase reserveGeneralUseCase;
    private final GetMyTicketUseCase getMyTicketUseCase;
    private final CancelReservationUseCase cancelReservationUseCase;
    private final ListReservationsUseCase listReservationsUseCase;
    private final CheckInTicketUseCase checkInTicketUseCase;
    private final SetVenueMapUseCase setVenueMapUseCase;
    private final DefineVenueSeatsUseCase defineVenueSeatsUseCase;
    private final GetConferenceSeatMapUseCase getConferenceSeatMapUseCase;
    private final ReserveSeatUseCase reserveSeatUseCase;
    private final SetEventTypeUseCase setEventTypeUseCase;
    private final EventCapabilityGuard eventCapabilityGuard;
    private final GetOrCreateEventPadUseCase getOrCreateEventPadUseCase;
    private final AssignEventRoleUseCase assignEventRoleUseCase;
    private final ListEventRolesUseCase listEventRolesUseCase;
    private final RemoveEventRoleUseCase removeEventRoleUseCase;
    private final GetEventDiagramUseCase getEventDiagramUseCase;
    private final SaveEventDiagramUseCase saveEventDiagramUseCase;
    private final GenerateJaasTokenUseCase generateJaasTokenUseCase;
    private final GenerateSeatLayoutUseCase generateSeatLayoutUseCase;
    private final SetSandboxConfigUseCase setSandboxConfigUseCase;
    private final SetSandboxInternetUseCase setSandboxInternetUseCase;
    private final EnsureUnassignedSandboxUseCase ensureUnassignedSandboxUseCase;
    private final SandboxHandler sandboxHandler;

    public ConferenceHandler(final CreateConferenceUseCase createConferenceUseCase,
                             final GetConferenceUseCase getConferenceUseCase,
                             final ValidateTokenUseCase validateTokenUseCase,
                             final JoinConferenceUseCase joinConferenceUseCase,
                             final GetConferenceHistoryUseCase getConferenceHistoryUseCase,
                             final GenerateCertificateUseCase generateCertificateUseCase,
                             final CountAttendeesUseCase countAttendeesUseCase,
                             final CountRegisteredAttendeesUseCase countRegisteredAttendeesUseCase,
                             final CountUniqueRegisteredAttendeesUseCase countUniqueRegisteredAttendeesUseCase,
                             final UpdateConferenceUseCase updateConferenceUseCase,
                             final RecordDownloadUseCase recordDownloadUseCase,
                             final GetDownloadCountsUseCase getDownloadCountsUseCase,
                             final SetSeatingModeUseCase setSeatingModeUseCase,
                             final ReserveGeneralUseCase reserveGeneralUseCase,
                             final GetMyTicketUseCase getMyTicketUseCase,
                             final CancelReservationUseCase cancelReservationUseCase,
                             final ListReservationsUseCase listReservationsUseCase,
                             final CheckInTicketUseCase checkInTicketUseCase,
                             final SetVenueMapUseCase setVenueMapUseCase,
                             final DefineVenueSeatsUseCase defineVenueSeatsUseCase,
                             final GetConferenceSeatMapUseCase getConferenceSeatMapUseCase,
                             final ReserveSeatUseCase reserveSeatUseCase,
                             final SetEventTypeUseCase setEventTypeUseCase,
                             final EventCapabilityGuard eventCapabilityGuard,
                             final GetOrCreateEventPadUseCase getOrCreateEventPadUseCase,
                             final AssignEventRoleUseCase assignEventRoleUseCase,
                             final ListEventRolesUseCase listEventRolesUseCase,
                             final RemoveEventRoleUseCase removeEventRoleUseCase,
                             final GetEventDiagramUseCase getEventDiagramUseCase,
                             final SaveEventDiagramUseCase saveEventDiagramUseCase,
                             final GenerateJaasTokenUseCase generateJaasTokenUseCase,
                             final GenerateSeatLayoutUseCase generateSeatLayoutUseCase,
                             final SetSandboxConfigUseCase setSandboxConfigUseCase,
                             final SetSandboxInternetUseCase setSandboxInternetUseCase,
                             final EnsureUnassignedSandboxUseCase ensureUnassignedSandboxUseCase,
                             final SandboxHandler sandboxHandler) {
        this.createConferenceUseCase = createConferenceUseCase;
        this.getConferenceUseCase = getConferenceUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
        this.joinConferenceUseCase = joinConferenceUseCase;
        this.getConferenceHistoryUseCase = getConferenceHistoryUseCase;
        this.generateCertificateUseCase = generateCertificateUseCase;
        this.countAttendeesUseCase = countAttendeesUseCase;
        this.countRegisteredAttendeesUseCase = countRegisteredAttendeesUseCase;
        this.countUniqueRegisteredAttendeesUseCase = countUniqueRegisteredAttendeesUseCase;
        this.updateConferenceUseCase = updateConferenceUseCase;
        this.recordDownloadUseCase = recordDownloadUseCase;
        this.getDownloadCountsUseCase = getDownloadCountsUseCase;
        this.setSeatingModeUseCase = setSeatingModeUseCase;
        this.reserveGeneralUseCase = reserveGeneralUseCase;
        this.getMyTicketUseCase = getMyTicketUseCase;
        this.cancelReservationUseCase = cancelReservationUseCase;
        this.listReservationsUseCase = listReservationsUseCase;
        this.checkInTicketUseCase = checkInTicketUseCase;
        this.setVenueMapUseCase = setVenueMapUseCase;
        this.defineVenueSeatsUseCase = defineVenueSeatsUseCase;
        this.getConferenceSeatMapUseCase = getConferenceSeatMapUseCase;
        this.reserveSeatUseCase = reserveSeatUseCase;
        this.setEventTypeUseCase = setEventTypeUseCase;
        this.eventCapabilityGuard = eventCapabilityGuard;
        this.getOrCreateEventPadUseCase = getOrCreateEventPadUseCase;
        this.assignEventRoleUseCase = assignEventRoleUseCase;
        this.listEventRolesUseCase = listEventRolesUseCase;
        this.removeEventRoleUseCase = removeEventRoleUseCase;
        this.getEventDiagramUseCase = getEventDiagramUseCase;
        this.saveEventDiagramUseCase = saveEventDiagramUseCase;
        this.generateJaasTokenUseCase = generateJaasTokenUseCase;
        this.generateSeatLayoutUseCase = generateSeatLayoutUseCase;
        this.setSandboxConfigUseCase = setSandboxConfigUseCase;
        this.setSandboxInternetUseCase = setSandboxInternetUseCase;
        this.ensureUnassignedSandboxUseCase = ensureUnassignedSandboxUseCase;
        this.sandboxHandler = sandboxHandler;
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
                Route.of("/attendees/registered-summary", Set.of("GET")),
                Route.of("/{id}/certificate", Set.of("GET")),
                Route.of("/{id}/attendees/count", Set.of("GET")),
                Route.of("/{id}/derive-name", Set.of("POST")),
                Route.of("/{id}/downloads", Set.of("POST")),
                Route.of("/{id}/downloads/count", Set.of("GET")),
                Route.of("/{id}/seating", Set.of("PUT")),
                Route.of("/{id}/event-type", Set.of("PUT")),
                Route.of("/{id}/venue-map", Set.of("PUT")),
                Route.of("/{id}/venue-map/generate-seats", Set.of("POST")),
                Route.of("/{id}/sandbox-config", Set.of("PUT")),
                Route.of("/{id}/sandbox-internet", Set.of("PUT")),
                Route.of("/{id}/sandbox", Set.of("GET")),
                Route.of("/{id}/sandbox/download", Set.of("POST")),
                Route.of("/{id}/seats", Set.of("GET", "PUT")),
                Route.of("/{id}/reservations", Set.of("GET", "POST")),
                Route.of("/{id}/reservations/me", Set.of("GET", "DELETE")),
                Route.of("/{id}/reservations/check-in", Set.of("POST")),
                Route.of("/{id}/notes", Set.of("GET")),
                Route.of("/{id}/diagram", Set.of("GET", "PUT")),
                Route.of("/{id}/jaas-token", Set.of("GET")),
                Route.of("/{id}/roles", Set.of("GET", "POST")),
                Route.of("/{id}/roles/{userUuid}", Set.of("DELETE")),
                Route.of("/{id}", Set.of("GET", "PUT", "DELETE")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST", "PUT", "DELETE");
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
        if (path.endsWith("/attendees/registered-summary")) {
            return handleRegisteredAttendeesSummary(jx);
        }
        if (path.endsWith("/certificate")) {
            return handleCertificate(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/attendees/count")) {
            return handleAttendeesCount(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/downloads/count")) {
            return handleDownloadCounts(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/reservations/me")) {
            return handleGetMyTicket(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/reservations")) {
            return handleListReservations(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/seats")) {
            return handleGetSeatMap(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/notes")) {
            return handleGetNotes(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/diagram")) {
            return handleGetDiagram(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/jaas-token")) {
            return handleGetJaasToken(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/roles")) {
            return handleListEventRoles(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/sandbox")) {
            return sandboxHandler.get(x);
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
        if (jx.path().endsWith("/derive-name")) {
            return handleDeriveName(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/downloads")) {
            return handleRecordDownload(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/reservations/check-in")) {
            return handleCheckIn(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/venue-map/generate-seats")) {
            return handleGenerateSeatLayout(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/reservations")) {
            return handleReserve(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/roles")) {
            return handleAssignEventRole(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox/download")) {
            return sandboxHandler.post(x);
        }
        return handleCreate(jx);
    }

    @Override
    public boolean delete(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/reservations/me")) {
            return handleCancelReservation(jx, jx.pathParam("id"));
        }
        if (jx.path().contains("/roles/")) {
            return handleRemoveEventRole(jx, jx.pathParam("id"), jx.pathParam("userUuid"));
        }
        return handleDelete(jx, jx.pathParam("id"));
    }

    @Override
    public boolean put(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/seating")) {
            return handleSetSeating(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/event-type")) {
            return handleSetEventType(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/venue-map")) {
            return handleSetVenueMap(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox-config")) {
            return handleSetSandboxConfig(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox-internet")) {
            return handleSetSandboxInternet(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/seats")) {
            return handleDefineSeats(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/diagram")) {
            return handleSaveDiagram(jx, jx.pathParam("id"));
        }
        return handleUpdate(jx, jx.pathParam("id"));
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
            final Integer timezoneId = body.get("timezoneId") instanceof Number n ? n.intValue() : null;
            final var result = createConferenceUseCase.execute(new CreateConferenceUseCase.CreateRequest(
                    (String) body.get("name"), (String) body.get("displayName"), v.subjectUuid(),
                    (String) body.get("expiresAt"),
                    latitude, longitude, (String) body.get("eventDate"), (String) body.get("venue"),
                    (String) body.get("startTime"), (String) body.get("endTime"), timezoneId,
                    (String) body.get("eventTypeKey")));
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

    /** Conteo de personas únicas (deduplicado) registradas en alguna conferencia del organizador. */
    private boolean handleRegisteredAttendeesSummary(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can view attendee counts");
                return true;
            }
            sendOk(jx, Map.of("uniqueRegisteredAttendees", countUniqueRegisteredAttendeesUseCase.execute(v.subjectUuid())));
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

    private boolean handleUpdate(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            final var body = parseBody(jx);
            final Double latitude = body.get("latitude") instanceof Number n ? n.doubleValue() : null;
            final Double longitude = body.get("longitude") instanceof Number n ? n.doubleValue() : null;
            final Integer timezoneId = body.get("timezoneId") instanceof Number n ? n.intValue() : null;
            final var updated = updateConferenceUseCase.execute(id, v.subjectUuid(),
                    new UpdateConferenceUseCase.UpdateRequest((String) body.get("displayName"),
                            (String) body.get("venue"), (String) body.get("eventDate"),
                            (String) body.get("startTime"), (String) body.get("endTime"), latitude, longitude,
                            (String) body.get("presentationSourceUrl"), (String) body.get("flyerBase64"), timezoneId));
            if (updated.isPresent()) {
                sendOk(jx, 200, updated.get());
            } else {
                sendError(jx, 404, "not_found", "Conference not found or not owned by you");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleDeriveName(final JettyHttpExchange jx, final String id) {
        if (!validInternalAuth(jx)) { sendError(jx, 403, "forbidden", "Internal access only"); return true; }
        try {
            final var body = parseBody(jx);
            final var updated = updateConferenceUseCase.deriveNameFromPresentation(id, (String) body.get("title"));
            sendOk(jx, 200, Map.of("updated", updated.isPresent()));
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
            recordDownloadUseCase.execute(conferenceId, "certificate");
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
            sendOk(jx, Map.of(
                    "count", countAttendeesUseCase.execute(conferenceId),
                    "registered", countRegisteredAttendeesUseCase.execute(conferenceId)));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleRecordDownload(final JettyHttpExchange jx, final String id) {
        if (!validInternalAuth(jx)) { sendError(jx, 403, "forbidden", "Internal access only"); return true; }
        try {
            final var body = parseBody(jx);
            recordDownloadUseCase.execute(id, (String) body.get("kind"));
            sendOk(jx, 200, Map.of("status", "recorded"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleDownloadCounts(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can view download counts");
                return true;
            }
            final var counts = getDownloadCountsUseCase.execute(conferenceId);
            sendOk(jx, Map.of("certificate", counts.certificate(), "presentation", counts.presentation()));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleSetSeating(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can configure seating");
                return true;
            }
            final var body = parseBody(jx);
            final String seatingMode = (String) body.get("seatingMode");
            final EventCapability requiredCapability = "SEATED".equals(seatingMode) ? EventCapability.TICKETING_SEATED
                    : "GENERAL".equals(seatingMode) ? EventCapability.TICKETING_GENERAL : null;
            if (requiredCapability != null && !hasCapability(id, requiredCapability)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita este modo de boletos");
                return true;
            }
            final Integer capacity = body.get("capacity") instanceof Number n ? n.intValue() : null;
            final var updated = setSeatingModeUseCase.execute(id, v.subjectUuid(), seatingMode, capacity);
            if (updated.isPresent()) {
                sendOk(jx, 200, updated.get());
            } else {
                sendError(jx, 404, "not_found", "Conference not found or not owned by you");
            }
        } catch (final IllegalStateException e) {
            sendError(jx, 409, e.getMessage(), "No se puede cambiar el modo de asientos con reservas activas");
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetNotes(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            if (!hasCapability(id, EventCapability.COLLAB_NOTES)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita notas colaborativas");
                return true;
            }
            getOrCreateEventPadUseCase.execute(id).ifPresentOrElse(
                    pad -> sendOk(jx, 200, pad),
                    () -> sendError(jx, 404, "conference_not_found", "Conference not found"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetDiagram(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            if (!hasCapability(id, EventCapability.DIAGRAMMING)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita diagramas");
                return true;
            }
            getEventDiagramUseCase.execute(id).ifPresentOrElse(
                    diagram -> sendOk(jx, 200, diagram),
                    () -> sendError(jx, 404, "conference_not_found", "Conference not found"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleSaveDiagram(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            if (!hasCapability(id, EventCapability.DIAGRAMMING)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita diagramas");
                return true;
            }
            final var body = parseBody(jx);
            final String xml = (String) body.get("xml");
            if (saveEventDiagramUseCase.execute(id, xml != null ? xml : "")) {
                sendOk(jx, 200, java.util.Map.of("saved", true));
            } else {
                sendError(jx, 404, "conference_not_found", "Conference not found");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetJaasToken(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            if (!hasCapability(id, EventCapability.VIDEO_CONFERENCE)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita videollamada");
                return true;
            }
            generateJaasTokenUseCase.execute(id, v.subjectUuid(), v.role()).ifPresentOrElse(
                    jaasToken -> sendOk(jx, 200, jaasToken),
                    () -> sendError(jx, 404, "jaas_not_configured", "JaaS no esta configurado en este despliegue"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleListEventRoles(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            listEventRolesUseCase.execute(id, v.subjectUuid(), v.role()).ifPresentOrElse(
                    roles -> sendOk(jx, 200, roles),
                    () -> sendError(jx, 403, "forbidden", "No tienes permiso para ver los roles de este evento"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleAssignEventRole(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            final var body = parseBody(jx);
            final var assignment = assignEventRoleUseCase.execute(id, v.subjectUuid(), v.role(),
                    (String) body.get("userIdentifier"), (String) body.get("roleKey"));
            sendOk(jx, 201, assignment);
        } catch (final SecurityException e) {
            sendError(jx, 403, "forbidden", "No tienes permiso para asignar roles en este evento");
        } catch (final IllegalArgumentException e) {
            sendError(jx, "user_not_found".equals(e.getMessage()) || "role_not_found".equals(e.getMessage()) ? 404 : 400,
                    e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleRemoveEventRole(final JettyHttpExchange jx, final String id, final String userUuid) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            removeEventRoleUseCase.execute(id, v.subjectUuid(), v.role(), userUuid);
            sendOk(jx, 200, Map.of("removed", true));
        } catch (final SecurityException e) {
            sendError(jx, 403, "forbidden", "No tienes permiso para quitar roles en este evento");
        } catch (final IllegalStateException e) {
            sendError(jx, 409, e.getMessage(), "No se puede quitar al Host original del evento");
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleSetEventType(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can change the event type");
                return true;
            }
            final var body = parseBody(jx);
            final String eventTypeKey = (String) body.get("eventTypeKey");
            final var updated = setEventTypeUseCase.execute(id, v.subjectUuid(), eventTypeKey);
            if (updated.isPresent()) {
                sendOk(jx, 200, updated.get());
            } else {
                sendError(jx, 404, "not_found", "Conference not found or not owned by you");
            }
        } catch (final IllegalStateException e) {
            sendError(jx, 409, e.getMessage(), "No se puede cambiar el tipo de evento con reservas de asiento activas");
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean hasCapability(final String conferenceId, final EventCapability capability) {
        return getConferenceUseCase.byId(conferenceId)
                .map(c -> eventCapabilityGuard.hasCapability(c, capability))
                .orElse(false);
    }

    private boolean handleReserve(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            final var body = parseBody(jx);
            final String seatUuid = (String) body.get("seatUuid");
            final EventCapability requiredCapability = seatUuid != null
                    ? EventCapability.TICKETING_SEATED : EventCapability.TICKETING_GENERAL;
            if (!hasCapability(id, requiredCapability)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita boletos");
                return true;
            }
            final Reservation reservation = seatUuid != null
                    ? reserveSeatUseCase.execute(id, v.subjectUuid(), seatUuid)
                    : reserveGeneralUseCase.execute(id, v.subjectUuid());
            sendOk(jx, 201, reservation);
        } catch (final IllegalStateException e) {
            final String detail = "seat_already_taken".equals(e.getMessage())
                    ? "Ese asiento ya fue reservado por alguien más" : "No hay cupo disponible para esta conferencia";
            sendError(jx, 409, e.getMessage(), detail);
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetMyTicket(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            getMyTicketUseCase.execute(id, v.subjectUuid()).ifPresentOrElse(
                    r -> sendOk(jx, 200, r),
                    () -> sendError(jx, 404, "no_ticket", "No tienes un boleto para esta conferencia"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleCancelReservation(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            final boolean cancelled = cancelReservationUseCase.execute(id, v.subjectUuid());
            sendOk(jx, 200, Map.of("cancelled", cancelled));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleListReservations(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can view the reservation list");
                return true;
            }
            listReservationsUseCase.execute(id, v.subjectUuid()).ifPresentOrElse(
                    list -> sendOk(jx, 200, list),
                    () -> sendError(jx, 404, "not_found", "Conference not found or not owned by you"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleCheckIn(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can check in tickets");
                return true;
            }
            final var body = parseBody(jx);
            final String ticketCode = (String) body.get("ticketCode");
            final var reservation = checkInTicketUseCase.execute(id, ticketCode);
            sendOk(jx, 200, reservation);
        } catch (final IllegalStateException e) {
            sendError(jx, 409, e.getMessage(), "Este boleto ya fue registrado");
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Boleto no encontrado para esta conferencia");
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleSetVenueMap(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can configure the venue map");
                return true;
            }
            if (!hasCapability(id, EventCapability.TICKETING_SEATED)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita mapa de asientos");
                return true;
            }
            final var body = parseBody(jx);
            final String imageBase64 = (String) body.get("imageBase64");
            final var updated = setVenueMapUseCase.execute(id, v.subjectUuid(), imageBase64);
            if (updated.isPresent()) {
                sendOk(jx, 200, updated.get());
            } else {
                sendError(jx, 404, "not_found", "Conference not found or not owned by you");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean handleGenerateSeatLayout(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can generate a seat layout");
                return true;
            }
            if (!hasCapability(id, EventCapability.TICKETING_SEATED)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita mapa de asientos");
                return true;
            }
            final var body = parseBody(jx);
            final String description = (String) body.get("description");
            final var seats = generateSeatLayoutUseCase.execute(description);
            sendOk(jx, 200, Map.of("seats", seats));
        } catch (final IllegalStateException e) {
            sendError(jx, 503, "llm_not_configured", "La generacion por IA no esta configurada en este despliegue");
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, "bad_request", e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 502, "llm_invalid_response", e.getMessage());
        }
        return true;
    }

    private boolean handleDefineSeats(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can configure seats");
                return true;
            }
            if (!hasCapability(id, EventCapability.TICKETING_SEATED)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita mapa de asientos");
                return true;
            }
            final var body = parseBody(jx);
            final Object rawSeats = body.get("seats");
            final List<DefineVenueSeatsUseCase.SeatInput> seats = new ArrayList<>();
            if (rawSeats instanceof List<?> list) {
                for (final Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        final Map<String, Object> seat = (Map<String, Object>) m;
                        final String seatUuid = (String) seat.get("uuid");
                        final String label = (String) seat.get("label");
                        final double x = seat.get("x") instanceof Number n ? n.doubleValue() : 0;
                        final double y = seat.get("y") instanceof Number n ? n.doubleValue() : 0;
                        seats.add(new DefineVenueSeatsUseCase.SeatInput(seatUuid, label, x, y));
                    }
                }
            }
            final var updated = defineVenueSeatsUseCase.execute(id, v.subjectUuid(), seats);
            if (updated.isPresent()) {
                sendOk(jx, 200, updated.get());
            } else {
                sendError(jx, 404, "not_found", "Conference not found or not owned by you");
            }
        } catch (final IllegalStateException e) {
            sendError(jx, 409, e.getMessage(), "No se puede quitar un asiento con una reserva activa");
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGetSeatMap(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            sendOk(jx, 200, getConferenceSeatMapUseCase.execute(id));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleSetSandboxConfig(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can configure sandboxes");
                return true;
            }
            final var body = parseBody(jx);
            final String sandboxVariant = (String) body.get("sandboxVariant");
            final Integer sandboxPoolSize = (Integer) body.get("sandboxPoolSize");
            final String sandboxExtraPackages = (String) body.get("sandboxExtraPackages");
            final String sandboxRemoteGitUrl = (String) body.get("sandboxRemoteGitUrl");
            final var result = setSandboxConfigUseCase.execute(id, sandboxVariant, sandboxPoolSize,
                sandboxExtraPackages, sandboxRemoteGitUrl);
            try {
                // Best-effort: si falla (ej. Kubernetes no disponible), no debe tumbar el guardado
                // de la config -- AssignSandboxUseCase sigue creando bajo demanda como fallback.
                ensureUnassignedSandboxUseCase.execute(id);
            } catch (final Exception ignored) {
                // pre-warm es una optimizacion, no un requisito para guardar la config
            }
            sendOk(jx, 200, result);
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleSetSandboxInternet(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can configure sandboxes");
                return true;
            }
            final var body = parseBody(jx);
            final Object rawValue = body.get("internetEnabled");
            final int internetEnabled = (rawValue instanceof Boolean b) ? (b ? 1 : 0)
                    : ((Number) rawValue).intValue();
            final var result = setSandboxInternetUseCase.execute(id, internetEnabled);
            sendOk(jx, 200, result);
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
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
