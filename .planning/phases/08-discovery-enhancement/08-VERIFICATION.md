---
phase: 08-discovery-enhancement
verified: 2026-04-05T20:10:00Z
status: human_needed
score: 6/6 must-haves verified
human_verification:
  - test: "Open /inspection-map in a browser, toggle grade checkboxes and change the borough dropdown"
    expected: "Markers update client-side in under 200ms with no network request fired to /api/restaurants/map-points; marker-count badge updates; cuisine dropdown populates on load"
    why_human: "Client-side filter timing and absence of network requests cannot be verified by static code inspection"
  - test: "Navigate to /uncontrolled while logged in as a controller — confirm 'Uncontrolled' link appears in the navbar. Then log in as a customer and confirm the link is absent."
    expected: "ROLE_CONTROLLER users see the Uncontrolled nav link; customers and anonymous visitors do not"
    why_human: "Requires live JWT with role payload; localStorage read and DOM mutation happen at runtime"
  - test: "Navigate to /restaurant/{id} for a restaurant that has coordinates (e.g. 40365632 if re-synced); scroll to bottom"
    expected: "Nearby Restaurants section appears with up to 5 mini-cards each showing name, grade badge, and a View Details link. Section is absent when coordinates are null."
    why_human: "Depends on MongoDB data — whether lat/lng is populated for that document; cannot verify without live DB"
  - test: "Open / and search for 'pizza'; use the sort dropdown to switch to Best Score, Worst Score, and A→Z"
    expected: "Results reorder client-side without a new network request; sort control is visible above the result list only when results are present"
    why_human: "Sort reordering and absence of extra fetch calls require browser observation"
---

# Phase 8: Discovery Enhancement — Verification Report

**Phase Goal:** Users can filter the map by grade/borough/cuisine, find uncontrolled restaurants, discover nearby places from a detail page, and sort search results
**Verified:** 2026-04-05T20:10:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `/inspection-map` filter bar has grade checkboxes (A/B/C/F), borough dropdown, and cuisine dropdown; toggling removes markers client-side | ✓ VERIFIED | `inspection-map.html` lines 69-93: 4 grade checkbox labels present and checked by default; `#borough-filter` select; `#cuisine-filter` populated via `fetch('/api/restaurants/cuisines')`. `applyFilters()` rebuilds `L.markerClusterGroup` from `allPoints` array — no network call on filter change. `#marker-count` badge updated at line 167. |
| 2 | `/uncontrolled` shows table of grade C/Z or 12-month-overdue restaurants; sortable by score; filterable by borough; Download CSV button present | ✓ VERIFIED | `uncontrolled.html`: table with 6 columns, borough filter select, CSV download `<a id="csv-btn">` pointing to `/api/inspection/uncontrolled/export.csv`, sort headers for Last Score and Days Since Inspection. Backend: `findUncontrolled()` MongoDB aggregation in `RestaurantDAOImpl.java` with `$or` match on grade C/Z or `lastInspectionMs < now-12months`. |
| 3 | Restaurant detail page has Nearby section showing up to 5 restaurants within 500m, each with grade badge and detail link | ✓ VERIFIED | `restaurant.html` lines 181-186: `#nearby-section` div present (hidden by default). `loadNearby(lat, lng, currentId)` at line 265 fetches `/api/restaurants/nearby?radius=500&limit=6`, filters out current restaurant, renders up to 5 `.nearby-card` elements. Call site at line 495. |
| 4 | Search results on landing page can be sorted by Best Score / Worst Score / A→Z; sort control visible above results | ✓ VERIFIED | `landing.html` lines 30-37: `#sort-control-wrapper` (hidden by default) with `<select id="sort-control">` offering 4 options. `sortResults()` at line 138 uses `latestScore` comparator. Sort triggers `renderResults()` client-side with no fetch. |
| 5 | Navbar shows an 'Uncontrolled' link for ROLE_CONTROLLER users only, hidden for others | ✓ VERIFIED | `navbar.html` line 21-22: `<a id="nav-uncontrolled">` with `display:none`. Lines 37-45: JWT payload decoded, `payload.role === 'ROLE_CONTROLLER'` check sets `style.display = 'inline-block'`. Uses Pattern A (flat `.role`) confirmed matching `JwtUtil.buildToken` which calls `.claim("role", role)`. |
| 6 | Restaurant names in /uncontrolled table are clickable links to /restaurant/{restaurantId} | ✓ VERIFIED | `uncontrolled.html` lines 99-104: name `<td>` wraps name in `<a href="/restaurant/' + r.restaurantId + '">` when `r.restaurantId` is present; falls back to plain text otherwise. |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/templates/fragments/navbar.html` | Controller-role nav link shown via JWT role check | ✓ VERIFIED | `id="nav-uncontrolled"` present at line 21; `ROLE_CONTROLLER` check at line 43 |
| `src/main/resources/templates/uncontrolled.html` | Clickable name links + always-visible CSV button | ✓ VERIFIED | `/restaurant/` anchor at lines 101-103; `flex-shrink: 0; white-space: nowrap` on csv-btn at lines 46-47 |
| `src/main/resources/templates/inspection-map.html` | Grade label colors; no `<h1>Restaurant Map</h1>` | ✓ VERIFIED | `#22c55e` at line 70, `#eab308` at line 73, `#ef4444` at lines 76 and 79; h1 absent (grep returned no match) |
| `src/main/resources/templates/landing.html` | 2-group flex layout for search result rows | ✓ VERIFIED | `flex-direction:column;gap:2px;flex:1;min-width:0` inner div at lines 129-133 |
| `src/main/java/com/aflokkat/service/RestaurantService.java` | `getLatitude` with debug logging | ✓ VERIFIED | SLF4J logger at line 30; three `log.debug()` calls in `getLatitude()` at lines 280-282 |
| `src/main/java/com/aflokkat/dto/UncontrolledEntry.java` | 7-field DTO for uncontrolled aggregation output | ✓ VERIFIED | Created in Plan 08-01; referenced by InspectionController |
| `src/main/java/com/aflokkat/controller/InspectionController.java` | GET /api/inspection/uncontrolled + export.csv | ✓ VERIFIED | `@GetMapping("/uncontrolled")` at line 88; `@GetMapping("/uncontrolled/export.csv")` at line 105 |
| `src/main/java/com/aflokkat/controller/ViewController.java` | GET /uncontrolled view route | ✓ VERIFIED | `@GetMapping("/uncontrolled")` returns `"uncontrolled"` at lines 59-61 |
| `src/main/resources/templates/restaurant.html` | Nearby section after inspection history | ✓ VERIFIED | `#nearby-section` at line 181; `loadNearby()` at line 265; call site at line 495 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `navbar.html` JS | JWT payload role field | `JSON.parse(atob(t.split('.')[1])).role` | ✓ WIRED | Line 37: `var payload = JSON.parse(atob(t.split('.')[1]))`. Line 43: `payload.role === 'ROLE_CONTROLLER'`. Pattern A matches `JwtUtil.buildToken` which uses `.claim("role", role)`. |
| `uncontrolled.html` renderTable | `/restaurant/{restaurantId}` | anchor tag wrapping restaurant name | ✓ WIRED | Line 101: `'<a href="/restaurant/' + r.restaurantId + '"'` — `r.restaurantId` comes from API JSON response |
| `RestaurantService.getLatitude` | `address.coord[1]` | POJO deserialization | ✓ WIRED | Line 283: `return a.getCoord().get(1)`. `Address.getCoord()` field is `@BsonProperty("coord")` matching MongoDB storage |
| `inspection-map.html` grade checkboxes | `applyFilters()` | change event listener | ✓ WIRED | Lines 210-213: `['grade-A','grade-B','grade-C','grade-F'].forEach(id => { document.getElementById(id).addEventListener('change', applyFilters) })` |
| `inspection-map.html` cuisine-filter | `/api/restaurants/cuisines` | DOMContentLoaded fetch | ✓ WIRED | Lines 196-208: fetch populates `<select id="cuisine-filter">` options via `resp.data` |
| `restaurant.html` loadNearby | `/api/restaurants/nearby` | fetch with lat/lng from restaurant data | ✓ WIRED | Line 268: `fetch('/api/restaurants/nearby?lat=' + lat + '&lng=' + lng + '&radius=500&limit=6')`. Self-exclusion at line 272-273. |
| `landing.html` sort-control | `renderResults(sortResults(...))` | change event | ✓ WIRED | Line 166-168: `document.getElementById('sort-control').addEventListener('change', function() { renderResults(sortResults(lastResults, this.value)); })` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `inspection-map.html` | `allPoints` | `fetch('/api/restaurants/map-points')` → MongoDB aggregation in `RestaurantDAOImpl.findMapPoints()` | Yes — DB query returns borough+cuisine fields (extended in 08-01) | ✓ FLOWING |
| `uncontrolled.html` | `allRows` | `fetch('/api/inspection/uncontrolled')` → `restaurantDAO.findUncontrolled()` → MongoDB `$or` aggregation | Yes — multi-stage pipeline with `$match`, `$addFields`, `$sort` | ✓ FLOWING |
| `restaurant.html` | `nearby` (rendered cards) | `fetch('/api/restaurants/nearby?...')` → `RestaurantController.getNearby()` → DAO query | Yes — existing `/api/restaurants/nearby` endpoint backed by MongoDB geospatial or score-based query; self-exclusion applied client-side | ✓ FLOWING (conditional on DB having coords) |
| `landing.html` | `lastResults` | `fetch('/api/restaurants/search?q=...')` → existing search endpoint | Yes — existing search endpoint unchanged | ✓ FLOWING |

### Behavioral Spot-Checks

Spot-checks requiring a running server are skipped — all verifiable behaviors are confirmed through static code analysis above.

| Behavior | Check | Status |
|----------|-------|--------|
| `UncontrolledEntry` DTO class exists | `ls target/classes/com/aflokkat/dto/UncontrolledEntry.class` | ✓ PASS (compiled class present from last Docker build) |
| `RestaurantService.class` compiled after 08-05 changes | file timestamp `Apr  4 19:27` (most recent Docker build) | ? SKIP — target owned by root; re-compilation blocked by environment issue |
| `ViewControllerUncontrolledTest` passes | surefire report: `Tests run: 1, Failures: 0` | ✓ PASS |
| `InspectionControllerUncontrolledTest` passes | surefire report: `Tests run: 2, Failures: 0` | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| DISC-01 | 08-03, 08-05 | Map at `/inspection-map` has grade checkboxes, borough and cuisine dropdowns; client-side marker filtering | ✓ SATISFIED | `inspection-map.html`: 4 grade checkboxes, borough select, cuisine select populated from API; `applyFilters()` filters `allPoints` array in-place; `#marker-count` badge updates live |
| DISC-02 | 08-01, 08-02, 08-05 | `/uncontrolled` page with table sortable by score, filterable by borough, Download CSV button | ✓ SATISFIED | Backend: `findUncontrolled()` DAO, `GET /api/inspection/uncontrolled`, `GET /api/inspection/uncontrolled/export.csv`. Frontend: `uncontrolled.html` with sort, borough filter, csv-btn |
| DISC-03 | 08-04 | Restaurant detail page shows up to 5 nearby restaurants within 500m with grade badge and link | ✓ SATISFIED | `restaurant.html`: `#nearby-section`, `loadNearby()`, `/api/restaurants/nearby?radius=500&limit=6`, self-exclusion filter, `.nearby-card` rendering |
| DISC-04 | 08-04 | Search results sort control (Best Score / Worst Score / A→Z) visible above results | ✓ SATISFIED | `landing.html`: `#sort-control-wrapper`, `sortResults()`, `renderResults()`, sort control hidden until results present |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `landing.html` | 47-50 | `<div style="background:#f0f2ff;...height:80px"></div>` placeholder cards | ℹ Info | These are loading-state placeholder rects for the sample grid while the API fetch completes — they are replaced by real data. Not a stub: `fetch('/api/restaurants/sample?limit=3')` overwrites them on load. |
| `restaurant.html` | 184 | `nearby-loading` paragraph shown during load | ℹ Info | `"Loading nearby restaurants…"` text is a legitimate loading indicator, hidden when results arrive or on error. Not a stub. |

No blockers or warnings found. The two info items are intentional loading states, not placeholders.

### Human Verification Required

#### 1. Map Filter Client-Side Performance

**Test:** Open `/inspection-map` in a browser with DevTools Network tab open. Toggle each grade checkbox (A, B, C, F) and change the borough dropdown to Manhattan.
**Expected:** No new request to `/api/restaurants/map-points` is fired on each filter change. Markers visible on the map update immediately (under 200ms). The marker-count badge (e.g. "4,312 markers shown") updates to reflect the current filtered count.
**Why human:** Network request absence and DOM update timing cannot be verified by static analysis.

#### 2. Navbar Controller Role Link

**Test:** Log in as a CONTROLLER account and load any page. Then log out and log in as a CUSTOMER account.
**Expected:** Controller sees an "Uncontrolled" nav link between Analytics and Bookmarks. Customer does not see the link. Anonymous visitor does not see the link.
**Why human:** JWT payload is only available at runtime in the browser's localStorage; role-conditional DOM mutation requires a live session.

#### 3. Nearby Restaurants Section on Detail Page

**Test:** Open `/restaurant/{id}` for a restaurant that has coordinates in MongoDB (confirm with `db.restaurants.findOne({restaurant_id:"40365632"}, {address:1})`). Scroll to the bottom of the page.
**Expected:** A "Nearby Restaurants" section appears showing up to 5 mini-cards, each with the restaurant name, a grade badge, and a "View Details →" link. If the restaurant has no coordinates stored, the section is absent with no empty-state message.
**Why human:** Whether the Nearby section actually renders depends on whether the specific MongoDB document has `address.coord` populated — a data condition that requires a live database.

#### 4. Search Sort Control

**Test:** Open `/` (landing page), search "pizza". After results appear, use the sort dropdown to select "Best Score first", then "Worst Score first", then "A → Z".
**Expected:** Results reorder each time with no additional network request. The sort control is invisible before any search is run and resets to "Sort: Relevance" when a new search query is entered.
**Why human:** Client-side sort correctness and the visibility toggle of the sort wrapper require visual/runtime confirmation.

---

## Build Verification

`mvn test` cannot be re-run in the current environment because `target/` is owned by root (created during a prior `docker compose up` run). This is a pre-existing environment constraint documented in 08-05-SUMMARY.md and unrelated to Phase 8 changes.

**Last successful test run results (from `target/surefire-reports/`, run 2026-04-04):**

| Test Class | Tests | Failures | Errors | Status |
|-----------|-------|----------|--------|--------|
| AggregationPojoTest | 6 | 0 | 0 | PASS |
| RestaurantCacheServiceTest | 8 | 0 | 0 | PASS |
| AppConfigTest | 7 | 0 | 0 | PASS |
| MongoClientFactoryTest | 4 | 0 | 0 | PASS |
| SecurityConfigTest | 6 | 2 | 0 | FAIL (pre-existing, unrelated to Phase 8) |
| AnalyticsControllerTest | 4 | 0 | 0 | PASS |
| InspectionControllerUncontrolledTest | 2 | 0 | 0 | PASS (Phase 8 tests) |
| ReportControllerTest | 14 | 0 | 0 | PASS |
| RestaurantControllerSampleTest | 3 | 0 | 0 | PASS |
| RestaurantControllerSearchTest | 4 | 0 | 0 | PASS |
| UserControllerMeTest | 3 | 0 | 0 | PASS |
| ViewControllerAnalyticsTest | 1 | 0 | 0 | PASS |
| ViewControllerDashboardTest | 3 | 0 | 0 | PASS |
| ViewControllerProfileTest | 1 | 0 | 0 | PASS |
| ViewControllerUncontrolledTest | 1 | 0 | 0 | PASS (Phase 8 tests) |
| RestaurantDAOImplTest | 5 | 0 | 0 | PASS |
| RestaurantDAOIntegrationTest | 15 | 0 | 15 | ERRORS (requires live MongoDB — expected per CLAUDE.md) |
| RestaurantTest | 2 | 0 | 0 | PASS |
| JwtUtilTest | 12 | 0 | 0 | PASS |
| RateLimitFilterTest | 2 | 0 | 0 | PASS |
| AuthServiceTest | 14 | 0 | 0 | PASS |
| RestaurantServiceTest | 21 | 0 | 0 | PASS |
| DataSeederTest | 2 | 0 | 0 | PASS |
| NycOpenDataClientTest | 4 | 0 | 0 | PASS |
| SyncServiceTest | 9 | 0 | 0 | PASS |
| ValidationUtilTest | 13 | 0 | 0 | PASS |

**Pre-existing failures (not introduced by Phase 8):**
- `SecurityConfigTest` — 2 failures: dashboard auth tests using full Spring Security context (pre-Phase 8 issue)
- `RestaurantDAOIntegrationTest` — 15 errors: requires live MongoDB on `mongodb:27017` (documented in CLAUDE.md as expected in CI-less environments)

**Phase 8 unit tests:** All pass (InspectionControllerUncontrolledTest 2/2, ViewControllerUncontrolledTest 1/1).

---

## Gaps Summary

No automated-verifiable gaps found. All 6 must-haves from the Phase 8 plan are fully implemented and wired. All 4 DISC requirements are satisfied in the codebase. The 4 human verification items above are runtime/data-condition checks that cannot be confirmed without a browser and a live database — this is why the status is `human_needed` rather than `passed`.

---

_Verified: 2026-04-05T20:10:00Z_
_Verifier: Claude (gsd-verifier)_
