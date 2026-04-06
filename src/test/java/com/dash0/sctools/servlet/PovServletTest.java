package com.dash0.sctools.servlet;

import com.dash0.sctools.Application;
import com.dash0.sctools.dao.PovDao;
import com.dash0.sctools.model.Pov;
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

    @BeforeEach
    void setUp() throws Exception {
        // Initialize the production database (creates tables if needed)
        DatabaseInitializer.initialize();

        // Use the production DAO (same as the servlet will use)
        dao = new PovDao();

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
}
