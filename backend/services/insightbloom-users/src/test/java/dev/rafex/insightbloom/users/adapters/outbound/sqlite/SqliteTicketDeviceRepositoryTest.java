package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.Ticket;
import dev.rafex.insightbloom.users.domain.model.TicketDeviceEnrollment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqliteTicketDeviceRepositoryTest {
    @TempDir Path tempDir;

    @Test
    void consumesEnrollmentAtomicallyAndRevokesOldCredentialOnVerifiedIdentityReplacement() throws Exception {
        final DatabaseManager db = new DatabaseManager(tempDir.resolve("users.db").toString());
        db.initialize();
        final SqliteTicketRepository tickets = new SqliteTicketRepository(db);
        final SqliteTicketDeviceRepository devices = new SqliteTicketDeviceRepository(db);
        final Ticket ticket = new Ticket("event-1", "issuer", "attendee@example.test", null, 1, "EMAIL_OTP");
        tickets.insert(ticket);
        final String jwk = publicJwk();
        final Instant now = Instant.now();

        final TicketDeviceEnrollment first = enrollment(ticket.getUuid(), "first-token", now);
        devices.createEnrollment(first);
        final var firstDevice = devices.enrollAndClaim(first.uuid(), "guest-one", UUID.randomUUID().toString(), ticket.getUuid(),
                jwk, "Teléfono anterior", null, 1, false, now).orElseThrow();
        assertTrue(firstDevice.active());
        assertThrows(IllegalArgumentException.class, () -> devices.enrollAndClaim(first.uuid(), "guest-one", UUID.randomUUID().toString(),
                ticket.getUuid(), jwk, "Replay", null, 1, true, Instant.now()));

        final TicketDeviceEnrollment replacement = enrollment(ticket.getUuid(), "second-token", Instant.now());
        devices.createEnrollment(replacement);
        final var newDevice = devices.enrollAndClaim(replacement.uuid(), "guest-two", UUID.randomUUID().toString(), ticket.getUuid(),
                jwk, "Teléfono nuevo", null, 1, true, Instant.now()).orElseThrow();

        assertTrue(newDevice.active());
        final var all = devices.listByTicket(ticket.getUuid());
        assertEquals(2, all.size());
        assertEquals(1, all.stream().filter(d -> d.active()).count());
        assertTrue(all.stream().anyMatch(d -> d.uuid().equals(firstDevice.uuid()) && !d.active()));
        assertEquals("guest-two", tickets.findByUuid(ticket.getUuid()).orElseThrow().getClaimedByUserUuid());

        devices.createEnrollment(enrollment(ticket.getUuid(), "third-token", Instant.now()));
        assertThrows(IllegalStateException.class,
                () -> devices.createEnrollment(enrollment(ticket.getUuid(), "fourth-token", Instant.now())));
    }

    private static TicketDeviceEnrollment enrollment(final String ticketUuid, final String token, final Instant now) {
        return new TicketDeviceEnrollment(UUID.randomUUID().toString(), ticketUuid, token, "EMAIL_OTP", "otp-hash",
                now.plusSeconds(600), null, 0, now);
    }

    private static String publicJwk() throws Exception {
        final var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        final var publicKey = (ECPublicKey) generator.generateKeyPair().getPublic();
        return "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"" + b64(fixed(publicKey.getW().getAffineX()))
                + "\",\"y\":\"" + b64(fixed(publicKey.getW().getAffineY())) + "\"}";
    }

    private static byte[] fixed(final java.math.BigInteger value) {
        final byte[] source = value.toByteArray(); final byte[] result = new byte[32];
        final int start = source.length > 32 ? source.length - 32 : 0;
        System.arraycopy(source, start, result, 32 - (source.length - start), source.length - start);
        return result;
    }
    private static String b64(final byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
}
