package com.dash0.sctools.servlet;

import com.dash0.sctools.Application;
import com.dash0.sctools.dao.PovDao;
import com.dash0.sctools.dao.TimeEntryDao;
import com.dash0.sctools.model.Pov;
import com.dash0.sctools.util.DatabaseInitializer;
import io.opentelemetry.api.trace.Span;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OpenTelemetry instrumentation: custom span attributes and Dash0 Web SDK presence.
 */
class InstrumentationTest {

    private Server server;
    private static final int TEST_PORT = 8099;
    private TimeEntryDao timeEntryDao;
    private PovDao povDao;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseInitializer.initialize();
        timeEntryDao = new TimeEntryDao();
        povDao = new PovDao();

        try (Connection conn = DatabaseInitializer.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM pov_criteria");
            stmt.execute("DELETE FROM povs");
            stmt.execute("DELETE FROM time_entries");
        }

        server = Application.createServer(TEST_PORT);
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    private HttpURLConnection openGet(String path) throws Exception {
        URL url = URI.create("http://localhost:" + TEST_PORT + path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    private String readBody(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private HttpURLConnection postForm(String path, String formData) throws Exception {
        URL url = URI.create("http://localhost:" + TEST_PORT + path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setInstanceFollowRedirects(false);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(formData.getBytes(StandardCharsets.UTF_8));
        }
        return conn;
    }

    // -------------------------------------------------------------------------
    // Dash0 Web SDK presence in pages
    // -------------------------------------------------------------------------

    @Test
    void dashboardPage_containsDash0WebSdk() throws Exception {
        HttpURLConnection conn = openGet("/dashboard");
        String body = readBody(conn);
        assertTrue(body.contains("dash0-sdk-web"), "Dashboard page should include Dash0 Web SDK script");
        assertTrue(body.contains("data-dash0-otel-collector-url"), "SDK should have collector URL configured");
        assertTrue(body.contains("sc-tools-frontend"), "SDK should have service name configured");
        conn.disconnect();
    }

    @Test
    void timeEntriesPage_containsDash0WebSdk() throws Exception {
        HttpURLConnection conn = openGet("/time-entries");
        String body = readBody(conn);
        assertTrue(body.contains("dash0-sdk-web"), "Time entries page should include Dash0 Web SDK script");
        conn.disconnect();
    }

    @Test
    void povsPage_containsDash0WebSdk() throws Exception {
        HttpURLConnection conn = openGet("/povs");
        String body = readBody(conn);
        assertTrue(body.contains("dash0-sdk-web"), "POVs page should include Dash0 Web SDK script");
        conn.disconnect();
    }

    // -------------------------------------------------------------------------
    // OTel API noop: servlets work correctly without agent
    // -------------------------------------------------------------------------

    @Test
    void spanCurrentReturnsNoopWithoutAgent() {
        Span span = Span.current();
        assertNotNull(span, "Span.current() should never return null");
        assertFalse(span.getSpanContext().isValid(),
                "Without agent, span context should be invalid (noop)");
    }

    @Test
    void noopSpanSetAttributeDoesNotThrow() {
        Span span = Span.current();
        assertDoesNotThrow(() -> {
            span.setAttribute("test.key", "test-value");
            span.setAttribute("test.number", 42L);
            span.setAttribute("test.double", 3.14);
        }, "Setting attributes on noop span should not throw");
    }

    @Test
    void povServlet_withOtelAttributes_worksCorrectly() throws Exception {
        String formData = "name=Test+POV&accountName=Acme&scName=Alice&status=PLANNED"
                + "&startDate=2026-04-01&targetEndDate=2026-06-30&description=Test";
        HttpURLConnection conn = postForm("/povs", formData);
        assertEquals(302, conn.getResponseCode(),
                "POV create should redirect (302) even with OTel span attributes");
        conn.disconnect();
    }

    @Test
    void timeEntryServlet_withOtelAttributes_worksCorrectly() throws Exception {
        String formData = "scName=Alice&date=2026-04-01&hours=4.0"
                + "&accountName=Acme+Corp&activityType=DEMO&description=Test";
        HttpURLConnection conn = postForm("/time-entries", formData);
        assertEquals(302, conn.getResponseCode(),
                "Time entry create should redirect (302) even with OTel span attributes");
        conn.disconnect();
    }

    @Test
    void dashboardServlet_withOtelAttributes_returns200() throws Exception {
        HttpURLConnection conn = openGet("/dashboard");
        assertEquals(200, conn.getResponseCode(),
                "Dashboard should return 200 even with OTel span attributes");
        conn.disconnect();
    }

    @Test
    void povDetail_withOtelAttributes_worksCorrectly() throws Exception {
        Pov pov = new Pov();
        pov.setName("Instrumented POV");
        pov.setAccountName("Acme");
        pov.setScName("Alice");
        pov.setStatus("IN_PROGRESS");
        pov.setStartDate(LocalDate.of(2026, 4, 1));
        pov.setTargetEndDate(LocalDate.of(2026, 6, 30));
        povDao.create(pov);

        HttpURLConnection conn = openGet("/povs?action=detail&id=" + pov.getId());
        assertEquals(200, conn.getResponseCode(),
                "POV detail should return 200 with OTel attributes set");
        String body = readBody(conn);
        assertTrue(body.contains("Instrumented POV"),
                "POV detail should display the POV name");
        conn.disconnect();
    }
}
