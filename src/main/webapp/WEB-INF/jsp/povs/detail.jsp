<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:include page="/WEB-INF/jsp/layout/header.jsp">
    <jsp:param name="pageTitle" value="POV Detail"/>
</jsp:include>

<div class="page-header">
    <h2><c:out value="${pov.name}"/></h2>
    <div class="actions">
        <a href="${pageContext.request.contextPath}/povs?action=edit&amp;id=${pov.id}" class="btn btn-primary">Edit POV</a>
        <form method="post" action="${pageContext.request.contextPath}/povs?action=delete&amp;id=${pov.id}" style="display:inline;" onsubmit="return confirm('Are you sure you want to delete this POV? All associated criteria will also be deleted.');">
            <button type="submit" class="btn btn-danger">Delete POV</button>
        </form>
    </div>
</div>

<div class="detail-card">
    <div class="detail-grid">
        <div class="detail-field">
            <span class="detail-label">Account Name</span>
            <span class="detail-value"><c:out value="${pov.accountName}"/></span>
        </div>
        <div class="detail-field">
            <span class="detail-label">SC Name</span>
            <span class="detail-value"><c:out value="${pov.scName}"/></span>
        </div>
        <div class="detail-field">
            <span class="detail-label">Status</span>
            <span class="detail-value">
                <span class="${badgeClass}"><c:out value="${statusDisplayName}"/></span>
            </span>
        </div>
        <div class="detail-field">
            <span class="detail-label">Start Date</span>
            <span class="detail-value"><c:out value="${pov.startDate}"/></span>
        </div>
        <div class="detail-field">
            <span class="detail-label">Target End Date</span>
            <span class="detail-value"><c:out value="${pov.targetEndDate}"/></span>
        </div>
        <c:if test="${not empty pov.description}">
            <div class="detail-field detail-field-full">
                <span class="detail-label">Description</span>
                <span class="detail-value"><c:out value="${pov.description}"/></span>
            </div>
        </c:if>
    </div>
</div>

<div class="criteria-section">
    <div class="page-header">
        <h3>Criteria</h3>
        <a href="${pageContext.request.contextPath}/criteria?action=new&amp;povId=${pov.id}" class="btn btn-primary btn-sm">Add Criteria</a>
    </div>

    <c:choose>
        <c:when test="${empty criteria}">
            <p class="empty-state">No criteria defined yet.</p>
        </c:when>
        <c:otherwise>
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>Status</th>
                        <th>Weight</th>
                        <th>Notes</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach var="criterion" items="${criteria}">
                        <tr>
                            <td><c:out value="${criterion.name}"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${criterion.status == 'NOT_STARTED'}">
                                        <span class="badge badge-not-started">Not Started</span>
                                    </c:when>
                                    <c:when test="${criterion.status == 'IN_PROGRESS'}">
                                        <span class="badge badge-in-progress">In Progress</span>
                                    </c:when>
                                    <c:when test="${criterion.status == 'MET'}">
                                        <span class="badge badge-met">Met</span>
                                    </c:when>
                                    <c:when test="${criterion.status == 'NOT_MET'}">
                                        <span class="badge badge-not-met">Not Met</span>
                                    </c:when>
                                    <c:when test="${criterion.status == 'PARTIALLY_MET'}">
                                        <span class="badge badge-partially-met">Partially Met</span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="badge"><c:out value="${criterion.status}"/></span>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td><c:out value="${criterion.weight}"/></td>
                            <td><c:out value="${criterion.notes}"/></td>
                            <td class="actions">
                                <a href="${pageContext.request.contextPath}/criteria?action=edit&amp;id=${criterion.id}&amp;povId=${pov.id}" class="btn btn-sm btn-secondary">Edit</a>
                                <form method="post" action="${pageContext.request.contextPath}/criteria?action=delete&amp;id=${criterion.id}&amp;povId=${pov.id}" style="display:inline;" onsubmit="return confirm('Are you sure you want to delete this criterion?');">
                                    <button type="submit" class="btn btn-sm btn-danger">Delete</button>
                                </form>
                            </td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </c:otherwise>
    </c:choose>
</div>

<div class="back-link" style="margin-top: 1.5rem;">
    <a href="${pageContext.request.contextPath}/povs">&laquo; Back to POV List</a>
</div>

<jsp:include page="/WEB-INF/jsp/layout/footer.jsp"/>
