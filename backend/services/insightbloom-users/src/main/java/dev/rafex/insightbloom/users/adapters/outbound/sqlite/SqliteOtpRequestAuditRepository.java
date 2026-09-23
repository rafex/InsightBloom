package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.OtpRequestAudit;
import dev.rafex.insightbloom.users.domain.ports.OtpRequestAuditPort;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SqliteOtpRequestAuditRepository implements OtpRequestAuditPort {
    private final DatabaseManager db;

    public SqliteOtpRequestAuditRepository(final DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(final OtpRequestAudit audit) {
        try (var c = db.getConnection(); var ps = c.prepareStatement("""
                INSERT INTO otp_request_audit (uuid, requested_at, account_uuid, client_ip, user_agent, outcome)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, audit.uuid());
            ps.setString(2, audit.requestedAt().toString());
            ps.setString(3, audit.accountUuid());
            ps.setString(4, audit.clientIp());
            ps.setString(5, truncate(audit.userAgent(), 512));
            ps.setString(6, audit.outcome());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("otp_audit_save_failed", e);
        }
    }

    @Override
    public Page find(final Filter filter, final int limit, final int offset) {
        final String where = where(filter);
        try (var c = db.getConnection()) {
            int total;
            try (var count = c.prepareStatement("SELECT COUNT(*) FROM otp_request_audit" + where)) {
                bind(count, filter, 1);
                try (var rs = count.executeQuery()) {
                    total = rs.next() ? rs.getInt(1) : 0;
                }
            }
            final List<OtpRequestAudit> items = new ArrayList<>();
            try (var ps = c.prepareStatement("""
                    SELECT uuid, requested_at, account_uuid, client_ip, user_agent, outcome
                    FROM otp_request_audit
                    """ + where + " ORDER BY requested_at DESC LIMIT ? OFFSET ?")) {
                int index = bind(ps, filter, 1);
                ps.setInt(index++, limit);
                ps.setInt(index, offset);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) items.add(map(rs));
                }
            }
            return new Page(List.copyOf(items), total);
        } catch (final SQLException e) {
            throw new RuntimeException("otp_audit_query_failed", e);
        }
    }

    @Override
    public int deleteOlderThan(final Instant cutoff) {
        try (var c = db.getConnection(); var ps = c.prepareStatement(
                "DELETE FROM otp_request_audit WHERE requested_at < ?")) {
            ps.setString(1, cutoff.toString());
            return ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("otp_audit_cleanup_failed", e);
        }
    }

    private static String where(final Filter filter) {
        final List<String> terms = new ArrayList<>();
        if (filter.from() != null) terms.add("requested_at >= ?");
        if (filter.to() != null) terms.add("requested_at <= ?");
        if (notBlank(filter.accountUuid())) terms.add("account_uuid = ?");
        if (notBlank(filter.clientIp())) terms.add("client_ip = ?");
        if (notBlank(filter.outcome())) terms.add("outcome = ?");
        return terms.isEmpty() ? "" : " WHERE " + String.join(" AND ", terms);
    }

    private static int bind(final PreparedStatement ps, final Filter filter, int index) throws SQLException {
        if (filter.from() != null) ps.setString(index++, filter.from().toString());
        if (filter.to() != null) ps.setString(index++, filter.to().toString());
        if (notBlank(filter.accountUuid())) ps.setString(index++, filter.accountUuid());
        if (notBlank(filter.clientIp())) ps.setString(index++, filter.clientIp());
        if (notBlank(filter.outcome())) ps.setString(index++, filter.outcome());
        return index;
    }

    private static OtpRequestAudit map(final ResultSet rs) throws SQLException {
        return new OtpRequestAudit(rs.getString("uuid"), Instant.parse(rs.getString("requested_at")),
                rs.getString("account_uuid"), rs.getString("client_ip"), rs.getString("user_agent"),
                rs.getString("outcome"));
    }

    private static boolean notBlank(final String value) { return value != null && !value.isBlank(); }

    private static String truncate(final String value, final int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
