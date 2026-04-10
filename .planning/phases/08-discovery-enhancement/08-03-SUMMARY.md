---
phase: 08-discovery-enhancement
plan: "03"
subsystem: templates
tags: [disc-01, inspection-map, filter-bar, leaflet, client-side]
dependency_graph:
  requires: [08-01]
  provides: [inspection-map filter bar with grade/borough/cuisine filters and live count badge]
  affects: [inspection-map.html]
tech_stack:
  added: []
  patterns: [module-level allPoints + markers scope, applyFilters() rebuilds markerClusterGroup, cuisine fetch on DOMContentLoaded, change event delegation]
key_files:
  created: []
  modified:
    - src/main/resources/templates/inspection-map.html
---

# Plan 08-03 Summary — inspection-map.html filter bar

## What was built

Extended `src/main/resources/templates/inspection-map.html` with a full client-side filter bar (DISC-01).

## Key changes

- **Promoted scope**: `const markers` inside `loadMapPoints()` → `let markers` at module level; added `let allPoints = []`
- **applyFilters()**: rebuilds `L.markerClusterGroup()` from `allPoints` filtering by active grades, borough, and cuisine — no new network requests
- **loadMapPoints()**: now stores `allPoints = data.data` and calls `applyFilters()` instead of building markers inline
- **Toolbar additions**: grade checkboxes A/B/C/F (all checked by default), cuisine `<select>` populated from `/api/restaurants/cuisines`, `#marker-count` badge at rightmost position
- **Borough option values**: updated to uppercase (MANHATTAN, BROOKLYN, etc.) to match MongoDB data
- **Event wiring**: all 4 grade checkboxes, borough select, and cuisine select trigger `applyFilters` on change
- **Cuisine fetch**: fires concurrently with `loadMapPoints()` on DOMContentLoaded; silent failure on error

## Verification status

- [x] grade-A/B/C/F checkboxes present and checked by default
- [x] cuisine-filter select populated via API
- [x] marker-count badge updates on each applyFilters call
- [x] let allPoints and let markers at module scope
- [x] no const markers inside function body
- [ ] Human verification pending (checkpoint in plan 08-03 task 2)
