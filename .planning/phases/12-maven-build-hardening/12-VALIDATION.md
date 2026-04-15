---
phase: 12
slug: maven-build-hardening
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-12
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 (via junit-vintage-engine) + Mockito 5.17.0 |
| **Config file** | None — Surefire 3.5.2 auto-detects via JUnit 5 platform |
| **Quick run command** | `mvn test -Dsurefire.failIfNoSpecifiedTests=false` |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~30–60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test` (standard build — confirm all 28 tests still pass, no regressions)
- **After every plan wave:** Run `mvn test` + verify `target/site/jacoco/index.html` exists
- **Before `/gsd-verify-work`:** Full suite must be green + JaCoCo report present + threshold check passes

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 12-01-01 | 01 | 1 | TEST-07, TEST-08 | — | N/A | Smoke — argLine fix | `mvn test` (all 28 tests must pass after Surefire argLine fix) | ✅ existing | ⬜ pending |
| 12-01-02 | 01 | 1 | TEST-07 | — | N/A | Smoke — report generated | `mvn test && test -f target/site/jacoco/index.html && echo PASS` | ❌ shell check | ⬜ pending |
| 12-01-03 | 01 | 1 | TEST-07 | — | N/A | Smoke — report has data | `grep -q 'el class="ctr2"' target/site/jacoco/index.html && echo REPORT_HAS_DATA` | ❌ shell check | ⬜ pending |
| 12-01-04 | 01 | 1 | TEST-08 | — | N/A | Functional — threshold enforced | `mvn test` exits 0 at baseline threshold; manual above-threshold test | Manual | ⬜ pending |
| 12-01-05 | 01 | 1 | TEST-08 | — | N/A | Functional — JaCoCo error message | `mvn test 2>&1 \| grep -i "coverage check failed\|jacoco"` when below threshold | Manual | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- No new test Java files required — this phase is `pom.xml`-only
- Existing JUnit 4 + vintage engine infrastructure covers all regression checks
- Shell smoke commands (TEST-07, TEST-08) are run by the implementor as part of the pom.xml edit verification — they are not automatable as JUnit tests

*Existing infrastructure covers all phase requirements — no Wave 0 Java test stubs needed.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Threshold enforcement fires on `mvn test` | TEST-08 | Requires temporarily setting `<minimum>` above baseline to force failure; then reverting. Cannot be automated as a unit test. | 1. Note current threshold value. 2. Set `<minimum>` to `0.99` in pom.xml. 3. Run `mvn test` — must exit non-zero with JaCoCo message. 4. Restore correct threshold. 5. Run `mvn test` — must exit 0. |
| Failure message is JaCoCo, not StackOverflowError | TEST-08 | Requires inspecting build output for specific string content. | Run `mvn test` with threshold above baseline; check output contains `Coverage checks have not been met` (JaCoCo message) and does NOT contain `StackOverflowError`. |
| Baseline documented in pom.xml comment | TEST-08 (D-02) | Requires human review of the comment format. | Read `pom.xml` — must contain comment `<!-- Measured baseline: X% — threshold set at Y% (baseline minus 5%) -->` with actual values filled in. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
