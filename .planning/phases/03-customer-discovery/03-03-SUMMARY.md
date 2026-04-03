---
phase: 03-customer-discovery
plan: "03"
subsystem: ui
tags: [thymeleaf, javascript, search, bookmarks, html]

# Dependency graph
requires:
  - phase: 03-02
    provides: /api/restaurants/search endpoint and /api/users/me/bookmarks endpoints

provides:
  - index.html search card with debounced JS wired to /api/restaurants/search
  - my-bookmarks.html template with bookmark list, optimistic remove, and empty state

affects:
  - 03-04-customer-discovery (restaurant detail page navigation from search results)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Debounced search input: 300ms timer cleared on each keystroke, fires fetch at >= 2 chars
    - Grade badge inline style pattern: hex colors per grade (A/B/other) as data-driven pill spans
    - Optimistic bookmark remove: row fades to 0.4 opacity, removed from DOM on 200 OK
    - fetchWithAuth copied verbatim from index.html to maintain consistent auth token pattern

key-files:
  created:
    - src/main/resources/templates/my-bookmarks.html
  modified:
    - src/main/resources/templates/index.html

key-decisions:
  - "Search card inserted between header and first .dashboard grid — no JS rewrite, append-only pattern"
  - "my-bookmarks.html is a standalone HTML template with no Thymeleaf th: attributes — all data loaded via client-side fetch"
  - "fetchWithAuth copied verbatim from index.html to avoid divergence in auth handling across pages"

patterns-established:
  - "Grade badge: inline span with background/color hex per grade level (A: #e8f5e9/#2e7d32, B: #fff8e1/#f57f17, other: #ffebee/#b71c1c)"
  - "Search results use existing .top-restaurant-item CSS class for visual consistency with top-restaurants widget"

requirements-completed: [CUST-01, CUST-04]

# Metrics
duration: 15min
completed: 2026-03-31
---

# Phase 3 Plan 03: Search Bar and My Bookmarks UI Summary

**Search card with debounced JS on index.html and /my-bookmarks Thymeleaf template with optimistic bookmark removal**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-31T10:19:00Z
- **Completed:** 2026-03-31T10:34:23Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Added search card to index.html between header and first dashboard grid — input fires debounced fetch at >= 2 chars, hides results below 2 chars
- Grade badges rendered with correct hex colors (A: green, B: yellow, other: red) on search result rows
- Bookmark toggle on search result rows reuses existing `toggleBookmark()` and `bookmarkedIds` Set
- Created my-bookmarks.html: full standalone page with bookmark list, optimistic remove (fade + DOM removal), empty state CTA, and auth guard redirecting to /login

## Task Commits

Each task was committed atomically:

1. **Task 1: Add search card + debounced JS to index.html** - `f3d84b8` (feat)
2. **Task 2: Create my-bookmarks.html template** - `a52ec15` (feat)

## Files Created/Modified
- `src/main/resources/templates/index.html` - Added search card HTML and appended debounced search JS block
- `src/main/resources/templates/my-bookmarks.html` - New template: bookmark list, remove, empty state, fetchWithAuth

## Decisions Made
- Search JS appended at end of existing `<script>` block, not replacing or reorganizing existing code
- `my-bookmarks.html` uses no Thymeleaf `th:` attributes — all rendering is client-side via fetch, matching the pattern established in Plan 03-02
- `fetchWithAuth` definition copied verbatim from index.html (lines 833-836) rather than a simplified version to preserve identical auth error handling behavior

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- `grep "localStorage.getItem" | grep "login"` acceptance check required both strings on the same line — consolidated `loadBookmarks()` guard to a single line: `if (!localStorage.getItem('accessToken')) { window.location.href = '/login'; return; }`. Minor formatting adjustment, no behavior change.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Search bar and bookmark pages are complete and human-verified (all 7 browser checks passed)
- Checkpoint approved 2026-03-31: search bar, grade badges, bookmark toggle on results, /my-bookmarks list, optimistic remove, empty state all confirmed working
- Ready for 03-04-PLAN.md: restaurant detail page and inspection-map clustering

---
*Phase: 03-customer-discovery*
*Completed: 2026-03-31*

## Self-Check: PASSED

- FOUND: src/main/resources/templates/index.html
- FOUND: src/main/resources/templates/my-bookmarks.html
- FOUND commit f3d84b8 (Task 1)
- FOUND commit a52ec15 (Task 2)
