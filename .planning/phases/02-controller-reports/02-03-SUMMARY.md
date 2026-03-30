---
phase: 02-controller-reports
plan: "03"
subsystem: api
tags: [spring-boot, multipart, file-upload, docker-volume, java]

# Dependency graph
requires:
  - phase: 02-controller-reports/02-01
    provides: ReportController, InspectionReportEntity with photoPath field, ReportRepository
  - phase: 02-controller-reports/02-02
    provides: patchReport() endpoint, ownership check pattern
provides:
  - POST /api/reports/{id}/photo — multipart file upload saved to named Docker volume
  - GET /api/reports/{id}/photo — streams file bytes with probed Content-Type
  - AppConfig.getUploadsDir() — static getter resolving APP_UPLOADS_DIR env var
  - uploads_data named Docker volume wired in docker-compose.yml
affects: [02-controller-reports, phase-03-customer-ui]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "MultipartFile upload: Files.createDirectories + Files.copy(REPLACE_EXISTING) to {uploadsDir}/{id}/{ts}_{filename}"
    - "File streaming: UrlResource wrapping Path, ResponseEntity<Resource> with probed Content-Type"
    - "Static config override in tests: reflection on AppConfig.properties field (avoids mockStatic VerifyError on Java 25)"

key-files:
  created: []
  modified:
    - src/main/java/com/aflokkat/config/AppConfig.java
    - src/main/resources/application.properties
    - docker-compose.yml
    - src/main/java/com/aflokkat/controller/ReportController.java
    - src/test/java/com/aflokkat/controller/ReportControllerTest.java

key-decisions:
  - "mockStatic(AppConfig.class) causes java.lang.VerifyError on Java 25 (Byte Buddy limitation); use reflection to patch AppConfig.properties static field in tests instead"
  - "photoUpload_returns404_whenReportNotFound must stub userRepository.findByUsername() — controller calls getCurrentUser() before findById()"

patterns-established:
  - "Photo upload pattern: Files.createDirectories(targetDir) before Files.copy() — Docker volume only pre-creates root, not subdirectories"
  - "getPhoto() returns ResponseEntity<Resource> (not Map<String,Object>) — Spring MVC allows mixed return types per handler method"

requirements-completed: [CTRL-04]

# Metrics
duration: 20min
completed: 2026-03-30
---

# Phase 02 Plan 03: Photo Upload and Streaming Summary

**POST /{id}/photo saves multipart files to uploads_data Docker named volume; GET /{id}/photo streams bytes with probed Content-Type; all 12 ReportControllerTest tests GREEN**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-03-30T15:30:00Z
- **Completed:** 2026-03-30T15:43:30Z
- **Tasks:** 2 of 3 complete (Task 3 is a human checkpoint — awaiting manual verification)
- **Files modified:** 5

## Accomplishments
- `AppConfig.getUploadsDir()` added, resolving from `APP_UPLOADS_DIR` env var with `/app/uploads` default
- `uploads_data` named volume declared and mounted in `docker-compose.yml` — photos survive `docker compose down`
- `uploadPhoto()` and `getPhoto()` endpoints added to `ReportController`, completing CTRL-04
- All 12 `ReportControllerTest` tests GREEN, no regressions in 63 unit tests

## Task Commits

Each task was committed atomically:

1. **Task 1: AppConfig.getUploadsDir() + application.properties + docker-compose.yml volume** - `b188ad2` (feat)
2. **Task 2 RED: failing tests for photo upload endpoints** - `748d16e` (test)
3. **Task 2 GREEN: POST /{id}/photo and GET /{id}/photo endpoints** - `cc509a1` (feat)

_Note: Task 3 is a human-verify checkpoint — not yet committed._

## Files Created/Modified
- `src/main/java/com/aflokkat/config/AppConfig.java` - Added `getUploadsDir()` static method
- `src/main/resources/application.properties` - Added `app.uploads.dir=/app/uploads` property
- `docker-compose.yml` - Added `APP_UPLOADS_DIR` env var, `uploads_data:/app/uploads` volume mount on app service, top-level `uploads_data:` declaration
- `src/main/java/com/aflokkat/controller/ReportController.java` - Added `uploadPhoto()` and `getPhoto()` endpoints with required imports
- `src/test/java/com/aflokkat/controller/ReportControllerTest.java` - Replaced three `assumeTrue(false)` stubs with real test implementations

## Decisions Made
- `mockStatic(AppConfig.class)` causes `java.lang.VerifyError` on Java 25 with Byte Buddy 1.16 — confirmed same class of issue as Phase 01-02 (agent attachment). Used reflection to patch the `AppConfig.properties` static `Properties` field in tests instead. This avoids `mockStatic` entirely while keeping tests hermetic via `@TempDir`.
- `photoUpload_returns404_whenReportNotFound` must stub `userRepository.findByUsername()` because the controller calls `getCurrentUser()` before `findById()` — without the stub, `IllegalArgumentException` is thrown early and `findById(99L)` is never reached, causing `UnnecessaryStubbingException` in strict mode.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Replaced mockStatic with reflection-based AppConfig.properties override**
- **Found during:** Task 2 (RED phase — running failing tests)
- **Issue:** `mockStatic(AppConfig.class)` throws `java.lang.VerifyError` on Java 25 runtime (Byte Buddy bytecode instrumentation fails for static initializer classes)
- **Fix:** Added private `setUploadsDir(String path)` helper using reflection to set `AppConfig.properties.setProperty("app.uploads.dir", path)` — works because `System.getenv(APP_UPLOADS_DIR)` returns null in test JVM (no env override), so `getProperty()` falls through to the patched properties field
- **Files modified:** `src/test/java/com/aflokkat/controller/ReportControllerTest.java`
- **Verification:** 3 photo tests pass GREEN; no `VerifyError`
- **Committed in:** `748d16e` (test commit), `cc509a1` (feat commit)

**2. [Rule 1 - Bug] Fixed UnnecessaryStubbingException in photoUpload_returns404_whenReportNotFound**
- **Found during:** Task 2 (GREEN phase — first test run)
- **Issue:** Test stubbed `findById(99L)` but never called it — controller calls `getCurrentUser()` first, which requires `userRepository.findByUsername()` stub; missing stub caused early exception, making `findById` stub unreachable
- **Fix:** Added `when(userRepository.findByUsername("ctrl_user")).thenReturn(Optional.of(user))` before the `findById` stub
- **Files modified:** `src/test/java/com/aflokkat/controller/ReportControllerTest.java`
- **Verification:** All 12 tests GREEN with no Mockito strict-stubbing violations
- **Committed in:** `cc509a1` (feat commit)

---

**Total deviations:** 2 auto-fixed (1 Rule 3 blocking, 1 Rule 1 bug)
**Impact on plan:** Both fixes required for correct test execution. No scope creep. Production controller code matches plan specification exactly.

## Issues Encountered
- Java 25 + Byte Buddy 1.16 limitation on `mockStatic` for classes with static initializers — resolved via reflection pattern (same family of issue as Phase 01-02 documented in STATE.md)

## User Setup Required
Task 3 (human checkpoint) requires manual verification:

1. `docker compose up -d`
2. Login as `controller_test`, create a report, upload a photo via `POST /api/reports/{id}/photo`
3. Verify photo accessible via `GET /api/reports/{id}/photo` (HTTP 200)
4. `docker compose down && docker compose up -d` — re-login, verify photo still accessible
5. If confirmed: type "approved" to resume

## Next Phase Readiness
- All four Phase 2 endpoints implemented: CTRL-01 (create), CTRL-02 (list), CTRL-03 (patch), CTRL-04 (photo)
- Pending: manual Docker volume persistence verification (Task 3 checkpoint)
- Phase 3 (Customer UI) can begin after Task 3 approval

---
*Phase: 02-controller-reports*
*Completed: 2026-03-30*

## Self-Check: PASSED
- AppConfig.java: FOUND
- ReportController.java: FOUND
- application.properties: FOUND
- docker-compose.yml: FOUND
- 02-03-SUMMARY.md: FOUND
- b188ad2 (Task 1 commit): FOUND
- 748d16e (Task 2 RED commit): FOUND
- cc509a1 (Task 2 GREEN commit): FOUND
