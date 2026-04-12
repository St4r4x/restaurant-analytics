# Phase 14: Testcontainers Integration Tests - Context

**Gathered:** 2026-04-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Migrate `RestaurantDAOIntegrationTest` (currently requires a live `localhost:27017` MongoDB) to Testcontainers so that:
- `mvn failsafe:integration-test` starts real MongoDB and PostgreSQL containers automatically
- Tests run in CI without any external database dependency
- A developer with no local MongoDB/PostgreSQL can run the full integration suite

This phase does NOT add full service-layer integration tests — that is Phase 19.
This phase does NOT add E2E browser tests — that is Phase 18.

</domain>

<decisions>
## Implementation Decisions

### MongoDB Test Data Seeding
- **D-01:** Seed test data programmatically in `@BeforeClass` by inserting ~50 hand-crafted `Document` objects via the MongoClient connected to the Testcontainer. No external fixture files, no NYC API calls.
- **D-02:** Cover all 5 NYC boroughs (Manhattan, Brooklyn, Queens, Bronx, Staten Island) and at least 3 cuisines (Italian, Chinese, American) across the seeded documents so all existing DAO assertions hold.
- **D-03:** Adapt the `findCuisinesWithMinimumCount` threshold in the IT test to match the seeded volume (e.g., `findCuisinesWithMinimumCount(10)` if 15+ Italian docs are inserted) — do NOT try to seed 500+ documents. The DAO logic is what is under test, not data volume.
- **D-04:** Scores and grades must be present in seeded documents so aggregation pipeline tests (avgScore assertions) return non-empty results.

### Testcontainers Version & JUnit 4 Compatibility
- **D-05:** Testcontainers pinned at **1.19.8** — 2.x dropped JUnit 4 support and this project uses `junit-vintage-engine`. Do NOT upgrade.
- **D-06:** Use `@ClassRule` (`public static MongoDBContainer mongoContainer = ...`) and `@ClassRule` (`public static PostgreSQLContainer pgContainer = ...`) — single container shared across all tests in the class for speed.

### MongoDB URI Injection
- **D-07:** Call `System.setProperty("MONGODB_URI", mongoContainer.getConnectionString())` in `@BeforeClass`, before any DAO is constructed. `AppConfig.getProperty()` checks `System.getProperty()` before env vars, so `MongoClientFactory` will pick up the TC-provided URI instead of `localhost:27017`.
- **D-08:** Similarly, call `System.setProperty("SPRING_DATASOURCE_URL", pgContainer.getJdbcUrl())` (and username/password) in `@BeforeClass` so the Spring JPA context wires to the TC PostgreSQL instance.

### Failsafe Test Naming
- **D-09:** All integration tests use `*IT.java` naming convention — run under Failsafe (`mvn failsafe:integration-test`), NOT during `mvn test`. Unit tests remain `*Test.java` under Surefire. This keeps fast unit tests and slow container tests in separate lifecycle phases.
- **D-10:** `RestaurantDAOIntegrationTest.java` → renamed to `RestaurantDAOIT.java` (same package `com.aflokkat.dao`).

### PostgreSQL Integration Test
- **D-11:** Add `UserRepositoryIT.java` in `com.aflokkat.repository` covering:
  - `UserRepository.save()` + `findByUsername()` for a `UserEntity`
  - `BookmarkRepository.save()` + `findByUserEntityId()` for a `BookmarkEntity` linked to the saved user
- **D-12:** The PG IT test uses `@SpringBootTest` with `spring.profiles.active=test` so `application-test.properties` is loaded — then `System.setProperty` overrides the datasource URL/username/password to point at the TC container before the Spring context initializes.

### Claude's Discretion
- Exact number of seeded documents: Claude decides based on what makes DAO queries return non-empty results with correct sort/grouping.
- Whether to use a `@Before`/`@BeforeClass` helper method or an inner `TestDataFactory` — Claude decides based on readability.
- Testcontainers MongoDB image version: use `mongo:7.0` (matches Phase 13's pinned production image).
- PostgreSQL image version: use `postgres:15-alpine` (matches production stack).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §Testing — TEST-04 (real MongoDB + PostgreSQL via TC), TEST-05 (migrate existing IT), TEST-06 (CI without external DB)

### Phase Goal & Success Criteria
- `.planning/ROADMAP.md` §Phase 14 — 3 success criteria (mvn failsafe:integration-test, renamed+extended test passes in CI, developer can delete local DBs)

### Files to Read Before Modifying
- `src/test/java/com/aflokkat/dao/RestaurantDAOIntegrationTest.java` — existing test to migrate and rename; read all 14 test methods to understand what data must be seeded
- `pom.xml` — Failsafe plugin config (Phase 12), existing Surefire argLine, Testcontainers dependency (must be added at 1.19.8)
- `src/main/java/com/aflokkat/config/AppConfig.java` — `getProperty()` lookup order (System.getProperty → env → dotenv → properties); injection point for TC URIs
- `src/main/java/com/aflokkat/config/MongoClientFactory.java` — static singleton; reads MONGODB_URI at initialization time
- `src/test/resources/application-test.properties` — safe test values; TC overrides via System.setProperty will take precedence
- `src/main/java/com/aflokkat/entity/UserEntity.java` — JPA entity for UserRepositoryIT
- `src/main/java/com/aflokkat/entity/BookmarkEntity.java` — JPA entity for UserRepositoryIT
- `src/main/java/com/aflokkat/repository/UserRepository.java` — Spring Data JPA repo to test
- `src/main/java/com/aflokkat/repository/BookmarkRepository.java` — Spring Data JPA repo to test

### Prior Phase Context
- `.planning/STATE.md` §Accumulated Context — TC 1.x pin, AppConfig System.getProperty injection, MongoClientFactory static singleton note
- `.planning/phases/12-maven-build-hardening/12-CONTEXT.md` §Failsafe Plugin — D-06 (Failsafe bound to integration-test/verify), D-07 (no *IT.java files yet)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RestaurantDAOIntegrationTest.java` — 14 test methods covering all 4 DAO use cases + generic queries; rename to `RestaurantDAOIT.java` and adapt
- `AppConfig.getProperty()` — already checks `System.getProperty()` first; TC URI injection requires no AppConfig changes
- `MongoClientFactory` — static singleton; must receive TC URI via `System.setProperty("MONGODB_URI", ...)` before first instantiation
- `application-test.properties` — already exists (Phase 13); provides JWT secret and empty signup codes; TC overrides datasource/redis URLs

### Established Patterns
- JUnit 4 `@ClassRule` for shared container — project uses JUnit 4 `@Test` throughout (JUnit Vintage); use `@ClassRule` not JUnit 5 `@Container`
- `@Before`/`@After` lifecycle in existing integration test — keep same pattern for setup/teardown hooks
- `RestaurantDAOImpl` constructor calls `AppConfig.getProperty()` → `MongoClientFactory.getInstance()` — injection must happen before constructor call

### Integration Points
- `pom.xml`: add `org.testcontainers:testcontainers:1.19.8`, `org.testcontainers:mongodb:1.19.8`, `org.testcontainers:postgresql:1.19.8` as `<scope>test</scope>` dependencies
- Failsafe `<includes>` pattern must include `**/*IT.java` (already the default for Failsafe, but confirm)
- `UserRepositoryIT` needs a Spring context — use `@RunWith(SpringRunner.class)` + `@SpringBootTest` with `@ActiveProfiles("test")`

</code_context>

<specifics>
## Specific Ideas

- The `MongoClientFactory` static singleton is the critical initialization order problem. `System.setProperty("MONGODB_URI", ...)` must be called in a `static {}` block or `@BeforeClass` that runs before any `new RestaurantDAOImpl()` call.
- For `UserRepositoryIT`: `@SpringBootTest` will try to connect to PostgreSQL and MongoDB at context startup — both containers must be started and their URIs set via `System.setProperty` before Spring context loads. A `static {}` initializer that starts containers and sets properties is the cleanest pattern.
- Testcontainers `mongo:7.0` image matches the pinned production image from Phase 13's `docker-compose.yml`.

</specifics>

<deferred>
## Deferred Ideas

- Full service-layer integration tests (AuthService, RestaurantService) against real DBs → Phase 19
- Redis integration test (RedisTemplate against a real Redis container) → Phase 19 or standalone phase
- Seeding from a BSON dump for more realistic data → out of scope for this phase

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 14-testcontainers-integration-tests*
*Context gathered: 2026-04-12*
