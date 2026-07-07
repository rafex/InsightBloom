package dev.rafex.insightbloom.users.adapters.outbound.sqlite;

import dev.rafex.insightbloom.users.domain.model.Timezone;
import dev.rafex.insightbloom.users.domain.ports.TimezoneRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteTimezoneRepository implements TimezoneRepository {
    private final DatabaseManager db;

    public SqliteTimezoneRepository(final DatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<Timezone> findAll() {
        final List<Timezone> list = new ArrayList<>();
        final String sql = "SELECT * FROM timezones ORDER BY utc_offset_minutes, label";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    @Override
    public Timezone findDefault() {
        final String sql = "SELECT * FROM timezones WHERE is_default = 1 LIMIT 1";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return map(rs);
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("No default timezone seeded");
    }

    @Override
    public Optional<Timezone> findById(final int id) {
        final String sql = "SELECT * FROM timezones WHERE id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    private static Timezone map(final ResultSet rs) throws SQLException {
        return new Timezone(rs.getInt("id"), rs.getString("iana_name"), rs.getString("label"),
                rs.getInt("utc_offset_minutes"), rs.getInt("is_default") != 0);
    }
}
