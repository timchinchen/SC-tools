<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:include page="/WEB-INF/jsp/layout/header.jsp">
    <jsp:param name="pageTitle" value="${formMode == 'edit' ? 'Edit Criterion' : 'New Criterion'}"/>
</jsp:include>

<div class="page-header">
    <h2><c:out value="${formMode == 'edit' ? 'Edit Criterion' : 'New Criterion'}"/></h2>
    <p class="subtitle">POV: <c:out value="${povName}"/></p>
</div>

<div class="form-container">

    <c:if test="${not empty errors}">
        <div class="error-summary">
            <strong>Please fix the following errors:</strong>
            <ul>
                <c:forEach var="error" items="${errors}">
                    <li><c:out value="${error}"/></li>
                </c:forEach>
            </ul>
        </div>
    </c:if>

    <c:choose>
        <c:when test="${formMode == 'edit'}">
            <form method="post" action="${pageContext.request.contextPath}/criteria?action=edit&amp;id=${criteriaId}&amp;povId=${povId}">
        </c:when>
        <c:otherwise>
            <form method="post" action="${pageContext.request.contextPath}/criteria?povId=${povId}">
        </c:otherwise>
    </c:choose>

        <div class="form-group">
            <label for="name">Name <span class="required">*</span></label>
            <input type="text" id="name" name="name" required
                   value="<c:out value='${name}'/>">
        </div>

        <div class="form-group">
            <label for="description">Description</label>
            <textarea id="description" name="description"><c:out value="${description}"/></textarea>
        </div>

        <div class="form-group">
            <label for="status">Status <span class="required">*</span></label>
            <select id="status" name="status" required>
                <c:forEach var="criteriaStatus" items="${criteriaStatuses}" varStatus="loop">
                    <option value="${criteriaStatus}"
                        <c:if test="${criteriaStatus == status}">selected</c:if>
                    ><c:out value="${criteriaStatusDisplayNames[loop.index]}"/></option>
                </c:forEach>
            </select>
        </div>

        <div class="form-group">
            <label for="weight">Weight (1-5) <span class="required">*</span></label>
            <select id="weight" name="weight" required>
                <option value="">-- Select Weight --</option>
                <c:forEach var="w" begin="1" end="5">
                    <c:set var="wStr" value="${w}"/>
                    <option value="${w}"
                        <c:if test="${fn:trim(wStr) eq fn:trim(weight)}">selected</c:if>
                    >${w}</option>
                </c:forEach>
            </select>
        </div>

        <div class="form-group">
            <label for="notes">Notes</label>
            <textarea id="notes" name="notes"><c:out value="${notes}"/></textarea>
        </div>

        <div class="form-actions">
            <button type="submit" class="btn btn-primary">
                <c:out value="${formMode == 'edit' ? 'Update Criterion' : 'Save Criterion'}"/>
            </button>
            <a href="${pageContext.request.contextPath}/povs?action=detail&amp;id=${povId}" class="btn btn-secondary">Cancel</a>
        </div>

    </form>
</div>

<jsp:include page="/WEB-INF/jsp/layout/footer.jsp"/>
