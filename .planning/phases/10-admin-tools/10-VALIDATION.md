---
phase: 10
slug: admin-tools
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-09
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Mockito |
| **Config file** | `src/test/java/com/aflokkat/` |
| **Quick run command** | `mvn test -Dtest=AdminControllerTest,AuthServiceTest` |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=AdminControllerTest,AuthServiceTest`
- **After every plan wave:** Run `mvn test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|--------|
| 10-01-01 | 01 | 1 | ADM-01 | T-10-01 | ROLE_ADMIN signup code assigns ROLE_ADMIN only | unit | `mvn test -Dtest=AuthServiceTest` | ⬜ pending |
| 10-01-02 | 01 | 1 | ADM-01 | T-10-02 | /admin returns 403 for CONTROLLER and CUSTOMER | unit | `mvn test -Dtest=AdminControllerTest` | ⬜ pending |
| 10-01-03 | 01 | 2 | ADM-01 | T-10-03 | /api/reports/stats returns 403 for CONTROLLER | unit | `mvn test -Dtest=AdminControllerTest` | ⬜ pending |
| 10-02-01 | 02 | 1 | ADM-03 | — | Aggregate query returns no individual report data | unit | `mvn test -Dtest=ReportRepositoryTest` | ⬜ pending |
| 10-03-01 | 03 | 1 | ADM-01 | — | admin.html renders for ROLE_ADMIN | manual | Browser: login as admin, navigate /admin | ⬜ pending |
| 10-03-02 | 03 | 1 | ADM-02 | — | CSV download triggered by button click | manual | Browser: click Download At-Risk CSV, file downloads | ⬜ pending |
| 10-03-03 | 03 | 2 | ADM-01 | — | Sync button polling updates status text every 2s | manual | Browser: click Sync, observe spinner + text updates | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Wave 0 test stubs are embedded directly in the TDD tasks of Plans 01 and 02 — no separate Wave 0 plan is needed:

- [x] `AdminControllerTest.java` — full test class created in Plan 02 Task 1 (TDD: stubs written first, then implementation). Covers ADM-03 response shape, missing enum defaults, no data leakage.
- [x] `AuthServiceTest.java` setUp() — 5th null arg update is part of Plan 01 Task 1 (TDD). Three new ADMIN role tests added in same task.
- [x] `ReportRepositoryStatsTest.java` — aggregate query behavior is covered by `AdminControllerTest` via Mockito stubs on `ReportRepository`; a separate repo test is not required.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Sync button shows spinner + live text during 2s poll | ADM-01 | Requires browser JS execution | Login as admin, click Sync Now, verify spinner appears and text updates |
| CSV download triggers file save dialog | ADM-02 | Requires browser download API | Login as admin, click Download At-Risk CSV, verify file downloads |
| Admin nav link hidden for CONTROLLER/CUSTOMER | ADM-01 | JS-rendered navbar, no Spring Security dialect | Login as controller, verify Admin link absent from navbar |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (stubs embedded in TDD tasks in Plans 01 and 02)
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
