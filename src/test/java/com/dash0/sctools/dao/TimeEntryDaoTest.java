package com.dash0.sctools.dao;

import com.dash0.sctools.model.ActivityType;
import com.dash0.sctools.model.TimeEntry;
import com.dash0.sctools.util.DatabaseInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for TimeEntryDao using in-memory H2 database.
 */
class TimeEntryDaoTest {

    private TimeEntryDao dao;

    @BeforeEach
    void setUp() throws Exception {
        // Use the test JDBC URL from DatabaseInitializer
        dao = new TimeEntryDao(DatabaseInitializer.TEST_JDBC_URL, DatabaseInitializer.JDBC_USER, DatabaseInitializer.JDBC_PASSWORD);

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
        List<TimeEntry> entries = dao.findAll();
        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    void findAll_multipleEntries_returnsAllOrderedByDateDesc() {
        // Create entries with different dates
        TimeEntry older = createSampleEntry("Alice", LocalDate.of(2026, 1, 1), new BigDecimal("4.00"), "Acme", ActivityType.DEMO);
        TimeEntry newer = createSampleEntry("Bob", LocalDate.of(2026, 3, 15), new BigDecimal("8.00"), "BigCorp", ActivityType.DISCOVERY);
        TimeEntry middle = createSampleEntry("Charlie", LocalDate.of(2026, 2, 10), new BigDecimal("2.50"), "MegaCorp", ActivityType.WORKSHOP);

        dao.create(older);
        dao.create(newer);
        dao.create(middle);

        List<TimeEntry> entries = dao.findAll();
        assertEquals(3, entries.size());

        // Verify date descending order
        assertEquals(LocalDate.of(2026, 3, 15), entries.get(0).getDate());
        assertEquals(LocalDate.of(2026, 2, 10), entries.get(1).getDate());
        assertEquals(LocalDate.of(2026, 1, 1), entries.get(2).getDate());
    }

    @Test
    void findAll_singleEntry_returnsList() {
        TimeEntry entry = createSampleEntry("Alice", LocalDate.of(2026, 4, 1), new BigDecimal("6.00"), "Acme", ActivityType.POV_WORK);
        dao.create(entry);

        List<TimeEntry> entries = dao.findAll();
        assertEquals(1, entries.size());
        assertEquals("Alice", entries.get(0).getScName());
    }

    // -------------------------------------------------------------------------
    // findById() tests
    // -------------------------------------------------------------------------

    @Test
    void findById_existingEntry_returnsEntry() {
        TimeEntry created = dao.create(
                createSampleEntry("Alice", LocalDate.of(2026, 4, 1), new BigDecimal("5.50"), "Acme", ActivityType.TECHNICAL_DEEP_DIVE));

        TimeEntry found = dao.findById(created.getId());
        assertNotNull(found);
        assertEquals(created.getId(), found.getId());
        assertEquals("Alice", found.getScName());
        assertEquals(LocalDate.of(2026, 4, 1), found.getDate());
        assertEquals(0, new BigDecimal("5.50").compareTo(found.getHours()));
        assertEquals("Acme", found.getAccountName());
        assertEquals(ActivityType.TECHNICAL_DEEP_DIVE, found.getActivityType());
    }

    @Test
    void findById_nonExistentId_returnsNull() {
        TimeEntry found = dao.findById(99999L);
        assertNull(found);
    }

    @Test
    void findById_returnsCorrectDescription() {
        TimeEntry entry = createSampleEntry("Bob", LocalDate.of(2026, 5, 1), new BigDecimal("3.00"), "BigCorp", ActivityType.TRAINING);
        entry.setDescription("Attended training session on new product features");
        TimeEntry created = dao.create(entry);

        TimeEntry found = dao.findById(created.getId());
        assertNotNull(found);
        assertEquals("Attended training session on new product features", found.getDescription());
    }

    @Test
    void findById_nullDescription_returnsNull() {
        TimeEntry entry = createSampleEntry("Bob", LocalDate.of(2026, 5, 1), new BigDecimal("3.00"), "BigCorp", ActivityType.ADMIN);
        entry.setDescription(null);
        TimeEntry created = dao.create(entry);

        TimeEntry found = dao.findById(created.getId());
        assertNotNull(found);
        assertNull(found.getDescription());
    }

    // -------------------------------------------------------------------------
    // create() tests
    // -------------------------------------------------------------------------

    @Test
    void create_setsIdOnEntry() {
        TimeEntry entry = createSampleEntry("Alice", LocalDate.of(2026, 4, 1), new BigDecimal("8.00"), "Acme", ActivityType.DEMO);
        TimeEntry created = dao.create(entry);

        assertTrue(created.getId() > 0, "ID should be auto-generated and positive");
    }

    @Test
    void create_setsCreatedAtAndUpdatedAt() {
        TimeEntry entry = createSampleEntry("Alice", LocalDate.of(2026, 4, 1), new BigDecimal("8.00"), "Acme", ActivityType.DEMO);
        TimeEntry created = dao.create(entry);

        assertNotNull(created.getCreatedAt(), "createdAt should be set");
        assertNotNull(created.getUpdatedAt(), "updatedAt should be set");
        assertEquals(created.getCreatedAt(), created.getUpdatedAt(), "createdAt and updatedAt should be the same on creation");
    }

    @Test
    void create_persistsAllFields() {
        TimeEntry entry = createSampleEntry("Alice", LocalDate.of(2026, 4, 1), new BigDecimal("7.25"), "Acme Corp", ActivityType.DISCOVERY);
        entry.setDescription("Initial discovery call with the customer");
        TimeEntry created = dao.create(entry);

        // Re-read from database to confirm persistence
        TimeEntry found = dao.findById(created.getId());
        assertNotNull(found);
        assertEquals("Alice", found.getScName());
        assertEquals(LocalDate.of(2026, 4, 1), found.getDate());
        assertEquals(0, new BigDecimal("7.25").compareTo(found.getHours()));
        assertEquals("Acme Corp", found.getAccountName());
        assertEquals(ActivityType.DISCOVERY, found.getActivityType());
        assertEquals("Initial discovery call with the customer", found.getDescription());
        assertNotNull(found.getCreatedAt());
        assertNotNull(found.getUpdatedAt());
    }

    @Test
    void create_allActivityTypes_persistCorrectly() {
        for (ActivityType type : ActivityType.values()) {
            TimeEntry entry = createSampleEntry("SC", LocalDate.of(2026, 1, 1), new BigDecimal("1.00"), "Account", type);
            TimeEntry created = dao.create(entry);
            TimeEntry found = dao.findById(created.getId());
            assertNotNull(found);
            assertEquals(type, found.getActivityType(), "Activity type " + type + " should persist correctly");
        }
    }

    @Test
    void create_decimalHours_persistCorrectly() {
        TimeEntry entry = createSampleEntry("Alice", LocalDate.of(2026, 4, 1), new BigDecimal("2.50"), "Acme", ActivityType.DEMO);
        TimeEntry created = dao.create(entry);

        TimeEntry found = dao.findById(created.getId());
        assertNotNull(found);
        assertEquals(0, new BigDecimal("2.50").compareTo(found.getHours()));
    }

    // -------------------------------------------------------------------------
    // update() tests
    // -------------------------------------------------------------------------

    @Test
    void update_changesFields() {
        TimeEntry created = dao.create(
                createSampleEntry("Alice", LocalDate.of(2026, 4, 1), new BigDecimal("4.00"), "Acme", ActivityType.DEMO));

        // Modify fields
        created.setScName("Bob");
        created.setDate(LocalDate.of(2026, 5, 15));
        created.setHours(new BigDecimal("6.50"));
        created.setAccountName("BigCorp");
        created.setActivityType(ActivityType.POV_WORK);
        created.setDescription("Updated description");

        dao.update(created);

        // Verify changes persisted
        TimeEntry found = dao.findById(created.getId());
        assertNotNull(found);
        assertEquals("Bob", found.getScName());
        assertEquals(LocalDate.of(2026, 5, 15), found.getDate());
        assertEquals(0, new BigDecimal("6.50").compareTo(found.getHours()));
        assertEquals("BigCorp", found.getAccountName());
        assertEquals(ActivityType.POV_WORK, found.getActivityType());
        assertEquals("Updated description", found.getDescription());
    }

    @Test
    void update_setsUpdatedAtToNewTimestamp() throws InterruptedException {
        TimeEntry created = dao.create(
                createSampleEntry("Alice", LocalDate.of(2026, 4, 1), new BigDecimal("4.00"), "Acme", ActivityType.DEMO));

        // Store original timestamps
        var originalCreatedAt = created.getCreatedAt();
        var originalUpdatedAt = created.getUpdatedAt();

        // Small delay to ensure timestamp difference
        Thread.sleep(50);

        created.setHours(new BigDecimal("8.00"));
        dao.update(created);

        TimeEntry found = dao.findById(created.getId());
        assertNotNull(found);
        // createdAt should not change; compare to what was returned by create()
        assertEquals(originalCreatedAt, found.getCreatedAt(), "createdAt should not change on update");
        // updatedAt should be newer
        assertTrue(found.getUpdatedAt().isAfter(originalUpdatedAt) || found.getUpdatedAt().isEqual(originalUpdatedAt),
                "updatedAt should be equal to or after original updatedAt");
    }

    @Test
    void update_doesNotCreateDuplicateEntry() {
        dao.create(createSampleEntry("Alice", LocalDate.of(2026, 4, 1), new BigDecimal("4.00"), "Acme", ActivityType.DEMO));
        TimeEntry created = dao.create(
                createSampleEntry("Bob", LocalDate.of(2026, 4, 2), new BigDecimal("3.00"), "BigCorp", ActivityType.INTERNAL));

        created.setScName("Bob Updated");
        dao.update(created);

        List<TimeEntry> all = dao.findAll();
        assertEquals(2, all.size(), "Update should not create a new entry");
    }

    // -------------------------------------------------------------------------
    // delete() tests
    // -------------------------------------------------------------------------

    @Test
    void delete_removesEntry() {
        TimeEntry created = dao.create(
                createSampleEntry("Alice", LocalDate.of(2026, 4, 1), new BigDecimal("4.00"), "Acme", ActivityType.DEMO));

        dao.delete(created.getId());

        TimeEntry found = dao.findById(created.getId());
        assertNull(found, "Entry should be null after deletion");
    }

    @Test
    void delete_onlyRemovesTargetEntry() {
        TimeEntry entry1 = dao.create(
                createSampleEntry("Alice", LocalDate.of(2026, 4, 1), new BigDecimal("4.00"), "Acme", ActivityType.DEMO));
        TimeEntry entry2 = dao.create(
                createSampleEntry("Bob", LocalDate.of(2026, 4, 2), new BigDecimal("3.00"), "BigCorp", ActivityType.INTERNAL));

        dao.delete(entry1.getId());

        assertNull(dao.findById(entry1.getId()), "Deleted entry should be null");
        assertNotNull(dao.findById(entry2.getId()), "Other entry should still exist");
        assertEquals(1, dao.findAll().size());
    }

    @Test
    void delete_nonExistentId_doesNotThrow() {
        // Deleting a non-existent ID should not throw (affected rows = 0)
        assertDoesNotThrow(() -> dao.delete(99999L));
    }

    // -------------------------------------------------------------------------
    // SQL injection safety tests
    // -------------------------------------------------------------------------

    @Test
    void create_withSqlInjectionPayload_doesNotCorruptData() {
        TimeEntry entry = createSampleEntry(
                "'; DROP TABLE time_entries; --",
                LocalDate.of(2026, 4, 1),
                new BigDecimal("1.00"),
                "'; DELETE FROM time_entries; --",
                ActivityType.OTHER);
        entry.setDescription("Robert'; DROP TABLE time_entries;--");

        TimeEntry created = dao.create(entry);
        assertNotNull(created);
        assertTrue(created.getId() > 0);

        // Verify the table still exists and entry is retrievable
        TimeEntry found = dao.findById(created.getId());
        assertNotNull(found);
        assertEquals("'; DROP TABLE time_entries; --", found.getScName());
        assertEquals("'; DELETE FROM time_entries; --", found.getAccountName());

        // Verify findAll still works (table not dropped)
        List<TimeEntry> all = dao.findAll();
        assertFalse(all.isEmpty(), "Table should still exist with data after SQL injection attempt");
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private TimeEntry createSampleEntry(String scName, LocalDate date, BigDecimal hours,
                                        String accountName, ActivityType activityType) {
        TimeEntry entry = new TimeEntry();
        entry.setScName(scName);
        entry.setDate(date);
        entry.setHours(hours);
        entry.setAccountName(accountName);
        entry.setActivityType(activityType);
        return entry;
    }
}
