package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.OrganizerInvitation;
import dev.rafex.insightbloom.users.domain.model.OrganizerInviteSettings;
import dev.rafex.insightbloom.users.domain.model.OrganizerReferral;
import dev.rafex.insightbloom.users.domain.ports.OrganizerInvitationRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SqliteOrganizerInvitationRepository implements OrganizerInvitationRepository {
    private final DatabaseManager db;

    public SqliteOrganizerInvitationRepository(final DatabaseManager db) { this.db = db; }

    @Override
    public OrganizerInviteSettings settings() {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT referrals_enabled, invitation_ttl_days, referral_limit FROM organizer_invite_settings WHERE id = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new OrganizerInviteSettings(false, 7, 3);
                return new OrganizerInviteSettings(rs.getInt(1) == 1, rs.getInt(2), rs.getInt(3));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public OrganizerInviteSettings updateSettings(final OrganizerInviteSettings s, final String actor, final Instant now) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("""
                UPDATE organizer_invite_settings SET referrals_enabled = ?, invitation_ttl_days = ?, referral_limit = ?,
                    updated_by = ?, updated_at = ? WHERE id = 1
                """)) {
                ps.setInt(1, s.referralsEnabled() ? 1 : 0); ps.setInt(2, s.invitationTtlDays()); ps.setInt(3, s.referralLimit());
                ps.setString(4, actor); ps.setString(5, now.toString()); ps.executeUpdate();
                audit(c, actor, "SETTINGS_UPDATED", "settings", "1", null, now); c.commit();
                return s;
            } catch (SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public List<OrganizerInvitation> listInvitations(final String status, final int limit, final int offset) {
        final List<OrganizerInvitation> rows = new ArrayList<>();
        final String sql = "SELECT * FROM organizer_invitations" + (status == null ? "" : " WHERE status = ?")
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            expireOldInvites(c, null, Instant.now());
            try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1; if (status != null) ps.setString(i++, status); ps.setInt(i++, bounded(limit, 100)); ps.setInt(i, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(invitation(rs)); }
            }
            c.commit();
            return rows;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public Optional<OrganizerInvitation> findInvitation(final String uuid) {
        try (Connection c = db.getConnection()) { return findInvitation(c, uuid); }
        catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public OrganizerInvitation createInvitation(final String email, final String tokenHash, final String actor,
            final String referralUuid, final Instant now, final Instant expiresAt, final String ipHash) {
        enforceRate("ADMIN_INVITE", actor, ipHash, now, 100, 200);
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                expireOldInvites(c, email, now);
                final String uuid = UUID.randomUUID().toString();
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO organizer_invitations (uuid,email,email_normalized,token_hash,status,issuer_uuid,referral_uuid,
                            created_at,expires_at) VALUES (?,?,?,?,?,?,?,?,?)
                        """)) {
                    ps.setString(1, uuid); ps.setString(2, normalize(email)); ps.setString(3, normalize(email)); ps.setString(4, tokenHash);
                    ps.setString(5, "PENDING"); ps.setString(6, actor); ps.setString(7, referralUuid);
                    ps.setString(8, now.toString()); ps.setString(9, expiresAt.toString()); ps.executeUpdate();
                }
                audit(c, actor, "INVITATION_CREATED", "invitation", uuid, emailHash(email), now);
                c.commit();
                return findInvitation(c, uuid).orElseThrow();
            } catch (RuntimeException | SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw translate(e); }
    }

    @Override
    public OrganizerInvitation resendInvitation(final String uuid, final String tokenHash, final String actor,
            final Instant now, final Instant expiresAt, final String ipHash) {
        enforceRate("ADMIN_RESEND", actor, ipHash, now, 100, 200);
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE organizer_invitations SET token_hash = ?, status = 'PENDING', expires_at = ?,
                            delivery_error = NULL, sent_at = NULL
                        WHERE uuid = ? AND status IN ('PENDING','DELIVERY_FAILED','EXPIRED')
                        """)) {
                    ps.setString(1, tokenHash); ps.setString(2, expiresAt.toString()); ps.setString(3, uuid);
                    if (ps.executeUpdate() != 1) throw new IllegalArgumentException("invitation_not_resendable");
                }
                audit(c, actor, "INVITATION_RESENT", "invitation", uuid, null, now); c.commit();
                return findInvitation(c, uuid).orElseThrow();
            } catch (RuntimeException | SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw translate(e); }
    }

    @Override
    public void markDelivery(final String uuid, final boolean delivered, final String safeError, final Instant now) {
        final String sql = delivered
                ? "UPDATE organizer_invitations SET status='PENDING',sent_at=?,delivery_error=NULL WHERE uuid=? AND status='PENDING'"
                : "UPDATE organizer_invitations SET status='DELIVERY_FAILED',delivery_error=? WHERE uuid=? AND status='PENDING'";
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (delivered) { ps.setString(1, now.toString()); ps.setString(2, uuid); }
                else { ps.setString(1, safeError); ps.setString(2, uuid); }
                ps.executeUpdate();
                try (PreparedStatement referral = c.prepareStatement("""
                        UPDATE organizer_referrals SET status=? WHERE invitation_uuid=?
                            AND status IN ('INVITATION_SENT','DELIVERY_FAILED','EXPIRED')
                            AND EXISTS(SELECT 1 FROM organizer_invitations i WHERE i.uuid=invitation_uuid
                                AND i.status NOT IN ('ACCEPTED','REVOKED'))
                        """)) {
                    referral.setString(1, delivered ? "INVITATION_SENT" : "DELIVERY_FAILED");
                    referral.setString(2, uuid); referral.executeUpdate();
                }
                audit(c, "system", delivered ? "EMAIL_DELIVERED" : "EMAIL_DELIVERY_FAILED", "invitation", uuid, null, now);
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public void revokeInvitation(final String uuid, final String actor, final Instant now) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("UPDATE organizer_invitations SET status='REVOKED',revoked_at=? WHERE uuid=? AND status IN ('PENDING','DELIVERY_FAILED','EXPIRED')")) {
                ps.setString(1, now.toString()); ps.setString(2, uuid);
                if (ps.executeUpdate() != 1) throw new IllegalArgumentException("invitation_not_revocable");
                try (PreparedStatement referral=c.prepareStatement("UPDATE organizer_referrals SET status='REVOKED' WHERE invitation_uuid=? AND status IN ('INVITATION_SENT','DELIVERY_FAILED','EXPIRED')")) { referral.setString(1,uuid);referral.executeUpdate(); }
                audit(c, actor, "INVITATION_REVOKED", "invitation", uuid, null, now); c.commit();
            } catch (RuntimeException | SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw translate(e); }
    }

    @Override
    public Optional<OrganizerInvitation> preview(final String tokenHash, final Instant now) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM organizer_invitations WHERE token_hash=? AND status='PENDING' AND expires_at>?")) {
            ps.setString(1, tokenHash); ps.setString(2, now.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(invitation(rs)) : Optional.empty(); }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public void recordPublicAttempt(final String eventType, final String ipHash, final Instant now) {
        enforceRate(eventType, "anonymous", ipHash, now, 0, 30);
    }

    @Override
    public boolean redeem(final String tokenHash, final String userUuid, final String normalizedEmail, final Instant now) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Take SQLite's write lock before reading the token so simultaneous claims serialize.
                try (PreparedStatement lock = c.prepareStatement("UPDATE organizer_invite_settings SET updated_at=updated_at WHERE id=1")) { lock.executeUpdate(); }
                final String inviteUuid;
                try (PreparedStatement ps = c.prepareStatement("SELECT uuid,email_normalized FROM organizer_invitations WHERE token_hash=? AND status='PENDING' AND expires_at>?")) {
                    ps.setString(1, tokenHash); ps.setString(2, now.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || !normalize(rs.getString(2)).equals(normalizedEmail)) { c.rollback(); return false; }
                        inviteUuid = rs.getString(1);
                    }
                }
                final String email; final String role; final String status; final boolean verified;
                try (PreparedStatement ps = c.prepareStatement("SELECT email,role,status,email_verified FROM users WHERE uuid=?")) {
                    ps.setString(1, userUuid); try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { c.rollback(); return false; }
                        email = rs.getString(1); role = rs.getString(2); status = rs.getString(3); verified = rs.getInt(4) == 1;
                    }
                }
                if (!normalize(email).equals(normalizedEmail) || !verified || !"ACTIVE".equals(status)) { c.rollback(); return false; }
                final Set<String> roles = new LinkedHashSet<>();
                if (role != null) for (String part : role.split(",")) if (!part.isBlank()) roles.add(part.trim().toUpperCase());
                roles.add("ORGANIZER");
                try (PreparedStatement ps = c.prepareStatement("UPDATE users SET role=?,updated_at=? WHERE uuid=? AND email_verified=1 AND status='ACTIVE'")) {
                    ps.setString(1, String.join(",", roles)); ps.setString(2, now.toString()); ps.setString(3, userUuid);
                    if (ps.executeUpdate() != 1) { c.rollback(); return false; }
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE organizer_invitations SET status='ACCEPTED',accepted_at=?,accepted_by_user_uuid=? WHERE uuid=? AND token_hash=? AND status='PENDING' AND expires_at>?")) {
                    ps.setString(1, now.toString()); ps.setString(2, userUuid); ps.setString(3, inviteUuid); ps.setString(4, tokenHash); ps.setString(5, now.toString());
                    if (ps.executeUpdate() != 1) { c.rollback(); return false; }
                }
                audit(c, userUuid, "INVITATION_ACCEPTED", "invitation", inviteUuid, emailHash(email), now);
                try (PreparedStatement referral=c.prepareStatement("UPDATE organizer_referrals SET status='ACCEPTED' WHERE invitation_uuid=? AND status IN ('INVITATION_SENT','DELIVERY_FAILED','EXPIRED')")) { referral.setString(1,inviteUuid);referral.executeUpdate(); }
                c.commit(); return true;
            } catch (RuntimeException | SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public List<OrganizerReferral> listReferrals(final String status, final String inviterUuid, final int limit, final int offset) {
        final List<OrganizerReferral> rows = new ArrayList<>();
        final StringBuilder sql = new StringBuilder("SELECT * FROM organizer_referrals WHERE 1=1");
        final List<String> args = new ArrayList<>();
        if (status != null) { sql.append(" AND status=?"); args.add(status); }
        if (inviterUuid != null) { sql.append(" AND inviter_uuid=?"); args.add(inviterUuid); }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i=1; for (String arg:args) ps.setString(i++,arg); ps.setInt(i++,bounded(limit,100)); ps.setInt(i,Math.max(0,offset));
            try (ResultSet rs=ps.executeQuery()) { while(rs.next()) rows.add(referral(rs)); }
            return rows;
        } catch(SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public OrganizerReferral submitReferral(final String inviterUuid, final String email, final String ipHash,
            final int monthlyLimit, final Instant now) {
        enforceRate("REFERRAL",inviterUuid,ipHash,now,5,10);
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                // First write serializes concurrent quota checks across service replicas using SQLite.
                try (PreparedStatement lock = c.prepareStatement("UPDATE organizer_invite_settings SET updated_at=updated_at WHERE id=1")) { lock.executeUpdate(); }
                final OrganizerInviteSettings s = settings(c);
                if (!s.referralsEnabled() || !enabled(c, inviterUuid)) throw new IllegalArgumentException("referrals_not_enabled");
                try (PreparedStatement ps=c.prepareStatement("SELECT email,role,status,email_verified FROM users WHERE uuid=?")) {
                    ps.setString(1,inviterUuid); try(ResultSet rs=ps.executeQuery()) {
                        if(!rs.next() || !"ACTIVE".equals(rs.getString("status")) || rs.getInt("email_verified")!=1
                                || !hasRole(rs.getString("role"),"ORGANIZER")) throw new IllegalArgumentException("organizer_required");
                        if(normalize(rs.getString("email")).equals(normalize(email))) throw new IllegalArgumentException("self_referral_not_allowed");
                    }
                }
                final long count;
                try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM organizer_referrals WHERE inviter_uuid=? AND created_at>?")) {
                    ps.setString(1,inviterUuid); ps.setString(2,now.minus(30,ChronoUnit.DAYS).toString()); try(ResultSet rs=ps.executeQuery()){rs.next();count=rs.getLong(1);}
                }
                if(count>=monthlyLimit) throw new IllegalArgumentException("referral_quota_reached");
                if(hasActiveInvitation(c,normalize(email),now)) throw new IllegalArgumentException("email_already_invited");
                final String uuid=UUID.randomUUID().toString();
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO organizer_referrals(uuid,email,email_normalized,status,inviter_uuid,created_at) VALUES(?,?,?,'PENDING_REVIEW',?,?)")) {
                    ps.setString(1,uuid);ps.setString(2,normalize(email));ps.setString(3,normalize(email));ps.setString(4,inviterUuid);ps.setString(5,now.toString());ps.executeUpdate();
                }
                audit(c,inviterUuid,"REFERRAL_SUBMITTED","referral",uuid,emailHash(email),now); c.commit();
                return findReferral(c,uuid).orElseThrow();
            } catch(RuntimeException|SQLException e){c.rollback();throw e;} finally {c.setAutoCommit(true);}
        } catch(SQLException e){throw translate(e);}
    }

    @Override
    public OrganizerInvitation approveReferral(final String referralUuid, final String tokenHash, final String reviewerUuid,
            final Instant now, final Instant expiresAt, final String ipHash) {
        enforceRate("ADMIN_APPROVE",reviewerUuid,ipHash,now,100,200);
        try (Connection c=db.getConnection()) {
            c.setAutoCommit(false);
            try {
                final OrganizerReferral referral=findReferral(c,referralUuid).orElseThrow(()->new IllegalArgumentException("referral_not_found"));
                if(!"PENDING_REVIEW".equals(referral.status())) throw new IllegalArgumentException("referral_not_pending");
                expireOldInvites(c,referral.email(),now);
                if(hasActiveInvitation(c,normalize(referral.email()),now)) throw new IllegalArgumentException("email_already_invited");
                final String inviteUuid=UUID.randomUUID().toString();
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO organizer_invitations(uuid,email,email_normalized,token_hash,status,issuer_uuid,referral_uuid,created_at,expires_at) VALUES(?,?,?,?,'PENDING',?,?,?,?)")) {
                    ps.setString(1,inviteUuid);ps.setString(2,referral.email());ps.setString(3,normalize(referral.email()));ps.setString(4,tokenHash);
                    ps.setString(5,reviewerUuid);ps.setString(6,referralUuid);ps.setString(7,now.toString());ps.setString(8,expiresAt.toString());ps.executeUpdate();
                }
                try(PreparedStatement ps=c.prepareStatement("UPDATE organizer_referrals SET status='INVITATION_SENT',reviewer_uuid=?,reviewed_at=?,invitation_uuid=? WHERE uuid=? AND status='PENDING_REVIEW'")) {
                    ps.setString(1,reviewerUuid);ps.setString(2,now.toString());ps.setString(3,inviteUuid);ps.setString(4,referralUuid);
                    if(ps.executeUpdate()!=1) throw new IllegalArgumentException("referral_not_pending");
                }
                audit(c,reviewerUuid,"REFERRAL_APPROVED","referral",referralUuid,emailHash(referral.email()),now);
                audit(c,reviewerUuid,"INVITATION_CREATED_FROM_REFERRAL","invitation",inviteUuid,emailHash(referral.email()),now);
                c.commit(); return findInvitation(c,inviteUuid).orElseThrow();
            } catch(RuntimeException|SQLException e){c.rollback();throw e;} finally {c.setAutoCommit(true);}
        } catch(SQLException e){throw translate(e);}
    }

    @Override
    public void rejectReferral(final String referralUuid, final String reviewerUuid, final Instant now) {
        try(Connection c=db.getConnection()) {
            c.setAutoCommit(false);
            try(PreparedStatement ps=c.prepareStatement("UPDATE organizer_referrals SET status='REJECTED',reviewer_uuid=?,reviewed_at=? WHERE uuid=? AND status='PENDING_REVIEW'")) {
                ps.setString(1,reviewerUuid);ps.setString(2,now.toString());ps.setString(3,referralUuid);
                if(ps.executeUpdate()!=1) throw new IllegalArgumentException("referral_not_pending");
                audit(c,reviewerUuid,"REFERRAL_REJECTED","referral",referralUuid,null,now); c.commit();
            } catch(RuntimeException|SQLException e){c.rollback();throw e;} finally {c.setAutoCommit(true);}
        }catch(SQLException e){throw new RuntimeException(e);}
    }

    @Override
    public void setReferrerEnabled(final String userUuid, final boolean enabled, final String actor, final Instant now) {
        try(Connection c=db.getConnection()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement check=c.prepareStatement("""
                        SELECT u.role,u.status,u.email_verified,
                            EXISTS(SELECT 1 FROM organizer_invitations i WHERE i.accepted_by_user_uuid=u.uuid
                                AND i.status='ACCEPTED' AND i.referral_uuid IS NOT NULL) AS referral_invitee
                        FROM users u WHERE u.uuid=?
                        """)) {
                    check.setString(1,userUuid);try(ResultSet rs=check.executeQuery()) {
                        if(!rs.next() || !"ACTIVE".equals(rs.getString("status")) || rs.getInt("email_verified")!=1
                                || !hasRole(rs.getString("role"),"ORGANIZER")) throw new IllegalArgumentException("organizer_not_found");
                        if(rs.getInt("referral_invitee") == 1) throw new IllegalArgumentException("referral_invitees_not_eligible");
                    }
                }
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO organizer_referrer_eligibility(user_uuid,enabled,updated_by,updated_at) VALUES(?,?,?,?) ON CONFLICT(user_uuid) DO UPDATE SET enabled=excluded.enabled,updated_by=excluded.updated_by,updated_at=excluded.updated_at")) {
                    ps.setString(1,userUuid);ps.setInt(2,enabled?1:0);ps.setString(3,actor);ps.setString(4,now.toString());ps.executeUpdate();
                }
                audit(c,actor,enabled?"REFERRER_ENABLED":"REFERRER_DISABLED","user",userUuid,null,now);
                c.commit();
            } catch(RuntimeException|SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(true); }
        }catch(SQLException e){throw new RuntimeException(e);}
    }

    @Override public List<String> listEnabledReferrers() {
        final List<String> out=new ArrayList<>();
        try(Connection c=db.getConnection();PreparedStatement ps=c.prepareStatement("SELECT e.user_uuid,u.role FROM organizer_referrer_eligibility e JOIN users u ON u.uuid=e.user_uuid WHERE e.enabled=1 AND u.status='ACTIVE' AND u.email_verified=1 ORDER BY u.username")) {
            try(ResultSet rs=ps.executeQuery()){while(rs.next())if(hasRole(rs.getString(2),"ORGANIZER"))out.add(rs.getString(1));}return out;
        }catch(SQLException e){throw new RuntimeException(e);}
    }

    @Override public List<String> listReferralEligibleOrganizers() {
        final List<String> out=new ArrayList<>();
        try(Connection c=db.getConnection();PreparedStatement ps=c.prepareStatement("""
                SELECT u.uuid,u.role FROM users u WHERE u.status='ACTIVE' AND u.email_verified=1
                    AND (u.email IS NOT NULL AND TRIM(u.email)!='')
                    AND NOT EXISTS(SELECT 1 FROM organizer_invitations i WHERE i.accepted_by_user_uuid=u.uuid
                        AND i.status='ACCEPTED' AND i.referral_uuid IS NOT NULL)
                ORDER BY u.username
                """)) {
            try(ResultSet rs=ps.executeQuery()){while(rs.next())if(hasRole(rs.getString(2),"ORGANIZER"))out.add(rs.getString(1));}return out;
        }catch(SQLException e){throw new RuntimeException(e);}
    }

    @Override public boolean isReferrerEnabled(final String userUuid) {
        try(Connection c=db.getConnection()){return enabled(c,userUuid);}catch(SQLException e){throw new RuntimeException(e);}
    }

    @Override public int remainingReferrals(final String userUuid, final int monthlyLimit, final Instant now) {
        try(Connection c=db.getConnection();PreparedStatement ps=c.prepareStatement(
                "SELECT COUNT(*) FROM organizer_referrals WHERE inviter_uuid=? AND created_at>?")) {
            ps.setString(1,userUuid);ps.setString(2,now.minus(30,ChronoUnit.DAYS).toString());
            try(ResultSet rs=ps.executeQuery()){rs.next();return Math.max(0,monthlyLimit-rs.getInt(1));}
        }catch(SQLException e){throw new RuntimeException(e);}
    }

    private Optional<OrganizerInvitation> findInvitation(Connection c,String uuid)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT * FROM organizer_invitations WHERE uuid=?")){ps.setString(1,uuid);try(ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(invitation(rs)):Optional.empty();}}
    }
    private Optional<OrganizerReferral> findReferral(Connection c,String uuid)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT * FROM organizer_referrals WHERE uuid=?")){ps.setString(1,uuid);try(ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(referral(rs)):Optional.empty();}}
    }
    private static OrganizerInviteSettings settings(Connection c)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT referrals_enabled,invitation_ttl_days,referral_limit FROM organizer_invite_settings WHERE id=1");ResultSet rs=ps.executeQuery()) {
            return rs.next()?new OrganizerInviteSettings(rs.getInt(1)==1,rs.getInt(2),rs.getInt(3)):new OrganizerInviteSettings(false,7,3);
        }
    }
    private static boolean enabled(Connection c,String userUuid)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT enabled FROM organizer_referrer_eligibility WHERE user_uuid=?")){ps.setString(1,userUuid);try(ResultSet rs=ps.executeQuery()){return rs.next()&&rs.getInt(1)==1;}}
    }
    private static void expireOldInvites(Connection c,String email,Instant now)throws SQLException {
        final String sql="SELECT uuid,email FROM organizer_invitations WHERE status IN ('PENDING','DELIVERY_FAILED') AND expires_at<=?"+(email==null?"":" AND email_normalized=?");
        final List<String[]> expired=new ArrayList<>();
        try(PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setString(1,now.toString());if(email!=null)ps.setString(2,normalize(email));
            try(ResultSet rs=ps.executeQuery()){while(rs.next())expired.add(new String[]{rs.getString(1),rs.getString(2)});}
        }
        if(expired.isEmpty())return;
        final String update="UPDATE organizer_invitations SET status='EXPIRED' WHERE uuid=? AND status IN ('PENDING','DELIVERY_FAILED') AND expires_at<=?";
        for(String[] item:expired) {
            try(PreparedStatement ps=c.prepareStatement(update)){ps.setString(1,item[0]);ps.setString(2,now.toString());if(ps.executeUpdate()==1){
                try(PreparedStatement referral=c.prepareStatement("UPDATE organizer_referrals SET status='EXPIRED' WHERE invitation_uuid=? AND status IN ('INVITATION_SENT','DELIVERY_FAILED')")){referral.setString(1,item[0]);referral.executeUpdate();}
                audit(c,"system","INVITATION_EXPIRED","invitation",item[0],emailHash(item[1]),now);
            }}
        }
    }
    private static boolean hasActiveInvitation(Connection c,String email,Instant now)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM organizer_invitations WHERE email_normalized=? AND status IN ('PENDING','DELIVERY_FAILED') AND expires_at>? LIMIT 1")){ps.setString(1,email);ps.setString(2,now.toString());try(ResultSet rs=ps.executeQuery()){return rs.next();}}
    }
    private void enforceRate(String event,String actor,String ipHash,Instant now,int actorLimit,int ipLimit) {
        final Instant since=now.minus(1,ChronoUnit.HOURS);
        try(Connection c=db.getConnection()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement lock=c.prepareStatement("UPDATE organizer_invite_settings SET updated_at=updated_at WHERE id=1")){lock.executeUpdate();}
                try(PreparedStatement ps=c.prepareStatement("DELETE FROM organizer_invite_rate_events WHERE occurred_at<?")){ps.setString(1,now.minus(30,ChronoUnit.DAYS).toString());ps.executeUpdate();}
                if(actorLimit>0) try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM organizer_invite_rate_events WHERE event_type=? AND actor_uuid=? AND occurred_at>?")){ps.setString(1,event);ps.setString(2,actor);ps.setString(3,since.toString());try(ResultSet rs=ps.executeQuery()){rs.next();if(rs.getInt(1)>=actorLimit)throw new IllegalArgumentException("rate_limit_exceeded");}}
                if(ipHash!=null&&!ipHash.isBlank()) try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM organizer_invite_rate_events WHERE event_type=? AND ip_hash=? AND occurred_at>?")){ps.setString(1,event);ps.setString(2,ipHash);ps.setString(3,since.toString());try(ResultSet rs=ps.executeQuery()){rs.next();if(rs.getInt(1)>=ipLimit)throw new IllegalArgumentException("rate_limit_exceeded");}}
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO organizer_invite_rate_events(uuid,event_type,actor_uuid,ip_hash,occurred_at) VALUES(?,?,?,?,?)")){ps.setString(1,UUID.randomUUID().toString());ps.setString(2,event);ps.setString(3,actor);ps.setString(4,ipHash);ps.setString(5,now.toString());ps.executeUpdate();}
                c.commit();
            } catch(RuntimeException|SQLException e) { c.rollback(); if(e instanceof RuntimeException runtime) throw runtime; else throw new RuntimeException(e); }
            finally { c.setAutoCommit(true); }
        } catch(SQLException e) { throw new RuntimeException(e); }
    }
    private static void audit(Connection c,String actor,String action,String type,String resourceId,String emailHash,Instant now)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO organizer_invite_audit(uuid,actor_uuid,action,resource_type,resource_id,email_hash,created_at) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1,UUID.randomUUID().toString());ps.setString(2,actor);ps.setString(3,action);ps.setString(4,type);ps.setString(5,resourceId);ps.setString(6,emailHash);ps.setString(7,now.toString());ps.executeUpdate();
        }
    }
    private static OrganizerInvitation invitation(ResultSet rs)throws SQLException {
        return new OrganizerInvitation(rs.getString("uuid"),rs.getString("email"),rs.getString("status"),rs.getString("issuer_uuid"),rs.getString("referral_uuid"),
                Instant.parse(rs.getString("created_at")),Instant.parse(rs.getString("expires_at")),parse(rs.getString("sent_at")),parse(rs.getString("accepted_at")),rs.getString("delivery_error"));
    }
    private static OrganizerReferral referral(ResultSet rs)throws SQLException {
        return new OrganizerReferral(rs.getString("uuid"),rs.getString("email"),rs.getString("status"),rs.getString("inviter_uuid"),rs.getString("reviewer_uuid"),rs.getString("invitation_uuid"),
                Instant.parse(rs.getString("created_at")),parse(rs.getString("reviewed_at")));
    }
    private static Instant parse(String value){return value==null?null:Instant.parse(value);}
    private static String normalize(String email){return email==null?"":email.trim().toLowerCase(java.util.Locale.ROOT);}
    private static boolean hasRole(String csv,String role){if(csv==null)return false;for(String p:csv.split(","))if(role.equalsIgnoreCase(p.trim()))return true;return false;}
    private static int bounded(int value,int max){return Math.max(1,Math.min(value,max));}
    private static String emailHash(String email){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(normalize(email).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static RuntimeException translate(SQLException e){return e.getMessage()!=null&&e.getMessage().contains("UNIQUE constraint failed: organizer_invitations.email_normalized")?new IllegalArgumentException("active_invitation_exists"):new RuntimeException(e);}
}
