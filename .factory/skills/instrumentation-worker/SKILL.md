---
name: instrumentation-worker
description: Integrates OpenTelemetry Java Agent and Dash0 Web SDK for observability instrumentation
---

# Instrumentation Worker

NOTE: Startup and cleanup are handled by `worker-base`. This skill defines the WORK PROCEDURE.

## When to Use This Skill

Use for features involving:
- OpenTelemetry Java Agent setup and configuration
- Dash0 Web SDK (`@dash0/sdk-web`) integration in JSP pages
- Custom span attributes for business context
- Telemetry export configuration (OTLP to OTel Collector)
- Observability-related Maven dependencies

## Work Procedure

1. **Read the feature description** carefully. Understand what instrumentation is expected and the verification approach.

2. **Check preconditions**: The application must be buildable and runnable. Run `mvn clean package` to verify. The OTel Collector must be running on localhost:4317 (check with `curl -sf http://localhost:13133/`).

3. **Write tests FIRST (Red phase)**:
   - For Java Agent configuration: write tests that verify the agent configuration properties are set correctly.
   - For custom span instrumentation: write tests using OpenTelemetry's test SDK (`opentelemetry-sdk-testing`) to verify custom attributes are added to spans.
   - For Dash0 Web SDK: write tests that verify the JSP includes contain the SDK script tag.
   - Run `mvn test` — new tests should FAIL.

4. **Implement instrumentation**:
   - **Java Agent**: Download the `opentelemetry-javaagent.jar`, configure it in the application startup script/class. Set environment variables: `OTEL_SERVICE_NAME=sc-tools`, `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317`, `OTEL_EXPORTER_OTLP_PROTOCOL=grpc`.
   - **Custom attributes**: Use the OpenTelemetry API (`io.opentelemetry:opentelemetry-api`) to add custom span attributes in servlets (e.g., `Span.current().setAttribute("pov.name", povName)`).
   - **Dash0 Web SDK**: Add the SDK script tag to the JSP layout/header include. Configure it to export to the OTel Collector's HTTP endpoint (`http://localhost:4318`).
   - **Maven dependencies**: Add `opentelemetry-api` as a compile dependency. The Java Agent JAR is a runtime-only artifact (not a Maven dependency).

5. **Make tests pass (Green phase)**:
   - Run `mvn test` — all tests pass.

6. **Manual verification (CRITICAL for instrumentation)**:
   - Start the application WITH the Java Agent attached.
   - Verify agent startup logs appear: grep for `[otel.javaagent]` in output.
   - Make several HTTP requests (browse pages, create entries).
   - Check OTel Collector logs for received spans (look for `sc-tools` service name).
   - Open browser DevTools → Network tab → verify Dash0 Web SDK sends telemetry.
   - Verify trace parent-child relationships: servlet spans should have JDBC child spans.
   - Record ALL observations in the handoff.

7. **Run full validation suite**:
   - `mvn clean test` — all tests pass
   - `mvn clean package` — builds successfully
   - Application starts with agent and serves pages correctly

## Key Technical Details

- **Java Agent JAR location**: Store in project root or `lib/` directory. Reference via `-javaagent:` flag.
- **Agent auto-instruments**: Jetty (servlet spans), JDBC/H2 (database spans), java.net.HttpURLConnection
- **OTel API for custom attributes**: `Span.current().setAttribute("key", "value")` — requires `opentelemetry-api` dependency
- **Dash0 Web SDK**: Include via CDN `<script>` tag or download locally. Initialize with configuration object specifying collector endpoint.
- **OTLP endpoints**: Backend uses gRPC on 4317, Frontend uses HTTP on 4318

## Example Handoff

```json
{
  "salientSummary": "Integrated OTel Java Agent with Jetty startup, configured OTLP/gRPC export to localhost:4317. Added Dash0 Web SDK to JSP header include with export to localhost:4318. Verified: agent attaches (startup logs confirm), servlet spans generated for all endpoints, H2 JDBC spans visible as children, Dash0 SDK loads in browser and sends page load spans. Custom pov.name attribute added to POV servlet spans.",
  "whatWasImplemented": "OTel Java Agent download and startup integration in Application.java (-javaagent flag). Environment variables for service name, exporter endpoint. OpenTelemetry API dependency in pom.xml. Custom span attributes in PovServlet and TimeEntryServlet. Dash0 Web SDK script tag in header.jsp include with initialization config. Updated startup script to include agent.",
  "whatWasLeftUndone": "",
  "verification": {
    "commandsRun": [
      { "command": "mvn clean package", "exitCode": 0, "observation": "Build successful with OTel API dependency" },
      { "command": "java -javaagent:lib/opentelemetry-javaagent.jar -jar target/sc-tools.jar (first 20 lines)", "exitCode": 0, "observation": "[otel.javaagent] opentelemetry-javaagent - version: 2.x.x, service.name=sc-tools" },
      { "command": "curl http://localhost:8090/dashboard", "exitCode": 0, "observation": "200 OK, page loads normally with agent running" },
      { "command": "curl http://localhost:13133/", "exitCode": 0, "observation": "OTel Collector health check OK" }
    ],
    "interactiveChecks": [
      { "action": "Opened http://localhost:8090/dashboard in agent-browser, checked page source", "observed": "Dash0 Web SDK script tag present in HTML head. SDK initialized without console errors." },
      { "action": "Navigated to time entries, created an entry, checked browser Network tab", "observed": "Browser sent OTLP POST to localhost:4318 with page load and interaction spans." },
      { "action": "Checked OTel Collector docker logs after traffic", "observed": "Collector received spans with service.name=sc-tools, HTTP and DB spans visible." }
    ]
  },
  "tests": {
    "added": [
      {
        "file": "src/test/java/com/dash0/sctools/servlet/InstrumentationTest.java",
        "cases": [
          { "name": "testCustomPovAttribute", "verifies": "POV servlet sets pov.name span attribute" },
          { "name": "testCustomTimeEntryAttribute", "verifies": "TimeEntry servlet sets sc.name span attribute" }
        ]
      }
    ]
  },
  "discoveredIssues": []
}
```

## When to Return to Orchestrator

- OTel Collector is not running or not reachable on 4317/4318
- Java Agent causes application startup failures that can't be resolved
- Dash0 Web SDK CDN is unreachable and no local copy exists
- Agent conflicts with application code (classloading issues)
- Need to modify OTel Collector configuration (out of scope)
