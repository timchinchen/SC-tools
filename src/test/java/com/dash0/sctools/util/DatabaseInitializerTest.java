package com.dash0.sctools.util;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DatabaseInitializer - verifies that all tables are created correctly.
 */
class DatabaseInitializerTest {

    @Test
    void testTablesAreCreated() throws SQLException {
        try (Connection conn = DatabaseInitializer.getTestConnection()) {
            DatabaseInitializer.createTables(conn);

            List<String> tableNames = getTableNames(conn);

            assertTrue(tableNames.contains("TIME_ENTRIES"), "time_entries table should exist");
            assertTrue(tableNames.contains("POVS"), "povs table should exist");
            assertTrue(tableNames.contains("POV_CRITERIA"), "pov_criteria table should exist");
        }
    }

    @Test
    void testCreateTablesIsIdempotent() throws SQLException {
        try (Connection conn = DatabaseInitializer.getTestConnection()) {
            // Call createTables twice - should not throw
            DatabaseInitializer.createTables(conn);
            DatabaseInitializer.createTables(conn);

            List<String> tableNames = getTableNames(conn);
            assertTrue(tableNames.contains("TIME_ENTRIES"));
            assertTrue(tableNames.contains("POVS"));
            assertTrue(tableNames.contains("POV_CRITERIA"));
        }
    }

    @Test
    void testTimeEntriesTableColumns() throws SQLException {
        try (Connection conn = DatabaseInitializer.getTestConnection()) {
            DatabaseInitializer.createTables(conn);

            List<String> columns = getColumnNames(conn, "TIME_ENTRIES");

            assertTrue(columns.contains("ID"), "Should have id column");
            assertTrue(columns.contains("SC_NAME"), "Should have sc_name column");
            assertTrue(columns.contains("DATE"), "Should have date column");
            assertTrue(columns.contains("HOURS"), "Should have hours column");
            assertTrue(columns.contains("ACCOUNT_NAME"), "Should have account_name column");
            assertTrue(columns.contains("ACTIVITY_TYPE"), "Should have activity_type column");
            assertTrue(columns.contains("DESCRIPTION"), "Should have description column");
            assertTrue(columns.contains("CREATED_AT"), "Should have created_at column");
            assertTrue(columns.contains("UPDATED_AT"), "Should have updated_at column");
        }
    }

    @Test
    void testPovsTableColumns() throws SQLException {
        try (Connection conn = DatabaseInitializer.getTestConnection()) {
            DatabaseInitializer.createTables(conn);

            List<String> columns = getColumnNames(conn, "POVS");

            assertTrue(columns.contains("ID"), "Should have id column");
            assertTrue(columns.contains("NAME"), "Should have name column");
            assertTrue(columns.contains("ACCOUNT_NAME"), "Should have account_name column");
            assertTrue(columns.contains("SC_NAME"), "Should have sc_name column");
            assertTrue(columns.contains("STATUS"), "Should have status column");
            assertTrue(columns.contains("START_DATE"), "Should have start_date column");
            assertTrue(columns.contains("TARGET_END_DATE"), "Should have target_end_date column");
            assertTrue(columns.contains("DESCRIPTION"), "Should have description column");
            assertTrue(columns.contains("CREATED_AT"), "Should have created_at column");
            assertTrue(columns.contains("UPDATED_AT"), "Should have updated_at column");
        }
    }

    @Test
    void testPovCriteriaTableColumns() throws SQLException {
        try (Connection conn = DatabaseInitializer.getTestConnection()) {
            DatabaseInitializer.createTables(conn);

            List<String> columns = getColumnNames(conn, "POV_CRITERIA");

            assertTrue(columns.contains("ID"), "Should have id column");
            assertTrue(columns.contains("POV_ID"), "Should have pov_id column");
            assertTrue(columns.contains("NAME"), "Should have name column");
            assertTrue(columns.contains("DESCRIPTION"), "Should have description column");
            assertTrue(columns.contains("STATUS"), "Should have status column");
            assertTrue(columns.contains("WEIGHT"), "Should have weight column");
            assertTrue(columns.contains("NOTES"), "Should have notes column");
            assertTrue(columns.contains("CREATED_AT"), "Should have created_at column");
            assertTrue(columns.contains("UPDATED_AT"), "Should have updated_at column");
        }
    }

    private List<String> getTableNames(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private List<String> getColumnNames(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns(null, null, tableName, "%")) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }
}
