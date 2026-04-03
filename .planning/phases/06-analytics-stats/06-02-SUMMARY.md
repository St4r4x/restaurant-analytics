---
phase: 06-analytics-stats
plan: 02
subsystem: api
tags: [mongodb, aggregation, analytics, java, spring-boot]

# Dependency graph
requires:
  - phase: 06-01
    provides: AnalyticsController placeholder + Wave 0 test stubs with @Disabled
provides:
  - "GET /api/analytics/kpis — 4 city-wide KPI values (totalRestaurants, percentGradeA, avgScore, atRiskCount)"
  - "GET /api/analytics/borough-grades — per-borough A/B/C grade distribution"
  - "GET /api/analytics/cuisine-rankings — top 10 cleanest and top 10 worst cuisine lists"
  - "GET /api/analytics/at-risk — 50 most at-risk restaurants (last grade C or Z)"
  - "RestaurantDAO: findBoroughGradeDistribution(), findBestCuisinesByAverageScore(int), countAtRiskRestaurants()"
  - "RestaurantService: getBoroughGradeDistribution(), getWorstCuisinesByAverageScore (real impl), getBestCuisinesByAverageScore (real impl), countAtRiskRestaurants()"
affects: [06-03, analytics-page, ui]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AnalyticsController injects RestaurantDAO directly — Mockito cannot mock RestaurantService with constructor injection on Java 25"
    - "MongoDB $count aggregation stage for counting without loading documents (countAtRiskRestaurants)"
    - "Two-stage $group pipeline for borough grade distribution (group by borough+grade, then re-group by borough)"

key-files:
  created:
    - src/main/java/com/aflokkat/controller/AnalyticsController.java
  modified:
    - src/main/java/com/aflokkat/dao/RestaurantDAO.java
    - src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java
    - src/main/java/com/aflokkat/service/RestaurantService.java
    - src/test/java/com/aflokkat/controller/AnalyticsControllerTest.java

key-decisions:
  - "AnalyticsController injects RestaurantDAO directly (not RestaurantService): Mockito 5 cannot mock RestaurantService on Java 25 because it has a constructor-injection dependency. Consistent with RestaurantController search/map-points pattern established in Phase 03."
  - "findBoroughGradeDistribution uses raw database.getCollection().aggregate() not typed aggregate() helper: return type is List<Document> not a POJO, matching findMapPoints() pattern"
  - "getInteger(field, 0) returns primitive int on Java — cast to (long) not .longValue() for countAtRiskRestaurants"

patterns-established:
  - "Analytics DAO methods: raw aggregate for Document return types, typed aggregate() helper for POJO return types (CuisineScore)"
  - "At-risk counting: $addFields lastGrade + $match + $count pipeline — never loads documents"

requirements-completed: [STAT-01, STAT-02, STAT-03, STAT-04]

# Metrics
duration: 27min
completed: 2026-04-03
---

# Phase 6 Plan 02: Analytics Stats — Backend Endpoints Summary

**4 MongoDB aggregation-backed analytics endpoints with borough grade distribution, cuisine rankings (cleanest/worst), KPI summary, and at-risk restaurant list — all 4 AnalyticsControllerTest tests enabled and passing**

## Performance

- **Duration:** 27 min
- **Started:** 2026-04-03T16:00:00Z
- **Completed:** 2026-04-03T16:27:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Implemented 3 new DAO methods (findBoroughGradeDistribution, findBestCuisinesByAverageScore, countAtRiskRestaurants) using raw MongoDB aggregation pipelines
- Replaced 2 RestaurantService stubs with real DAO delegation; added 2 more service wrappers
- Replaced AnalyticsController placeholder with 4 fully working REST endpoints
- Removed all 4 @Disabled annotations from AnalyticsControllerTest — all 4 tests pass (0 skipped)

## Task Commits

1. **Task 1: Extend DAO + Service with 3 new analytics methods** - `071c0e6` (feat)
2. **Task 2: Implement AnalyticsController with 4 endpoints + enable tests** - `76fe34d` (feat)

## Files Created/Modified
- `src/main/java/com/aflokkat/controller/AnalyticsController.java` - Full implementation replacing Wave 0 placeholder; 4 endpoints
- `src/main/java/com/aflokkat/dao/RestaurantDAO.java` - 3 new method declarations added
- `src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java` - 3 new aggregation pipeline implementations
- `src/main/java/com/aflokkat/service/RestaurantService.java` - Stub methods replaced with real DAO delegation; 2 new wrappers added
- `src/test/java/com/aflokkat/controller/AnalyticsControllerTest.java` - @Disabled removed from all 4 tests; mocks updated

## Decisions Made
- **AnalyticsController uses RestaurantDAO directly** (not RestaurantService): Mockito 5 cannot mock `RestaurantService` on Java 25 because it uses constructor injection — `@Autowired public RestaurantService(RestaurantDAO restaurantDAO)`. This causes "Mockito cannot mock this class" at runtime. Injecting the DAO interface directly resolves the test isolation. Consistent with `RestaurantController` search/map-points pattern from Phase 03.
- **`getInteger(field, 0)` returns primitive `int`** in BSON Document — calling `.longValue()` fails compilation. Cast with `(long)` instead.
- **findBoroughGradeDistribution uses `database.getCollection().aggregate()`** not the typed `aggregate()` helper — return type is `List<Document>`, matching `findMapPoints()` pattern.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed `int cannot be dereferenced` in countAtRiskRestaurants**
- **Found during:** Task 1 (compilation after DAO impl)
- **Issue:** `results.get(0).getInteger("total", 0).longValue()` — BSON `Document.getInteger(String, int)` with primitive default returns `int` not `Integer`, so `.longValue()` is invalid
- **Fix:** Changed to `(long) results.get(0).getInteger("total", 0)`
- **Files modified:** `src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java`
- **Verification:** `mvn compile` exits 0
- **Committed in:** `071c0e6` (Task 1 commit)

**2. [Rule 1 - Bug] AnalyticsController refactored to inject RestaurantDAO**
- **Found during:** Task 2 (test execution)
- **Issue:** `@Mock RestaurantService` fails on Java 25 with "Mockito cannot mock this class" — constructor-injected service is unmockable with inline mock maker
- **Fix:** Changed `AnalyticsController` from `@Autowired RestaurantService` to `@Autowired RestaurantDAO`; updated test from `@Mock RestaurantService` to `@Mock RestaurantDAO`
- **Files modified:** `src/main/java/com/aflokkat/controller/AnalyticsController.java`, `src/test/java/com/aflokkat/controller/AnalyticsControllerTest.java`
- **Verification:** 4 tests pass, `mvn test -Dtest=AnalyticsControllerTest` exits 0
- **Committed in:** `76fe34d` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs)
**Impact on plan:** Both fixes were necessary for correctness. Controller still delivers exact same REST interface. No scope creep.

## Issues Encountered
- Java 25 Mockito inline mock limitation is a recurring constraint in this project — applies to any `@Service` with constructor injection. Documented in STATE.md decisions.

## Next Phase Readiness
- All 4 analytics endpoints are functional and tested
- Plan 06-03 (UI page) can now consume `/api/analytics/*` endpoints
- `ViewControllerAnalyticsTest` still has 1 `@Disabled` test awaiting `ViewController.analytics()` method from 06-03

---
*Phase: 06-analytics-stats*
*Completed: 2026-04-03*

## Self-Check: PASSED

- FOUND: AnalyticsController.java
- FOUND: RestaurantDAO.java (3 new methods)
- FOUND: RestaurantDAOImpl.java (3 new implementations)
- FOUND: RestaurantService.java (real wrappers)
- FOUND: AnalyticsControllerTest.java (0 @Disabled)
- FOUND: 06-02-SUMMARY.md
- FOUND: commit 071c0e6 (Task 1)
- FOUND: commit 76fe34d (Task 2)
- Tests: 26 passing, 1 skipped (ViewControllerAnalyticsTest — deliberately @Disabled for Plan 06-03)
