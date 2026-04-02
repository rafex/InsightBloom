package dev.rafex.insightbloom.ingest.adapters.outbound.sqlite;
import dev.rafex.insightbloom.ingest.domain.model.*;
import dev.rafex.insightbloom.ingest.domain.ports.MessageRepository;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
public class SqliteMessageRepository implements MessageRepository {
    private final DatabaseManager db;
    public SqliteMessageRepository(DatabaseManager db) { this.db = db; }
    @Override
    public void save(Message m) {
        String sql = """
            INSERT OR REPLACE INTO messages
            (uuid,conference_uuid,author_uuid,author_kind,device_fingerprint,message_type,source_type,
             received_at,word_original,word_normalized,word_canonical,word_intent,detail_original,
             detail_visible,detail_intent,word_status,detail_status,is_visible,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,m.getUuid()); ps.setString(2,m.getConferenceUuid());
            ps.setString(3,m.getAuthorUuid()); ps.setString(4,m.getAuthorKind().name());
            ps.setString(5,m.getDeviceFingerprint()); ps.setString(6,m.getMessageType().name());
            ps.setString(7,m.getSourceType().name()); ps.setString(8,m.getReceivedAt().toString());
            ps.setString(9,m.getWordOriginal()); ps.setString(10,m.getWordNormalized());
            ps.setString(11,m.getWordCanonical());
            ps.setString(12, m.getWordIntent() != null ? m.getWordIntent().name() : null);
            ps.setString(13,m.getDetailOriginal()); ps.setString(14,m.getDetailVisible());
            ps.setString(15, m.getDetailIntent() != null ? m.getDetailIntent().name() : null);
            ps.setString(16,m.getWordStatus().name()); ps.setString(17,m.getDetailStatus().name());
            ps.setInt(18,m.isVisible()?1:0); ps.setString(19,m.getCreatedAt().toString());
            ps.setString(20,m.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    @Override
    public Optional<Message> findByUuid(String uuid) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM messages WHERE uuid=?")) {
            ps.setString(1,uuid); ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }
    private Message map(ResultSet rs) throws SQLException {
        String wIntent = rs.getString("word_intent");
        String dIntent = rs.getString("detail_intent");
        return new Message(rs.getString("uuid"),rs.getString("conference_uuid"),
            rs.getString("author_uuid"), AuthorKind.valueOf(rs.getString("author_kind")),
            rs.getString("device_fingerprint"), MessageType.valueOf(rs.getString("message_type")),
            SourceType.valueOf(rs.getString("source_type")), Instant.parse(rs.getString("received_at")),
            rs.getString("word_original"),rs.getString("word_normalized"),rs.getString("word_canonical"),
            wIntent!=null ? WordIntent.valueOf(wIntent) : null,
            rs.getString("detail_original"),rs.getString("detail_visible"),
            dIntent!=null ? DetailIntent.valueOf(dIntent) : null,
            ContentStatus.valueOf(rs.getString("word_status")), ContentStatus.valueOf(rs.getString("detail_status")),
            rs.getInt("is_visible")==1, Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("updated_at")));
    }
}
