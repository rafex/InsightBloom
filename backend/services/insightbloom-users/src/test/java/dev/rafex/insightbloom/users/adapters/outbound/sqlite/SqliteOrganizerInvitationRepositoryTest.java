package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.OrganizerInviteSettings;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class SqliteOrganizerInvitationRepositoryTest {
    private record Fixture(DatabaseManager db, SqliteOrganizerInvitationRepository repository) {}

    private Fixture fixture(Path tempDir) {
        DatabaseManager db = new DatabaseManager(tempDir.resolve("users.db").toString());
        db.initialize();
        return new Fixture(db, new SqliteOrganizerInvitationRepository(db));
    }

    private void addUser(DatabaseManager db, String uuid, String email, String role, boolean verified) throws Exception {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users(uuid,username,email,role,status,email_verified,created_at,updated_at) VALUES(?,?,?,?,'ACTIVE',?,?,?)")) {
            ps.setString(1, uuid); ps.setString(2, uuid); ps.setString(3, email); ps.setString(4, role);
            ps.setInt(5, verified ? 1 : 0); ps.setString(6, Instant.now().toString()); ps.setString(7, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    @Test
    void invitationStoresOnlyHashAndRedeemsForMatchingVerifiedAccountOnce(@TempDir Path tempDir) throws Exception {
        Fixture f = fixture(tempDir);
        addUser(f.db(), "u1", "invitee@example.com", "ATTENDEE", true);
        addUser(f.db(), "u2", "different@example.com", "ATTENDEE", true);
        Instant now = Instant.now();
        String raw = "opaque-token-value";
        String hash = "sha256-token-hash";
        var invite = f.repository().createInvitation("Invitee@Example.com", hash, "admin", null, now,
                now.plus(7, ChronoUnit.DAYS), "ip-hash");

        assertEquals("invitee@example.com", invite.email());
        assertTrue(f.repository().preview(hash, now).isPresent());
        assertFalse(f.repository().redeem(hash, "u2", "different@example.com", now));
        assertTrue(f.repository().redeem(hash, "u1", "invitee@example.com", now.plusSeconds(1)));
        assertFalse(f.repository().redeem(hash, "u1", "invitee@example.com", now.plusSeconds(2)));
        assertTrue(f.repository().findInvitation(invite.uuid()).orElseThrow().status().equals("ACCEPTED"));
        try (Connection c = f.db().getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT token_hash FROM organizer_invitations WHERE uuid=?")) {
            ps.setString(1, invite.uuid());
            try (var rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(hash, rs.getString(1)); assertNotEquals(raw, rs.getString(1)); }
        }
        try (Connection c = f.db().getConnection(); PreparedStatement ps = c.prepareStatement("SELECT role FROM users WHERE uuid='u1'")) {
            try (var rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals("ATTENDEE,ORGANIZER", rs.getString(1)); }
        }
    }

    @Test
    void expiredRevokedAndUnverifiedInvitationsCannotBeRedeemed(@TempDir Path tempDir) throws Exception {
        Fixture f = fixture(tempDir);
        addUser(f.db(), "u1", "invitee@example.com", "ATTENDEE", true);
        addUser(f.db(), "u2", "unverified@example.com", "ATTENDEE", false);
        Instant now = Instant.now();
        var expired = f.repository().createInvitation("expired@example.com", "expired-hash", "admin", null, now,
                now.minusSeconds(1), null);
        f.repository().listInvitations(null, 10, 0); // Materializes the expired state for admin review.
        assertEquals("EXPIRED", f.repository().findInvitation(expired.uuid()).orElseThrow().status());
        f.repository().resendInvitation(expired.uuid(), "renewed-hash", "admin", now.plusSeconds(1),
                now.plus(7, ChronoUnit.DAYS), null);
        assertFalse(f.repository().preview("expired-hash", now.plusSeconds(1)).isPresent());
        assertTrue(f.repository().preview("renewed-hash", now.plusSeconds(1)).isPresent());
        var revoked = f.repository().createInvitation("invitee@example.com", "revoked-hash", "admin", null, now,
                now.plus(7, ChronoUnit.DAYS), null);
        f.repository().revokeInvitation(revoked.uuid(), "admin", now);
        var invite = f.repository().createInvitation("unverified@example.com", "unverified-hash", "admin", null, now,
                now.plus(7, ChronoUnit.DAYS), null);
        assertFalse(f.repository().preview("expired-hash", now).isPresent());
        assertEquals("PENDING", f.repository().findInvitation(expired.uuid()).orElseThrow().status());
        assertFalse(f.repository().preview("revoked-hash", now).isPresent());
        assertFalse(f.repository().redeem("unverified-hash", "u2", "unverified@example.com", now));
        assertEquals("PENDING", f.repository().findInvitation(invite.uuid()).orElseThrow().status());
    }

    @Test
    void recommendationsRequireEnabledVerifiedOrganizerAndAdminGate(@TempDir Path tempDir) throws Exception {
        Fixture f = fixture(tempDir);
        addUser(f.db(), "org", "organizer@example.com", "ATTENDEE,ORGANIZER", true);
        addUser(f.db(), "attendee", "attendee@example.com", "ATTENDEE", true);
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> f.repository().submitReferral("org", "guest@example.com", null, 3, now));
        f.repository().updateSettings(new OrganizerInviteSettings(true, 7, 3), "admin", now);
        assertThrows(IllegalArgumentException.class, () -> f.repository().submitReferral("attendee", "guest@example.com", null, 3, now));
        f.repository().setReferrerEnabled("org", true, "admin", now);
        var referral = f.repository().submitReferral("org", "Guest@Example.com", null, 3, now);
        assertEquals("guest@example.com", referral.email());
        assertEquals(2, f.repository().remainingReferrals("org", 3, now));
        assertThrows(IllegalArgumentException.class, () -> f.repository().submitReferral("org", "organizer@example.com", null, 3, now));
        f.repository().submitReferral("org", "other1@example.com", null, 3, now);
        f.repository().submitReferral("org", "other2@example.com", null, 3, now);
        assertEquals(0, f.repository().remainingReferrals("org", 3, now));
        assertThrows(IllegalArgumentException.class, () -> f.repository().submitReferral("org", "other3@example.com", null, 3, now));
        addUser(f.db(), "guest", "guest@example.com", "ATTENDEE", true);
        var referredInvite = f.repository().approveReferral(referral.uuid(), "derived-token-hash", "admin", now,
                now.plus(7, ChronoUnit.DAYS), null);
        assertEquals(referral.uuid(), referredInvite.referralUuid());
        f.repository().markDelivery(referredInvite.uuid(), false, "email_delivery_failed", now);
        assertEquals("DELIVERY_FAILED", f.repository().listReferrals(null, null, 20, 0).stream()
                .filter(row -> row.uuid().equals(referral.uuid())).findFirst().orElseThrow().status());
        f.repository().resendInvitation(referredInvite.uuid(), "retry-token-hash", "admin", now.plusSeconds(1),
                now.plus(8, ChronoUnit.DAYS), null);
        f.repository().markDelivery(referredInvite.uuid(), true, null, now.plusSeconds(2));
        assertFalse(f.repository().preview("derived-token-hash", now.plusSeconds(2)).isPresent());
        assertTrue(f.repository().redeem("retry-token-hash", "guest", "guest@example.com", now.plusSeconds(2)));
        assertTrue(f.repository().listReferralEligibleOrganizers().contains("org"));
        assertFalse(f.repository().listReferralEligibleOrganizers().contains("guest"));
        assertThrows(IllegalArgumentException.class, () -> f.repository().setReferrerEnabled("guest", true, "admin", now));
        assertEquals("ACCEPTED", f.repository().listReferrals(null, null, 20, 0).stream()
                .filter(row -> row.uuid().equals(referral.uuid())).findFirst().orElseThrow().status());
    }

    @Test
    void administrativeInvitationRateLimitIsAppliedPerActor(@TempDir Path tempDir) {
        Fixture f = fixture(tempDir);
        Instant now = Instant.now();
        for (int i = 0; i < 100; i++) {
            f.repository().createInvitation("person" + i + "@example.com", "hash-" + i, "admin", null,
                    now.plusSeconds(i), now.plus(7, ChronoUnit.DAYS), null);
        }
        assertThrows(IllegalArgumentException.class, () -> f.repository().createInvitation("last@example.com",
                "hash-last", "admin", null, now.plusSeconds(100), now.plus(7, ChronoUnit.DAYS), null));
    }

    @Test
    void publicPreviewRateLimitIsAppliedPerIp(@TempDir Path tempDir) {
        Fixture f = fixture(tempDir);
        Instant now = Instant.now();
        for (int i = 0; i < 30; i++) f.repository().recordPublicAttempt("INVITE_PREVIEW", "same-ip-hash", now.plusSeconds(i));
        assertThrows(IllegalArgumentException.class,
                () -> f.repository().recordPublicAttempt("INVITE_PREVIEW", "same-ip-hash", now.plusSeconds(30)));
    }

    @Test
    void initializationIsIdempotentAndKeepsExistingInvitationSettings(@TempDir Path tempDir) {
        Fixture f = fixture(tempDir);
        Instant now = Instant.now();
        assertEquals(new dev.rafex.insightbloom.users.domain.model.OrganizerInviteSettings(false, 7, 3), f.repository().settings());
        f.repository().updateSettings(new dev.rafex.insightbloom.users.domain.model.OrganizerInviteSettings(true, 14, 5), "admin", now);
        f.db().initialize();
        assertEquals(new dev.rafex.insightbloom.users.domain.model.OrganizerInviteSettings(true, 14, 5), f.repository().settings());
    }

    @Test
    void concurrentRedemptionConsumesInvitationOnlyOnce(@TempDir Path tempDir) throws Exception {
        Fixture f = fixture(tempDir);
        addUser(f.db(), "u1", "invitee@example.com", "ATTENDEE", true);
        Instant now = Instant.now();
        f.repository().createInvitation("invitee@example.com", "concurrent-hash", "admin", null, now,
                now.plus(7, ChronoUnit.DAYS), null);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return f.repository().redeem("concurrent-hash", "u1", "invitee@example.com", Instant.now()); });
            var second = pool.submit(() -> { start.await(); return f.repository().redeem("concurrent-hash", "u1", "invitee@example.com", Instant.now()); });
            start.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS) ^ second.get(5, TimeUnit.SECONDS));
        }
    }
}
