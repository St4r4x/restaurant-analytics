---
phase: 15-github-actions-ci-pipeline
plan: "01"
subsystem: infra
tags: [jacoco, maven, docker, github-actions, ci, coverage, ghcr]

# Dependency graph
requires:
  - phase: 14-testcontainers-integration-tests
    provides: full test suite (165 Surefire + 19 IT) that JaCoCo now instruments
provides:
  - JaCoCo 0.8.12 plugin in pom.xml with prepare-agent/report/check executions
  - target/site/jacoco/jacoco.xml generated on every mvn test run
  - OCI LABEL in Dockerfile linking image to St4r4x/restaurant-analytics on GHCR
  - CI badge in README.md pointing to ci.yml workflow
affects:
  - 15-02 (ci.yml workflow reads jacoco.xml via madraphos/jacoco-report action)

# Tech tracking
tech-stack:
  added:
    - jacoco-maven-plugin 0.8.12
  patterns:
    - JaCoCo argLine late-binding via @{argLine} property (already in pom.xml — Surefire/Failsafe use it; JaCoCo prepend-agent overwrites the property at initialize phase)
    - Coverage threshold set at baseline-5% to tolerate minor drift (38% = 43% baseline - 5%)
    - Exclusion paths use com/aflokkat/ forward-slash wildcard format

key-files:
  created: []
  modified:
    - pom.xml
    - Dockerfile
    - README.md

key-decisions:
  - "JaCoCo threshold set at 38% (baseline 43% from Phase 12 minus 5% tolerance margin)"
  - "Exclusions cover dto/entity/aggregation/domain — pure data holders with no business logic"
  - "OCI LABEL uses exact repository URL St4r4x/restaurant-analytics for GHCR package linking"

patterns-established:
  - "JaCoCo exclusion paths: com/aflokkat/{package}/** (forward slash, wildcard)"
  - "argLine property empty by default; JaCoCo overwrites at initialize phase before Surefire test phase"

requirements-completed:
  - CI-05
  - CI-08
  - CI-09

# Metrics
duration: 2min
completed: 2026-04-12
---

# Phase 15 Plan 01: JaCoCo + OCI Label + CI Badge Prerequisites Summary

**JaCoCo 0.8.12 added to pom.xml with 38% coverage gate, OCI LABEL added to Dockerfile for GHCR push authorization, and CI badge added to README.md**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-04-12T20:35:00Z
- **Completed:** 2026-04-12T20:36:34Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Added jacoco-maven-plugin 0.8.12 with three execution bindings: prepare-agent (initialize phase), report (test phase), check (test phase with 38% INSTRUCTION coverage threshold)
- Excluded pure data-holder packages (dto, entity, aggregation, domain) using correct `com/aflokkat/` paths
- `mvn test -q` exits 0 and generates `target/site/jacoco/jacoco.xml` — prerequisite for CI-08 (madraphos/jacoco-report action)
- Added `LABEL org.opencontainers.image.source=https://github.com/St4r4x/restaurant-analytics` immediately before `EXPOSE 8080` — required for GHCR package linking and GITHUB_TOKEN push authorization
- Added CI badge `![CI](https://github.com/St4r4x/restaurant-analytics/actions/workflows/ci.yml/badge.svg)` on line 3 of README.md (after heading, before description)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add JaCoCo plugin to pom.xml and OCI LABEL to Dockerfile** - `c46064f` (feat)
2. **Task 2: Add CI badge to README.md** - `3209943` (feat)

## Files Created/Modified

- `pom.xml` - Added jacoco-maven-plugin 0.8.12 block (57 lines) after maven-failsafe-plugin closing tag
- `Dockerfile` - Inserted OCI LABEL line before EXPOSE 8080 (line 31, EXPOSE at line 34)
- `README.md` - Inserted CI badge on line 3 between heading and description paragraph

## Decisions Made

- Coverage threshold set at 38% (not 40% or 50%) — measured baseline from Phase 12 was 43%; minus-5% tolerance chosen to survive minor drift without blocking CI on unrelated commits
- JaCoCo version 0.8.12 specified explicitly — Spring Boot 2.6.15 BOM does not manage `jacoco-maven-plugin`, so version must be pinned
- Four packages excluded from coverage measurement (dto, entity, aggregation, domain) — all are pure Java POJOs/records with no business logic; instrumenting them would inflate the denominator without meaningful quality signal
- Exclusion paths confirmed as `com/aflokkat/` (not `com/st4r4x/` — the old package name pre-rename)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. `mvn test -q` passed immediately on first run after adding the JaCoCo plugin. Coverage threshold (38%) was met without adjustment — actual coverage exceeded the threshold.

## Threat Surface Verification

T-15-01-02 (Elevation of Privilege — OCI LABEL): Verified label references exact repository URL `https://github.com/St4r4x/restaurant-analytics`. `grep "org.opencontainers.image.source=https://github.com/St4r4x/restaurant-analytics" Dockerfile` returns exactly one match.

## User Setup Required

None - no external service configuration required. The GHCR push authorization (OCI LABEL) takes effect automatically on first `docker push` from the CI workflow.

## Next Phase Readiness

- Phase 15 Plan 02 (ci.yml workflow) can now proceed: `target/site/jacoco/jacoco.xml` exists, Dockerfile has OCI LABEL, README has badge
- The jacoco-check goal enforces a 38% coverage floor — Plan 02 must not add test exclusions that would drop below this threshold
- `@{argLine}` late-binding in Surefire/Failsafe already in place — JaCoCo prepare-agent will prepend its `-javaagent` flag correctly

---
*Phase: 15-github-actions-ci-pipeline*
*Completed: 2026-04-12*

## Self-Check: PASSED

| Item | Status |
|------|--------|
| pom.xml exists | FOUND |
| Dockerfile exists | FOUND |
| README.md exists | FOUND |
| 15-01-SUMMARY.md exists | FOUND |
| Commit c46064f exists | FOUND |
| Commit 3209943 exists | FOUND |
