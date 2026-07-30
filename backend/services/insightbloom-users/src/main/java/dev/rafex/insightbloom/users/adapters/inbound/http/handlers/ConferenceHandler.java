package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.insightbloom.users.application.usecases.EnsureUnassignedSandboxUseCase;
import dev.rafex.insightbloom.users.application.usecases.PrewarmSandboxPoolUseCase;
import dev.rafex.insightbloom.users.application.usecases.ResetSandboxUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListSandboxIncidentsUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListSandboxStatusUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetSandboxConfigUseCase;
import dev.rafex.insightbloom.users.application.usecases.EgressPolicyUseCase;
import dev.rafex.insightbloom.users.domain.model.EgressPolicy;
import dev.rafex.insightbloom.users.application.usecases.SetSandboxInternetUseCase;
import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.http.core.HttpExchange.EventStream;
import dev.rafex.ether.json.JsonUtils;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.CancelReservationUseCase;
import dev.rafex.insightbloom.users.application.usecases.CheckInTicketUseCase;
import dev.rafex.insightbloom.users.application.usecases.CountAttendeesUseCase;
import dev.rafex.insightbloom.users.application.usecases.CountRegisteredAttendeesUseCase;
import dev.rafex.insightbloom.users.application.usecases.CountUniqueRegisteredAttendeesUseCase;
import dev.rafex.insightbloom.users.application.usecases.CountActiveRegisteredAttendeesUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetConferenceActiveUseCase;
import dev.rafex.insightbloom.users.application.usecases.CreateConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.DefineVenueSeatsUseCase;
import dev.rafex.insightbloom.users.application.usecases.GenerateCertificateUseCase;
import dev.rafex.insightbloom.users.application.usecases.GenerateSeatLayoutUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetConferenceHistoryUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetConferenceSeatMapUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetDownloadCountsUseCase;
import dev.rafex.insightbloom.users.application.usecases.GenerateJaasTokenUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetJaasUsageUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetEventDiagramUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetEventWhiteboardUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetMyTicketUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetOrCreateEventPadUseCase;
import dev.rafex.insightbloom.users.application.usecases.ExportEventNotesUseCase;
import dev.rafex.insightbloom.users.application.usecases.EventMaterialsDownloadUseCase;
import dev.rafex.insightbloom.users.application.usecases.AssignEventRoleUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListEventRolesUseCase;
import dev.rafex.insightbloom.users.application.usecases.RemoveEventRoleUseCase;
import dev.rafex.insightbloom.users.application.usecases.JoinConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListReservationsUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListConferenceAttendeesUseCase;
import dev.rafex.insightbloom.users.application.usecases.RecordDownloadUseCase;
import dev.rafex.insightbloom.users.application.usecases.ReserveGeneralUseCase;
import dev.rafex.insightbloom.users.application.usecases.ReserveSeatUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetEventTypeUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetCanvasConfigUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetDeviceAccessConfigUseCase;
import dev.rafex.insightbloom.users.application.usecases.ListDeviceBlocksUseCase;
import dev.rafex.insightbloom.users.application.usecases.UnblockDeviceUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetSeatingModeUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetVenueMapUseCase;
import dev.rafex.insightbloom.users.application.usecases.SaveEventDiagramUseCase;
import dev.rafex.insightbloom.users.application.usecases.SaveEventWhiteboardUseCase;
import dev.rafex.insightbloom.users.application.usecases.UpdateConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.application.usecases.TicketUseCase;
import dev.rafex.insightbloom.users.application.usecases.CreateGuestUseCase;
import dev.rafex.insightbloom.users.application.usecases.ToolAccessUseCase;
import dev.rafex.insightbloom.users.application.usecases.SendAttendeeEmailUseCase;
import dev.rafex.insightbloom.users.application.usecases.GenerateEmailDraftUseCase;
import dev.rafex.insightbloom.users.application.usecases.NotifyConferenceUpdatedUseCase;
import dev.rafex.insightbloom.users.domain.model.ToolKey;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.ConferenceStatus;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.model.CanvasConfig;
import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.services.EventCapabilityGuard;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;
import dev.rafex.insightbloom.users.domain.services.DeviceAccessGuard;
import dev.rafex.insightbloom.users.domain.model.ToolKind;

import org.eclipse.jetty.server.Request;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConferenceHandler extends BaseResourceHandler {

    private static final long DIAGRAM_STREAM_HEARTBEAT_SECONDS = 25;
    private static final int MAX_FLYER_BYTES = 8 * 1024 * 1024;
    private static final int MAX_FLYER_REQUEST_BYTES = MAX_FLYER_BYTES + 128 * 1024;
    private static final Pattern MULTIPART_BOUNDARY = Pattern.compile(
            "(?:^|;)\\s*boundary=(?:\\\"([^\\\"]+)\\\"|([^;\\s]+))", Pattern.CASE_INSENSITIVE);

    private static final java.util.logging.Logger LOGGER =
        java.util.logging.Logger.getLogger(ConferenceHandler.class.getName());

    private final CreateConferenceUseCase createConferenceUseCase;
    private final GetConferenceUseCase getConferenceUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final JoinConferenceUseCase joinConferenceUseCase;
    private final GetConferenceHistoryUseCase getConferenceHistoryUseCase;
    private final GenerateCertificateUseCase generateCertificateUseCase;
    private final CountAttendeesUseCase countAttendeesUseCase;
    private final CountRegisteredAttendeesUseCase countRegisteredAttendeesUseCase;
    private final CountUniqueRegisteredAttendeesUseCase countUniqueRegisteredAttendeesUseCase;
    private final CountActiveRegisteredAttendeesUseCase countActiveRegisteredAttendeesUseCase;
    private final UpdateConferenceUseCase updateConferenceUseCase;
    private final SetConferenceActiveUseCase setConferenceActiveUseCase;
    private final RecordDownloadUseCase recordDownloadUseCase;
    private final GetDownloadCountsUseCase getDownloadCountsUseCase;
    private final SetSeatingModeUseCase setSeatingModeUseCase;
    private final ReserveGeneralUseCase reserveGeneralUseCase;
    private final GetMyTicketUseCase getMyTicketUseCase;
    private final CancelReservationUseCase cancelReservationUseCase;
    private final ListReservationsUseCase listReservationsUseCase;
    private final ListConferenceAttendeesUseCase listConferenceAttendeesUseCase;
    private final CheckInTicketUseCase checkInTicketUseCase;
    private final TicketUseCase ticketUseCase;
    private final EventPermissionGuard eventPermissionGuard;
    private final CreateGuestUseCase createGuestUseCase;
    private final SetVenueMapUseCase setVenueMapUseCase;
    private final DefineVenueSeatsUseCase defineVenueSeatsUseCase;
    private final GetConferenceSeatMapUseCase getConferenceSeatMapUseCase;
    private final ReserveSeatUseCase reserveSeatUseCase;
    private final SetEventTypeUseCase setEventTypeUseCase;
    private final SetCanvasConfigUseCase setCanvasConfigUseCase;
    private final EventCapabilityGuard eventCapabilityGuard;
    private final GetOrCreateEventPadUseCase getOrCreateEventPadUseCase;
    private final ExportEventNotesUseCase exportEventNotesUseCase;
    private final EventMaterialsDownloadUseCase eventMaterialsDownloadUseCase;
    private final AssignEventRoleUseCase assignEventRoleUseCase;
    private final ListEventRolesUseCase listEventRolesUseCase;
    private final RemoveEventRoleUseCase removeEventRoleUseCase;
    private final GetEventDiagramUseCase getEventDiagramUseCase;
    private final SaveEventDiagramUseCase saveEventDiagramUseCase;
    private final GetEventWhiteboardUseCase getEventWhiteboardUseCase;
    private final SaveEventWhiteboardUseCase saveEventWhiteboardUseCase;
    private final GenerateJaasTokenUseCase generateJaasTokenUseCase;
    private final GenerateSeatLayoutUseCase generateSeatLayoutUseCase;
    private final SetSandboxConfigUseCase setSandboxConfigUseCase;
    private final EgressPolicyUseCase egressPolicyUseCase;
    private final SetSandboxInternetUseCase setSandboxInternetUseCase;
    private final EnsureUnassignedSandboxUseCase ensureUnassignedSandboxUseCase;
    private final PrewarmSandboxPoolUseCase prewarmSandboxPoolUseCase;
    private final ResetSandboxUseCase resetSandboxUseCase;
    private final ListSandboxIncidentsUseCase listSandboxIncidentsUseCase;
    private final ListSandboxStatusUseCase listSandboxStatusUseCase;
    private final SetDeviceAccessConfigUseCase setDeviceAccessConfigUseCase;
    private final ListDeviceBlocksUseCase listDeviceBlocksUseCase;
    private final UnblockDeviceUseCase unblockDeviceUseCase;
    private final SandboxHandler sandboxHandler;
    private final SandboxFilesHandler sandboxFilesHandler;
    private final UserRepository userRepository;
    private final DeviceAccessGuard deviceAccessGuard;
    private final GetJaasUsageUseCase getJaasUsageUseCase;
    private final ToolAccessUseCase toolAccessUseCase;
    private final SendAttendeeEmailUseCase sendAttendeeEmailUseCase;
    private final GenerateEmailDraftUseCase generateEmailDraftUseCase;
    private final NotifyConferenceUpdatedUseCase notifyConferenceUpdatedUseCase;
    private final Map<String, CopyOnWriteArrayList<EventStream>> diagramSubscribers = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<EventStream>> whiteboardSubscribers = new ConcurrentHashMap<>();
    private record VideoSubscriber(EventStream stream, String deviceFingerprint) {}
    private final Map<String, CopyOnWriteArrayList<VideoSubscriber>> videoSubscribers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService diagramStreamScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread thread = new Thread(r, "diagram-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public ConferenceHandler(final CreateConferenceUseCase createConferenceUseCase,
                             final GetConferenceUseCase getConferenceUseCase,
                             final ValidateTokenUseCase validateTokenUseCase,
                             final JoinConferenceUseCase joinConferenceUseCase,
                             final GetConferenceHistoryUseCase getConferenceHistoryUseCase,
                             final GenerateCertificateUseCase generateCertificateUseCase,
                             final CountAttendeesUseCase countAttendeesUseCase,
                             final CountRegisteredAttendeesUseCase countRegisteredAttendeesUseCase,
                             final CountUniqueRegisteredAttendeesUseCase countUniqueRegisteredAttendeesUseCase,
                             final CountActiveRegisteredAttendeesUseCase countActiveRegisteredAttendeesUseCase,
                             final UpdateConferenceUseCase updateConferenceUseCase,
                             final SetConferenceActiveUseCase setConferenceActiveUseCase,
                             final RecordDownloadUseCase recordDownloadUseCase,
                             final GetDownloadCountsUseCase getDownloadCountsUseCase,
                             final SetSeatingModeUseCase setSeatingModeUseCase,
                             final ReserveGeneralUseCase reserveGeneralUseCase,
                             final GetMyTicketUseCase getMyTicketUseCase,
                             final CancelReservationUseCase cancelReservationUseCase,
                             final ListReservationsUseCase listReservationsUseCase,
                             final ListConferenceAttendeesUseCase listConferenceAttendeesUseCase,
                             final CheckInTicketUseCase checkInTicketUseCase,
                             final TicketUseCase ticketUseCase,
                             final EventPermissionGuard eventPermissionGuard,
                             final CreateGuestUseCase createGuestUseCase,
                             final SetVenueMapUseCase setVenueMapUseCase,
                             final DefineVenueSeatsUseCase defineVenueSeatsUseCase,
                             final GetConferenceSeatMapUseCase getConferenceSeatMapUseCase,
                             final ReserveSeatUseCase reserveSeatUseCase,
                             final SetEventTypeUseCase setEventTypeUseCase,
                             final SetCanvasConfigUseCase setCanvasConfigUseCase,
                             final EventCapabilityGuard eventCapabilityGuard,
                             final GetOrCreateEventPadUseCase getOrCreateEventPadUseCase,
                             final ExportEventNotesUseCase exportEventNotesUseCase,
                             final EventMaterialsDownloadUseCase eventMaterialsDownloadUseCase,
                             final AssignEventRoleUseCase assignEventRoleUseCase,
                             final ListEventRolesUseCase listEventRolesUseCase,
                             final RemoveEventRoleUseCase removeEventRoleUseCase,
                             final GetEventDiagramUseCase getEventDiagramUseCase,
                             final SaveEventDiagramUseCase saveEventDiagramUseCase,
                             final GetEventWhiteboardUseCase getEventWhiteboardUseCase,
                             final SaveEventWhiteboardUseCase saveEventWhiteboardUseCase,
                             final GenerateJaasTokenUseCase generateJaasTokenUseCase,
                             final GenerateSeatLayoutUseCase generateSeatLayoutUseCase,
                             final SetSandboxConfigUseCase setSandboxConfigUseCase,
                             final EgressPolicyUseCase egressPolicyUseCase,
                             final SetSandboxInternetUseCase setSandboxInternetUseCase,
                             final EnsureUnassignedSandboxUseCase ensureUnassignedSandboxUseCase,
                             final PrewarmSandboxPoolUseCase prewarmSandboxPoolUseCase,
                             final ResetSandboxUseCase resetSandboxUseCase,
                             final ListSandboxIncidentsUseCase listSandboxIncidentsUseCase,
                             final ListSandboxStatusUseCase listSandboxStatusUseCase,
                             final SetDeviceAccessConfigUseCase setDeviceAccessConfigUseCase,
                             final ListDeviceBlocksUseCase listDeviceBlocksUseCase,
                             final UnblockDeviceUseCase unblockDeviceUseCase,
                             final SandboxHandler sandboxHandler,
                             final SandboxFilesHandler sandboxFilesHandler,
                             final UserRepository userRepository,
                             final DeviceAccessGuard deviceAccessGuard,
                             final GetJaasUsageUseCase getJaasUsageUseCase,
                             final ToolAccessUseCase toolAccessUseCase,
                              final SendAttendeeEmailUseCase sendAttendeeEmailUseCase,
                              final GenerateEmailDraftUseCase generateEmailDraftUseCase,
                              final NotifyConferenceUpdatedUseCase notifyConferenceUpdatedUseCase) {
        this.createConferenceUseCase = createConferenceUseCase;
        this.getConferenceUseCase = getConferenceUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
        this.joinConferenceUseCase = joinConferenceUseCase;
        this.getConferenceHistoryUseCase = getConferenceHistoryUseCase;
        this.generateCertificateUseCase = generateCertificateUseCase;
        this.countAttendeesUseCase = countAttendeesUseCase;
        this.countRegisteredAttendeesUseCase = countRegisteredAttendeesUseCase;
        this.countUniqueRegisteredAttendeesUseCase = countUniqueRegisteredAttendeesUseCase;
        this.countActiveRegisteredAttendeesUseCase = countActiveRegisteredAttendeesUseCase;
        this.updateConferenceUseCase = updateConferenceUseCase;
        this.setConferenceActiveUseCase = setConferenceActiveUseCase;
        this.recordDownloadUseCase = recordDownloadUseCase;
        this.getDownloadCountsUseCase = getDownloadCountsUseCase;
        this.setSeatingModeUseCase = setSeatingModeUseCase;
        this.reserveGeneralUseCase = reserveGeneralUseCase;
        this.getMyTicketUseCase = getMyTicketUseCase;
        this.cancelReservationUseCase = cancelReservationUseCase;
        this.listReservationsUseCase = listReservationsUseCase;
        this.listConferenceAttendeesUseCase = listConferenceAttendeesUseCase;
        this.checkInTicketUseCase = checkInTicketUseCase;
        this.ticketUseCase = ticketUseCase;
        this.eventPermissionGuard = eventPermissionGuard;
        this.createGuestUseCase = createGuestUseCase;
        this.setVenueMapUseCase = setVenueMapUseCase;
        this.defineVenueSeatsUseCase = defineVenueSeatsUseCase;
        this.getConferenceSeatMapUseCase = getConferenceSeatMapUseCase;
        this.reserveSeatUseCase = reserveSeatUseCase;
        this.setEventTypeUseCase = setEventTypeUseCase;
        this.setCanvasConfigUseCase = setCanvasConfigUseCase;
        this.eventCapabilityGuard = eventCapabilityGuard;
        this.getOrCreateEventPadUseCase = getOrCreateEventPadUseCase;
        this.exportEventNotesUseCase = exportEventNotesUseCase;
        this.eventMaterialsDownloadUseCase = eventMaterialsDownloadUseCase;
        this.assignEventRoleUseCase = assignEventRoleUseCase;
        this.listEventRolesUseCase = listEventRolesUseCase;
        this.removeEventRoleUseCase = removeEventRoleUseCase;
        this.getEventDiagramUseCase = getEventDiagramUseCase;
        this.saveEventDiagramUseCase = saveEventDiagramUseCase;
        this.getEventWhiteboardUseCase = getEventWhiteboardUseCase;
        this.saveEventWhiteboardUseCase = saveEventWhiteboardUseCase;
        this.generateJaasTokenUseCase = generateJaasTokenUseCase;
        this.generateSeatLayoutUseCase = generateSeatLayoutUseCase;
        this.setSandboxConfigUseCase = setSandboxConfigUseCase;
        this.egressPolicyUseCase = egressPolicyUseCase;
        this.setSandboxInternetUseCase = setSandboxInternetUseCase;
        this.ensureUnassignedSandboxUseCase = ensureUnassignedSandboxUseCase;
        this.prewarmSandboxPoolUseCase = prewarmSandboxPoolUseCase;
        this.resetSandboxUseCase = resetSandboxUseCase;
        this.listSandboxIncidentsUseCase = listSandboxIncidentsUseCase;
        this.listSandboxStatusUseCase = listSandboxStatusUseCase;
        this.setDeviceAccessConfigUseCase = setDeviceAccessConfigUseCase;
        this.listDeviceBlocksUseCase = listDeviceBlocksUseCase;
        this.unblockDeviceUseCase = unblockDeviceUseCase;
        this.sandboxHandler = sandboxHandler;
        this.sandboxFilesHandler = sandboxFilesHandler;
        this.userRepository = userRepository;
        this.deviceAccessGuard = deviceAccessGuard;
        this.getJaasUsageUseCase = getJaasUsageUseCase;
        this.toolAccessUseCase = toolAccessUseCase;
        this.sendAttendeeEmailUseCase = sendAttendeeEmailUseCase;
        this.generateEmailDraftUseCase = generateEmailDraftUseCase;
        this.notifyConferenceUpdatedUseCase = notifyConferenceUpdatedUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/conferences";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/", Set.of("GET", "POST")),
                Route.of("/public", Set.of("GET")),
                Route.of("/public/{friendlyId}", Set.of("GET")),
                Route.of("/public/{friendlyId}/tickets", Set.of("POST")),
                Route.of("/public/{friendlyId}/flyer", Set.of("GET")),
                Route.of("/by-friendly/{friendlyId}", Set.of("GET")),
                Route.of("/by-friendly/{friendlyId}/jitsi-access", Set.of("GET")),
                Route.of("/by-short/{shortCode}", Set.of("GET")),
                Route.of("/join", Set.of("POST")),
                Route.of("/history", Set.of("GET")),
                Route.of("/tickets/claim", Set.of("POST")),
                Route.of("/attendees/registered-summary", Set.of("GET")),
                Route.of("/attendees/active-summary", Set.of("GET")),
                Route.of("/jaas-usage", Set.of("GET")),
                Route.of("/{id}/certificate", Set.of("GET")),
                Route.of("/{id}/attendees/count", Set.of("GET")),
                Route.of("/{id}/derive-name", Set.of("POST")),
                Route.of("/{id}/downloads", Set.of("POST")),
                Route.of("/{id}/downloads/count", Set.of("GET")),
                Route.of("/{id}/seating", Set.of("PUT")),
                Route.of("/{id}/ticket-sales", Set.of("PUT")),
                Route.of("/{id}/event-type", Set.of("PUT")),
                Route.of("/{id}/flyer", Set.of("POST", "PUT")),
                Route.of("/{id}/canvas-config", Set.of("PUT")),
                Route.of("/{id}/active", Set.of("PUT")),
                Route.of("/{id}/venue-map", Set.of("PUT")),
                Route.of("/{id}/venue-map/generate-seats", Set.of("POST")),
                Route.of("/{id}/sandbox-config", Set.of("PUT")),
                Route.of("/{id}/egress-policy", Set.of("GET", "PUT")),
                Route.of("/{id}/device-access-config", Set.of("PUT")),
                Route.of("/{id}/device-blocks", Set.of("GET")),
                Route.of("/{id}/device-blocks/{blockId}/unblock", Set.of("POST")),
                Route.of("/{id}/sandbox-internet", Set.of("PUT")),
                Route.of("/{id}/sandbox-incidents", Set.of("GET")),
                Route.of("/{id}/sandbox-status", Set.of("GET")),
                Route.of("/{id}/sandbox/prewarm", Set.of("POST")),
                Route.of("/{id}/sandbox/{sandboxUuid}/delete", Set.of("POST")),
                Route.of("/{id}/sandbox/{sandboxUuid}/recreate", Set.of("POST")),
                Route.of("/{id}/sandbox", Set.of("GET")),
                Route.of("/{id}/sandbox/stream", Set.of("GET")),
                Route.of("/{id}/sandbox/availability", Set.of("GET")),
                Route.of("/{id}/sandbox/download", Set.of("POST")),
                Route.of("/{id}/sandbox/preview", Set.of("POST")),
                Route.of("/{id}/sandbox/preview/{publicationId}", Set.of("DELETE")),
                Route.of("/{id}/sandbox/app-preview", Set.of("POST")),
                Route.of("/{id}/sandbox/app-preview/{publicationId}", Set.of("DELETE")),
                Route.of("/{id}/sandbox/files", Set.of("GET")),
                Route.of("/{id}/sandbox/file", Set.of("GET", "PUT")),
                Route.of("/{id}/seats", Set.of("GET", "PUT")),
                Route.of("/{id}/reservations", Set.of("GET", "POST")),
                Route.of("/{id}/attendees", Set.of("GET")),
                Route.of("/{id}/attendees/email", Set.of("POST")),
                Route.of("/{id}/email/draft", Set.of("POST")),
                Route.of("/{id}/reservations/me", Set.of("GET", "DELETE")),
                Route.of("/{id}/access", Set.of("GET")),
                Route.of("/{id}/survey-management-access", Set.of("GET")),
                Route.of("/{id}/presentation-access", Set.of("GET")),
                Route.of("/{id}/reservations/check-in", Set.of("POST")),
                Route.of("/{id}/tickets", Set.of("GET", "POST")),
                Route.of("/{id}/tickets/me", Set.of("GET")),
                Route.of("/{id}/tickets/claim", Set.of("POST")),
                Route.of("/{id}/tickets/check-in", Set.of("POST")),
                Route.of("/{id}/tickets/{ticketUuid}/revoke", Set.of("POST")),
                Route.of("/{id}/tickets/{ticketUuid}/resend", Set.of("POST")),
                Route.of("/{id}/tickets/resend-all", Set.of("POST")),
                Route.of("/{id}/notes", Set.of("GET")),
                Route.of("/{id}/notes/export", Set.of("GET")),
                Route.of("/{id}/materials.zip", Set.of("GET")),
                Route.of("/{id}/diagram/stream", Set.of("GET")),
                Route.of("/{id}/diagram", Set.of("GET", "PUT")),
                Route.of("/{id}/whiteboard/stream", Set.of("GET")),
                Route.of("/{id}/whiteboard", Set.of("GET", "PUT")),
                Route.of("/{id}/jaas-token", Set.of("GET")),
                Route.of("/{id}/video-session/stream", Set.of("GET")),
                Route.of("/{id}/video-session/takeover", Set.of("POST")),
                Route.of("/{id}/roles", Set.of("GET", "POST")),
                Route.of("/{id}/roles/{userUuid}", Set.of("DELETE")),
                Route.of("/{id}/tool-access", Set.of("GET")),
                Route.of("/{id}/tool-access/management", Set.of("GET")),
                Route.of("/{id}/tool-access/release-all", Set.of("POST")),
                Route.of("/{id}/tool-access/{toolKey}/release", Set.of("POST")),
                Route.of("/{id}/tool-access/{toolKey}/lock", Set.of("POST")),
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
        if (path.endsWith("/public")) return handlePublicList(jx);
        // Debe preceder al catch-all de /public/{friendlyId}: sirve la imagen binaria del flyer
        // (no el JSON del evento) para que exista una URL http(s) real que WhatsApp/Telegram
        // puedan usar como og:image -- el JSON solo trae flyerBase64 inline, que esas apps no
        // aceptan como imagen de preview.
        if (path.contains("/public/") && path.endsWith("/flyer")) {
            return handlePublicFlyer(jx, jx.pathParam("friendlyId"));
        }
        if (path.contains("/public/") && !path.endsWith("/tickets")) {
            return handlePublicDetail(jx, jx.pathParam("friendlyId"));
        }
        // Must precede the generic /by-friendly route: this is an authorization decision,
        // not a public conference lookup.
        if (path.endsWith("/jitsi-access")) {
            return handleJitsiInviteAccess(jx, jx.pathParam("friendlyId"));
        }
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
        if (path.endsWith("/attendees/active-summary")) {
            return handleActiveAttendeesSummary(jx);
        }
        if (path.endsWith("/jaas-usage")) return handleJaasUsage(jx);
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
        if (path.endsWith("/access")) return handleAccess(jx, jx.pathParam("id"));
        if (path.endsWith("/survey-management-access")) {
            return handleSurveyManagementAccess(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/presentation-access")) return handlePresentationAccess(jx, jx.pathParam("id"));
        if (path.endsWith("/tickets/me")) return handleGetMyIssuedTicket(jx, jx.pathParam("id"));
        if (path.endsWith("/tickets")) return handleListTickets(jx, jx.pathParam("id"));
        if (path.endsWith("/reservations")) {
            return handleListReservations(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/attendees")) {
            return handleListConferenceAttendees(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/seats")) {
            return handleGetSeatMap(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/egress-policy")) {
            return handleGetEgressPolicy(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/notes")) {
            return handleGetNotes(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/notes/export")) {
            return handleExportNotes(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/materials.zip")) {
            return handleMaterialsDownload(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/diagram/stream")) {
            return handleDiagramStream(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/diagram")) {
            return handleGetDiagram(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/whiteboard/stream")) {
            return handleWhiteboardStream(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/whiteboard")) {
            return handleGetWhiteboard(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/jaas-token")) {
            return handleGetJaasToken(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/video-session/stream")) {
            return handleVideoSessionStream(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/roles")) {
            return handleListEventRoles(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/tool-access/management")) {
            return handleToolAccessManagement(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/tool-access")) {
            return handleToolAccess(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/sandbox-incidents")) {
            return handleListSandboxIncidents(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/sandbox-status")) {
            return handleListSandboxStatus(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/device-blocks")) {
            return handleListDeviceBlocks(jx, jx.pathParam("id"));
        }
        if (path.endsWith("/sandbox/availability")) {
            return sandboxHandler.get(x);
        }
        if (path.endsWith("/sandbox/stream")) {
            return sandboxHandler.get(x);
        }
        if (path.endsWith("/sandbox/files") || path.endsWith("/sandbox/file")) {
            return sandboxFilesHandler.get(x);
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
        // The route includes the friendly id between /public and /tickets.
        // Checking for the exact suffix "/public/tickets" skipped this handler and
        // let the generic /{id}/tickets branch receive a null id, resulting in
        // conference_not_found when confirming a public free ticket.
        if (jx.path().contains("/public/") && jx.path().endsWith("/tickets")) {
            return handlePublicTicket(jx, jx.pathParam("friendlyId"));
        }
        if (jx.path().endsWith("/join")) {
            return handleJoin(jx);
        }
        if (jx.path().endsWith("/tickets/claim") && jx.pathParam("id") == null) {
            return handleClaimTicketByCode(jx);
        }
        if (jx.path().endsWith("/derive-name")) {
            return handleDeriveName(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/flyer")) {
            return handleUploadFlyer(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/downloads")) {
            return handleRecordDownload(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/reservations/check-in")) {
            return handleCheckIn(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/tickets/claim")) return handleClaimTicket(jx, jx.pathParam("id"));
        if (jx.path().endsWith("/tickets/check-in")) return handleTicketCheckIn(jx, jx.pathParam("id"));
        if (jx.path().endsWith("/revoke")) return handleRevokeTicket(jx, jx.pathParam("id"), jx.pathParam("ticketUuid"));
        if (jx.path().endsWith("/tickets/resend-all")) return handleResendAllTickets(jx, jx.pathParam("id"));
        if (jx.path().endsWith("/resend")) return handleResendTicket(jx, jx.pathParam("id"), jx.pathParam("ticketUuid"));
        if (jx.path().endsWith("/venue-map/generate-seats")) {
            return handleGenerateSeatLayout(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/attendees/email")) {
            return handleSendAttendeeEmail(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/email/draft")) {
            return handleGenerateEmailDraft(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/reservations")) {
            return handleReserve(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/tickets")) return handleIssueTicket(jx, jx.pathParam("id"));
        if (jx.path().endsWith("/roles")) {
            return handleAssignEventRole(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/tool-access/release-all")) {
            return handleReleaseAllTools(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/release") && jx.path().contains("/tool-access/")) {
            return handleReleaseTool(jx, jx.pathParam("id"), jx.pathParam("toolKey"));
        }
        if (jx.path().endsWith("/lock") && jx.path().contains("/tool-access/")) {
            return handleLockTool(jx, jx.pathParam("id"), jx.pathParam("toolKey"));
        }
        if (jx.path().endsWith("/sandbox/download")) {
            return sandboxHandler.post(x);
        }
        if (jx.path().endsWith("/sandbox/preview")) {
            return sandboxHandler.post(x);
        }
        if (jx.path().endsWith("/sandbox/app-preview")) {
            return sandboxHandler.post(x);
        }
        if (jx.path().endsWith("/sandbox/prewarm")) {
            return handlePrewarmSandboxPool(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/video-session/takeover")) {
            return handleVideoSessionTakeover(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox/delete")) {
            return handleResetSandbox(jx, jx.pathParam("id"), jx.pathParam("sandboxUuid"), false);
        }
        if (jx.path().endsWith("/sandbox/recreate")) {
            return handleResetSandbox(jx, jx.pathParam("id"), jx.pathParam("sandboxUuid"), true);
        }
        if (jx.path().endsWith("/unblock")) {
            return handleUnblockDevice(jx, jx.pathParam("id"), jx.pathParam("blockId"));
        }
        return handleCreate(jx);
    }

    @Override
    public boolean delete(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().contains("/sandbox/preview/")) {
            return sandboxHandler.delete(x);
        }
        if (jx.path().contains("/sandbox/app-preview/")) {
            return sandboxHandler.delete(x);
        }
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
        if (jx.path().endsWith("/flyer")) {
            return handleUploadFlyer(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/seating")) {
            return handleSetSeating(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/ticket-sales")) {
            return handleSetTicketSales(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/event-type")) {
            return handleSetEventType(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/canvas-config")) {
            return handleSetCanvasConfig(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/active")) {
            return handleSetActive(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/venue-map")) {
            return handleSetVenueMap(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox-config")) {
            return handleSetSandboxConfig(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/egress-policy")) {
            return handleSetEgressPolicy(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/device-access-config")) {
            return handleSetDeviceAccessConfig(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox/file")) {
            return sandboxFilesHandler.put(x);
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
        if (jx.path().endsWith("/whiteboard")) {
            return handleSaveWhiteboard(jx, jx.pathParam("id"));
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
            final Integer capacity = body.get("capacity") instanceof Number n ? n.intValue() : null;
            final List<CanvasConfig> canvasConfigs = parseCanvasConfigs(body.get("canvasConfigs"));
            final var result = createConferenceUseCase.execute(new CreateConferenceUseCase.CreateRequest(
                    (String) body.get("name"), (String) body.get("displayName"), v.subjectUuid(),
                    (String) body.get("expiresAt"),
                    latitude, longitude, (String) body.get("eventDate"), (String) body.get("venue"),
                    (String) body.get("startTime"), (String) body.get("endTime"), timezoneId,
                    (String) body.get("eventTypeKey"), capacity,
                    (String) body.get("ticketPrice"), (String) body.get("ticketCurrency"),
                    (String) body.get("canvasTool"), (String) body.get("canvasAudienceMode"), canvasConfigs,
                    (String) body.get("certificateEngine"), (String) body.get("description"),
                    (String) body.get("visibility"), (String) body.get("scheduleMarkdown"),
                    (String) body.get("scheduleLayout"), (String) body.get("publicTheme")));
            sendOk(jx, 201, result);
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    // AUD-01: el agregado completo (config de sandboxes, XML de drawio, escena de Excalidraw,
    // motor de certificados, etc.) respondia sin credenciales por estas dos rutas. by-id lo usan
    // exclusivamente pantallas autenticadas del dashboard/moderacion (todas mandan token hoy);
    // by-friendly la usa la pagina publica del evento (ConferencePage.vue) SIN token para
    // cualquier visitante -- por eso ahi el agregado se sanitiza siempre, y by-id exige sesion.

    private boolean handleGetById(final JettyHttpExchange jx, final String id) {
        // Ademas del token de usuario, se acepta X-Internal-Auth: otros servicios (survey,
        // moderation) llaman esta ruta sin sesion de usuario para resolver isConferenceOwner.
        if (!validInternalAuth(jx)) {
            final String token = extractToken(jx);
            if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
            try {
                final var v = validateTokenUseCase.execute(token);
                if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            } catch (final Exception e) {
                sendError(jx, 500, "internal_error", e.getMessage());
                return true;
            }
        }
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
                    c -> sendOk(jx, 200, sanitizeForPublicAggregate(c)),
                    () -> sendError(jx, 404, "conference_not_found", "Conference not found"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    /**
     * Quita del agregado los campos operativos/internos (config de sandboxes, control de
     * dispositivos, motor de certificados, XML editable de drawio, escena nativa de Excalidraw)
     * antes de servirlo por una ruta sin autenticación -- ver AUD-01. Las versiones "publicadas"
     * (SVG) de drawio/whiteboard SÍ son para el público, se conservan.
     */
    private Conference sanitizeForPublicAggregate(final Conference c) {
        c.setSandboxVariant(null);
        c.setSandboxPoolSize(null);
        c.setSandboxCliPoolSize(null);
        c.setSandboxInternetEnabled(null);
        c.setSandboxRemoteGitUrl(null);
        c.setSandboxJvmHeapMb(null);
        c.setSandboxSeatsPerPod(null);
        c.setMaxDevicesPerUser(null);
        c.setMaxAccountsPerDevice(null);
        c.setCertificateEngine(null);
        c.setDiagramXml(null);
        c.setWhiteboardSceneAndPublishedSvg(null, c.getWhiteboardPublishedSvg());
        return c;
    }

    /** DTO público reducido: no expone sandboxes, tokens ni configuración interna. */
    private record PublicConferenceView(String friendlyId, String name, String description,
                                        String organizer, String eventDate, String startTime,
                                        String endTime, String venue, Double latitude, Double longitude,
                                        Integer capacity, Integer remainingSeats, boolean ticketRequired,
                                        boolean ticketPurchaseEnabled, String visibility, String flyerBase64,
                                        String scheduleMarkdown, String scheduleLayout,
                                        String publicTheme, String organizerPhotoBase64,
                                        String ticketPrice, String ticketCurrency,
                                        boolean hasTicket) {}

    private PublicConferenceView publicView(final Conference conference) {
        return publicView(conference, null);
    }

    /**
     * Public event data may be requested with an optional user session. Only the
     * boolean access hint is added for that session; ticket identifiers and
     * claimant data never become part of the public representation.
     */
    private PublicConferenceView publicView(final Conference conference, final String userUuid) {
        final boolean ticketRequired = ticketUseCase != null && ticketUseCase.isTicketed(conference);
        // Para eventos con boletos, el aforo publico se basa en boletos realmente reclamados
        // (CLAIMED/CHECKED_IN/operativos), no en reservedCount -- ese contador sube apenas se
        // EMITE un boleto (incluidos lotes sin reclamar) porque gatea el sobre-cupo al emitir.
        // Ver TicketUseCase.countOccupiedSeats(). Eventos sin ticketing (reserva directa GENERAL/
        // SEATED) no tienen boletos "sin reclamar" -- ahi reservedCount ya refleja ocupacion real.
        final int occupied = ticketRequired ? ticketUseCase.countOccupiedSeats(conference.getUuid())
                : conference.getReservedCount();
        final Integer remaining = conference.getCapacity() == null ? null
                : Math.max(0, conference.getCapacity() - occupied);
        final boolean hasTicket = userUuid != null && ticketUseCase != null
                && ticketUseCase.myTicket(conference.getUuid(), userUuid).isPresent();
        final String organizer = userRepository.findByUuid(conference.getCreatedByUserUuid())
                .map(user -> user.getDisplayName())
                .filter(name -> name != null && !name.isBlank())
                .orElse("Organizador del evento");
        return new PublicConferenceView(conference.getFriendlyId(), conference.getName(),
                conference.getDescription(), organizer, conference.getEventDate(),
                conference.getStartTime(), conference.getEndTime(), conference.getVenue(),
                conference.getLatitude(), conference.getLongitude(), conference.getCapacity(), remaining,
                ticketRequired, ticketRequired && ("PUBLIC".equals(conference.getVisibility())
                        || "HYBRID".equals(conference.getVisibility())) && conference.isTicketSalesEnabled(), conference.getVisibility(),
                conference.getFlyerBase64(), conference.getScheduleMarkdown(), conference.getScheduleLayout(),
                conference.getPublicTheme(),
                userRepository.findByUuid(conference.getCreatedByUserUuid())
                        .map(user -> user.getPublicProfilePhotoBase64()).orElse(null),
                conference.getTicketPrice(), conference.getTicketCurrency(), hasTicket);
    }

    private boolean handlePublicList(final JettyHttpExchange jx) {
        try {
            final String userUuid = optionalAuthenticatedUserUuid(jx);
            sendOk(jx, 200, getConferenceUseCase.publicEvents().stream()
                    .map(conference -> publicView(conference, userUuid)).toList());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handlePublicDetail(final JettyHttpExchange jx, final String friendlyId) {
        try {
            final String userUuid = optionalAuthenticatedUserUuid(jx);
            getConferenceUseCase.resolveAny(publicIdentifier(jx, friendlyId))
                    .filter(c -> "PUBLIC".equals(c.getVisibility()) || "HYBRID".equals(c.getVisibility()))
                    .filter(c -> c.getStatus() == ConferenceStatus.ACTIVE)
                    .ifPresentOrElse(c -> sendOk(jx, 200, publicView(c, userUuid)),
                            () -> sendError(jx, 404, "public_event_not_found", "Evento público no encontrado"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private static final Pattern FLYER_DATA_URL = Pattern.compile(
            "^data:(image/jpeg|image/png);base64,(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Imagen binaria del flyer, sin auth, para eventos públicos -- pensada como {@code og:image}
     * de la página de previsualización que arma insightbloom-presentations al compartir el link
     * del evento (WhatsApp/Telegram no aceptan data: URIs, necesitan una URL http(s) real).
     */
    private boolean handlePublicFlyer(final JettyHttpExchange jx, final String friendlyId) {
        try {
            final Conference conference = getConferenceUseCase.resolveAny(publicIdentifier(jx, friendlyId))
                    .filter(c -> "PUBLIC".equals(c.getVisibility()) || "HYBRID".equals(c.getVisibility()))
                    .filter(c -> c.getStatus() == ConferenceStatus.ACTIVE)
                    .orElse(null);
            final String dataUrl = conference == null ? null : conference.getFlyerBase64();
            final Matcher matcher = dataUrl == null ? null : FLYER_DATA_URL.matcher(dataUrl);
            if (matcher == null || !matcher.matches()) {
                sendError(jx, 404, "flyer_not_found", "Este evento no tiene flyer");
                return true;
            }
            final byte[] bytes = java.util.Base64.getDecoder().decode(matcher.group(2));
            jx.response().setStatus(200);
            jx.response().getHeaders().put("Content-Type", matcher.group(1).toLowerCase(java.util.Locale.ROOT));
            jx.response().getHeaders().put("Cache-Control", "public, max-age=3600");
            jx.response().write(true, ByteBuffer.wrap(bytes), jx.callback());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    /** Invalid or guest sessions do not make an otherwise public request fail. */
    private String optionalAuthenticatedUserUuid(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null || token.isBlank()) return null;
        try {
            final var validation = validateTokenUseCase.execute(token);
            return validation.valid() && !"guest".equalsIgnoreCase(validation.role())
                    ? validation.subjectUuid() : null;
        } catch (final Exception ignored) {
            return null;
        }
    }

    /** Solicitud de boleto público: requiere sesión para ligar el boleto a una cuenta. */
    private boolean handlePublicTicket(final JettyHttpExchange jx, final String friendlyId) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Inicia sesión para solicitar un boleto"); return true; }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid() || "guest".equalsIgnoreCase(validation.role())) {
                sendError(jx, 403, "login_required", "Se requiere una cuenta para solicitar el boleto");
                return true;
            }
            final Conference conference = getConferenceUseCase.resolveAny(publicIdentifier(jx, friendlyId))
                    .filter(c -> "PUBLIC".equals(c.getVisibility()) || "HYBRID".equals(c.getVisibility()))
                    .filter(c -> c.getStatus() == ConferenceStatus.ACTIVE)
                    .orElseThrow(() -> new IllegalArgumentException("public_event_not_found"));
            if (ticketUseCase == null || !ticketUseCase.isTicketed(conference)) {
                sendError(jx, 409, "ticket_not_required", "Este evento no requiere boleto");
                return true;
            }
            if (!conference.isTicketSalesEnabled()) {
                sendError(jx, 409, "ticket_sales_closed", "La emisión de boletos está cerrada para este evento");
                return true;
            }
            if (!conference.isFreeTicket()) {
                sendError(jx, 402, "payment_required",
                        "Este evento requiere pago. La integración de pagos aún no está habilitada");
                return true;
            }
            final var existing = ticketUseCase.myTicket(conference.getUuid(), validation.subjectUuid());
            // Antes de emitir un boleto nuevo, se intenta reclamar uno anonimo pre-emitido por
            // el organizador (issueBatch) que nadie haya reclamado todavia -- ver
            // TicketUseCase.claimAnyAvailable. Solo si no queda ninguno libre se emite uno.
            final var ticket = existing.orElseGet(() -> ticketUseCase
                    .claimAnyAvailable(conference.getUuid(), validation.subjectUuid())
                    .orElseGet(() -> {
                        final var issued = ticketUseCase.issue(conference.getUuid(), validation.subjectUuid(), null, null);
                        return ticketUseCase.claim(conference.getUuid(), issued.getTicketCode(), validation.subjectUuid());
                    }));
            sendOk(jx, 201, ticket);
        } catch (final IllegalStateException e) {
            final int status = "capacity_exceeded".equals(e.getMessage()) ? 409 : 400;
            sendError(jx, status, e.getMessage(), e.getMessage());
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    /** Acepta friendly id, UUID o short code aunque un cliente antiguo construya la ruta con otro identificador. */
    private static String publicIdentifier(final JettyHttpExchange jx, final String fallback) {
        final String routeId = jx.pathParam("friendlyId");
        return routeId == null || routeId.isBlank() ? fallback : routeId;
    }

    /** Authorizes the custom JaaS invite without issuing or exposing a provider JWT. */
    private boolean handleJitsiInviteAccess(final JettyHttpExchange jx, final String friendlyId) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Inicia sesión para acceder a la videollamada");
            return true;
        }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid() || !"user".equalsIgnoreCase(validation.kind())) {
                sendError(jx, 401, "token_invalid", "Necesitas una sesión de usuario válida");
                return true;
            }
            final var conference = getConferenceUseCase.byFriendlyId(friendlyId);
            if (conference.isEmpty()) {
                sendError(jx, 404, "conference_not_found", "Evento no encontrado");
                return true;
            }
            final Conference event = conference.get();
            if (rejectIfConferenceClosed(jx, event)) return true;
            if (!eventCapabilityGuard.hasCapability(event, EventCapability.VIDEO_CONFERENCE)) {
                sendError(jx, 409, "capability_not_available", "El evento no habilita videollamada");
                return true;
            }
            final boolean ticketRequired = ticketUseCase != null && ticketUseCase.isTicketed(event);
            // The creator and assigned moderators receive protected operational tickets, so this
            // check remains counted and auditable instead of using an uncounted staff bypass.
            if (ticketRequired && !ticketUseCase.hasAccess(event, validation.subjectUuid())) {
                sendError(jx, 403, "ticket_required", "Regístrate y canjea un boleto para entrar");
                return true;
            }
            sendOk(jx, 200, Map.of(
                    "allowed", true,
                    "conferenceId", event.getUuid(),
                    "friendlyId", event.getFriendlyId(),
                    "ticketRequired", ticketRequired));
        } catch (final Exception e) {
            LOGGER.warning("jitsi_invite_access_failed friendlyId=" + friendlyId + " error=" + e.getMessage());
            sendError(jx, 500, "internal_error", "No se pudo validar el acceso a la videollamada");
        }
        return true;
    }

    private boolean handleGetByShortCode(final JettyHttpExchange jx, final String shortCode) {
        try {
            // AUD-01: mismo tratamiento que by-friendly -- ruta publica sin token, agregado
            // sanitizado (ver sanitizeForPublicAggregate).
            getConferenceUseCase.byShortCode(shortCode).ifPresentOrElse(
                    c -> sendOk(jx, 200, sanitizeForPublicAggregate(c)),
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
            final var resolvedConference = getConferenceUseCase.resolveAny(identifier);
            if (resolvedConference.isEmpty()) {
                sendError(jx, 404, "conference_not_found", "Esta conferencia ya no se encuentra disponible");
                return true;
            }
            if (rejectIfConferenceClosed(jx, resolvedConference.get())) return true;
            final var joinedConference = joinConferenceUseCase.execute(v.subjectUuid(), identifier);
            sendOk(jx, 200, joinedConference);
        } catch (final IllegalStateException e) {
            if ("ticket_required".equals(e.getMessage())) {
                sendError(jx, 403, "ticket_required", "Necesitas canjear un boleto para acceder a este evento");
            } else {
                sendError(jx, 409, e.getMessage(), e.getMessage());
            }
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
            if (!canViewAttendeeSummary(v)) {
                sendError(jx, 403, "forbidden", "Only organizers can view attendee counts");
                return true;
            }
            sendOk(jx, Map.of("uniqueRegisteredAttendees", countUniqueRegisteredAttendeesUseCase.execute(v.subjectUuid())));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    /** Conteo de personas únicas registradas en alguna conferencia del organizador cuyo status es ACTIVE. */
    private boolean handleActiveAttendeesSummary(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!canViewAttendeeSummary(v)) {
                sendError(jx, 403, "forbidden", "Only organizers can view attendee counts");
                return true;
            }
            sendOk(jx, Map.of("activeRegisteredAttendees", countActiveRegisteredAttendeesUseCase.execute(v.subjectUuid())));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleJaasUsage(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid() || !isOrganizerOrAdmin(validation.role())) {
                sendError(jx, 403, "forbidden", "Solo organizadores y administradores pueden ver este contador");
                return true;
            }
            sendOk(jx, 200, getJaasUsageUseCase.execute());
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

    private boolean handleUploadFlyer(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid()) {
                sendError(jx, 401, "token_invalid", "Invalid token");
                return true;
            }
            final MultipartFlyer upload = readFlyerMultipart(jx);
            // Keep a JSON part in the contract so the upload can grow without putting metadata
            // back into the large conference JSON document. Currently only kind and filename
            // are accepted; the server never trusts either value for storage or authorization.
            JsonUtils.codec().readValue(upload.metadataJson(), Map.class);
            final var updated = updateConferenceUseCase.updateFlyer(
                    id, validation.subjectUuid(), upload.bytes(), upload.contentType());
            if (updated.isPresent()) {
                sendOk(jx, 200, updated.get());
            } else {
                sendError(jx, 404, "not_found", "Conference not found or not owned by you");
            }
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final IOException e) {
            sendError(jx, 400, "invalid_multipart", "El multipart del flyer no es válido");
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private record MultipartFlyer(String metadataJson, byte[] bytes, String contentType) {}

    private MultipartFlyer readFlyerMultipart(final JettyHttpExchange jx) throws IOException {
        final String contentType = jx.request().getHeaders().get("Content-Type");
        if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT)
                .startsWith("multipart/form-data")) {
            throw new IllegalArgumentException("flyer_multipart_required");
        }
        final Matcher boundaryMatcher = MULTIPART_BOUNDARY.matcher(contentType);
        if (!boundaryMatcher.find()) throw new IllegalArgumentException("multipart_boundary_missing");
        final String boundary = boundaryMatcher.group(1) != null
                ? boundaryMatcher.group(1) : boundaryMatcher.group(2);
        if (boundary == null || boundary.length() > 120) {
            throw new IllegalArgumentException("multipart_boundary_invalid");
        }
        final int declaredLength;
        try {
            final String lengthHeader = jx.request().getHeaders().get("Content-Length");
            declaredLength = lengthHeader == null ? -1 : Integer.parseInt(lengthHeader);
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("multipart_length_invalid");
        }
        if (declaredLength > MAX_FLYER_REQUEST_BYTES) {
            throw new IllegalArgumentException("flyer_too_large");
        }
        final byte[] body = readLimited(Request.asInputStream(jx.request()), MAX_FLYER_REQUEST_BYTES);
        final String bodyText = new String(body, StandardCharsets.ISO_8859_1);
        final String delimiter = "--" + boundary;
        int cursor = bodyText.indexOf(delimiter);
        if (cursor < 0) throw new IllegalArgumentException("multipart_boundary_invalid");
        String metadata = null;
        byte[] file = null;
        String fileType = null;
        while (cursor >= 0) {
            int headersStart = cursor + delimiter.length();
            if (bodyText.startsWith("--", headersStart)) break;
            if (bodyText.startsWith("\r\n", headersStart)) headersStart += 2;
            final int headerEnd = bodyText.indexOf("\r\n\r\n", headersStart);
            if (headerEnd < 0) throw new IllegalArgumentException("multipart_headers_invalid");
            final String headers = bodyText.substring(headersStart, headerEnd);
            final int contentStart = headerEnd + 4;
            final int nextBoundary = bodyText.indexOf("\r\n" + delimiter, contentStart);
            if (nextBoundary < 0) throw new IllegalArgumentException("multipart_boundary_invalid");
            final MultipartHeaders partHeaders = MultipartHeaders.parse(headers);
            final byte[] part = java.util.Arrays.copyOfRange(body, contentStart, nextBoundary);
            if ("metadata".equals(partHeaders.name())) {
                if (part.length > 16_384) throw new IllegalArgumentException("multipart_metadata_too_large");
                metadata = new String(part, StandardCharsets.UTF_8);
            } else if ("file".equals(partHeaders.name())) {
                if (file != null) throw new IllegalArgumentException("flyer_multiple_files");
                if (part.length == 0 || part.length > MAX_FLYER_BYTES) {
                    throw new IllegalArgumentException("flyer_too_large");
                }
                file = part;
                fileType = partHeaders.contentType();
            }
            cursor = nextBoundary + 2;
        }
        if (metadata == null || file == null || fileType == null) {
            throw new IllegalArgumentException("flyer_file_and_metadata_required");
        }
        return new MultipartFlyer(metadata, file, fileType);
    }

    private static byte[] readLimited(final java.io.InputStream input, final int maxBytes) throws IOException {
        try (input) {
            final ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
            final byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IllegalArgumentException("flyer_too_large");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private record MultipartHeaders(String name, String contentType) {
        private static final Pattern NAME = Pattern.compile(
                "(?:^|;)\\s*name=\\\"([^\\\"]+)\\\"", Pattern.CASE_INSENSITIVE);

        static MultipartHeaders parse(final String raw) {
            final Matcher nameMatcher = NAME.matcher(raw.replace("\r\n", ";"));
            if (!nameMatcher.find()) throw new IllegalArgumentException("multipart_name_missing");
            final String type = raw.lines()
                    .filter(line -> line.toLowerCase(java.util.Locale.ROOT).startsWith("content-type:"))
                    .map(line -> line.substring(line.indexOf(':') + 1).trim())
                    .findFirst().orElse("");
            return new MultipartHeaders(nameMatcher.group(1), type);
        }
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
            final Conference before = getConferenceUseCase.byId(id).orElse(null);
            final var updated = updateConferenceUseCase.execute(id, v.subjectUuid(),
                    new UpdateConferenceUseCase.UpdateRequest((String) body.get("displayName"),
                            (String) body.get("venue"), (String) body.get("eventDate"),
                            (String) body.get("startTime"), (String) body.get("endTime"), latitude, longitude,
                            (String) body.get("presentationSourceUrl"), (String) body.get("flyerBase64"), timezoneId,
                            (String) body.get("description"), (String) body.get("visibility"),
                            (String) body.get("scheduleMarkdown"), (String) body.get("scheduleLayout"),
                            (String) body.get("publicTheme"), (String) body.get("ticketPrice"),
                            (String) body.get("ticketCurrency")));
            if (updated.isPresent()) {
                sendOk(jx, 200, updated.get());
                if (before != null) {
                    try {
                        notifyConferenceUpdatedUseCase.execute(before, updated.get());
                    } catch (final Exception notifyError) {
                        LOGGER.warning("No se pudo notificar el cambio del evento " + id + ": " + notifyError.getMessage());
                    }
                }
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
            if (!v.valid()) {
                // An expired/revoked token is an authentication failure. Returning 401 lets the
                // frontend clear the stale session instead of presenting a misleading certificate
                // generation error while retaining an invalid token in the tab.
                sendError(jx, 401, "token_invalid", "Tu sesión ya no es válida. Inicia sesión nuevamente");
                return true;
            }
            if ("guest".equals(v.kind())) {
                sendError(jx, 403, "certificate_user_required", "Debes iniciar sesión con una cuenta para descargar el certificado");
                return true;
            }
            final var result = generateCertificateUseCase.execute(conferenceId, v.subjectUuid(), token);
            recordDownloadUseCase.execute(conferenceId, "certificate", v.subjectUuid());
            jx.response().setStatus(200);
            jx.response().getHeaders().put("Content-Type", "application/pdf");
            jx.response().getHeaders().put("Content-Disposition", "attachment; filename=\"" + result.fileName() + "\"");
            jx.response().write(true, ByteBuffer.wrap(result.pdfBytes()), jx.callback());
        } catch (final IllegalStateException e) {
            if ("survey_not_completed".equals(e.getMessage())) {
                sendError(jx, 409, e.getMessage(), "Debes completar la encuesta antes de descargar tu certificado");
            } else if (e.getMessage() != null && e.getMessage().startsWith("certificate_renderer_")) {
                // Do not expose renderer internals to attendees and do not classify an upstream
                // rendering failure as an incomplete survey.
                sendError(jx, 502, "certificate_renderer_unavailable", "El certificado no está disponible temporalmente. Intenta nuevamente");
            } else {
                sendError(jx, 500, "certificate_generation_failed", "No se pudo generar el certificado");
            }
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
            if (!canViewConferenceAttendeeCount(conferenceId, v)) {
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
            if (rejectIfConferenceClosed(jx, id)) return true;
            if (!hasCapability(id, EventCapability.COLLAB_NOTES)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita notas colaborativas");
                return true;
            }
            getOrCreateEventPadUseCase.execute(id, v.subjectUuid()).ifPresentOrElse(
                    pad -> sendOk(jx, 200, pad),
                    () -> sendError(jx, 404, "conference_not_found", "Conference not found"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleExportNotes(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            if (rejectIfConferenceClosed(jx, id)) return true;
            if (!hasCapability(id, EventCapability.COLLAB_NOTES)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita notas");
                return true;
            }
            final String format = queryParam(jx, "format");
            final boolean html = "html".equalsIgnoreCase(format);
            final var export = exportEventNotesUseCase.execute(id, v.subjectUuid()).orElseThrow(
                    () -> new IllegalArgumentException("conference_not_found"));
            final byte[] body = (html ? export.html() : export.text()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            jx.response().setStatus(200);
            jx.response().getHeaders().put("Content-Type", html ? "text/html; charset=utf-8" : "text/plain; charset=utf-8");
            jx.response().getHeaders().put("Content-Disposition", "attachment; filename=\"notas-"
                    + (export.individual() ? "individuales" : "grupales") + "." + (html ? "html" : "txt") + "\"");
            jx.response().getHeaders().put("Content-Length", Integer.toString(body.length));
            jx.response().write(true, ByteBuffer.wrap(body), jx.callback());
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), e.getMessage());
        } catch (final IllegalStateException e) {
            sendError(jx, 502, "etherpad_export_failed", "No se pudieron leer las notas");
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleMaterialsDownload(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            final var conference = getConferenceUseCase.byId(id);
            if (conference.isEmpty()) { sendError(jx, 404, "conference_not_found", "Conference not found"); return true; }
            final boolean manager = legacyRoleHasAny(v.role(), "admin")
                    || (isOrganizerOrAdmin(v.role())
                    && conference.get().getCreatedByUserUuid().equals(v.subjectUuid()));
            if (!manager && !ticketUseCase.hasAccess(conference.get(), v.subjectUuid())) {
                sendError(jx, 403, "forbidden", "No tienes acceso a los materiales de este evento");
                return true;
            }
            final byte[] body = eventMaterialsDownloadUseCase.execute(id);
            jx.response().setStatus(200);
            jx.response().getHeaders().put("Content-Type", "application/zip");
            jx.response().getHeaders().put("Content-Disposition", "attachment; filename=\"event-materials-"
                    + conference.get().getFriendlyId() + ".zip\"");
            jx.response().getHeaders().put("Content-Length", Integer.toString(body.length));
            jx.response().write(true, ByteBuffer.wrap(body), jx.callback());
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), e.getMessage());
        } catch (final IllegalStateException e) {
            sendError(jx, 502, "materials_export_failed", "No se pudieron preparar los materiales");
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
            if (rejectIfConferenceClosed(jx, id)) return true;
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
            if (rejectIfConferenceClosed(jx, id)) return true;
            if (!hasCapability(id, EventCapability.DIAGRAMMING)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita diagramas");
                return true;
            }
            final var body = parseBody(jx);
            final String xml = (String) body.get("xml");
            final String publishedSvg = body.get("publishedSvg") instanceof String svg ? svg : null;
            if (publishedSvg != null && publishedSvg.length() > 12_000_000) {
                sendError(jx, 413, "diagram_too_large", "La exportacion del diagrama excede el limite permitido");
                return true;
            }
            if (saveEventDiagramUseCase.execute(id, xml != null ? xml : "", publishedSvg, v.subjectUuid())) {
                getEventDiagramUseCase.execute(id).ifPresent(diagram -> publishDiagramUpdate(id, diagram));
                sendOk(jx, 200, java.util.Map.of("saved", true));
            } else {
                sendError(jx, 403, "moderator_only", "Solo el moderador puede guardar el material del lienzo");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleDiagramStream(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            if (rejectIfConferenceClosed(jx, id)) return true;
            if (!hasCapability(id, EventCapability.DIAGRAMMING)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita diagramas");
                return true;
            }
            final var stream = jx.startEventStream();
            final var subscribers = diagramSubscribers.computeIfAbsent(id, ignored -> new CopyOnWriteArrayList<>());
            subscribers.add(stream);
            getEventDiagramUseCase.execute(id).ifPresent(diagram -> stream.send("snapshot", diagramMetadata(diagram)));
            final ScheduledFuture<?> heartbeat = diagramStreamScheduler.scheduleAtFixedRate(
                    () -> stream.comment("ping"), DIAGRAM_STREAM_HEARTBEAT_SECONDS,
                    DIAGRAM_STREAM_HEARTBEAT_SECONDS, TimeUnit.SECONDS);
            stream.onClose(() -> {
                heartbeat.cancel(true);
                subscribers.remove(stream);
                if (subscribers.isEmpty()) diagramSubscribers.remove(id, subscribers);
            });
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private void publishDiagramUpdate(final String id, final GetEventDiagramUseCase.DiagramInfo diagram) {
        final var subscribers = diagramSubscribers.get(id);
        if (subscribers == null || subscribers.isEmpty()) return;
        final String payload = diagramMetadata(diagram);
        subscribers.forEach(stream -> stream.send("update", payload));
    }

    private static String diagramMetadata(final GetEventDiagramUseCase.DiagramInfo diagram) {
        return JsonUtils.toJson(java.util.Map.of(
                "version", diagram.version(),
                "updatedAt", diagram.updatedAt() != null ? diagram.updatedAt().toString() : ""));
    }

    private boolean handleGetWhiteboard(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            if (rejectIfConferenceClosed(jx, id)) return true;
            if (!hasCapability(id, EventCapability.WHITEBOARD)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita pizarra");
                return true;
            }
            getEventWhiteboardUseCase.execute(id).ifPresentOrElse(
                    whiteboard -> sendOk(jx, 200, whiteboard),
                    () -> sendError(jx, 404, "conference_not_found", "Conference not found"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleSaveWhiteboard(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            if (rejectIfConferenceClosed(jx, id)) return true;
            if (!hasCapability(id, EventCapability.WHITEBOARD)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita pizarra");
                return true;
            }
            final var body = parseBody(jx);
            final String sceneJson = body.get("sceneJson") instanceof String scene ? scene : "";
            final String publishedSvg = body.get("publishedSvg") instanceof String svg ? svg : null;
            if (sceneJson.length() > 12_000_000 || (publishedSvg != null && publishedSvg.length() > 12_000_000)) {
                sendError(jx, 413, "whiteboard_too_large", "La pizarra excede el limite permitido");
                return true;
            }
            if (saveEventWhiteboardUseCase.execute(id, sceneJson, publishedSvg, v.subjectUuid())) {
                getEventWhiteboardUseCase.execute(id).ifPresent(whiteboard -> publishWhiteboardUpdate(id, whiteboard));
                sendOk(jx, 200, java.util.Map.of("saved", true));
            } else {
                sendError(jx, 403, "moderator_only", "Solo el moderador puede guardar el material de la pizarra");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleWhiteboardStream(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            if (rejectIfConferenceClosed(jx, id)) return true;
            if (!hasCapability(id, EventCapability.WHITEBOARD)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita pizarra");
                return true;
            }
            final var stream = jx.startEventStream();
            final var subscribers = whiteboardSubscribers.computeIfAbsent(id, ignored -> new CopyOnWriteArrayList<>());
            subscribers.add(stream);
            getEventWhiteboardUseCase.execute(id).ifPresent(whiteboard ->
                    stream.send("snapshot", whiteboardMetadata(whiteboard)));
            final ScheduledFuture<?> heartbeat = diagramStreamScheduler.scheduleAtFixedRate(
                    () -> stream.comment("ping"), DIAGRAM_STREAM_HEARTBEAT_SECONDS,
                    DIAGRAM_STREAM_HEARTBEAT_SECONDS, TimeUnit.SECONDS);
            stream.onClose(() -> {
                heartbeat.cancel(true);
                subscribers.remove(stream);
                if (subscribers.isEmpty()) whiteboardSubscribers.remove(id, subscribers);
            });
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private void publishWhiteboardUpdate(final String id, final GetEventWhiteboardUseCase.WhiteboardInfo whiteboard) {
        final var subscribers = whiteboardSubscribers.get(id);
        if (subscribers == null || subscribers.isEmpty()) return;
        final String payload = whiteboardMetadata(whiteboard);
        subscribers.forEach(stream -> stream.send("update", payload));
    }

    private static String whiteboardMetadata(final GetEventWhiteboardUseCase.WhiteboardInfo whiteboard) {
        return JsonUtils.toJson(java.util.Map.of(
                "version", whiteboard.version(),
                "updatedAt", whiteboard.updatedAt() != null ? whiteboard.updatedAt().toString() : ""));
    }

    private boolean handleGetJaasToken(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            if (rejectIfConferenceClosed(jx, id)) return true;
            if (!hasCapability(id, EventCapability.VIDEO_CONFERENCE)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita videollamada");
                return true;
            }
            final var result = generateJaasTokenUseCase.execute(
                    id, v.subjectUuid(), v.role(), extractDeviceFingerprint(jx));
            if (result instanceof GenerateJaasTokenUseCase.JaasResult.Issued issued) {
                notifyVideoRevocations(id, v.subjectUuid(),
                        issued.token().revokedDeviceFingerprints(), extractDeviceFingerprint(jx));
                sendOk(jx, 200, issued.token());
            } else if (result instanceof GenerateJaasTokenUseCase.JaasResult.Blocked) {
                sendError(jx, 403, "device_blocked", "Este dispositivo fue bloqueado por uso con múltiples cuentas");
            } else if (result instanceof GenerateJaasTokenUseCase.JaasResult.TicketRequired) {
                sendError(jx, 403, "ticket_required", "Registro y boleto requeridos");
            } else if (result instanceof GenerateJaasTokenUseCase.JaasResult.ConferenceNotFound) {
                sendError(jx, 404, "conference_not_found", "Evento no encontrado");
            } else {
                sendError(jx, 404, "jaas_not_configured", "JaaS no esta configurado en este despliegue");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleVideoSessionStream(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            if (rejectIfConferenceClosed(jx, id)) return true;
            if (!hasCapability(id, EventCapability.VIDEO_CONFERENCE)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita videollamada");
                return true;
            }
            final String fingerprint = extractDeviceFingerprint(jx);
            if (fingerprint == null || fingerprint.isBlank()) {
                sendError(jx, 400, "device_fingerprint_missing", "No se pudo identificar este dispositivo");
                return true;
            }
            final EventStream stream = jx.startEventStream();
            final String key = videoSubscriberKey(id, validation.subjectUuid());
            final var subscribers = videoSubscribers.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
            final VideoSubscriber subscriber = new VideoSubscriber(stream, fingerprint);
            subscribers.add(subscriber);
            stream.send("connected", JsonUtils.toJson(Map.of("connected", true)));
            final ScheduledFuture<?> heartbeat = diagramStreamScheduler.scheduleAtFixedRate(
                    () -> stream.comment("ping"), DIAGRAM_STREAM_HEARTBEAT_SECONDS,
                    DIAGRAM_STREAM_HEARTBEAT_SECONDS, TimeUnit.SECONDS);
            stream.onClose(() -> {
                heartbeat.cancel(true);
                subscribers.remove(subscriber);
                if (subscribers.isEmpty()) videoSubscribers.remove(key, subscribers);
            });
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleVideoSessionTakeover(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            if (rejectIfConferenceClosed(jx, id)) return true;
            if (!hasCapability(id, EventCapability.VIDEO_CONFERENCE)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita videollamada");
                return true;
            }
            final String fingerprint = extractDeviceFingerprint(jx);
            if (fingerprint == null || fingerprint.isBlank()) {
                sendError(jx, 400, "device_fingerprint_missing", "No se pudo identificar este dispositivo");
                return true;
            }
            final List<String> revoked = deviceAccessGuard.takeOver(id, validation.subjectUuid(), ToolKind.VIDEO);
            notifyVideoRevocations(id, validation.subjectUuid(), revoked, fingerprint);
            sendOk(jx, 200, Map.of("takenOver", true, "revokedSessions", revoked.size()));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private void notifyVideoRevocations(final String conferenceId, final String userUuid,
                                        final List<String> revokedFingerprints,
                                        final String currentFingerprint) {
        if (revokedFingerprints == null || revokedFingerprints.isEmpty()) return;
        final var subscribers = videoSubscribers.get(videoSubscriberKey(conferenceId, userUuid));
        if (subscribers == null) return;
        final String payload = JsonUtils.toJson(Map.of("reason", "takeover"));
        subscribers.stream()
                .filter(subscriber -> currentFingerprint == null
                        || !currentFingerprint.equals(subscriber.deviceFingerprint()))
                .filter(subscriber -> revokedFingerprints.contains(subscriber.deviceFingerprint()))
                .forEach(subscriber -> subscriber.stream().send("revoked", payload));
    }

    private static String videoSubscriberKey(final String conferenceId, final String userUuid) {
        return conferenceId + ":" + userUuid;
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
            final Conference before = getConferenceUseCase.byId(id).orElse(null);
            final var updated = setEventTypeUseCase.execute(id, v.subjectUuid(), eventTypeKey);
            if (updated.isPresent()) {
                sendOk(jx, 200, updated.get());
                if (before != null) {
                    try {
                        notifyConferenceUpdatedUseCase.execute(before, updated.get());
                    } catch (final Exception notifyError) {
                        LOGGER.warning("No se pudo notificar el cambio del evento " + id + ": " + notifyError.getMessage());
                    }
                }
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

    private boolean handleSetCanvasConfig(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can configure the event canvas");
                return true;
            }
            final var body = parseBody(jx);
            final String canvasTool = (String) body.get("canvasTool");
            final String audienceMode = (String) body.get("canvasAudienceMode");
            final List<CanvasConfig> canvasConfigs = parseCanvasConfigs(body.get("canvasConfigs"));
            if (canvasConfigs != null) {
                for (final CanvasConfig config : canvasConfigs) {
                    final EventCapability required = canvasCapability(config.tool());
                    if (required != null && !hasCapability(id, required)) {
                        sendError(jx, 409, "capability_not_available",
                                "El tipo de evento no habilita una de las herramientas seleccionadas");
                        return true;
                    }
                }
            }
            final EventCapability requiredCapability = canvasTool == null ? null : switch (canvasTool) {
                    case "DRAWIO" -> EventCapability.DIAGRAMMING;
                    case "EXCALIDRAW" -> EventCapability.WHITEBOARD;
                    case "ETHERPAD" -> EventCapability.COLLAB_NOTES;
                    default -> null;
                };
            if (requiredCapability != null && !hasCapability(id, requiredCapability)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita la herramienta seleccionada");
                return true;
            }
            final var updated = canvasConfigs != null
                    ? setCanvasConfigUseCase.execute(id, v.subjectUuid(), canvasConfigs)
                    : setCanvasConfigUseCase.execute(id, v.subjectUuid(), canvasTool, audienceMode);
            if (updated.isPresent()) {
                sendOk(jx, 200, updated.get());
            } else {
                sendError(jx, 404, "not_found", "Conference not found or not owned by you");
            }
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private static List<CanvasConfig> parseCanvasConfigs(final Object raw) {
        if (!(raw instanceof List<?> items)) return null;
        final List<CanvasConfig> configs = new ArrayList<>();
        for (final Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                configs.add(new CanvasConfig(null, null));
                continue;
            }
            final Object tool = map.get("tool");
            final Object mode = map.get("audienceMode");
            configs.add(new CanvasConfig(tool instanceof String ? (String) tool : null,
                    mode instanceof String ? (String) mode : null));
        }
        return configs;
    }

    private static EventCapability canvasCapability(final String tool) {
        if (tool == null) return null;
        return switch (tool) {
            case "DRAWIO" -> EventCapability.DIAGRAMMING;
            case "EXCALIDRAW" -> EventCapability.WHITEBOARD;
            case "ETHERPAD" -> EventCapability.COLLAB_NOTES;
            default -> null;
        };
    }

    private boolean handleSetActive(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Only organizers can activate/deactivate a conference");
                return true;
            }
            final var body = parseBody(jx);
            final boolean active = Boolean.TRUE.equals(body.get("active"));
            final var updated = setConferenceActiveUseCase.execute(id, v.subjectUuid(), active);
            if (updated.isPresent()) {
                sendOk(jx, 200, updated.get());
            } else {
                sendError(jx, 404, "not_found",
                        "Conference not found, not owned by you, or has an expiration date");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleSetTicketSales(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
                sendError(jx, 403, "forbidden", "Solo el creador del evento puede cambiar la emisión de boletos");
                return true;
            }
            final var body = parseBody(jx);
            if (!(body.get("enabled") instanceof Boolean enabled)) {
                sendError(jx, 400, "enabled_required", "Debes indicar enabled como booleano");
                return true;
            }
            updateConferenceUseCase.setTicketSalesEnabled(id, v.subjectUuid(), enabled).ifPresentOrElse(
                    updated -> sendOk(jx, 200, updated),
                    () -> sendError(jx, 404, "not_found", "Evento no encontrado o no es tuyo"));
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

    private boolean canManageTickets(final String conferenceId, final ValidateTokenUseCase.ValidationResult v) {
        if (eventPermissionGuard.hasPermission(conferenceId, v.subjectUuid(), v.role(), Permission.MANAGE_TICKETS)) return true;
        return isOrganizerOrAdmin(v.role()) && getConferenceUseCase.byId(conferenceId)
                .map(c -> c.getCreatedByUserUuid().equals(v.subjectUuid())).orElse(false);
    }

    private boolean canManageSurvey(final String conferenceId, final ValidateTokenUseCase.ValidationResult v) {
        return eventPermissionGuard.hasPermission(conferenceId, v.subjectUuid(), v.role(), Permission.MANAGE_SURVEY)
                || (isOrganizerOrAdmin(v.role()) && getConferenceUseCase.byId(conferenceId)
                .map(c -> c.getCreatedByUserUuid().equals(v.subjectUuid())).orElse(false));
    }

    /** Mismo criterio que la moderación de contenido existente (Dudas/Temas/Editor Monaco). */
    private boolean canManageToolAccess(final String conferenceId, final ValidateTokenUseCase.ValidationResult v) {
        return eventPermissionGuard.hasPermission(conferenceId, v.subjectUuid(), v.role(), Permission.MODERATE_CONTENT)
                || (isOrganizerOrAdmin(v.role()) && getConferenceUseCase.byId(conferenceId)
                .map(c -> c.getCreatedByUserUuid().equals(v.subjectUuid())).orElse(false));
    }

    /** Candado por herramienta resuelto para el usuario autenticado actual (o vacío si es anónimo). */
    private boolean handleToolAccess(final JettyHttpExchange jx, final String id) {
        try {
            final String userUuid = optionalAuthenticatedUserUuid(jx);
            final var released = userUuid == null ? java.util.Set.<ToolKey>of()
                    : toolAccessUseCase.resolveForUser(id, userUuid);
            final Map<String, Boolean> view = new java.util.LinkedHashMap<>();
            for (final ToolKey key : ToolKey.values()) view.put(key.name(), released.contains(key));
            sendOk(jx, view);
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleToolAccessManagement(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !canManageToolAccess(id, v)) {
                sendError(jx, 403, "forbidden", "No tienes permiso para moderar las herramientas de este evento");
                return true;
            }
            final var matrix = toolAccessUseCase.managementView(id);
            final Map<String, Object> view = new java.util.LinkedHashMap<>();
            matrix.forEach((key, tool) -> view.put(key.name(), Map.of(
                    "releasedForAll", tool.releasedForAll(),
                    "attendees", tool.attendees().stream().map(a -> Map.of(
                            "uuid", a.uuid(), "displayName", a.displayName() == null ? "" : a.displayName(),
                            "email", a.email() == null ? "" : a.email(), "released", a.released())).toList())));
            sendOk(jx, view);
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private ToolKey parseToolKey(final String raw) {
        try {
            return ToolKey.valueOf(String.valueOf(raw).toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid_tool_key");
        }
    }

    private boolean handleReleaseTool(final JettyHttpExchange jx, final String id, final String rawToolKey) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !canManageToolAccess(id, v)) {
                sendError(jx, 403, "forbidden", "No tienes permiso para moderar las herramientas de este evento");
                return true;
            }
            final ToolKey toolKey = parseToolKey(rawToolKey);
            final var body = parseBody(jx);
            if (Boolean.TRUE.equals(body.get("all"))) {
                toolAccessUseCase.release(id, toolKey, true, java.util.List.of());
                sendOk(jx, Map.of("releasedForAll", true));
                return true;
            }
            final Object rawUsers = body.get("userUuids");
            if (!(rawUsers instanceof java.util.List<?>)) throw new IllegalArgumentException("user_uuids_required");
            final java.util.List<String> userUuids = ((java.util.List<?>) rawUsers).stream()
                    .filter(String.class::isInstance).map(String.class::cast).distinct().toList();
            toolAccessUseCase.release(id, toolKey, false, userUuids);
            sendOk(jx, Map.of("releasedForAll", toolAccessUseCase.managementView(id).get(toolKey).releasedForAll(),
                    "releasedCount", userUuids.size()));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleLockTool(final JettyHttpExchange jx, final String id, final String rawToolKey) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !canManageToolAccess(id, v)) {
                sendError(jx, 403, "forbidden", "No tienes permiso para moderar las herramientas de este evento");
                return true;
            }
            final ToolKey toolKey = parseToolKey(rawToolKey);
            final var body = parseBody(jx);
            if (Boolean.TRUE.equals(body.get("all"))) {
                toolAccessUseCase.lock(id, toolKey, true, java.util.List.of());
                sendOk(jx, Map.of("releasedForAll", false));
                return true;
            }
            final Object rawUsers = body.get("userUuids");
            if (!(rawUsers instanceof java.util.List<?>)) throw new IllegalArgumentException("user_uuids_required");
            final java.util.List<String> userUuids = ((java.util.List<?>) rawUsers).stream()
                    .filter(String.class::isInstance).map(String.class::cast).distinct().toList();
            toolAccessUseCase.lock(id, toolKey, false, userUuids);
            sendOk(jx, Map.of("lockedCount", userUuids.size()));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    /** Botón de recuperación de un clic: libera las 9 herramientas para todos de una vez. */
    private boolean handleReleaseAllTools(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !canManageToolAccess(id, v)) {
                sendError(jx, 403, "forbidden", "No tienes permiso para moderar las herramientas de este evento");
                return true;
            }
            toolAccessUseCase.releaseAll(id);
            sendOk(jx, Map.of("released", true));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    /** Operational staff do not need an attendee ticket for the conference. */
    private boolean hasOperationalStaffAccess(final String conferenceId,
                                               final ValidateTokenUseCase.ValidationResult v) {
        return legacyRoleHasAny(v.role(), "admin", "organizer", "moderator")
                || canManageTickets(conferenceId, v);
    }

    private boolean canCheckIn(final String conferenceId, final ValidateTokenUseCase.ValidationResult v) {
        return eventPermissionGuard.hasPermission(conferenceId, v.subjectUuid(), v.role(), Permission.CHECK_IN)
                || (isOrganizerOrAdmin(v.role()) && getConferenceUseCase.byId(conferenceId)
                .map(c -> c.getCreatedByUserUuid().equals(v.subjectUuid())).orElse(false));
    }

    private boolean handleIssueTicket(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !canManageTickets(id, v)) { sendError(jx, 403, "forbidden", "No tienes permiso para emitir boletos"); return true; }
            final var conference = getConferenceUseCase.byId(id).orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
            if (!conference.isTicketSalesEnabled()) {
                sendError(jx, 409, "ticket_sales_closed", "La emisión de boletos está cerrada para este evento");
                return true;
            }
            final var body = parseBody(jx);
            final String recipientEmail = (String) body.get("recipientEmail");
            final String seatUuid = (String) body.get("seatUuid");
            final Integer quantity = body.get("quantity") instanceof Number n ? n.intValue() : null;
            if (quantity != null && quantity > 1) {
                if (recipientEmail != null && !recipientEmail.isBlank()) {
                    sendError(jx, 400, "quantity_with_recipient_not_allowed",
                            "No se puede emitir en lote con un destinatario específico; emite uno a la vez para mandarlo por correo.");
                    return true;
                }
                if (seatUuid != null && !seatUuid.isBlank()) {
                    sendError(jx, 400, "quantity_with_seat_not_allowed",
                            "No se puede emitir en lote en un evento con asientos; cada boleto necesita su propio asiento.");
                    return true;
                }
                sendOk(jx, 201, ticketUseCase.issueBatch(id, v.subjectUuid(), quantity));
                return true;
            }
            sendOk(jx, 201, ticketUseCase.issue(id, v.subjectUuid(), recipientEmail, seatUuid));
        } catch (final IllegalStateException e) {
            final String code = e.getMessage() == null ? "ticket_issue_failed" : e.getMessage();
            final String detail = switch (code) {
                case "capacity_exceeded" -> "Se alcanzó el límite de boletos del evento. Aumenta el aforo o libera una plaza disponible.";
                case "capability_not_available" -> "Este evento no tiene habilitada la emisión de boletos.";
                case "seat_required" -> "Debes seleccionar un asiento para este evento.";
                case "seat_not_allowed" -> "Este evento no utiliza asientos; elimina el UUID de asiento.";
                case "conference_expired" -> "El evento ya terminó y no admite nuevos boletos.";
                case "ticket_sales_closed" -> "La emisión de boletos está cerrada para este evento.";
                default -> "No fue posible emitir el boleto. Revisa la configuración y el aforo del evento.";
            };
            sendError(jx, 409, code, detail);
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleListTickets(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !canManageTickets(id, v)) { sendError(jx, 403, "forbidden", "No tienes permiso para consultar boletos"); return true; }
            sendOk(jx, 200, ticketUseCase.listManagement(id));
        } catch (Exception e) { sendError(jx, 500, "internal_error", e.getMessage()); }
        return true;
    }

    private boolean handleGetMyIssuedTicket(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            ticketUseCase.myTicket(id, v.subjectUuid()).ifPresentOrElse(
                    t -> sendOk(jx, 200, t),
                    () -> sendError(jx, 404, "no_ticket", "No tienes un boleto para esta conferencia"));
        } catch (Exception e) { sendError(jx, 500, "internal_error", e.getMessage()); }
        return true;
    }

    private boolean handleAccess(final JettyHttpExchange jx, final String id) {
        try {
            final var conference = getConferenceUseCase.byId(id).orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
            final boolean eventActive = conference.getStatus() == ConferenceStatus.ACTIVE;
            final String token = extractToken(jx);
            final var v = token == null ? null : validateTokenUseCase.execute(token);
            // Staff access is role-based and independent of attendee tickets. A revoked or
            // expired attendee ticket must not remove access from event operators.
            final boolean staffAccess = eventActive && v != null && v.valid() && hasOperationalStaffAccess(id, v);
            final boolean hasAccess = eventActive && v != null && v.valid() && (staffAccess || ticketUseCase.hasAccess(conference, v.subjectUuid()));
            final boolean presentationAccess = eventActive && v != null && v.valid() && (hasAccess || staffAccess || eventPermissionGuard.hasPermission(
                    id, v.subjectUuid(), v.role(), Permission.MANAGE_PRESENTATION));
            final List<String> publicAreas = eventActive ? List.of("presentation_preview") : List.of();
            final List<String> privateAreas = eventActive
                    ? List.of("event_info", "ticket_claim", "cloud", "presentation_full", "survey", "video", "whiteboard", "diagrams", "notes", "ide")
                    : List.of();
            sendOk(jx, 200, Map.of("eventActive", eventActive, "eventStatus", conference.getStatus().name(),
                    "ticketRequired", ticketUseCase.isTicketed(conference), "hasAccess", hasAccess,
                    "presentationAccess", presentationAccess,
                    "publicOnly", eventActive && ticketUseCase.isTicketed(conference) && !hasAccess,
                    "publicAreas", publicAreas,
                    "privateAreas", privateAreas,
                    "previewSlideLimit", eventActive ? 5 : 0));
        } catch (final IllegalArgumentException e) { sendError(jx, 404, e.getMessage(), e.getMessage());
        } catch (final Exception e) { sendError(jx, 500, "internal_error", e.getMessage()); }
        return true;
    }

    private boolean rejectIfConferenceClosed(final JettyHttpExchange jx, final String id) {
        final var conference = getConferenceUseCase.byId(id);
        if (conference.isEmpty()) {
            sendError(jx, 404, "conference_not_found", "Evento no encontrado");
            return true;
        }
        return rejectIfConferenceClosed(jx, conference.get());
    }

    private boolean rejectIfConferenceClosed(final JettyHttpExchange jx, final Conference conference) {
        if (conference.getStatus() != ConferenceStatus.ACTIVE) {
            sendError(jx, 423, "conference_closed", "Este evento está desactivado temporalmente");
            return true;
        }
        return false;
    }

    /**
     * Authorization endpoint for the presentations service. It is deliberately
     * separate from ticket access: only a platform admin, the conference owner,
     * or an event role with MANAGE_PRESENTATION may upload/manage its files.
     */
    private boolean handlePresentationAccess(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return true;
        }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) {
                sendError(jx, 401, "token_invalid", "Invalid token");
                return true;
            }
            final boolean allowed = eventPermissionGuard.hasPermission(
                    id, v.subjectUuid(), v.role(), Permission.MANAGE_PRESENTATION)
                    || (isOrganizerOrAdmin(v.role()) && getConferenceUseCase.byId(id)
                    .map(c -> isPlatformAdminRole(v.role()) || v.subjectUuid().equals(c.getCreatedByUserUuid()))
                    .orElse(false));
            if (!allowed) {
                sendError(jx, 403, "forbidden", "No tienes permiso para administrar la presentación");
                return true;
            }
            sendOk(jx, 200, Map.of("allowed", true));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleClaimTicket(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        try {
            if (rejectIfConferenceClosed(jx, id)) return true;
            final var body = parseBody(jx);
            final String input = body.get("ticket") instanceof String s ? s
                    : body.get("ticketCode") instanceof String s ? s : (String) body.get("qr");
            if (token == null) {
                final String displayName = body.get("displayName") instanceof String s && !s.isBlank() ? s.trim() : "Invitado";
                final String fingerprint = body.get("deviceFingerprint") instanceof String s ? s : extractDeviceFingerprint(jx);
                final var guest = createGuestUseCase.execute(new CreateGuestUseCase.GuestRequest(displayName, fingerprint, id));
                final var ticket = ticketUseCase.claim(id, input, guest.guestUuid());
                sendOk(jx, 200, Map.of("ticket", ticket, "token", guest.token(), "guestUuid", guest.guestUuid(),
                        "role", "guest", "expiresAt", guest.expiresAt()));
            } else {
                final var v = validateTokenUseCase.execute(token);
                if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
                sendOk(jx, 200, ticketUseCase.claim(id, input, v.subjectUuid()));
            }
        } catch (final IllegalStateException e) { sendError(jx, 409, e.getMessage(), "Este boleto no se puede canjear");
        } catch (final IllegalArgumentException e) { sendError(jx, 400, e.getMessage(), "El QR o UUID del boleto no es válido");
        } catch (final Exception e) { sendError(jx, 500, "internal_error", e.getMessage()); }
        return true;
    }

    private boolean handleClaimTicketByCode(final JettyHttpExchange jx) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            final var body = parseBody(jx);
            final String input = body.get("ticket") instanceof String s ? s
                    : body.get("ticketCode") instanceof String s ? s : (String) body.get("qr");
            sendOk(jx, 200, ticketUseCase.claimByCode(input, v.subjectUuid()));
        } catch (final IllegalStateException e) { sendError(jx, 409, e.getMessage(), "Este boleto no se puede canjear");
        } catch (final IllegalArgumentException e) { sendError(jx, 400, e.getMessage(), "El QR o UUID del boleto no es válido");
        } catch (final Exception e) { sendError(jx, 500, "internal_error", e.getMessage()); }
        return true;
    }

    private boolean handleTicketCheckIn(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !canCheckIn(id, v)) { sendError(jx, 403, "forbidden", "No tienes permiso para validar boletos"); return true; }
            final var body = parseBody(jx);
            final String input = body.get("ticket") instanceof String s ? s
                    : body.get("ticketCode") instanceof String s ? s : (String) body.get("qr");
            sendOk(jx, 200, ticketUseCase.checkIn(id, input));
        } catch (final IllegalStateException e) { sendError(jx, 409, e.getMessage(), "Este boleto ya fue utilizado o aún no fue canjeado");
        } catch (final IllegalArgumentException e) { sendError(jx, 404, e.getMessage(), "Boleto no encontrado para esta conferencia");
        } catch (final Exception e) { sendError(jx, 500, "internal_error", e.getMessage()); }
        return true;
    }

    private boolean handleRevokeTicket(final JettyHttpExchange jx, final String id, final String ticketUuid) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !canManageTickets(id, v)) { sendError(jx, 403, "forbidden", "No tienes permiso para revocar boletos"); return true; }
            sendOk(jx, 200, ticketUseCase.revoke(id, ticketUuid, v.subjectUuid()));
        } catch (final IllegalStateException e) {
            final String detail = "operational_ticket_protected".equals(e.getMessage())
                    ? "Los boletos operativos del personal no se pueden revocar"
                    : "El boleto ya fue utilizado";
            sendError(jx, 409, e.getMessage(), detail);
        } catch (final IllegalArgumentException e) { sendError(jx, 404, e.getMessage(), "Boleto no encontrado");
        } catch (final Exception e) { sendError(jx, 500, "internal_error", e.getMessage()); }
        return true;
    }

    private boolean handleResendTicket(final JettyHttpExchange jx, final String id, final String ticketUuid) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !canManageTickets(id, v)) { sendError(jx, 403, "forbidden", "No tienes permiso para reenviar boletos"); return true; }
            sendOk(jx, 200, ticketUseCase.resend(id, ticketUuid));
        } catch (final IllegalStateException e) {
            final String detail = switch (e.getMessage() == null ? "" : e.getMessage()) {
                case "ticket_revoked" -> "El boleto fue revocado y no se puede reenviar";
                case "ticket_expired" -> "El boleto expiró y no se puede reenviar";
                case "email_provider_not_configured" -> "El envío de correo no está configurado en la plataforma";
                case "no_email_available" -> "Este boleto no tiene un correo asociado (ni de emisión ni de la cuenta que lo reclamó)";
                default -> "No fue posible reenviar el boleto";
            };
            sendError(jx, 409, e.getMessage(), detail);
        } catch (final IllegalArgumentException e) { sendError(jx, 404, e.getMessage(), "Boleto no encontrado");
        } catch (final Exception e) { sendError(jx, 500, "internal_error", e.getMessage()); }
        return true;
    }

    private boolean handleResendAllTickets(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !canManageTickets(id, v)) { sendError(jx, 403, "forbidden", "No tienes permiso para reenviar boletos"); return true; }
            final var summary = ticketUseCase.resendAll(id);
            sendOk(jx, 200, Map.of("sent", summary.sent(), "skipped", summary.skipped()));
        } catch (final IllegalArgumentException e) { sendError(jx, 404, e.getMessage(), "Evento no encontrado");
        } catch (final Exception e) { sendError(jx, 500, "internal_error", e.getMessage()); }
        return true;
    }

    private boolean handleSendAttendeeEmail(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !canManageTickets(id, v)) {
                sendError(jx, 403, "forbidden", "No tienes permiso para comunicarte con los inscritos");
                return true;
            }
            final var body = parseBody(jx);
            final Object rawRecipients = body.get("recipientUuids");
            final List<String> recipientUuids = rawRecipients instanceof List<?> items
                    ? items.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                    : null;
            final var summary = sendAttendeeEmailUseCase.execute(id, new SendAttendeeEmailUseCase.SendRequest(
                    (String) body.get("subject"), (String) body.get("message"),
                    (String) body.getOrDefault("format", "text"), recipientUuids));
            sendOk(jx, 200, Map.of("sent", summary.sent(), "skipped", summary.skipped()));
        } catch (final IllegalStateException e) {
            final String detail = "email_provider_not_configured".equals(e.getMessage())
                    ? "El envío de correo no está configurado en la plataforma"
                    : "No fue posible enviar el correo";
            sendError(jx, 409, e.getMessage(), detail);
        } catch (final IllegalArgumentException e) {
            final String detail = switch (e.getMessage() == null ? "" : e.getMessage()) {
                case "subject_invalid" -> "El asunto es obligatorio";
                case "message_invalid" -> "El mensaje es obligatorio";
                case "format_invalid" -> "Formato no soportado (usar text, html o markdown)";
                case "no_recipients" -> "No hay destinatarios validos para este envio";
                case "conference_not_found" -> "Evento no encontrado";
                default -> "Datos inválidos";
            };
            sendError(jx, 400, e.getMessage(), detail);
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGenerateEmailDraft(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !canManageTickets(id, v)) {
                sendError(jx, 403, "forbidden", "No tienes permiso para usar el asistente IA de correos");
                return true;
            }
            final var body = parseBody(jx);
            final String prompt = (String) body.get("prompt");
            final String draft = generateEmailDraftUseCase.execute(id, prompt);
            sendOk(jx, 200, Map.of("draft", draft));
        } catch (final IllegalStateException e) {
            sendError(jx, 409, e.getMessage(),
                    "El asistente IA de correos no esta configurado en la plataforma. Configuralo en Dashboard → IA.");
        } catch (final IllegalArgumentException e) {
            final String detail = switch (e.getMessage() == null ? "" : e.getMessage()) {
                case "prompt_required" -> "Describe que queres comunicar";
                case "conference_not_found" -> "Evento no encontrado";
                default -> "Datos invalidos";
            };
            sendError(jx, 400, e.getMessage(), detail);
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleReserve(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            final var conference = getConferenceUseCase.byId(id).orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
            if (!conference.isTicketSalesEnabled()) {
                sendError(jx, 409, "ticket_sales_closed", "La emisión de boletos está cerrada para este evento");
                return true;
            }
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
            final String detail = switch (e.getMessage()) {
                case "seat_already_taken" -> "Ese asiento ya fue reservado por alguien más";
                case "staff_exempt_no_ticket_needed" ->
                        "Como organizador, admin o moderador ya tenés acceso garantizado -- no necesitás boleto";
                default -> "No hay cupo disponible para esta conferencia";
            };
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

    private boolean handleListConferenceAttendees(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid() || !canManageSurvey(id, v)) {
                sendError(jx, 403, "forbidden", "No tienes permiso para gestionar la encuesta");
                return true;
            }
            sendOk(jx, 200, listConferenceAttendeesUseCase.execute(id));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleSurveyManagementAccess(final JettyHttpExchange jx, final String id) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return true; }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return true; }
            sendOk(jx, 200, Map.of("allowed", canManageSurvey(id, v)));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "No se pudo verificar el permiso de la encuesta");
        }
        return true;
    }

    private boolean handleCheckIn(final JettyHttpExchange jx, final String id) {
        try {
            if (requireConferenceOwner(jx, id) == null) return true;
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

    private boolean handleGetEgressPolicy(final JettyHttpExchange jx, final String id) {
        try {
            if (requireConferenceOwner(jx, id) == null) return true;
            final EgressPolicy policy = egressPolicyUseCase.get(id);
            sendOk(jx, 200, toEgressPolicyView(policy));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleSetEgressPolicy(final JettyHttpExchange jx, final String id) {
        try {
            if (requireConferenceOwner(jx, id) == null) return true;
            final var body = parseBody(jx);
            final String allowedHosts = (String) body.get("allowedHosts");
            final String blockedHosts = (String) body.get("blockedHosts");
            final EgressPolicy policy = egressPolicyUseCase.save(id, allowedHosts, blockedHosts);
            sendOk(jx, 200, toEgressPolicyView(policy));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private static Map<String, Object> toEgressPolicyView(final EgressPolicy policy) {
        final Map<String, Object> view = new java.util.HashMap<>();
        view.put("conferenceUuid", policy.conferenceUuid());
        view.put("allowedHosts", policy.allowedHosts());
        view.put("blockedHosts", policy.blockedHosts());
        return view;
    }

    private boolean handleSetSandboxConfig(final JettyHttpExchange jx, final String id) {
        try {
            if (requireConferenceOwner(jx, id) == null) return true;
            final var body = parseBody(jx);
            final String sandboxVariant = (String) body.get("sandboxVariant");
            final Integer sandboxPoolSize = (Integer) body.get("sandboxPoolSize");
            final String sandboxRemoteGitUrl = (String) body.get("sandboxRemoteGitUrl");
            final Integer sandboxJvmHeapMb = (Integer) body.get("sandboxJvmHeapMb");
            final Integer sandboxSeatsPerPod = (Integer) body.get("sandboxSeatsPerPod");
            final Integer sandboxCliPoolSize = (Integer) body.get("sandboxCliPoolSize");
            final var result = setSandboxConfigUseCase.execute(id, sandboxVariant, sandboxPoolSize,
                sandboxRemoteGitUrl, sandboxJvmHeapMb, sandboxSeatsPerPod,
                sandboxCliPoolSize);
            try {
                // Best-effort: si falla (ej. Kubernetes no disponible), no debe tumbar el guardado
                // de la config -- AssignSandboxUseCase sigue creando bajo demanda como fallback.
                ensureUnassignedSandboxUseCase.execute(id);
            } catch (final Exception e) {
                // pre-warm es una optimizacion, no un requisito para guardar la config -- pero se
                // loguea (no se ignora en silencio) para poder diagnosticar si nunca pre-provisiona.
                LOGGER.log(java.util.logging.Level.WARNING,
                    "pre-warm de sandbox fallo para conferencia " + id, e);
            }
            sendOk(jx, 200, result);
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    /** Prepara el pool completo antes de que entren los alumnos; la autorización se mantiene
     * separada de la configuración para permitir que un moderador operativo lo dispare sin darle
     * permiso para cambiar el tamaño del pool. */
    private boolean handlePrewarmSandboxPool(final JettyHttpExchange jx, final String id) {
        try {
            final var v = requireSandboxPrewarmAccess(jx, id);
            if (v == null) return true;
            if (!hasCapability(id, EventCapability.CODE_IDE)) {
                sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita el IDE");
                return true;
            }
            sendOk(jx, 200, prewarmSandboxPoolUseCase.execute(id));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            LOGGER.log(java.util.logging.Level.WARNING, "pre-warm explícito falló para " + id, e);
            sendError(jx, 500, "internal_error", "No se pudo preparar el pool de sandboxes");
        }
        return true;
    }

    private boolean handleSetDeviceAccessConfig(final JettyHttpExchange jx, final String id) {
        try {
            if (requireConferenceOwner(jx, id) == null) return true;
            final var body = parseBody(jx);
            final Integer maxDevicesPerUser = (Integer) body.get("maxDevicesPerUser");
            final Integer maxAccountsPerDevice = (Integer) body.get("maxAccountsPerDevice");
            final var result = setDeviceAccessConfigUseCase.execute(id, maxDevicesPerUser, maxAccountsPerDevice);
            sendOk(jx, 200, result);
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    /** Le muestra al moderador que dispositivos fueron bloqueados en esta conferencia por
     *  usar demasiadas cuentas distintas -- ver DeviceAccessGuard. */
    private boolean handleListDeviceBlocks(final JettyHttpExchange jx, final String id) {
        try {
            if (requireConferenceOwner(jx, id) == null) return true;
            sendOk(jx, 200, listDeviceBlocksUseCase.execute(id));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleUnblockDevice(final JettyHttpExchange jx, final String id, final String blockId) {
        try {
            final var v = requireConferenceOwner(jx, id);
            if (v == null) return true;
            unblockDeviceUseCase.execute(blockId, v.subjectUuid());
            sendOk(jx, 200, Map.of("unblocked", true));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    /** Fase C (DEC-0025): le muestra al organizador que asientos de sus Pods "neovim"
     *  compartidos tuvo que terminar el watchdog por abuso de recursos, y quien era. */
    private boolean handleListSandboxIncidents(final JettyHttpExchange jx, final String id) {
        try {
            if (requireConferenceOwner(jx, id) == null) return true;
            sendOk(jx, 200, listSandboxIncidentsUseCase.execute(id));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    /** Dashboard de moderador: estado en vivo de los Pods de sandbox de la conferencia (fase,
     *  ready, variante, quien ocupa cada asiento) -- ver ListSandboxStatusUseCase. */
    private boolean handleListSandboxStatus(final JettyHttpExchange jx, final String id) {
        try {
            if (requireConferenceOwner(jx, id) == null) return true;
            sendOk(jx, 200, listSandboxStatusUseCase.execute(id));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleResetSandbox(final JettyHttpExchange jx, final String conferenceId,
                                       final String sandboxUuid, final boolean recreate) {
        try {
            if (requireSandboxPrewarmAccess(jx, conferenceId) == null) return true;
            final var result = recreate
                ? resetSandboxUseCase.recreate(conferenceId, sandboxUuid)
                : resetSandboxUseCase.delete(conferenceId, sandboxUuid);
            sendOk(jx, 200, result);
        } catch (final IllegalArgumentException e) {
            final String errorCode = e.getMessage() != null ? e.getMessage() : "invalid_sandbox";
            final int status = switch (errorCode) {
                case "sandbox_in_use" -> 409;
                case "sandbox_not_found", "sandbox_not_in_conference", "conference_not_found" -> 404;
                default -> 400;
            };
            sendError(jx, status, errorCode, switch (errorCode) {
                case "sandbox_in_use" -> "El sandbox tiene usuarios asignados; libera sus asientos antes de recrearlo";
                case "sandbox_not_in_conference" -> "El sandbox no pertenece a este evento";
                case "sandbox_not_found" -> "No se encontró el sandbox";
                case "conference_not_found" -> "No se encontró el evento";
                default -> "La operación sobre el sandbox no es válida";
            });
        } catch (final Exception e) {
            LOGGER.log(java.util.logging.Level.WARNING, "reset de sandbox falló para " + conferenceId, e);
            sendError(jx, 500, "internal_error", "No se pudo restablecer el sandbox");
        }
        return true;
    }

    private boolean handleSetSandboxInternet(final JettyHttpExchange jx, final String id) {
        try {
            if (requireConferenceOwner(jx, id) == null) return true;
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
        return legacyRoleHasAny(role, "organizer", "admin");
    }

    /**
     * El resumen del panel se limita a eventos propios o a eventos donde el usuario tiene una
     * asignación operativa. No concede acceso al listado global de cuentas: solo permite el
     * agregado de asistentes de los eventos que realmente administra.
     */
    private boolean canViewAttendeeSummary(final ValidateTokenUseCase.ValidationResult validation) {
        if (validation == null || !validation.valid()) return false;
        if (isOrganizerOrAdmin(validation.role())) return true;
        return getConferenceUseCase.byUser(validation.subjectUuid()).stream().anyMatch(conference ->
                eventPermissionGuard.hasPermission(conference.getUuid(), validation.subjectUuid(), validation.role(), Permission.MODERATE_CONTENT)
                    || eventPermissionGuard.hasPermission(conference.getUuid(), validation.subjectUuid(), validation.role(), Permission.MANAGE_TICKETS)
                    || eventPermissionGuard.hasPermission(conference.getUuid(), validation.subjectUuid(), validation.role(), Permission.HOST_EVENT));
    }

    private boolean canViewConferenceAttendeeCount(final String conferenceId,
                                                    final ValidateTokenUseCase.ValidationResult validation) {
        if (validation == null || !validation.valid()) return false;
        if (isOrganizerOrAdmin(validation.role())) return true;
        return eventPermissionGuard.hasPermission(conferenceId, validation.subjectUuid(), validation.role(), Permission.MODERATE_CONTENT)
                || eventPermissionGuard.hasPermission(conferenceId, validation.subjectUuid(), validation.role(), Permission.MANAGE_TICKETS)
                || eventPermissionGuard.hasPermission(conferenceId, validation.subjectUuid(), validation.role(), Permission.HOST_EVENT);
    }

    private static boolean isPlatformAdminRole(final String role) {
        return legacyRoleHasAny(role, "admin");
    }

    /** Exige token valido, rol organizer/admin Y que el caller sea dueño real de la conferencia
     *  (o admin de plataforma). Envia la respuesta de error y devuelve null si no cumple -- mismo
     *  patron ya usado por handleSetSeating/handleDefineSeats via los use cases que filtran por
     *  createdByUserUuid, pero aca para handlers que llaman use cases sin ese filtro propio. */
    private ValidateTokenUseCase.ValidationResult requireConferenceOwner(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return null; }
        final var v = validateTokenUseCase.execute(token);
        if (!v.valid() || !isOrganizerOrAdmin(v.role())) {
            sendError(jx, 403, "forbidden", "Only organizers can perform this action");
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
        return v;
    }

    /**
     * El botón de pre-warm no cambia configuración. Por eso también se permite al staff asignado
     * al evento con MODERATE_CONTENT/HOST_EVENT, además del creador y del admin de plataforma.
     */
    private ValidateTokenUseCase.ValidationResult requireSandboxPrewarmAccess(
            final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) { sendError(jx, 401, "token_missing", "Authorization required"); return null; }
        final var v = validateTokenUseCase.execute(token);
        if (!v.valid()) { sendError(jx, 401, "token_invalid", "Invalid token"); return null; }
        // Un admin de plataforma ya tiene bypass global; no hace falta reconstruir/leer el
        // agregado Conference para autorizar una operación administrativa sobre el Pod. Para un
        // organizer sí verificamos que sea el creador real del evento.
        final boolean platformAdmin = legacyRoleHasAny(v.role(), "admin");
        final boolean owner = platformAdmin || (isOrganizerOrAdmin(v.role()) &&
            getConferenceUseCase.byId(conferenceId)
                .map(c -> c.getCreatedByUserUuid().equals(v.subjectUuid()))
                .orElse(false));
        final boolean eventStaff = eventPermissionGuard.hasPermission(
                conferenceId, v.subjectUuid(), v.role(), Permission.MODERATE_CONTENT)
            || eventPermissionGuard.hasPermission(
                conferenceId, v.subjectUuid(), v.role(), Permission.HOST_EVENT);
        if (!owner && !eventStaff) {
            sendError(jx, 403, "forbidden", "No tienes permiso para preparar los sandboxes del evento");
            return null;
        }
        return v;
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        // AUD-02: el fallback a query string existe SOLO para GET (EventSource no permite
        // headers Authorization; el stream de diagramas y los iframes de herramientas integradas
        // dependen de esto). Toda operacion que muta estado (POST/PUT/PATCH/DELETE) siempre pasa
        // por axios en el frontend, que SI puede mandar el header -- aceptar el token por query
        // ahi solo agrega superficie de filtracion (historial del navegador, logs de proxy,
        // Referer) sin ningun beneficio real.
        if (!"GET".equals(jx.method())) return null;
        return queryParam(jx, "ib_token");
    }

    private String extractDeviceFingerprint(final JettyHttpExchange jx) {
        return jx.request().getHeaders().get("X-Device-Fingerprint");
    }

}
