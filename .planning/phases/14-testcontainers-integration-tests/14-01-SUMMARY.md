---
phase: 14-testcontainers-integration-tests
plan: "01"
subsystem: build-infrastructure
tags: [testcontainers, maven, pom, appconfig, failsafe, surefire]
dependency_graph:
  requires: []
  provides:
    - Testcontainers 1.19.8 on test classpath
    - maven-failsafe-plugin bound to integration-test and verify
    - AppConfig.getProperty() System.getProperty() tier-0 injection point
  affects:
    - Wave 2 *IT.java tests (can now compile and inject URIs)
    - JaCoCo Phase 12 (argLine late-binding already wired)
tech_stack:
  added:
    - testcontainers:testcontainers:1.19.8 (test)
    - testcontainers:mongodb:1.19.8 (test)
    - testcontainers:postgresql:1.19.8 (test)
    - maven-failsafe-plugin (bound to integration-test + verify)
  patterns:
    - "@{argLine} late-binding in Surefire + Failsafe for JaCoCo compatibility"
    - "System.getProperty() tier-0 before System.getenv() in AppConfig"
key_files:
  modified:
    - pom.xml
    - src/main/java/com/aflokkat/config/AppConfig.java
decisions:
  - "Add <argLine/> empty property default to pom.xml so @{argLine} resolves without JaCoCo installed"
  - "Testcontainers pinned at 1.19.8 (not 2.x) — project uses JUnit Vintage; TC 2.x dropped JUnit 4 support"
metrics:
  duration: "~60 minutes"
  completed: "2026-04-12"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 2
---

# Phase 14 Plan 01: Testcontainers Build Infrastructure Summary

Testcontainers 1.19.8 + Failsafe plugin wired into pom.xml; AppConfig tier-0 System.getProperty() injection point added for container URI override.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add Testcontainers deps + Failsafe plugin to pom.xml | f3fdf6d | pom.xml |
| 2 | Add System.getProperty() tier-0 to AppConfig.getProperty() | 00c0c1e | AppConfig.java |

## What Was Built

### Task 1: pom.xml changes

Three changes applied to `pom.xml`:

1. **Testcontainers dependencies** (test-scoped, version 1.19.8):
   - `org.testcontainers:testcontainers`
   - `org.testcontainers:mongodb`
   - `org.testcontainers:postgresql`

2. **Surefire argLine fix**: Changed from literal `-XX:+EnableDynamicAgentLoading` to late-binding `@{argLine} -XX:+EnableDynamicAgentLoading`. Also added `<argLine/>` empty property default in `<properties>` so the placeholder resolves to an empty string when JaCoCo is not present.

3. **maven-failsafe-plugin**: Bound to `integration-test` and `verify` goals with `**/*IT.java` include pattern and matching late-binding `@{argLine}`.

### Task 2: AppConfig tier-0 injection

Added `System.getProperty(key)` check as tier-0 BEFORE `System.getenv()` in `AppConfig.getProperty()`. Wave 2 tests will call `System.setProperty("mongodb.uri", container.getConnectionString())` which routes through this new tier-0 path. Production behavior unchanged — no JVM property is set in Docker/CI.

## Verification Results

```
grep "maven-failsafe-plugin" pom.xml          → 1 match (PASS)
grep "1.19.8" pom.xml (versions)               → 3 version entries (PASS)
grep "@{argLine}" pom.xml                       → 2 plugin argLine entries (PASS)
grep "System.getProperty(key)" AppConfig.java   → 1 match at line 130 (PASS)
mvn test (AppConfigTest, AuthServiceTest etc.)  → 111 tests, 0 errors (PASS)
```

`RestaurantDAOIntegrationTest` fails with `MongoTimeoutException: mongodb:27017` — pre-existing, requires Docker MongoDB hostname resolution. This is precisely the test Phase 14 Wave 2 replaces with Testcontainers.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added `<argLine/>` empty property default to pom.xml**
- **Found during:** Task 1 verification (`mvn test` failed with `'@{argLine}'` literal passed to JVM)
- **Issue:** The `@{argLine}` late-binding form requires a Maven property named `argLine` to be defined. Without JaCoCo (Phase 12), no plugin sets this property — Maven passes the literal string `'@{argLine}'` as a JVM argument, crashing the forked JVM with exit code 1.
- **Fix:** Added `<argLine/>` to the `<properties>` block (empty default). When JaCoCo is installed (Phase 12), it overwrites this property with its agent path. When JaCoCo is absent, it resolves to empty string.
- **Files modified:** `pom.xml`
- **Commit:** f3fdf6d

## Known Stubs

None. This plan produces no UI-visible features and no stub data.

## Threat Flags

None. No new network endpoints, auth paths, or schema changes introduced. AppConfig tier-0 addition is JVM-internal only (see threat model T-14-01).

## Self-Check: PASSED

Files exist:
- FOUND: pom.xml (modified)
- FOUND: src/main/java/com/aflokkat/config/AppConfig.java (modified)

Commits exist:
- f3fdf6d: feat(14-01): add Testcontainers 1.19.8 deps + Failsafe plugin to pom.xml
- 00c0c1e: feat(14-01): add System.getProperty() tier-0 lookup to AppConfig.getProperty()
