# User Testing

Testing surface, tools, and resource cost classification.

**What belongs here:** Validation surface findings, testing tool configuration, resource cost estimates, concurrency limits.

---

## Validation Surface

### Primary Surface: Web Browser
- URL: http://localhost:8090
- Tool: `agent-browser`
- Pages to test: /dashboard, /time-entries, /time-entries (create form), /povs, /povs/{id} (detail), /povs (create form), criteria forms
- Auth: None (no authentication in v1)

### Supplementary: HTTP API (curl)
- Direct curl to servlet endpoints for status code verification
- Useful for quick smoke tests and redirect verification

## Validation Concurrency

### agent-browser (primary surface)
- **App cost**: ~300-500 MB per JVM (embedded Jetty + H2)
- **Browser cost**: ~200-400 MB per headless Chromium instance
- **Per-validator total**: ~700 MB
- **Machine**: 32 GB RAM, 8 cores, ~11.3 GB available headroom
- **CPU note**: Load average was ~9.2 on 8 cores during dry run (high)
- **70% headroom**: ~7.9 GB usable
- **Max concurrent**: **4 validators** (constrained by CPU more than RAM)

## Testing Notes
- Application must be started before validation (use `services.yaml` app service)
- H2 database state persists between tests — validators may need to account for existing data
- No authentication — all pages are publicly accessible
- The app uses POST-Redirect-GET pattern — follow redirects when testing with curl
- **agent-browser is NOT available** due to npm cache permission issues (root-owned files in ~/.npm/_cacache). sudo requires password and is not available non-interactively. Use `curl` as the primary testing tool instead. curl can verify HTTP status codes, follow redirects, inspect HTML content, and verify form fields.

## Flow Validator Guidance: Web Browser

### Environment
- App URL: http://localhost:8090
- Tool: `agent-browser` (invoke via Skill tool at session start)
- No authentication required — all pages are public
- JAVA_HOME: /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

### Key URLs
- Dashboard: http://localhost:8090/dashboard (also http://localhost:8090/)
- Time Entries List: http://localhost:8090/time-entries
- Time Entry Create Form: http://localhost:8090/time-entries?action=new
- Time Entry Edit Form: http://localhost:8090/time-entries?action=edit&id={id}

### Isolation Rules
- **Shared H2 database**: All validators share the same database. Entries created by one validator are visible to others.
- **Empty state testing**: If a validator needs empty state (VAL-TIME-001), it MUST run before any other validator creates data. The orchestrator ensures this by running empty-state tests in a group that runs first.
- **Data identification**: Each validator should use identifiable names (e.g., include "GroupA" or "GroupB" in SC Name) to distinguish its data from other validators' data.
- **No cleanup needed**: Validators don't need to clean up their test data — the database will be wiped between validation rounds if needed.

### Form Fields for Time Entry
- SC Name (text input, required)
- Date (date input, required)
- Hours (number input, step=0.25, required)
- Account Name (text input, required)
- Activity Type (select dropdown with 9 options: DEMO, DISCOVERY, POV_WORK, TECHNICAL_DEEP_DIVE, WORKSHOP, INTERNAL, TRAINING, ADMIN, OTHER)
- Description (textarea, optional)

### Activity Type Enum Values
DEMO, DISCOVERY, POV_WORK, TECHNICAL_DEEP_DIVE, WORKSHOP, INTERNAL, TRAINING, ADMIN, OTHER

### Verification Tips
- For redirect verification: check that after form submission, the browser navigates to the list page
- For validation errors: look for error messages on the form page (form is re-rendered with errors)
- For delete confirmation: JavaScript confirm() dialog appears — agent-browser can handle dialog acceptance/dismissal
- For non-existent ID: navigate to /time-entries?action=edit&id=99999 and verify 404 or friendly error page
- POST forms: verify form method="post" in DOM
- XSS: all user text should be HTML-escaped in output (use <c:out> in JSP)

## Flow Validator Guidance: curl

### Environment
- App URL: http://localhost:8090
- Tool: `curl` (all assertions tested via HTTP requests)
- No authentication required — all pages are public
- Use `-L` to follow redirects, `-D -` to dump headers, `-s` for silent mode

### Key URLs for POV Management
- POV List: http://localhost:8090/povs
- POV Create Form: http://localhost:8090/povs?action=new
- POV Detail: http://localhost:8090/povs?action=detail&id={id}
- POV Edit Form: http://localhost:8090/povs?action=edit&id={id}
- Criteria Create: http://localhost:8090/criteria?action=new&povId={id}
- Criteria Edit: http://localhost:8090/criteria?action=edit&id={id}&povId={povId}
- Dashboard: http://localhost:8090/dashboard

### POV Status Enum
PLANNED, IN_PROGRESS, COMPLETED, WON, LOST, CANCELLED

### Criteria Status Enum
NOT_STARTED, IN_PROGRESS, MET, NOT_MET, PARTIALLY_MET

### Testing Patterns
- **Create POV via POST**: `curl -s -D - -X POST http://localhost:8090/povs -d "name=TestPOV&accountName=TestCorp&scName=TestSC&status=PLANNED&startDate=2026-01-01&targetEndDate=2026-06-30&description=Test"`
- **Follow redirect to get created ID**: Look for Location header after POST 302 redirect
- **Verify HTML content**: Pipe curl output through grep to check for expected text
- **Check empty state**: Before creating any data, verify POV list shows empty message
- **XSS test**: Submit `<script>alert(1)</script>` as name and verify it's escaped in output (shows &lt; instead of <)
- **SQL injection test**: Submit `'; DROP TABLE povs; --` and verify no error occurs

### Isolation Rules
- **Shared H2 database**: All validators share the same database
- **Empty state group MUST run first**: Before any other validator creates data
- **Data identification**: Use unique prefixes in names (e.g., "G2-TestPOV", "G3-CritPOV")
- **Order matters**: Group 1 (empty state) → then Groups 2-5 can run in parallel if using unique data
