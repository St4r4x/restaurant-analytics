# Architecture — Restaurant Analytics

## Overview

Spring Boot REST API with a Thymeleaf dashboard for analyzing New York City restaurant
inspection data. Data is sourced nightly from the NYC Open Data API, stored in MongoDB,
and served through a Redis cache layer.

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 11 |
| Framework | Spring Boot | 2.6.15 |
| Primary DB | MongoDB (driver-sync) | 4.x |
| Relational DB | PostgreSQL + Spring Data JPA | 15 |
| Cache / Leaderboard | Redis (Lettuce) | 7-alpine |
| Security | Spring Security + JJWT | 0.11.5 |
| API documentation | Springdoc OpenAPI | 1.8.0 |
| Build | Maven | 3.x |
| Config | dotenv-java | 3.0.0 |
| Testing | JUnit 4 + Mockito | 4.x (BOM) |
| Deployment | Docker / Docker Compose | — |

---

## Package Structure

```
com.aflokkat/
├── Application.java              # @SpringBootApplication, @EnableScheduling
│
├── config/
│   ├── AppConfig.java            # Env-var / .env / properties resolver
│   ├── MongoClientFactory.java   # MongoDB client singleton
│   ├── OpenApiConfig.java        # Springdoc metadata (title, version, description)
│   └── RedisConfig.java          # Lettuce connection factory
│
├── controller/
│   ├── RestaurantController.java # REST endpoints (/api/restaurants/*)
│   ├── InspectionController.java # Admin-only endpoints (/api/inspection/*)
│   ├── UserController.java       # User profile & bookmarks (/api/users/*)
│   ├── AuthController.java       # Auth endpoints (/api/auth/*)
│   └── ViewController.java       # Thymeleaf page routes
│
├── service/
│   ├── RestaurantService.java    # Business logic, delegates to DAO
│   └── AuthService.java          # Register / login / refresh logic
│
├── security/
│   ├── JwtUtil.java              # Token generation, validation, claims extraction
│   └── JwtAuthenticationFilter.java  # OncePerRequestFilter — sets SecurityContext
│
├── cache/
│   └── RestaurantCacheService.java  # Redis cache-aside + sorted set
│
├── dao/
│   ├── RestaurantDAO.java        # Interface
│   ├── RestaurantDAOImpl.java    # MongoDB impl (raw driver, aggregation pipelines)
│   ├── UserDAO.java              # Interface (legacy Mongo user DAO)
│   └── UserDAOImpl.java          # MongoDB impl
│
├── entity/
│   ├── UserEntity.java           # JPA entity — mapped to PostgreSQL `users` table
│   └── BookmarkEntity.java       # JPA entity — mapped to PostgreSQL `bookmarks` table
│
├── repository/
│   ├── UserRepository.java       # Spring Data JPA repository
│   └── BookmarkRepository.java   # Spring Data JPA repository (findByUserId, existsByUserIdAndRestaurantId)
│
├── domain/
│   ├── Restaurant.java           # Main POJO (BSON-mapped) + computed badge getters
│   │                             #   getLatestGrade(), getLatestScore(), getTrend(), getBadgeColor()
│   ├── Address.java              # Embedded address + GeoJSON coords
│   ├── Grade.java                # Inspection record (date, score, grade, violation, criticalFlag)
│   └── User.java                 # MongoDB user domain object
│
├── aggregation/
│   ├── AggregationCount.java     # { id, count } — result of $group by field
│   ├── BoroughCuisineScore.java  # { borough, avgScore }
│   └── CuisineScore.java         # { cuisine, avgScore, count }
│
├── dto/
│   ├── TopRestaurantEntry.java   # Lightweight Redis sorted-set snapshot
│   ├── HeatmapPoint.java         # { lat, lng, weight } — heatmap aggregation result
│   ├── AtRiskEntry.java          # { restaurantId, name, borough, cuisine, lastGrade, lastScore, consecutiveBadGrades }
│   ├── AuthRequest.java          # { username, password }
│   ├── RegisterRequest.java      # { username, email, password }
│   ├── RefreshRequest.java       # { refreshToken }
│   └── JwtResponse.java          # { accessToken, refreshToken }
│
├── sync/
│   ├── NycOpenDataClient.java    # Paginated HTTP client (RestTemplate, exp. backoff)
│   ├── NycApiRestaurantDto.java  # Jackson DTO — one row per inspection record
│   ├── SyncService.java          # Orchestrates fetch → map → upsert → cache
│   └── SyncResult.java           # Immutable sync result (builder pattern)
│
└── util/
    ├── ValidationUtil.java       # requirePositive, requireNonEmpty, validateFieldName
    └── ResponseUtil.java         # Shared errorResponse() — 400 for IllegalArgument, 500 otherwise
```

---

## Data Flow

```
┌─────────────────────────────────────────────────────────────┐
│                        Nightly Sync (02:00)                  │
│          or  POST /api/restaurants/refresh                   │
└──────────────────────────┬──────────────────────────────────┘
                           │
                    NycOpenDataClient
                    (paginated, retry)
                           │
                    NYC Open Data API
                    43nn-pn8j.json
                           │
                    SyncService.mapToRestaurants()
                    group by camis → deduplicate grades by date
                           │
              ┌────────────┴────────────┐
              │                         │
        MongoDB upsert             Redis update
        (bulkWrite)                invalidateAll()
                                   updateTopRestaurants()
```

```
HTTP Request
     │
RestaurantController
     │
     ├──► RestaurantCacheService.getOrLoad(key, loader, type)
     │         │
     │    Cache HIT ──► return JSON from Redis (TTL 1h)
     │         │
     │    Cache MISS ──► RestaurantService ──► RestaurantDAO ──► MongoDB
     │                       store in Redis
     │
     └──► GET /api/restaurants/top
               └──► RestaurantCacheService.getTopRestaurants(limit)
                         └──► Redis ZRANGE restaurants:top 0 N-1
```

---

## Authentication Flow

```
POST /api/auth/register  or  POST /api/auth/login
           │
      AuthController
           │
      AuthService
       ├── validate inputs (ValidationUtil)
       ├── BCrypt hash / match password
       ├── load / save UserEntity via UserRepository (PostgreSQL)
       └── JwtUtil.generateAccessToken()  +  generateRefreshToken()
                │
         JwtResponse { accessToken (15min), refreshToken (7d) }

──────────────────────────────────────────────────────────────────
Every subsequent request:

  Authorization: Bearer <accessToken>
           │
  JwtAuthenticationFilter (OncePerRequestFilter)
           │
  JwtUtil.getClaimsIfValid(token)
           ├── valid  → set SecurityContext (username + role as GrantedAuthority)
           └── invalid → continue unauthenticated (public endpoints only)
```

---

## PostgreSQL Data Model

### Table: `users`

| Column | Type | Constraints |
|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY |
| `username` | VARCHAR | UNIQUE NOT NULL |
| `email` | VARCHAR | UNIQUE NOT NULL |
| `password_hash` | VARCHAR | NOT NULL (BCrypt) |
| `role` | VARCHAR | NOT NULL (default `ROLE_USER`) |
| `created_at` | TIMESTAMP | NOT NULL |

### Table: `bookmarks`

| Column | Type | Constraints |
|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY |
| `user_id` | BIGINT | FK → `users.id` NOT NULL |
| `restaurant_id` | VARCHAR | NOT NULL (MongoDB restaurant_id / camis) |
| `created_at` | TIMESTAMP | NOT NULL |
| — | UNIQUE | (`user_id`, `restaurant_id`) |

Managed by Spring Data JPA / Hibernate auto-DDL.

---

## MongoDB Data Model

### Collection: `restaurants`

```json
{
  "_id":           ObjectId,
  "restaurant_id": "30075445",
  "name":          "Morris Park Bake Shop",
  "cuisine":       "Bakery",
  "borough":       "Bronx",
  "phone":         "7188924968",
  "address": {
    "building": "1007",
    "street":   "Morris Park Ave",
    "zipcode":  "10462",
    "coord":    [-73.856077, 40.848447]
  },
  "grades": [
    {
      "date":                  "2014-03-03T00:00:00.000",
      "grade":                 "A",
      "score":                 2,
      "inspection_type":       "Cycle Inspection / Initial Inspection",
      "action":                "Violations were cited in the following area(s).",
      "violation_code":        "10F",
      "violation_description": "Non-food contact surface improperly constructed...",
      "critical_flag":         "Not Critical"
    }
  ]
}
```

**Key design decisions:**
- One document per restaurant (`restaurant_id` = camis from NYC API)
- Grades embedded (one per inspection date — multiple violation rows from the API are deduplicated)
- Coordinates stored as GeoJSON `[longitude, latitude]` for `$geoNear` compatibility

### Index recommendations

```js
db.restaurants.createIndex({ restaurant_id: 1 }, { unique: true })
db.restaurants.createIndex({ borough: 1 })
db.restaurants.createIndex({ cuisine: 1 })
db.restaurants.createIndex({ "address.coord": "2dsphere" })
```

---

## Redis Data Model

### Cache keys (String, JSON, TTL = 1h)

| Key | Value | Set by |
|---|---|---|
| `restaurants:by_borough` | `List<AggregationCount>` JSON | `GET /api/restaurants/by-borough` |
| `restaurants:cuisine_scores:{cuisine}` | `List<BoroughCuisineScore>` JSON | `GET /api/restaurants/cuisine-scores` |
| `restaurants:worst_cuisines:{borough}:{limit}` | `List<CuisineScore>` JSON | `GET /api/restaurants/worst-cuisines` |

### Sorted set

| Key | Member | Score |
|---|---|---|
| `restaurants:top` | `TopRestaurantEntry` JSON | Latest inspection score (lower = healthier) |

All `restaurants:*` keys are deleted on every successful sync.

---

## API Endpoints

### Restaurants (authenticated)

| Method | Path | Auth | Description | Cached |
|---|---|---|---|---|
| GET | `/api/restaurants/by-borough` | User | Restaurant count per borough | ✅ Redis |
| GET | `/api/restaurants/cuisine-scores?cuisine=` | User | Avg inspection score by borough for a cuisine | ✅ Redis |
| GET | `/api/restaurants/worst-cuisines?borough=&limit=` | User | Worst cuisines by avg score in a borough | ✅ Redis |
| GET | `/api/restaurants/popular-cuisines?minCount=` | User | Cuisines with ≥ N restaurants | ❌ |
| GET | `/api/restaurants/top?limit=` | User | Healthiest restaurants (Redis sorted set) | ✅ Redis |
| GET | `/api/restaurants/stats` | User | Total count + borough breakdown | ❌ |
| GET | `/api/restaurants/hygiene-radar` | User | Restaurants from best-scoring cuisines | ❌ |
| GET | `/api/restaurants/random` | User | Random restaurant (`$sample`) | ❌ |
| GET | `/api/restaurants/health` | — | Health check | ❌ |
| GET | `/api/restaurants/{id}` | User | Full restaurant detail + computed badge | ❌ |
| GET | `/api/restaurants/recent-inspections?days=&limit=` | User | Restaurants inspected in last N days | ❌ |
| GET | `/api/restaurants/nearby?lat=&lng=&radius=&limit=` | User | Geospatial search (`$geoNear`, 2dsphere) | ❌ |
| GET | `/api/restaurants/heatmap?borough=&limit=` | **Admin** | Lat/lng/weight points for heatmap | ❌ |
| POST | `/api/restaurants/refresh` | **Admin** | Trigger manual sync (invalidates cache) | — |
| GET | `/api/restaurants/sync-status` | **Admin** | Last sync result + running state | — |
| POST | `/api/restaurants/rebuild-cache?limit=` | **Admin** | Repopulate Redis leaderboard from MongoDB | — |

### Inspection (admin only)

| Method | Path | Description |
|---|---|---|
| GET | `/api/inspection/at-risk?borough=&limit=` | At-risk restaurants (last grade C or Z) |
| GET | `/api/inspection/at-risk/export.csv` | CSV export of at-risk restaurants |

### Users

| Method | Path | Description |
|---|---|---|
| GET | `/api/users/me` | Current user profile |
| GET | `/api/users/me/bookmarks` | Bookmarked restaurants (enriched from MongoDB) |
| POST | `/api/users/me/bookmarks/{restaurantId}` | Add bookmark (idempotent) |
| DELETE | `/api/users/me/bookmarks/{restaurantId}` | Remove bookmark |

### Auth

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Create account, returns JWT pair |
| POST | `/api/auth/login` | Authenticate, returns JWT pair |
| POST | `/api/auth/refresh` | Exchange refresh token for new access token |

Full interactive docs: `http://localhost:8080/swagger-ui/index.html`

---

## Configuration

All values can be overridden via environment variable (key = `PROPERTY_KEY` in uppercase with dots replaced by underscores).

| Property | Env var | Default | Description |
|---|---|---|---|
| `mongodb.uri` | `MONGODB_URI` | `mongodb://localhost:27017` | MongoDB connection string |
| `mongodb.database` | `MONGODB_DATABASE` | `newyork` | Database name |
| `mongodb.collection` | `MONGODB_COLLECTION` | `restaurants` | Collection name |
| `redis.host` | `REDIS_HOST` | `localhost` | Redis hostname |
| `redis.port` | `REDIS_PORT` | `6379` | Redis port |
| `redis.cache.ttl-seconds` | `REDIS_CACHE_TTL_SECONDS` | `3600` | Cache TTL (seconds) |
| `redis.top.limit` | `REDIS_TOP_LIMIT` | `10` | Default `/top` result size |
| `nyc.api.url` | `NYC_API_URL` | NYC Open Data endpoint | API base URL |
| `nyc.api.app_token` | `NYC_API_APP_TOKEN` | *(empty)* | Socrata app token |
| `nyc.api.page-size` | `NYC_API_PAGE_SIZE` | `1000` | Records per API page |
| `nyc.api.max_records` | `NYC_API_MAX_RECORDS` | `0` (unlimited) | Cap on total records |
| `jwt.secret` | `JWT_SECRET` | *(changeit)* | HMAC-SHA256 signing key |
| `jwt.access.expiration.ms` | `JWT_ACCESS_EXPIRATION_MS` | `900000` (15min) | Access token TTL |
| `jwt.refresh.expiration.ms` | `JWT_REFRESH_EXPIRATION_MS` | `604800000` (7d) | Refresh token TTL |

---

## Docker Services

```
┌──────────────────────────────────────────────────────┐
│  Docker Compose                                       │
│                                                       │
│  ┌─────────────┐   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │  restaurant │   │   mongodb   │  │    redis    │  │  postgres   │ │
│  │     -app    │──►│  port 27017 │  │  port 6379  │  │  port 5432  │ │
│  │  port 8080  │──►│             │  │  7-alpine   │  │  pg 15      │ │
│  └─────────────┘   └─────────────┘  └─────────────┘  └─────────────┘ │
│                                                                        │
│  Network: restaurant-network (bridge)                                  │
│  Volumes: mongodb_data, postgres_data                                  │
└────────────────────────────────────────────────────────────────────────┘
```

Startup order: `mongodb` (healthy) → `redis` (healthy) → `postgres` (healthy) → `app`

---

## Frontend Pages

| Route | Template | Auth | Description |
|---|---|---|---|
| `/login` | `login.html` | — | Login / register form |
| `/` | `index.html` | User | Main dashboard |
| `/restaurant/{id}` | `restaurant.html` | User | Restaurant detail + badge + score chart |
| `/hygiene-radar` | `hygiene-radar.html` | User | Healthiest restaurants search |
| `/inspection-map` | `inspection-map.html` | Admin | Leaflet + Leaflet.heat violation heatmap |
| `/inspection` | `inspection.html` | Admin | Agent dashboard: at-risk table, worst cuisines, CSV export |

---

## Roadmap Status

| Phase | Description | Status |
|---|---|---|
| 1 | NYC Open Data API sync (paginated, nightly scheduled) | Done |
| 2 | Redis cache layer + top-restaurants sorted set | Done |
| 3 | User management (JWT auth, PostgreSQL, BCrypt, refresh tokens, bookmarks) | Done |
| 4 | Citizen & inspection agent features (heatmap, at-risk, detail page, geospatial) | Done |

---

## Phase 1 Additions — Role Infrastructure

### UserEntity changes
- Added `role` field (String, stored as `ROLE_CUSTOMER` or `ROLE_CONTROLLER`)
- `hasRole()` used consistently via Spring Security; ROLE_ prefix always stored

### Security
- `SecurityConfig`: antMatchers lock `/api/reports/**` to `ROLE_CONTROLLER`
- `JwtAuthenticationFilter`: extracts role from JWT and populates `SecurityContext`
- `Bucket4j` rate limiter on `/api/auth/**` (configurable threshold)
- Controller signup gated by `CONTROLLER_SIGNUP_CODE` env var

### Seeded accounts
- `DataSeeder` (ApplicationRunner): seeds `customer_test` and `controller_test` on startup
  using idempotent findByUsername().isPresent() guard

---

## Phase 2 Additions — Controller Reports

### New JPA entities (PostgreSQL)
- `InspectionReportEntity`: id (Long), restaurantId (String, references MongoDB by camis),
  grade (Grade enum), status (Status enum), violationCodes (String), notes (String),
  photoPath (String), createdAt (Date), updatedAt (Date), user (ManyToOne → UserEntity)
- `Grade` enum (com.aflokkat.entity): A, B, C, F
- `Status` enum (com.aflokkat.entity): OPEN, IN_PROGRESS, RESOLVED

### New repository
- `ReportRepository` (Spring JPA): findByUserId(Long), findByUserIdAndStatus(Long, Status)

### New controller
- `ReportController`: POST /api/reports, GET /api/reports, PATCH /api/reports/{id},
  POST /api/reports/{id}/photo, GET /api/reports/{id}/photo
- Ownership check: PATCH and GET photo verify entity.getUser().getId() == caller.getId()
- Photo upload writes to `AppConfig.getUploadsDir()/{reportId}/{timestamp}_{originalFilename}`

### AppConfig.getUploadsDir()
- Priority: APP_UPLOADS_DIR env var → .env file → `app.uploads.dir` properties field
- Default in Docker: `/app/uploads` (bound to `uploads_data` named volume)

### Docker volume
- `uploads_data:/app/uploads` in docker-compose.yml — persists photos across restarts

---

## Phase 3 Additions — Customer Discovery

### New DAO methods (RestaurantDAO / RestaurantDAOImpl)
- `searchByNameOrAddress(String q, int limit)`: case-insensitive $regex on name and
  address.street, returns List<Restaurant>
- `findMapPoints()`: returns List<Document> with projection
  {restaurantId, name, lat, lng, grade} for all restaurants with coordinates

### New REST endpoints (RestaurantController)
- GET /api/restaurants/search?q=&limit= — delegates to searchByNameOrAddress
- GET /api/restaurants/map-points — delegates to findMapPoints

### New page routes (ViewController)
- GET /my-bookmarks → my-bookmarks.html

### New templates
- `my-bookmarks.html`: standalone client-side page, fetch-only (no Thymeleaf th: attributes)
- `restaurant.html`: grade badge, cleanliness score, inspection history timeline,
  bookmark toggle (auth required on toggle only)
- `inspection-map.html`: Leaflet + markerCluster, grade-colored markers, ~27K points

### Bookmark endpoints (UserController)
- GET /api/users/bookmarks, POST /api/users/bookmarks, DELETE /api/users/bookmarks/{id}
