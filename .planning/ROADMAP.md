# Roadmap: Restaurant Hygiene Control App

## Milestones

- ✅ **v1.0 — Foundation** — Phases 1-4 (shipped 2026-04-01): auth, controller reports API, customer discovery UI, integration tests
- 🚧 **v2.0 — Full Product** — Phases 5-10 (in progress): controller UI, analytics dashboard, homepage, discovery filters, UX polish, admin tools

## Overview

**v1.0** transformed the Spring Boot NYC restaurant analytics API into a dual-role web application with JWT auth, controller report filing, customer search/map/bookmark UI, and a hardened security layer.

**v2.0** completes the product: controllers get a full UI workspace, a public analytics dashboard surfaces city-wide hygiene trends, the homepage is redesigned for both anonymous visitors and authenticated users, discovery is enhanced with map filters and an uncontrolled-restaurants tracker, and the whole app gets UX polish (pagination, skeletons, toasts, mobile).

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Role Infrastructure** - Extend JWT auth and Spring Security for CUSTOMER/CONTROLLER roles with secure registration and URL-level access guards (completed 2026-03-29)
- [x] **Phase 2: Controller Reports** - CRUD API for internal inspection reports stored in PostgreSQL, including photo attachment (completed 2026-03-30)
- [x] **Phase 3: Customer Discovery** - Restaurant search, detail page, and interactive map UI for customer-facing reads (completed 2026-03-31)
- [x] **Phase 4: Integration Polish** - Cross-role security tests, ownership invariant tests, and rate limiting hardening (completed 2026-04-01)

## Phase Details

### Phase 1: Role Infrastructure
**Goal**: Users can register as either customers or controllers (with a signup code), roles are embedded in JWT tokens, and URL-level guards block unauthorized access
**Depends on**: Nothing (first phase)
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05
**Success Criteria** (what must be TRUE):
  1. A new user can register without a code and receives a JWT with CUSTOMER role; a new user with the correct signup code receives a JWT with CONTROLLER role; a user with a wrong code is rejected
  2. A CUSTOMER JWT sent to any `/api/reports/**` endpoint returns HTTP 403; an unauthenticated request to the same endpoint returns HTTP 401
  3. The application starts up with one seeded CUSTOMER account and one seeded CONTROLLER account that can each log in immediately
  4. Auth endpoints (login, register) return HTTP 429 after exceeding the configured request threshold
**Plans**: 4 plans

Plans:
- [ ] 01-01-PLAN.md — Role assignment in RegisterRequest + AuthService (AUTH-01, AUTH-02)
- [ ] 01-02-PLAN.md — SecurityConfig antMatchers + 403 accessDeniedHandler (AUTH-03)
- [ ] 01-03-PLAN.md — Bucket4j RateLimitFilter for /api/auth/** (AUTH-04)
- [ ] 01-04-PLAN.md — DataSeeder: seed customer_test and controller_test on startup (AUTH-05)

### Phase 2: Controller Reports
**Goal**: An authenticated controller can create, view, edit, and attach photos to internal inspection reports scoped to a specific restaurant
**Depends on**: Phase 1
**Requirements**: CTRL-01, CTRL-02, CTRL-03, CTRL-04
**Success Criteria** (what must be TRUE):
  1. A controller can POST a new inspection report for a restaurant (with violations, grade, status) and receive the created report back including the restaurant name and borough pulled from MongoDB
  2. A controller can list their own reports and filter by status; they cannot see reports filed by other controllers
  3. A controller can edit the grade, status, violation codes, and notes on a report they own; another controller's attempt to edit the same report returns HTTP 403
  4. A controller can upload a photo to a report and retrieve the photo URL in subsequent GET responses; photos survive a `docker compose down && docker compose up`
**Plans**: 3 plans

Plans:
- [ ] 02-01-PLAN.md — InspectionReportEntity + ReportRepository + DTO + POST /api/reports + GET /api/reports (CTRL-01, CTRL-02)
- [ ] 02-02-PLAN.md — PATCH /api/reports/{id} with ownership check (CTRL-03)
- [ ] 02-03-PLAN.md — Photo upload/streaming endpoints + AppConfig.getUploadsDir() + Docker volume (CTRL-04)

### Phase 3: Customer Discovery
**Goal**: A customer can search for any NYC restaurant by name or address, view its hygiene detail, and browse all restaurants on a grade-colored interactive map
**Depends on**: Phase 1
**Requirements**: CUST-01, CUST-02, CUST-03, CUST-04
**Success Criteria** (what must be TRUE):
  1. A customer can type a restaurant name or address fragment into a search field and see a results list showing each restaurant's name, borough, and hygiene grade
  2. A customer can open a restaurant detail page and see the current grade badge, cleanliness score, and the full NYC inspection history timeline with violation descriptions
  3. A customer can open a map page and see grade-colored markers (green A, yellow B, red C/F) across NYC; the map does not freeze or crash with the full ~27K restaurant dataset loaded
  4. A customer can bookmark a restaurant from the detail page or search results and view their saved bookmarks in a dedicated list
**Plans**: 4 plans

Plans:
- [ ] 03-01-PLAN.md — Wave 0 test scaffold: RestaurantControllerSearchTest with @Disabled stubs (CUST-01, CUST-03)
- [ ] 03-02-PLAN.md — DAO methods + REST endpoints /search + /map-points + ViewController /my-bookmarks route (CUST-01, CUST-03, CUST-04)
- [x] 03-03-PLAN.md — index.html search bar + my-bookmarks.html template (CUST-01, CUST-04)
- [x] 03-04-PLAN.md — restaurant.html grade badge + history table + bookmark toggle; inspection-map.html clustering migration (CUST-02, CUST-03, CUST-04)

### Phase 4: Integration Polish
**Goal**: Security boundaries, ownership rules, and photo persistence are verified end-to-end with targeted tests; the application behaves correctly at all role/permission boundaries
**Depends on**: Phase 3
**Requirements**: (none — validates correctness of AUTH-01 through CUST-04)
**Success Criteria** (what must be TRUE):
  1. Running the test suite produces passing tests that explicitly assert: CUSTOMER JWT against CONTROLLER endpoints returns 403, anonymous request against authenticated endpoints returns 401
  2. Running the test suite produces a passing test that asserts controller B cannot read or edit controller A's reports
  3. A photo uploaded in one container run is accessible after `docker compose down && docker compose up --build` with no manual intervention
**Plans**: 4 plans

Plans:
- [ ] 04-01-PLAN.md — Translate entire project to English (HTML templates + Java comments)
- [ ] 04-02-PLAN.md — Remove orphaned routes (/hygiene-radar, /inspection), dead endpoints (/worst-cuisines, /cuisine-scores, /popular-cuisines), and delete orphaned templates
- [ ] 04-03-PLAN.md — Refresh documentation (README.md, ARCHITECTURE.md, CHANGELOG.md)
- [ ] 04-04-PLAN.md — Add SC-2 read path + SC-3 file-I/O tests to ReportControllerTest

---

### Phase 5: Controller Workspace
**Goal**: Controllers can manage their inspection reports entirely through a dedicated UI — search a restaurant, file a report, edit it, attach a photo — without touching the API directly
**Depends on**: Phase 4
**Requirements**: CTRL-05, CTRL-06, CTRL-07, CTRL-08
**Success Criteria** (what must be TRUE):
  1. A logged-in controller can navigate to `/dashboard`, see their reports grouped by status (All / Open / In Progress / Resolved), and each card shows restaurant name, grade badge, date, and a thumbnail if a photo exists
  2. A controller can open a "New Report" form, search for a restaurant by name (live autocomplete using `/api/restaurants/search`), fill in grade/violations/notes, submit, and see the new card appear in the list without a page reload
  3. A controller can click "Edit" on any of their own report cards and update grade, status, violations, or notes via an inline panel; changes are persisted and the card updates immediately
  4. A controller can click "Upload Photo" on a report card, select an image, and see a thumbnail preview on the card after upload
**Plans**: 2 plans

Plans:
- [ ] 05-01-PLAN.md — ViewController redirect + SecurityConfig /dashboard guard + 6 tests (CTRL-05, CTRL-06)
- [ ] 05-02-PLAN.md — dashboard.html: tabs, cards, New Report modal, edit panel, photo upload (CTRL-05, CTRL-06, CTRL-07, CTRL-08)

### Phase 6: Analytics & Stats
**Goal**: A public analytics page gives any visitor a city-wide picture of NYC restaurant hygiene — borough breakdown, cuisine rankings, at-risk list, and a healthiest restaurants leaderboard
**Depends on**: Phase 4
**Requirements**: STAT-01, STAT-02, STAT-03, STAT-04
**Success Criteria** (what must be TRUE):
  1. Navigating to `/analytics` without authentication shows the page correctly (fully public)
  2. The page header strip shows four KPI tiles: Total Restaurants, % Grade A, Average Score (city-wide), and At-Risk Count (grade C or Z) — all populated from live API data
  3. The borough section shows a grade distribution bar for each of the 5 NYC boroughs — each bar visually encodes the A/B/C proportion using the standard green/yellow/red palette
  4. The cuisine section shows two ranked lists: top 10 cleanest and top 10 worst cuisines by average inspection score, each with the score value visible
  5. The "At Risk" section lists restaurants with last grade C or Z; each row has restaurant name, borough, grade badge, and a link to the detail page
**Plans**: 3 plans

Plans:
- [ ] 06-01-PLAN.md — Wave 0 test scaffolds: AnalyticsControllerTest (4 stubs) + ViewControllerAnalyticsTest (1 stub) (STAT-01, STAT-02, STAT-03, STAT-04)
- [ ] 06-02-PLAN.md — AnalyticsController (4 endpoints) + DAO/Service extensions (borough distribution, best/worst cuisines, at-risk count) (STAT-01, STAT-02, STAT-03, STAT-04)
- [ ] 06-03-PLAN.md — analytics.html template + ViewController /analytics route + nav links in index.html + dashboard.html (STAT-01, STAT-02, STAT-03, STAT-04)

### Phase 7: Homepage & Navigation
**Goal**: Non-authenticated visitors land on a proper public homepage; authenticated users see a personalised dashboard; a consistent top navbar links all sections of the app
**Depends on**: Phase 6 (analytics KPIs reused on homepage)
**Requirements**: UX-01, UX-02, UX-03, UX-04
**Success Criteria** (what must be TRUE):
  1. Visiting `/` without a JWT shows a public landing page — hero with city stats, a search bar CTA, and 3 randomly-sampled restaurant cards — NOT the authenticated dashboard
  2. Visiting `/` with a valid JWT shows a personalised dashboard: a "Your Bookmarks" strip (last 3), a "Nearby" strip if geolocation was granted, and the 4 analytics KPI tiles
  3. Every page (`/`, `/restaurant/:id`, `/inspection-map`, `/analytics`, `/my-bookmarks`, `/dashboard`) includes the same top navbar with: logo left, nav links center (Search, Map, Analytics), auth button right (Sign In or username + Logout)
  4. A logged-in user can navigate to `/profile` and see their username, email, role badge, total bookmarks, and (for controllers) total reports filed

### Phase 8: Discovery Enhancement
**Goal**: Users can filter the map by grade/borough/cuisine, find uncontrolled restaurants, discover nearby places from a detail page, and sort search results
**Depends on**: Phase 7
**Requirements**: DISC-01, DISC-02, DISC-03, DISC-04
**Success Criteria** (what must be TRUE):
  1. On `/inspection-map`, a filter bar at the top has grade checkboxes (A/B/C/F) and a borough dropdown; toggling a filter removes matching markers from the map in under 200ms without a network request (client-side filtering on already-loaded data)
  2. Navigating to `/uncontrolled` shows a table of restaurants with last grade C/Z or no inspection in the past 12 months; the table can be sorted by score or filtered by borough dropdown; a "Download CSV" button calls the existing export endpoint
  3. The restaurant detail page has a "Nearby restaurants" section showing up to 5 restaurants within 500m, each with name, grade badge, and a link to their detail page (calls `/api/restaurants/nearby`)
  4. Above the search results on the homepage, a sort control (Best Score / Worst Score / A→Z) reorders the current result set client-side

### Phase 9: UX Polish
**Goal**: Every list is paginated, every async operation shows a skeleton, errors surface as toasts, and all pages work on mobile
**Depends on**: Phase 8
**Requirements**: UX-05, UX-06, UX-07, UX-08
**Success Criteria** (what must be TRUE):
  1. Search results, the at-risk list, the uncontrolled list, and the bookmarks list all show a maximum of 20 items per page with visible Previous / Next controls; the URL or state reflects the current page
  2. All sections that fetch data show skeleton loading cards (grey animated placeholders) for the duration of the network request — no blank space, no "Loading…" text
  3. All success and error feedback (bookmark added, report saved, upload failed, etc.) appears as a toast notification bottom-right that auto-dismisses after 3 seconds — no inline error divs remain
  4. On a 375px viewport (iPhone SE), all pages render without horizontal scroll: the navbar collapses to a hamburger, cards stack vertically, and the map fills the screen correctly

### Phase 10: Admin Tools
**Goal**: Controllers can trigger data sync and cache rebuild from the UI, export the at-risk list, and see aggregate report statistics across the platform
**Depends on**: Phase 5
**Requirements**: ADM-01, ADM-02, ADM-03
**Success Criteria** (what must be TRUE):
  1. A logged-in controller navigating to `/admin` sees the last sync date/status and a "Sync NYC Data" button; clicking it triggers the sync and shows live progress (polling the sync-status endpoint every 2s) until completion
  2. The admin page has a "Download At-Risk CSV" button that triggers a file download of the existing `/api/inspection/at-risk/export.csv` endpoint
  3. The admin page shows a "Report Statistics" panel with counts grouped by status (Open / In Progress / Resolved) and by grade (A/B/C/F) across all controllers' reports — the aggregate query must NOT return individual reports from other controllers

## Progress

**Execution Order:**
v1.0: Phases 1 → 2 → 3 → 4
v2.0: Phase 5 → (6 ∥ 7) → 8 → 9 → 10  (6 and 7 can run in parallel)

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Role Infrastructure | 4/4 | Complete | 2026-03-29 |
| 2. Controller Reports | 3/3 | Complete | 2026-03-31 |
| 3. Customer Discovery | 4/4 | Complete | 2026-03-31 |
| 4. Integration Polish | 4/4 | Complete | 2026-04-01 |
| 5. Controller Workspace | 2/2 | Complete   | 2026-04-03 |
| 6. Analytics & Stats | 1/3 | In Progress|  |
| 7. Homepage & Navigation | 0/TBD | Not started | - |
| 8. Discovery Enhancement | 0/TBD | Not started | - |
| 9. UX Polish | 0/TBD | Not started | - |
| 10. Admin Tools | 0/TBD | Not started | - |
