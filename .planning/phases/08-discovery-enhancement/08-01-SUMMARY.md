---
phase: 08-discovery-enhancement
plan: "01"
subsystem: inspection-api
tags: [disc-02, uncontrolled, inspection, dao, dto, wave0]
dependency_graph:
  requires: []
  provides: [UncontrolledEntry DTO, findUncontrolled DAO method, GET /api/inspection/uncontrolled, GET /api/inspection/uncontrolled/export.csv, GET /uncontrolled view route, findMapPoints borough+cuisine fields]
  affects: [InspectionController, ViewController, RestaurantDAO, RestaurantDAOImpl]
tech_stack:
  added: []
  patterns: [direct DAO injection in controller (consistent with AnalyticsController pattern), BsonProperty DTO mapping, MongoDB aggregation with $or match, $addFields + $toLong date conversion]
key_files:
  created:
    - src/main/java/com/aflokkat/dto/UncontrolledEntry.java
    - src/test/java/com/aflokkat/controller/ViewControllerUncontrolledTest.java
    - src/test/java/com/aflokkat/controller/InspectionControllerUncontrolledTest.java
  modified:
    - src/main/java/com/aflokkat/dao/RestaurantDAO.java
    - src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java
    - src/main/java/com/aflokkat/controller/InspectionController.java
    - src/main/java/com/aflokkat/controller/ViewController.java
decisions:
  - RestaurantDAO injected directly into InspectionController for uncontrolled endpoints — consistent with AnalyticsController pattern; avoids Mockito Java 25 limitation with constructor-injected services
  - Class-level @PreAuthorize removed from InspectionController; method-level @PreAuthorize added to existing at-risk endpoints to preserve security behavior
  - findUncontrolled uses $addFields + $toLong + $toDate pipeline to convert grades.date array element to milliseconds for comparison
  - daysSinceInspection computed in MongoDB pipeline using $subtract(System.currentTimeMillis(), lastInspectionMs) / 86_400_000
  - findMapPoints extended with borough and cuisine projections (required by DISC-01 client-side map filter in Plan 08-03)
metrics:
  duration_minutes: 30
  completed_date: "2026-04-04"
  tasks_completed: 3
  files_changed: 7
requirements_satisfied: [DISC-02]
---

# Phase 08 Plan 01: DISC-02 Backend — Uncontrolled Restaurants API Summary

**One-liner:** Public `GET /api/inspection/uncontrolled` and CSV export backed by MongoDB aggregation matching grade C/Z or no inspection in 12 months, with ViewController route and Wave 0 test stubs.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Wave 0 test stubs for DISC-02 | 3d63d00 | ViewControllerUncontrolledTest.java, InspectionControllerUncontrolledTest.java |
| 2 | UncontrolledEntry DTO + findUncontrolled DAO + findMapPoints extension | b12952c | UncontrolledEntry.java, RestaurantDAO.java, RestaurantDAOImpl.java |
| 3 | InspectionController new endpoints + ViewController /uncontrolled route | fd2bbc7 | InspectionController.java, ViewController.java |

## What Was Built

### UncontrolledEntry DTO (`src/main/java/com/aflokkat/dto/UncontrolledEntry.java`)
7-field DTO matching aggregation output: `restaurantId`, `name`, `borough`, `cuisine`, `lastGrade`, `lastScore`, `daysSinceInspection`. All fields annotated with `@BsonProperty`.

### findUncontrolled() DAO method
MongoDB aggregation pipeline:
1. Optional `$match` borough pre-filter
2. `$addFields` — extract `lastGrade`, `lastScore`, `lastInspectionDate` from `grades[0]`
3. `$addFields` — convert `lastInspectionDate` to milliseconds via `$toLong($toDate(...))`
4. `$match` — `$or: [lastGrade in [C,Z], lastInspectionMs < now-12months]`
5. `$project` — final fields + `daysSinceInspection` via `$divide($subtract(now, lastInspectionMs), 86_400_000)`
6. `$sort` by `lastScore desc`, `$limit`

### findMapPoints() extension
Added `.append("borough", 1)` and `.append("cuisine", 1)` to the existing `$project` stage. Required by Plan 08-03 (DISC-01 client-side map filter bar that filters markers by borough and cuisine).

### InspectionController changes
- Removed class-level `@PreAuthorize("hasRole('ADMIN')")` — was blocking the new public endpoints
- Added method-level `@PreAuthorize("hasRole('ADMIN')")` to `getAtRisk()` and `exportAtRiskCsv()` — preserves existing security
- Added `@Autowired RestaurantDAO restaurantDAO` field
- Added `GET /api/inspection/uncontrolled` — public, delegates to `restaurantDAO.findUncontrolled(borough, limit)`
- Added `GET /api/inspection/uncontrolled/export.csv` — public, text/csv with attachment disposition

### ViewController changes
Added `GET /uncontrolled` route returning `"uncontrolled"` Thymeleaf view name.

### Test stubs (Wave 0)
- `ViewControllerUncontrolledTest`: asserts `viewController.uncontrolled()` returns `"uncontrolled"`
- `InspectionControllerUncontrolledTest`: 
  - `testUncontrolled_returns200`: MockMvc GET with Mockito stub, expects 200 + `$.status=="success"` + `$.data` is array
  - `testExportCsv_returnsTextCsv`: MockMvc GET, expects 200 + `Content-Type: text/csv`

## Test Results

```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
ViewControllerUncontrolledTest: 1/1 PASS
InspectionControllerUncontrolledTest: 2/2 PASS
BUILD SUCCESS
```

Full suite: Pre-existing failures in `SecurityConfigTest` (2 failures: dashboard auth tests using full Spring Security context — unrelated to this plan) and `RestaurantDAOIntegrationTest` (15 errors: requires live MongoDB at `mongodb:27017` — expected, noted in CLAUDE.md). All unit tests pass.

## Deviations from Plan

### Auto-added (Rule 2 - compilation requirement)

**1. UncontrolledEntry.java created in Task 1 (not Task 2)**
- **Found during:** Task 1 — test compilation requires the DTO to exist
- **Fix:** Created UncontrolledEntry.java as part of Task 1 stub phase so tests compile
- **Files modified:** `src/main/java/com/aflokkat/dto/UncontrolledEntry.java`
- **Commit:** 3d63d00 (included in Task 1 commit, then finalized in b12952c)

**2. InspectionController fully updated in Task 1**
- **Found during:** Task 1 — `InspectionControllerUncontrolledTest` uses `@InjectMocks InspectionController` with `@Mock RestaurantDAO`; if `restaurantDAO` field doesn't exist on the controller, `@InjectMocks` cannot wire it
- **Fix:** Added full InspectionController changes (new field + new methods) during Task 1 to make tests compile and pass
- **Result:** Tasks effectively executed together; commits split logically by concern

None of the above deviate from the plan's intended outcome — they are ordering adjustments to satisfy compilation requirements.

## Known Stubs

None. All implementations are substantive:
- `findUncontrolled()` has a complete MongoDB aggregation pipeline
- `getUncontrolled()` and `exportUncontrolledCsv()` have complete request handling logic
- The Thymeleaf template (`uncontrolled.html`) is intentionally not in this plan — it is Plan 08-02's deliverable

## Threat Flags

No new security-relevant surface introduced beyond what the plan intended. The two new endpoints (`/api/inspection/uncontrolled` and `/api/inspection/uncontrolled/export.csv`) are intentionally public per DISC-02 requirements. The at-risk endpoints retain their existing `@PreAuthorize("hasRole('ADMIN')")` protection.

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| UncontrolledEntry.java exists | FOUND |
| RestaurantDAO.java exists | FOUND |
| RestaurantDAOImpl.java exists | FOUND |
| InspectionController.java exists | FOUND |
| ViewController.java exists | FOUND |
| ViewControllerUncontrolledTest.java exists | FOUND |
| InspectionControllerUncontrolledTest.java exists | FOUND |
| Commit 3d63d00 exists | FOUND |
| Commit b12952c exists | FOUND |
| Commit fd2bbc7 exists | FOUND |
| findUncontrolled in DAO interface | FOUND |
| findUncontrolled in DAOImpl | FOUND |
| daysSinceInspection in DTO | FOUND |
| borough in findMapPoints | FOUND |
| cuisine in findMapPoints | FOUND |
| No class-level @PreAuthorize on InspectionController | VERIFIED |
| uncontrolled() in ViewController | FOUND |
