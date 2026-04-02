package dev.rafex.insightbloom.moderation.adapters.outbound.sqlite;
import dev.rafex.insightbloom.moderation.domain.model.BlockedTerm;
import dev.rafex.insightbloom.moderation.domain.ports.BlockedTermRepository;
import java.sql.*;
import java.time.Instant;
import java.util.*;
public class SqliteBlockedTermRepository implements BlockedTermRepository {
    private final DatabaseManager db;
    public SqliteBlockedTermRepository(DatabaseManager db) { this.db = db; }
    @Override
    public void save(BlockedTerm term) {
        String sql = "INSERT OR REPLACE INTO blocked_terms (uuid,scope,term_normalized,status,created_by_user_uuid,created_at,updated_at) VALUES(?,?,?,?,?,?,?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, term.getUuid()); ps.setString(2, term.getScope());
            ps.setString(3, term.getTermNormalized()); ps.setString(4, term.getStatus());
            ps.setString(5, term.getCreatedByUserUuid()); ps.setString(6, term.getCreatedAt().toString());
            ps.setString(7, term.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    @Override
    public List<BlockedTerm> findAllActive() {
        List<BlockedTerm> list = new ArrayList<>();
        String sql = "SELECT * FROM blocked_terms WHERE status='active'";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }
    @Override
    public boolean existsByTerm(String term) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT 1 FROM blocked_terms WHERE term_normalized=? AND status='active'")) {
            ps.setString(1, term); return ps.executeQuery().next();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    private BlockedTerm map(ResultSet rs) throws SQLException {
        return new BlockedTerm(rs.getString("uuid"), rs.getString("scope"), rs.getString("term_normalized"),
            rs.getString("status"), rs.getString("created_by_user_uuid"),
            Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("updated_at")));
    }
}
