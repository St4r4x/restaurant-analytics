# Domain Pitfalls

**Domain:** Production-readiness on an existing Spring Boot 2.6.15 / Java 11 monolith — CI/CD, testing, security hardening, UI redesign
**Researched:** 2026-04-11
**Confidence:** HIGH (codebase read directly; Java 25 runtime issues confirmed in pom.xml comments; framework patterns from official sources)

---

## Note on Prior Pitfalls

The previous version of this file (2026-03-27) covered v2.0 pitfalls — RBAC, file upload, map performance, refresh-token revocation. Those are now shipped and validated. This document covers v3.0 pitfalls only: CI/CD pipeline, testing infrastructure, security hardening, and UI design system.

---

## Critical Pitfalls

Mistakes that cause pipeline failures, broken coverage reports, or CI environments that never work.

---

### Pitfall 1: JaCoCo argLine clobbers the Java 25 Mockito agent flag

**What goes wrong:** The project's `maven-surefire-plugin` uses `<argLine>-XX:+EnableDynamicAgentLoading</argLine>` — required for Mockito's ByteBuddy self-attachment on Java 25. When JaCoCo's `prepare-agent` goal runs, it appends its own `-javaagent` string to the `argLine` Maven property. If `argLine` is defined as a static string in `<configuration>`, JaCoCo overwrites it completely — the Mockito flag disappears, ByteBuddy can no longer attach, and tests produce `StackOverflowError` or `IllegalStateException: Could not self-attach` on Java 25.

**Why it happens:** JaCoCo's `prepare-agent` writes to the Maven property `argLine`. If Surefire's `<argLine>` is a literal string, the JaCoCo property is ignored. If it's the same property name, the last writer wins.

**Consequences:** All mocked controller slice tests fail silently after JaCoCo is added. The test output shows the same StackOverflow that was already fixed in v2.0 — because the fix was undone.

**Prevention — HIGH priority:**
Use late-binding syntax in Surefire so both values are merged:

```xml
<!-- pom.xml: define an empty default so late binding never produces "null" -->
<properties>
  <argLine></argLine>
</properties>

<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <!-- @{argLine} expands AFTER JaCoCo prepare-agent has written its value -->
    <argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>
  </configuration>
</plugin>
```

JaCoCo then writes its agent into `${argLine}` and Surefire sees both.

**Detection:** After adding JaCoCo, run `mvn test`. If Mockito-instrumented tests that passed before now throw `StackOverflowError`, the argLine merge is broken.

**Phase:** CI / Coverage (whichever phase adds JaCoCo first).

---

### Pitfall 2: Testcontainers + JUnit 4 @ClassRule lifecycle does not integrate with Spring context

**What goes wrong:** The project mixes JUnit 4 (`SecurityConfigTest`, `RestaurantDAOIntegrationTest`) with JUnit 5 (`AuthServiceTest`, all controller tests). The existing integration test (`RestaurantDAOIntegrationTest`) assumes a live MongoDB at `localhost:27017`. When converting to Testcontainers, the natural JUnit 4 pattern is:

```java
@ClassRule
public static MongoDBContainer mongo = new MongoDBContainer("mongo:6");
```

This works for a plain JUnit 4 test. However, if Testcontainers is placed on a test class that is also loaded by a `SpringRunner` or `SpringBootTest`, the `@ClassRule` container starts BEFORE the Spring context. The Spring context reads `mongodb.uri` from `application.properties` (pointing at `localhost:27017`), not at the dynamically-assigned Testcontainers port — so the application connects to nothing.

**Why it happens:** `@ClassRule` provides no hook to override Spring properties before the context is refreshed. JUnit 5's `@Testcontainers` + `DynamicPropertySource` solves this natively; JUnit 4 has no equivalent.

**Consequences:** The Spring context wires up against the wrong MongoDB URI. All repository calls fail with connection refused. Containers start and are immediately destroyed unused.

**Prevention:**

Option A (recommended) — keep integration tests as JUnit 5. The project already uses JUnit Vintage Engine to run JUnit 4 tests on the JUnit 5 platform. Write new Testcontainers-based integration tests as JUnit 5 classes using `@DynamicPropertySource`:

```java
@Testcontainers
@SpringBootTest
class RestaurantDAOContainerTest {
    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry r) {
        r.add("mongodb.uri", mongo::getReplicaSetUrl);
    }
    // ...
}
```

Option B — if the test must stay JUnit 4: use a static initializer to start the container and then use a `JUnit4SpringRunner` custom rule that sets system properties before the Spring context starts. This is significantly more complex and error-prone.

**Detection:** Log the MongoDB URI at startup inside `MongoClientFactory`. If it logs `localhost:27017` during a Testcontainers test run, the property override is not working.

**Phase:** Integration testing phase (Testcontainers introduction).

---

### Pitfall 3: Testcontainers needs a working Docker socket in GitHub Actions — it is available but not obvious

**What goes wrong:** Developers assume Testcontainers on GitHub Actions requires Docker-in-Docker (DinD) setup or special configuration. Without understanding the environment, they either add DinD (which is slow and requires `--privileged`) or mark all Testcontainers tests as `@Ignore` in CI.

**What is actually true:** GitHub Actions `ubuntu-latest` runners have Docker Engine installed and the Docker socket is available at `/var/run/docker.sock`. Testcontainers connects to this socket automatically via the standard discovery chain. No explicit configuration is needed.

**The real risk:** Resource contention. GitHub Actions free-tier runners have 2 vCPUs and 7 GB RAM. Starting MongoDB + PostgreSQL + Redis containers simultaneously alongside the Spring Boot application context can OOM-kill the runner mid-test.

**Prevention:**
- Do not start all three database containers in the same test class unless absolutely necessary. Scope containers to the test that needs them.
- Use `@Container` (instance-level lifecycle) rather than `static @Container` (class-level) to limit concurrent container count.
- Use Alpine or slim Docker images (`mongo:6-focal`, `postgres:15-alpine`, `redis:7-alpine`) — they start faster and use less memory.
- Add a `withStartupTimeout(Duration.ofSeconds(120))` wait strategy — CI runners are slower than dev machines.
- Set `services: {}` in the workflow (no GitHub Actions service containers) when using Testcontainers — do not run both simultaneously.

**Detection:** If the CI runner is killed mid-job without an error message, it is an OOM kill. Check the GitHub Actions runner log for "Process killed" or "Runner system is running out of disk space".

**Phase:** CI pipeline setup.

---

### Pitfall 4: Docker layer cache invalidation every build because the JAR changes

**What goes wrong:** A naively-written Dockerfile copies the entire source tree or the fat JAR in a single `COPY` instruction. Because the JAR changes on every commit, Docker cannot reuse any layer — every CI build re-downloads all dependencies into the container image. A Spring Boot fat JAR can be 60–120 MB; with no layer caching, each build pushes a full new layer.

**Why it happens:** Spring Boot fat JARs bundle all dependencies. There is no mechanism for Docker to know "only the app code changed, not the libraries."

**Consequences:** CI Docker build steps take 3–5 minutes per run instead of 30 seconds.

**Prevention:** Use Spring Boot's layered JAR feature (enabled by default in Spring Boot 2.3+, which includes 2.6.15):

```dockerfile
FROM eclipse-temurin:11-jre AS builder
WORKDIR /app
COPY target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]
```

Layer order from least-changing to most-changing: `dependencies` (changes rarely), `spring-boot-loader` (changes on Spring Boot upgrade), `snapshot-dependencies` (changes on SNAPSHOT dependency update), `application` (changes every commit). Only the last layer is ever invalidated on normal commits.

Combine with GitHub Actions cache:
```yaml
- uses: docker/build-push-action@v6
  with:
    cache-from: type=gha
    cache-to: type=gha,mode=max
```

Note: `type=gha` requires Docker Buildx >= 0.21.0 (updated in April 2025 to require GitHub Cache API v2). Pin `docker/setup-buildx-action` to a version that ships Buildx 0.21+.

**Detection:** Check the CI Docker build step output. If it shows `[+] Building 180s` with no `CACHED` layer reuses, caching is broken.

**Phase:** Docker / CI build pipeline.

---

### Pitfall 5: GitHub Actions Docker build fails to push to GHCR due to package permission mislink

**What goes wrong:** Using `GITHUB_TOKEN` for GHCR authentication works when the container image is linked to the repository. If the image name does not match or the `org.opencontainers.image.source` label is absent from the Dockerfile, the first push creates an unlinked package. Subsequent pushes with `GITHUB_TOKEN` get `denied: permission_denied`.

**Why it happens:** GHCR ties `GITHUB_TOKEN` write permission to the specific repository associated with the package. Unlinked packages have no automatic association.

**Prevention:**
1. Add to the Dockerfile: `LABEL org.opencontainers.image.source=https://github.com/St4r4x/restaurant-analytics`
2. In the workflow, name the image as `ghcr.io/st4r4x/restaurant-analytics:latest` (lowercase owner).
3. On the first push, go to GitHub Packages settings and link the package to the repository manually if the label alone is insufficient.
4. Use this pattern in the workflow:

```yaml
- name: Log in to GHCR
  uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}
```

No PAT required — `GITHUB_TOKEN` is sufficient when the package is linked.

**Detection:** Push fails with `denied: permission_denied` even though `GITHUB_TOKEN` has `packages: write`. Check whether the package appears under the repository's "Packages" tab on GitHub.

**Phase:** CI Docker push pipeline.

---

### Pitfall 6: CORS preflight returns 403 because Spring Security intercepts OPTIONS before CORS headers are added

**What goes wrong:** When CORS is configured only in `WebMvcConfigurer.addCorsMappings()` without also being registered in Spring Security's filter chain, OPTIONS preflight requests sent by the browser are intercepted by Spring Security before the MVC CORS resolver runs. Spring Security sees an unauthenticated OPTIONS request, finds no matching permit rule, and returns 403. The browser then blocks the actual request.

This is directly relevant because the existing `SecurityConfig` uses `anyRequest().permitAll()` — which seems like it should pass OPTIONS through. However, the existing code does not explicitly call `.cors(withDefaults())`, meaning Spring Security does not engage its own CORS filter at all. Whether CORS preflight works currently depends on whether there are any CORS-triggering callers.

Adding an explicit CORS policy for production (a v3.0 goal) makes this pitfall acute.

**Why it happens:** MVC CORS config and Spring Security CORS config are two separate mechanisms. Spring Security runs in a Servlet filter, before MVC. If Security does not handle CORS, it passes the request to MVC — but for protected endpoints, Security rejects it first.

**Prevention — HIGH confidence (official Spring Security docs):**

Register a `CorsConfigurationSource` bean AND call `.cors(withDefaults())` in `SecurityConfig`:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .cors(withDefaults())  // picks up the CorsConfigurationSource bean
        // rest of config...
    return http.build();
}

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:8080", "https://your-prod-domain.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

Do NOT use `allowedOrigins("*")` when the JWT `Authorization` header must be sent — browsers block credentialed cross-origin requests to wildcard origins.

**Detection:** In browser DevTools, check the Network tab for a preflight `OPTIONS` request returning 403. If absent, CORS is not being triggered (same-origin access). If present and 403, Spring Security is intercepting before CORS headers are applied.

**Phase:** Security hardening (CORS policy phase).

---

## Moderate Pitfalls

---

### Pitfall 7: Playwright E2E tests against Spring Boot — JWT in localStorage, not cookies

**What goes wrong:** Playwright's standard authentication pattern uses `context.storageState()` to save and restore browser cookies. For apps using server-side sessions or cookie-based auth, this works perfectly. This project stores JWT tokens in `localStorage` (the JavaScript IIFE pattern). Playwright's `storageState` does save `localStorage`, but only for the exact origin. If the test base URL changes between local and CI (e.g., `localhost:8080` vs. a container hostname), the saved state is useless — `localStorage` is origin-scoped.

**Additional complication:** The app has `anyRequest().permitAll()` for HTML pages but JWT-protected API endpoints. E2E tests that click through the UI trigger JavaScript `fetch` calls that include the JWT from `localStorage`. Playwright must have the token in storage before those JavaScript calls fire.

**Prevention:**
1. In Playwright test setup, call the `/api/auth/login` endpoint directly via `APIRequestContext`, extract the `accessToken`, and inject it into `localStorage` before navigating to the page:

```java
APIRequestContext api = playwright.request().newContext(
    new APIRequest.NewContextOptions().setBaseURL("http://localhost:8080"));
APIResponse loginResp = api.post("/api/auth/login", RequestOptions.create()
    .setData(Map.of("username", "testctrl", "password", "testpass")));
String token = new ObjectMapper()
    .readTree(loginResp.body()).get("accessToken").asText();

BrowserContext ctx = browser.newContext();
ctx.addInitScript("localStorage.setItem('accessToken', '" + token + "')");
Page page = ctx.newPage();
```

2. Use a dedicated test user seeded in `DataSeeder` with a known password, not a real account.
3. Store the setup code in a `@BeforeAll` shared across tests, not duplicated per test.

**Detection:** E2E test navigates to `/dashboard` and gets blank content or a redirect to `/` — the IIFE redirected because `accessToken` was absent from `localStorage`.

**Phase:** E2E testing phase.

---

### Pitfall 8: Playwright browser binaries not installed in CI Docker image

**What goes wrong:** Adding `playwright` as a Maven dependency does not install browser binaries. Playwright downloads Chromium/Firefox/WebKit binaries via a CLI step. In CI, if this step is skipped or the binaries directory is not cached, tests fail with `Executable doesn't exist at ...chromium...`.

**Prevention:**
Install browsers as a dedicated workflow step before running tests:

```yaml
- name: Install Playwright browsers
  run: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install --with-deps chromium"
```

Use only Chromium in CI (not all three engines) to reduce download size and installation time.

Do NOT cache Playwright browsers — the Playwright documentation explicitly states that cache restoration time equals download time, making caching net-neutral while adding complexity.

**Detection:** CI job fails with `Error: browserType.launch: Executable doesn't exist`. This is unambiguous.

**Phase:** E2E testing / CI pipeline.

---

### Pitfall 9: Bucket4j 7.x has no Spring Boot autoconfiguration — manual filter wiring required

**What goes wrong:** The project already includes `bucket4j-core:7.6.1` (correctly pinned to 7.x because 8.x requires Java 17). However, `bucket4j-core` is a plain library — there is no Spring Boot starter autoconfiguration for 7.x. The Spring Boot Starter for Bucket4j (which provides YAML config and `FilterRegistrationBean` auto-setup) requires Bucket4j 8.x and Java 17+.

This means rate limiting must be implemented as a `javax.servlet.Filter` (not `jakarta.servlet.Filter`) registered manually in Spring Security's filter chain. Getting the filter registration order wrong relative to `JwtAuthenticationFilter` causes either:
- Rate limiting that never fires (filter registered after the request is already processed)
- Token-based requests that are rate-limited before the JWT is parsed (blocking valid users)

**Prevention:**
- Register the rate-limit filter BEFORE `JwtAuthenticationFilter` in the security chain via `http.addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)`.
- Use an in-memory `Bucket` per IP address stored in a `ConcurrentHashMap` — no Redis or JCache dependency needed for a single-instance deployment.
- Scope limits to auth endpoints only (`/api/auth/login`, `/api/auth/register`) to avoid limiting normal API traffic.

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        if (!req.getRequestURI().startsWith("/api/auth/")) {
            chain.doFilter(req, res);
            return;
        }
        String ip = req.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(ip, k ->
            Bucket.builder()
                .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(1))))
                .build());
        if (bucket.tryConsume(1)) {
            chain.doFilter(req, res);
        } else {
            res.setStatus(429);
            res.getWriter().write("{\"error\":\"Too many requests\"}");
        }
    }
}
```

**Detection:** Rate limiting silently does nothing (no 429 ever) or blocks legitimate auth calls. Write a unit test that fires 21 login requests from the same IP and asserts the 21st gets 429.

**Phase:** Security hardening / rate limiting.

---

### Pitfall 10: JaCoCo coverage report excludes mocked code — inflated or deflated numbers

**What goes wrong:** JaCoCo counts coverage by instrumenting bytecode. Methods that are only ever called via Mockito mocks in tests never execute their real bytecode — JaCoCo reports them as 0% covered. Conversely, if a class is only tested via `@WebMvcTest` slice tests (which boot a partial Spring context and Mockito-mock the service layer), the service itself shows 0% coverage in the report even if it has dedicated unit tests in a different test class.

This creates misleading coverage numbers — services might show low coverage because integration tests mock them, even though unit tests cover them directly.

**Prevention:**
- Do not set a coverage threshold in the first CI run — establish the baseline first and set the threshold to `baseline - 5%` to allow for some variance.
- Exclude generated code from coverage: DTOs, entities, Lombok-generated methods. Add exclusions in the JaCoCo plugin config:

```xml
<configuration>
  <excludes>
    <exclude>com/aflokkat/dto/**</exclude>
    <exclude>com/aflokkat/entity/**</exclude>
    <exclude>com/aflokkat/domain/**</exclude>
    <exclude>com/aflokkat/aggregation/**</exclude>
  </excludes>
</configuration>
```

- The real target for coverage is service and DAO layers. Set a per-class threshold on those packages rather than a global line threshold.

**Detection:** Run `mvn jacoco:report` and open `target/site/jacoco/index.html`. If `RestaurantService` shows 20% despite having 12 unit tests, check whether those unit tests use `@InjectMocks` (direct instantiation, no bytecode instrumentation gap) or `@SpringBootTest` (full context, JaCoCo does instrument it). Mock-only tests do not contribute to coverage of the mocked class.

**Phase:** Coverage / JaCoCo setup.

---

### Pitfall 11: Thymeleaf CSS design system — server-rendered vs JS-rendered DOM conflicts

**What goes wrong:** The existing templates use vanilla JS to dynamically insert DOM nodes (restaurant cards, inspection rows, map markers) after page load. A new CSS design system applied to static Thymeleaf markup will not automatically apply to JS-rendered nodes.

Common symptom: the page looks correct on initial load (server-rendered nav, headings, layout), but dynamically inserted restaurant cards use old styles or no styles at all — because the CSS class names in the JS templates do not match the new design system class names.

**Why it happens:** The JavaScript `fetch` calls build HTML strings directly in JS (e.g., `innerHTML += '<div class="restaurant-card">...'`). The class names in those strings are hardcoded. Renaming CSS classes during the UI redesign does not update JS string templates automatically.

**Prevention:**
- Before renaming any CSS class, grep for all uses of that class name in `.js` files and Thymeleaf templates simultaneously.
- Prefer JS template literals that reference a shared function (`renderRestaurantCard(data)`) rather than inline HTML strings scattered across multiple fetch handlers. One function = one place to update.
- When introducing a new design system, add the new classes alongside the old ones initially. Verify JS-rendered content looks correct, then remove the old class names.

**Detection:** On the `/search` or `/map` page, search for a restaurant. If the dynamically-loaded cards look visually broken while the static page frame looks correct, the JS string templates were not updated.

**Phase:** UI redesign phase.

---

### Pitfall 12: setup-java Maven cache key collides across branches

**What goes wrong:** The `actions/setup-java` action with `cache: 'maven'` uses the `pom.xml` hash as the cache key. If two branches modify `pom.xml` differently (one adds a test dependency, another adds a main dependency), their caches collide — the runner may restore a stale cache from the wrong branch and produce `ClassNotFoundException` at test time.

**Prevention:**
```yaml
- uses: actions/setup-java@v4
  with:
    java-version: '11'
    distribution: 'temurin'
    cache: 'maven'
    cache-dependency-path: '**/pom.xml'
```

The `cache-dependency-path` glob ensures any `pom.xml` change invalidates the cache. This is sufficient — no manual `hashFiles()` needed.

**Detection:** CI passes locally but fails with `ClassNotFoundException` or `NoSuchMethodError` on a branch that added new dependencies. Delete the cache entry in GitHub Actions and re-run — if it passes, the cache was stale.

**Phase:** CI pipeline setup.

---

## Minor Pitfalls

---

### Pitfall 13: Hardcoded secrets in application.properties will be caught by GitHub secret scanning

**What goes wrong:** `application.properties` currently contains `jwt.secret=<min 32 chars>` as a placeholder. If the actual secret is committed (even once, even on a branch), GitHub's push protection will block the push or flag it in the security tab. GHCR image builds that embed the properties file will bake the secret into the image layer permanently.

**Prevention:**
- Never commit a real `jwt.secret` value. The placeholder in properties is correct.
- In Docker Compose, inject via environment variable: `JWT_SECRET=${JWT_SECRET}` and read in Spring with `${JWT_SECRET}`.
- In GitHub Actions, store as a repository secret and pass to the Docker build with `--build-arg` only if needed at build time (it should not be — it is a runtime value, not a build-time constant).
- Add `application-local.properties` to `.gitignore` for local dev overrides.

**Phase:** Config/secrets hardening.

---

### Pitfall 14: MongoDB aggregation pipeline tests are fragile against NYC Open Data schema drift

**What goes wrong:** The existing DAO integration tests (`RestaurantDAOIntegrationTest`) assert `results.size() >= 5` boroughs and `score > 0`. These pass when the live NYC dataset is synced. If Testcontainers seeds the MongoDB container with a fixture file that has slightly different field names (e.g., `camis` vs `restaurantId`, or missing `score` field), all aggregation tests fail with empty results — not with a helpful error.

**Prevention:**
- Create a minimal fixture JSON (10–20 restaurants covering 5 boroughs, 3 cuisines, varied scores) specifically designed to exercise all DAO aggregation paths.
- Verify the fixture field names match the POJO mapping in `NycApiRestaurantDto` exactly.
- Do not use the live NYC API in Testcontainers-based tests — the dataset changes and makes tests non-deterministic.

**Phase:** Integration testing / Testcontainers introduction.

---

### Pitfall 15: Playwright tests assume port 8080 is always free — fails in parallel CI jobs

**What goes wrong:** If the GitHub Actions workflow runs integration tests and E2E tests as separate jobs but they share a self-hosted runner (or the E2E job starts before integration tests have released port 8080), the Spring Boot app fails to start with `Address already in use`.

**Prevention:**
- Use `server.port=0` (random port) in tests and inject the port via `@LocalServerPort`.
- For Playwright, obtain the port from Spring's environment and pass it to the Playwright base URL:

```java
@Value("${local.server.port}")
private int port;

String baseUrl = "http://localhost:" + port;
```

- Alternatively, split E2E into a separate workflow that only runs on pull requests to `main`, not on every push to `develop`.

**Phase:** E2E testing / CI workflow design.

---

### Pitfall 16: JUnit 4 tests silently skipped when JUnit Vintage Engine is absent in CI

**What goes wrong:** The project mixes JUnit 4 (`SecurityConfigTest`, `RestaurantDAOIntegrationTest`) and JUnit 5 tests. JUnit Vintage Engine (`junit-vintage-engine`) is already in `pom.xml`, which is correct. The risk: if someone adds a `<excludeJUnit5Engines>junit-vintage</excludeJUnit5Engines>` configuration to Maven Surefire (a common "clean up" action), all JUnit 4 tests are silently excluded with no failure — they simply do not run. Coverage drops, regressions go undetected.

**Prevention:**
- Never configure `excludeJUnit5Engines` in this project unless explicitly migrating away from JUnit 4.
- Add a CI check: assert the test count is above a minimum threshold. If `mvn test` reports fewer than N tests passed, fail the pipeline.
- Consider migrating `SecurityConfigTest` to JUnit 5 (it only uses `@Before`, `@Test` from JUnit 4 — trivial migration) to remove the dependency on Vintage Engine entirely.

**Phase:** CI pipeline / test migration.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|---|---|---|
| JaCoCo coverage setup | argLine clobbers Mockito agent flag (Pitfall 1) | Use `@{argLine} -XX:+EnableDynamicAgentLoading` in Surefire config |
| Testcontainers introduction | JUnit 4 @ClassRule cannot inject Spring properties (Pitfall 2) | Write new integration tests as JUnit 5 with @DynamicPropertySource |
| Testcontainers in CI | OOM kills on 2-vCPU GitHub runner (Pitfall 3) | Use slim images; don't start all 3 DB containers simultaneously |
| Docker build in CI | All layers invalidated on every commit (Pitfall 4) | Spring Boot layered JAR Dockerfile |
| GHCR push | Permission denied after first unlinked push (Pitfall 5) | Add OCI source label to Dockerfile; link package to repo |
| CORS policy | OPTIONS preflight returns 403 from Spring Security (Pitfall 6) | Register CorsConfigurationSource bean + call .cors(withDefaults()) |
| E2E with Playwright | JWT in localStorage not restored by storageState (Pitfall 7) | Inject token via addInitScript after API login |
| Playwright in CI | Browser binaries missing (Pitfall 8) | Add explicit install step in workflow; use Chromium only |
| Rate limiting | Bucket4j 7.x has no autoconfiguration (Pitfall 9) | Manual OncePerRequestFilter; register before JwtAuthenticationFilter |
| JaCoCo report | Mocked dependencies show 0% coverage (Pitfall 10) | Exclude POJOs/DTOs; set threshold on service layer only |
| UI redesign | JS-rendered cards not updated with new CSS classes (Pitfall 11) | Grep for class names across JS and templates; update together |
| Maven cache in CI | Cache key collision across branches (Pitfall 12) | Use cache-dependency-path: '**/pom.xml' |
| Secrets management | Committed jwt.secret baked into image (Pitfall 13) | Runtime env var injection; never in Dockerfile |
| Testcontainers fixtures | Aggregation tests break on schema drift (Pitfall 14) | Controlled 20-document fixture; match NycApiRestaurantDto fields |
| E2E parallel jobs | Port 8080 conflict (Pitfall 15) | server.port=0; inject port via @LocalServerPort |
| JUnit 4 coexistence | Vintage Engine exclusion silently drops tests (Pitfall 16) | Never configure excludeJUnit5Engines; consider migrating to JUnit 5 |

---

## Java 25 Runtime — Summary of Known Issues in This Project

This project compiles for Java 11 (`source`/`target`) but runs on a Java 25 JVM. Issues already encountered and resolved in v2.0:

| Issue | Root Cause | Resolution in pom.xml |
|---|---|---|
| `StackOverflowError` in `@WebMvcTest` slice tests | Mockito ByteBuddy self-attachment blocked without explicit JVM flag | `-XX:+EnableDynamicAgentLoading` in Surefire argLine |
| `MockMaker conflict` with old `mockito-inline` | Mockito 5.x merged inline mocking into core; having both caused `MockMaker` conflict | Removed `mockito-inline` dep; kept `mockito-core:5.17.0` |
| ByteBuddy `IllegalArgumentException` on newer class file format | ByteBuddy 1.12.x (Spring Boot 2.6 default BOM) does not understand Java 25 class file version | Overrode `byte-buddy.version` to `1.16.0` in pom.xml properties |

**These are already fixed. The CI pipeline must preserve all three fixes.** Specifically:
- The JaCoCo argLine merge (Pitfall 1) must not remove `-XX:+EnableDynamicAgentLoading`.
- Do not downgrade `byte-buddy.version` from `1.16.0`.
- Do not re-add `mockito-inline` as a dependency.

If any future phase adds dependencies that transitively pull in an older ByteBuddy, add an explicit `<dependencyManagement>` exclusion for it.

---

## Sources

- Direct codebase analysis: `pom.xml` (read 2026-04-11) — confirms Mockito 5.17.0, ByteBuddy 1.16.0 override, Vintage Engine, JUnit 4/5 mix, Bucket4j 7.6.1
- Direct codebase analysis: `SecurityConfigTest.java` — confirms manual `AnnotationConfigWebApplicationContext` pattern to avoid `@WebMvcTest` crash on Java 25
- Direct codebase analysis: `RestaurantDAOIntegrationTest.java` — confirms JUnit 4 test with live MongoDB at localhost:27017 (no Testcontainers yet)
- JaCoCo argLine merge pattern: HIGH confidence — from official JaCoCo Maven plugin documentation (`prepare-agent-mojo.html`)
- Testcontainers JUnit 4 @ClassRule + Spring context limitation: HIGH confidence — from Testcontainers GitHub Issues (#970, module restructuring in #10285) and Spring `@DynamicPropertySource` official docs
- GitHub Actions Docker socket availability: HIGH confidence — GitHub-hosted runner documentation states Docker Engine is pre-installed on `ubuntu-latest`
- Spring Boot layered JAR Dockerfile: HIGH confidence — Spring Boot 2.3+ official documentation; applicable to 2.6.15
- GHCR GITHUB_TOKEN permission + package linking: HIGH confidence — official GitHub Packages documentation
- Spring Security CORS filter ordering: HIGH confidence — official Spring Security CORS integration documentation (confirmed: CorsConfigurationSource bean + .cors(withDefaults()) is the correct pattern)
- Playwright localStorage authentication pattern: HIGH confidence — official Playwright Java docs (auth page, storageState)
- Playwright CI browser installation: HIGH confidence — official Playwright CI documentation
- Bucket4j 7.x no Spring Boot starter: HIGH confidence — Bucket4j GitHub confirms Spring Boot Starter requires 8.x (Java 17+); version 7.6.1 is pure library
- JaCoCo coverage exclusion pattern: MEDIUM confidence — common practice; documented in JaCoCo plugin configuration reference
