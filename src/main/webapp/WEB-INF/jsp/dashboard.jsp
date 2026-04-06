<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:include page="/WEB-INF/jsp/layout/header.jsp">
    <jsp:param name="pageTitle" value="Dashboard"/>
</jsp:include>

<div class="dashboard">
    <h2>Dashboard</h2>
    <p class="subtitle">Welcome to SC-Tools — your Solution Consultant Time Tracking &amp; POV Management hub.</p>

    <div class="dashboard-grid">
        <div class="dashboard-card">
            <h3>SC Time Tracking</h3>
            <p class="empty-state">No time entries yet. Start tracking your time!</p>
            <a href="${pageContext.request.contextPath}/time-entries" class="btn btn-primary">View Time Entries</a>
        </div>

        <div class="dashboard-card">
            <h3>POV Management</h3>
            <p class="empty-state">No POVs yet. Create your first Proof of Value project!</p>
            <a href="${pageContext.request.contextPath}/povs" class="btn btn-primary">View POVs</a>
        </div>
    </div>
</div>

<jsp:include page="/WEB-INF/jsp/layout/footer.jsp"/>
