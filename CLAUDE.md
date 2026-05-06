# Restaurant Analytics - CLAUDE.md

## Project Overview

Spring Boot REST API + web dashboard for analyzing New York restaurant inspection data.
Data is synced live from the **NYC Open Data API** (`data.cityofnewyork.us`) into MongoDB.
Academic project (Aflokkat / big data module).

## Stack

- **Language**: Java 25
- **Framework**: Spring Boot 4.0.5
- **Primary DB**: MongoDB (driver: `mongodb-driver-sync`, raw aggregation pipelines — no Spring Data MongoDB)
- **RDBMS**: PostgreSQL 15 — users, bookmarks, inspection reports (Spring JPA / Hibernate)
- **Cache**: Redis 7 (TTL-based, via `RestaurantCacheService`)
- **Search**: Elasticsearch (via `ElasticsearchSyncService`)
- **Security**: JWT (access 15 min / refresh 7 days), Spring Security
- **Build**: Maven (`mvn`)
- **Config**: `src/main/resources/application.properties` (main config, no dotenv in production)
- **Testing**: JUnit 5 + Mockito
- **Deployment**: Docker / Docker Compose (plugin — use `docker compose`, not `docker-compose`)

## Architecture

```
com.st4r4x/
├── Application.java
├── config/           # AppConfig, MongoClientFactory, RedisConfig, SecurityConfig, OpenApiConfig, ElasticsearchConfig, MethodSecurityConfig
├── controller/       # RestaurantController, AnalyticsController, InspectionController, AuthController,
│                     # UserController, AdminController, ReportController, ViewController
├── service/          # RestaurantService, AuthService
├── dao/              # RestaurantDAO + Impl, AnalyticsDAO + Impl (MongoDB), RestaurantWriteDAO
├── repository/       # UserRepository, BookmarkRepository, ReportRepository (Spring JPA / PostgreSQL)
├── cache/            # RestaurantCacheService (Redis)
├── sync/             # NycOpenDataClient, SyncService, OsmEnrichmentService, ElasticsearchSyncService,
│                     # CronScheduler, JobStatus, NycApiRestaurantDto, SyncResult
├── domain/           # Restaurant, Address, InspectionRecord (MongoDB POJOs)
├── entity/           # UserEntity, BookmarkEntity, InspectionReportEntity, LetterGrade, Status (JPA)
├── dto/              # AuthRequest, JwtResponse, RegisterRequest, RefreshRequest, HeatmapPoint,
│                     # AtRiskEntry, TopRestaurantEntry, UncontrolledEntry, ReportRequest
├── aggregation/      # AggregationCount, BoroughCuisineScore, CuisineScore
├── security/         # JwtUtil, JwtService, JwtAuthenticationFilter, RateLimitFilter
├── startup/          # DataSeeder
└── util/             # ValidationUtil, ResponseUtil
```

**Key naming notes:**
- `domain.InspectionRecord` — full inspection POJO (date, grade, score, violations…) stored in MongoDB
- `entity.LetterGrade` — JPA enum `{ A, B, C, F }` used in `InspectionReportEntity`
- `AnalyticsDAO` — owns heatmap, borough-grade-distribution, and at-risk aggregations (split from `RestaurantDAO`)

## Data Flow

NYC Open Data API → `NycOpenDataClient` → `SyncService` → MongoDB (`newyork.restaurants`)
MongoDB → `RestaurantDAO` / `AnalyticsDAO` → `RestaurantService` → REST controllers → JSON responses
Hot data → `RestaurantCacheService` (Redis, TTL 3600s)
Users / bookmarks / reports → PostgreSQL via Spring JPA
OSM data → `OsmEnrichmentService` (phone, website, opening hours — runs on startup + weekly cron)
Elasticsearch → `ElasticsearchSyncService` (full-text search, rebuilt daily by `CronScheduler`)

## Configuration (`application.properties`)

```properties
mongodb.uri=mongodb://mongodb:27017
mongodb.database=newyork
mongodb.collection=restaurants

spring.datasource.url=jdbc:postgresql://postgres:5432/restaurantdb
spring.datasource.username=restaurant
spring.datasource.password=restaurant

redis.host=localhost
redis.port=6379
redis.cache.ttl-seconds=3600

nyc.api.url=https://data.cityofnewyork.us/resource/43nn-pn8j.json
nyc.api.app_token=          # optional — avoids rate limiting
nyc.api.page-size=1000
nyc.api.max_records=0       # 0 = unlimited

jwt.secret=<min 32 chars>
jwt.access.expiration.ms=900000
jwt.refresh.expiration.ms=604800000

spring.task.scheduling.pool.size=4
app.semver=2.1.1
```

Docker Compose sets `MONGODB_URI`, `MONGODB_DATABASE`, `MONGODB_COLLECTION`, `REDIS_HOST`, `REDIS_PORT` as env vars on the app container.

## Common Commands

```bash
# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
java -jar target/quickstart-app-1.0-SNAPSHOT.jar

# Tests
mvn test
mvn test -Dtest=RestaurantDAOImplTest

# Docker
docker compose up -d
docker compose logs -f app
docker compose down
```

## API Endpoints

| Endpoint | Description |
|---|---|
| `POST /api/auth/register` | Register user |
| `POST /api/auth/login` | Login → JWT |
| `POST /api/auth/refresh` | Refresh token |
| `GET /api/restaurants/by-borough` | Count per borough |
| `GET /api/restaurants/by-cuisine` | Count per cuisine |
| `GET /api/restaurants/top` | Top restaurants (Redis sorted set) |
| `GET /api/restaurants/sample` | Random sample |
| `GET /api/restaurants/search` | Full-text search (Elasticsearch) |
| `GET /api/restaurants/autocomplete` | Autocomplete (Elasticsearch edge-ngram) |
| `GET /api/restaurants/nearby` | Nearby restaurants by coords |
| `GET /api/restaurants/stats` | Global stats |
| `GET /api/restaurants/health` | Health check |
| `GET /api/restaurants/sync-status` | Last sync state |
| `POST /api/restaurants/refresh` | Trigger manual sync |
| `GET /api/analytics/heatmap` | Heatmap data points |
| `GET /api/analytics/borough-grades` | Grade distribution per borough |
| `GET /api/analytics/at-risk` | At-risk restaurants |
| `GET /api/analytics/cuisine-rankings` | Cuisine hygiene rankings |
| `GET /api/analytics/kpis` | Dashboard KPIs |
| `GET /api/inspection/uncontrolled` | Uncontrolled restaurants |
| `GET /api/inspection/recent-inspections` | Recent inspection feed |
| `GET /api/users/me` | Current user profile |
| `GET /api/users/me/bookmarks` | User bookmarks |
| `POST/DELETE /api/users/me/bookmarks/{restaurantId}` | Add/remove bookmark |
| `POST /api/reports` | Submit hygiene report |
| `GET /api/reports/stats` | Report statistics |
| `GET /api/admin/cron/status` | Cron job registry (ADMIN) |
| `POST /api/admin/osm-enrich` | Trigger OSM re-enrichment (ADMIN) |
| `POST /api/admin/rebuild-cache` | Rebuild Redis cache (ADMIN) |
| `GET /swagger-ui.html` | Swagger UI |

App runs on `http://localhost:8080`.

## Git Workflow

Work on `feature/<topic>` branches, merge to `main`. Use Superpowers skills for planning and execution:
- Design: `/brainstorm` → saves spec to `docs/superpowers/specs/`
- Plan: invokes `writing-plans` → saves plan to `docs/superpowers/plans/`
- Execute: `subagent-driven-development` or `executing-plans`
- Finish: `finishing-a-development-branch`

### End-of-Phase Documentation

After each significant feature, keep these files up to date:

1. **`CHANGELOG.md`** — add an entry under `## [Unreleased]`
2. **`README.md`** — update stale sections (API endpoints table, architecture)
3. **`CLAUDE.md`** — update Architecture and API Endpoints if new packages/routes were added

## Key Notes

- Data comes from NYC Open Data API (no `restaurants.json` import needed in normal use)
- Integration tests require live MongoDB on `localhost:27017` with `newyork` DB populated
- `nyc.api.max_records=0` means unlimited — set a small value (e.g. 5000) for local testing to avoid long sync
- Swagger available at `http://localhost:8080/swagger-ui.html`
- `DataSeeder` seeds known credentials unconditionally — gate behind `@Profile("dev")` before going to production
- `CronScheduler` owns all `@Scheduled` jobs: cache warm-up (02:30), OSM re-enrichment (Sun 03:00), ES reindex (04:00)
