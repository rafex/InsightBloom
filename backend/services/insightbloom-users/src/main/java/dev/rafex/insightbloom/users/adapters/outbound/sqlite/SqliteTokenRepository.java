package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.ports.TokenRepository;
import dev.rafex.insightbloom.users.domain.services.PasswordService;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteTokenRepository implements TokenRepository {
    private final DatabaseManager db;

    public SqliteTokenRepository(DatabaseManager db) {
        this.db = db;
    }

    // The raw token value is given to the client; only its SHA-256 hash is stored in the DB.
    // This prevents token theft from a DB dump — the hash cannot be reversed.
    private static String hashToken(final String rawToken) {
        return PasswordService.sha256(rawToken);
    }

    @Override
    public void save(final Token token) {
        final String sql = """
            INSERT INTO tokens (uuid, user_uuid, guest_user_uuid, token_kind, token_value, expires_at, created_at, device_fingerprint)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token.getUuid());
            ps.setString(2, token.getUserUuid());
            ps.setString(3, token.getGuestUserUuid());
            ps.setString(4, token.getTokenKind().name());
            ps.setString(5, hashToken(token.getTokenValue()));
            ps.setString(6, token.getExpiresAt().toString());
            ps.setString(7, token.getCreatedAt().toString());
            ps.setString(8, token.getDeviceFingerprint());
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Token> findByValue(final String tokenValue) {
        final String sql = "SELECT * FROM tokens WHERE token_value = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hashToken(tokenValue));
            final ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                final String revokedStr = rs.getString("revoked_at");
                // Return a Token with the original (raw) token value so callers can still use it
                return Optional.of(new Token(
                    rs.getString("uuid"), rs.getString("user_uuid"), rs.getString("guest_user_uuid"),
                    TokenKind.valueOf(rs.getString("token_kind")), tokenValue,
                    parseInstant(rs.getString("expires_at")), parseInstant(rs.getString("created_at")),
                    revokedStr != null ? parseInstant(revokedStr) : null,
                    rs.getString("device_fingerprint")
                ));
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public void revokeAllForUser(final String userUuid) {
        final String sql = "UPDATE tokens SET revoked_at = ? WHERE user_uuid = ? AND revoked_at IS NULL";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, userUuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void revokeByValue(final String tokenValue) {
        final String sql = "UPDATE tokens SET revoked_at = ? WHERE token_value = ? AND revoked_at IS NULL";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, hashToken(tokenValue));
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Token> findActiveByUser(final String userUuid) {
        final String sql = """
            SELECT * FROM tokens
            WHERE user_uuid = ? AND revoked_at IS NULL AND expires_at > ?
            ORDER BY created_at ASC
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userUuid);
            ps.setString(2, Instant.now().toString());
            return queryList(ps);
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Token> findActiveByFingerprint(final String deviceFingerprint) {
        final String sql = """
            SELECT * FROM tokens
            WHERE device_fingerprint = ? AND revoked_at IS NULL AND expires_at > ?
            ORDER BY created_at ASC
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceFingerprint);
            ps.setString(2, Instant.now().toString());
            return queryList(ps);
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void revokeAllForFingerprint(final String deviceFingerprint) {
        final String sql = "UPDATE tokens SET revoked_at = ? WHERE device_fingerprint = ? AND revoked_at IS NULL";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, deviceFingerprint);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void revokeByUuid(final String tokenUuid) {
        final String sql = "UPDATE tokens SET revoked_at = ? WHERE uuid = ? AND revoked_at IS NULL";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, tokenUuid);
            ps.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // El valor crudo del token no se recupera del hash almacenado -- estas filas se usan solo
    // para contar/identificar sesiones (uuid, sujeto, fingerprint), nunca para reautenticar.
    private static List<Token> queryList(final PreparedStatement ps) throws SQLException {
        final List<Token> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                final String revokedStr = rs.getString("revoked_at");
                result.add(new Token(
                    rs.getString("uuid"), rs.getString("user_uuid"), rs.getString("guest_user_uuid"),
                    TokenKind.valueOf(rs.getString("token_kind")), null,
                    parseInstant(rs.getString("expires_at")), parseInstant(rs.getString("created_at")),
                    revokedStr != null ? parseInstant(revokedStr) : null,
                    rs.getString("device_fingerprint")
                ));
            }
        }
        return result;
    }

    private static Instant parseInstant(String s) {
        if (s == null) return Instant.now();
        String iso = s.contains("T") ? s : s.replace(" ", "T") + "Z";
        return Instant.parse(iso);
    }
}
