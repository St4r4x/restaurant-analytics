# Architecture Patterns: v3.0 Production Readiness

**Domain:** Spring Boot monolith — production-readiness integration
**Researched:** 2026-04-11
**Confidence:** HIGH (direct codebase inspection + verified against official docs)

---

## Existing Architecture Baseline

```
com.aflokkat/
├── config/          AppConfig, MongoClientFactory, RedisConfig, SecurityConfig,
│                    OpenApiConfig, MethodSecurityConfig
├── controller/      RestaurantController, InspectionController, AuthController,
│                    UserController, ViewController, AnalyticsController,
│                    AdminController, ReportController
├── service/         RestaurantService, AuthService
├── dao/             RestaurantDAO + Impl (MongoDB — raw aggregation pipelines)
├── repository/      UserRepository, BookmarkRepository (Spring Data JPA / PostgreSQL)
├── cache/           RestaurantCacheService (Redis, TTL 3600s)
├── sync/            NycOpenDataClient, SyncService
├── domain/          Restaurant, Address, Grade, User (MongoDB POJOs)
├── entity/          UserEntity, BookmarkEntity (JPA)
├── dto/             AuthRequest, JwtResponse, RegisterRequest, RefreshRequest, ...
├── aggregation/     AggregationCount, BoroughCuisineScore, CuisineScore
├── security/        JwtUtil, JwtAuthenticationFilter, JwtService, RateLimitFilter
└── util/            ValidationUtil, ResponseUtil
src/main/resources/
├── application.properties
├── simplelogger.properties          ← REPLACED by logback-spring.xml in v3.0
└── templates/                       ← 13 Thymeleaf templates + fragments/
src/test/java/com/aflokkat/
└── 27 test classes across dao/, controller/, service/, config/, security/, util/
```

**Key architectural facts from codebase inspection:**

- `MongoClientFactory` is a manual static singleton — calls `MongoClients.create(AppConfig.getMongoUri())` on first access. Any test that triggers this before Testcontainers starts will connect to the wrong host. This is the central Testcontainers integration problem.
- `AppConfig.getProperty()` checks: (1) `System.getenv()`, (2) `.env` file, (3) `application.properties`. It does NOT check `System.getProperty()`. Test infrastructure must either set env vars or patch this method.
- All controller tests use `standaloneSetup` + `@ExtendWith(MockitoExtension.class)` — no `@WebMvcTest`. This is a deliberate workaround for a Spring Boot 2.6 + Java 25 Byte Buddy crash.
- `RateLimitFilter` is `@Component @Order(1)` — auto-registered in the servlet filter chain, not added via `SecurityConfig`. Any new filter using `@Component` must use `@Order` to establish ordering relative to this.
- `SecurityConfig` has `anyRequest().permitAll()` intentionally — view routes rely on client-side IIFE guards because JWT lives in localStorage, not cookies.
- `simplelogger.properties` is currently dead configuration. Spring Boot's `spring-boot-starter-logging` activates Logback; SLF4J Simple Logger is not on the classpath. The file has no effect.
- Existing Surefire config sets `<argLine>-XX:+EnableDynamicAgentLoading</argLine>` — this will conflict with JaCoCo unless changed to `@{argLine}` (late-binding Maven property).

---

## Component Map: New vs Modified

### NEW Components to Create

| Component | Location | What It Is |
|-----------|----------|------------|
| `logback-spring.xml` | `src/main/resources/` | Replaces `simplelogger.properties`; structured JSON in prod profile, pattern in dev |
| `MdcLoggingFilter` | `src/main/java/com/aflokkat/filter/` | `OncePerRequestFilter` that sets `requestId` and `userId` in MDC per request |
| `WebConfig` | `src/main/java/com/aflokkat/config/` | `WebMvcConfigurer` implementing CORS via `addCorsMappings()` |
| `AbstractContainerBase` | `src/test/java/com/aflokkat/` | Abstract base class with static Testcontainers singleton (MongoDB + Postgres + Redis) |
| `application-test.properties` | `src/test/resources/` | Test-scope property overrides pointing at Testcontainers-assigned ports |
| `.github/workflows/ci.yml` | `.github/workflows/` | Main CI pipeline: build, unit tests, integration tests, E2E, Docker build |
| `e2e/` | `src/test/java/com/aflokkat/e2e/` | Playwright E2E tests (Java), run via Failsafe `*E2ETest.java` naming |
| `design-system.css` | `src/main/resources/static/css/` | Single CSS file of custom properties (color, spacing, typography tokens) |

### MODIFIED Existing Components

| Component | What Changes |
|-----------|-------------|
| `pom.xml` | Add JaCoCo plugin, Testcontainers BOM + modules, logstash-logback-encoder, Playwright, Failsafe plugin; change Surefire `argLine` to `@{argLine}` |
| `SecurityConfig` | Add `.cors(withDefaults())` to `filterChain` bean |
| `AppConfig` | Add `System.getProperty()` as step 0 in `getProperty()` lookup chain (enables test override without env var injection) |
| `application.properties` | Move `SPRING_DATASOURCE_PASSWORD` comment to document env var name clearly |
| `docker-compose.yml` | Add `mem_limit`, `cpus` resource limits; add `JWT_SECRET` env var injection; add production-safe JVM flags |
| `Dockerfile` | Add JVM flags (`-XX:MaxRAMPercentage=75`, `-Djava.security.egd=file:/dev/./urandom`), add non-root user |
| All 13 Thymeleaf templates | Replace inline `<style>` blocks with `<link rel="stylesheet" href="/css/design-system.css">`; no controller or JS logic changes |
| `fragments/navbar.html` | Style-only update; included by all pages so one change propagates everywhere |

---

## Feature Integration Details

### 1. GitHub Actions CI Pipeline

**Location:** `.github/workflows/ci.yml`

**Four-job pipeline (recommended single file):**

```
Job 1: build
  → mvn package -DskipTests
  → Cache ~/.m2 with setup-java (cache: maven), key on pom.xml hash
  → Upload JAR as artifact for downstream jobs

Job 2: unit-tests  (needs: build)
  → mvn test
  → No external services — all 27 existing tests use Mockito standaloneSetup
  → Upload JaCoCo HTML report as artifact

Job 3: integration  (needs: build)
  → mvn verify -Pfailsafe  (or mvn failsafe:integration-test failsafe:verify)
  → Testcontainers starts its own containers — no services: block needed
  → Needs Docker available on runner (GitHub ubuntu-latest has Docker installed)

Job 4: e2e  (needs: integration)
  → docker compose up -d
  → Wait for health: curl retry loop against /api/restaurants/health
  → E2E_BASE_URL=http://localhost:8080 mvn test -pl . -Dtest="*E2ETest"
  → docker compose down

Job 5: docker-build  (needs: unit-tests, integration; only on push to main or tag)
  → docker build + push to registry
  → Uses ${{ secrets.REGISTRY_TOKEN }}
```

**Integration points with existing architecture:**
- `JWT_SECRET` must be a GitHub Actions secret (`${{ secrets.JWT_SECRET }}`). `AppConfig.getJwtSecret()` reads `JWT_SECRET` env var first — no code change needed.
- `CONTROLLER_SIGNUP_CODE` should also be a secret in CI for the E2E job that tests controller registration.
- Unit test job needs no secrets — `SecurityConfigTest` and auth tests construct `AuthService` directly with test values.
- The Testcontainers integration job does NOT need a running MongoDB/Redis/Postgres service — containers are spun up by the test JVM itself via Docker.

---

### 2. Testcontainers Integration

**Maven additions to `pom.xml`:**

```xml
<!-- In <dependencyManagement>/<dependencies> -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-bom</artifactId>
    <version>2.0.4</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- In <dependencies>, test scope -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mongodb</artifactId>  <!-- artifact name changed in 2.x: testcontainers-mongodb -->
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<!-- Redis: no dedicated module in Testcontainers — use GenericContainer("redis:7-alpine") -->
```

**The MongoClientFactory singleton problem (CRITICAL):**

`MongoClientFactory` connects to `AppConfig.getMongoUri()` at first class load via its static singleton. If any class that calls `MongoClientFactory.getInstance()` is loaded before `AbstractContainerBase` sets the container URI, the singleton binds permanently to the wrong address.

**Two-part fix:**

Part 1 — Patch `AppConfig.getProperty()` to check `System.getProperty()` as step 0:
```java
private static String getProperty(String key, String defaultValue) {
    // 0. JVM system properties (set by Testcontainers base class in tests)
    String sysPropKey = key.replace(".", "_").toUpperCase();
    String sysPropValue = System.getProperty(sysPropKey);
    if (sysPropValue != null) return sysPropValue;

    // 1. System environment variable (Docker, CI...)
    String envValue = System.getenv(sysPropKey);
    if (envValue != null) return envValue;

    // ... rest of existing logic
}
```

This change is production-safe: `System.getProperty()` is never set in prod Docker containers.

Part 2 — `AbstractContainerBase` sets system properties before any DAO is instantiated:
```java
// src/test/java/com/aflokkat/AbstractContainerBase.java
public abstract class AbstractContainerBase {

    private static final MongoDBContainer MONGO =
        new MongoDBContainer("mongo:7.0");
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:15");
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static {
        MONGO.start();
        POSTGRES.start();
        REDIS.start();

        System.setProperty("MONGODB_URI", MONGO.getConnectionString());
        System.setProperty("MONGODB_DATABASE", "newyork");
        System.setProperty("SPRING_DATASOURCE_URL", POSTGRES.getJdbcUrl());
        System.setProperty("SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername());
        System.setProperty("SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword());
        System.setProperty("REDIS_HOST", REDIS.getHost());
        System.setProperty("REDIS_PORT", String.valueOf(REDIS.getFirstMappedPort()));
    }
}
```

**JUnit 4 compatibility:**

`@DynamicPropertySource` requires `@SpringBootTest` context (JUnit 5 + SpringExtension). The existing `RestaurantDAOIntegrationTest` does not start a Spring context — it instantiates `RestaurantDAOImpl` directly. The system property approach above is the correct pattern for this test class. No `@ClassRule` needed when using the abstract base class singleton pattern.

```java
// Updated RestaurantDAOIntegrationTest
public class RestaurantDAOIntegrationTest extends AbstractContainerBase {
    private RestaurantDAO restaurantDAO;

    @Before
    public void setup() {
        restaurantDAO = new RestaurantDAOImpl();  // will use container URI via AppConfig
    }
    // ... existing test methods unchanged
}
```

**Unit test isolation:**

Rename integration tests to `*ContainerIT.java` (e.g. `RestaurantDAOContainerIT.java`) and run them exclusively via `maven-failsafe-plugin`. Existing unit tests run via Surefire and never touch `AbstractContainerBase`.

```xml
<!-- pom.xml — Failsafe plugin -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

### 3. JaCoCo Configuration

**CRITICAL — Surefire argLine must be changed first:**

Current `pom.xml` Surefire config:
```xml
<argLine>-XX:+EnableDynamicAgentLoading</argLine>
```

JaCoCo's `prepare-agent` goal injects the JaCoCo agent by appending to the Maven property `${argLine}`. If Surefire hardcodes `argLine`, the JaCoCo agent never attaches and coverage is recorded as zero. The check goal then sees 0% and fails the build.

Change to late-binding Maven property syntax:
```xml
<argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
```

`@{argLine}` is resolved at execution time after `prepare-agent` has set the property. `${argLine}` is resolved at parse time (before JaCoCo runs) and evaluates to empty string.

**JaCoCo plugin addition:**

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.60</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
                <excludes>
                    <!-- DTOs and POJOs: getters/setters not meaningful to cover -->
                    <exclude>com/aflokkat/dto/**</exclude>
                    <exclude>com/aflokkat/domain/**</exclude>
                    <exclude>com/aflokkat/aggregation/**</exclude>
                    <exclude>com/aflokkat/Application.class</exclude>
                </excludes>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Report location:** `target/site/jacoco/index.html`

**In CI:** Upload report with `actions/upload-artifact@v4`, path `target/site/jacoco/`.

---

### 4. E2E Tests (Playwright — Java)

**Rationale for Java (not Node.js):** Keeps the entire test suite in one language and one build tool. Avoids adding a `package.json` / Node runtime to CI. Java Playwright (`com.microsoft.playwright:playwright`) is fully supported and actively maintained.

**Maven dependency:**
```xml
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.50.0</version>
    <scope>test</scope>
</dependency>
```

**Project structure:**
```
src/test/java/com/aflokkat/e2e/
├── BaseE2ETest.java          Playwright lifecycle, baseUrl from E2E_BASE_URL env
├── LoginE2ETest.java         Login flow: happy path, bad credentials, redirect
├── SearchE2ETest.java        Restaurant search: results display, empty query
├── DashboardE2ETest.java     Controller dashboard: requires auth
└── AnalyticsE2ETest.java     Analytics page: KPI cards render
```

E2E test classes are named `*E2ETest.java`. Failsafe plugin picks them up via pattern:
```xml
<includes>
    <include>**/*IT.java</include>
    <include>**/*E2ETest.java</include>
</includes>
```

**BaseE2ETest (JUnit 4 compatible):**
```java
public abstract class BaseE2ETest {
    protected static Playwright playwright;
    protected static Browser browser;
    protected static final String BASE_URL =
        System.getenv().getOrDefault("E2E_BASE_URL", "http://localhost:8080");

    @BeforeClass
    public static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterClass
    public static void closeBrowser() {
        if (playwright != null) playwright.close();
    }
}
```

**CI requirement — install browser binaries before test run:**
```yaml
- name: Install Playwright browsers
  run: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI \
       -D exec.args="install --with-deps chromium"
```

**CI E2E job flow:**
1. Start `docker compose up -d`
2. Poll `http://localhost:8080/api/restaurants/health` with retries until 200
3. Run `E2E_BASE_URL=http://localhost:8080 mvn failsafe:integration-test failsafe:verify`
4. `docker compose down`

The app must have data to run meaningful E2E tests. On first startup it triggers a sync from NYC Open Data API; configure `nyc.api.max_records=200` (env var `NYC_API_MAX_RECORDS=200`) in the E2E docker compose environment to limit sync time.

---

### 5. Structured Logging (Logback + MDC)

**Replace `simplelogger.properties`:**

`simplelogger.properties` is non-functional — Spring Boot's `spring-boot-starter-logging` activates Logback (which is already on the classpath). SLF4J Simple Logger is not loaded. All effective logging configuration goes in `logback-spring.xml`.

**Dependency — logstash-logback-encoder:**
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

Version compatibility matrix (verified against official README):
- 7.4: Java 8+, Logback 1.2+, Jackson 2.x — compatible with Spring Boot 2.6.15
- 8.x: requires Logback 1.3+ (Java 17 minimum)
- 9.x: requires Java 17, Jackson 3.x

**`src/main/resources/logback-spring.xml`:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Development: human-readable, shows requestId from MDC -->
    <springProfile name="!prod">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} [%X{requestId}] - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO"><appender-ref ref="CONSOLE"/></root>
        <logger name="com.aflokkat" level="DEBUG"/>
        <logger name="org.springframework" level="WARN"/>
        <logger name="org.mongodb" level="INFO"/>
    </springProfile>

    <!-- Production: JSON structured output, all MDC fields included automatically -->
    <springProfile name="prod">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <customFields>{"app":"restaurant-analytics","version":"3.0"}</customFields>
            </encoder>
        </appender>
        <root level="INFO"><appender-ref ref="JSON"/></root>
    </springProfile>
</configuration>
```

**New `MdcLoggingFilter`:**

Location: `src/main/java/com/aflokkat/filter/MdcLoggingFilter.java`

```java
@Component
@Order(2)  // After RateLimitFilter (@Order(1)), before Spring Security
public class MdcLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
        MDC.put("method", req.getMethod());
        MDC.put("uri", req.getRequestURI());
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.clear();  // MANDATORY — MDC is ThreadLocal; must clear to prevent leaks
        }
    }
}
```

`@Order(2)` ensures this fires after `RateLimitFilter` (`@Order(1)`) but before the Spring Security filter chain (`@Order(Integer.MIN_VALUE)` by default). The `requestId` is present in all downstream log lines for the request lifetime, including logs emitted inside service and DAO layers.

**Optional userId enrichment:** `JwtAuthenticationFilter` runs after `MdcLoggingFilter`. After it parses the JWT and sets `SecurityContextHolder`, add `MDC.put("userId", authentication.getName())` to enrich post-auth log lines with the authenticated username.

---

### 6. Security Hardening

**6a. CORS — new `WebConfig` bean**

Location: `src/main/java/com/aflokkat/config/WebConfig.java`

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("http://localhost:*", "https://*.example.com")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type", "Accept")
            .exposedHeaders("Authorization")
            .allowCredentials(false)  // JWT in Authorization header, not cookies
            .maxAge(3600);
    }
}
```

**`SecurityConfig` change — add `.cors(withDefaults())` to `filterChain`:**

```java
http
    .cors(withDefaults())  // Delegates to WebMvcConfigurer CORS config
    .csrf().disable()
    ...
```

Without `.cors(withDefaults())`, Spring Security intercepts OPTIONS preflight requests before CORS headers are applied and returns 401/403 on cross-origin requests. Both the `WebConfig` bean (defines the CORS policy) and `.cors(withDefaults())` (tells Spring Security to apply it) are required. Source: Spring Security CORS reference documentation (HIGH confidence).

**6b. Security headers — add to `SecurityConfig.filterChain()`:**

```java
http.headers()
    .frameOptions().deny()
    .contentTypeOptions().and()
    .xssProtection();
```

No new class needed — configuration-only addition inside the existing `filterChain` bean.

**6c. Rate limiting — already implemented**

`RateLimitFilter` covers `/api/auth/**` with per-IP token bucket (Bucket4j 7.6.1). No changes needed for v3.0 scope. The documented limitation (unbounded `ConcurrentHashMap`) is acceptable for academic scope.

**6d. Input validation — already implemented**

`ValidationUtil` exists. Ensure it is consistently called by all controllers that accept user-supplied input. No new class needed.

---

### 7. UI Redesign — Design System Approach

**Location:** `src/main/resources/static/css/design-system.css`

**Integration with existing Thymeleaf templates:**

All 13 templates currently have inline `<style>` blocks with redundant CSS and inconsistent variable naming. The redesign approach:

1. Create `design-system.css` as the single source of all CSS custom properties:
   - `--color-bg`, `--color-surface`, `--color-accent`, `--color-text-*` (dark neutral palette)
   - `--font-family-sans`, `--font-size-*`, `--font-weight-*`
   - `--space-*` (spacing scale: 4px, 8px, 16px, 24px, 32px, 48px)
   - `--radius-*`, `--shadow-*`
   - Component classes: `.card`, `.btn`, `.btn-primary`, `.badge-grade-A/B/C/Z`, `.table-data`

2. Replace inline `<style>` blocks in each template with:
   ```html
   <link rel="stylesheet" href="/css/design-system.css">
   ```

3. `fragments/navbar.html` is included via Thymeleaf `th:replace` in all pages. Updating the navbar fragment once propagates the style to all 13 templates.

4. No changes to Thymeleaf model attributes, `th:*` expressions, Spring controller logic, or JS `fetch()` calls. The redesign is purely presentational.

**Static file serving:** Spring Boot auto-serves `src/main/resources/static/**` at the root path (`/css/design-system.css` → `src/main/resources/static/css/design-system.css`). No additional `WebMvcConfigurer` resource handler configuration needed. `WebConfig.addCorsMappings()` configures only `/api/**` so static files are unaffected.

---

## Recommended Build Order

Dependencies drive this order. Each phase must complete before the next to avoid broken intermediate states.

```
Phase 1: Logging Infrastructure
  Deliverables: logback-spring.xml (replace simplelogger.properties),
                MdcLoggingFilter, AppConfig @Order annotation
  Rationale: Every subsequent phase benefits from structured logs immediately.
             No external dependencies. Lowest risk — replaces non-functional config.
             Establishes the requestId pattern used in all later log output.

Phase 2: JaCoCo + Maven build hardening
  Deliverables: pom.xml — JaCoCo plugin, Surefire argLine fix to @{argLine},
                Failsafe plugin, version set to 3.0-SNAPSHOT
  Rationale: argLine fix is a blocker for Testcontainers (Failsafe also uses argLine).
             Must establish coverage baseline against existing 27 tests before adding
             new test infrastructure. Zero code changes — pure Maven config.

Phase 3: Testcontainers + AppConfig patch
  Deliverables: AbstractContainerBase.java, AppConfig.getProperty() system property
                fallback, src/test/resources/application-test.properties,
                rename RestaurantDAOIntegrationTest → RestaurantDAOContainerIT
  Rationale: Makes the existing integration test runnable in CI without a live DB.
             JaCoCo argLine fix (Phase 2) must exist so Failsafe records coverage.
             This unblocks the GitHub Actions integration job.

Phase 4: GitHub Actions CI Pipeline
  Deliverables: .github/workflows/ci.yml
  Rationale: Depends on Testcontainers (Phase 3) so the integration job can run.
             Depends on JaCoCo (Phase 2) so the report artifact is generated.
             Once CI is live, all subsequent phases are automatically validated.

Phase 5: Security Hardening (CORS + headers)
  Deliverables: WebConfig.java, SecurityConfig modifications
  Rationale: No dependencies on test infrastructure.
             Placing it after CI (Phase 4) means the CORS change is validated by
             the automated pipeline immediately, catching misconfigurations early.

Phase 6: E2E Tests (Playwright)
  Deliverables: src/test/java/com/aflokkat/e2e/, CI e2e job in ci.yml
  Rationale: Depends on CI pipeline being established (Phase 4).
             E2E tests run against a fully running app — all functional features
             must be stable first. Logically last in the test pyramid.

Phase 7: UI Redesign
  Deliverables: design-system.css, 13 template style updates
  Rationale: Purely presentational, zero backend risk. No other phase depends on it.
             Doing it after all functional phases prevents rework if template
             structure changes earlier.
```

---

## Component Boundaries Summary

| Layer | Existing | New/Modified for v3.0 |
|-------|----------|-----------------------|
| Logging | `simplelogger.properties` (dead) | `logback-spring.xml` (NEW), `MdcLoggingFilter` (NEW), delete `simplelogger.properties` |
| Testing — unit | 27 test classes, Surefire | Surefire `@{argLine}` fix, JaCoCo `prepare-agent` |
| Testing — integration | `RestaurantDAOIntegrationTest` (live DB required) | `AbstractContainerBase` (NEW), `AppConfig` patched, Failsafe plugin, rename to `*ContainerIT.java` |
| Testing — E2E | None | `src/test/java/com/aflokkat/e2e/` (NEW), Playwright dependency, CI e2e job |
| Security | `SecurityConfig`, `RateLimitFilter`, `ValidationUtil` | `WebConfig` (NEW), `SecurityConfig` (MODIFIED — `.cors()` + headers) |
| CI/CD | None | `.github/workflows/ci.yml` (NEW) |
| Configuration | `AppConfig`, `application.properties` | `AppConfig` (MODIFIED — `System.getProperty()` fallback), `application-test.properties` (NEW) |
| UI | 13 templates, `fragments/` | `design-system.css` (NEW), all 13 templates (style-only edits) |

---

## Data Flow: Unchanged

All existing data flows remain identical in v3.0. No new data paths are introduced.

```
NYC Open Data API → NycOpenDataClient → SyncService → MongoDB (newyork.restaurants)
MongoDB → RestaurantDAO → RestaurantService → REST controllers → JSON responses
Hot data → RestaurantCacheService (Redis, TTL 3600s)
Users / bookmarks / reports → PostgreSQL via Spring Data JPA
```

`MdcLoggingFilter` sits in the servlet filter chain, emitting log events per request. It does not modify request or response data.

---

## Critical Integration Pitfalls

### Pitfall 1: JaCoCo + Surefire argLine Overwrite
**What breaks:** Static `<argLine>-XX:+EnableDynamicAgentLoading</argLine>` in Surefire is overwritten by JaCoCo's `prepare-agent`. Coverage records as 0%. The `check` goal sees 0% and fails the build on every `mvn verify` run.
**Fix:** Change to `@{argLine} -XX:+EnableDynamicAgentLoading`. This is Phase 2 step 1.
**Confidence:** HIGH — verified from JaCoCo Maven plugin docs + Maven Surefire late-binding property documentation.

### Pitfall 2: MongoClientFactory Static Singleton Initialization Order
**What breaks:** If any test class that accesses `RestaurantDAO` loads before `AbstractContainerBase` fires its static initializer, `MongoClientFactory` permanently binds to `mongodb://mongodb:27017` (the Docker URI, not the Testcontainers port).
**Fix:** Both parts required — `AppConfig.getProperty()` must check `System.getProperty()` first, AND `AbstractContainerBase` static block must set the system property before any test class touches `MongoClientFactory`.
**Confidence:** HIGH — verified by reading `MongoClientFactory.java` and `AppConfig.java` source.

### Pitfall 3: simplelogger.properties Is Dead Configuration
**What this means for v3.0:** Any logging changes made to `simplelogger.properties` will have no effect. The file should be deleted and replaced with `logback-spring.xml` to avoid confusion.
**Confidence:** HIGH — Spring Boot `spring-boot-starter-logging` BOM selects Logback and excludes the SLF4J simple logger.

### Pitfall 4: CORS Requires Both `WebConfig` and `.cors(withDefaults())` in SecurityConfig
**What breaks:** Adding `WebConfig implements WebMvcConfigurer` alone is insufficient. Without `.cors(withDefaults())` in `SecurityConfig.filterChain()`, Spring Security's filter chain intercepts OPTIONS preflight requests before CORS headers are applied, returning 401 to all cross-origin preflight requests.
**Fix:** Both beans are required — `WebConfig` defines the policy, `SecurityConfig` applies it at the Security filter layer.
**Confidence:** HIGH — verified against Spring Security CORS reference documentation.

### Pitfall 5: Testcontainers 2.x Module Artifact Name
**What breaks:** In Testcontainers 1.x the MongoDB module artifact was `mongodb`. In 2.x (current: 2.0.4) it is `mongodb` as part of the `testcontainers-bom`. Verify the exact artifact coordinates when resolving the BOM — artifact naming changed in the 2.x series.
**Mitigation:** Always declare the BOM in `<dependencyManagement>` and use version-less module declarations in `<dependencies>`.
**Confidence:** MEDIUM — based on Testcontainers 2.x official documentation.

---

## Sources

| Source | Topics Covered | Confidence |
|--------|---------------|------------|
| Direct codebase inspection: `pom.xml`, `SecurityConfig.java`, `AppConfig.java`, `MongoClientFactory.java`, `RateLimitFilter.java`, `simplelogger.properties`, all 27 test files | Existing architecture, integration points, argLine issue, MongoClientFactory singleton | HIGH |
| `java.testcontainers.org` — JUnit 4 integration, MongoDB module, version 2.0.4, singleton pattern | Testcontainers JUnit 4 `@ClassRule`/abstract base class pattern | HIGH |
| Spring Framework 5.3 reference — `@DynamicPropertySource` | JUnit 4 + Spring context compatibility | HIGH |
| Spring Security reference — CORS with `WebMvcConfigurer` | `.cors(withDefaults())` requirement | HIGH |
| logstash-logback-encoder GitHub README | Version compatibility matrix (7.x for Spring Boot 2.6) | HIGH |
| JaCoCo Maven plugin documentation (`jacoco.org`) | `prepare-agent`, argLine interaction, `check` configuration | HIGH |
| Playwright Java documentation — `@UsePlaywright`, `BrowserContext` | E2E test patterns, browser lifecycle | MEDIUM |

---

*Research date: 2026-04-11 — v3.0 Production Readiness milestone*
