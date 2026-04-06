<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:include page="/WEB-INF/jsp/layout/header.jsp">
    <jsp:param name="pageTitle" value="Dashboard"/>
</jsp:include>

<div class="dashboard">
    <h2>Dashboard</h2>
    <p class="subtitle">Welcome to SC-Tools &mdash; your Solution Consultant Time Tracking &amp; POV Management hub.</p>

    <div class="dashboard-grid">
        <%-- SC Time Summary Section --%>
        <div class="dashboard-card">
            <h3>SC Time Tracking</h3>
            <c:choose>
                <c:when test="${timeEntryCount == 0}">
                    <p class="empty-state">No time entries recorded</p>
                </c:when>
                <c:otherwise>
                    <div class="summary-stats">
                        <div class="stat">
                            <span class="stat-value">${totalHours}</span>
                            <span class="stat-label">Total Hours Logged</span>
                        </div>
                        <div class="stat">
                            <span class="stat-value">${timeEntryCount}</span>
                            <span class="stat-label">Time Entries</span>
                        </div>
                    </div>
                </c:otherwise>
            </c:choose>
            <a href="${pageContext.request.contextPath}/time-entries" class="btn btn-primary">View Time Entries</a>
        </div>

        <%-- POV Summary Section --%>
        <div class="dashboard-card">
            <h3>POV Management</h3>
            <c:choose>
                <c:when test="${povCount == 0}">
                    <p class="empty-state">No POVs tracked</p>
                </c:when>
                <c:otherwise>
                    <div class="summary-stats">
                        <div class="stat">
                            <span class="stat-value">${povCount}</span>
                            <span class="stat-label">Total POVs</span>
                        </div>
                    </div>
                    <div class="pov-status-breakdown">
                        <h4>Status Breakdown</h4>
                        <ul class="status-list">
                            <li><span class="badge badge-planned">Planned</span> <span class="status-count">${povStatusCounts['PLANNED']}</span></li>
                            <li><span class="badge badge-in-progress">In Progress</span> <span class="status-count">${povStatusCounts['IN_PROGRESS']}</span></li>
                            <li><span class="badge badge-completed">Completed</span> <span class="status-count">${povStatusCounts['COMPLETED']}</span></li>
                            <li><span class="badge badge-won">Won</span> <span class="status-count">${povStatusCounts['WON']}</span></li>
                            <li><span class="badge badge-lost">Lost</span> <span class="status-count">${povStatusCounts['LOST']}</span></li>
                            <li><span class="badge badge-cancelled">Cancelled</span> <span class="status-count">${povStatusCounts['CANCELLED']}</span></li>
                        </ul>
                    </div>
                </c:otherwise>
            </c:choose>
            <a href="${pageContext.request.contextPath}/povs" class="btn btn-primary">View POVs</a>
        </div>
    </div>
</div>

<jsp:include page="/WEB-INF/jsp/layout/footer.jsp"/>
