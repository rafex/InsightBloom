package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SandboxPublicationCapabilityTest {
    @Test
    void encodesAndDecodesTheSandboxScope() {
        final Sandbox sandbox = new Sandbox(
                "sandbox-1", "conference-1", 0, 0, Sandbox.VARIANT_WEB,
                "guest-1", Instant.now(), Instant.now(), Instant.now().plusSeconds(600));
        final SandboxPublicationCapability codec = new SandboxPublicationCapability("test-secret");

        final String token = codec.encode(sandbox);
        final var parsed = codec.decode(token);

        assertTrue(parsed.isPresent());
        assertEquals("conference-1", parsed.get().conferenceUuid());
        assertEquals("sandbox-1", parsed.get().sandboxUuid());
        assertEquals("guest-1", parsed.get().userUuid());
        assertEquals(0, parsed.get().seatIndex());
    }

    @Test
    void rejectsTamperingAndWrongSecret() {
        final Sandbox sandbox = new Sandbox(
                "sandbox-1", "conference-1", 0, 0, Sandbox.VARIANT_WEB,
                "guest-1", Instant.now(), Instant.now(), Instant.now().plusSeconds(600));
        final String token = new SandboxPublicationCapability("test-secret").encode(sandbox);

        assertTrue(new SandboxPublicationCapability("test-secret").decode(token + "x").isEmpty());
        assertTrue(new SandboxPublicationCapability("other-secret").decode(token).isEmpty());
    }

    @Test
    void rejectsAnExpiredSandboxCapability() {
        final Instant now = Instant.now();
        final Sandbox sandbox = new Sandbox(
                "sandbox-1", "conference-1", 0, 0, Sandbox.VARIANT_WEB,
                "guest-1", now.minusSeconds(20), now.minusSeconds(20), now.minusSeconds(1));

        final String token = new SandboxPublicationCapability("test-secret").encode(sandbox);

        assertTrue(new SandboxPublicationCapability("test-secret").decode(token).isEmpty());
    }
}
