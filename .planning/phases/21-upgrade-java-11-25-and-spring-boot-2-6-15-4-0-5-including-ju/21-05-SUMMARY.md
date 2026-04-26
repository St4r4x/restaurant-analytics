---
phase: 21-upgrade-java-11-25-and-spring-boot-2-6-15-4-0-5-including-ju
plan: 05
subsystem: infra
tags: [spring-boot, java, junit5, jackson, maven, testcontainers]

# Dependency graph
requires:
  - phase: 21-04
    provides: JUnit 4 → JUnit 5 migration across 9 test files
  - phase: 21-03
    provides: Spring Security antMatchers → requestMatchers migration
  - phase: 21-02
    provides: javax → jakarta namespace migration
  - phase: 21-01
    provides: pom.xml updated to Boot 4.0.5, Java 25, JUnit 4 removed
provides:
  - "Full mvn clean verify green build: 165 Surefire + 19 Failsafe = BUILD SUCCESS"
  - "Jackson 2 ObjectMapper bean exposed for RestaurantCacheService (Boot 4 compat)"
  - "CHANGELOG.md updated with Phase 21 entry"
  - "CLAUDE.md Stack section updated to Java 25 / Spring Boot 4.0.5 / JUnit 5"
  - "README.md version references updated to Java 25 / Spring Boot 4.0.5"
affects: [all future phases — Phase 21 establishes the Java 25 / Boot 4.0.5 baseline]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Expose Jackson 2 ObjectMapper as explicit @Bean when Boot 4 auto-config only provides Jackson 3 (tools.jackson)"

key-files:
  created: []
  modified:
    - src/main/java/com/st4r4x/config/RedisConfig.java
    - CHANGELOG.md
    - CLAUDE.md
    - README.md

key-decisions:
  - "Boot 4 ships Jackson 3 (tools.jackson) auto-config only; Jackson 2 ObjectMapper must be provided explicitly for any @Service that injects com.fasterxml.jackson.databind.ObjectMapper"
  - "Smoke test (live Docker startup) skipped in this context — no Docker services available; mvn clean verify with Testcontainers covers equivalent correctness guarantee"

patterns-established:
  - "Jackson 2 + 3 coexistence: expose @Bean ObjectMapper in RedisConfig for Jackson 2 consumers; Spring framework uses Jackson 3 internally"

requirements-completed: [UPGRADE-01, UPGRADE-02, UPGRADE-03, UPGRADE-04]

# Metrics
duration: 5min
completed: 2026-04-13
---

# Phase 21 Plan 05: Full Verification and Documentation Summary

**Boot 4.0.5 + Java 25 upgrade chain acceptance gate: mvn clean verify green (165 Surefire + 19 Failsafe), ObjectMapper Boot 4 compat fixed, CHANGELOG/CLAUDE.md/README updated**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-13T08:07:15Z
- **Completed:** 2026-04-13T08:12:40Z
- **Tasks:** 2 (Task 1 + Task 3; Task 2 was checkpoint skipped per context instructions)
- **Files modified:** 4

## Accomplishments
- `mvn clean verify` exits 0: 165 Surefire unit tests + 19 Failsafe integration tests (15 RestaurantDAOIT + 4 UserRepositoryIT) all pass, no failures, no errors
- Fixed Boot 4 Jackson 2 ObjectMapper injection failure in `RestaurantCacheService` — Boot 4 auto-configures `tools.jackson` (Jackson 3) only; added explicit `@Bean ObjectMapper` in `RedisConfig`
- Updated CHANGELOG.md, CLAUDE.md (Stack section), and README.md to reflect Java 25 / Spring Boot 4.0.5 / JUnit 5

## Task Commits

Each task was committed atomically:

1. **Task 1: Run full build and fix residual failures** - `c91628d` (fix)
3. **Task 3: Update CHANGELOG.md, CLAUDE.md, and README.md** - `21518c3` (docs)

**Plan metadata:** (created in final commit)

## Files Created/Modified
- `src/main/java/com/st4r4x/config/RedisConfig.java` - Added explicit `@Bean ObjectMapper` for Jackson 2 / Boot 4 compatibility
- `CHANGELOG.md` - Added Phase 21 entry with full upgrade bullet list
- `CLAUDE.md` - Stack section: Java 11 → 25, Spring Boot 2.6.15 → 4.0.5, JUnit 4 → 5
- `README.md` - Stack table: Java 11 → 25, Spring Boot 2.6.15 → 4.0.5

## Decisions Made
- Boot 4 auto-configures Jackson 3 (`tools.jackson.databind.json.JsonMapper`) rather than Jackson 2 (`com.fasterxml.jackson.databind.ObjectMapper`). Since `RestaurantCacheService` uses Jackson 2 for Redis serialization (stable, no need to migrate), an explicit `@Bean ObjectMapper` was added in `RedisConfig`. Both Jackson 2 and Jackson 3 are on the classpath; they coexist without conflict.
- Smoke test (Task 2 checkpoint) was skipped because Docker services (MongoDB, PostgreSQL, Redis) are not available in this execution context. The Testcontainers-based integration tests in Failsafe (RestaurantDAOIT, UserRepositoryIT) provide equivalent coverage — they start real containers and exercise actual database connectivity.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Jackson 2 ObjectMapper not available in Spring Boot 4 application context**
- **Found during:** Task 1 (mvn clean verify)
- **Issue:** `UserRepositoryIT` failed with `UnsatisfiedDependencyException` — `RestaurantCacheService` constructor injects `com.fasterxml.jackson.databind.ObjectMapper`, but Spring Boot 4's `JacksonAutoConfiguration` only registers `tools.jackson.databind.json.JsonMapper` (Jackson 3). No `com.fasterxml.jackson.databind.ObjectMapper` bean was available in the application context.
- **Fix:** Added `@Bean public ObjectMapper objectMapper()` returning `new ObjectMapper()` in `RedisConfig.java`. This provides the Jackson 2 `ObjectMapper` as a named Spring bean without interfering with Boot 4's Jackson 3 infrastructure.
- **Files modified:** `src/main/java/com/st4r4x/config/RedisConfig.java`
- **Verification:** Re-ran `mvn clean verify` — all 165 Surefire + 19 Failsafe tests pass, BUILD SUCCESS.
- **Committed in:** `c91628d` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug)
**Impact on plan:** The fix was necessary to unblock `UserRepositoryIT` which loads the full Spring application context. No scope creep — the fix is the minimal change needed to restore Boot 4 compatibility for Jackson 2 consumers.

## Issues Encountered
- `UserRepositoryIT` (Failsafe) failed on first run: `RestaurantCacheService` required `com.fasterxml.jackson.databind.ObjectMapper` but Boot 4 auto-config provides `tools.jackson.databind.json.JsonMapper` only. Root cause: Spring Boot 4 migrated its Jackson auto-configuration to Jackson 3. Fixed by exposing explicit `@Bean ObjectMapper` in `RedisConfig`. See Deviations section for full details.

## Smoke Test (Task 2 Checkpoint)

Skipped — Docker services not available in execution context. The `mvn clean verify` build with Testcontainers provides equivalent verification:
- `RestaurantDAOIT`: 15 tests against `mongo:7.0` container (real MongoDB)
- `UserRepositoryIT`: 4 tests against `postgres:15-alpine` + `mongo:7.0` containers (real PostgreSQL + MongoDB, full Spring context startup)

Autoconfigure exclusions (`spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.*`) verified present in `application.properties` and unchanged from Boot 2.

## Known Stubs
None.

## Threat Flags
None — no new network endpoints, auth paths, file access patterns, or schema changes introduced.

## Next Phase Readiness
- Phase 21 upgrade chain is complete: Boot 2.6.15 → 4.0.5, Java 11 → 25, JUnit 4 → 5, javax → jakarta, Spring Security 5 → 6, springdoc v1 → v2, all verified green
- All documentation updated (CHANGELOG.md, CLAUDE.md, README.md)
- Runnable JAR: `target/quickstart-app-1.0-SNAPSHOT.jar` (80 MB, built with Java 25 / Boot 4.0.5)

---
*Phase: 21-upgrade-java-11-25-and-spring-boot-2-6-15-4-0-5-including-ju*
*Completed: 2026-04-13*

## Self-Check: PASSED

- FOUND: src/main/java/com/st4r4x/config/RedisConfig.java
- FOUND: CHANGELOG.md
- FOUND: CLAUDE.md
- FOUND: README.md
- FOUND: .planning/phases/21-upgrade-java-11-25-and-spring-boot-2-6-15-4-0-5-including-ju/21-05-SUMMARY.md
- FOUND: target/quickstart-app-1.0-SNAPSHOT.jar
- FOUND commit c91628d: fix(21-05): expose Jackson 2 ObjectMapper bean for RestaurantCacheService
- FOUND commit 21518c3: docs(21-05): update CHANGELOG, CLAUDE.md, README for Phase 21 upgrade
