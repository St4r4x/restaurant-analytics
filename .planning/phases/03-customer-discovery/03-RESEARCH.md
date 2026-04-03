# Phase 3: Customer Discovery - Research

**Researched:** 2026-03-31
**Domain:** Spring Boot REST endpoints + Thymeleaf template enhancements + Leaflet.js clustering
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Search UI**
- Search bar lives on the existing `index.html` — no new route needed
- Real-time as-you-type (debounced ~300ms), no submit button required
- Each result card shows: restaurant name + borough + grade badge only
- Max 20 results returned; empty query hides results and restores the normal dashboard
- Grade badge colors: green = A, yellow = B, red = C/F (same coding across all UI surfaces)
- Clicking a result navigates to `/restaurant/{id}`

**Map performance**
- Leaflet.markerCluster plugin for 27K markers — clusters at zoom-out, individual markers at zoom-in
- New lightweight endpoint: `GET /api/restaurants/map-points` returns only `[id, name, lat, lon, grade]` per restaurant
- Clicking a marker opens a Leaflet popup: restaurant name + grade badge + "View details" link to `/restaurant/{id}`
- `inspection-map.html` is enhanced (not rewritten) to load from the new map-points endpoint and use clustering

**Restaurant detail**
- Grade badge: large colored letter (circle) — green A, yellow B, red C/F — in the existing header-card
- Cleanliness score: numeric score from the most recent inspection record (latest from MongoDB)
- NYC inspection history: chronological table (newest first), all entries — columns: date | grade | score | violations
- Table rendered inside an existing white card, consistent with current `restaurant.html` style
- `restaurant.html` is enhanced (not rewritten)

**Bookmark UX**
- Unauthenticated click on Bookmark → redirect to `/login`
- Bookmark button shows visual toggle: `+ Bookmark` (unsaved) vs `✓ Saved` (filled/colored when saved)
- Toggle state loaded on page load by calling `GET /api/users/me/bookmarks` (only when JWT present in localStorage)
- Saved bookmarks list lives at `/my-bookmarks` — new route in `ViewController` + new `my-bookmarks.html` template
- `/my-bookmarks` shows bookmarked restaurants as cards (name, borough, grade) — same card style as index.html
- Bookmark button also present on search result cards in index.html (same toggle logic)

### Claude's Discretion
- Leaflet.markerCluster CDN URL and version
- Debounce implementation (inline `setTimeout` or a small utility function)
- Exact CSS for the grade badge circle (size, font, border-radius)
- Empty state message for `/my-bookmarks` when no bookmarks saved
- Loading spinner/state while search API responds

### Deferred Ideas (OUT OF SCOPE)
- None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CUST-01 | Customer can search restaurants by name or address and see a list of results with hygiene grade | `searchByNameOrAddress` MongoDB `$regex` query on `name` + `address.street`; new `GET /api/restaurants/search?q=` endpoint in RestaurantController; inline JS debounce in index.html |
| CUST-02 | Customer can view a restaurant detail page showing hygiene grade, cleanliness score, and NYC inspection history | `RestaurantService.toView()` already provides `latestGrade`, `latestScore`, and `grades` list; `restaurant.html` needs grade badge circle + history table; existing fetch pattern works as-is (endpoint public) |
| CUST-03 | Customer can browse restaurants on an interactive map with grade-colored markers that do not freeze | `findMapPoints()` projection-only MongoDB query; new `GET /api/restaurants/map-points` endpoint; Leaflet.markerCluster 1.5.3 CDN; `inspection-map.html` enhanced to load from new endpoint with clustering |
| CUST-04 | Customer can bookmark a restaurant and view their saved bookmarks list | Existing `UserController` bookmark API wired into JS toggle on detail page and search cards; new `/my-bookmarks` view route + `my-bookmarks.html` template; `loadBookmarkedIds()` pattern from index.html reused |
</phase_requirements>

---

## Summary

Phase 3 is a pure extension phase. No new backend domains, no new security boundaries. All four requirements reduce to: two new MongoDB DAO methods, two new REST endpoints on `RestaurantController`, one new view route on `ViewController`, and targeted HTML/JS additions to three existing templates plus one new template.

The most critical technical risk is the map's 27K-marker performance. The solution is already decided: a projection-only MongoDB query sends only `[id, name, lat, lon, grade]` to the client, and `Leaflet.markerCluster` handles browser-side clustering. Both the query pattern (projection pipeline) and the CDN URL are established.

The bookmark toggle logic is already fully implemented in `index.html` (`toggleBookmark`, `bookmarkedIds` Set, `fetchWithAuth`). All new surfaces — search result cards, the detail page button, and `/my-bookmarks` — reuse the exact same pattern. The only new backend work is the two DAO methods and two controller endpoints.

**Primary recommendation:** Implement in three plans: (1) search endpoint + index.html search bar, (2) detail page grade badge/bookmark enhancements + `/my-bookmarks` page, (3) map-points endpoint + inspection-map.html clustering migration.

---

## Standard Stack

### Core (already present — no new installs)
| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| Spring Boot | 2.6.15 | Framework | Java 11 |
| mongodb-driver-sync | bundled | Raw MongoDB queries | POJO codec registry pattern established |
| Leaflet.js | 1.9.4 | Base map | CDN: cdnjs.cloudflare.com, already loaded in all 3 map templates |
| Thymeleaf | bundled | HTML templates | No Spring Model attributes used — all data loaded via client-side fetch |

### New Additions
| Library | Version | Purpose | CDN |
|---------|---------|---------|-----|
| leaflet.markercluster | 1.5.3 | Cluster 27K markers without freeze | jsDelivr (see Registry section) |

**No Java dependency changes required.** `leaflet.markercluster` is loaded via CDN only.

### CDN URLs (canonical — use exactly as written)
```html
<!-- Add after leaflet.min.js in inspection-map.html -->
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet.markercluster@1.5.3/dist/MarkerCluster.css" />
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet.markercluster@1.5.3/dist/MarkerCluster.Default.css" />
<script src="https://cdn.jsdelivr.net/npm/leaflet.markercluster@1.5.3/dist/leaflet.markercluster.js"></script>
```
These are verified from `03-UI-SPEC.md` (safety-checked 2026-03-31).

---

## Architecture Patterns

### Recommended Project Structure Changes
```
src/main/java/com/aflokkat/
├── dao/
│   ├── RestaurantDAO.java          # Add: searchByNameOrAddress, findMapPoints
│   └── RestaurantDAOImpl.java      # Implement both with existing aggregate() helper
├── controller/
│   ├── RestaurantController.java   # Add: GET /search, GET /map-points (no @PreAuthorize)
│   └── ViewController.java         # Add: GET /my-bookmarks
src/main/resources/templates/
├── index.html                      # Add: search card + JS debounce + search result rendering
├── restaurant.html                 # Add: grade badge circle + bookmark button
├── inspection-map.html             # Migrate: heatmap → clustered grade markers
└── my-bookmarks.html               # New template
src/test/java/com/aflokkat/
└── controller/
    └── RestaurantControllerSearchTest.java  # New: unit tests for search + map-points
```

### Pattern 1: MongoDB `$regex` Search
**What:** Case-insensitive prefix/fragment search on `name` and `address.street` fields.
**When to use:** CUST-01 — user types partial restaurant name or street address.

```java
// Follows existing aggregate() helper pattern in RestaurantDAOImpl
// Source: RestaurantDAOImpl.java findWithFilters/findAll pattern

public List<Restaurant> searchByNameOrAddress(String q, int limit) {
    Document regex = new Document("$regex", q).append("$options", "i");
    Document filter = new Document("$or", Arrays.asList(
        new Document("name", regex),
        new Document("address.street", regex)
    ));
    List<Restaurant> results = new ArrayList<>();
    restaurantCollection.find(filter).limit(limit).forEach(results::add);
    return results;
}
```

**Index note:** STATE.md blocker documents: "Verify whether a MongoDB index on `name` / `address.street` already exists before implementing `$regex` search." A text index (`$text` / `$search`) is faster for large datasets but `$regex` with `"i"` option works without an index change. For 27K documents with a limit of 20, `$regex` latency will be acceptable (<200ms) without a dedicated index. A dedicated index is not required for Phase 3 to meet its success criteria. However, a Wave 0 task should verify or create an index on `name` and `address.street`.

### Pattern 2: Projection-Only Map Points Query
**What:** MongoDB `$project` pipeline that returns only the 5 fields needed for map rendering. Avoids sending full `grades` arrays (which can be large) for 27K records.
**When to use:** CUST-03 — `GET /api/restaurants/map-points`.

```java
// Source: RestaurantDAOImpl.findHeatmapData pattern (same projection approach)

public List<Document> findMapPoints() {
    List<Document> pipeline = Arrays.asList(
        new Document("$match", new Document("address.coord", new Document("$exists", true))),
        new Document("$project", new Document("_id", 0)
            .append("restaurantId", "$restaurant_id")
            .append("name", 1)
            .append("grade", new Document("$arrayElemAt", Arrays.asList("$grades.grade", 0)))
            .append("lat",  new Document("$arrayElemAt", Arrays.asList("$address.coord", 1)))
            .append("lng",  new Document("$arrayElemAt", Arrays.asList("$address.coord", 0)))
        )
    );
    List<Document> results = new ArrayList<>();
    database.getCollection(AppConfig.getMongoCollection())
            .aggregate(pipeline).forEach(results::add);
    return results;
}
```

**Return type:** `List<Document>` (not `List<Restaurant>`) — no POJO needed for this lightweight projection. The controller wraps it directly in the response map.

**Important:** `grades[0]` in the raw MongoDB array is the MOST RECENT grade (NYC Open Data stores newest-first). This matches `RestaurantService.getLatestGrade()` which also uses `arrayElemAt(..., 0)` via `getLatestGradeEntry()` finding the max date. Confirm with a sample document before finalizing.

### Pattern 3: New REST Endpoints (No Auth Required)
**What:** Both new endpoints follow the existing `RestaurantController` pattern — try/catch, `ResponseUtil.errorResponse()`, no `@PreAuthorize`.
**When to use:** CUST-01 and CUST-03. Per Phase 1 decisions: `/api/restaurants/**` is fully public.

```java
// Source: RestaurantController.java — existing getByBorough() pattern

@GetMapping("/search")
public ResponseEntity<Map<String, Object>> searchRestaurants(
        @RequestParam String q,
        @RequestParam(defaultValue = "20") int limit) {
    try {
        List<Restaurant> data = restaurantDAO.searchByNameOrAddress(q, limit);
        List<Map<String, Object>> views = data.stream()
            .map(RestaurantService::toView).collect(Collectors.toList());
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", views);
        response.put("count", views.size());
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        return errorResponse(e);
    }
}

@GetMapping("/map-points")
public ResponseEntity<Map<String, Object>> getMapPoints() {
    try {
        List<Document> data = restaurantDAO.findMapPoints();
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

### Pattern 4: JS Debounce (Inline `setTimeout`)
**What:** Established in `03-CONTEXT.md` and `03-UI-SPEC.md`. No external library.

```javascript
// Source: 03-UI-SPEC.md — Interaction Contract
let searchTimer = null;
searchInput.addEventListener('input', function() {
    clearTimeout(searchTimer);
    const q = this.value.trim();
    if (q.length < 2) { hideResults(); return; }
    searchTimer = setTimeout(() => doSearch(q), 300);
});
```

### Pattern 5: Bookmark Toggle (Reuse Existing Logic)
**What:** `index.html` already has the full implementation: `bookmarkedIds` Set, `fetchWithAuth`, `toggleBookmark()`, `refreshBookmarkButtons()`. These functions must be copied verbatim into `restaurant.html` and `my-bookmarks.html` — not reimplemented.
**JWT key name:** `localStorage.getItem("accessToken")` — confirmed in `index.html` line 811, `inspection-map.html` line 79, `restaurant.html` line 169.

### Pattern 6: Leaflet.markerCluster Usage
```javascript
// Source: Leaflet.markerCluster README + 03-UI-SPEC.md

// After leaflet.markercluster.js is loaded:
const markers = L.markerClusterGroup();

data.forEach(r => {
    if (!r.lat || !r.lng) return;
    const color = r.grade === 'A' ? '#22c55e' : r.grade === 'B' ? '#eab308' : '#ef4444';
    const icon = L.divIcon({
        className: '',
        html: `<div style="width:12px;height:12px;background:${color};border:2px solid #fff;border-radius:50%;box-shadow:0 1px 4px rgba(0,0,0,0.4)"></div>`,
        iconSize: [12, 12], iconAnchor: [6, 6]
    });
    const marker = L.marker([r.lat, r.lng], { icon });
    marker.bindPopup(`
        <div style="font-size:0.88em;max-width:200px">
            <strong>${r.name}</strong><br>
            Grade: <span style="font-weight:700">${r.grade || '—'}</span><br>
            <a href="/restaurant/${r.restaurantId}" style="color:#667eea;font-weight:600">View details →</a>
        </div>`);
    markers.addLayer(marker);
});

map.addLayer(markers);
```

### Pattern 7: `inspection-map.html` Auth Change
**Critical discovery:** The current `inspection-map.html` redirects to `/login` if no token is present (`if (!token) window.location.href = "/login"`). Under Phase 3, the map must be **publicly accessible** — no redirect. The existing heatmap endpoint (`/api/restaurants/heatmap`) required auth (it has `@PreAuthorize("hasRole('ADMIN')")`), but the new `/api/restaurants/map-points` is fully public.

The JS must be changed from:
```javascript
// REMOVE — old auth guard
const token = localStorage.getItem("accessToken");
if (!token) window.location.href = "/login";
```
to: no auth guard (the endpoint is public, the map page is open to all).

### Pattern 8: `restaurant.html` Auth Removal
Same issue: `restaurant.html` currently redirects unauthenticated users to `/login`. Under Phase 3, the detail page must be accessible without login (customers need to see it from search results). The existing `GET /api/restaurants/{id}` endpoint is already public (no `@PreAuthorize`). The JS redirect guard must be removed.

The bookmark button, however, DOES require auth — handle this by checking token before making bookmark API calls (redirect to `/login` only on bookmark click, not on page load).

### Pattern 9: Grade Badge Circle CSS (detail page)
```css
/* Source: 03-UI-SPEC.md Component Inventory #3 */
.grade-circle {
    width: 56px; height: 56px;
    border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    font-size: 1.8em; font-weight: 700;
    flex-shrink: 0;
}
.grade-circle-A { background: #e8f5e9; color: #2e7d32; border: 2px solid #4caf50; }
.grade-circle-B { background: #fff8e1; color: #f57f17; border: 2px solid #ffc107; }
.grade-circle-CF { background: #ffebee; color: #b71c1c; border: 2px solid #f44336; }
```

### Anti-Patterns to Avoid
- **Using `$text` / `$search` for the search endpoint:** Requires a text index that may not exist; `$regex` works without schema changes.
- **Sending full Restaurant POJO for map-points:** Full documents include `grades` arrays with dozens of entries each — this would transfer megabytes for 27K restaurants. Use the projection pipeline.
- **Adding `@PreAuthorize` to the new endpoints:** `/api/restaurants/**` is fully public per Phase 1 security decisions. Never add auth guards to these endpoints.
- **Re-implementing bookmark logic:** `toggleBookmark()`, `fetchWithAuth()`, `bookmarkedIds` already exist in `index.html`. Copy, don't rewrite.
- **Rewriting `inspection-map.html`:** The CONTEXT.md explicitly says "enhanced, not rewritten." Keep the toolbar, borough filter, and map initialization structure; replace the data loading and rendering.
- **Using `restaurant.restaurantId` vs `r.restaurantId`:** The `toView()` map uses `"restaurantId"` key (not `"restaurant_id"`). Confirmed in `RestaurantService.java` line 266. Use `r.restaurantId` in JS.
- **Assuming `grades[0]` is always the latest:** The `$arrayElemAt` projection picks index 0 because NYC data stores grades newest-first. This is consistent with `RestaurantService.getLatestGradeEntry()`. However, if data integrity is suspect, the map-points pipeline should add a `$sort` on grades by date inside `$project` using `$reduce` or `$first`/`$sort` stage — this is a LOW confidence concern (the existing code's use of index 0 confirms the convention is established).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Map marker clustering for 27K points | Custom quad-tree / viewport culling | `leaflet.markercluster` 1.5.3 | Browser freezes at ~1K unmanaged DOM markers; markerCluster is battle-tested and CDN-available |
| Search debounce | Underscore/Lodash `debounce` | Inline `setTimeout`/`clearTimeout` pattern | Project uses zero npm; no bundler; one-liner pattern already established in codebase |
| Authentication-aware fetch | Custom fetch wrapper | `fetchWithAuth()` from `index.html` | Already handles 401/403 redirect, token injection — copy verbatim |
| Bookmark state management | Custom store/event bus | `bookmarkedIds` Set + `refreshBookmarkButtons()` from `index.html` | Already implemented and tested in production use on index.html |
| Grade color logic | New switch statement | Existing `badgeColor` from `RestaurantService.toView()` plus hex constants from UI-SPEC | Color contract is centralized in UI-SPEC: `#22c55e` / `#eab308` / `#ef4444` for markers; `#e8f5e9`/`#2e7d32` etc. for badges |

---

## Common Pitfalls

### Pitfall 1: `inspection-map.html` Requires Auth Today
**What goes wrong:** Current map page has `if (!token) window.location.href = "/login"` at the top and fetches from `/api/restaurants/heatmap` which requires ADMIN role. Failing to remove the auth guard or failing to change the fetch URL will break CUST-03 for unauthenticated customers.
**Why it happens:** The old map was a controller-only tool; the new map is a customer-facing page.
**How to avoid:** Remove the token guard entirely. Change the fetch from `/api/restaurants/heatmap?limit=500` to `/api/restaurants/map-points`. No auth headers needed.
**Warning signs:** If the map redirects unauthenticated users to login, this pitfall has been hit.

### Pitfall 2: `restaurant.html` Requires Auth Today
**What goes wrong:** Same issue — current detail page redirects unauthenticated users to `/login`. CUST-02 requires customers to view the detail page without being logged in.
**Why it happens:** Original implementation assumed all page viewers were logged-in controllers.
**How to avoid:** Remove `if (!token) window.location.href = "/login"` from `restaurant.html`. Retain auth-checking only for the bookmark button click handler.
**Warning signs:** Unauthenticated access to `/restaurant/{id}` redirects to login page.

### Pitfall 3: `index.html` Requires Auth Today (Search Must Work for Unauthenticated Users)
**What goes wrong:** `index.html` line 811-813 redirects to `/login` if no `accessToken` in localStorage. The search bar is being added to `index.html`. If customers can't reach the dashboard without logging in, CUST-01 breaks.
**Why it happens:** The dashboard was originally a logged-in-only view.
**How to avoid:** The CONTEXT.md says search bar lives on `index.html` with no new route needed. This implies either (a) the auth guard on index.html must be relaxed or (b) customers are expected to be logged in to use search. The ROADMAP success criteria says "A customer can type…" without mention of auth, suggesting the page should work for all users. **Decision needed:** Either relax the index.html auth guard, or the search endpoint is accessible but the page itself still requires login (consistent with current behavior — customers register/log in as ROLE_CUSTOMER). Given CUST-04 references bookmarking from search results, and bookmarking requires auth, it is likely intentional that customers log in as ROLE_CUSTOMER. The bookmark button on search cards handles unauthenticated clicks via redirect. No change to the index.html login guard may be needed. **Flag this for planning.**
**Warning signs:** If the search card is present but the whole page redirects before showing it, the guard is too broad.

### Pitfall 4: `data.restaurantId` vs `data.restaurant_id` in Bookmark API Response
**What goes wrong:** `UserController.getBookmarks()` returns `List<Restaurant>` (full POJO) serialized via Jackson. Jackson will serialize `restaurantId` as the Java getter name. But the `loadBookmarkedIds()` function in `index.html` uses `r.restaurantId` for the Set. This must be consistent in all new templates.
**Why it happens:** Jackson serializes `getRestaurantId()` as `restaurantId`. The `toView()` map explicitly sets `"restaurantId"`. Both agree. But `GET /api/users/me/bookmarks` returns raw `Restaurant` objects, not `toView()` maps — so the field name may differ.
**How to avoid:** When reading bookmarks, use `r.restaurantId` (as `index.html` already does at line 1268: `r.restaurantId`). Verify: `Restaurant.getRestaurantId()` → Jackson → `"restaurantId"`. The `@BsonProperty("restaurant_id")` annotation is for MongoDB, not Jackson — Jackson uses the getter name. Confirmed safe.

### Pitfall 5: MongoDB `$arrayElemAt` Index 0 for Grade
**What goes wrong:** The `findMapPoints()` projection uses `$arrayElemAt: ["$grades.grade", 0]` assuming index 0 is the latest. If any restaurant has grades stored in ascending date order, the wrong grade will be shown on the map.
**Why it happens:** NYC Open Data documents store grades in reverse-chronological order (newest first), but this is a convention, not a schema guarantee.
**How to avoid:** The existing `RestaurantService.getLatestGradeEntry()` uses `max(Comparator.comparing(Grade::getDate))` for precision — but this requires the full grades array. For the lightweight map-points endpoint, accepting the convention is a pragmatic tradeoff. If visual accuracy is critical, add `$first` after `$sort` within the pipeline: `$push` + `$sortArray` (MongoDB 5.2+) or `$unwind` / `$sort` / `$group`. At MongoDB driver version for Spring Boot 2.6.15, use `$unwind` + `$sort` + `$group` approach if needed.
**Warning signs:** A restaurant known to have grade A appears as C on the map.

### Pitfall 6: `my-bookmarks.html` Tries to Load Data Without Auth
**What goes wrong:** `/my-bookmarks` calls `GET /api/users/me/bookmarks` which requires JWT. If no token, the API returns 401, `fetchWithAuth` redirects to `/login`. This is correct behavior but the page should handle the no-token case before making the API call.
**How to avoid:** Follow the same pattern as the index.html bookmark section: `if (!localStorage.getItem("accessToken")) return;` before fetching. Or use `fetchWithAuth` and let its built-in 401 redirect handle it.

### Pitfall 7: `ViewController` Adds `/my-bookmarks` Without Thymeleaf Dependency
**What goes wrong:** `ViewController.java` returns template names. Adding a new route `return "my-bookmarks"` requires `my-bookmarks.html` to exist in `src/main/resources/templates/`. If the template is missing, Spring throws `TemplateInputException` at runtime.
**How to avoid:** Always create the template file in the same task as the controller route. Never add the view route before the template exists.

### Pitfall 8: MarkerCluster CSS Load Order
**What goes wrong:** Loading `leaflet.markercluster.js` before `leaflet.min.js` throws `L is not defined`. Loading the CSS after the JS may cause flash of unstyled clusters.
**How to avoid:** Load order must be: `leaflet.min.css` → `MarkerCluster.css` → `MarkerCluster.Default.css` → `leaflet.min.js` → `leaflet.markercluster.js`. This matches the `03-UI-SPEC.md` registry entry ("Load after leaflet.min.js").

---

## Code Examples

### Search result card HTML pattern (from index.html `.top-restaurant-item`)
```html
<!-- Reuse existing CSS classes — no new styles needed -->
<div class="top-restaurant-item" style="border-left-color: {gradeColor}">
    <div class="top-restaurant-info">
        <div class="top-restaurant-name">{name}</div>
        <div class="top-restaurant-meta">{borough} · {cuisine}</div>
    </div>
    <span class="grade-badge grade-badge-{A|B|C}">{grade}</span>
    <button class="btn-bookmark" data-id="{restaurantId}"
            onclick="toggleBookmark('{restaurantId}', this)">★</button>
    <a class="btn-view" href="/restaurant/{restaurantId}">View →</a>
</div>
```

Grade badge inline style (no new CSS class needed, matches UI-SPEC):
```html
<!-- Grade A -->
<span style="display:inline-block;padding:2px 8px;border-radius:12px;font-weight:700;font-size:0.82em;background:#e8f5e9;color:#2e7d32">A</span>
<!-- Grade B -->
<span style="display:inline-block;padding:2px 8px;border-radius:12px;font-weight:700;font-size:0.82em;background:#fff8e1;color:#f57f17">B</span>
<!-- Grade C/F/other -->
<span style="display:inline-block;padding:2px 8px;border-radius:12px;font-weight:700;font-size:0.82em;background:#ffebee;color:#b71c1c">{grade}</span>
```

### Grade badge circle (restaurant.html detail page)
```html
<div class="header-card" id="header-card">
    <div class="header-info"> ... </div>
    <!-- New: grade circle + bookmark button on right side of header-card flex row -->
    <div style="display:flex;flex-direction:column;align-items:center;gap:12px;flex-shrink:0">
        <div id="grade-circle" style="width:56px;height:56px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:1.8em;font-weight:700"></div>
        <button id="bookmark-btn" onclick="handleBookmarkClick()">+ Bookmark</button>
    </div>
</div>
```

### ViewController new route
```java
// Source: ViewController.java pattern — add alongside existing routes
@GetMapping("/my-bookmarks")
public String myBookmarks() {
    return "my-bookmarks";
}
```

### RestaurantDAO interface additions
```java
// Add to RestaurantDAO.java interface
List<Restaurant> searchByNameOrAddress(String q, int limit);
List<Document> findMapPoints();
```

Note: `findMapPoints()` returns `List<Document>` (not `List<Restaurant>`) to avoid loading the full POJO for the projection result.

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Heatmap via `/api/restaurants/heatmap` (ADMIN only, score-based) | Grade-colored markers via `/api/restaurants/map-points` (public, grade-based) | CUST-03: map is now customer-facing |
| All map data in `Restaurant` POJO (includes full grades array) | Projection to 5 fields only for map endpoint | Performance: 5× smaller payload for 27K records |
| No search capability | `$regex` on name/address.street | CUST-01: first-time search feature |
| No public-facing detail page | `restaurant.html` accessible without auth | CUST-02: detail page visible to all |

---

## Open Questions

1. **Should `index.html` remain auth-gated after Phase 3?**
   - What we know: Current `index.html` redirects to `/login` if no `accessToken`. CUST-01 places the search bar on `index.html`. The ROADMAP says "A customer can search" without explicit mention of auth state.
   - What's unclear: Whether customers are expected to register/login before searching (ROLE_CUSTOMER), or whether search should be truly anonymous.
   - Recommendation: Treat customers as always logged-in (ROLE_CUSTOMER); the existing auth guard stays. The search endpoint is public but the page itself requires login. The bookmark UX (redirect on unauthenticated click) reinforces that customers are expected to have accounts.

2. **Does `grades[0]` reliably hold the latest grade in all documents?**
   - What we know: `RestaurantService.getLatestGradeEntry()` uses date-based max to be safe. The heatmap pipeline uses `$arrayElemAt(0)` as a convention.
   - What's unclear: Whether all 27K documents follow this ordering.
   - Recommendation: Use `$arrayElemAt(0)` in `findMapPoints()` for performance (consistent with existing `findHeatmapData`). If incorrect grades appear on the map during testing, upgrade to `$unwind`/`$sort`/`$group` pipeline.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) via Spring Boot 2.6.15 BOM |
| Config file | none — convention-based `src/test/java/` |
| Quick run command | `mvn test -Dtest=RestaurantControllerSearchTest -pl .` |
| Full suite command | `mvn test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CUST-01 | `GET /api/restaurants/search?q=pizza` returns list with restaurantId, name, borough, grade | unit (MockMvc standaloneSetup) | `mvn test -Dtest=RestaurantControllerSearchTest#testSearch_returnsResults` | ❌ Wave 0 |
| CUST-01 | Empty query `q=` returns 400 or empty results | unit | `mvn test -Dtest=RestaurantControllerSearchTest#testSearch_emptyQuery` | ❌ Wave 0 |
| CUST-02 | `GET /api/restaurants/{id}` response includes `latestGrade`, `latestScore`, `grades` list | unit (RestaurantServiceTest — already exists) | `mvn test -Dtest=RestaurantServiceTest` | ✅ |
| CUST-03 | `GET /api/restaurants/map-points` returns list with lat, lng, grade, restaurantId | unit (MockMvc standaloneSetup) | `mvn test -Dtest=RestaurantControllerSearchTest#testMapPoints_returnsProjection` | ❌ Wave 0 |
| CUST-04 | `GET /api/users/me/bookmarks` returns bookmarked restaurants (existing) | manual / integration | `mvn test -Dtest=ReportControllerTest` (existing; UserController has no dedicated test) | manual only |

Note: CUST-02 and CUST-04 backend logic is covered by existing tests. New test work is concentrated on the two new endpoints (CUST-01 search, CUST-03 map-points). CUST-02 template changes (badge/history) and CUST-04 template changes (`my-bookmarks.html`) are browser-tested at verify time.

### Test Pattern to Follow
Tests MUST use `MockMvcBuilders.standaloneSetup(controller)` (not `@WebMvcTest`) — confirmed by Phase 1 decision: `@WebMvcTest` causes JVM crash on Java 25 runtime. Use the `ReportControllerTest` pattern (Mockito + standaloneSetup, `@ExtendWith(MockitoExtension.class)`).

### Sampling Rate
- **Per task commit:** `mvn test -Dtest=RestaurantControllerSearchTest`
- **Per wave merge:** `mvn test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/com/aflokkat/controller/RestaurantControllerSearchTest.java` — covers CUST-01 search endpoint and CUST-03 map-points endpoint
- [ ] No framework install needed — JUnit 5 already present

---

## Sources

### Primary (HIGH confidence)
- Read directly from codebase: `RestaurantDAOImpl.java`, `RestaurantController.java`, `ViewController.java`, `UserController.java`, `RestaurantService.java`, `Restaurant.java`, `Grade.java`, `RestaurantDAO.java`
- Read directly from codebase: `index.html`, `restaurant.html`, `inspection-map.html`
- Read directly from planning docs: `03-CONTEXT.md`, `03-UI-SPEC.md`, `REQUIREMENTS.md`, `ROADMAP.md`, `STATE.md`

### Secondary (MEDIUM confidence)
- Leaflet.markerCluster 1.5.3 CDN URL: sourced from `03-UI-SPEC.md` registry safety section (safety-checked 2026-03-31)
- MongoDB `$regex` query pattern: consistent with existing `$match` patterns in `RestaurantDAOImpl.java`
- `$arrayElemAt` index-0 convention: consistent with `findHeatmapData()` existing implementation

### Tertiary (LOW confidence)
- MongoDB `$arrayElemAt(0)` always being newest grade: inferred from data convention; not verified against live data
- `$regex` query performance for 27K documents without dedicated index: estimated acceptable for limit=20; not benchmarked

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — fully read from codebase, no external tools needed
- Architecture patterns: HIGH — derived directly from existing code patterns in `RestaurantDAOImpl.java` and `RestaurantController.java`
- Pitfalls: HIGH — pitfalls 1-4 confirmed by reading actual code; pitfalls 5-8 are logical derivations
- Test patterns: HIGH — confirmed from existing `ReportControllerTest.java` and Phase 1 test decisions in `STATE.md`

**Research date:** 2026-03-31
**Valid until:** 2026-05-01 (stable stack, no fast-moving dependencies)
