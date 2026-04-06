package com.dash0.sctools.dao;

import com.dash0.sctools.model.Pov;
import com.dash0.sctools.util.DatabaseInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Pov CRUD operations.
 * All SQL uses PreparedStatement to prevent SQL injection.
 */
public class PovDao {

    private static final Logger LOG = LoggerFactory.getLogger(PovDao.class);

    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;

    /**
     * Creates a DAO that connects to the production database.
     */
    public PovDao() {
        this(DatabaseInitializer.JDBC_URL, DatabaseInitializer.JDBC_USER, DatabaseInitializer.JDBC_PASSWORD);
    }

    /**
     * Creates a DAO with custom connection parameters (used for testing with in-memory H2).
     */
    public PovDao(String jdbcUrl, String jdbcUser, String jdbcPassword) {
        this.jdbcUrl = jdbcUrl;
        this.jdbcUser = jdbcUser;
        this.jdbcPassword = jdbcPassword;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
    }

    /**
     * Returns all POVs ordered by created_at descending.
     */
    public List<Pov> findAll() {
        String sql = "SELECT id, name, account_name, sc_name, status, start_date, target_end_date, "
                + "description, created_at, updated_at FROM povs ORDER BY created_at DESC";
        List<Pov> povs = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                povs.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOG.error("Error finding all POVs", e);
            throw new RuntimeException("Error finding all POVs", e);
        }
        return povs;
    }

    /**
     * Finds a POV by its ID.
     *
     * @param id the POV ID
     * @return the Pov, or null if not found
     */
    public Pov findById(long id) {
        String sql = "SELECT id, name, account_name, sc_name, status, start_date, target_end_date, "
                + "description, created_at, updated_at FROM povs WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOG.error("Error finding POV by id: {}", id, e);
            throw new RuntimeException("Error finding POV by id: " + id, e);
        }
        return null;
    }

    /**
     * Creates a new POV. Sets createdAt and updatedAt to the current time.
     * The generated ID is set on the returned entry.
     *
     * @param pov the POV to create
     * @return the created POV with id, createdAt, and updatedAt set
     */
    public Pov create(Pov pov) {
        String sql = "INSERT INTO povs (name, account_name, sc_name, status, start_date, target_end_date, "
                + "description, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();
        pov.setCreatedAt(now);
        pov.setUpdatedAt(now);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, pov.getName());
            ps.setString(2, pov.getAccountName());
            ps.setString(3, pov.getScName());
            ps.setString(4, pov.getStatus());
            ps.setDate(5, pov.getStartDate() != null ? Date.valueOf(pov.getStartDate()) : null);
            ps.setDate(6, pov.getTargetEndDate() != null ? Date.valueOf(pov.getTargetEndDate()) : null);
            ps.setString(7, pov.getDescription());
            ps.setTimestamp(8, Timestamp.valueOf(pov.getCreatedAt()));
            ps.setTimestamp(9, Timestamp.valueOf(pov.getUpdatedAt()));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    pov.setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error creating POV", e);
            throw new RuntimeException("Error creating POV", e);
        }
        return pov;
    }

    /**
     * Updates an existing POV. Sets updatedAt to the current time.
     *
     * @param pov the POV to update (must have a valid id)
     * @return the updated POV with updatedAt refreshed
     */
    public Pov update(Pov pov) {
        String sql = "UPDATE povs SET name = ?, account_name = ?, sc_name = ?, status = ?, "
                + "start_date = ?, target_end_date = ?, description = ?, updated_at = ? WHERE id = ?";
        LocalDateTime now = LocalDateTime.now();
        pov.setUpdatedAt(now);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pov.getName());
            ps.setString(2, pov.getAccountName());
            ps.setString(3, pov.getScName());
            ps.setString(4, pov.getStatus());
            ps.setDate(5, pov.getStartDate() != null ? Date.valueOf(pov.getStartDate()) : null);
            ps.setDate(6, pov.getTargetEndDate() != null ? Date.valueOf(pov.getTargetEndDate()) : null);
            ps.setString(7, pov.getDescription());
            ps.setTimestamp(8, Timestamp.valueOf(pov.getUpdatedAt()));
            ps.setLong(9, pov.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error updating POV id: {}", pov.getId(), e);
            throw new RuntimeException("Error updating POV id: " + pov.getId(), e);
        }
        return pov;
    }

    /**
     * Deletes a POV by its ID. Associated criteria are cascade-deleted by the database.
     *
     * @param id the POV ID to delete
     */
    public void delete(long id) {
        String sql = "DELETE FROM povs WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error deleting POV id: {}", id, e);
            throw new RuntimeException("Error deleting POV id: " + id, e);
        }
    }

    /**
     * Maps a ResultSet row to a Pov object.
     */
    private Pov mapRow(ResultSet rs) throws SQLException {
        Pov pov = new Pov();
        pov.setId(rs.getLong("id"));
        pov.setName(rs.getString("name"));
        pov.setAccountName(rs.getString("account_name"));
        pov.setScName(rs.getString("sc_name"));
        pov.setStatus(rs.getString("status"));

        Date startDate = rs.getDate("start_date");
        if (startDate != null) {
            pov.setStartDate(startDate.toLocalDate());
        }

        Date targetEndDate = rs.getDate("target_end_date");
        if (targetEndDate != null) {
            pov.setTargetEndDate(targetEndDate.toLocalDate());
        }

        pov.setDescription(rs.getString("description"));

        Timestamp createdTs = rs.getTimestamp("created_at");
        if (createdTs != null) {
            pov.setCreatedAt(createdTs.toLocalDateTime());
        }

        Timestamp updatedTs = rs.getTimestamp("updated_at");
        if (updatedTs != null) {
            pov.setUpdatedAt(updatedTs.toLocalDateTime());
        }

        return pov;
    }
}
