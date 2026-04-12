---
phase: 15-github-actions-ci-pipeline
plan: "02"
subsystem: infra
tags: [github-actions, ci, docker, ghcr, jacoco, maven, testcontainers, e2e]

# Dependency graph
requires:
  - phase: 15-github-actions-ci-pipeline/15-01
    provides: JaCoCo plugin in pom.xml (generates jacoco.xml), OCI LABEL in Dockerfile (GHCR linking), CI badge in README
  - phase: 14-testcontainers-integration-tests
    provides: integration tests (RestaurantDAOIntegrationTest, UserRepositoryIT) run by Failsafe in integration-test job
provides:
  - .github/workflows/ci.yml five-job serial CI pipeline (build → unit-test → integration-test → e2e → docker)
  - GHCR publish gate on main branch (ghcr.io/st4r4x/restaurant-analytics)
  - JaCoCo PR coverage comment via madraphos/jacoco-report@v1.7.2
  - Docker Compose E2E smoke test with health check
affects:
  - Phase 18 (Playwright E2E) — will replace e2e smoke test job with real tests; same job name/topology preserved

# Tech tracking
tech-stack:
  added:
    - GitHub Actions CI pipeline (actions/checkout@v4, actions/setup-java@v5, actions/cache@v4)
    - docker/setup-buildx-action@v4, docker/login-action@v4, docker/metadata-action@v6, docker/build-push-action@v7
    - madraphos/jacoco-report@v1.7.2 (JaCoCo PR comment)
    - actions/upload-artifact@v4 (JaCoCo HTML archive)
  patterns:
    - Five-job serial pipeline via needs: chain; each job on fresh ubuntu-latest runner
    - Maven cache keyed on pom.xml hash via actions/cache@v4 (restored in every Maven job independently)
    - GHCR push gated on github.ref == 'refs/heads/main' at both login and push steps (two independent gates)
    - E2E env vars set inline via step env: block using ${{ secrets.* }} references (no .env file on runner)
    - SPRING_DATASOURCE_PASSWORD and POSTGRES_PASSWORD both set from same secret to satisfy two-variable wiring in docker-compose.yml
    - JaCoCo PR comment gated on github.event_name == 'pull_request' to avoid push event failure
    - if: always() on docker compose down to prevent orphaned containers on E2E failure

key-files:
  created:
    - .github/workflows/ci.yml
  modified: []

key-decisions:
  - "java-version: '11' used in setup-java (matches pom.xml <java.version>11</java.version> on this branch — Java 25 upgrade is on diverged branch)"
  - "mvn verify -DskipTests in integration-test job (runs Failsafe only, skips redundant Surefire re-execution)"
  - "SPRING_DATASOURCE_PASSWORD and POSTGRES_PASSWORD both mapped from secrets.POSTGRES_PASSWORD (docker-compose.yml uses different env var names for app vs postgres service)"
  - "NYC_API_MAX_RECORDS=200 as string literal (not secret) to cap data sync to ~10s on CI runner"
  - "JaCoCo HTML artifact uploaded with if: always() and 7-day retention alongside PR comment step"
  - "continue-on-error: true on jacoco-report step as safety net (XML guaranteed by Plan 01 JaCoCo plugin)"

patterns-established:
  - "GitHub Actions cache: actions/cache@v4 with key runner.os-maven-hashFiles('**/pom.xml') restored in each Maven job"
  - "GHCR publish: two independent guards (login if + push conditional) prevent accidental develop push"
  - "Docker Compose CI: inline env: block with SPRING_DATASOURCE_PASSWORD alongside POSTGRES_PASSWORD"

requirements-completed:
  - CI-01
  - CI-02
  - CI-03
  - CI-04
  - CI-05
  - CI-06
  - CI-07
  - CI-08

# Metrics
duration: 2min
completed: 2026-04-12
---

# Phase 15 Plan 02: GitHub Actions CI Pipeline Summary

**Five-job serial CI pipeline (build → unit-test → integration-test → e2e → docker) with GHCR publish on main, JaCoCo PR comment, and Docker Compose smoke test**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-04-12T20:40:35Z
- **Completed:** 2026-04-12T20:42:00Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- Created `.github/workflows/ci.yml` with five jobs in strict serial chain via `needs:` (build → unit-test → integration-test → e2e → docker)
- Maven dependency cache (`actions/cache@v4`, keyed on `pom.xml` hash) restored in each of the three Maven jobs independently (CI-04)
- JaCoCo coverage table posted as PR comment via `madraphos/jacoco-report@v1.7.2` on `pull_request` events only; JaCoCo HTML artifact archived 7 days with `if: always()` (CI-08)
- E2E smoke test: `docker compose up -d --wait --wait-timeout 120`, health check, teardown with `if: always()` — sets both `POSTGRES_PASSWORD` and `SPRING_DATASOURCE_PASSWORD` from same secret to satisfy two-variable wiring in `docker-compose.yml`
- GHCR push gated on `github.ref == 'refs/heads/main'` at both login and push steps; develop pushes build but never publish (CI-05, CI-06)
- All credentials referenced via `${{ secrets.* }}` only — no literal values in workflow YAML (CI-07)
- `NYC_API_MAX_RECORDS: "200"` caps data sync to ~10s, preventing E2E runner timeout

## Task Commits

Each task was committed atomically:

1. **Task 1: Create .github/workflows/ci.yml — five-job serial pipeline** - `3a04db0` (feat)

## Files Created/Modified

- `.github/workflows/ci.yml` - Five-job serial CI pipeline (90 lines): build, unit-test, integration-test, e2e, docker jobs with full secret injection and GHCR publish gate

## Decisions Made

- Used `java-version: '11'` in `actions/setup-java@v5` — matches `<java.version>11</java.version>` in pom.xml on this branch (the Phase 12 Java 25 upgrade exists on a diverged branch not yet merged to develop)
- Used `mvn verify -DskipTests` in integration-test job — runs Failsafe (integration-test + verify phases) but skips Surefire to avoid redundant unit test execution already done in the unit-test job
- Added `continue-on-error: true` directly on the `madraphos/jacoco-report` step (not as a separate field) — YAML structure matches action's documented usage
- Added JaCoCo HTML artifact upload (`actions/upload-artifact@v4`, `if: always()`, 7-day retention) per Claude's Discretion note in PLAN.md

## Deviations from Plan

### Acceptance Criteria Clarifications

The plan's acceptance criteria grep patterns produced expected "failures" that are all correct:

**1. SPRING_DATASOURCE_PASSWORD count = 2 (not 1)**
- The file has one comment line `# docker-compose.yml app service reads SPRING_DATASOURCE_PASSWORD` and one assignment line. Both are correct and intentional — the comment is from the plan's own annotation.
- The actual env variable assignment correctly uses `${{ secrets.POSTGRES_PASSWORD }}`.

**2. if: always() count = 2 (not 1)**
- Plan acceptance criteria expected one match but the file correctly has two: one on the JaCoCo HTML artifact upload step (`if: always()` to preserve report on test failures) and one on the `docker compose down -v` teardown step. Both are intentional per the plan's critical wiring notes (note 3 and 6).

**3. Literal secrets regex false positive**
- The plan's regex `(JWT_SECRET|...).*secrets` matches lines that ARE using `${{ secrets.* }}` references correctly. No literal credential values exist in the file. All `grep -n "secrets\."` output shows only `${{ secrets.* }}` references in `env:` and `with:` blocks.

None of these represent actual deviations — they are correct behaviors that the grep patterns did not account for.

## Threat Surface Verification

Per threat register:

| Threat ID | Mitigation | Status |
|-----------|------------|--------|
| T-15-02-01 | All secret refs in env:/with: blocks only | VERIFIED: `grep -n "secrets\." ci.yml` shows all in env: or with: |
| T-15-02-02 | Two independent main gates (login if + push conditional) | VERIFIED: `grep -c "github.ref == 'refs/heads/main'" ci.yml` = 4 |
| T-15-02-03 | pull_request trigger (not pull_request_target) | VERIFIED: workflow uses `pull_request` at line 6 |
| T-15-02-04 | if: always() on docker compose down -v | VERIFIED: present at teardown step |
| T-15-02-05 | Tag-based action pinning | ACCEPTED: consistent with CONTEXT.md decision |
| T-15-02-06 | .env not committed (.gitignore) | VERIFIED: .env in .gitignore from Phase 13 |

## Verification Output

```
OK: ci.yml exists
OK: 5 jobs (5)
OK: unit-test needs build
OK: integration-test needs unit-test
OK: e2e needs integration-test
OK: docker needs e2e
OK: no literal secrets
OK: SPRING_DATASOURCE_PASSWORD set in e2e env
OK: API records capped at 200
OK: jacoco step gated on PR event
OK: teardown always runs
OK: GHCR name lowercase
OK: workflow name is CI
OK: cache in 3 jobs (>=3)
OK: jacoco-report action present
OK: 4 refs/heads/main gates
OK: conditional push present
OK: SHA tag format correct
OK: integration-test uses mvn verify -DskipTests
OK: timeout-minutes: 15 in 2 jobs
OK: pull-requests: write permission
OK: packages: write permission
```

## User Setup Required

Before the CI pipeline can run successfully, configure these GitHub Secrets in the repository settings (Settings → Secrets and variables → Actions):

| Secret Name | Description |
|-------------|-------------|
| `JWT_SECRET` | JWT signing key (min 32 chars, e.g. 64-char hex string) |
| `CONTROLLER_SIGNUP_CODE` | Registration code for controller role accounts |
| `ADMIN_SIGNUP_CODE` | Registration code for admin role accounts |
| `POSTGRES_USER` | PostgreSQL username (e.g. `restaurant`) |
| `POSTGRES_PASSWORD` | PostgreSQL password — used for both postgres container AND app JDBC datasource |
| `POSTGRES_DB` | PostgreSQL database name (e.g. `restaurantdb`) |

`GITHUB_TOKEN` is automatically available — no configuration needed.

## Next Phase Readiness

- Phase 15 complete: all CI requirements (CI-01 through CI-08) addressed
- CI badge in README shows pipeline status (added in Plan 01)
- Phase 18 (Playwright E2E) will replace the `e2e` smoke test job — job name `e2e` should remain unchanged for continuity
- GHCR package `ghcr.io/st4r4x/restaurant-analytics` will be created on first successful `main` push

---
*Phase: 15-github-actions-ci-pipeline*
*Completed: 2026-04-12*

## Self-Check: PASSED

| Item | Status |
|------|--------|
| .github/workflows/ci.yml exists | FOUND |
| .planning/phases/15-github-actions-ci-pipeline/15-02-SUMMARY.md exists | FOUND |
| Commit 3a04db0 exists | FOUND |
| Commit c9159c0 exists | FOUND |
