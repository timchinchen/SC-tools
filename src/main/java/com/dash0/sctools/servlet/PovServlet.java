package com.dash0.sctools.servlet;

import com.dash0.sctools.dao.PovCriteriaDao;
import com.dash0.sctools.dao.PovDao;
import com.dash0.sctools.model.Pov;
import com.dash0.sctools.model.PovCriteria;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servlet handling POV CRUD operations.
 * GET  /povs                          — shows the list of all POVs (or empty state).
 * GET  /povs?action=new               — shows the create form.
 * GET  /povs?action=detail&id=N       — shows the POV detail page with criteria.
 * GET  /povs?action=edit&id=N         — shows the edit form pre-populated with POV data.
 * POST /povs                          — creates a new POV (with validation).
 * POST /povs?action=edit&id=N         — updates an existing POV (with validation).
 * POST /povs?action=delete&id=N       — deletes a POV (cascades to criteria).
 */
@WebServlet(urlPatterns = {"/povs"})
public class PovServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(PovServlet.class);

    /** Valid POV status values */
    private static final String[] POV_STATUSES = {
        "PLANNED", "IN_PROGRESS", "COMPLETED", "WON", "LOST", "CANCELLED"
    };

    /** Human-readable display names for POV statuses */
    private static final String[] POV_STATUS_DISPLAY_NAMES = {
        "Planned", "In Progress", "Completed", "Won", "Lost", "Cancelled"
    };

    private PovDao povDao;
    private PovCriteriaDao povCriteriaDao;

    /**
     * Default constructor uses production DAOs.
     */
    public PovServlet() {
        this.povDao = new PovDao();
        this.povCriteriaDao = new PovCriteriaDao();
    }

    /**
     * Constructor for testing with custom DAOs.
     */
    public PovServlet(PovDao povDao) {
        this.povDao = povDao;
        this.povCriteriaDao = new PovCriteriaDao();
    }

    /**
     * Constructor for testing with custom DAOs including criteria.
     */
    public PovServlet(PovDao povDao, PovCriteriaDao povCriteriaDao) {
        this.povDao = povDao;
        this.povCriteriaDao = povCriteriaDao;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");

        if ("new".equals(action)) {
            LOG.debug("GET /povs?action=new");
            showCreateForm(req, resp);
        } else if ("detail".equals(action)) {
            LOG.debug("GET /povs?action=detail");
            showDetail(req, resp);
        } else if ("edit".equals(action)) {
            LOG.debug("GET /povs?action=edit");
            showEditForm(req, resp);
        } else {
            LOG.debug("GET /povs");
            showList(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");

        if ("edit".equals(action)) {
            LOG.debug("POST /povs?action=edit");
            handleEdit(req, resp);
        } else if ("delete".equals(action)) {
            LOG.debug("POST /povs?action=delete");
            handleDelete(req, resp);
        } else {
            LOG.debug("POST /povs");
            handleCreate(req, resp);
        }
    }

    /**
     * Shows the POV list page.
     */
    private void showList(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        List<Pov> povs = povDao.findAll();
        req.setAttribute("povs", povs);
        req.getRequestDispatcher("/WEB-INF/jsp/povs/list.jsp").forward(req, resp);
    }

    /**
     * Shows the POV detail page with all fields, status badge, and criteria section.
     * Returns 404 if the POV ID is invalid or the POV does not exist.
     */
    private void showDetail(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Long id = parseIdParam(req);
        if (id == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        Pov pov = povDao.findById(id);
        if (pov == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        List<PovCriteria> criteria = povCriteriaDao.findByPovId(id);

        req.setAttribute("pov", pov);
        req.setAttribute("criteria", criteria);
        req.setAttribute("statusDisplayName", getStatusDisplayName(pov.getStatus()));
        req.setAttribute("badgeClass", getBadgeClass(pov.getStatus()));
        req.getRequestDispatcher("/WEB-INF/jsp/povs/detail.jsp").forward(req, resp);
    }

    /**
     * Shows the create POV form.
     */
    private void showCreateForm(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setAttribute("povStatuses", POV_STATUSES);
        req.setAttribute("povStatusDisplayNames", POV_STATUS_DISPLAY_NAMES);
        req.setAttribute("formMode", "create");
        // Default status is PLANNED
        req.setAttribute("status", "PLANNED");
        req.getRequestDispatcher("/WEB-INF/jsp/povs/form.jsp").forward(req, resp);
    }

    /**
     * Shows the edit POV form pre-populated with the POV's current values.
     * Returns 404 if the POV ID is invalid or the POV does not exist.
     */
    private void showEditForm(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Long id = parseIdParam(req);
        if (id == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        Pov pov = povDao.findById(id);
        if (pov == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        req.setAttribute("povStatuses", POV_STATUSES);
        req.setAttribute("povStatusDisplayNames", POV_STATUS_DISPLAY_NAMES);
        req.setAttribute("formMode", "edit");
        req.setAttribute("povId", pov.getId());
        req.setAttribute("name", pov.getName());
        req.setAttribute("accountName", pov.getAccountName());
        req.setAttribute("scName", pov.getScName());
        req.setAttribute("status", pov.getStatus());
        req.setAttribute("startDate", pov.getStartDate() != null ? pov.getStartDate().toString() : "");
        req.setAttribute("targetEndDate", pov.getTargetEndDate() != null ? pov.getTargetEndDate().toString() : "");
        req.setAttribute("description", pov.getDescription() != null ? pov.getDescription() : "");

        req.getRequestDispatcher("/WEB-INF/jsp/povs/form.jsp").forward(req, resp);
    }

    /**
     * Handles POV creation with server-side validation.
     * On validation failure: re-renders form with error messages and preserved input.
     * On success: creates the POV and redirects (302) to the list page.
     */
    private void handleCreate(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        List<String> errors = new ArrayList<>();
        FormData formData = extractAndValidateFormData(req, errors);

        // If there are validation errors, re-render the form with errors and preserved input
        if (!errors.isEmpty()) {
            setFormErrorAttributes(req, errors, formData, "create", null);
            req.getRequestDispatcher("/WEB-INF/jsp/povs/form.jsp").forward(req, resp);
            return;
        }

        // Create the POV
        Pov pov = new Pov();
        pov.setName(formData.name);
        pov.setAccountName(formData.accountName);
        pov.setScName(formData.scName);
        pov.setStatus(formData.status);
        pov.setStartDate(formData.startDate);
        pov.setTargetEndDate(formData.targetEndDate);
        pov.setDescription(formData.description);

        povDao.create(pov);
        LOG.info("Created POV id={} name='{}'", pov.getId(), pov.getName());

        // PRG: redirect to the list page
        resp.sendRedirect(req.getContextPath() + "/povs");
    }

    /**
     * Handles POV update with server-side validation.
     * On validation failure: re-renders form with error messages and preserved input.
     * On success: updates the POV and redirects (302) to the detail page.
     * Returns 404 if the POV ID is invalid or the POV does not exist.
     */
    private void handleEdit(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Long id = parseIdParam(req);
        if (id == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        Pov existing = povDao.findById(id);
        if (existing == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        List<String> errors = new ArrayList<>();
        FormData formData = extractAndValidateFormData(req, errors);

        // If there are validation errors, re-render the form with errors and preserved input
        if (!errors.isEmpty()) {
            setFormErrorAttributes(req, errors, formData, "edit", id);
            req.getRequestDispatcher("/WEB-INF/jsp/povs/form.jsp").forward(req, resp);
            return;
        }

        // Update the existing POV
        existing.setName(formData.name);
        existing.setAccountName(formData.accountName);
        existing.setScName(formData.scName);
        existing.setStatus(formData.status);
        existing.setStartDate(formData.startDate);
        existing.setTargetEndDate(formData.targetEndDate);
        existing.setDescription(formData.description);

        povDao.update(existing);
        LOG.info("Updated POV id={} name='{}'", existing.getId(), existing.getName());

        // PRG: redirect to the detail page
        resp.sendRedirect(req.getContextPath() + "/povs?action=detail&id=" + id);
    }

    /**
     * Handles POV deletion. Cascades to criteria via the database ON DELETE CASCADE.
     * Returns 404 if the POV ID is invalid or the POV does not exist.
     * On success: deletes the POV and redirects (302) to the list page.
     */
    private void handleDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Long id = parseIdParam(req);
        if (id == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        Pov existing = povDao.findById(id);
        if (existing == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        povDao.delete(id);
        LOG.info("Deleted POV id={}", id);

        // PRG: redirect to the list page
        resp.sendRedirect(req.getContextPath() + "/povs");
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
        data.name = trimParam(req, "name");
        data.accountName = trimParam(req, "accountName");
        data.scName = trimParam(req, "scName");
        data.status = trimParam(req, "status");
        data.startDateStr = trimParam(req, "startDate");
        data.targetEndDateStr = trimParam(req, "targetEndDate");
        data.description = trimParam(req, "description");

        // Validate required fields
        if (data.name.isEmpty()) {
            errors.add("Name is required.");
        }
        if (data.accountName.isEmpty()) {
            errors.add("Account Name is required.");
        }
        if (data.scName.isEmpty()) {
            errors.add("SC Name is required.");
        }
        if (data.startDateStr.isEmpty()) {
            errors.add("Start Date is required.");
        }
        if (data.targetEndDateStr.isEmpty()) {
            errors.add("Target End Date is required.");
        }

        // Validate status is one of the allowed values
        if (!data.status.isEmpty()) {
            boolean validStatus = false;
            for (String s : POV_STATUSES) {
                if (s.equals(data.status)) {
                    validStatus = true;
                    break;
                }
            }
            if (!validStatus) {
                errors.add("Invalid status value.");
            }
        } else {
            // Default to PLANNED if empty
            data.status = "PLANNED";
        }

        // Validate date format
        if (!data.startDateStr.isEmpty()) {
            try {
                data.startDate = LocalDate.parse(data.startDateStr);
            } catch (DateTimeParseException e) {
                errors.add("Start Date must be a valid date (YYYY-MM-DD).");
            }
        }

        if (!data.targetEndDateStr.isEmpty()) {
            try {
                data.targetEndDate = LocalDate.parse(data.targetEndDateStr);
            } catch (DateTimeParseException e) {
                errors.add("Target End Date must be a valid date (YYYY-MM-DD).");
            }
        }

        // Validate target end date >= start date
        if (data.startDate != null && data.targetEndDate != null) {
            if (data.targetEndDate.isBefore(data.startDate)) {
                errors.add("Target End Date must not be before Start Date.");
            }
        }

        return data;
    }

    /**
     * Sets request attributes for re-rendering the form with errors and preserved input.
     */
    private void setFormErrorAttributes(HttpServletRequest req, List<String> errors,
                                        FormData formData, String formMode, Long povId) {
        req.setAttribute("errors", errors);
        req.setAttribute("povStatuses", POV_STATUSES);
        req.setAttribute("povStatusDisplayNames", POV_STATUS_DISPLAY_NAMES);
        req.setAttribute("formMode", formMode);
        if (povId != null) {
            req.setAttribute("povId", povId);
        }
        req.setAttribute("name", formData.name);
        req.setAttribute("accountName", formData.accountName);
        req.setAttribute("scName", formData.scName);
        req.setAttribute("status", formData.status);
        req.setAttribute("startDate", formData.startDateStr);
        req.setAttribute("targetEndDate", formData.targetEndDateStr);
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
     * Returns the CSS badge class for a given POV status.
     * This is made available as a static utility for use in JSPs if needed.
     */
    public static String getBadgeClass(String status) {
        if (status == null) return "badge";
        return switch (status) {
            case "PLANNED" -> "badge badge-planned";
            case "IN_PROGRESS" -> "badge badge-in-progress";
            case "COMPLETED" -> "badge badge-completed";
            case "WON" -> "badge badge-won";
            case "LOST" -> "badge badge-lost";
            case "CANCELLED" -> "badge badge-cancelled";
            default -> "badge";
        };
    }

    /**
     * Returns a human-readable display name for a POV status.
     */
    public static String getStatusDisplayName(String status) {
        if (status == null) return "";
        return switch (status) {
            case "PLANNED" -> "Planned";
            case "IN_PROGRESS" -> "In Progress";
            case "COMPLETED" -> "Completed";
            case "WON" -> "Won";
            case "LOST" -> "Lost";
            case "CANCELLED" -> "Cancelled";
            default -> status;
        };
    }

    /**
     * Returns a human-readable display name for a criteria status.
     */
    public static String getCriteriaStatusDisplayName(String status) {
        if (status == null) return "";
        return switch (status) {
            case "NOT_STARTED" -> "Not Started";
            case "IN_PROGRESS" -> "In Progress";
            case "MET" -> "Met";
            case "NOT_MET" -> "Not Met";
            case "PARTIALLY_MET" -> "Partially Met";
            default -> status;
        };
    }

    /**
     * Returns the CSS badge class for a given criteria status.
     */
    public static String getCriteriaBadgeClass(String status) {
        if (status == null) return "badge";
        return switch (status) {
            case "NOT_STARTED" -> "badge badge-not-started";
            case "IN_PROGRESS" -> "badge badge-in-progress";
            case "MET" -> "badge badge-met";
            case "NOT_MET" -> "badge badge-not-met";
            case "PARTIALLY_MET" -> "badge badge-partially-met";
            default -> "badge";
        };
    }

    /**
     * Internal data holder for form field values during validation.
     */
    private static class FormData {
        String name;
        String accountName;
        String scName;
        String status;
        String startDateStr;
        String targetEndDateStr;
        String description;
        LocalDate startDate;
        LocalDate targetEndDate;
    }
}
