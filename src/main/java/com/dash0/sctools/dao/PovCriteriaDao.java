package com.dash0.sctools.dao;

import com.dash0.sctools.model.PovCriteria;
import com.dash0.sctools.util.DatabaseInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for PovCriteria CRUD operations.
 * All SQL uses PreparedStatement to prevent SQL injection.
 */
public class PovCriteriaDao {

    private static final Logger LOG = LoggerFactory.getLogger(PovCriteriaDao.class);

    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;

    /**
     * Creates a DAO that connects to the production database.
     */
    public PovCriteriaDao() {
        this(DatabaseInitializer.JDBC_URL, DatabaseInitializer.JDBC_USER, DatabaseInitializer.JDBC_PASSWORD);
    }

    /**
     * Creates a DAO with custom connection parameters (used for testing with in-memory H2).
     */
    public PovCriteriaDao(String jdbcUrl, String jdbcUser, String jdbcPassword) {
        this.jdbcUrl = jdbcUrl;
        this.jdbcUser = jdbcUser;
        this.jdbcPassword = jdbcPassword;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
    }

    /**
     * Returns all criteria for a specific POV, ordered by id ascending.
     *
     * @param povId the POV ID
     * @return list of criteria for the POV
     */
    public List<PovCriteria> findByPovId(long povId) {
        String sql = "SELECT id, pov_id, name, description, status, weight, notes, created_at, updated_at "
                + "FROM pov_criteria WHERE pov_id = ? ORDER BY id ASC";
        List<PovCriteria> criteria = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, povId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    criteria.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error finding criteria for POV id: {}", povId, e);
            throw new RuntimeException("Error finding criteria for POV id: " + povId, e);
        }
        return criteria;
    }

    /**
     * Finds a criterion by its ID.
     *
     * @param id the criterion ID
     * @return the PovCriteria, or null if not found
     */
    public PovCriteria findById(long id) {
        String sql = "SELECT id, pov_id, name, description, status, weight, notes, created_at, updated_at "
                + "FROM pov_criteria WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOG.error("Error finding criterion by id: {}", id, e);
            throw new RuntimeException("Error finding criterion by id: " + id, e);
        }
        return null;
    }

    /**
     * Creates a new criterion. Sets createdAt and updatedAt to the current time.
     * The generated ID is set on the returned entry.
     *
     * @param criteria the criterion to create
     * @return the created criterion with id, createdAt, and updatedAt set
     */
    public PovCriteria create(PovCriteria criteria) {
        String sql = "INSERT INTO pov_criteria (pov_id, name, description, status, weight, notes, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();
        criteria.setCreatedAt(now);
        criteria.setUpdatedAt(now);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, criteria.getPovId());
            ps.setString(2, criteria.getName());
            ps.setString(3, criteria.getDescription());
            ps.setString(4, criteria.getStatus());
            ps.setInt(5, criteria.getWeight());
            ps.setString(6, criteria.getNotes());
            ps.setTimestamp(7, Timestamp.valueOf(criteria.getCreatedAt()));
            ps.setTimestamp(8, Timestamp.valueOf(criteria.getUpdatedAt()));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    criteria.setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            LOG.error("Error creating criterion", e);
            throw new RuntimeException("Error creating criterion", e);
        }
        return criteria;
    }

    /**
     * Updates an existing criterion. Sets updatedAt to the current time.
     *
     * @param criteria the criterion to update (must have a valid id)
     * @return the updated criterion with updatedAt refreshed
     */
    public PovCriteria update(PovCriteria criteria) {
        String sql = "UPDATE pov_criteria SET name = ?, description = ?, status = ?, weight = ?, "
                + "notes = ?, updated_at = ? WHERE id = ?";
        LocalDateTime now = LocalDateTime.now();
        criteria.setUpdatedAt(now);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, criteria.getName());
            ps.setString(2, criteria.getDescription());
            ps.setString(3, criteria.getStatus());
            ps.setInt(4, criteria.getWeight());
            ps.setString(5, criteria.getNotes());
            ps.setTimestamp(6, Timestamp.valueOf(criteria.getUpdatedAt()));
            ps.setLong(7, criteria.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error updating criterion id: {}", criteria.getId(), e);
            throw new RuntimeException("Error updating criterion id: " + criteria.getId(), e);
        }
        return criteria;
    }

    /**
     * Deletes a criterion by its ID.
     *
     * @param id the criterion ID to delete
     */
    public void delete(long id) {
        String sql = "DELETE FROM pov_criteria WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Error deleting criterion id: {}", id, e);
            throw new RuntimeException("Error deleting criterion id: " + id, e);
        }
    }

    /**
     * Maps a ResultSet row to a PovCriteria object.
     */
    private PovCriteria mapRow(ResultSet rs) throws SQLException {
        PovCriteria criteria = new PovCriteria();
        criteria.setId(rs.getLong("id"));
        criteria.setPovId(rs.getLong("pov_id"));
        criteria.setName(rs.getString("name"));
        criteria.setDescription(rs.getString("description"));
        criteria.setStatus(rs.getString("status"));
        criteria.setWeight(rs.getInt("weight"));
        criteria.setNotes(rs.getString("notes"));

        Timestamp createdTs = rs.getTimestamp("created_at");
        if (createdTs != null) {
            criteria.setCreatedAt(createdTs.toLocalDateTime());
        }

        Timestamp updatedTs = rs.getTimestamp("updated_at");
        if (updatedTs != null) {
            criteria.setUpdatedAt(updatedTs.toLocalDateTime());
        }

        return criteria;
    }
}
