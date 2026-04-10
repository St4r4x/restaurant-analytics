---
phase: 08-discovery-enhancement
plan: "04"
subsystem: templates
tags: [disc-03, disc-04, restaurant, landing, nearby, sort, client-side]
dependency_graph:
  requires: [08-01]
  provides: [Nearby Restaurants section on restaurant.html, sort control on landing.html]
  affects: [restaurant.html, landing.html, CHANGELOG.md, README.md]
tech_stack:
  added: []
  patterns: [loadNearby fetch with self-exclusion filter, module-level lastResults + renderResults/sortResults, sort control shown/hidden based on result presence]
key_files:
  created: []
  modified:
    - src/main/resources/templates/restaurant.html
    - src/main/resources/templates/landing.html
    - CHANGELOG.md
    - README.md
---

# Plan 08-04 Summary — Nearby Restaurants + Sort Control

## What was built

### Task 1: restaurant.html — Nearby Restaurants (DISC-03)
- Added `.nearby-card` CSS class
- Added `gradeBadgeHtml()` helper (not previously present in this template)
- Added `loadNearby(lat, lng, currentId)` — fetches `/api/restaurants/nearby?radius=500&limit=6`, filters out current restaurant (`restaurantId !== currentId`), renders up to 5 mini-cards
- Added `#nearby-section` HTML (hidden by default) after `#history-card`
- Call site: `loadNearby(r.latitude, r.longitude, currentId)` after `renderHistoryTable()`
- No lat/lng → section stays hidden; fetch error → section hidden

### Task 2: landing.html — Sort Control (DISC-04)
- Added `#sort-control-wrapper` (hidden by default) with select: Relevance / Best Score / Worst Score / A→Z
- Extracted `renderResults(arr)` from `doSearch()` rendering logic
- Added `sortResults(arr, mode)` using `latestScore` comparator (not `score`)
- `doSearch()` now stores `lastResults = res.data`, resets sort to "", shows wrapper, calls `renderResults()`
- `hideResults()` resets `lastResults`, hides wrapper, resets sort value
- Sort control change event: `renderResults(sortResults(lastResults, this.value))` — no network call

## Verification status

- [x] nearby-section, loadNearby, api/restaurants/nearby, nearby-card, restaurantId !== currentId all present
- [x] sort-control, lastResults, sortResults, renderResults, latestScore all present
- [x] CHANGELOG.md updated with Phase 8 entry
- [x] README.md updated with 2 new inspection endpoints
- [ ] Human verification pending (checkpoint in plan 08-04 task 3)
