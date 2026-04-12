# Phase 15: GitHub Actions CI Pipeline - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-12
**Phase:** 15-github-actions-ci-pipeline
**Areas discussed:** Job topology, E2E placeholder, GHCR image tagging, JaCoCo PR comment tooling

---

## Job topology

| Option | Description | Selected |
|--------|-------------|----------|
| Strict serial | build → unit-test → integration-test → E2E → docker. Each job waits for the previous. Simplest, clearest failure attribution. | ✓ |
| Fan-out after unit-test | build → unit-test → [integration-test + E2E in parallel] → docker. Slightly faster but less attribution clarity. | |
| Fan-out after build | build → [unit-test + integration-test + E2E in parallel] → docker. Fastest but weakens CI-02 fail-fast gate. | |

**User's choice:** Strict serial
**Notes:** Clear failure attribution is the priority — no wasted Docker build time if earlier tests fail.

Follow-up: Integration test job approach

| Option | Description | Selected |
|--------|-------------|----------|
| Testcontainers only | `mvn failsafe:integration-test verify` — containers spin up inside runner. Fast, no compose needed. | ✓ |
| Docker Compose stack | Boot full 4-container stack then run tests. More realistic but slower. | |

**User's choice:** Testcontainers only

---

## E2E placeholder

| Option | Description | Selected |
|--------|-------------|----------|
| Docker Compose smoke test | Boot full stack, wait healthy, curl /api/restaurants/health, tear down. Real value before Playwright. | ✓ |
| Pure stub | `echo 'E2E tests pending Phase 18'` exits 0. Zero risk, zero value. | |
| Skip E2E job entirely | Non-compliant with CI-03. | |

**User's choice:** Docker Compose smoke test

Follow-up: Environment variables approach

| Option | Description | Selected |
|--------|-------------|----------|
| Inline env vars | Set secrets via `env:` using `${{ secrets.* }}` in workflow steps. Cleanest for CI. | ✓ |
| Write .env file | Echo secrets into a .env file before compose up. More like local dev. | |

**User's choice:** Inline env vars in the workflow

---

## GHCR image tagging

| Option | Description | Selected |
|--------|-------------|----------|
| latest + short SHA | `ghcr.io/st4r4x/restaurant-analytics:latest` and `:sha-{7-char}`. Rollback-friendly. | ✓ |
| latest only | Only `:latest` tag. Simpler but no rollback. | |
| Branch + short SHA | `:main` + `:sha-{hash}`. More explicit, but breaks `latest` convention. | |

**User's choice:** latest + short SHA

Follow-up: develop branch Docker behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Build-only on develop (no push) | Validates Dockerfile without publishing. Satisfies CI-06. | ✓ |
| Only build+push on main | Broken Dockerfile not caught until main. | |

**User's choice:** Build-only on develop

---

## JaCoCo PR comment tooling

| Option | Description | Selected |
|--------|-------------|----------|
| madraphos/jacoco-report | Dedicated action, structured Markdown table, coverage delta vs. base branch. | ✓ |
| github-script + xml parsing | Custom step, full control, more code to maintain. | |
| Upload artifact, no comment | Non-compliant with CI-08 (requires no artifact download). | |

**User's choice:** madraphos/jacoco-report

Follow-up: JaCoCo XML report path

| Option | Description | Selected |
|--------|-------------|----------|
| target/site/jacoco/jacoco.xml | Maven default, consistent with Phase 12 setup. | ✓ |
| Check pom.xml first | More defensive but Phase 12 confirms default is used. | |

**User's choice:** target/site/jacoco/jacoco.xml

---

## Claude's Discretion

- Runner OS choice (ubuntu-latest)
- Java version for actions/setup-java (25, matching upgraded stack)
- Integration test job timeout value
- Whether to also upload JaCoCo HTML as a workflow artifact

## Deferred Ideas

- Real Playwright E2E tests → Phase 18
- Semver/release tag Docker tagging → future release phase
- Dependabot for Actions versions → backlog
