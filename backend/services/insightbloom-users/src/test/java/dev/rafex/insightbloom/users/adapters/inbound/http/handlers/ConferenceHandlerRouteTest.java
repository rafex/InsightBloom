package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConferenceHandlerRouteTest {
    @Test
    void recognizesPortalSandboxDeleteAndRecreateRoutesWithSandboxUuidSegment() {
        assertEquals("delete", ConferenceHandler.sandboxResetAction(
            "/api/users/api/v1/conferences/conf-1/sandbox/6cdd8c11-4cf0-4945-a9c9-a89519a63dae/delete"));
        assertEquals("recreate", ConferenceHandler.sandboxResetAction(
            "/api/users/api/v1/conferences/conf-1/sandbox/6cdd8c11-4cf0-4945-a9c9-a89519a63dae/recreate"));
    }

    @Test
    void doesNotTreatOtherSandboxPostsAsResetActions() {
        assertNull(ConferenceHandler.sandboxResetAction("/api/users/api/v1/conferences/conf-1/sandbox/prewarm"));
        assertNull(ConferenceHandler.sandboxResetAction("/api/users/api/v1/conferences/conf-1/sandbox/delete"));
        assertNull(ConferenceHandler.sandboxResetAction(
            "/api/users/api/v1/conferences/conf-1/sandbox/preview/publication-1/delete"));
    }
}
