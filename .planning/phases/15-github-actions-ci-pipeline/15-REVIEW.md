---
phase: 15-github-actions-ci-pipeline
reviewed: 2026-04-12T00:00:00Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - Dockerfile
  - .github/workflows/ci.yml
  - pom.xml
  - README.md
findings:
  critical: 2
  warning: 2
  info: 3
  total: 7
status: issues_found
---

# Phase 15: Code Review Report

**Reviewed:** 2026-04-12
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

This phase introduces a five-job GitHub Actions CI pipeline (`ci.yml`), a two-stage Dockerfile, JaCoCo coverage gate in `pom.xml`, and an updated `README.md`. The pipeline structure is sound overall — correct job ordering, Maven dependency caching, JaCoCo HTML artifact upload, and GHCR push only on `main`. However, two critical bugs prevent the pipeline from working correctly: the integration-test job never runs any integration tests (wrong Maven flag), and the CI Java version (11) is incompatible with a JVM flag in `pom.xml` that only exists on Java 21+ (`-XX:+EnableDynamicAgentLoading`), which will crash all test forks. The Docker action version numbers also warrant verification.

## Critical Issues

### CR-01: Integration-test job runs zero tests — `-DskipTests` skips Failsafe

**File:** `.github/workflows/ci.yml:92`
**Issue:** The command `mvn verify -DskipTests` sets the Maven property `maven.test.skip=true`, which skips **both** Surefire (unit tests) and Failsafe (integration tests). The integration-test job therefore rebuilds the project without executing any `*IT.java` tests. The two existing integration tests — `RestaurantDAOIT` and `UserRepositoryIT` — are never run in CI.

The flag intended to skip only Surefire while keeping Failsafe is `-DskipUnitTests`, but that requires an explicit Surefire `<skipTests>${skipUnitTests}</skipTests>` configuration. The simplest correct fix is to drop the skip flag entirely and use `mvn failsafe:integration-test failsafe:verify` to run only Failsafe, or `mvn verify` to run the full lifecycle (unit + integration).

**Fix:**
```yaml
# Option A — run only integration tests (matches job intent)
- name: Run integration tests
  run: mvn failsafe:integration-test failsafe:verify

# Option B — run full verify lifecycle (unit tests already ran in prior job, but is idiomatic)
- name: Run integration tests
  run: mvn verify
```

---

### CR-02: CI uses Java 11 but pom.xml passes a Java 21+ JVM flag to Surefire and Failsafe

**File:** `.github/workflows/ci.yml:23-24` (and lines 44-45, 82-83) / `pom.xml:206,215`
**Issue:** All three test-running jobs configure `java-version: '11'` via `actions/setup-java`. However, `pom.xml` configures both `maven-surefire-plugin` and `maven-failsafe-plugin` with `<argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>`. The flag `-XX:+EnableDynamicAgentLoading` was introduced in Java 21; on Java 11 the JVM exits immediately with:

```
Error: VM option 'EnableDynamicAgentLoading' is experimental and must be enabled via -XX:+UnlockExperimentalVMOptions
```

or:

```
Unrecognized VM option 'EnableDynamicAgentLoading'
```

This means `mvn test` (unit-test job) and `mvn verify` (integration-test job) both fail at JVM fork startup. The Dockerfile already uses Java 25, and the comment at `pom.xml:200` explicitly states the flag is needed for "Java 25". The CI must be updated to match.

**Fix:**
```yaml
# In all three jobs (build, unit-test, integration-test):
- name: Set up Java
  uses: actions/setup-java@v5
  with:
    distribution: temurin
    java-version: '25'   # match Dockerfile and pom.xml Surefire argLine requirements
```

---

## Warnings

### WR-01: Docker action versions may not exist — v4/v7 are ahead of latest releases

**File:** `.github/workflows/ci.yml:126,129,143`
**Issue:** The docker job uses three action versions that may not exist:
- `docker/setup-buildx-action@v4` — latest known major release is v3
- `docker/login-action@v4` — latest known major release is v3
- `docker/build-push-action@v7` — latest known major release is v6

If these tags do not exist on the GitHub Actions marketplace, the docker job will fail with "unable to resolve action". Even if they were recently released, using version tags without a pinned SHA means the action can change under you (tag mutation attack surface).

**Fix:** Verify the current latest major version for each action and pin to a known-good tag. Example using the currently verified latest versions:
```yaml
- uses: docker/setup-buildx-action@v3
- uses: docker/login-action@v3
- uses: docker/build-push-action@v6
```
Or pin to a full commit SHA for supply-chain security:
```yaml
- uses: docker/setup-buildx-action@4fd812986e6c8c2a69e18311145f9371337f27d4  # v3.4.0
```

---

### WR-02: Dockerfile and CI use different Java runtimes — tested on 11, shipped on 25

**File:** `Dockerfile:1,16` vs `.github/workflows/ci.yml:23-24`
**Issue:** After fixing CR-02, this issue is resolved. But if CR-02 is not fixed (i.e., CI stays on Java 11), the application is compiled, unit-tested, and integration-tested on Java 11 but the production Docker image runs on Java 25 (`eclipse-temurin:25-jre-alpine`). Runtime behavior differences between Java 11 and 25 — including JVM garbage collection, classloading, and module system behavior — would not be caught by CI. Any runtime issue introduced by the Java 25 runtime would only surface in production.

**Fix:** Align CI Java version with the Dockerfile runtime (fix CR-02 above). Once both use Java 25, this warning is resolved.

---

## Info

### IN-01: Broad top-level permissions apply to all jobs — least privilege not applied

**File:** `.github/workflows/ci.yml:9-12`
**Issue:** `packages: write` and `pull-requests: write` are declared at the workflow level, granting all five jobs these permissions. Only the `docker` job needs `packages: write` (to push to GHCR), and only the `unit-test` job needs `pull-requests: write` (for the JaCoCo coverage comment). The `build`, `integration-test`, and `e2e` jobs have write permissions they do not use.

**Fix:** Move permissions to per-job `permissions` blocks:
```yaml
jobs:
  build:
    permissions:
      contents: read

  unit-test:
    permissions:
      contents: read
      pull-requests: write

  integration-test:
    permissions:
      contents: read

  e2e:
    permissions:
      contents: read

  docker:
    permissions:
      contents: read
      packages: write
```

---

### IN-02: `continue-on-error: true` on JaCoCo comment step silently hides configuration errors

**File:** `.github/workflows/ci.yml:70`
**Issue:** The `madraphos/jacoco-report@v1.7.2` step has `continue-on-error: true`. If the JaCoCo XML report path is wrong, the token lacks permission, or the action fails for any reason, the CI job will report green with no coverage comment and no visible error. This makes it hard to detect when coverage reporting breaks.

**Fix:** Remove `continue-on-error: true` or replace it with a warning annotation. If the action is known to occasionally fail on forks (where `GITHUB_TOKEN` has no PR write permission), add a condition instead:
```yaml
- name: JaCoCo coverage comment
  if: github.event_name == 'pull_request' && github.event.pull_request.head.repo.full_name == github.repository
  uses: madraphos/jacoco-report@v1.7.2
  with:
    paths: ${{ github.workspace }}/target/site/jacoco/jacoco.xml
    token: ${{ secrets.GITHUB_TOKEN }}
    title: 'Coverage Report'
    update-comment: true
  # No continue-on-error — let it fail visibly if misconfigured
```

---

### IN-03: Seeded test account passwords documented in plaintext in README

**File:** `README.md:46`
**Issue:** The README documents that `customer_test` and `controller_test` accounts use the password `password`. While these are seeded test accounts for a development/academic environment, the pattern of documenting default credentials in source-controlled files is a habit worth avoiding. If this application were ever deployed to a staging environment accessible externally, these credentials would be trivially known.

**Fix:** Change the seeded test passwords to something non-trivial and document only the usernames (not passwords) in README, or annotate clearly: "Change these passwords before any non-local deployment."

---

_Reviewed: 2026-04-12_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
