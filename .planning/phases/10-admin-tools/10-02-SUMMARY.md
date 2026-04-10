---
phase: 10-admin-tools
plan: "02"
subsystem: api
tags: [spring-boot, spring-data-jpa, jpql, rest, admin, postgresql]

# Dependency graph
requires:
  - phase: 02-controller-reports
    provides: InspectionReportEntity with Grade/Status enums, ReportRepository JpaRepository base

provides:
  - ReportRepository.countGroupByStatus() JPQL GROUP BY aggregate query
  - ReportRepository.countGroupByGrade() JPQL GROUP BY aggregate query
  - AdminController GET /api/reports/stats returning { byStatus, byGrade } maps
  - AuthService adminSignupCode field + ROLE_ADMIN assignment in register()

affects: [10-03-SecurityConfig-admin-html, security-config-antmatcher-ordering]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "JPQL GROUP BY with List<Object[]> return type for enum-keyed aggregates"
    - "Pre-populate all enum values with 0L before merging query results to guarantee complete keys"
    - "AdminController separate from ReportController to keep ADMIN and CONTROLLER logic isolated"

key-files:
  created:
    - src/main/java/com/aflokkat/controller/AdminController.java
    - src/test/java/com/aflokkat/controller/AdminControllerTest.java
  modified:
    - src/main/java/com/aflokkat/repository/ReportRepository.java
    - src/main/java/com/aflokkat/service/AuthService.java

key-decisions:
  - "AdminController uses @Autowired ReportRepository directly (no service wrapper) — consistent with existing AnalyticsController pattern"
  - "Response map uses LinkedHashMap for byStatus/byGrade to preserve enum declaration order (OPEN/IN_PROGRESS/RESOLVED, A/B/C/F)"
  - "AuthService 5-arg constructor added (controllerSignupCode + adminSignupCode) — ADMIN check runs before CONTROLLER check to prevent code collision"
  - "AuthService register() checks adminSignupCode first — fail-safe: null adminSignupCode means ADMIN signup is disabled"

patterns-established:
  - "Pattern: JPQL GROUP BY aggregate — @Query with SELECT r.field, COUNT(r) FROM Entity r GROUP BY r.field returns List<Object[]>"
  - "Pattern: Enum-complete response — pre-populate LinkedHashMap with all enum.values() set to 0L before merging query results"

requirements-completed: [ADM-03]

# Metrics
duration: 2min
completed: 2026-04-10
---

# Phase 10 Plan 02: Admin Tools — ReportRepository Aggregates + AdminController Summary

**JPQL GROUP BY aggregates on InspectionReportEntity delivering GET /api/reports/stats with enum-complete byStatus and byGrade maps for ADM-03**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-10T15:20:08Z
- **Completed:** 2026-04-10T15:22:47Z
- **Tasks:** 1
- **Files modified:** 4 (2 created, 2 modified)

## Accomplishments
- Added `countGroupByStatus()` and `countGroupByGrade()` JPQL `@Query` methods to `ReportRepository` — aggregate across ALL controllers with no userId filter
- Created `AdminController` with `@PreAuthorize("hasRole('ADMIN')")` on `GET /api/reports/stats` — returns `{ byStatus, byGrade }` with all enum keys always present (defaulting to 0)
- Fixed `AuthService` 5-arg constructor + `adminSignupCode` field + ROLE_ADMIN assignment in `register()` — unblocked compilation of `AuthServiceTest` which already expected 5 args

## Task Commits

Each task was committed atomically:

1. **Task 1: ReportRepository aggregate @Query methods + AdminController + AdminControllerTest** - `722c6dc` (feat)

## Files Created/Modified
- `src/main/java/com/aflokkat/controller/AdminController.java` - New ADMIN-only REST controller, GET /api/reports/stats
- `src/main/java/com/aflokkat/repository/ReportRepository.java` - Added countGroupByStatus() and countGroupByGrade() @Query methods
- `src/main/java/com/aflokkat/service/AuthService.java` - Updated to 5-arg constructor + adminSignupCode field + ROLE_ADMIN branch in register()
- `src/test/java/com/aflokkat/controller/AdminControllerTest.java` - 3 unit tests covering response shape, missing-enum defaulting, no-data-leak guarantee

## Decisions Made
- Used separate `AdminController` (not extending `ReportController`) to keep ADMIN stats logic isolated from CONTROLLER-facing report CRUD
- Pre-populate `LinkedHashMap` with all `Status.values()` and `Grade.values()` at 0L before merging GROUP BY results — guarantees all enum keys always present even when no reports have a given value
- ADMIN signup code check inserted BEFORE controller code check in `register()` — prevents code collision if both codes happen to match

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed AuthService 4-arg → 5-arg constructor mismatch blocking compilation**
- **Found during:** Task 1 (running mvn test -Dtest=AdminControllerTest — RED phase)
- **Issue:** `AuthServiceTest.java` (updated in the `chore(state)` commit) already called `new AuthService(..., null, null)` with 5 args, but `AuthService.java` still had only a 4-arg test constructor (`controllerSignupCode` only, no `adminSignupCode`). This caused compile errors in `AuthServiceTest`, which prevented `mvn test -Dtest=AdminControllerTest` from running.
- **Fix:** Added `adminSignupCode` field to `AuthService`, updated `@Autowired` constructor to accept both codes via `@Value`, updated package-visible test constructor to 5-arg. Also added the `ROLE_ADMIN` assignment branch to `register()` (the full plan 10-01 AuthService work).
- **Files modified:** `src/main/java/com/aflokkat/service/AuthService.java`
- **Verification:** `mvn test -Dtest=AuthServiceTest` — 17 tests green; `mvn test -Dtest=AdminControllerTest` — 3 tests green
- **Committed in:** `722c6dc` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 3 — blocking compilation)
**Impact on plan:** The AuthService fix was necessary to unblock compilation. It also delivers the AuthService portion of plan 10-01 (adminSignupCode + ROLE_ADMIN), which is additive and correct behavior.

## Issues Encountered
- `AuthServiceTest.java` was pre-updated to expect a 5-arg constructor (from `chore(state)` commit) but `AuthService.java` was not updated yet — blocking compile. Fixed inline per Rule 3.

## User Setup Required
None - no external service configuration required for this plan.

## Next Phase Readiness
- `AdminController` is ready; `GET /api/reports/stats` endpoint exists and returns correct shape
- Plan 10-03 needs to: (1) add `antMatcher("/api/reports/stats").hasRole("ADMIN")` BEFORE the `/api/reports/**` → CONTROLLER rule in SecurityConfig, (2) add `/admin` → `hasRole("ADMIN")` route, (3) create `admin.html` Thymeleaf template with Cards 1/2/3

---
*Phase: 10-admin-tools*
*Completed: 2026-04-10*
