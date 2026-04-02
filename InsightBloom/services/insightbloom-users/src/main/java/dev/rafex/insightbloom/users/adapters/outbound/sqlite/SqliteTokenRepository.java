package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.ports.TokenRepository;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

public class SqliteTokenRepository implements TokenRepository {
    private final DatabaseManager db;

    public SqliteTokenRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(Token token) {
        String sql = """
            INSERT INTO tokens (uuid, user_uuid, guest_user_uuid, token_kind, token_value, expires_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token.getUuid());
            ps.setString(2, token.getUserUuid());
            ps.setString(3, token.getGuestUserUuid());
            ps.setString(4, token.getTokenKind().name());
            ps.setString(5, token.getTokenValue());
            ps.setString(6, token.getExpiresAt().toString());
            ps.setString(7, token.getCreatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Token> findByValue(String tokenValue) {
        String sql = "SELECT * FROM tokens WHERE token_value = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenValue);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String revokedStr = rs.getString("revoked_at");
                return Optional.of(new Token(
                    rs.getString("uuid"), rs.getString("user_uuid"), rs.getString("guest_user_uuid"),
                    TokenKind.valueOf(rs.getString("token_kind")), rs.getString("token_value"),
                    Instant.parse(rs.getString("expires_at")), Instant.parse(rs.getString("created_at")),
                    revokedStr != null ? Instant.parse(revokedStr) : null
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }
}
