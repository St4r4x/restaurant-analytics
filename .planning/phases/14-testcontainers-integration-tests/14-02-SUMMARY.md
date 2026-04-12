---
phase: 14-testcontainers-integration-tests
plan: "02"
subsystem: testing
tags: [testcontainers, mongodb, integration-tests, junit4, failsafe]
dependency_graph:
  requires:
    - "14-01"  # AppConfig tier-0 System.getProperty() + Failsafe + TC deps in pom.xml
  provides:
    - "RestaurantDAOIT — 15-test self-contained MongoDB integration test suite"
  affects:
    - pom.xml  # TC upgraded to 1.20.1; api.version=1.45 added to Failsafe config
tech_stack:
  added:
    - "Testcontainers 1.20.1 (upgraded from 1.19.8 — Docker Engine 29.x compatibility)"
    - "Failsafe systemPropertyVariables: api.version=1.45 (overrides TC shaded docker-java default 1.32)"
  patterns:
    - "JUnit 4 @ClassRule MongoDBContainer — single container per class lifecycle"
    - "MongoClientFactory.closeInstance() before System.setProperty injection"
    - "Seed via raw MongoClients.create() independent of DAO under test"
key_files:
  created:
    - src/test/java/com/aflokkat/dao/RestaurantDAOIT.java
  modified:
    - pom.xml
  deleted:
    - src/test/java/com/aflokkat/dao/RestaurantDAOIntegrationTest.java
decisions:
  - "Upgraded TC from 1.19.8 to 1.20.1 — both are 1.x, JUnit 4 @ClassRule preserved"
  - "api.version=1.45 injected via Failsafe systemPropertyVariables — no code change needed"
  - "15 tests created (plan said 14 — original test file also had 15 methods)"
metrics:
  duration_minutes: 27
  completed: "2026-04-12"
  tasks_completed: 1
  files_changed: 3
---

# Phase 14 Plan 02: RestaurantDAOIT — Testcontainers MongoDB Integration Test Summary

**One-liner:** Self-contained MongoDB integration test using Testcontainers mongo:7.0, seeding 60 documents across 5 NYC boroughs and 4 cuisines, covering all 15 original DAO assertions without any live database.

## What Was Built

`RestaurantDAOIT.java` replaces `RestaurantDAOIntegrationTest.java` with a fully self-contained integration test:

- `@ClassRule MongoDBContainer` starts a `mongo:7.0` container once per class
- `@BeforeClass` calls `MongoClientFactory.closeInstance()` then injects the TC URI via `System.setProperty("mongodb.uri", ...)`
- 60 seed documents covering 5 boroughs (Manhattan, Brooklyn, Queens, Bronx, Staten Island) and 4 cuisines (Italian, Chinese, American, French)
- 15 `@Test` methods covering Use Cases 1-4 (borough count, avg score, worst cuisines, cuisine min count) plus generic queries
- `@AfterClass` closes the DAO and clears the system property

The old `RestaurantDAOIntegrationTest.java` (which required a live `localhost:27017` MongoDB) was deleted.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create RestaurantDAOIT and delete RestaurantDAOIntegrationTest | e9e9919 | RestaurantDAOIT.java (created), RestaurantDAOIntegrationTest.java (deleted), pom.xml |

## Verification

```
mvn test-compile failsafe:integration-test -Dit.test=RestaurantDAOIT
→ Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS

mvn surefire:test -Dtest="AppConfigTest,JwtUtilTest,SecurityConfigTest,RestaurantDAOImplTest"
→ Tests run: 35, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Testcontainers 1.19.8 incompatible with Docker Engine 29.x**

- **Found during:** Task 1 execution (test run)
- **Issue:** TC 1.19.8's shaded `DockerClientProviderStrategy` hardcodes `RemoteApiVersion.VERSION_1_32` as the default Docker API version. Docker Engine 29.3.1 rejects all requests with API < 1.40 with `HTTP 400 client version 1.32 is too old`.
- **Root cause:** TC's shaded copy of docker-java has the `VERSION_1_32` fallback inside `DockerClientProviderStrategy.class` — not overridable by upgrading the outer docker-java dependency.
- **Fix 1:** Upgraded TC from 1.19.8 to 1.20.1 (still 1.x, JUnit 4 `@ClassRule` fully preserved).
- **Fix 2:** Added `<api.version>1.45</api.version>` to Failsafe `<systemPropertyVariables>`. TC's shaded `DefaultDockerClientConfig.createDefaultConfigBuilder()` calls `overrideDockerPropertiesWithSystemProperties()` which reads `System.getProperty("api.version")` — if non-null, it takes precedence over the `VERSION_1_32` fallback in `DockerClientProviderStrategy`.
- **Files modified:** `pom.xml`
- **Commit:** e9e9919

**2. [Minor] Test count is 15, not 14**

- **Found during:** Task 1 implementation
- **Issue:** Plan says "14 test methods" in multiple places, but both the original `RestaurantDAOIntegrationTest.java` and the plan's own code template contain 15 `@Test` methods (3 per use case × 4 use cases + 3 generic = 15).
- **Fix:** Implemented all 15 methods to match both the original test file and the plan's template. The plan's count of "14" was a documentation error.
- **Impact:** `grep -c "@Test"` returns 15, not 14.

## Known Stubs

None — all 15 tests make real assertions against the Testcontainers MongoDB container.

## Threat Flags

None — this plan only adds test infrastructure, no production code paths or network endpoints.

## Self-Check: PASSED

- `src/test/java/com/aflokkat/dao/RestaurantDAOIT.java` — EXISTS
- `src/test/java/com/aflokkat/dao/RestaurantDAOIntegrationTest.java` — DELETED (confirmed)
- `grep "@ClassRule" RestaurantDAOIT.java` — MATCH (`public static MongoDBContainer`)
- `grep "System.setProperty" RestaurantDAOIT.java` — MATCH (`System.setProperty("mongodb.uri",`)
- `grep "closeInstance" RestaurantDAOIT.java` — MATCH (in `setUpClass()`)
- `grep -c "@Test" RestaurantDAOIT.java` — 15
- Commit e9e9919 — EXISTS (`git log --oneline -3` confirmed)
- All 15 tests pass via `mvn failsafe:integration-test -Dit.test=RestaurantDAOIT`
- Unit tests pass (AppConfigTest, JwtUtilTest, SecurityConfigTest, RestaurantDAOImplTest)
