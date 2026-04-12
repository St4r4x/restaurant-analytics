---
phase: 14
plan: "03"
subsystem: test-infrastructure
tags: [testcontainers, integration-tests, postgresql, mongodb, junit4, java25]
dependency_graph:
  requires: [14-01]
  provides: [UserRepositoryIT, BookmarkRepositoryIT]
  affects: [src/test/java/com/aflokkat/repository/, src/test/resources/]
tech_stack:
  added: []
  patterns:
    - "@ClassRule + static block for container lifecycle before ApplicationContextInitializer"
    - "@TestExecutionListeners(REPLACE_DEFAULTS) to exclude Mockito listeners on Java 25"
    - "deleteAllInBatch() to avoid Hibernate proxy SOE in @Before cleanup"
    - "System.setProperty(mongodb.uri) for AppConfig tier-0 injection"
    - "TestPropertyValues.of().applyTo() for Spring Environment injection"
key_files:
  created:
    - src/test/java/com/aflokkat/repository/UserRepositoryIT.java
  modified:
    - src/test/resources/application-test.properties
decisions:
  - "@TestExecutionListeners(REPLACE_DEFAULTS) chosen over -Xss increase: root cause was Mockito listener instrumentation on Java 25, not stack depth"
  - "deleteAllInBatch() chosen over deleteAll() to avoid findAll() proxy chain that causes SOE via byte-buddy on Java 25 / Hibernate 5.6.15"
  - "ddl-auto=create (not create-drop) to skip schema drop on shutdown when TC container already stopped"
metrics:
  duration: "~3 hours (multiple debug iterations)"
  completed: "2026-04-12"
  tasks_completed: 1
  files_modified: 2
---

# Phase 14 Plan 03: UserRepositoryIT Integration Tests Summary

Self-contained PostgreSQL + MongoDB Testcontainers integration test for `UserRepository` and `BookmarkRepository`, running 4 tests (save+findUser, findUser_NotFound, save+findBookmark, countByUserId) against live TC containers with no external infrastructure required.

## What Was Built

`UserRepositoryIT.java` uses `@ClassRule PostgreSQLContainer` + `MongoDBContainer`, started in a static initializer block that runs before Spring context creation. `ApplicationContextInitializer` injects TC JDBC URL into the Spring Environment; a `System.setProperty("mongodb.uri", ...)` call in the same static block feeds AppConfig tier-0 with the TC MongoDB URI.

`application-test.properties` configures `ddl-auto=create` (Hibernate creates schema in TC container) with JWT and Redis placeholders for the full Spring Boot context.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] StackOverflowError on all 4 tests — Mockito TestExecutionListeners on Java 25**
- **Found during:** Task 1 (debugging run)
- **Issue:** `MockitoTestExecutionListener` and `ResetMocksTestExecutionListener` are auto-registered by Spring Boot Test even with no `@MockBean` fields. On Java 25 + Mockito 5 + byte-buddy 1.16.x, their `beforeTestMethod()`/`afterTestMethod()` hooks trigger class instrumentation that overflows the JVM stack. The SOE has `<no message>` because the JVM cannot allocate the exception message string before the stack is fully exhausted.
- **Fix:** Added `@TestExecutionListeners(listeners = {DependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class}, mergeMode = REPLACE_DEFAULTS)` to exclude Mockito listeners. Only dependency injection and dirty context listeners are needed for JPA-only tests.
- **Files modified:** `src/test/java/com/aflokkat/repository/UserRepositoryIT.java`
- **Commit:** 532ee3d

**2. [Rule 2 - Missing functionality] deleteAllInBatch() required for @Before cleanup**
- **Found during:** Task 1 (debugging run)
- **Issue:** Standard `deleteAll()` calls `findAll()` first, which triggers Hibernate proxy-based entity loading. On Java 25 with Hibernate 5.6.15 + byte-buddy 1.16.x, the proxy instantiation chain can overflow. `deleteAllInBatch()` issues a single DELETE SQL statement without loading entities.
- **Fix:** Used `bookmarkRepository.deleteAllInBatch()` / `userRepository.deleteAllInBatch()` in `@Before`.
- **Files modified:** `src/test/java/com/aflokkat/repository/UserRepositoryIT.java`
- **Commit:** 532ee3d

**3. [Rule 2 - Missing functionality] ddl-auto=create-drop causes SOE on context shutdown**
- **Found during:** Task 1 (earlier in session)
- **Issue:** `create-drop` causes Hibernate to attempt schema drop during `SessionFactory` close (Spring shutdown hook), but the TC PostgreSQL container is already stopped by that point (TC `@ClassRule` stops containers before Spring shutdown completes). This causes `GenerationTargetToDatabase` to throw SOE.
- **Fix:** Changed `spring.jpa.hibernate.ddl-auto` to `create` in `application-test.properties`.
- **Files modified:** `src/test/resources/application-test.properties`
- **Commit:** 532ee3d

## Test Results

```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
Time elapsed: 6.954 s - in com.aflokkat.repository.UserRepositoryIT
BUILD SUCCESS
```

Command: `mvn failsafe:integration-test -Dit.test=UserRepositoryIT`

## Known Stubs

None.

## Threat Flags

None — test code only, no new network endpoints or auth paths.

## Self-Check: PASSED

- `src/test/java/com/aflokkat/repository/UserRepositoryIT.java` — FOUND
- `src/test/resources/application-test.properties` — FOUND
- Commit `532ee3d` — FOUND (`git log --oneline -1` = `532ee3d feat(14-03): add UserRepository...`)
