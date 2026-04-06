package com.dash0.sctools.dao;

import com.dash0.sctools.model.Pov;
import com.dash0.sctools.util.DatabaseInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for PovDao using in-memory H2 database.
 */
class PovDaoTest {

    private PovDao dao;

    @BeforeEach
    void setUp() throws Exception {
        dao = new PovDao(DatabaseInitializer.TEST_JDBC_URL, DatabaseInitializer.JDBC_USER, DatabaseInitializer.JDBC_PASSWORD);

        // Re-create tables fresh for each test (drop + create)
        try (Connection conn = DatabaseInitializer.getTestConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS pov_criteria");
            stmt.execute("DROP TABLE IF EXISTS povs");
            stmt.execute("DROP TABLE IF EXISTS time_entries");
        }
        DatabaseInitializer.initializeTestDatabase();
    }

    // -------------------------------------------------------------------------
    // findAll() tests
    // -------------------------------------------------------------------------

    @Test
    void findAll_emptyDatabase_returnsEmptyList() {
        List<Pov> povs = dao.findAll();
        assertNotNull(povs);
        assertTrue(povs.isEmpty());
    }

    @Test
    void findAll_multipleEntries_returnsAll() {
        dao.create(createSamplePov("POV Alpha", "Acme Corp", "Alice", "PLANNED"));
        dao.create(createSamplePov("POV Beta", "BigCorp", "Bob", "IN_PROGRESS"));
        dao.create(createSamplePov("POV Gamma", "MegaCorp", "Charlie", "COMPLETED"));

        List<Pov> povs = dao.findAll();
        assertEquals(3, povs.size());
    }

    @Test
    void findAll_orderedByCreatedAtDesc() throws InterruptedException {
        Pov first = dao.create(createSamplePov("First POV", "Acme", "Alice", "PLANNED"));
        Thread.sleep(10); // Ensure different timestamps
        Pov second = dao.create(createSamplePov("Second POV", "BigCorp", "Bob", "IN_PROGRESS"));
        Thread.sleep(10);
        Pov third = dao.create(createSamplePov("Third POV", "MegaCorp", "Charlie", "COMPLETED"));

        List<Pov> povs = dao.findAll();
        assertEquals(3, povs.size());
        // Most recently created first
        assertEquals("Third POV", povs.get(0).getName());
        assertEquals("Second POV", povs.get(1).getName());
        assertEquals("First POV", povs.get(2).getName());
    }

    @Test
    void findAll_singleEntry_returnsList() {
        dao.create(createSamplePov("Only POV", "Acme", "Alice", "PLANNED"));

        List<Pov> povs = dao.findAll();
        assertEquals(1, povs.size());
        assertEquals("Only POV", povs.get(0).getName());
    }

    // -------------------------------------------------------------------------
    // findById() tests
    // -------------------------------------------------------------------------

    @Test
    void findById_existingEntry_returnsEntry() {
        Pov created = dao.create(
                createSamplePov("Test POV", "Acme Corp", "Alice", "IN_PROGRESS"));

        Pov found = dao.findById(created.getId());
        assertNotNull(found);
        assertEquals(created.getId(), found.getId());
        assertEquals("Test POV", found.getName());
        assertEquals("Acme Corp", found.getAccountName());
        assertEquals("Alice", found.getScName());
        assertEquals("IN_PROGRESS", found.getStatus());
    }

    @Test
    void findById_nonExistentId_returnsNull() {
        Pov found = dao.findById(99999L);
        assertNull(found);
    }

    @Test
    void findById_returnsAllFields() {
        Pov pov = createSamplePov("Full POV", "Acme", "Alice", "PLANNED");
        pov.setStartDate(LocalDate.of(2026, 1, 15));
        pov.setTargetEndDate(LocalDate.of(2026, 6, 30));
        pov.setDescription("A detailed description of this POV project");
        Pov created = dao.create(pov);

        Pov found = dao.findById(created.getId());
        assertNotNull(found);
        assertEquals("Full POV", found.getName());
        assertEquals("Acme", found.getAccountName());
        assertEquals("Alice", found.getScName());
        assertEquals("PLANNED", found.getStatus());
        assertEquals(LocalDate.of(2026, 1, 15), found.getStartDate());
        assertEquals(LocalDate.of(2026, 6, 30), found.getTargetEndDate());
        assertEquals("A detailed description of this POV project", found.getDescription());
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
    }

    @Test
    void findById_nullDates_returnsNull() {
        Pov pov = createSamplePov("No Dates POV", "Acme", "Alice", "PLANNED");
        // startDate and targetEndDate default to null
        Pov created = dao.create(pov);

        Pov found = dao.findById(created.getId());
        assertNotNull(found);
        assertNull(found.getStartDate());
        assertNull(found.getTargetEndDate());
    }

    @Test
    void findById_nullDescription_returnsNull() {
        Pov pov = createSamplePov("No Desc POV", "Acme", "Alice", "PLANNED");
        pov.setDescription(null);
        Pov created = dao.create(pov);

        Pov found = dao.findById(created.getId());
        assertNotNull(found);
        assertNull(found.getDescription());
    }

    // -------------------------------------------------------------------------
    // create() tests
    // -------------------------------------------------------------------------

    @Test
    void create_setsIdOnEntry() {
        Pov pov = createSamplePov("New POV", "Acme", "Alice", "PLANNED");
        Pov created = dao.create(pov);

        assertTrue(created.getId() > 0, "ID should be auto-generated and positive");
    }

    @Test
    void create_setsCreatedAtAndUpdatedAt() {
        Pov pov = createSamplePov("New POV", "Acme", "Alice", "PLANNED");
        Pov created = dao.create(pov);

        assertNotNull(created.getCreatedAt(), "createdAt should be set");
        assertNotNull(created.getUpdatedAt(), "updatedAt should be set");
        assertEquals(created.getCreatedAt(), created.getUpdatedAt(),
                "createdAt and updatedAt should be the same on creation");
    }

    @Test
    void create_persistsAllFields() {
        Pov pov = createSamplePov("Full POV", "Acme Corp", "Alice Smith", "IN_PROGRESS");
        pov.setStartDate(LocalDate.of(2026, 3, 1));
        pov.setTargetEndDate(LocalDate.of(2026, 9, 30));
        pov.setDescription("Comprehensive POV for enterprise deployment");
        Pov created = dao.create(pov);

        // Re-read from database to confirm persistence
        Pov found = dao.findById(created.getId());
        assertNotNull(found);
        assertEquals("Full POV", found.getName());
        assertEquals("Acme Corp", found.getAccountName());
        assertEquals("Alice Smith", found.getScName());
        assertEquals("IN_PROGRESS", found.getStatus());
        assertEquals(LocalDate.of(2026, 3, 1), found.getStartDate());
        assertEquals(LocalDate.of(2026, 9, 30), found.getTargetEndDate());
        assertEquals("Comprehensive POV for enterprise deployment", found.getDescription());
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
    }

    @Test
    void create_allStatuses_persistCorrectly() {
        String[] statuses = {"PLANNED", "IN_PROGRESS", "COMPLETED", "WON", "LOST", "CANCELLED"};
        for (String status : statuses) {
            Pov pov = createSamplePov("POV " + status, "Acme", "SC", status);
            Pov created = dao.create(pov);
            Pov found = dao.findById(created.getId());
            assertNotNull(found);
            assertEquals(status, found.getStatus(), "Status " + status + " should persist correctly");
        }
    }

    // -------------------------------------------------------------------------
    // update() tests
    // -------------------------------------------------------------------------

    @Test
    void update_changesFields() {
        Pov created = dao.create(
                createSamplePov("Original POV", "Acme", "Alice", "PLANNED"));

        // Modify fields
        created.setName("Updated POV");
        created.setAccountName("BigCorp");
        created.setScName("Bob");
        created.setStatus("IN_PROGRESS");
        created.setStartDate(LocalDate.of(2026, 5, 1));
        created.setTargetEndDate(LocalDate.of(2026, 12, 31));
        created.setDescription("Updated description");

        dao.update(created);

        // Verify changes persisted
        Pov found = dao.findById(created.getId());
        assertNotNull(found);
        assertEquals("Updated POV", found.getName());
        assertEquals("BigCorp", found.getAccountName());
        assertEquals("Bob", found.getScName());
        assertEquals("IN_PROGRESS", found.getStatus());
        assertEquals(LocalDate.of(2026, 5, 1), found.getStartDate());
        assertEquals(LocalDate.of(2026, 12, 31), found.getTargetEndDate());
        assertEquals("Updated description", found.getDescription());
    }

    @Test
    void update_setsUpdatedAtToNewTimestamp() throws InterruptedException {
        Pov created = dao.create(
                createSamplePov("Test POV", "Acme", "Alice", "PLANNED"));

        var originalCreatedAt = created.getCreatedAt();
        var originalUpdatedAt = created.getUpdatedAt();

        // Small delay to ensure timestamp difference
        Thread.sleep(50);

        created.setStatus("IN_PROGRESS");
        dao.update(created);

        Pov found = dao.findById(created.getId());
        assertNotNull(found);
        assertEquals(originalCreatedAt, found.getCreatedAt(), "createdAt should not change on update");
        assertTrue(found.getUpdatedAt().isAfter(originalUpdatedAt) || found.getUpdatedAt().isEqual(originalUpdatedAt),
                "updatedAt should be equal to or after original updatedAt");
    }

    @Test
    void update_doesNotCreateDuplicateEntry() {
        dao.create(createSamplePov("POV One", "Acme", "Alice", "PLANNED"));
        Pov second = dao.create(createSamplePov("POV Two", "BigCorp", "Bob", "IN_PROGRESS"));

        second.setName("POV Two Updated");
        dao.update(second);

        List<Pov> all = dao.findAll();
        assertEquals(2, all.size(), "Update should not create a new entry");
    }

    @Test
    void update_statusChange_persistsCorrectly() {
        Pov created = dao.create(
                createSamplePov("Lifecycle POV", "Acme", "Alice", "PLANNED"));

        // Walk through a status lifecycle
        String[] statusFlow = {"IN_PROGRESS", "COMPLETED", "WON"};
        for (String status : statusFlow) {
            created.setStatus(status);
            dao.update(created);

            Pov found = dao.findById(created.getId());
            assertNotNull(found);
            assertEquals(status, found.getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // delete() tests
    // -------------------------------------------------------------------------

    @Test
    void delete_removesEntry() {
        Pov created = dao.create(
                createSamplePov("To Delete", "Acme", "Alice", "PLANNED"));

        dao.delete(created.getId());

        Pov found = dao.findById(created.getId());
        assertNull(found, "POV should be null after deletion");
    }

    @Test
    void delete_onlyRemovesTargetEntry() {
        Pov pov1 = dao.create(createSamplePov("POV One", "Acme", "Alice", "PLANNED"));
        Pov pov2 = dao.create(createSamplePov("POV Two", "BigCorp", "Bob", "IN_PROGRESS"));

        dao.delete(pov1.getId());

        assertNull(dao.findById(pov1.getId()), "Deleted POV should be null");
        assertNotNull(dao.findById(pov2.getId()), "Other POV should still exist");
        assertEquals(1, dao.findAll().size());
    }

    @Test
    void delete_nonExistentId_doesNotThrow() {
        assertDoesNotThrow(() -> dao.delete(99999L));
    }

    @Test
    void delete_cascadesDeleteToCriteria() {
        // Create a POV and add criteria
        Pov pov = dao.create(createSamplePov("Cascade POV", "Acme", "Alice", "IN_PROGRESS"));

        PovCriteriaDao criteriaDao = new PovCriteriaDao(
                DatabaseInitializer.TEST_JDBC_URL, DatabaseInitializer.JDBC_USER, DatabaseInitializer.JDBC_PASSWORD);

        com.dash0.sctools.model.PovCriteria c1 = new com.dash0.sctools.model.PovCriteria();
        c1.setPovId(pov.getId());
        c1.setName("Criterion 1");
        c1.setStatus("NOT_STARTED");
        c1.setWeight(3);
        criteriaDao.create(c1);

        com.dash0.sctools.model.PovCriteria c2 = new com.dash0.sctools.model.PovCriteria();
        c2.setPovId(pov.getId());
        c2.setName("Criterion 2");
        c2.setStatus("IN_PROGRESS");
        c2.setWeight(5);
        criteriaDao.create(c2);

        // Verify criteria exist
        assertEquals(2, criteriaDao.findByPovId(pov.getId()).size());

        // Delete the POV
        dao.delete(pov.getId());

        // Verify POV is gone
        assertNull(dao.findById(pov.getId()));

        // Verify criteria are cascade-deleted
        List<com.dash0.sctools.model.PovCriteria> remainingCriteria = criteriaDao.findByPovId(pov.getId());
        assertTrue(remainingCriteria.isEmpty(), "Criteria should be cascade-deleted when POV is deleted");
    }

    @Test
    void delete_cascadeDoesNotAffectOtherPovCriteria() {
        // Create two POVs with criteria each
        Pov pov1 = dao.create(createSamplePov("POV One", "Acme", "Alice", "IN_PROGRESS"));
        Pov pov2 = dao.create(createSamplePov("POV Two", "BigCorp", "Bob", "PLANNED"));

        PovCriteriaDao criteriaDao = new PovCriteriaDao(
                DatabaseInitializer.TEST_JDBC_URL, DatabaseInitializer.JDBC_USER, DatabaseInitializer.JDBC_PASSWORD);

        com.dash0.sctools.model.PovCriteria c1 = new com.dash0.sctools.model.PovCriteria();
        c1.setPovId(pov1.getId());
        c1.setName("POV1 Criterion");
        c1.setStatus("NOT_STARTED");
        c1.setWeight(2);
        criteriaDao.create(c1);

        com.dash0.sctools.model.PovCriteria c2 = new com.dash0.sctools.model.PovCriteria();
        c2.setPovId(pov2.getId());
        c2.setName("POV2 Criterion");
        c2.setStatus("MET");
        c2.setWeight(4);
        criteriaDao.create(c2);

        // Delete POV1
        dao.delete(pov1.getId());

        // POV2's criteria should still exist
        List<com.dash0.sctools.model.PovCriteria> pov2Criteria = criteriaDao.findByPovId(pov2.getId());
        assertEquals(1, pov2Criteria.size());
        assertEquals("POV2 Criterion", pov2Criteria.get(0).getName());
    }

    // -------------------------------------------------------------------------
    // getCount() tests
    // -------------------------------------------------------------------------

    @Test
    void getCount_emptyDatabase_returnsZero() {
        assertEquals(0, dao.getCount(),
                "Count should be 0 when no POVs exist");
    }

    @Test
    void getCount_multipleEntries_returnsCorrectCount() {
        dao.create(createSamplePov("POV 1", "Acme", "Alice", "PLANNED"));
        dao.create(createSamplePov("POV 2", "BigCorp", "Bob", "IN_PROGRESS"));
        dao.create(createSamplePov("POV 3", "MegaCorp", "Charlie", "COMPLETED"));

        assertEquals(3, dao.getCount(),
                "Count should be 3 when three POVs exist");
    }

    // -------------------------------------------------------------------------
    // countByStatus() tests
    // -------------------------------------------------------------------------

    @Test
    void countByStatus_emptyDatabase_returnsAllZeros() {
        java.util.Map<String, Long> counts = dao.countByStatus();
        assertNotNull(counts);
        assertEquals(0L, counts.get("PLANNED"));
        assertEquals(0L, counts.get("IN_PROGRESS"));
        assertEquals(0L, counts.get("COMPLETED"));
        assertEquals(0L, counts.get("WON"));
        assertEquals(0L, counts.get("LOST"));
        assertEquals(0L, counts.get("CANCELLED"));
    }

    @Test
    void countByStatus_withMixedStatuses_returnsCorrectCounts() {
        dao.create(createSamplePov("P1", "Co1", "SC1", "PLANNED"));
        dao.create(createSamplePov("P2", "Co2", "SC2", "PLANNED"));
        dao.create(createSamplePov("IP1", "Co3", "SC3", "IN_PROGRESS"));
        dao.create(createSamplePov("W1", "Co4", "SC4", "WON"));
        dao.create(createSamplePov("W2", "Co5", "SC5", "WON"));
        dao.create(createSamplePov("W3", "Co6", "SC6", "WON"));
        dao.create(createSamplePov("L1", "Co7", "SC7", "LOST"));

        java.util.Map<String, Long> counts = dao.countByStatus();
        assertEquals(2L, counts.get("PLANNED"));
        assertEquals(1L, counts.get("IN_PROGRESS"));
        assertEquals(0L, counts.get("COMPLETED"));
        assertEquals(3L, counts.get("WON"));
        assertEquals(1L, counts.get("LOST"));
        assertEquals(0L, counts.get("CANCELLED"));
    }

    @Test
    void countByStatus_allSameStatus_returnsSingleNonZero() {
        dao.create(createSamplePov("C1", "Co1", "SC1", "COMPLETED"));
        dao.create(createSamplePov("C2", "Co2", "SC2", "COMPLETED"));

        java.util.Map<String, Long> counts = dao.countByStatus();
        assertEquals(0L, counts.get("PLANNED"));
        assertEquals(0L, counts.get("IN_PROGRESS"));
        assertEquals(2L, counts.get("COMPLETED"));
        assertEquals(0L, counts.get("WON"));
        assertEquals(0L, counts.get("LOST"));
        assertEquals(0L, counts.get("CANCELLED"));
    }

    // -------------------------------------------------------------------------
    // SQL injection safety tests
    // -------------------------------------------------------------------------

    @Test
    void create_withSqlInjectionPayload_doesNotCorruptData() {
        Pov pov = createSamplePov(
                "'; DROP TABLE povs; --",
                "'; DELETE FROM povs; --",
                "Robert'; DROP TABLE povs;--",
                "PLANNED");
        pov.setDescription("'; DROP TABLE pov_criteria; --");

        Pov created = dao.create(pov);
        assertNotNull(created);
        assertTrue(created.getId() > 0);

        // Verify the table still exists and entry is retrievable
        Pov found = dao.findById(created.getId());
        assertNotNull(found);
        assertEquals("'; DROP TABLE povs; --", found.getName());
        assertEquals("'; DELETE FROM povs; --", found.getAccountName());

        // Verify findAll still works (table not dropped)
        List<Pov> all = dao.findAll();
        assertFalse(all.isEmpty(), "Table should still exist with data after SQL injection attempt");
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private Pov createSamplePov(String name, String accountName, String scName, String status) {
        Pov pov = new Pov();
        pov.setName(name);
        pov.setAccountName(accountName);
        pov.setScName(scName);
        pov.setStatus(status);
        return pov;
    }
}
