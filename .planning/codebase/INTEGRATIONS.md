# External Integrations

**Analysis Date:** 2026-03-27

## APIs & External Services

**NYC Open Data API:**
- Service: NYC Open Data - New York City restaurant inspection records
- What it's used for: Real-time data sync of restaurant inspection data, grades, and violations
- SDK/Client: `org.springframework.web.client.RestTemplate` (Spring Web built-in)
- Implementation: `com.aflokkat.sync.NycOpenDataClient`
- Endpoint: `https://data.cityofnewyork.us/resource/43nn-pn8j.json`
- Auth: Optional app token via `nyc.api.app_token` property (avoids rate limiting, not required)
- Pagination: Configurable page size (default 1000 records), supports `$offset` and `$limit` query params
- Retry Strategy: Exponential backoff with 3 retries, 2000ms initial delay, doubles on each retry
- Configuration:
  - URL: `nyc.api.url` property (default: `https://data.cityofnewyork.us/resource/43nn-pn8j.json`)
  - Token: `nyc.api.app_token` (empty string = no auth)
  - Page size: `nyc.api.page-size` (default: 1000)
  - Max records: `nyc.api.max_records` (0 = unlimited, set small value for local testing)

## Data Storage

**Databases:**

**MongoDB (Primary NoSQL):**
- Provider: MongoDB (self-hosted via Docker or remote URI)
- Purpose: Stores restaurant inspection data, aggregations, historical records
- Connection: URI-based via `mongodb.uri` property (default: `mongodb://mongodb:27017`)
- Database: `newyork` (configurable)
- Collection: `restaurants` (configurable)
- Client: `com.mongodb.client.MongoClient` (native synchronous driver via `org.mongodb:mongodb-driver-sync`)
- Connection Management: Singleton `MongoClientFactory` ensures single instance per JVM
- Configuration class: `com.aflokkat.config.MongoClientFactory`
- Access layer: `com.aflokkat.dao.RestaurantDAOImpl` - Raw aggregation pipelines (no Spring Data MongoDB)
- Environment override: `MONGODB_URI`, `MONGODB_DATABASE`, `MONGODB_COLLECTION`

**PostgreSQL (Relational - Users & Bookmarks):**
- Provider: PostgreSQL 15
- Purpose: Stores user accounts, authentication credentials, user bookmarks
- Connection: JDBC URL `jdbc:postgresql://postgres:5432/restaurantdb` (configurable)
- Credentials: Default `restaurant` / `restaurant` (configurable via Spring properties)
- Client: Spring Data JPA with Hibernate ORM
- Configuration:
  - Driver: `org.postgresql.Driver`
  - Dialect: `org.hibernate.dialect.PostgreSQLDialect`
  - DDL: `spring.jpa.hibernate.ddl-auto=update` (auto-creates/updates schema)
- Repositories:
  - `com.aflokkat.repository.UserRepository` - Spring JPA repository for `UserEntity`
  - `com.aflokkat.repository.BookmarkRepository` - Spring JPA repository for `BookmarkEntity`
- Entities:
  - `com.aflokkat.entity.UserEntity` - username, email, password_hash, role
  - `com.aflokkat.entity.BookmarkEntity` - user_id, restaurant_id, created_at

**File Storage:**
- Not used - All data is persisted in MongoDB and PostgreSQL

**Caching:**
- Redis 7 (Alpine Linux Docker image)
- Purpose: TTL-based caching for expensive MongoDB aggregation queries
- Connection: Standalone configuration via Lettuce connection factory
- Configuration:
  - Host: `redis.host` property (default: `localhost`)
  - Port: `redis.port` property (default: 6379)
  - TTL: `redis.cache.ttl-seconds` (default: 3600 seconds / 1 hour)
- Service: `com.aflokkat.cache.RestaurantCacheService`
- Access Pattern: Cache-aside (get/load/store on miss)
- Cached Keys:
  - `restaurants:by_borough` - Borough counts aggregation
  - `restaurants:cuisine_scores:{cuisine}` - Average scores by borough for a cuisine
  - `restaurants:worst_cuisines:{borough}:{limit}` - Worst cuisines in a borough
  - `restaurants:top` - Sorted set of top/healthiest restaurants (sorted by inspection score)
- Graceful Degradation: All Redis failures logged as warnings; app continues without cache

## Authentication & Identity

**Auth Provider:**
- Custom JWT-based authentication (no external provider)
- Implementation: `com.aflokkat.service.AuthService` + `com.aflokkat.security.JwtUtil`

**JWT Tokens:**
- Algorithm: HMAC SHA-256 (HS256)
- Secret: Minimum 32 characters, configured via `jwt.secret` property
- Access Token:
  - Expiration: 15 minutes (900000 ms, configurable via `jwt.access.expiration.ms`)
  - Claims: subject (username), role, iat, exp
- Refresh Token:
  - Expiration: 7 days (604800000 ms, configurable via `jwt.refresh.expiration.ms`)
  - Claims: subject (username), iat, exp
- Endpoint: `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`

**Password Encoding:**
- Spring Security `PasswordEncoder` (BCrypt via `spring-boot-starter-security`)
- Passwords hashed and salted before storage in PostgreSQL

**Request Authentication:**
- JWT validation via `com.aflokkat.security.JwtAuthenticationFilter`
- Filter intercepts all `/api/*` requests
- Bearer token extraction from `Authorization: Bearer {token}` header
- Invalid/expired tokens return 401 Unauthorized

## Monitoring & Observability

**Error Tracking:**
- Not configured - No external error tracking service (Sentry, DataDog, etc.)

**Logs:**
- Framework: SLF4J + Logback (included by `spring-boot-starter-logging`)
- Configuration: `src/main/resources/application.properties`
  - Root level: `INFO`
  - Application package: `DEBUG` (logs from `com.aflokkat`)
- Output: Console/stdout (standard Docker logging)
- Key log sources:
  - `NycOpenDataClient` - API sync attempts, retries, failures
  - `RestaurantCacheService` - Cache hits/misses, invalidations, Redis errors
  - `SyncService` - Data sync lifecycle events
  - `AuthService` - Authentication events

**Health Checks:**
- Endpoint: `GET /api/restaurants/health`
- Docker Compose health check: Polls endpoint every 30s with 10s timeout, 3 retries
- Response: Used for service readiness in orchestration

## CI/CD & Deployment

**Hosting:**
- Docker (containerized Java application)
- Deployment: Docker Compose (local dev) or any container orchestrator (K8s, Docker Swarm, etc.)

**CI Pipeline:**
- Not configured - No GitHub Actions, Jenkins, GitLab CI, etc. in repository

**Container Image:**
- Multi-stage Dockerfile: Maven build stage → Java 21 JRE runtime
- Base image (production): `eclipse-temurin:21-jre-jammy` (Debian 12)
- Build image: `maven:3.8-eclipse-temurin-21`
- Exposed port: 8080
- Default ENTRYPOINT: `java -jar app.jar`
- Environment variables pre-set in Dockerfile (can be overridden by Docker Compose)

**Docker Compose Orchestration:**
- 4 services: app (Spring Boot), mongodb, redis, postgres
- Networking: `restaurant-network` (bridge driver)
- Persistent volumes: `mongodb_data`, `postgres_data`
- Service dependencies: App depends on mongodb, redis, postgres (with health check conditions)
- Restart policy: `unless-stopped` (automatic recovery on crash)

## Environment Configuration

**Required Environment Variables:**

**MongoDB:**
- `MONGODB_URI` - Connection string (default: `mongodb://mongodb:27017`)
- `MONGODB_DATABASE` - Database name (default: `newyork`)
- `MONGODB_COLLECTION` - Collection name (default: `restaurants`)

**PostgreSQL:**
- `spring.datasource.url` - JDBC URL (default: `jdbc:postgresql://postgres:5432/restaurantdb`)
- `spring.datasource.username` - User (default: `restaurant`)
- `spring.datasource.password` - Password (default: `restaurant`)

**Redis:**
- `REDIS_HOST` - Hostname (default: `localhost`)
- `REDIS_PORT` - Port (default: `6379`)
- `redis.cache.ttl-seconds` - Cache TTL (default: 3600)

**JWT Security:**
- `jwt.secret` - Signing secret, minimum 32 characters (no default - must be set)
- `jwt.access.expiration.ms` - Access token lifetime (default: 900000)
- `jwt.refresh.expiration.ms` - Refresh token lifetime (default: 604800000)

**NYC Open Data API:**
- `nyc.api.url` - API endpoint (default: `https://data.cityofnewyork.us/resource/43nn-pn8j.json`)
- `nyc.api.app_token` - Optional app token for rate limit increase (empty/optional)
- `nyc.api.page-size` - Pagination size (default: 1000)
- `nyc.api.max_records` - Max records to sync (0 = unlimited, set small for local testing)

**Configuration Priority:**
1. System environment variables (Docker Compose `environment:` section)
2. `.env` file (local development only, loaded by dotenv-java)
3. `src/main/resources/application.properties` (defaults)

**Secrets Location:**
- Secrets NOT committed to git
- `.env` file listed in `.gitignore` (local only)
- Docker Compose environment variables passed at runtime
- Production: Use container secrets management (Docker Secrets, K8s Secrets, HashiCorp Vault, etc.)

## Webhooks & Callbacks

**Incoming:**
- None - API is request-response only (no webhook consumers)

**Outgoing:**
- None - No external systems are called via webhook (only NYC Open Data API via scheduled sync)

## Data Sync Flow

**Scheduled Sync:**
- Trigger: `com.aflokkat.sync.SyncService` (Spring `@Scheduled` task, configurable pool size: 2 threads)
- Process:
  1. `NycOpenDataClient.fetchAll()` - HTTP paginated fetch from NYC Open Data API
  2. Transform raw DTOs to `Restaurant` domain objects
  3. Bulk upsert into MongoDB `restaurants` collection
  4. Invalidate Redis cache (delete all `restaurants:*` keys)
  5. Rebuild top restaurants sorted set

**Sync Configuration:**
- Thread pool size: `spring.task.scheduling.pool.size=2`
- API endpoint: `https://data.cityofnewyork.us/resource/43nn-pn8j.json`
- Pagination: 1000 records per page (configurable)
- Retry: 3 attempts with exponential backoff (2s, 4s, 8s)
- Error handling: Exceptions logged, retried on next cycle (if scheduled)

---

*Integration audit: 2026-03-27*
