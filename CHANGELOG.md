# Changelog

## [2.0.0] — 2026-03-20

### Added
- **Restaurant detail page** (`/restaurant/{id}`) — Leaflet map, full inspection history table, Chart.js score timeline, trust badge
- **Trust badge** — computed getters on `Restaurant` (latestGrade, latestScore, trend, badgeColor); zero schema change, inherited by all API endpoints
- **Geospatial search** — `GET /api/restaurants/nearby` with `$geoNear` pipeline; 2dsphere index created at startup; "Autour de moi" Leaflet widget on dashboard
- **Violation heatmap** (`/inspection-map`) — Leaflet + Leaflet.heat CDN, borough filter, admin-only
- **Inspection agent dashboard** (`/inspection`) — at-risk table (grade C/Z), worst-cuisines bar chart, recent inspections, CSV export; admin-only
- `GET /api/inspection/at-risk` and `GET /api/inspection/at-risk/export.csv` endpoints
- `GET /api/restaurants/heatmap` endpoint (admin)
- `GET /api/restaurants/recent-inspections` endpoint
- `POST /api/restaurants/rebuild-cache` — repopulate Redis leaderboard from MongoDB without external sync
- **Hygiene Radar** — renamed from Trash Advisor for a more professional image
- Admin nav links (inspection map, agent dashboard) shown conditionally via JWT role decode
- "Voir" buttons on top-restaurants and bookmarks cards linking to restaurant detail page

### Fixed
- `JwtAuthenticationFilter` — role claim from JWT was never loaded into Spring Security authorities; `@PreAuthorize` always returned 403
- `getAtRiskRestaurants` — MongoDB error 17124 (`$size` on null); fixed with `$ifNull: ["$lastGrades", []]`
- Heatmap invisible — weights were divided by 100 making all points transparent; fixed with raw scores and `max: 100` + `minOpacity: 0.35`
- `findAll` — BSON decode crash on legacy documents where `address` is a plain string; added `$type: 3` filter
- Admin user had `ROLE_USER` — promoted via PostgreSQL UPDATE
- `RestaurantCacheServiceTest` — mocked `redis.keys()` but implementation uses `redis.execute(RedisCallback)` + SCAN

### Changed
- `ResponseUtil.errorResponse()` extracted from duplicate private methods in `RestaurantController` and `InspectionController`; `UserController` now uses it too
- `Restaurant.getLatestGrade()` and `getLatestScore()` share a single `getLatestGradeEntry()` private helper (was two independent stream sorts)
- `inspection.html` initial load uses `Promise.all([loadAtRisk(), loadWorstCuisines()])` instead of sequential calls
- Removed 10 fake test documents from MongoDB (`init-restaurants.js` data)

### Removed
- `init-restaurants.js` — obsolete fake data seed file (data comes from NYC Open Data sync)

---

## [1.2.0] — Phase 3: Authentication & Bookmarks

### Added
- Spring Security + JJWT — `POST /api/auth/login`, `/register`, `/refresh`
- `ROLE_USER` / `ROLE_ADMIN` with `@PreAuthorize` on admin endpoints
- Bookmarks — `POST/DELETE/GET /api/users/me/bookmarks/{id}`, persisted in PostgreSQL
- `GET /api/users/me` profile endpoint
- Login page (`/login`), logout button, JWT stored in localStorage
- `BookmarkEntity` JPA + `BookmarkRepository`

---

## [1.1.0] — Phase 2: Redis Cache

### Added
- Spring Data Redis (Lettuce) — cache-aside on aggregations (TTL 1h)
- `restaurants:top` sorted set updated on every sync
- `GET /api/restaurants/top` leaderboard endpoint
- SCAN-based full cache invalidation on sync

---

## [1.0.0] — Phase 1: NYC Open Data Sync

### Added
- `NycOpenDataClient` — paginated HTTP client with exponential backoff
- `SyncService` — deduplication (N violation rows → 1 restaurant document), nightly `@Scheduled` at 02:00
- `POST /api/restaurants/refresh` — manual sync trigger
- `GET /api/restaurants/sync-status` — last sync result + running flag
- Docker Compose with MongoDB, Redis, PostgreSQL health checks

---

## [0.1.0] — Initial release

### Added
- Basic Spring Boot REST API with 4 MongoDB aggregation use cases
- Dashboard UI (Thymeleaf + vanilla JS)
- `GET /api/restaurants/by-borough`, `/cuisine-scores`, `/worst-cuisines`, `/popular-cuisines`, `/stats`
