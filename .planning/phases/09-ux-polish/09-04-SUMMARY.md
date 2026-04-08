---
phase: 09-ux-polish
plan: 04
subsystem: frontend-templates
tags: [ux, toast, error-handling, pagination, dashboard]
dependency_graph:
  requires: [09-01]
  provides: [UX-07, UX-05-dashboard]
  affects: [landing.html, analytics.html, uncontrolled.html, my-bookmarks.html, dashboard.html, index.html, restaurant.html, inspection-map.html]
tech_stack:
  added: []
  patterns: [showToast-error-handling, client-side-pagination]
key_files:
  created: []
  modified:
    - src/main/resources/templates/landing.html
    - src/main/resources/templates/analytics.html
    - src/main/resources/templates/uncontrolled.html
    - src/main/resources/templates/my-bookmarks.html
    - src/main/resources/templates/dashboard.html
    - src/main/resources/templates/index.html
    - src/main/resources/templates/restaurant.html
    - src/main/resources/templates/inspection-map.html
decisions:
  - login.html form validation kept inline per plan spec (not replaced with toasts)
  - Empty state messages (no data found) kept inline per plan spec
  - #error-msg div in uncontrolled.html replaced with HTML comment since toast handles it
  - removeBookmark in my-bookmarks.html adds both non-ok branch and .catch() error toast
metrics:
  duration_min: 25
  completed_date: "2026-04-08"
  tasks_completed: 3
  files_modified: 8
---

# Phase 09 Plan 04: Toast Notifications + Dashboard Pagination Summary

Toast-based error/success feedback system wired to all 8 templates using `window.showToast()` from `ux-utils.html`, plus client-side pagination added to dashboard.html report list (PAGE_SIZE=20, Prev/Next controls).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Replace inline errors in landing, analytics, uncontrolled, my-bookmarks | 4ff3d85 | 4 templates |
| 2 | Replace inline errors + add success toasts in dashboard, index, restaurant, inspection-map | 2476c14 | 4 templates |
| 3 | Add pagination to dashboard.html report list | 12f60a2 | dashboard.html |

## What Was Built

### Task 1 — First 4 templates

**landing.html:**
- Search `.catch()` now calls `showToast('Could not load search results', 'error')` and clears results
- Sample grid `.catch()` already had toast from prior work (kept)
- "No restaurants found" empty state kept inline

**analytics.html:**
- KPI `.catch()` calls `showToast('Could not load analytics stats', 'error')`
- KPI else-branch (data.status !== 'success') calls `showToast('Analytics data unavailable', 'error')`
- Borough chart `.catch()` calls `showToast('Borough chart failed to load', 'error')` + neutral fallback text
- At-risk `.catch()` calls `showToast('At-risk data failed to load', 'error')` + neutral table row

**uncontrolled.html:**
- `loadData` `.catch()` calls `showToast('Could not load uncontrolled restaurants', 'error')` + neutral tbody row
- `#error-msg` div replaced with HTML comment (toast is the feedback mechanism now)

**my-bookmarks.html:**
- `removeBookmark` non-ok response branch adds `showToast('Could not remove bookmark', 'error')`
- Added `.catch()` handler to `removeBookmark` with same toast + row opacity revert
- `loadBookmarks` catch/else already had showToast from prior work (kept)

### Task 2 — Second 4 templates

**dashboard.html:**
- `loadReports` error response: `showToast(data.message || 'Error loading reports', 'error')` + neutral fallback
- `loadReports` `.catch()`: `showToast('Failed to load reports', 'error')` + neutral fallback
- `submitNewReport` success: `showToast('Report created', 'success')`
- `submitNewReport` `.catch()`: `showToast('Failed to create report', 'error')`
- `saveEdit` success: `showToast('Report updated', 'success')`
- `saveEdit` `.catch()`: `showToast('Failed to save changes', 'error')`
- `uploadPhoto` success: `showToast('Photo uploaded', 'success')`
- `uploadPhoto` `.catch()`: `showToast('Photo upload failed', 'error')`

**index.html:**
- Bookmarks `.catch()`: `showToast('Could not load bookmarks', 'error')` + neutral fallback
- KPI `.catch()`: `showToast('Could not load stats', 'error')`

**restaurant.html:**
- `resp.status !== 'success'`: `showToast('Could not load restaurant details', 'error')` + neutral fallback
- Main fetch `.catch()`: same toast + neutral fallback
- `handleBookmarkClick` success: `showToast(saved ? 'Bookmark removed' : 'Bookmarked!', 'success')`
- `handleBookmarkClick` non-ok: `showToast('Could not update bookmark', 'error')`
- `handleBookmarkClick` `.catch()`: `showToast('Could not update bookmark', 'error')`

**inspection-map.html:**
- `loadMapPoints` `.catch()`: `showToast('Map data failed to load', 'error')`

### Task 3 — Dashboard report list pagination

- Added `allReports`, `reportPage`, `REPORT_PAGE_SIZE=20` module-level vars
- `renderReportList` stores into `allReports` and calls `renderReportPage()`
- `renderReportPage` slices 20 items and renders via existing `renderCardHtml`/`renderEditPanelHtml`
- `renderReportPagination` renders Prev/Next controls with disabled state when at boundary
- `changeReportPage(delta)` handles page navigation
- `#report-pagination` div added in HTML after `#report-list`
- `switchTab` resets `reportPage = 0` before calling `loadReports()`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing functionality] removeBookmark .catch() handler was absent**
- **Found during:** Task 1
- **Issue:** `removeBookmark` only had a `.then()` handler with no `.catch()`. A network error would leave the row faded at 0.4 opacity with no feedback.
- **Fix:** Added `.catch()` to `removeBookmark` that reverts row opacity and shows error toast
- **Files modified:** `my-bookmarks.html`
- **Commit:** 4ff3d85

**2. [Rule 2 - Missing functionality] handleBookmarkClick success branch had no feedback**
- **Found during:** Task 2
- **Issue:** `handleBookmarkClick` in restaurant.html performed optimistic toggle but never confirmed success to the user
- **Fix:** Added success toast in `.then()` when `r.ok`, error toast when `!r.ok`
- **Files modified:** `restaurant.html`
- **Commit:** 2476c14

## Known Stubs

None — all toast call sites use hardcoded string messages, no data flowing through stubs.

## Threat Flags

None — error messages are hardcoded strings, no user input reflected in toast text. Consistent with T-09-04 disposition (accept).

## Verification Results

- `grep -rn "showToast" src/main/resources/templates/ | wc -l` = **29** (>= 15 threshold met)
- `grep -c "showToast" src/main/resources/templates/login.html` = **0** (login untouched)
- `mvn clean package -DskipTests`: skipped — pre-existing filesystem permission issue in `target/` directory (file owned by root from prior Docker run, unrelated to template changes). Java source code was not modified in this plan.

## Self-Check: PASSED
