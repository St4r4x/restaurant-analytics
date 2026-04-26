# Deferred Items — Phase 21

## Out-of-scope failures found during Plan 21-04 execution

### UserControllerMeTest — getStatusCodeValue() removed in Spring 6.x

**File:** `src/test/java/com/st4r4x/controller/UserControllerMeTest.java`
**Lines:** 61, 79, 98
**Issue:** `ResponseEntity.getStatusCodeValue()` was removed in Spring 6.x. These tests call
`response.getStatusCodeValue()` which does not exist in Spring Boot 4.x / Spring 6.x.
**Fix needed:** Replace `response.getStatusCodeValue()` with `response.getStatusCode().value()`
**Tests failing:** 5 (in UserControllerMeTest)
**Scope:** This file is NOT in Plan 21-04's `files_modified` list. Last touched in commit d27b9c7
(pre-upgrade). Should be fixed in a subsequent plan (21-05 or a dedicated 21-06).
**Discovered during:** Task 2 verification (`mvn test`)
