
## [Unreleased] — 2026-05-05

### Bug Fixes
- Bump app.semver to 2.1.1
- Widen navbar-container to 1140px to prevent wrap when all nav items visible

### CI/CD
- Redeploy v2.1.1 navbar fix to production

### Chores
- Update for 81b03bed8c27d66bd71e56e761e2af15c20e1eea [skip ci]
- Update for bb941cb518f980e3a3a5a18c5435036e40245b5a [skip ci]
- Update for 24d3b78bef733a7da8fe536a786f2aa5e856dc2b [skip ci]

### Documentation
- Release v2.1.1 — navbar flex hotfix [skip ci]

## [Unreleased] — 2026-05-05

### Bug Fixes
- Bump app.semver to 2.1.1

### CI/CD
- Redeploy v2.1.1 navbar fix to production

### Chores
- Update for 81b03bed8c27d66bd71e56e761e2af15c20e1eea [skip ci]
- Update for bb941cb518f980e3a3a5a18c5435036e40245b5a [skip ci]

### Documentation
- Release v2.1.1 — navbar flex hotfix [skip ci]

## [Unreleased] — 2026-05-05

### CI/CD
- Redeploy v2.1.1 navbar fix to production

### Chores
- Update for 81b03bed8c27d66bd71e56e761e2af15c20e1eea [skip ci]

### Documentation
- Release v2.1.1 — navbar flex hotfix [skip ci]
# Changelog

All notable changes are documented here.

## [2.1.1] — 2026-05-05

### Bug Fixes
- Restore flex layout on navbar-container so logo and nav links are in the same row on all pages

## [2.1.0] — 2026-05-05

### Features
- Elasticsearch 8 as a 5th Docker service — full-text index of all restaurants, bulk-synced after each nightly data pull
- `GET /api/restaurants/autocomplete?q=` — fuzzy multi-match on name, cuisine, street, borough; returns up to 8 suggestions
- Autocomplete dropdown on landing page search bar — debounced 250 ms, keyboard navigation (↑↓ Enter Escape), name suggestions navigate to restaurant page
- OpenStreetMap enrichment — `OsmEnrichmentService` enriches restaurant phone, website, and opening hours from Overpass API (async, 1 req/s rate-limited); shown on restaurant detail page
- `POST /api/admin/osm-enrich` — admin endpoint to trigger a full OSM re-enrichment pass

### Bug Fixes
- Navbar alignment inconsistency between pages — fragment now carries its own `.navbar-container` CSS so all pages share identical max-width and padding

## [2.0.0] — 2026-05-03

### Bug Fixes
- Use Bootstrap collapse data attributes for hamburger menu
- Pull footer version from health endpoint instead of hardcoding
- Add separator between nav links and auth area; version as semver+sha
- Harden for pre-commercial readiness
- Restore partial-match for short queries alongside $text index

### Documentation
- Add frontend redesign design spec and implementation plan
- Add commercialisation guide
- Add architecture, API, configuration, deployment, UI, and development docs

### Performance
- Add missing indexes, fix text search and Redis connection leak

### Refactoring
- Split DAO interfaces and enforce controller→service layering

### Frontend Redesign
- Replace purple-gradient aesthetic with Clean Civic design system across all 11 templates and 2 fragments
- Bootstrap 5.3.3 + Playfair Display/Inter typography
- White navbar with 3px red bottom border, dark page headers, off-white page background
- KPI cards with red top border and Playfair Display numbers
- Grade badges (A/B/C) with semantic colors consistent across all pages
- Show deployed git SHA as version badge on every page

### CI/CD
- Add git-cliff changelog auto-update on main push

## [Phase 21] — 2026-04-13 — Upgrade Java 11 → 25 and Spring Boot 2.6.15 → 4.0.5

- Bumped Spring Boot parent from 2.6.15 to 4.0.5 and Java source/target from 11 to 25
- Migrated springdoc to springdoc-openapi-starter-webmvc-ui:2.8.6
- Removed JUnit 4; migrated 9 test files to JUnit 5
- Migrated javax.* → jakarta.* in 6 main + 1 test source file
- Migrated SecurityConfig to Spring Security 6 lambda DSL
- Fixed JaCoCo exclusion patterns for correct package name
- Exposed Jackson 2 ObjectMapper bean in RedisConfig

## [Phase 14] — 2026-04-12 — Testcontainers Integration Tests

- Migrated RestaurantDAOIntegrationTest to Testcontainers (mongo:7.0)
- Added UserRepositoryIT and BookmarkRepository tests against Testcontainers postgres:15-alpine
- Added maven-failsafe-plugin for IT tests (*IT.java naming convention)
- Fixed Surefire argLine to use @{argLine} late-binding for JaCoCo compatibility

## [Phase 10] — 2026-04-10 — Admin Tools

- ROLE_ADMIN role with admin signup code
- GET `/api/reports/stats` endpoint (ADMIN-only): aggregate report counts by status and grade
- GET `/api/inspection/at-risk/export.csv` download endpoint
- `admin.html`: Sync Controls, At-Risk CSV Download, Report Statistics

## [Phase 9] — 2026-04-08 — UX Polish

- Shared `fragments/ux-utils.html` fragment: skeleton shimmer CSS + toast notifications
- Pagination (20/page) on search results, at-risk list, uncontrolled list, bookmarks, and reports
- Mobile-responsive navbar with hamburger menu at <=768px

## [Phase 8] — 2026-04-04 — Discovery Enhancement

- Inspection map filter bar (grade, borough, cuisine) with live marker filtering
- Uncontrolled restaurants page (/uncontrolled): grade C/Z or uninspected 12+ months
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
