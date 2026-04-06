package com.dash0.sctools.servlet;

import com.dash0.sctools.Application;
import com.dash0.sctools.dao.PovDao;
import com.dash0.sctools.dao.TimeEntryDao;
import com.dash0.sctools.model.ActivityType;
import com.dash0.sctools.model.Pov;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DashboardServlet using embedded Jetty.
 * Tests dashboard page rendering, summary data, empty states, and navigation.
 */
class DashboardServletTest {

    private Server server;
    private static final int TEST_PORT = 8098;
    private TimeEntryDao timeEntryDao;
    private PovDao povDao;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize the production database (creates tables if needed)
        DatabaseInitializer.initialize();

        // Use the production DAOs (same as the servlet will use)
        timeEntryDao = new TimeEntryDao();
        povDao = new PovDao();

        // Clean out any existing data for a fresh test state
        try (Connection conn = DatabaseInitializer.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM pov_criteria");
            stmt.execute("DELETE FROM povs");
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

    private TimeEntry createTimeEntry(String scName, BigDecimal hours) {
        TimeEntry entry = new TimeEntry();
        entry.setScName(scName);
        entry.setDate(LocalDate.of(2026, 4, 1));
        entry.setHours(hours);
        entry.setAccountName("Acme Corp");
        entry.setActivityType(ActivityType.DEMO);
        entry.setDescription("Test entry");
        return timeEntryDao.create(entry);
    }

    private Pov createPov(String name, String status) {
        Pov pov = new Pov();
        pov.setName(name);
        pov.setAccountName("Acme Corp");
        pov.setScName("Alice");
        pov.setStatus(status);
        pov.setStartDate(LocalDate.of(2026, 4, 1));
        pov.setTargetEndDate(LocalDate.of(2026, 6, 30));
        pov.setDescription("Test POV");
        return povDao.create(pov);
    }

    // -------------------------------------------------------------------------
    // GET /dashboard basic tests
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_returns200() throws Exception {
        HttpURLConnection conn = openConnection("/dashboard");
        assertEquals(200, conn.getResponseCode(), "GET /dashboard should return 200");
        conn.disconnect();
    }

    @Test
    void getDashboard_returnsHtml() throws Exception {
        HttpURLConnection conn = openConnection("/dashboard");
        String contentType = conn.getContentType();
        assertTrue(contentType.contains("text/html"), "Response should be HTML, got: " + contentType);
        conn.disconnect();
    }

    @Test
    void getDashboard_containsPageStructure() throws Exception {
        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertTrue(body.contains("<!DOCTYPE html>"), "Page should start with DOCTYPE");
        assertTrue(body.contains("<header"), "Page should contain header element");
        assertTrue(body.contains("<footer"), "Page should contain footer element");
        assertTrue(body.contains("style.css"), "Page should reference the stylesheet");
        assertTrue(body.contains("Dashboard"), "Page should contain 'Dashboard' title");
        conn.disconnect();
    }

    @Test
    void getDashboard_containsNoCacheHeaders() throws Exception {
        HttpURLConnection conn = openConnection("/dashboard");
        String cacheControl = conn.getHeaderField("Cache-Control");
        assertNotNull(cacheControl, "Cache-Control header should be set");
        assertTrue(cacheControl.contains("no-cache"), "Cache-Control should contain no-cache");
        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // Root URL redirect tests
    // -------------------------------------------------------------------------

    @Test
    void getRoot_redirectsToDashboard() throws Exception {
        HttpURLConnection conn = openConnection("/");
        int statusCode = conn.getResponseCode();
        assertEquals(302, statusCode, "GET / should redirect (302)");

        String location = conn.getHeaderField("Location");
        assertNotNull(location, "Redirect should have a Location header");
        assertTrue(location.endsWith("/dashboard"),
                "Should redirect to /dashboard, got: " + location);
        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // Navigation tests
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_containsNavigationLinks() throws Exception {
        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertTrue(body.contains("/dashboard"), "Page should contain link to dashboard");
        assertTrue(body.contains("/time-entries"), "Page should contain link to time entries");
        assertTrue(body.contains("/povs"), "Page should contain link to POVs");
        conn.disconnect();
    }

    @Test
    void getDashboard_containsTimeEntriesLink() throws Exception {
        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertTrue(body.contains("View Time Entries"),
                "Dashboard should contain 'View Time Entries' link");
        assertTrue(body.contains("/time-entries"),
                "Dashboard should have link to /time-entries");
        conn.disconnect();
    }

    @Test
    void getDashboard_containsPovLink() throws Exception {
        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertTrue(body.contains("View POVs"),
                "Dashboard should contain 'View POVs' link");
        assertTrue(body.contains("/povs"),
                "Dashboard should have link to /povs");
        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // Empty state tests
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_emptyDatabase_showsTimeEmptyState() throws Exception {
        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertTrue(body.contains("No time entries recorded"),
                "Should show 'No time entries recorded' when no time entries exist");
        conn.disconnect();
    }

    @Test
    void getDashboard_emptyDatabase_showsPovEmptyState() throws Exception {
        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertTrue(body.contains("No POVs tracked"),
                "Should show 'No POVs tracked' when no POVs exist");
        conn.disconnect();
    }

    @Test
    void getDashboard_emptyDatabase_noTotalHoursShown() throws Exception {
        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertFalse(body.contains("Total Hours Logged"),
                "Should not show 'Total Hours Logged' when no entries exist");
        conn.disconnect();
    }

    @Test
    void getDashboard_emptyDatabase_noStatusBreakdownShown() throws Exception {
        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertFalse(body.contains("Status Breakdown"),
                "Should not show 'Status Breakdown' when no POVs exist");
        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // SC Time Summary tests
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_withTimeEntries_showsTotalHours() throws Exception {
        createTimeEntry("Alice", new BigDecimal("4.5"));
        createTimeEntry("Bob", new BigDecimal("8.0"));

        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertTrue(body.contains("Total Hours Logged"),
                "Should show 'Total Hours Logged' label");
        assertTrue(body.contains("12.5") || body.contains("12.50"),
                "Should show total hours of 12.5 (4.5 + 8.0)");
        assertFalse(body.contains("No time entries recorded"),
                "Should NOT show empty state when entries exist");
        conn.disconnect();
    }

    @Test
    void getDashboard_withTimeEntries_showsEntryCount() throws Exception {
        createTimeEntry("Alice", new BigDecimal("4.0"));
        createTimeEntry("Bob", new BigDecimal("8.0"));
        createTimeEntry("Charlie", new BigDecimal("2.0"));

        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertTrue(body.contains("Time Entries"),
                "Should show 'Time Entries' label");
        // Should show count of 3
        assertTrue(body.contains(">3<"),
                "Should show entry count of 3");
        conn.disconnect();
    }

    @Test
    void getDashboard_withSingleTimeEntry_showsCorrectTotal() throws Exception {
        createTimeEntry("Alice", new BigDecimal("7.25"));

        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertTrue(body.contains("7.25"),
                "Should show total hours of 7.25 for a single entry");
        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // POV Summary tests
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_withPovs_showsTotalCount() throws Exception {
        createPov("POV Alpha", "PLANNED");
        createPov("POV Beta", "IN_PROGRESS");

        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertTrue(body.contains("Total POVs"),
                "Should show 'Total POVs' label");
        assertFalse(body.contains("No POVs tracked"),
                "Should NOT show empty state when POVs exist");
        conn.disconnect();
    }

    @Test
    void getDashboard_withPovs_showsStatusBreakdown() throws Exception {
        createPov("POV Alpha", "PLANNED");
        createPov("POV Beta", "PLANNED");
        createPov("POV Gamma", "IN_PROGRESS");
        createPov("POV Delta", "COMPLETED");
        createPov("POV Epsilon", "WON");
        createPov("POV Zeta", "LOST");

        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertTrue(body.contains("Status Breakdown"),
                "Should show 'Status Breakdown' heading");
        assertTrue(body.contains("badge-planned"),
                "Should show Planned status badge");
        assertTrue(body.contains("badge-in-progress"),
                "Should show In Progress status badge");
        assertTrue(body.contains("badge-completed"),
                "Should show Completed status badge");
        assertTrue(body.contains("badge-won"),
                "Should show Won status badge");
        assertTrue(body.contains("badge-lost"),
                "Should show Lost status badge");
        assertTrue(body.contains("badge-cancelled"),
                "Should show Cancelled status badge");
        conn.disconnect();
    }

    @Test
    void getDashboard_withPovs_statusCountsAreCorrect() throws Exception {
        createPov("POV 1", "PLANNED");
        createPov("POV 2", "PLANNED");
        createPov("POV 3", "IN_PROGRESS");
        createPov("POV 4", "WON");

        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        // Verify the total count is 4
        assertTrue(body.contains(">4<"),
                "Should show total POV count of 4");
        conn.disconnect();
    }

    @Test
    void getDashboard_withMixedStatuses_showsAllStatusCounts() throws Exception {
        createPov("P1", "PLANNED");
        createPov("P2", "PLANNED");
        createPov("P3", "PLANNED");
        createPov("IP1", "IN_PROGRESS");
        createPov("IP2", "IN_PROGRESS");
        createPov("W1", "WON");

        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        assertTrue(body.contains("Planned"), "Should show Planned label");
        assertTrue(body.contains("In Progress"), "Should show In Progress label");
        assertTrue(body.contains("Completed"), "Should show Completed label");
        assertTrue(body.contains("Won"), "Should show Won label");
        assertTrue(body.contains("Lost"), "Should show Lost label");
        assertTrue(body.contains("Cancelled"), "Should show Cancelled label");
        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // Data updates reflected on dashboard
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_updatesAfterNewTimeEntry() throws Exception {
        // Initially empty
        HttpURLConnection conn1 = openConnection("/dashboard");
        String body1 = readResponseBody(conn1);
        assertTrue(body1.contains("No time entries recorded"),
                "Initially should show empty state");
        conn1.disconnect();

        // Create a time entry
        createTimeEntry("Alice", new BigDecimal("5.0"));

        // Dashboard should now show the entry
        HttpURLConnection conn2 = openConnection("/dashboard");
        String body2 = readResponseBody(conn2);
        assertFalse(body2.contains("No time entries recorded"),
                "After creating entry, should NOT show empty state");
        assertTrue(body2.contains("5.0") || body2.contains("5.00"),
                "After creating entry, should show total hours");
        conn2.disconnect();
    }

    @Test
    void getDashboard_updatesAfterNewPov() throws Exception {
        // Initially empty
        HttpURLConnection conn1 = openConnection("/dashboard");
        String body1 = readResponseBody(conn1);
        assertTrue(body1.contains("No POVs tracked"),
                "Initially should show empty state for POVs");
        conn1.disconnect();

        // Create a POV
        createPov("New POV", "PLANNED");

        // Dashboard should now show the POV
        HttpURLConnection conn2 = openConnection("/dashboard");
        String body2 = readResponseBody(conn2);
        assertFalse(body2.contains("No POVs tracked"),
                "After creating POV, should NOT show empty state");
        assertTrue(body2.contains("Total POVs"),
                "After creating POV, should show Total POVs");
        conn2.disconnect();
    }

    // -------------------------------------------------------------------------
    // SC Time and POV sections are independent
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_timeEntriesOnly_showsTimeSummaryAndPovEmptyState() throws Exception {
        createTimeEntry("Alice", new BigDecimal("3.0"));

        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        // Time summary should be shown
        assertTrue(body.contains("Total Hours Logged"),
                "Should show time summary when entries exist");
        assertFalse(body.contains("No time entries recorded"),
                "Should NOT show time empty state when entries exist");

        // POV should still show empty state
        assertTrue(body.contains("No POVs tracked"),
                "Should show POV empty state when no POVs exist");

        conn.disconnect();
    }

    @Test
    void getDashboard_povsOnly_showsPovSummaryAndTimeEmptyState() throws Exception {
        createPov("Test POV", "IN_PROGRESS");

        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        // Time section should show empty state
        assertTrue(body.contains("No time entries recorded"),
                "Should show time empty state when no entries exist");

        // POV summary should be shown
        assertTrue(body.contains("Total POVs"),
                "Should show POV summary when POVs exist");
        assertFalse(body.contains("No POVs tracked"),
                "Should NOT show POV empty state when POVs exist");

        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // Both sections have data
    // -------------------------------------------------------------------------

    @Test
    void getDashboard_bothSectionsHaveData() throws Exception {
        createTimeEntry("Alice", new BigDecimal("4.0"));
        createTimeEntry("Bob", new BigDecimal("6.0"));
        createPov("POV A", "PLANNED");
        createPov("POV B", "WON");

        HttpURLConnection conn = openConnection("/dashboard");
        String body = readResponseBody(conn);

        // Time summary present
        assertTrue(body.contains("Total Hours Logged"),
                "Should show time summary");
        assertTrue(body.contains("10.0") || body.contains("10.00"),
                "Should show total hours of 10.0");

        // POV summary present
        assertTrue(body.contains("Total POVs"),
                "Should show POV summary");
        assertTrue(body.contains("Status Breakdown"),
                "Should show status breakdown");

        // No empty states
        assertFalse(body.contains("No time entries recorded"),
                "Should NOT show time empty state");
        assertFalse(body.contains("No POVs tracked"),
                "Should NOT show POV empty state");

        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // Cross-navigation tests
    // -------------------------------------------------------------------------

    @Test
    void timeEntriesPage_hasNavLinkToDashboard() throws Exception {
        HttpURLConnection conn = openConnection("/time-entries");
        String body = readResponseBody(conn);

        assertTrue(body.contains("/dashboard"),
                "Time Entries page should have nav link to Dashboard");
        conn.disconnect();
    }

    @Test
    void povsPage_hasNavLinkToDashboard() throws Exception {
        HttpURLConnection conn = openConnection("/povs");
        String body = readResponseBody(conn);

        assertTrue(body.contains("/dashboard"),
                "POVs page should have nav link to Dashboard");
        conn.disconnect();
    }

    @Test
    void allPages_accessibleViaDirectUrl() throws Exception {
        // Dashboard
        HttpURLConnection dashConn = openConnection("/dashboard");
        assertEquals(200, dashConn.getResponseCode(), "/dashboard should return 200");
        dashConn.disconnect();

        // Time Entries
        HttpURLConnection timeConn = openConnection("/time-entries");
        assertEquals(200, timeConn.getResponseCode(), "/time-entries should return 200");
        timeConn.disconnect();

        // POVs
        HttpURLConnection povConn = openConnection("/povs");
        assertEquals(200, povConn.getResponseCode(), "/povs should return 200");
        povConn.disconnect();
    }
}
