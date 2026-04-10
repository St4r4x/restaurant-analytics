# Phase 8: Discovery Enhancement - Research

**Researched:** 2026-04-03
**Domain:** Client-side filtering, MongoDB date-range aggregation, CSV export, Thymeleaf views
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Map filter bar (DISC-01)
- Layout: Horizontal top-toolbar strip above the map (below navbar) — extends the existing borough select shell already in the template
- Grade filter: Four checkboxes (A / B / C / F), all checked by default — unchecking hides matching markers without a network request
- Borough filter: Existing `<select id="borough-filter">` dropdown, already partially wired
- Cuisine filter: `<select>` populated from `GET /api/restaurants/cuisines` — full list, no truncation; browser-native `<select>` handles scrolling/search
- Filtering logic: Client-side only — all markers are already loaded from `/api/restaurants/map-points`; filter callbacks show/hide layers in the existing `markers` ClusterGroup
- Feedback: A count badge updates live: e.g. "312 markers shown" — shown in the toolbar next to the filters
- Cuisine dropdown load: Fetched once on page load, populates the select; "All cuisines" as default option

#### Uncontrolled page (DISC-02)
- Route: `/uncontrolled` → ViewController returns `"uncontrolled"` template; public (no auth)
- Data criteria: Wider than the existing at-risk list — last grade C/Z **OR** not inspected in the past 12 months (new backend query)
- New endpoint: `GET /api/inspection/uncontrolled` — returns the full uncontrolled dataset
- New CSV endpoint: `GET /api/inspection/uncontrolled/export.csv` — exports the uncontrolled dataset (does NOT reuse at-risk CSV, which has different criteria)
- Table columns: Name · Borough · Cuisine · Last Grade · Last Score · Days Since Inspection (sortable by score; borough filterable via dropdown)
- Sorting: Client-side — default API order; user can click "Sort by Score ↑/↓" to reorder the rendered table
- No auth required: Consistent with analytics and restaurant detail pages

#### Nearby section on restaurant detail (DISC-03)
- Placement: After inspection history (bottom of page) — primary grade/score/history content stays at the top
- Data: Calls `GET /api/restaurants/nearby?lat=X&lng=Y&radius=500&limit=5` using the restaurant's own lat/lng
- Cards: Each card shows name, grade badge, and a "View Details" link — same mini-card pattern used on index.html bookmarks strip
- No GPS case: If the restaurant document has no lat/lng, the Nearby section is not rendered at all (no empty state message)
- Self-exclusion: The current restaurant must be excluded from its own nearby results (filter by restaurantId client-side or pass an exclude param)

#### Search sort control (DISC-04)
- Scope: Landing page only (`landing.html`) — where the search CTA lives
- Options: Best Score (lowest numeric score first) / Worst Score (highest score first) / A→Z (alphabetical by name)
- Default: No sort — results appear in API order (relevance) when a new search runs
- Reset behavior: Selecting a new search query resets sort to default (no sort)
- Implementation: Client-side only — sort reorders the already-rendered result list; no new API call
- Placement: Sort control appears above the result list, visible only when results are present

### Claude's Discretion
- Exact sort control widget style (select dropdown vs pill buttons)
- CSS for the "X markers shown" badge on the map
- Uncontrolled table row hover, striping, or other visual polish
- Whether the Nearby section has a section heading card or inline heading

### Deferred Ideas (OUT OF SCOPE)
- None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DISC-01 | Map at `/inspection-map` has filter controls — grade checkboxes (A/B/C/F), borough dropdown, cuisine dropdown — markers update client-side without reload | Markers ClusterGroup var already window-scoped in inspection-map.html; cuisine API exists at `/api/restaurants/cuisines`; borough select shell at line 70 |
| DISC-02 | `/uncontrolled` page lists restaurants not inspected 12+ months or last grade C/Z; sortable by score, filterable by borough; CSV download | `findAtRiskRestaurants` aggregation in RestaurantDAOImpl is the direct template; `$toDate` date arithmetic pattern established in `findRecentlyInspected`; CSV export pattern in `InspectionController.exportAtRiskCsv` |
| DISC-03 | Restaurant detail page shows up to 5 nearby restaurants (within 500m) in a "Nearby" section with grade badge and link | `/api/restaurants/nearby` endpoint exists (RestaurantController line 313); restaurant lat/lng available from `/api/restaurants/{id}` response; mini-card pattern in index.html |
| DISC-04 | Search results sortable by score (best first), grade, or name; sort control visible above results | Search results already stored in JS array in landing.html; sort is a pure client-side array sort + re-render; no backend changes needed |
</phase_requirements>

---

## Summary

Phase 8 enhances four existing pages with discovery features. All four requirements are primarily client-side changes layered on top of already-working backend endpoints, with one exception: the `/uncontrolled` page requires a new MongoDB aggregation, two new endpoints on `InspectionController`, and a new Thymeleaf route.

The most complex deliverable is DISC-02. The uncontrolled query must combine two disjoint criteria (last grade C/Z OR last inspection older than 12 months) in a single `$match` using `$or`. The date arithmetic uses the `$toDate` / `$subtract` / `$dateSubtract` pattern already established in `findRecentlyInspected`. The aggregation result must also expose a `daysSinceInspection` computed field for the table column.

The other three requirements (DISC-01, DISC-03, DISC-04) touch only template JavaScript and add no new Java code beyond wiring. The existing APIs (`/api/restaurants/cuisines`, `/api/restaurants/nearby`) are confirmed present and functional.

**Primary recommendation:** Implement in wave order — backend (DISC-02 DAO + controller + route) first so it can be tested independently, then the three front-end layers (DISC-01 map filters, DISC-03 nearby section, DISC-04 sort control).

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 2.6.15 | Web framework, MVC, security | Project standard |
| MongoDB driver-sync | per BOM | Raw aggregation pipelines, no Spring Data | Project standard; constructor pattern locked |
| JUnit 5.8.2 | via BOM | Unit tests | Project standard (BOM from Spring Boot 2.6.15) |
| Mockito 5.x | project override | Mock interfaces in tests | Project standard; Java 25 constraints apply |
| Leaflet.js | 1.9.4 | Map rendering in inspection-map.html | Already loaded via CDN |
| Leaflet.markercluster | 1.5.3 | Clustered marker groups | Already loaded via CDN |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| MockMvc standaloneSetup | via Spring Test | Controller unit tests | Always — never @WebMvcTest (Java 25 crash) |
| Thymeleaf | via BOM | Server-side HTML template rendering | Only for ViewController routes; all data via fetch |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Client-side array sort | Server-side sort param | Client-side is correct here — all results already fetched |
| $or aggregation | Two separate queries merged in Java | Single aggregation is cleaner and faster |

---

## Architecture Patterns

### Recommended Project Structure

New files this phase:
```
src/main/resources/templates/
└── uncontrolled.html                   # new — DISC-02 view

src/main/java/com/aflokkat/
├── controller/
│   ├── ViewController.java             # add GET /uncontrolled route
│   └── InspectionController.java       # add 2 new endpoints
├── dao/
│   ├── RestaurantDAO.java              # add findUncontrolled() interface method
│   └── RestaurantDAOImpl.java          # implement findUncontrolled()
└── dto/
    └── UncontrolledEntry.java          # new DTO (mirrors AtRiskEntry + daysSince field)

src/test/java/com/aflokkat/controller/
├── ViewControllerUncontrolledTest.java # new — DISC-02
└── InspectionControllerUncontrolledTest.java  # new — DISC-02
```

Existing files modified:
```
src/main/resources/templates/inspection-map.html   # DISC-01: grade checkboxes + cuisine select + count badge
src/main/resources/templates/restaurant.html        # DISC-03: Nearby section after history card
src/main/resources/templates/landing.html           # DISC-04: sort control above search-results div
```

### Pattern 1: Client-side Marker Filtering (DISC-01)

**What:** Store all map points in a module-level JS array at load time. On each filter change, rebuild a new `L.markerClusterGroup` containing only the matching markers, swap it into the map.

**When to use:** Already loaded dataset, no network cost, sub-200ms target.

**Critical constraint from codebase:** The current `inspection-map.html` creates `markers` as a local variable inside `loadMapPoints()`. To filter, `allPoints` (raw data array) and `markers` ClusterGroup must be promoted to module-level scope so the filter callbacks can access them.

**Example:**
```javascript
// Source: inspection-map.html refactor — promote to module scope
let allPoints = [];   // raw map-point objects from API
let markers = L.markerClusterGroup();

function applyFilters() {
  const activeGrades = ['A','B','C','F'].filter(g =>
    document.getElementById('grade-' + g).checked
  );
  const borough = document.getElementById('borough-filter').value;
  const cuisine = document.getElementById('cuisine-filter').value;

  map.removeLayer(markers);
  markers = L.markerClusterGroup();

  const shown = allPoints.filter(r => {
    const gradeMatch = activeGrades.includes(r.grade) || (!r.grade && activeGrades.length > 0);
    const boroughMatch = !borough || r.borough === borough;
    const cuisineMatch = !cuisine || r.cuisine === cuisine;
    return gradeMatch && boroughMatch && cuisineMatch;
  });

  shown.forEach(r => { /* build and add marker */ markers.addLayer(marker); });
  map.addLayer(markers);
  document.getElementById('marker-count').textContent = shown.length + ' markers shown';
}
```

**Gotcha:** The `allPoints` array from `/api/restaurants/map-points` currently does NOT include `borough` or `cuisine` fields in the projection. The `findMapPoints()` aggregation in `RestaurantDAOImpl` must be extended to add `borough` and `cuisine` to the `$project` stage for client-side borough/cuisine filtering to work.

### Pattern 2: Uncontrolled MongoDB Aggregation (DISC-02)

**What:** A `$match` with `$or` combining grade criteria and date arithmetic for last inspection date.

**When to use:** Single-pass aggregation over all restaurant documents.

**Example:**
```java
// Source: modeled on findAtRiskRestaurants + findRecentlyInspected in RestaurantDAOImpl
long twelveMonthsAgoMs = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000;
java.util.Date cutoff = new java.util.Date(twelveMonthsAgoMs);

List<Document> pipeline = new ArrayList<>();
if (borough != null && !borough.isEmpty()) {
    pipeline.add(new Document("$match", new Document("borough", borough)));
}
pipeline.add(new Document("$addFields", new Document()
    .append("lastGrade", new Document("$arrayElemAt", Arrays.asList("$grades.grade", 0)))
    .append("lastScore", new Document("$arrayElemAt", Arrays.asList("$grades.score", 0)))
    .append("lastInspectionDate", new Document("$arrayElemAt", Arrays.asList("$grades.date", 0)))
));
pipeline.add(new Document("$addFields", new Document(
    "lastInspectionMs", new Document("$toLong",
        new Document("$toDate", new Document("$ifNull",
            Arrays.asList("$lastInspectionDate", new java.util.Date(0)))))
)));
pipeline.add(new Document("$match", new Document("$or", Arrays.asList(
    new Document("lastGrade", new Document("$in", Arrays.asList("C", "Z"))),
    new Document("lastInspectionMs", new Document("$lt", twelveMonthsAgoMs))
))));
// $addFields daysSinceInspection, $sort, $limit
```

**Alternative date approach:** Use `$expr` with `$lt` on `$toDate` value vs a BSON Date literal — same result, slightly simpler. The `findRecentlyInspected` method in the existing codebase uses `$expr` + `$toDate` successfully.

### Pattern 3: UncontrolledEntry DTO

**What:** New POJO mirroring `AtRiskEntry` with an additional `daysSinceInspection` computed field.

**Key detail:** The `@BsonProperty` mapping must match the `$addFields` output names in the aggregation pipeline. The existing `AtRiskEntry` class with `@BsonProperty("restaurant_id")` is the direct model.

```java
public class UncontrolledEntry {
    @BsonProperty("restaurant_id") private String restaurantId;
    @BsonProperty("name")          private String name;
    @BsonProperty("borough")       private String borough;
    @BsonProperty("cuisine")       private String cuisine;
    @BsonProperty("lastGrade")     private String lastGrade;
    @BsonProperty("lastScore")     private Integer lastScore;
    @BsonProperty("daysSinceInspection") private Integer daysSinceInspection;
    // getters + setters
}
```

### Pattern 4: InspectionController — Remove @PreAuthorize (DISC-02)

**Critical:** `InspectionController` currently has `@PreAuthorize("hasRole('ADMIN')")` at the **class level** (line 29). The new `/uncontrolled` endpoints must be public (no auth). The correct approach is to move the `@PreAuthorize` annotation from the class level down to each individual existing method (`/at-risk` and `/at-risk/export.csv`), and leave the new endpoints unannotated so they fall through to `anyRequest().permitAll()`.

Do NOT add a class-level `@PreAuthorize` override on individual methods — Spring Security resolves class-level before method-level; removing from class and adding to each existing method is the safe pattern.

### Pattern 5: Nearby Section in restaurant.html (DISC-03)

**What:** Append a new card after the existing `#history-card` div. Fetch `/api/restaurants/nearby` using lat/lng from the already-fetched restaurant object. Filter out the current restaurant by `restaurantId` before rendering.

**Placement in existing template:** After line 173 (closing `</div>` of `#history-card`), inside `.container`.

**Lat/lng source:** The restaurant detail API response (`/api/restaurants/{id}`) returns `r.latitude` and `r.longitude` fields (confirmed in `renderMap()` function at line 304 of restaurant.html). These are read from `address.coord[1]` and `address.coord[0]` respectively.

**Mini-card pattern (from index.html):**
```javascript
// Source: index.html renderRestaurantCards function
'<div class="mini-card">'
+ '<div style="font-weight:700;font-size:0.9em;margin-bottom:4px">' + (r.name || '—') + '</div>'
+ '<div style="font-size:0.82em;color:#666;margin-bottom:8px">' + (r.borough || '—') + '</div>'
+ gradeBadgeHtml(r.grade)
+ '<br><a href="/restaurant/' + r.restaurantId + '" ...>View Details</a>'
+ '</div>'
```

The `.mini-card` CSS class must be defined in restaurant.html (it currently is not — it's only in index.html). Add it inline to `<style>`.

### Pattern 6: Search Sort Control (DISC-04)

**What:** Store the raw search results in a module-level JS array. On sort selection change, sort a copy and re-render.

**Key detail:** In `landing.html`, the current `doSearch()` function renders directly to `searchResults.innerHTML` without storing the data array. The function must be refactored to store results in `var lastResults = []` and call a separate `renderResults()` function. The sort control only appears when `lastResults.length > 0`.

**Sort implementations:**
```javascript
// Source: MDN Array.prototype.sort
function sortResults(arr, mode) {
  const copy = arr.slice();
  if (mode === 'best')  return copy.sort((a, b) => (a.score ?? 999) - (b.score ?? 999));
  if (mode === 'worst') return copy.sort((a, b) => (b.score ?? 0)   - (a.score ?? 0));
  if (mode === 'az')    return copy.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
  return copy; // no sort (default)
}
```

**Note on score field in search results:** `RestaurantService.toView()` returns a view map. The `latestScore` field must be present in the view for sort-by-score to work. Verify that `toView()` includes `latestScore` in its output.

### Anti-Patterns to Avoid
- **Reusing the at-risk endpoint for uncontrolled data:** The criteria differ; add a dedicated endpoint.
- **Using @WebMvcTest:** Crashes JVM on Java 25 — always use `standaloneSetup`.
- **Mocking RestaurantService (constructor-injected) in new tests:** Use `@Mock RestaurantDAO` + `@InjectMocks Controller` directly (same as AnalyticsControllerTest).
- **Modifying map markers via `layer.clearLayers()` on the existing cluster group:** `clearLayers()` followed by re-adding is slower than swapping the entire group. Create a new `L.markerClusterGroup` and swap — this is the established Leaflet.markercluster pattern.
- **Committing `@Disabled` Wave 0 stubs if not needed:** Phase 8 tests can be written complete since all APIs exist; Wave 0 stubs are not required here.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| CSV serialization | Custom string builder per new endpoint | Copy the `csvField()` + `StringBuilder` pattern from `InspectionController.exportAtRiskCsv` | Already handles quote-escaping; consistent output |
| Date comparison in MongoDB | Java-side date filter after fetching all docs | `$toLong` / `$toDate` + `$lt` in aggregation `$match` | Server-side filtering; same approach as `findRecentlyInspected` |
| Borough filter in `/uncontrolled` endpoint | New filter mechanism | Existing `if (borough != null)` prepend `$match` pattern from `findAtRiskRestaurants` | Identical pattern, one `if` block |
| Grade badge HTML in uncontrolled.html | New badge function | Copy `gradeBadgeHtml()` from landing.html / analytics.html verbatim | No shared module in this project; copy-per-template is the established pattern |

---

## Common Pitfalls

### Pitfall 1: findMapPoints Projection Missing borough/cuisine
**What goes wrong:** Client-side borough and cuisine filtering on the map silently shows 0 results because the marker data objects have no `borough` or `cuisine` fields.
**Why it happens:** Current `findMapPoints()` only projects `restaurantId`, `name`, `lat`, `lng`, `grade`. The filtering JS checks `r.borough` and `r.cuisine` which are both `undefined`.
**How to avoid:** Extend the `$project` stage in `findMapPoints()` to include `borough` and `cuisine` fields. Also add both to the `RestaurantDAO.findMapPoints()` method signature contract.
**Warning signs:** Filter dropdowns change but shown count doesn't change.

### Pitfall 2: Class-level @PreAuthorize on InspectionController Blocking Public Endpoints
**What goes wrong:** `GET /api/inspection/uncontrolled` returns 403 even though no auth is intended, because the class-level `@PreAuthorize("hasRole('ADMIN')")` applies to all methods.
**Why it happens:** Spring Security evaluates class-level security annotations before method-level ones, and there is no way to "opt out" with a method-level annotation.
**How to avoid:** Move `@PreAuthorize("hasRole('ADMIN')")` from the class annotation on `InspectionController` to each of the two existing methods (`getAtRisk`, `exportAtRiskCsv`). Leave new methods unannotated.
**Warning signs:** 403 on the new endpoints even without a JWT token.

### Pitfall 3: markers Variable Scope in inspection-map.html
**What goes wrong:** Filter callbacks throw `ReferenceError: markers is not defined` because `markers` is declared with `const` inside `loadMapPoints()`.
**Why it happens:** Current code has `const markers = L.markerClusterGroup()` inside the function body (line 119).
**How to avoid:** Promote `allPoints` and `markers` to module-level `let` declarations before `loadMapPoints()`. Change `const markers = L.markerClusterGroup()` inside the function to `markers = L.markerClusterGroup()`.
**Warning signs:** TypeError or ReferenceError in browser console when filter inputs change.

### Pitfall 4: restaurant.html Nearby Section Triggered When No Coordinates
**What goes wrong:** Fetch to `/api/restaurants/nearby` fires with `lat=null&lng=null`, returning an error or irrelevant results.
**Why it happens:** `r.latitude` and `r.longitude` can be undefined if the restaurant has no GPS data.
**How to avoid:** Guard with `if (r.latitude && r.longitude)` before rendering the nearby section div and before calling the API. The CONTEXT.md decision confirms "if the restaurant document has no lat/lng, the Nearby section is not rendered at all."
**Warning signs:** 400 or 500 response from `/api/restaurants/nearby` for some restaurant detail pages.

### Pitfall 5: daysSinceInspection Computation Overflow in BSON
**What goes wrong:** `$toLong` on a BSON Date returns milliseconds (a large long), which divided by 86400000 gives days — but intermediate results in a `$divide` BSON expression can lose precision if not handled carefully.
**Why it happens:** BSON arithmetic expressions use 64-bit doubles by default.
**How to avoid:** Compute `daysSinceInspection` in Java after the aggregation fetch using `(System.currentTimeMillis() - lastInspectionMs) / 86_400_000L` during result mapping, or use a `$project` with `$divide` and `$subtract` directly. The integer division approach is simplest.

### Pitfall 6: Search Results score Field May Be latestScore in View
**What goes wrong:** Sort by score produces no reordering because `r.score` is undefined (the field is named `latestScore` in the toView() map).
**Why it happens:** `RestaurantService.toView()` maps the field as `latestScore`, not `score`. Search results returned by `/api/restaurants/search` use this same view.
**How to avoid:** Use `r.latestScore` (not `r.score`) in the sort comparator on landing.html. Verify against the actual API response before writing the sort function.

---

## Code Examples

Verified patterns from existing codebase:

### Adding borough to findMapPoints Projection
```java
// Source: RestaurantDAOImpl.findMapPoints() — extend $project
new Document("$project", new Document("_id", 0)
    .append("restaurantId", "$restaurant_id")
    .append("name", 1)
    .append("grade", new Document("$arrayElemAt", Arrays.asList("$grades.grade", 0)))
    .append("borough", 1)          // ADD THIS
    .append("cuisine", 1)          // ADD THIS
    .append("lat",  new Document("$arrayElemAt", Arrays.asList("$address.coord", 1)))
    .append("lng",  new Document("$arrayElemAt", Arrays.asList("$address.coord", 0)))
)
```

### Uncontrolled endpoint (InspectionController pattern)
```java
// Source: modeled on InspectionController.getAtRisk() — no @PreAuthorize
@Operation(summary = "Uncontrolled restaurants")
@GetMapping("/uncontrolled")
public ResponseEntity<Map<String, Object>> getUncontrolled(
        @RequestParam(required = false) String borough,
        @RequestParam(defaultValue = "500") int limit) {
    try {
        List<UncontrolledEntry> data = restaurantDAO.findUncontrolled(borough, limit);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", data);
        response.put("count", data.size());
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        return errorResponse(e);
    }
}
```

### CSV export (copy pattern from exportAtRiskCsv)
```java
// Source: InspectionController.exportAtRiskCsv() — same structure
@GetMapping("/uncontrolled/export.csv")
public ResponseEntity<byte[]> exportUncontrolledCsv(
        @RequestParam(required = false) String borough,
        @RequestParam(defaultValue = "5000") int limit) {
    // columns: restaurant_id,name,borough,cuisine,lastGrade,lastScore,daysSinceInspection
}
```

### ViewController route (pattern from analytics())
```java
// Source: ViewController.java — same minimal pattern
@GetMapping("/uncontrolled")
public String uncontrolled() {
    return "uncontrolled";
}
```

### Test pattern for new ViewController route
```java
// Source: ViewControllerAnalyticsTest.java
@ExtendWith(MockitoExtension.class)
public class ViewControllerUncontrolledTest {
    @InjectMocks private ViewController viewController;

    @Test
    public void testUncontrolledPage_returnsView() {
        assertEquals("uncontrolled", viewController.uncontrolled());
    }
}
```

### Test pattern for new InspectionController endpoints
```java
// Source: AnalyticsControllerTest.java pattern
@ExtendWith(MockitoExtension.class)
class InspectionControllerUncontrolledTest {
    @Mock  private RestaurantDAO restaurantDAO;
    @InjectMocks private InspectionController inspectionController;
    private MockMvc mockMvc;

    @BeforeEach void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(inspectionController).build();
    }
    // test getUncontrolled returns 200 with data array
    // test exportUncontrolledCsv returns content-type text/csv
}
```

**Important:** `InspectionController` currently `@Autowired RestaurantService`. For the uncontrolled query to follow the established pattern (`@Mock RestaurantDAO` directly), either:
(a) inject `RestaurantDAO` directly into `InspectionController` for the new methods (consistent with AnalyticsController), or
(b) stub `RestaurantService` — but Mockito constructor-injection mocking fails on Java 25.
**Recommended:** Inject `RestaurantDAO` directly in `InspectionController` (same as AnalyticsController) and call `restaurantDAO.findUncontrolled()` directly. Remove the `RestaurantService` dependency from `InspectionController` if it is only used for at-risk queries (or keep both injections if needed for existing at-risk endpoints).

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Local `const markers` inside loadMapPoints | Module-level `let markers` + `let allPoints` | Phase 8 refactor | Enables client-side filter callbacks |
| At-risk only covers grade C/Z | Uncontrolled covers grade C/Z OR 12+ months uninspected | Phase 8 | Wider safety net |

**Deprecated/outdated:**
- Class-level `@PreAuthorize` on InspectionController: will be replaced by method-level annotations in Plan 08-01.

---

## Open Questions

1. **`RestaurantService.toView()` field name for score**
   - What we know: `renderHeader()` in restaurant.html reads `r.latestScore`. Search results in landing.html render grade badge but do not currently display score.
   - What's unclear: Whether the search result objects returned by `/api/restaurants/search` → `toView()` include `latestScore` reliably (restaurant may have empty grades array).
   - Recommendation: Inspect `RestaurantService.toView()` source before writing the sort comparator. Use `(r.latestScore ?? null)` with null-safe sorting.

2. **InspectionController injection strategy**
   - What we know: Current `InspectionController` injects `RestaurantService` (field `@Autowired`). Java 25 + Mockito cannot mock constructor-injected services.
   - What's unclear: Whether at-risk methods should be migrated to `RestaurantDAO` direct injection (breaking change to service layer) or if `RestaurantService` wrapper is fine for existing tests.
   - Recommendation: Inject `RestaurantDAO` directly in `InspectionController` for new uncontrolled methods (as a new `@Autowired RestaurantDAO` field alongside existing `RestaurantService`). Test the new endpoints by mocking DAO directly, keep existing at-risk tests unchanged.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5.8.2 + Mockito 5.x (via Spring Boot 2.6.15 BOM + project override) |
| Config file | none — convention-based Maven Surefire |
| Quick run command | `mvn test -Dtest=ViewControllerUncontrolledTest,InspectionControllerUncontrolledTest -DfailIfNoTests=false` |
| Full suite command | `mvn test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DISC-01 | Map filter bar wired correctly | manual | Manual browser test (client-side JS only) | N/A — no Java code |
| DISC-02 | GET /api/inspection/uncontrolled returns 200 with data array | unit | `mvn test -Dtest=InspectionControllerUncontrolledTest#testUncontrolled_returns200` | ❌ Wave 0 |
| DISC-02 | GET /api/inspection/uncontrolled/export.csv returns text/csv | unit | `mvn test -Dtest=InspectionControllerUncontrolledTest#testExportCsv_returnsTextCsv` | ❌ Wave 0 |
| DISC-02 | GET /uncontrolled returns "uncontrolled" view | unit | `mvn test -Dtest=ViewControllerUncontrolledTest#testUncontrolledPage_returnsView` | ❌ Wave 0 |
| DISC-03 | Nearby section rendering | manual | Manual browser test (client-side JS) | N/A — no Java code |
| DISC-04 | Search sort control | manual | Manual browser test (client-side JS) | N/A — no Java code |

### Sampling Rate
- **Per task commit:** `mvn test -Dtest=ViewControllerUncontrolledTest,InspectionControllerUncontrolledTest -DfailIfNoTests=false`
- **Per wave merge:** `mvn test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/com/aflokkat/controller/ViewControllerUncontrolledTest.java` — covers DISC-02 view route
- [ ] `src/test/java/com/aflokkat/controller/InspectionControllerUncontrolledTest.java` — covers DISC-02 endpoints

*(DISC-01, DISC-03, DISC-04 are pure client-side JS — no automated test infrastructure needed beyond what exists)*

---

## Sources

### Primary (HIGH confidence)
- Direct source read: `InspectionController.java` — class-level @PreAuthorize location confirmed at line 29
- Direct source read: `RestaurantDAOImpl.java` — findAtRiskRestaurants, findRecentlyInspected, findMapPoints implementations confirmed
- Direct source read: `RestaurantController.java` — /nearby endpoint confirmed at line 313, /cuisines at line 132
- Direct source read: `inspection-map.html` — markers ClusterGroup declared local (line 119), borough select at line 70
- Direct source read: `restaurant.html` — lat/lng read from `r.latitude`/`r.longitude` in renderMap(), history card at line 168
- Direct source read: `landing.html` — doSearch() pattern, gradeBadgeHtml(), search-results div
- Direct source read: `index.html` — mini-card pattern, renderRestaurantCards() function at line 87
- Direct source read: `AnalyticsControllerTest.java` — standaloneSetup + @Mock DAO pattern confirmed
- Direct source read: `RestaurantDAO.java` — interface listing; findMapPoints, findNearby, findAtRiskRestaurants confirmed
- Direct source read: `AtRiskEntry.java` — @BsonProperty field mapping pattern for new UncontrolledEntry DTO

### Secondary (MEDIUM confidence)
- STATE.md accumulated decisions — Java 25 Mockito constraints, @WebMvcTest ban, direct DAO injection pattern

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in use, no new dependencies
- Architecture: HIGH — patterns directly observed in existing source files
- Pitfalls: HIGH — most are derived from direct source inspection (variable scope, class annotation, projection gap)
- Validation: HIGH — test framework and patterns confirmed from existing tests

**Research date:** 2026-04-03
**Valid until:** 2026-05-03 (stable stack; no fast-moving dependencies)
