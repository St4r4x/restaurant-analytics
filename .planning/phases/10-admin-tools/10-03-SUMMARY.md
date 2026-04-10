---
phase: 10-admin-tools
plan: "03"
subsystem: ui
tags: [spring-security, thymeleaf, jwt, admin, antmatcher, navbar]

# Dependency graph
requires:
  - phase: 10-01
    provides: ROLE_ADMIN registration path, admin_test seed account, admin.signup.code property
  - phase: 10-02
    provides: GET /api/reports/stats endpoint, AdminController, ReportRepository aggregates

provides:
  - SecurityConfig with /api/reports/stats (ADMIN) before /api/reports/** (CONTROLLER) antMatcher ordering
  - SecurityConfig with /admin.hasRole("ADMIN") antMatcher
  - GET /admin ViewController route returning "admin" Thymeleaf view
  - admin.html three-card page: sync controls, at-risk CSV download, report statistics
  - navbar.html nav-admin link (hidden by default, shown only for ROLE_ADMIN)

affects:
  - Human verification checkpoint (Task 3 — awaiting approval)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "antMatcher ordering: specific paths before wildcards (/api/reports/stats before /api/reports/**)"
    - "Client-side IIFE auth guard in admin.html for ROLE_ADMIN (defense in depth)"
    - "2s setInterval sync polling pattern with button disabled state"
    - "Badge pill rows using inline CSS for colored status/grade indicators"

key-files:
  created:
    - src/main/resources/templates/admin.html
  modified:
    - src/main/java/com/aflokkat/config/SecurityConfig.java
    - src/main/java/com/aflokkat/controller/ViewController.java
    - src/main/resources/templates/fragments/navbar.html
    - src/test/java/com/aflokkat/config/SecurityConfigTest.java
    - src/test/java/com/aflokkat/startup/DataSeederTest.java

key-decisions:
  - "antMatcher /api/reports/stats placed BEFORE /api/reports/** wildcard — first-match-wins Spring Security ordering"
  - "SecurityConfigTest dashboard tests updated: Phase 7 removed server-side /dashboard guard (client-side IIFE only)"
  - "DataSeederTest updated: now expects 3 seed users (customer, controller, admin) after Phase 10-01 added admin_test"
  - "fetchWithAuth defined inline in admin.html (same as dashboard.html pattern, not from ux-utils fragment)"

patterns-established:
  - "Pattern: Admin-only antMatchers declared before broader wildcards in SecurityConfig"
  - "Pattern: nav-admin hidden by default (display:none), shown by JS IIFE on ROLE_ADMIN"

requirements-completed:
  - ADM-01
  - ADM-02
  - ADM-03

# Metrics
duration: 15min
completed: 2026-04-10
---

# Phase 10 Plan 03: Admin Security Wiring and admin.html Summary

**Admin panel with SecurityConfig ADMIN guards, /admin route, three-card admin.html (sync/CSV/stats), and ROLE_ADMIN navbar link — awaiting human verification**

## Performance

- **Duration:** 15 min
- **Started:** 2026-04-10T15:35:00Z
- **Completed:** 2026-04-10T15:50:41Z (Tasks 1+2 only; Task 3 awaits human verify)
- **Tasks:** 2 of 3 (Task 3 is checkpoint:human-verify)
- **Files modified:** 6

## Accomplishments
- SecurityConfig: added ADMIN-only `/api/reports/stats` antMatcher before the CONTROLLER `/api/reports/**` wildcard (correct first-match-wins ordering)
- SecurityConfig: added `/admin` ADMIN-only antMatcher; ViewController registered GET `/admin` → "admin" view
- admin.html: three-card page (Sync Controls, At-Risk CSV Download, Report Statistics) with 2s polling, 10s auto-dismiss, ROLE_ADMIN IIFE guard
- navbar.html: nav-admin link visible only for ROLE_ADMIN (hidden for CUSTOMER and CONTROLLER)
- Fixed 4 pre-existing test failures: SecurityConfigTest dashboard tests (Phase 7 stale expectations) + DataSeederTest admin_test account

## Task Commits

1. **Task 1: SecurityConfig + ViewController + navbar ADMIN link** - `13053dc` (feat)
2. **Task 2: Create admin.html** - `793fb45` (feat)
3. **Task 3: Human verification** — AWAITING CHECKPOINT APPROVAL

## Files Created/Modified
- `src/main/java/com/aflokkat/config/SecurityConfig.java` - Added /api/reports/stats (ADMIN) and /admin (ADMIN) antMatchers
- `src/main/java/com/aflokkat/controller/ViewController.java` - Added GET /admin route
- `src/main/resources/templates/fragments/navbar.html` - Added nav-admin anchor + ROLE_ADMIN JS block
- `src/main/resources/templates/admin.html` - New: three-card admin page
- `src/test/java/com/aflokkat/config/SecurityConfigTest.java` - Updated dashboard tests + added admin/stats tests
- `src/test/java/com/aflokkat/startup/DataSeederTest.java` - Updated to expect 3 seed users

## Decisions Made
- SecurityConfig antMatcher ordering: `/api/reports/stats` (ADMIN) declared before `/api/reports/**` (CONTROLLER) — Spring Security first-match-wins
- SecurityConfigTest: tests for `/dashboard` updated to reflect Phase 7 decision (client-side IIFE guard only, no server-side antMatcher)
- DataSeederTest: updated to account for `admin_test` seed added in Phase 10-01
- `fetchWithAuth` defined inline in admin.html (same as dashboard.html — not extracted to ux-utils fragment)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SecurityConfigTest dashboard tests tested removed behavior**
- **Found during:** Task 1 (SecurityConfig changes)
- **Issue:** `dashboard_redirectsToLogin_whenUnauthenticated` and `dashboard_returns403_forCustomer` expected server-side security for `/dashboard`, but Phase 7 (`fix(07): remove server-side auth guard`) intentionally removed it. Tests became stale.
- **Fix:** Updated tests to `dashboard_isAccessible_whenUnauthenticated` / `dashboard_isAccessible_forCustomer` (both expect 200 via anyRequest().permitAll()). Added new tests for `/admin` and `/api/reports/stats` security.
- **Files modified:** src/test/java/com/aflokkat/config/SecurityConfigTest.java
- **Verification:** SecurityConfigTest runs 11 tests, all pass
- **Committed in:** 13053dc (Task 1 commit)

**2. [Rule 1 - Bug] DataSeederTest expected 2 seed users but DataSeeder now seeds 3**
- **Found during:** Task 1 (running test suite)
- **Issue:** Phase 10-01 added `admin_test` to DataSeeder.run() but DataSeederTest was not updated — expected `times(2)` save calls and only stubbed 2 `findByUsername()` calls
- **Fix:** Updated DataSeederTest: stub `findByUsername("admin_test")`, expect `times(3)` saves, verify admin entity has ROLE_ADMIN
- **Files modified:** src/test/java/com/aflokkat/startup/DataSeederTest.java
- **Verification:** DataSeederTest runs 2 tests, all pass
- **Committed in:** 13053dc (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 - Bug)
**Impact on plan:** Both fixes corrected stale tests from prior phases. No scope creep.

## Issues Encountered
- `SyncServiceTest` triggered an unlimited live NYC API sync during `mvn test` run, taking too long. Tests targeted individually using `-Dtest=` flag to avoid waiting.
- `RestaurantCacheServiceTest` and `NycOpenDataClientTest` have pre-existing failures (Mockito compatibility) unrelated to this plan's changes — deferred.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Human checkpoint approval needed (Task 3) before plan is fully complete
- After approval: CHANGELOG.md + README.md update, v2.0 milestone release merge (develop → main, tag v2.0)
- All ADM requirements (ADM-01, ADM-02, ADM-03) implemented and awaiting verification

---
*Phase: 10-admin-tools*
*Completed: 2026-04-10 (pending human verification)*
