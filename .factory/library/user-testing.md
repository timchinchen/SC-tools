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
