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

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Roadmap: Controller reports stored in PostgreSQL (JPA), referencing MongoDB restaurants by `camis` ID only
- Roadmap: Role field added to `UserEntity` (no new table); `ROLE_CUSTOMER` / `ROLE_CONTROLLER` convention with `hasRole()`
- Roadmap: Controller signup gated by env-var registration code (Docker Compose injection)
- Roadmap: Customer UI extends existing `ViewController` + Thymeleaf templates
- Roadmap: Photos stored on Docker named volume (`uploads_data:/app/uploads`) — no S3

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 1: `anyRequest().permitAll()` is currently active in `SecurityConfig` — must be replaced with explicit `antMatchers` before any feature code is written on top
- Phase 1: `ROLE_` prefix convention must be locked in (store `ROLE_CONTROLLER` / `ROLE_CUSTOMER`, always use `hasRole()`) before Phase 2 starts
- Phase 2: Docker named volume for photo uploads must be added to `docker-compose.yml` before the first photo upload test
- Phase 3: Verify whether a MongoDB index on `name` / `address.street` already exists before implementing `$regex` search

## Session Continuity

Last session: 2026-03-27
Stopped at: Roadmap written; no plans created yet
Resume file: None
