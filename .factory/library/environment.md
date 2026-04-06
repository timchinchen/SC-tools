# Environment

Environment variables, external dependencies, and setup notes.

**What belongs here:** Required env vars, external API keys/services, dependency quirks, platform-specific notes.
**What does NOT belong here:** Service ports/commands (use `.factory/services.yaml`).

---

## Java Setup
- JDK 21 installed via Homebrew: `brew install openjdk@21`
- JAVA_HOME: `/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
- Maven installed via Homebrew: `brew install maven`

## H2 Database
- Embedded, file-based at `./data/sctools` (creates `sctools.mv.db`)
- JDBC URL: `jdbc:h2:./data/sctools;AUTO_SERVER=TRUE`
- No username/password required for local development
- Schema auto-initialized on startup via DatabaseInitializer

## OTel Collector (Pre-existing)
- Running on localhost:4317 (gRPC) and localhost:4318 (HTTP)
- Health check: http://localhost:13133/
- Do NOT start/stop/configure — managed externally

## OpenTelemetry Java Agent
- Download from: https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases
- Store at: `lib/opentelemetry-javaagent.jar`
- Attach via: `-javaagent:lib/opentelemetry-javaagent.jar`
- Key env vars:
  - `OTEL_SERVICE_NAME=sc-tools`
  - `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317`
  - `OTEL_EXPORTER_OTLP_PROTOCOL=grpc`

## Dash0 Web SDK
- NPM package: `@dash0/sdk-web`
- Can also be loaded via CDN script tag in JSP pages
- Exports to OTel Collector HTTP endpoint: `http://localhost:4318`
