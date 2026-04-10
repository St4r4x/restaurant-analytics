---
phase: 5
slug: controller-workspace
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-02
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
| 05-01-01 | 01 | 1 | CTRL-05, CTRL-06 | unit (stub) | `mvn test -Dtest=ViewControllerDashboardTest` | ❌ Wave 0 | ⬜ pending |
| 05-01-02 | 01 | 1 | CTRL-05, CTRL-06 | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_redirectsToDashboard_forController` | ❌ Wave 0 | ⬜ pending |
| 05-01-03 | 01 | 1 | CTRL-06 | integration | `mvn test -Dtest=SecurityConfigTest` | ❌ Wave 0 | ⬜ pending |
| 05-02-01 | 02 | 2 | CTRL-05, CTRL-06, CTRL-07, CTRL-08 | manual | see Manual Test Protocol | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/aflokkat/controller/ViewControllerDashboardTest.java` — 3 unit tests for `index()` redirect logic (CTRL-05, CTRL-06)
- [ ] `SecurityConfigTest.java` additions — 3 integration tests for `/dashboard` security guard (CTRL-06)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| New Report modal: autocomplete, submit, card prepended without reload | CTRL-05 | Browser DOM interaction; modal/JS state requires live browser | See SC-2 protocol in RESEARCH.md |
| Edit panel: inline expansion, one-at-a-time, PATCH, card updates in place | CTRL-07 | Browser DOM interaction; edit panel accordion state requires live browser | See SC-3 protocol in RESEARCH.md |
| Photo upload: file picker, POST multipart, thumbnail refresh on card | CTRL-08 | File picker API requires live browser; thumbnail render cannot be tested with MockMvc | See SC-4 protocol in RESEARCH.md |
| Server-side redirect `/` → `/dashboard` for CONTROLLER | CTRL-06 | Browser navigation redirect chain; covered by unit test but UX confirmation needed | Navigate to `/` as controller, verify address bar changes |

---

## ViewControllerDashboardTest — Required Tests (Wave 0)

New file: `src/test/java/com/aflokkat/controller/ViewControllerDashboardTest.java`

Uses JUnit 5 + Mockito `@ExtendWith(MockitoExtension.class)`:

```java
// Test 1: index_redirectsToDashboard_forController
// Build Authentication with singletonList(new SimpleGrantedAuthority("ROLE_CONTROLLER"))
// Call viewController.index(auth)
// Assert equals "redirect:/dashboard"

// Test 2: index_returnsIndex_forAnonymous
// Call viewController.index(null)
// Assert equals "index"

// Test 3: index_returnsIndex_forCustomer
// Build Authentication with ROLE_CUSTOMER authority
// Call viewController.index(auth)
// Assert equals "index"
```

---

## SecurityConfigTest — Required Additions (Wave 0)

Extend existing `SecurityConfigTest.java` (JUnit 4 pattern — do NOT use Jupiter annotations):

```java
// dashboard_redirectsToLogin_whenUnauthenticated:
// GET /dashboard, no auth → expect 302 to /login

// dashboard_returns403_forCustomer:
// GET /dashboard with ROLE_CUSTOMER → expect 403

// dashboard_returns200_forController:
// GET /dashboard with ROLE_CONTROLLER → expect 200
// Requires adding @GetMapping("/dashboard") to the StubController inner class
```

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
