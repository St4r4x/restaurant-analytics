# Phase 14: Testcontainers Integration Tests - Research

**Researched:** 2026-04-12
**Domain:** Testcontainers 1.x JUnit 4, MongoDB + PostgreSQL container lifecycle, Maven Failsafe
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Seed test data programmatically in `@BeforeClass` (~50 `Document` objects via MongoClient to the TC container). No external fixture files, no NYC API calls.
- **D-02:** Cover all 5 NYC boroughs (Manhattan, Brooklyn, Queens, Bronx, Staten Island) and at least 3 cuisines (Italian, Chinese, American) so all existing DAO assertions hold.
- **D-03:** Adapt `findCuisinesWithMinimumCount` threshold to match seeded volume — do NOT try to seed 500+ documents.
- **D-04:** Scores and grades must be present in seeded documents so aggregation pipeline tests return non-empty results.
- **D-05:** Testcontainers pinned at **1.19.8** — do NOT upgrade (2.x dropped JUnit 4 support).
- **D-06:** Use `@ClassRule` (`public static MongoDBContainer mongoContainer = ...` and `public static PostgreSQLContainer pgContainer = ...`) — single container shared across all tests in the class.
- **D-07:** Call `System.setProperty("MONGODB_URI", mongoContainer.getConnectionString())` in `@BeforeClass`. AppConfig.getProperty() must check `System.getProperty()` before env vars for this to work.
- **D-08:** Call `System.setProperty("SPRING_DATASOURCE_URL", pgContainer.getJdbcUrl())` (and username/password) in `@BeforeClass`.
- **D-09:** All integration tests use `*IT.java` naming — run under Failsafe (`mvn failsafe:integration-test`), NOT during `mvn test`.
- **D-10:** `RestaurantDAOIntegrationTest.java` → renamed to `RestaurantDAOIT.java` (same package `com.aflokkat.dao`).
- **D-11:** Add `UserRepositoryIT.java` in `com.aflokkat.repository` covering `UserRepository.save()` + `findByUsername()` and `BookmarkRepository.save()` + `findByUserId()`.
- **D-12:** `UserRepositoryIT` uses `@SpringBootTest` with `spring.profiles.active=test` — `System.setProperty` overrides datasource URL/username/password before Spring context initializes.

### Claude's Discretion

- Exact number of seeded documents: Claude decides based on making DAO queries return non-empty results.
- Whether to use a `@BeforeClass` helper or inner `TestDataFactory` — Claude decides.
- Testcontainers MongoDB image: `mongo:7.0`.
- PostgreSQL image: `postgres:15-alpine`.

### Deferred Ideas (OUT OF SCOPE)

- Full service-layer integration tests (AuthService, RestaurantService) against real DBs — Phase 19.
- Redis integration test against a real Redis container — Phase 19 or standalone.
- Seeding from a BSON dump for more realistic data — out of scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TEST-04 | User can run integration tests with real MongoDB and PostgreSQL via Testcontainers (no live database required) | MongoDBContainer 1.19.8 + PostgreSQLContainer 1.19.8 both verified downloadable; Docker daemon available; both images pre-pulled |
| TEST-05 | User can see existing `RestaurantDAOIntegrationTest` migrated to Testcontainers (no `localhost:27017` assumption) | Existing test analyzed — 14 methods, seeding strategy designed to satisfy all assertions; AppConfig injection point identified |
| TEST-06 | User can run integration tests in CI without any external DB dependency | Failsafe plugin absent from current pom.xml — must be added; naming convention `*IT.java` separates from Surefire |
</phase_requirements>

---

## Summary

Phase 14 migrates the existing `RestaurantDAOIntegrationTest` (which requires a live `localhost:27017` MongoDB) to run against Testcontainers-managed containers, and adds a new `UserRepositoryIT` for the PostgreSQL/JPA layer. The deliverables are: (1) Testcontainers 1.19.8 dependencies added to `pom.xml`, (2) `maven-failsafe-plugin` added with `@{argLine}` late-binding, (3) `AppConfig.getProperty()` modified to check `System.getProperty()` before `System.getenv()`, (4) `RestaurantDAOIntegrationTest` renamed and rewritten as `RestaurantDAOIT` with TC container lifecycle, (5) `UserRepositoryIT` new file covering Spring Data JPA repositories.

The critical constraint is that `MongoClientFactory` is a static singleton initialized on first `getInstance()` call. The TC MongoDB URI must be injected into `AppConfig` via `System.setProperty("MONGODB_URI", ...)` BEFORE any code calls `new RestaurantDAOImpl()` or `MongoClientFactory.getInstance()`. This requires both a code change to AppConfig (add `System.getProperty()` tier) and careful test initialization order using a `static {}` block or `@BeforeClass` that runs before any DAO construction.

**Primary recommendation:** Add `System.getProperty()` as the first lookup tier in `AppConfig.getProperty()`. Use a `static {}` block in `RestaurantDAOIT` to start the container and set the system property before the JUnit lifecycle begins, guaranteeing the property is in place when `RestaurantDAOImpl()` is first instantiated.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.testcontainers:testcontainers` | 1.19.8 | Core TC runtime, `@ClassRule` support, `GenericContainer` base | Last 1.x release with JUnit 4 `ExternalResource` support [VERIFIED: local Maven repo] |
| `org.testcontainers:mongodb` | 1.19.8 | `MongoDBContainer` class | Provides TC-managed MongoDB with `getConnectionString()` [VERIFIED: jar inspection] |
| `org.testcontainers:postgresql` | 1.19.8 | `PostgreSQLContainer` class | Provides TC-managed PostgreSQL with `getJdbcUrl()`, `getUsername()`, `getPassword()` [VERIFIED: jar inspection] |
| `maven-failsafe-plugin` | BOM-managed (3.x) | Runs `*IT.java` in `integration-test` + `verify` phases | Standard Maven separation of slow integration tests from fast unit tests [VERIFIED: Phase 12 CONTEXT.md D-06 through D-08] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.testcontainers:junit-4` | NOT NEEDED | TC JUnit 4 extensions | NOT needed — `GenericContainer` already extends `FailureDetectingExternalResource` which implements JUnit `ExternalResource`, so `@ClassRule` works natively [VERIFIED: jar inspection] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `@ClassRule` static container | `@Rule` per-method container | `@ClassRule` starts container once per class — faster by 10-30s per test class; `@Rule` restarts per test — safer isolation but prohibitively slow |
| `System.setProperty` injection | Separate `application-it.properties` | Properties file cannot use dynamic TC port; `System.setProperty` is the only way to inject a runtime-assigned port before Spring/AppConfig initializes |
| `static {}` block | `@BeforeClass` for container start | TC `@ClassRule` starts the container automatically when the rule fires (before `@BeforeClass`), but `System.setProperty` must happen before `new RestaurantDAOImpl()` — use `@BeforeClass` in combination (container starts via `@ClassRule`, properties set in `@BeforeClass`) |

**Installation (to add to pom.xml):**
```xml
<!-- Testcontainers — pinned at 1.19.8, do NOT use 2.x (dropped JUnit 4 support) -->
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>testcontainers</artifactId>
  <version>1.19.8</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>mongodb</artifactId>
  <version>1.19.8</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <version>1.19.8</version>
  <scope>test</scope>
</dependency>
```

**Version verification:**
```
org.testcontainers:testcontainers:1.19.8 — VERIFIED in local Maven repo ~/.m2/repository/org/testcontainers/
org.testcontainers:mongodb:1.19.8        — VERIFIED in local Maven repo
org.testcontainers:postgresql:1.19.8    — VERIFIED in local Maven repo
```

---

## Architecture Patterns

### Recommended Project Structure

```
src/test/java/com/aflokkat/
├── dao/
│   └── RestaurantDAOIT.java       # migrated from RestaurantDAOIntegrationTest
└── repository/
    └── UserRepositoryIT.java      # new PostgreSQL JPA test
```

### Pattern 1: JUnit 4 @ClassRule with MongoDBContainer (RestaurantDAOIT)

**What:** Static container started once per class, URI injected via System.setProperty in @BeforeClass, DAO constructed after property is set, collection wiped and re-seeded in @BeforeClass.

**When to use:** Pure DAO tests with no Spring context — fastest pattern since no Spring startup overhead.

**Critical initialization order:**
1. `@ClassRule` fires → TC starts `mongo:7.0` container, assigns random port
2. `@BeforeClass` fires → `System.setProperty("MONGODB_URI", mongoContainer.getConnectionString())`
3. `@BeforeClass` continues → `new RestaurantDAOImpl()` — now reads TC URI from `AppConfig.getProperty()` via the new `System.getProperty()` tier
4. `@BeforeClass` continues → seed 50 documents
5. `@Test` methods run

**Example:**
```java
// Source: Testcontainers 1.19.8 JUnit 4 API (jar inspection) + project AppConfig pattern
package com.aflokkat.dao;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.bson.Document;
import com.mongodb.client.MongoClients;
import com.aflokkat.config.MongoClientFactory;

public class RestaurantDAOIT {

    @ClassRule
    public static MongoDBContainer mongoContainer =
        new MongoDBContainer("mongo:7.0");

    private static RestaurantDAO restaurantDAO;

    @BeforeClass
    public static void setUpClass() {
        // Inject TC URI before any DAO construction — AppConfig.getProperty()
        // checks System.getProperty() first (modified in this phase)
        System.setProperty("MONGODB_URI", mongoContainer.getConnectionString());

        // Now safe to construct DAO — MongoClientFactory will use the TC URI
        restaurantDAO = new RestaurantDAOImpl();

        // Seed test data
        seedTestData();
    }

    @AfterClass
    public static void tearDownClass() {
        if (restaurantDAO != null) {
            restaurantDAO.close(); // calls MongoClientFactory.closeInstance()
        }
        // Clear System property to avoid leaking state to other test classes
        System.clearProperty("MONGODB_URI");
    }

    private static void seedTestData() {
        // Insert via raw MongoClient (independent of DAO under test)
        try (var client = MongoClients.create(mongoContainer.getConnectionString())) {
            var collection = client.getDatabase("newyork").getCollection("restaurants");
            collection.drop();
            // Insert ~50 documents covering 5 boroughs and 3+ cuisines
            // See: Seeding Design section below
        }
    }
}
```

### Pattern 2: JUnit 4 @ClassRule + @SpringBootTest for PostgreSQL (UserRepositoryIT)

**What:** Static container started via `@ClassRule`, Spring context loaded via `@SpringBootTest`, `System.setProperty` called in a static initializer block that executes before Spring context initialization.

**When to use:** Spring Data JPA repository tests that require the full Spring datasource/Hibernate wiring.

**Critical insight:** Spring Boot resolves `spring.datasource.url` from its own `Environment`, which includes `System.getProperty()` as a standard property source. Therefore `System.setProperty("spring.datasource.url", pgContainer.getJdbcUrl())` WILL be picked up by Spring without any AppConfig modification — Spring's `StandardEnvironment` always includes `systemProperties` source.

**Static initializer timing issue:** `@SpringBootTest` starts the Spring context during class loading, potentially before `@BeforeClass` or even `@ClassRule` fires. The solution is a `static {}` block on the test class — Java executes static initializers before any JUnit lifecycle begins, and before Spring context creation.

**Example:**
```java
// Source: Spring Boot test documentation + TC 1.19.8 API
package com.aflokkat.repository;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.PostgreSQLContainer;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class UserRepositoryIT {

    @ClassRule
    public static PostgreSQLContainer<?> pgContainer =
        new PostgreSQLContainer<>("postgres:15-alpine");

    // Static initializer fires before Spring context — this is the injection point
    static {
        pgContainer.start();
        System.setProperty("spring.datasource.url", pgContainer.getJdbcUrl());
        System.setProperty("spring.datasource.username", pgContainer.getUsername());
        System.setProperty("spring.datasource.password", pgContainer.getPassword());
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Test
    public void testSaveAndFindUser() {
        // ...
    }
}
```

**Note on static initializer vs @ClassRule lifecycle:** When `@ClassRule` is used on a field, TC starts the container when the Rule fires (during JUnit `@ClassRule` processing, before `@BeforeClass`). However, the static initializer `static {}` fires even earlier — during class loading. Since `pgContainer` field initializer runs before `static {}` block body, the correct pattern is to declare the container WITHOUT auto-start in the field, call `pgContainer.start()` in the static block, then set properties. Alternatively, declare the container as `@ClassRule` (which handles start) AND use an `ApplicationContextInitializer` that reads TC properties at Spring context creation time — but that is more complex. The simplest pattern for JUnit 4 is: declare container as `@ClassRule` field, AND also set properties in `@BeforeClass` by checking if Spring context is needed. For `UserRepositoryIT`, use `@SpringBootTest` with `@ContextConfiguration(initializers = UserRepositoryIT.Initializer.class)` pattern (see Pitfall 2 below).

### Pattern 3: AppConfig System.getProperty() Tier (Required Code Change)

**What:** Add `System.getProperty(key)` as the FIRST lookup before `System.getenv(envKey)` in `AppConfig.getProperty()`.

**Current code (AppConfig.java line 126-139):**
```java
private static String getProperty(String key, String defaultValue) {
    // 1. System environment variable (Docker, CI...)
    String envKey = key.replace(".", "_").toUpperCase();
    String envValue = System.getenv(envKey);
    if (envValue != null) return envValue;
    // 2. dotenv
    // 3. application.properties
}
```

**Required change — add System.getProperty() as tier 0:**
```java
private static String getProperty(String key, String defaultValue) {
    // 0. JVM system property (Testcontainers injection in tests)
    String sysProp = System.getProperty(key);
    if (sysProp != null) return sysProp;

    // 1. System environment variable (Docker, CI...)
    String envKey = key.replace(".", "_").toUpperCase();
    String envValue = System.getenv(envKey);
    if (envValue != null) return envValue;
    // 2. dotenv
    // 3. application.properties
}
```

**Why this is necessary:** `MongoClientFactory.getInstance()` calls `AppConfig.getMongoUri()` which calls `getProperty("mongodb.uri", ...)`. Without the `System.getProperty()` tier, the TC URI set via `System.setProperty("MONGODB_URI", ...)` is never seen — only `System.getenv("MONGODB_URI")` is checked, and Java does not allow setting OS environment variables from within a running JVM.

**Note:** `System.setProperty("MONGODB_URI", ...)` uses the env-style key. `System.getProperty("mongodb.uri")` uses the properties-style key. These are DIFFERENT keys. The injection call must use `System.setProperty("mongodb.uri", mongoContainer.getConnectionString())` to match the `System.getProperty(key)` lookup where `key` = `"mongodb.uri"`.

### Seeding Design for RestaurantDAOIT

**Required document count analysis (from DAO assertions):**

| DAO Method | Assertion | Seeding Requirement |
|-----------|-----------|---------------------|
| `findCountByBorough()` | `results.size() >= 5`, sorted descending | 1+ restaurant per each of 5 boroughs |
| `findCountByBorough()` | `count > 0` for all entries | All boroughs have positive count |
| `findAverageScoreByCuisineAndBorough("Italian")` | Non-empty, `avgScore > 0` | Italian restaurants in at least 1 borough with non-null scores |
| `findAverageScoreByCuisineAndBorough("NonExistentCuisine12345")` | Returns non-null list | Empty list acceptable |
| `findWorstCuisinesByAverageScoreInBorough("Manhattan", 3)` | `size() <= 3` | Manhattan restaurants with grades |
| `findWorstCuisinesByAverageScoreInBorough("Manhattan", 5)` | `cuisine != null`, `avgScore > 0`, `count > 0` | Manhattan restaurants with non-null cuisine and grades |
| `findWorstCuisines_SortedByScore` | ascending sort | Multiple cuisines in Manhattan with different avg scores |
| `findCuisinesWithMinimumCount(threshold)` | non-empty | **threshold must be adapted to seeded volume** (D-03) |
| `findCuisinesWithMinCount_Alphabetical` | sorted alphabetically | Multiple cuisines meeting threshold |
| `findCuisinesWithHighMinCount(bigThreshold)` | non-null list | Empty list acceptable |
| `countAll()` | `count > 0` | Any restaurant in collection |
| `countByCuisine("Italian")` | `count > 0` | At least 1 Italian restaurant |
| `findByCuisine("Italian", 10)` | non-null, non-empty | At least 1 Italian restaurant |

**Recommended seeding layout (60 documents total — Claude's discretion per D-03):**

| Cuisine | Manhattan | Brooklyn | Queens | Bronx | Staten Island | Total |
|---------|-----------|----------|--------|-------|---------------|-------|
| Italian | 5 | 4 | 3 | 3 | 2 | **17** |
| Chinese | 5 | 4 | 4 | 2 | 1 | **16** |
| American | 5 | 4 | 3 | 3 | 1 | **16** |
| French (bonus) | 4 | 3 | 2 | 1 | 1 | **11** |
| Total | **19** | **15** | **12** | **9** | **5** | **60** |

**Adapted test thresholds (D-03):**
- `testUseCase4_GetCuisinesWithMinimumCount_500` → change `500` to `10` (Italian=17, Chinese=16, American=16, French=11 all qualify)
- `testUseCase4_CuisinesWithHighMinCount` with `1000` → change to `20` (no cuisine reaches 20, so returns empty list — still valid: asserts non-null)

**Document structure per seed record (matches MongoClientFactory/RestaurantDAOImpl field expectations):**
```java
// Field names must match BSON field names used in DAO aggregations
// borough field: "borough" (title-case: "Manhattan", "Brooklyn", etc.)
// cuisine field: "cuisine" (full name: "Italian", "Chinese", "American")
// grades field: array of embedded documents with "score" (int) and "grade" (string)
new Document("restaurant_id", "R001")
    .append("name", "Mario Ristorante")
    .append("cuisine", "Italian")
    .append("borough", "Manhattan")
    .append("address", new Document("building", "100")
        .append("street", "5th Ave")
        .append("zipcode", "10001")
        .append("coord", Arrays.asList(-73.9857, 40.7484)))
    .append("grades", Arrays.asList(
        new Document("date", "2024-01-15")
            .append("grade", "A")
            .append("score", 10)
    ))
```

**Field name source of truth (verified from codebase):**
- `borough`: `findCountByBorough()` groups on `"$borough"` — title-case required [VERIFIED: RestaurantDAOImpl.java line 155-158]
- `cuisine`: `findByCuisine()` filters on `"cuisine"` [VERIFIED: RestaurantDAOImpl.java line 103]
- `grades[].score`: `findAverageScoreByCuisineAndBorough` unwinds `"$grades"`, averages `"$grades.score"` [VERIFIED: line 163-171]
- `findByCuisine` sorts on `"name"` field [VERIFIED: line 106]
- `findAll` filters on `"address"` with `$type: 3` (embedded document) — seeded records must have address as Document, not string [VERIFIED: line 95]

### Anti-Patterns to Avoid

- **JUnit 5 @Container annotation:** This project uses JUnit Vintage (JUnit 4). `@Container` and `@Testcontainers` are JUnit 5 annotations — using them will cause the test runner to ignore them silently or throw errors.
- **tc.image.pull.strategy in tests:** Don't set custom pull strategies in test code; Docker daemon will use locally cached images.
- **Calling MongoClientFactory.getInstance() before System.setProperty:** Any code path that touches `MongoClientFactory` before the property is set will cache the wrong URI in the static field `mongoClient`. There is no way to undo this without calling `MongoClientFactory.closeInstance()`.
- **Seeding via RestaurantDAOImpl.upsertRestaurants():** Do NOT use the DAO under test to seed data — seed via a separate raw MongoClient to avoid conflating test setup with tested behavior.
- **Not clearing MONGODB_URI system property in @AfterClass:** If `System.clearProperty("mongodb.uri")` is not called, subsequent test classes that construct a new DAO may inherit the stale property (or worse, the MongoClient singleton is already closed).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Starting MongoDB for tests | Custom Docker wrapper or `ProcessBuilder("mongod")` | `MongoDBContainer` (TC 1.19.8) | TC handles port assignment, readiness waiting, container lifecycle [VERIFIED: jar inspection] |
| Starting PostgreSQL for tests | Embedded H2 database | `PostgreSQLContainer` (TC 1.19.8) | H2 doesn't support PostgreSQL-specific column types or constraints; TC gives the real engine [ASSUMED] |
| Waiting for container readiness | `Thread.sleep(5000)` | TC built-in wait strategy | TC polls container logs for ready signal; sleep is flaky and wastes time [VERIFIED: TC docs pattern] |
| Dynamic port discovery | Hardcoded `localhost:27017` | `mongoContainer.getConnectionString()` | TC assigns a random free host port; hardcoding causes failures when that port is in use [VERIFIED: jar inspection] |

---

## Critical Finding: AppConfig Does Not Check System.getProperty()

**This is the most important finding from code analysis.**

`AppConfig.getProperty()` lookup order (current, from `AppConfig.java` lines 126-139):
1. `System.getenv(MONGODB_URI)` — OS environment variable
2. `dotenv.get(MONGODB_URI)` — .env file
3. `properties.getProperty("mongodb.uri")` — application.properties

`System.setProperty("MONGODB_URI", ...)` sets a JVM system property — this is NOT visible to `System.getenv()`. In Java, `System.getProperty()` and `System.getenv()` are completely separate key/value stores. You cannot set OS environment variables from within a running JVM.

**Required fix:** Phase 14 must modify `AppConfig.getProperty()` to add `System.getProperty(key)` as tier 0:
```java
// Tier 0: JVM system properties (used by Testcontainers tests)
String sysProp = System.getProperty(key);
if (sysProp != null) return sysProp;
```

**Injection call must use the properties-style key:**
```java
// CORRECT — matches AppConfig tier 0 lookup on key "mongodb.uri"
System.setProperty("mongodb.uri", mongoContainer.getConnectionString());

// WRONG — "MONGODB_URI" is the env-style key, not the properties-style key
// System.setProperty("MONGODB_URI", mongoContainer.getConnectionString());
```

**For PostgreSQL/Spring datasource:** Spring Boot's `Environment` already includes `systemProperties` as a standard property source. `System.setProperty("spring.datasource.url", pgContainer.getJdbcUrl())` WILL be picked up by Spring without any AppConfig modification. However, the static initializer timing is still critical (see Pitfall 2).

[VERIFIED: AppConfig.java source code — lines 126-139 inspected directly]

---

## Critical Finding: pom.xml on Current Branch is Pre-Phase-12

**The pom.xml on `gsd/phase-14-testcontainers-integration-tests` is the pre-Phase-12 version:**
- Spring Boot 2.6.15 (not 3.4.4)
- Java 11 (not 25)
- No JaCoCo plugin
- No Failsafe plugin
- Surefire argLine is literal `-XX:+EnableDynamicAgentLoading` (not `@{argLine}`)
- `RestaurantDAOIntegrationTest` has NO `@Ignore` annotation

Phases 12 and 13 were executed on their own branches but NOT merged to `main` or `develop`. This branch was created from the pre-Phase-12 state.

**Phase 14 must therefore include ALL of the following pom.xml changes:**
1. Add Testcontainers dependencies (3 artifacts at 1.19.8)
2. Add `maven-failsafe-plugin` with `@{argLine}` late-binding
3. Fix Surefire argLine to `@{argLine} -XX:+EnableDynamicAgentLoading`
4. Optionally add JaCoCo (if TEST-07 is in scope — it is NOT for Phase 14; do not add JaCoCo here)
5. Add `@Ignore` to `RestaurantDAOIntegrationTest` BEFORE renaming (or just rename directly to `*IT.java`)

**Note on Spring Boot version:** Phase 14 does NOT need to upgrade Spring Boot. The Testcontainers 1.19.8 libraries work with Spring Boot 2.6.15. [VERIFIED: TC 1.19.8 has no Spring Boot version requirement in pom]

[VERIFIED: git branch comparison — `gsd/phase-12-maven-build-hardening:pom.xml` shows Spring Boot 3.4.4 / Java 25; `gsd/phase-14-testcontainers-integration-tests:pom.xml` shows Spring Boot 2.6.15 / Java 11]

---

## Common Pitfalls

### Pitfall 1: MongoClientFactory Singleton Caches Wrong URI

**What goes wrong:** `MongoClientFactory.getInstance()` is called by the first `new RestaurantDAOImpl()` anywhere in the test process. If ANY other test class runs before `RestaurantDAOIT` and constructs a DAO (even indirectly via Spring context), the static `mongoClient` field is set to `localhost:27017`. When `RestaurantDAOIT` then sets the system property and constructs a new DAO, `getInstance()` sees the cached non-null `mongoClient` and returns it without checking the URI.

**Why it happens:** `MongoClientFactory` is a static singleton — `mongoClient` persists for the entire JVM lifetime.

**How to avoid:**
- Call `MongoClientFactory.closeInstance()` at the START of `@BeforeClass` to reset the singleton, then set the system property, then construct the DAO.
- Alternatively: `RestaurantDAOIT` should be an `*IT.java` file (Failsafe), so it runs in a separate JVM fork from unit tests (Surefire). If Failsafe is configured with `<forkCount>1</forkCount>` (default), each test class gets its own JVM — no cross-class state leakage.

**Warning signs:** Test works in isolation (`mvn failsafe:integration-test -Dit.test=RestaurantDAOIT`) but fails when the full suite runs.

### Pitfall 2: Spring Context Initializes Before Static Initializer in UserRepositoryIT

**What goes wrong:** `@SpringBootTest` with `@RunWith(SpringRunner.class)` initializes the Spring `ApplicationContext` during test class setup. If `System.setProperty("spring.datasource.url", ...)` is placed in `@BeforeClass`, it may fire AFTER Spring's datasource connection pool has already been created with the `application-test.properties` value (`localhost:5432`).

**Why it happens:** Spring context creation happens during test infrastructure setup, before JUnit lifecycle methods.

**How to avoid (two options):**

Option A — Use `@ContextConfiguration` with `ApplicationContextInitializer`:
```java
@SpringBootTest
@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = UserRepositoryIT.Initializer.class)
public class UserRepositoryIT {

    @ClassRule
    public static PostgreSQLContainer<?> pgContainer =
        new PostgreSQLContainer<>("postgres:15-alpine");

    public static class Initializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of(
                "spring.datasource.url=" + pgContainer.getJdbcUrl(),
                "spring.datasource.username=" + pgContainer.getUsername(),
                "spring.datasource.password=" + pgContainer.getPassword()
            ).applyTo(ctx.getEnvironment());
        }
    }
}
```

Option B — Use static initializer block (start container manually):
```java
@ClassRule
public static PostgreSQLContainer<?> pgContainer = new PostgreSQLContainer<>("postgres:15-alpine");

static {
    pgContainer.start();
    System.setProperty("spring.datasource.url",      pgContainer.getJdbcUrl());
    System.setProperty("spring.datasource.username", pgContainer.getUsername());
    System.setProperty("spring.datasource.password", pgContainer.getPassword());
}
```

**Recommendation:** Option A is cleaner and idiomatic for Spring Boot integration tests. Option B is simpler but depends on class loading order.

**Warning signs:** `PSQLException: Connection refused` or `HikariPool connection failure` during Spring context startup.

### Pitfall 3: Missing Redis in UserRepositoryIT Spring Context

**What goes wrong:** `@SpringBootTest` loads the full `Application.java` context. The application has Redis (`RestaurantCacheService`) wired in. If the Redis bean cannot connect at startup, the Spring context fails to start — even though `UserRepositoryIT` doesn't test Redis.

**Why it happens:** Spring Boot's auto-configuration creates a `RedisTemplate` and `RestaurantCacheService` bean. The Redis connection pool may attempt a connection at startup.

**How to avoid:**
- `application-test.properties` already sets `redis.host=localhost`. In tests without a Redis TC container, this will fail to connect but Spring's `RedisAutoConfiguration` typically does not fail-fast on connection errors (it fails lazily when operations are first executed).
- If connection failure causes context load failure: add `spring.redis.host` + `spring.redis.port` to `application-test.properties` pointing to a non-resolvable host, OR use `@MockBean` for `RestaurantCacheService` in `UserRepositoryIT`.

**Warning signs:** `RedisConnectionException` or `io.lettuce.core.RedisConnectionException` during Spring context startup in `UserRepositoryIT`.

### Pitfall 4: findByCuisine Uses "cuisine" Field, Not "cuisine_description"

**What goes wrong:** The NYC API DTO uses `cuisine_description` but `SyncService.buildRestaurant()` maps it to `r.setCuisine(first.getCuisineDescription())`. The MongoDB field is stored as `cuisine`. Seeding documents with `"cuisine_description"` key will cause `findByCuisine("Italian")` to return empty.

**How to avoid:** Seed documents using `"cuisine"` field name. [VERIFIED: RestaurantDAOImpl.java line 103: `new Document("cuisine", cuisine)`]

### Pitfall 5: Failsafe Default Includes Pattern Does Not Cover *IT.java Without Explicit Configuration

**What goes wrong:** The Failsafe plugin default includes pattern is `**/*IT.java, **/*ITCase.java, **/IT*.java`. This SHOULD include `*IT.java` by default. However, if Failsafe is not bound to any lifecycle phases, `mvn failsafe:integration-test` will still run tests but `mvn verify` may not.

**How to avoid:** Explicitly configure Failsafe executions with `integration-test` and `verify` goals:
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
  <configuration>
    <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
  </configuration>
  <executions>
    <execution>
      <id>integration-tests</id>
      <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

[VERIFIED: Phase 12 CONTEXT.md D-06 through D-08 and VERIFICATION.md confirm this exact pattern was used on the phase-12 branch]

### Pitfall 6: @ClassRule Container Field Must Be public static

**What goes wrong:** JUnit 4 `@ClassRule` requires the field to be `public static`. A non-public or non-static field causes `java.lang.Exception: The @ClassRule annotated field 'mongoContainer' must be public and static`.

**How to avoid:**
```java
// CORRECT
@ClassRule
public static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:7.0");

// WRONG — will throw
@ClassRule
private static MongoDBContainer mongoContainer = ...;
```

---

## Code Examples

### Complete RestaurantDAOIT skeleton

```java
// Source: Testcontainers 1.19.8 API (jar inspection) + AppConfig analysis
package com.aflokkat.dao;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import org.bson.Document;
import org.junit.*;
import org.testcontainers.containers.MongoDBContainer;
import com.mongodb.client.MongoClients;
import com.aflokkat.aggregation.*;
import com.aflokkat.config.MongoClientFactory;
import com.aflokkat.domain.Restaurant;

public class RestaurantDAOIT {

    @ClassRule
    public static MongoDBContainer mongoContainer =
        new MongoDBContainer("mongo:7.0");

    private static RestaurantDAO restaurantDAO;

    @BeforeClass
    public static void setUpClass() {
        // Reset singleton in case other test classes initialized it
        MongoClientFactory.closeInstance();

        // Inject TC URI as System property — AppConfig.getProperty() checks
        // System.getProperty("mongodb.uri") as tier 0 (added in this phase)
        System.setProperty("mongodb.uri", mongoContainer.getConnectionString());

        restaurantDAO = new RestaurantDAOImpl();
        seedTestData();
    }

    @AfterClass
    public static void tearDownClass() {
        if (restaurantDAO != null) restaurantDAO.close();
        System.clearProperty("mongodb.uri");
    }

    private static void seedTestData() {
        try (var client = MongoClients.create(mongoContainer.getConnectionString())) {
            var col = client.getDatabase("newyork").getCollection("restaurants");
            col.drop();
            col.insertMany(buildSeedDocuments());
        }
    }

    // ... 14 test methods (see existing RestaurantDAOIntegrationTest — rename + adapt thresholds)
}
```

### AppConfig getProperty() modification

```java
// Source: AppConfig.java lines 126-139 — add tier 0
private static String getProperty(String key, String defaultValue) {
    // 0. JVM system property — for Testcontainers URI injection in tests
    String sysProp = System.getProperty(key);
    if (sysProp != null) return sysProp;

    // 1. System environment variable (Docker, CI...)
    String envKey = key.replace(".", "_").toUpperCase();
    String envValue = System.getenv(envKey);
    if (envValue != null) return envValue;

    // 2. Fichier .env
    if (dotenv != null) {
        String dotenvValue = dotenv.get(envKey, null);
        if (dotenvValue != null) return dotenvValue;
    }

    // 3. application.properties
    return properties.getProperty(key, defaultValue);
}
```

### MongoDBContainer connection string format

```
// getConnectionString() returns: "mongodb://localhost:<random_port>"
// This is a valid MongoDB connection URI — compatible with MongoClients.create()
// and AppConfig.getMongoUri() (which returns the "mongodb.uri" property value)
```

### PostgreSQLContainer API

```java
pgContainer.getJdbcUrl()    // "jdbc:postgresql://localhost:<port>/test"
pgContainer.getUsername()   // "test"
pgContainer.getPassword()   // "test"
pgContainer.getDatabaseName() // "test"
```

### Failsafe plugin snippet

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
  <configuration>
    <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
  </configuration>
  <executions>
    <execution>
      <id>integration-tests</id>
      <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| TC `@Rule` (per-method) | TC `@ClassRule` (per-class) | TC 1.x | 10-30s speed improvement per test class |
| `@RunWith(MockitoJUnitRunner)` for ITs | `@RunWith(SpringRunner)` + `@SpringBootTest` | Spring Boot 1.4 | Full context available for JPA tests |
| Embedded H2 for JPA tests | `PostgreSQLContainer` (real engine) | TC 1.0 | No H2-vs-Postgres dialect mismatch |
| TC `@Testcontainers` annotation | NOT available — JUnit 5 only | TC 1.x | Use `@ClassRule` for JUnit 4 |

**Deprecated/outdated:**
- `testcontainers:junit-4` module: Not needed in TC 1.19.8 — `GenericContainer extends FailureDetectingExternalResource` already handles `@ClassRule` natively without a separate JUnit 4 module [VERIFIED: jar inspection]

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Embedded H2 doesn't support PostgreSQL-specific column types for this project | Don't Hand-Roll | Low risk — TC PostgreSQL is the decided approach per CONTEXT.md; H2 is never considered |
| A2 | `@SpringBootTest` with `@RunWith(SpringRunner.class)` initializes Spring context before `@BeforeClass` | Pitfall 2 | If wrong, simpler `@BeforeClass` injection would work; `ApplicationContextInitializer` approach is still safe regardless |
| A3 | Redis connection failure in `application-test.properties` (`localhost:6379`) does not cause Spring context load failure | Pitfall 3 | If wrong, `UserRepositoryIT` needs `@MockBean RestaurantCacheService` — planner should add this as a noted contingency |

---

## Open Questions

1. **Does `@SpringBootTest` in `UserRepositoryIT` require a running Redis to start the context?**
   - What we know: `application-test.properties` sets `redis.host=localhost` with no Redis container. Spring's Lettuce Redis client typically connects lazily.
   - What's unclear: Whether `RestaurantCacheService` constructor eagerly establishes a connection.
   - Recommendation: Planner should note this as a contingency — if context fails, add `@MockBean RestaurantCacheService` to `UserRepositoryIT`.

2. **Should `UserRepositoryIT` also start a MongoDB container?**
   - What we know: `@SpringBootTest` wires the full application context including `RestaurantDAOImpl`, which calls `MongoClientFactory.getInstance()`.
   - What's unclear: Whether Spring Boot 2.6.15 auto-configuration eagerly creates the MongoDB connection.
   - Recommendation: Add both `mongoContainer` and `pgContainer` as `@ClassRule` fields in `UserRepositoryIT`, with system property injection for both. This is safe and avoids context load failures.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker daemon | Testcontainers | ✓ | 29.3.1 | — |
| `mongo:7.0` image | `RestaurantDAOIT` | ✓ | Pulled | — |
| `postgres:15-alpine` image | `UserRepositoryIT` | ✓ | Pulled | — |
| Java runtime | Maven compile + test | ✓ | 25.0.2 | — |
| Apache Maven | Build | ✓ | 3.8.7 | — |
| TC `testcontainers:1.19.8` JAR | Core TC runtime | ✓ | In local Maven repo | Maven Central |
| TC `mongodb:1.19.8` JAR | `MongoDBContainer` | ✓ | In local Maven repo | Maven Central |
| TC `postgresql:1.19.8` JAR | `PostgreSQLContainer` | ✓ | In local Maven repo | Maven Central |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** None.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 via `junit-vintage-engine` (unit tests, Surefire) + Failsafe (IT tests) |
| Config file | None — Failsafe + Surefire auto-detect via JUnit 5 platform |
| Quick run command | `mvn test` (unit tests only, < 60s) |
| Full suite command | `mvn verify` (unit + integration tests) |
| IT-only command | `mvn failsafe:integration-test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TEST-04 | MongoDB container starts and DAO queries return data | integration | `mvn failsafe:integration-test -Dit.test=RestaurantDAOIT` | ❌ Wave 0 |
| TEST-04 | PostgreSQL container starts and JPA repos save/find | integration | `mvn failsafe:integration-test -Dit.test=UserRepositoryIT` | ❌ Wave 0 |
| TEST-05 | All 14 existing `RestaurantDAOIntegrationTest` assertions pass via TC | integration | `mvn failsafe:integration-test -Dit.test=RestaurantDAOIT` | ❌ Wave 0 (rename + rewrite) |
| TEST-06 | `mvn failsafe:integration-test` on machine with no local MongoDB/PostgreSQL succeeds | smoke | `mvn failsafe:integration-test` | ❌ requires Wave 0 + Failsafe config |

### Sampling Rate

- **Per task commit:** `mvn test` (ensure no regressions to unit tests — stays < 60s)
- **Per wave merge:** `mvn failsafe:integration-test` (container smoke)
- **Phase gate:** `mvn verify` — full suite (unit + IT) green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/com/aflokkat/dao/RestaurantDAOIT.java` — covers TEST-04, TEST-05 (rename + rewrite from `RestaurantDAOIntegrationTest`)
- [ ] `src/test/java/com/aflokkat/repository/UserRepositoryIT.java` — covers TEST-04, TEST-06
- [ ] `pom.xml` — add Failsafe plugin + TC dependencies (covers TEST-06 infrastructure)
- [ ] `src/main/java/com/aflokkat/config/AppConfig.java` — add `System.getProperty()` tier 0 (covers TEST-04 injection point)
- [ ] Remove `@Ignore` from `RestaurantDAOIntegrationTest` AND rename to `RestaurantDAOIT` (or delete original and create new file)

---

## Security Domain

Phase 14 is test infrastructure only — no production code paths are added or modified except the `System.getProperty()` addition to `AppConfig`. That change does not weaken production security:
- Production deployments use `System.getenv()` (Docker/CI env vars) which still takes precedence IF no matching `System.getProperty()` exists
- `System.setProperty()` can only be called from within the JVM — not from external input
- No new endpoints, no authentication changes, no new network services

ASVS categories are not applicable to test infrastructure additions. No security review required for this phase.

---

## Sources

### Primary (HIGH confidence)

- Local Maven repo `~/.m2/repository/org/testcontainers/` — TC 1.19.8 artifacts verified present; `MongoDBContainer.getConnectionString()` and `PostgreSQLContainer.getJdbcUrl()/getUsername()/getPassword()` verified via `javap`
- `src/main/java/com/aflokkat/config/AppConfig.java` — direct source inspection of property lookup chain (lines 126-139); confirmed NO `System.getProperty()` check
- `src/main/java/com/aflokkat/config/MongoClientFactory.java` — static singleton pattern verified; `closeInstance()` API confirmed
- `src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java` — all 14 test method DAO calls verified; field name mappings confirmed
- `src/test/java/com/aflokkat/dao/RestaurantDAOIntegrationTest.java` — 14 test methods fully read; assertion thresholds documented
- `pom.xml` (current branch) — confirmed no Failsafe, no TC, Surefire argLine literal, Spring Boot 2.6.15
- `src/test/resources/application-test.properties` — confirmed `spring.datasource.url=jdbc:postgresql://localhost:5432/restaurantdb`

### Secondary (MEDIUM confidence)

- `.planning/phases/14-testcontainers-integration-tests/14-CONTEXT.md` — all 12 locked decisions
- `.planning/phases/12-maven-build-hardening/12-VERIFICATION.md` — Failsafe configuration pattern verified as working on phase-12 branch
- `git show gsd/phase-12-maven-build-hardening:pom.xml` — Failsafe + JaCoCo plugin config pattern confirmed

### Tertiary (LOW confidence)

- A3 (Redis lazy connection): based on Lettuce client default behavior — not verified in this session

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all TC JARs verified in local Maven repo, API methods verified via javap
- Architecture: HIGH — all patterns derived from direct source code analysis
- Pitfalls: HIGH — based on direct code inspection (AppConfig, MongoClientFactory); Pitfall 3 is MEDIUM (Redis lazy connection assumption)

**Research date:** 2026-04-12
**Valid until:** 2026-05-12 (TC 1.x is stable; Spring Boot 2.6.15 patch releases don't affect this)
