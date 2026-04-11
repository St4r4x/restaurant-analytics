---
phase: 07-homepage-navigation
verified: 2026-04-11T14:00:00Z
status: human_needed
score: 4/4 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Open http://localhost:8080/ in a browser with no token in localStorage. Confirm the landing page appears (not a dashboard)."
    expected: "landing.html renders: hero heading, KPI stat strip (totalRestaurants and percentGradeA populated after ~1s), a Search Restaurants input, and a 3-column Discover Restaurants grid with real restaurant cards."
    why_human: "ViewController routing branch (auth == null -> landing) depends on Spring Security parsing the request in a real HTTP context. The JS-driven KPI and sample grid fetches also require a running app and a populated MongoDB."
  - test: "Log in as a CUSTOMER account (e.g. customer_test / Test1234!) and navigate to http://localhost:8080/."
    expected: "index.html personalised dashboard renders: Strip A Your Bookmarks shows any saved cards (or empty-state message), Strip B Nearby is hidden by default and only appears if geolocation permission granted, Strip C NYC Hygiene Overview shows 4 KPI tiles with real values."
    why_human: "Strip B (geolocation) is display:none by default and revealed only if navigator.geolocation resolves and the API returns results — cannot verify without a live browser session."
  - test: "Log in as a CONTROLLER account (e.g. controller_test / Test1234!) and navigate to http://localhost:8080/."
    expected: "Browser redirects to /dashboard (ViewController returns redirect:/dashboard for ROLE_CONTROLLER). Dashboard.html renders with the controller report list."
    why_human: "Server-side redirect depends on Spring Security Principal populated in a real HTTP session."
  - test: "On any page, observe the navbar auth area (top-right corner) when logged out vs logged in."
    expected: "Logged out: Sign In button only. Logged in: username link (href /profile) and Sign Out button. CONTROLLER role also shows Dashboard and Uncontrolled links. ROLE_ADMIN shows Admin link."
    why_human: "Navbar auth state is entirely JS-driven (atob JWT decode from localStorage). Requires live browser with a valid token in localStorage."
  - test: "Navigate to http://localhost:8080/profile as a logged-in CUSTOMER."
    expected: "Profile card shows username, email, green CUSTOMER role badge, bookmark count stat. Bookmarks section below shows up to 3 mini-cards (or empty-state message). No report count stat visible."
    why_human: "bookmarkCount and reportCount data comes from /api/users/me which requires a real authenticated call to a running app with a PostgreSQL connection."
  - test: "Navigate to http://localhost:8080/profile as a logged-in CONTROLLER."
    expected: "Profile card shows username, email, orange CONTROLLER role badge, both bookmark count AND report count stats."
    why_human: "reportCount conditional rendering (shown only when d.reportCount != null) requires a live session where the JWT role is ROLE_CONTROLLER and /api/users/me returns a non-null reportCount."
---

# Phase 7: Homepage Navigation — Verification Report

**Phase Goal:** Non-authenticated visitors land on a proper public homepage; authenticated users see a personalised dashboard; a consistent top navbar links all sections of the app.
**Verified:** 2026-04-11T14:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | UX-01: Non-authenticated visitors see a landing page with city-wide stats, a search CTA, and 3 sample restaurants — not the full dashboard | VERIFIED | `ViewController.index()` returns `"landing"` when `auth == null`. `landing.html` fetches `/api/analytics/kpis` for hero stat strip, has `#search-input` CTA with 300ms debounced fetch, and fetches `/api/restaurants/sample?limit=3` into a 3-column `#sample-grid`. |
| 2 | UX-02: Authenticated users see a personalised dashboard on `/`: recent bookmarks, nearby restaurants (if geolocation allowed), and a summary stats strip | VERIFIED | `ViewController.index()` returns `"index"` for CUSTOMER (or `redirect:/dashboard` for CONTROLLER). `index.html` has Strip A (bookmarks via `fetchWithAuth('/api/users/me/bookmarks')`), Strip B (`#nearby-section` starts `display:none`, shown only if geolocation resolves and API returns results), Strip C (4 KPI tiles via `/api/analytics/kpis`). |
| 3 | UX-03: A persistent top navbar exists on all pages: logo, Search, Map, Analytics links; right side shows Login or username + Logout | VERIFIED | `fragments/navbar.html` exists with `th:fragment="navbar"`. Logo link to `/`, Search → `/`, Map → `/inspection-map`, Analytics → `/analytics` all present. Auth area JS-driven: no token → Sign In button; valid token → `payload.sub` (username) link + Sign Out button. Fragment included via `th:replace="fragments/navbar :: navbar"` in all 8 content templates (landing, index, profile, analytics, dashboard, restaurant, inspection-map, my-bookmarks) plus admin and uncontrolled (10 total). `login.html` intentionally omits it. |
| 4 | UX-04: A `/profile` page shows the logged-in user's username, email, role badge, bookmark count, and (for controllers) report count | VERIFIED | `profile.html` fetches `/api/users/me`, renders `#p-username`, `#p-email`, `#p-role` badge (green for CUSTOMER, orange for CONTROLLER), `bookmarkCount` stat always shown, `reportCount` stat rendered conditionally only when `d.reportCount != null`. `UserController` enriches response at lines 61-67: `bookmarkRepository.countByUserId()` always set; `reportRepository.countByUserId()` only for ROLE_CONTROLLER. Bookmark mini-cards from `/api/users/me/bookmarks` also rendered. |

**Score:** 4/4 must-haves verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/templates/fragments/navbar.html` | Navbar fragment: logo, Search/Map/Analytics links, JS auth area | VERIFIED | `th:fragment="navbar"`, logo, 3 nav pills always visible, `#nav-auth` IIFE renders Sign In or username + Sign Out |
| `src/main/resources/templates/landing.html` | Public homepage: hero KPI strip, search CTA, 3 sample cards | VERIFIED | Fetches `/api/analytics/kpis` → `#hero-stats`; `#search-input` with debounce; fetches `/api/restaurants/sample?limit=3` → `#sample-grid` 3-col grid |
| `src/main/resources/templates/index.html` | Personalised CUSTOMER dashboard: bookmarks strip, nearby section, KPI tiles | VERIFIED | Strip A `#bookmarks-grid` via `fetchWithAuth('/api/users/me/bookmarks')`; Strip B `#nearby-section` display:none + geolocation check; Strip C `#kpi-grid` 4 tiles via `/api/analytics/kpis` |
| `src/main/resources/templates/profile.html` | Profile page: username, email, role badge, bookmark count, conditional report count | VERIFIED | All 5 data points rendered from `/api/users/me`. `reportCount` block guarded by `if (d.reportCount != null)`. Bookmark cards section below profile card. |
| `src/main/java/com/aflokkat/controller/ViewController.java` | Routes: null auth → landing, CUSTOMER → index, CONTROLLER → /dashboard, /profile route | VERIFIED | `auth == null` → `"landing"`; `ROLE_CONTROLLER` authority → `redirect:/dashboard`; fallthrough → `"index"`. `@GetMapping("/profile")` returns `"profile"`. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ViewController.index()` | `landing.html` | `return "landing"` when `auth == null` | VERIFIED | Line 15-17 of ViewController.java |
| `ViewController.index()` | `redirect:/dashboard` | `ROLE_CONTROLLER` authority check | VERIFIED | Lines 18-21 of ViewController.java |
| `ViewController.index()` | `index.html` | fallthrough `return "index"` | VERIFIED | Line 22 of ViewController.java |
| `landing.html` | `/api/analytics/kpis` | `fetch('/api/analytics/kpis')` in inline script | VERIFIED | landing.html line 84 — response populates `#hero-stats` text |
| `landing.html` | `/api/restaurants/sample?limit=3` | `fetch('/api/restaurants/sample?limit=3')` | VERIFIED | landing.html line 98 — response renders into `#sample-grid` |
| `index.html` | `/api/users/me/bookmarks` | `fetchWithAuth('/api/users/me/bookmarks')` | VERIFIED | index.html line 109 — results rendered via `renderRestaurantCards('bookmarks-grid', ...)` |
| `index.html` | `/api/restaurants/nearby` | `navigator.geolocation.getCurrentPosition` + `fetchWithAuth` | VERIFIED | index.html lines 123-138 — `#nearby-section` shown only on success with non-empty results |
| `index.html` | `/api/analytics/kpis` | `fetch('/api/analytics/kpis')` | VERIFIED | index.html line 142 — populates `#kpi-total`, `#kpi-pct`, `#kpi-avg`, `#kpi-risk` |
| `profile.html` | `/api/users/me` | `fetchWithAuth('/api/users/me')` | VERIFIED | profile.html line 73 — populates username, email, role badge, bookmark/report counts |
| `profile.html` | `/api/users/me/bookmarks` | `fetchWithAuth('/api/users/me/bookmarks')` | VERIFIED | profile.html line 113 — renders up to 3 mini-cards in `#bookmarks-grid` |
| `UserController /api/users/me` | `bookmarkRepository.countByUserId()` | Spring JPA derived query | VERIFIED | UserController.java lines 61-62 |
| `UserController /api/users/me` | `reportRepository.countByUserId()` | conditional on ROLE_CONTROLLER | VERIFIED | UserController.java lines 63-65 |
| `navbar.html IIFE` | `localStorage.getItem('accessToken')` | `atob(t.split('.')[1])` JWT decode | VERIFIED | navbar.html lines 60-90 — renders Sign In or `payload.sub` + Sign Out |
| All 10 content templates | `fragments/navbar :: navbar` | `th:replace` directive | VERIFIED | Confirmed in: landing, index, profile, analytics, dashboard, restaurant, inspection-map, my-bookmarks, admin, uncontrolled |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `landing.html` hero stat strip | `res.totalRestaurants`, `res.percentGradeA` | GET `/api/analytics/kpis` | Yes — KPI endpoint queries MongoDB aggregation pipeline | FLOWING |
| `landing.html` sample grid | `res.data` (3 restaurants) | GET `/api/restaurants/sample?limit=3` | Yes — DAO uses MongoDB `$sample` aggregation | FLOWING |
| `index.html` bookmarks strip | `res.data` (bookmark list) | GET `/api/users/me/bookmarks` (authenticated) | Yes — `BookmarkRepository.findByUserId()` queries PostgreSQL | FLOWING |
| `index.html` KPI tiles | `res.totalRestaurants`, `res.percentGradeA`, `res.avgScore`, `res.atRiskCount` | GET `/api/analytics/kpis` | Yes — MongoDB aggregation | FLOWING |
| `profile.html` stats | `d.bookmarkCount`, `d.reportCount` | GET `/api/users/me` | Yes — `bookmarkRepository.countByUserId()` and `reportRepository.countByUserId()` query PostgreSQL | FLOWING |
| `profile.html` bookmark cards | `res.data` (up to 3 cards) | GET `/api/users/me/bookmarks` | Yes — same bookmark repository | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| ViewController returns "landing" for null auth | `grep -A8 "public String index" ViewController.java` | `auth == null` → `return "landing"` at lines 15-17 | PASS |
| ViewController returns "index" for CUSTOMER | `grep -A8 "public String index" ViewController.java` | fallthrough `return "index"` at line 22 | PASS |
| landing.html calls `/api/analytics/kpis` | `grep "api/analytics/kpis" landing.html` | Found at line 84 | PASS |
| landing.html calls `/api/restaurants/sample?limit=3` | `grep "api/restaurants/sample" landing.html` | Found at line 98 | PASS |
| index.html nearby section starts hidden | `grep "display:none" index.html` | `#nearby-section` has `display:none` inline style | PASS |
| Navbar fragment included in all 8 content templates | `grep -r "fragments/navbar :: navbar" templates/` | 10 files found (8 content pages + admin + uncontrolled) | PASS |
| profile.html renders reportCount conditionally | `grep "reportCount" profile.html` | Guarded by `if (d.reportCount != null)` at line 99 | PASS |
| UserController enriches /api/users/me with counts | `grep "bookmarkCount\|reportCount" UserController.java` | Both set at lines 61-67 | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|---------|
| UX-01 | 07-02 | Non-authenticated visitors see landing page with stats, search CTA, 3 sample restaurants | VERIFIED | ViewController null-auth branch; landing.html sections 1-3 |
| UX-02 | 07-02 | Authenticated CUSTOMER users see personalised dashboard: bookmarks, nearby, KPI strip | VERIFIED | ViewController CUSTOMER branch; index.html 3-strip layout |
| UX-03 | 07-02, 07-03 | Persistent navbar on all pages: logo, Search/Map/Analytics, auth area (Sign In or user+logout) | VERIFIED | navbar.html fragment; th:replace in all 10 content templates |
| UX-04 | 07-02, 07-04 | /profile page: username, email, role badge, bookmark count, report count for controllers | VERIFIED | profile.html; UserController enrichment; role badge colors; conditional reportCount |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `src/main/resources/templates/landing.html` | 21 | Comment `<!-- KPI stat strip: 2 live values from /api/analytics/kpi -->` uses old URL path `kpi` (singular) — the actual fetch on line 84 correctly uses `/api/analytics/kpis` (plural) | Info | Comment only — no functional impact |

No functional stub patterns found. All fetch calls reference real endpoints. All data variables are populated from API responses. The nearby section's `display:none` default is intentional (revealed by geolocation success), not a stub.

### Human Verification Required

#### 1. Anonymous visitor lands on landing page (not dashboard)

**Test:** Open `http://localhost:8080/` in a private/incognito browser window (no localStorage token). Observe the rendered page.
**Expected:** `landing.html` renders — hero heading "NYC Restaurant Inspector", KPI strip populates with real totalRestaurants and percentGradeA figures, search input visible, 3-column sample grid loads real restaurant cards after skeleton placeholders clear.
**Why human:** Spring Security `Authentication auth` injection and the `auth == null` branch require a real HTTP context. KPI strip and sample grid data require a running app with populated MongoDB.

#### 2. CUSTOMER authenticated user sees personalised dashboard

**Test:** Log in as a CUSTOMER account at `POST /api/auth/login`. Store the accessToken in localStorage. Navigate to `http://localhost:8080/`.
**Expected:** `index.html` dashboard renders. Strip A (Your Bookmarks) shows saved restaurants or empty-state message. Strip B (Nearby) is hidden unless geolocation is granted and returns results. Strip C (NYC Hygiene Overview) shows 4 real KPI tiles.
**Why human:** Strip B conditional display requires live geolocation API interaction in a browser. The Strip A empty-state vs populated state depends on actual bookmark data in PostgreSQL.

#### 3. CONTROLLER authenticated user redirects to /dashboard

**Test:** Log in as controller_test / Test1234!. Navigate to `http://localhost:8080/`.
**Expected:** Browser receives a 3xx redirect to `/dashboard`. The controller dashboard (dashboard.html) renders.
**Why human:** Server-side redirect issued by Spring Security `Authentication` containing `ROLE_CONTROLLER` — requires a real running application.

#### 4. Navbar auth area renders correctly in both states

**Test:** (a) Logged out: navigate to any page and observe top-right navbar area. (b) Log in as customer_test, observe same area. (c) Log in as controller_test, check that Dashboard and Uncontrolled links appear.
**Expected:** (a) Sign In button only. (b) Username link (pointing to /profile) + Sign Out button; Bookmarks link visible. (c) Dashboard and Uncontrolled links also visible.
**Why human:** Navbar auth state is entirely JS-driven via JWT atob decode from localStorage — cannot verify rendering without a live browser with a valid token.

#### 5. Profile page shows correct data per role

**Test:** (a) Log in as CUSTOMER, navigate to `/profile`. (b) Log in as CONTROLLER, navigate to `/profile`.
**Expected:** (a) Green CUSTOMER badge, bookmark count stat, no report count stat, bookmark cards or empty-state. (b) Orange CONTROLLER badge, both bookmark count and report count stats visible.
**Why human:** Conditional `reportCount` rendering requires a live call to `/api/users/me` with an authenticated token and a running PostgreSQL connection.

### Gaps Summary

No gaps found. All 4 must-haves are fully implemented and wired.

One informational note: `landing.html` line 21 has a comment referencing `/api/analytics/kpi` (singular) — the actual `fetch()` call on line 84 correctly uses `/api/analytics/kpis` (plural). This is a stale comment with no functional impact.

All automated checks pass. The phase is blocked only on human verification of browser-dependent behaviors: anonymous vs authenticated routing, JS-driven navbar auth state, and geolocation conditional rendering.

---

_Verified: 2026-04-11T14:00:00Z_
_Verifier: Claude (gsd-verifier)_
