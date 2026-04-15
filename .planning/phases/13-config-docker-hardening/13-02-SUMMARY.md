---
phase: 13-config-docker-hardening
plan: 02
subsystem: infra
tags: [docker, java25, alpine, non-root, security, dockerfile, dockerignore]

# Dependency graph
requires: []
provides:
  - "Dockerfile upgraded to Java 25 Alpine two-stage build (maven:3.9-eclipse-temurin-25 builder, eclipse-temurin:25-jre-alpine runtime)"
  - "Non-root appuser created and activated in runtime container (DOCKER-05)"
  - ".dockerignore excluding .env, .git/, target/, .planning/ while retaining src/ and pom.xml for builder stage (DOCKER-06)"
affects: [14-ci-pipeline, deployment, docker-security]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Alpine BusyBox user creation: addgroup -S appuser && adduser -S appuser -G appuser (NOT useradd)"
    - "USER directive placed after COPY to avoid permission issues on copied JAR"
    - ".dockerignore retains src/ and pom.xml for multi-stage builder; excludes secrets and artifacts"

key-files:
  created: [".dockerignore"]
  modified: ["Dockerfile"]

key-decisions:
  - "Use eclipse-temurin:25-jre-alpine (JRE only, Alpine) over eclipse-temurin:25-jdk-alpine — smaller attack surface, matches pom.xml compiler target"
  - "Alpine BusyBox adduser syntax required (not useradd) — Alpine has no /usr/sbin/useradd"
  - "USER appuser placed after COPY --from=builder to avoid file ownership issues on app.jar"
  - ".dockerignore excludes *.md to keep documentation out of image layers"

patterns-established:
  - "Non-root container user pattern: addgroup -S + adduser -S -G on Alpine"
  - "Multi-stage .dockerignore: exclude build artifacts and secrets, retain src/ and pom.xml"

requirements-completed: [DOCKER-04, DOCKER-05, DOCKER-06]

# Metrics
duration: 2min
completed: 2026-04-12
---

# Phase 13 Plan 02: Dockerfile Hardening Summary

**Two-stage Java 25 Alpine Dockerfile with non-root appuser, fixing UnsupportedClassVersionError and adding ASVS container hardening**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-12T07:46:36Z
- **Completed:** 2026-04-12T07:49:16Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Fixed blocking deployment bug: `UnsupportedClassVersionError` caused by Java 21 runtime vs Java 25 bytecode — upgraded both stages to Java 25
- Added non-root container user (`appuser`) using Alpine BusyBox syntax; activated via `USER appuser` before `ENTRYPOINT` (DOCKER-05)
- Created `.dockerignore` to prevent secrets (`.env`), git history (`.git/`), and planning artifacts (`.planning/`) from entering Docker build context (DOCKER-06)
- Docker build verified: `docker compose build app` exits 0 with the updated Dockerfile and .dockerignore

## Task Commits

Each task was committed atomically:

1. **Task 1: Upgrade Dockerfile to Java 25 Alpine with non-root user** - `04c1f2d` (feat)
2. **Task 2: Create .dockerignore for lean build context** - `d05ba67` (chore)

## Files Created/Modified
- `Dockerfile` - Two-stage build: maven:3.9-eclipse-temurin-25 builder, eclipse-temurin:25-jre-alpine runtime, non-root appuser
- `.dockerignore` - Excludes target/, .git/, .planning/, .env, *.log, docker-compose*.yml; retains src/ and pom.xml

## Decisions Made
- Used `eclipse-temurin:25-jre-alpine` (JRE-only) over JDK — smaller image, reduced attack surface
- Alpine BusyBox `addgroup -S`/`adduser -S` syntax chosen explicitly (Debian `useradd` does not exist on Alpine)
- `USER appuser` placed after `COPY --from=builder` — ensures the copied app.jar is owned correctly before the user switch
- `.dockerignore` excludes `*.md` to keep documentation out of image layers

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Dockerfile now correctly targets Java 25; `docker compose build app` passes
- Non-root container runtime established (DOCKER-05 satisfied)
- Build context is lean — secrets and planning artifacts excluded (DOCKER-06 satisfied)
- Phase 13 Plan 03 (docker-compose hardening) can proceed without Dockerfile blockers

---
*Phase: 13-config-docker-hardening*
*Completed: 2026-04-12*

## Self-Check: PASSED

- FOUND: Dockerfile
- FOUND: .dockerignore
- FOUND: .planning/phases/13-config-docker-hardening/13-02-SUMMARY.md
- FOUND commit: 04c1f2d (Task 1 — Dockerfile upgrade)
- FOUND commit: d05ba67 (Task 2 — .dockerignore)
