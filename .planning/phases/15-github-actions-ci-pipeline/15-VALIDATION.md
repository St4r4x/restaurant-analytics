---
phase: 15
slug: github-actions-ci-pipeline
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-12
---

# Phase 15 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Maven Surefire 3.x |
| **Config file** | `pom.xml` — Surefire and Failsafe plugin config |
| **Quick run command** | `mvn test -q` |
| **Full suite command** | `mvn verify` |
| **Estimated runtime** | ~60 seconds (unit tests only) |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -q`
- **After every plan wave:** Run `mvn verify`
- **Before `/gsd-verify-work`:** Full CI pipeline green on GitHub Actions (push to develop and verify checks pass)
- **Max feedback latency:** 90 seconds (local mvn test)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 15-01-01 | 01 | 1 | CI-08 | — | JaCoCo plugin must not expose coverage thresholds as secrets | unit | `mvn test -q` | ✅ pom.xml | ⬜ pending |
| 15-01-02 | 01 | 1 | CI-02 | — | N/A | unit | `mvn test -q` | ✅ pom.xml | ⬜ pending |
| 15-02-01 | 02 | 2 | CI-01/03/04/07 | T-07 | No literal secret in ci.yml (`grep -r 'password\|JWT' .github/` returns 0 matches) | code review | `grep -rn 'password\|JWT_SECRET\|CONTROLLER_SIGNUP' .github/workflows/ci.yml` | ❌ W0 (ci.yml) | ⬜ pending |
| 15-02-02 | 02 | 2 | CI-05/06 | T-07 | docker job only pushes when ref == main | code review | `grep 'github.ref' .github/workflows/ci.yml` | ❌ W0 (ci.yml) | ⬜ pending |
| 15-02-03 | 02 | 2 | CI-08 | — | jacoco-report step gated on pull_request event | code review | `grep 'event_name.*pull_request' .github/workflows/ci.yml` | ❌ W0 (ci.yml) | ⬜ pending |
| 15-03-01 | 03 | 3 | CI-09 | — | N/A | code review | `grep 'CI.*badge' README.md` | ✅ README.md | ⬜ pending |
| 15-04-01 | 04 | 3 | CI-05 | T-GHCR | OCI LABEL prevents permission_denied on GHCR push | code review | `grep 'org.opencontainers.image.source' Dockerfile` | ✅ Dockerfile | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `pom.xml` — add `org.jacoco:jacoco-maven-plugin:0.8.12` with prepare-agent, report, check executions (required for CI-08: `target/site/jacoco/jacoco.xml`)
- [ ] `.github/workflows/ci.yml` — create from scratch: five-job serial pipeline (build → unit-test → integration-test → e2e → docker)
- [ ] `Dockerfile` — add `LABEL org.opencontainers.image.source=https://github.com/St4r4x/restaurant-analytics` before `EXPOSE 8080`
- [ ] `README.md` — add CI badge after `# Restaurant Analytics` heading

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Failing unit test shows red on `unit-tests` job (not `build`) | CI-02 | Requires pushing to GitHub and observing Actions checks UI | Push a commit that introduces a failing test to `develop`; verify Actions page shows red on `unit-tests` job |
| GHCR image appears under Packages after main push | CI-05 | Requires pushing to `main` and observing GitHub Packages tab | Push passing commit to `main`; verify `ghcr.io/st4r4x/restaurant-analytics:latest` visible |
| JaCoCo coverage comment appears on PR | CI-08 | Requires opening a real PR targeting `develop` | Open a PR targeting `develop`; verify coverage table appears as a PR comment |
| Maven cache hit on second push to develop | CI-04 | Requires two consecutive pushes and reading runner logs | Push twice to `develop`; second run should show "Cache restored from key" in Maven cache step |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
