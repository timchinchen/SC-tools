package com.dash0.sctools.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Initializes the H2 database and creates all required tables on startup.
 * The database is file-based at ./data/sctools.
 */
public class DatabaseInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseInitializer.class);

    /** JDBC URL for the file-based H2 database */
    public static final String JDBC_URL = "jdbc:h2:./data/sctools;AUTO_SERVER=TRUE";
    public static final String JDBC_USER = "sa";
    public static final String JDBC_PASSWORD = "";

    /** JDBC URL for in-memory test database */
    public static final String TEST_JDBC_URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";

    /**
     * Initializes the production database.
     */
    public static void initialize() {
        // Ensure the data directory exists
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
            LOG.info("Created data directory: {}", dataDir.getAbsolutePath());
        }

        try (Connection conn = getConnection()) {
            createTables(conn);
            LOG.info("Database initialized successfully");
        } catch (SQLException e) {
            LOG.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Initializes the test database (in-memory H2).
     */
    public static void initializeTestDatabase() {
        try (Connection conn = getTestConnection()) {
            createTables(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Test database initialization failed", e);
        }
    }

    /**
     * Creates all required tables if they don't already exist.
     */
    public static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Time Entries table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS time_entries (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    sc_name VARCHAR(255) NOT NULL,
                    date DATE NOT NULL,
                    hours DECIMAL(5,2) NOT NULL,
                    account_name VARCHAR(255) NOT NULL,
                    activity_type VARCHAR(50) NOT NULL,
                    description VARCHAR(2000),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            LOG.debug("Table 'time_entries' ready");

            // POVs table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS povs (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    account_name VARCHAR(255) NOT NULL,
                    sc_name VARCHAR(255) NOT NULL,
                    status VARCHAR(50) NOT NULL DEFAULT 'PLANNED',
                    start_date DATE,
                    target_end_date DATE,
                    description VARCHAR(2000),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            LOG.debug("Table 'povs' ready");

            // POV Criteria table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pov_criteria (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    pov_id BIGINT NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    description VARCHAR(2000),
                    status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
                    weight INT NOT NULL DEFAULT 1,
                    notes VARCHAR(2000),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_pov_criteria_pov FOREIGN KEY (pov_id) REFERENCES povs(id) ON DELETE CASCADE
                )
                """);
            LOG.debug("Table 'pov_criteria' ready");
        }
    }

    /**
     * Gets a connection to the production database.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
    }

    /**
     * Gets a connection to the test database.
     */
    public static Connection getTestConnection() throws SQLException {
        return DriverManager.getConnection(TEST_JDBC_URL, JDBC_USER, JDBC_PASSWORD);
    }
}
