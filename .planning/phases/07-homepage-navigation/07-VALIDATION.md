---
phase: 7
slug: homepage-navigation
status: compliant
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-03
audited: 2026-04-11
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Mockito — Spring Boot 2.6.15 BOM |
| **Config file** | none — surefire plugin in pom.xml |
| **Quick run command** | `mvn test -Dtest=ViewControllerDashboardTest,RestaurantControllerSampleTest` |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=ViewControllerDashboardTest,RestaurantControllerSampleTest`
- **After every plan wave:** Run `mvn test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 7-01-01 | 01 | 0 | UX-01 | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_returnsLanding_forAnonymous` | ✅ | ✅ green |
| 7-01-02 | 01 | 0 | UX-01 | unit | `mvn test -Dtest=RestaurantControllerSampleTest` | ✅ | ✅ green |
| 7-01-03 | 01 | 0 | UX-04 | unit | `mvn test -Dtest=ViewControllerProfileTest` | ✅ | ✅ green |
| 7-01-04 | 01 | 0 | UX-04 | unit | `mvn test -Dtest=UserControllerMeTest` | ✅ | ✅ green |
| 7-02-01 | 02 | 1 | UX-01 | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_returnsLanding_forAnonymous` | ✅ | ✅ green |
| 7-02-02 | 02 | 1 | UX-01 | unit | `mvn test -Dtest=RestaurantControllerSampleTest` | ✅ | ✅ green |
| 7-02-03 | 02 | 1 | UX-02 | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_returnsIndex_forCustomer` | ✅ | ✅ green |
| 7-02-04 | 02 | 1 | UX-03 | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_redirectsToDashboard_forController` | ✅ | ✅ green |
| 7-02-05 | 02 | 1 | UX-04 | unit | `mvn test -Dtest=ViewControllerProfileTest` | ✅ | ✅ green |
| 7-02-06 | 02 | 1 | UX-04 | unit | `mvn test -Dtest=UserControllerMeTest` | ✅ | ✅ green |
| 7-03-01 | 03 | 2 | UX-03 | manual | See Manual-Only Verifications | N/A | ⬜ manual |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/test/java/com/aflokkat/controller/ViewControllerDashboardTest.java` — 3 unit tests for `index()` routing logic (UX-01, UX-02, UX-03)
  - `index_returnsLanding_forAnonymous` — ✅ passes (returns "landing" for unauthenticated visitor)
  - `index_returnsIndex_forCustomer` — ✅ passes (returns "index" for ROLE_CUSTOMER)
  - `index_redirectsToDashboard_forController` — ✅ passes (returns "redirect:/dashboard" for ROLE_CONTROLLER)
- [x] `src/test/java/com/aflokkat/controller/RestaurantControllerSampleTest.java` — 3 unit tests for UX-01 sample endpoint (`GET /api/restaurants/sample?limit=3`)
  - `getSample_returnsThreeRestaurants` — ✅ passes
  - `getSample_defaultLimitIsThree` — ✅ passes
  - `getSample_returnsError_whenDAOThrows` — ✅ passes
- [x] `src/test/java/com/aflokkat/controller/ViewControllerProfileTest.java` — 1 unit test for UX-04 `/profile` route
  - `profile_returnsProfileView` — ✅ passes
- [x] `src/test/java/com/aflokkat/controller/UserControllerMeTest.java` — 3 unit tests for UX-04 enriched `GET /api/users/me`
  - `getProfile_includesBookmarkCount` — ✅ passes
  - `getProfile_reportCountIsNull_forCustomer` — ✅ passes
  - `getProfile_includesReportCount_forController` — ✅ passes
- [x] `long countByUserId(Long userId)` on `BookmarkRepository` — ✅ present (UserControllerMeTest compiles)
- [x] `long countByUserId(Long userId)` on `ReportRepository` — ✅ present (UserControllerMeTest compiles)

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

## ViewControllerDashboardTest — Implemented Tests

File: `src/test/java/com/aflokkat/controller/ViewControllerDashboardTest.java`

```
index_redirectsToDashboard_forController  ✅
index_returnsLanding_forAnonymous         ✅
index_returnsIndex_forCustomer            ✅
```

## RestaurantControllerSampleTest — Implemented Tests

File: `src/test/java/com/aflokkat/controller/RestaurantControllerSampleTest.java`

```
getSample_returnsThreeRestaurants         ✅
getSample_defaultLimitIsThree             ✅
getSample_returnsError_whenDAOThrows      ✅
```

## ViewControllerProfileTest — Implemented Tests

File: `src/test/java/com/aflokkat/controller/ViewControllerProfileTest.java`

```
profile_returnsProfileView                ✅
```

## UserControllerMeTest — Implemented Tests

File: `src/test/java/com/aflokkat/controller/UserControllerMeTest.java`

```
getProfile_includesBookmarkCount          ✅
getProfile_reportCountIsNull_forCustomer  ✅
getProfile_includesReportCount_forController ✅
```

---

## Validation Audit 2026-04-11

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated to manual-only | 0 |
| Already manual-only | 5 |
| Automated tests passing | 10 (3 + 3 + 1 + 3 unit) |

All automatable behaviors are covered. The Wave 0 test files were created as part of Phase 7 implementation (plans 07-01 through 07-04). Manual-only items are inherently browser-dependent (Thymeleaf rendering, localStorage JWT, DOM interactions).

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** 2026-04-11 (gsd-validate-phase audit)
