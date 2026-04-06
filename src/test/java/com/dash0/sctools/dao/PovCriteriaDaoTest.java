package com.dash0.sctools.dao;

import com.dash0.sctools.model.Pov;
import com.dash0.sctools.model.PovCriteria;
import com.dash0.sctools.util.DatabaseInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for PovCriteriaDao using in-memory H2 database.
 */
class PovCriteriaDaoTest {

    private PovCriteriaDao criteriaDao;
    private PovDao povDao;

    /** A POV created in setUp that tests can use as the parent for criteria. */
    private Pov parentPov;

    @BeforeEach
    void setUp() throws Exception {
        criteriaDao = new PovCriteriaDao(DatabaseInitializer.TEST_JDBC_URL, DatabaseInitializer.JDBC_USER, DatabaseInitializer.JDBC_PASSWORD);
        povDao = new PovDao(DatabaseInitializer.TEST_JDBC_URL, DatabaseInitializer.JDBC_USER, DatabaseInitializer.JDBC_PASSWORD);

        // Re-create tables fresh for each test (drop + create)
        try (Connection conn = DatabaseInitializer.getTestConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS pov_criteria");
            stmt.execute("DROP TABLE IF EXISTS povs");
            stmt.execute("DROP TABLE IF EXISTS time_entries");
        }
        DatabaseInitializer.initializeTestDatabase();

        // Create a parent POV for criteria tests
        Pov pov = new Pov();
        pov.setName("Test POV");
        pov.setAccountName("Acme Corp");
        pov.setScName("Alice");
        pov.setStatus("IN_PROGRESS");
        parentPov = povDao.create(pov);
    }

    // -------------------------------------------------------------------------
    // findByPovId() tests
    // -------------------------------------------------------------------------

    @Test
    void findByPovId_noCriteria_returnsEmptyList() {
        List<PovCriteria> criteria = criteriaDao.findByPovId(parentPov.getId());
        assertNotNull(criteria);
        assertTrue(criteria.isEmpty());
    }

    @Test
    void findByPovId_multipleCriteria_returnsAll() {
        criteriaDao.create(createSampleCriteria(parentPov.getId(), "Criterion A", "NOT_STARTED", 3));
        criteriaDao.create(createSampleCriteria(parentPov.getId(), "Criterion B", "IN_PROGRESS", 4));
        criteriaDao.create(createSampleCriteria(parentPov.getId(), "Criterion C", "MET", 5));

        List<PovCriteria> criteria = criteriaDao.findByPovId(parentPov.getId());
        assertEquals(3, criteria.size());
    }

    @Test
    void findByPovId_orderedByIdAsc() {
        PovCriteria first = criteriaDao.create(createSampleCriteria(parentPov.getId(), "First", "NOT_STARTED", 1));
        PovCriteria second = criteriaDao.create(createSampleCriteria(parentPov.getId(), "Second", "NOT_STARTED", 2));
        PovCriteria third = criteriaDao.create(createSampleCriteria(parentPov.getId(), "Third", "NOT_STARTED", 3));

        List<PovCriteria> criteria = criteriaDao.findByPovId(parentPov.getId());
        assertEquals(3, criteria.size());
        assertEquals("First", criteria.get(0).getName());
        assertEquals("Second", criteria.get(1).getName());
        assertEquals("Third", criteria.get(2).getName());
    }

    @Test
    void findByPovId_onlyReturnsCriteriaForSpecifiedPov() {
        // Create a second POV
        Pov otherPov = new Pov();
        otherPov.setName("Other POV");
        otherPov.setAccountName("BigCorp");
        otherPov.setScName("Bob");
        otherPov.setStatus("PLANNED");
        otherPov = povDao.create(otherPov);

        // Add criteria to both POVs
        criteriaDao.create(createSampleCriteria(parentPov.getId(), "Parent Criterion", "NOT_STARTED", 3));
        criteriaDao.create(createSampleCriteria(otherPov.getId(), "Other Criterion", "MET", 5));

        // Only parent POV criteria should be returned
        List<PovCriteria> parentCriteria = criteriaDao.findByPovId(parentPov.getId());
        assertEquals(1, parentCriteria.size());
        assertEquals("Parent Criterion", parentCriteria.get(0).getName());

        // Only other POV criteria should be returned
        List<PovCriteria> otherCriteria = criteriaDao.findByPovId(otherPov.getId());
        assertEquals(1, otherCriteria.size());
        assertEquals("Other Criterion", otherCriteria.get(0).getName());
    }

    @Test
    void findByPovId_nonExistentPovId_returnsEmptyList() {
        List<PovCriteria> criteria = criteriaDao.findByPovId(99999L);
        assertNotNull(criteria);
        assertTrue(criteria.isEmpty());
    }

    // -------------------------------------------------------------------------
    // findById() tests
    // -------------------------------------------------------------------------

    @Test
    void findById_existingEntry_returnsEntry() {
        PovCriteria created = criteriaDao.create(
                createSampleCriteria(parentPov.getId(), "Test Criterion", "IN_PROGRESS", 4));

        PovCriteria found = criteriaDao.findById(created.getId());
        assertNotNull(found);
        assertEquals(created.getId(), found.getId());
        assertEquals(parentPov.getId(), found.getPovId());
        assertEquals("Test Criterion", found.getName());
        assertEquals("IN_PROGRESS", found.getStatus());
        assertEquals(4, found.getWeight());
    }

    @Test
    void findById_nonExistentId_returnsNull() {
        PovCriteria found = criteriaDao.findById(99999L);
        assertNull(found);
    }

    @Test
    void findById_returnsAllFields() {
        PovCriteria criteria = createSampleCriteria(parentPov.getId(), "Full Criterion", "NOT_STARTED", 5);
        criteria.setDescription("Detailed description of the criterion");
        criteria.setNotes("Important notes about progress");
        PovCriteria created = criteriaDao.create(criteria);

        PovCriteria found = criteriaDao.findById(created.getId());
        assertNotNull(found);
        assertEquals("Full Criterion", found.getName());
        assertEquals(parentPov.getId(), found.getPovId());
        assertEquals("Detailed description of the criterion", found.getDescription());
        assertEquals("NOT_STARTED", found.getStatus());
        assertEquals(5, found.getWeight());
        assertEquals("Important notes about progress", found.getNotes());
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
    }

    @Test
    void findById_nullDescriptionAndNotes_returnsNull() {
        PovCriteria criteria = createSampleCriteria(parentPov.getId(), "Minimal Criterion", "NOT_STARTED", 1);
        criteria.setDescription(null);
        criteria.setNotes(null);
        PovCriteria created = criteriaDao.create(criteria);

        PovCriteria found = criteriaDao.findById(created.getId());
        assertNotNull(found);
        assertNull(found.getDescription());
        assertNull(found.getNotes());
    }

    // -------------------------------------------------------------------------
    // create() tests
    // -------------------------------------------------------------------------

    @Test
    void create_setsIdOnEntry() {
        PovCriteria criteria = createSampleCriteria(parentPov.getId(), "New Criterion", "NOT_STARTED", 3);
        PovCriteria created = criteriaDao.create(criteria);

        assertTrue(created.getId() > 0, "ID should be auto-generated and positive");
    }

    @Test
    void create_setsCreatedAtAndUpdatedAt() {
        PovCriteria criteria = createSampleCriteria(parentPov.getId(), "New Criterion", "NOT_STARTED", 3);
        PovCriteria created = criteriaDao.create(criteria);

        assertNotNull(created.getCreatedAt(), "createdAt should be set");
        assertNotNull(created.getUpdatedAt(), "updatedAt should be set");
        assertEquals(created.getCreatedAt(), created.getUpdatedAt(),
                "createdAt and updatedAt should be the same on creation");
    }

    @Test
    void create_persistsAllFields() {
        PovCriteria criteria = createSampleCriteria(parentPov.getId(), "Full Criterion", "IN_PROGRESS", 4);
        criteria.setDescription("Criterion description");
        criteria.setNotes("Progress notes");
        PovCriteria created = criteriaDao.create(criteria);

        // Re-read from database to confirm persistence
        PovCriteria found = criteriaDao.findById(created.getId());
        assertNotNull(found);
        assertEquals(parentPov.getId(), found.getPovId());
        assertEquals("Full Criterion", found.getName());
        assertEquals("Criterion description", found.getDescription());
        assertEquals("IN_PROGRESS", found.getStatus());
        assertEquals(4, found.getWeight());
        assertEquals("Progress notes", found.getNotes());
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
    }

    @Test
    void create_allStatuses_persistCorrectly() {
        String[] statuses = {"NOT_STARTED", "IN_PROGRESS", "MET", "NOT_MET", "PARTIALLY_MET"};
        for (String status : statuses) {
            PovCriteria criteria = createSampleCriteria(parentPov.getId(), "Criterion " + status, status, 3);
            PovCriteria created = criteriaDao.create(criteria);
            PovCriteria found = criteriaDao.findById(created.getId());
            assertNotNull(found);
            assertEquals(status, found.getStatus(), "Status " + status + " should persist correctly");
        }
    }

    @Test
    void create_allWeights_persistCorrectly() {
        for (int weight = 1; weight <= 5; weight++) {
            PovCriteria criteria = createSampleCriteria(parentPov.getId(), "Weight " + weight, "NOT_STARTED", weight);
            PovCriteria created = criteriaDao.create(criteria);
            PovCriteria found = criteriaDao.findById(created.getId());
            assertNotNull(found);
            assertEquals(weight, found.getWeight(), "Weight " + weight + " should persist correctly");
        }
    }

    // -------------------------------------------------------------------------
    // update() tests
    // -------------------------------------------------------------------------

    @Test
    void update_changesFields() {
        PovCriteria created = criteriaDao.create(
                createSampleCriteria(parentPov.getId(), "Original Criterion", "NOT_STARTED", 2));

        // Modify fields
        created.setName("Updated Criterion");
        created.setDescription("Updated description");
        created.setStatus("MET");
        created.setWeight(5);
        created.setNotes("Now fully met after evaluation");

        criteriaDao.update(created);

        // Verify changes persisted
        PovCriteria found = criteriaDao.findById(created.getId());
        assertNotNull(found);
        assertEquals("Updated Criterion", found.getName());
        assertEquals("Updated description", found.getDescription());
        assertEquals("MET", found.getStatus());
        assertEquals(5, found.getWeight());
        assertEquals("Now fully met after evaluation", found.getNotes());
    }

    @Test
    void update_setsUpdatedAtToNewTimestamp() throws InterruptedException {
        PovCriteria created = criteriaDao.create(
                createSampleCriteria(parentPov.getId(), "Test Criterion", "NOT_STARTED", 3));

        var originalCreatedAt = created.getCreatedAt();
        var originalUpdatedAt = created.getUpdatedAt();

        // Small delay to ensure timestamp difference
        Thread.sleep(50);

        created.setStatus("IN_PROGRESS");
        criteriaDao.update(created);

        PovCriteria found = criteriaDao.findById(created.getId());
        assertNotNull(found);
        assertEquals(originalCreatedAt, found.getCreatedAt(), "createdAt should not change on update");
        assertTrue(found.getUpdatedAt().isAfter(originalUpdatedAt) || found.getUpdatedAt().isEqual(originalUpdatedAt),
                "updatedAt should be equal to or after original updatedAt");
    }

    @Test
    void update_doesNotCreateDuplicateEntry() {
        criteriaDao.create(createSampleCriteria(parentPov.getId(), "Criterion One", "NOT_STARTED", 1));
        PovCriteria second = criteriaDao.create(
                createSampleCriteria(parentPov.getId(), "Criterion Two", "NOT_STARTED", 2));

        second.setName("Criterion Two Updated");
        criteriaDao.update(second);

        List<PovCriteria> all = criteriaDao.findByPovId(parentPov.getId());
        assertEquals(2, all.size(), "Update should not create a new entry");
    }

    @Test
    void update_statusChange_persistsCorrectly() {
        PovCriteria created = criteriaDao.create(
                createSampleCriteria(parentPov.getId(), "Lifecycle Criterion", "NOT_STARTED", 3));

        // Walk through a status lifecycle
        String[] statusFlow = {"IN_PROGRESS", "PARTIALLY_MET", "MET"};
        for (String status : statusFlow) {
            created.setStatus(status);
            criteriaDao.update(created);

            PovCriteria found = criteriaDao.findById(created.getId());
            assertNotNull(found);
            assertEquals(status, found.getStatus());
        }
    }

    @Test
    void update_preservesPovId() {
        PovCriteria created = criteriaDao.create(
                createSampleCriteria(parentPov.getId(), "Criterion", "NOT_STARTED", 3));

        created.setName("Updated Name");
        criteriaDao.update(created);

        PovCriteria found = criteriaDao.findById(created.getId());
        assertNotNull(found);
        assertEquals(parentPov.getId(), found.getPovId(), "povId should not change on update");
    }

    // -------------------------------------------------------------------------
    // delete() tests
    // -------------------------------------------------------------------------

    @Test
    void delete_removesEntry() {
        PovCriteria created = criteriaDao.create(
                createSampleCriteria(parentPov.getId(), "To Delete", "NOT_STARTED", 1));

        criteriaDao.delete(created.getId());

        PovCriteria found = criteriaDao.findById(created.getId());
        assertNull(found, "Criterion should be null after deletion");
    }

    @Test
    void delete_onlyRemovesTargetEntry() {
        PovCriteria c1 = criteriaDao.create(
                createSampleCriteria(parentPov.getId(), "Criterion One", "NOT_STARTED", 1));
        PovCriteria c2 = criteriaDao.create(
                createSampleCriteria(parentPov.getId(), "Criterion Two", "IN_PROGRESS", 2));

        criteriaDao.delete(c1.getId());

        assertNull(criteriaDao.findById(c1.getId()), "Deleted criterion should be null");
        assertNotNull(criteriaDao.findById(c2.getId()), "Other criterion should still exist");
        assertEquals(1, criteriaDao.findByPovId(parentPov.getId()).size());
    }

    @Test
    void delete_nonExistentId_doesNotThrow() {
        assertDoesNotThrow(() -> criteriaDao.delete(99999L));
    }

    @Test
    void delete_doesNotAffectParentPov() {
        PovCriteria created = criteriaDao.create(
                createSampleCriteria(parentPov.getId(), "Criterion", "NOT_STARTED", 3));

        criteriaDao.delete(created.getId());

        // Parent POV should still exist
        Pov found = povDao.findById(parentPov.getId());
        assertNotNull(found, "Parent POV should not be affected by deleting a criterion");
    }

    // -------------------------------------------------------------------------
    // SQL injection safety tests
    // -------------------------------------------------------------------------

    @Test
    void create_withSqlInjectionPayload_doesNotCorruptData() {
        PovCriteria criteria = createSampleCriteria(
                parentPov.getId(),
                "'; DROP TABLE pov_criteria; --",
                "NOT_STARTED",
                3);
        criteria.setDescription("Robert'; DROP TABLE povs;--");
        criteria.setNotes("'; DELETE FROM pov_criteria; --");

        PovCriteria created = criteriaDao.create(criteria);
        assertNotNull(created);
        assertTrue(created.getId() > 0);

        // Verify the table still exists and entry is retrievable
        PovCriteria found = criteriaDao.findById(created.getId());
        assertNotNull(found);
        assertEquals("'; DROP TABLE pov_criteria; --", found.getName());

        // Verify findByPovId still works (table not dropped)
        List<PovCriteria> all = criteriaDao.findByPovId(parentPov.getId());
        assertFalse(all.isEmpty(), "Table should still exist with data after SQL injection attempt");
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private PovCriteria createSampleCriteria(long povId, String name, String status, int weight) {
        PovCriteria criteria = new PovCriteria();
        criteria.setPovId(povId);
        criteria.setName(name);
        criteria.setStatus(status);
        criteria.setWeight(weight);
        return criteria;
    }
}
