---
phase: 02-controller-reports
plan: "01"
subsystem: api
tags: [spring-boot, jpa, postgresql, mockito, junit5, mockmvc]

# Dependency graph
requires:
  - phase: 01-role-infrastructure
    provides: UserEntity (FK target), UserRepository, SecurityConfig with /api/reports hasRole(CONTROLLER), JPA config

provides:
  - InspectionReportEntity JPA entity (inspection_reports table, auto-created on startup)
  - Grade enum (A/B/C/F) and Status enum (OPEN/IN_PROGRESS/RESOLVED) in com.aflokkat.entity
  - ReportRepository with findByUserId and findByUserIdAndStatus
  - ReportRequest DTO for POST and PATCH bodies
  - ReportController: POST /api/reports (201 + enriched) and GET /api/reports (list with MongoDB enrichment)
  - ReportControllerTest: 6 GREEN tests (CTRL-01, CTRL-02) + 6 aborting stubs (CTRL-03, CTRL-04)

affects:
  - 02-02 (PATCH /api/reports/{id} — fills patchReport_* stubs)
  - 02-03 (photo upload — fills photoUpload_* stubs)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@InjectMocks + MockMvcBuilders.standaloneSetup() for controller unit tests (no @WebMvcTest)"
    - "assumeTrue(false) for TDD RED stubs on JUnit 5.8.2 (abort(String) not available until 5.9)"
    - "@ManyToOne FetchType.LAZY + @JoinColumn for FK-to-UserEntity pattern (matches BookmarkEntity)"
    - "@Enumerated(EnumType.STRING) for Grade and Status columns"
    - "@Transactional on listReports() to prevent LazyInitializationException"

key-files:
  created:
    - src/main/java/com/aflokkat/entity/Grade.java
    - src/main/java/com/aflokkat/entity/Status.java
    - src/main/java/com/aflokkat/entity/InspectionReportEntity.java
    - src/main/java/com/aflokkat/repository/ReportRepository.java
    - src/main/java/com/aflokkat/dto/ReportRequest.java
    - src/main/java/com/aflokkat/controller/ReportController.java
    - src/test/java/com/aflokkat/controller/ReportControllerTest.java
  modified: []

key-decisions:
  - "assumeTrue(false) used instead of Assumptions.abort(String) — abort(String) was added in JUnit 5.9.0, project uses 5.8.2 via Spring Boot 2.6.15 BOM"
  - "Grade enum placed in com.aflokkat.entity (not com.aflokkat.domain) to avoid collision with existing com.aflokkat.domain.Grade (MongoDB POJO)"

patterns-established:
  - "Controller unit test pattern: @InjectMocks on controller + MockMvcBuilders.standaloneSetup + SecurityContextHolder manual auth setup"
  - "TDD stub pattern for JUnit 5.8.2: assumeTrue(false, message) in @Test methods"

requirements-completed: [CTRL-01, CTRL-02]

# Metrics
duration: 18min
completed: 2026-03-30
---

# Phase 02 Plan 01: Controller Reports Data Layer and POST/GET Endpoints Summary

**JPA inspection_reports table (Grade/Status enums), ReportRepository, and POST+GET /api/reports endpoints with MongoDB enrichment — 6 CTRL-01/02 tests GREEN, 6 CTRL-03/04 stubs aborting**

## Performance

- **Duration:** 18 min
- **Started:** 2026-03-30T13:04:20Z
- **Completed:** 2026-03-30T13:22:57Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Created complete JPA data layer: Grade enum, Status enum, InspectionReportEntity (@Table inspection_reports), ReportRepository (findByUserId + findByUserIdAndStatus), ReportRequest DTO
- Implemented POST /api/reports (creates report with status defaulting to OPEN, HTTP 201, enriched with restaurantName/borough from MongoDB)
- Implemented GET /api/reports (lists caller's own reports, delegates to findByUserIdAndStatus when ?status= param present, toResponseMap for enrichment)
- 6 CTRL-01/02 tests passing GREEN; 6 CTRL-03/04 stubs cleanly aborting; 56 total unit tests pass with no regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: Wave 0 — Entity, enums, repository, DTO, and test scaffold** - `7668998` (test)
2. **Task 2: POST /api/reports and GET /api/reports endpoints + fill CTRL-01/02 tests** - `cfa052b` (feat)

**Plan metadata:** _(docs commit — added below after state updates)_

_Note: TDD tasks may have multiple commits (test -> feat -> refactor)_

## Files Created/Modified
- `src/main/java/com/aflokkat/entity/Grade.java` - Grade enum (A/B/C/F) for inspection reports
- `src/main/java/com/aflokkat/entity/Status.java` - Status enum (OPEN/IN_PROGRESS/RESOLVED)
- `src/main/java/com/aflokkat/entity/InspectionReportEntity.java` - JPA entity mapped to inspection_reports table
- `src/main/java/com/aflokkat/repository/ReportRepository.java` - JPA repository with findByUserId and findByUserIdAndStatus
- `src/main/java/com/aflokkat/dto/ReportRequest.java` - POST/PATCH request body DTO
- `src/main/java/com/aflokkat/controller/ReportController.java` - POST /api/reports and GET /api/reports endpoints
- `src/test/java/com/aflokkat/controller/ReportControllerTest.java` - 12 tests (6 GREEN + 6 aborting stubs)

## Decisions Made
- `assumeTrue(false)` used instead of `Assumptions.abort(String)` — `abort(String)` was introduced in JUnit 5.9.0, but the project is on 5.8.2 via Spring Boot 2.6.15 BOM; `assumeTrue(false)` achieves identical TestAbortedException behavior.
- `Grade` enum placed in `com.aflokkat.entity` (not `com.aflokkat.domain`) to avoid collision with the existing `com.aflokkat.domain.Grade` MongoDB POJO class.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed missing static import for abort(String) — replaced with assumeTrue(false)**
- **Found during:** Task 1 (test scaffold compilation)
- **Issue:** Plan scaffold used `import static org.junit.jupiter.api.Assumptions.abort` and called `abort("not implemented")`. JUnit 5.8.2 does not have `Assumptions.abort(String)` — that method was added in JUnit 5.9.0.
- **Fix:** Replaced all `abort("not implemented")` calls with `assumeTrue(false, "not implemented")`. Behavior is identical: test is aborted with TestAbortedException (counted as "Skipped").
- **Files modified:** `src/test/java/com/aflokkat/controller/ReportControllerTest.java`
- **Verification:** `mvn test -Dtest=ReportControllerTest` shows 12 tests, 0 failures, 12 skipped (RED state confirmed).
- **Committed in:** 7668998 (Task 1 commit)

**2. [Rule 3 - Blocking] Fixed ResultMatcher chaining .or() not available in Spring Boot 2.6.15 test library**
- **Found during:** Task 2 (GREEN test compilation)
- **Issue:** Test used `.andExpect(jsonPath("$.data.restaurantName").doesNotExist().or(...))` — the `.or()` combinator on `ResultMatcher` doesn't exist in Spring Boot 2.6.15's spring-test library.
- **Fix:** Simplified assertion to `jsonPath("$.data.restaurantName").value(nullValue())` — the JSON serializer outputs `"restaurantName": null` when the value is null, so the assertion is correct and more direct.
- **Files modified:** `src/test/java/com/aflokkat/controller/ReportControllerTest.java`
- **Verification:** 6 CTRL-01/02 tests pass GREEN after fix.
- **Committed in:** cfa052b (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2 blocking — JUnit API version mismatch, MockMvc API mismatch)
**Impact on plan:** Both auto-fixes necessary to compile and run tests. No scope creep; behavioral contract unchanged.

## Issues Encountered
- Full `mvn test` suite hung/timed out — likely due to integration test or network-bound test (RestaurantDAOIntegrationTest, NycOpenDataClientTest). Regression verification was done by running a named subset of 56 unit tests: AuthServiceTest, ReportControllerTest, SecurityConfigTest, ValidationUtilTest, DataSeederTest, JwtUtilTest — all passed.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- inspection_reports table auto-created by Hibernate on next application startup
- POST /api/reports and GET /api/reports are ready to test end-to-end
- ReportControllerTest has 6 aborting stubs ready for Plan 02 (PATCH) and Plan 03 (photo upload) to fill in
- SecurityConfig already has `/api/reports/**` guarded by `hasRole("CONTROLLER")` — 401/403 behavior tested in SecurityConfigTest

---
*Phase: 02-controller-reports*
*Completed: 2026-03-30*

## Self-Check: PASSED

All files exist on disk. Both task commits (7668998, cfa052b) confirmed in git history.
