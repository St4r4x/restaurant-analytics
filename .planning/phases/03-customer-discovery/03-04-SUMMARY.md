---
phase: 03-customer-discovery
plan: "04"
subsystem: ui
tags: [leaflet, markercluster, thymeleaf, jwt, mongodb, bookmarks]

requires:
  - phase: 03-02
    provides: /api/restaurants/map-points and /api/restaurants/{id} endpoints used by both templates
  - phase: 03-03
    provides: fetchWithAuth helper pattern and bookmark JS established in index.html

provides:
  - restaurant.html: public detail page with grade circle (56px), cleanliness score, inspection history table, and bookmark toggle
  - inspection-map.html: public grade-colored clustered marker map loaded from /api/restaurants/map-points via Leaflet.markerCluster

affects:
  - 04-integration-polish (tests both pages are accessible without auth; bookmark API integrity)

tech-stack:
  added:
    - Leaflet.markerCluster 1.5.3 (CDN)
  patterns:
    - Grade-circle CSS convention: 56px circle, A=green (#4caf50), B=yellow (#ffc107), C/F=red (#f44336)
    - Public detail page with auth-gated bookmark button (redirect to /login only on button click)
    - Optimistic bookmark toggle with revert-on-error pattern
    - divIcon dot markers + markerClusterGroup for performance with 27K+ points

key-files:
  created: []
  modified:
    - src/main/resources/templates/restaurant.html
    - src/main/resources/templates/inspection-map.html

key-decisions:
  - "restaurant.html and inspection-map.html are now public pages — auth guard removed from page load; authentication only triggers on bookmark button click"
  - "Leaflet.markerCluster CDN loaded after leaflet.min.js — load order is mandatory (CSS before JS, markerCluster after Leaflet)"
  - "Last cluster spiderfies on click when all restaurants share the same GPS coordinates — standard markerCluster behavior, not a bug (noted by reviewer)"

patterns-established:
  - "Grade circle pattern: 56px inline-styled div, JS sets background/color/border based on latestGrade after fetch"
  - "Bookmark button optimistic toggle: updateBookmarkBtn(!saved) immediately, then revert on API error"
  - "Public map page: no auth check on load, fetch /api/restaurants/map-points with plain fetch() (no auth header)"

requirements-completed: [CUST-02, CUST-03, CUST-04]

duration: 25min
completed: 2026-03-31
---

# Phase 3 Plan 04: Customer Detail Page and Map Clustering Summary

**restaurant.html and inspection-map.html made public with grade circle, inspection history table, bookmark toggle, and Leaflet.markerCluster migration from heatmap to grade-colored dot markers**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-03-31T11:00:00Z
- **Completed:** 2026-03-31T12:00:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Removed page-load auth guard from both templates; both pages now fully accessible to unauthenticated users
- restaurant.html: grade circle (56px, color-coded A/B/C-F), cleanliness score, full NYC inspection history table (date, grade, score, violations), bookmark toggle with fetchWithAuth
- inspection-map.html: migrated from old heatmap to grade-colored div-icon dot markers clustered by Leaflet.markerCluster 1.5.3; each popup has name, grade badge, and "View details →" link
- All 13 browser verification checks passed (reviewer noted last-cluster spiderfy is expected markerCluster behavior)

## Task Commits

1. **Task 1: Enhance restaurant.html** - `5213285` (feat)
2. **Task 2: Migrate inspection-map.html to clustered markers** - `d84a2df` (feat)

## Files Created/Modified

- `src/main/resources/templates/restaurant.html` - Auth guard removed; grade circle + score + inspection history table + bookmark toggle added
- `src/main/resources/templates/inspection-map.html` - Auth guard removed; heatmap fetch replaced with grade-colored markerCluster from /api/restaurants/map-points

## Decisions Made

- Auth guard on page load was removed from both templates; the bookmark button is the only action requiring auth and it redirects to /login immediately on click without making any API call
- Leaflet.markerCluster loaded from CDN (jsdelivr, pinned at 1.5.3) — strict load order enforced: leaflet.min.css → MarkerCluster.css → MarkerCluster.Default.css → leaflet.min.js → leaflet.markercluster.js
- Cluster colors overridden to match app palette (purple/indigo for small/medium clusters, red for large clusters)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. The reviewer's note about the last cluster spiderfying on click is standard Leaflet.markerCluster behavior when all remaining points share the exact same GPS coordinates — not a regression.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 3 (Customer Discovery) is fully complete — all 4 plans done (CUST-01 through CUST-04)
- Phase 4 (Integration Polish) can start: both customer and controller surfaces are functional; security boundary tests, ownership invariant tests, and photo persistence verification remain

---
*Phase: 03-customer-discovery*
*Completed: 2026-03-31*
