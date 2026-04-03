---
phase: 07-homepage-navigation
plan: 02
subsystem: ui
tags: [thymeleaf, jwt, html, css, javascript]

# Dependency graph
requires:
  - phase: 07-01
    provides: backend routes for /api/restaurants/sample, /api/users/me enriched, /profile view route
provides:
  - Thymeleaf navbar fragment with JWT-driven auth state (fragments/navbar.html)
  - Public landing page with hero KPI strip, debounced search CTA, sample restaurant grid
  - Authenticated profile page with username/email/role badge/bookmark count
  - Personalised customer dashboard (3-strip: bookmarks, nearby, KPI tiles)
affects:
  - 07-03 (human-verify checkpoint — these are the templates being verified)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Thymeleaf fragment insertion via th:replace="fragments/navbar :: navbar"
    - JWT decode in browser IIFE for auth state (atob + JSON.parse on token.split('.')[1])
    - window.location.pathname vs data-nav attribute for active link detection
    - Debounced fetch search pattern (300ms timeout, clearTimeout on each keystroke)
    - Conditional nearby section (display:none by default, shown only if geolocation succeeds)

key-files:
  created:
    - src/main/resources/templates/fragments/navbar.html
    - src/main/resources/templates/landing.html
    - src/main/resources/templates/profile.html
  modified:
    - src/main/resources/templates/index.html

key-decisions:
  - "Navbar auth state fully JS-driven: no Spring Security Thymeleaf (sec:authorize) — stateless JWT app has no server session to query"
  - "landing.html has no auth guard: public page must not redirect anonymous visitors"
  - "Nearby section in index.html starts display:none and is shown only if geolocation resolves and returns results"
  - "Chart.js and Leaflet CDN references removed from index.html: only needed on analytics.html and inspection-map.html respectively"

patterns-established:
  - "Fragment insertion: <div th:replace='fragments/navbar :: navbar'></div> as first child of body"
  - "IIFE auth IIFE runs synchronously in same fragment file — <script> tag placed after <span id='nav-auth'>"

requirements-completed: [UX-01, UX-02, UX-03, UX-04]

# Metrics
duration: 13min
completed: 2026-04-03
---

# Phase 7 Plan 02: Homepage Navigation Templates Summary

**Thymeleaf navbar fragment with JWT auth state + landing, profile, and 3-strip customer dashboard pages using inline CSS/JS patterns**

## Performance

- **Duration:** 13 min
- **Started:** 2026-04-03T18:07:48Z
- **Completed:** 2026-04-03T18:21:31Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- Created `fragments/navbar.html` — sticky gradient navbar with logo, 3 nav pills, and JWT-decoded auth area (Sign In vs username + Sign Out)
- Created `landing.html` — public homepage with live KPI hero strip, debounced search CTA, and sample restaurant grid (no auth guard)
- Created `profile.html` — authenticated profile card with CUSTOMER (green) / CONTROLLER (orange) role badge, bookmark count, and conditional report count
- Rewrote `index.html` as 3-strip personalised customer dashboard (Strip A: bookmarks, Strip B: nearby, Strip C: KPI tiles) — removed Chart.js/Leaflet CDN references

## Task Commits

Each task was committed atomically:

1. **Task 1: Navbar fragment** - `529eda6` (feat)
2. **Task 2: landing.html, profile.html, index.html rewrite** - `09dcd47` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `src/main/resources/templates/fragments/navbar.html` — Thymeleaf navbar fragment with th:fragment="navbar", JWT auth IIFE, active link detection
- `src/main/resources/templates/landing.html` — Public homepage: hero KPI, search, sample grid
- `src/main/resources/templates/profile.html` — Authenticated profile card: username, email, role badge, stats
- `src/main/resources/templates/index.html` — Rewritten as 3-strip personalised customer dashboard

## Decisions Made

- Navbar auth state fully JS-driven: no Spring Security Thymeleaf (`sec:authorize`) — stateless JWT app has no server session
- `landing.html` has no auth guard — public page must not redirect anonymous visitors
- Nearby section in `index.html` starts `display:none` and is shown only if geolocation resolves and returns results
- Chart.js and Leaflet CDN references removed from `index.html` — only needed on analytics.html and inspection-map.html respectively

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- All 4 templates ready for human-verify checkpoint in Plan 07-03
- Navbar fragment is reusable across all future templates
- Landing/profile/dashboard pages are complete UX surfaces

---
*Phase: 07-homepage-navigation*
*Completed: 2026-04-03*
