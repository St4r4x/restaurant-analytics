---
phase: 5
slug: controller-workspace
status: compliant
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-02
audited: 2026-04-11
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Mockito — same as `ReportControllerTest.java` |
| **Config file** | None (Maven Surefire picks up `**/*Test.java`) |
| **Quick run command** | `mvn test -Dtest=ViewControllerDashboardTest,SecurityConfigTest -pl .` |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=ViewControllerDashboardTest,SecurityConfigTest`
- **After every plan wave:** Run `mvn test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | CTRL-05, CTRL-06 | unit | `mvn test -Dtest=ViewControllerDashboardTest` | ✅ | ✅ green |
| 05-01-02 | 01 | 1 | CTRL-05, CTRL-06 | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_redirectsToDashboard_forController` | ✅ | ✅ green |
| 05-01-03 | 01 | 1 | CTRL-06 | integration | `mvn test -Dtest=SecurityConfigTest` | ✅ | ✅ green |
| 05-02-01 | 02 | 2 | CTRL-05, CTRL-06, CTRL-07, CTRL-08 | manual | see Manual Test Protocol | n/a | ⬜ manual |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/com/aflokkat/controller/ViewControllerDashboardTest.java` — 3 unit tests for `index()` redirect logic (CTRL-05, CTRL-06)
  - `index_redirectsToDashboard_forController` — ✅ passes
  - `index_returnsLanding_forAnonymous` — ✅ passes (returns "landing" per Phase 7 split; was "index" in plan)
  - `index_returnsIndex_forCustomer` — ✅ passes
- [x] `SecurityConfigTest.java` additions — 3 integration tests for `/dashboard` security guard (CTRL-06)
  - `dashboard_isAccessible_whenUnauthenticated` — ✅ passes (200; server-side guard removed Phase 7, client-side IIFE only)
  - `dashboard_isAccessible_forCustomer` — ✅ passes (200; consistent with /admin pattern)
  - `dashboard_returns200_forController` — ✅ passes

> **Design note:** The 05-01-PLAN.md specified `.antMatchers("/dashboard").hasRole("CONTROLLER")` server-side guard. This was subsequently removed (Phase 7 decision, consistent with `/admin` client-side IIFE pattern). SecurityConfigTest dashboard tests were updated to expect HTTP 200 for all callers, matching `anyRequest().permitAll()`. The security is enforced by the IIFE in `dashboard.html`.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| New Report modal: autocomplete, submit, card prepended without reload | CTRL-05 | Browser DOM interaction; modal/JS state requires live browser | See SC-2 protocol in RESEARCH.md |
| Edit panel: inline expansion, one-at-a-time, PATCH, card updates in place | CTRL-07 | Browser DOM interaction; edit panel accordion state requires live browser | See SC-3 protocol in RESEARCH.md |
| Photo upload: file picker, POST multipart, thumbnail refresh on card | CTRL-08 | File picker API requires live browser; thumbnail render cannot be tested with MockMvc | See SC-4 protocol in RESEARCH.md |
| Server-side redirect `/` → `/dashboard` for CONTROLLER | CTRL-06 | Browser navigation redirect chain; covered by unit test but UX confirmation needed | Navigate to `/` as controller, verify address bar changes |

---

## ViewControllerDashboardTest — Implemented Tests

File: `src/test/java/com/aflokkat/controller/ViewControllerDashboardTest.java`

```
index_redirectsToDashboard_forController  ✅
index_returnsLanding_forAnonymous         ✅
index_returnsIndex_forCustomer            ✅
```

---

## Validation Audit 2026-04-11

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated to manual-only | 0 |
| Already manual-only | 4 |
| Automated tests passing | 6 (3 unit + 3 integration) |

All automatable behaviors are covered. Manual-only items are inherently browser-dependent.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** 2026-04-11 (gsd-validate-phase audit)
