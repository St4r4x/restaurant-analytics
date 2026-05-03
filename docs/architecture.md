# Architecture

## Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 25 |
| Framework | Spring Boot | 4.0.5 |
| Primary DB | MongoDB (raw driver, no Spring Data) | 7.x |
| Relational DB | PostgreSQL + Spring Data JPA | 15 |
| Cache / Leaderboard | Redis (Lettuce) | 7-alpine |
| Security | Spring Security + JWT | — |
| API docs | Springdoc OpenAPI | — |
| Build | Maven | 3.x |
| Testing | JUnit 5 + Mockito | — |
| Deployment | Docker / Docker Compose | — |

---

## Package Structure

```
com.st4r4x/
├── Application.java                  # @SpringBootApplication, @EnableScheduling
│
├── config/
│   ├── AppConfig.java                # 4-tier property resolver: JVM → env → .env → application.properties
│   ├── MongoClientFactory.java       # MongoDB singleton client
│   ├── RedisConfig.java              # Lettuce connection factory + Jackson serializer
│   ├── SecurityConfig.java           # Spring Security filter chain, role matchers
│   ├── MethodSecurityConfig.java     # @EnableMethodSecurity
│   └── OpenApiConfig.java            # Springdoc metadata
│
├── controller/
│   ├── RestaurantController.java     # /api/restaurants/*
│   ├── AnalyticsController.java      # /api/analytics/*
│   ├── InspectionController.java     # /api/inspection/*
│   ├── ReportController.java         # /api/reports/* (CONTROLLER role)
│   ├── UserController.java           # /api/users/*
│   ├── AuthController.java           # /api/auth/*
│   └── ViewController.java           # Thymeleaf page routes
│
├── service/
│   ├── RestaurantService.java        # Business logic + static view helpers (toView, getLatestGrade…)
│   └── AuthService.java              # Register / login / refresh logic
│
├── security/
│   ├── JwtUtil.java                  # Token generation, validation, claims extraction
│   └── JwtAuthenticationFilter.java  # OncePerRequestFilter — populates SecurityContext
│
├── cache/
│   └── RestaurantCacheService.java   # Redis cache-aside (TTL keys) + sorted set leaderboard
│
├── dao/
│   ├── RestaurantDAO.java            # Read interface — queries, lookups, aggregations
│   ├── RestaurantDAOImpl.java        # MongoDB impl (implements RestaurantDAO + RestaurantWriteDAO)
│   ├── RestaurantWriteDAO.java       # Write interface — upsertRestaurants only
│   ├── AnalyticsDAO.java             # Analytics interface — KPI aggregations, cuisine rankings, at-risk
│   └── AnalyticsDAOImpl.java         # MongoDB impl
│
├── repository/
│   ├── UserRepository.java           # Spring Data JPA
│   ├── BookmarkRepository.java       # Spring Data JPA
│   └── ReportRepository.java         # Spring Data JPA
│
├── entity/
│   ├── UserEntity.java               # PostgreSQL users table
│   ├── BookmarkEntity.java           # PostgreSQL bookmarks table
│   ├── InspectionReportEntity.java   # PostgreSQL inspection_reports table
│   ├── LetterGrade.java              # Enum: A, B, C, F
│   └── Status.java                   # Enum: OPEN, IN_PROGRESS, RESOLVED
│
├── domain/
│   ├── Restaurant.java               # MongoDB POJO (restaurant_id, name, cuisine, borough, address, grades)
│   ├── Address.java                  # Embedded address + GeoJSON coords [lng, lat]
│   └── InspectionRecord.java         # Embedded grade per inspection date
│
├── aggregation/
│   ├── AggregationCount.java         # { id, count } — $group by field result
│   ├── BoroughCuisineScore.java      # { borough, avgScore }
│   └── CuisineScore.java             # { cuisine, avgScore, count }
│
├── dto/
│   ├── AuthRequest.java              # { username, password }
│   ├── RegisterRequest.java          # { username, email, password, signupCode? }
│   ├── RefreshRequest.java           # { refreshToken }
│   ├── JwtResponse.java              # { accessToken, refreshToken }
│   ├── ReportRequest.java            # { restaurantId, grade, status, violationCodes, notes }
│   ├── HeatmapPoint.java             # { lat, lng, weight }
│   ├── AtRiskEntry.java              # { restaurantId, name, borough, cuisine, lastGrade, lastScore, consecutiveBadGrades }
│   ├── UncontrolledEntry.java        # { restaurantId, name, borough, cuisine, lastGrade, lastScore, daysSinceInspection }
│   └── TopRestaurantEntry.java       # Redis sorted-set snapshot
│
├── sync/
│   ├── NycOpenDataClient.java        # Paginated HTTP client (exponential backoff)
│   ├── NycApiRestaurantDto.java      # Jackson DTO — one row per inspection record from the API
│   ├── SyncService.java              # Orchestrates fetch → group by camis → upsert → cache update
│   └── SyncResult.java               # Immutable result (builder pattern)
│
└── util/
    ├── ValidationUtil.java           # requirePositive, requireNonEmpty, validateFieldName
    └── ResponseUtil.java             # errorResponse() — 400 for IllegalArgument, 500 otherwise
```

---

## DAO Layer Design

The DAO layer is split into three focused interfaces:

| Interface | Responsibility | Impl |
|---|---|---|
| `RestaurantDAO` | Read queries: find, count, filter, geo, heatmap | `RestaurantDAOImpl` |
| `RestaurantWriteDAO` | Write: `upsertRestaurants` (sync only) | `RestaurantDAOImpl` |
| `AnalyticsDAO` | Aggregations: cuisine rankings, at-risk, uncontrolled, KPIs, search | `AnalyticsDAOImpl` |

`RestaurantDAOImpl` implements both `RestaurantDAO` and `RestaurantWriteDAO`. Spring injects
the correct interface at each injection point — `SyncService` receives `RestaurantWriteDAO`,
all read callers receive `RestaurantDAO`.

---

## Data Flow

### Nightly sync

```
Cron 02:00  OR  POST /api/restaurants/refresh
        │
  SyncService.runSync()
        │
  NycOpenDataClient.streamPages()   ← paginated, exponential backoff
        │
  NYC Open Data API (43nn-pn8j.json)
        │
  SyncService.mapToRestaurants()
  group by camis → deduplicate grades by date
        │
        ├── RestaurantWriteDAO.upsertRestaurants()  →  MongoDB bulkWrite
        └── RestaurantCacheService.updateTopRestaurants()  →  Redis ZADD
```

### HTTP read path

```
HTTP request
     │
Controller
     │
     ├── RestaurantCacheService.getOrLoad(key, loader)
     │       │
     │   HIT → return JSON from Redis (TTL 1h)
     │       │
     │   MISS → RestaurantService → RestaurantDAO → MongoDB
     │               store result in Redis
     │
     └── GET /api/restaurants/top
             └── RestaurantCacheService.getTopRestaurants(limit)
                     └── Redis ZRANGE restaurants:top 0 N-1
```

### Authentication flow

```
POST /api/auth/register  or  /api/auth/login
          │
    AuthController → AuthService
          ├── ValidationUtil checks
          ├── BCrypt hash / verify
          ├── UserRepository (PostgreSQL)
          └── JwtUtil.generateAccessToken() + generateRefreshToken()
                    │
              JwtResponse { accessToken (15 min), refreshToken (7 d) }

Every subsequent request:
  Authorization: Bearer <accessToken>
          │
  JwtAuthenticationFilter (OncePerRequestFilter)
          │
  JwtUtil.getClaimsIfValid(token)
          ├── valid   → set SecurityContext (username + role)
          └── invalid → continue unauthenticated (public endpoints pass through)
```

---

## Data Models

### MongoDB — `restaurants` collection

```json
{
  "_id":           "ObjectId",
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
      "violation_description": "Non-food contact surface improperly constructed.",
      "critical_flag":         "Not Critical"
    }
  ]
}
```

Key decisions:
- One document per restaurant (`restaurant_id` = camis)
- Grades embedded; multiple violation rows from the API are deduplicated by inspection date
- Coordinates stored as GeoJSON `[longitude, latitude]` for `$geoNear` compatibility

Recommended indexes:
```js
db.restaurants.createIndex({ restaurant_id: 1 }, { unique: true })
db.restaurants.createIndex({ borough: 1 })
db.restaurants.createIndex({ cuisine: 1 })
db.restaurants.createIndex({ "address.coord": "2dsphere" })
```

### PostgreSQL

**`users`**

| Column | Type | Constraints |
|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY |
| `username` | VARCHAR | UNIQUE NOT NULL |
| `email` | VARCHAR | UNIQUE NOT NULL |
| `password_hash` | VARCHAR | NOT NULL (BCrypt) |
| `role` | VARCHAR | NOT NULL (e.g. `ROLE_CUSTOMER`) |
| `created_at` | TIMESTAMP | NOT NULL |

**`bookmarks`**

| Column | Type | Constraints |
|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY |
| `user_id` | BIGINT | FK → `users.id` NOT NULL |
| `restaurant_id` | VARCHAR | NOT NULL (MongoDB camis) |
| `created_at` | TIMESTAMP | NOT NULL |
| — | UNIQUE | (`user_id`, `restaurant_id`) |

**`inspection_reports`**

| Column | Type | Constraints |
|---|---|---|
| `id` | BIGSERIAL | PRIMARY KEY |
| `user_id` | BIGINT | FK → `users.id` NOT NULL |
| `restaurant_id` | VARCHAR | NOT NULL (MongoDB camis) |
| `grade` | VARCHAR | NOT NULL (`LetterGrade` enum) |
| `status` | VARCHAR | NOT NULL (`Status` enum) |
| `violation_codes` | VARCHAR | nullable |
| `notes` | TEXT | nullable |
| `photo_path` | VARCHAR | nullable |
| `created_at` | TIMESTAMP | NOT NULL |
| `updated_at` | TIMESTAMP | NOT NULL |

Managed by Spring Data JPA / Hibernate auto-DDL.

### Redis

**Cache keys** (String, JSON, TTL = 1h by default)

| Key | Value type |
|---|---|
| `restaurants:by_borough` | `List<AggregationCount>` JSON |
| `restaurants:cuisine_scores:{cuisine}` | `List<BoroughCuisineScore>` JSON |
| `restaurants:worst_cuisines:{borough}:{limit}` | `List<CuisineScore>` JSON |

**Sorted set**

| Key | Member | Score |
|---|---|---|
| `restaurants:top` | `TopRestaurantEntry` JSON | Latest inspection score (lower = healthier) |

All `restaurants:*` keys are invalidated on every successful sync.
