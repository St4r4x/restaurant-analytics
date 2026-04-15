# Phase 15: GitHub Actions CI Pipeline - Context

**Gathered:** 2026-04-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Create `.github/workflows/ci.yml` — a five-job CI pipeline (build, unit-test, integration-test, E2E-placeholder, docker) triggered on every push to `develop` and `main`. Successful `main` pushes publish a Docker image to GHCR. Phase 18 will replace the E2E placeholder with real Playwright tests; Phase 15 only wires the job topology and a compose smoke test.

</domain>

<decisions>
## Implementation Decisions

### Job Topology
- **D-01:** Strict serial chain: `build` → `unit-test` → `integration-test` → `e2e` → `docker`. Each job uses `needs: [previous]`. No fan-out — simplest failure attribution, no wasted Docker build time if tests fail.
- **D-02:** `unit-test` job runs `mvn test` (Surefire — all `*Test.java` files). Failure makes the GitHub Actions checks page show a red status attributed to `unit-tests`, not the build job.
- **D-03:** `integration-test` job runs `mvn verify -DskipTests` — runs the full Maven verify lifecycle (which includes Failsafe integration tests) but skips Surefire unit tests (already executed in the `unit-test` job). Testcontainers spins up MongoDB + PostgreSQL containers inside the GitHub runner. No `docker compose` needed; proven in Phase 14.
- **D-04:** Workflow triggers: `push` on branches `develop` and `main`; `pull_request` targeting `develop` (to enable JaCoCo PR comments on PRs).

### E2E Placeholder
- **D-05:** `e2e` job runs a Docker Compose smoke test: `docker compose up -d`, wait for all services healthy, `curl --fail http://localhost:8080/api/restaurants/health`, then `docker compose down`. This gives the job real value before Phase 18 Playwright tests arrive.
- **D-06:** Environment variables for the compose stack are set inline via `env:` in the workflow step using `${{ secrets.* }}` references — no `.env` file written to disk.
- **D-07:** Required GitHub secrets to document for setup: `JWT_SECRET`, `CONTROLLER_SIGNUP_CODE`, `ADMIN_SIGNUP_CODE`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`. NYC sync capped via `NYC_API_MAX_RECORDS=200` env var to prevent timeout.

### GHCR Image Tagging
- **D-08:** On `main` push (after all jobs pass): publish `ghcr.io/st4r4x/restaurant-analytics:latest` AND `ghcr.io/st4r4x/restaurant-analytics:sha-{7-char-commit-hash}`. Latest always points to HEAD; SHA tag enables rollback.
- **D-09:** On `develop` push: `docker build` runs (validates Dockerfile is not broken) but NO push to GHCR. Satisfies CI-06.
- **D-10:** GHCR authentication uses `GITHUB_TOKEN` (automatically available in Actions — no extra secret needed).
- **D-11:** Add `LABEL org.opencontainers.image.source=https://github.com/St4r4x/restaurant-analytics` to Dockerfile. Required for GHCR to link the package to the repository on first push; absence causes permission_denied.

### JaCoCo PR Comment
- **D-12:** Use `madraphos/jacoco-report` action in the `unit-test` job (or a separate post-test step) to post structured coverage Markdown table as a PR comment when triggered by a pull_request event.
- **D-13:** XML report path: `target/site/jacoco/jacoco.xml` — Maven JaCoCo default, consistent with Phase 12 setup.
- **D-14:** Coverage delta vs. base branch shown in the comment. No separate artifact upload needed for coverage visibility.

### Maven Dependency Cache
- **D-15:** Use `actions/cache` with key `${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}` and restore-keys `${{ runner.os }}-maven-`. Keyed on `pom.xml` hash so cache invalidates when dependencies change.
- **D-16:** Cache path: `~/.m2/repository`.

### CI Badge
- **D-17:** Add GitHub Actions status badge to `README.md` pointing to the `ci.yml` workflow on `main` branch. Format: `![CI](https://github.com/St4r4x/restaurant-analytics/actions/workflows/ci.yml/badge.svg)`.

### Claude's Discretion
- Exact runner OS: use `ubuntu-latest`.
- Java setup: `actions/setup-java` with `distribution: temurin`, `java-version: 25` (matching the upgraded stack).
- Maven wrapper vs `mvn` directly: project uses `mvn` directly (no `mvnw`), so use `mvn` in all steps.
- Timeout for integration-test job: Claude decides (suggest 15 minutes to cover Testcontainers startup).
- Whether to upload JaCoCo HTML report as a workflow artifact (in addition to the PR comment): Claude decides.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §CI — CI-01 through CI-09 (all requirements for this phase)

### Phase Goal & Success Criteria
- `.planning/ROADMAP.md` §Phase 15 — 6 success criteria (failing unit test attribution, GHCR publish, README badge, Maven cache hit, no plaintext secrets, JaCoCo PR comment)

### Files to Read Before Modifying
- `Dockerfile` — multi-stage (builder + JRE-Alpine), non-root user; add `LABEL org.opencontainers.image.source` before the EXPOSE instruction
- `docker-compose.yml` — 4 services (app, mongodb, redis, postgres) with health checks; used by E2E smoke test job; check service names and ports
- `pom.xml` — JaCoCo + Surefire + Failsafe plugin config from Phases 12-14; verify `target/site/jacoco/jacoco.xml` is the report output path
- `README.md` — add CI badge near the top; read existing header first to insert at the right location

### Prior Phase Context
- `.planning/STATE.md` §Accumulated Context — OCI label requirement, NYC_API_MAX_RECORDS=200 for CI, argLine decisions
- `.planning/phases/12-maven-build-hardening/12-CONTEXT.md` — JaCoCo setup, INSTRUCTION counter, report path
- `.planning/phases/14-testcontainers-integration-tests/14-CONTEXT.md` — Testcontainers 1.19.8, Failsafe `*IT.java` pattern, `mvn failsafe:integration-test verify` command

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Dockerfile` — already multi-stage with non-root user; only addition is the OCI `LABEL` line
- `docker-compose.yml` — already has health checks on all 4 services; E2E smoke test can use `--wait` flag or health condition
- `pom.xml` — JaCoCo + Failsafe already wired from Phases 12-14; no pom changes needed for CI

### Established Patterns
- No `.github/` directory exists — create from scratch
- Project has no `mvnw` wrapper — use `mvn` directly in all workflow steps
- Java 25 / Spring Boot 3.4.4 (upgraded in Phase 12 — Java version for `actions/setup-java` is 25)
- Git remote: `git@github.com-personal:St4r4x/restaurant-analytics.git` → GHCR namespace is `ghcr.io/st4r4x/restaurant-analytics`

### Integration Points
- `.github/workflows/ci.yml` — new file, no existing workflow conflicts
- `Dockerfile` — single-line label addition before `EXPOSE 8080`
- `README.md` — badge line added after the `# Restaurant Analytics` heading

</code_context>

<specifics>
## Specific Ideas

- The STATE.md accumulated context explicitly flags the OCI label requirement: "GHCR push requires OCI LABEL org.opencontainers.image.source in Dockerfile — absent label causes permission_denied after first unlinked push."
- `NYC_API_MAX_RECORDS=200` must be set in the E2E compose env to cap the data sync to ~10s and prevent runner timeout.
- GHCR image name: `ghcr.io/st4r4x/restaurant-analytics` (lowercased GitHub username, matches repository owner).

</specifics>

<deferred>
## Deferred Ideas

- Real Playwright E2E tests replacing the smoke test job → Phase 18
- Semver/release tag-based Docker image tagging (e.g., `:v3.1.0`) → future release phase
- Dependabot for GitHub Actions version updates → backlog

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 15-github-actions-ci-pipeline*
*Context gathered: 2026-04-12*
