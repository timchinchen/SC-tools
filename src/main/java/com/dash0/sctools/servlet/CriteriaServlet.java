package com.dash0.sctools.servlet;

import com.dash0.sctools.dao.PovCriteriaDao;
import com.dash0.sctools.dao.PovDao;
import com.dash0.sctools.model.Pov;
import com.dash0.sctools.model.PovCriteria;
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servlet handling POV Criteria CRUD operations.
 * GET  /criteria?action=new&povId=N             — shows the create form.
 * GET  /criteria?action=edit&id=N&povId=N       — shows the edit form pre-populated with criteria data.
 * POST /criteria?povId=N                        — creates a new criterion (with validation).
 * POST /criteria?action=edit&id=N&povId=N       — updates an existing criterion (with validation).
 * POST /criteria?action=delete&id=N&povId=N     — deletes a criterion.
 *
 * All redirects navigate back to the parent POV's detail page.
 */
@WebServlet(urlPatterns = {"/criteria"})
public class CriteriaServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(CriteriaServlet.class);

    /** Valid criteria status values */
    private static final String[] CRITERIA_STATUSES = {
        "NOT_STARTED", "IN_PROGRESS", "MET", "NOT_MET", "PARTIALLY_MET"
    };

    /** Human-readable display names for criteria statuses */
    private static final String[] CRITERIA_STATUS_DISPLAY_NAMES = {
        "Not Started", "In Progress", "Met", "Not Met", "Partially Met"
    };

    private PovCriteriaDao criteriaDao;
    private PovDao povDao;

    /**
     * Default constructor uses production DAOs.
     */
    public CriteriaServlet() {
        this.criteriaDao = new PovCriteriaDao();
        this.povDao = new PovDao();
    }

    /**
     * Constructor for testing with custom DAOs.
     */
    public CriteriaServlet(PovCriteriaDao criteriaDao, PovDao povDao) {
        this.criteriaDao = criteriaDao;
        this.povDao = povDao;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");

        if ("new".equals(action)) {
            LOG.debug("GET /criteria?action=new");
            showCreateForm(req, resp);
        } else if ("edit".equals(action)) {
            LOG.debug("GET /criteria?action=edit");
            showEditForm(req, resp);
        } else {
            // No list page for criteria; redirect to POVs list
            resp.sendRedirect(req.getContextPath() + "/povs");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");

        if ("edit".equals(action)) {
            LOG.debug("POST /criteria?action=edit");
            handleEdit(req, resp);
        } else if ("delete".equals(action)) {
            LOG.debug("POST /criteria?action=delete");
            handleDelete(req, resp);
        } else {
            LOG.debug("POST /criteria");
            handleCreate(req, resp);
        }
    }

    /**
     * Shows the create criteria form.
     * Requires a valid povId parameter pointing to an existing POV.
     */
    private void showCreateForm(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Long povId = parseLongParam(req, "povId");
        if (povId == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        Pov pov = povDao.findById(povId);
        if (pov == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        req.setAttribute("criteriaStatuses", CRITERIA_STATUSES);
        req.setAttribute("criteriaStatusDisplayNames", CRITERIA_STATUS_DISPLAY_NAMES);
        req.setAttribute("formMode", "create");
        req.setAttribute("povId", povId);
        req.setAttribute("povName", pov.getName());
        // Default status is NOT_STARTED
        req.setAttribute("status", "NOT_STARTED");
        req.getRequestDispatcher("/WEB-INF/jsp/criteria/form.jsp").forward(req, resp);
    }

    /**
     * Shows the edit criteria form pre-populated with the criterion's current values.
     * Returns 404 if the criterion ID or POV ID is invalid or the criterion does not exist.
     */
    private void showEditForm(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Long id = parseLongParam(req, "id");
        Long povId = parseLongParam(req, "povId");
        if (id == null || povId == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Criterion not found.");
            return;
        }

        PovCriteria criteria = criteriaDao.findById(id);
        if (criteria == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Criterion not found.");
            return;
        }

        Pov pov = povDao.findById(povId);
        if (pov == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        req.setAttribute("criteriaStatuses", CRITERIA_STATUSES);
        req.setAttribute("criteriaStatusDisplayNames", CRITERIA_STATUS_DISPLAY_NAMES);
        req.setAttribute("formMode", "edit");
        req.setAttribute("criteriaId", criteria.getId());
        req.setAttribute("povId", povId);
        req.setAttribute("povName", pov.getName());
        req.setAttribute("name", criteria.getName());
        req.setAttribute("description", criteria.getDescription() != null ? criteria.getDescription() : "");
        req.setAttribute("status", criteria.getStatus());
        req.setAttribute("weight", String.valueOf(criteria.getWeight()));
        req.setAttribute("notes", criteria.getNotes() != null ? criteria.getNotes() : "");

        req.getRequestDispatcher("/WEB-INF/jsp/criteria/form.jsp").forward(req, resp);
    }

    /**
     * Handles criteria creation with server-side validation.
     * On validation failure: re-renders form with error messages and preserved input.
     * On success: creates the criterion and redirects (302) to the POV detail page.
     */
    private void handleCreate(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Long povId = parseLongParam(req, "povId");
        if (povId == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        Pov pov = povDao.findById(povId);
        if (pov == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        List<String> errors = new ArrayList<>();
        CriteriaFormData formData = extractAndValidateFormData(req, errors);

        // If there are validation errors, re-render the form with errors and preserved input
        if (!errors.isEmpty()) {
            setFormErrorAttributes(req, errors, formData, "create", null, povId, pov.getName());
            req.getRequestDispatcher("/WEB-INF/jsp/criteria/form.jsp").forward(req, resp);
            return;
        }

        // Create the criterion
        PovCriteria criteria = new PovCriteria();
        criteria.setPovId(povId);
        criteria.setName(formData.name);
        criteria.setDescription(formData.description);
        criteria.setStatus(formData.status);
        criteria.setWeight(formData.weight);
        criteria.setNotes(formData.notes);

        criteriaDao.create(criteria);
        Span.current().setAttribute("criteria.id", criteria.getId());
        Span.current().setAttribute("criteria.name", criteria.getName());
        Span.current().setAttribute("criteria.pov_id", povId);
        LOG.info("Created criterion id={} name='{}' for POV id={}", criteria.getId(), criteria.getName(), povId);

        // PRG: redirect to the POV detail page
        resp.sendRedirect(req.getContextPath() + "/povs?action=detail&id=" + povId);
    }

    /**
     * Handles criteria update with server-side validation.
     * On validation failure: re-renders form with error messages and preserved input.
     * On success: updates the criterion and redirects (302) to the POV detail page.
     */
    private void handleEdit(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Long id = parseLongParam(req, "id");
        Long povId = parseLongParam(req, "povId");
        if (id == null || povId == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Criterion not found.");
            return;
        }

        PovCriteria existing = criteriaDao.findById(id);
        if (existing == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Criterion not found.");
            return;
        }

        Pov pov = povDao.findById(povId);
        if (pov == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "POV not found.");
            return;
        }

        List<String> errors = new ArrayList<>();
        CriteriaFormData formData = extractAndValidateFormData(req, errors);

        // If there are validation errors, re-render the form with errors and preserved input
        if (!errors.isEmpty()) {
            setFormErrorAttributes(req, errors, formData, "edit", id, povId, pov.getName());
            req.getRequestDispatcher("/WEB-INF/jsp/criteria/form.jsp").forward(req, resp);
            return;
        }

        // Update the existing criterion
        existing.setName(formData.name);
        existing.setDescription(formData.description);
        existing.setStatus(formData.status);
        existing.setWeight(formData.weight);
        existing.setNotes(formData.notes);

        criteriaDao.update(existing);
        Span.current().setAttribute("criteria.id", existing.getId());
        Span.current().setAttribute("criteria.name", existing.getName());
        Span.current().setAttribute("criteria.pov_id", povId);
        LOG.info("Updated criterion id={} name='{}' for POV id={}", existing.getId(), existing.getName(), povId);

        // PRG: redirect to the POV detail page
        resp.sendRedirect(req.getContextPath() + "/povs?action=detail&id=" + povId);
    }

    /**
     * Handles criteria deletion.
     * Returns 404 if the criterion ID or POV ID is invalid or the criterion does not exist.
     * On success: deletes the criterion and redirects (302) to the POV detail page.
     */
    private void handleDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Long id = parseLongParam(req, "id");
        Long povId = parseLongParam(req, "povId");
        if (id == null || povId == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Criterion not found.");
            return;
        }

        PovCriteria existing = criteriaDao.findById(id);
        if (existing == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Criterion not found.");
            return;
        }

        Span.current().setAttribute("criteria.id", id);
        Span.current().setAttribute("criteria.pov_id", povId);
        criteriaDao.delete(id);
        LOG.info("Deleted criterion id={} from POV id={}", id, povId);

        // PRG: redirect to the POV detail page
        resp.sendRedirect(req.getContextPath() + "/povs?action=detail&id=" + povId);
    }

    /**
     * Parses a named request parameter as a Long.
     * Returns null if the parameter is missing, empty, or not a valid long.
     */
    private Long parseLongParam(HttpServletRequest req, String paramName) {
        String value = req.getParameter(paramName);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extracts form data from the request and performs validation.
     * Validation errors are added to the provided errors list.
     */
    private CriteriaFormData extractAndValidateFormData(HttpServletRequest req, List<String> errors) {
        CriteriaFormData data = new CriteriaFormData();
        data.name = trimParam(req, "name");
        data.description = trimParam(req, "description");
        data.status = trimParam(req, "status");
        data.weightStr = trimParam(req, "weight");
        data.notes = trimParam(req, "notes");

        // Validate required fields
        if (data.name.isEmpty()) {
            errors.add("Name is required.");
        }

        // Validate weight: required, must be 1-5
        if (data.weightStr.isEmpty()) {
            errors.add("Weight is required.");
        } else {
            try {
                data.weight = Integer.parseInt(data.weightStr);
                if (data.weight < 1 || data.weight > 5) {
                    errors.add("Weight must be between 1 and 5.");
                }
            } catch (NumberFormatException e) {
                errors.add("Weight must be a valid number between 1 and 5.");
            }
        }

        // Validate status is one of the allowed values
        if (!data.status.isEmpty()) {
            boolean validStatus = false;
            for (String s : CRITERIA_STATUSES) {
                if (s.equals(data.status)) {
                    validStatus = true;
                    break;
                }
            }
            if (!validStatus) {
                errors.add("Invalid status value.");
            }
        } else {
            // Default to NOT_STARTED if empty
            data.status = "NOT_STARTED";
        }

        return data;
    }

    /**
     * Sets request attributes for re-rendering the form with errors and preserved input.
     */
    private void setFormErrorAttributes(HttpServletRequest req, List<String> errors,
                                        CriteriaFormData formData, String formMode,
                                        Long criteriaId, Long povId, String povName) {
        req.setAttribute("errors", errors);
        req.setAttribute("criteriaStatuses", CRITERIA_STATUSES);
        req.setAttribute("criteriaStatusDisplayNames", CRITERIA_STATUS_DISPLAY_NAMES);
        req.setAttribute("formMode", formMode);
        if (criteriaId != null) {
            req.setAttribute("criteriaId", criteriaId);
        }
        req.setAttribute("povId", povId);
        req.setAttribute("povName", povName);
        req.setAttribute("name", formData.name);
        req.setAttribute("description", formData.description);
        req.setAttribute("status", formData.status);
        req.setAttribute("weight", formData.weightStr);
        req.setAttribute("notes", formData.notes);
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
    private static class CriteriaFormData {
        String name;
        String description;
        String status;
        String weightStr;
        int weight;
        String notes;
    }
}
