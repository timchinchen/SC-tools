package com.dash0.sctools.servlet;

import com.dash0.sctools.dao.TimeEntryDao;
import com.dash0.sctools.model.ActivityType;
import com.dash0.sctools.model.TimeEntry;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servlet handling time entry CRUD operations.
 * GET  /time-entries                    — shows the list of all time entries (or empty state).
 * GET  /time-entries?action=new         — shows the create form.
 * GET  /time-entries?action=edit&id=N   — shows the edit form pre-populated with entry N.
 * POST /time-entries                    — creates a new time entry (with validation).
 * POST /time-entries?action=edit&id=N   — updates entry N (with validation).
 * POST /time-entries?action=delete&id=N — deletes entry N.
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
        } else if ("edit".equals(action)) {
            LOG.debug("GET /time-entries?action=edit");
            showEditForm(req, resp);
        } else {
            LOG.debug("GET /time-entries");
            showList(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");

        if ("edit".equals(action)) {
            LOG.debug("POST /time-entries?action=edit");
            handleEdit(req, resp);
        } else if ("delete".equals(action)) {
            LOG.debug("POST /time-entries?action=delete");
            handleDelete(req, resp);
        } else {
            LOG.debug("POST /time-entries");
            handleCreate(req, resp);
        }
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
        req.setAttribute("formMode", "create");
        req.getRequestDispatcher("/WEB-INF/jsp/time-entries/form.jsp").forward(req, resp);
    }

    /**
     * Shows the edit time entry form pre-populated with the entry's current values.
     * Returns 404 if the entry ID is invalid or the entry does not exist.
     */
    private void showEditForm(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Long id = parseIdParam(req);
        if (id == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Time entry not found.");
            return;
        }

        TimeEntry entry = timeEntryDao.findById(id);
        if (entry == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Time entry not found.");
            return;
        }

        req.setAttribute("activityTypes", ActivityType.values());
        req.setAttribute("formMode", "edit");
        req.setAttribute("entryId", entry.getId());
        req.setAttribute("scName", entry.getScName());
        req.setAttribute("date", entry.getDate() != null ? entry.getDate().toString() : "");
        req.setAttribute("hours", entry.getHours() != null ? entry.getHours().stripTrailingZeros().toPlainString() : "");
        req.setAttribute("accountName", entry.getAccountName());
        req.setAttribute("activityType", entry.getActivityType() != null ? entry.getActivityType().name() : "");
        req.setAttribute("description", entry.getDescription() != null ? entry.getDescription() : "");

        req.getRequestDispatcher("/WEB-INF/jsp/time-entries/form.jsp").forward(req, resp);
    }

    /**
     * Handles time entry creation with server-side validation.
     * On validation failure: re-renders form with error messages and preserved input.
     * On success: creates the entry and redirects (302) to the list page.
     */
    private void handleCreate(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        List<String> errors = new ArrayList<>();
        FormData formData = extractAndValidateFormData(req, errors);

        // If there are validation errors, re-render the form with errors and preserved input
        if (!errors.isEmpty()) {
            setFormErrorAttributes(req, errors, formData, "create", null);
            req.getRequestDispatcher("/WEB-INF/jsp/time-entries/form.jsp").forward(req, resp);
            return;
        }

        // Create the time entry
        TimeEntry entry = new TimeEntry();
        entry.setScName(formData.scName);
        entry.setDate(formData.date);
        entry.setHours(formData.hours);
        entry.setAccountName(formData.accountName);
        entry.setActivityType(formData.activityType);
        entry.setDescription(formData.description);

        timeEntryDao.create(entry);
        Span.current().setAttribute("time_entry.id", entry.getId());
        Span.current().setAttribute("time_entry.sc_name", entry.getScName());
        LOG.info("Created time entry id={} for sc={}", entry.getId(), entry.getScName());

        // PRG: redirect to the list page
        resp.sendRedirect(req.getContextPath() + "/time-entries");
    }

    /**
     * Handles time entry update with server-side validation.
     * On validation failure: re-renders form with error messages and preserved input.
     * On success: updates the entry and redirects (302) to the list page.
     * Returns 404 if the entry ID is invalid or the entry does not exist.
     */
    private void handleEdit(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Long id = parseIdParam(req);
        if (id == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Time entry not found.");
            return;
        }

        TimeEntry existing = timeEntryDao.findById(id);
        if (existing == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Time entry not found.");
            return;
        }

        List<String> errors = new ArrayList<>();
        FormData formData = extractAndValidateFormData(req, errors);

        // If there are validation errors, re-render the form with errors and preserved input
        if (!errors.isEmpty()) {
            setFormErrorAttributes(req, errors, formData, "edit", id);
            req.getRequestDispatcher("/WEB-INF/jsp/time-entries/form.jsp").forward(req, resp);
            return;
        }

        // Update the existing entry
        existing.setScName(formData.scName);
        existing.setDate(formData.date);
        existing.setHours(formData.hours);
        existing.setAccountName(formData.accountName);
        existing.setActivityType(formData.activityType);
        existing.setDescription(formData.description);

        timeEntryDao.update(existing);
        Span.current().setAttribute("time_entry.id", existing.getId());
        Span.current().setAttribute("time_entry.sc_name", existing.getScName());
        LOG.info("Updated time entry id={} for sc={}", existing.getId(), existing.getScName());

        // PRG: redirect to the list page
        resp.sendRedirect(req.getContextPath() + "/time-entries");
    }

    /**
     * Handles time entry deletion.
     * Returns 404 if the entry ID is invalid or the entry does not exist.
     * On success: deletes the entry and redirects (302) to the list page.
     */
    private void handleDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Long id = parseIdParam(req);
        if (id == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Time entry not found.");
            return;
        }

        TimeEntry existing = timeEntryDao.findById(id);
        if (existing == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Time entry not found.");
            return;
        }

        Span.current().setAttribute("time_entry.id", id);
        timeEntryDao.delete(id);
        LOG.info("Deleted time entry id={}", id);

        // PRG: redirect to the list page
        resp.sendRedirect(req.getContextPath() + "/time-entries");
    }

    /**
     * Parses the 'id' request parameter as a Long.
     * Returns null if the parameter is missing, empty, or not a valid long.
     */
    private Long parseIdParam(HttpServletRequest req) {
        String idStr = req.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(idStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extracts form data from the request and performs validation.
     * Validation errors are added to the provided errors list.
     */
    private FormData extractAndValidateFormData(HttpServletRequest req, List<String> errors) {
        FormData data = new FormData();
        data.scName = trimParam(req, "scName");
        data.dateStr = trimParam(req, "date");
        data.hoursStr = trimParam(req, "hours");
        data.accountName = trimParam(req, "accountName");
        data.activityTypeStr = trimParam(req, "activityType");
        data.description = trimParam(req, "description");

        // Validate required fields
        if (data.scName.isEmpty()) {
            errors.add("SC Name is required.");
        }
        if (data.dateStr.isEmpty()) {
            errors.add("Date is required.");
        }
        if (data.hoursStr.isEmpty()) {
            errors.add("Hours is required.");
        }
        if (data.accountName.isEmpty()) {
            errors.add("Account Name is required.");
        }
        if (data.activityTypeStr.isEmpty()) {
            errors.add("Activity Type is required.");
        }

        // Validate date format
        if (!data.dateStr.isEmpty()) {
            try {
                data.date = LocalDate.parse(data.dateStr);
            } catch (DateTimeParseException e) {
                errors.add("Date must be a valid date (YYYY-MM-DD).");
            }
        }

        // Validate hours: must be numeric, > 0, and <= 24
        if (!data.hoursStr.isEmpty()) {
            try {
                data.hours = new BigDecimal(data.hoursStr);
                if (data.hours.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add("Hours must be greater than 0.");
                } else if (data.hours.compareTo(new BigDecimal("24")) > 0) {
                    errors.add("Hours must not exceed 24.");
                }
            } catch (NumberFormatException e) {
                errors.add("Hours must be a valid number.");
            }
        }

        // Validate activity type
        if (!data.activityTypeStr.isEmpty()) {
            try {
                data.activityType = ActivityType.valueOf(data.activityTypeStr);
            } catch (IllegalArgumentException e) {
                errors.add("Invalid activity type.");
            }
        }

        return data;
    }

    /**
     * Sets request attributes for re-rendering the form with errors and preserved input.
     */
    private void setFormErrorAttributes(HttpServletRequest req, List<String> errors,
                                        FormData formData, String formMode, Long entryId) {
        req.setAttribute("errors", errors);
        req.setAttribute("activityTypes", ActivityType.values());
        req.setAttribute("formMode", formMode);
        if (entryId != null) {
            req.setAttribute("entryId", entryId);
        }
        req.setAttribute("scName", formData.scName);
        req.setAttribute("date", formData.dateStr);
        req.setAttribute("hours", formData.hoursStr);
        req.setAttribute("accountName", formData.accountName);
        req.setAttribute("activityType", formData.activityTypeStr);
        req.setAttribute("description", formData.description);
    }

    /**
     * Gets a trimmed request parameter, returning empty string if null.
     */
    private String trimParam(HttpServletRequest req, String name) {
        String value = req.getParameter(name);
        return value != null ? value.trim() : "";
    }

    /**
     * Internal data holder for form field values during validation.
     */
    private static class FormData {
        String scName;
        String dateStr;
        String hoursStr;
        String accountName;
        String activityTypeStr;
        String description;
        LocalDate date;
        BigDecimal hours;
        ActivityType activityType;
    }
}
