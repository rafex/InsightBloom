package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizerInvitationHandlerAuthorizationTest {
    @Test
    void adminGateRequiresAnExactAdminRole() {
        assertTrue(OrganizerInvitationHandler.hasRole("ATTENDEE,ADMIN", "ADMIN"));
        assertFalse(OrganizerInvitationHandler.hasRole("ATTENDEE,NOT_ADMIN", "ADMIN"));
        assertFalse(OrganizerInvitationHandler.hasRole("ADMINISTRATOR", "ADMIN"));
        assertFalse(OrganizerInvitationHandler.hasRole(null, "ADMIN"));
    }
}
