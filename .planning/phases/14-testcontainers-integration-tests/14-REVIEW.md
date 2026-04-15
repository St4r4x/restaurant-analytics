---
phase: 14-testcontainers-integration-tests
reviewed: 2026-04-12T00:00:00Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - pom.xml
  - src/main/java/com/aflokkat/config/AppConfig.java
  - src/test/java/com/aflokkat/dao/RestaurantDAOIT.java
  - src/test/java/com/aflokkat/repository/UserRepositoryIT.java
  - src/test/resources/application-test.properties
  - CHANGELOG.md
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
status: issues_found
---

# Phase 14: Code Review Report

**Reviewed:** 2026-04-12
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

Phase 14 introduces Testcontainers-based integration tests for `RestaurantDAOIT` (MongoDB) and `UserRepositoryIT` (PostgreSQL + MongoDB). The overall design is solid: the `@ClassRule` lifecycle, static-block container startup order, and `AppConfig` tier-0 `System.getProperty` injection are all handled correctly and well-commented.

Three warnings are raised: a missing `System.clearProperty` teardown in `UserRepositoryIT` that can pollute subsequent test runs, a stale Redis comment in `application-test.properties` that contradicts the actual code, and a slightly too-loose sort assertion in `testUseCase3_WorstCuisines_SortedByScore`. Four informational items cover unused imports, a missing `@After`-class close on the MongoDB singleton in `UserRepositoryIT`, a magic threshold in the use-case 4 test, and the minor comment/naming inconsistency in the CHANGELOG.

No critical (security, data-loss, or crash) issues were found.

## Warnings

### WR-01: `System.clearProperty("mongodb.uri")` never called in `UserRepositoryIT`

**File:** `src/test/java/com/aflokkat/repository/UserRepositoryIT.java:71`
**Issue:** `RestaurantDAOIT.tearDownClass()` correctly clears the `mongodb.uri` system property after each run (line 58). `UserRepositoryIT` sets the same property in its static initializer block (line 71) but never clears it. If both IT classes execute in the same JVM (which Failsafe does by default), the stale property from `UserRepositoryIT` will be visible to any subsequent test class that calls `AppConfig.getProperty("mongodb.uri")`, potentially pointing at a stopped container URI.
**Fix:** Add an `@AfterClass` method that clears the property:

```java
@AfterClass
public static void tearDownClass() {
    System.clearProperty("mongodb.uri");
}
```

---

### WR-02: `application-test.properties` comment says `@MockBean` but code uses no mock

**File:** `src/test/resources/application-test.properties:24`
**Issue:** The comment on line 24 reads `# Redis — no TC container; RestaurantCacheService is @MockBean in UserRepositoryIT`. However, the `UserRepositoryIT` class explicitly documents (lines 96–104) that `@MockBean` was removed because it causes `java.lang.VerifyError` on Java 25, and that Lettuce connects lazily so no mock is needed. The stale comment creates confusion about whether the test class is currently broken or intentionally using a mock.
**Fix:** Update the comment to reflect the actual state:

```properties
# Redis — no TC container; RestaurantCacheService is NOT mocked.
# Lettuce connects lazily — no Redis connection is attempted during these JPA-only tests.
```

---

### WR-03: Sort assertion in `testUseCase3_WorstCuisines_SortedByScore` is vacuously true when fewer than 2 results are returned

**File:** `src/test/java/com/aflokkat/dao/RestaurantDAOIT.java:226-233`
**Issue:** The loop `for (int i = 1; i < results.size(); i++)` only executes if there are at least 2 elements. With the seeded data, Manhattan has 4 cuisines and the limit is 5, so this is safe today — but if the seed data is later reduced to a single cuisine per borough or the borough name changes, the sort invariant is never validated and the test passes vacuously. A defensive assertion on the result size guards against this regression.
**Fix:** Add an assertion before the loop to ensure the sort check is actually exercised:

```java
@Test
public void testUseCase3_WorstCuisines_SortedByScore() {
    List<CuisineScore> results =
        restaurantDAO.findWorstCuisinesByAverageScoreInBorough("Manhattan", 5);
    assertTrue("Expected at least 2 results to verify sort order", results.size() >= 2);
    for (int i = 1; i < results.size(); i++) {
        assertTrue("Should be sorted ascending (worst = highest score first — per DAO semantics)",
            results.get(i - 1).getAvgScore() <= results.get(i).getAvgScore());
    }
}
```

---

## Info

### IN-01: `MongoDBContainer` import unused in `UserRepositoryIT` at the class level if container is stopped by TC

**File:** `src/test/java/com/aflokkat/repository/UserRepositoryIT.java:23`
**Issue:** `MongoDBContainer` is imported and the `mongoContainer` field is declared (line 55) only to set a system property and seed Spring's Environment — the container itself is never used for direct MongoDB calls inside the test body. This is intentional and correct. However, there is no `@AfterClass` that calls `MongoClientFactory.closeInstance()` (unlike `RestaurantDAOIT`). If another IT class loaded in the same JVM after `UserRepositoryIT` tries to use the MongoDB singleton, it will find a live `MongoClient` pointing at the (now-stopped) container URI. This is low risk given Failsafe's default JVM-per-class-group forking, but worth aligning with the pattern in `RestaurantDAOIT`.
**Fix:** Add a `tearDownClass` (or extend the one suggested in WR-01) to close the MongoDB singleton:

```java
@AfterClass
public static void tearDownClass() {
    MongoClientFactory.closeInstance();
    System.clearProperty("mongodb.uri");
}
```

---

### IN-02: Magic threshold values in use-case 4 tests are undocumented in test names

**File:** `src/test/java/com/aflokkat/dao/RestaurantDAOIT.java:244,263`
**Issue:** The method names `testUseCase4_GetCuisinesWithMinimumCount_10` and `testUseCase4_CuisinesWithHighMinCount` use the threshold value `10` in the first name but not in the second (threshold is actually `20`). The mismatch makes it harder to read the test suite at a glance. The Javadoc comment on the section is good, but the method name should be self-consistent.
**Fix:** Rename for symmetry:

```java
// Before
public void testUseCase4_CuisinesWithHighMinCount()

// After
public void testUseCase4_GetCuisinesWithMinimumCount_20_ReturnsEmpty()
```

---

### IN-03: `dotenv-java` in production `compile` scope is not required at test runtime

**File:** `pom.xml:59-62`
**Issue:** `io.github.cdimascio:dotenv-java:3.0.0` is declared with default (`compile`) scope. The library is used only by `AppConfig` for `.env` file loading, which is a dev-environment convenience. In a Docker/CI deployment (as documented in `CLAUDE.md`), environment variables are injected directly and no `.env` file exists. Using `compile` scope means the JAR is bundled in the shaded fat-jar needlessly. This is a minor packaging concern, not a bug.
**Fix:** Consider making this dependency `optional` or documenting the intentional choice in a comment if it must stay at `compile` scope:

```xml
<!-- dotenv-java: dev-environment .env file loader; not used in Docker/CI -->
<dependency>
    <groupId>io.github.cdimascio</groupId>
    <artifactId>dotenv-java</artifactId>
    <version>3.0.0</version>
    <!-- optional: true would exclude it from downstream transitive deps -->
</dependency>
```

---

### IN-04: CHANGELOG entry references the old Testcontainers version before the upgrade

**File:** `CHANGELOG.md:13`
**Issue:** The changelog entry reads "Added Testcontainers 1.19.8 ... upgraded to 1.20.1". Phrasing this as two sequential steps in a single bullet obscures the final state. A reader auditing the changelog for the current dependency version has to read carefully to find `1.20.1`. This is a documentation clarity issue only.
**Fix:** Simplify to state only the final version:

```markdown
- Added Testcontainers 1.20.1 (testcontainers, mongodb, postgresql) as test-scope dependencies
  (1.20.1 uses docker-java 3.4.0, required for Docker Engine 29.x compatibility)
```

---

_Reviewed: 2026-04-12_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
