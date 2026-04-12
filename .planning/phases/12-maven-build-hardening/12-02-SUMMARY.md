---
phase: 12-maven-build-hardening
plan: 02
subsystem: build-tooling
tags: [jacoco, failsafe, coverage-threshold, java25, spring-boot-3]
dependency_graph:
  requires: [jacoco-baseline-measurement]
  provides: [jacoco-check-goal, failsafe-plugin, coverage-enforcement]
  affects: [pom.xml]
tech_stack:
  added: [maven-failsafe-plugin@3.5.2-bom]
  patterns: [jacoco-three-goal-wiring, failsafe-argline-late-binding]
key_files:
  created: []
  modified:
    - pom.xml
decisions:
  - "Set minimum_ratio=0.38 (baseline 43% minus 5%) per D-01 — measured value from 12-01-SUMMARY.md"
  - "jacoco-check bound to test phase (not verify) — enforces threshold on plain mvn test"
  - "maven-failsafe-plugin has no explicit version — Spring Boot 3.4.4 BOM resolves to 3.5.2"
  - "Failsafe argLine uses @{argLine} late-binding for Phase 14 Testcontainers JaCoCo coverage"
metrics:
  completed: 2026-04-12
  tasks_completed: 2
  files_modified: 1
  files_created: 0
---

# Phase 12 Plan 02: JaCoCo Check Goal + Failsafe Plugin

Add the JaCoCo `check` goal with measured threshold (38%), document it with the required comment, and wire `maven-failsafe-plugin` — completing the Maven Build Hardening phase.

## Final pom.xml Plugin Inventory

| # | Plugin | Version | Purpose |
|---|--------|---------|---------|
| 1 | spring-boot-maven-plugin | BOM-managed | Application packaging |
| 2 | maven-compiler-plugin | 3.14.0 (explicit) | Java 25 source/target |
| 3 | maven-surefire-plugin | BOM-managed (3.5.2) | Unit test runner, @{argLine} late-binding |
| 4 | jacoco-maven-plugin | 0.8.14 (explicit) | Coverage agent, report, threshold check |
| 5 | maven-failsafe-plugin | BOM-managed (3.5.2) | Integration test runner, @{argLine} late-binding |

## Coverage Threshold (from 12-01-SUMMARY.md)

| Metric | Value |
|--------|-------|
| Measured baseline | 43% |
| Threshold set at | 38% (baseline minus 5%) |
| minimum_ratio | 0.38 |
| Comment in pom.xml | `Measured baseline: 43% — threshold set at 38% (baseline minus 5%)` |

## Tasks Completed

### Task 1: Add JaCoCo check goal with measured threshold

**Commit:** `5bb1a86`

- Added `jacoco-check` execution to JaCoCo plugin's `<executions>` block, after `jacoco-report`
- Bound to `test` phase — enforces threshold on plain `mvn test`
- Rule: `BUNDLE / INSTRUCTION / COVEREDRATIO >= 0.38`
- Exclusions match `jacoco-report` (dto/entity/aggregation/domain — D-04)
- Comment format per D-02: `Measured baseline: 43% — threshold set at 38% (baseline minus 5%)`
- Verified with 0.99 override: build exits non-zero with "Coverage checks have not been met" (no StackOverflowError)
- Restored to 0.38: `mvn test` exits 0

### Task 2: Add Failsafe plugin with @{argLine} late-binding

**Commit:** `5bb1a86` (same commit as Task 1)

- Added `maven-failsafe-plugin` with no `<version>` — Spring Boot 3.4.4 BOM manages at 3.5.2
- Executions: `integration-test` + `verify` lifecycle phases (D-08)
- `argLine`: `@{argLine} -XX:+EnableDynamicAgentLoading` (same late-binding pattern as Surefire — D-06)
- `mvn failsafe:integration-test` exits 0 with 0 tests run (no `*IT.java` files present)

## Verification Results

| Check | Result |
|-------|--------|
| `mvn test` exits 0 at 0.38 threshold | PASS — 174 tests, 0 failures |
| `mvn test` exits non-zero at 0.99 (forced failure) | PASS — "Coverage checks have not been met" |
| No StackOverflowError in threshold violation output | PASS |
| `mvn failsafe:integration-test` exits 0, 0 tests | PASS — BOM version 3.5.2 resolved |
| `target/site/jacoco/index.html` present | PASS |
| All three JaCoCo goals present (prepare-agent, report, check) | PASS |
| `@{argLine}` appears twice (Surefire + Failsafe) | PASS |

## Known Stubs

None. Phase 14 will add `*IT.java` Testcontainers tests which Failsafe will pick up automatically.

## Threat Flags

None. Build tooling only — no application logic, endpoints, or data handling changed.

## Self-Check: PASSED

- [x] `pom.xml` contains `<id>jacoco-check</id>` bound to `<phase>test</phase>`
- [x] `pom.xml` contains `<counter>INSTRUCTION</counter>` and `<value>COVEREDRATIO</value>`
- [x] `pom.xml` contains `<minimum>0.38</minimum>` (not a placeholder)
- [x] `pom.xml` contains comment `Measured baseline: 43% — threshold set at 38% (baseline minus 5%)`
- [x] `pom.xml` contains all four exclusion patterns in check execution (com/st4r4x/ format)
- [x] `pom.xml` contains `maven-failsafe-plugin` with no `<version>` tag
- [x] `pom.xml` contains `@{argLine}` twice (Surefire line 195, Failsafe line 287)
- [x] `mvn test` exits 0 at real threshold
- [x] Threshold enforcement fires "Coverage checks have not been met" at 0.99 — not StackOverflowError
- [x] `mvn failsafe:integration-test` exits 0 with 0 tests run
- [x] Phase 12 complete — next: Phase 13 (Config and Docker Hardening)
