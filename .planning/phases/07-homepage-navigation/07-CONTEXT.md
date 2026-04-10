# Phase 7: Homepage & Navigation - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Non-authenticated visitors get a proper public landing page at `/`. Authenticated customers
get a personalised dashboard at `/`. A persistent top navbar lives on every page. A
`/profile` page shows account details.

No new REST domains. Changes touch: ViewController (routing logic + new routes), a new
Thymeleaf fragment (navbar), two new/modified templates (landing.html, index.html), one
new template (profile.html), one new REST endpoint (`GET /api/restaurants/sample`), one
new REST endpoint (`GET /api/users/me`), and updates to all existing templates to insert
the navbar fragment.

</domain>

<decisions>
## Implementation Decisions

### Homepage routing split

- **Two templates, server-side.** `ViewController.index(Authentication auth)` returns:
  - `"landing"` — when `auth == null` (anonymous visitors)
  - `redirect:/dashboard` — when ROLE_CONTROLLER (already implemented in Phase 5)
  - `"index"` — when ROLE_CUSTOMER (new personalised page)
- Anonymous visitors can browse freely. Restaurant detail (`/restaurant/{id}`) and search
  are already public. Login is only prompted when a user clicks "Bookmark" on a detail page
  (existing behavior — no change needed).
- The old stats content in `index.html` (borough chart, cuisine scores, inspection heatmap,
  nearby map) is **moved to `/analytics`** and removed from `index.html`. The analytics
  page (Phase 6) becomes the single home for city-wide stats.
- The new `index.html` (authenticated customer view) shows the UX-02 layout:
  1. Recent bookmarks strip (last 3, from `GET /api/users/me/bookmarks` or existing bookmarks endpoint)
  2. Nearby restaurants section (geolocation-based — if permission granted)
  3. 4 KPI tiles reusing Phase 6 analytics data

### Top navbar

- **Thymeleaf fragment** at `src/main/resources/templates/fragments/navbar.html` with
  `th:fragment="navbar"`. Inserted via `th:replace="fragments/navbar :: navbar"` in
  every template.
- **Auth state is JS-driven** (consistent with all existing templates): the fragment
  renders a `<span id="nav-auth">` placeholder; on page load a shared JS snippet checks
  `localStorage.getItem("accessToken")` and renders either:
  - Logged in: `<a href="/profile">{username}</a> | <button onclick="logout()">Logout</button>`
  - Logged out: `<a href="/login">Sign In</a>`
  - Username must be decoded from the JWT payload (`atob(token.split('.')[1])` → `.sub`)
- **Nav links:** Logo (left) · Search · Map · Analytics (center) · auth area (right)
  - "Search" → `/` (the personalised dashboard for customers, landing for anonymous)
  - "Map" → `/inspection-map`
  - "Analytics" → `/analytics`
  - No "My Bookmarks" in the navbar (stays as a separate page, linked from the bookmarks
    strip on the personalised homepage)
- The fragment's CSS is self-contained inline styles consistent with the purple gradient
  theme (`#667eea` / `#764ba2`). The nav bar sits above the existing `<header>` on each
  page, or replaces the existing pill-link header area.

### Public landing page (landing.html)

- **Three sections:**
  1. Hero — app name, tagline, a few city-wide stats
  2. Search CTA — search bar that calls `GET /api/restaurants/search?q=...` and shows
     results inline (same pattern as the existing search card in `index.html`)
  3. Sample restaurants strip — 3 cards loaded from `GET /api/restaurants/sample?limit=3`
- **City-wide stats in hero:** reuse the Phase 6 KPI endpoint `GET /api/analytics/kpi`.
  Show 2–3 values (e.g. "27 000 restaurants tracked · 68% Grade A"). Em dash `—` as
  loading state (same pattern as analytics.html KPI tiles).
- **3 sample restaurants:** new endpoint `GET /api/restaurants/sample?limit=3` using a
  MongoDB `$sample` aggregation stage. Returns random restaurants each page load.
  Each card shows: name, borough, grade badge, and a link to `/restaurant/{id}`.
- **No authentication required** — all three sections are fully public.
- **Styling:** same purple gradient background, `.card` style, inline CSS — no new patterns.

### Profile page (/profile)

- **New endpoint `GET /api/users/me`** (JWT-required) returns:
  ```json
  {
    "username": "john",
    "email": "john@example.com",
    "role": "CUSTOMER",
    "bookmarkCount": 12,
    "reportCount": null
  }
  ```
  `reportCount` is populated only for ROLE_CONTROLLER (count of their own reports from
  `InspectionReportRepository`). CUSTOMER receives `null`.
- **Standalone card page** (`profile.html`): purple gradient background, centered white
  card with: avatar icon, username, email, role badge (green CUSTOMER / orange CONTROLLER),
  bookmark count, report count (controllers only). Same visual language as all other pages.
- **Username in navbar links to `/profile`.** Clicking the username navigates to
  `/profile` — the profile page requires a valid JWT; unauthenticated access returns 403
  (handled by existing SecurityConfig `anyRequest().permitAll()` pattern plus a client-side
  redirect to `/login` if no token in localStorage).

### Claude's Discretion

- Exact navbar height, padding, logo text vs icon
- Whether to add the navbar to `login.html` as well (probably not — login has its own
  minimal layout)
- CSS for the role badge (green vs orange background or outline style)
- Whether the sample restaurants section shows a "Reload" button
- Exact nearby restaurants behavior when geolocation is denied (show a message or hide the
  section entirely)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Routing and security
- `src/main/java/com/aflokkat/controller/ViewController.java` — current routing logic
  (index() already has ROLE_CONTROLLER redirect from Phase 5); add landing/profile routes
- `src/main/java/com/aflokkat/config/SecurityConfig.java` — `/profile` needs
  `hasAnyRole("CUSTOMER","CONTROLLER")` or a client-side guard; confirm `/` stays public

### Templates to replicate style from
- `src/main/resources/templates/index.html` — purple theme, CSS variables, `.card`,
  `.dashboard`, `fetchWithAuth()`, `gradeBadgeHtml()`, `borderColor()`, search debounce
  pattern; also contains the nearby-map section (geolocation + Leaflet) to migrate to the
  new personalised homepage
- `src/main/resources/templates/analytics.html` — KPI tiles pattern, public page structure
- `src/main/resources/templates/dashboard.html` — most recent template, grade badge + card
- `src/main/resources/templates/restaurant.html` — public page, bookmark button auth guard

### Templates to update (navbar insertion)
All 7 templates need `th:replace="fragments/navbar :: navbar"`:
- `src/main/resources/templates/index.html`
- `src/main/resources/templates/analytics.html`
- `src/main/resources/templates/dashboard.html`
- `src/main/resources/templates/restaurant.html`
- `src/main/resources/templates/inspection-map.html`
- `src/main/resources/templates/my-bookmarks.html`
- New: `src/main/resources/templates/landing.html`
- New: `src/main/resources/templates/profile.html`

### Existing backend to extend
- `src/main/java/com/aflokkat/controller/RestaurantController.java` — add
  `GET /api/restaurants/sample?limit=N` endpoint
- `src/main/java/com/aflokkat/controller/UserController.java` — add
  `GET /api/users/me` endpoint
- `src/main/java/com/aflokkat/dao/RestaurantDAO.java` + `RestaurantDAOImpl.java` — add
  `findSampleRestaurants(int limit)` using MongoDB `$sample` aggregation
- `src/main/java/com/aflokkat/repository/BookmarkRepository.java` — `countByUser()` or
  `countByUserEntity()` for bookmark count in /api/users/me
- `src/main/java/com/aflokkat/repository/InspectionReportRepository.java` (or
  `ReportRepository`) — `countByUser()` for report count in /api/users/me

### Existing analytics endpoint to reuse
- `GET /api/analytics/kpi` — Phase 6 endpoint returning `{ totalRestaurants, pctGradeA,
  avgScore, atRiskCount }` — reused on landing.html hero and on personalised index.html

### Requirements
- `.planning/REQUIREMENTS.md` — UX-01, UX-02, UX-03, UX-04
- `.planning/ROADMAP.md` — Phase 7 goal and success criteria

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `fetchWithAuth(url, options)` in `index.html` / `dashboard.html` — copy into navbar
  fragment script block for logout() and auth state rendering
- `gradeBadgeHtml(grade)` — copy into `landing.html` for sample restaurant cards
- Grade color constants `#22c55e` (A), `#eab308` (B), `#ef4444` (C) — established
- Chart.js 4.4 CDN and Leaflet 1.9.4 CDN — both in `index.html`; the nearby-map section
  (geolocation + Leaflet) should move to the new personalised `index.html`
- JWT decode pattern for username: `JSON.parse(atob(token.split('.')[1])).sub`

### Established Patterns
- All templates inline CSS — no external stylesheets
- All API calls are client-side `fetch` / `fetchWithAuth` in `<script>` tags
- No CSS framework — pure CSS matching `.card` / `.dashboard` grid patterns
- Loading state: show `—` while data fetches; no skeleton yet (Phase 9 adds those)
- Page structure: `<body>` → `<div class="container">` → `<header>` → content sections

### Integration Points
- `ViewController.java` — add `GET /landing` (no, `/` already handles routing) plus new
  `GET /profile` route returning `"profile"` template
- `SecurityConfig.java` — `/profile` should require authentication; can use
  `.antMatchers("/profile").authenticated()` before `anyRequest().permitAll()`
- `RestaurantDAO.java` — `findSampleRestaurants(int limit)` uses `$sample` aggregation,
  similar structure to `findWorstCuisinesByAverageScore`
- `UserController.java` — new `/api/users/me` endpoint reads `UserEntity` from
  `UserRepository.findByUsername(principal.getName())`, counts from bookmark/report repos

</code_context>

<specifics>
## Specific Ideas

- Username in navbar is decoded client-side from the JWT payload `sub` field — no extra
  API call needed just to display the name
- Landing page hero could show "27,000+ restaurants tracked · 68% grade A in NYC" using
  the KPI values from `/api/analytics/kpi`
- Profile role badge colors: CUSTOMER → green (#22c55e), CONTROLLER → orange (#f97316)

</specifics>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope

</deferred>

---

*Phase: 07-homepage-navigation*
*Context gathered: 2026-04-03*
