<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:include page="/WEB-INF/jsp/layout/header.jsp">
    <jsp:param name="pageTitle" value="${formMode == 'edit' ? 'Edit POV' : 'New POV'}"/>
</jsp:include>

<div class="page-header">
    <h2><c:out value="${formMode == 'edit' ? 'Edit POV' : 'New POV'}"/></h2>
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
            <form method="post" action="${pageContext.request.contextPath}/povs?action=edit&amp;id=${povId}">
        </c:when>
        <c:otherwise>
            <form method="post" action="${pageContext.request.contextPath}/povs">
        </c:otherwise>
    </c:choose>

        <div class="form-group">
            <label for="name">Name <span class="required">*</span></label>
            <input type="text" id="name" name="name" required
                   value="<c:out value='${name}'/>">
        </div>

        <div class="form-group">
            <label for="accountName">Account Name <span class="required">*</span></label>
            <input type="text" id="accountName" name="accountName" required
                   value="<c:out value='${accountName}'/>">
        </div>

        <div class="form-group">
            <label for="scName">SC Name <span class="required">*</span></label>
            <input type="text" id="scName" name="scName" required
                   value="<c:out value='${scName}'/>">
        </div>

        <div class="form-group">
            <label for="status">Status <span class="required">*</span></label>
            <select id="status" name="status" required>
                <c:forEach var="povStatus" items="${povStatuses}" varStatus="loop">
                    <option value="${povStatus}"
                        <c:if test="${povStatus == status}">selected</c:if>
                    ><c:out value="${povStatusDisplayNames[loop.index]}"/></option>
                </c:forEach>
            </select>
        </div>

        <div class="form-group">
            <label for="startDate">Start Date <span class="required">*</span></label>
            <input type="date" id="startDate" name="startDate" required
                   value="<c:out value='${startDate}'/>">
        </div>

        <div class="form-group">
            <label for="targetEndDate">Target End Date <span class="required">*</span></label>
            <input type="date" id="targetEndDate" name="targetEndDate" required
                   value="<c:out value='${targetEndDate}'/>">
        </div>

        <div class="form-group">
            <label for="description">Description</label>
            <textarea id="description" name="description"><c:out value="${description}"/></textarea>
        </div>

        <div class="form-actions">
            <button type="submit" class="btn btn-primary">
                <c:out value="${formMode == 'edit' ? 'Update POV' : 'Save POV'}"/>
            </button>
            <c:choose>
                <c:when test="${formMode == 'edit'}">
                    <a href="${pageContext.request.contextPath}/povs?action=detail&amp;id=${povId}" class="btn btn-secondary">Cancel</a>
                </c:when>
                <c:otherwise>
                    <a href="${pageContext.request.contextPath}/povs" class="btn btn-secondary">Cancel</a>
                </c:otherwise>
            </c:choose>
        </div>

    </form>
</div>

<jsp:include page="/WEB-INF/jsp/layout/footer.jsp"/>
