# Phase 8: Discovery Enhancement - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Four targeted enhancements to existing pages and data:
- `/inspection-map` gains a full filter bar (grade checkboxes + borough + cuisine dropdowns) with client-side marker filtering
- A new `/uncontrolled` page lists restaurants with last grade C/Z OR not inspected in 12+ months, with sorting, borough filter, and CSV export
- The restaurant detail page gains a "Nearby restaurants" section (up to 5 within 500m) at the bottom, below inspection history
- The landing page search gains a sort control (Best Score / Worst Score / A→Z) that reorders already-fetched results client-side

No new user-facing auth flows. No changes to the navbar. `/uncontrolled` is public.

</domain>

<decisions>
## Implementation Decisions

### Map filter bar (DISC-01)
- **Layout**: Horizontal top-toolbar strip above the map (below navbar) — extends the existing borough select shell already in the template
- **Grade filter**: Four checkboxes (A / B / C / F), all checked by default — unchecking hides matching markers without a network request
- **Borough filter**: Existing `<select id="borough-filter">` dropdown, already partially wired
- **Cuisine filter**: `<select>` populated from `GET /api/restaurants/cuisines` — full list, no truncation; browser-native `<select>` handles scrolling/search
- **Filtering logic**: Client-side only — all markers are already loaded from `/api/restaurants/map-points`; filter callbacks show/hide layers in the existing `markers` ClusterGroup
- **Feedback**: A count badge updates live: e.g. "312 markers shown" — shown in the toolbar next to the filters
- **Cuisine dropdown load**: Fetched once on page load, populates the select; "All cuisines" as default option

### Uncontrolled page (DISC-02)
- **Route**: `/uncontrolled` → ViewController returns `"uncontrolled"` template; public (no auth)
- **Data criteria**: Wider than the existing at-risk list — last grade C/Z **OR** not inspected in the past 12 months (new backend query)
- **New endpoint**: `GET /api/inspection/uncontrolled` — returns the full uncontrolled dataset
- **New CSV endpoint**: `GET /api/inspection/uncontrolled/export.csv` — exports the uncontrolled dataset (does NOT reuse at-risk CSV, which has different criteria)
- **Table columns**: Name · Borough · Cuisine · Last Grade · Last Score · Days Since Inspection (sortable by score; borough filterable via dropdown)
- **Sorting**: Client-side — default API order; user can click "Sort by Score ↑/↓" to reorder the rendered table
- **No auth required**: Consistent with analytics and restaurant detail pages

### Nearby section on restaurant detail (DISC-03)
- **Placement**: After inspection history (bottom of page) — primary grade/score/history content stays at the top
- **Data**: Calls `GET /api/restaurants/nearby?lat=X&lng=Y&radius=500&limit=5` using the restaurant's own lat/lng
- **Cards**: Each card shows name, grade badge, and a "View Details" link — same mini-card pattern used on index.html bookmarks strip
- **No GPS case**: If the restaurant document has no lat/lng, the Nearby section is not rendered at all (no empty state message)
- **Self-exclusion**: The current restaurant must be excluded from its own nearby results (filter by restaurantId client-side or pass an exclude param)

### Search sort control (DISC-04)
- **Scope**: Landing page only (`landing.html`) — where the search CTA lives
- **Options**: Best Score (lowest numeric score first) / Worst Score (highest score first) / A→Z (alphabetical by name)
- **Default**: No sort — results appear in API order (relevance) when a new search runs
- **Reset behavior**: Selecting a new search query resets sort to default (no sort)
- **Implementation**: Client-side only — sort reorders the already-rendered result list; no new API call
- **Placement**: Sort control appears above the result list, visible only when results are present

### Claude's Discretion
- Exact sort control widget style (select dropdown vs pill buttons)
- CSS for the "X markers shown" badge on the map
- Uncontrolled table row hover, striping, or other visual polish
- Whether the Nearby section has a section heading card or inline heading

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` — DISC-01, DISC-02, DISC-03, DISC-04 acceptance criteria

### Templates to modify
- `src/main/resources/templates/inspection-map.html` — existing borough select shell, Leaflet + markerCluster setup, grade coloring; extend filter bar here
- `src/main/resources/templates/restaurant.html` — restaurant detail page; add Nearby section after inspection history (currently ~line 370+)
- `src/main/resources/templates/landing.html` — search CTA + result list; add sort control above results

### Existing backend to extend
- `src/main/java/com/aflokkat/controller/InspectionController.java` — add `GET /api/inspection/uncontrolled` and `GET /api/inspection/uncontrolled/export.csv`
- `src/main/java/com/aflokkat/controller/RestaurantController.java` — `GET /api/restaurants/nearby` already exists (line 313); `GET /api/restaurants/cuisines` already exists (line 132)
- `src/main/java/com/aflokkat/controller/ViewController.java` — add `GET /uncontrolled` route
- `src/main/java/com/aflokkat/dao/RestaurantDAO.java` + `RestaurantDAOImpl.java` — add `findUncontrolled()` using MongoDB aggregation ($match: grade C/Z OR lastInspection < now-12mo)

### Reference patterns
- `src/main/resources/templates/index.html` — mini-card pattern for bookmarks strip (reuse for Nearby section)
- `src/main/resources/templates/analytics.html` — at-risk table pattern (reference for uncontrolled table structure)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `gradeBadgeHtml(grade)` — defined in landing.html, index.html, dashboard.html — copy into new templates as needed
- `GET /api/restaurants/cuisines` — returns sorted list of all cuisine types; already exists in RestaurantController
- `GET /api/restaurants/nearby` — exists at RestaurantController line 313, takes lat/lng/radius/limit params
- `GET /api/inspection/at-risk` — InspectionController line 37; reference for uncontrolled query structure
- `GET /api/inspection/at-risk/export.csv` — InspectionController line 54; reference implementation for CSV export
- Mini-card pattern from index.html bookmarks strip — reuse for Nearby section cards on restaurant.html

### Established Patterns
- Client-side filtering without reload — map already loads all markers at once from `/api/restaurants/map-points`; Phase 8 extends the existing filter callbacks
- Borough select shell already exists in inspection-map.html (line 70) — just needs to be wired
- Inline CSS / fetch patterns — no external stylesheets, all styles inline
- `fetchWithAuth` for authenticated calls; bare `fetch` for public endpoints
- `gradeBadgeHtml` helper copied per-template (no shared module)

### Integration Points
- `inspection-map.html` filter bar: the `markers` ClusterGroup variable is already in scope at window level — filter callbacks call `map.removeLayer(markers)` / `map.addLayer(filteredGroup)`
- `restaurant.html`: lat/lng are available in the restaurant JSON from `GET /api/restaurants/{id}` — read them when populating the page, pass to nearby fetch
- `landing.html`: search results are stored in a JS array — sort mutates a copy of this array, then re-renders the results list

</code_context>

<specifics>
## Specific Ideas

- Uncontrolled table "Days Since Inspection" column highlights long-overdue restaurants at a glance — shows the 12-month criteria visually
- Map count badge phrasing: "312 markers shown" updates as each filter changes
- Nearby section self-exclusion: filter out the current restaurant's own restaurantId from the nearby results before rendering

</specifics>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope

</deferred>

---

*Phase: 08-discovery-enhancement*
*Context gathered: 2026-04-03*
