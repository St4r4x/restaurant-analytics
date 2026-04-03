# Phase 3: Customer Discovery - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Customers can search restaurants by name/address, view a hygiene detail page, browse a grade-colored interactive map, and bookmark favorites. Pure UI/frontend phase — extends existing Thymeleaf templates and adds new REST endpoints (search, map-points). No new backend domains. Controller reports remain invisible to customers.

</domain>

<decisions>
## Implementation Decisions

### Search UI
- Search bar lives on the existing `index.html` — no new route needed
- Real-time as-you-type (debounced ~300ms), no submit button required
- Each result card shows: restaurant name + borough + grade badge only
- Max 20 results returned; empty query hides results and restores the normal dashboard
- Grade badge colors: green = A, yellow = B, red = C/F (same coding across all UI surfaces)
- Clicking a result navigates to `/restaurant/{id}`

### Map performance
- Leaflet.markerCluster plugin for 27K markers — clusters at zoom-out, individual markers at zoom-in
- New lightweight endpoint: `GET /api/restaurants/map-points` returns only `[id, name, lat, lon, grade]` per restaurant (avoids sending full documents to the client)
- Clicking a marker opens a Leaflet popup: restaurant name + grade badge + "View details" link to `/restaurant/{id}`
- `inspection-map.html` is enhanced (not rewritten) to load from the new map-points endpoint and use clustering

### Restaurant detail
- Grade badge: large colored letter (circle) — green A, yellow B, red C/F — in the existing header-card
- Cleanliness score: numeric score from the most recent inspection record (latest from MongoDB)
- NYC inspection history: chronological table (newest first), all entries — columns: date | grade | score | violations
- Table rendered inside an existing white card, consistent with current `restaurant.html` style
- `restaurant.html` is enhanced (not rewritten)

### Bookmark UX
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing templates (extend, do not rewrite)
- `src/main/resources/templates/index.html` — current dashboard layout, card CSS patterns, existing Leaflet + Chart.js CDN imports
- `src/main/resources/templates/restaurant.html` — current detail page structure, header-card layout, existing CSS variables
- `src/main/resources/templates/inspection-map.html` — current map page, Leaflet initialization pattern

### Existing controller and routing
- `src/main/java/com/aflokkat/controller/ViewController.java` — all view routes; add `/my-bookmarks` here
- `src/main/java/com/aflokkat/controller/RestaurantController.java` — add `GET /api/restaurants/search` and `GET /api/restaurants/map-points` here

### Existing bookmark API (wire, don't rebuild)
- `src/main/java/com/aflokkat/controller/UserController.java` — `GET /api/users/me/bookmarks`, `POST /api/users/me/bookmarks/{restaurantId}`, `DELETE /api/users/me/bookmarks/{restaurantId}`

### Requirements
- `.planning/REQUIREMENTS.md` — CUST-01 through CUST-04 (all Phase 3 requirements)
- `.planning/ROADMAP.md` — Phase 3 goal and success criteria (4 success criteria, map freeze/crash explicitly called out)

### Security decisions from Phase 1 (already live)
- `.planning/phases/01-role-infrastructure/01-CONTEXT.md` — `/api/restaurants/**` fully public, view routes open, `/api/users/**` requires JWT

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `index.html` card CSS (`.card`, `.dashboard` grid, white + shadow + border-radius 12px): reuse for search results and `/my-bookmarks` restaurant cards
- Leaflet.js 1.9.4 already loaded via CDN in `index.html`, `restaurant.html`, `inspection-map.html` — no new CDN import needed for Leaflet itself, only add markerCluster
- `GET /api/users/me/bookmarks`: returns `{"status":"success","data":[{restaurantId, ...}]}` — use for toggle state check on page load
- `POST/DELETE /api/users/me/bookmarks/{restaurantId}`: already idempotent — safe to call on toggle

### Established Patterns
- JWT in localStorage: inspect existing `login.html` or `index.html` for the key name used (e.g. `localStorage.getItem('token')`) before writing auth-dependent JS
- Fetch + JSON pattern: existing templates use `fetch('/api/...').then(r => r.json())` inline in `<script>` blocks
- CSS color vars: no CSS variables declared — use hex literals (`#22c55e` green, `#eab308` yellow, `#ef4444` red) consistent with Leaflet map marker decisions

### Integration Points
- `ViewController.java` — add `@GetMapping("/my-bookmarks")` returning `"my-bookmarks"` template, and `@GetMapping("/search")` if a standalone page is ever needed
- `RestaurantController.java` — new `searchRestaurants(@RequestParam String q)` method calling `RestaurantDAO`, and `getMapPoints()` method; both fully public (no `@PreAuthorize`)
- `RestaurantDAO` / `RestaurantDAOImpl.java` — add `searchByNameOrAddress(String q, int limit)` and `findMapPoints()` MongoDB aggregation methods

</code_context>

<specifics>
## Specific Ideas

- Grade color coding is consistent across all surfaces: green A, yellow B, red C/F — this is explicitly stated in the success criteria and must match between map markers, detail badge, and search result cards

</specifics>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope

</deferred>

---

*Phase: 03-customer-discovery*
*Context gathered: 2026-03-31*
