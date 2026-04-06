package com.dash0.sctools;

import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the Application class - verifies that Jetty starts
 * and serves pages correctly.
 */
class ApplicationTest {

    private Server server;
    private static final int TEST_PORT = 8099;

    @BeforeEach
    void setUp() throws Exception {
        server = Application.createServer(TEST_PORT);
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    @Test
    void testServerStarts() {
        assertTrue(server.isRunning(), "Server should be running");
    }

    @Test
    void testDashboardReturns200() throws Exception {
        URL url = URI.create("http://localhost:" + TEST_PORT + "/dashboard").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(false);

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            // Capture error body for debugging
            try (var is = conn.getErrorStream()) {
                if (is != null) {
                    String body = new String(is.readAllBytes());
                    System.err.println("Dashboard error response: " + body.substring(0, Math.min(body.length(), 2000)));
                }
            }
        }
        assertEquals(200, statusCode, "Dashboard should return 200");
        conn.disconnect();
    }

    @Test
    void testRootRedirectsToDashboard() throws Exception {
        URL url = URI.create("http://localhost:" + TEST_PORT + "/").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(false);

        int statusCode = conn.getResponseCode();
        // Should redirect to /dashboard
        assertTrue(statusCode == 302 || statusCode == 200,
                "Root should redirect (302) or serve content (200), got: " + statusCode);
        conn.disconnect();
    }

    @Test
    void testStaticCssAccessible() throws Exception {
        URL url = URI.create("http://localhost:" + TEST_PORT + "/static/css/style.css").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int statusCode = conn.getResponseCode();
        assertEquals(200, statusCode, "Static CSS should be accessible");
        conn.disconnect();
    }
}
