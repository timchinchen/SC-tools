# Architecture

Architectural decisions, patterns, and conventions for SC-Tools.

**What belongs here:** Package structure, naming conventions, design patterns, technology decisions, gotchas discovered during implementation.
**What does NOT belong here:** Service ports/commands (use `.factory/services.yaml`), environment variables (use `environment.md`).

---

## Technology Stack
- Java 21 LTS
- Maven (build tool)
- Embedded Jetty (servlet container)
- H2 Database (embedded, file-based)
- JSP pages with JSTL + vanilla HTML/CSS/JS
- JUnit 5 (testing)

## Package Structure
```
com.dash0.sctools
├── Application.java          # Main class, Jetty setup
├── model/                    # POJOs (TimeEntry, Pov, PovCriteria)
├── dao/                      # Data Access Objects (JDBC + PreparedStatement)
├── servlet/                  # Servlet handlers
└── util/                     # Utilities (DatabaseInitializer, etc.)
```

## Web Structure
```
src/main/webapp/
├── WEB-INF/jsp/              # JSP pages
│   ├── layout/               # Header, footer, nav includes
│   ├── time-entries/          # Time entry list, form
│   ├── povs/                  # POV list, form, detail
│   └── dashboard.jsp          # Dashboard/home
└── static/                   # CSS, JS, images
```

## Key Patterns
- **PRG (Post-Redirect-Get)**: All POST handlers redirect after success to prevent form resubmission
- **Server-side validation**: Validate in servlets, re-render forms with errors and preserved input
- **XSS prevention**: Use `<c:out>` or `fn:escapeXml()` in all JSP output
- **SQL injection prevention**: Always use PreparedStatement, never string concatenation for SQL
- **Fat JAR**: Maven Shade plugin or Assembly plugin to create runnable JAR with embedded Jetty
