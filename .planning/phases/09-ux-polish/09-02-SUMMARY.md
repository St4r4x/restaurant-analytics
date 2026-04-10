---
phase: 09-ux-polish
plan: 02
subsystem: frontend
tags: [thymeleaf, javascript, pagination, skeleton, landing-page]
dependency_graph:
  requires: [09-01]
  provides: [landing.html pagination, landing.html skeleton loading]
  affects: [src/main/resources/templates/landing.html]
tech_stack:
  added: []
  patterns: [client-side array slicing pagination, skeleton shimmer loading via .skel class]
key_files:
  created: []
  modified:
    - src/main/resources/templates/landing.html
decisions:
  - "PAGE_SIZE=20 with client-side array slicing — no backend changes needed (endpoint already returns up to limit= items)"
  - "Raise search fetch limit from 10 to 200 — gives 10 full pages of 20 results, covers all practical searches"
  - "renderResults renamed to renderResultRows (internal); new renderPage() is the public entry point for sort+slice+paginate"
  - "currentPage reset to 0 on both new search and sort change to prevent empty-page edge case"
  - "Sample grid error uses showToast + brief inline paragraph (not frozen skeletons, not the old error-msg class)"
metrics:
  duration: 2m
  completed: 2026-04-07
  tasks_completed: 2
  files_changed: 1
requirements: [UX-05, UX-06]
---

# Phase 09 Plan 02: Landing Page Pagination + Skeleton Summary

**One-liner:** Client-side 20-items/page pagination with Prev/Next for search results and skeleton shimmer loading for sample grid and search area in landing.html, with fetch limit raised to 200.

## What Was Built

`landing.html` received two UX improvements:

**Task 1 — Skeleton Loading**
- The 3 static grey placeholder `<div>` elements in `#sample-grid` were replaced with `<div class="skel">` elements using the shimmer animation from the `ux-utils` fragment created in Plan 01.
- `doSearch(q)` now injects 5 skeleton rows (`height:44px`) into `#search-results` before the fetch fires, replacing the blank space during network latency.
- The sample grid `.catch()` handler was updated to call `showToast('Could not load sample restaurants', 'error')` and show a brief centered inline message instead of the old `.error-msg` paragraph — frozen skeletons are avoided.

**Task 2 — Client-Side Pagination**
- `#pagination-controls` div added after `#search-results` in the HTML.
- Pagination state: `PAGE_SIZE = 20`, `currentPage = 0`.
- `renderResults(arr)` renamed to `renderResultRows(arr)` — internal row renderer.
- New `renderPage()`: applies current sort to full `lastResults` array, slices the current page, calls `renderResultRows` then `renderPagination`.
- New `renderPagination()`: renders Prev/Next buttons with disabled state on boundary pages and a "Page X of Y" counter; hides itself when total pages ≤ 1.
- New `goPage(delta)`: clamps page within valid range, calls `renderPage()`.
- `doSearch()` success handler: sets `currentPage = 0`, calls `renderPage()` instead of `renderResults`.
- Sort control change handler: sets `currentPage = 0`, calls `renderPage()`.
- `hideResults()` now also clears `#pagination-controls` and resets `currentPage = 0`.
- Search fetch limit raised from `&limit=10` to `&limit=200`.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | Skeleton loading for sample grid and search results | c02edb2 |
| 2 | Client-side pagination with Prev/Next for search results | 4e13fad |

## Deviations from Plan

None — plan executed exactly as written.

The plan specified all function names, variable names, HTML structure, and button styles; these were applied verbatim.

## Known Stubs

None. All search results are rendered from live API data. The skeleton placeholders are replaced by real content on fetch completion.

## Threat Flags

None. T-09-02 (XSS via search results innerHTML) was accepted in the plan's threat model — restaurant names come from NYC Open Data (trusted source), no user-generated content is rendered in search results.

## Self-Check: PASSED

- `src/main/resources/templates/landing.html` — FOUND
- Task 1 commit c02edb2 — FOUND
- Task 2 commit 4e13fad — FOUND
- `skel` occurrences in landing.html: 4 (3 sample grid + 1 in Array(5).fill string)
- `PAGE_SIZE` occurrences: 5
- `pagination-controls` occurrences: 5 (HTML div + hideResults + renderPagination + doSearch empty + doSearch catch)
- `limit=200` present in fetch URL
- `mvn clean package -DskipTests` — BUILD SUCCESS
