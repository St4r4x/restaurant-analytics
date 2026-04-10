# Phase 09: UX Polish - Research

**Researched:** 2026-04-06
**Domain:** Vanilla JS UX patterns — pagination, skeleton loading, toast notifications, mobile responsiveness in Thymeleaf templates
**Confidence:** HIGH (all findings are from direct codebase inspection — no external API research needed)

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| UX-05 | Pagination (20 items/page, Prev/Next) on: search results (landing.html), at-risk list (analytics.html), uncontrolled list (uncontrolled.html), bookmarks list (my-bookmarks.html) | API endpoints and current data-loading patterns inventoried below |
| UX-06 | Skeleton loading cards on all data-fetching sections — no blank space, no "Loading…" text | All fetch sites catalogued; current spinner/placeholder patterns identified |
| UX-07 | Toast notification system (bottom-right, auto-dismiss 3s) replacing ALL inline success/error messages | Complete inline error/success inventory below |
| UX-08 | Mobile responsive (320px–768px): navbar hamburger, cards stack vertically, map fills screen, no horizontal scroll | Navbar structure fully inspected; missing viewport tags identified |
</phase_requirements>

---

## Summary

Phase 09 is a pure frontend concern — no new API endpoints need to be created, and no backend Java code changes are needed except for one gap in pagination support (detailed below). All work is in-template HTML/CSS/JS changes.

The project uses Thymeleaf fragments for the navbar only. All JS is inline per-template with no shared JS file infrastructure (no `/static/` directory exists). The project already uses a single shared fragment (`fragments/navbar.html`) that all templates include via `th:replace`. This fragment is the correct insertion point for globally-shared utilities (toast system, skeleton CSS). Injecting a second shared fragment called `fragments/ux-utils.html` containing the toast function and skeleton CSS is the cleanest approach that avoids copy-paste across 10+ templates while staying consistent with the existing pattern.

The largest implementation risk for UX-05 is that **three of the four paginatable endpoints do not return a `totalCount`** field — they return all matching rows up to a server-side `limit`. Client-side pagination (slice the returned array in JS) is the correct approach for all four lists given how the data is currently served. This avoids backend changes and works correctly for the data volumes involved (at-risk is capped at 50, bookmarks are typically < 100, search is capped at limit).

**Primary recommendation:** Use a shared `fragments/ux-utils.html` Thymeleaf fragment for toast CSS/JS and skeleton CSS. Client-side pagination (JS array slicing) for all four UX-05 lists. Hamburger menu injected inside the existing navbar flex row via a `<button id="hamburger-btn">` that toggles a CSS class.

---

## Template Inventory

### Templates in scope (data-fetching pages)

| Template | Fetches Data | Has inline error/success | Has spinner | Has viewport meta | In UX-05 scope |
|----------|-------------|--------------------------|-------------|-------------------|----------------|
| landing.html | YES (KPI, sample, search) | YES — `.error-msg` class | NO — uses static grey boxes | NO | YES (search results) |
| analytics.html | YES (KPI, borough chart, cuisine rankings, at-risk) | YES — inline color changes, text content | NO | YES | YES (at-risk table) |
| uncontrolled.html | YES (uncontrolled list) | YES — `#error-msg` div, `Loading…` td | NO — uses `Loading…` text in `<td>` | YES | YES (uncontrolled table) |
| my-bookmarks.html | YES (bookmarks) | YES — inline `<p style="color:#c33">` | YES — `.spinner` CSS class | YES | YES (bookmarks list) |
| restaurant.html | YES (restaurant detail, nearby) | YES — `.error` div, inline red | YES — `.spinner` CSS class | YES | NO |
| dashboard.html | YES (reports list) | YES — inline `<p style="color:#b71c1c">`, modal error divs | YES — `.spinner` CSS class | YES | NO |
| inspection-map.html | YES (map-points, cuisines) | YES — statusEl text mutation | NO — uses text "Loading…" | YES | NO |
| index.html | YES (bookmarks, nearby, KPI) | YES — inline `<p style="color:#b71c1c">` | NO — uses static grey boxes | NO | NO |
| profile.html | YES (user profile) | YES — `.error-msg` | NO | NO | NO |
| login.html | YES (auth forms) | YES — `.error-msg`, `.success-msg` | NO | YES | NO |

### Templates missing `<meta name="viewport">` [VERIFIED: grep]

- `index.html` — missing viewport meta
- `landing.html` — missing viewport meta
- `profile.html` — missing viewport meta

All others already have `<meta name="viewport" content="width=device-width, initial-scale=1.0">`.

---

## Standard Stack

No external libraries are needed for this phase. All four UX features can be implemented in pure CSS + vanilla JS:

### Core (already present)
| Library | Version | Purpose | Source |
|---------|---------|---------|--------|
| Thymeleaf | 2.6.15 bundled | Template fragments | [VERIFIED: pom.xml] |
| Vanilla JS | ES6+ | All existing template JS | [VERIFIED: template inspection] |

### Nothing to install

All UX patterns (skeleton, toast, pagination, hamburger) are implementable with:
- CSS `@keyframes` for skeleton shimmer animation
- `position:fixed` for toast positioning
- JS array slicing for pagination
- CSS media queries and a `display:none` toggle for hamburger

**Installation:** None required. [VERIFIED: no `static/` directory exists, project pattern is inline CSS/JS per template plus shared Thymeleaf fragments]

---

## Architecture Patterns

### Pattern 1: Shared Thymeleaf Fragment for Cross-Template Utilities

**What:** A new `fragments/ux-utils.html` file containing (1) skeleton CSS `@keyframes` + `.skel` class definition, (2) the `window.showToast()` function, injected into every template's `<head>` via `th:replace`.

**When to use:** Any utility that is identical across all templates. Toast and skeleton meet this criterion; pagination is per-list and stays inline.

**Why this over copy-paste:** The project already establishes this pattern with `fragments/navbar.html`. A second fragment is consistent, avoids 10 copies of the same CSS/JS block, and makes future fixes trivial.

**Injection pattern:**
```html
<!-- In <head> of each template: -->
<div th:replace="fragments/ux-utils :: ux-utils"></div>
```

**Fragment definition:**
```html
<!-- fragments/ux-utils.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
<th:block th:fragment="ux-utils">
<style>
/* Skeleton shimmer */
.skel {
  background: linear-gradient(90deg, #e0e0e0 25%, #f0f0f0 50%, #e0e0e0 75%);
  background-size: 200% 100%;
  animation: skel-shimmer 1.4s infinite;
  border-radius: 6px;
}
@keyframes skel-shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
/* Toast container */
#toast-container {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}
.toast {
  background: #333;
  color: white;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 0.88em;
  box-shadow: 0 4px 12px rgba(0,0,0,0.25);
  opacity: 0;
  transform: translateY(8px);
  transition: opacity 0.25s ease, transform 0.25s ease;
  pointer-events: auto;
  max-width: 320px;
}
.toast.toast-visible { opacity: 1; transform: translateY(0); }
.toast.toast-success { background: #2e7d32; }
.toast.toast-error   { background: #b71c1c; }
.toast.toast-info    { background: #1565c0; }
</style>
<script>
(function() {
  function ensureContainer() {
    var c = document.getElementById('toast-container');
    if (!c) {
      c = document.createElement('div');
      c.id = 'toast-container';
      document.body.appendChild(c);
    }
    return c;
  }
  window.showToast = function(msg, type, durationMs) {
    type = type || 'info';
    durationMs = durationMs || 3000;
    var container = ensureContainer();
    var el = document.createElement('div');
    el.className = 'toast toast-' + type;
    el.textContent = msg;
    container.appendChild(el);
    // Trigger reflow for animation
    void el.offsetWidth;
    el.classList.add('toast-visible');
    setTimeout(function() {
      el.classList.remove('toast-visible');
      setTimeout(function() { el.remove(); }, 300);
    }, durationMs);
  };
})();
</script>
</th:block>
</head>
</html>
```

[ASSUMED] Thymeleaf allows `th:replace` inside `<head>` without a wrapping `<div>` — use `<th:block>` if the `<div>` causes validator warnings.

### Pattern 2: Client-Side Pagination (JS Array Slicing)

**What:** Keep the full data array in a JS variable. Render only items `[page * pageSize, (page+1) * pageSize)`. Prev/Next buttons update the page variable and call the render function.

**When to use:** All four UX-05 lists. This is the correct approach because:
1. None of the four endpoints return server-side pagination parameters
2. Data volumes are manageable (at-risk: 50 items, bookmarks: typically < 100, search: limited by `&limit=`, uncontrolled: up to 500 but filtered)
3. Adding `page`/`offset` backend parameters would require DAO changes

**Pattern (copy-paste per list):**
```javascript
var PAGE_SIZE = 20;
var currentPage = 0;
var allItems = [];  // filled by fetch

function renderPage() {
  var start = currentPage * PAGE_SIZE;
  var slice = allItems.slice(start, start + PAGE_SIZE);
  // render slice into DOM...
  renderPagination();
}

function renderPagination() {
  var totalPages = Math.ceil(allItems.length / PAGE_SIZE);
  var el = document.getElementById('pagination-controls');
  if (totalPages <= 1) { el.innerHTML = ''; return; }
  el.innerHTML =
    '<button onclick="goPage(-1)" ' + (currentPage === 0 ? 'disabled' : '') + '>← Prev</button>'
    + ' <span>Page ' + (currentPage + 1) + ' of ' + totalPages + '</span> '
    + '<button onclick="goPage(1)" ' + (currentPage >= totalPages - 1 ? 'disabled' : '') + '>Next →</button>';
}

function goPage(delta) {
  var maxPage = Math.ceil(allItems.length / PAGE_SIZE) - 1;
  currentPage = Math.max(0, Math.min(maxPage, currentPage + delta));
  renderPage();
}
```

**Reset on filter change:** When the borough filter changes in `uncontrolled.html`, call `currentPage = 0` before re-fetching and re-rendering.

**Search pagination note:** `landing.html` currently fetches with `&limit=10`. For pagination to show more than 10 results, the fetch limit must be raised. Change `&limit=10` to `&limit=100` (or higher) so the client has items to paginate through. The search endpoint accepts any `limit` param. [VERIFIED: RestaurantController.java line 371]

### Pattern 3: Skeleton Placeholders

**What:** Replace static grey loading boxes and spinner divs with animated `.skel` elements. Size the skeletons to match the expected rendered content (same height/width as the real items).

**Skeleton for a list row (table):**
```javascript
function skeletonRows(n, cols) {
  var row = '<tr>';
  for (var i = 0; i < cols; i++) {
    row += '<td><div class="skel" style="height:16px;border-radius:4px;width:' + (60+Math.random()*30).toFixed(0) + '%"></div></td>';
  }
  row += '</tr>';
  return Array(n).fill(row).join('');
}
// Usage before fetch:
tbody.innerHTML = skeletonRows(5, 4);
```

**Skeleton for a card grid:**
```javascript
function skeletonCards(n) {
  return Array(n).fill(
    '<div class="skel" style="height:80px;border-radius:8px"></div>'
  ).join('');
}
// Usage before fetch:
grid.innerHTML = skeletonCards(3);
```

**Current static placeholders to replace (already exist):**
- `landing.html` `#sample-grid`: already has 3 grey `<div>` placeholders — add `.skel` class and animation
- `index.html` `#bookmarks-grid`: same pattern
- Templates with `.spinner` class: `my-bookmarks.html`, `restaurant.html`, `dashboard.html` — replace spinner with sized skeleton

### Pattern 4: Navbar Hamburger Menu

**What:** Add a `<button id="hamburger-btn">` to the right of the logo in `navbar.html`. On screens ≤ 768px, hide the nav link group and show the hamburger. Clicking the hamburger toggles `display:flex/none` on the nav link group. The auth area collapses into the menu.

**Navbar current structure (from codebase):**
```
<nav style="...display:flex;...justify-content:space-between">
  <a>Logo</a>                          <!-- left -->
  <div style="display:flex;gap:8px">   <!-- center nav links -->
    ...links...
  </div>
  <span id="nav-auth">...</span>       <!-- right auth -->
</nav>
```

**Hamburger approach (CSS-only toggle, no external library):**
```css
/* Add to navbar.html <style> block */
#nav-links { display: flex; gap: 8px; }
#hamburger-btn { display: none; background: none; border: none; cursor: pointer;
                 color: white; font-size: 1.4em; padding: 4px 8px; }
@media (max-width: 768px) {
  #nav-links {
    display: none;
    position: absolute;
    top: 56px; left: 0; right: 0;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    flex-direction: column;
    padding: 8px 16px 16px;
    gap: 4px;
    z-index: 99;
    box-shadow: 0 4px 12px rgba(0,0,0,0.2);
  }
  #nav-links.open { display: flex; }
  #nav-auth { display: none !important; }  /* Fold auth into menu on mobile */
  #hamburger-btn { display: block; }
  nav { position: relative; }
}
```

**JS toggle (inline in navbar.html):**
```javascript
document.getElementById('hamburger-btn').addEventListener('click', function() {
  document.getElementById('nav-links').classList.toggle('open');
});
// Close on link click
document.querySelectorAll('#nav-links a').forEach(function(a) {
  a.addEventListener('click', function() {
    document.getElementById('nav-links').classList.remove('open');
  });
});
```

**HTML change:** Add `id="nav-links"` to the existing center `<div>`, and insert `<button id="hamburger-btn">&#9776;</button>` between the logo and the nav-links div.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Animated skeleton | Custom shimmer | `.skel` CSS class with `@keyframes` | Simple, no library |
| Toast system | Complex event bus | `window.showToast(msg, type)` function | This project needs only 3 lines per call |
| Pagination | Server-side paging | Client-side array slicing | All four endpoints already return full result sets |
| Hamburger menu | External library | CSS class toggle | 10 lines of CSS + 5 lines of JS |

---

## API Endpoint Audit for Pagination (UX-05)

### Endpoints powering the four paginated lists

| List | Template | Endpoint | Returns totalCount? | Current limit | Strategy |
|------|----------|----------|---------------------|---------------|----------|
| Search results | landing.html | `GET /api/restaurants/search?q=&limit=` | NO — returns `count` = items.length, no total | `&limit=10` | Raise fetch limit to 100, client-side slice |
| At-risk list | analytics.html | `GET /api/analytics/at-risk` | NO — hardcoded `limit=50` in DAO | 50 rows | Client-side slice of 50 items |
| Uncontrolled list | uncontrolled.html | `GET /api/inspection/uncontrolled?borough=&limit=` | NO — returns `count` = items.length | `?limit=500` | Client-side slice |
| Bookmarks list | my-bookmarks.html | `GET /api/users/me/bookmarks` | YES — returns `count` (= total) | Unlimited | Client-side slice |

[VERIFIED: RestaurantController.java, InspectionController.java, AnalyticsController.java, UserController.java]

**Key finding:** None of the four endpoints support `page`/`offset` parameters. Client-side pagination is the only viable approach without backend changes.

**Search fetch limit change required:** `landing.html` line 147 currently sends `&limit=10`. This must be changed to `&limit=100` (or the project's expected max results) to give the pagination anything to work with. With `&limit=10` and 20-items-per-page, page 1 would always be empty. [VERIFIED: landing.html line 147]

**At-risk endpoint concern:** `GET /api/analytics/at-risk` is hardcoded to `findAtRiskRestaurants(null, 50)` in `AnalyticsController.java` line 149 — no `limit` parameter is accepted. With 50 items and 20/page, the user gets 3 pages max. This is acceptable per UX-05 requirements ("showing top 50" is already noted in the analytics.html template on line 92).

---

## Inline Error/Success Message Inventory (for UX-07 replacement)

### Error messages to replace with `showToast(msg, 'error')`

| Template | Location | Current pattern | Replace? |
|----------|----------|-----------------|----------|
| landing.html | `#sample-grid` innerHTML on fetch failure | `<p class="error-msg">Could not load sample restaurants...</p>` | YES — toast |
| landing.html | `#search-results` innerHTML on search failure | `<div class="error-msg">Could not load results...</div>` | YES — toast |
| landing.html | `#search-results` innerHTML on empty search | `<div class="error-msg">No restaurants found for...</div>` | NO — keep inline (contextual, not an error) |
| analytics.html | KPI ids textContent on failure | Text content change to red | YES — toast + show `—` |
| analytics.html | boroughChart container innerHTML | `<p style="color:#b71c1c...">` | YES — toast |
| analytics.html | cuisine-best/worst innerHTML | `<li style="color:#888">` | NO — empty state (not error) |
| analytics.html | at-risk-body innerHTML | `<tr><td style="color:#b71c1c...">` | YES — toast |
| uncontrolled.html | `#error-msg` div | `display:block` on error | YES — toast + keep div hidden |
| my-bookmarks.html | `#bookmarks-list` innerHTML | `<p style="color:#c33">Failed to load bookmarks.</p>` | YES — toast |
| dashboard.html | `#report-list` innerHTML | `<p style="color:#b71c1c">Failed to load reports</p>` | YES — toast |
| restaurant.html | `#header-card` innerHTML | `<div class="error">Error: ...</div>` | YES — toast + show brief inline |
| index.html | `#bookmarks-grid` innerHTML | `<p style="color:#b71c1c">Could not load bookmarks.</p>` | YES — toast |
| login.html | `#loginError`, `#registerError` | `.error-msg` div show/hide | NO — form validation, keep inline |

### Success messages to replace with `showToast(msg, 'success')`

| Template | Location | Current pattern | Replace? |
|----------|----------|-----------------|----------|
| login.html | `#registerSuccess` | `.success-msg` div show/hide | YES — toast |
| dashboard.html | modal close after submit | No explicit UI feedback | ADD toast "Report created" |
| dashboard.html | saveEdit success | No explicit UI feedback | ADD toast "Report updated" |
| restaurant.html | bookmark toggle | Button text only | ADD toast "Bookmarked" / "Removed" |

**Keep as inline (do NOT replace with toast):**
- Empty states (no data found) — these are content, not errors
- Form field validation errors in login.html — these must stay adjacent to the field
- Bookmark button state indicator in restaurant.html — visual state, not a notification

---

## Mobile Responsive Gaps (UX-08)

### Critical gaps found

**1. No `<meta name="viewport">` in 3 templates** [VERIFIED: grep]
- `index.html`, `landing.html`, `profile.html` — these will not scale on mobile without this tag
- Fix: add `<meta name="viewport" content="width=device-width, initial-scale=1.0">` to `<head>`

**2. Fixed grid layouts with no breakpoints** [VERIFIED: template inspection]
- `analytics.html` line 50: `grid-template-columns: repeat(4, 1fr)` — 4 KPI tiles crush to ~80px each at 320px
- `analytics.html` line 78: `grid-template-columns: repeat(2, 1fr)` — cuisine ranking side-by-side
- `landing.html` line 46: `grid-template-columns:repeat(3,1fr)` — sample cards grid
- `index.html` line 22: `grid-template-columns:repeat(3,1fr)` — bookmarks grid
- `index.html` line 41: `grid-template-columns:repeat(4,1fr)` — KPI tiles
- Fix: add `@media (max-width: 768px) { .dashboard { grid-template-columns: 1fr !important; } }` or replace inline `style` with classes

**3. Tables with no horizontal scroll wrapper** [VERIFIED: template inspection]
- `uncontrolled.html` line 57: `<table id="uncontrolled-table">` — 6 columns, no overflow wrapper
- `analytics.html` line 93: at-risk table — 4 columns, no overflow wrapper
- `restaurant.html` line 424: inspections table — 7 columns, has `overflow-x:auto` wrapper at render time
- Fix: wrap with `<div style="overflow-x:auto">` or add at render time in JS

**4. inspection-map.html toolbar overflow** [VERIFIED: template inspection]
- Line 18-23: toolbar is `display:flex;flex-wrap:wrap` — this mostly handles itself, but the 6 filter controls at 320px may stack awkwardly
- Map itself is `height:100%` on `#map-wrapper` which is `flex:1` — this fills remaining screen height, which is correct for mobile

**5. Navbar has no hamburger** [VERIFIED: navbar.html inspection]
- At 320px, 5 nav links + auth area in a single flex row will overflow or wrap
- The current navbar has `white-space:nowrap` on the logo but no overflow handling on the link group

**6. Analytics.html `<div class="container">` has `max-width:1200px`** — at narrow viewports without a responsive grid, content can overflow. Not a horizontal scroll issue by itself, but compounds with the fixed-column grids.

### What already works on mobile

- `uncontrolled.html` filter row: `flex-wrap: wrap; gap: 8px` on line 34 — wraps cleanly
- `restaurant.html`: has `@media (max-width: 768px) { .grid-2 { grid-template-columns: 1fr; } }` on line 78 — this template is already partially responsive
- `my-bookmarks.html`: single-column layout, responsive by default

---

## Common Pitfalls

### Pitfall 1: Fragment `<div>` wrapper breaks `<head>` children
**What goes wrong:** `<div th:replace="fragments/ux-utils :: ux-utils">` inside `<head>` renders a `<div>` which is invalid HTML inside `<head>`. Some browsers tolerate it; others do not.
**Prevention:** Use `<th:block th:replace="fragments/ux-utils :: ux-utils">` instead — Thymeleaf removes the `<th:block>` tag from output, leaving only the `<style>` and `<script>` content.
[VERIFIED: Thymeleaf documentation pattern — `<th:block>` is a meta-tag removed at render time]

### Pitfall 2: Pagination resets on filter change
**What goes wrong:** User is on page 2 of uncontrolled list, changes the borough filter — the new data has fewer pages but `currentPage` is still 2, so the list appears empty.
**Prevention:** Always reset `currentPage = 0` when any filter changes before calling `loadData()` or `renderPage()`.

### Pitfall 3: Search pagination with `limit=10`
**What goes wrong:** `landing.html` fetches `&limit=10`. Pagination divides the result array into 20-item pages — page 1 shows nothing because there are only 10 items and page 0 already showed them all.
**Prevention:** Raise the search endpoint call to `&limit=100` when pagination is added.

### Pitfall 4: Toast container missing when `showToast()` is called early
**What goes wrong:** Some templates call JS before `DOMContentLoaded`. If `showToast()` runs before `<body>` is parsed, `document.body` is null.
**Prevention:** The `ensureContainer()` function in the toast snippet above uses lazy creation — it creates the container on first call. Additionally, all existing fetch calls in these templates happen either on `DOMContentLoaded` or inline at script execution time after `<body>` has been rendered (since `<script>` is at the bottom of `<body>`). This is safe.

### Pitfall 5: analytics.html uses non-standard KPI endpoint
**What goes wrong:** `analytics.html` calls `/api/analytics/kpis` (line 121). `landing.html` calls `/api/analytics/kpi` (line 78). `index.html` calls `/api/analytics/kpi` (line 135). Only `/api/analytics/kpis` exists in `AnalyticsController.java`. The calls to `/api/analytics/kpi` silently return 404.
**Prevention:** This is a pre-existing bug; UX-07 work on analytics.html should target `/api/analytics/kpis`. Do not introduce new calls to the non-existent `/api/analytics/kpi`.
[VERIFIED: AnalyticsController.java (only `@GetMapping("/kpis")` exists), landing.html line 78, index.html line 135]

### Pitfall 6: Navbar `<script>` runs immediately (not deferred)
**What goes wrong:** The navbar script block runs inline, before the rest of `<body>`. Any hamburger toggle JS added to `navbar.html` must not reference elements added after the navbar (they won't exist yet).
**Prevention:** The hamburger button is inside the navbar itself — `getElementById('hamburger-btn')` will work because the button is rendered before the script. Safe.

### Pitfall 7: `display:none !important` on auth in hamburger menu blocks role visibility
**What goes wrong:** If `#nav-auth` is hidden at mobile, the user sees no way to log in on mobile.
**Prevention:** Move the auth link/button into the collapsed `#nav-links` menu on mobile, or use a simpler approach: keep auth visible and only collapse the nav links. The recommendation above hides `#nav-auth` — reconsider: better to include a "Sign In" link as the last item in the collapsed nav menu, populated by the same JS auth check.

---

## Code Examples

### Skeleton shimmer in a table body (before fetch)
```javascript
// Source: inline pattern from codebase inspection
function showTableSkeleton(tbodyId, rows, cols) {
  var html = '';
  for (var i = 0; i < rows; i++) {
    html += '<tr>';
    for (var j = 0; j < cols; j++) {
      html += '<td><div class="skel" style="height:14px;border-radius:3px;width:' + (50 + (i * j % 4) * 12) + '%"></div></td>';
    }
    html += '</tr>';
  }
  document.getElementById(tbodyId).innerHTML = html;
}

// Before fetch in uncontrolled.html:
showTableSkeleton('uncontrolled-tbody', 5, 6);
// Before fetch in analytics.html at-risk:
showTableSkeleton('at-risk-body', 5, 4);
```

### Pagination controls HTML
```javascript
// Source: inline pattern recommendation
function renderPaginationControls(containerId, currentPage, totalItems, pageSize, onGoPage) {
  var totalPages = Math.ceil(totalItems / pageSize);
  var el = document.getElementById(containerId);
  if (totalPages <= 1) { el.innerHTML = ''; return; }
  el.innerHTML =
    '<div style="display:flex;align-items:center;gap:12px;padding:12px 0;justify-content:center">'
    + '<button onclick="' + onGoPage + '(-1)" style="padding:6px 14px;border:1px solid #ddd;border-radius:6px;cursor:pointer;background:white" '
    + (currentPage === 0 ? 'disabled style="opacity:0.4;cursor:default"' : '') + '>&#8592; Prev</button>'
    + '<span style="font-size:0.88em;color:#555">Page ' + (currentPage + 1) + ' of ' + totalPages + '</span>'
    + '<button onclick="' + onGoPage + '(1)" style="padding:6px 14px;border:1px solid #ddd;border-radius:6px;cursor:pointer;background:white" '
    + (currentPage >= totalPages - 1 ? 'disabled style="opacity:0.4;cursor:default"' : '') + '>Next &#8594;</button>'
    + '</div>';
}
```

### Toast call sites
```javascript
// Error fetch failure (replaces innerHTML error patterns):
.catch(function() {
  showToast('Could not load data. Check your connection.', 'error');
});

// Success after action:
showToast('Bookmark added', 'success');
showToast('Report updated', 'success');
```

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + Spring Boot Test (MockMvc) |
| Config file | `pom.xml` — no separate config file |
| Quick run command | `mvn test -Dtest=ViewControllerAnalyticsTest,InspectionControllerUncontrolledTest` |
| Full suite command | `mvn test` |

[VERIFIED: pom.xml test dependencies]

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UX-05 | Pagination controls render for > 20 items | Manual — client-side JS only | Manual browser test at `/` with search query | N/A |
| UX-06 | Skeleton shown before fetch completes | Manual — requires network throttle | Manual browser test (Chrome DevTools slow 3G) | N/A |
| UX-07 | Toast appears on fetch failure | Manual — requires endpoint mock or actual error | Manual browser test (disconnect network) | N/A |
| UX-08 | No horizontal scroll at 320px | Manual — browser DevTools viewport | Manual browser test (320px width) | N/A |

**Note:** All four UX requirements are frontend rendering behaviors that cannot be meaningfully tested with Spring MockMvc (which returns HTTP 200 with HTML content but does not execute JavaScript). The existing test suite tests server-side route availability (ViewControllerTest pattern) — those tests will continue to pass unchanged because no server-side routes are being added or modified.

### Wave 0 Gaps
None — no new Java code, no new API endpoints. Existing tests remain valid.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 09 is purely frontend (HTML/CSS/JS template edits and one new Thymeleaf fragment). No external tools, CLI utilities, or runtime services beyond the existing running Spring Boot application are required.

---

## Runtime State Inventory

Step 2.5: SKIPPED — Phase 09 is not a rename/refactor/migration phase. No stored data, live service config, or OS-registered state is touched.

---

## Project Constraints (from CLAUDE.md)

| Directive | Impact on This Phase |
|-----------|---------------------|
| Java 11, Spring Boot 2.6.15 | No impact — all work is in HTML/CSS/JS templates |
| MongoDB + raw aggregation | No impact — no new aggregation queries needed |
| JUnit 4 + Mockito | Test framework for any server-side tests (none needed for this phase) |
| No CSS framework (no Bootstrap/Tailwind) | Confirmed: all responsive CSS must be hand-written media queries |
| All JS is inline in templates, no bundler | Confirmed: shared code goes in Thymeleaf fragments, not JS files |
| `docker compose` not `docker-compose` | No impact |
| Git branch: `gsd/phase-08-discovery-enhancement` is current | Phase 09 work should be on its own branch per CLAUDE.md convention |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `<th:block th:replace="...">` inside `<head>` renders without wrapping element in Thymeleaf 2.6.x | Architecture Patterns #1 | If wrong, use `<style>` and `<script>` tags directly in each template's `<head>` (copy-paste approach instead of fragment) |
| A2 | Raising search fetch limit from 10 to 100 does not cause performance issues | API Audit | If the search regex is slow at limit=100, keep at 20 and reduce PAGE_SIZE to match |
| A3 | `GET /api/analytics/kpi` (no 's') is a pre-existing dead endpoint and landing.html KPI strip silently fails today | Pitfall 5 | If there's a route alias I missed, no problem. If confirmed dead, the KPI fix is out of scope for Phase 09 |

---

## Open Questions (RESOLVED)

1. **Should `#nav-auth` collapse into the hamburger menu on mobile?**
   - What we know: currently auth state is shown in the right side of the navbar
   - What's unclear: requirements don't specify whether "Sign In" / username should be accessible in the collapsed hamburger menu
   - Recommendation: include auth link as last item in collapsed nav menu (simple: copy the same JS logic that populates `#nav-auth`)
   - **RESOLVED:** Keep `#nav-auth` (username + Sign Out) visible at all widths on the right side of the navbar. The hamburger collapses only the center nav links. This avoids the risk of locking users out of Sign In on mobile.

2. **Search pagination: raise limit to 100 or implement "search more" button?**
   - What we know: current `&limit=10` means only 10 results are ever fetched
   - What's unclear: whether the intent is to show 10 results paginated (nonsensical with 20/page) or to load more results and paginate them
   - Recommendation: raise to 100 (or at minimum to `PAGE_SIZE * 3 = 60`) in the fetch call
   - **RESOLVED:** Raise to `&limit=200` in landing.html. This gives up to 10 pages of 20, covers all practical searches, and avoids a backend change. Plan 09-02 implements this.

3. **Should `at-risk` in analytics.html support more than 50 items?**
   - What we know: `AnalyticsController.getAtRisk()` is hardcoded to `limit=50`; the template already says "showing top 50"
   - What's unclear: UX-05 says "20 items/page" — with 50 items that's 3 pages, which is fine
   - Recommendation: no backend change needed; pagination with 3 pages is acceptable
   - **RESOLVED:** No backend change. 50 items / 20 per page = 3 pages. The template already labels this "top 50 at-risk restaurants." Plan 09-03 implements client-side pagination only.

---

## Sources

### Primary (HIGH confidence)
- Direct codebase inspection via Read tool — all templates, all controllers, DAO interface
- `fragments/navbar.html` — full inspection of current flex layout and JS
- `RestaurantController.java`, `InspectionController.java`, `AnalyticsController.java`, `UserController.java` — all endpoint signatures verified
- `RestaurantDAOImpl.java` — pagination parameter support verified

### Secondary (MEDIUM confidence)
- [ASSUMED] Thymeleaf `<th:block>` behavior in `<head>` — standard Thymeleaf pattern but not verified against this specific Spring Boot 2.6.15 version in this session

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new libraries, pure codebase inspection
- Architecture: HIGH — patterns derived directly from existing code
- Pitfalls: HIGH — all derived from actual codebase issues found during inspection
- API pagination analysis: HIGH — all controllers and DAO verified
- Mobile gap inventory: HIGH — all templates inspected

**Research date:** 2026-04-06
**Valid until:** 2026-05-06 (frontend-only phase, no fast-moving dependencies)
