package com.dash0.sctools.servlet;

import com.dash0.sctools.dao.TimeEntryDao;
import com.dash0.sctools.model.ActivityType;
import com.dash0.sctools.model.TimeEntry;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servlet handling time entry list display and creation.
 * GET  /time-entries            — shows the list of all time entries (or empty state).
 * GET  /time-entries?action=new — shows the create form.
 * POST /time-entries            — creates a new time entry (with validation).
 */
@WebServlet(urlPatterns = {"/time-entries"})
public class TimeEntryServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(TimeEntryServlet.class);

    private TimeEntryDao timeEntryDao;

    /**
     * Default constructor uses production DAO.
     */
    public TimeEntryServlet() {
        this.timeEntryDao = new TimeEntryDao();
    }

    /**
     * Constructor for testing with a custom DAO.
     */
    public TimeEntryServlet(TimeEntryDao timeEntryDao) {
        this.timeEntryDao = timeEntryDao;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");

        if ("new".equals(action)) {
            LOG.debug("GET /time-entries?action=new");
            showCreateForm(req, resp);
        } else {
            LOG.debug("GET /time-entries");
            showList(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        LOG.debug("POST /time-entries");
        handleCreate(req, resp);
    }

    /**
     * Shows the time entry list page.
     */
    private void showList(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        List<TimeEntry> entries = timeEntryDao.findAll();
        req.setAttribute("entries", entries);
        req.getRequestDispatcher("/WEB-INF/jsp/time-entries/list.jsp").forward(req, resp);
    }

    /**
     * Shows the create time entry form.
     */
    private void showCreateForm(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setAttribute("activityTypes", ActivityType.values());
        req.getRequestDispatcher("/WEB-INF/jsp/time-entries/form.jsp").forward(req, resp);
    }

    /**
     * Handles time entry creation with server-side validation.
     * On validation failure: re-renders form with error messages and preserved input.
     * On success: creates the entry and redirects (302) to the list page.
     */
    private void handleCreate(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String scName = trimParam(req, "scName");
        String dateStr = trimParam(req, "date");
        String hoursStr = trimParam(req, "hours");
        String accountName = trimParam(req, "accountName");
        String activityTypeStr = trimParam(req, "activityType");
        String description = trimParam(req, "description");

        List<String> errors = new ArrayList<>();

        // Validate required fields
        if (scName.isEmpty()) {
            errors.add("SC Name is required.");
        }
        if (dateStr.isEmpty()) {
            errors.add("Date is required.");
        }
        if (hoursStr.isEmpty()) {
            errors.add("Hours is required.");
        }
        if (accountName.isEmpty()) {
            errors.add("Account Name is required.");
        }
        if (activityTypeStr.isEmpty()) {
            errors.add("Activity Type is required.");
        }

        // Validate date format
        LocalDate date = null;
        if (!dateStr.isEmpty()) {
            try {
                date = LocalDate.parse(dateStr);
            } catch (DateTimeParseException e) {
                errors.add("Date must be a valid date (YYYY-MM-DD).");
            }
        }

        // Validate hours: must be numeric, > 0, and <= 24
        BigDecimal hours = null;
        if (!hoursStr.isEmpty()) {
            try {
                hours = new BigDecimal(hoursStr);
                if (hours.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add("Hours must be greater than 0.");
                } else if (hours.compareTo(new BigDecimal("24")) > 0) {
                    errors.add("Hours must not exceed 24.");
                }
            } catch (NumberFormatException e) {
                errors.add("Hours must be a valid number.");
            }
        }

        // Validate activity type
        ActivityType activityType = null;
        if (!activityTypeStr.isEmpty()) {
            try {
                activityType = ActivityType.valueOf(activityTypeStr);
            } catch (IllegalArgumentException e) {
                errors.add("Invalid activity type.");
            }
        }

        // If there are validation errors, re-render the form with errors and preserved input
        if (!errors.isEmpty()) {
            req.setAttribute("errors", errors);
            req.setAttribute("activityTypes", ActivityType.values());
            req.setAttribute("scName", scName);
            req.setAttribute("date", dateStr);
            req.setAttribute("hours", hoursStr);
            req.setAttribute("accountName", accountName);
            req.setAttribute("activityType", activityTypeStr);
            req.setAttribute("description", description);
            req.getRequestDispatcher("/WEB-INF/jsp/time-entries/form.jsp").forward(req, resp);
            return;
        }

        // Create the time entry
        TimeEntry entry = new TimeEntry();
        entry.setScName(scName);
        entry.setDate(date);
        entry.setHours(hours);
        entry.setAccountName(accountName);
        entry.setActivityType(activityType);
        entry.setDescription(description);

        timeEntryDao.create(entry);
        LOG.info("Created time entry id={} for sc={}", entry.getId(), entry.getScName());

        // PRG: redirect to the list page
        resp.sendRedirect(req.getContextPath() + "/time-entries");
    }

    /**
     * Gets a trimmed request parameter, returning empty string if null.
     */
    private String trimParam(HttpServletRequest req, String name) {
        String value = req.getParameter(name);
        return value != null ? value.trim() : "";
    }
}
