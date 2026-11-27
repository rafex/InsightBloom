package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.json.JsonUtils;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase.ValidationResult;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.ConferenceStatus;
import dev.rafex.insightbloom.users.domain.model.OnDemandCuePoint;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConferenceViewsTest {

    private static final String SECRET = "https://token@git.example.test/secreto.git";

    private final EventPermissionGuard guard = Mockito.mock(EventPermissionGuard.class);
    private boolean ticketed;
    private Set<String> ticketHolders = Set.of();
    private ConferenceViews views;
    private Conference conference;

    @BeforeEach
    void setUp() {
        views = new ConferenceViews(guard, c -> ticketed, (c, user) -> ticketHolders.contains(user));
        conference = new Conference("evento", "Evento", "creador");
        conference.setSandboxRemoteGitUrl(SECRET);
        conference.setSandboxMaterialSourceUrl("https://github.com/example/materiales");
        conference.setSandboxBootstrapValue("export SECRETO=1");
        conference.setSandboxCliLazyVimPoolSize(3);
        conference.setPresentationSourceUrl("https://example.test/slides.zip");
        conference.setOnDemandVideoProvider("YOUTUBE");
        conference.setOnDemandVideoUrl("https://youtu.be/privado");
        conference.setOnDemandCuePoints(List.of(new OnDemandCuePoint(10, "inicio", "survey")));
    }

    private static ValidationResult user(final String uuid, final String role) {
        return new ValidationResult(true, uuid, "user", role, null);
    }

    private static ValidationResult guest(final String uuid) {
        return new ValidationResult(true, uuid, "guest", "guest", null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(final Object value) throws Exception {
        final var codec = JsonUtils.codec();
        return codec.readValue(codec.toJson(value), Map.class);
    }

    @Test
    void anonymousViewExposesOnlyTheAllowedFields() throws Exception {
        final var body = json(views.publicAggregate(conference, null));

        // Lista cerrada a proposito: si falla al agregar un campo, confirmar primero que puede
        // leerlo una persona anonima.
        final var allowed = Set.of("uuid", "friendlyId", "name", "status", "createdByUserUuid",
                "description", "eventDate", "startTime", "endTime", "timezoneId", "venue", "latitude",
                "longitude", "visibility", "publiclyListed", "flyerBase64", "scheduleMarkdown",
                "scheduleLayout", "publicTheme", "seatingMode", "capacity", "ticketPrice",
                "ticketCurrency", "freeTicket", "ticketSalesEnabled", "eventTypeKey", "canvasTool",
                "canvasAudienceMode", "canvasConfigs", "onDemandVideoProvider",
                "presentationSourceUrl", "onDemandVideoUrl", "onDemandCuePoints");
        assertTrue(allowed.containsAll(body.keySet()), () -> "campos no permitidos: " + body.keySet());
        assertNull(body.get("createdByUserUuid"));
        assertFalse(body.toString().contains("SECRETO"));
        assertFalse(body.toString().contains(SECRET));
    }

    @Test
    void adminCreatorAndEventStaffReceiveTheFullAggregate() {
        Mockito.when(guard.hasActiveEventRole(conference.getUuid(), "staff")).thenReturn(true);

        assertSame(conference, views.forSession(conference, user("otra", "admin")));
        assertSame(conference, views.forSession(conference, user("creador", "organizer")));
        assertSame(conference, views.forSession(conference, user("staff", "attendee")));
    }

    @Test
    void unrelatedSessionsReceiveThePublicView() {
        final var sessions = new ValidationResult[] {
                guest("invitado"),
                user("asistente", "attendee"),
                user("otro-organizador", "organizer"),
                user("moderador-global", "moderator"),
                user("casi-admin", "notadmin"),
                new ValidationResult(true, "creador", "webhook", "webhook", null),
                new ValidationResult(false, null, null, null, null)
        };
        for (final var session : sessions) {
            assertInstanceOf(ConferenceViews.PublicAggregate.class, views.forSession(conference, session),
                    () -> "sesion " + session);
        }
    }

    @Test
    void creatorUuidTravelsOnlyToTheCreator() {
        assertEquals("creador", views.publicAggregate(conference, user("creador", "organizer")).createdByUserUuid());
        assertNull(views.publicAggregate(conference, user("asistente", "attendee")).createdByUserUuid());
    }

    @Test
    void ticketedContentRequiresAccess() {
        ticketed = true;
        ticketHolders = Set.of("con-boleto", "invitado-con-boleto");

        for (final var session : new ValidationResult[] {null, user("sin-boleto", "attendee"), guest("invitado")}) {
            final var view = views.publicAggregate(conference, session);
            assertNull(view.onDemandVideoUrl());
            assertNull(view.presentationSourceUrl());
            assertEquals(List.of(), view.onDemandCuePoints());
        }
        for (final var session : new ValidationResult[] {user("con-boleto", "attendee"),
                guest("invitado-con-boleto"), user("moderador-global", "moderator")}) {
            final var view = views.publicAggregate(conference, session);
            assertEquals("https://youtu.be/privado", view.onDemandVideoUrl(), () -> "sesion " + session);
            assertEquals(1, view.onDemandCuePoints().size());
        }
    }

    @Test
    void openEventContentIsVisibleWhileActiveOnly() {
        assertEquals("https://youtu.be/privado", views.publicAggregate(conference, null).onDemandVideoUrl());

        conference.setStatus(ConferenceStatus.CLOSED);

        assertNull(views.publicAggregate(conference, null).onDemandVideoUrl());
        assertEquals("https://youtu.be/privado",
                views.publicAggregate(conference, user("creador", "organizer")).onDemandVideoUrl());
    }
}
