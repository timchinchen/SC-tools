package com.dash0.sctools.servlet;

import com.dash0.sctools.dao.PovDao;
import com.dash0.sctools.dao.TimeEntryDao;
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Serves the dashboard/home page with summary data.
 * Mapped to "/dashboard". The root "/" is handled by RootRedirectServlet.
 * <p>
 * Queries the database on each request (no caching) to always show the latest data.
 */
@WebServlet(urlPatterns = {"/dashboard"})
public class DashboardServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardServlet.class);

    private final TimeEntryDao timeEntryDao;
    private final PovDao povDao;

    /**
     * Default constructor using production DAOs.
     */
    public DashboardServlet() {
        this.timeEntryDao = new TimeEntryDao();
        this.povDao = new PovDao();
    }

    /**
     * Constructor for testing with custom DAOs.
     */
    public DashboardServlet(TimeEntryDao timeEntryDao, PovDao povDao) {
        this.timeEntryDao = timeEntryDao;
        this.povDao = povDao;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // Prevent caching so dashboard always reflects latest data
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);

        // SC Time Summary
        BigDecimal totalHours = timeEntryDao.getTotalHours();
        long timeEntryCount = timeEntryDao.getCount();

        // POV Summary
        long povCount = povDao.getCount();
        Map<String, Long> povStatusCounts = povDao.countByStatus();

        // Set attributes for JSP
        req.setAttribute("totalHours", totalHours);
        req.setAttribute("timeEntryCount", timeEntryCount);
        req.setAttribute("povCount", povCount);
        req.setAttribute("povStatusCounts", povStatusCounts);

        Span.current().setAttribute("dashboard.total_hours", totalHours.doubleValue());
        Span.current().setAttribute("dashboard.time_entry_count", timeEntryCount);
        Span.current().setAttribute("dashboard.pov_count", povCount);

        LOG.debug("Dashboard loaded: totalHours={}, timeEntries={}, povs={}", totalHours, timeEntryCount, povCount);

        req.getRequestDispatcher("/WEB-INF/jsp/dashboard.jsp").forward(req, resp);
    }
}
