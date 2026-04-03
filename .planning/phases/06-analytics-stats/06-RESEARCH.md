# Phase 6: Analytics & Stats - Research

**Researched:** 2026-04-03
**Domain:** Spring Boot REST endpoints + MongoDB aggregation pipelines + Chart.js stacked bar chart + Thymeleaf template
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Page structure**
- Single long-scroll page — one Thymeleaf template (`analytics.html`), one new ViewController route `GET /analytics`.
- `/analytics` is fully public — no `antMatchers` restriction in SecurityConfig; consistent with `/restaurant/{id}` and `/inspection-map` being public.
- Page follows the purple gradient theme (`#667eea` / `#764ba2`) and `.card` style used across all other templates.

**KPI tiles (STAT-01)**
- 4 tiles in a single 4-column row using the existing `.dashboard` grid (auto-wraps on small screens).
- Tile labels: **Total Restaurants** | **% Grade A** | **Average Score** | **At-Risk Count**.
- Loading state: show `—` (em dash) in each tile while API data is fetching. No skeleton (Phase 9 adds skeletons uniformly via UX-06).
- Data fetched client-side on page load from a single new endpoint that returns all 4 KPI values.

**Borough grade distribution (STAT-02)**
- Chart.js 4.4 stacked horizontal bar chart (Chart.js already loaded via CDN on index.html — reuse same CDN URL).
- One horizontal bar per borough (5 boroughs), color-coded A/B/C segments: green A (#22c55e), yellow B (#eab308), red C (#ef4444).
- Bars show **percentages** (normalized to 100% so boroughs are visually comparable).
- Hovering reveals exact counts in the Chart.js tooltip (e.g. "1200 A, 400 B, 150 C").
- Only grades A, B, C included — N/Z/P/Other excluded from the chart.

**Cuisine hygiene ranking (STAT-03)**
- Two side-by-side cards using the `.dashboard` grid:
  - Left card: **Top 10 Cleanest** (lowest average score = best)
  - Right card: **Top 10 Worst** (highest average score)
- Each list is a numbered ranked list: rank · cuisine name · avg score (numeric, rounded to 1 decimal, e.g. `12.4`).
- No color badges on scores — numeric only.
- `findWorstCuisinesByAverageScore(int limit)` already exists in DAO. A `findBestCuisinesByAverageScore(int limit)` method needs to be added (same aggregation, reversed sort).

**At-risk restaurant list (STAT-04)**
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

### Deferred Ideas (OUT OF SCOPE)
- None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| STAT-01 | Public `/analytics` page shows city-wide KPIs: total restaurants, % grade A, average score, count of at-risk (grade C/Z) | New `GET /api/analytics/kpis` endpoint batching 4 MongoDB queries; `RestaurantService.countAll()` and new aggregation for % grade A / avg score; `findAtRiskRestaurants(null, 50)` already exists |
| STAT-02 | Analytics page shows per-borough grade distribution — for each of the 5 boroughs, a visual breakdown of A/B/C counts | New `GET /api/analytics/borough-grades` endpoint; new DAO method `findBoroughGradeDistribution()`; Chart.js 4.4 stacked horizontal bar already on CDN |
| STAT-03 | Analytics page shows cuisine hygiene ranking — top 10 cleanest and top 10 worst cuisines by average score | New `GET /api/analytics/cuisine-rankings`; `findWorstCuisinesByAverageScore(10)` exists; add `findBestCuisinesByAverageScore(10)` (same pipeline, sort +1) |
| STAT-04 | Analytics page shows "At Risk" list — restaurants with last grade C or Z, with links to their detail page | New public `GET /api/analytics/at-risk`; `findAtRiskRestaurants(null, 50)` already exists in DAO/Service; `AtRiskEntry` DTO is complete |
</phase_requirements>

---

## Summary

Phase 6 adds a fully public `/analytics` page. The backend work is almost entirely new wiring of existing building blocks: three of the four data needs (`countAll`, `findAtRiskRestaurants`, `findWorstCuisinesByAverageScore`) already exist in the DAO/Service layer. The only net-new MongoDB aggregation required is borough-level grade distribution (group by `borough` + `grades.grade`, filter to A/B/C, compute counts). A single `findBestCuisinesByAverageScore` method also needs to be added — it is identical to `findWorstCuisinesByAverageScore` with the sort direction flipped.

On the frontend, Chart.js 4.4 is already loaded via CDN in `index.html`. The stacked horizontal bar chart for borough grades is a configuration-only task (type `"bar"`, `indexAxis: "y"`, `stacked: true` on both axes, three datasets for A/B/C). The `gradeBadgeHtml()` and `borderColor()` functions can be copied verbatim from `index.html` into `analytics.html`.

The biggest architectural decision is whether to put all four new API endpoints in `RestaurantController` or in a new `AnalyticsController`. Creating a dedicated `AnalyticsController` is the recommended approach: it avoids further bloating an already 389-line controller, maps cleanly to the `/api/analytics/*` path prefix, and the SecurityConfig `anyRequest().permitAll()` catch-all already makes every non-ADMIN API path public by default.

**Primary recommendation:** Create `AnalyticsController` at `/api/analytics` with four endpoints, extend `RestaurantDAO`/`RestaurantDAOImpl` with two new methods, and build `analytics.html` by composing existing CSS/JS patterns from `index.html` and `dashboard.html`.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Chart.js | 4.4.0 | Borough grade distribution stacked bar chart | Already loaded via CDN in `index.html`; reuse same URL |
| mongodb-driver-sync | (project BOM) | Borough grade distribution aggregation pipeline | All other MongoDB aggregations use this — no change |
| Spring Boot 2.6.15 / Spring MVC | (project BOM) | REST controller, view controller | Established project stack |
| Thymeleaf | (project BOM) | Server-side HTML template for `/analytics` | Used by all other templates |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Jackson | (Spring Boot BOM) | JSON serialization of DTO response bodies | Automatic via `@RestController` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Dedicated `AnalyticsController` | Adding to `RestaurantController` | `RestaurantController` is already 389 lines; dedicated controller is cleaner separation, maps to `/api/analytics/*` prefix |
| Client-side fetch on page load | Thymeleaf model attributes | All other templates use client-side fetch pattern; consistency is more important than SSR here |

**Installation:**

No new dependencies. Chart.js is already in `index.html`:
```html
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
```

---

## Architecture Patterns

### Recommended Project Structure

New files for this phase:

```
src/main/java/com/aflokkat/
├── controller/
│   └── AnalyticsController.java      # NEW — GET /api/analytics/{kpis,borough-grades,cuisine-rankings,at-risk}
├── dao/
│   ├── RestaurantDAO.java            # EXTEND — add findBoroughGradeDistribution(), findBestCuisinesByAverageScore()
│   └── RestaurantDAOImpl.java        # EXTEND — implement the two new methods
└── service/
    └── RestaurantService.java        # EXTEND — add getBoroughGradeDistribution(), getBestCuisinesByAverageScore()

src/main/resources/templates/
└── analytics.html                    # NEW — single long-scroll page

src/main/java/com/aflokkat/controller/
└── ViewController.java               # EXTEND — add @GetMapping("/analytics")

src/test/java/com/aflokkat/controller/
└── AnalyticsControllerTest.java      # NEW — Wave 0 test scaffold
```

### Pattern 1: New AnalyticsController

**What:** A `@RestController` at `/api/analytics` with no `@PreAuthorize` — inherits the `anyRequest().permitAll()` catch-all in SecurityConfig.

**When to use:** Always for analytics endpoints that must be public.

```java
// Pattern derived from RestaurantController in this project
@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AnalyticsController {

    @Autowired
    private RestaurantService restaurantService;

    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> getKpis() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            // totalRestaurants, percentGradeA, avgScore, atRiskCount assembled here
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseUtil.errorResponse(e);
        }
    }
}
```

### Pattern 2: Borough Grade Distribution MongoDB Aggregation

**What:** Group by `(borough, grade)`, filter to A/B/C, reshape into per-borough lists.

**When to use:** For STAT-02 only.

```java
// New method in RestaurantDAOImpl — follows exact style of findAtRiskRestaurants
// Pipeline:
// 1. $project: extract lastGrade from first element of grades array
// 2. $match: lastGrade in ["A","B","C"]
// 3. $group: _id: {borough, grade}, count: $sum 1
// 4. $group: _id: borough, grades: $push {grade, count}

List<Document> pipeline = Arrays.asList(
    new Document("$addFields", new Document("lastGrade",
        new Document("$arrayElemAt", Arrays.asList("$grades.grade", 0)))),
    new Document("$match", new Document("lastGrade",
        new Document("$in", Arrays.asList("A", "B", "C")))),
    new Document("$group", new Document()
        .append("_id", new Document()
            .append("borough", "$borough")
            .append("grade", "$lastGrade"))
        .append("count", new Document("$sum", 1))),
    new Document("$group", new Document()
        .append("_id", "$_id.borough")
        .append("grades", new Document("$push", new Document()
            .append("grade", "$_id.grade")
            .append("count", "$count"))))
);
// Returns List<Document> — deserialize in service layer into Map<String, Map<String,Long>>
```

### Pattern 3: findBestCuisinesByAverageScore

**What:** Identical to `findWorstCuisinesByAverageScore` but sort `avgScore: 1` instead of `avgScore: -1`.

**Note:** `findWorstCuisinesByAverageScore` already sorts `avgScore: 1` (ascending = lowest scores = BEST). Verify the existing method actually returns worst (highest) scores — looking at the implementation the sort is `avgScore: 1` (ascending) but the method is named "worst". Inspection scores in NYC DOH system: **higher score = more violations = worse**. The sort `avgScore: 1` returns lowest scores = cleanest. This is a naming inconsistency in the existing code. Research finding:

- `findWorstCuisinesByAverageScore` uses `$sort: {avgScore: 1}` → returns **lowest** avg scores = **cleanest** cuisines
- To get worst (highest avg score), sort `$sort: {avgScore: -1}`

**Action for planner:** The new method `findBestCuisinesByAverageScore(int limit)` should sort `avgScore: 1` (lowest = cleanest) and `findWorstCuisinesByAverageScore` should sort `avgScore: -1` (highest = worst). Verify this against the existing `CLAUDE.md` note: "Lower score = better (fewer violations)" confirmed in `RestaurantService.getTrend()`.

```java
// In RestaurantDAOImpl — add findBestCuisinesByAverageScore
@Override
public List<CuisineScore> findBestCuisinesByAverageScore(int limit) {
    return aggregate(Arrays.asList(
        new Document("$unwind", "$grades"),
        new Document("$group", new Document()
            .append("_id", "$cuisine")
            .append("avgScore", new Document("$avg", "$grades.score"))
            .append("count", new Document("$sum", 1))),
        new Document("$sort", new Document("avgScore", 1)),   // lowest = best
        new Document("$limit", limit)
    ), CuisineScore.class);
}
```

### Pattern 4: KPI Endpoint — Batch 4 Values

**What:** Single `/api/analytics/kpis` endpoint returning all 4 KPI values to avoid 4 separate page-load fetches.

**KPI calculations:**
- `totalRestaurants` → `restaurantService.countAll()` (already exists)
- `percentGradeA` → new aggregation: count where `lastGrade == "A"` / `countAll()` * 100
- `avgScore` → new aggregation: `$avg` of `$arrayElemAt(grades.score, 0)`
- `atRiskCount` → `findAtRiskRestaurants(null, Integer.MAX_VALUE).size()` — or better, a dedicated `countAtRiskRestaurants()` DAO method

**Note:** For `percentGradeA` and `avgScore`, running two MongoDB aggregations is acceptable given Redis caching exists. The `atRiskCount` should use a `$count` aggregation stage rather than loading all 50 records and calling `.size()`.

### Pattern 5: Stacked Horizontal Bar Chart (Chart.js 4.4)

**What:** Stacked bar showing per-borough A/B/C grade distribution as percentages.

```javascript
// Source: Chart.js 4.4 official docs — stacked bar configuration
new Chart(ctx, {
    type: 'bar',
    data: {
        labels: ['MANHATTAN', 'BROOKLYN', 'QUEENS', 'BRONX', 'STATEN ISLAND'],
        datasets: [
            { label: 'A', data: [percentA...], backgroundColor: '#22c55e' },
            { label: 'B', data: [percentB...], backgroundColor: '#eab308' },
            { label: 'C', data: [percentC...], backgroundColor: '#ef4444' }
        ]
    },
    options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        scales: {
            x: { stacked: true, max: 100, ticks: { callback: v => v + '%' } },
            y: { stacked: true }
        },
        plugins: {
            tooltip: {
                callbacks: {
                    label: (ctx) => {
                        const borough = ctx.label;
                        const grade = ctx.dataset.label;
                        const count = rawCounts[borough][grade] || 0;
                        return `${grade}: ${count} (${ctx.parsed.x.toFixed(1)}%)`;
                    }
                }
            }
        }
    }
});
```

**Key detail:** The tooltip must show raw counts ("Manhattan — A: 3200, B: 1100, C: 400"). Store raw counts separately from percentage data and reference them in the tooltip callback.

### Pattern 6: ViewController route

```java
// In ViewController.java — no auth annotation, follows restaurant() pattern
@GetMapping("/analytics")
public String analytics() {
    return "analytics";
}
```

### Anti-Patterns to Avoid

- **Adding analytics endpoints to RestaurantController:** That file is already 389 lines; adding 4 more endpoints makes maintenance harder with no benefit.
- **Calling `findAtRiskRestaurants(null, 50).size()` for the at-risk count KPI:** This loads 50 full documents just to count them. Use a dedicated `countAtRiskRestaurants()` DAO method with `$count` pipeline stage.
- **Using `fetchWithAuth()` for public analytics API calls:** The analytics endpoints are public; standard `fetch()` is sufficient. `fetchWithAuth()` can still be used if nav context needs token-aware behavior.
- **Four separate API calls on page load:** All 4 KPIs must come from one endpoint. Confirmed by CONTEXT.md decision.
- **Forgetting `stacked: true` on both x and y axes:** Omitting it from one axis causes Chart.js to render overlapping non-stacked bars.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Stacked bar chart | Custom SVG/CSS bars | Chart.js 4.4 (already on CDN) | Tooltip, animation, responsiveness built-in |
| Borough grade aggregation | Java-side grouping after fetching all docs | MongoDB aggregation pipeline | Avoids transferring full collection over the wire |
| At-risk restaurant query | Manual iteration over all restaurants | `findAtRiskRestaurants()` already exists | Pipeline with `$match lastGrade in [C,Z]` + `$sort` + `$limit` |
| Grade badge HTML | New badge component | `gradeBadgeHtml()` function from `index.html` | Already handles all grades, consistent styling |

**Key insight:** All four data requirements map to existing DAO methods or simple extensions of them. No novel aggregation logic is needed beyond the borough grade distribution.

---

## Common Pitfalls

### Pitfall 1: Sort direction confusion in cuisine ranking
**What goes wrong:** `findWorstCuisinesByAverageScore` sorts `avgScore: 1` (ascending = lowest = cleanest). The name says "worst" but the result is "best". If `findBestCuisinesByAverageScore` is added with the same sort direction, both lists will show the same data.
**Why it happens:** NYC DOH score semantics: lower = fewer violations = better. The existing method name is misleading.
**How to avoid:** Verify: `findWorstCuisinesByAverageScore` should use `$sort: {avgScore: -1}`. `findBestCuisinesByAverageScore` uses `$sort: {avgScore: 1}`.
**Warning signs:** Both ranked lists showing the same cuisines.

### Pitfall 2: Borough name casing mismatch
**What goes wrong:** MongoDB stores borough names in uppercase (e.g., "MANHATTAN", "BROOKLYN"). Chart.js labels will show uppercase. The tooltip format in CONTEXT.md shows "Manhattan" (title case).
**Why it happens:** Raw data casing flows through aggregation unchanged.
**How to avoid:** Either normalize in the aggregation with `$toLower`/`$toUpper`, or apply `.toLowerCase()` with title-casing in the frontend JavaScript before building chart labels. Consistent with existing behavior: `index.html` passes borough strings from the API directly to Chart.js labels.
**Warning signs:** Labels rendering as "MANHATTAN" when spec says "Manhattan".

### Pitfall 3: SecurityConfig — `/api/inspection/at-risk` vs new public endpoint
**What goes wrong:** `InspectionController` has a class-level `@PreAuthorize("hasRole('ADMIN')")`. Adding a new method to it would inherit the admin restriction. The new public at-risk endpoint must NOT be in `InspectionController`.
**Why it happens:** `@PreAuthorize` at class level applies to all methods.
**How to avoid:** Place the new public `GET /api/analytics/at-risk` in `AnalyticsController` (no `@PreAuthorize`). Leave `InspectionController` untouched.
**Warning signs:** Public at-risk endpoint returning HTTP 403.

### Pitfall 4: SecurityConfig already has `anyRequest().permitAll()`
**What goes wrong:** Developers might add an explicit `antMatchers("/analytics").permitAll()` rule. This is harmless but redundant and can confuse future readers.
**Why it happens:** Default caution.
**How to avoid:** Confirmed: SecurityConfig line 69 has `.anyRequest().permitAll()` — `/analytics` and `/api/analytics/**` are already public. No SecurityConfig changes needed.
**Warning signs:** None — this is a non-issue, just don't waste a task on SecurityConfig edits.

### Pitfall 5: KPI `avgScore` aggregation over `grades` array
**What goes wrong:** Using `$unwind: "$grades"` then `$avg` gives the average score across ALL historical inspection records, not just the latest grade. The CONTEXT.md spec says "average score (city-wide)" without specifying which score to use, but last grade is the most meaningful.
**Why it happens:** `grades` is an array; naive `$avg` hits all elements.
**How to avoid:** Use `$arrayElemAt: ["$grades.score", 0]` to extract only the most recent score per restaurant, then `$avg` across restaurants. This matches how `findAtRiskRestaurants` and `findMapPoints` already handle the grades array.

---

## Code Examples

### Borough Grade Distribution — Complete Pipeline

```java
// In RestaurantDAOImpl — new method findBoroughGradeDistribution()
// Returns List<Document>: [{_id: "MANHATTAN", grades: [{grade: "A", count: 3200}, ...]}, ...]
List<Document> pipeline = Arrays.asList(
    new Document("$addFields", new Document("lastGrade",
        new Document("$arrayElemAt", Arrays.asList("$grades.grade", 0)))),
    new Document("$match", new Document("lastGrade",
        new Document("$in", Arrays.asList("A", "B", "C")))),
    new Document("$group", new Document()
        .append("_id", new Document("borough", "$borough").append("grade", "$lastGrade"))
        .append("count", new Document("$sum", 1))),
    new Document("$group", new Document()
        .append("_id", "$_id.borough")
        .append("grades", new Document("$push",
            new Document("grade", "$_id.grade").append("count", "$count")))),
    new Document("$sort", new Document("_id", 1))
);
List<Document> results = new ArrayList<>();
database.getCollection(AppConfig.getMongoCollection())
    .aggregate(pipeline)
    .forEach(results::add);
return results;
```

### KPI Endpoint Response Shape

```json
{
  "status": "success",
  "totalRestaurants": 27000,
  "percentGradeA": 73.4,
  "avgScore": 14.2,
  "atRiskCount": 412
}
```

### gradeBadgeHtml — Copy from index.html

```javascript
// Verbatim copy from index.html line 1549
function gradeBadgeHtml(grade) {
    const g = grade || '—';
    let bg = '#ffebee', color = '#b71c1c';
    if (g === 'A') { bg = '#e8f5e9'; color = '#2e7d32'; }
    else if (g === 'B') { bg = '#fff8e1'; color = '#f57f17'; }
    return `<span style="display:inline-block;padding:2px 8px;border-radius:12px;font-weight:700;font-size:0.82em;background:${bg};color:${color}">${g}</span>`;
}
```

### KPI Tile CSS — Reuse `.card`

```html
<!-- 4-column grid within a .dashboard container -->
<div class="dashboard" style="grid-template-columns: repeat(4, 1fr)">
    <div class="card" style="text-align:center">
        <div id="kpi-total" style="font-size:2em;font-weight:700;color:#667eea">—</div>
        <div style="font-size:0.85em;color:#666;margin-top:4px">Total Restaurants</div>
    </div>
    <!-- repeat for other 3 KPIs -->
</div>
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Chart.js 2.x (labels on bar) | Chart.js 4.x (plugins API for tooltips) | Chart.js 3.0 (2021) | `options.plugins.tooltip.callbacks.label` replaces `options.tooltips.callbacks.label` |
| `anyRequest().authenticated()` catch-all | `anyRequest().permitAll()` | Phase 3 | No new SecurityConfig changes needed for public analytics page |

**Deprecated/outdated:**
- Chart.js 2.x tooltip API: use `options.plugins.tooltip.callbacks` (not `options.tooltips.callbacks`)

---

## Open Questions

1. **`findWorstCuisinesByAverageScore` sort direction**
   - What we know: The method sorts `avgScore: 1` (ascending = lowest = fewest violations = cleanest)
   - What's unclear: Whether the intent was "worst" (highest score) or the naming is inverted
   - Recommendation: Treat `avgScore: 1` sort as returning "best" (cleanest); the new `findBestCuisinesByAverageScore` should also sort `avgScore: 1` and `findWorstCuisinesByAverageScore` should be updated to sort `avgScore: -1`. If changing existing sort breaks existing tests, add the new method only and leave the old one as-is — reference it as "cleanest" in the analytics page.

2. **`atRiskCount` KPI calculation method**
   - What we know: `findAtRiskRestaurants(null, 50)` exists and returns up to 50 entries
   - What's unclear: Whether the KPI should count all at-risk restaurants (may be thousands) or just the 50 shown
   - Recommendation: Add a dedicated `countAtRiskRestaurants()` DAO method using `$count` to get the true city-wide total; the KPI tile shows this total while the table below shows the 50 most recent.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (jupiter) via Spring Boot 2.6.15 BOM + Mockito (ExtendWith) |
| Config file | none — inherited from Spring Boot BOM |
| Quick run command | `mvn test -Dtest=AnalyticsControllerTest -pl . -q` |
| Full suite command | `mvn test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| STAT-01 | `GET /api/analytics/kpis` returns 200 with totalRestaurants, percentGradeA, avgScore, atRiskCount | unit | `mvn test -Dtest=AnalyticsControllerTest#testKpis_returns200` | ❌ Wave 0 |
| STAT-02 | `GET /api/analytics/borough-grades` returns 200 with 5 borough entries | unit | `mvn test -Dtest=AnalyticsControllerTest#testBoroughGrades_returns5Boroughs` | ❌ Wave 0 |
| STAT-03 | `GET /api/analytics/cuisine-rankings` returns best and worst lists each with 10 entries | unit | `mvn test -Dtest=AnalyticsControllerTest#testCuisineRankings_returnsTwoLists` | ❌ Wave 0 |
| STAT-04 | `GET /api/analytics/at-risk` returns 200 with data array, each entry has restaurantId, name, borough, lastGrade | unit | `mvn test -Dtest=AnalyticsControllerTest#testAtRisk_returnsEntries` | ❌ Wave 0 |
| STAT-04 | `GET /analytics` returns HTTP 200 (Thymeleaf view rendered) | unit | `mvn test -Dtest=ViewControllerAnalyticsTest#testAnalyticsPage_returns200` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `mvn test -Dtest=AnalyticsControllerTest -q`
- **Per wave merge:** `mvn test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/com/aflokkat/controller/AnalyticsControllerTest.java` — covers STAT-01, STAT-02, STAT-03, STAT-04
- [ ] `src/test/java/com/aflokkat/controller/ViewControllerAnalyticsTest.java` — covers STAT-04 (public page load)

Pattern for `AnalyticsControllerTest`: follow `RestaurantControllerSearchTest` exactly — `@ExtendWith(MockitoExtension.class)` + `MockMvcBuilders.standaloneSetup(analyticsController).build()`. Never use `@WebMvcTest`.

---

## Sources

### Primary (HIGH confidence)

- Source code inspection: `RestaurantDAOImpl.java`, `RestaurantService.java`, `InspectionController.java`, `RestaurantController.java`, `SecurityConfig.java`, `ViewController.java` — direct reading of existing implementation
- Source code inspection: `index.html` — Chart.js 4.4 CDN URL, `gradeBadgeHtml()` and `borderColor()` function bodies, `fetchWithAuth()` pattern
- Source code inspection: `AtRiskEntry.java`, `CuisineScore.java` — DTO shapes for downstream serialization

### Secondary (MEDIUM confidence)

- Chart.js 4.x stacked bar configuration: standard `indexAxis: "y"` + `scales.x.stacked: true` + `scales.y.stacked: true` pattern, consistent with Chart.js 4.4 documentation patterns

### Tertiary (LOW confidence)

- None — all research grounded in existing codebase inspection

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in use, versions verified in source
- Architecture: HIGH — all patterns derived from existing code in this repo
- Pitfalls: HIGH — sort direction pitfall derived from direct code inspection; SecurityConfig confirmed by reading the file
- MongoDB aggregation examples: HIGH — follow exact style of existing `findAtRiskRestaurants` and `findMapPoints` in `RestaurantDAOImpl.java`

**Research date:** 2026-04-03
**Valid until:** 2026-05-03 (stable stack — Spring Boot 2.6.15, Chart.js 4.4 already locked)
