---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: planning
stopped_at: Phase 1 execution complete — all 4 plans done, 33/33 tests passing
last_updated: "2026-03-29T18:21:16.576Z"
last_activity: 2026-03-27 — Roadmap created, Phase 1 ready for planning
progress:
  total_phases: 4
  completed_phases: 1
  total_plans: 4
  completed_plans: 4
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-27)

**Core value:** A customer can search any NYC restaurant and immediately know whether it's clean — and a controller can document new hygiene findings against the same data.
**Current focus:** Phase 1 — Role Infrastructure

## Current Position

Phase: 1 of 4 (Role Infrastructure)
Plan: 0 of TBD in current phase
Status: Ready to plan
Last activity: 2026-03-27 — Roadmap created, Phase 1 ready for planning

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: -
- Total execution time: -

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**
- Last 5 plans: -
- Trend: -

*Updated after each plan completion*
| Phase 01-role-infrastructure P04 | 2 | 1 tasks | 2 files |
| Phase 01-role-infrastructure P01-01 | 15 | 1 tasks | 5 files |
| Phase 01-role-infrastructure P02 | 90 | 2 tasks | 4 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Roadmap: Controller reports stored in PostgreSQL (JPA), referencing MongoDB restaurants by `camis` ID only
- Roadmap: Role field added to `UserEntity` (no new table); `ROLE_CUSTOMER` / `ROLE_CONTROLLER` convention with `hasRole()`
- Roadmap: Controller signup gated by env-var registration code (Docker Compose injection)
- Roadmap: Customer UI extends existing `ViewController` + Thymeleaf templates
- Roadmap: Photos stored on Docker named volume (`uploads_data:/app/uploads`) — no S3
- [Phase 01-04]: Constructor injection chosen over @Autowired field injection for DataSeeder (testability, immutability)
- [Phase 01-04]: Idempotent seed pattern: findByUsername().isPresent() guard before every save() in ApplicationRunner
- [Phase 01-role-infrastructure]: ROLE_CUSTOMER / ROLE_CONTROLLER locked in as registration roles; ROLE_USER removed entirely
- [Phase 01-role-infrastructure]: Controller signup is fail-safe: CONTROLLER_SIGNUP_CODE env var absent means any signupCode returns HTTP 400
- [Phase 01-role-infrastructure]: Mockito upgraded to 5.x, Byte Buddy to 1.14.18 to support static mocking on Java 21+ runtime
- [Phase 01-02]: Abandoned @WebMvcTest for SecurityConfigTest on Java 25: Mockito agent attachment crashes JVM. Used JUnit 4 + AnnotationConfigWebApplicationContext + standaloneSetup instead.
- [Phase 01-02]: SecurityConfig registered before SecurityAutoConfiguration in test context to prevent duplicate FilterChain from @ConditionalOnDefaultWebSecurity.
- [Phase 01-02]: FilterRegistrationBean.setEnabled(false) prevents JwtAuthenticationFilter double-registration as servlet filter.

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 1: `anyRequest().permitAll()` is currently active in `SecurityConfig` — must be replaced with explicit `antMatchers` before any feature code is written on top
- Phase 1: `ROLE_` prefix convention must be locked in (store `ROLE_CONTROLLER` / `ROLE_CUSTOMER`, always use `hasRole()`) before Phase 2 starts
- Phase 2: Docker named volume for photo uploads must be added to `docker-compose.yml` before the first photo upload test
- Phase 3: Verify whether a MongoDB index on `name` / `address.street` already exists before implementing `$regex` search

## Session Continuity

Last session: 2026-03-29T18:21:16.572Z
Stopped at: Phase 1 execution complete — all 4 plans done, 33/33 tests passing
Resume file: .planning/phases/01-role-infrastructure/01-04-SUMMARY.md
