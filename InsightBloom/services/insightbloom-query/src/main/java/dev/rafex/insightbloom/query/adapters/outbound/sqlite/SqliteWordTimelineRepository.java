package dev.rafex.insightbloom.query.adapters.outbound.sqlite;
import dev.rafex.insightbloom.query.domain.model.AuthorKind;
import dev.rafex.insightbloom.query.domain.model.MessageType;
import dev.rafex.insightbloom.query.domain.model.WordTimeline;
import dev.rafex.insightbloom.query.domain.ports.WordTimelineRepository;
import java.sql.*;
import java.time.Instant;
import java.util.*;
public class SqliteWordTimelineRepository implements WordTimelineRepository {
    private final DatabaseManager db;
    public SqliteWordTimelineRepository(DatabaseManager db) { this.db = db; }
    @Override
    public void save(WordTimeline t) {
        String sql = """
            INSERT OR REPLACE INTO word_timeline (uuid,conference_uuid,word_normalized,message_uuid,message_type,
                author_label,author_kind,detail_visible,received_at,is_visible,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,t.getUuid()); ps.setString(2,t.getConferenceUuid());
            ps.setString(3,t.getWordNormalized()); ps.setString(4,t.getMessageUuid());
            ps.setString(5,t.getMessageType().name()); ps.setString(6,t.getAuthorLabel());
            ps.setString(7,t.getAuthorKind().name()); ps.setString(8,t.getDetailVisible());
            ps.setString(9,t.getReceivedAt().toString()); ps.setInt(10,t.isVisible()?1:0);
            ps.setString(11,t.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    @Override
    public Optional<WordTimeline> findByMessageUuid(String msgUuid) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM word_timeline WHERE message_uuid=?")) {
            ps.setString(1,msgUuid); ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }
    @Override
    public List<WordTimeline> findVisibleByConferenceAndWord(String conf, String word) {
        List<WordTimeline> list = new ArrayList<>();
        String sql = "SELECT * FROM word_timeline WHERE conference_uuid=? AND word_normalized=? AND is_visible=1 ORDER BY received_at ASC";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,conf); ps.setString(2,word);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }
    private WordTimeline map(ResultSet rs) throws SQLException {
        return new WordTimeline(rs.getString("uuid"),rs.getString("conference_uuid"),
            rs.getString("word_normalized"),rs.getString("message_uuid"),
            MessageType.valueOf(rs.getString("message_type")),rs.getString("author_label"),
            AuthorKind.valueOf(rs.getString("author_kind")),rs.getString("detail_visible"),
            Instant.parse(rs.getString("received_at")),rs.getInt("is_visible")==1,
            Instant.parse(rs.getString("updated_at")));
    }
}
