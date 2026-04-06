<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:include page="/WEB-INF/jsp/layout/header.jsp">
    <jsp:param name="pageTitle" value="New Time Entry"/>
</jsp:include>

<div class="page-header">
    <h2>New Time Entry</h2>
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

    <form method="post" action="${pageContext.request.contextPath}/time-entries">

        <div class="form-group">
            <label for="scName">SC Name <span class="required">*</span></label>
            <input type="text" id="scName" name="scName" required
                   value="<c:out value='${scName}'/>">
        </div>

        <div class="form-group">
            <label for="date">Date <span class="required">*</span></label>
            <input type="date" id="date" name="date" required
                   value="<c:out value='${date}'/>">
        </div>

        <div class="form-group">
            <label for="hours">Hours <span class="required">*</span></label>
            <input type="number" id="hours" name="hours" step="0.25" required
                   value="<c:out value='${hours}'/>">
        </div>

        <div class="form-group">
            <label for="accountName">Account Name <span class="required">*</span></label>
            <input type="text" id="accountName" name="accountName" required
                   value="<c:out value='${accountName}'/>">
        </div>

        <div class="form-group">
            <label for="activityType">Activity Type <span class="required">*</span></label>
            <select id="activityType" name="activityType" required>
                <option value="">-- Select Activity Type --</option>
                <c:forEach var="type" items="${activityTypes}">
                    <option value="${type.name()}"
                        <c:if test="${type.name() == activityType}">selected</c:if>
                    ><c:out value="${type.displayName}"/></option>
                </c:forEach>
            </select>
        </div>

        <div class="form-group">
            <label for="description">Description</label>
            <textarea id="description" name="description"><c:out value="${description}"/></textarea>
        </div>

        <div class="form-actions">
            <button type="submit" class="btn btn-primary">Save Time Entry</button>
            <a href="${pageContext.request.contextPath}/time-entries" class="btn btn-secondary">Cancel</a>
        </div>

    </form>
</div>

<jsp:include page="/WEB-INF/jsp/layout/footer.jsp"/>
