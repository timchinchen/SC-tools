package com.dash0.sctools.servlet;

import com.dash0.sctools.Application;
import com.dash0.sctools.dao.PovCriteriaDao;
import com.dash0.sctools.dao.PovDao;
import com.dash0.sctools.model.Pov;
import com.dash0.sctools.model.PovCriteria;
import com.dash0.sctools.util.DatabaseInitializer;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
 * Integration tests for PovServlet using embedded Jetty.
 * Tests GET list, GET create form, and POST create with validation.
 */
class PovServletTest {

    private Server server;
    private static final int TEST_PORT = 8098;
    private PovDao dao;
    private PovCriteriaDao criteriaDao;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize the production database (creates tables if needed)
        DatabaseInitializer.initialize();

        // Use the production DAOs (same as the servlet will use)
        dao = new PovDao();
        criteriaDao = new PovCriteriaDao();

        // Clean out any existing POVs and criteria for a fresh test state
        try (Connection conn = DatabaseInitializer.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM pov_criteria");
            stmt.execute("DELETE FROM povs");
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

    private Pov createSamplePov(String name, String accountName, String scName,
                                String status, LocalDate startDate, LocalDate targetEndDate,
                                String description) {
        Pov pov = new Pov();
        pov.setName(name);
        pov.setAccountName(accountName);
        pov.setScName(scName);
        pov.setStatus(status);
        pov.setStartDate(startDate);
        pov.setTargetEndDate(targetEndDate);
        pov.setDescription(description);
        return pov;
    }

    // -------------------------------------------------------------------------
    // GET /povs (list) tests
    // -------------------------------------------------------------------------

    @Test
    void getPovs_returns200() throws Exception {
        HttpURLConnection conn = openConnection("/povs");
        int statusCode = conn.getResponseCode();
        assertEquals(200, statusCode, "GET /povs should return 200");
        conn.disconnect();
    }

    @Test
    void getPovs_returnsHtml() throws Exception {
        HttpURLConnection conn = openConnection("/povs");
        String contentType = conn.getContentType();
        assertTrue(contentType.contains("text/html"), "Response should be HTML, got: " + contentType);
        conn.disconnect();
    }

    @Test
    void getPovs_emptyDatabase_showsEmptyState() throws Exception {
        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertTrue(body.contains("No POVs yet."),
                "Empty state message should be displayed when no POVs exist");
        assertFalse(body.contains("<table"),
                "Table should not be rendered when no POVs exist");
        conn.disconnect();
    }

    @Test
    void getPovs_emptyDatabase_showsCreateButton() throws Exception {
        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertTrue(body.contains("Create New POV"),
                "Create New POV button should be visible");
        assertTrue(body.contains("/povs?action=new"),
                "Create button should link to the create form");
        conn.disconnect();
    }

    @Test
    void getPovs_containsNavigationLinks() throws Exception {
        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertTrue(body.contains("/dashboard"),
                "Page should contain link to dashboard");
        assertTrue(body.contains("/time-entries"),
                "Page should contain link to time entries");
        assertTrue(body.contains("/povs"),
                "Page should contain link to POVs");
        conn.disconnect();
    }

    @Test
    void getPovs_containsPageStructure() throws Exception {
        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

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
    void getPovs_withData_showsTableWithAllColumns() throws Exception {
        Pov pov = createSamplePov("Acme POV", "Acme Corp", "Alice Johnson",
                "IN_PROGRESS", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                "POV for Acme Corp");
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs");
        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        // Table should be present
        assertTrue(body.contains("<table"), "Table should be rendered when POVs exist");
        assertFalse(body.contains("No POVs yet."),
                "Empty state should NOT be shown when POVs exist");

        // Verify column headers
        assertTrue(body.contains("Name"), "Table should have Name header");
        assertTrue(body.contains("Account Name"), "Table should have Account Name header");
        assertTrue(body.contains("SC Name"), "Table should have SC Name header");
        assertTrue(body.contains("Status"), "Table should have Status header");
        assertTrue(body.contains("Start Date"), "Table should have Start Date header");
        assertTrue(body.contains("Target End Date"), "Table should have Target End Date header");
        assertTrue(body.contains("Actions"), "Table should have Actions header");

        // Verify data values rendered
        assertTrue(body.contains("Acme POV"), "Name should be displayed");
        assertTrue(body.contains("Acme Corp"), "Account Name should be displayed");
        assertTrue(body.contains("Alice Johnson"), "SC Name should be displayed");
        assertTrue(body.contains("2026-04-01"), "Start Date should be displayed");
        assertTrue(body.contains("2026-06-30"), "Target End Date should be displayed");

        // Verify action links
        assertTrue(body.contains("Edit"), "Edit link should be present");
        assertTrue(body.contains("Delete"), "Delete button should be present");

        conn.disconnect();
    }

    @Test
    void getPovs_nameIsLinkToDetail() throws Exception {
        Pov pov = createSamplePov("Test POV", "TestCo", "Bob",
                "PLANNED", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 7, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertTrue(body.contains("action=detail"),
                "Name column should link to detail page");
        assertTrue(body.contains("id=" + pov.getId()),
                "Detail link should include the POV id");
        conn.disconnect();
    }

    @Test
    void getPovs_statusBadge_planned() throws Exception {
        Pov pov = createSamplePov("Planned POV", "Co1", "SC1",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertTrue(body.contains("badge-planned"),
                "PLANNED status should have badge-planned CSS class");
        assertTrue(body.contains("Planned"),
                "PLANNED status should display as 'Planned'");
        conn.disconnect();
    }

    @Test
    void getPovs_statusBadge_inProgress() throws Exception {
        Pov pov = createSamplePov("Active POV", "Co2", "SC2",
                "IN_PROGRESS", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertTrue(body.contains("badge-in-progress"),
                "IN_PROGRESS status should have badge-in-progress CSS class");
        assertTrue(body.contains("In Progress"),
                "IN_PROGRESS status should display as 'In Progress'");
        conn.disconnect();
    }

    @Test
    void getPovs_statusBadge_completed() throws Exception {
        Pov pov = createSamplePov("Done POV", "Co3", "SC3",
                "COMPLETED", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertTrue(body.contains("badge-completed"),
                "COMPLETED status should have badge-completed CSS class");
        conn.disconnect();
    }

    @Test
    void getPovs_statusBadge_won() throws Exception {
        Pov pov = createSamplePov("Won POV", "Co4", "SC4",
                "WON", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertTrue(body.contains("badge-won"),
                "WON status should have badge-won CSS class");
        conn.disconnect();
    }

    @Test
    void getPovs_statusBadge_lost() throws Exception {
        Pov pov = createSamplePov("Lost POV", "Co5", "SC5",
                "LOST", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertTrue(body.contains("badge-lost"),
                "LOST status should have badge-lost CSS class");
        conn.disconnect();
    }

    @Test
    void getPovs_statusBadge_cancelled() throws Exception {
        Pov pov = createSamplePov("Cancelled POV", "Co6", "SC6",
                "CANCELLED", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertTrue(body.contains("badge-cancelled"),
                "CANCELLED status should have badge-cancelled CSS class");
        conn.disconnect();
    }

    @Test
    void getPovs_withMultiplePovs_showsAllPovs() throws Exception {
        dao.create(createSamplePov("POV Alpha", "AlphaCo", "Alice",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), "First POV"));
        dao.create(createSamplePov("POV Beta", "BetaCo", "Bob",
                "IN_PROGRESS", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 1), "Second POV"));

        HttpURLConnection conn = openConnection("/povs");
        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        assertTrue(body.contains("POV Alpha"), "First POV name should be shown");
        assertTrue(body.contains("POV Beta"), "Second POV name should be shown");
        assertTrue(body.contains("AlphaCo"), "First POV account should be shown");
        assertTrue(body.contains("BetaCo"), "Second POV account should be shown");

        conn.disconnect();
    }

    @Test
    void getPovs_xssProtection() throws Exception {
        dao.create(createSamplePov("<script>alert(1)</script>", "<img onerror=alert(1)>", "SC1",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1),
                "<b>bold</b>"));

        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertFalse(body.contains("<script>alert(1)</script>"),
                "Script tags should be escaped, not rendered as raw HTML");
        assertFalse(body.contains("<img onerror=alert(1)>"),
                "Image tags with event handlers should be escaped");
        assertTrue(body.contains("&lt;script&gt;") || body.contains("&lt;script"),
                "Script tags should be HTML-escaped");

        conn.disconnect();
    }

    @Test
    void getPovs_editLinkIncludesId() throws Exception {
        Pov pov = createSamplePov("Test POV", "TestCo", "SC1",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertTrue(body.contains("action=edit&amp;id=" + pov.getId()),
                "Edit link should include the POV id");
        conn.disconnect();
    }

    @Test
    void getPovs_deleteFormWithPostMethod() throws Exception {
        Pov pov = createSamplePov("Test POV", "TestCo", "SC1",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertTrue(body.contains("action=delete&amp;id=" + pov.getId()),
                "Delete form should include the POV id");
        assertTrue(body.contains("method=\"post\""),
                "Delete should use POST method");
        assertTrue(body.contains("confirm("),
                "Delete form should have JavaScript confirm dialog");
        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // GET /povs?action=new (create form) tests
    // -------------------------------------------------------------------------

    @Test
    void getCreateForm_returns200() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=new");
        int statusCode = conn.getResponseCode();
        assertEquals(200, statusCode, "GET /povs?action=new should return 200");
        conn.disconnect();
    }

    @Test
    void getCreateForm_containsFormFields() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=new");
        String body = readResponseBody(conn);

        assertTrue(body.contains("method=\"post\""),
                "Form should use POST method");
        assertTrue(body.contains("name=\"name\""),
                "Form should have name field");
        assertTrue(body.contains("name=\"accountName\""),
                "Form should have accountName field");
        assertTrue(body.contains("name=\"scName\""),
                "Form should have scName field");
        assertTrue(body.contains("name=\"status\""),
                "Form should have status field");
        assertTrue(body.contains("name=\"startDate\""),
                "Form should have startDate field");
        assertTrue(body.contains("name=\"targetEndDate\""),
                "Form should have targetEndDate field");
        assertTrue(body.contains("name=\"description\""),
                "Form should have description field");

        conn.disconnect();
    }

    @Test
    void getCreateForm_containsCorrectInputTypes() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=new");
        String body = readResponseBody(conn);

        assertTrue(body.contains("type=\"text\""),
                "Form should have text inputs");
        assertTrue(body.contains("type=\"date\""),
                "Form should have date inputs");
        assertTrue(body.contains("<select"),
                "Form should have select dropdown for status");
        assertTrue(body.contains("<textarea"),
                "Form should have textarea for description");

        conn.disconnect();
    }

    @Test
    void getCreateForm_statusDropdownHas6Options() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=new");
        String body = readResponseBody(conn);

        // Check all 6 status options are present
        assertTrue(body.contains("value=\"PLANNED\""), "Dropdown should have PLANNED option value");
        assertTrue(body.contains("value=\"IN_PROGRESS\""), "Dropdown should have IN_PROGRESS option value");
        assertTrue(body.contains("value=\"COMPLETED\""), "Dropdown should have COMPLETED option value");
        assertTrue(body.contains("value=\"WON\""), "Dropdown should have WON option value");
        assertTrue(body.contains("value=\"LOST\""), "Dropdown should have LOST option value");
        assertTrue(body.contains("value=\"CANCELLED\""), "Dropdown should have CANCELLED option value");

        // Check display names
        assertTrue(body.contains("Planned"), "Dropdown should show 'Planned'");
        assertTrue(body.contains("In Progress"), "Dropdown should show 'In Progress'");
        assertTrue(body.contains("Completed"), "Dropdown should show 'Completed'");
        assertTrue(body.contains("Won"), "Dropdown should show 'Won'");
        assertTrue(body.contains("Lost"), "Dropdown should show 'Lost'");
        assertTrue(body.contains("Cancelled"), "Dropdown should show 'Cancelled'");

        conn.disconnect();
    }

    @Test
    void getCreateForm_statusDefaultsToPLANNED() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=new");
        String body = readResponseBody(conn);

        // The PLANNED option should be selected by default
        // Check for the pattern: value="PLANNED" ... selected
        assertTrue(body.contains("value=\"PLANNED\"") && body.contains("selected"),
                "Status dropdown should default to PLANNED (selected)");

        conn.disconnect();
    }

    @Test
    void getCreateForm_showsNewPovTitle() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=new");
        String body = readResponseBody(conn);

        assertTrue(body.contains("New POV"),
                "Create form should have 'New POV' title");

        conn.disconnect();
    }

    @Test
    void getCreateForm_containsCancelLink() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=new");
        String body = readResponseBody(conn);

        assertTrue(body.contains("/povs") && body.contains("Cancel"),
                "Form should have a Cancel link back to the list page");

        conn.disconnect();
    }

    @Test
    void getCreateForm_containsSaveButton() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=new");
        String body = readResponseBody(conn);

        assertTrue(body.contains("Save POV"),
                "Create form should show 'Save POV' button text");

        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // POST /povs (create) tests — valid data
    // -------------------------------------------------------------------------

    @Test
    void postCreate_validData_redirectsToList() throws Exception {
        String formData = "name=Test+POV&accountName=Acme+Corp&scName=Alice&status=PLANNED"
                + "&startDate=2026-04-01&targetEndDate=2026-06-30&description=Test+POV+description";
        HttpURLConnection conn = openPostConnection("/povs", formData);

        int statusCode = conn.getResponseCode();
        assertEquals(302, statusCode, "POST with valid data should redirect (302)");

        String location = conn.getHeaderField("Location");
        assertNotNull(location, "Redirect should have a Location header");
        assertTrue(location.endsWith("/povs"),
                "Should redirect to /povs, got: " + location);

        conn.disconnect();
    }

    @Test
    void postCreate_validData_persistsInDatabase() throws Exception {
        String formData = "name=Alpha+POV&accountName=AlphaCo&scName=Bob&status=IN_PROGRESS"
                + "&startDate=2026-05-01&targetEndDate=2026-08-15&description=Alpha+description";
        HttpURLConnection conn = openPostConnection("/povs", formData);
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        // Verify the POV was persisted
        List<Pov> povs = dao.findAll();
        assertFalse(povs.isEmpty(), "POV should be persisted after successful POST");

        Pov created = povs.stream()
                .filter(p -> "Alpha POV".equals(p.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(created, "POV with name='Alpha POV' should exist");
        assertEquals("AlphaCo", created.getAccountName());
        assertEquals("Bob", created.getScName());
        assertEquals("IN_PROGRESS", created.getStatus());
        assertEquals(LocalDate.of(2026, 5, 1), created.getStartDate());
        assertEquals(LocalDate.of(2026, 8, 15), created.getTargetEndDate());
        assertEquals("Alpha description", created.getDescription());
    }

    @Test
    void postCreate_optionalDescriptionEmpty_succeeds() throws Exception {
        String formData = "name=Simple+POV&accountName=Co&scName=SC&status=PLANNED"
                + "&startDate=2026-04-01&targetEndDate=2026-05-01&description=";
        HttpURLConnection conn = openPostConnection("/povs", formData);

        assertEquals(302, conn.getResponseCode(),
                "POST with empty description should succeed (description is optional)");
        conn.disconnect();
    }

    @Test
    void postCreate_sameDatesValid_succeeds() throws Exception {
        String formData = "name=Short+POV&accountName=Co&scName=SC&status=PLANNED"
                + "&startDate=2026-04-01&targetEndDate=2026-04-01&description=";
        HttpURLConnection conn = openPostConnection("/povs", formData);

        assertEquals(302, conn.getResponseCode(),
                "POST with start date == target end date should succeed");
        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // POST /povs (create) tests — validation failures
    // -------------------------------------------------------------------------

    @Test
    void postCreate_missingName_showsError() throws Exception {
        String formData = "name=&accountName=Acme&scName=Alice&status=PLANNED"
                + "&startDate=2026-04-01&targetEndDate=2026-05-01&description=test";
        HttpURLConnection conn = openPostConnection("/povs", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Name is required"),
                "Should show 'Name is required' error");

        conn.disconnect();
    }

    @Test
    void postCreate_missingAccountName_showsError() throws Exception {
        String formData = "name=Test&accountName=&scName=Alice&status=PLANNED"
                + "&startDate=2026-04-01&targetEndDate=2026-05-01&description=test";
        HttpURLConnection conn = openPostConnection("/povs", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Account Name is required"),
                "Should show 'Account Name is required' error");

        conn.disconnect();
    }

    @Test
    void postCreate_missingScName_showsError() throws Exception {
        String formData = "name=Test&accountName=Acme&scName=&status=PLANNED"
                + "&startDate=2026-04-01&targetEndDate=2026-05-01&description=test";
        HttpURLConnection conn = openPostConnection("/povs", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("SC Name is required"),
                "Should show 'SC Name is required' error");

        conn.disconnect();
    }

    @Test
    void postCreate_missingStartDate_showsError() throws Exception {
        String formData = "name=Test&accountName=Acme&scName=Alice&status=PLANNED"
                + "&startDate=&targetEndDate=2026-05-01&description=test";
        HttpURLConnection conn = openPostConnection("/povs", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Start Date is required"),
                "Should show 'Start Date is required' error");

        conn.disconnect();
    }

    @Test
    void postCreate_missingTargetEndDate_showsError() throws Exception {
        String formData = "name=Test&accountName=Acme&scName=Alice&status=PLANNED"
                + "&startDate=2026-04-01&targetEndDate=&description=test";
        HttpURLConnection conn = openPostConnection("/povs", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Target End Date is required"),
                "Should show 'Target End Date is required' error");

        conn.disconnect();
    }

    @Test
    void postCreate_allRequiredFieldsMissing_showsMultipleErrors() throws Exception {
        String formData = "name=&accountName=&scName=&status=PLANNED"
                + "&startDate=&targetEndDate=&description=";
        HttpURLConnection conn = openPostConnection("/povs", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Name is required"), "Should list Name error");
        assertTrue(body.contains("Account Name is required"), "Should list Account Name error");
        assertTrue(body.contains("SC Name is required"), "Should list SC Name error");
        assertTrue(body.contains("Start Date is required"), "Should list Start Date error");
        assertTrue(body.contains("Target End Date is required"), "Should list Target End Date error");

        // Verify no POV was created
        List<Pov> povs = dao.findAll();
        assertTrue(povs.isEmpty(), "No POV should be created when required fields are missing");

        conn.disconnect();
    }

    @Test
    void postCreate_targetEndDateBeforeStartDate_showsError() throws Exception {
        String formData = "name=Test&accountName=Acme&scName=Alice&status=PLANNED"
                + "&startDate=2026-06-01&targetEndDate=2026-04-01&description=test";
        HttpURLConnection conn = openPostConnection("/povs", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Target End Date must not be before Start Date"),
                "Should show date validation error");

        // Verify no POV was created
        List<Pov> povs = dao.findAll();
        assertTrue(povs.isEmpty(), "No POV should be created when target end date is before start date");

        conn.disconnect();
    }

    @Test
    void postCreate_validationFailure_preservesEnteredValues() throws Exception {
        String formData = "name=My+Great+POV&accountName=&scName=Alice+Johnson&status=IN_PROGRESS"
                + "&startDate=2026-04-01&targetEndDate=2026-06-30&description=Some+notes";
        HttpURLConnection conn = openPostConnection("/povs", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        // Previously entered values should be preserved
        assertTrue(body.contains("My Great POV"),
                "Name value should be preserved after validation failure");
        assertTrue(body.contains("Alice Johnson"),
                "SC Name value should be preserved after validation failure");
        assertTrue(body.contains("2026-04-01"),
                "Start Date value should be preserved after validation failure");
        assertTrue(body.contains("2026-06-30"),
                "Target End Date value should be preserved after validation failure");
        assertTrue(body.contains("Some notes"),
                "Description value should be preserved after validation failure");
        // Status should be selected
        assertTrue(body.contains("IN_PROGRESS") && body.contains("selected"),
                "Status selection should be preserved after validation failure");

        conn.disconnect();
    }

    @Test
    void postCreate_validationFailure_showsFormWithDropdown() throws Exception {
        String formData = "name=&accountName=&scName=&status=PLANNED"
                + "&startDate=&targetEndDate=&description=";
        HttpURLConnection conn = openPostConnection("/povs", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        // Form should still have the status dropdown with all 6 options
        assertTrue(body.contains("value=\"PLANNED\""), "Dropdown should still have PLANNED");
        assertTrue(body.contains("value=\"WON\""), "Dropdown should still have WON");
        assertTrue(body.contains("value=\"CANCELLED\""), "Dropdown should still have CANCELLED");
        assertTrue(body.contains("<form"), "Should re-render the form");
        assertTrue(body.contains("method=\"post\""), "Form should use POST method");

        conn.disconnect();
    }

    @Test
    void postCreate_xssInFormValues_escapedOnRerender() throws Exception {
        String formData = "name=%3Cscript%3Ealert(1)%3C%2Fscript%3E&accountName=&scName=%3Cimg+onerror%3Dalert(1)%3E"
                + "&status=PLANNED&startDate=2026-04-01&targetEndDate=2026-05-01&description=%3Cb%3Ebold%3C%2Fb%3E";
        HttpURLConnection conn = openPostConnection("/povs", formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        assertFalse(body.contains("<script>alert(1)</script>"),
                "Script tags should be escaped in form re-render");
        assertFalse(body.contains("<img onerror=alert(1)>"),
                "Image tags should be escaped in form re-render");

        conn.disconnect();
    }

    @Test
    void postCreate_validData_appearsInList() throws Exception {
        // Create a POV
        String formData = "name=Visible+POV&accountName=VisibleCo&scName=SC+Visible&status=WON"
                + "&startDate=2026-04-01&targetEndDate=2026-06-30&description=";
        HttpURLConnection conn = openPostConnection("/povs", formData);
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        // Now verify it appears in the list
        HttpURLConnection listConn = openConnection("/povs");
        assertEquals(200, listConn.getResponseCode());
        String body = readResponseBody(listConn);

        assertTrue(body.contains("Visible POV"), "Created POV should appear in list");
        assertTrue(body.contains("VisibleCo"), "Account name should appear in list");
        assertTrue(body.contains("SC Visible"), "SC name should appear in list");
        assertTrue(body.contains("badge-won"), "WON status badge should be displayed");

        listConn.disconnect();
    }

    // -------------------------------------------------------------------------
    // Helper: create a sample PovCriteria
    // -------------------------------------------------------------------------

    private PovCriteria createSampleCriteria(long povId, String name, String status,
                                              int weight, String notes) {
        PovCriteria c = new PovCriteria();
        c.setPovId(povId);
        c.setName(name);
        c.setDescription("Test criterion description");
        c.setStatus(status);
        c.setWeight(weight);
        c.setNotes(notes);
        return c;
    }

    // -------------------------------------------------------------------------
    // GET /povs?action=detail&id=N (detail page) tests
    // -------------------------------------------------------------------------

    @Test
    void getDetail_validId_returns200() throws Exception {
        Pov pov = createSamplePov("Detail POV", "Acme", "Alice",
                "IN_PROGRESS", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                "A test POV for detail.");
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=detail&id=" + pov.getId());
        assertEquals(200, conn.getResponseCode(), "GET detail should return 200");
        conn.disconnect();
    }

    @Test
    void getDetail_showsAllPovFields() throws Exception {
        Pov pov = createSamplePov("Full Detail POV", "Acme Corp", "Alice Johnson",
                "IN_PROGRESS", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                "A comprehensive test POV.");
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=detail&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Full Detail POV"), "Name should be displayed");
        assertTrue(body.contains("Acme Corp"), "Account Name should be displayed");
        assertTrue(body.contains("Alice Johnson"), "SC Name should be displayed");
        assertTrue(body.contains("2026-04-01"), "Start Date should be displayed");
        assertTrue(body.contains("2026-06-30"), "Target End Date should be displayed");
        assertTrue(body.contains("A comprehensive test POV."), "Description should be displayed");

        conn.disconnect();
    }

    @Test
    void getDetail_showsStatusBadge() throws Exception {
        Pov pov = createSamplePov("Badge POV", "Co", "SC",
                "IN_PROGRESS", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=detail&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("badge-in-progress"),
                "Detail page should display status badge with correct CSS class");
        assertTrue(body.contains("In Progress"),
                "Detail page should display human-readable status name");

        conn.disconnect();
    }

    @Test
    void getDetail_showsEditAndDeleteButtons() throws Exception {
        Pov pov = createSamplePov("Actions POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=detail&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Edit POV"), "Detail page should have Edit POV button");
        assertTrue(body.contains("Delete POV"), "Detail page should have Delete POV button");
        assertTrue(body.contains("action=edit&amp;id=" + pov.getId()),
                "Edit link should include the POV id");
        assertTrue(body.contains("action=delete&amp;id=" + pov.getId()),
                "Delete form should include the POV id");

        conn.disconnect();
    }

    @Test
    void getDetail_showsBackToListLink() throws Exception {
        Pov pov = createSamplePov("Back Link POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=detail&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Back to POV List") || body.contains("/povs\""),
                "Detail page should have a back to list link");

        conn.disconnect();
    }

    @Test
    void getDetail_noCriteria_showsEmptyState() throws Exception {
        Pov pov = createSamplePov("No Criteria POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=detail&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("No criteria defined yet."),
                "Detail page should show empty state when no criteria exist");

        conn.disconnect();
    }

    @Test
    void getDetail_withCriteria_showsCriteriaTable() throws Exception {
        Pov pov = createSamplePov("Criteria POV", "Co", "SC",
                "IN_PROGRESS", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 1), null);
        dao.create(pov);

        PovCriteria c1 = createSampleCriteria(pov.getId(), "Performance", "MET", 5, "Meets all benchmarks");
        criteriaDao.create(c1);
        PovCriteria c2 = createSampleCriteria(pov.getId(), "Security", "NOT_STARTED", 3, "Pending review");
        criteriaDao.create(c2);

        HttpURLConnection conn = openConnection("/povs?action=detail&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertFalse(body.contains("No criteria defined yet."),
                "Empty state should NOT be shown when criteria exist");
        assertTrue(body.contains("Performance"), "First criterion name should be displayed");
        assertTrue(body.contains("Security"), "Second criterion name should be displayed");
        assertTrue(body.contains("badge-met"), "MET status badge should be displayed");
        assertTrue(body.contains("badge-not-started"), "NOT_STARTED status badge should be displayed");
        assertTrue(body.contains("Meets all benchmarks"), "Notes should be displayed");
        assertTrue(body.contains("Pending review"), "Notes should be displayed");

        conn.disconnect();
    }

    @Test
    void getDetail_criteriaTable_hasCorrectColumns() throws Exception {
        Pov pov = createSamplePov("Columns POV", "Co", "SC",
                "IN_PROGRESS", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 1), null);
        dao.create(pov);

        PovCriteria c = createSampleCriteria(pov.getId(), "Test Criterion", "IN_PROGRESS", 4, "Some notes");
        criteriaDao.create(c);

        HttpURLConnection conn = openConnection("/povs?action=detail&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("<th>Name</th>") || body.contains(">Name<"),
                "Criteria table should have Name header");
        assertTrue(body.contains(">Status<"),
                "Criteria table should have Status header");
        assertTrue(body.contains(">Weight<"),
                "Criteria table should have Weight header");
        assertTrue(body.contains(">Notes<"),
                "Criteria table should have Notes header");
        assertTrue(body.contains(">Actions<"),
                "Criteria table should have Actions header");

        conn.disconnect();
    }

    @Test
    void getDetail_criteriaHasEditAndDeleteActions() throws Exception {
        Pov pov = createSamplePov("Criteria Actions POV", "Co", "SC",
                "IN_PROGRESS", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 1), null);
        dao.create(pov);

        PovCriteria c = createSampleCriteria(pov.getId(), "Actionable", "NOT_STARTED", 3, null);
        criteriaDao.create(c);

        HttpURLConnection conn = openConnection("/povs?action=detail&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("action=edit&amp;id=" + c.getId()),
                "Criteria should have edit link with criterion id");
        assertTrue(body.contains("action=delete&amp;id=" + c.getId()),
                "Criteria should have delete form with criterion id");

        conn.disconnect();
    }

    @Test
    void getDetail_showsAddCriteriaButton() throws Exception {
        Pov pov = createSamplePov("Add Criteria POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=detail&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Add Criteria"),
                "Detail page should have Add Criteria button");
        assertTrue(body.contains("povId=" + pov.getId()),
                "Add Criteria link should include the POV id");

        conn.disconnect();
    }

    @Test
    void getDetail_nonExistentId_returns404() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=detail&id=99999");
        assertEquals(404, conn.getResponseCode(),
                "GET detail with non-existent ID should return 404");
        conn.disconnect();
    }

    @Test
    void getDetail_invalidIdFormat_returns404() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=detail&id=abc");
        assertEquals(404, conn.getResponseCode(),
                "GET detail with non-numeric ID should return 404");
        conn.disconnect();
    }

    @Test
    void getDetail_missingId_returns404() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=detail");
        assertEquals(404, conn.getResponseCode(),
                "GET detail without ID parameter should return 404");
        conn.disconnect();
    }

    @Test
    void getDetail_nonExistentId_noStackTrace() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=detail&id=99999");
        assertEquals(404, conn.getResponseCode());
        String body = readErrorBody(conn);

        assertFalse(body.contains("java.lang."),
                "Error page should not contain Java class references");
        assertFalse(body.contains("NullPointerException"),
                "Error page should not contain NullPointerException");

        conn.disconnect();
    }

    @Test
    void getDetail_xssProtection() throws Exception {
        Pov pov = createSamplePov("<script>alert(1)</script>", "<img onerror=alert(1)>", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1),
                "<b>bold</b>");
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=detail&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertFalse(body.contains("<script>alert(1)</script>"),
                "Script tags should be escaped in detail page");
        assertFalse(body.contains("<img onerror=alert(1)>"),
                "Image tags should be escaped in detail page");

        conn.disconnect();
    }

    @Test
    void getDetail_deleteFormHasConfirmation() throws Exception {
        Pov pov = createSamplePov("Confirm POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=detail&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("confirm("),
                "Delete POV form should have JavaScript confirm dialog");
        assertTrue(body.contains("method=\"post\""),
                "Delete POV should use POST method");

        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // GET /povs?action=edit&id=N (edit form) tests
    // -------------------------------------------------------------------------

    @Test
    void getEditForm_validId_returns200() throws Exception {
        Pov pov = createSamplePov("Edit POV", "Acme", "Alice",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                "Edit me");
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=edit&id=" + pov.getId());
        assertEquals(200, conn.getResponseCode(), "GET edit form should return 200");
        conn.disconnect();
    }

    @Test
    void getEditForm_prePopulatesAllFields() throws Exception {
        Pov pov = createSamplePov("Edit All POV", "EditCo", "Edit SC",
                "IN_PROGRESS", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 8, 15),
                "Edit description");
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=edit&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Edit All POV"), "Name should be pre-populated");
        assertTrue(body.contains("EditCo"), "Account Name should be pre-populated");
        assertTrue(body.contains("Edit SC"), "SC Name should be pre-populated");
        assertTrue(body.contains("2026-05-01"), "Start Date should be pre-populated");
        assertTrue(body.contains("2026-08-15"), "Target End Date should be pre-populated");
        assertTrue(body.contains("Edit description"), "Description should be pre-populated");

        conn.disconnect();
    }

    @Test
    void getEditForm_selectsCorrectStatus() throws Exception {
        Pov pov = createSamplePov("Status POV", "Co", "SC",
                "COMPLETED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=edit&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("COMPLETED") && body.contains("selected"),
                "Status dropdown should have COMPLETED selected");

        conn.disconnect();
    }

    @Test
    void getEditForm_showsEditTitle() throws Exception {
        Pov pov = createSamplePov("Title POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=edit&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Edit POV"),
                "Edit form should have 'Edit POV' title");

        conn.disconnect();
    }

    @Test
    void getEditForm_showsUpdateButton() throws Exception {
        Pov pov = createSamplePov("Button POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=edit&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Update POV"),
                "Edit form should show 'Update POV' button text");

        conn.disconnect();
    }

    @Test
    void getEditForm_formPointsToEditAction() throws Exception {
        Pov pov = createSamplePov("Form POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openConnection("/povs?action=edit&id=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("action=edit"),
                "Form action should include action=edit");
        assertTrue(body.contains("id=" + pov.getId()),
                "Form action should include the POV ID");
        assertTrue(body.contains("method=\"post\""),
                "Form should use POST method");

        conn.disconnect();
    }

    @Test
    void getEditForm_nonExistentId_returns404() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=edit&id=99999");
        assertEquals(404, conn.getResponseCode(),
                "GET edit form with non-existent ID should return 404");
        conn.disconnect();
    }

    @Test
    void getEditForm_invalidIdFormat_returns404() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=edit&id=abc");
        assertEquals(404, conn.getResponseCode(),
                "GET edit form with non-numeric ID should return 404");
        conn.disconnect();
    }

    @Test
    void getEditForm_missingId_returns404() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=edit");
        assertEquals(404, conn.getResponseCode(),
                "GET edit form without ID parameter should return 404");
        conn.disconnect();
    }

    @Test
    void getEditForm_nonExistentId_noStackTrace() throws Exception {
        HttpURLConnection conn = openConnection("/povs?action=edit&id=99999");
        assertEquals(404, conn.getResponseCode());
        String body = readErrorBody(conn);

        assertFalse(body.contains("java.lang."),
                "Error page should not contain Java class references");
        assertFalse(body.contains("NullPointerException"),
                "Error page should not contain NullPointerException");

        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // POST /povs?action=edit&id=N (edit/update) tests
    // -------------------------------------------------------------------------

    @Test
    void postEdit_validData_redirectsToDetail() throws Exception {
        Pov pov = createSamplePov("Original POV", "OldCo", "OldSC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), "Old desc");
        dao.create(pov);

        String formData = "name=Updated+POV&accountName=NewCo&scName=NewSC&status=IN_PROGRESS"
                + "&startDate=2026-04-15&targetEndDate=2026-07-15&description=New+desc";
        HttpURLConnection conn = openPostConnection(
                "/povs?action=edit&id=" + pov.getId(), formData);

        assertEquals(302, conn.getResponseCode(),
                "POST edit with valid data should redirect (302)");

        String location = conn.getHeaderField("Location");
        assertNotNull(location, "Redirect should have a Location header");
        assertTrue(location.contains("action=detail") && location.contains("id=" + pov.getId()),
                "Should redirect to detail page, got: " + location);

        conn.disconnect();
    }

    @Test
    void postEdit_validData_updatesInDatabase() throws Exception {
        Pov pov = createSamplePov("Original POV", "OldCo", "OldSC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), "Old desc");
        dao.create(pov);
        long id = pov.getId();

        String formData = "name=Updated+Name&accountName=UpdatedCo&scName=UpdatedSC&status=COMPLETED"
                + "&startDate=2026-05-01&targetEndDate=2026-08-01&description=Updated+desc";
        HttpURLConnection conn = openPostConnection(
                "/povs?action=edit&id=" + id, formData);
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        // Verify the POV was updated
        Pov updated = dao.findById(id);
        assertNotNull(updated, "POV should still exist after update");
        assertEquals("Updated Name", updated.getName());
        assertEquals("UpdatedCo", updated.getAccountName());
        assertEquals("UpdatedSC", updated.getScName());
        assertEquals("COMPLETED", updated.getStatus());
        assertEquals(LocalDate.of(2026, 5, 1), updated.getStartDate());
        assertEquals(LocalDate.of(2026, 8, 1), updated.getTargetEndDate());
        assertEquals("Updated desc", updated.getDescription());
    }

    @Test
    void postEdit_statusChange_persists() throws Exception {
        Pov pov = createSamplePov("Status Change POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);
        long id = pov.getId();

        // Change status from PLANNED to WON
        String formData = "name=Status+Change+POV&accountName=Co&scName=SC&status=WON"
                + "&startDate=2026-04-01&targetEndDate=2026-05-01&description=";
        HttpURLConnection conn = openPostConnection(
                "/povs?action=edit&id=" + id, formData);
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        Pov updated = dao.findById(id);
        assertEquals("WON", updated.getStatus(),
                "Status should be updated to WON");
    }

    @Test
    void postEdit_validData_doesNotCreateDuplicate() throws Exception {
        Pov pov = createSamplePov("No Dup POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        int countBefore = dao.findAll().size();

        String formData = "name=Updated+No+Dup&accountName=Co&scName=SC&status=PLANNED"
                + "&startDate=2026-04-01&targetEndDate=2026-05-01&description=";
        HttpURLConnection conn = openPostConnection(
                "/povs?action=edit&id=" + pov.getId(), formData);
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        int countAfter = dao.findAll().size();
        assertEquals(countBefore, countAfter,
                "Edit should not create a duplicate POV; count should remain the same");
    }

    @Test
    void postEdit_invalidData_returnsFormWithErrors() throws Exception {
        Pov pov = createSamplePov("Error POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        // Submit with missing name (invalid)
        String formData = "name=&accountName=Co&scName=SC&status=PLANNED"
                + "&startDate=2026-04-01&targetEndDate=2026-05-01&description=";
        HttpURLConnection conn = openPostConnection(
                "/povs?action=edit&id=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode(),
                "POST edit with invalid data should return 200 (re-render form)");
        String body = readResponseBody(conn);
        assertTrue(body.contains("Name is required"),
                "Should show validation error for missing name");

        conn.disconnect();
    }

    @Test
    void postEdit_dateValidation_showsError() throws Exception {
        Pov pov = createSamplePov("Date POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        // Submit with target end date before start date
        String formData = "name=Date+POV&accountName=Co&scName=SC&status=PLANNED"
                + "&startDate=2026-06-01&targetEndDate=2026-04-01&description=";
        HttpURLConnection conn = openPostConnection(
                "/povs?action=edit&id=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Target End Date must not be before Start Date"),
                "Should show date validation error");

        conn.disconnect();
    }

    @Test
    void postEdit_invalidData_preservesInput() throws Exception {
        Pov pov = createSamplePov("Preserve POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        String formData = "name=&accountName=NewCo&scName=NewSC&status=COMPLETED"
                + "&startDate=2026-05-01&targetEndDate=2026-08-01&description=Preserved+desc";
        HttpURLConnection conn = openPostConnection(
                "/povs?action=edit&id=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        assertTrue(body.contains("NewCo"), "Account Name should be preserved");
        assertTrue(body.contains("NewSC"), "SC Name should be preserved");
        assertTrue(body.contains("2026-05-01"), "Start Date should be preserved");
        assertTrue(body.contains("2026-08-01"), "Target End Date should be preserved");
        assertTrue(body.contains("Preserved desc"), "Description should be preserved");
        assertTrue(body.contains("COMPLETED") && body.contains("selected"),
                "Status selection should be preserved");

        conn.disconnect();
    }

    @Test
    void postEdit_invalidData_doesNotModifyPov() throws Exception {
        Pov pov = createSamplePov("NoMod POV", "OrigCo", "OrigSC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), "Original desc");
        dao.create(pov);
        long id = pov.getId();

        // Submit with missing name (invalid)
        String formData = "name=&accountName=Changed&scName=Changed&status=WON"
                + "&startDate=2026-06-01&targetEndDate=2026-09-01&description=Changed";
        HttpURLConnection conn = openPostConnection(
                "/povs?action=edit&id=" + id, formData);
        assertEquals(200, conn.getResponseCode());
        conn.disconnect();

        // Verify the original POV is unchanged
        Pov unchanged = dao.findById(id);
        assertEquals("NoMod POV", unchanged.getName(), "Name should not have changed");
        assertEquals("OrigCo", unchanged.getAccountName(), "Account Name should not have changed");
        assertEquals("PLANNED", unchanged.getStatus(), "Status should not have changed");
    }

    @Test
    void postEdit_nonExistentId_returns404() throws Exception {
        String formData = "name=Test&accountName=Co&scName=SC&status=PLANNED"
                + "&startDate=2026-04-01&targetEndDate=2026-05-01&description=";
        HttpURLConnection conn = openPostConnection(
                "/povs?action=edit&id=99999", formData);

        assertEquals(404, conn.getResponseCode(),
                "POST edit with non-existent ID should return 404");

        conn.disconnect();
    }

    @Test
    void postEdit_invalidIdFormat_returns404() throws Exception {
        String formData = "name=Test&accountName=Co&scName=SC&status=PLANNED"
                + "&startDate=2026-04-01&targetEndDate=2026-05-01&description=";
        HttpURLConnection conn = openPostConnection(
                "/povs?action=edit&id=abc", formData);

        assertEquals(404, conn.getResponseCode(),
                "POST edit with non-numeric ID should return 404");

        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // POST /povs?action=delete&id=N (delete) tests
    // -------------------------------------------------------------------------

    @Test
    void postDelete_validId_redirectsToList() throws Exception {
        Pov pov = createSamplePov("Delete POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        HttpURLConnection conn = openPostConnection(
                "/povs?action=delete&id=" + pov.getId(), "");

        assertEquals(302, conn.getResponseCode(),
                "POST delete should redirect (302)");

        String location = conn.getHeaderField("Location");
        assertNotNull(location);
        assertTrue(location.endsWith("/povs"),
                "Should redirect to /povs, got: " + location);

        conn.disconnect();
    }

    @Test
    void postDelete_validId_removesPovFromDatabase() throws Exception {
        Pov pov = createSamplePov("Gone POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);
        long id = pov.getId();

        HttpURLConnection conn = openPostConnection(
                "/povs?action=delete&id=" + id, "");
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        assertNull(dao.findById(id), "POV should be removed after delete");
    }

    @Test
    void postDelete_cascadesToCriteria() throws Exception {
        Pov pov = createSamplePov("Cascade POV", "Co", "SC",
                "IN_PROGRESS", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 1), null);
        dao.create(pov);
        long povId = pov.getId();

        // Create criteria for this POV
        PovCriteria c1 = createSampleCriteria(povId, "Criterion 1", "MET", 5, "Notes 1");
        criteriaDao.create(c1);
        PovCriteria c2 = createSampleCriteria(povId, "Criterion 2", "NOT_STARTED", 3, "Notes 2");
        criteriaDao.create(c2);

        // Verify criteria exist before delete
        List<PovCriteria> before = criteriaDao.findByPovId(povId);
        assertEquals(2, before.size(), "Should have 2 criteria before delete");

        // Delete the POV
        HttpURLConnection conn = openPostConnection(
                "/povs?action=delete&id=" + povId, "");
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        // Verify POV is gone
        assertNull(dao.findById(povId), "POV should be removed");

        // Verify criteria are also gone (cascade delete)
        List<PovCriteria> after = criteriaDao.findByPovId(povId);
        assertTrue(after.isEmpty(), "Criteria should be cascade-deleted when POV is deleted");
    }

    @Test
    void postDelete_onlyRemovesTargetPov() throws Exception {
        Pov pov1 = createSamplePov("POV Keep", "Co1", "SC1",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov1);
        Pov pov2 = createSamplePov("POV Delete", "Co2", "SC2",
                "IN_PROGRESS", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov2);

        HttpURLConnection conn = openPostConnection(
                "/povs?action=delete&id=" + pov2.getId(), "");
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        assertNotNull(dao.findById(pov1.getId()), "Other POV should still exist");
        assertNull(dao.findById(pov2.getId()), "Deleted POV should be removed");
    }

    @Test
    void postDelete_nonExistentId_returns404() throws Exception {
        HttpURLConnection conn = openPostConnection(
                "/povs?action=delete&id=99999", "");

        assertEquals(404, conn.getResponseCode(),
                "POST delete with non-existent ID should return 404");

        conn.disconnect();
    }

    @Test
    void postDelete_invalidIdFormat_returns404() throws Exception {
        HttpURLConnection conn = openPostConnection(
                "/povs?action=delete&id=abc", "");

        assertEquals(404, conn.getResponseCode(),
                "POST delete with non-numeric ID should return 404");

        conn.disconnect();
    }

    @Test
    void postDelete_missingId_returns404() throws Exception {
        HttpURLConnection conn = openPostConnection(
                "/povs?action=delete", "");

        assertEquals(404, conn.getResponseCode(),
                "POST delete without ID parameter should return 404");

        conn.disconnect();
    }

    @Test
    void postDelete_nonExistentId_noStackTrace() throws Exception {
        HttpURLConnection conn = openPostConnection(
                "/povs?action=delete&id=99999", "");

        assertEquals(404, conn.getResponseCode());
        String body = readErrorBody(conn);
        assertFalse(body.contains("java.lang."),
                "Error response should not contain Java class references");
        assertFalse(body.contains("NullPointerException"),
                "Error response should not contain NullPointerException");

        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // Integration: edit, then verify in detail/list
    // -------------------------------------------------------------------------

    @Test
    void editPov_changesVisibleInDetailPage() throws Exception {
        Pov pov = createSamplePov("Before Edit", "OldCo", "OldSC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), "Old desc");
        dao.create(pov);

        // Edit the POV
        String formData = "name=After+Edit&accountName=NewCo&scName=NewSC&status=COMPLETED"
                + "&startDate=2026-05-01&targetEndDate=2026-08-01&description=New+desc";
        HttpURLConnection editConn = openPostConnection(
                "/povs?action=edit&id=" + pov.getId(), formData);
        assertEquals(302, editConn.getResponseCode());
        editConn.disconnect();

        // Verify changes in detail page
        HttpURLConnection detailConn = openConnection("/povs?action=detail&id=" + pov.getId());
        assertEquals(200, detailConn.getResponseCode());
        String body = readResponseBody(detailConn);

        assertTrue(body.contains("After Edit"), "Updated name should be visible in detail");
        assertTrue(body.contains("NewCo"), "Updated account name should be visible");
        assertTrue(body.contains("NewSC"), "Updated SC name should be visible");
        assertTrue(body.contains("badge-completed"), "Updated status badge should be visible");
        assertTrue(body.contains("New desc"), "Updated description should be visible");

        detailConn.disconnect();
    }

    @Test
    void deletePov_removedFromList() throws Exception {
        Pov pov = createSamplePov("Removed POV", "Co", "SC",
                "PLANNED", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null);
        dao.create(pov);

        // Delete the POV
        HttpURLConnection deleteConn = openPostConnection(
                "/povs?action=delete&id=" + pov.getId(), "");
        assertEquals(302, deleteConn.getResponseCode());
        deleteConn.disconnect();

        // Verify POV is no longer in the list
        HttpURLConnection listConn = openConnection("/povs");
        assertEquals(200, listConn.getResponseCode());
        String body = readResponseBody(listConn);

        assertFalse(body.contains("Removed POV"),
                "Deleted POV should not appear in the list");

        listConn.disconnect();
    }
}
