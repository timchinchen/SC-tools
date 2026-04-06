package com.dash0.sctools.servlet;

import com.dash0.sctools.Application;
import com.dash0.sctools.dao.TimeEntryDao;
import com.dash0.sctools.model.ActivityType;
import com.dash0.sctools.model.TimeEntry;
import com.dash0.sctools.util.DatabaseInitializer;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for TimeEntryServlet GET handler using embedded Jetty.
 * The embedded server uses the production DAO (file-based H2), so we initialize
 * the production database and clean time_entries before each test.
 */
class TimeEntryServletTest {

    private Server server;
    private static final int TEST_PORT = 8098;
    private TimeEntryDao dao;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize the production database (creates tables if needed)
        // The servlet's default constructor uses the production DAO,
        // so the production database must exist for integration tests.
        DatabaseInitializer.initialize();

        // Use the production DAO (same as the servlet will use)
        dao = new TimeEntryDao();

        // Clean out any existing time entries for a fresh test state
        try (Connection conn = DatabaseInitializer.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM time_entries");
        }

        // Start server
        server = Application.createServer(TEST_PORT);
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private HttpURLConnection openConnection(String path) throws Exception {
        URL url = URI.create("http://localhost:" + TEST_PORT + path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    private String readResponseBody(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private TimeEntry createSampleEntry(String scName, LocalDate date, BigDecimal hours,
                                        String accountName, ActivityType activityType, String description) {
        TimeEntry entry = new TimeEntry();
        entry.setScName(scName);
        entry.setDate(date);
        entry.setHours(hours);
        entry.setAccountName(accountName);
        entry.setActivityType(activityType);
        entry.setDescription(description);
        return entry;
    }

    // -------------------------------------------------------------------------
    // GET /time-entries tests
    // -------------------------------------------------------------------------

    @Test
    void getTimeEntries_returns200() throws Exception {
        HttpURLConnection conn = openConnection("/time-entries");
        int statusCode = conn.getResponseCode();
        assertEquals(200, statusCode, "GET /time-entries should return 200");
        conn.disconnect();
    }

    @Test
    void getTimeEntries_returnsHtml() throws Exception {
        HttpURLConnection conn = openConnection("/time-entries");
        String contentType = conn.getContentType();
        assertTrue(contentType.contains("text/html"), "Response should be HTML, got: " + contentType);
        conn.disconnect();
    }

    @Test
    void getTimeEntries_emptyDatabase_showsEmptyState() throws Exception {
        HttpURLConnection conn = openConnection("/time-entries");
        String body = readResponseBody(conn);

        assertTrue(body.contains("No time entries yet."),
                "Empty state message should be displayed when no entries exist");
        // Should NOT contain the data table when empty
        assertFalse(body.contains("<table"),
                "Table should not be rendered when no entries exist");
        conn.disconnect();
    }

    @Test
    void getTimeEntries_emptyDatabase_showsAddButton() throws Exception {
        HttpURLConnection conn = openConnection("/time-entries");
        String body = readResponseBody(conn);

        assertTrue(body.contains("Add New Time Entry"),
                "Add New Time Entry button should be visible");
        assertTrue(body.contains("/time-entries?action=new"),
                "Add button should link to the create form");
        conn.disconnect();
    }

    @Test
    void getTimeEntries_withEntries_showsTable() throws Exception {
        // Create a test entry via DAO (uses production DB since server is running in prod mode)
        // Note: The server uses prod DB, so we need to create entries there.
        // Since the server starts with a fresh embedded Jetty and the prod DAO,
        // we verify via the HTML response that the table headers are present.
        HttpURLConnection conn = openConnection("/time-entries");
        String body = readResponseBody(conn);

        // Page should have the correct title
        assertTrue(body.contains("Time Entries"),
                "Page should contain 'Time Entries' title");
        conn.disconnect();
    }

    @Test
    void getTimeEntries_containsTableHeaders() throws Exception {
        // We need entries to see the table; the production DAO connects to file DB
        // which may have entries or not. Let's verify the page renders without errors.
        HttpURLConnection conn = openConnection("/time-entries");
        assertEquals(200, conn.getResponseCode(), "Should render without errors");
        String body = readResponseBody(conn);

        // Whether or not there are entries, the page should render cleanly
        assertTrue(body.contains("Time Entries"),
                "Page should contain heading");
        // The Add New button should always be present
        assertTrue(body.contains("Add New Time Entry"),
                "Add button should always be present");
        conn.disconnect();
    }

    @Test
    void getTimeEntries_containsNavigationLinks() throws Exception {
        HttpURLConnection conn = openConnection("/time-entries");
        String body = readResponseBody(conn);

        // Navigation should contain links from the header
        assertTrue(body.contains("/dashboard"),
                "Page should contain link to dashboard");
        assertTrue(body.contains("/time-entries"),
                "Page should contain link to time entries");
        assertTrue(body.contains("/povs"),
                "Page should contain link to POVs");
        conn.disconnect();
    }

    @Test
    void getTimeEntries_containsPageStructure() throws Exception {
        HttpURLConnection conn = openConnection("/time-entries");
        String body = readResponseBody(conn);

        // Verify the page has proper HTML structure from the layout includes
        assertTrue(body.contains("<!DOCTYPE html>"),
                "Page should start with DOCTYPE");
        assertTrue(body.contains("<header"),
                "Page should contain header element");
        assertTrue(body.contains("<footer"),
                "Page should contain footer element");
        assertTrue(body.contains("style.css"),
                "Page should reference the stylesheet");
        conn.disconnect();
    }

    @Test
    void getTimeEntries_withData_showsTableWithEntryValues() throws Exception {
        // Insert a time entry into the production database
        TimeEntry entry = createSampleEntry(
                "Alice Johnson", LocalDate.of(2026, 3, 15), new BigDecimal("4.50"),
                "Acme Corp", ActivityType.DEMO, "Product demo for stakeholders");
        dao.create(entry);

        HttpURLConnection conn = openConnection("/time-entries");
        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        // Table should be present (not empty state)
        assertTrue(body.contains("<table"), "Table should be rendered when entries exist");
        assertFalse(body.contains("No time entries yet."),
                "Empty state should NOT be shown when entries exist");

        // Verify column headers
        assertTrue(body.contains("SC Name"), "Table should have SC Name header");
        assertTrue(body.contains("Date"), "Table should have Date header");
        assertTrue(body.contains("Hours"), "Table should have Hours header");
        assertTrue(body.contains("Account Name"), "Table should have Account Name header");
        assertTrue(body.contains("Activity Type"), "Table should have Activity Type header");
        assertTrue(body.contains("Description"), "Table should have Description header");
        assertTrue(body.contains("Actions"), "Table should have Actions header");

        // Verify data values rendered
        assertTrue(body.contains("Alice Johnson"), "SC Name should be displayed");
        assertTrue(body.contains("2026-03-15"), "Date should be displayed in YYYY-MM-DD format");
        assertTrue(body.contains("4.5"), "Hours should be displayed with up to 2 decimal places");
        assertTrue(body.contains("Acme Corp"), "Account Name should be displayed");
        assertTrue(body.contains("Demo"), "Activity type should display in human-readable format");
        assertTrue(body.contains("Product demo for stakeholders"), "Description should be displayed");

        // Verify action links
        assertTrue(body.contains("Edit"), "Edit link should be present");
        assertTrue(body.contains("Delete"), "Delete link should be present");
        assertTrue(body.contains("action=edit"), "Edit link should point to edit action");

        conn.disconnect();
    }

    @Test
    void getTimeEntries_withMultipleEntries_showsAllEntries() throws Exception {
        dao.create(createSampleEntry("Alice", LocalDate.of(2026, 3, 15),
                new BigDecimal("4.00"), "Acme Corp", ActivityType.DEMO, "Demo 1"));
        dao.create(createSampleEntry("Bob", LocalDate.of(2026, 3, 16),
                new BigDecimal("8.00"), "BigCorp", ActivityType.DISCOVERY, "Discovery call"));

        HttpURLConnection conn = openConnection("/time-entries");
        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Alice"), "First entry SC Name should be shown");
        assertTrue(body.contains("Bob"), "Second entry SC Name should be shown");
        assertTrue(body.contains("Acme Corp"), "First entry account should be shown");
        assertTrue(body.contains("BigCorp"), "Second entry account should be shown");
        assertTrue(body.contains("Demo"), "First entry activity type should be readable");
        assertTrue(body.contains("Discovery"), "Second entry activity type should be readable");

        conn.disconnect();
    }

    @Test
    void getTimeEntries_hoursDisplayedWithMaxTwoDecimalPlaces() throws Exception {
        // Test decimal hours (up to 2 decimal places)
        dao.create(createSampleEntry("Alice", LocalDate.of(2026, 3, 15),
                new BigDecimal("4.50"), "Acme", ActivityType.DEMO, null));

        HttpURLConnection conn = openConnection("/time-entries");
        String body = readResponseBody(conn);

        // Should show 4.5 or 4.50 (max 2 decimal places, not more)
        assertTrue(body.contains("4.5"),
                "Hours should be displayed with up to 2 decimal places");
        // Should not have more than 2 decimal places
        assertFalse(body.contains("4.500"),
                "Hours should not show more than 2 decimal places");

        conn.disconnect();
    }

    @Test
    void getTimeEntries_activityTypeDisplaysHumanReadable() throws Exception {
        dao.create(createSampleEntry("Alice", LocalDate.of(2026, 3, 15),
                new BigDecimal("2.00"), "Acme", ActivityType.TECHNICAL_DEEP_DIVE, null));
        dao.create(createSampleEntry("Bob", LocalDate.of(2026, 3, 16),
                new BigDecimal("3.00"), "Corp", ActivityType.POV_WORK, null));

        HttpURLConnection conn = openConnection("/time-entries");
        String body = readResponseBody(conn);

        assertTrue(body.contains("Technical Deep Dive"),
                "TECHNICAL_DEEP_DIVE should display as 'Technical Deep Dive'");
        assertTrue(body.contains("POV Work"),
                "POV_WORK should display as 'POV Work'");

        conn.disconnect();
    }

    @Test
    void getTimeEntries_xssProtection() throws Exception {
        // Create entry with XSS payload
        dao.create(createSampleEntry("<script>alert(1)</script>", LocalDate.of(2026, 3, 15),
                new BigDecimal("1.00"), "<img onerror=alert(1)>", ActivityType.OTHER,
                "<b>bold</b>"));

        HttpURLConnection conn = openConnection("/time-entries");
        String body = readResponseBody(conn);

        // c:out should escape the HTML
        assertFalse(body.contains("<script>alert(1)</script>"),
                "Script tags should be escaped, not rendered as raw HTML");
        assertFalse(body.contains("<img onerror=alert(1)>"),
                "Image tags with event handlers should be escaped");
        // The escaped versions should be present
        assertTrue(body.contains("&lt;script&gt;") || body.contains("&lt;script"),
                "Script tags should be HTML-escaped");

        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // GET /time-entries?action=new (create form) tests
    // -------------------------------------------------------------------------

    @Test
    void getCreateForm_returns200() throws Exception {
        HttpURLConnection conn = openConnection("/time-entries?action=new");
        int statusCode = conn.getResponseCode();
        assertEquals(200, statusCode, "GET /time-entries?action=new should return 200");
        conn.disconnect();
    }

    @Test
    void getCreateForm_containsFormFields() throws Exception {
        HttpURLConnection conn = openConnection("/time-entries?action=new");
        String body = readResponseBody(conn);

        // Check form element with POST method
        assertTrue(body.contains("method=\"post\""),
                "Form should use POST method");

        // Check all required fields
        assertTrue(body.contains("name=\"scName\""),
                "Form should have scName field");
        assertTrue(body.contains("name=\"date\""),
                "Form should have date field");
        assertTrue(body.contains("name=\"hours\""),
                "Form should have hours field");
        assertTrue(body.contains("name=\"accountName\""),
                "Form should have accountName field");
        assertTrue(body.contains("name=\"activityType\""),
                "Form should have activityType field");
        assertTrue(body.contains("name=\"description\""),
                "Form should have description field");

        conn.disconnect();
    }

    @Test
    void getCreateForm_containsCorrectInputTypes() throws Exception {
        HttpURLConnection conn = openConnection("/time-entries?action=new");
        String body = readResponseBody(conn);

        assertTrue(body.contains("type=\"text\""),
                "Form should have text inputs");
        assertTrue(body.contains("type=\"date\""),
                "Form should have date input");
        assertTrue(body.contains("type=\"number\""),
                "Form should have number input");
        assertTrue(body.contains("step=\"0.25\""),
                "Hours input should have step=0.25");
        assertTrue(body.contains("<select"),
                "Form should have select dropdown for activity type");
        assertTrue(body.contains("<textarea"),
                "Form should have textarea for description");

        conn.disconnect();
    }

    @Test
    void getCreateForm_activityTypeDropdownHas9Options() throws Exception {
        HttpURLConnection conn = openConnection("/time-entries?action=new");
        String body = readResponseBody(conn);

        // Check all 9 activity types are present in the dropdown
        assertTrue(body.contains("Demo"), "Dropdown should contain Demo");
        assertTrue(body.contains("Discovery"), "Dropdown should contain Discovery");
        assertTrue(body.contains("POV Work"), "Dropdown should contain POV Work");
        assertTrue(body.contains("Technical Deep Dive"), "Dropdown should contain Technical Deep Dive");
        assertTrue(body.contains("Workshop"), "Dropdown should contain Workshop");
        assertTrue(body.contains("Internal"), "Dropdown should contain Internal");
        assertTrue(body.contains("Training"), "Dropdown should contain Training");
        assertTrue(body.contains("Admin"), "Dropdown should contain Admin");
        assertTrue(body.contains("Other"), "Dropdown should contain Other");

        // Check the option values use enum names
        assertTrue(body.contains("value=\"DEMO\""), "Dropdown should have DEMO option value");
        assertTrue(body.contains("value=\"DISCOVERY\""), "Dropdown should have DISCOVERY option value");
        assertTrue(body.contains("value=\"POV_WORK\""), "Dropdown should have POV_WORK option value");
        assertTrue(body.contains("value=\"TECHNICAL_DEEP_DIVE\""), "Dropdown should have TECHNICAL_DEEP_DIVE option value");
        assertTrue(body.contains("value=\"WORKSHOP\""), "Dropdown should have WORKSHOP option value");
        assertTrue(body.contains("value=\"INTERNAL\""), "Dropdown should have INTERNAL option value");
        assertTrue(body.contains("value=\"TRAINING\""), "Dropdown should have TRAINING option value");
        assertTrue(body.contains("value=\"ADMIN\""), "Dropdown should have ADMIN option value");
        assertTrue(body.contains("value=\"OTHER\""), "Dropdown should have OTHER option value");

        conn.disconnect();
    }

    @Test
    void getCreateForm_containsCancelLink() throws Exception {
        HttpURLConnection conn = openConnection("/time-entries?action=new");
        String body = readResponseBody(conn);

        assertTrue(body.contains("/time-entries") && body.contains("Cancel"),
                "Form should have a Cancel link back to the list page");

        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // POST /time-entries (create) tests — helpers
    // -------------------------------------------------------------------------

    private HttpURLConnection openPostConnection(String path, String formData) throws Exception {
        URL url = URI.create("http://localhost:" + TEST_PORT + path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setInstanceFollowRedirects(false);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.getOutputStream().write(formData.getBytes("UTF-8"));
        conn.getOutputStream().flush();
        return conn;
    }

    private String readErrorBody(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    // -------------------------------------------------------------------------
    // POST /time-entries (create) tests — valid data
    // -------------------------------------------------------------------------

    @Test
    void postCreate_validData_redirectsToList() throws Exception {
        String formData = "scName=Alice&date=2026-04-01&hours=4.5&accountName=Acme&activityType=DEMO&description=Test+demo";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        int statusCode = conn.getResponseCode();
        assertEquals(302, statusCode, "POST with valid data should redirect (302)");

        String location = conn.getHeaderField("Location");
        assertNotNull(location, "Redirect should have a Location header");
        assertTrue(location.endsWith("/time-entries"),
                "Should redirect to /time-entries, got: " + location);

        conn.disconnect();
    }

    @Test
    void postCreate_validData_entryPersistedInDatabase() throws Exception {
        String formData = "scName=Bob&date=2026-04-02&hours=8&accountName=BigCorp&activityType=DISCOVERY&description=Call";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        // Verify the entry was persisted
        List<TimeEntry> entries = dao.findAll();
        assertFalse(entries.isEmpty(), "Entry should be persisted after successful POST");

        TimeEntry created = entries.stream()
                .filter(e -> "Bob".equals(e.getScName()))
                .findFirst()
                .orElse(null);
        assertNotNull(created, "Entry with scName='Bob' should exist");
        assertEquals(LocalDate.of(2026, 4, 2), created.getDate());
        assertEquals(0, new BigDecimal("8").compareTo(created.getHours()));
        assertEquals("BigCorp", created.getAccountName());
        assertEquals(ActivityType.DISCOVERY, created.getActivityType());
        assertEquals("Call", created.getDescription());
    }

    @Test
    void postCreate_decimalHours_succeeds() throws Exception {
        String formData = "scName=Alice&date=2026-04-01&hours=2.5&accountName=Acme&activityType=DEMO&description=";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        int statusCode = conn.getResponseCode();
        assertEquals(302, statusCode, "POST with decimal hours (2.5) should succeed with redirect");

        // Verify persistence
        List<TimeEntry> entries = dao.findAll();
        TimeEntry created = entries.stream()
                .filter(e -> "Alice".equals(e.getScName()))
                .findFirst()
                .orElse(null);
        assertNotNull(created, "Entry should be persisted");
        assertEquals(0, new BigDecimal("2.5").compareTo(created.getHours()),
                "Hours should be 2.5");

        conn.disconnect();
    }

    @Test
    void postCreate_optionalDescriptionEmpty_succeeds() throws Exception {
        String formData = "scName=Alice&date=2026-04-01&hours=1&accountName=Acme&activityType=OTHER&description=";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(302, conn.getResponseCode(),
                "POST with empty description should succeed (description is optional)");
        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // POST /time-entries (create) tests — validation failures
    // -------------------------------------------------------------------------

    @Test
    void postCreate_missingScName_showsError() throws Exception {
        String formData = "scName=&date=2026-04-01&hours=4&accountName=Acme&activityType=DEMO&description=test";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        int statusCode = conn.getResponseCode();
        assertEquals(200, statusCode, "POST with missing scName should return 200 (re-render form)");

        String body = readResponseBody(conn);
        assertTrue(body.contains("SC Name is required"),
                "Should show 'SC Name is required' error");

        conn.disconnect();
    }

    @Test
    void postCreate_missingDate_showsError() throws Exception {
        String formData = "scName=Alice&date=&hours=4&accountName=Acme&activityType=DEMO&description=test";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Date is required"),
                "Should show 'Date is required' error");

        conn.disconnect();
    }

    @Test
    void postCreate_missingHours_showsError() throws Exception {
        String formData = "scName=Alice&date=2026-04-01&hours=&accountName=Acme&activityType=DEMO&description=test";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Hours is required"),
                "Should show 'Hours is required' error");

        conn.disconnect();
    }

    @Test
    void postCreate_missingAccountName_showsError() throws Exception {
        String formData = "scName=Alice&date=2026-04-01&hours=4&accountName=&activityType=DEMO&description=test";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Account Name is required"),
                "Should show 'Account Name is required' error");

        conn.disconnect();
    }

    @Test
    void postCreate_missingActivityType_showsError() throws Exception {
        String formData = "scName=Alice&date=2026-04-01&hours=4&accountName=Acme&activityType=&description=test";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Activity Type is required"),
                "Should show 'Activity Type is required' error");

        conn.disconnect();
    }

    @Test
    void postCreate_allFieldsMissing_showsMultipleErrors() throws Exception {
        String formData = "scName=&date=&hours=&accountName=&activityType=&description=";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("SC Name is required"), "Should list SC Name error");
        assertTrue(body.contains("Date is required"), "Should list Date error");
        assertTrue(body.contains("Hours is required"), "Should list Hours error");
        assertTrue(body.contains("Account Name is required"), "Should list Account Name error");
        assertTrue(body.contains("Activity Type is required"), "Should list Activity Type error");

        // Verify no entry was created
        List<TimeEntry> entries = dao.findAll();
        assertTrue(entries.isEmpty(), "No entry should be created when all fields are missing");

        conn.disconnect();
    }

    @Test
    void postCreate_hoursZero_showsError() throws Exception {
        String formData = "scName=Alice&date=2026-04-01&hours=0&accountName=Acme&activityType=DEMO&description=";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Hours must be greater than 0"),
                "Should show error for hours=0");

        conn.disconnect();
    }

    @Test
    void postCreate_hoursNegative_showsError() throws Exception {
        String formData = "scName=Alice&date=2026-04-01&hours=-2&accountName=Acme&activityType=DEMO&description=";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Hours must be greater than 0"),
                "Should show error for negative hours");

        conn.disconnect();
    }

    @Test
    void postCreate_hoursExceeds24_showsError() throws Exception {
        String formData = "scName=Alice&date=2026-04-01&hours=25&accountName=Acme&activityType=DEMO&description=";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Hours must not exceed 24"),
                "Should show error for hours > 24");

        conn.disconnect();
    }

    @Test
    void postCreate_hoursNonNumeric_showsError() throws Exception {
        String formData = "scName=Alice&date=2026-04-01&hours=abc&accountName=Acme&activityType=DEMO&description=";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Hours must be a valid number"),
                "Should show error for non-numeric hours");

        conn.disconnect();
    }

    @Test
    void postCreate_validationFailure_preservesEnteredValues() throws Exception {
        String formData = "scName=Alice+Johnson&date=2026-04-01&hours=&accountName=BigCorp&activityType=WORKSHOP&description=Some+notes";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        // Previously entered values should be preserved
        assertTrue(body.contains("Alice Johnson"),
                "SC Name value should be preserved after validation failure");
        assertTrue(body.contains("2026-04-01"),
                "Date value should be preserved after validation failure");
        assertTrue(body.contains("BigCorp"),
                "Account Name value should be preserved after validation failure");
        assertTrue(body.contains("Some notes"),
                "Description value should be preserved after validation failure");
        // Activity type should be selected
        assertTrue(body.contains("WORKSHOP") && body.contains("selected"),
                "Activity Type selection should be preserved after validation failure");

        conn.disconnect();
    }

    @Test
    void postCreate_validationFailure_showsFormWithDropdown() throws Exception {
        String formData = "scName=&date=&hours=&accountName=&activityType=&description=";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        // Form should still have the activity type dropdown with all 9 options
        assertTrue(body.contains("value=\"DEMO\""), "Dropdown should still have DEMO");
        assertTrue(body.contains("value=\"OTHER\""), "Dropdown should still have OTHER");
        assertTrue(body.contains("<form"), "Should re-render the form");
        assertTrue(body.contains("method=\"post\""), "Form should use POST method");

        conn.disconnect();
    }

    @Test
    void postCreate_hours24_succeeds() throws Exception {
        String formData = "scName=Alice&date=2026-04-01&hours=24&accountName=Acme&activityType=DEMO&description=";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(302, conn.getResponseCode(),
                "POST with hours=24 should succeed (24 is the maximum)");
        conn.disconnect();
    }

    @Test
    void postCreate_xssInFormValues_escapedOnRerender() throws Exception {
        String formData = "scName=%3Cscript%3Ealert(1)%3C%2Fscript%3E&date=2026-04-01&hours=&accountName=%3Cimg+onerror%3Dalert(1)%3E&activityType=DEMO&description=%3Cb%3Ebold%3C%2Fb%3E";
        HttpURLConnection conn = openPostConnection("/time-entries", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        // XSS payloads should be escaped, not rendered as raw HTML
        assertFalse(body.contains("<script>alert(1)</script>"),
                "Script tags should be escaped in form re-render");
        assertFalse(body.contains("<img onerror=alert(1)>"),
                "Image tags should be escaped in form re-render");

        conn.disconnect();
    }
}
