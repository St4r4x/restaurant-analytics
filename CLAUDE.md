# Restaurant Analytics - CLAUDE.md

## Project Overview

Spring Boot REST API + web dashboard for analyzing New York restaurant inspection data.
Data is synced live from the **NYC Open Data API** (`data.cityofnewyork.us`) into MongoDB.
Academic project (Aflokkat / big data module).

## Stack

- **Language**: Java 25
- **Framework**: Spring Boot 4.0.5
- **Primary DB**: MongoDB (driver: `mongodb-driver-sync`, raw aggregation pipelines — no Spring Data MongoDB)
- **RDBMS**: PostgreSQL 15 — users, bookmarks (Spring JPA / Hibernate)
- **Cache**: Redis 7 (TTL-based, via `RestaurantCacheService`)
- **Security**: JWT (access 15 min / refresh 7 days), Spring Security
- **Build**: Maven (`mvn`)
- **Config**: `src/main/resources/application.properties` (main config, no dotenv in production)
- **Testing**: JUnit 5 + Mockito
- **Deployment**: Docker / Docker Compose (plugin — use `docker compose`, not `docker-compose`)

## Architecture

```
com.aflokkat/
├── Application.java
├── config/           # AppConfig, MongoClientFactory, RedisConfig, SecurityConfig, OpenApiConfig
├── controller/       # RestaurantController, InspectionController, AuthController, UserController, ViewController
├── service/          # RestaurantService, AuthService
├── dao/              # RestaurantDAO + Impl, UserDAO + Impl (MongoDB)
├── repository/       # UserRepository, BookmarkRepository (Spring JPA / PostgreSQL)
├── cache/            # RestaurantCacheService (Redis)
├── sync/             # NycOpenDataClient, SyncService, NycApiRestaurantDto, SyncResult
├── domain/           # Restaurant, Address, Grade, User (MongoDB POJOs)
├── entity/           # UserEntity, BookmarkEntity (JPA entities)
├── dto/              # AuthRequest, JwtResponse, RegisterRequest, RefreshRequest, HeatmapPoint, AtRiskEntry, TopRestaurantEntry
├── aggregation/      # AggregationCount, BoroughCuisineScore, CuisineScore
├── security/         # JwtUtil, JwtAuthenticationFilter
└── util/             # ValidationUtil, ResponseUtil
```

## Data Flow

NYC Open Data API → `NycOpenDataClient` → `SyncService` → MongoDB (`newyork.restaurants`)
MongoDB → `RestaurantDAO` → `RestaurantService` → REST controllers → JSON responses
Hot data → `RestaurantCacheService` (Redis, TTL 3600s)
Users/bookmarks → PostgreSQL via Spring JPA

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
mvn test -Dtest=RestaurantDAOIntegrationTest

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
| `GET /api/restaurants/cuisine-scores?cuisine=X` | Avg score by borough for a cuisine |
| `GET /api/restaurants/worst-cuisines?borough=X&limit=N` | Worst cuisines in a borough |
| `GET /api/restaurants/popular-cuisines?minCount=N` | Cuisines with >= N restaurants |
| `GET /api/restaurants/stats` | Global stats |
| `GET /api/restaurants/health` | Health check |
| `GET /api/inspections/*` | Inspection endpoints |
| `GET /api/users/*` | User/bookmark endpoints |
| `GET /swagger-ui.html` | Swagger UI |

App runs on `http://localhost:8080`.

## Git Workflow

Branching and milestone releases are managed by **GSD** — use `/gsd-execute-phase`, `/gsd-complete-milestone`. Do not merge or tag manually.

### End-of-Phase Documentation

At the end of each phase, keep these files up to date:

1. **`CHANGELOG.md`** — add an entry under `## [Unreleased]`:
   ```
   ### Phase N: <Name> (YYYY-MM-DD)
   - <one-line per significant feature/endpoint added>
   ```

2. **`README.md`** — update stale sections (API endpoints table, architecture diagram).

## Key Notes

- Data comes from NYC Open Data API (no `restaurants.json` import needed in normal use)
- `init-restaurants.js` at project root is a broken empty directory owned by root — do NOT mount it in Docker; the volume mount was removed from `docker-compose.yml`
- Integration tests require live MongoDB on `localhost:27017` with `newyork` DB populated
- `nyc.api.max_records=0` means unlimited — set a small value (e.g. 5000) for local testing to avoid long sync
- Swagger available at `http://localhost:8080/swagger-ui.html`
