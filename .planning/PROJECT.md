# Restaurant Hygiene Control App

## What This Is

A web application that connects restaurant hygiene controllers and customers around NYC inspection data. Controllers file internal inspection reports (violations, grades, photos, follow-up status) for restaurants. Customers search for restaurants and instantly see their hygiene grade and cleanliness score. Built on top of a Spring Boot API already syncing data from the NYC Open Data API.

## Core Value

A customer can search any NYC restaurant and immediately know whether it's clean — and a controller can document new hygiene findings against the same data.

## Current Milestone: v3.0 Production Readiness

**Goal:** Transform the academic project into a portfolio-grade, deployable application with full CI/CD pipeline, comprehensive test coverage, and production-quality code across every layer.

**Target features:**
- CI/CD: GitHub Actions pipeline — build, unit tests, integration tests (Testcontainers), E2E (Playwright), Docker build & push to registry
- Testing: Unit (services/DAOs), integration (real DB), E2E browser tests, JaCoCo coverage report with minimum threshold
- Database: MongoDB indexes, query optimization, data model cleanup, validation constraints
- Config/secrets: No hardcoded secrets, proper env var handling for all environments
- Docker: Production-grade Compose, health checks, resource limits
- Code quality: Structured logging, dead code removal, complete OpenAPI docs, proper HTTP error responses
- Security: CORS policy, rate limiting, input validation, HTTPS-ready config
- UI: Full visual redesign — modern dark/neutral SaaS look (Vercel/Linear aesthetic), consistent design system

## Current State

**In progress: v3.0 — Production Readiness**

Phase 11 complete. Logging infrastructure added: Logback JSON/plaintext profile-switched config, request-ID tracing via MDC + X-Request-ID header.

**Previously shipped: v2.0 — Full Product (2026-04-11)**

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

### Validated (v3.0 — in progress)

- ✓ Logback JSON/plaintext profile-switched logging (QA-01) — Validated in Phase 11: logging-infrastructure
- ✓ LogstashEncoder JSON output in prod profile (QA-02) — Validated in Phase 11: logging-infrastructure
- ✓ Request-ID tracing: UUID in MDC + X-Request-ID response header (QA-03) — Validated in Phase 11: logging-infrastructure

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

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-11 — Phase 11 complete (logging infrastructure)*
