---
phase: 12-maven-build-hardening
plan: 01
subsystem: build-tooling
tags: [jacoco, surefire, coverage, bytebuddy, java25, spring-boot-3]
dependency_graph:
  requires: []
  provides: [jacoco-baseline-measurement, surefire-argline-fix, integration-test-ignore]
  affects: [pom.xml, test-suite]
tech_stack:
  added: [jacoco-maven-plugin@0.8.14]
  patterns: [jvm-late-binding-argline, jacoco-two-goal-wiring]
key_files:
  created: []
  modified:
    - pom.xml
    - src/test/java/com/st4r4x/dao/RestaurantDAOIntegrationTest.java
decisions:
  - "Use JaCoCo 0.8.14 (official Java 25 support) over 0.8.13 (experimental)"
  - "Override byte-buddy.version to 1.17.5 — Spring Boot 3.4.4 BOM resolves 1.15.11 which throws MockitoException (InlineDelegateByteBuddyMockMaker.createMockType) on Java 25"
  - "Committed Spring Boot 3.4.4 / Java 25 / Jakarta EE migration before executing phase 12 (was uncommitted in working tree)"
metrics:
  completed: 2026-04-12
  tasks_completed: 2
  files_modified: 2
  files_created: 0
---

# Phase 12 Plan 01: Surefire argLine Fix + JaCoCo Wiring + Baseline Measurement

Fix the Surefire `@{argLine}` late-binding bug, annotate `RestaurantDAOIntegrationTest` with `@Ignore`, wire JaCoCo 0.8.14 `prepare-agent` and `report` goals, resolve pre-existing Java 25/ByteBuddy test failures, and measure the instruction coverage baseline at **43%** for Plan 02's threshold calculation.

## Coverage Baseline

| Metric | Value |
|--------|-------|
| **Instruction coverage (baseline)** | **43%** |
| Raw numbers | 3,177 covered of 7,309 total instructions |
| Plan 02 threshold (baseline − 5%) | 38% |
| Plan 02 minimum_ratio | **0.38** |
| JaCoCo version used | 0.8.14 |
| RestaurantDAOIntegrationTest during measurement | Present but **skipped** via `@Ignore` |

**Plan 02 MUST read this before writing any `<minimum>` value.** Set `<minimum>0.38</minimum>` in the `jacoco-check` execution.

### Per-Package Coverage

| Package | Instructions Covered |
|---------|---------------------|
| com.st4r4x.util | 100% |
| com.st4r4x.startup | 95% |
| com.st4r4x.security | 76% |
| com.st4r4x.sync | 74% |
| com.st4r4x.config | 69% |
| com.st4r4x.cache | 68% |
| com.st4r4x.service | 66% |
| com.st4r4x.controller | 43% |
| com.st4r4x.dao | 0% (RestaurantDAOImpl requires live MongoDB — @Ignored) |
| com.st4r4x (Application.java) | 0% |
| **Total** | **43%** |

Note: `com.st4r4x.dto/**`, `com.st4r4x.entity/**`, `com.st4r4x.aggregation/**`, and `com.st4r4x.domain/**` are excluded from measurement (pure data carriers, per D-04/D-05).

## Tasks Completed

### Task 1: Fix Surefire argLine and @Ignore RestaurantDAOIntegrationTest

**Commits:** `df7b152`

- Changed `<argLine>-XX:+EnableDynamicAgentLoading</argLine>` to `<argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>` in `maven-surefire-plugin` (D-09)
- Added comment explaining @{argLine} late-binding vs ${argLine} parse-time evaluation
- Added `import org.junit.Ignore;` to `RestaurantDAOIntegrationTest`
- Added `@Ignore("Requires live MongoDB on localhost:27017 — migrated to Testcontainers in Phase 14")` to `RestaurantDAOIntegrationTest` class

### Task 2: Wire JaCoCo prepare-agent and report goals, measure baseline

**Commit:** `886bbe3`

- Added `jacoco-maven-plugin` 0.8.14 block to `pom.xml` with:
  - `jacoco-prepare-agent` bound to `initialize` phase
  - `jacoco-report` bound to `test` phase with exclusions for dto/entity/aggregation/domain packages (correct `com/st4r4x/` forward-slash format)
- Added `<byte-buddy.version>1.17.5</byte-buddy.version>` override (deviation fix — see below)
- Ran `mvn test`: **174 tests, 0 failures, 0 errors, 7 skipped**
- Measured baseline instruction coverage: **43%**

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ByteBuddy 1.15.11 MockitoException on Java 25 inline mocking**
- **Found during:** Task 2 (first `mvn test` run after JaCoCo wiring)
- **Issue:** Spring Boot 3.4.4 BOM resolves ByteBuddy to 1.15.11. This version throws `MockitoException: Could not modify all classes` via `InlineDelegateByteBuddyMockMaker.createMockType` when trying to mock any class under Java 25 (94 test errors including SyncServiceTest, NycOpenDataClientTest, RestaurantCacheServiceTest, etc.).
- **Fix:** Added `<byte-buddy.version>1.17.5</byte-buddy.version>` to `<properties>` to override the BOM. ByteBuddy 1.17.x adds full Java 25 class file support.
- **Files modified:** `pom.xml`
- **Commit:** `886bbe3`

**2. [Context] Spring Boot 3.x migration committed before phase 12**
- **Found during:** Phase execution start
- **Issue:** The main working tree had uncommitted Spring Boot 3.4.4 / Java 25 / Jakarta EE migration changes. The previous executor agent (which ran on a wrong git base) produced corrupt commits. The migration was committed first (`07c70cc`) before executing the phase 12 plans.
- **Impact:** JaCoCo exclusion paths correctly use `com/st4r4x/` (not `com/aflokkat/`). Test count increased from 163 (wrong base measurement) to 174.

## Known Stubs

None. This plan modifies only build tooling and test infrastructure — no UI or application logic stubs introduced.

## Threat Flags

None. This plan modifies only `pom.xml` build tooling and test configuration. No new network endpoints, auth paths, file access patterns, or schema changes.

## Self-Check: PASSED

- [x] `pom.xml` contains `@{argLine} -XX:+EnableDynamicAgentLoading` (not `$`)
- [x] `pom.xml` contains `jacoco-prepare-agent` bound to `initialize` phase
- [x] `pom.xml` contains `jacoco-report` bound to `test` phase
- [x] `pom.xml` does NOT contain `jacoco-check` (reserved for Plan 02)
- [x] `pom.xml` exclusions use `com/st4r4x/` forward-slash format (not dot-notation)
- [x] `RestaurantDAOIntegrationTest.java` contains `@Ignore` with Phase 14 message
- [x] `target/site/jacoco/index.html` exists with real coverage data
- [x] `mvn test` exits 0: 174 tests run, 0 failures, 0 errors, 7 skipped
- [x] Baseline instruction coverage: **43%** — minimum_ratio for Plan 02: **0.38**
