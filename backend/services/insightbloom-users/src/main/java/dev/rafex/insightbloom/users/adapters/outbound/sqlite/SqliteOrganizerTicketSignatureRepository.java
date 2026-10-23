package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.ports.OrganizerTicketSignatureRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

public final class SqliteOrganizerTicketSignatureRepository implements OrganizerTicketSignatureRepository {
    private final DatabaseManager db;
    public SqliteOrganizerTicketSignatureRepository(final DatabaseManager db) { this.db = db; }

    @Override public Optional<EventKey> activeKey(final String conferenceUuid) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT conference_uuid,key_id,public_key_spki,state,created_at FROM organizer_ticket_signing_keys WHERE conference_uuid=? AND state='ACTIVE'")) {
            ps.setString(1, conferenceUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new EventKey(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), Instant.parse(rs.getString(5))));
            }
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }

    @Override public Optional<EventKey> key(final String conferenceUuid, final String keyId) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT conference_uuid,key_id,public_key_spki,state,created_at FROM organizer_ticket_signing_keys WHERE conference_uuid=? AND key_id=?")) {
            ps.setString(1, conferenceUuid); ps.setString(2, keyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new EventKey(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), Instant.parse(rs.getString(5))));
            }
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }

    @Override public List<EventKey> keys(final String conferenceUuid) {
        final List<EventKey> result = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT conference_uuid,key_id,public_key_spki,state,created_at FROM organizer_ticket_signing_keys WHERE conference_uuid=? ORDER BY created_at,key_id")) {
            ps.setString(1, conferenceUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new EventKey(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), Instant.parse(rs.getString(5))));
            }
            return List.copyOf(result);
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }

    @Override public void registerKey(final String conferenceUuid, final String keyId, final String publicKeySpki,
                                      final String actorUuid, final Instant now) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement revoke = c.prepareStatement("UPDATE organizer_ticket_signing_keys SET state='REVOKED',revoked_at=? WHERE conference_uuid=? AND state='ACTIVE'")) {
                revoke.setString(1, now.toString()); revoke.setString(2, conferenceUuid); revoke.executeUpdate();
            }
            try (PreparedStatement insert = c.prepareStatement("INSERT INTO organizer_ticket_signing_keys(conference_uuid,key_id,public_key_spki,state,created_by,created_at) VALUES(?,?,?,'ACTIVE',?,?)")) {
                insert.setString(1, conferenceUuid); insert.setString(2, keyId); insert.setString(3, publicKeySpki);
                insert.setString(4, actorUuid); insert.setString(5, now.toString()); insert.executeUpdate();
            }
            try (PreparedStatement audit = c.prepareStatement("INSERT INTO organizer_ticket_signing_audit(conference_uuid,key_id,action,actor_uuid,occurred_at) VALUES(?,?,'KEY_REGISTERED',?,?)")) {
                audit.setString(1, conferenceUuid); audit.setString(2, keyId); audit.setString(3, actorUuid); audit.setString(4, now.toString()); audit.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }

    @Override public boolean revokeActiveKey(final String conferenceUuid, final String actorUuid, final Instant now) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            final String keyId;
            try (PreparedStatement query = c.prepareStatement("SELECT key_id FROM organizer_ticket_signing_keys WHERE conference_uuid=? AND state='ACTIVE'")) {
                query.setString(1, conferenceUuid); try (ResultSet rs = query.executeQuery()) { if (!rs.next()) return false; keyId = rs.getString(1); }
            }
            try (PreparedStatement update = c.prepareStatement("UPDATE organizer_ticket_signing_keys SET state='REVOKED',revoked_at=? WHERE conference_uuid=? AND state='ACTIVE'")) {
                update.setString(1, now.toString()); update.setString(2, conferenceUuid); update.executeUpdate();
            }
            try (PreparedStatement audit = c.prepareStatement("INSERT INTO organizer_ticket_signing_audit(conference_uuid,key_id,action,actor_uuid,occurred_at) VALUES(?,?,'KEY_REVOKED',?,?)")) {
                audit.setString(1, conferenceUuid); audit.setString(2, keyId); audit.setString(3, actorUuid); audit.setString(4, now.toString()); audit.executeUpdate();
            }
            try (PreparedStatement delete = c.prepareStatement("UPDATE organizer_ticket_offline_packages SET downloadable=0 WHERE conference_uuid=? AND downloadable=1")) {
                delete.setString(1, conferenceUuid); delete.executeUpdate();
            }
            c.commit(); return true;
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }

    @Override public boolean saveTicketSignature(final String conferenceUuid, final String ticketUuid, final String keyId,
                                                 final String coseSign1, final String actorUuid, final Instant now) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO organizer_ticket_signatures(conference_uuid,ticket_uuid,key_id,cose_sign1,signed_by,signed_at) " +
                        "SELECT ?,?,?,?, ?,? WHERE EXISTS(SELECT 1 FROM organizer_ticket_signing_keys WHERE conference_uuid=? AND key_id=? AND state='ACTIVE')")) {
            ps.setString(1, conferenceUuid); ps.setString(2, ticketUuid); ps.setString(3, keyId); ps.setString(4, coseSign1);
            ps.setString(5, actorUuid); ps.setString(6, now.toString()); ps.setString(7, conferenceUuid); ps.setString(8, keyId);
            final boolean inserted = ps.executeUpdate() == 1;
            if (inserted) try (PreparedStatement audit = c.prepareStatement("INSERT INTO organizer_ticket_signing_audit(conference_uuid,key_id,ticket_uuid,action,actor_uuid,occurred_at) VALUES(?,?,?,'TICKET_SIGNED',?,?)")) {
                audit.setString(1, conferenceUuid); audit.setString(2, keyId); audit.setString(3, ticketUuid); audit.setString(4, actorUuid); audit.setString(5, now.toString()); audit.executeUpdate();
            }
            return inserted;
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }

    @Override public Optional<String> ticketSignature(final String conferenceUuid, final String ticketUuid) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT cose_sign1 FROM organizer_ticket_signatures WHERE conference_uuid=? AND ticket_uuid=?")) {
            ps.setString(1, conferenceUuid); ps.setString(2, ticketUuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(); }
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }

    @Override public boolean hasTicketSignature(final String conferenceUuid, final String ticketUuid) {
        return ticketSignature(conferenceUuid, ticketUuid).isPresent();
    }

    @Override public void saveOfflinePackage(final OfflinePackage value, final String actorUuid) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM organizer_ticket_offline_packages WHERE expires_at<=?")) {
                ps.setString(1, Instant.now().toString()); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE organizer_ticket_offline_packages SET downloadable=0 WHERE conference_uuid=? AND downloadable=1")) {
                ps.setString(1, value.conferenceUuid()); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO organizer_ticket_offline_packages(conference_uuid,key_id,payload,cose_sign1,issued_at,expires_at,created_by,downloadable) VALUES(?,?,?,?,?,?,?,1)")) {
                ps.setString(1, value.conferenceUuid()); ps.setString(2, value.keyId()); ps.setString(3, value.payload());
                ps.setString(4, value.coseSign1()); ps.setString(5, value.issuedAt().toString()); ps.setString(6, value.expiresAt().toString());
                ps.setString(7, actorUuid); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO organizer_ticket_signing_audit(conference_uuid,key_id,action,actor_uuid,occurred_at) VALUES(?,?,'OFFLINE_PACKAGE_SIGNED',?,?)")) {
                ps.setString(1, value.conferenceUuid()); ps.setString(2, value.keyId()); ps.setString(3, actorUuid); ps.setString(4, Instant.now().toString()); ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }

    @Override public Optional<OfflinePackage> offlinePackage(final String conferenceUuid) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT conference_uuid,key_id,payload,cose_sign1,issued_at,expires_at FROM organizer_ticket_offline_packages WHERE conference_uuid=? AND downloadable=1 AND expires_at>? ORDER BY issued_at DESC LIMIT 1")) {
            ps.setString(1, conferenceUuid);
            ps.setString(2, Instant.now().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new OfflinePackage(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        Instant.parse(rs.getString(5)), Instant.parse(rs.getString(6))));
            }
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }

    @Override public boolean isKnownOfflinePackage(final String conferenceUuid, final String payload,
                                                   final String coseSign1, final Instant now) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM organizer_ticket_offline_packages WHERE conference_uuid=? AND payload=? AND cose_sign1=? AND expires_at>? LIMIT 1")) {
            ps.setString(1, conferenceUuid); ps.setString(2, payload); ps.setString(3, coseSign1); ps.setString(4, now.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }

    @Override public void saveOfflineDraft(final String conferenceUuid, final String payload, final Instant expiresAt) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO organizer_ticket_offline_drafts(conference_uuid,payload,expires_at) VALUES(?,?,?) ON CONFLICT(conference_uuid) DO UPDATE SET payload=excluded.payload,expires_at=excluded.expires_at")) {
            ps.setString(1, conferenceUuid); ps.setString(2, payload); ps.setString(3, expiresAt.toString()); ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }

    @Override public Optional<String> offlineDraft(final String conferenceUuid, final Instant now) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT payload FROM organizer_ticket_offline_drafts WHERE conference_uuid=? AND expires_at>?")) {
            ps.setString(1, conferenceUuid); ps.setString(2, now.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(); }
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }

    @Override public void clearOfflineDraft(final String conferenceUuid) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM organizer_ticket_offline_drafts WHERE conference_uuid=?")) {
            ps.setString(1, conferenceUuid); ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }

    @Override public void invalidateOfflinePackage(final String conferenceUuid) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE organizer_ticket_offline_packages SET downloadable=0 WHERE conference_uuid=? AND downloadable=1")) {
                ps.setString(1, conferenceUuid); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM organizer_ticket_offline_packages WHERE expires_at<=?")) {
                ps.setString(1, Instant.now().toString()); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM organizer_ticket_offline_drafts WHERE conference_uuid=?")) {
                ps.setString(1, conferenceUuid); ps.executeUpdate();
            }
        } catch (SQLException e) { throw new IllegalStateException("organizer_ticket_signing_storage_failed", e); }
    }
}
