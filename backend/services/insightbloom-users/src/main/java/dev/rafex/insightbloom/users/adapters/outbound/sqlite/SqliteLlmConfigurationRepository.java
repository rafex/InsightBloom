package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.EventLlmSource;
import dev.rafex.insightbloom.users.domain.ports.LlmConfigurationRepository;
import java.sql.*;
import java.util.*;

public final class SqliteLlmConfigurationRepository implements LlmConfigurationRepository {
  private final DatabaseManager db;

  public SqliteLlmConfigurationRepository(DatabaseManager db) {
    this.db = db;
  }

  public Optional<EventLlmSource> find(String eventId, String capability) {
    try (var c = db.getConnection();
        var p =
            c.prepareStatement(
                "SELECT * FROM event_llm_sources WHERE event_uuid=? AND capability=?")) {
      p.setString(1, eventId);
      p.setString(2, capability);
      try (var rs = p.executeQuery()) {
        return rs.next() ? Optional.of(read(rs)) : Optional.empty();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("llm_storage_unavailable", e);
    }
  }

  private EventLlmSource read(ResultSet rs) throws SQLException {
    return new EventLlmSource(
        rs.getString("event_uuid"),
        rs.getString("capability"),
        rs.getString("mode"),
        rs.getString("member_uuid"));
  }

  public List<EventLlmSource> linkedTo(String userId) {
    try (var c = db.getConnection();
        var p =
            c.prepareStatement(
                "SELECT * FROM event_llm_sources WHERE mode='MEMBER_PROFILE' AND member_uuid=?")) {
      p.setString(1, userId);
      var out = new ArrayList<EventLlmSource>();
      try (var rs = p.executeQuery()) {
        while (rs.next()) out.add(read(rs));
      }
      return out;
    } catch (SQLException e) {
      throw new IllegalStateException("llm_storage_unavailable", e);
    }
  }

  public boolean withdrawMemberProfile(String userId, String eventId, String capability) {
    try (var c = db.getConnection()) {
      c.setAutoCommit(false);
      try {
        int changed;
        try (var p =
            c.prepareStatement(
                "UPDATE event_llm_sources SET mode='INHERIT_OWNER',member_uuid=NULL WHERE"
                    + " event_uuid=? AND capability=? AND mode='MEMBER_PROFILE' AND"
                    + " member_uuid=?")) {
          p.setString(1, eventId);
          p.setString(2, capability);
          p.setString(3, userId);
          changed = p.executeUpdate();
        }
        if (changed == 1) audit(c, userId, "member_profile_withdrawn", eventId, capability);
        c.commit();
        return changed == 1;
      } catch (SQLException e) {
        c.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("llm_storage_unavailable", e);
    }
  }

  public void save(EventLlmSource s, String actor) {
    try (var c = db.getConnection()) {
      c.setAutoCommit(false);
      try {
        try (var p =
            c.prepareStatement(
                "INSERT INTO event_llm_sources(event_uuid,capability,mode,member_uuid)"
                    + " VALUES(?,?,?,?) ON CONFLICT(event_uuid,capability) DO UPDATE SET"
                    + " mode=excluded.mode,member_uuid=excluded.member_uuid")) {
          p.setString(1, s.eventId());
          p.setString(2, s.capability());
          p.setString(3, s.mode());
          p.setString(4, s.memberId());
          p.executeUpdate();
        }
        audit(c, actor, "mode_" + s.mode(), s.eventId(), s.capability());
        c.commit();
      } catch (SQLException e) {
        c.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("llm_storage_unavailable", e);
    }
  }

  public void audit(String actor, String action, String eventId, String capability) {
    try (var c = db.getConnection()) {
      audit(c, actor, action, eventId, capability);
    } catch (SQLException e) {
      throw new IllegalStateException("llm_storage_unavailable", e);
    }
  }

  private void audit(Connection c, String actor, String action, String eventId, String capability)
      throws SQLException {
    try (var p =
        c.prepareStatement(
            "INSERT INTO llm_configuration_audit(actor,action,event_uuid,capability,occurred_at)"
                + " VALUES(?,?,?,?,?)")) {
      p.setString(1, actor);
      p.setString(2, action);
      p.setString(3, eventId);
      p.setString(4, capability);
      p.setString(5, java.time.Instant.now().toString());
      p.executeUpdate();
    }
  }
}
