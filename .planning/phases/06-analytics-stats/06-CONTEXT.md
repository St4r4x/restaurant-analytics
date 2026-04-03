# Phase 6: Analytics & Stats - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning

<domain>
## Phase Boundary

A fully public `/analytics` page giving any visitor a city-wide picture of NYC restaurant
hygiene — 4 KPI tiles, per-borough grade distribution chart, cuisine hygiene rankings
(top 10 cleanest / top 10 worst), and an at-risk restaurant list (last grade C or Z).

No authentication required. No new REST domains. No schema changes.
All backend aggregation methods are either added to RestaurantDAO/Service or adapted from
existing ones (at-risk service method already exists, needs a public endpoint).

</domain>

<decisions>
## Implementation Decisions

### Page structure
- Single long-scroll page — one Thymeleaf template (`analytics.html`), one new ViewController route `GET /analytics`.
- `/analytics` is fully public — no `antMatchers` restriction in SecurityConfig; consistent with `/restaurant/{id}` and `/inspection-map` being public.
- Page follows the purple gradient theme (`#667eea` / `#764ba2`) and `.card` style used across all other templates.

### KPI tiles (STAT-01)
- 4 tiles in a single 4-column row using the existing `.dashboard` grid (auto-wraps on small screens).
- Tile labels: **Total Restaurants** | **% Grade A** | **Average Score** | **At-Risk Count**.
- Loading state: show `—` (em dash) in each tile while API data is fetching. No skeleton (Phase 9 adds skeletons uniformly via UX-06).
- Data fetched client-side on page load from a single new endpoint that returns all 4 KPI values.

### Borough grade distribution (STAT-02)
- Chart.js 4.4 stacked horizontal bar chart (Chart.js already loaded via CDN on index.html — reuse same CDN URL).
- One horizontal bar per borough (5 boroughs), color-coded A/B/C segments: green A (#22c55e), yellow B (#eab308), red C (#ef4444).
- Bars show **percentages** (normalized to 100% so boroughs are visually comparable).
- Hovering reveals exact counts in the Chart.js tooltip (e.g. "1200 A, 400 B, 150 C").
- Only grades A, B, C included — N/Z/P/Other excluded from the chart.

### Cuisine hygiene ranking (STAT-03)
- Two side-by-side cards using the `.dashboard` grid:
  - Left card: **Top 10 Cleanest** (lowest average score = best)
  - Right card: **Top 10 Worst** (highest average score)
- Each list is a numbered ranked list: rank · cuisine name · avg score (numeric, rounded to 1 decimal, e.g. `12.4`).
- No color badges on scores — numeric only.
- `findWorstCuisinesByAverageScore(int limit)` already exists in DAO. A `findBestCuisinesByAverageScore(int limit)` method needs to be added (same aggregation, reversed sort).

### At-risk restaurant list (STAT-04)
- Fixed limit of 50 restaurants (last grade C or Z). No pagination (Phase 9 adds it uniformly).
- Table columns: **Restaurant name** | **Borough** | **Grade badge** | **View link** (→ `/restaurant/{id}`).
- Grade badge uses the existing `gradeBadgeHtml()` pattern (same as index.html and dashboard.html).
- The existing `/api/inspection/at-risk` endpoint is ADMIN-only (`@PreAuthorize("hasRole('ADMIN')")`). A new public endpoint must be created — either a new `GET /api/restaurants/at-risk` in RestaurantController, or a dedicated `AnalyticsController`. The ADMIN-only endpoint stays as-is.

### Claude's Discretion
- Exact Chart.js configuration (axis labels, legend placement, animation)
- Whether to create a dedicated `AnalyticsController` or add analytics endpoints to `RestaurantController`
- Route order in SecurityConfig (just ensure `/analytics` is accessible without auth)
- CSS for the KPI tiles (can reuse `.card` with a large bold number + smaller label below)
- Nav link placement for `/analytics` in the page header strip

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Templates to replicate style from
- `src/main/resources/templates/index.html` — purple gradient theme, `.card`, `.dashboard` grid, Chart.js usage pattern, `gradeBadgeHtml()`, `borderColor()`, `fetchWithAuth()`
- `src/main/resources/templates/dashboard.html` — most recent template; grade badge + card pattern
- `src/main/resources/templates/restaurant.html` — public page structure (no auth guard)

### Routing and security
- `src/main/java/com/aflokkat/controller/ViewController.java` — add `GET /analytics` route returning `"analytics"` template
- `src/main/java/com/aflokkat/config/SecurityConfig.java` — verify `/analytics` falls under `anyRequest().permitAll()` (should already be public; confirm no catch-all auth rule blocks it)

### Existing backend to extend (do NOT re-implement)
- `src/main/java/com/aflokkat/controller/RestaurantController.java` — add analytics KPI endpoint and public at-risk endpoint here, or create `AnalyticsController`
- `src/main/java/com/aflokkat/controller/InspectionController.java` — existing ADMIN-only `/api/inspection/at-risk` stays unchanged; a new public endpoint is separate
- `src/main/java/com/aflokkat/service/RestaurantService.java` — `getAtRiskRestaurants(String borough, int limit)` already exists (line 190); `getStatisticsByBorough()` exists (line 89)
- `src/main/java/com/aflokkat/dao/RestaurantDAO.java` — `findWorstCuisinesByAverageScore(int limit)` exists (line 67); `findAtRiskRestaurants(String borough, int limit)` exists (line 123); need to add `findBestCuisinesByAverageScore(int limit)` and borough grade distribution aggregation
- `src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java` — implement new aggregation methods here

### DTOs to reuse or extend
- `src/main/java/com/aflokkat/dto/AtRiskEntry.java` — at-risk entry shape (restaurantId, name, borough, lastGrade, lastScore, consecutiveBadGrades)
- `src/main/java/com/aflokkat/aggregation/CuisineScore.java` — cuisine name + avg score shape (used by worst cuisines)

### Requirements
- `.planning/REQUIREMENTS.md` — STAT-01, STAT-02, STAT-03, STAT-04
- `.planning/ROADMAP.md` — Phase 6 goal and success criteria

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `gradeBadgeHtml(grade)` function in `index.html` and `dashboard.html` — copy verbatim into `analytics.html` for at-risk table
- Chart.js 4.4 CDN: `https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js` — already in `index.html`, reuse same URL
- `fetchWithAuth(url, options)` from `index.html` — copy for any authenticated API calls (though /analytics is public, auth-aware fetching is still useful for future nav context)
- Grade color constants: `#22c55e` (A), `#eab308` (B), `#ef4444` (C) — established across all surfaces

### Established Patterns
- All templates use inline `<style>` blocks — no external CSS files
- All API calls are client-side fetch in `<script>` tags (no Thymeleaf data binding)
- No CSS framework — pure CSS matching existing `.card` / `.dashboard` patterns
- All templates show `—` or empty state before data loads

### Integration Points
- `ViewController.java` — add `@GetMapping("/analytics")` returning `"analytics"` template (public, no auth annotation)
- New MongoDB aggregation needed: borough grade distribution — count restaurants grouped by (borough, lastGrade) — similar to `findWorstCuisinesByAverageScore` pipeline structure
- New DAO method needed: `findBestCuisinesByAverageScore(int limit)` — same as `findWorstCuisinesByAverageScore` but `$sort: { avgScore: 1 }` instead of `-1`
- KPI endpoint should batch all 4 values in a single response to avoid 4 separate page-load fetches

</code_context>

<specifics>
## Specific Ideas

- At-risk table "View" link goes to `/restaurant/{id}` using the `restaurantId` (camis) field from `AtRiskEntry`
- Borough chart tooltip shows: "Manhattan — A: 3200, B: 1100, C: 400"
- Cuisine ranked list format: `1. American — 12.4`

</specifics>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope

</deferred>

---

*Phase: 06-analytics-stats*
*Context gathered: 2026-04-03*
