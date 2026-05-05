
## [Unreleased] — 2026-05-05

### Bug Fixes
- Self-contain navbar container to fix cross-page alignment
- Register Jsr310CodecProvider for Instant BSON serialization
- Add @Autowired, @EnableAsync, RestTemplate timeouts, better error logging
- Remove misleading @Autowired, add @Async to enrichNew
- Remove unused Consumer import in SyncServiceOsmTest
- Replace assert keyword with JUnit assertEquals in AdminOsmEnrichTest
- Validate osmWebsite scheme before assigning to href
- Add OsmEnrichmentService mock to SyncServiceTest
- Guard empty bulk batch, async reindex init, close cursor
- Cache item.error() to resolve null-pointer diagnostic
- Reliable selectItem routing and ArrowUp deselect in autocomplete

### Chores
- Update for 03f5873021e8c3add516b50411f3c376c35d417c [skip ci]
- Remove unnecessary @SuppressWarnings from SyncServiceTest
- Suppress unchecked cast warning in autocomplete test
- Remove duplicate Unreleased section after rebase

### Documentation
- Add design spec for ES search, navbar fix, OSM enrichment
- Add implementation plans for navbar fix, OSM enrichment, and ES autocomplete
- Release v2.1.0 — Elasticsearch autocomplete, OSM enrichment, navbar fix

### Features
- Add osm enrichment fields to Restaurant domain
- Add OsmEnrichmentService with Overpass API integration
- Wire OsmEnrichmentService into SyncService post-sync
- Add POST /api/admin/osm-enrich endpoint
- Show phone, website, hours on restaurant detail page
- Add Elasticsearch 8 dependency and Docker service
- Add ElasticsearchConfig bean
- Add ElasticsearchSyncService with bulk reindex
- Trigger ES reindex after successful sync
- Add GET /api/restaurants/autocomplete endpoint
- Add autocomplete dropdown to landing page search bar
# Changelog

All notable changes are documented here.

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

### Chores
- Update for 371dfeb017f4c5866f1734a73fba2db0dac48ab8 [skip ci]
- Add .superpowers to .gitignore; update CHANGELOG for frontend redesign
- Update for 533a5040ffe5f32892b36cf7916a16a6f9d3575f [skip ci]
- Ignore screenshots, fix test passwords, add UI design system to README
- Update for 2da8dfd23ba0b0295f820bb41aa72324c2bec093 [skip ci]
- Update for 8866a508a618ee490b54bd8aeb6993ebde70625d [skip ci]
- Update for f8c5fda78ce648834fd271a420711f9839faf23e [skip ci]
- Update for d71a0e3e59b0d83452704010624b04e23d82f63a [skip ci]
- Update for 947c5f834db8a29232ace974f1c270abbe0a641c [skip ci]
- Update for a30cd551bd278dc091e9ac8a0fdf1034a2cd5faa [skip ci]

### Documentation
- Add frontend redesign design spec
- Update frontend spec — login/register tab detail
- Add frontend redesign implementation plan
- Add commercialisation guide

### Performance
- Add missing indexes, fix text search and Redis connection leak

### Refactoring
- Split DAO interfaces and enforce controller→service layering


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
