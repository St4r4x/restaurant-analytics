---
phase: 14-testcontainers-integration-tests
fixed_at: 2026-04-12T00:00:00Z
review_path: .planning/phases/14-testcontainers-integration-tests/14-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 14: Code Review Fix Report

**Fixed at:** 2026-04-12
**Source review:** .planning/phases/14-testcontainers-integration-tests/14-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: `System.clearProperty("mongodb.uri")` never called in `UserRepositoryIT`

**Files modified:** `src/test/java/com/aflokkat/repository/UserRepositoryIT.java`
**Commit:** 608af1c
**Applied fix:** Added `import org.junit.AfterClass;` and a `tearDownClass()` static method annotated with `@AfterClass` that calls `System.clearProperty("mongodb.uri")`, placed immediately after the static initializer block.

### WR-02: `application-test.properties` comment says `@MockBean` but code uses no mock

**Files modified:** `src/test/resources/application-test.properties`
**Commit:** 55f09b2
**Applied fix:** Replaced the stale comment `# Redis — no TC container; RestaurantCacheService is @MockBean in UserRepositoryIT` with two accurate lines: `# Redis — no TC container; RestaurantCacheService is NOT mocked.` and `# Lettuce connects lazily — no Redis connection is attempted during these JPA-only tests.`

### WR-03: Sort assertion in `testUseCase3_WorstCuisines_SortedByScore` is vacuously true when fewer than 2 results

**Files modified:** `src/test/java/com/aflokkat/dao/RestaurantDAOIT.java`
**Commit:** cfe5734
**Applied fix:** Added `assertTrue("Expected at least 2 results to verify sort order", results.size() >= 2);` immediately before the sort-order loop in `testUseCase3_WorstCuisines_SortedByScore`, ensuring the loop body is actually exercised.

---

_Fixed: 2026-04-12_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
