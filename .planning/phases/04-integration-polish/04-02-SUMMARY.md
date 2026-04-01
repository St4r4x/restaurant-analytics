---
phase: 04-integration-polish
plan: "02"
subsystem: api
tags: [spring-boot, java, mvc, cleanup, dead-code]

# Dependency graph
requires:
  - phase: 04-01
    provides: English-translated templates and Java source files
provides:
  - ViewController with exactly 5 routes (index, login, restaurant, inspection-map, my-bookmarks)
  - RestaurantController without /cuisine-scores, /worst-cuisines, /popular-cuisines dead endpoints
  - Clean templates/ directory with exactly 5 files (no orphaned hygiene-radar.html or inspection.html)
affects: [future route audits, Swagger documentation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Dead-code removal: delete both the route handler and its associated template together
    - Import cleanup: remove unused aggregation imports when removing the only method that used them

key-files:
  created: []
  modified:
    - src/main/java/com/aflokkat/controller/ViewController.java
    - src/main/java/com/aflokkat/controller/RestaurantController.java
  deleted:
    - src/main/resources/templates/hygiene-radar.html
    - src/main/resources/templates/inspection.html

key-decisions:
  - "/hygiene-radar REST endpoint retained in RestaurantController — only the Thymeleaf view route was removed from ViewController"
  - "BoroughCuisineScore and CuisineScore imports removed as they were exclusively used by the deleted methods"

patterns-established:
  - "Route removal: delete view route, template, and any REST endpoints that exclusively served that view"

requirements-completed: []

# Metrics
duration: 10min
completed: 2026-04-01
---

# Phase 4 Plan 02: Remove Orphaned Routes and Dead Endpoints Summary

**Removed 2 orphaned Thymeleaf view routes, 3 dead REST endpoints, 2 unused imports, and deleted 2 orphaned HTML templates — leaving ViewController with 5 routes and RestaurantController with no hygiene-radar-specific analytics endpoints**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-04-01T09:20:00Z
- **Completed:** 2026-04-01T09:30:00Z
- **Tasks:** 2
- **Files modified:** 2 modified, 2 deleted

## Accomplishments
- ViewController.java now contains exactly 5 @GetMapping routes (/, /login, /restaurant/{id}, /inspection-map, /my-bookmarks)
- RestaurantController.java no longer exposes /cuisine-scores, /worst-cuisines, or /popular-cuisines
- Deleted orphaned templates hygiene-radar.html (678 lines) and inspection.html (371 lines)
- Removed unused imports BoroughCuisineScore and CuisineScore from RestaurantController

## Task Commits

Each task was committed atomically:

1. **Task 1: Remove orphaned routes from ViewController and delete orphaned templates** - `a85e84f` (refactor)
2. **Task 2: Remove dead endpoints from RestaurantController and verify tests pass** - `5d8c3fe` (refactor)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `src/main/java/com/aflokkat/controller/ViewController.java` - Orphaned /hygiene-radar and /inspection routes removed; 5 routes remain
- `src/main/java/com/aflokkat/controller/RestaurantController.java` - getCuisineScores, getWorstCuisines, getPopularCuisines methods removed; unused BoroughCuisineScore and CuisineScore imports removed
- `src/main/resources/templates/hygiene-radar.html` - Deleted (orphaned, 678 lines)
- `src/main/resources/templates/inspection.html` - Deleted (orphaned, 371 lines)

## Decisions Made
- The `/hygiene-radar` REST endpoint in RestaurantController was kept (it serves map overlays); only the Thymeleaf view route `/hygiene-radar` was removed from ViewController
- BoroughCuisineScore and CuisineScore imports were removed as they were exclusively used by the deleted methods — confirmed no other method references them

## Deviations from Plan

None - plan executed exactly as written. Task 1 was already committed in the previous session; Task 2 changes existed in the working tree but had not been committed, so Task 2 was committed in this session.

## Issues Encountered
- `mvn test` with the full test suite times out in this environment (no live MongoDB/PostgreSQL). Ran targeted test subsets: unit tests (33 tests, 0 failures), RestaurantControllerSearchTest (4 tests, 0 failures), and `mvn compile` to confirm no compilation errors. All pass.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Dead code is fully removed; the codebase surface area is smaller and consistent with the live navigation
- templates/ directory now exactly matches the routes exposed by ViewController
- Ready for any further integration or polish tasks in Phase 04

---
*Phase: 04-integration-polish*
*Completed: 2026-04-01*
