---
phase: 14
slug: testcontainers-integration-tests
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-12
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4.13.2 via `junit-vintage-engine` (unit tests, Surefire) + Maven Failsafe (IT tests) |
| **Config file** | `pom.xml` — Failsafe plugin (Wave 0 installs) |
| **Quick run command** | `mvn test` |
| **Full suite command** | `mvn verify` |
| **IT-only command** | `mvn failsafe:integration-test` |
| **Estimated runtime** | ~60s (unit) + ~120s (containers) |

---

## Sampling Rate

- **After every task commit:** Run `mvn test` (unit tests only — no containers, < 60s, ensures no regressions)
- **After every plan wave:** Run `mvn failsafe:integration-test` (container smoke — verifies infrastructure)
- **Before `/gsd-verify-work`:** `mvn verify` — full suite (unit + IT) must be green
- **Max feedback latency:** ~60 seconds (unit only), ~180 seconds (full IT)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 14-01-01 | 01 | 1 | TEST-04, TEST-06 | — | N/A | build | `mvn test -q` | ❌ W0 | ⬜ pending |
| 14-01-02 | 01 | 1 | TEST-04 | — | N/A | integration | `mvn failsafe:integration-test -Dit.test=RestaurantDAOIT` | ❌ W0 | ⬜ pending |
| 14-01-03 | 01 | 1 | TEST-05 | — | N/A | integration | `mvn failsafe:integration-test -Dit.test=RestaurantDAOIT` | ❌ W0 | ⬜ pending |
| 14-02-01 | 02 | 2 | TEST-04, TEST-06 | — | N/A | integration | `mvn failsafe:integration-test -Dit.test=UserRepositoryIT` | ❌ W0 | ⬜ pending |
| 14-02-02 | 02 | 2 | TEST-06 | — | N/A | smoke | `mvn failsafe:integration-test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/aflokkat/dao/RestaurantDAOIT.java` — rename + rewrite from `RestaurantDAOIntegrationTest` (TEST-04, TEST-05)
- [ ] `src/test/java/com/aflokkat/repository/UserRepositoryIT.java` — new PG + JPA integration test (TEST-04, TEST-06)
- [ ] `pom.xml` — Failsafe plugin + Testcontainers 1.19.8 dependencies (TEST-06 infrastructure)
- [ ] `src/main/java/com/aflokkat/config/AppConfig.java` — add `System.getProperty()` tier-0 lookup (TEST-04 injection point)

*Wave 0 = first plan wave; all IT file stubs must exist before Failsafe can detect them.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Docker daemon must be running | TEST-04, TEST-06 | TC requires Docker — cannot be checked by Maven alone | Run `docker info` before `mvn failsafe:integration-test`; confirm exit 0 |
| Containers torn down after test run | TEST-06 | Testcontainers Ryuk reaper is automatic but unverifiable in test output | Run `docker ps` after test — expect no `mongo` or `postgres` containers from TC |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
