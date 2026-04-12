# Restaurant Hygiene Control App

## What This Is

A web application that connects restaurant hygiene controllers and customers around NYC inspection data. Controllers file internal inspection reports (violations, grades, photos, follow-up status) for restaurants. Customers search for restaurants and instantly see their hygiene grade and cleanliness score. Built on top of a Spring Boot API already syncing data from the NYC Open Data API.

## Core Value

A customer can search any NYC restaurant and immediately know whether it's clean — and a controller can document new hygiene findings against the same data.

## Current State

**v3.0 in progress — Phase 14 complete (2026-04-12)**

Phase 14 (testcontainers-integration-tests) complete. TEST-04/05/06 validated. Replaced live-DB integration tests with Testcontainers:
- RestaurantDAOIT: 15 tests against real mongo:7.0 container (TC 1.20.1)
- UserRepositoryIT: 4 tests against postgres:15-alpine + mongo:7.0 containers
- AppConfig tier-0 System.getProperty() lookup enables container URI injection
- Full suite: 165 Surefire + 15 + 4 IT = BUILD SUCCESS, no external services needed

**Shipped: v2.0 — Full Product (2026-04-11)**

All 10 phases complete. 36/36 requirements validated. The app is a fully deployed Spring Boot monolith with:
- Dual-role auth (CUSTOMER / CONTROLLER / ADMIN) via JWT
- Controller dashboard: report filing, inline edit, photo upload
- Public analytics page: city-wide KPIs, borough breakdown, cuisine rankings, at-risk list
- Dual landing/home routing (anonymous vs. authenticated `/`)
- Persistent navbar across all 10 pages + `/profile` page
- Map filters (grade/borough/cuisine), `/uncontrolled` tracker, nearby restaurants, sort controls
- UX polish: pagination (20/page), skeleton loading, toast notifications, mobile responsive
- Admin tools: sync controls, at-risk CSV export, aggregate report stats

## Requirements

### Validated (v1.0 — shipped 2026-04-01)

- ✓ NYC Open Data API sync into MongoDB
- ✓ Restaurant data queryable by borough, cuisine, score, grade
- ✓ JWT authentication (register, login, refresh)
- ✓ User accounts in PostgreSQL
- ✓ Redis TTL cache for expensive analytics queries
- ✓ REST API with Swagger documentation
- ✓ Web dashboard with HTML templates (ViewController)
- ✓ Separate controller registration (signup code)
- ✓ Role-based access control (CUSTOMER / CONTROLLER roles)
- ✓ Controller can create, view, edit inspection reports with photos
- ✓ Customer can search restaurants and see hygiene grade + inspection history
- ✓ Customer can browse restaurants on an interactive map
- ✓ Customer can bookmark restaurants

### Validated (v2.0 — shipped 2026-04-11)

- ✓ Controller dashboard UI (status tabs, report cards, inline edit, photo thumbnails)
- ✓ Public analytics page (KPIs, borough distribution, cuisine rankings, at-risk list)
- ✓ Dual landing/home routing based on auth state
- ✓ Persistent navbar across all pages + `/profile` page
- ✓ Map filters (grade, borough, cuisine) — client-side, no reload
- ✓ `/uncontrolled` page (C/Z grade or >12 months without inspection) + CSV export
- ✓ Nearby restaurants section on detail page
- ✓ Sort control on search results
- ✓ Pagination (20 items/page), skeleton loading, toast notifications, mobile responsive
- ✓ Admin tools: sync controls, CSV export, aggregate report stats (ADMIN role)

### Deferred to v3

- Report status notifications to admin
- Cross-controller report view (admin)
- Bulk photo upload
- Real-time notifications for bookmarked restaurant updates
- Hygiene trends over time
- Admin manages controller accounts
- PDF export of reports

### Out of Scope

- Pushing controller reports to NYC Open Data API — no write access
- Customer-visible controller reports — internal only
- Mobile native app — web-first
- Multi-city support — NYC only
- Object storage for photos (S3, GCS) — Docker volume sufficient

## Context

Production-grade Spring Boot 2.6.15 monolith:
- MongoDB for restaurant/inspection data (`mongodb-driver-sync`, raw aggregation pipelines)
- PostgreSQL for users/bookmarks/reports (Spring Data JPA)
- Redis 7 for caching (TTL 3600s)
- JWT security (15-min access / 7-day refresh tokens)
- Deployment: Docker Compose (4 containers: app, MongoDB, Redis, Postgres)

## Constraints

- **Tech stack**: Java 11, Spring Boot 2.6.15 — no framework upgrade
- **Database**: MongoDB for restaurant data, PostgreSQL for user/report metadata
- **Auth**: JWT — extend existing system, don't replace
- **NYC API**: Read-only
- **Academic**: Academic project (Aflokkat / big data module)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Controller reports stored in PostgreSQL (JPA) | Structured relational data, fits existing JPA layer | ✓ Validated |
| Role field added to UserEntity | Simplest extension of existing auth — no new table | ✓ Validated |
| Controller signup via registration code | Prevents open public access to filing reports | ✓ Validated |
| Customer UI extends existing ViewController | Reuses Thymeleaf template infrastructure | ✓ Validated |
| Navbar auth state fully JS-driven | Stateless JWT app has no server session for Thymeleaf Security | ✓ Validated |
| anyRequest().permitAll() with client-side IIFE guards for /admin, /dashboard | Browser navigation does not send Authorization header | ✓ Validated |
| uploadPhoto uses raw fetch() (not fetchWithAuth) | fetchWithAuth sets Content-Type: application/json, corrupts multipart boundary | ✓ Validated |
| ADMIN role separate from CONTROLLER | Admin-specific signup code, separate DataSeeder seed user | ✓ Validated |

---
*Last updated: 2026-04-12 — Phase 13 complete (config-docker-hardening)*
