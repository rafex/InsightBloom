package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase.ValidationResult;
import dev.rafex.insightbloom.users.domain.model.CanvasAudienceMode;
import dev.rafex.insightbloom.users.domain.model.CanvasConfig;
import dev.rafex.insightbloom.users.domain.model.CanvasTool;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.ConferenceStatus;
import dev.rafex.insightbloom.users.domain.model.OnDemandCuePoint;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Decide que representacion de un evento recibe cada peticion.
 *
 * <p>El agregado {@link Conference} incluye configuracion interna (sandboxes, repositorios,
 * script de arranque, XML de drawio, escena de Excalidraw, limites de dispositivos) y solo se
 * entrega a quien trabaja en el evento: admin de plataforma, creador o persona con un rol activo
 * asignado en ese evento. Cualquier otra peticion recibe {@link PublicAggregate}, una lista
 * explicita de campos: un campo nuevo en el modelo queda oculto hasta que alguien lo agregue
 * aqui a proposito.
 */
final class ConferenceViews {

    /**
     * Representacion para quien no trabaja en el evento. Antes de agregar un componente,
     * confirmar que puede leerlo una persona anonima.
     *
     * <p>{@code createdByUserUuid} solo viaja cuando quien pregunta es el creador. El contenido
     * del evento ({@code presentationSourceUrl}, {@code onDemandVideoUrl} y sus marcas) solo
     * viaja cuando la peticion tiene acceso al evento.
     */
    record PublicAggregate(String uuid, String friendlyId, String name, ConferenceStatus status,
                           String createdByUserUuid, String description, String eventDate,
                           String startTime, String endTime, Integer timezoneId, String venue,
                           Double latitude, Double longitude, String visibility, boolean publiclyListed,
                           String flyerBase64, String scheduleMarkdown, String scheduleLayout,
                           String publicTheme, String seatingMode, Integer capacity, String ticketPrice,
                           String ticketCurrency, boolean freeTicket, boolean ticketSalesEnabled,
                           String eventTypeKey, CanvasTool canvasTool,
                           CanvasAudienceMode canvasAudienceMode, List<CanvasConfig> canvasConfigs,
                           String onDemandVideoProvider, String presentationSourceUrl,
                           String onDemandVideoUrl, List<OnDemandCuePoint> onDemandCuePoints) {}

    private final EventPermissionGuard eventPermissionGuard;
    private final Predicate<Conference> isTicketed;
    private final BiPredicate<Conference, String> hasTicketAccess;

    ConferenceViews(final EventPermissionGuard eventPermissionGuard,
                    final Predicate<Conference> isTicketed,
                    final BiPredicate<Conference, String> hasTicketAccess) {
        this.eventPermissionGuard = eventPermissionGuard;
        this.isTicketed = isTicketed;
        this.hasTicketAccess = hasTicketAccess;
    }

    /** Agregado completo para quien trabaja en el evento; vista publica para el resto. */
    Object forSession(final Conference conference, final ValidationResult session) {
        return isEventStaff(conference, session) ? conference : publicAggregate(conference, session);
    }

    /** Vista publica; {@code session} es null o invalida cuando la peticion es anonima. */
    PublicAggregate publicAggregate(final Conference c, final ValidationResult session) {
        final boolean creator = isCreator(c, session);
        final boolean content = canSeeContent(c, session);
        return new PublicAggregate(c.getUuid(), c.getFriendlyId(), c.getName(), c.getStatus(),
                creator ? c.getCreatedByUserUuid() : null, c.getDescription(), c.getEventDate(),
                c.getStartTime(), c.getEndTime(), c.getTimezoneId(), c.getVenue(),
                c.getLatitude(), c.getLongitude(), c.getVisibility(), c.isPubliclyListed(),
                c.getFlyerBase64(), c.getScheduleMarkdown(), c.getScheduleLayout(),
                c.getPublicTheme(), c.getSeatingMode(), c.getCapacity(), c.getTicketPrice(),
                c.getTicketCurrency(), c.isFreeTicket(), c.isTicketSalesEnabled(),
                c.getEventTypeKey(), c.getCanvasTool(),
                c.getCanvasAudienceMode(), c.getCanvasConfigs(),
                c.getOnDemandVideoProvider(),
                content ? c.getPresentationSourceUrl() : null,
                content ? c.getOnDemandVideoUrl() : null,
                content ? c.getOnDemandCuePoints() : List.of());
    }

    boolean isEventStaff(final Conference conference, final ValidationResult session) {
        if (!isUser(session)) return false;
        return hasPlatformRole(session, "admin")
                || isCreator(conference, session)
                || eventPermissionGuard.hasActiveEventRole(conference.getUuid(), session.subjectUuid());
    }

    /** Misma regla de acceso que {@code /conferences/{id}/access}. */
    private boolean canSeeContent(final Conference conference, final ValidationResult session) {
        if (isEventStaff(conference, session)) return true;
        if (conference.getStatus() != ConferenceStatus.ACTIVE) return false;
        if (!isTicketed.test(conference)) return true;
        if (session == null || !session.valid()) return false;
        return (isUser(session) && hasPlatformRole(session, "organizer", "moderator"))
                || hasTicketAccess.test(conference, session.subjectUuid());
    }

    private static boolean isCreator(final Conference conference, final ValidationResult session) {
        return isUser(session) && session.subjectUuid().equals(conference.getCreatedByUserUuid());
    }

    /** Invitados y webhooks nunca cuentan como personal del evento. */
    private static boolean isUser(final ValidationResult session) {
        return session != null && session.valid() && "user".equals(session.kind());
    }

    private static boolean hasPlatformRole(final ValidationResult session, final String... expected) {
        if (session.role() == null) return false;
        final List<String> wanted = List.of(expected);
        return Arrays.stream(session.role().split(","))
                .map(String::trim)
                .anyMatch(wanted::contains);
    }
}
