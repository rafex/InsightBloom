package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.TicketDeviceCredential;
import dev.rafex.insightbloom.users.domain.model.TicketDeviceEnrollment;
import dev.rafex.insightbloom.users.domain.ports.TicketDeviceRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteTicketDeviceRepository implements TicketDeviceRepository {
    private final DatabaseManager db;
    public SqliteTicketDeviceRepository(final DatabaseManager db) { this.db = db; }

    @Override public void createEnrollment(final TicketDeviceEnrollment e) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM ticket_device_enrollments WHERE ticket_uuid=? AND created_at>=?")) {
                    ps.setString(1, e.ticketUuid()); ps.setString(2, e.createdAt().minus(Duration.ofHours(1)).toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) >= 3) throw new IllegalStateException("ticket_enrollment_rate_limited");
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE ticket_device_enrollments SET consumed_at=? WHERE ticket_uuid=? AND method=? AND consumed_at IS NULL")) {
                    ps.setString(1, e.createdAt().toString()); ps.setString(2, e.ticketUuid()); ps.setString(3, e.method()); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ticket_device_enrollments (uuid,ticket_uuid,token_hash,method,otp_hash,expires_at,created_at) " +
                                "SELECT ?,t.uuid,?,?,?,?,? FROM tickets t WHERE t.uuid=? AND t.status IN ('ISSUED','CLAIMED') AND t.device_max_devices BETWEEN 1 AND 5 AND (t.device_enrollment_method=? OR ?='EMAIL_OTP' OR ?='ONE_TIME_LINK')")) {
                    ps.setString(1, e.uuid()); ps.setString(2, e.tokenHash()); ps.setString(3, e.method()); ps.setString(4, e.otpHash());
                    ps.setString(5, e.expiresAt().toString()); ps.setString(6, e.createdAt().toString()); ps.setString(7, e.ticketUuid()); ps.setString(8, e.method()); ps.setString(9, e.method()); ps.setString(10, e.method());
                    if (ps.executeUpdate() != 1) throw new IllegalArgumentException("ticket_enrollment_not_available");
                }
                c.commit();
            } catch (final RuntimeException | SQLException ex) {
                c.rollback(); if (ex instanceof RuntimeException runtime) throw runtime; throw ex;
            } finally { c.setAutoCommit(true); }
        } catch (SQLException ex) { throw new IllegalStateException("ticket_enrollment_unavailable", ex); }
    }

    @Override public Optional<TicketDeviceEnrollment> findEnrollmentByHash(final String hash) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM ticket_device_enrollments WHERE token_hash=?")) {
            ps.setString(1, hash); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(enrollment(rs)) : Optional.empty(); }
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override public int countEnrollmentsSince(final String ticketUuid, final Instant since) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM ticket_device_enrollments WHERE ticket_uuid=? AND created_at>=?")) {
            ps.setString(1, ticketUuid); ps.setString(2, since.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override public boolean recordEnrollmentFailure(final String uuid) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE ticket_device_enrollments SET failed_attempts=failed_attempts+1 WHERE uuid=? AND consumed_at IS NULL AND failed_attempts<5")) {
            ps.setString(1, uuid); return ps.executeUpdate() == 1;
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override public Optional<TicketDeviceCredential> enrollAndClaim(final String enrollmentUuid, final String claimantUuid,
            final String credentialUuid, final String ticketUuid, final String publicKeyJwk, final String label,
            final String fingerprint, final int ignoredMaxDevices, final boolean replaceOldest, final Instant now) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                String owner; String status; int maxDevices;
                try (PreparedStatement ps = c.prepareStatement("SELECT claimed_by_user_uuid,status,device_max_devices FROM tickets WHERE uuid=?")) {
                    ps.setString(1, ticketUuid); try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("ticket_not_found");
                        owner = rs.getString(1); status = rs.getString(2); maxDevices = rs.getInt(3);
                    }
                }
                if (maxDevices < 1 || maxDevices > 5) throw new IllegalStateException("ticket_device_enrollment_not_enabled");
                if ("REVOKED".equals(status) || "EXPIRED".equals(status) || "CHECKED_IN".equals(status))
                    throw new IllegalStateException("ticket_not_available");
                int activeDevices;
                try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM ticket_device_credentials WHERE ticket_uuid=? AND revoked_at IS NULL")) {
                    ps.setString(1, ticketUuid); try (ResultSet rs = ps.executeQuery()) {
                        activeDevices = rs.next() ? rs.getInt(1) : 0;
                    }
                }
                final boolean ownershipTransfer = owner != null && !owner.equals(claimantUuid);
                if (ownershipTransfer && !replaceOldest)
                    throw new IllegalStateException("ticket_device_limit_reached");
                if (activeDevices >= maxDevices || ownershipTransfer) {
                    if (!replaceOldest) throw new IllegalStateException("ticket_device_limit_reached");
                    if (ownershipTransfer) {
                        try (PreparedStatement ps = c.prepareStatement("UPDATE ticket_device_credentials SET revoked_at=? WHERE ticket_uuid=? AND revoked_at IS NULL")) {
                            ps.setString(1, now.toString()); ps.setString(2, ticketUuid); ps.executeUpdate();
                        }
                        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ticket_device_audit(ticket_uuid,credential_uuid,action,actor_uuid,detail,occurred_at) VALUES (?,NULL,?,?,?,?)")) {
                            ps.setString(1, ticketUuid); ps.setString(2, "DEVICE_SET_REPLACED"); ps.setString(3, claimantUuid); ps.setString(4, "verified_identity_transfer"); ps.setString(5, now.toString()); ps.executeUpdate();
                        }
                    } else if (activeDevices >= maxDevices) {
                        final String replacedUuid;
                        try (PreparedStatement ps = c.prepareStatement("SELECT uuid FROM ticket_device_credentials WHERE ticket_uuid=? AND revoked_at IS NULL ORDER BY registered_at ASC LIMIT 1")) {
                            ps.setString(1, ticketUuid); try (ResultSet rs = ps.executeQuery()) {
                                if (!rs.next()) throw new IllegalStateException("ticket_device_limit_reached");
                                replacedUuid = rs.getString(1);
                            }
                        }
                        try (PreparedStatement ps = c.prepareStatement("UPDATE ticket_device_credentials SET revoked_at=? WHERE uuid=? AND ticket_uuid=? AND revoked_at IS NULL")) {
                            ps.setString(1, now.toString()); ps.setString(2, replacedUuid); ps.setString(3, ticketUuid);
                            if (ps.executeUpdate() != 1) throw new IllegalStateException("ticket_device_limit_reached");
                        }
                        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ticket_device_audit(ticket_uuid,credential_uuid,action,actor_uuid,detail,occurred_at) VALUES (?,?,?,?,?,?)")) {
                            ps.setString(1, ticketUuid); ps.setString(2, replacedUuid); ps.setString(3, "DEVICE_REPLACED"); ps.setString(4, claimantUuid); ps.setString(5, "verified_replacement"); ps.setString(6, now.toString()); ps.executeUpdate();
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE ticket_device_enrollments SET consumed_at=? WHERE uuid=? AND ticket_uuid=? AND consumed_at IS NULL AND failed_attempts<5 AND expires_at>?")) {
                    ps.setString(1, now.toString()); ps.setString(2, enrollmentUuid); ps.setString(3, ticketUuid); ps.setString(4, now.toString());
                    if (ps.executeUpdate() != 1) throw new IllegalArgumentException("ticket_enrollment_invalid_or_expired");
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE tickets SET status='CLAIMED',claimed_by_user_uuid=CASE WHEN ?=1 THEN ? ELSE COALESCE(claimed_by_user_uuid,?) END,claimed_at=COALESCE(claimed_at,?) WHERE uuid=? AND status IN ('ISSUED','CLAIMED') AND (claimed_by_user_uuid IS NULL OR claimed_by_user_uuid=? OR ?=1)")) {
                    ps.setInt(1, replaceOldest ? 1 : 0); ps.setString(2, claimantUuid); ps.setString(3, claimantUuid); ps.setString(4, now.toString()); ps.setString(5, ticketUuid); ps.setString(6, claimantUuid); ps.setInt(7, replaceOldest ? 1 : 0);
                    if (ps.executeUpdate() != 1) throw new IllegalStateException("ticket_already_claimed");
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO ticket_device_credentials (uuid,ticket_uuid,public_key_jwk,label,fingerprint,registered_at) VALUES (?,?,?,?,?,?)")) {
                    ps.setString(1, credentialUuid); ps.setString(2, ticketUuid); ps.setString(3, publicKeyJwk); ps.setString(4, label);
                    ps.setString(5, fingerprint); ps.setString(6, now.toString()); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO ticket_device_audit(ticket_uuid,credential_uuid,action,actor_uuid,detail,occurred_at) VALUES (?,?,?,?,?,?)")) {
                    ps.setString(1, ticketUuid); ps.setString(2, credentialUuid); ps.setString(3, "DEVICE_REGISTERED"); ps.setString(4, claimantUuid); ps.setString(5, "enrollment"); ps.setString(6, now.toString()); ps.executeUpdate();
                }
                c.commit();
                return Optional.of(new TicketDeviceCredential(credentialUuid, ticketUuid, publicKeyJwk, label, fingerprint, now, null));
            } catch (final RuntimeException | SQLException ex) {
                c.rollback(); if (ex instanceof RuntimeException runtime) throw runtime; throw ex;
            } finally { c.setAutoCommit(true); }
        } catch (SQLException ex) { throw new IllegalStateException("ticket_device_enrollment_failed", ex); }
    }

    @Override public List<TicketDeviceCredential> listByTicket(final String ticketUuid) {
        final List<TicketDeviceCredential> out = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM ticket_device_credentials WHERE ticket_uuid=? ORDER BY registered_at")) {
            ps.setString(1, ticketUuid); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(credential(rs)); }
            return out;
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override public Optional<TicketDeviceCredential> findActiveCredential(final String credentialUuid, final String ticketUuid) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM ticket_device_credentials WHERE uuid=? AND ticket_uuid=? AND revoked_at IS NULL")) {
            ps.setString(1, credentialUuid); ps.setString(2, ticketUuid); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(credential(rs)) : Optional.empty(); }
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override public boolean revokeCredential(final String ticketUuid, final String credentialUuid, final Instant revokedAt) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE ticket_device_credentials SET revoked_at=? WHERE ticket_uuid=? AND uuid=? AND revoked_at IS NULL")) {
            ps.setString(1, revokedAt.toString()); ps.setString(2, ticketUuid); ps.setString(3, credentialUuid); return ps.executeUpdate() == 1;
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override public void recordAudit(final String ticketUuid, final String credentialUuid, final String action,
            final String actorUuid, final String detail, final Instant at) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO ticket_device_audit(ticket_uuid,credential_uuid,action,actor_uuid,detail,occurred_at) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, ticketUuid); ps.setString(2, credentialUuid); ps.setString(3, action); ps.setString(4, actorUuid); ps.setString(5, detail); ps.setString(6, at.toString()); ps.executeUpdate();
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    @Override public List<TicketDeviceCredential> listActiveForConference(final String conferenceUuid) {
        final List<TicketDeviceCredential> out = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT d.* FROM ticket_device_credentials d JOIN tickets t ON t.uuid=d.ticket_uuid WHERE t.conference_uuid=? AND t.status IN ('ISSUED','CLAIMED') AND d.revoked_at IS NULL")) {
            ps.setString(1, conferenceUuid); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(credential(rs)); }
            return out;
        } catch (SQLException ex) { throw new IllegalStateException(ex); }
    }

    private static TicketDeviceEnrollment enrollment(final ResultSet rs) throws SQLException {
        return new TicketDeviceEnrollment(rs.getString("uuid"), rs.getString("ticket_uuid"), rs.getString("token_hash"), rs.getString("method"),
                rs.getString("otp_hash"), Instant.parse(rs.getString("expires_at")), parse(rs.getString("consumed_at")), rs.getInt("failed_attempts"), Instant.parse(rs.getString("created_at")));
    }
    private static TicketDeviceCredential credential(final ResultSet rs) throws SQLException {
        return new TicketDeviceCredential(rs.getString("uuid"), rs.getString("ticket_uuid"), rs.getString("public_key_jwk"), rs.getString("label"), rs.getString("fingerprint"),
                Instant.parse(rs.getString("registered_at")), parse(rs.getString("revoked_at")));
    }
    private static Instant parse(final String value) { return value == null ? null : Instant.parse(value); }
}
