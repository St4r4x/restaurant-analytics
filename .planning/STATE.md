---
gsd_state_version: 1.0
milestone: v3.0
milestone_name: phases
status: planning
stopped_at: Phase 13 context gathered
last_updated: "2026-04-12T07:12:25.413Z"
last_activity: 2026-04-12
progress:
  total_phases: 10
  completed_phases: 2
  total_plans: 4
  completed_plans: 4
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-11)

**Core value:** A customer can search any NYC restaurant and immediately know whether it's clean — and a controller can document new hygiene findings against the same data.
**Current focus:** Milestone v3.0 — Production Readiness

## Current Position

Phase: 13
Plan: Not started
Status: Roadmap created, ready to plan Phase 11
Last activity: 2026-04-12

Progress: [░░░░░░░░░░] 0%

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.

Carried over from v2.0:

- anyRequest().permitAll() with client-side IIFE guards for /admin, /dashboard — browser navigation does not send Authorization header
- uploadPhoto uses raw fetch() (not fetchWithAuth) — fetchWithAuth sets Content-Type: application/json, corrupts multipart boundary
- ADMIN role separate from CONTROLLER — Admin-specific signup code, separate DataSeeder seed user
- Mockito mock(Authentication.class) fails on Java 25 — use UsernamePasswordAuthenticationToken concrete class instead
- mockStatic(AppConfig.class) causes VerifyError on Java 25 — use reflection to patch AppConfig.properties static field in tests

New for v3.0 (from research):

- Testcontainers must stay at 1.x (1.19.8) — 2.x dropped JUnit 4 support; project uses junit-vintage-engine
- logstash-logback-encoder pinned at 7.3 — 7.4+ dropped Logback 1.2.x support; Spring Boot 2.6.15 ships Logback 1.2.12
- Bucket4j must stay at 7.6.1 — 8.11.0+ requires JDK 17
- Playwright pinned at 1.49.0 — upgrade only if CI browser install fails
- JaCoCo argLine must use late-binding @{argLine} form — literal string causes JaCoCo to silently overwrite the Mockito ByteBuddy flag, causing StackOverflowError on all controller tests
- AppConfig.getProperty() must check System.getProperty() before env vars — required for Testcontainers to inject URIs before MongoClientFactory static singleton initializes
- CORS requires both CorsConfigurationSource bean AND http.cors(withDefaults()) in SecurityConfig — either alone causes OPTIONS preflight 403
- Playwright E2E auth: call /api/auth/login via APIRequestContext, extract accessToken, inject via addInitScript() — storageState() does not work for localStorage JWT
- GHCR push requires OCI LABEL org.opencontainers.image.source in Dockerfile — absent label causes permission_denied after first unlinked push
- NYC_API_MAX_RECORDS=200 in E2E CI compose — caps sync to ~10s, prevents timeout

### Pending Todos

None yet.

### Blockers/Concerns

- JaCoCo coverage baseline is unknown — must run `mvn jacoco:report` as first action in Phase 12 before enabling check goal; set threshold to baseline - 5% if below 60%
- Dockerfile current state unknown — must read before Phase 13 to avoid overwriting an already-correct multi-stage setup

## Session Continuity

Last session: 2026-04-12T07:12:25.409Z
Stopped at: Phase 13 context gathered
Resume file: .planning/phases/13-config-docker-hardening/13-CONTEXT.md
