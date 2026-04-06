<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<jsp:include page="/WEB-INF/jsp/layout/header.jsp">
    <jsp:param name="pageTitle" value="Time Entries"/>
</jsp:include>

<div class="page-header">
    <h2>Time Entries</h2>
    <a href="${pageContext.request.contextPath}/time-entries?action=new" class="btn btn-primary">Add New Time Entry</a>
</div>

<c:choose>
    <c:when test="${empty entries}">
        <p class="empty-state">No time entries yet.</p>
    </c:when>
    <c:otherwise>
        <table class="data-table">
            <thead>
                <tr>
                    <th>SC Name</th>
                    <th>Date</th>
                    <th>Hours</th>
                    <th>Account Name</th>
                    <th>Activity Type</th>
                    <th>Description</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
                <c:forEach var="entry" items="${entries}">
                    <tr>
                        <td><c:out value="${entry.scName}"/></td>
                        <td><c:out value="${entry.date}"/></td>
                        <td><fmt:formatNumber value="${entry.hours}" minFractionDigits="0" maxFractionDigits="2"/></td>
                        <td><c:out value="${entry.accountName}"/></td>
                        <td><c:out value="${entry.activityType.displayName}"/></td>
                        <td><c:out value="${entry.description}"/></td>
                        <td class="actions">
                            <a href="${pageContext.request.contextPath}/time-entries?action=edit&amp;id=${entry.id}" class="btn btn-sm btn-secondary">Edit</a>
                            <form method="post" action="${pageContext.request.contextPath}/time-entries?action=delete&amp;id=${entry.id}" style="display:inline;" onsubmit="return confirm('Are you sure you want to delete this time entry?');">
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
