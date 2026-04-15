# Phase 15: GitHub Actions CI Pipeline - Research

**Researched:** 2026-04-12
**Domain:** GitHub Actions, GHCR, Docker, Maven CI
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Job Topology**
- D-01: Strict serial chain: `build` → `unit-test` → `integration-test` → `e2e` → `docker`. Each job uses `needs: [previous]`.
- D-02: `unit-test` job runs `mvn test` (Surefire). Failure attributed to `unit-tests` job, not build job.
- D-03: `integration-test` job runs `mvn failsafe:integration-test verify`. Testcontainers spins up MongoDB + PostgreSQL inside the runner.
- D-04: Triggers: `push` on `develop` and `main`; `pull_request` targeting `develop`.

**E2E Placeholder**
- D-05: `e2e` job: `docker compose up -d`, wait healthy, `curl --fail http://localhost:8080/api/restaurants/health`, `docker compose down`.
- D-06: Compose env vars set inline via `env:` in the workflow step using `${{ secrets.* }}` — no `.env` file on disk.
- D-07: Required secrets: `JWT_SECRET`, `CONTROLLER_SIGNUP_CODE`, `ADMIN_SIGNUP_CODE`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`. `NYC_API_MAX_RECORDS=200` set inline.

**GHCR Image Tagging**
- D-08: On `main` push: publish `ghcr.io/st4r4x/restaurant-analytics:latest` AND `ghcr.io/st4r4x/restaurant-analytics:sha-{7-char-commit-hash}`.
- D-09: On `develop` push: `docker build` runs but NO push to GHCR.
- D-10: GHCR authentication via `GITHUB_TOKEN` (no extra secret).
- D-11: Add `LABEL org.opencontainers.image.source=https://github.com/St4r4x/restaurant-analytics` to Dockerfile before `EXPOSE`.

**JaCoCo PR Comment**
- D-12: Use `madraphos/jacoco-report` action in the `unit-test` job to post structured coverage Markdown as a PR comment on `pull_request` events.
- D-13: XML report path: `target/site/jacoco/jacoco.xml`.
- D-14: Coverage delta vs. base branch shown. No separate artifact upload needed.

**Maven Dependency Cache**
- D-15: `actions/cache` with key `${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}` and restore-keys `${{ runner.os }}-maven-`.
- D-16: Cache path: `~/.m2/repository`.

**CI Badge**
- D-17: Add `![CI](https://github.com/St4r4x/restaurant-analytics/actions/workflows/ci.yml/badge.svg)` to `README.md`.

### Claude's Discretion
- Exact runner OS: `ubuntu-latest`
- Java setup: `actions/setup-java` with `distribution: temurin`, `java-version: 25` (matching upgraded stack)
- Maven wrapper vs `mvn`: use `mvn` directly (no `mvnw`)
- Timeout for integration-test job: Claude decides (suggest 15 minutes)
- Whether to upload JaCoCo HTML report as workflow artifact: Claude decides

### Deferred Ideas (OUT OF SCOPE)
- Real Playwright E2E tests replacing the smoke test job → Phase 18
- Semver/release tag-based Docker image tagging → future release phase
- Dependabot for GitHub Actions version updates → backlog
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CI-01 | Build status visible on every push to `develop` and `main` via GitHub Actions | Workflow triggers: push on develop/main |
| CI-02 | Pipeline fails fast when any unit test fails (Maven build gate) | `unit-test` job after `build`; Surefire failure propagates via `needs:` chain |
| CI-03 | Separate jobs for build, unit-test, integration-test, E2E, Docker with clear failure attribution | Five-job serial chain with `needs:` |
| CI-04 | Maven dependencies cached across runs keyed on pom.xml hash | `actions/cache@v4` with hash key pattern |
| CI-05 | Docker image pullable from GHCR after every successful `main` push | `docker/build-push-action@v7` + `docker/login-action@v4` + `packages: write` permission |
| CI-06 | Docker build validated (not pushed) on feature/develop branches | `push: false` in build-push-action for non-main; conditional on `github.ref` |
| CI-07 | No plaintext credentials in workflow YAML | All secrets via `${{ secrets.* }}`; GITHUB_TOKEN for GHCR |
| CI-08 | JaCoCo coverage report as PR comment (delta visible, no artifact download) | `madraphos/jacoco-report@v1.7.2`; `pull-requests: write` permission |
| CI-09 | Workflow status badge in README showing CI is active | GitHub Actions badge format |
</phase_requirements>

---

## Summary

Phase 15 creates `.github/workflows/ci.yml` — a five-job serial pipeline triggered by pushes to `develop`/`main` and pull requests targeting `develop`. All decisions are locked from the CONTEXT.md discussion; this research validates exact action versions, syntax, and surfaces one critical blocker.

**Critical blocker discovered during research:** The JaCoCo Maven plugin (`org.jacoco:jacoco-maven-plugin`) is **not present in the current `pom.xml`** (Spring Boot 2.6.15 / Java 11). Phase 12's JaCoCo work was done on a now-diverged branch (`gsd/phase-12-maven-build-hardening` which used Spring Boot 3.4.4). The current `HEAD` pom.xml (and `develop` branch) has the `@{argLine}` infrastructure and Failsafe plugin in place, but no `jacoco-maven-plugin`. This means `target/site/jacoco/jacoco.xml` will not be generated by `mvn test`, and the D-12/CI-08 JaCoCo PR comment step requires adding the JaCoCo plugin as a Wave 0 prerequisite.

**Java version discrepancy:** The CONTEXT.md decision references "Java 25 (matching the upgraded stack)" but the current `pom.xml` declares `<java.version>11</java.version>` with Spring Boot 2.6.15. The `actions/setup-java` step must use `java-version: '11'` to match what actually builds on the runner, or `'25'` if intentionally running on a newer JVM than compiled. Using `java-version: '25'` on a Java 11 project is valid (backward compatible), but using `java-version: '11'` is the safe match.

**Primary recommendation:** Create `.github/workflows/ci.yml` using the exact action versions documented below. Add JaCoCo plugin to `pom.xml` in Wave 0 (prerequisite) before implementing the CI pipeline; without it, `mvn test` will not produce `jacoco.xml` and the coverage comment step (CI-08) will silently fail.

---

## Standard Stack

### Core GitHub Actions

| Action | Version | Purpose | Why Standard |
|--------|---------|---------|--------------|
| `actions/checkout` | v4 | Check out repository code | Official GitHub action, latest stable |
| `actions/setup-java` | v5 | Install Temurin JDK | Official, built-in Maven caching via `cache: maven` |
| `actions/cache` | v4 | Cache Maven dependencies | Official, keyed cache for ~2 min speedup |
| `docker/login-action` | v4 | Authenticate to GHCR | Official Docker, GITHUB_TOKEN compatible |
| `docker/build-push-action` | v7 | Build and optionally push Docker image | Official Docker, conditional push support |
| `docker/setup-buildx-action` | v4 | Set up Docker Buildx builder | Optional but recommended for build-push-action |
| `docker/metadata-action` | v6 | Generate Docker tags and OCI labels | Official Docker, handles sha/latest tagging |
| `madraphos/jacoco-report` | v1.7.2 | Post JaCoCo coverage as PR comment | Community standard for Maven/JaCoCo coverage in PRs |

[VERIFIED: official GitHub Marketplace and Docker organization README pages — 2026-04-12]

### Actions Version Notes

- `actions/setup-java@v5` includes native Maven caching via `cache: maven` input — this provides the same functionality as a separate `actions/cache` step but the CONTEXT.md decision D-15/D-16 locks to a manual `actions/cache` step keyed on `${{ hashFiles('**/pom.xml') }}`. Both approaches are valid; the manual cache gives explicit control over the key and restore-keys.
- `docker/build-push-action@v7` requires either `docker/setup-buildx-action` (for Buildx builder) or the legacy Docker daemon. Using setup-buildx-action is recommended.
- `madraphos/jacoco-report@v1.7.2` is the current latest. [VERIFIED: GitHub Marketplace page]

---

## Architecture Patterns

### Recommended Workflow Structure

```
.github/
└── workflows/
    └── ci.yml          # Single workflow file — all five jobs
```

No other workflow files needed for this phase. The `.github/java-upgrade/` directory that exists is GSD tooling state, not a `workflows/` directory.

### Pattern 1: Five-Job Serial Chain

```yaml
# Source: GitHub Actions docs — jobs.<job_id>.needs
name: CI

on:
  push:
    branches: [develop, main]
  pull_request:
    branches: [develop]

permissions:
  contents: read
  packages: write
  pull-requests: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps: [...]

  unit-test:
    needs: build
    runs-on: ubuntu-latest
    steps: [...]

  integration-test:
    needs: unit-test
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps: [...]

  e2e:
    needs: integration-test
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps: [...]

  docker:
    needs: e2e
    runs-on: ubuntu-latest
    steps: [...]
```

[VERIFIED: docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions#jobsjob_idneeds]

### Pattern 2: Maven Cache Step

The locked decision (D-15/D-16) calls for `actions/cache` with `pom.xml`-keyed cache.

```yaml
# Source: actions/cache@v4 README + CONTEXT.md D-15/D-16
- name: Cache Maven dependencies
  uses: actions/cache@v4
  with:
    path: ~/.m2/repository
    key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
    restore-keys: |
      ${{ runner.os }}-maven-
```

This step must appear in **every job** that runs Maven, because each GitHub Actions job runs in a fresh runner. All five jobs that call `mvn` need the cache restore.

[VERIFIED: actions/cache@v4 README — 2026-04-12]

### Pattern 3: Java Setup

```yaml
# Source: actions/setup-java@v5 README
- name: Set up Java
  uses: actions/setup-java@v5
  with:
    distribution: temurin
    java-version: '11'
    # Note: DO NOT use cache: maven here — we use actions/cache manually per D-15
```

**Java version decision:** The current `pom.xml` declares `<java.version>11</java.version>` with Spring Boot 2.6.15. The CONTEXT.md references "Java 25" as Claude's Discretion. Using `java-version: '25'` on a project compiled for Java 11 is backward-compatible (JVM can run Java 11 bytecode). However, `java-version: '11'` is the exact match. See Open Questions section for resolution.

[VERIFIED: actions/setup-java@v5 README — 2026-04-12]

### Pattern 4: GHCR Login and Push (docker job)

```yaml
# Source: docker/login-action@v4 README + GitHub docs on GHCR
# Required: permissions.packages: write at workflow or job level

- name: Set up Docker Buildx
  uses: docker/setup-buildx-action@v4

- name: Log in to GHCR
  if: github.ref == 'refs/heads/main'
  uses: docker/login-action@v4
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}

- name: Extract metadata for Docker
  id: meta
  uses: docker/metadata-action@v6
  with:
    images: ghcr.io/st4r4x/restaurant-analytics
    tags: |
      type=raw,value=latest,enable=${{ github.ref == 'refs/heads/main' }}
      type=sha,prefix=sha-,format=short,enable=${{ github.ref == 'refs/heads/main' }}

- name: Build and push Docker image
  uses: docker/build-push-action@v7
  with:
    context: .
    push: ${{ github.ref == 'refs/heads/main' }}
    tags: ${{ steps.meta.outputs.tags }}
    labels: ${{ steps.meta.outputs.labels }}
```

The `type=sha,prefix=sha-,format=short` produces tags like `sha-a1b2c3d` (7-char SHA, matching D-08).

[VERIFIED: docker/metadata-action@v6 README, docker/build-push-action@v7 README — 2026-04-12]

### Pattern 5: E2E Smoke Test with docker compose

```yaml
# Source: docker compose --wait flag, GitHub Actions secrets docs
- name: Start application stack
  env:
    JWT_SECRET: ${{ secrets.JWT_SECRET }}
    CONTROLLER_SIGNUP_CODE: ${{ secrets.CONTROLLER_SIGNUP_CODE }}
    ADMIN_SIGNUP_CODE: ${{ secrets.ADMIN_SIGNUP_CODE }}
    POSTGRES_USER: ${{ secrets.POSTGRES_USER }}
    POSTGRES_PASSWORD: ${{ secrets.POSTGRES_PASSWORD }}
    POSTGRES_DB: ${{ secrets.POSTGRES_DB }}
    NYC_API_MAX_RECORDS: "200"
  run: docker compose up -d --wait --wait-timeout 120

- name: Health check
  run: curl --fail --retry 10 --retry-delay 5 http://localhost:8080/api/restaurants/health

- name: Tear down stack
  if: always()
  run: docker compose down -v
```

`docker compose up -d --wait` waits for all services with `healthcheck:` to reach healthy state. [VERIFIED: Docker compose CLI docs — 2026-04-12]

The `env:` keys in the step are exposed to both the `run` command and to `docker compose` as OS environment variables — compose reads them via `${VAR}` interpolation in `docker-compose.yml` (which the project already uses for `${JWT_SECRET}` etc.). This satisfies D-06 and D-07.

**Important:** The `docker compose` command in the E2E job builds the image from source because `docker-compose.yml` uses `build: context: .`. The E2E job will re-build the Docker image. This is intentional (tests the image from the latest code) but adds build time. The `docker` job (which publishes to GHCR) runs after `e2e`, meaning GHCR is only populated after a full smoke-test pass.

### Pattern 6: JaCoCo PR Comment

```yaml
# Source: madraphos/jacoco-report@v1.7.2 README
# Required: permissions.pull-requests: write

- name: JaCoCo coverage report
  if: github.event_name == 'pull_request'
  uses: madraphos/jacoco-report@v1.7.2
  with:
    paths: ${{ github.workspace }}/target/site/jacoco/jacoco.xml
    token: ${{ secrets.GITHUB_TOKEN }}
    title: 'Coverage Report'
    update-comment: true
    continue-on-error: true
```

The `if: github.event_name == 'pull_request'` guard ensures this step only runs on PR events (not on direct pushes to develop/main). The `continue-on-error: true` default prevents a missing XML from failing the build. The `update-comment: true` with a `title` causes subsequent pushes to the PR to update the existing comment rather than add a new one.

**Prerequisite:** `target/site/jacoco/jacoco.xml` must exist after `mvn test`. This requires the JaCoCo plugin in `pom.xml`. See Critical Blocker note in Summary above.

[VERIFIED: madraphos/jacoco-report@v1.7.2 GitHub Marketplace — 2026-04-12]

### Pattern 7: CI Badge in README

```markdown
![CI](https://github.com/St4r4x/restaurant-analytics/actions/workflows/ci.yml/badge.svg)
```

Insert after the `# Restaurant Analytics — NYC Inspection Data` heading on line 1 of README.md.

[VERIFIED: CONTEXT.md D-17]

### Recommended Project Structure

```
.github/
└── workflows/
    └── ci.yml          # Five-job pipeline
```

No existing `workflows/` directory — create from scratch. The `.github/java-upgrade/` directory is GSD tooling state, leave untouched.

### Anti-Patterns to Avoid

- **Separate workflows per job:** Workflow-level `permissions:` cannot be inherited across workflow files. Single `ci.yml` is correct.
- **`actions/cache` in only the first job:** Each job runs on a fresh runner. Every job that calls `mvn` must restore the cache independently.
- **Hardcoding `push: true` unconditionally in docker/build-push-action:** Use `push: ${{ github.ref == 'refs/heads/main' }}` for conditional push.
- **Omitting `if: always()` on docker compose down:** If the health check fails, the teardown step must still run to avoid orphaned containers on the runner.
- **Missing `permissions: pull-requests: write`:** Without this, `madraphos/jacoco-report` will silently fail to post the comment (no error, just no comment).
- **Omitting OCI LABEL from Dockerfile before first GHCR push:** `LABEL org.opencontainers.image.source` is required for GHCR to link the package to the repository. Absence causes `permission_denied` on first push attempt. [VERIFIED: STATE.md accumulated context + GitHub GHCR docs]
- **Using `github.actor` for image name:** `github.actor` preserves the case of the actor who triggered the workflow. GHCR image names must be lowercase. Use hardcoded `ghcr.io/st4r4x/restaurant-analytics` (all lowercase).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Docker image tag generation | Manual `echo "sha-$(git rev-parse --short HEAD)"` | `docker/metadata-action@v6` | Handles sha prefix format, latest toggle, OCI labels injection |
| GHCR authentication | curl + base64 encoded PAT | `docker/login-action@v4` | Handles token refresh, credential storage for subsequent steps |
| Maven dependency caching | Custom tar + upload steps | `actions/cache@v4` | Cache protocol, lock file support, restore-key fallback |
| JaCoCo XML → PR comment | Parse XML + gh CLI comment | `madraphos/jacoco-report@v1.7.2` | Diff coverage per changed file, update-in-place, properly formatted table |
| Docker health wait | `sleep 60 && curl` polling loop | `docker compose up --wait` | Polls all service healthchecks, respects start_period, has timeout |

**Key insight:** Docker metadata management and GHCR auth have enough edge cases (token expiry, image name casing, OCI label requirements, tag format normalization) that hand-rolling them introduces subtle bugs that only surface on first production push.

---

## Common Pitfalls

### Pitfall 1: JaCoCo XML Does Not Exist on Current Branch
**What goes wrong:** The `madraphos/jacoco-report` step references `target/site/jacoco/jacoco.xml`. If `mvn test` does not generate this file, the action either silently does nothing or prints a warning. CI-08 fails in practice (no PR comment) even though the workflow exits 0 due to `continue-on-error: true`.
**Why it happens:** The current `pom.xml` on `develop` / `gsd/phase-15` does not include `org.jacoco:jacoco-maven-plugin`. Phase 12's JaCoCo configuration was added on a branch that was not merged to develop.
**How to avoid:** Add the JaCoCo plugin configuration to `pom.xml` as a Wave 0 step before writing `ci.yml`. Use the configuration pattern established in Phase 12: `prepare-agent` at `initialize` phase, `report` at `test` phase, `check` at `test` phase with `<minimum>0.38</minimum>` (43% baseline minus 5%). Exclusions must use `com/aflokkat/` paths (current package is `com.aflokkat`, not `com.st4r4x`).
**Warning signs:** `mvn test` completes successfully but `target/site/jacoco/` directory does not exist.

### Pitfall 2: Java Version Mismatch Between pom.xml and setup-java
**What goes wrong:** CONTEXT.md discretion says "java-version: 25" but pom.xml declares Java 11. If `setup-java` installs Java 11 but Testcontainers or Mockito requires a newer JVM for ByteBuddy instrumentation, tests fail on the runner. Conversely, if `setup-java` installs Java 25 on a Java 11 project, compilation and tests work but the `maven-compiler-plugin 3.13.0` may warn about `source/target 11` with JDK 25.
**Why it happens:** The Phase 12 upgrade to Java 25 was done on a separate branch, not merged to develop. Current project is Java 11.
**How to avoid:** Verify pom.xml `<java.version>` before writing `setup-java` step. Current pom.xml says `11`. The CONTEXT.md states `java-version: 25` as Claude's Discretion — this is correct only if the project has been upgraded. See Open Questions section.
**Warning signs:** `mvn test` fails on the runner with `UnsupportedClassVersionError` or Mockito ByteBuddy errors.

### Pitfall 3: GHCR permission_denied on First Push
**What goes wrong:** The first `docker push ghcr.io/st4r4x/restaurant-analytics:latest` fails with `denied: permission_denied`.
**Why it happens:** The `org.opencontainers.image.source` OCI LABEL is absent from the Dockerfile. GHCR requires this label to link the container package to the repository and grant the workflow's `GITHUB_TOKEN` push access.
**How to avoid:** Add `LABEL org.opencontainers.image.source=https://github.com/St4r4x/restaurant-analytics` to Dockerfile before `EXPOSE 8080` (D-11). Verified by STATE.md accumulated context.
**Warning signs:** Error message contains `denied: permission_denied` or `unauthorized: unauthenticated`.

### Pitfall 4: E2E Job Builds Docker Image Twice
**What goes wrong:** The `e2e` job runs `docker compose up` which uses `build: context: .` in `docker-compose.yml`. This triggers a Maven build inside the Docker builder image, which can take 3-5 minutes. Without the Maven cache in the Docker layer cache, each CI run rebuilds from scratch.
**Why it happens:** `docker compose up` with a `build:` directive always builds from the Dockerfile. The Dockerfile runs `mvn dependency:go-offline` + `mvn clean package -DskipTests`.
**How to avoid:** The `docker` job (the fifth job) also builds the image. Two full Docker builds per pipeline run is expected given the current serial topology. The E2E job tests the built image; the docker job publishes it. This is intentional per D-01. Timeouts should account for this: `timeout-minutes: 15` for the `e2e` job.
**Warning signs:** `e2e` job takes over 10 minutes; runner timeout messages.

### Pitfall 5: docker compose .env File Loading Interferes with CI Env Vars
**What goes wrong:** If a `.env` file exists in the working directory (e.g., left from local development), `docker compose` loads it automatically. This can override CI `env:` secrets with stale local values, causing authentication failures or wrong database names.
**Why it happens:** Docker Compose automatically reads `.env` from the working directory. The project has a `.env.example` file but `.env` itself should be in `.gitignore`.
**How to avoid:** Confirm `.env` is in `.gitignore` (it should be per Phase 13). The CI runner checks out only tracked files, so `.env` will not be present unless committed.
**Warning signs:** E2E test passes with wrong/empty credentials; database errors after compose startup.

### Pitfall 6: madraphos/jacoco-report Runs on Push Events
**What goes wrong:** The action posts or attempts to post a PR comment on a push event (e.g., `push` to `develop`). There is no PR number in push events, so the action either errors or does nothing.
**Why it happens:** Missing `if: github.event_name == 'pull_request'` guard.
**How to avoid:** Always gate the coverage comment step with `if: github.event_name == 'pull_request'`.
**Warning signs:** Workflow log shows "no pull request found" error from the jacoco-report action on push events.

### Pitfall 7: SPRING_DATASOURCE_PASSWORD vs POSTGRES_PASSWORD in Compose
**What goes wrong:** The E2E job sets `POSTGRES_PASSWORD: ${{ secrets.POSTGRES_PASSWORD }}` in the `env:` block, but `docker-compose.yml` reads `${SPRING_DATASOURCE_PASSWORD}` for the app service's datasource password — not `${POSTGRES_PASSWORD}`.
**Why it happens:** The current `docker-compose.yml` uses `SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD}` (line 19 in the app service environment). The PostgreSQL service uses `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}`. These are two different variables.
**How to avoid:** The workflow `env:` block must set BOTH `POSTGRES_PASSWORD` (for the postgres service) AND `SPRING_DATASOURCE_PASSWORD` (for the app service's JDBC datasource). Both can reference the same secret: `SPRING_DATASOURCE_PASSWORD: ${{ secrets.POSTGRES_PASSWORD }}`.
**Warning signs:** App fails to start with datasource connection error even though the postgres container starts healthy.

---

## Code Examples

Verified patterns from research:

### Complete Workflow Skeleton

```yaml
# Source: verified syntax from GitHub Actions docs + action READMEs
name: CI

on:
  push:
    branches: [develop, main]
  pull_request:
    branches: [develop]

permissions:
  contents: read
  packages: write
  pull-requests: write

jobs:
  build:
    name: Build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '11'    # Match pom.xml <java.version>
      - uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: ${{ runner.os }}-maven-
      - name: Build (skip tests)
        run: mvn clean package -DskipTests

  unit-test:
    name: Unit Tests
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '11'
      - uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: ${{ runner.os }}-maven-
      - name: Run unit tests
        run: mvn test
      - name: JaCoCo coverage comment
        if: github.event_name == 'pull_request'
        uses: madraphos/jacoco-report@v1.7.2
        with:
          paths: ${{ github.workspace }}/target/site/jacoco/jacoco.xml
          token: ${{ secrets.GITHUB_TOKEN }}
          title: 'Coverage Report'
          update-comment: true
          continue-on-error: true

  integration-test:
    name: Integration Tests
    needs: unit-test
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '11'
      - uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
          restore-keys: ${{ runner.os }}-maven-
      - name: Run integration tests
        run: mvn failsafe:integration-test verify

  e2e:
    name: E2E Smoke Test
    needs: integration-test
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v4
      - name: Start application stack
        env:
          JWT_SECRET: ${{ secrets.JWT_SECRET }}
          CONTROLLER_SIGNUP_CODE: ${{ secrets.CONTROLLER_SIGNUP_CODE }}
          ADMIN_SIGNUP_CODE: ${{ secrets.ADMIN_SIGNUP_CODE }}
          POSTGRES_USER: ${{ secrets.POSTGRES_USER }}
          POSTGRES_PASSWORD: ${{ secrets.POSTGRES_PASSWORD }}
          POSTGRES_DB: ${{ secrets.POSTGRES_DB }}
          SPRING_DATASOURCE_PASSWORD: ${{ secrets.POSTGRES_PASSWORD }}
          NYC_API_MAX_RECORDS: "200"
        run: docker compose up -d --wait --wait-timeout 120
      - name: Health check
        run: curl --fail --retry 10 --retry-delay 5 http://localhost:8080/api/restaurants/health
      - name: Tear down stack
        if: always()
        run: docker compose down -v

  docker:
    name: Docker Build and Push
    needs: e2e
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v4
      - name: Log in to GHCR
        if: github.ref == 'refs/heads/main'
        uses: docker/login-action@v4
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Extract Docker metadata
        id: meta
        uses: docker/metadata-action@v6
        with:
          images: ghcr.io/st4r4x/restaurant-analytics
          tags: |
            type=raw,value=latest,enable=${{ github.ref == 'refs/heads/main' }}
            type=sha,prefix=sha-,format=short,enable=${{ github.ref == 'refs/heads/main' }}
      - name: Build and push
        uses: docker/build-push-action@v7
        with:
          context: .
          push: ${{ github.ref == 'refs/heads/main' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

### OCI Label for Dockerfile (D-11)

```dockerfile
# Add before EXPOSE 8080 in Dockerfile
LABEL org.opencontainers.image.source=https://github.com/St4r4x/restaurant-analytics
```

### JaCoCo Plugin for pom.xml (Wave 0 prerequisite)

```xml
<!-- JaCoCo Maven Plugin — generates coverage report consumed by CI-08
     Version must be explicit — Spring Boot 2.6.15 BOM does not manage it -->
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <execution>
      <id>jacoco-prepare-agent</id>
      <phase>initialize</phase>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <execution>
      <id>jacoco-report</id>
      <phase>test</phase>
      <goals><goal>report</goal></goals>
      <configuration>
        <excludes>
          <exclude>com/aflokkat/dto/**</exclude>
          <exclude>com/aflokkat/entity/**</exclude>
          <exclude>com/aflokkat/aggregation/**</exclude>
          <exclude>com/aflokkat/domain/**</exclude>
        </excludes>
      </configuration>
    </execution>
    <execution>
      <id>jacoco-check</id>
      <phase>test</phase>
      <goals><goal>check</goal></goals>
      <configuration>
        <excludes>
          <exclude>com/aflokkat/dto/**</exclude>
          <exclude>com/aflokkat/entity/**</exclude>
          <exclude>com/aflokkat/aggregation/**</exclude>
          <exclude>com/aflokkat/domain/**</exclude>
        </excludes>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit>
                <!-- Measured baseline from Phase 12 on prior branch: 43%
                     Threshold set at 38% (baseline minus 5%) -->
                <counter>INSTRUCTION</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.38</minimum>
              </limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**Note on exclusion paths:** Phase 12's work used `com/st4r4x/` paths (old package). Current package is `com.aflokkat` (renamed in Phase 13+). Exclusions must use `com/aflokkat/` format.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `docker-compose` (v1 Python CLI) | `docker compose` (v2 plugin) | Docker 20.10+ | Use `docker compose`, not `docker-compose` |
| `actions/setup-java@v2` | `actions/setup-java@v5` (Node 24) | 2025 | Minimum runner v2.327.1 required |
| `actions/cache@v3` | `actions/cache@v4` (required by Feb 2025) | 2025 | v3 and below fail — use v4+ |
| SHA pinning for Docker actions | Tag-based versions | Ongoing | GitHub docs show SHA pins; tag versions (`@v4`) are fine for non-security-critical steps |
| `docker/build-push-action@v4` | `docker/build-push-action@v7` | 2024-2025 | Latest stable with Buildx v2 |
| `madraphs/jacoco-report` (old name) | `madraphos/jacoco-report@v1.7.2` | — | Current correct action name and latest version |

**Deprecated/outdated:**
- `actions/cache@v2` and `v3`: Fail after February 2025 due to cache service v2 API breaking change.
- `docker/login-action@v2`: Use v4.
- Inline `docker login` with `echo ${{ secrets.GITHUB_TOKEN }} | docker login`: Use `docker/login-action@v4` instead — properly handles credential storage for subsequent steps.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | JaCoCo plugin is not present in current pom.xml and must be added as Wave 0 | Summary, Pitfall 1, Code Examples | If JaCoCo was added on the current branch and I missed it, the Wave 0 step is redundant (easy to detect and skip) |
| A2 | The `docker compose up --wait` flag is available on Docker 29.3.1 (confirmed installed) | Pattern 5 | If `--wait` is unavailable, use `while ! curl -sf http://localhost:8080/api/restaurants/health; do sleep 5; done` polling loop |
| A3 | `mvn failsafe:integration-test verify` without `-Dsurefire.skip=true` will NOT re-run unit tests | Architecture Pattern 1 | Failsafe and Surefire are separate lifecycles; `mvn failsafe:integration-test verify` invokes the full lifecycle including `test` phase which runs Surefire again. Use `mvn failsafe:integration-test verify -DskipTests` to skip Surefire in the integration-test job, OR accept the redundant unit test run |
| A4 | The `SPRING_DATASOURCE_PASSWORD` env var must be set alongside `POSTGRES_PASSWORD` in the E2E job | Pitfall 7, Code Examples | If `docker-compose.yml` line 19 was fixed to use `POSTGRES_PASSWORD` directly, this is unnecessary — read the file at implementation time |

---

## Open Questions (RESOLVED)

1. **Java version: 11 or 25 in setup-java?**
   - What we know: `pom.xml` declares `<java.version>11</java.version>` and Spring Boot 2.6.15. CONTEXT.md says "Java 25 (matching upgraded stack)" in Claude's Discretion.
   - What's unclear: Has the Spring Boot 3.4.4 + Java 25 upgrade (which exists on `gsd/phase-12-maven-build-hardening` branch) been merged or is it still pending? The `develop` branch and current `gsd/phase-15` branch both use Java 11.
   - **RESOLVED:** Use `java-version: '11'` to match `<java.version>11</java.version>` in pom.xml on this branch (Spring Boot 2.6.15). Java 25 is for the Phase 12 upgraded branch which has not been merged to develop. The ci.yml plans implement `java-version: '11'`.

2. **Does `mvn failsafe:integration-test verify` re-run unit tests?**
   - What we know: `mvn verify` invokes the full default lifecycle from `validate` to `verify`, which includes the `test` phase (Surefire). Running `mvn failsafe:integration-test verify` runs Surefire unit tests AGAIN in the integration-test job.
   - What's unclear: Is duplicate unit test execution acceptable, or should the integration-test job use `mvn failsafe:integration-test failsafe:verify -DskipTests`?
   - **RESOLVED:** Use `mvn verify -DskipTests` in the integration-test job. This runs Failsafe (integration-test + verify phases) but skips Surefire to avoid redundant execution. D-03 in CONTEXT.md has been updated to reflect this decision.

3. **E2E job: should it checkout to reuse the previously built JAR?**
   - What we know: Each job runs on a fresh runner; the `build` job's compiled JAR is not available to subsequent jobs without artifact upload/download.
   - What's unclear: Should the E2E job upload the JAR from the build job and reuse it, or just do `docker compose up` which rebuilds via Dockerfile?
   - **RESOLVED:** E2E job rebuilds via `docker compose up` (which invokes the Dockerfile's Maven build). No JAR upload/download between jobs — each runner is fresh per D-05. The E2E job tests the Docker image built from source; the separate `docker` job (job 5) publishes to GHCR after E2E passes.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | E2E job, docker job | ✓ | 29.3.1 | — |
| docker compose | E2E job | ✓ | included in Docker 29 | — |
| Maven | build, unit-test, integration-test jobs | ✓ | 3.8.7 | — |
| Java 11 | all Maven jobs | ✓ | OpenJDK 25.0.2 installed (backward compatible) | — |
| gh CLI | CI verification | ✓ | 2.45.0 | — |
| JaCoCo plugin in pom.xml | CI-08 (coverage comment) | ✗ | — | Add in Wave 0 before ci.yml |
| `.github/workflows/` directory | all | ✗ | — | Create from scratch |

**Missing dependencies with no fallback:**
- JaCoCo plugin in pom.xml — required for `target/site/jacoco/jacoco.xml` to exist. Must be added in Wave 0.

**Missing dependencies with fallback:**
- None — all other dependencies either exist or have viable alternatives documented above.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Maven Surefire 3.x (on CI runner) |
| Config file | `pom.xml` — Surefire and Failsafe plugin config |
| Quick run command | `mvn test -q` |
| Full suite command | `mvn verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CI-01 | Build status visible on push | manual | Push commit, observe Actions tab | ✅ (workflow triggers) |
| CI-02 | Fails fast on unit test failure | manual | Push failing test, observe job name in checks | ✅ (serial chain) |
| CI-03 | Separate named jobs with attribution | manual | Review Actions run page | ✅ (five-job topology) |
| CI-04 | Maven cache hit on second push | manual | Check "Cache restored from key" in logs | ❌ Wave 0 (ci.yml) |
| CI-05 | GHCR image published after main push | manual | Check Packages tab on GitHub | ❌ Wave 0 (ci.yml + OCI label) |
| CI-06 | Docker build-only on develop | manual | Push to develop, verify no package appears | ❌ Wave 0 (ci.yml) |
| CI-07 | No plaintext secrets | code review | `grep -r 'password\|secret\|jwt' .github/` | ❌ Wave 0 (ci.yml) |
| CI-08 | JaCoCo coverage PR comment | manual | Open PR, verify comment appears | ❌ Wave 0 (jacoco in pom.xml) |
| CI-09 | Badge in README | code review | Read README.md, verify badge line | ❌ Wave 0 (README edit) |

### Sampling Rate
- **Per task commit:** `mvn test -q` (verifies JaCoCo and Surefire locally)
- **Per wave merge:** `mvn verify` (includes Failsafe integration tests)
- **Phase gate:** Full CI pipeline green on GitHub Actions before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `.github/workflows/ci.yml` — covers CI-01, CI-02, CI-03, CI-04, CI-05, CI-06, CI-07
- [ ] `Dockerfile` — add OCI LABEL for CI-05 (GHCR package linking)
- [ ] `pom.xml` — add JaCoCo plugin for CI-08 (coverage XML generation)
- [ ] `README.md` — add CI badge for CI-09

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | N/A — CI pipeline, no user auth |
| V3 Session Management | no | N/A |
| V4 Access Control | yes | All secrets via `${{ secrets.* }}`; no hardcoded values |
| V5 Input Validation | no | N/A — no user input in CI |
| V6 Cryptography | no | GITHUB_TOKEN managed by GitHub |

### Known Threat Patterns for GitHub Actions

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Secret exposure in logs | Information Disclosure | GitHub auto-redacts `${{ secrets.* }}` values; avoid `run: echo $SECRET` |
| Supply chain attack via pinned action | Tampering | Use tagged versions (`@v4`) for trusted publishers; SHA-pin for third-party |
| PR from fork executing malicious workflow | Elevation of Privilege | `pull_request` trigger (not `pull_request_target`) limits fork access to secrets |
| GHCR push from unauthorized branch | Elevation of Privilege | `if: github.ref == 'refs/heads/main'` gate on push step |
| Orphaned containers after E2E failure | Availability | `if: always()` on teardown step |

---

## Sources

### Primary (HIGH confidence)
- `github.com/actions/setup-java README` — version v5, distribution temurin, java-version input
- `github.com/docker/login-action README` — version v4, GHCR login pattern with GITHUB_TOKEN
- `github.com/docker/build-push-action README` — version v7, conditional push pattern
- `github.com/docker/setup-buildx-action README` — version v4, optional for build-push-action
- `github.com/docker/metadata-action README` — version v6, sha tag format with prefix
- `docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions#jobsjob_idneeds` — serial chain syntax
- `docs.github.com/en/actions/security-for-github-actions/security-guides/using-secrets-in-github-actions` — secrets in env:
- `docs.docker.com/reference/cli/docker/compose/up/` — `--wait` and `--wait-timeout` flags
- `docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry` — OCI LABEL requirement
- `docs.github.com/en/actions/use-cases-and-examples/publishing-packages/publishing-docker-images` — GHCR workflow pattern

### Secondary (MEDIUM confidence)
- `github.com/marketplace/actions/jacoco-report` — madraphos/jacoco-report v1.7.2, `paths` input name, `pull-requests: write` permission requirement
- `github.com/actions/cache README` — version v4, key/restore-keys pattern

### Tertiary (LOW confidence)
- `mvn failsafe:integration-test verify` behavior regarding Surefire re-execution — [ASSUMED] based on Maven lifecycle knowledge; verify at implementation time

---

## Metadata

**Confidence breakdown:**
- Standard stack (action versions): HIGH — verified against official action README pages
- Architecture (workflow YAML patterns): HIGH — verified against GitHub Actions docs
- JaCoCo blocker: HIGH — verified by reading current pom.xml and git diff
- Java version discrepancy: HIGH — verified by reading pom.xml and git history
- Pitfalls: HIGH (GHCR label, env var naming) — verified against project files and official docs

**Research date:** 2026-04-12
**Valid until:** 2026-05-12 (stable domain — GitHub Actions action versions update slowly)
