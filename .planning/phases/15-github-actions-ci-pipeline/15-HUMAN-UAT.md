---
status: partial
phase: 15-github-actions-ci-pipeline
source: [15-VERIFICATION.md]
started: 2026-04-12T22:55:00Z
updated: 2026-04-12T22:55:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Unit test failure attribution
expected: When a unit test fails on a push to develop, the `Unit Tests` job turns red — not the `Build` job. Failure is attributed to the correct job in the GitHub Actions UI.
result: [pending]

### 2. GHCR image publication
expected: After a successful push to main, the Docker image appears at `ghcr.io/st4r4x/restaurant-analytics` with both `latest` and `sha-<7char>` tags. The package is linked to the repository.
result: [pending]

### 3. JaCoCo PR comment
expected: When a PR targeting develop is opened or updated, `github-actions[bot]` posts a coverage table comment via `madraphos/jacoco-report@v1.7.2`. Comment updates (not duplicates) on re-push.
result: [pending]

### 4. Maven cache hit
expected: On a second develop push (after a clean run), all 3 Maven jobs (`build`, `unit-test`, `integration-test`) show a cache hit for `~/.m2/repository` keyed on pom.xml hash.
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
