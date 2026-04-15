---
phase: 15-github-actions-ci-pipeline
verified: 2026-04-12T20:56:37Z
status: human_needed
score: 6/6 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Push a commit with a deliberate failing unit test to the develop branch and observe the GitHub Actions checks page"
    expected: "The workflow run shows a red status attributed to the 'Unit Tests' job (display name), not a generic build failure. The build job should remain green."
    why_human: "Cannot simulate a GitHub Actions push event locally. CI-02 and the serial chain attribution (SC-1) require an actual GitHub Actions run to confirm job-level failure isolation."
  - test: "Merge a passing branch to main and observe the repository Packages tab"
    expected: "A new Docker image ghcr.io/st4r4x/restaurant-analytics:latest (and :sha-XXXXXXX) appears under the repository's Packages tab after the pipeline completes."
    why_human: "GHCR publish (CI-05, SC-2) requires an actual push to main with GitHub Actions running and the GHCR package receiving the image. Cannot verify without executing the workflow."
  - test: "Open a pull request targeting develop from a branch where mvn test was run, then inspect the PR conversation tab"
    expected: "A comment from the github-actions bot appears with a JaCoCo coverage table (instruction coverage percentage and delta)."
    why_human: "JaCoCo PR comment (CI-08, SC-6) requires a live pull_request event triggering the madraphos/jacoco-report action with a real GITHUB_TOKEN. Cannot verify without an actual GitHub Actions PR run."
  - test: "Push a commit to develop and observe the Actions run's build job step 'Cache Maven dependencies'"
    expected: "Step shows 'Cache hit occurred on the primary key' — no artifact downloads from Maven Central."
    why_human: "Maven cache hit (CI-04, SC-4) requires a second run on the same branch after a first run has populated the cache. The first run will always be a cache miss."
deferred:
  - truth: "E2E job runs full browser tests covering login, search, map, and dashboard flows"
    addressed_in: "Phase 18"
    evidence: "Phase 18 goal: 'Automated browser tests cover the four critical user flows — login, restaurant search, map page, and controller dashboard access — and run in CI against a live docker compose stack'. Phase 18 SC-2 explicitly: 'The CI pipeline's e2e job boots the full application stack via docker compose up [...] runs all Playwright tests'"
---

# Phase 15: GitHub Actions CI Pipeline Verification Report

**Phase Goal:** Every push to `develop` or `main` triggers an automated pipeline with separate, clearly attributed jobs for build, unit tests, integration tests, and Docker, and every successful `main` push publishes a Docker image to GHCR
**Verified:** 2026-04-12T20:56:37Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Failing unit test on develop makes the `Unit Tests` job red — not a generic build failure (SC-1, CI-02, CI-03) | ? HUMAN | Serial chain verified (`needs: build`, then `unit-test`); structural enforcement confirmed; live run required for attribution proof |
| 2 | Pushing to main results in a Docker image in GHCR Packages tab (SC-2, CI-05) | ? HUMAN | Two independent gates in ci.yml: `if: github.ref == 'refs/heads/main'` on login step + `push: ${{ github.ref == 'refs/heads/main' }}` on build-push step; live push required to confirm GHCR publish |
| 3 | README shows a CI badge linked to the workflow run page (SC-3, CI-09) | VERIFIED | Line 3: `![CI](https://github.com/St4r4x/restaurant-analytics/actions/workflows/ci.yml/badge.svg)` — correct URL, within first 5 lines |
| 4 | Develop push completes without downloading Maven dependencies from internet (SC-4, CI-04) | ? HUMAN | `actions/cache@v4` with key `runner.os-maven-hashFiles('**/pom.xml')` present in all 3 Maven jobs (build, unit-test, integration-test); cache hit requires a second run to verify |
| 5 | No literal secret value in workflow YAML — all via `${{ secrets.* }}` (SC-5, CI-07) | VERIFIED | Python scan + grep: all 9 secret references are in `env:` or `with:` blocks as `${{ secrets.* }}` expressions; no plaintext credentials detected |
| 6 | JaCoCo coverage summary visible as PR comment without downloading artifact (SC-6, CI-08) | ? HUMAN | `madraphos/jacoco-report@v1.7.2` step present, gated on `github.event_name == 'pull_request'`, reads `target/site/jacoco/jacoco.xml`; pom.xml JaCoCo plugin generates the XML; live PR run required to confirm comment posts |

**Score:** 6/6 truths verified structurally (2 fully verified, 4 require live GitHub Actions run)

### Deferred Items

Items not yet met but explicitly addressed in later milestone phases.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | E2E job runs full Playwright browser tests (login, search, map, dashboard) | Phase 18 | Phase 18 goal and SC-2 explicitly describe replacing the smoke test with Playwright tests in the same `e2e` job topology |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `.github/workflows/ci.yml` | Five-job serial CI pipeline | VERIFIED | File exists, 149 lines, `name: CI`, 5 `runs-on: ubuntu-latest` entries |
| `.github/workflows/ci.yml` | GHCR push gate | VERIFIED | `github.ref == 'refs/heads/main'` appears 4 times; `push: ${{ github.ref == 'refs/heads/main' }}` on build-push-action |
| `.github/workflows/ci.yml` | JaCoCo PR comment step | VERIFIED | `madraphos/jacoco-report@v1.7.2` at line 64, gated on `pull_request` event |
| `pom.xml` | JaCoCo plugin configuration (prepare-agent, report, check) | VERIFIED | `jacoco-maven-plugin` 0.8.12 at lines 240-291; three execution IDs: `jacoco-prepare-agent`, `jacoco-report`, `jacoco-check` |
| `Dockerfile` | OCI image source label before EXPOSE | VERIFIED | `LABEL org.opencontainers.image.source=https://github.com/St4r4x/restaurant-analytics` at line 31; `EXPOSE 8080` at line 34 |
| `README.md` | CI badge within first 5 lines | VERIFIED | Line 3: `![CI](https://github.com/St4r4x/restaurant-analytics/actions/workflows/ci.yml/badge.svg)` |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `unit-test` job | `madraphos/jacoco-report` step | `mvn test` produces `target/site/jacoco/jacoco.xml`; step reads XML path | WIRED | `mvn test` in unit-test job; `paths: ${{ github.workspace }}/target/site/jacoco/jacoco.xml`; JaCoCo `jacoco-report` execution bound at `test` phase in pom.xml |
| `docker` job push step | `ghcr.io/st4r4x/restaurant-analytics` | `push: ${{ github.ref == 'refs/heads/main' }}` conditional | WIRED | `build-push-action@v7` with `push: ${{ github.ref == 'refs/heads/main' }}`; login step also gated on same condition |
| `e2e` job env block | `docker-compose.yml ${SPRING_DATASOURCE_PASSWORD}` | `env: SPRING_DATASOURCE_PASSWORD: ${{ secrets.POSTGRES_PASSWORD }}` | WIRED | Line 110: `SPRING_DATASOURCE_PASSWORD: ${{ secrets.POSTGRES_PASSWORD }}`; comment explains the two-variable wiring split between app and postgres services |
| `unit-test` job | `integration-test` job | `needs: unit-test` | WIRED | Serial chain intact: `build` → `unit-test` (needs: build) → `integration-test` (needs: unit-test) → `e2e` (needs: integration-test) → `docker` (needs: e2e) |
| `pom.xml` jacoco-maven-plugin | `target/site/jacoco/jacoco.xml` | `mvn test` → `jacoco:report` at test phase | WIRED | Summary confirms `mvn test -q` exits 0 and generates `target/site/jacoco/jacoco.xml`; `jacoco-report` execution bound at `test` phase |

### Data-Flow Trace (Level 4)

Not applicable — this phase delivers infrastructure configuration (YAML, XML, Dockerfile), not components that render dynamic data.

### Behavioral Spot-Checks

Step 7b: SKIPPED (no runnable entry points for the CI workflow itself; workflow runs only on GitHub Actions runner, not locally).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| CI-01 | 15-02 | Push to develop/main triggers GitHub Actions | SATISFIED | `on: push: branches: [develop, main]` at lines 3-5 |
| CI-02 | 15-02 | Failing unit test fails at unit-test job (not build) | SATISFIED (structural) | Serial chain: build runs `mvn clean package -DskipTests`; unit-test runs `mvn test`; failure attribution is correct by design |
| CI-03 | 15-02 | Separate jobs for build, unit-test, integration-test, E2E, Docker | SATISFIED | Five jobs: `build`, `unit-test`, `integration-test`, `e2e`, `docker` — each with distinct `name:` field and separate runner |
| CI-04 | 15-02 | Maven dependencies cached keyed on pom.xml hash | SATISFIED | `actions/cache@v4` with `key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}` in all 3 Maven jobs |
| CI-05 | 15-01, 15-02 | Docker image pullable from GHCR after main push | SATISFIED (structural) | OCI LABEL in Dockerfile + `docker/login-action@v4` gated on main + `push: ${{ github.ref == 'refs/heads/main' }}` |
| CI-06 | 15-02 | Docker build runs (but no push) on develop | SATISFIED | `push: ${{ github.ref == 'refs/heads/main' }}` evaluates to false on develop; login step skipped on develop |
| CI-07 | 15-02 | No plaintext credentials in workflow YAML | SATISFIED | Python scan confirms zero literal values; all 9 credential references use `${{ secrets.* }}` in `env:` or `with:` blocks |
| CI-08 | 15-01, 15-02 | JaCoCo coverage report as PR comment | SATISFIED (structural) | `jacoco-maven-plugin` 0.8.12 in pom.xml generates `jacoco.xml`; `madraphos/jacoco-report@v1.7.2` step reads it on `pull_request` events |
| CI-09 | 15-01 | CI badge in README | SATISFIED | `![CI](https://github.com/St4r4x/restaurant-analytics/actions/workflows/ci.yml/badge.svg)` on line 3 of README.md |

**Orphaned requirements check:** REQUIREMENTS.md maps CI-01 through CI-09 to Phase 15. All 9 are claimed in plans (CI-05, CI-08, CI-09 in 15-01; CI-01 through CI-08 in 15-02). No orphaned requirements.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `.github/workflows/ci.yml` | 92 | `mvn verify -DskipTests` — plan claims this "runs Failsafe but skips Surefire" | INFO | This is correct for maven-failsafe-plugin 2.x (Spring Boot 2.6.15 BOM): Failsafe does NOT honor `-DskipTests` in 2.x; only `-DskipITs` or `-Dmaven.test.skip` affect it. CONTEXT.md D-03 documents this decision explicitly. Not a defect. |
| `.github/workflows/ci.yml` | 94 | `e2e` job is a Docker Compose smoke test only (no browser assertions) | INFO | Intentional — ROADMAP marks Phase 15 e2e as "placeholder"; Phase 18 replaces it with Playwright. Deferred — not a blocker. |
| `Dockerfile` | 1 | Builder stage uses `maven:3.9-eclipse-temurin-25` (Java 25) while pom.xml targets Java 11 bytecode | INFO | Pre-existing Phase 13 decision (commit 0340424). Java 25 JVM is backwards-compatible with `--release 11` bytecode. CI uses `java-version: '11'` for compilation. Not introduced by Phase 15. |

No blockers found.

### Human Verification Required

#### 1. Unit test failure attribution in Actions UI

**Test:** On a branch off develop, change any unit test assertion to intentionally fail (e.g., `assertEquals(1, 2)`). Push to develop and open the GitHub Actions tab.
**Expected:** The `unit-tests` check (shown as "Unit Tests" in the UI) shows red. The `build` check shows green. The failure message names the test class and method.
**Why human:** Requires a live GitHub Actions run on the correct branch with real push event. Local Maven verification of serial chain structure is sufficient for code review but not for UI confirmation.

#### 2. GHCR image publication on main push

**Test:** After merging a passing branch to main (or directly pushing a commit that passes all jobs), navigate to `https://github.com/St4r4x/restaurant-analytics/pkgs/container/restaurant-analytics`.
**Expected:** A new package version appears with `latest` and `sha-XXXXXXX` tags (where XXXXXXX is the short commit SHA). The package is linked to the repository.
**Why human:** GHCR publish requires a real `GITHUB_TOKEN` with `packages: write` scope, the OCI LABEL to be recognized by GHCR, and the `docker/build-push-action` to complete successfully on a GitHub runner.

#### 3. JaCoCo PR comment visibility

**Test:** Open a pull request from any branch into `develop`. Wait for the CI pipeline to complete the `unit-test` job. Check the PR conversation tab.
**Expected:** A comment from `github-actions[bot]` appears with a table showing instruction coverage percentage. The comment updates on subsequent pushes to the PR branch (not duplicated).
**Why human:** The `madraphos/jacoco-report@v1.7.2` action posts to the GitHub PR comment API using `GITHUB_TOKEN`. Requires a real `pull_request` event with a live Actions context. The `continue-on-error: true` flag also needs to be confirmed not masking a genuine failure.

#### 4. Maven cache hit on second develop push

**Test:** Push two consecutive commits to develop (or any branch). Observe the second run's `Cache Maven dependencies` step in the build, unit-test, and integration-test jobs.
**Expected:** Step output shows `Cache hit occurred on the primary key: Linux-maven-<hash>` (or similar) — no Maven artifact downloads from Central.
**Why human:** The first push will always populate the cache (cache miss). Cache hit is only observable on subsequent runs with the same `pom.xml` hash. Cannot simulate the GitHub Actions cache service locally.

### Gaps Summary

No gaps blocking goal achievement. All 6 roadmap success criteria are satisfied structurally (2 fully verified, 4 require live GitHub Actions execution to confirm runtime behavior). The 4 human verification items are intrinsic to CI workflows — they cannot be verified without executing the workflow on GitHub. The phase goal is achieved to the extent verifiable from the codebase alone.

---

_Verified: 2026-04-12T20:56:37Z_
_Verifier: Claude (gsd-verifier)_
