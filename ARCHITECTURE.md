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
| Cache / Leaderboard | Redis (Lettuce) | 7-alpine |
| API documentation | Springdoc OpenAPI | 1.8.0 |
| Build | Maven | 3.x |
| Config | dotenv-java | 3.0.0 |
| Testing | JUnit 5 + Mockito | 4.x (BOM) |
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
│   └── ViewController.java       # Thymeleaf page routes
│
├── service/
│   └── RestaurantService.java    # Business logic, delegates to DAO
│
├── cache/
│   └── RestaurantCacheService.java  # Redis cache-aside + sorted set
│
├── dao/
│   ├── RestaurantDAO.java        # Interface
│   └── RestaurantDAOImpl.java    # MongoDB impl (raw driver, aggregation pipelines)
│
├── domain/
│   ├── Restaurant.java           # Main POJO (BSON-mapped)
│   ├── Address.java              # Embedded address + GeoJSON coords
│   └── Grade.java                # Inspection record (date, score, grade letter, violation)
│
├── aggregation/
│   ├── AggregationCount.java     # { id, count } — result of $group by field
│   ├── BoroughCuisineScore.java  # { borough, avgScore }
│   └── CuisineScore.java         # { cuisine, avgScore, count }
│
├── dto/
│   └── TopRestaurantEntry.java   # Lightweight Redis sorted-set snapshot
│
├── sync/
│   ├── NycOpenDataClient.java    # Paginated HTTP client (RestTemplate, exp. backoff)
│   ├── NycApiRestaurantDto.java  # Jackson DTO — one row per inspection record
│   ├── SyncService.java          # Orchestrates fetch → map → upsert → cache
│   └── SyncResult.java           # Immutable sync result (builder pattern)
│
└── util/
    └── ValidationUtil.java       # requirePositive, requireNonEmpty, validateFieldName
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

| Method | Path | Description | Cached |
|---|---|---|---|
| GET | `/api/restaurants/by-borough` | Restaurant count per borough | ✅ Redis |
| GET | `/api/restaurants/cuisine-scores?cuisine=` | Avg inspection score by borough for a cuisine | ✅ Redis |
| GET | `/api/restaurants/worst-cuisines?borough=&limit=` | Worst cuisines by avg score in a borough | ✅ Redis |
| GET | `/api/restaurants/popular-cuisines?minCount=` | Cuisines with ≥ N restaurants | ❌ |
| GET | `/api/restaurants/top?limit=` | Healthiest restaurants (Redis sorted set) | ✅ Redis |
| GET | `/api/restaurants/by-cuisine?limit=` | Top N cuisines by count | ❌ |
| GET | `/api/restaurants/cuisines` | All distinct cuisine types | ❌ |
| GET | `/api/restaurants/stats` | Total count + borough breakdown | ❌ |
| GET | `/api/restaurants/trash-advisor` | Restaurants from worst-scoring cuisines | ❌ |
| GET | `/api/restaurants/health` | Health check | ❌ |
| POST | `/api/restaurants/refresh` | Trigger manual sync (invalidates cache) | — |
| GET | `/api/restaurants/sync-status` | Last sync result | — |

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

---

## Docker Services

```
┌──────────────────────────────────────────────────────┐
│  Docker Compose                                       │
│                                                       │
│  ┌─────────────┐   ┌─────────────┐  ┌─────────────┐ │
│  │  restaurant │   │   mongodb   │  │    redis    │ │
│  │     -app    │──►│  port 27017 │  │  port 6379  │ │
│  │  port 8080  │──►│             │  │  7-alpine   │ │
│  └─────────────┘   └─────────────┘  └─────────────┘ │
│                                                       │
│  Network: restaurant-network (bridge)                 │
│  Volumes: mongodb_data                                │
└──────────────────────────────────────────────────────┘
```

Startup order: `mongodb` (healthy) → `redis` (healthy) → `app`

---

## Roadmap Status

| Phase | Description | Status |
|---|---|---|
| 1 | NYC Open Data API sync (paginated, nightly scheduled) | ✅ Done |
| 2 | Redis cache layer + top-restaurants sorted set | ✅ Done |
| 3 | User management (JWT auth, roles, bookmarks) | 🔄 In progress (colleague branch) |
| 4 | Stretch: GraphQL, geospatial, Kafka, Prometheus, CI/CD | ⏳ Planned |
