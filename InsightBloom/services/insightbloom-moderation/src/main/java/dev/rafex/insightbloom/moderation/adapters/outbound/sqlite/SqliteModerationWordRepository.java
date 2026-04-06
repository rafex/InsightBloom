package dev.rafex.insightbloom.moderation.adapters.outbound.sqlite;
import dev.rafex.insightbloom.moderation.domain.model.ContentStatus;
import dev.rafex.insightbloom.moderation.domain.model.ModerationWord;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationWordRepository;
import java.sql.*;
import java.time.Instant;
import java.util.*;
public class SqliteModerationWordRepository implements ModerationWordRepository {
    private final DatabaseManager db;
    public SqliteModerationWordRepository(DatabaseManager db) { this.db = db; }
    @Override
    public void save(ModerationWord w) {
        String sql = "INSERT OR REPLACE INTO moderation_words (uuid,conference_uuid,word_normalized,word_canonical,status,reason,edited_value,updated_by_user_uuid,updated_at) VALUES(?,?,?,?,?,?,?,?,?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, w.getUuid()); ps.setString(2, w.getConferenceUuid());
            ps.setString(3, w.getWordNormalized()); ps.setString(4, w.getWordCanonical());
            ps.setString(5, w.getStatus().name()); ps.setString(6, w.getReason());
            ps.setString(7, w.getEditedValue()); ps.setString(8, w.getUpdatedByUserUuid());
            ps.setString(9, w.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    @Override
    public Optional<ModerationWord> findByUuid(String uuid) {
        return query("SELECT * FROM moderation_words WHERE uuid=?", uuid);
    }
    @Override
    public Optional<ModerationWord> findByConferenceAndNormalized(String conf, String word) {
        String sql = "SELECT * FROM moderation_words WHERE conference_uuid=? AND word_normalized=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conf); ps.setString(2, word);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }
    @Override
    public List<ModerationWord> findByConference(String conf, int page, int pageSize) {
        List<ModerationWord> list = new ArrayList<>();
        String sql = "SELECT * FROM moderation_words WHERE conference_uuid=? ORDER BY updated_at DESC LIMIT ? OFFSET ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conf); ps.setInt(2, pageSize); ps.setInt(3, (page-1)*pageSize);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }
    @Override
    public List<ModerationWord> findByConferenceAndStatus(String conf, String status, int page, int pageSize) {
        List<ModerationWord> list = new ArrayList<>();
        String sql = "SELECT * FROM moderation_words WHERE conference_uuid=? AND status=? ORDER BY updated_at DESC LIMIT ? OFFSET ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conf); ps.setString(2, status); ps.setInt(3, pageSize); ps.setInt(4, (page-1)*pageSize);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }
    @Override
    public long countByConference(String conf) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM moderation_words WHERE conference_uuid=?")) {
            ps.setString(1, conf); ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
        return 0;
    }
    @Override
    public long countByConferenceAndStatus(String conf, String status) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM moderation_words WHERE conference_uuid=? AND status=?")) {
            ps.setString(1, conf); ps.setString(2, status); ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
        return 0;
    }
    private Optional<ModerationWord> query(String sql, String param) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param); ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }
    private ModerationWord map(ResultSet rs) throws SQLException {
        return new ModerationWord(rs.getString("uuid"), rs.getString("conference_uuid"),
            rs.getString("word_normalized"), rs.getString("word_canonical"),
            ContentStatus.valueOf(rs.getString("status")), rs.getString("reason"),
            rs.getString("edited_value"), rs.getString("updated_by_user_uuid"),
            Instant.parse(rs.getString("updated_at")));
    }
}
