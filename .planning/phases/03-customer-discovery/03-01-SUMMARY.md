---
phase: 03-customer-discovery
plan: 01
subsystem: testing
tags: [java, junit5, mockito, spring-mvc, tdd, wave0]

# Dependency graph
requires:
  - phase: 02-controller-reports
    provides: RestaurantController, RestaurantDAO, RestaurantService — base architecture for new endpoints
provides:
  - RestaurantControllerSearchTest.java with 4 @Disabled Wave 0 stubs (search + map-points)
  - searchByNameOrAddress(String q, int limit) added to RestaurantDAO interface
  - findMapPoints() added to RestaurantDAO interface
  - UnsupportedOperationException stubs in RestaurantDAOImpl (unblocks compile; impl in 03-02)
affects: [03-02-customer-discovery]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Wave 0 TDD: write @Disabled test stubs against interface methods before implementation exists"
    - "@ExtendWith(MockitoExtension.class) + standaloneSetup — the only approved MockMvc pattern; never @WebMvcTest"

key-files:
  created:
    - src/test/java/com/aflokkat/controller/RestaurantControllerSearchTest.java
  modified:
    - src/main/java/com/aflokkat/dao/RestaurantDAO.java
    - src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java

key-decisions:
  - "Wave 0 stubs: @Disabled annotation on each test method so suite compiles and skips cleanly until Plan 03-02 enables them"
  - "DAO interface methods added in Plan 03-01 with UnsupportedOperationException stubs in Impl — avoids compile break while keeping implementation deferred to 03-02"

patterns-established:
  - "Wave 0 scaffold: interface method declarations + disabled test stubs as a two-commit unit before any endpoint code"

requirements-completed: [CUST-01, CUST-03]

# Metrics
duration: 35min
completed: 2026-03-31
---

# Phase 3 Plan 1: Customer Discovery — Wave 0 Test Scaffold Summary

**TDD Wave 0 scaffold: 4 @Disabled MockMvc stubs for search (CUST-01) and map-points (CUST-03) endpoints, with DAO interface declarations that gate Plan 03-02 implementation**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-03-31T09:13:43Z
- **Completed:** 2026-03-31T09:48:26Z
- **Tasks:** 1
- **Files modified:** 3

## Accomplishments
- Created `RestaurantControllerSearchTest.java` with 4 @Disabled test methods following the project's mandatory MockMvc pattern
- Added `searchByNameOrAddress(String q, int limit)` and `findMapPoints()` to `RestaurantDAO` interface — enabling test compilation before implementation
- Added `UnsupportedOperationException` stubs to `RestaurantDAOImpl` to keep the full build and test suite green
- All 4 tests compile and run SKIPPED (not failed) — Nyquist Wave 0 compliance achieved

## Task Commits

1. **Task 1: Write failing test stubs for search and map-points endpoints** - `f3b0cb3` (test)

**Plan metadata:** _(docs commit follows)_

## Files Created/Modified
- `src/test/java/com/aflokkat/controller/RestaurantControllerSearchTest.java` - Wave 0 test scaffold, 4 @Disabled stubs for search and map-points
- `src/main/java/com/aflokkat/dao/RestaurantDAO.java` - Added `searchByNameOrAddress` and `findMapPoints` interface declarations
- `src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java` - Added `UnsupportedOperationException` stub implementations

## Decisions Made
- Adding DAO interface methods in Plan 03-01 (not 03-02) was necessary to allow the test file to compile. The plan noted these were "to be added in Plan 02" but compile-time correctness required them now. Implementations are still deferred to Plan 03-02 via `UnsupportedOperationException`.
- `@Disabled` annotation placed on each test method individually (not at class level) to keep per-test granularity when they are re-enabled in Plan 03-02.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added DAO interface method stubs to allow test compilation**
- **Found during:** Task 1 (writing test stubs)
- **Issue:** `RestaurantControllerSearchTest` calls `restaurantDAO.searchByNameOrAddress()` and `restaurantDAO.findMapPoints()`, but neither method existed on the `RestaurantDAO` interface — the test file would not compile
- **Fix:** Added both method signatures to `RestaurantDAO` interface with Javadoc noting Plan 03-02 implementation. Added `UnsupportedOperationException` stubs to `RestaurantDAOImpl` to satisfy the interface contract without implementing the feature
- **Files modified:** `src/main/java/com/aflokkat/dao/RestaurantDAO.java`, `src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java`
- **Verification:** `mvn test -Dtest=RestaurantControllerSearchTest` compiles clean, 4 tests SKIPPED
- **Committed in:** `f3b0cb3` (part of Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 3 - blocking)
**Impact on plan:** Auto-fix essential for compilation. The plan implied interface methods would be added in Plan 03-02, but the Wave 0 test scaffold requires them at the interface level in Plan 03-01 to compile. No functional scope creep — implementations are still stubbed as `UnsupportedOperationException`.

## Issues Encountered
None beyond the DAO interface compilation requirement handled via Rule 3 above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Wave 0 scaffold complete; Plan 03-02 can remove `@Disabled` and implement endpoints
- `RestaurantDAO.searchByNameOrAddress()` and `findMapPoints()` signatures are locked in — Plan 03-02 implements the MongoDB queries
- `RestaurantDAOImpl` stubs throw `UnsupportedOperationException` — they will be replaced with real implementations in Plan 03-02

---
*Phase: 03-customer-discovery*
*Completed: 2026-03-31*

## Self-Check: PASSED
- `src/test/java/com/aflokkat/controller/RestaurantControllerSearchTest.java` — FOUND
- `.planning/phases/03-customer-discovery/03-01-SUMMARY.md` — FOUND
- Commit `f3b0cb3` — FOUND in git log
