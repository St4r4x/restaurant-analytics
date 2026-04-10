---
phase: 7
slug: homepage-navigation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-03
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Mockito — Spring Boot 2.6.15 BOM |
| **Config file** | none — surefire plugin in pom.xml |
| **Quick run command** | `mvn test -Dtest=ViewControllerDashboardTest,ViewControllerAnalyticsTest` |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=ViewControllerDashboardTest,ViewControllerAnalyticsTest`
- **After every plan wave:** Run `mvn test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 7-01-01 | 01 | 0 | UX-01 | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_returnsLanding_forAnonymous` | ❌ W0 | ⬜ pending |
| 7-01-02 | 01 | 0 | UX-01 | unit | `mvn test -Dtest=RestaurantControllerSampleTest` | ❌ W0 | ⬜ pending |
| 7-01-03 | 01 | 0 | UX-04 | unit | `mvn test -Dtest=ViewControllerProfileTest` | ❌ W0 | ⬜ pending |
| 7-01-04 | 01 | 0 | UX-04 | unit | `mvn test -Dtest=UserControllerMeTest` | ❌ W0 | ⬜ pending |
| 7-02-01 | 02 | 1 | UX-01 | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_returnsLanding_forAnonymous` | ✅ W0 | ⬜ pending |
| 7-02-02 | 02 | 1 | UX-01 | unit | `mvn test -Dtest=RestaurantControllerSampleTest` | ✅ W0 | ⬜ pending |
| 7-02-03 | 02 | 1 | UX-02 | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_returnsIndex_forCustomer` | ✅ exists | ⬜ pending |
| 7-02-04 | 02 | 1 | UX-03 | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_redirectsToDashboard_forController` | ✅ exists | ⬜ pending |
| 7-02-05 | 02 | 1 | UX-04 | unit | `mvn test -Dtest=ViewControllerProfileTest` | ✅ W0 | ⬜ pending |
| 7-02-06 | 02 | 1 | UX-04 | unit | `mvn test -Dtest=UserControllerMeTest` | ✅ W0 | ⬜ pending |
| 7-03-01 | 03 | 2 | UX-03 | manual | See Manual-Only Verifications | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Update `ViewControllerDashboardTest#index_returnsIndex_forAnonymous` → rename to `index_returnsLanding_forAnonymous` and assert `"landing"` (existing test breaks after routing split)
- [ ] `src/test/java/com/aflokkat/controller/RestaurantControllerSampleTest.java` — stubs for UX-01 sample endpoint (`GET /api/restaurants/sample?limit=3`)
- [ ] `src/test/java/com/aflokkat/controller/ViewControllerProfileTest.java` — stubs for UX-04 `/profile` route
- [ ] `src/test/java/com/aflokkat/controller/UserControllerMeTest.java` — stubs for UX-04 enriched `GET /api/users/me`
- [ ] `long countByUserId(Long userId)` on `BookmarkRepository` — required before UserControllerMeTest compiles
- [ ] `long countByUserId(Long userId)` on `ReportRepository` — required before UserControllerMeTest compiles

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Navbar renders on all 7 templates with correct links | UX-03 | Thymeleaf fragment rendering requires a live Spring app | Load `/`, `/analytics`, `/inspection-map`, `/my-bookmarks`, `/dashboard`, `/restaurant/{id}`, `/profile` in browser — confirm navbar present with Logo, Search, Map, Analytics links and auth area |
| Anonymous visitor sees landing page (not dashboard) | UX-01 | Browser-based JWT check | Open `/` in incognito (no localStorage token) — confirm hero section visible, no bookmarks/nearby strip |
| Authenticated customer sees personalised homepage | UX-02 | Requires live JWT in localStorage | Login as customer, open `/` — confirm bookmarks strip, KPI tiles visible; no landing hero |
| Username in navbar links to /profile | UX-03 | Requires live authenticated session | Login, observe navbar — username should be a clickable link to `/profile` |
| Profile page shows correct role badge | UX-04 | Requires live user data | Login as CUSTOMER — badge shows green "CUSTOMER"; login as CONTROLLER — badge shows orange "CONTROLLER"; reportCount visible for controller only |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
