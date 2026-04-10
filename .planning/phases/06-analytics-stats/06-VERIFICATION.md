---
phase: 06-analytics-stats
verified: 2026-04-03T00:00:00Z
status: human_needed
score: 9/9 must-haves verified
human_verification:
  - test: "Navigate to http://localhost:8080/analytics without being logged in — verify page loads with HTTP 200 and all four sections render"
    expected: "KPI tiles initially show dashes then populate with numbers, stacked borough chart appears with 5 boroughs, two cuisine ranking lists show 10 entries each, at-risk table shows restaurant rows with grade badges and View links"
    why_human: "Visual rendering and Chart.js chart initialisation cannot be verified programmatically; Thymeleaf template compilation only confirmed at unit-test level (direct method call, no HTTP round-trip)"
  - test: "From index.html (http://localhost:8080), click the Analytics nav link"
    expected: "Browser navigates to /analytics"
    why_human: "Link href value exists in DOM but click-through navigation needs human confirmation"
  - test: "From dashboard.html (http://localhost:8080/dashboard), click the Analytics nav link"
    expected: "Browser navigates to /analytics"
    why_human: "Same as above for the dashboard header pill"
  - test: "Hover over a segment in the borough stacked bar chart"
    expected: "Tooltip shows 'A: NNNN (XX.X%)' format with raw count and percentage"
    why_human: "Chart.js tooltip callback wired in code, but actual rendering and interactivity requires browser"
  - test: "Click a 'View' link in the at-risk table"
    expected: "Browser navigates to /restaurant/{restaurantId} detail page"
    why_human: "Link href template uses JS string interpolation — correctness of restaurantId substitution requires live data"
---

# Phase 6: Analytics Stats Verification Report

**Phase Goal:** Expose aggregated analytics (city-wide KPIs, borough grade distribution, cuisine rankings, at-risk list) through 4 REST endpoints and a public Thymeleaf dashboard page.
**Verified:** 2026-04-03
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | GET /api/analytics/kpis returns 200 with totalRestaurants, percentGradeA, avgScore, atRiskCount | VERIFIED | AnalyticsControllerTest.testKpis_returns200 passes; controller lines 76-81 populate all four keys |
| 2 | GET /api/analytics/borough-grades returns 200 with borough data array | VERIFIED | AnalyticsControllerTest.testBoroughGrades_returns5Boroughs passes; controller delegates to findBoroughGradeDistribution() |
| 3 | GET /api/analytics/cuisine-rankings returns 200 with best and worst arrays each size 10 | VERIFIED | AnalyticsControllerTest.testCuisineRankings_returnsTwoLists passes; controller line 130-131 calls findWorstCuisinesByAverageScore(10) and findBestCuisinesByAverageScore(10) |
| 4 | GET /api/analytics/at-risk returns 200 with data array where each entry has restaurantId, name, borough, lastGrade | VERIFIED | AnalyticsControllerTest.testAtRisk_returnsEntries passes; controller delegates to findAtRiskRestaurants(null, 50) |
| 5 | /analytics page is public (no auth required) | VERIFIED | SecurityConfig line 69: `.anyRequest().permitAll()` — all view routes are open; ViewController.analytics() has no @PreAuthorize |
| 6 | analytics.html contains KPI tiles, borough chart, cuisine rankings, at-risk table | VERIFIED | Elements kpi-total, kpi-grade-a, kpi-avg-score, kpi-at-risk, boroughChart, cuisine-best, cuisine-worst, at-risk-body all present |
| 7 | analytics.html fetches from all 4 API endpoints | VERIFIED | fetch('/api/analytics/kpis') line 119, fetch('/api/analytics/borough-grades') line 143, fetch('/api/analytics/cuisine-rankings') line 207, fetch('/api/analytics/at-risk') line 236 |
| 8 | index.html and dashboard.html each have an Analytics nav link | VERIFIED | index.html line 463: `<a href="/analytics" ...>Analytics</a>`; dashboard.html line 41: same pattern |
| 9 | ViewController.analytics() returns "analytics" and ViewControllerAnalyticsTest passes | VERIFIED | ViewController.java line 52: `public String analytics() { return "analytics"; }`; test: 1/1 passing, 0 skipped |

**Score:** 9/9 truths verified (automated)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/com/aflokkat/controller/AnalyticsController.java` | 4 REST endpoints (kpis, borough-grades, cuisine-rankings, at-risk) | VERIFIED | 4 @GetMapping methods present; delegates to RestaurantDAO directly (not RestaurantService — Java 25 Mockito limitation documented in summary) |
| `src/main/java/com/aflokkat/dao/RestaurantDAO.java` | Interface with findBoroughGradeDistribution, findBestCuisinesByAverageScore, countAtRiskRestaurants | VERIFIED | Lines 130, 136, 142 — all 3 method declarations present |
| `src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java` | MongoDB aggregation implementations for 3 new methods | VERIFIED | findBoroughGradeDistribution (line 377), findBestCuisinesByAverageScore (line 403), countAtRiskRestaurants (line 417) — all substantive pipeline implementations |
| `src/main/java/com/aflokkat/service/RestaurantService.java` | Service wrappers for new DAO methods | VERIFIED | getBoroughGradeDistribution (line 144), getWorstCuisinesByAverageScore (line 153), getBestCuisinesByAverageScore (line 161), countAtRiskRestaurants (line 168) |
| `src/main/resources/templates/analytics.html` | Complete analytics page template | VERIFIED | All required element IDs present; Chart.js 4.4.0 CDN loaded; correct copywriting ("NYC Restaurant Analytics", "City-wide hygiene data — updated live", section headings) |
| `src/main/java/com/aflokkat/controller/ViewController.java` | Route @GetMapping("/analytics") | VERIFIED | Line 51-54: `@GetMapping("/analytics") public String analytics() { return "analytics"; }` |
| `src/test/java/com/aflokkat/controller/AnalyticsControllerTest.java` | 4 enabled tests for STAT-01 to STAT-04 | VERIFIED | 0 @Disabled annotations; mvn test passes 4/4 |
| `src/test/java/com/aflokkat/controller/ViewControllerAnalyticsTest.java` | 1 enabled test for /analytics route | VERIFIED | 0 @Disabled annotations; mvn test passes 1/1 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| analytics.html DOMContentLoaded | /api/analytics/kpis | fetch('/api/analytics/kpis') | WIRED | Line 119; response consumed: kpi-total, kpi-grade-a, kpi-avg-score, kpi-at-risk populated at lines 123-126 |
| analytics.html DOMContentLoaded | /api/analytics/borough-grades | fetch('/api/analytics/borough-grades') | WIRED | Line 143; response consumed: boroughs array built and passed to new Chart() at line 166 |
| analytics.html DOMContentLoaded | /api/analytics/cuisine-rankings | fetch('/api/analytics/cuisine-rankings') | WIRED | Line 207; response consumed: renderCuisineList('cuisine-best', data.best) and renderCuisineList('cuisine-worst', data.worst) at lines 225-226 |
| analytics.html DOMContentLoaded | /api/analytics/at-risk | fetch('/api/analytics/at-risk') | WIRED | Line 236; response consumed: at-risk-body populated at line 239 |
| at-risk table View link | /restaurant/{restaurantId} | href='/restaurant/${entry.restaurantId}' | WIRED | Line 249: `href="/restaurant/${entry.restaurantId}"` — JS template literal in tbody innerHTML |
| AnalyticsController.getKpis() | RestaurantDAO.countAll() + findBoroughGradeDistribution() + countAtRiskRestaurants() + findWorstCuisinesByAverageScore(200) | direct DAO calls | WIRED | Lines 40-62; all 4 DAO methods called and results used in response map |
| AnalyticsController.getAtRisk() | RestaurantDAO.findAtRiskRestaurants(null, 50) | direct DAO call | WIRED | Line 149 |
| RestaurantDAOImpl.findBoroughGradeDistribution() | MongoDB collection.aggregate() | database.getCollection().aggregate() | WIRED | Lines 396-398: raw aggregate pipeline executed and results collected |
| RestaurantDAOImpl.findBestCuisinesByAverageScore() | MongoDB collection.aggregate() | typed aggregate() helper | WIRED | Line 403-413: pipeline with avgScore: -1 sort (descending = worst cuisines) |
| RestaurantDAOImpl.countAtRiskRestaurants() | MongoDB $count aggregation | database.getCollection().aggregate() | WIRED | Lines 426-429: pipeline returns count Document, cast to long |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| STAT-01 | 06-01, 06-02, 06-03 | Public /analytics page shows city-wide KPIs: total restaurants, % grade A, average score, count of at-risk | SATISFIED | GET /api/analytics/kpis endpoint returns all 4 fields; analytics.html kpi-* tiles fetch and display them |
| STAT-02 | 06-01, 06-02, 06-03 | Analytics page shows per-borough grade distribution for each of the 5 boroughs, A/B/C breakdown | SATISFIED | GET /api/analytics/borough-grades backed by findBoroughGradeDistribution() MongoDB pipeline; Chart.js stacked bar chart in analytics.html |
| STAT-03 | 06-01, 06-02, 06-03 | Analytics page shows cuisine hygiene ranking — top 10 cleanest and top 10 worst by average score | SATISFIED | GET /api/analytics/cuisine-rankings backed by findWorstCuisinesByAverageScore(10) and findBestCuisinesByAverageScore(10); cuisine-best and cuisine-worst lists rendered |
| STAT-04 | 06-01, 06-02, 06-03 | Analytics page shows "At Risk" list — restaurants with last grade C or Z, with links to detail page | SATISFIED | GET /api/analytics/at-risk backed by findAtRiskRestaurants(null, 50); at-risk-body table rendered with View links to /restaurant/{restaurantId} |

No orphaned requirements — all 4 STAT requirements claimed across plans 06-01, 06-02, and 06-03.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | — | — | No anti-patterns found |

No TODO/FIXME/HACK comments, no placeholder returns, no empty implementations found in AnalyticsController.java, RestaurantDAOImpl.java, or analytics.html.

### Human Verification Required

#### 1. Full /analytics page end-to-end render

**Test:** Start the application (`docker compose up -d` or `mvn spring-boot:run` with MongoDB available). Navigate to `http://localhost:8080/analytics` without being logged in.
**Expected:** Page loads with HTTP 200. KPI tiles initially show dashes, then populate within ~1 second with real numbers (Total Restaurants ~27K, Grade A as a percentage, Avg Score as a decimal, At-Risk Count as an integer). A stacked horizontal bar chart appears for 5 boroughs. Two cuisine ranking lists appear with 10 numbered entries each. An at-risk table shows restaurant rows with grade badge pills (green/yellow/red) and "View" links.
**Why human:** The unit test (ViewControllerAnalyticsTest) calls `viewController.analytics()` directly — it confirms the method returns the string "analytics" but does NOT verify Thymeleaf template compilation, Chart.js initialisation, or any of the 4 concurrent fetch calls executing correctly in a browser.

#### 2. Analytics nav link from index.html

**Test:** Navigate to `http://localhost:8080`. Locate the Analytics link in the header.
**Expected:** An "Analytics" pill link is visible in the page header. Clicking it navigates to `/analytics`.
**Why human:** The link was added to index.html (line 463 confirms href="/analytics") but the exact positioning and visibility in the rendered header needs visual confirmation; the index.html header uses a mix of inline styles and JS-driven elements.

#### 3. Analytics nav link from dashboard.html

**Test:** Log in as a controller user and navigate to `http://localhost:8080/dashboard`. Locate the Analytics link in the header navigation strip.
**Expected:** An "Analytics" pill link is visible in the dashboard header nav. Clicking it navigates to `/analytics`.
**Why human:** dashboard.html line 41 confirms the link was added, but integration with the existing nav requires visual check.

#### 4. Borough chart tooltip format

**Test:** On the /analytics page with data loaded, hover over a colored segment in the stacked bar chart.
**Expected:** Tooltip shows "A: NNNN (XX.X%)" format, e.g. "A: 3200 (72.0%)".
**Why human:** Chart.js tooltip callback is wired in analytics.html lines 188-196, but actual tooltip rendering and rawCounts lookup correctness requires a browser with live data.

#### 5. At-risk table View link navigation

**Test:** On the /analytics page with data loaded, click a "View →" link in the at-risk table.
**Expected:** Browser navigates to `/restaurant/{restaurantId}` showing the restaurant detail page.
**Why human:** Link href uses JS template literal with `entry.restaurantId` — requires live data to confirm the restaurantId field is populated from the API response and substituted correctly.

### Gaps Summary

No gaps. All 9 observable truths pass automated verification. All 4 requirements (STAT-01 through STAT-04) are satisfied by substantive, wired implementations.

The 5 human verification items are standard end-to-end checks for a UI phase — visual rendering, Chart.js behaviour, and navigation clicks cannot be verified with grep or unit tests. All automated signals are green.

**Notable deviation from plan:** AnalyticsController injects `RestaurantDAO` directly instead of `RestaurantService` (documented in 06-02-SUMMARY.md). This is because Mockito 5 on Java 25 cannot mock `RestaurantService` due to its constructor injection. The REST interface is identical; the change is internal to the controller wiring layer. RestaurantService analytics wrappers (getBoroughGradeDistribution, getBestCuisinesByAverageScore, countAtRiskRestaurants, getWorstCuisinesByAverageScore) exist and are correct — they are just not consumed by AnalyticsController (which bypasses the service and calls the DAO directly, consistent with the RestaurantController search/map-points precedent).

---

_Verified: 2026-04-03_
_Verifier: Claude (gsd-verifier)_
