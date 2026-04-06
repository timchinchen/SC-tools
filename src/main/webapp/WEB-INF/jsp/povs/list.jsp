<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:include page="/WEB-INF/jsp/layout/header.jsp">
    <jsp:param name="pageTitle" value="POVs"/>
</jsp:include>

<div class="page-header">
    <h2>POVs</h2>
    <a href="${pageContext.request.contextPath}/povs?action=new" class="btn btn-primary">Create New POV</a>
</div>

<c:choose>
    <c:when test="${empty povs}">
        <p class="empty-state">No POVs yet.</p>
    </c:when>
    <c:otherwise>
        <table class="data-table">
            <thead>
                <tr>
                    <th>Name</th>
                    <th>Account Name</th>
                    <th>SC Name</th>
                    <th>Status</th>
                    <th>Start Date</th>
                    <th>Target End Date</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="pov" items="${povs}">
                    <tr>
                        <td>
                            <a href="${pageContext.request.contextPath}/povs?action=detail&amp;id=${pov.id}">
                                <c:out value="${pov.name}"/>
                            </a>
                        </td>
                        <td><c:out value="${pov.accountName}"/></td>
                        <td><c:out value="${pov.scName}"/></td>
                        <td>
                            <c:choose>
                                <c:when test="${pov.status == 'PLANNED'}">
                                    <span class="badge badge-planned">Planned</span>
                                </c:when>
                                <c:when test="${pov.status == 'IN_PROGRESS'}">
                                    <span class="badge badge-in-progress">In Progress</span>
                                </c:when>
                                <c:when test="${pov.status == 'COMPLETED'}">
                                    <span class="badge badge-completed">Completed</span>
                                </c:when>
                                <c:when test="${pov.status == 'WON'}">
                                    <span class="badge badge-won">Won</span>
                                </c:when>
                                <c:when test="${pov.status == 'LOST'}">
                                    <span class="badge badge-lost">Lost</span>
                                </c:when>
                                <c:when test="${pov.status == 'CANCELLED'}">
                                    <span class="badge badge-cancelled">Cancelled</span>
                                </c:when>
                                <c:otherwise>
                                    <span class="badge"><c:out value="${pov.status}"/></span>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td><c:out value="${pov.startDate}"/></td>
                        <td><c:out value="${pov.targetEndDate}"/></td>
                        <td class="actions">
                            <a href="${pageContext.request.contextPath}/povs?action=edit&amp;id=${pov.id}" class="btn btn-sm btn-secondary">Edit</a>
                            <form method="post" action="${pageContext.request.contextPath}/povs?action=delete&amp;id=${pov.id}" style="display:inline;" onsubmit="return confirm('Are you sure you want to delete this POV?');">
                                <button type="submit" class="btn btn-sm btn-danger">Delete</button>
                            </form>
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </c:otherwise>
</c:choose>

<jsp:include page="/WEB-INF/jsp/layout/footer.jsp"/>
