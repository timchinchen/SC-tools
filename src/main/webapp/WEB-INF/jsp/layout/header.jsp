<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SC-Tools - ${param.pageTitle != null ? param.pageTitle : 'Solution Consultant Tools'}</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/style.css">
    <script src="https://cdn.dash0.com/sdk-web/latest/dash0-sdk-web.min.js"
            data-dash0-otel-collector-url="http://localhost:4318"
            data-dash0-service-name="sc-tools-frontend"></script>
</head>
<body>
    <header class="site-header">
        <div class="header-container">
            <h1 class="site-title">
                <a href="${pageContext.request.contextPath}/dashboard">SC-Tools</a>
            </h1>
            <nav class="main-nav">
                <ul>
                    <li><a href="${pageContext.request.contextPath}/dashboard" class="nav-link">Dashboard</a></li>
                    <li><a href="${pageContext.request.contextPath}/time-entries" class="nav-link">Time Entries</a></li>
                    <li><a href="${pageContext.request.contextPath}/povs" class="nav-link">POVs</a></li>
                </ul>
            </nav>
        </div>
    </header>
    <main class="content-container">
