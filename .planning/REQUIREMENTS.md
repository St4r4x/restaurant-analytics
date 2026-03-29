# Requirements: Restaurant Hygiene Control App

**Defined:** 2026-03-27
**Core Value:** A customer can search any NYC restaurant and immediately know whether it's clean — and a controller can document new hygiene findings against the same data.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Authentication & Roles

- [x] **AUTH-01**: User account has a CUSTOMER or CONTROLLER role stored in PostgreSQL
- [x] **AUTH-02**: Controller can register via a dedicated endpoint using a shared signup code
- [x] **AUTH-03**: URL-level security guards block CONTROLLER endpoints from unauthenticated or CUSTOMER access
- [ ] **AUTH-04**: Auth endpoints (login/register) have rate limiting to prevent brute-force attacks
- [x] **AUTH-05**: One CUSTOMER and one CONTROLLER test account are seeded automatically on application startup

### Controller Reports

- [ ] **CTRL-01**: Controller can create an inspection report for a restaurant (violations found, grade A/B/C/F, status open/in-progress/resolved)
- [ ] **CTRL-02**: Controller can view a list of their own submitted inspection reports
- [ ] **CTRL-03**: Controller can edit their own inspection reports
- [ ] **CTRL-04**: Controller can attach a photo to an inspection report

### Customer Discovery

- [ ] **CUST-01**: Customer can search restaurants by name or address and see a list of results with hygiene grade
- [ ] **CUST-02**: Customer can view a restaurant detail page showing hygiene grade, cleanliness score, and NYC inspection history
- [ ] **CUST-03**: Customer can browse restaurants on an interactive map with grade-colored markers (green=A, yellow=B, red=C/F)
- [ ] **CUST-04**: Customer can bookmark/favorite restaurants and view their saved list

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Controller Reports (v2)

- **CTRL-V2-01**: Report status change notifications to admin
- **CTRL-V2-02**: Controller can view reports from all controllers (admin view)
- **CTRL-V2-03**: Bulk photo upload (multiple photos per report)

### Customer Features (v2)

- **CUST-V2-01**: Real-time notifications when a bookmarked restaurant gets a new inspection
- **CUST-V2-02**: Filter map by grade, borough, or cuisine type
- **CUST-V2-03**: Customer can see hygiene trend over time (is it improving?)

### Platform (v2)

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
| AUTH-04 | Phase 1 | Pending |
| AUTH-05 | Phase 1 | Complete |
| CTRL-01 | Phase 2 | Pending |
| CTRL-02 | Phase 2 | Pending |
| CTRL-03 | Phase 2 | Pending |
| CTRL-04 | Phase 2 | Pending |
| CUST-01 | Phase 3 | Pending |
| CUST-02 | Phase 3 | Pending |
| CUST-03 | Phase 3 | Pending |
| CUST-04 | Phase 3 | Pending |

**Coverage:**
- v1 requirements: 13 total
- Mapped to phases: 13
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-27*
*Last updated: 2026-03-27 after roadmap creation*
