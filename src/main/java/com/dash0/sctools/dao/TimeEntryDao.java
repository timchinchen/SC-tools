package com.dash0.sctools.dao;

import com.dash0.sctools.model.ActivityType;
import com.dash0.sctools.model.TimeEntry;
import com.dash0.sctools.util.DatabaseInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for TimeEntry CRUD operations.
 * All SQL uses PreparedStatement to prevent SQL injection.
 */
public class TimeEntryDao {

    private static final Logger LOG = LoggerFactory.getLogger(TimeEntryDao.class);

    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;

    /**
     * Creates a DAO that connects to the production database.
     */
    public TimeEntryDao() {
        this(DatabaseInitializer.JDBC_URL, DatabaseInitializer.JDBC_USER, DatabaseInitializer.JDBC_PASSWORD);
    }

    /**
     * Creates a DAO with custom connection parameters (used for testing with in-memory H2).
     */
    public TimeEntryDao(String jdbcUrl, String jdbcUser, String jdbcPassword) {
        this.jdbcUrl = jdbcUrl;
        this.jdbcUser = jdbcUser;
        this.jdbcPassword = jdbcPassword;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
    }

    /**
     * Returns all time entries ordered by date descending.
     */
    public List<TimeEntry> findAll() {
        String sql = "SELECT id, sc_name, date, hours, account_name, activity_type, description, created_at, updated_at "
                + "FROM time_entries ORDER BY date DESC";
        List<TimeEntry> entries = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                entries.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOG.error("Error finding all time entries", e);
            throw new RuntimeException("Error finding all time entries", e);
        }
        return entries;
    }

    /**
     * Finds a time entry by its ID.
     *
     * @param id the entry ID
     * @return the TimeEntry, or null if not found
     */
    public TimeEntry findById(long id) {
        String sql = "SELECT id, sc_name, date, hours, account_name, activity_type, description, created_at, updated_at "
                + "FROM time_entries WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOG.error("Error finding time entry by id: {}", id, e);
            throw new RuntimeException("Error finding time entry by id: " + id, e);
        }
        return null;
    }

    /**
     * Creates a new time entry. Sets createdAt and updatedAt to the current time.
     * The generated ID is set on the returned entry.
     *
     * @param entry the time entry to create
     * @return the created entry with id, createdAt, and updatedAt set
     */
    public TimeEntry create(TimeEntry entry) {
        String sql = "INSERT INTO time_entries (sc_name, date, hours, account_name, activity_type, description, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, entry.getScName());
            ps.setDate(2, Date.valueOf(entry.getDate()));
            ps.setBigDecimal(3, entry.getHours());
            ps.setString(4, entry.getAccountName());
            ps.setString(5, entry.getActivityType().name());
            ps.setString(6, entry.getDescription());
            ps.setTimestamp(7, Timestamp.valueOf(entry.getCreatedAt()));
            ps.setTimestamp(8, Timestamp.valueOf(entry.getUpdatedAt()));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    entry.setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error creating time entry", e);
            throw new RuntimeException("Error creating time entry", e);
        }
        return entry;
    }

    /**
     * Updates an existing time entry. Sets updatedAt to the current time.
     *
     * @param entry the time entry to update (must have a valid id)
     * @return the updated entry with updatedAt refreshed
     */
    public TimeEntry update(TimeEntry entry) {
        String sql = "UPDATE time_entries SET sc_name = ?, date = ?, hours = ?, account_name = ?, "
                + "activity_type = ?, description = ?, updated_at = ? WHERE id = ?";
        LocalDateTime now = LocalDateTime.now();
        entry.setUpdatedAt(now);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.getScName());
            ps.setDate(2, Date.valueOf(entry.getDate()));
            ps.setBigDecimal(3, entry.getHours());
            ps.setString(4, entry.getAccountName());
            ps.setString(5, entry.getActivityType().name());
            ps.setString(6, entry.getDescription());
            ps.setTimestamp(7, Timestamp.valueOf(entry.getUpdatedAt()));
            ps.setLong(8, entry.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error updating time entry id: {}", entry.getId(), e);
            throw new RuntimeException("Error updating time entry id: " + entry.getId(), e);
        }
        return entry;
    }

    /**
     * Deletes a time entry by its ID.
     *
     * @param id the entry ID to delete
     */
    public void delete(long id) {
        String sql = "DELETE FROM time_entries WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error deleting time entry id: {}", id, e);
            throw new RuntimeException("Error deleting time entry id: " + id, e);
        }
    }

    /**
     * Returns the sum of all time entry hours.
     *
     * @return the total hours, or BigDecimal.ZERO if no entries exist
     */
    public BigDecimal getTotalHours() {
        String sql = "SELECT COALESCE(SUM(hours), 0) FROM time_entries";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            LOG.error("Error calculating total hours", e);
            throw new RuntimeException("Error calculating total hours", e);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Returns the count of all time entries.
     *
     * @return the number of time entries
     */
    public long getCount() {
        String sql = "SELECT COUNT(*) FROM time_entries";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            LOG.error("Error counting time entries", e);
            throw new RuntimeException("Error counting time entries", e);
        }
        return 0;
    }

    /**
     * Maps a ResultSet row to a TimeEntry object.
     */
    private TimeEntry mapRow(ResultSet rs) throws SQLException {
        TimeEntry entry = new TimeEntry();
        entry.setId(rs.getLong("id"));
        entry.setScName(rs.getString("sc_name"));

        Date sqlDate = rs.getDate("date");
        if (sqlDate != null) {
            entry.setDate(sqlDate.toLocalDate());
        }

        BigDecimal hours = rs.getBigDecimal("hours");
        entry.setHours(hours);

        entry.setAccountName(rs.getString("account_name"));

        String activityTypeStr = rs.getString("activity_type");
        if (activityTypeStr != null) {
            entry.setActivityType(ActivityType.valueOf(activityTypeStr));
        }

        entry.setDescription(rs.getString("description"));

        Timestamp createdTs = rs.getTimestamp("created_at");
        if (createdTs != null) {
            entry.setCreatedAt(createdTs.toLocalDateTime());
        }

        Timestamp updatedTs = rs.getTimestamp("updated_at");
        if (updatedTs != null) {
            entry.setUpdatedAt(updatedTs.toLocalDateTime());
        }

        return entry;
    }
}
