---
phase: 02-controller-reports
plan: "02"
subsystem: api
tags: [java, spring-boot, jpa, mockito, patch, ownership-check, tdd]

# Dependency graph
requires:
  - phase: 02-controller-reports/02-01
    provides: ReportController with POST/GET endpoints, InspectionReportEntity with FetchType.LAZY user, ReportRequest DTO, Grade/Status enums
provides:
  - PATCH /api/reports/{id} endpoint with partial update and ownership check
  - patchReport_* unit tests GREEN (9 total pass in ReportControllerTest)
affects:
  - 02-03 (photo upload — builds on same ReportController)
  - 02-04 (customer UI — references report data shape)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Manual 403 response map to avoid ResponseUtil.errorResponse() returning 400 for security errors"
    - "ArgumentCaptor for partial-update assertion instead of spy() (Byte Buddy cannot instrument JPA entities on Java 25)"
    - "assumeTrue(false) for stub tests (JUnit 5.8.2 — abort(String) unavailable)"

key-files:
  created: []
  modified:
    - src/main/java/com/aflokkat/controller/ReportController.java
    - src/test/java/com/aflokkat/controller/ReportControllerTest.java

key-decisions:
  - "spy() on JPA entity fails on Java 25 — use ArgumentCaptor on reportRepository.save() to verify partial-update behavior"
  - "Manual 403 body map returned directly — do NOT use ResponseUtil.errorResponse() which maps all exceptions to 400/500"

patterns-established:
  - "Ownership-guard pattern: findById → check owner id → return 403 map directly if mismatch"
  - "Partial-update pattern: null-guard each field before setter call; ignore immutable fields like restaurantId"

requirements-completed: [CTRL-03]

# Metrics
duration: 8min
completed: 2026-03-30
---

# Phase 02 Plan 02: PATCH /api/reports/{id} Summary

**PATCH endpoint on ReportController with ownership check (HTTP 403 map) and partial update (null fields preserved), with 3 patchReport unit tests GREEN via ArgumentCaptor**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-30T13:26:09Z
- **Completed:** 2026-03-30T13:34:00Z
- **Tasks:** 1
- **Files modified:** 2

## Accomplishments
- Added `patchReport()` to `ReportController` with `@PatchMapping("/{id}")` and `@Transactional`
- Ownership check returns HTTP 403 via manually constructed response map (not thrown exception)
- Partial update applies only non-null request fields; `restaurantId` silently ignored
- Replaced 3 `assumeTrue(false)` stubs in `ReportControllerTest` with real assertions, all GREEN
- No regressions: 9 tests pass in `ReportControllerTest`, 3 `photoUpload_*` stubs still skipped

## Task Commits

Each task was committed atomically:

1. **Task 1: PATCH /api/reports/{id} endpoint + patchReport_* tests GREEN** - `39c923b` (feat)

**Plan metadata:** (to be added)

_Note: TDD tasks may have multiple commits (test → feat → refactor). RED-phase fix (spy to ArgumentCaptor) was folded into the single task commit._

## Files Created/Modified
- `src/main/java/com/aflokkat/controller/ReportController.java` - Added `patchReport()` method after `listReports()`
- `src/test/java/com/aflokkat/controller/ReportControllerTest.java` - Replaced 3 stub methods with real assertions

## Decisions Made
- **spy() incompatible with Java 25 + Byte Buddy:** `spy(InspectionReportEntity)` threw `MockitoException: Could not modify all classes`. Used `ArgumentCaptor<InspectionReportEntity>` on `reportRepository.save()` to capture the saved entity and assert its field values directly. No change to production code needed.
- **Manual 403 response map:** `ResponseUtil.errorResponse()` maps `IllegalArgumentException` to 400 and anything else to 500 — it cannot produce 403. The plan already specified building the map manually; confirmed and followed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Replaced spy() with ArgumentCaptor for partial-update test**
- **Found during:** Task 1 RED phase (patchReport_appliesOnlyNonNullFields_leavingOthersUnchanged)
- **Issue:** `spy(InspectionReportEntity.class)` throws `MockitoException: Could not modify all classes` on Java 25 — Byte Buddy cannot instrument JPA entity classes in the inline mock setup used by this project (same root cause as Phase 01-02 issue).
- **Fix:** Replaced `spy(entity)` pattern with `ArgumentCaptor.forClass(InspectionReportEntity.class)`, capturing the argument passed to `reportRepository.save()` and asserting field values directly via `assertEquals`/`assertNull`.
- **Files modified:** `src/test/java/com/aflokkat/controller/ReportControllerTest.java`
- **Verification:** Test passes (GREEN) without spy; all 3 patchReport tests pass.
- **Committed in:** `39c923b` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - Bug)
**Impact on plan:** Test correctness preserved with equivalent behavior verification. No scope creep. No production code affected.

## Issues Encountered
- Byte Buddy / Java 25 incompatibility with `spy()` on JPA entities — resolved via ArgumentCaptor pattern (consistent with Phase 01-02 known issue with Mockito on this JVM).

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `ReportController` now has POST, GET, and PATCH endpoints fully implemented and tested
- Ready for Plan 02-03: photo upload endpoint (`POST /api/reports/{id}/photo` and `GET /api/reports/{id}/photo`)
- Named Docker volume for `uploads_data` must be present in `docker-compose.yml` before photo upload tests run (pre-existing blocker noted in STATE.md)

---
*Phase: 02-controller-reports*
*Completed: 2026-03-30*
