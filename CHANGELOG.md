# Changelog

All notable changes are documented by phase.

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
