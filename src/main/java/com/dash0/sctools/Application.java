package com.dash0.sctools;

import com.dash0.sctools.servlet.DashboardServlet;
import com.dash0.sctools.servlet.RootRedirectServlet;
import com.dash0.sctools.util.DatabaseInitializer;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;

/**
 * Main application entry point. Starts an embedded Jetty server on port 8090
 * with JSP support, serving the SC-Tools web application.
 */
public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);
    private static final int PORT = 8090;

    public static void main(String[] args) throws Exception {
        // Initialize the database (creates tables if they don't exist)
        DatabaseInitializer.initialize();

        // Create and configure the Jetty server
        Server server = createServer(PORT);

        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down SC-Tools...");
            try {
                server.stop();
            } catch (Exception e) {
                LOG.error("Error during shutdown", e);
            }
        }));

        // Start the server
        server.start();
        LOG.info("SC-Tools started on http://localhost:{}", PORT);
        server.join();
    }

    /**
     * Creates and configures the Jetty server with JSP support.
     */
    public static Server createServer(int port) throws Exception {
        Server server = new Server(port);

        // Configure the web application context
        WebAppContext webapp = new WebAppContext();
        webapp.setContextPath("/");

        // Determine the webapp directory
        String webappDir = getWebAppDir();
        webapp.setResourceBase(webappDir);

        // Only set descriptor if the web.xml exists as a file
        File webXml = new File(webappDir, "WEB-INF/web.xml");
        if (webXml.exists()) {
            webapp.setDescriptor(webXml.getAbsolutePath());
        }

        // Set a temp directory for JSP compilation
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "sc-tools-jsp");
        tempDir.mkdirs();
        webapp.setTempDirectory(tempDir);

        // Use the parent classloader so that all JARs (including JSTL) are visible
        // This is essential for embedded Jetty mode where everything is on one classpath
        webapp.setParentLoaderPriority(true);

        // Enable JSP/JSTL support - include all JARs and directories for TLD scanning
        // The pattern must match both .jar files and class directories
        webapp.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*");
        webapp.setAttribute("org.eclipse.jetty.server.webapp.WebInfIncludeJarPattern", ".*");

        // Initialize JSP support (Apache Jasper) for embedded mode.
        // This bean calls JettyJasperInitializer.onStartup() which sets up
        // TLD cache, JSP servlet, and JSTL tag libraries.
        webapp.addBean(new JspStarter(webapp));

        // Register servlets programmatically for reliable behavior in both
        // development and packaged (fat JAR) modes
        registerServlets(webapp);

        // Add the web application context to the server
        server.setHandler(webapp);

        return server;
    }

    /**
     * Lifecycle bean that initializes JSP support via JettyJasperInitializer.
     * Required for embedded Jetty to set up JSP compilation, TLD cache, and JSTL.
     */
    public static class JspStarter extends AbstractLifeCycle {
        private final WebAppContext context;

        public JspStarter(WebAppContext context) {
            this.context = context;
        }

        @Override
        protected void doStart() throws Exception {
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(context.getClassLoader());
            try {
                JettyJasperInitializer initializer = new JettyJasperInitializer();
                initializer.onStartup(null, context.getServletContext());
                LOG.info("JSP/JSTL support initialized successfully");
            } catch (Exception e) {
                LOG.error("Failed to initialize JSP support", e);
                throw e;
            } finally {
                Thread.currentThread().setContextClassLoader(old);
            }
        }
    }

    /**
     * Registers all servlets programmatically.
     * This ensures servlets are available in both development and fat JAR modes.
     */
    static void registerServlets(WebAppContext webapp) {
        webapp.addServlet(new ServletHolder("dashboard", DashboardServlet.class), "/dashboard");
        webapp.addServlet(new ServletHolder("root", RootRedirectServlet.class), "");
    }

    /**
     * Resolves the webapp resource directory. Looks for:
     * 1. src/main/webapp (development mode)
     * 2. webapp/ on classpath (packaged JAR mode)
     */
    static String getWebAppDir() {
        // Try development path first
        File devPath = new File("src/main/webapp");
        if (devPath.exists() && devPath.isDirectory()) {
            LOG.info("Using development webapp directory: {}", devPath.getAbsolutePath());
            return devPath.getAbsolutePath();
        }

        // Try classpath resource (packaged JAR)
        URL webappUrl = Application.class.getClassLoader().getResource("webapp");
        if (webappUrl != null) {
            LOG.info("Using classpath webapp directory: {}", webappUrl.toExternalForm());
            return webappUrl.toExternalForm();
        }

        // Fallback
        LOG.warn("Could not find webapp directory, using current directory");
        return ".";
    }
}
