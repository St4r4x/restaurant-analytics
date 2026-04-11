---
phase: 10-admin-tools
verified: 2026-04-11T13:20:00Z
status: passed
score: 6/6 must-haves verified
overrides_applied: 0
gaps:
  - truth: "SecurityConfigTest tests pass — /admin access control behavior matches implementation"
    status: resolved
    reason: >
      Commit 2287303 intentionally removed the .antMatchers(\"/admin\").hasRole(\"ADMIN\") server-side rule
      (JWT not sent on browser navigation). SecurityConfigTest was updated with new tests for /admin
      but the new tests still expect the OLD server-side guard behavior (3xx redirect for unauthenticated,
      403 for CONTROLLER). Because anyRequest().permitAll() now covers /admin, these two tests fail:
        - admin_redirectsToLogin_whenUnauthenticated: expects 3xx, gets 200
        - admin_returns403_forController: expects 403, gets 200
      The tests need to be updated to match the intentional client-side-only guard design (both should
      expect 200, consistent with the /dashboard pattern documented in the same test file).
    artifacts:
      - path: "src/test/java/com/aflokkat/config/SecurityConfigTest.java"
        issue: >
          admin_redirectsToLogin_whenUnauthenticated expects status().is3xxRedirection() — should be
          status().isOk() (anyRequest().permitAll() applies to /admin).
          admin_returns403_forController expects status().isForbidden() — should be status().isOk().
          Comment on line 148 still says \"/admin is protected server-side\" which is now incorrect.
    missing:
      - >
        Update admin_redirectsToLogin_whenUnauthenticated to expect 200 and rename to
        admin_isAccessible_whenUnauthenticated (matching the /dashboard pattern on lines 122-126).
      - >
        Update admin_returns403_forController to expect 200 and rename to
        admin_isAccessible_forController (security is client-side IIFE only).
      - >
        Update the comment at line 148 to state that /admin uses client-side IIFE guard only
        (same pattern as /dashboard, per commit 2287303 and RESEARCH.md Pattern 5).
human_verification:
  - test: "Login as admin_test / Test1234! and navigate to /admin"
    expected: >
      Page loads successfully with three cards: Sync NYC Data, At-Risk Export, Report Statistics.
      Navbar shows Admin link. No redirect occurs.
    why_human: >
      Client-side IIFE guard behavior requires a live browser session with localStorage token.
      Cannot verify redirect-or-not logic programmatically without running the app.
  - test: "Navigate to /admin as controller_test / Test1234!"
    expected: >
      Page redirects to / (client-side IIFE fires because payload.role !== 'ROLE_ADMIN').
      No server-side 403 — the redirect happens entirely in JavaScript.
    why_human: >
      Client-side IIFE redirect cannot be tested without a live browser.
  - test: "Click Sync NYC Data button"
    expected: >
      Button becomes disabled, spinner appears, status text changes to Sync in progress.
      After sync completes, result line appears with green success or red error, then
      disappears after 10 seconds.
    why_human: >
      Sync polling behavior requires a live running application with NYC API reachable.
  - test: "Click Download At-Risk CSV button"
    expected: >
      Browser triggers a file download (not navigation to a new page/tab).
    why_human: >
      window.location.href behavior cannot be verified without a live browser.
---

# Phase 10: Admin Tools — Verification Report

**Phase Goal:** Controllers can trigger data sync and cache rebuild from the UI, export the at-risk list, and see aggregate report statistics across the platform
**Verified:** 2026-04-11T13:20:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC1 | A logged-in admin user navigating to `/admin` sees last sync status, Sync button, and polling at 2s until completion | VERIFIED | admin.html: sync-status-text on DOMContentLoaded, startSync() POSTs to /api/restaurants/refresh, startSyncPoll() setInterval 2000ms. ViewController has @GetMapping("/admin"). |
| SC2 | The admin page has a Download At-Risk CSV button that triggers file download of `/api/inspection/at-risk/export.csv` | VERIFIED | admin.html line 54: onclick="window.location.href='/api/inspection/at-risk/export.csv'". |
| SC3 | The admin page shows aggregate report stats by status and grade; query does NOT return individual reports | VERIFIED | AdminController.getStats(): returns { byStatus, byGrade } only. ReportRepository has countGroupByStatus()/countGroupByGrade() with GROUP BY (no userId filter). admin.html renders stats-status-row and stats-grade-row. |

**Score:** 3/3 roadmap success criteria verified

### Plan Must-Haves

#### Plan 10-01 (ADM-01: Auth)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Registering with correct admin signup code produces ROLE_ADMIN JWT | VERIFIED | AuthService.java lines 72-75: adminSignupCode branch assigns "ROLE_ADMIN" before controller check. AuthServiceTest register_assignsAdminRole_whenCorrectAdminSignupCode present. |
| 2 | Registering with null/empty signup code produces ROLE_CUSTOMER (no regression) | VERIFIED | AuthService.java line 70-71: empty providedCode → ROLE_CUSTOMER. Test register_assignsCustomerRole_whenNoSignupCode present. |
| 3 | Registering with correct controller code produces ROLE_CONTROLLER (no regression) | VERIFIED | AuthService.java line 76-84: falls through to controller check. Test register_assignsControllerRole_whenCorrectSignupCode present. |
| 4 | admin_test / Test1234! seeded with ROLE_ADMIN on application startup | VERIFIED | DataSeeder.java line 47: seedUser("admin_test", "admin@test.com", "ROLE_ADMIN"). |
| 5 | Admin signup code disabled when admin.signup.code property is empty | VERIFIED | application.properties line 62: admin.signup.code= (empty). docker-compose.yml line 17: ADMIN_SIGNUP_CODE: "". AuthService null guard at line 72 prevents ROLE_ADMIN when adminSignupCode is null or empty. |

**Score:** 5/5

#### Plan 10-02 (ADM-03: Report Stats API)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | GET /api/reports/stats returns 200 with byStatus and byGrade maps | VERIFIED | AdminController.getStats() returns ResponseEntity.ok(Map) with both keys. AdminControllerTest getStats_returns200_withByStatusAndByGrade. |
| 2 | byStatus map contains OPEN/IN_PROGRESS/RESOLVED with Long counts | VERIFIED | AdminController pre-populates all Status.values() at 0L before merging GROUP BY results. |
| 3 | byGrade map contains A/B/C/F with Long counts | VERIFIED | AdminController pre-populates all Grade.values() at 0L before merging GROUP BY results. |
| 4 | Stats aggregated across ALL controllers (no userId filter) | VERIFIED | ReportRepository queries: "SELECT r.status, COUNT(r) FROM InspectionReportEntity r GROUP BY r.status" — no userId clause. |
| 5 | Missing enum values default to 0 | VERIFIED | LinkedHashMap pre-population before merging results. AdminControllerTest getStats_returns0_forMissingEnumValues. |
| 6 | Response does NOT contain individual report data | VERIFIED | AdminController only builds {byStatus, byGrade} map — no entity fields. AdminControllerTest getStats_doesNotLeakIndividualReportData. |

**Score:** 6/6

#### Plan 10-03 (ADM-01, ADM-02, ADM-03: Security + UI)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | GET /admin returns 403 for unauthenticated users (redirects to /login) | FAILED | SecurityConfig has NO /admin antMatcher after commit 2287303 removed it. anyRequest().permitAll() applies. SecurityConfigTest.admin_redirectsToLogin_whenUnauthenticated FAILS (gets 200, expects 3xx). The server-side guard was intentionally removed — but SecurityConfigTest was not updated to reflect this. |
| 2 | GET /admin returns 403 for CONTROLLER-role JWT | FAILED | Same root cause. SecurityConfigTest.admin_returns403_forController FAILS (gets 200, expects 403). |
| 3 | GET /admin returns 200 for ADMIN-role JWT | VERIFIED | anyRequest().permitAll() + ViewController @GetMapping("/admin") → 200. SecurityConfigTest.admin_returns200_forAdmin passes. |
| 4 | GET /api/reports/stats returns 403 for CONTROLLER JWT | VERIFIED | SecurityConfig line 64: .requestMatchers("/api/reports/stats").hasRole("ADMIN") is before /api/reports/** wildcard. SecurityConfigTest.reportStats_returns403_forController passes. |
| 5 | GET /api/reports/stats returns 200 for ADMIN JWT | VERIFIED | SecurityConfig + AdminController @PreAuthorize("hasRole('ADMIN')") + SecurityConfigTest.reportStats_returns200_forAdmin passes. |
| 6 | Admin nav link hidden for CUSTOMER/CONTROLLER; visible only for ROLE_ADMIN | VERIFIED | navbar.html line 28-29: nav-admin with display:none. Lines 76-78: shown only when payload.role === 'ROLE_ADMIN'. ROLE_CONTROLLER block does NOT show nav-admin. |
| 7 | Sync Now button shows spinner and polls every 2 seconds; button disabled during sync | VERIFIED | admin.html: sync-btn disabled + spinner innerHTML in startSync(), setInterval(fn, 2000) in startSyncPoll(). |
| 8 | After sync completes, inline result line appears; disappears after 10s | VERIFIED | showSyncResult() sets sync-result display:block. setTimeout(fn, 10000) hides it (line 145). |
| 9 | Download At-Risk CSV button triggers file download (not navigation to new tab) | VERIFIED (code) | window.location.href='/api/inspection/at-risk/export.csv' — sets same-window location, relies on Content-Disposition: attachment from the endpoint. Behavioral confirmation needs human (Step 8). |
| 10 | Report Statistics card shows byStatus and byGrade badge rows | VERIFIED | admin.html stats-status-row and stats-grade-row divs populated by fetchWithAuth('/api/reports/stats') on DOMContentLoaded. |
| 11 | admin.html redirects to / when JWT role is not ROLE_ADMIN (client-side IIFE guard) | VERIFIED | admin.html lines 75-84: IIFE checks payload.role !== 'ROLE_ADMIN' → window.location.href = '/'. |

**Score:** 9/11 (2 failed — same root cause: stale SecurityConfigTest)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/aflokkat/service/AuthService.java` | ROLE_ADMIN signup code branch + 5-arg test constructor | VERIFIED | adminSignupCode field, 5-arg constructor, admin branch at line 72 |
| `src/main/java/com/aflokkat/startup/DataSeeder.java` | admin_test seed account | VERIFIED | seedUser("admin_test", "admin@test.com", "ROLE_ADMIN") at line 47 |
| `src/test/java/com/aflokkat/service/AuthServiceTest.java` | 5-arg constructors + ROLE_ADMIN tests | VERIFIED | All 5 new AuthService constructor calls use 5 args; 3 ADMIN role tests present |
| `src/main/java/com/aflokkat/repository/ReportRepository.java` | countGroupByStatus() and countGroupByGrade() | VERIFIED | Both @Query methods at lines 22 and 30 |
| `src/main/java/com/aflokkat/controller/AdminController.java` | GET /api/reports/stats, ADMIN only | VERIFIED | @PreAuthorize("hasRole('ADMIN')") at line 35; returns {byStatus, byGrade} |
| `src/test/java/com/aflokkat/controller/AdminControllerTest.java` | 3 unit tests | VERIFIED | 3 tests covering response shape, missing enum defaulting, no data leak |
| `src/main/java/com/aflokkat/config/SecurityConfig.java` | /api/reports/stats (ADMIN) before /api/reports/** (CONTROLLER) | VERIFIED | Line 64 before line 66; no /admin server-side guard (intentional) |
| `src/main/java/com/aflokkat/controller/ViewController.java` | GET /admin route | VERIFIED | @GetMapping("/admin") returns "admin" at line 69 |
| `src/main/resources/templates/admin.html` | Three-card admin page | VERIFIED | sync-btn, rebuild-btn, at-risk/export.csv, api/reports/stats, ROLE_ADMIN guard all present |
| `src/main/resources/templates/fragments/navbar.html` | nav-admin hidden by default, shown for ROLE_ADMIN only | VERIFIED | display:none on anchor, shown only in ROLE_ADMIN block |
| `src/test/java/com/aflokkat/config/SecurityConfigTest.java` | Updated tests matching implementation | FAILED | 2 tests still assert server-side /admin guard behavior that was intentionally removed |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| AuthService.register() | adminSignupCode field | @Value("${admin.signup.code:#{null}}") | VERIFIED | Line 37 of AuthService.java |
| docker-compose.yml ADMIN_SIGNUP_CODE | application.properties admin.signup.code | Spring Boot relaxed binding | VERIFIED | docker-compose.yml line 17: ADMIN_SIGNUP_CODE: "" |
| AdminController.getStats() | ReportRepository.countGroupByStatus/countGroupByGrade | @Autowired ReportRepository | VERIFIED | Lines 43-46 and 52-55 of AdminController.java |
| admin.html sync-btn onclick | POST /api/restaurants/refresh | fetchWithAuth (Authorization from localStorage) | VERIFIED | admin.html line 159: fetchWithAuth('/api/restaurants/refresh', { method: 'POST' }) |
| admin.html setInterval poll | GET /api/restaurants/sync-status | fetchWithAuth, 2000ms | VERIFIED | admin.html line 111-129: setInterval(fn, 2000) → fetchWithAuth('/api/restaurants/sync-status') |
| admin.html stats-card JS | GET /api/reports/stats | fetchWithAuth on DOMContentLoaded | VERIFIED | admin.html line 212: fetchWithAuth('/api/reports/stats') |
| navbar.html JS | nav-admin element | payload.role === 'ROLE_ADMIN' | VERIFIED | navbar.html lines 76-78 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| AdminController.getStats() | byStatus / byGrade | ReportRepository.countGroupByStatus() / countGroupByGrade() — JPQL GROUP BY queries | Yes — actual DB aggregates | FLOWING |
| admin.html stats card | data.byStatus / data.byGrade | GET /api/reports/stats fetched via fetchWithAuth on DOMContentLoaded | Yes — populated from AdminController which queries PostgreSQL | FLOWING |
| admin.html sync status | data.status, data.upsertedRestaurants | GET /api/restaurants/sync-status fetched on DOMContentLoaded | Yes — RestaurantController reads sync state from SyncService | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| AuthService ROLE_ADMIN branch compiles and contains adminSignupCode | grep "adminSignupCode" AuthService.java | Found at lines 32, 38, 44, 49, 72, 73, 74 | PASS |
| admin.signup.code property configured empty | grep "admin.signup.code" application.properties | Found at line 62: admin.signup.code= | PASS |
| ADMIN_SIGNUP_CODE in docker-compose | grep "ADMIN_SIGNUP_CODE" docker-compose.yml | Found at line 17: ADMIN_SIGNUP_CODE: "" | PASS |
| antMatcher ordering: stats before wildcard | grep -n "api/reports/stats\|api/reports/\*\*" SecurityConfig.java | stats at line 64, wildcard at line 66 | PASS |
| SecurityConfigTest admin tests | mvn test -Dtest=SecurityConfigTest | 2 FAILURES: admin_redirectsToLogin_whenUnauthenticated (200 != 3xx), admin_returns403_forController (200 != 403) | FAIL |
| admin.html has all required elements | grep sync-btn/rebuild-btn/ROLE_ADMIN/api/reports/stats admin.html | All 8 patterns found | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|---------|
| ADM-01 | 10-01, 10-03 | /admin page (ADMIN role) with sync status, Sync Now button, Rebuild Cache button | VERIFIED | AuthService ROLE_ADMIN path; DataSeeder admin_test; ViewController /admin; admin.html sync controls; navbar ROLE_ADMIN link |
| ADM-02 | 10-03 | Export At-Risk CSV button triggers /api/inspection/at-risk/export.csv download | VERIFIED | admin.html line 54: window.location.href='/api/inspection/at-risk/export.csv' |
| ADM-03 | 10-02, 10-03 | Aggregate report stats (count by status/grade) across all controllers; no individual report exposure | VERIFIED | ReportRepository GROUP BY queries; AdminController {byStatus, byGrade} response; admin.html stats card; SecurityConfig /api/reports/stats ADMIN gate |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| src/test/java/com/aflokkat/config/SecurityConfigTest.java | 148 | Comment "// /admin is protected server-side: ROLE_ADMIN only" — contradicts current implementation | Warning | Misleading documentation |
| src/test/java/com/aflokkat/config/SecurityConfigTest.java | 151-155 | admin_redirectsToLogin_whenUnauthenticated asserts 3xx — wrong for anyRequest().permitAll() | Blocker | Test fails, breaking mvn test |
| src/test/java/com/aflokkat/config/SecurityConfigTest.java | 157-165 | admin_returns403_forController asserts 403 — wrong for anyRequest().permitAll() | Blocker | Test fails, breaking mvn test |

### Human Verification Required

#### 1. Admin page renders for ROLE_ADMIN user

**Test:** Login as admin_test / Test1234! at POST /api/auth/login. Copy accessToken. Open browser at http://localhost:8080/login, log in, navigate to http://localhost:8080/admin.
**Expected:** Page loads (not redirected). Three cards visible: Sync NYC Data, At-Risk Export, Report Statistics. Navbar shows Admin link.
**Why human:** Client-side IIFE guard relies on localStorage token — cannot test without live browser session.

#### 2. ROLE_ADMIN IIFE guard redirects non-admin users

**Test:** Log in as controller_test / Test1234!. Navigate to http://localhost:8080/admin.
**Expected:** Page immediately redirects to / (client-side IIFE fires). No server-side 403.
**Why human:** JavaScript redirect behavior requires live browser.

#### 3. Sync polling live behavior

**Test:** As admin_test, click Sync NYC Data. Observe UI state changes.
**Expected:** Button disabled, spinner visible, status text changes. After completion, inline result line appears green. Disappears after ~10 seconds.
**Why human:** Requires running app + NYC API reachable, with real-time observation.

#### 4. CSV download triggers file download

**Test:** As admin_test, click Download At-Risk CSV.
**Expected:** Browser initiates file download (Content-Disposition: attachment). File does not open in new tab.
**Why human:** window.location.href behavior and Content-Disposition response header enforcement requires live browser.

### Gaps Summary

One gap found, with two failing tests sharing the same root cause.

**Root cause:** Commit 2287303 intentionally removed the `.antMatchers("/admin").hasRole("ADMIN")` server-side guard from SecurityConfig, because JWT-based auth stores tokens in localStorage (not cookies), so browsers never send the Authorization header on page navigation. The fix was correct for the application architecture.

However, SecurityConfigTest was updated with new `/admin` test methods that still assert the behavior of the old server-side guard (3xx redirect for unauthenticated, 403 for CONTROLLER). These two tests should instead assert 200 (matching the `/dashboard` pattern already documented in the same test file — see lines 122-136 and the comment at line 117).

**Fix required:**
- `SecurityConfigTest.admin_redirectsToLogin_whenUnauthenticated` → rename to `admin_isAccessible_whenUnauthenticated`, change expectation to `status().isOk()`
- `SecurityConfigTest.admin_returns403_forController` → rename to `admin_isAccessible_forController`, change expectation to `status().isOk()`
- Update comment at line 148 to state client-side IIFE guard only (same as /dashboard)

This is a 3-line fix. All other phase 10 goals are fully achieved.

---

_Verified: 2026-04-11T13:20:00Z_
_Verifier: Claude (gsd-verifier)_
