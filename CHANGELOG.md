# Changelog

All notable changes are documented by phase.

## [Phase 21] — 2026-04-13 — Upgrade Java 11 → 25 and Spring Boot 2.6.15 → 4.0.5

### Phase 21: Upgrade Java 11 → 25 and Spring Boot 2.6.15 → 4.0.5 (2026-04-13)
- Bumped Spring Boot parent from 2.6.15 to 4.0.5 and Java source/target from 11 to 25
- Migrated springdoc from springdoc-openapi-ui:1.8.0 to springdoc-openapi-starter-webmvc-ui:2.8.6
- Added logstash-logback-encoder 8.1 (replaces 7.3 — compatible with Logback 1.5.x in Boot 4)
- Removed junit:junit:4.13.2 and junit-vintage-engine (JUnit 4 no longer needed)
- Migrated 9 test files from JUnit 4 to JUnit 5 (import renames, @Test(expected) → assertThrows, @ClassRule → @BeforeAll)
- Migrated javax.servlet.* and javax.persistence.* → jakarta.* in 6 main + 1 test source file
- Migrated SecurityConfig.java from antMatchers/authorizeRequests to requestMatchers/authorizeHttpRequests (Spring Security 6 lambda DSL)
- Removed spring.mvc.pathmatch.matching-strategy=ant_path_matcher from application.properties (removed in Boot 3+)
- Removed spring.jpa.properties.hibernate.dialect from application.properties and application-test.properties (Hibernate 6 auto-detects PostgreSQL dialect)
- Fixed JaCoCo exclusion patterns from com/aflokkat/ to com/st4r4x/ (incorrect package was inflating coverage denominator)
- Exposed Jackson 2 ObjectMapper bean in RedisConfig (Boot 4 auto-configures Jackson 3 only; RestaurantCacheService requires Jackson 2)

## [Phase 14] — 2026-04-12 — Testcontainers Integration Tests

### Added
- Migrated RestaurantDAOIntegrationTest to RestaurantDAOIT using Testcontainers mongo:7.0 — no live MongoDB required
- Added UserRepositoryIT covering UserRepository.save/findByUsername and BookmarkRepository.save/findByUserId against Testcontainers postgres:15-alpine
- Added maven-failsafe-plugin bound to integration-test and verify goals; IT tests use *IT.java naming convention
- Added Testcontainers 1.19.8 (testcontainers, mongodb, postgresql) as test-scope dependencies, upgraded to 1.20.1 for Docker Engine 29.x compatibility
- Fixed Surefire argLine to use @{argLine} late-binding to support future JaCoCo integration
- Added System.getProperty(key) tier-0 lookup to AppConfig.getProperty() for TC URI injection

## [Phase 10] — 2026-04-10 — Admin Tools

### Added
- ROLE_ADMIN role: `admin.signup.code` property, `ADMIN_SIGNUP_CODE` env var, AuthService admin registration path
- `admin_test` seed account (ROLE_ADMIN) created by DataSeeder on startup (password: `Test1234!`)
- GET `/api/reports/stats` endpoint (AdminController, ADMIN-only): aggregate counts for inspection reports by status (OPEN/IN_PROGRESS/RESOLVED) and grade (A/B/C/F)
- GET `/api/inspection/at-risk/export.csv` download endpoint: at-risk restaurants as downloadable CSV
- SecurityConfig: `/api/reports/stats` ADMIN-only antMatcher declared before `/api/reports/**` CONTROLLER wildcard (first-match-wins ordering)
- GET `/admin` ViewController route returning admin Thymeleaf view
- `admin.html`: three-card admin page — Sync Controls (POST /api/restaurants/refresh, 2s polling, 10s auto-dismiss result), At-Risk CSV Download, Report Statistics (badge pills by status and grade)
- `navbar.html`: Admin nav link (hidden by default, visible only for ROLE_ADMIN via JS IIFE)
- Client-side ROLE_ADMIN IIFE guard in admin.html redirects non-ADMIN users to /

## [Phase 9] — 2026-04-08 — UX Polish

### Added
- Shared `fragments/ux-utils.html` fragment: skeleton shimmer CSS + `showToast()` notification system
- Pagination (20/page, Prev/Next) on search results, at-risk list, uncontrolled list, bookmarks list, and report list
- Skeleton loading cards replace all "Loading…" text across data-fetching sections
- Toast notifications replace all inline error/success messages (login.html excepted)
- Mobile-responsive navbar with hamburger menu at <=768px (dropdown with close-on-link-click)
- Viewport meta tag added to landing.html, index.html, and profile.html
- Table `overflow-x:auto` scroll wrappers on at-risk table (analytics.html) and uncontrolled table
- Responsive grid breakpoints: sample-grid, bookmarks-grid, nearby-grid, dashboard grids stack to 1-col at <=768px; KPI tiles go 2×2

## [Phase 8] — 2026-04-04 — Discovery Enhancement

### Added
- DISC-01: inspection-map.html filter bar with grade checkboxes (A/B/C/F), borough dropdown, cuisine dropdown; client-side marker filtering with live count badge
- DISC-02: /uncontrolled public page listing restaurants with grade C/Z or uninspected 12+ months; borough filter, score/days sort, CSV download; GET /api/inspection/uncontrolled + export.csv endpoints
- DISC-03: restaurant.html Nearby Restaurants section showing up to 5 restaurants within 500m; self-excludes current restaurant
- DISC-04: landing.html sort control above search results (Best Score / Worst Score / A→Z); client-side reorder, no new API calls

### Fixed (Phase 8 UAT gap-closure — 08-05)
- navbar.html: controller role nav link (Uncontrolled) shown only when JWT payload.role === ROLE_CONTROLLER
- uncontrolled.html: restaurant name cell is now a clickable link to /restaurant/{restaurantId}
- uncontrolled.html: CSV download button has flex-shrink:0 so it remains visible at narrow viewports
- inspection-map.html: removed redundant <h1>Restaurant Map</h1> from toolbar
- inspection-map.html: grade checkbox labels are color-coded (A=green, B=yellow, C/F=red)
- landing.html: search result rows restructured to 2-group flex layout for consistent borough alignment
- RestaurantService.java: added SLF4J debug logging in getLatitude for null-coord diagnosis

## [Phase 7] — 2026-04-03 — Homepage & Navigation

### Added
- Landing page (/) for anonymous visitors: hero section, stat strip, inline restaurant search, 3 sample restaurant cards
- Authenticated customer dashboard at / (personalised bookmarks strip + KPI tiles, shown when JWT present)
- Profile page (/profile): username, email, role badge, bookmark count (controller: report count too)
- Persistent sticky navbar fragment (fragments/navbar.html): Logo + Search/Map/Analytics links + auth area (Sign In or username + Sign Out)
- Navbar inserted into all 8 templates: landing, index, profile, analytics, dashboard, restaurant, inspection-map, my-bookmarks
- GET /api/restaurants/sample — 3 random restaurants for landing page discovery section
- GET /api/users/me — enriched response: bookmarkCount + reportCount fields
- ViewController routes: /profile, / split to landing.html vs index.html by JWT presence
- /profile and /dashboard protected client-side (localStorage token check); browser navigation does not forward Bearer headers
- /api/restaurants/sample is public (no auth required)

## [Phase 3] — 2026-03-31 — Customer Discovery

### Added
- Restaurant search endpoint: GET /api/restaurants/search?q=&limit=
- Map points endpoint: GET /api/restaurants/map-points (lightweight projection)
- Restaurant detail page (/restaurant/{camis}): grade badge, inspection history timeline
- Interactive map page (/inspection-map): Leaflet + markerCluster, grade-colored markers
- My bookmarks page (/my-bookmarks): client-side fetch-only template
- Bookmark CRUD: GET/POST /api/users/bookmarks, DELETE /api/users/bookmarks/{restaurantId}
- DAO methods: searchByNameOrAddress, findMapPoints
- ViewController route: /my-bookmarks

## [Phase 2] — 2026-03-30 — Controller Reports

### Added
- InspectionReportEntity (PostgreSQL/JPA) with Grade and Status enums
- ReportRepository: findByUserId, findByUserIdAndStatus
- ReportController: POST/GET /api/reports, PATCH /api/reports/{id}
- Photo upload: POST /api/reports/{id}/photo, GET /api/reports/{id}/photo
- AppConfig.getUploadsDir() with env-var → .env → properties fallback chain
- Docker named volume uploads_data:/app/uploads for photo persistence

## [Phase 1] — 2026-03-29 — Role Infrastructure

### Added
- role field on UserEntity (ROLE_CUSTOMER / ROLE_CONTROLLER)
- Controller signup gated by CONTROLLER_SIGNUP_CODE env var
- SecurityConfig antMatchers: /api/reports/** requires ROLE_CONTROLLER
- Bucket4j rate limiter on /api/auth/**
- DataSeeder: seeds customer_test and controller_test on startup
- JWT access token (15 min) + refresh token (7 days) with role claim
