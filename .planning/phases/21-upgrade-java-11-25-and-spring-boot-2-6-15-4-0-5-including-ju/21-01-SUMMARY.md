---
phase: 21-upgrade-java-11-25-and-spring-boot-2-6-15-4-0-5-including-ju
plan: 01
subsystem: infra
tags: [spring-boot, maven, java, jacoco, springdoc, logstash, testcontainers, junit5]

# Dependency graph
requires:
  - phase: 14-testcontainers-integration-tests
    provides: Testcontainers deps + JaCoCo plugin already in pom.xml
  - phase: 15-github-actions-ci-pipeline
    provides: JaCoCo coverage reporting setup
provides:
  - Spring Boot 4.0.5 parent declared in pom.xml
  - Java 25 compiler target declared
  - springdoc-openapi-starter-webmvc-ui:2.8.6 (Boot 3+/4+ compatible artifact)
  - logstash-logback-encoder:8.1 (Logback 1.5.x compatible)
  - JUnit 4 explicit artifacts removed (junit:junit:4.13.2, junit-vintage-engine)
  - JaCoCo exclusions corrected to com/st4r4x/ package paths
  - Deprecated Boot 3+ properties removed from application.properties files
affects:
  - 21-02 (javax→jakarta migration: compile errors expected after this plan)
  - 21-03 (Spring Security 6 API update)
  - 21-04 (JUnit 4→5 test migration)
  - 21-05 (MongoDB properties prefix rename)

# Tech tracking
tech-stack:
  added:
    - spring-boot-starter-parent:4.0.5
    - springdoc-openapi-starter-webmvc-ui:2.8.6
    - logstash-logback-encoder:8.1
  patterns:
    - All dependency versions explicitly pinned (no LATEST/RELEASE)
    - JaCoCo exclusions use correct com/st4r4x/ class-file paths

key-files:
  created: []
  modified:
    - pom.xml
    - src/main/resources/application.properties
    - src/test/resources/application-test.properties

key-decisions:
  - "springdoc artifact renamed in Boot 3+/4+: springdoc-openapi-ui:1.x → springdoc-openapi-starter-webmvc-ui:2.x"
  - "logstash-logback-encoder bumped to 8.1 — 7.3 targeted Logback 1.2.x (Boot 2.6.15), 8.x targets Logback 1.5.x (Boot 3+/4+)"
  - "JUnit Vintage Engine removed — tests will be migrated to JUnit 5 native in Plan 04"
  - "JaCoCo exclusions fixed from com/aflokkat/ to com/st4r4x/ — wrong paths caused all exclusions to match nothing"
  - "hibernate.dialect removed from both properties files — Hibernate 6 auto-detects PostgreSQL dialect"
  - "ant_path_matcher removed — Boot 3+ removed this config property; springdoc 2.x uses PathPatternParser"

patterns-established:
  - "Dependency foundation plan: update coordinates only, expect compile failures in next plan (javax→jakarta)"
  - "JaCoCo exclusion paths must match actual compiled class file paths, not just package names"

requirements-completed: [UPGRADE-01]

# Metrics
duration: 25min
completed: 2026-04-13
---

# Phase 21 Plan 01: Upgrade Foundation Summary

**Spring Boot parent 2.6.15→4.0.5 and Java 11→25 declared; springdoc v2, logstash 8.1, JaCoCo package fix, and deprecated property removals — dependency foundation for full Boot 4 migration**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-13T00:00:00Z
- **Completed:** 2026-04-13
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Updated pom.xml Spring Boot parent from 2.6.15 to 4.0.5 and Java version from 11 to 25
- Replaced springdoc-openapi-ui:1.8.0 with springdoc-openapi-starter-webmvc-ui:2.8.6 (required Boot 3+/4+ artifact rename)
- Added logstash-logback-encoder:8.1 targeting Logback 1.5.x shipped with Boot 3+/4+
- Removed explicit JUnit 4 dependencies (junit:junit:4.13.2, junit-vintage-engine) — tests migrated in Plan 04
- Fixed JaCoCo exclusion patterns from wrong com/aflokkat/ to correct com/st4r4x/ package paths
- Cleaned deprecated properties: ant_path_matcher and hibernate.dialect removed from both application.properties files

## Task Commits

1. **Task 1: Update pom.xml — Boot parent, Java version, dependency coordinates, JaCoCo exclusion fix** - `7213032` (feat)
2. **Task 2: Clean application.properties and application-test.properties of removed Boot 3+ properties** - `82b13e7` (chore)

## Files Created/Modified

- `pom.xml` - Boot parent 4.0.5, Java 25, springdoc v2, logstash 8.1, JUnit 4 removed, JaCoCo exclusions fixed
- `src/main/resources/application.properties` - ant_path_matcher and hibernate.dialect lines removed
- `src/test/resources/application-test.properties` - hibernate.dialect removed, ddl-auto comment updated for JUnit 5

## Decisions Made

- springdoc artifact renamed in Boot 3+: `springdoc-openapi-ui` → `springdoc-openapi-starter-webmvc-ui` (groupId stays `org.springdoc`)
- logstash-logback-encoder 8.1 used instead of 7.3 — 8.x targets Logback 1.5.x which Boot 4 ships; 7.3 targeted Logback 1.2.x
- JUnit Vintage Engine removed immediately — Plan 04 migrates test annotations to JUnit 5 native
- JaCoCo exclusions corrected to `com/st4r4x/` — the wrong `com/aflokkat/` path matched nothing, inflating coverage denominator with low-coverage POJOs

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

Worktree was reset to correct base commit `0c3483f` using `git reset --soft` (worktree was pointing to an older merge commit). After reset, working tree files needed to be restored from HEAD with `git checkout HEAD -- <files>` before edits could be applied. This is a standard worktree setup artifact, not a code issue.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- pom.xml now declares Boot 4.0.5; `mvn dependency:tree` resolves BUILD SUCCESS
- `mvn clean compile` will FAIL with javax symbol errors — this is expected and correct; Plan 02 addresses javax→jakarta namespace migration
- application.properties clean of removed Boot 3+ properties; springdoc 2.x paths intact
- JaCoCo coverage thresholds and exclusions correctly configured for future test runs

---
*Phase: 21-upgrade-java-11-25-and-spring-boot-2-6-15-4-0-5-including-ju*
*Completed: 2026-04-13*
