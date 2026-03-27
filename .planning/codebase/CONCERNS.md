# Concerns

**Analysis Date:** 2026-03-27

## Tech Debt

### Code Quality
- **Raw exception catching** — broad `catch (Exception e)` in several DAO/service methods instead of typed exceptions; hides root causes
- **Monolithic RestaurantController** — ~413 lines with 18 endpoints; could be split by domain (analytics, inspections)
- **Swallowed interruptions** — `InterruptedException` caught and logged but thread interrupt status not restored
- **Missing null checks** — several DAO return values used without null guard before calling `.isEmpty()` or `.get(0)`
- **Hardcoded JWT secret placeholder** — `jwt.secret` in `application.properties` is a placeholder; no enforcement of minimum entropy at startup

### Architecture
- **SyncService race condition** — `running` boolean flag used for concurrency control without `volatile` or `AtomicBoolean`; unsafe under multi-thread access
- **MongoDB codec registry initialized per-request** — `MongoClientFactory` rebuilds codec registry on every call instead of caching it
- **DAO fieldName not validated** — dynamic field name injection in aggregation pipelines (e.g. sort fields) uses unvalidated strings; potential NoSQL injection vector

### Configuration
- **API token in URL** — `nyc.api.app_token` appended as query parameter; should be in `Authorization` header
- **Plain-text credentials in `docker-compose.yml`** — PostgreSQL password `restaurant` hardcoded in compose file

## Known Bugs

- **Legacy address format** — Some older restaurant documents have flat address fields instead of the nested `Address` object; parsing falls back silently, producing null coordinates
- **Duplicate grades from pagination** — When NYC Open Data API returns overlapping pages during sync, duplicate grade entries can accumulate in MongoDB
- **NPE if no grades** — `getLatestGrade()` in `RestaurantService` assumes non-empty grade list; throws NPE on restaurants with no inspection history

## Security

| Issue | Location | Severity |
|---|---|---|
| CORS wildcard (`*`) | `SecurityConfig.java` | High |
| JWT validation swallows exceptions | `JwtAuthenticationFilter.java` | Medium |
| NoSQL injection via unvalidated sort field | `RestaurantDAOImpl.java` | Medium |
| API key in query param (not header) | `NycOpenDataClient.java` | Low |
| Hardcoded Docker credentials | `docker-compose.yml` | Low |
| No rate limiting on auth endpoints | `AuthController.java` | Medium |

## Performance

- **N+1 queries in `getHygieneRadarRestaurants`** — fetches restaurant list then queries each grade individually instead of using a single aggregation
- **Unbounded aggregation memory** — `$group` stages in several pipelines have no `$limit` before grouping; can cause OOM on full dataset
- **Redis serialization overhead** — objects serialized to JSON string in Redis; no binary serialization (e.g. MessagePack)
- **Sync blocks cache operations** — `SyncService` does not invalidate Redis during sync, so stale data can be served mid-sync for up to 3600s

## Fragile Areas

- **Geospatial index creation** — `ensureIndexes()` in DAO runs `createIndex` without checking if index exists; fails silently on duplicate but adds startup latency
- **`SyncService.running` flag** — stop signal is not synchronized; a rapid start/stop sequence can leave the service in an inconsistent state
- **MongoDB codec registry** — custom POJOs registered with `PojoCodecProvider`; adding a new domain field without a codec causes silent deserialization failure (field is null)
- **`nyc.api.max_records=0`** — means unlimited; accidentally leaving this at 0 in production initiates a full sync that can take many minutes and exhaust memory

## Scaling Limits

- **Single MongoDB instance** — no replica set; no read scaling; data loss risk
- **Single Redis instance** — no Redis Sentinel/Cluster; cache failure brings down all hot-path endpoints
- **JWT token size** — adding more claims will inflate token size; no token size budget enforced
- **Unlimited API pagination** — `GET /api/restaurants/*` endpoints return full lists without pagination; large responses on full dataset

## Dependencies at Risk

| Dependency | Version | Concern |
|---|---|---|
| `spring-boot-starter-parent` | 2.6.15 | Spring Boot 2.x EOL (Nov 2024); no security patches |
| `jjwt` | 0.11.5 | Older JJWT API; `jjwt-api` 0.12.x has breaking changes |
| `dotenv-java` | 3.0.0 | Unmaintained; no recent releases |
| `mongodb-driver-sync` | 4.x (via Spring Boot BOM) | Pinned to older minor; MongoDB 7.x driver available |

## Test Coverage Gaps

- No controller-layer tests (`@WebMvcTest`) — HTTP error handling and serialization untested
- No Redis failure / connection error scenarios
- No SyncService concurrency / thread-safety tests
- No JWT token tampering or replay attack tests
- No PostgreSQL / JPA layer tests (UserRepository, BookmarkRepository)
- No end-to-end API tests (e.g. RestAssured, TestContainers)
- Integration tests require manual MongoDB setup — not runnable in CI without Docker

## Observability

- No structured logging (plain SLF4J string interpolation; not JSON)
- No distributed tracing (no Micrometer / OpenTelemetry)
- No health metrics beyond `/api/restaurants/health` endpoint
- No alerting on sync failures
