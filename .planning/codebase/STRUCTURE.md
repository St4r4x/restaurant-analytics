# Codebase Structure

**Analysis Date:** 2026-03-27

## Directory Layout

```
quickstart-app/
├── src/
│   ├── main/
│   │   ├── java/com/aflokkat/
│   │   │   ├── Application.java              # Spring Boot entry point
│   │   │   ├── aggregation/                  # MongoDB aggregation result POJOs
│   │   │   │   ├── AggregationCount.java
│   │   │   │   ├── BoroughCuisineScore.java
│   │   │   │   └── CuisineScore.java
│   │   │   ├── cache/                        # Redis cache-aside layer
│   │   │   │   └── RestaurantCacheService.java
│   │   │   ├── config/                       # Spring config, singletons
│   │   │   │   ├── AppConfig.java            # property loader (env → .env → props)
│   │   │   │   ├── MongoClientFactory.java   # MongoDB singleton
│   │   │   │   ├── RedisConfig.java          # StringRedisTemplate bean
│   │   │   │   ├── SecurityConfig.java       # JWT filter chain
│   │   │   │   └── OpenApiConfig.java        # Swagger/OpenAPI
│   │   │   ├── controller/                   # REST API endpoints (18+ endpoints)
│   │   │   │   ├── RestaurantController.java
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── InspectionController.java
│   │   │   │   ├── UserController.java
│   │   │   │   └── ViewController.java
│   │   │   ├── dao/                          # MongoDB data access
│   │   │   │   ├── RestaurantDAO.java        # interface (29 methods)
│   │   │   │   └── RestaurantDAOImpl.java     # aggregation pipelines
│   │   │   ├── domain/                       # MongoDB POJO models
│   │   │   │   ├── Restaurant.java           # aggregate root
│   │   │   │   ├── Address.java              # nested document
│   │   │   │   └── Grade.java                # inspection record
│   │   │   ├── dto/                          # Request/response DTOs
│   │   │   │   ├── AuthRequest.java
│   │   │   │   ├── RegisterRequest.java
│   │   │   │   ├── JwtResponse.java
│   │   │   │   ├── RefreshRequest.java
│   │   │   │   ├── HeatmapPoint.java
│   │   │   │   ├── TopRestaurantEntry.java
│   │   │   │   └── AtRiskEntry.java
│   │   │   ├── entity/                       # JPA entities (PostgreSQL)
│   │   │   │   ├── UserEntity.java
│   │   │   │   └── BookmarkEntity.java
│   │   │   ├── repository/                   # Spring JPA repositories
│   │   │   │   ├── UserRepository.java
│   │   │   │   └── BookmarkRepository.java
│   │   │   ├── security/                     # JWT authentication
│   │   │   │   ├── JwtUtil.java              # token generation/validation
│   │   │   │   └── JwtAuthenticationFilter.java
│   │   │   ├── service/                      # Business logic layer
│   │   │   │   ├── RestaurantService.java    # analytics, computed fields
│   │   │   │   └── AuthService.java          # user auth
│   │   │   ├── sync/                         # NYC API sync orchestration
│   │   │   │   ├── SyncService.java          # scheduler, nightly 02:00
│   │   │   │   ├── NycOpenDataClient.java    # HTTP REST client
│   │   │   │   ├── NycApiRestaurantDto.java  # API response DTO
│   │   │   │   └── SyncResult.java           # sync metadata
│   │   │   └── util/                         # Shared utilities
│   │   │       ├── ValidationUtil.java
│   │   │       └── ResponseUtil.java
│   │   └── resources/
│   │       ├── application.properties        # Spring config (main)
│   │       ├── simplelogger.properties       # SLF4J logging config
│   │       └── templates/                    # HTML templates (Thymeleaf)
│   │           ├── index.html
│   │           ├── login.html
│   │           ├── restaurant.html
│   │           ├── inspection.html
│   │           ├── inspection-map.html
│   │           └── hygiene-radar.html
│   ├── test/
│   │   └── java/com/aflokkat/
│   │       ├── dao/                          # DAO integration tests (64 tests)
│   │       ├── domain/
│   │       ├── sync/
│   │       ├── security/
│   │       ├── util/
│   │       ├── cache/
│   │       ├── config/
│   │       ├── service/
│   │       └── aggregation/
├── pom.xml                                   # Maven build config
├── docker-compose.yml                        # 4 containers: app, MongoDB, Redis, PostgreSQL
├── Dockerfile                                # Spring Boot app image
├── application.properties                    # (in src/main/resources)
├── CLAUDE.md                                 # Project context document
└── README.md                                 # Project documentation
```

## Directory Purposes

**`src/main/java/com/aflokkat/`:**
- Purpose: Main Java application code
- Contains: Package-organized classes by layer (controller, service, DAO, etc.)

**`aggregation/`:**
- Purpose: Intermediate POJOs for MongoDB aggregation pipeline results
- Contains: `AggregationCount`, `BoroughCuisineScore`, `CuisineScore`
- When used: Service layer returns these after aggregation queries

**`cache/`:**
- Purpose: Redis cache-aside implementation for expensive queries
- Contains: `RestaurantCacheService` (single bean)
- When used: Controllers call `cacheService.getOrLoad(key, supplier, typeRef)`

**`config/`:**
- Purpose: Application-wide configuration, Spring beans, singletons
- Contains: Property loading, MongoDB/Redis connections, security setup, Swagger
- Key pattern: `AppConfig` centralizes all property resolution (env → .env → application.properties)

**`controller/`:**
- Purpose: HTTP REST API endpoints and response handling
- Contains: 5 controller classes with @RestController and @RequestMapping annotations
- Pattern: controllers are thin — they validate, delegate to service, format response

**`dao/`:**
- Purpose: MongoDB data access layer using raw driver
- Contains: Interface `RestaurantDAO` (29 method signatures) and implementation `RestaurantDAOImpl`
- Key feature: Constructs raw BSON aggregation pipelines (no Spring Data MongoDB)
- POJO codec: uses `PojoCodecProvider` for automatic Restaurant POJO mapping

**`domain/`:**
- Purpose: MongoDB document models (POJOs with BSON annotations)
- Contains: `Restaurant` (main aggregate), `Address` (nested), `Grade` (inspection records)
- Annotations: `@BsonProperty` maps Java fields to MongoDB document fields

**`dto/`:**
- Purpose: Request/response DTOs for controllers and cache serialization
- Contains: Auth DTOs (`AuthRequest`, `JwtResponse`), query result DTOs (`HeatmapPoint`, `TopRestaurantEntry`)

**`entity/`:**
- Purpose: JPA entities for PostgreSQL (users and bookmarks)
- Contains: `UserEntity`, `BookmarkEntity` with @Entity and @Table annotations

**`repository/`:**
- Purpose: Spring Data JPA repositories for PostgreSQL
- Contains: `UserRepository`, `BookmarkRepository`
- Pattern: Spring-generated CRUD + custom queries

**`security/`:**
- Purpose: JWT token generation, validation, and HTTP filter
- Contains: `JwtUtil` (token ops) and `JwtAuthenticationFilter` (request interceptor)
- Token handling: HMAC-SHA256, access (15min) and refresh (7 days) tokens

**`service/`:**
- Purpose: Business logic and orchestration
- Contains: `RestaurantService` (analytics use-cases, computed fields), `AuthService` (user auth)
- Pattern: services validate input via `ValidationUtil`, delegate to DAO/repositories, compute output

**`sync/`:**
- Purpose: Nightly data sync from NYC Open Data API
- Contains: `SyncService` (scheduler + runner), `NycOpenDataClient` (HTTP), DTOs and results
- Scheduler: cron 0 0 2 * * * (daily 02:00 local time)
- Flow: fetch → group by camis → map to Restaurants → upsert → invalidate cache

**`util/`:**
- Purpose: Shared utility functions
- Contains: `ValidationUtil` (input validation), `ResponseUtil` (error formatting)

**`src/main/resources/templates/`:**
- Purpose: Thymeleaf HTML templates for web views
- Contains: Dashboard pages (index, restaurant detail, inspection map, hygiene radar, login)
- Served by: `ViewController` endpoints

## Key File Locations

**Entry Points:**
- `src/main/java/com/aflokkat/Application.java` — Spring Boot application entry point with `@SpringBootApplication` and `@EnableScheduling`
- `src/main/resources/application.properties` — Spring Boot configuration (server port, DB URIs, API settings, JWT)

**Configuration:**
- `src/main/java/com/aflokkat/config/AppConfig.java` — centralized property resolution
- `src/main/java/com/aflokkat/config/MongoClientFactory.java` — MongoDB singleton client
- `src/main/java/com/aflokkat/config/SecurityConfig.java` — Spring Security + JWT filter chain
- `docker-compose.yml` — 4-container setup (app, MongoDB, Redis, PostgreSQL)

**Core Logic:**
- `src/main/java/com/aflokkat/service/RestaurantService.java` — analytics methods, computed fields (latest grade, trend, badge color, coordinates)
- `src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java` — MongoDB aggregation pipelines
- `src/main/java/com/aflokkat/sync/SyncService.java` — nightly data sync orchestration

**Testing:**
- `src/test/java/com/aflokkat/` — 64+ JUnit 4 tests with Mockito
- Test categories: DAO integration tests, service unit tests, security tests, cache tests, config tests

**APIs:**
- REST endpoints: see `src/main/java/com/aflokkat/controller/RestaurantController.java` (18 endpoints)
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Base path: `/api/` (restaurants, auth, users, inspections)

## Naming Conventions

**Files:**
- Class files: PascalCase (e.g., `RestaurantService.java`, `AuthController.java`)
- Interface files: PascalCase without "Impl" suffix (e.g., `RestaurantDAO.java`)
- Implementation files: PascalCase with "Impl" suffix (e.g., `RestaurantDAOImpl.java`)
- Test files: PascalCase + "Test" or "IntegrationTest" suffix (e.g., `RestaurantDAOIntegrationTest.java`)

**Directories:**
- Package directories: lowercase, plural when appropriate (e.g., `controller/`, `service/`, `dao/`, `entity/`)
- Functional grouping by layer

**Java Classes:**
- Class names: PascalCase (e.g., `Restaurant`, `RestaurantService`)
- Field names: camelCase (e.g., `restaurantId`, `bouroughCount`)
- Method names: camelCase, verbs preferred (e.g., `findCountByBorough()`, `getLatestGrade()`)
- Constants: UPPERCASE_SNAKE_CASE (e.g., `KEY_TOP`, `TYPE_AGG_COUNT`)

**MongoDB Document Fields:**
- Snake_case in POJO `@BsonProperty` annotations (e.g., `restaurant_id`, `cuisine_description`)
- Java field names remain camelCase (mapped via annotations)

## Where to Add New Code

**New Analytics Endpoint:**
1. **DAO layer:** Add method to `RestaurantDAO` interface and implement in `RestaurantDAOImpl.java` with aggregation pipeline
2. **Service layer:** Add public method in `RestaurantService.java` that calls DAO and applies business logic
3. **Controller layer:** Add `@GetMapping` or `@PostMapping` in `RestaurantController.java` that calls service
4. **Optional caching:** Wrap service call in `RestaurantCacheService.getOrLoad()` for expensive queries
5. **Test:** Add unit test to `src/test/java/com/aflokkat/service/RestaurantServiceTest.java`

**New Authentication Feature:**
1. **Entity layer:** Add/modify fields in `UserEntity.java` if needed
2. **Repository layer:** Add query method to `UserRepository.java` if needed
3. **Service layer:** Add logic to `AuthService.java`
4. **Controller layer:** Add endpoint to `AuthController.java`
5. **Security layer:** Modify `SecurityConfig.java` or `JwtUtil.java` if token structure changes
6. **Test:** Add test to `src/test/java/com/aflokkat/service/AuthServiceTest.java`

**New User Feature (Bookmarks, Profile):**
1. **Entity layer:** Add/modify `UserEntity.java` or `BookmarkEntity.java` in `src/main/java/com/aflokkat/entity/`
2. **Repository layer:** Add methods to `UserRepository.java` or `BookmarkRepository.java`
3. **Service layer:** Create or modify service in `src/main/java/com/aflokkat/service/`
4. **Controller layer:** Add endpoints to `UserController.java`
5. **Test:** Add integration tests in `src/test/java/com/aflokkat/repository/`

**New Data Sync Source:**
1. **DTO layer:** Create DTO for API response (e.g., `NewApiRestaurantDto.java`)
2. **Sync layer:** Create new client (e.g., `NewApiClient.java`) or extend `NycOpenDataClient.java`
3. **Service layer:** Add mapper in `SyncService.java` or new service to convert API DTO to domain `Restaurant`
4. **DAO layer:** Ensure `upsertRestaurants()` can handle the new data
5. **Test:** Add tests to `src/test/java/com/aflokkat/sync/`

**New Utility Function:**
- Location: `src/main/java/com/aflokkat/util/`
- Pattern: Static methods in utility class (e.g., `ValidationUtil`, `ResponseUtil`)
- Scope: Shared across multiple layers, no business logic

**New Web Template:**
- Location: `src/main/resources/templates/`
- Format: Thymeleaf HTML
- Served by: `ViewController` endpoints (add method + `@GetMapping` in `ViewController.java`)

## Special Directories

**`target/`:**
- Purpose: Maven build output
- Generated: Yes (mvn clean package)
- Committed: No (.gitignore)
- Contains: Compiled classes, JAR, dependencies

**`src/test/`:**
- Purpose: Unit and integration tests
- Generated: No (manually written)
- Committed: Yes
- Pattern: Test classes mirror src/main structure; use JUnit 4 + Mockito

**`docker-compose.yml`:**
- Purpose: Development/deployment environment specification
- Services: 4 containers (app, MongoDB, Redis, PostgreSQL)
- Environment: Database credentials, API configs passed as env vars to app container
- Volumes: MongoDB data persistence, PostgreSQL data persistence

**`.planning/`:**
- Purpose: GSD (Goal, Strategy, Development) planning documents
- Generated: Yes (populated by orchestrator)
- Committed: Yes
- Contains: ARCHITECTURE.md, STRUCTURE.md, CONVENTIONS.md, TESTING.md, CONCERNS.md, STACK.md, INTEGRATIONS.md

**`docs/` (if present):**
- For project documentation, API specs, deployment guides

## Adding New Dependencies

**Maven (pom.xml):**
- Add `<dependency>` block to appropriate section (Spring Boot, testing, DB drivers, etc.)
- Run: `mvn clean install`
- Restart application

**Spring Boot Auto-Configuration:**
- Drop JAR into classpath → Spring Boot auto-configures beans
- Example: `spring-boot-starter-redis` → `StringRedisTemplate` bean auto-created

---

*Structure analysis: 2026-03-27*
```

Now I'll provide both complete documents as requested:

---

# ARCHITECTURE.md (Complete Content)

# Architecture

**Analysis Date:** 2026-03-27

## Pattern Overview

**Overall:** Layered MVC with Domain-Driven Design and Cache-Aside pattern

**Key Characteristics:**
- Spring Boot 2.6.15 REST API following controller-service-DAO layers
- Raw MongoDB driver (mongodb-driver-sync) for aggregation pipelines, no Spring Data MongoDB
- PostgreSQL for user/bookmark persistence (Spring Data JPA)
- Redis cache-aside pattern for expensive MongoDB aggregations (3600s TTL)
- Scheduled data sync from NYC Open Data API (nightly at 02:00) with manual refresh endpoint
- JWT-based security with access (15min) and refresh (7 days) tokens
- POJO-based domain models with computed fields extracted to service layer

## Layers

**Controller Layer:**
- Purpose: REST API endpoints, request routing, response formatting
- Location: `src/main/java/com/aflokkat/controller/`
- Contains: 
  - `RestaurantController.java` — analytics endpoints (18 endpoints total)
  - `AuthController.java` — JWT auth (register, login, refresh)
  - `InspectionController.java` — inspection-specific queries
  - `UserController.java` — user profile/bookmarks
  - `ViewController.java` — HTML template serving
- Depends on: Service layer, DTO layer, Cache service
- Used by: HTTP clients (browsers, API consumers)

**Service Layer:**
- Purpose: Business logic, input validation, computed field calculations
- Location: `src/main/java/com/aflokkat/service/`
- Contains:
  - `RestaurantService.java` — restaurant queries, computed fields (latest grade, trend, badge color, coordinates), use-case implementations
  - `AuthService.java` — user registration, login, token generation
- Depends on: DAO layer, ValidationUtil, domain models
- Used by: Controllers, SyncService, CacheService

**DAO Layer:**
- Purpose: MongoDB data access via raw driver, aggregation pipeline construction
- Location: `src/main/java/com/aflokkat/dao/`
- Contains:
  - `RestaurantDAO.java` — interface (29 methods)
  - `RestaurantDAOImpl.java` — MongoDB aggregation pipelines, POJO codec registry
- Depends on: Domain models, MongoDB driver
- Used by: Service layer, Sync layer
- Note: Uses raw `mongodb-driver-sync` aggregation pipelines, not Spring Data MongoDB

**Repository Layer (Spring JPA):**
- Purpose: PostgreSQL persistence for users and bookmarks
- Location: `src/main/java/com/aflokkat/repository/`
- Contains:
  - `UserRepository.java` — Spring JPA for `UserEntity`
  - `BookmarkRepository.java` — Spring JPA for `BookmarkEntity`
- Depends on: JPA/Hibernate
- Used by: AuthService, UserController

**Sync Layer:**
- Purpose: Orchestrate NYC Open Data API fetch → map → MongoDB upsert
- Location: `src/main/java/com/aflokkat/sync/`
- Contains:
  - `SyncService.java` — nightly scheduler (02:00), manual trigger, result tracking
  - `NycOpenDataClient.java` — HTTP REST calls to NYC Open Data API
  - `NycApiRestaurantDto.java` — DTO for API response mapping
  - `SyncResult.java` — sync metadata (counts, timestamps, error)
- Depends on: RestaurantDAO, RestaurantCacheService, config
- Used by: Application startup, scheduler, POST `/api/restaurants/refresh` endpoint
- Flow: NYC API → fetch all records → group by `camis` → map inspection rows to grades → upsert restaurants → invalidate cache

**Cache Layer:**
- Purpose: Redis cache-aside for expensive MongoDB aggregations; sorted set for top restaurants
- Location: `src/main/java/com/aflokkat/cache/`
- Contains: `RestaurantCacheService.java`
- Depends on: Spring Data Redis, ObjectMapper, configuration
- Used by: Controllers (cache-aside calls), SyncService (invalidation)
- Pattern: `getOrLoad(key, supplier, typeRef)` — returns cached value or calls supplier and stores result
- Sorted set operations: `KEY_TOP` stores restaurants by inspection score (lower = healthier)
- TTL: 3600s (configurable via `redis.cache.ttl-seconds`)

**Security Layer:**
- Purpose: JWT token generation, validation, request filtering
- Location: `src/main/java/com/aflokkat/security/`
- Contains:
  - `JwtUtil.java` — token generation (access + refresh), claims extraction
  - `JwtAuthenticationFilter.java` — request interceptor, token validation
- Depends on: `jjwt` library, AppConfig
- Used by: AuthController, SecurityConfig

**Config Layer:**
- Purpose: Application-wide configuration and dependency injection
- Location: `src/main/java/com/aflokkat/config/`
- Contains:
  - `AppConfig.java` — centralized property loading (env vars → .env → application.properties)
  - `MongoClientFactory.java` — singleton MongoDB client
  - `RedisConfig.java` — StringRedisTemplate bean
  - `SecurityConfig.java` — Spring Security setup, JWT filter chain
  - `OpenApiConfig.java` — Swagger/OpenAPI bean
- Used by: Entire application

**Domain Layer (MongoDB POJOs):**
- Purpose: Data models with BSON codec annotations
- Location: `src/main/java/com/aflokkat/domain/`
- Contains:
  - `Restaurant.java` — main aggregate root (`_id`, `restaurant_id`, `name`, `cuisine`, `borough`, `address`, `phone`, `grades`)
  - `Address.java` — nested document (`building`, `street`, `zipcode`, `coord` [GeoJSON])
  - `Grade.java` — inspection record (nested in `grades[]`; `date`, `grade`, `score`, `inspection_type`, `violation_code`, etc.)
- Used by: DAO, Service, Cache layers

**Entity Layer (PostgreSQL JPA):**
- Purpose: User and bookmark persistence
- Location: `src/main/java/com/aflokkat/entity/`
- Contains:
  - `UserEntity.java` — user credentials, roles
  - `BookmarkEntity.java` — user-restaurant associations
- Used by: Repositories, AuthService

**DTO Layer:**
- Purpose: Request/response DTOs, aggregation result pojos
- Location: `src/main/java/com/aflokkat/dto/`
- Contains:
  - Request/Response: `AuthRequest`, `RegisterRequest`, `RefreshRequest`, `JwtResponse`
  - Query results: `HeatmapPoint`, `TopRestaurantEntry`, `AtRiskEntry`
- Used by: Controllers, Cache layer, Service

**Aggregation POJOs:**
- Purpose: Intermediate results from MongoDB aggregation pipelines
- Location: `src/main/java/com/aflokkat/aggregation/`
- Contains:
  - `AggregationCount.java` — count per field (e.g., `{_id: "Italian", count: 500}`)
  - `BoroughCuisineScore.java` — avg score per borough for a cuisine
  - `CuisineScore.java` — avg score per cuisine

**Utility Layer:**
- Purpose: Shared helper functions
- Location: `src/main/java/com/aflokkat/util/`
- Contains:
  - `ValidationUtil.java` — `requireNonEmpty()`, `requirePositive()`, `validateFieldName()`
  - `ResponseUtil.java` — uniform error response formatting

## Data Flow

**Analytics Query (e.g., GET /api/restaurants/by-borough):**

1. HTTP GET → `RestaurantController`
2. Controller checks Redis cache via `RestaurantCacheService`
3. If cache miss: calls `RestaurantService.getRestaurantCountByBorough()`
4. Service delegates to `RestaurantDAOImpl.findCountByBorough()`
5. DAO builds MongoDB aggregation pipeline (`$group` by borough, `$count`)
6. Results returned as `List<AggregationCount>`
7. Cache stores result (TTL 3600s)
8. Response formatted as JSON and returned

**Data Sync Flow (Scheduled 02:00 daily):**

1. `SyncService.scheduledSync()` triggered by `@Scheduled` cron
2. `SyncService.runSync()` calls `NycOpenDataClient.fetchAll()`
3. Client fetches paginated records from NYC API (1000 per page, respects `max_records` limit)
4. Records grouped by `camis` (restaurant ID) in `mapToRestaurants()`
5. Each group: create `Restaurant` POJO with latest address/phone, aggregated `grades[]` (one per inspection date)
6. `RestaurantDAOImpl.upsertRestaurants()` bulk upsert (replace-on-match by `restaurant_id`)
7. `RestaurantCacheService.invalidateAll()` removes all `restaurants:*` keys
8. `RestaurantCacheService.updateTopRestaurants()` rebuilds sorted set `restaurants:top` with latest scores
9. SyncResult recorded with counts and timestamps

**Authentication Flow:**

1. POST `/api/auth/login` with credentials
2. `AuthController` → `AuthService.login()`
3. AuthService queries `UserRepository` for username
4. Password verified (bcrypt)
5. `JwtUtil.generateAccessToken()` + `generateRefreshToken()`
6. Response contains both tokens + user metadata
7. Subsequent requests include `Authorization: Bearer <access-token>` header
8. `JwtAuthenticationFilter` intercepts, validates token via `JwtUtil.validateToken()`
9. Token valid → request proceeds; invalid/expired → 401 Unauthorized

**State Management:**

- MongoDB: authoritative data store for restaurants (upserted nightly)
- PostgreSQL: user accounts, bookmarks (transactional)
- Redis: computed aggregations (cache-aside, TTL 3600s), top restaurants sorted set (rebuilt on sync)
- In-memory: `SyncService.lastResult` (sync status), JWT secret (`JwtUtil.key`)

## Key Abstractions

**Cache-Aside Pattern:**
- Method: `RestaurantCacheService.getOrLoad(key, supplier, typeRef)`
- On hit: deserialize from Redis, return
- On miss: call supplier (triggers DAO query), serialize to Redis, return
- Failure mode: swallows Redis exceptions, falls through to supplier (graceful degradation)

**Aggregation Pipeline:**
- Location: `RestaurantDAOImpl` methods construct `List<Document>` pipelines
- Example: `findCountByBorough()` → `$match(address.$type: 3)` → `$group(_id: borough, count: $sum: 1)` → `$sort(count: -1)`
- Executed via `restaurantCollection.aggregate(pipeline, resultClass).forEach()`
- Result class: `AggregationCount`, `BoroughCuisineScore`, etc.

**POJO Separation:**
- Restaurant POJO holds only stored fields from MongoDB
- Computed fields (latest grade, trend, badge color, lat/lng) calculated in `RestaurantService.toView()`
- Example: `toView()` builds `Map<String, Object>` with computed fields added
- Benefit: clean separation, computed fields never persisted

**Sync Orchestration:**
- `SyncService` stateful: `running` flag, `lastResult`, `runningStartedAt`
- Result shared via `getLastResult()` for polling sync status
- Thread-safe: uses `volatile` for flags and timestamps

## Entry Points

**HTTP API:**
- Location: `Application.java` → Spring Boot startup
- Listens: `http://localhost:8080`
- Base paths:
  - `/api/restaurants/*` → analytics, hygiene radar, sync
  - `/api/auth/*` → login, register, refresh
  - `/api/users/*` → profile, bookmarks
  - `/api/inspections/*` → inspection-specific queries
  - `/swagger-ui.html` → OpenAPI documentation

**Scheduled Tasks:**
- `SyncService.scheduledSync()` → cron: 0 0 2 * * * (daily 02:00)
- Triggered by `@EnableScheduling` on `Application` class

**Configuration Files:**
- `application.properties` → Spring config (logging, DB, API, JWT)
- `.env` → environment overrides (Docker Compose)
- System environment variables → highest priority

## Error Handling

**Strategy:** Layer-specific catch-and-log with graceful degradation

**Patterns:**

1. **Controller Layer:**
   - Try-catch all endpoints
   - Catch `IllegalArgumentException` → 400 Bad Request
   - Catch `Exception` → 500 Internal Server Error
   - Use `ResponseUtil.errorResponse()` for uniform format

2. **Service Layer:**
   - Validate input via `ValidationUtil` methods (throw `IllegalArgumentException`)
   - Let DAO exceptions propagate (caught by controller)

3. **DAO Layer:**
   - MongoDB failures logged but not caught (bubble to service)
   - Invalid queries return empty lists

4. **Cache Layer:**
   - All Redis operations wrapped in try-catch
   - Failures logged as warnings, supplier called silently (cache-aside fallback)

5. **Sync Layer:**
   - Network failures caught, result marked `success: false` with error message
   - Partial data treated as failure (all-or-nothing)

## Cross-Cutting Concerns

**Logging:**
- Framework: SLF4J + Simple Logger
- Configuration: `src/main/resources/simplelogger.properties`
- Root level: INFO; com.aflokkat: DEBUG
- Usage: SQL calls, sync events, cache operations, API errors

**Validation:**
- Centralized: `ValidationUtil` class
- Methods: `requireNonEmpty()`, `requirePositive()`, `validateFieldName()`
- Failures: throw `IllegalArgumentException` with descriptive messages (French/English mix)
- Used by: Service layer before DAO calls

**Authentication:**
- JWT tokens: HMAC-SHA256 signed
- Access token: 15 min (900,000 ms)
- Refresh token: 7 days (604,800,000 ms)
- Validation: checked on every request by `JwtAuthenticationFilter`
- Roles: extracted from token claim, checked by `@PreAuthorize("hasRole('ADMIN')")`

**CORS:**
- All controllers: `@CrossOrigin(origins = "*", allowedHeaders = "*")`
- Allows frontend on different domain/port

---

*Architecture analysis: 2026-03-27*

---

# STRUCTURE.md (Complete Content)

# Codebase Structure

**Analysis Date:** 2026-03-27

## Directory Layout

```
quickstart-app/
├── src/
│   ├── main/
│   │   ├── java/com/aflokkat/
│   │   │   ├── Application.java              # Spring Boot entry point
│   │   │   ├── aggregation/                  # MongoDB aggregation result POJOs
│   │   │   │   ├── AggregationCount.java
│   │   │   │   ├── BoroughCuisineScore.java
│   │   │   │   └── CuisineScore.java
│   │   │   ├── cache/                        # Redis cache-aside layer
│   │   │   │   └── RestaurantCacheService.java
│   │   │   ├── config/                       # Spring config, singletons
│   │   │   │   ├── AppConfig.java            # property loader (env → .env → props)
│   │   │   │   ├── MongoClientFactory.java   # MongoDB singleton
│   │   │   │   ├── RedisConfig.java          # StringRedisTemplate bean
│   │   │   │   ├── SecurityConfig.java       # JWT filter chain
│   │   │   │   └── OpenApiConfig.java        # Swagger/OpenAPI
│   │   │   ├── controller/                   # REST API endpoints (18+ endpoints)
│   │   │   │   ├── RestaurantController.java
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── InspectionController.java
│   │   │   │   ├── UserController.java
│   │   │   │   └── ViewController.java
│   │   │   ├── dao/                          # MongoDB data access
│   │   │   │   ├── RestaurantDAO.java        # interface (29 methods)
│   │   │   │   └── RestaurantDAOImpl.java     # aggregation pipelines
│   │   │   ├── domain/                       # MongoDB POJO models
│   │   │   │   ├── Restaurant.java           # aggregate root
│   │   │   │   ├── Address.java              # nested document
│   │   │   │   └── Grade.java                # inspection record
│   │   │   ├── dto/                          # Request/response DTOs
│   │   │   │   ├── AuthRequest.java
│   │   │   │   ├── RegisterRequest.java
│   │   │   │   ├── JwtResponse.java
│   │   │   │   ├── RefreshRequest.java
│   │   │   │   ├── HeatmapPoint.java
│   │   │   │   ├── TopRestaurantEntry.java
│   │   │   │   └── AtRiskEntry.java
│   │   │   ├── entity/                       # JPA entities (PostgreSQL)
│   │   │   │   ├── UserEntity.java
│   │   │   │   └── BookmarkEntity.java
│   │   │   ├── repository/                   # Spring JPA repositories
│   │   │   │   ├── UserRepository.java
│   │   │   │   └── BookmarkRepository.java
│   │   │   ├── security/                     # JWT authentication
│   │   │   │   ├── JwtUtil.java              # token generation/validation
│   │   │   │   └── JwtAuthenticationFilter.java
│   │   │   ├── service/                      # Business logic layer
│   │   │   │   ├── RestaurantService.java    # analytics, computed fields
│   │   │   │   └── AuthService.java          # user auth
│   │   │   ├── sync/                         # NYC API sync orchestration
│   │   │   │   ├── SyncService.java          # scheduler, nightly 02:00
│   │   │   │   ├── NycOpenDataClient.java    # HTTP REST client
│   │   │   │   ├── NycApiRestaurantDto.java  # API response DTO
│   │   │   │   └── SyncResult.java           # sync metadata
│   │   │   └── util/                         # Shared utilities
│   │   │       ├── ValidationUtil.java
│   │   │       └── ResponseUtil.java
│   │   └── resources/
│   │       ├── application.properties        # Spring config (main)
│   │       ├── simplelogger.properties       # SLF4J logging config
│   │       └── templates/                    # HTML templates (Thymeleaf)
│   │           ├── index.html
│   │           ├── login.html
│   │           ├── restaurant.html
│   │           ├── inspection.html
│   │           ├── inspection-map.html
│   │           └── hygiene-radar.html
│   ├── test/
│   │   └── java/com/aflokkat/
│   │       ├── dao/                          # DAO integration tests (64 tests)
│   │       ├── domain/
│   │       ├── sync/
│   │       ├── security/
│   │       ├── util/
│   │       ├── cache/
│   │       ├── config/
│   │       ├── service/
│   │       └── aggregation/
├── pom.xml                                   # Maven build config
├── docker-compose.yml                        # 4 containers: app, MongoDB, Redis, PostgreSQL
├── Dockerfile                                # Spring Boot app image
├── application.properties                    # (in src/main/resources)
├── CLAUDE.md                                 # Project context document
└── README.md                                 # Project documentation
