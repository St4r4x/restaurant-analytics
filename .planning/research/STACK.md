# Technology Stack — v3.0 Production Readiness

**Project:** Restaurant Hygiene Control App — v3.0 Production Readiness
**Researched:** 2026-04-11
**Base constraint:** Spring Boot 2.6.15 / Java 11 — no framework upgrade
**Scope:** New additions only. Existing stack is fixed and not re-researched.

---

## Existing Stack (Locked — Do Not Change)

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | Spring Boot | 2.6.15 |
| Language | Java | 11 |
| Security | Spring Security | 5.6.x (BOM) |
| Auth tokens | jjwt | 0.11.5 |
| ORM | Spring Data JPA / Hibernate | 5.6.15.Final (BOM) |
| RDBMS | PostgreSQL | 15 |
| Document DB | mongodb-driver-sync | BOM managed |
| Cache | Redis 7 + Spring Data Redis | BOM managed |
| Templates | Thymeleaf | BOM managed |
| API docs | springdoc-openapi-ui | 1.8.0 |
| Validation | spring-boot-starter-validation | BOM managed (Hibernate Validator 6.2.5) |
| Rate limiting | bucket4j-core | 7.6.1 |
| Testing | JUnit 4.13.2 + JUnit Jupiter 5.8.2 | Mixed (JUnit Vintage Engine) |
| Mocking | Mockito | 5.17.0 (overrides BOM 4.0.0) |
| Build | Maven | 3.8+ |

**BOM-confirmed versions (Spring Boot 2.6.15):**
- `logback` 1.2.12, `slf4j` 1.7.36, `junit-jupiter` 5.8.2, `mockito` 4.0.0, `hibernate-validator` 6.2.5.Final
- Testcontainers is NOT in the Spring Boot 2.6.15 BOM — must be declared explicitly with version

---

## Recommended Additions for v3.0

### 1. Testcontainers — Integration Tests (MongoDB + PostgreSQL in CI)

**Verdict: Use Testcontainers 1.19.8. Four new test-scope dependencies.**

Testcontainers 1.x compiles to Java 8 bytecode and is fully compatible with Java 11. Version 1.19.8 is the last 1.x release before 2.0.0 (which dropped JUnit 4 support and requires Java 11+). Since the project uses a JUnit 4/5 mixed stack with JUnit Vintage Engine, 1.19.8 is the correct choice — it supports both JUnit 4 and JUnit 5.

**Why not 2.x:** Testcontainers 2.0.0 removed JUnit 4 support entirely. Migrating all existing JUnit 4 tests to JUnit 5 is out of scope for a production-readiness milestone.

```xml
<properties>
  <testcontainers.version>1.19.8</testcontainers.version>
</properties>

<!-- Add to <dependencyManagement> to unify all module versions -->
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>testcontainers-bom</artifactId>
      <version>${testcontainers.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<!-- Then add modules with no version (managed by BOM above) -->
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>testcontainers</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>mongodb</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
```

**Integration pattern for existing DAO tests:**

```java
@Testcontainers
class RestaurantDAOIntegrationTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0");

    @BeforeAll
    static void setup() {
        // Override the MongoClientFactory connection URI at test startup
        System.setProperty("mongodb.uri", mongo.getReplicaSetUrl());
    }
}
```

The existing `RestaurantDAOIntegrationTest` currently requires a live MongoDB on `localhost:27017`. Replace that dependency with a `MongoDBContainer` started by Testcontainers — no live MongoDB needed in CI.

**Confidence:** HIGH — Maven coordinates verified on Maven Central (1.19.8 exists for `testcontainers`, `mongodb`, `postgresql`, `junit-jupiter` modules). Java 11 compatibility confirmed (1.x compiles to Java 8 bytecode).

---

### 2. JaCoCo — Code Coverage with Minimum Threshold

**Verdict: Use JaCoCo Maven Plugin 0.8.12. Zero new runtime dependencies — Maven plugin only.**

JaCoCo 0.8.12 requires Java 8+ for the test executor. Fully compatible with Java 11 and Spring Boot 2.6.15. It is NOT in the Spring Boot BOM — declare the version explicitly.

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <!-- Instrument JVM with coverage agent before tests run -->
    <execution>
      <id>jacoco-prepare-agent</id>
      <goals><goal>prepare-agent</goal></goals>
    </execution>
    <!-- Generate HTML/XML report after tests complete -->
    <execution>
      <id>jacoco-report</id>
      <phase>test</phase>
      <goals><goal>report</goal></goals>
    </execution>
    <!-- Fail build if coverage is below threshold -->
    <execution>
      <id>jacoco-check</id>
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
              <limit>
                <counter>BRANCH</counter>
                <value>COVEREDRATIO</value>
                <minimum>0.50</minimum>
              </limit>
            </limits>
          </rule>
        </rules>
        <excludes>
          <!-- Exclude generated/boilerplate classes from threshold -->
          <exclude>com/aflokkat/Application.class</exclude>
          <exclude>com/aflokkat/dto/**</exclude>
          <exclude>com/aflokkat/aggregation/**</exclude>
          <exclude>com/aflokkat/domain/**</exclude>
          <exclude>com/aflokkat/entity/**</exclude>
        </excludes>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**Important:** The `jacoco:prepare-agent` goal adds a `-javaagent` JVM argument. This conflicts with the current Surefire configuration that manually sets `<argLine>-XX:+EnableDynamicAgentLoading</argLine>`. Fix by using the `@{argLine}` placeholder to chain both:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <!-- @{argLine} is populated by jacoco:prepare-agent at runtime -->
    <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
  </configuration>
</plugin>
```

**Report output:** `target/site/jacoco/index.html` (HTML), `target/site/jacoco/jacoco.xml` (for CI parsing).

**Threshold recommendation:** Start at 60% line / 50% branch. The existing codebase has service/DAO classes under partial test — a realistic starting threshold that can be raised incrementally. Do not set 80%+ on day one: it will fail immediately on existing gaps in coverage.

**Confidence:** HIGH — JaCoCo 0.8.12 verified on Maven Central. `check` goal behavior and threshold configuration verified from official JaCoCo documentation.

---

### 3. Playwright for Java — E2E Browser Tests

**Verdict: Use Playwright for Java 1.49.0. One new test-scope dependency.**

Playwright for Java supports Java 8+. The current stable version is 1.49.0 (MEDIUM confidence — verified on Maven Central as of research date; note the library releases frequently: 1.59.0 is the absolute latest as of 2026-04-11 but 1.49.0 is stable and well-documented for this use case).

**Why Playwright over Selenium:** Playwright is the current standard for E2E browser testing. It has a simpler async/sync API, built-in auto-waiting (eliminates flaky `Thread.sleep()` calls), and the browser installation is a single Maven command. Selenium WebDriver 4.x requires managing browser driver binaries separately (or Selenium Grid), which is heavier CI setup. For a portfolio project, Playwright signals modern practices.

**Why not Selenium:** Selenium is still valid but is the legacy choice in 2025. Playwright has better CI/CD integration and cleaner Java API.

```xml
<dependency>
  <groupId>com.microsoft.playwright</groupId>
  <artifactId>playwright</artifactId>
  <version>1.49.0</version>
  <scope>test</scope>
</dependency>
```

**Browser installation (one-time, during CI setup):**
```bash
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI \
  -D exec.args="install --with-deps chromium"
```

Installing only `chromium` (not all three browsers) reduces CI time significantly. Playwright downloads ~170MB of Chromium vs ~500MB for all browsers.

**GitHub Actions setup pattern:**
```yaml
- name: Install Playwright browsers
  run: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install --with-deps chromium"
```

**Alternative — Docker image:** `mcr.microsoft.com/playwright/java:v1.49.0-noble` pre-installs browsers. Use in GitHub Actions as a container if browser install time is unacceptable, but it adds Docker-in-Docker complexity.

**E2E test structure:** Place E2E tests in `src/test/java/.../e2e/` and bind them to a Maven failsafe plugin execution (`integration-test` phase), not the `surefire` unit test phase. This allows `mvn test` to run unit/integration tests and `mvn verify` to also run E2E tests.

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
  <configuration>
    <!-- E2E tests must have IT suffix or be in e2e package -->
    <includes>
      <include>**/*IT.java</include>
      <include>**/e2e/**/*Test.java</include>
    </includes>
    <!-- Pass app URL via system property; CI sets it to deployed container URL -->
    <systemPropertyVariables>
      <app.base.url>${app.base.url}</app.base.url>
    </systemPropertyVariables>
  </configuration>
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

**Confidence:** MEDIUM — Playwright for Java verified on Maven Central and official docs. Version pinned to 1.49.0 for stability; 1.59.0 is latest but pinning a tested version is safer for a new integration. Java 8+ requirement confirmed from official docs.

---

### 4. Structured Logging — logstash-logback-encoder

**Verdict: Use logstash-logback-encoder 7.3. One new dependency.**

Spring Boot 2.6.15 ships Logback 1.2.12. The built-in `JsonEncoder` (`ch.qos.logback.classic.encoder.JsonEncoder`) was added in Logback 1.3.8 — it is NOT available in Logback 1.2.x. Upgrading Logback within Spring Boot 2.x is risky (Logback 1.3+ requires SLF4J 2.x, which conflicts with Spring Boot 2.x's SLF4J 1.7.36).

logstash-logback-encoder 7.3 is the last version that explicitly supports Logback 1.2.x (version 7.4 dropped Logback 1.2 support per its release notes). It provides `LogstashEncoder` which produces JSON log lines compatible with ELK stack, Datadog, and any log aggregator.

**Why not upgrade Logback:** Logback 1.3.x requires SLF4J 2.x. Spring Boot 2.6.15 BOM manages SLF4J 1.7.36. Overriding both introduces version incompatibilities across Spring's logging infrastructure. Not worth the risk.

```xml
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>7.3</version>
</dependency>
```

**logback-spring.xml configuration:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="CONSOLE_JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <!-- Add application name and version to every log line -->
      <customFields>{"app":"restaurant-hygiene","version":"3.0"}</customFields>
    </encoder>
  </appender>

  <appender name="CONSOLE_PLAIN" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- Use JSON in production profile, plain text in dev -->
  <springProfile name="prod">
    <root level="INFO">
      <appender-ref ref="CONSOLE_JSON"/>
    </root>
  </springProfile>
  <springProfile name="!prod">
    <root level="INFO">
      <appender-ref ref="CONSOLE_PLAIN"/>
    </root>
  </springProfile>
</configuration>
```

**Structured logging usage in code:**
```java
// Add MDC fields to every request via a filter
MDC.put("userId", userId);
MDC.put("requestId", UUID.randomUUID().toString());
log.info("Restaurant search", kv("borough", borough), kv("cuisine", cuisine));
```

**Confidence:** HIGH — logstash-logback-encoder 7.3 compatibility with Logback 1.2.x confirmed from release notes. Version 7.4's removal of Logback 1.2 support confirmed from official release notes. Maven coordinates verified on Maven Central.

---

### 5. GitHub Actions CI/CD Pipeline

**Verdict: No new Maven dependencies. GitHub Actions YAML only.**

**Recommended action versions (verified April 2026):**

| Action | Version | Purpose |
|--------|---------|---------|
| `actions/checkout` | `v5` | Checkout source code |
| `actions/setup-java` | `v4` | Install Temurin JDK 11 with Maven cache |
| `actions/cache` | `v5` | Explicit Maven cache if needed beyond setup-java |
| `docker/login-action` | `v4` | Authenticate to Docker Hub / GHCR |
| `docker/metadata-action` | `v6` | Generate image tags from git ref |
| `docker/build-push-action` | `v7` | Build and push multi-platform image |

**Recommended pipeline structure:**

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [develop, main]
  pull_request:
    branches: [develop, main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5

      - name: Set up JDK 11
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'
          cache: maven

      - name: Run unit and integration tests
        run: mvn -B verify -DskipE2E

      - name: Upload JaCoCo coverage report
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: target/site/jacoco/

  e2e:
    runs-on: ubuntu-latest
    needs: test
    steps:
      - uses: actions/checkout@v5

      - name: Set up JDK 11
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'
          cache: maven

      - name: Start application stack
        run: docker compose up -d --wait

      - name: Install Playwright browsers
        run: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install --with-deps chromium"

      - name: Run E2E tests
        run: mvn -B failsafe:integration-test failsafe:verify -Dapp.base.url=http://localhost:8080

      - name: Stop application stack
        if: always()
        run: docker compose down

  docker:
    runs-on: ubuntu-latest
    needs: [test, e2e]
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v5

      - name: Docker metadata
        id: meta
        uses: docker/metadata-action@v6
        with:
          images: ghcr.io/${{ github.repository }}
          tags: |
            type=ref,event=branch
            type=semver,pattern={{version}}

      - name: Login to GHCR
        uses: docker/login-action@v4
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push
        uses: docker/build-push-action@v7
        with:
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

**Note on `docker compose up -d --wait`:** This requires Docker Compose v2.21+ and health checks defined on all containers (addressed in the Docker section below). `--wait` blocks until all services report healthy — eliminates race conditions in CI.

**Confidence:** HIGH — action versions verified from GitHub releases pages. `actions/setup-java@v4` with `cache: maven` is the official recommendation replacing manual `actions/cache@v5` for Maven projects. `docker/build-push-action@v7` current as of April 2026.

---

### 6. Spring Security Hardening

**Verdict: No new dependencies for CORS and input validation. Bucket4j 7.6.1 already in pom.xml for rate limiting.**

**CORS (zero new dependencies):**
Configure via `WebMvcConfigurer.addCorsMappings()`. For this JWT-stateless app, credentials are sent as `Authorization` header, not cookies, so `allowCredentials(false)` is correct. Spring Security's `CorsFilter` must be declared in `SecurityConfig` to take precedence over the security filter chain.

```java
// In SecurityConfig: register CORS before Spring Security processes the request
http.cors().configurationSource(corsConfigurationSource());

@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("http://localhost:*", "https://yourdomain.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}
```

**Rate limiting (Bucket4j 7.6.1 — already in pom.xml):**
Bucket4j 7.x is the correct version for Java 11. Version 8.11.0+ migrated to JDK 17 as minimum. The `bucket4j-core` in-memory implementation requires no additional infrastructure — no Redis or Hazelcast dependency for basic rate limiting. Implement as a `HandlerInterceptor` that maintains a `ConcurrentHashMap<String, Bucket>` keyed on client IP.

```java
// Per-IP rate limiting: 20 requests / 1 second sliding window
Bandwidth limit = Bandwidth.classic(20, Refill.greedy(20, Duration.ofSeconds(1)));
Bucket bucket = Bucket.builder().addLimit(limit).build();
```

**Input validation (already in pom.xml from v2.0 — spring-boot-starter-validation):** Add `@Valid` annotations to new request DTOs and a `@ControllerAdvice` that returns RFC 7807 Problem Details for `MethodArgumentNotValidException`.

**Confidence:** HIGH — CORS configuration pattern verified from Spring Security 5.6.x docs. Bucket4j 7.6.1 Java 11 compatibility confirmed from project pom.xml and Bucket4j release notes (8.11.0 raised minimum to JDK 17).

---

### 7. OpenAPI / Swagger Improvements

**Verdict: No new dependencies. springdoc-openapi-ui 1.8.0 already in pom.xml.**

springdoc-openapi-ui 1.8.0 is the final open-source release of springdoc v1.x targeting Spring Boot 2.x. It is already present. All improvements are annotation additions to controllers.

**What to add (code only, no dependencies):**
- `@Operation(summary = "...", description = "...")` on each controller method
- `@ApiResponse` annotations documenting 200, 400, 401, 403, 404, 500 responses
- `@Schema` on DTO classes with `description` and `example` fields
- `@Tag(name = "...", description = "...")` on each controller class
- `@SecurityRequirement(name = "bearerAuth")` on protected endpoints
- A `@Bean OpenAPI customOpenAPI()` in `OpenApiConfig` registering the JWT security scheme

**springdoc configuration in `application.properties`:**
```properties
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operationsSorter=method
springdoc.swagger-ui.tagsSorter=alpha
springdoc.show-actuator=false
```

**Confidence:** HIGH — springdoc 1.8.0 version confirmed in existing pom.xml. Annotation API verified against springdoc-openapi v1 documentation.

---

### 8. Production Docker Compose

**Verdict: No new dependencies. Docker Compose YAML changes only.**

Key additions to `docker-compose.yml`:

**Health checks:**
```yaml
services:
  mongodb:
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  postgres:
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U restaurant"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  app:
    depends_on:
      mongodb: { condition: service_healthy }
      postgres: { condition: service_healthy }
      redis: { condition: service_healthy }
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/api/restaurants/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 60s
```

**Resource limits:**
```yaml
  app:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 512M
        reservations:
          memory: 256M

  mongodb:
    deploy:
      resources:
        limits:
          memory: 512M

  postgres:
    deploy:
      resources:
        limits:
          memory: 256M

  redis:
    deploy:
      resources:
        limits:
          memory: 128M
```

**Secrets via environment (no hardcoded values):**
```yaml
  app:
    environment:
      MONGODB_URI: ${MONGODB_URI:-mongodb://mongodb:27017}
      SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL:-jdbc:postgresql://postgres:5432/restaurantdb}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      NYC_API_APP_TOKEN: ${NYC_API_APP_TOKEN:-}
```

A `.env.example` file documents required variables without containing real values. The actual `.env` stays in `.gitignore`.

**Confidence:** HIGH — Docker Compose v2 health check syntax and resource limits documented in Docker official docs. `--wait` flag behavior confirmed for CI use. `depends_on` with `condition: service_healthy` is a Docker Compose v2 feature.

---

## Full Dependency Delta for v3.0

### New Maven Dependencies

```xml
<!-- ===== TESTCONTAINERS (integration tests) ===== -->
<!-- Add to <dependencyManagement>/<dependencies> -->
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>testcontainers-bom</artifactId>
  <version>1.19.8</version>
  <type>pom</type>
  <scope>import</scope>
</dependency>

<!-- Add to <dependencies> -->
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>testcontainers</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>mongodb</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>

<!-- ===== PLAYWRIGHT (E2E browser tests) ===== -->
<dependency>
  <groupId>com.microsoft.playwright</groupId>
  <artifactId>playwright</artifactId>
  <version>1.49.0</version>
  <scope>test</scope>
</dependency>

<!-- ===== STRUCTURED LOGGING ===== -->
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>7.3</version>
</dependency>
```

### New Maven Plugin Additions

```xml
<!-- ===== JACOCO (coverage + threshold enforcement) ===== -->
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <!-- (see full configuration in section 2 above) -->
</plugin>

<!-- ===== FAILSAFE (E2E integration tests separate from unit tests) ===== -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
  <!-- version managed by Spring Boot parent -->
  <!-- (see full configuration in section 3 above) -->
</plugin>
```

### Surefire argLine Fix (Required for JaCoCo compatibility)

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <!-- @{argLine} populated by jacoco:prepare-agent; must use this form not ${argLine} -->
    <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
  </configuration>
</plugin>
```

### New Files (No Maven Dependencies)

| File | Purpose |
|------|---------|
| `.github/workflows/ci.yml` | GitHub Actions CI pipeline |
| `src/main/resources/logback-spring.xml` | Structured JSON logging config |
| `.env.example` | Documents required env vars |
| `docker-compose.prod.yml` or updated `docker-compose.yml` | Health checks, resource limits |

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Integration test containers | Testcontainers 1.19.8 | Testcontainers 2.x | 2.x dropped JUnit 4 support; existing tests use JUnit Vintage Engine |
| E2E testing | Playwright for Java | Selenium WebDriver 4.x | Playwright has simpler CI setup, auto-waiting, no driver binary management |
| E2E testing | Playwright for Java | REST Assured (API-only) | REST Assured tests the API, not the browser UI; does not validate Thymeleaf rendering |
| JSON logging | logstash-logback-encoder 7.3 | Logback 1.3.x built-in JsonEncoder | Logback 1.3.x requires SLF4J 2.x — incompatible with Spring Boot 2.6 BOM (SLF4J 1.7.36) |
| Rate limiting | Bucket4j 7.6.1 (already in pom.xml) | Resilience4j 1.7.x ratelimiter | Resilience4j adds AOP + actuator complexity; Bucket4j in-memory is simpler for per-IP limiting |
| Rate limiting | Bucket4j 7.6.1 | Bucket4j 8.x | 8.11.0+ requires JDK 17; incompatible with Java 11 |
| CORS config | `CorsConfigurationSource` bean | `@CrossOrigin` on each controller | Single point of configuration; consistent enforcement; works correctly with Spring Security filter chain |
| CI registry | GitHub Container Registry (GHCR) | Docker Hub | GHCR is free for public repos with `GITHUB_TOKEN` auth; no separate Docker Hub credentials needed |

---

## What NOT to Add

**Spring Boot Actuator for rate limiting:** Actuator has no built-in rate limiting. Do not add Actuator just for rate limiting — Bucket4j is already present and sufficient.

**Resilience4j:** Adds AOP dependency, auto-configuration, and actuator metrics exposure for features (circuit breaker, retry) that are not needed here. The only need is rate limiting, which Bucket4j handles directly without AOP.

**spring-security-oauth2-resource-server:** The existing JWT implementation using `jjwt` and `JwtAuthenticationFilter` is correct for this project. Do not replace it with Spring Security's OAuth2 resource server support — that would require rewriting `JwtUtil`, `JwtAuthenticationFilter`, and `SecurityConfig`.

**Testcontainers 2.x:** Requires Java 11+ and removes JUnit 4 support. Since the project has existing JUnit 4 tests running via `junit-vintage-engine`, 1.19.8 is the correct choice until those tests are migrated.

**WireMock:** Not needed — the NYC Open Data API is tested via Testcontainers + real containers, not HTTP mocking.

**Flyway:** Useful for production schema management but out of scope for this milestone. The existing `spring.jpa.hibernate.ddl-auto=update` is sufficient.

---

## Integration Complexity Assessment

| Feature | Effort | Integration Notes |
|---------|--------|-------------------|
| GitHub Actions pipeline | Low | YAML only; `setup-java@v4` handles JDK + Maven cache |
| JaCoCo plugin | Low | Maven plugin; only Surefire `argLine` fix is non-obvious |
| Testcontainers (PostgreSQL) | Low | Drop-in replacement for live DB in `@SpringBootTest` |
| Testcontainers (MongoDB) | Medium | Existing `RestaurantDAOIntegrationTest` wires directly to MongoDB URI; requires refactoring `MongoClientFactory` to accept a configurable URI |
| Structured logging | Low | Add dependency + `logback-spring.xml`; no code changes to services |
| CORS hardening | Low | Add `CorsConfigurationSource` bean to `SecurityConfig` |
| Rate limiting (Bucket4j) | Low | Dependency already present; implement as a `HandlerInterceptor` |
| OpenAPI completeness | Medium | Annotation work across all controllers — tedious but straightforward |
| Production Docker Compose | Low | YAML changes only; health checks, resource limits, env var cleanup |
| Playwright E2E tests | High | Requires running application stack in CI; `docker compose up --wait` with health checks; browser install step; slowest part of pipeline (~5-10 min) |

---

## Sources

- Spring Boot 2.6.15 BOM (`spring-boot-dependencies-2.6.15.pom`): Logback 1.2.12, SLF4J 1.7.36, JUnit Jupiter 5.8.2, Hibernate Validator 6.2.5.Final — HIGH confidence (direct POM inspection)
- Maven Central: Testcontainers 1.19.8 mongodb/postgresql/junit-jupiter modules exist — HIGH confidence (HTTP 200 confirmed)
- Maven Central: JaCoCo 0.8.12 latest stable — HIGH confidence (verified)
- Maven Central: Playwright for Java 1.59.0 latest, 1.49.0 stable — MEDIUM confidence (verified on Maven Central; pinned to 1.49.0 for stability)
- logstash-logback-encoder 7.3 release notes: last version supporting Logback 1.2.x — HIGH confidence (GitHub release notes confirmed)
- Playwright Java docs: Java 8+ minimum requirement, browser install via Maven exec — HIGH confidence (official docs)
- Playwright Java CI docs: `mcr.microsoft.com/playwright/java` Docker image available — HIGH confidence (official docs)
- GitHub Actions releases: `actions/checkout@v5`, `actions/setup-java@v4`, `docker/build-push-action@v7`, `docker/login-action@v4`, `docker/metadata-action@v6` — HIGH confidence (verified from GitHub releases pages)
- Bucket4j release notes: 8.11.0 raised JDK minimum to 17; 7.6.1 is correct for Java 11 — HIGH confidence (release notes confirmed)
- JaCoCo docs: `check` goal configuration with `COVEREDRATIO` thresholds — HIGH confidence (official docs)
- Existing `pom.xml`: current dependency set, Surefire `argLine` configuration — HIGH confidence (direct read)

---

*Stack research: 2026-04-11*
