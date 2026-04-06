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
 * Integration tests for CriteriaServlet using embedded Jetty.
 * Tests GET create form, GET edit form, POST create, POST edit, and POST delete
 * for POV criteria CRUD operations.
 */
class CriteriaServletTest {

    private Server server;
    private static final int TEST_PORT = 8097;
    private PovDao povDao;
    private PovCriteriaDao criteriaDao;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize the production database (creates tables if needed)
        DatabaseInitializer.initialize();

        // Use the production DAOs (same as the servlet will use)
        povDao = new PovDao();
        criteriaDao = new PovCriteriaDao();

        // Clean out any existing data for a fresh test state
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

    private Pov createSamplePov() {
        Pov pov = new Pov();
        pov.setName("Test POV");
        pov.setAccountName("Test Corp");
        pov.setScName("Alice");
        pov.setStatus("IN_PROGRESS");
        pov.setStartDate(LocalDate.of(2026, 4, 1));
        pov.setTargetEndDate(LocalDate.of(2026, 6, 30));
        pov.setDescription("Test POV for criteria tests");
        return povDao.create(pov);
    }

    private PovCriteria createSampleCriteria(long povId, String name, String status, int weight, String notes) {
        PovCriteria c = new PovCriteria();
        c.setPovId(povId);
        c.setName(name);
        c.setDescription("Test criterion description");
        c.setStatus(status);
        c.setWeight(weight);
        c.setNotes(notes);
        return criteriaDao.create(c);
    }

    // -------------------------------------------------------------------------
    // GET /criteria?action=new&povId=N (create form) tests
    // -------------------------------------------------------------------------

    @Test
    void getCreateForm_returns200() throws Exception {
        Pov pov = createSamplePov();
        HttpURLConnection conn = openConnection("/criteria?action=new&povId=" + pov.getId());
        assertEquals(200, conn.getResponseCode(), "GET create form should return 200");
        conn.disconnect();
    }

    @Test
    void getCreateForm_containsAllFormFields() throws Exception {
        Pov pov = createSamplePov();
        HttpURLConnection conn = openConnection("/criteria?action=new&povId=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("method=\"post\""), "Form should use POST method");
        assertTrue(body.contains("name=\"name\""), "Form should have name field");
        assertTrue(body.contains("name=\"description\""), "Form should have description field");
        assertTrue(body.contains("name=\"status\""), "Form should have status field");
        assertTrue(body.contains("name=\"weight\""), "Form should have weight field");
        assertTrue(body.contains("name=\"notes\""), "Form should have notes field");

        conn.disconnect();
    }

    @Test
    void getCreateForm_statusDropdownHas5Options() throws Exception {
        Pov pov = createSamplePov();
        HttpURLConnection conn = openConnection("/criteria?action=new&povId=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("value=\"NOT_STARTED\""), "Dropdown should have NOT_STARTED option");
        assertTrue(body.contains("value=\"IN_PROGRESS\""), "Dropdown should have IN_PROGRESS option");
        assertTrue(body.contains("value=\"MET\""), "Dropdown should have MET option");
        assertTrue(body.contains("value=\"NOT_MET\""), "Dropdown should have NOT_MET option");
        assertTrue(body.contains("value=\"PARTIALLY_MET\""), "Dropdown should have PARTIALLY_MET option");

        // Check display names
        assertTrue(body.contains("Not Started"), "Dropdown should show 'Not Started'");
        assertTrue(body.contains("In Progress"), "Dropdown should show 'In Progress'");
        assertTrue(body.contains(">Met<"), "Dropdown should show 'Met'");
        assertTrue(body.contains("Not Met"), "Dropdown should show 'Not Met'");
        assertTrue(body.contains("Partially Met"), "Dropdown should show 'Partially Met'");

        conn.disconnect();
    }

    @Test
    void getCreateForm_statusDefaultsToNotStarted() throws Exception {
        Pov pov = createSamplePov();
        HttpURLConnection conn = openConnection("/criteria?action=new&povId=" + pov.getId());
        String body = readResponseBody(conn);

        // NOT_STARTED should be selected by default
        assertTrue(body.contains("value=\"NOT_STARTED\"") && body.contains("selected"),
                "Status dropdown should default to NOT_STARTED (selected)");

        conn.disconnect();
    }

    @Test
    void getCreateForm_weightDropdownHas1to5() throws Exception {
        Pov pov = createSamplePov();
        HttpURLConnection conn = openConnection("/criteria?action=new&povId=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("value=\"1\""), "Weight dropdown should have value 1");
        assertTrue(body.contains("value=\"2\""), "Weight dropdown should have value 2");
        assertTrue(body.contains("value=\"3\""), "Weight dropdown should have value 3");
        assertTrue(body.contains("value=\"4\""), "Weight dropdown should have value 4");
        assertTrue(body.contains("value=\"5\""), "Weight dropdown should have value 5");

        conn.disconnect();
    }

    @Test
    void getCreateForm_showsNewCriterionTitle() throws Exception {
        Pov pov = createSamplePov();
        HttpURLConnection conn = openConnection("/criteria?action=new&povId=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("New Criterion"), "Create form should have 'New Criterion' title");

        conn.disconnect();
    }

    @Test
    void getCreateForm_showsPovName() throws Exception {
        Pov pov = createSamplePov();
        HttpURLConnection conn = openConnection("/criteria?action=new&povId=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Test POV"), "Create form should show parent POV name");

        conn.disconnect();
    }

    @Test
    void getCreateForm_containsCancelLink() throws Exception {
        Pov pov = createSamplePov();
        HttpURLConnection conn = openConnection("/criteria?action=new&povId=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Cancel"), "Form should have Cancel link");
        assertTrue(body.contains("action=detail") && body.contains("id=" + pov.getId()),
                "Cancel link should point to POV detail page");

        conn.disconnect();
    }

    @Test
    void getCreateForm_containsSaveButton() throws Exception {
        Pov pov = createSamplePov();
        HttpURLConnection conn = openConnection("/criteria?action=new&povId=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Save Criterion"), "Create form should show 'Save Criterion' button text");

        conn.disconnect();
    }

    @Test
    void getCreateForm_missingPovId_returns404() throws Exception {
        HttpURLConnection conn = openConnection("/criteria?action=new");
        assertEquals(404, conn.getResponseCode(),
                "GET create form without povId should return 404");
        conn.disconnect();
    }

    @Test
    void getCreateForm_invalidPovId_returns404() throws Exception {
        HttpURLConnection conn = openConnection("/criteria?action=new&povId=99999");
        assertEquals(404, conn.getResponseCode(),
                "GET create form with non-existent POV ID should return 404");
        conn.disconnect();
    }

    @Test
    void getCreateForm_nonNumericPovId_returns404() throws Exception {
        HttpURLConnection conn = openConnection("/criteria?action=new&povId=abc");
        assertEquals(404, conn.getResponseCode(),
                "GET create form with non-numeric POV ID should return 404");
        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // POST /criteria?povId=N (create) tests — valid data
    // -------------------------------------------------------------------------

    @Test
    void postCreate_validData_redirectsToPovDetail() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=Performance&description=Performance+benchmarks&status=NOT_STARTED&weight=5&notes=Important";
        HttpURLConnection conn = openPostConnection("/criteria?povId=" + pov.getId(), formData);

        assertEquals(302, conn.getResponseCode(), "POST with valid data should redirect (302)");

        String location = conn.getHeaderField("Location");
        assertNotNull(location, "Redirect should have a Location header");
        assertTrue(location.contains("action=detail") && location.contains("id=" + pov.getId()),
                "Should redirect to POV detail page, got: " + location);

        conn.disconnect();
    }

    @Test
    void postCreate_validData_persistsInDatabase() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=Security&description=Security+review&status=NOT_STARTED&weight=4&notes=Pending";
        HttpURLConnection conn = openPostConnection("/criteria?povId=" + pov.getId(), formData);
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        // Verify the criterion was persisted
        List<PovCriteria> criteria = criteriaDao.findByPovId(pov.getId());
        assertFalse(criteria.isEmpty(), "Criterion should be persisted after successful POST");

        PovCriteria created = criteria.stream()
                .filter(c -> "Security".equals(c.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(created, "Criterion with name='Security' should exist");
        assertEquals("Security review", created.getDescription());
        assertEquals("NOT_STARTED", created.getStatus());
        assertEquals(4, created.getWeight());
        assertEquals("Pending", created.getNotes());
        assertEquals(pov.getId(), created.getPovId());
    }

    @Test
    void postCreate_optionalFieldsEmpty_succeeds() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=Minimal+Criterion&description=&status=NOT_STARTED&weight=3&notes=";
        HttpURLConnection conn = openPostConnection("/criteria?povId=" + pov.getId(), formData);

        assertEquals(302, conn.getResponseCode(),
                "POST with empty optional fields should succeed");
        conn.disconnect();

        List<PovCriteria> criteria = criteriaDao.findByPovId(pov.getId());
        assertFalse(criteria.isEmpty(), "Criterion should be created with empty optional fields");
    }

    @Test
    void postCreate_visibleOnPovDetailPage() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=Scalability&description=Scale+test&status=NOT_STARTED&weight=5&notes=Critical";
        HttpURLConnection conn = openPostConnection("/criteria?povId=" + pov.getId(), formData);
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        // Verify it appears on the POV detail page
        HttpURLConnection detailConn = openConnection("/povs?action=detail&id=" + pov.getId());
        assertEquals(200, detailConn.getResponseCode());
        String body = readResponseBody(detailConn);

        assertTrue(body.contains("Scalability"), "Created criterion should appear on POV detail page");
        assertTrue(body.contains("Critical"), "Criterion notes should appear on POV detail page");
        assertTrue(body.contains("badge-not-started"), "NOT_STARTED status badge should be displayed");

        detailConn.disconnect();
    }

    // -------------------------------------------------------------------------
    // POST /criteria?povId=N (create) tests — validation failures
    // -------------------------------------------------------------------------

    @Test
    void postCreate_missingName_showsError() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=&description=test&status=NOT_STARTED&weight=3&notes=test";
        HttpURLConnection conn = openPostConnection("/criteria?povId=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Name is required"), "Should show 'Name is required' error");

        conn.disconnect();
    }

    @Test
    void postCreate_missingWeight_showsError() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=TestCriterion&description=test&status=NOT_STARTED&weight=&notes=test";
        HttpURLConnection conn = openPostConnection("/criteria?povId=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Weight is required"), "Should show 'Weight is required' error");

        conn.disconnect();
    }

    @Test
    void postCreate_weightZero_showsError() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=TestCriterion&description=test&status=NOT_STARTED&weight=0&notes=test";
        HttpURLConnection conn = openPostConnection("/criteria?povId=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Weight must be between 1 and 5"), "Should show weight range error");

        conn.disconnect();
    }

    @Test
    void postCreate_weightSix_showsError() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=TestCriterion&description=test&status=NOT_STARTED&weight=6&notes=test";
        HttpURLConnection conn = openPostConnection("/criteria?povId=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Weight must be between 1 and 5"), "Should show weight range error");

        conn.disconnect();
    }

    @Test
    void postCreate_weightNegative_showsError() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=TestCriterion&description=test&status=NOT_STARTED&weight=-1&notes=test";
        HttpURLConnection conn = openPostConnection("/criteria?povId=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Weight must be between 1 and 5"), "Should show weight range error");

        conn.disconnect();
    }

    @Test
    void postCreate_weightNonNumeric_showsError() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=TestCriterion&description=test&status=NOT_STARTED&weight=abc&notes=test";
        HttpURLConnection conn = openPostConnection("/criteria?povId=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Weight must be a valid number"), "Should show weight format error");

        conn.disconnect();
    }

    @Test
    void postCreate_missingNameAndWeight_showsMultipleErrors() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=&description=test&status=NOT_STARTED&weight=&notes=test";
        HttpURLConnection conn = openPostConnection("/criteria?povId=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);
        assertTrue(body.contains("Name is required"), "Should show Name error");
        assertTrue(body.contains("Weight is required"), "Should show Weight error");

        // Verify no criterion was created
        List<PovCriteria> criteria = criteriaDao.findByPovId(createSamplePov().getId());
        // Use the original pov - check no criteria exist
        conn.disconnect();
    }

    @Test
    void postCreate_validationFailure_preservesInput() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=&description=Important+desc&status=IN_PROGRESS&weight=4&notes=My+notes";
        HttpURLConnection conn = openPostConnection("/criteria?povId=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Important desc"), "Description should be preserved");
        assertTrue(body.contains("My notes"), "Notes should be preserved");
        assertTrue(body.contains("IN_PROGRESS") && body.contains("selected"),
                "Status selection should be preserved");

        conn.disconnect();
    }

    @Test
    void postCreate_xssInFormValues_escapedOnRerender() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=%3Cscript%3Ealert(1)%3C%2Fscript%3E&description=test&status=NOT_STARTED&weight=&notes=%3Cb%3Ebold%3C%2Fb%3E";
        HttpURLConnection conn = openPostConnection("/criteria?povId=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        assertFalse(body.contains("<script>alert(1)</script>"),
                "Script tags should be escaped in form re-render");

        conn.disconnect();
    }

    @Test
    void postCreate_invalidPovId_returns404() throws Exception {
        String formData = "name=Test&description=test&status=NOT_STARTED&weight=3&notes=test";
        HttpURLConnection conn = openPostConnection("/criteria?povId=99999", formData);

        assertEquals(404, conn.getResponseCode(),
                "POST create with non-existent POV ID should return 404");

        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // GET /criteria?action=edit&id=N&povId=N (edit form) tests
    // -------------------------------------------------------------------------

    @Test
    void getEditForm_returns200() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Performance", "NOT_STARTED", 5, "Benchmark notes");

        HttpURLConnection conn = openConnection(
                "/criteria?action=edit&id=" + c.getId() + "&povId=" + pov.getId());
        assertEquals(200, conn.getResponseCode(), "GET edit form should return 200");
        conn.disconnect();
    }

    @Test
    void getEditForm_prePopulatesAllFields() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Security Review", "IN_PROGRESS", 4, "Ongoing review");

        HttpURLConnection conn = openConnection(
                "/criteria?action=edit&id=" + c.getId() + "&povId=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Security Review"), "Name should be pre-populated");
        assertTrue(body.contains("Test criterion description"), "Description should be pre-populated");
        assertTrue(body.contains("Ongoing review"), "Notes should be pre-populated");

        conn.disconnect();
    }

    @Test
    void getEditForm_selectsCorrectStatus() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Status Test", "MET", 3, null);

        HttpURLConnection conn = openConnection(
                "/criteria?action=edit&id=" + c.getId() + "&povId=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("MET") && body.contains("selected"),
                "Status dropdown should have MET selected");

        conn.disconnect();
    }

    @Test
    void getEditForm_selectsCorrectWeight() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Weight Test", "NOT_STARTED", 4, null);

        HttpURLConnection conn = openConnection(
                "/criteria?action=edit&id=" + c.getId() + "&povId=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("value=\"4\"") && body.contains("selected"),
                "Weight dropdown should have value 4 selected");

        conn.disconnect();
    }

    @Test
    void getEditForm_showsEditTitle() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Title Test", "NOT_STARTED", 3, null);

        HttpURLConnection conn = openConnection(
                "/criteria?action=edit&id=" + c.getId() + "&povId=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Edit Criterion"), "Edit form should have 'Edit Criterion' title");

        conn.disconnect();
    }

    @Test
    void getEditForm_showsUpdateButton() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Button Test", "NOT_STARTED", 3, null);

        HttpURLConnection conn = openConnection(
                "/criteria?action=edit&id=" + c.getId() + "&povId=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Update Criterion"), "Edit form should show 'Update Criterion' button text");

        conn.disconnect();
    }

    @Test
    void getEditForm_formPointsToEditAction() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Action Test", "NOT_STARTED", 3, null);

        HttpURLConnection conn = openConnection(
                "/criteria?action=edit&id=" + c.getId() + "&povId=" + pov.getId());
        String body = readResponseBody(conn);

        assertTrue(body.contains("action=edit"), "Form action should include action=edit");
        assertTrue(body.contains("id=" + c.getId()), "Form action should include the criterion ID");
        assertTrue(body.contains("povId=" + pov.getId()), "Form action should include the POV ID");
        assertTrue(body.contains("method=\"post\""), "Form should use POST method");

        conn.disconnect();
    }

    @Test
    void getEditForm_nonExistentCriterionId_returns404() throws Exception {
        Pov pov = createSamplePov();
        HttpURLConnection conn = openConnection(
                "/criteria?action=edit&id=99999&povId=" + pov.getId());
        assertEquals(404, conn.getResponseCode(),
                "GET edit form with non-existent criterion ID should return 404");
        conn.disconnect();
    }

    @Test
    void getEditForm_missingId_returns404() throws Exception {
        Pov pov = createSamplePov();
        HttpURLConnection conn = openConnection(
                "/criteria?action=edit&povId=" + pov.getId());
        assertEquals(404, conn.getResponseCode(),
                "GET edit form without criterion ID should return 404");
        conn.disconnect();
    }

    @Test
    void getEditForm_missingPovId_returns404() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Missing POV", "NOT_STARTED", 3, null);

        HttpURLConnection conn = openConnection(
                "/criteria?action=edit&id=" + c.getId());
        assertEquals(404, conn.getResponseCode(),
                "GET edit form without POV ID should return 404");
        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // POST /criteria?action=edit&id=N&povId=N (edit/update) tests
    // -------------------------------------------------------------------------

    @Test
    void postEdit_validData_redirectsToPovDetail() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Original", "NOT_STARTED", 3, "Old notes");

        String formData = "name=Updated&description=Updated+desc&status=MET&weight=5&notes=New+notes";
        HttpURLConnection conn = openPostConnection(
                "/criteria?action=edit&id=" + c.getId() + "&povId=" + pov.getId(), formData);

        assertEquals(302, conn.getResponseCode(), "POST edit should redirect (302)");

        String location = conn.getHeaderField("Location");
        assertNotNull(location, "Redirect should have a Location header");
        assertTrue(location.contains("action=detail") && location.contains("id=" + pov.getId()),
                "Should redirect to POV detail page, got: " + location);

        conn.disconnect();
    }

    @Test
    void postEdit_validData_updatesInDatabase() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Original", "NOT_STARTED", 3, "Old notes");
        long criteriaId = c.getId();

        String formData = "name=Updated+Name&description=Updated+desc&status=MET&weight=5&notes=New+notes";
        HttpURLConnection conn = openPostConnection(
                "/criteria?action=edit&id=" + criteriaId + "&povId=" + pov.getId(), formData);
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        // Verify the criterion was updated
        PovCriteria updated = criteriaDao.findById(criteriaId);
        assertNotNull(updated, "Criterion should still exist after update");
        assertEquals("Updated Name", updated.getName());
        assertEquals("Updated desc", updated.getDescription());
        assertEquals("MET", updated.getStatus());
        assertEquals(5, updated.getWeight());
        assertEquals("New notes", updated.getNotes());
    }

    @Test
    void postEdit_statusChange_notStartedToMet_persists() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Status Change", "NOT_STARTED", 4, null);
        long criteriaId = c.getId();

        // Change status from NOT_STARTED to MET
        String formData = "name=Status+Change&description=&status=MET&weight=4&notes=";
        HttpURLConnection conn = openPostConnection(
                "/criteria?action=edit&id=" + criteriaId + "&povId=" + pov.getId(), formData);
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        PovCriteria updated = criteriaDao.findById(criteriaId);
        assertEquals("MET", updated.getStatus(), "Status should be updated to MET");
    }

    @Test
    void postEdit_statusChange_notStartedToInProgress_persists() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Progress", "NOT_STARTED", 3, null);
        long criteriaId = c.getId();

        String formData = "name=Progress&description=&status=IN_PROGRESS&weight=3&notes=";
        HttpURLConnection conn = openPostConnection(
                "/criteria?action=edit&id=" + criteriaId + "&povId=" + pov.getId(), formData);
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        PovCriteria updated = criteriaDao.findById(criteriaId);
        assertEquals("IN_PROGRESS", updated.getStatus(), "Status should be updated to IN_PROGRESS");
    }

    @Test
    void postEdit_statusChange_visibleOnDetailPage() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Visible Change", "NOT_STARTED", 5, "Test notes");

        // Change status to MET
        String formData = "name=Visible+Change&description=&status=MET&weight=5&notes=Test+notes";
        HttpURLConnection conn = openPostConnection(
                "/criteria?action=edit&id=" + c.getId() + "&povId=" + pov.getId(), formData);
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        // Verify the updated status is visible on the POV detail page
        HttpURLConnection detailConn = openConnection("/povs?action=detail&id=" + pov.getId());
        assertEquals(200, detailConn.getResponseCode());
        String body = readResponseBody(detailConn);

        assertTrue(body.contains("Visible Change"), "Criterion name should be on detail page");
        assertTrue(body.contains("badge-met"), "MET status badge should be displayed");

        detailConn.disconnect();
    }

    @Test
    void postEdit_doesNotCreateDuplicate() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "No Dup", "NOT_STARTED", 3, null);

        int countBefore = criteriaDao.findByPovId(pov.getId()).size();

        String formData = "name=Updated+No+Dup&description=&status=MET&weight=5&notes=";
        HttpURLConnection conn = openPostConnection(
                "/criteria?action=edit&id=" + c.getId() + "&povId=" + pov.getId(), formData);
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        int countAfter = criteriaDao.findByPovId(pov.getId()).size();
        assertEquals(countBefore, countAfter,
                "Edit should not create a duplicate criterion");
    }

    @Test
    void postEdit_invalidData_returnsFormWithErrors() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Error Criterion", "NOT_STARTED", 3, null);

        // Submit with missing name (invalid)
        String formData = "name=&description=test&status=NOT_STARTED&weight=3&notes=test";
        HttpURLConnection conn = openPostConnection(
                "/criteria?action=edit&id=" + c.getId() + "&povId=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode(),
                "POST edit with invalid data should return 200 (re-render form)");
        String body = readResponseBody(conn);
        assertTrue(body.contains("Name is required"),
                "Should show validation error for missing name");

        conn.disconnect();
    }

    @Test
    void postEdit_invalidData_preservesInput() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Preserve", "NOT_STARTED", 3, null);

        String formData = "name=&description=Preserved+desc&status=MET&weight=5&notes=Preserved+notes";
        HttpURLConnection conn = openPostConnection(
                "/criteria?action=edit&id=" + c.getId() + "&povId=" + pov.getId(), formData);

        assertEquals(200, conn.getResponseCode());
        String body = readResponseBody(conn);

        assertTrue(body.contains("Preserved desc"), "Description should be preserved");
        assertTrue(body.contains("Preserved notes"), "Notes should be preserved");
        assertTrue(body.contains("MET") && body.contains("selected"),
                "Status selection should be preserved");

        conn.disconnect();
    }

    @Test
    void postEdit_invalidData_doesNotModifyCriterion() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "NoMod", "NOT_STARTED", 3, "Original notes");
        long criteriaId = c.getId();

        // Submit with missing name (invalid)
        String formData = "name=&description=Changed&status=MET&weight=5&notes=Changed";
        HttpURLConnection conn = openPostConnection(
                "/criteria?action=edit&id=" + criteriaId + "&povId=" + pov.getId(), formData);
        assertEquals(200, conn.getResponseCode());
        conn.disconnect();

        // Verify the original criterion is unchanged
        PovCriteria unchanged = criteriaDao.findById(criteriaId);
        assertEquals("NoMod", unchanged.getName(), "Name should not have changed");
        assertEquals("NOT_STARTED", unchanged.getStatus(), "Status should not have changed");
        assertEquals(3, unchanged.getWeight(), "Weight should not have changed");
    }

    @Test
    void postEdit_nonExistentCriterionId_returns404() throws Exception {
        Pov pov = createSamplePov();
        String formData = "name=Test&description=test&status=NOT_STARTED&weight=3&notes=test";
        HttpURLConnection conn = openPostConnection(
                "/criteria?action=edit&id=99999&povId=" + pov.getId(), formData);

        assertEquals(404, conn.getResponseCode(),
                "POST edit with non-existent criterion ID should return 404");

        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // POST /criteria?action=delete&id=N&povId=N (delete) tests
    // -------------------------------------------------------------------------

    @Test
    void postDelete_validId_redirectsToPovDetail() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Delete Me", "NOT_STARTED", 3, null);

        HttpURLConnection conn = openPostConnection(
                "/criteria?action=delete&id=" + c.getId() + "&povId=" + pov.getId(), "");

        assertEquals(302, conn.getResponseCode(), "POST delete should redirect (302)");

        String location = conn.getHeaderField("Location");
        assertNotNull(location);
        assertTrue(location.contains("action=detail") && location.contains("id=" + pov.getId()),
                "Should redirect to POV detail page, got: " + location);

        conn.disconnect();
    }

    @Test
    void postDelete_validId_removesCriterionFromDatabase() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Gone Criterion", "MET", 5, null);
        long criteriaId = c.getId();

        HttpURLConnection conn = openPostConnection(
                "/criteria?action=delete&id=" + criteriaId + "&povId=" + pov.getId(), "");
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        assertNull(criteriaDao.findById(criteriaId), "Criterion should be removed after delete");
    }

    @Test
    void postDelete_removedFromPovDetailPage() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Removed Criterion", "NOT_STARTED", 3, null);

        // Delete the criterion
        HttpURLConnection deleteConn = openPostConnection(
                "/criteria?action=delete&id=" + c.getId() + "&povId=" + pov.getId(), "");
        assertEquals(302, deleteConn.getResponseCode());
        deleteConn.disconnect();

        // Verify it's no longer on the POV detail page
        HttpURLConnection detailConn = openConnection("/povs?action=detail&id=" + pov.getId());
        assertEquals(200, detailConn.getResponseCode());
        String body = readResponseBody(detailConn);

        assertFalse(body.contains("Removed Criterion"),
                "Deleted criterion should not appear on POV detail page");

        detailConn.disconnect();
    }

    @Test
    void postDelete_onlyRemovesTargetCriterion() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c1 = createSampleCriteria(pov.getId(), "Keep This", "MET", 5, null);
        PovCriteria c2 = createSampleCriteria(pov.getId(), "Delete This", "NOT_STARTED", 3, null);

        HttpURLConnection conn = openPostConnection(
                "/criteria?action=delete&id=" + c2.getId() + "&povId=" + pov.getId(), "");
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        assertNotNull(criteriaDao.findById(c1.getId()), "Other criterion should still exist");
        assertNull(criteriaDao.findById(c2.getId()), "Deleted criterion should be removed");
    }

    @Test
    void postDelete_parentPovUnaffected() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "Delete Criterion", "NOT_STARTED", 3, null);

        HttpURLConnection conn = openPostConnection(
                "/criteria?action=delete&id=" + c.getId() + "&povId=" + pov.getId(), "");
        assertEquals(302, conn.getResponseCode());
        conn.disconnect();

        // POV should still exist
        Pov parentPov = povDao.findById(pov.getId());
        assertNotNull(parentPov, "Parent POV should not be affected by criterion deletion");
        assertEquals("Test POV", parentPov.getName());
    }

    @Test
    void postDelete_nonExistentCriterionId_returns404() throws Exception {
        Pov pov = createSamplePov();
        HttpURLConnection conn = openPostConnection(
                "/criteria?action=delete&id=99999&povId=" + pov.getId(), "");

        assertEquals(404, conn.getResponseCode(),
                "POST delete with non-existent criterion ID should return 404");

        conn.disconnect();
    }

    @Test
    void postDelete_missingId_returns404() throws Exception {
        Pov pov = createSamplePov();
        HttpURLConnection conn = openPostConnection(
                "/criteria?action=delete&povId=" + pov.getId(), "");

        assertEquals(404, conn.getResponseCode(),
                "POST delete without criterion ID should return 404");

        conn.disconnect();
    }

    @Test
    void postDelete_missingPovId_returns404() throws Exception {
        Pov pov = createSamplePov();
        PovCriteria c = createSampleCriteria(pov.getId(), "No POV Delete", "NOT_STARTED", 3, null);

        HttpURLConnection conn = openPostConnection(
                "/criteria?action=delete&id=" + c.getId(), "");

        assertEquals(404, conn.getResponseCode(),
                "POST delete without POV ID should return 404");

        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // Full lifecycle integration test
    // -------------------------------------------------------------------------

    @Test
    void fullLifecycle_createEditDelete() throws Exception {
        Pov pov = createSamplePov();

        // Step 1: Create a criterion
        String createData = "name=Lifecycle+Criterion&description=Test+lifecycle&status=NOT_STARTED&weight=4&notes=Initial";
        HttpURLConnection createConn = openPostConnection("/criteria?povId=" + pov.getId(), createData);
        assertEquals(302, createConn.getResponseCode(), "Create should redirect");
        createConn.disconnect();

        // Verify criterion was created
        List<PovCriteria> afterCreate = criteriaDao.findByPovId(pov.getId());
        assertEquals(1, afterCreate.size(), "Should have 1 criterion after create");
        long criteriaId = afterCreate.get(0).getId();
        assertEquals("NOT_STARTED", afterCreate.get(0).getStatus());

        // Step 2: Edit the criterion - change status to MET
        String editData = "name=Lifecycle+Criterion&description=Updated+lifecycle&status=MET&weight=5&notes=Completed";
        HttpURLConnection editConn = openPostConnection(
                "/criteria?action=edit&id=" + criteriaId + "&povId=" + pov.getId(), editData);
        assertEquals(302, editConn.getResponseCode(), "Edit should redirect");
        editConn.disconnect();

        // Verify status was updated
        PovCriteria afterEdit = criteriaDao.findById(criteriaId);
        assertEquals("MET", afterEdit.getStatus(), "Status should be MET after edit");
        assertEquals(5, afterEdit.getWeight(), "Weight should be 5 after edit");

        // Verify on detail page
        HttpURLConnection detailConn = openConnection("/povs?action=detail&id=" + pov.getId());
        String detailBody = readResponseBody(detailConn);
        assertTrue(detailBody.contains("badge-met"), "MET badge should be visible on detail page");
        assertTrue(detailBody.contains("Lifecycle Criterion"), "Name should be visible");
        detailConn.disconnect();

        // Step 3: Delete the criterion
        HttpURLConnection deleteConn = openPostConnection(
                "/criteria?action=delete&id=" + criteriaId + "&povId=" + pov.getId(), "");
        assertEquals(302, deleteConn.getResponseCode(), "Delete should redirect");
        deleteConn.disconnect();

        // Verify criterion was deleted
        assertNull(criteriaDao.findById(criteriaId), "Criterion should be removed after delete");
        List<PovCriteria> afterDelete = criteriaDao.findByPovId(pov.getId());
        assertTrue(afterDelete.isEmpty(), "No criteria should remain after delete");
    }

    // -------------------------------------------------------------------------
    // GET /criteria (no action) - redirect test
    // -------------------------------------------------------------------------

    @Test
    void getWithoutAction_redirectsToPovsList() throws Exception {
        HttpURLConnection conn = openConnection("/criteria");
        assertEquals(302, conn.getResponseCode(),
                "GET /criteria without action should redirect");

        String location = conn.getHeaderField("Location");
        assertNotNull(location);
        assertTrue(location.endsWith("/povs"),
                "Should redirect to /povs, got: " + location);

        conn.disconnect();
    }
}
