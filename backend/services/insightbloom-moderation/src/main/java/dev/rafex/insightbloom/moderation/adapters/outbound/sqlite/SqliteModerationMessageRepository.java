package dev.rafex.insightbloom.moderation.adapters.outbound.sqlite;
import dev.rafex.insightbloom.moderation.domain.model.ContentStatus;
import dev.rafex.insightbloom.moderation.domain.model.ModerationMessage;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;
import java.sql.*;
import java.time.Instant;
import java.util.*;
public class SqliteModerationMessageRepository implements ModerationMessageRepository {
    private final DatabaseManager db;
    public SqliteModerationMessageRepository(DatabaseManager db) { this.db = db; }
    @Override
    public void save(ModerationMessage m) {
        String sql = "INSERT OR REPLACE INTO moderation_messages (uuid,message_uuid,conference_uuid,word_status,detail_status,reason,edited_word_value,edited_detail_value,updated_by_user_uuid,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, m.getUuid()); ps.setString(2, m.getMessageUuid());
            ps.setString(3, m.getConferenceUuid()); ps.setString(4, m.getWordStatus().name());
            ps.setString(5, m.getDetailStatus().name()); ps.setString(6, m.getReason());
            ps.setString(7, m.getEditedWordValue()); ps.setString(8, m.getEditedDetailValue());
            ps.setString(9, m.getUpdatedByUserUuid()); ps.setString(10, m.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    @Override
    public Optional<ModerationMessage> findByUuid(String uuid) {
        return query("SELECT * FROM moderation_messages WHERE uuid=?", uuid);
    }
    @Override
    public Optional<ModerationMessage> findByMessageUuid(String msgUuid) {
        return query("SELECT * FROM moderation_messages WHERE message_uuid=?", msgUuid);
    }
    @Override
    public List<ModerationMessage> findByConference(String conf, int page, int pageSize) {
        List<ModerationMessage> list = new ArrayList<>();
        String sql = "SELECT * FROM moderation_messages WHERE conference_uuid=? ORDER BY updated_at DESC LIMIT ? OFFSET ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conf); ps.setInt(2, pageSize); ps.setInt(3, (page-1)*pageSize);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }
    @Override
    public List<ModerationMessage> findByConferenceAndStatus(String conf, String status, int page, int pageSize) {
        List<ModerationMessage> list = new ArrayList<>();
        String sql = "SELECT * FROM moderation_messages WHERE conference_uuid=? AND word_status=? ORDER BY updated_at DESC LIMIT ? OFFSET ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, conf); ps.setString(2, status); ps.setInt(3, pageSize); ps.setInt(4, (page-1)*pageSize);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }
    @Override
    public long countByConference(String conf) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM moderation_messages WHERE conference_uuid=?")) {
            ps.setString(1, conf); ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
        return 0;
    }
    @Override
    public long countByConferenceAndStatus(String conf, String status) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM moderation_messages WHERE conference_uuid=? AND word_status=?")) {
            ps.setString(1, conf); ps.setString(2, status); ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
        return 0;
    }
    private Optional<ModerationMessage> query(String sql, String param) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param); ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }
    private ModerationMessage map(ResultSet rs) throws SQLException {
        return new ModerationMessage(rs.getString("uuid"), rs.getString("message_uuid"),
            rs.getString("conference_uuid"), ContentStatus.valueOf(rs.getString("word_status")),
            ContentStatus.valueOf(rs.getString("detail_status")), rs.getString("reason"),
            rs.getString("edited_word_value"), rs.getString("edited_detail_value"),
            rs.getString("updated_by_user_uuid"), parseInstant(rs.getString("updated_at")));
    }

    private static Instant parseInstant(String s) {
        if (s == null) return Instant.now();
        String iso = s.contains("T") ? s : s.replace(" ", "T") + "Z";
        return Instant.parse(iso);
    }
}
