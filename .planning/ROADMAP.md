# Roadmap: Restaurant Hygiene Control App

## Overview

This milestone transforms the existing Spring Boot NYC restaurant analytics API into a dual-role web application. Phase 1 locks in role infrastructure and security — every other phase depends on it. Phase 2 gives controllers the ability to file internal inspection reports. Phase 3 gives customers a search, detail, and map interface to find clean restaurants. Phase 4 hardens the seams: cross-role security tests, ownership invariants, and photo persistence verification close the gaps most likely to surface as real bugs.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Role Infrastructure** - Extend JWT auth and Spring Security for CUSTOMER/CONTROLLER roles with secure registration and URL-level access guards (completed 2026-03-29)
- [x] **Phase 2: Controller Reports** - CRUD API for internal inspection reports stored in PostgreSQL, including photo attachment (completed 2026-03-30)
- [ ] **Phase 3: Customer Discovery** - Restaurant search, detail page, and interactive map UI for customer-facing reads
- [ ] **Phase 4: Integration Polish** - Cross-role security tests, ownership invariant tests, and rate limiting hardening

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
**Plans**: TBD

### Phase 4: Integration Polish
**Goal**: Security boundaries, ownership rules, and photo persistence are verified end-to-end with targeted tests; the application behaves correctly at all role/permission boundaries
**Depends on**: Phase 3
**Requirements**: (none — validates correctness of AUTH-01 through CUST-04)
**Success Criteria** (what must be TRUE):
  1. Running the test suite produces passing tests that explicitly assert: CUSTOMER JWT against CONTROLLER endpoints returns 403, anonymous request against authenticated endpoints returns 401
  2. Running the test suite produces a passing test that asserts controller B cannot read or edit controller A's reports
  3. A photo uploaded in one container run is accessible after `docker compose down && docker compose up --build` with no manual intervention
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Role Infrastructure | 4/4 | Complete   | 2026-03-29 |
| 2. Controller Reports | 3/3 | Complete   | 2026-03-30 |
| 3. Customer Discovery | 0/TBD | Not started | - |
| 4. Integration Polish | 0/TBD | Not started | - |
