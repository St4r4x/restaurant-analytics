---
phase: 10-admin-tools
plan: "01"
subsystem: auth
tags: [spring-boot, jwt, role-based-access, dataseed, docker-compose]

# Dependency graph
requires:
  - phase: 01-role-infrastructure
    provides: AuthService, UserEntity role field, ROLE_CONTROLLER signup code pattern
provides:
  - ROLE_ADMIN signup code branch in AuthService.register()
  - admin_test seed account on application startup
  - admin.signup.code Spring property and ADMIN_SIGNUP_CODE Docker env var
  - ReportRepository.countGroupByStatus() and countGroupByGrade() JPQL queries
  - AdminController GET /api/reports/stats (ADMIN role required)
affects: [10-02-admin-page, 10-03-report-stats, SecurityConfig ROLE_ADMIN guards]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Admin signup code checked before controller code in if/else chain (admin takes priority)"
    - "Fail-safe admin signup: empty string disables the path; admin accounts seeded via DataSeeder"
    - "JPQL GROUP BY aggregate queries return List<Object[]> with [enum, Long] row structure"

key-files:
  created:
    - src/main/java/com/aflokkat/controller/AdminController.java
    - src/test/java/com/aflokkat/controller/AdminControllerTest.java
  modified:
    - src/main/java/com/aflokkat/service/AuthService.java
    - src/main/java/com/aflokkat/startup/DataSeeder.java
    - src/main/resources/application.properties
    - docker-compose.yml
    - src/main/java/com/aflokkat/repository/ReportRepository.java
    - src/test/java/com/aflokkat/service/AuthServiceTest.java

key-decisions:
  - "Admin signup code checked first in role-assignment if/else — admin takes priority over controller code when both are set"
  - "admin.signup.code= (empty) is the default — admin accounts created via DataSeeder, not self-registration"
  - "ADMIN_SIGNUP_CODE env var defaults to empty string in docker-compose.yml (admin signup disabled in Docker)"
  - "DataSeeder seeds admin_test / admin@test.com / ROLE_ADMIN with same SEED_PASSWORD as other test accounts"

patterns-established:
  - "Pattern: New signup roles added by inserting a new else-if branch before the controller check, preserving fail-safe order"
  - "Pattern: New seed accounts added as additional seedUser() calls in DataSeeder.run() — idempotent, order-insensitive"

requirements-completed: [ADM-01]

# Metrics
duration: 5min
completed: 2026-04-10
---

# Phase 10 Plan 01: Admin Auth Foundation Summary

**ROLE_ADMIN signup code path added to AuthService with admin_test DataSeeder account, admin.signup.code property, and ADMIN_SIGNUP_CODE Docker env var**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-04-10T15:20:06Z
- **Completed:** 2026-04-10T15:25:00Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments
- AuthService.register() now assigns ROLE_ADMIN when the correct admin signup code is provided (checked before controller code)
- admin_test / admin@test.com seeded automatically on startup with ROLE_ADMIN
- admin.signup.code Spring property and ADMIN_SIGNUP_CODE Docker env var added (both empty/disabled by default)
- 17 AuthServiceTest tests pass including 3 new ADMIN role-assignment tests
- AdminController and ReportRepository aggregate queries were already in place from a prior session commit

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend AuthService with ROLE_ADMIN signup code branch** - `722c6dc` (feat)
2. **Task 2: DataSeeder admin_test + application.properties + docker-compose.yml** - `0c4b716` (feat)
3. **Task 3: Fix REQUIREMENTS.md ADM-01 role label** - no commit needed (already "(ADMIN role)")

## Files Created/Modified
- `src/main/java/com/aflokkat/service/AuthService.java` - Added adminSignupCode field, updated constructors to 5 args, added ROLE_ADMIN branch before ROLE_CONTROLLER in role-assignment logic
- `src/main/java/com/aflokkat/startup/DataSeeder.java` - Added seedUser("admin_test", "admin@test.com", "ROLE_ADMIN")
- `src/main/resources/application.properties` - Added admin.signup.code= placeholder property
- `docker-compose.yml` - Added ADMIN_SIGNUP_CODE: "" env var for app service
- `src/main/java/com/aflokkat/repository/ReportRepository.java` - Added countGroupByStatus() and countGroupByGrade() JPQL queries (needed for AdminController and AdminControllerTest compilation)
- `src/main/java/com/aflokkat/controller/AdminController.java` - Created: GET /api/reports/stats with @PreAuthorize("hasRole('ADMIN')")
- `src/test/java/com/aflokkat/service/AuthServiceTest.java` - Added 3 new ADMIN role tests; all 17 tests green
- `src/test/java/com/aflokkat/controller/AdminControllerTest.java` - Created: 3 tests for GET /api/reports/stats

## Decisions Made
- Admin signup code checked before controller code — if both are set and a user provides the admin code, they get ROLE_ADMIN not ROLE_CONTROLLER
- Empty admin.signup.code disables the admin self-registration path (fail-safe, consistent with controller code pattern)
- Admin accounts created via DataSeeder for development, not via signup code in normal use

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] AdminControllerTest.java compilation error**
- **Found during:** Task 1 verification (test-compile)
- **Issue:** AdminControllerTest.java referenced AdminController and ReportRepository.countGroupByStatus()/countGroupByGrade() — none of these existed, blocking all test compilation
- **Fix:** Both AdminController.java and ReportRepository JPQL methods were already committed by a prior session (commit 722c6dc); the compilation error was resolved by the same commit that fixed AuthService
- **Files modified:** src/main/java/com/aflokkat/controller/AdminController.java, src/main/java/com/aflokkat/repository/ReportRepository.java, src/test/java/com/aflokkat/controller/AdminControllerTest.java
- **Verification:** mvn test-compile succeeds; AdminControllerTest 3/3 pass
- **Committed in:** 722c6dc (Task 1 commit, part of prior session that also covered plan 10-02 work)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary for compilation. The AdminController/ReportRepository work was pre-implemented in a prior session commit that bundled Plan 10-01 and 10-02 concerns together.

## Issues Encountered
- A prior execution session had already committed AuthService 5-arg constructor update, AdminController, ReportRepository aggregate queries, AdminControllerTest, and AuthServiceTest constructor updates (commit 722c6dc labeled feat(10-02)). This plan execution found the AuthService role-assignment logic still needed the ROLE_ADMIN branch, and added it plus the 3 new ADMIN tests, DataSeeder seed, application.properties property, and docker-compose.yml env var.

## User Setup Required
None - no external service configuration required. The admin signup code is disabled by default; admin_test account is seeded automatically on startup.

## Next Phase Readiness
- ROLE_ADMIN registration path is functional
- admin_test account exists with ROLE_ADMIN on startup
- AdminController GET /api/reports/stats ready (gated by ROLE_ADMIN)
- Ready for Plan 10-02: /admin Thymeleaf page with SecurityConfig ROLE_ADMIN guard

## Self-Check: PASSED

- AuthService.java: FOUND
- DataSeeder.java: FOUND
- application.properties: FOUND
- docker-compose.yml: FOUND
- 10-01-SUMMARY.md: FOUND
- Commit 722c6dc: FOUND
- Commit 0c4b716: FOUND

---
*Phase: 10-admin-tools*
*Completed: 2026-04-10*
