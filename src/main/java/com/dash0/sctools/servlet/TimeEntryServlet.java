package com.dash0.sctools.servlet;

import com.dash0.sctools.dao.TimeEntryDao;
import com.dash0.sctools.model.TimeEntry;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Servlet handling time entry list display.
 * GET /time-entries — shows the list of all time entries (or empty state).
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
        LOG.debug("GET /time-entries");

        List<TimeEntry> entries = timeEntryDao.findAll();
        req.setAttribute("entries", entries);
        req.getRequestDispatcher("/WEB-INF/jsp/time-entries/list.jsp").forward(req, resp);
    }
}
