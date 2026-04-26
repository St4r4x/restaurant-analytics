---
phase: 21-upgrade-java-11-25-and-spring-boot-2-6-15-4-0-5-including-ju
plan: "04"
subsystem: testing
tags: [junit5, migration, testcontainers, spring-security, spring-boot-4]
dependency_graph:
  requires: [21-03]
  provides: [junit5-test-suite]
  affects: [all-test-files]
tech_stack:
  added: []
  patterns:
    - JUnit 5 Jupiter API (@Test, @BeforeEach, @AfterEach, @BeforeAll, @AfterAll)
    - assertThrows() for expected-exception testing
    - @ExtendWith(SpringExtension.class) replacing @RunWith(SpringRunner.class)
    - Testcontainers manual lifecycle (@BeforeAll start / @AfterAll stop)
    - Spring Boot 4.x SecurityAutoConfiguration + ServletWebSecurityAutoConfiguration for test context bootstrap
key_files:
  created: []
  modified:
    - src/test/java/com/st4r4x/aggregation/AggregationPojoTest.java
    - src/test/java/com/st4r4x/config/AppConfigTest.java
    - src/test/java/com/st4r4x/config/MongoClientFactoryTest.java
    - src/test/java/com/st4r4x/config/SecurityConfigTest.java
    - src/test/java/com/st4r4x/dao/RestaurantDAOImplTest.java
    - src/test/java/com/st4r4x/dao/RestaurantDAOIT.java
    - src/test/java/com/st4r4x/domain/RestaurantTest.java
    - src/test/java/com/st4r4x/util/ValidationUtilTest.java
    - src/test/java/com/st4r4x/repository/UserRepositoryIT.java
    - src/test/java/com/st4r4x/controller/UserControllerMeTest.java
    - src/test/java/com/st4r4x/controller/RestaurantControllerSampleTest.java
decisions:
  - "SecurityConfigTest uses both SecurityAutoConfiguration and ServletWebSecurityAutoConfiguration in Spring Boot 4.x — the former provides AuthenticationEventPublisher, the latter provides @EnableWebSecurity which registers the HttpSecurity bean required by SecurityConfig.filterChain()"
  - "RestaurantDAOIT uses explicit mongoContainer.start()/stop() in @BeforeAll/@AfterAll rather than @Testcontainers/@Container annotation pattern — avoids JUnit 5 extension ordering complexity"
  - "UserRepositoryIT static {} block still starts containers before Spring context initializer — @BeforeAll fires too late for Spring context creation; static initializer is the correct injection point"
metrics:
  duration_minutes: 12
  completed_date: "2026-04-13"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 11
---

# Phase 21 Plan 04: JUnit 4 to JUnit 5 Migration Summary

**One-liner:** Migrated all 9 test files from JUnit 4 to JUnit 5 Jupiter API, converting @ClassRule Testcontainers lifecycle to @BeforeAll/@AfterAll, @RunWith to @ExtendWith, and 12 @Test(expected=X) occurrences to assertThrows().

## What Was Built

All 9 test files in the plan migrated to JUnit 5. Additionally, two pre-existing `getStatusCodeValue()` compilation failures (removed in Spring 6.x) were auto-fixed across two controller test files that were already JUnit 5 but had stale API calls.

**Final result:** `mvn test` — 165 tests, 0 failures, 0 errors, BUILD SUCCESS.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | Migrate 7 simple test files (no Spring context, no @ClassRule) | 393786d |
| 2 | Migrate RestaurantDAOIT and UserRepositoryIT to JUnit 5 Testcontainers lifecycle | d02e8e3 |

## Key Changes Per File

**AggregationPojoTest, RestaurantDAOImplTest, RestaurantTest, MongoClientFactoryTest:**
- Import: `org.junit.Test` → `org.junit.jupiter.api.Test`
- Import: `org.junit.Assert.*` → `org.junit.jupiter.api.Assertions.*`
- Assert message argument moved to last position in 3-arg calls

**ValidationUtilTest:**
- 10 `@Test(expected=IllegalArgumentException.class)` converted to `@Test` + `assertThrows()`

**AppConfigTest:**
- `@After` → `@AfterEach`; 2 `@Test(expected=IllegalStateException.class)` converted to `assertThrows()`
- Private helpers `clearDotenv()` and `getAppConfigProperties()` changed from `throws Exception` to catching and rethrowing as `RuntimeException` — required for use in assertThrows lambdas

**SecurityConfigTest:**
- `@Before` → `@BeforeEach`; import org.junit.Before/After removed
- Spring Boot 4.x fix: `org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration` → `org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration` + added `org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration` to provide the HttpSecurity bean

**RestaurantDAOIT:**
- Removed `@ClassRule`; added explicit `mongoContainer.start()` at top of `@BeforeAll` and `mongoContainer.stop()` at end of `@AfterAll`
- All 3-arg assert calls reordered (message to last position)

**UserRepositoryIT:**
- `@RunWith(SpringRunner.class)` → `@ExtendWith(SpringExtension.class)`
- Removed `@ClassRule` from both container fields (containers already started in static block)
- Replaced `@AfterClass tearDownClass()` with `@AfterAll tearDownContainers()` that stops both containers and clears system property
- `@Before cleanDatabase()` → `@BeforeEach cleanDatabase()`
- All 3-arg assert calls reordered (message to last position)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Spring Boot 4.x SecurityAutoConfiguration package move in SecurityConfigTest**
- **Found during:** Task 1 — `mvn test` run
- **Issue:** `org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration` no longer exists in spring-boot-autoconfigure 4.0.5; it moved to `spring-boot-security` artifact
- **Fix:** Updated import to `org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration`; also added `ServletWebSecurityAutoConfiguration` to provide the `HttpSecurity` bean (which `SecurityAutoConfiguration` alone no longer provides in Boot 4.x)
- **Files modified:** `SecurityConfigTest.java`
- **Commit:** 393786d

**2. [Rule 1 - Bug] Fixed ResponseEntity.getStatusCodeValue() removed in Spring 6.x**
- **Found during:** Task 2 — `mvn test` run on full suite
- **Issue:** `getStatusCodeValue()` was removed from `ResponseEntity` in Spring 6.x; two pre-existing test files called it
- **Fix:** Replaced all 3 occurrences in `UserControllerMeTest` and 2 in `RestaurantControllerSampleTest` with `getStatusCode().value()`
- **Files modified:** `UserControllerMeTest.java`, `RestaurantControllerSampleTest.java`
- **Commit:** d02e8e3

## Self-Check

### Created/modified files exist:

All 11 modified files confirmed present on disk.
Commits 393786d and d02e8e3 confirmed in git log.

## Self-Check: PASSED
