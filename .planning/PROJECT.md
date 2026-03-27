# Restaurant Hygiene Control App

## What This Is

A web application that connects restaurant hygiene controllers and customers around NYC inspection data. Controllers file internal inspection reports (violations, grades, photos, follow-up status) for restaurants. Customers search for restaurants and instantly see their hygiene grade and cleanliness score. Built on top of a Spring Boot API already syncing data from the NYC Open Data API.

## Core Value

A customer can search any NYC restaurant and immediately know whether it's clean — and a controller can document new hygiene findings against the same data.

## Requirements

### Validated

- ✓ NYC Open Data API sync into MongoDB — existing
- ✓ Restaurant data queryable by borough, cuisine, score, grade — existing
- ✓ JWT authentication (register, login, refresh) — existing
- ✓ User accounts in PostgreSQL — existing
- ✓ Redis TTL cache for expensive analytics queries — existing
- ✓ REST API with Swagger documentation — existing
- ✓ Web dashboard with HTML templates (ViewController) — existing

### Active

- [ ] Separate controller registration (signup code / dedicated route)
- [ ] Role-based access control (CUSTOMER vs CONTROLLER roles)
- [ ] Controller can create an internal inspection report for a restaurant
- [ ] Inspection report contains: violations found, overall score/grade, photos, status/follow-up
- [ ] Controller can view, edit, and update their submitted reports
- [ ] Customer can search restaurants by name or address
- [ ] Customer sees hygiene grade and cleanliness score for a restaurant
- [ ] Customer can browse restaurants on an interactive map
- [ ] Restaurant detail page shows grade, score, and NYC inspection history

### Out of Scope

- Pushing controller reports to the NYC Open Data API — no write access in v1
- Customer-visible controller reports — internal reports are for controllers only in v1
- Mobile native app — web-first
- Real-time notifications — defer to v2
- Multi-city support — NYC only

## Context

The codebase is a production-grade Spring Boot 2.6.15 monolith with:
- MongoDB for restaurant/inspection data (raw `mongodb-driver-sync`, no Spring Data)
- PostgreSQL for users/bookmarks (Spring Data JPA)
- Redis 7 for caching
- JWT security (Spring Security)
- Existing auth supports single user role — needs extending for CUSTOMER/CONTROLLER split
- Existing `UserEntity` in PostgreSQL — role field needs adding
- `ViewController.java` already serves Thymeleaf/HTML templates — customer UI can extend this
- `BookmarkRepository` exists in PostgreSQL — can model for controller reports too

Deployment: Docker Compose (4 containers: app, MongoDB, Redis, Postgres).

## Constraints

- **Tech stack**: Java 11, Spring Boot 2.6.15 — no framework upgrade in v1
- **Database**: MongoDB for restaurant data, PostgreSQL for user/report metadata — stay with existing split
- **Auth**: JWT — extend existing system, don't replace
- **NYC API**: Read-only — no write access to external data source
- **Academic**: This is an academic project (Aflokkat / big data module)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Controller reports stored in PostgreSQL (JPA) | Structured relational data, fits existing JPA layer, not restaurant data | — Pending |
| Role field added to UserEntity | Simplest extension of existing auth — no new table | — Pending |
| Controller signup via registration code | Prevents open public access to filing reports | — Pending |
| Customer UI extends existing ViewController | Reuses Thymeleaf template infrastructure already in place | — Pending |

---
*Last updated: 2026-03-27 after initialization*
