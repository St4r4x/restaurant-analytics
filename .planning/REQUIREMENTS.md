# Requirements: Restaurant Hygiene Control App

**Defined:** 2026-03-27
**Core Value:** A customer can search any NYC restaurant and immediately know whether it's clean — and a controller can document new hygiene findings against the same data.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Authentication & Roles

- [x] **AUTH-01**: User account has a CUSTOMER or CONTROLLER role stored in PostgreSQL
- [x] **AUTH-02**: Controller can register via a dedicated endpoint using a shared signup code
- [x] **AUTH-03**: URL-level security guards block CONTROLLER endpoints from unauthenticated or CUSTOMER access
- [x] **AUTH-04**: Auth endpoints (login/register) have rate limiting to prevent brute-force attacks
- [x] **AUTH-05**: One CUSTOMER and one CONTROLLER test account are seeded automatically on application startup

### Controller Reports

- [x] **CTRL-01**: Controller can create an inspection report for a restaurant (violations found, grade A/B/C/F, status open/in-progress/resolved)
- [x] **CTRL-02**: Controller can view a list of their own submitted inspection reports
- [x] **CTRL-03**: Controller can edit their own inspection reports
- [x] **CTRL-04**: Controller can attach a photo to an inspection report

### Customer Discovery

- [x] **CUST-01**: Customer can search restaurants by name or address and see a list of results with hygiene grade
- [x] **CUST-02**: Customer can view a restaurant detail page showing hygiene grade, cleanliness score, and NYC inspection history
- [x] **CUST-03**: Customer can browse restaurants on an interactive map with grade-colored markers (green=A, yellow=B, red=C/F)
- [x] **CUST-04**: Customer can bookmark/favorite restaurants and view their saved list

## v2 Requirements

Active requirements for milestone v2.0. Each maps to roadmap phases 5-10.

### Controller Workspace (Phase 5)

- [x] **CTRL-05**: Controller can create an inspection report via a web form — search restaurant by name, fill in grade/violations/notes, submit without using the API directly
- [x] **CTRL-06**: Controller can view all their reports on a dashboard page with status filter tabs (All / Open / In Progress / Resolved) and grade badges
- [ ] **CTRL-07**: Controller can edit a report from the dashboard (grade, status, violations, notes) via an inline edit panel, without leaving the page
- [ ] **CTRL-08**: Controller can upload a photo and see a thumbnail preview on the report card

### Analytics & Stats (Phase 6)

- [ ] **STAT-01**: Public `/analytics` page shows city-wide KPIs: total restaurants, % grade A, average score, count of at-risk (grade C/Z)
- [ ] **STAT-02**: Analytics page shows per-borough grade distribution — for each of the 5 boroughs, a visual breakdown of A/B/C counts
- [ ] **STAT-03**: Analytics page shows cuisine hygiene ranking — top 10 cleanest and top 10 worst cuisines by average score
- [ ] **STAT-04**: Analytics page shows "At Risk" list — restaurants with last grade C or Z, with links to their detail page

### Homepage & Navigation (Phase 7)

- [ ] **UX-01**: Non-authenticated visitors see a landing page with city-wide stats, a search CTA, and 3 sample restaurants — not the full dashboard
- [ ] **UX-02**: Authenticated users see a personalised dashboard on `/`: recent bookmarks, nearby restaurants (if geolocation allowed), and a summary stats strip
- [ ] **UX-03**: A persistent top navbar exists on all pages: logo, Search, Map, Analytics links; right side shows Login or username + Logout
- [ ] **UX-04**: A `/profile` page shows the logged-in user's username, email, role badge, bookmark count, and (for controllers) report count

### Discovery Enhancement (Phase 8)

- [ ] **DISC-01**: The map at `/inspection-map` has filter controls — grade checkboxes (A/B/C/F), borough dropdown, cuisine dropdown — markers update client-side without reload
- [ ] **DISC-02**: A `/uncontrolled` page lists restaurants not inspected in 12+ months or with last grade C/Z; table is sortable by score and filterable by borough; includes a "Download CSV" button
- [ ] **DISC-03**: The restaurant detail page shows up to 5 nearby restaurants (within 500m) in a "Nearby" section, each with grade badge and link
- [ ] **DISC-04**: Search results can be sorted by score (best first), grade, or name; sort control visible above results

### UX Polish (Phase 9)

- [ ] **UX-05**: All list views (search results, reports, bookmarks, analytics at-risk) are paginated — 20 items per page with Previous / Next controls
- [ ] **UX-06**: All data-fetching sections show skeleton loading cards instead of blank space or a "Loading…" text
- [ ] **UX-07**: A toast notification system replaces all inline success/error messages — toasts appear bottom-right, auto-dismiss after 3s
- [ ] **UX-08**: All pages render correctly on mobile viewports (320px–768px) — no horizontal scroll, no overlapping elements

### Admin Tools (Phase 10)

- [ ] **ADM-01**: A `/admin` page (CONTROLLER role) shows the last NYC data sync status, a "Sync Now" button with live progress feedback, and a "Rebuild Cache" button
- [ ] **ADM-02**: The admin page has an "Export At-Risk CSV" button that triggers the existing `/api/inspection/at-risk/export.csv` endpoint
- [ ] **ADM-03**: The admin page shows aggregate report statistics across all controllers: count by status (open/in-progress/resolved) and count by grade — without exposing individual reports from other controllers

### Deferred to v3

- **CTRL-V2-01**: Report status change notifications to admin
- **CTRL-V2-02**: Controller can view individual reports from all controllers (admin view)
- **CTRL-V2-03**: Bulk photo upload (multiple photos per report)
- **CUST-V2-01**: Real-time notifications when a bookmarked restaurant gets a new inspection
- **CUST-V2-03**: Customer can see hygiene trend over time (is it improving?)
- **PLAT-V2-01**: Admin role can manage controller accounts
- **PLAT-V2-02**: Export controller reports to PDF

## Out of Scope

| Feature | Reason |
|---------|--------|
| Push controller reports to NYC Open Data API | No write access in v1; API is read-only |
| Customer-visible controller reports | Internal reports are for controllers only in v1 |
| Mobile native app | Web-first; mobile deferred |
| Multi-city support | NYC Open Data only |
| Object storage for photos (S3, GCS) | Docker local volume sufficient for academic scope |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 1 | Complete |
| AUTH-02 | Phase 1 | Complete |
| AUTH-03 | Phase 1 | Complete |
| AUTH-04 | Phase 1 | Complete |
| AUTH-05 | Phase 1 | Complete |
| CTRL-01 | Phase 2 | Complete |
| CTRL-02 | Phase 2 | Complete |
| CTRL-03 | Phase 2 | Complete |
| CTRL-04 | Phase 2 | Complete |
| CUST-01 | Phase 3 | Complete |
| CUST-02 | Phase 3 | Complete |
| CUST-03 | Phase 3 | Complete |
| CUST-04 | Phase 3 | Complete |

**v1 Coverage:**
- v1 requirements: 13 total
- Mapped to phases: 13
- Unmapped: 0 ✓

| Requirement | Phase | Status |
|-------------|-------|--------|
| CTRL-05 | Phase 5 | Not started |
| CTRL-06 | Phase 5 | Not started |
| CTRL-07 | Phase 5 | Not started |
| CTRL-08 | Phase 5 | Not started |
| STAT-01 | Phase 6 | Not started |
| STAT-02 | Phase 6 | Not started |
| STAT-03 | Phase 6 | Not started |
| STAT-04 | Phase 6 | Not started |
| UX-01 | Phase 7 | Not started |
| UX-02 | Phase 7 | Not started |
| UX-03 | Phase 7 | Not started |
| UX-04 | Phase 7 | Not started |
| DISC-01 | Phase 8 | Not started |
| DISC-02 | Phase 8 | Not started |
| DISC-03 | Phase 8 | Not started |
| DISC-04 | Phase 8 | Not started |
| UX-05 | Phase 9 | Not started |
| UX-06 | Phase 9 | Not started |
| UX-07 | Phase 9 | Not started |
| UX-08 | Phase 9 | Not started |
| ADM-01 | Phase 10 | Not started |
| ADM-02 | Phase 10 | Not started |
| ADM-03 | Phase 10 | Not started |

**v2 Coverage:**
- v2 requirements: 23 total
- Mapped to phases: 23
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-27*
*Last updated: 2026-04-01 after v2.0 roadmap creation (phases 5-10)*
