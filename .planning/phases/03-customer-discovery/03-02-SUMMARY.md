---
phase: 03-customer-discovery
plan: 02
subsystem: api
tags: [java, spring-boot, mongodb, regex-search, map-points, mockito, junit5]

# Dependency graph
requires:
  - phase: 03-customer-discovery
    plan: 01
    provides: Wave 0 @Disabled test stubs + DAO interface declarations for searchByNameOrAddress and findMapPoints
  - phase: 02-controller-reports
    provides: RestaurantController, RestaurantDAO, RestaurantService — base architecture
provides:
  - GET /api/restaurants/search?q=... endpoint (case-insensitive $regex, public, no @PreAuthorize)
  - GET /api/restaurants/map-points endpoint (lightweight projection, public)
  - GET /my-bookmarks view route in ViewController
  - searchByNameOrAddress(String q, int limit) — real MongoDB $or $regex implementation
  - findMapPoints() — aggregation pipeline returning restaurantId, name, lat, lng, grade
affects: [03-03-customer-discovery, future template wiring for search and map features]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Direct DAO injection in RestaurantController for endpoints that do not require service-layer orchestration"
    - "findMapPoints uses raw database.getCollection().aggregate() (not the typed aggregate() helper) because the return type is List<Document> not a typed POJO"
    - "Wave 0 -> Wave 1 promotion: remove @Disabled + wire real implementation in the same plan"
    - "Drop unmockable @Mock RestaurantService when only static methods (toView) are needed — avoids Mockito VerifyError on Java 25"

key-files:
  created: []
  modified:
    - src/main/java/com/aflokkat/dao/RestaurantDAO.java
    - src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java
    - src/main/java/com/aflokkat/controller/RestaurantController.java
    - src/main/java/com/aflokkat/controller/ViewController.java
    - src/test/java/com/aflokkat/controller/RestaurantControllerSearchTest.java

key-decisions:
  - "RestaurantDAO injected directly into RestaurantController for search and map-points — no service-layer wrapper needed since there is no business logic beyond delegation"
  - "findMapPoints uses raw collection.aggregate() not the typed aggregate() helper — result type is List<Document> not a POJO"
  - "Remove @Mock RestaurantService from RestaurantControllerSearchTest — Mockito cannot inline-mock RestaurantService on Java 25 (VerifyError); since toView is static no instance mock is needed"

patterns-established:
  - "Wave 1 implementation: remove @Disabled, implement DAO, add controller endpoints in one plan"

requirements-completed: [CUST-01, CUST-03, CUST-04]

# Metrics
duration: 30min
completed: 2026-03-31
---

# Phase 3 Plan 2: Customer Discovery — Search + Map-Points Endpoints Summary

**MongoDB $regex search on name/street and aggregation-based map-points projection wired to two new public REST endpoints, with 4 Wave 0 test stubs promoted to GREEN**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-03-31T09:52:07Z
- **Completed:** 2026-03-31T10:22:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Replaced `UnsupportedOperationException` stubs in `RestaurantDAOImpl` with real MongoDB implementations for `searchByNameOrAddress` and `findMapPoints`
- Added `GET /api/restaurants/search` and `GET /api/restaurants/map-points` to `RestaurantController` — both public, no `@PreAuthorize`
- Added `GET /my-bookmarks` view route to `ViewController`
- Promoted all 4 Wave 0 `@Disabled` test stubs to GREEN in `RestaurantControllerSearchTest`

## Task Commits

1. **Task 1: Add DAO interface methods + implementations** - `845ed5f` (feat)
2. **Task 2: Add REST endpoints + ViewController route + enable tests** - `c883e6b` (feat)

**Plan metadata:** _(docs commit follows)_

## Files Created/Modified
- `src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java` - Real `searchByNameOrAddress` ($or $regex on name + address.street) and `findMapPoints` (aggregation pipeline) implementations
- `src/main/java/com/aflokkat/dao/RestaurantDAO.java` - Updated Javadoc to reflect Plan 03-02 implementation
- `src/main/java/com/aflokkat/controller/RestaurantController.java` - Added `RestaurantDAO` field injection, `GET /search` and `GET /map-points` endpoints
- `src/main/java/com/aflokkat/controller/ViewController.java` - Added `GET /my-bookmarks` route
- `src/test/java/com/aflokkat/controller/RestaurantControllerSearchTest.java` - Removed all 4 `@Disabled` annotations, removed unmockable `@Mock RestaurantService` field

## Decisions Made
- `RestaurantDAO` injected directly into `RestaurantController` alongside `RestaurantService` — the search and map-points endpoints call DAO directly since there is no business logic to encapsulate in the service layer.
- `findMapPoints` uses `database.getCollection(AppConfig.getMongoCollection()).aggregate(pipeline)` (raw `MongoDatabase`) rather than the typed `aggregate()` helper — the return type is `List<Document>` not a typed POJO, so the POJO-codec collection cannot be used.
- `@Mock RestaurantService` removed from test — Mockito inline mocking of `RestaurantService` triggers `VerifyError` on Java 25. Since all controller calls to `RestaurantService` in the tested methods are to the static `toView()` method, no instance mock is needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed unmockable @Mock RestaurantService from test**
- **Found during:** Task 2 (enabling test stubs)
- **Issue:** `@Mock private RestaurantService restaurantService` caused `Mockito cannot mock this class: RestaurantService — VerifyError` on Java 25. Only 1 of 4 tests errored (the framework tried to create the mock during any test execution in the class). The plan said to align the mock with `toView` static vs instance, but the correct fix is to remove the mock entirely since none of the tested methods call instance methods on `RestaurantService`.
- **Fix:** Removed `@Mock RestaurantService restaurantService` field and `import com.aflokkat.service.RestaurantService` from the test class. The controller's `RestaurantService::toView` static call works without any mock.
- **Files modified:** `src/test/java/com/aflokkat/controller/RestaurantControllerSearchTest.java`
- **Verification:** `mvn test -Dtest=RestaurantControllerSearchTest` exits 0 with 4 tests passing
- **Committed in:** `c883e6b` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug in test mock setup)
**Impact on plan:** Essential for test correctness on Java 25. No scope creep — the plan explicitly mentioned investigating static vs instance mock, and the fix aligns with the project's documented Java 25 Mockito limitation.

## Issues Encountered
- Java 25 + Mockito inline mock VerifyError for `RestaurantService` — same root cause as documented in STATE.md for prior phases. Resolved by removing the unnecessary mock (the static `toView` method needs no instance).

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `GET /api/restaurants/search` and `GET /api/restaurants/map-points` are live and publicly accessible
- Frontend templates (Plan 03-03) can now wire the search bar to `/api/restaurants/search?q=...`
- The `GET /my-bookmarks` route is registered; the Thymeleaf template is not yet created (Plan 03-03)
- Both endpoints are confirmed GREEN via unit tests

---
*Phase: 03-customer-discovery*
*Completed: 2026-03-31*

## Self-Check: PASSED
- `src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java` — FOUND
- `src/main/java/com/aflokkat/controller/RestaurantController.java` — FOUND
- `src/main/java/com/aflokkat/controller/ViewController.java` — FOUND
- `src/test/java/com/aflokkat/controller/RestaurantControllerSearchTest.java` — FOUND
- `.planning/phases/03-customer-discovery/03-02-SUMMARY.md` — FOUND
- Commit `845ed5f` — FOUND in git log
- Commit `c883e6b` — FOUND in git log
