---
name: java-servlet-worker
description: Builds Java Servlet endpoints, JSP pages, DAOs, models, and tests for the SC-Tools application
---

# Java Servlet Worker

NOTE: Startup and cleanup are handled by `worker-base`. This skill defines the WORK PROCEDURE.

## When to Use This Skill

Use for features involving:
- Java Servlet endpoints (doGet/doPost handlers)
- JSP page creation and modification
- Data Access Objects (DAOs) and model classes
- H2 database schema and queries
- Maven project structure and dependencies
- JUnit 5 tests for all of the above

## Work Procedure

1. **Read the feature description** carefully. Understand preconditions, expected behavior, and verification steps.

2. **Check preconditions**: Read `.factory/services.yaml` for commands. Run `mvn compile` to ensure the project builds. If preconditions mention other features, verify those exist.

3. **Write tests (TDD where practical)**:
   - Create JUnit 5 test classes in `src/test/java/` mirroring the production package structure.
   - For DAO tests: use in-memory H2 (`jdbc:h2:mem:test`) with test schema setup. TDD Red-Green works well here.
   - For servlet integration tests: these require live Jetty + JSP compilation, so implementation-first is acceptable. Write tests immediately after the servlet/JSP code, not as a separate phase.
   - For scaffolding features: tests come after infrastructure is working.
   - Tests must cover: happy path, validation errors, edge cases, and error handling.
   - Run `mvn test` to verify all tests pass.

4. **Implement production code (Green phase)**:
   - Models in `src/main/java/.../model/`
   - DAOs in `src/main/java/.../dao/`
   - Servlets in `src/main/java/.../servlet/`
   - JSP pages in `src/main/webapp/WEB-INF/jsp/` (or `src/main/webapp/jsp/`)
   - CSS/JS in `src/main/webapp/static/`
   - Follow existing patterns in the codebase.

5. **Make tests pass (Green phase)**:
   - Run `mvn test` — all tests must pass.
   - Fix any compilation errors first, then test failures.

6. **Manual verification**:
   - Start the application: use the command from `.factory/services.yaml`
   - Use `curl` to verify HTTP endpoints return correct status codes and content.
   - Use `agent-browser` to verify JSP pages render correctly in a browser.
   - For each page: verify form fields, table data, navigation links, error messages.
   - Stop the application after verification.

7. **Run full validation suite**:
   - `mvn clean test` — all tests pass
   - `mvn clean package` — builds successfully
   - Check for no compilation warnings if possible

8. **Update shared state if needed**:
   - If you discovered patterns, gotchas, or conventions, add them to `.factory/library/architecture.md`
   - If you found environment issues, add to `.factory/library/environment.md`

## Key Conventions

- **Package structure**: `com.dash0.sctools.model`, `com.dash0.sctools.dao`, `com.dash0.sctools.servlet`
- **Servlet mapping**: Use `@WebServlet` annotations
- **JSP conventions**: Use JSTL tags for iteration/conditionals, EL expressions for data binding
- **Database**: Use JDBC directly (no ORM) with PreparedStatement for SQL injection prevention
- **HTML output escaping**: Always use `<c:out>` or `fn:escapeXml()` in JSP for XSS prevention
- **PRG pattern**: POST handlers should redirect (302/303) after successful mutations
- **Validation**: Server-side validation in servlets, re-render form with errors and preserved input on failure

## Example Handoff

```json
{
  "salientSummary": "Implemented SC Time Entry CRUD: created TimeEntry model, TimeEntryDao with H2 JDBC, TimeEntryServlet handling GET (list/form) and POST (create/update/delete), plus list.jsp and form.jsp. Ran 'mvn test' (12 passing), verified list page shows entries via curl (200 OK) and agent-browser (table renders correctly, form submits with redirect).",
  "whatWasImplemented": "TimeEntry model class, TimeEntryDao with CRUD methods using PreparedStatement, TimeEntryServlet with doGet/doPost handling list/create/edit/delete actions, list.jsp with JSTL table and empty state, form.jsp with validation error display and input preservation. Schema initialization in DatabaseInitializer.",
  "whatWasLeftUndone": "",
  "verification": {
    "commandsRun": [
      { "command": "mvn clean test", "exitCode": 0, "observation": "12 tests passed, 0 failures" },
      { "command": "mvn clean package", "exitCode": 0, "observation": "sc-tools-1.0.jar built successfully" },
      { "command": "curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/time-entries", "exitCode": 0, "observation": "200 - list page renders" },
      { "command": "curl -X POST -d 'scName=Test&date=2026-04-06&hours=2&accountName=Acme&activityType=DEMO' http://localhost:8090/time-entries -s -o /dev/null -w '%{http_code}'", "exitCode": 0, "observation": "302 - redirect after create" }
    ],
    "interactiveChecks": [
      { "action": "Opened http://localhost:8090/time-entries in agent-browser", "observed": "List page renders with table showing 1 entry. Edit and Delete links present per row." },
      { "action": "Clicked 'Add New Time Entry' button", "observed": "Form page loaded with all fields: SC Name, Date, Hours, Account Name, Activity Type dropdown, Description textarea." },
      { "action": "Submitted form with empty SC Name", "observed": "Form re-rendered with error message 'SC Name is required' and other fields preserved." }
    ]
  },
  "tests": {
    "added": [
      {
        "file": "src/test/java/com/dash0/sctools/dao/TimeEntryDaoTest.java",
        "cases": [
          { "name": "testCreateAndFindAll", "verifies": "Creating an entry persists it and findAll returns it" },
          { "name": "testFindById", "verifies": "Finding by ID returns correct entry" },
          { "name": "testUpdate", "verifies": "Updating an entry changes its fields" },
          { "name": "testDelete", "verifies": "Deleting an entry removes it from the database" }
        ]
      },
      {
        "file": "src/test/java/com/dash0/sctools/servlet/TimeEntryServletTest.java",
        "cases": [
          { "name": "testListPageReturns200", "verifies": "GET /time-entries returns 200" },
          { "name": "testCreateWithValidData", "verifies": "POST with valid data creates entry and redirects" },
          { "name": "testCreateWithMissingFields", "verifies": "POST with missing required fields returns form with errors" }
        ]
      }
    ]
  },
  "discoveredIssues": []
}
```

## When to Return to Orchestrator

- H2 database schema conflicts with existing tables
- Maven dependency conflicts that can't be resolved
- JSP rendering issues that require Jetty configuration changes beyond the feature scope
- Feature depends on another feature's servlet/DAO that doesn't exist yet
- Ambiguity in data model or validation rules
