package dev.rafex.insightbloom.query.adapters.outbound.sqlite;
import dev.rafex.insightbloom.query.domain.model.CloudWord;
import dev.rafex.insightbloom.query.domain.model.MessageType;
import dev.rafex.insightbloom.query.domain.ports.CloudWordRepository;
import java.sql.*;
import java.time.Instant;
import java.util.*;
public class SqliteCloudWordRepository implements CloudWordRepository {
    private final DatabaseManager db;
    public SqliteCloudWordRepository(DatabaseManager db) { this.db = db; }
    @Override
    public void save(CloudWord w) {
        String sql = """
            INSERT INTO cloud_words (uuid,conference_uuid,message_type,word_normalized,word_canonical,
                relevance_score,message_count,first_seen_at,last_seen_at,is_visible,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(conference_uuid,message_type,word_normalized) DO UPDATE SET
                word_canonical=excluded.word_canonical, relevance_score=excluded.relevance_score,
                message_count=excluded.message_count, last_seen_at=excluded.last_seen_at,
                is_visible=excluded.is_visible, updated_at=excluded.updated_at
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,w.getUuid()); ps.setString(2,w.getConferenceUuid());
            ps.setString(3,w.getMessageType().name()); ps.setString(4,w.getWordNormalized());
            ps.setString(5,w.getWordCanonical()); ps.setDouble(6,w.getRelevanceScore());
            ps.setLong(7,w.getMessageCount()); ps.setString(8,w.getFirstSeenAt().toString());
            ps.setString(9,w.getLastSeenAt().toString()); ps.setInt(10,w.isVisible()?1:0);
            ps.setString(11,w.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    @Override
    public Optional<CloudWord> findByConferenceAndWord(String conf, String word, MessageType type) {
        String sql = "SELECT * FROM cloud_words WHERE conference_uuid=? AND word_normalized=? AND message_type=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,conf); ps.setString(2,word); ps.setString(3,type.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }
    @Override
    public List<CloudWord> findVisibleByConferenceAndType(String conf, MessageType type) {
        List<CloudWord> list = new ArrayList<>();
        String sql = "SELECT * FROM cloud_words WHERE conference_uuid=? AND message_type=? AND is_visible=1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,conf); ps.setString(2,type.name());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }
    private CloudWord map(ResultSet rs) throws SQLException {
        return new CloudWord(rs.getString("uuid"),rs.getString("conference_uuid"),
            MessageType.valueOf(rs.getString("message_type")),rs.getString("word_normalized"),
            rs.getString("word_canonical"),rs.getDouble("relevance_score"),rs.getLong("message_count"),
            Instant.parse(rs.getString("first_seen_at")),Instant.parse(rs.getString("last_seen_at")),
            rs.getInt("is_visible")==1,Instant.parse(rs.getString("updated_at")));
    }
}
