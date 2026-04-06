package dev.rafex.insightbloom.stats.adapters.outbound.sqlite;
import dev.rafex.insightbloom.stats.domain.model.MessageType;
import dev.rafex.insightbloom.stats.domain.model.WordStats;
import dev.rafex.insightbloom.stats.domain.ports.WordStatsRepository;
import java.sql.*;
import java.time.Instant;
import java.util.*;
public class SqliteWordStatsRepository implements WordStatsRepository {
    private final DatabaseManager db;
    public SqliteWordStatsRepository(DatabaseManager db) { this.db = db; }
    @Override
    public void save(WordStats s) {
        String sql = """
            INSERT INTO word_stats (uuid,conference_uuid,message_type,word_normalized,word_canonical,
                count_total,count_visible,count_censored,score_intent,relevance_score,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(conference_uuid,message_type,word_normalized) DO UPDATE SET
                word_canonical=excluded.word_canonical,
                count_total=excluded.count_total,
                count_visible=excluded.count_visible,
                count_censored=excluded.count_censored,
                score_intent=excluded.score_intent,
                relevance_score=excluded.relevance_score,
                updated_at=excluded.updated_at
        """;
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,s.getUuid()); ps.setString(2,s.getConferenceUuid());
            ps.setString(3,s.getMessageType().name()); ps.setString(4,s.getWordNormalized());
            ps.setString(5,s.getWordCanonical()); ps.setLong(6,s.getCountTotal());
            ps.setLong(7,s.getCountVisible()); ps.setLong(8,s.getCountCensored());
            ps.setDouble(9,s.getScoreIntent()); ps.setDouble(10,s.getRelevanceScore());
            ps.setString(11,s.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    @Override
    public Optional<WordStats> findByConferenceAndWord(String conf, String word, MessageType type) {
        String sql = "SELECT * FROM word_stats WHERE conference_uuid=? AND word_normalized=? AND message_type=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,conf); ps.setString(2,word); ps.setString(3,type.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }
    @Override
    public List<WordStats> findByConference(String conf) {
        List<WordStats> list = new ArrayList<>();
        String sql = "SELECT * FROM word_stats WHERE conference_uuid=? ORDER BY relevance_score DESC";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1,conf); ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return list;
    }
    private WordStats map(ResultSet rs) throws SQLException {
        return new WordStats(rs.getString("uuid"),rs.getString("conference_uuid"),
            MessageType.valueOf(rs.getString("message_type")),rs.getString("word_normalized"),
            rs.getString("word_canonical"),rs.getLong("count_total"),rs.getLong("count_visible"),
            rs.getLong("count_censored"),rs.getDouble("score_intent"),rs.getDouble("relevance_score"),
            parseInstant(rs.getString("updated_at")));
    }

    private static Instant parseInstant(String s) {
        if (s == null) return Instant.now();
        String iso = s.contains("T") ? s : s.replace(" ", "T") + "Z";
        return Instant.parse(iso);
    }
}
