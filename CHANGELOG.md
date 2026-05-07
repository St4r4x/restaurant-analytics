# Changelog

All notable changes are documented here.

## [Unreleased]

## [2.2.1] — 2026-05-07

### Removed
- Remove OSM enrichment (`OsmEnrichmentService`) — Overpass API is blocked on Railway; phone data comes from NYC Open Data directly
- Remove Bug Report card from admin page — redundant with the floating 🐛 button already injected by ux-utils on every page

### Bug Fixes
- Move floating 🐛 bug report button to ux-utils fragment so it appears on all pages for all users, not just admin
- Fix version badge inconsistency across pages: cache version in sessionStorage so it renders identically on every page without re-fetching
- Fix analytics footer showing hardcoded v0.2.0 — now reads from sessionStorage like all other pages
- Add missing footer to inspection-map page
- Fix footer appearing in middle of bookmarks page when few bookmarks — add sticky footer layout (flex column + flex:1 on content)
- Fix admin page-header eyebrow misaligned with navbar — override container max-width to 1140px in page-header and footer
- Add missing footer to admin page
- Fix my-bookmarks page-header eyebrow misaligned with navbar — page-header container widened to 1140px; content cards keep narrower content-container

### CI
- Upgrade `actions/download-artifact` v4→v8 and `actions/checkout` v5→v6 to resolve Node.js 20 deprecation warnings

### Infrastructure
- Rename `Dockerfile` → `Dockerfile.ci` so Railway Railpack auto-detects Maven instead of forcing DOCKERFILE builder mode
- Add `railway.toml` with explicit `buildCommand` to prevent Railpack from adding `-Pproduction` flag (no such Maven profile exists)
- Add `.tool-versions` to specify Java 25 for Railpack (defaults to Java 21 otherwise, which doesn't support `--sun-misc-unsafe-memory-access=allow`)

## [2.2.0] — 2026-05-06

### Features
- Grade trend chart: per-point colour by grade (A=green, B=amber, C=orange, F=red), inverted y-axis, grade in tooltip
- Add paginated Audit Log card to admin page (DOM-safe rendering, Prev/Next pagination)
- Add `GET /api/admin/audit` endpoint (ADMIN only) with paginated audit entries; log actions in AdminController, RestaurantController, ReportController
- Add AuditLogEntity, AuditService, AuditLogRepository for persisting admin actions to PostgreSQL audit_log table
- Add User Management card on admin page: list all users, change role via dropdown
- Add `GET /api/admin/users` and `POST /api/admin/users/{id}/role` endpoints (ADMIN)
- Add Bug Report card on admin page with GitHub Issues links (bug + feature request)
- Add floating 🐛 button on admin page linking to GitHub Issues
- Show completedAt date+time alongside record count on admin sync status
- Add `POST /api/admin/cron/run/{jobKey}` endpoint to manually trigger any cron job (ADMIN)
  Valid keys: `cache-warmup` (sync), `osm-reenrichment` (async), `es-reindex` (async)
  Writes to the job registry so `/api/admin/cron/status` reflects the run immediately
- Expose `/actuator/prometheus` (Micrometer + Prometheus) locked to ADMIN role; `/actuator/health` remains public for Railway health checks

### Bug Fixes
- Null-guard actorUsername in AuditService when auth.getName() returns null
- Serialize AuditLogEntity action as .name() string in GET /api/admin/audit response
- Add missing @CrossOrigin to AdminController and ReportController
- Pass error detail to SYNC_TRIGGERED audit entry when sync fails
- Fix audit pagination: disable Next button by default; fix falsy page=0 guard; hide table on empty page after navigation
- Fix score chart: destroy previous Chart.js instance before re-render; guard pending grade codes (Z/P/N) as grey; safe resize in setTimeout

### Chores
- Add .githooks/pre-commit enforcing CHANGELOG update on code commits
- Update CLAUDE.md: fix package name, expand architecture tree, endpoints, Git workflow
- Remove deprecated ROADMAP.md
- Improve global git rules: conventional commits prefix, unified CHANGELOG rule, semver rule

### CI/CD
- Fix Docker Build: upload JAR from build job as artifact, download in e2e+docker jobs; replace Maven builder stage with direct JAR copy to eliminate Maven Central 403 rate-limiting

## [2.1.1] — 2026-05-05

### Bug Fixes
- Restore flex layout on navbar-container so logo and nav links are in the same row on all pages
- Widen navbar-container to 1140px to prevent wrap when all nav items visible
- Fix autocomplete dropdown clipped by hero overflow:hidden; fix ES index mapping boost removed in ES8
- Move nearby+contact into left column, truncate violation descriptions, fix chart resize
- Bump app.semver to 2.1.1

### Features
- Add street to autocomplete search with edge-ngram; add reindex retry logic
- Show formatted address with Maps link and formatted phone on restaurant detail page; trigger OSM enrichment on startup
- Improve autocomplete with edge-ngram index and borough-aware query splitting

### CI/CD
- Parallelize unit+integration tests in CI; remove redundant changelog job

## [2.1.0] — 2026-05-05

### Features
- Elasticsearch 8 as a 5th Docker service — full-text index of all restaurants, bulk-synced after each nightly data pull
- `GET /api/restaurants/autocomplete?q=` — fuzzy multi-match on name, cuisine, street, borough; returns up to 8 suggestions
- Autocomplete dropdown on landing page search bar — debounced 250 ms, keyboard navigation (↑↓ Enter Escape)
- OpenStreetMap enrichment — `OsmEnrichmentService` enriches restaurant phone, website, and opening hours from Overpass API (async, 1 req/s)
- `POST /api/admin/osm-enrich` — admin endpoint to trigger a full OSM re-enrichment pass
- `GET /api/admin/cron/status` — cron job registry endpoint (ADMIN)
- `CronScheduler` with cache warm-up (02:30), OSM re-enrichment (Sun 03:00), ES reindex (04:00)

### Bug Fixes
- Navbar alignment inconsistency between pages

## [2.0.0] — 2026-05-03

### Frontend Redesign
- Replace purple-gradient aesthetic with Clean Civic design system across all 11 templates and 2 fragments
- Bootstrap 5.3.3 + Playfair Display/Inter typography
- KPI cards with red top border; grade badges with semantic colors consistent across all pages
- Show deployed git SHA as version badge on every page

### Features
- `AnalyticsDAO` split from `RestaurantDAO` — owns heatmap, borough-grade distribution, at-risk aggregations
- Rename `domain.Grade` → `InspectionRecord`, `entity.Grade` → `LetterGrade`

### Bug Fixes
- Restore partial-match for short queries alongside $text index
- Add missing indexes, fix text search and Redis connection leak

### Documentation
- Add architecture, API, configuration, deployment, UI, and development docs
- Add commercialisation guide

### CI/CD
- Add git-cliff changelog auto-update on main push (later removed in favour of manual updates)

## [Phase 21] — 2026-04-13 — Upgrade Java 11 → 25 and Spring Boot 2.6.15 → 4.0.5

- Bumped Spring Boot parent from 2.6.15 to 4.0.5 and Java source/target from 11 to 25
- Migrated springdoc to springdoc-openapi-starter-webmvc-ui:2.8.6
- Removed JUnit 4; migrated 9 test files to JUnit 5
- Migrated javax.* → jakarta.* in 6 main + 1 test source file
- Migrated SecurityConfig to Spring Security 6 lambda DSL

## [Phase 14] — 2026-04-12 — Testcontainers Integration Tests

- Migrated RestaurantDAOIntegrationTest to Testcontainers (mongo:7.0)
- Added UserRepositoryIT and BookmarkRepository tests against Testcontainers postgres:15-alpine
- Added maven-failsafe-plugin for IT tests (*IT.java naming convention)

## [Phase 10] — 2026-04-10 — Admin Tools

- ROLE_ADMIN role with admin signup code
- `GET /api/reports/stats` endpoint (ADMIN-only)
- `GET /api/inspection/at-risk/export.csv` download endpoint
- admin.html: Sync Controls, At-Risk CSV Download, Report Statistics

## [Phase 9] — 2026-04-08 — UX Polish

- Shared `fragments/ux-utils.html` fragment: skeleton shimmer CSS + toast notifications
- Pagination (20/page) on search results, at-risk list, uncontrolled list, bookmarks, reports
- Mobile-responsive navbar with hamburger menu at <=768px

## [Phase 8] — 2026-04-04 — Discovery Enhancement

- Inspection map filter bar (grade, borough, cuisine) with live marker filtering
- Uncontrolled restaurants page (/uncontrolled)
- Nearby restaurants section on restaurant detail page (within 500m)
- Landing page sort control (Best Score / Worst Score / A→Z)

## [Phase 7] — 2026-04-03 — Homepage & Navigation

- Landing page for anonymous visitors: hero section, stat strip, inline search, sample cards
- Authenticated customer dashboard with bookmarks strip + KPI tiles
- Profile page (/profile): username, email, role badge, bookmark count
- Persistent sticky navbar fragment inserted into all templates

## [Phase 3] — 2026-03-31 — Customer Discovery

- Restaurant search, map points, restaurant detail page, interactive Leaflet map
- My bookmarks page; bookmark CRUD endpoints

## [Phase 2] — 2026-03-30 — Controller Reports

- InspectionReportEntity (PostgreSQL/JPA) with Grade and Status enums
- Report CRUD and photo upload endpoints

## [Phase 1] — 2026-03-29 — Role Infrastructure

- ROLE_CUSTOMER / ROLE_CONTROLLER with gated controller signup
- JWT access (15 min) + refresh (7 days) with role claim
- Bucket4j rate limiter on /api/auth/**
- DataSeeder: seeds customer_test and controller_test on startup
