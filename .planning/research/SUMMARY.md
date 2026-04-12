# Research Summary — v3.0 Production Readiness

**Project:** Restaurant Hygiene Control App — Spring Boot Monolith
**Domain:** Production-readiness hardening of an existing Spring Boot 2.6.15 / Java 11 monolith
**Researched:** 2026-04-11
**Confidence:** HIGH

---

## Executive Summary

v3.0 is a hardening milestone, not a feature milestone. The application is functionally complete (v2.0 shipped all 36 requirements). The goal is to make it portfolio-grade: full CI/CD pipeline, real test coverage against live containers, security hardening, structured logging, production-quality Docker Compose, and a visual redesign. None of these require framework upgrades or significant architectural change — the stack is frozen at Spring Boot 2.6.15 / Java 11 by constraint, and all new additions are either Maven plugins, test-scoped dependencies, or YAML / CSS files.

The recommended approach treats the milestone as eight distinct infrastructure layers built in dependency order: logging and Maven build tooling first (everything else benefits from them immediately), then test infrastructure (JaCoCo + Testcontainers), then the CI pipeline (which consumes the test infrastructure), then security and code quality additions, and finally the UI redesign as the last purely presentational layer. This ordering avoids the most common failure mode — adding a CI pipeline before the test infrastructure is self-contained, which produces a pipeline that only passes on runners with a live database.

The two hardest integration problems are both known and fully solvable. First, the JaCoCo `prepare-agent` goal will silently break the existing Mockito/Java 25 `argLine` fix unless Surefire is changed from a literal string to the late-binding `@{argLine}` form — this is a one-line fix that must happen before anything else in the Maven build. Second, `MongoClientFactory` is a static singleton that binds at first class load; the Testcontainers base class must inject `System.setProperty()` overrides before any DAO is instantiated, which requires patching `AppConfig.getProperty()` to check `System.getProperty()` before the environment. Both fixes have HIGH-confidence verified solutions from direct codebase inspection.

---

## Stack Additions

Only net-new additions relative to the locked v2.0 stack. No dependency upgrades, no framework changes.

### New Maven dependencies

| Dependency | Version | Scope | Purpose |
|---|---|---|---|
| `org.testcontainers:testcontainers-bom` | **1.19.8** | BOM import | Version alignment for all Testcontainers modules |
| `org.testcontainers:testcontainers` | (BOM) | test | Core Testcontainers runtime |
| `org.testcontainers:mongodb` | (BOM) | test | MongoDB container for integration tests |
| `org.testcontainers:postgresql` | (BOM) | test | PostgreSQL container for integration tests |
| `org.testcontainers:junit-jupiter` | (BOM) | test | JUnit 5 `@Testcontainers` annotation support |
| `com.microsoft.playwright:playwright` | **1.49.0** | test | E2E browser automation (Java API) |
| `net.logstash.logback:logstash-logback-encoder` | **7.3** | compile | JSON structured log output for `prod` Spring profile |

### New Maven plugins

| Plugin | Version | Purpose |
|---|---|---|
| `org.jacoco:jacoco-maven-plugin` | **0.8.12** | Coverage instrumentation, HTML report, build-fail threshold |
| `maven-failsafe-plugin` | (Spring Boot BOM) | Separate lifecycle for integration + E2E tests (`*IT.java`, `*E2ETest.java`) |

### Version constraints to respect

- Testcontainers must stay at **1.x (1.19.8)**: 2.x dropped JUnit 4 support; the project has JUnit 4 tests via `junit-vintage-engine`.
- logstash-logback-encoder must stay at **7.3**: 7.4+ dropped Logback 1.2.x support; Spring Boot 2.6.15 ships Logback 1.2.12.
- Bucket4j must stay at **7.6.1** (already in pom.xml): 8.11.0+ requires JDK 17.
- Playwright pinned to **1.49.0** for stability; latest is 1.59.0 but 1.49.0 is documented and verified on Maven Central.

### New non-dependency files

| File | Purpose |
|---|---|
| `.github/workflows/ci.yml` | GitHub Actions pipeline (build, unit, integration, e2e, docker) |
| `src/main/resources/logback-spring.xml` | Replaces the non-functional `simplelogger.properties` |
| `src/test/resources/application-test.properties` | Test-profile property overrides pointing at Testcontainers ports |
| `.env.example` | Documents all required environment variables for operators and reviewers |

---

## Feature Table Stakes

### CI/CD

| Category | Features |
|---|---|
| Table stakes | Push/PR triggers on `develop` and `main`; Java 11 Temurin build; `mvn test` gate; Maven dependency cache via `setup-java@v4`; Docker build on every push; Docker push to GHCR on `main` only; all secrets via GitHub Actions secrets |
| Differentiators | Separate jobs with `needs:` dependency graph (clear failure attribution); integration job with Testcontainers (no `services:` block needed — TC manages its own containers); E2E job with Playwright against a live `docker compose up` stack; JaCoCo HTML report uploaded as artifact; README status badge |
| Anti-features | Staging/prod deployment pipeline (no live server); Slack/email notifications; Java version matrix; self-hosted runners |

### Testing

| Category | Features |
|---|---|
| Table stakes | Existing 27 unit tests stay green; service and DAO unit tests with Mockito; JaCoCo plugin measuring coverage with a 60% line / 50% branch threshold |
| Differentiators | Testcontainers integration tests against real MongoDB + PostgreSQL containers; JaCoCo threshold enforced as build gate; Playwright E2E smoke tests covering login, search, analytics, and controller dashboard |
| Anti-features | Mocking MongoDB aggregation pipelines in integration tests (masks real pipeline syntax failures); 100% coverage target; JUnit 4 to JUnit 5 mass migration (high risk, out of scope) |

### Database (MongoDB)

| Category | Features |
|---|---|
| Table stakes | Index on `camis` (unique), `boro`, `cuisine_description`; compound index on `grades.score + grades.grade`; `ensureIndexes()` called at startup or sync time |
| Differentiators | Text index on `dba` for full-text search (`$text` operator); 2dsphere index on `address.coord` for `$near` queries; projection in DAO list queries to reduce bandwidth |
| Anti-features | Index on every field (write overhead); migrating to Spring Data MongoDB from raw driver |

### Config and Secrets

| Category | Features |
|---|---|
| Table stakes | No hardcoded secrets in source; `application.properties` fully env-var-driven; JWT secret from `${JWT_SECRET}`; controller and admin signup codes from env vars; `application-test.properties` for test profile; `.env.example` at project root |
| Differentiators | Fail-fast assertion on missing JWT secret at startup; `docker-compose.override.yml` pattern for local dev |
| Anti-features | HashiCorp Vault / AWS Secrets Manager; Spring Cloud Config Server; dotenv-java as production config mechanism |

### Docker

| Category | Features |
|---|---|
| Table stakes | Health checks on all 4 services (app, MongoDB, Postgres, Redis); `depends_on: condition: service_healthy`; named volumes for persistent data; `restart: unless-stopped`; no `changeme` defaults in compose |
| Differentiators | Memory limits via `deploy.resources`; multi-stage Dockerfile with Spring Boot layered JAR; non-root user in Dockerfile; `.dockerignore` file |
| Anti-features | Kubernetes / Helm (overkill for portfolio scope); MongoDB auth in compose (network isolation sufficient) |

### Security

| Category | Features |
|---|---|
| Table stakes | Explicit CORS policy via `CorsConfigurationSource` bean registered in `SecurityConfig`; input validation `@Valid` on all request DTOs with `MethodArgumentNotValidException` handler; rate limiting on auth endpoints (Bucket4j 7.6.1, already in pom.xml); JWT secret length enforcement at startup; no sensitive data in log statements |
| Differentiators | Rate limiting extended to public API endpoints; `server.forward-headers-strategy=native` for reverse-proxy HTTPS; Spring Security default security headers verified not disabled (`X-Frame-Options`, `X-Content-Type-Options`, `X-XSS-Protection`) |
| Anti-features | TLS termination in Spring Boot; OAuth2/OIDC replacement; CSRF protection on REST API (does not apply to JWT stateless API) |

### Code Quality

| Category | Features |
|---|---|
| Table stakes | Structured logging with MDC (`requestId`, `userId`); consistent log levels by Spring profile; dead code removal per existing `CLEANUP.md` audit; complete `@Operation` / `@ApiResponse` / `@Tag` OpenAPI annotations on all controllers; consistent HTTP error responses via `ResponseUtil` everywhere |
| Differentiators | `MdcLoggingFilter` adding `requestId` as response header `X-Request-ID`; JSON log output in `prod` Spring profile; OpenAPI tags grouped by domain |
| Anti-features | Migrating to Log4j2; adding CheckStyle/PMD to the build; migrating to WebFlux |

### UI Redesign

| Category | Features |
|---|---|
| Table stakes | Consistent color palette via CSS custom properties; dark/neutral background (Vercel/Linear aesthetic); typography system (single font family, `Inter` or system stack); shared `.card` component class; primary/secondary/danger button variants; mobile responsiveness preserved across all 13 pages |
| Differentiators | Design token system in `design-system.css`; animated grade badges (A/B/C/Z with color coding); Leaflet marker clustering for the 27k-restaurant map; consistent SVG icon set (Lucide or Heroicons, replacing inline emoji) |
| Anti-features | Thymeleaf to React/Vue migration; Bootstrap to Tailwind swap mid-redesign; dark mode toggle (doubles CSS complexity); more than 2-3 micro-animations |

---

## Architecture Integration Points

### New files to create

| File | Location | Responsibility |
|---|---|---|
| `MdcLoggingFilter.java` | `com.aflokkat.filter` | `OncePerRequestFilter` `@Order(2)` — sets `requestId` and `method`/`uri` in MDC per request; clears MDC in `finally` block to prevent ThreadLocal leaks |
| `WebConfig.java` | `com.aflokkat.config` | `WebMvcConfigurer` defining the `CorsConfigurationSource` bean with explicit allowed origins and methods |
| `AbstractContainerBase.java` | `src/test/java/com/aflokkat` | Static Testcontainers singleton (MongoDB + Postgres + Redis); fires `System.setProperty()` overrides in a `static {}` block before any DAO class loads |
| `application-test.properties` | `src/test/resources` | Test-profile overrides; points at Testcontainers-assigned ports; isolates tests from production secrets |
| `design-system.css` | `src/main/resources/static/css` | All CSS custom properties (`--color-*`, `--font-*`, `--space-*`) and shared component classes (`.card`, `.btn-primary`, `.badge-grade-A`) |
| `src/test/java/com/aflokkat/e2e/` | (package) | Playwright E2E tests: `BaseE2ETest`, `LoginE2ETest`, `SearchE2ETest`, `DashboardE2ETest`, `AnalyticsE2ETest` |
| `.github/workflows/ci.yml` | project root | Five-job pipeline: build, unit-tests, integration, e2e, docker-build |
| `logback-spring.xml` | `src/main/resources` | Replaces non-functional `simplelogger.properties`; JSON via `LogstashEncoder` in `prod` profile, pattern encoder in dev |
| `.env.example` | project root | Documents all required environment variables with descriptions and no real values |

### Existing files to modify

| File | Required Change |
|---|---|
| `pom.xml` | Add Testcontainers BOM, JaCoCo plugin, Failsafe plugin, logstash-logback-encoder, Playwright; change Surefire `<argLine>` from literal to `@{argLine} -XX:+EnableDynamicAgentLoading`; add `<properties><argLine></argLine></properties>` |
| `AppConfig.java` | Add `System.getProperty()` as step 0 in `getProperty()` lookup chain (before env var check) — enables Testcontainers to inject URIs via JVM system properties |
| `SecurityConfig.java` | Add `.cors(withDefaults())` to `filterChain`; add `.headers().frameOptions().deny().contentTypeOptions()` |
| `docker-compose.yml` | Add `deploy.resources` memory limits; add `JWT_SECRET` env var injection; remove any remaining `changeme` placeholder values |
| `Dockerfile` | Add JVM flags (`-XX:MaxRAMPercentage=75`, `-Djava.security.egd=file:/dev/./urandom`); add non-root user; add `LABEL org.opencontainers.image.source` for GHCR package linking; adopt Spring Boot layered JAR extraction |
| All 13 Thymeleaf templates | Replace inline `<style>` blocks with `<link rel="stylesheet" href="/css/design-system.css">` |
| `fragments/navbar.html` | Style-only update; propagates to all 13 templates via `th:replace` |
| `RestaurantDAOIntegrationTest.java` | Rename to `RestaurantDAOContainerIT.java`; extend `AbstractContainerBase`; remove live MongoDB dependency |

### Data flow: unchanged

All existing data flows remain identical in v3.0. No new data paths, no new collections, no schema changes. `MdcLoggingFilter` observes requests in the servlet filter chain without modifying request or response data.

---

## Recommended Build Order

The ordering is driven by hard dependencies. Each phase unblocks the next and avoids broken intermediate states.

### Phase 1: Logging Infrastructure
**Rationale:** Zero external dependencies; `simplelogger.properties` is currently dead configuration (Spring Boot's Logback starter is active, not SLF4J Simple Logger); every subsequent phase benefits from structured log output immediately; lowest risk entry point for the milestone.
**Delivers:** `logback-spring.xml` (replaces `simplelogger.properties`), `MdcLoggingFilter`, JSON logging in `prod` Spring profile, plain text with `[requestId]` in dev profile. Delete `simplelogger.properties`.

### Phase 2: Maven Build Hardening (JaCoCo + Failsafe)
**Rationale:** The Surefire `argLine` fix is a hard blocker for JaCoCo and Testcontainers. Must establish a coverage baseline against the existing 27 tests before adding new test infrastructure. Pure Maven configuration — zero source code changes.
**Delivers:** JaCoCo plugin with `prepare-agent` + `report` + `check` goals; Failsafe plugin wired for `*IT.java` and `*E2ETest.java`; Surefire `argLine` changed to `@{argLine} -XX:+EnableDynamicAgentLoading`; baseline coverage report showing current line coverage.
**Critical:** If `argLine` is not patched first, adding JaCoCo causes all Mockito-instrumented controller tests to throw `StackOverflowError` on Java 25, exactly reproducing a bug that was already fixed in v2.0.

### Phase 3: Testcontainers + AppConfig Patch
**Rationale:** Makes the existing integration test self-contained and runnable in CI without a live database. Depends on Phase 2 (Failsafe must exist to run `*ContainerIT.java`).
**Delivers:** `AbstractContainerBase.java`, `AppConfig.getProperty()` `System.getProperty()` fallback (one-method patch), `application-test.properties`, rename of `RestaurantDAOIntegrationTest` to `RestaurantDAOContainerIT` extending `AbstractContainerBase`.
**Critical:** Without the `AppConfig` patch, `MongoClientFactory` binds its static singleton to `mongodb://mongodb:27017` (the Docker Compose network address) before Testcontainers starts, and all DAO calls fail silently.

### Phase 4: GitHub Actions CI Pipeline
**Rationale:** Depends on Phase 2 (JaCoCo report artifact) and Phase 3 (integration job needs Testcontainers). Once live, the pipeline acts as the automated regression guard for all subsequent phases.
**Delivers:** `.github/workflows/ci.yml` with five jobs: build (package JAR), unit-tests (Surefire + JaCoCo report upload), integration (Failsafe + Testcontainers — no `services:` block), e2e (placeholder for Phase 7), docker-build (GHCR push on `main`).
**Critical:** Do not add a `services:` block in the integration job. Testcontainers uses the Docker socket directly (available on `ubuntu-latest`). Running both GitHub service containers and Testcontainers containers simultaneously wastes the 7 GB runner RAM and risks OOM kills.

### Phase 5: Security Hardening
**Rationale:** No dependency on test infrastructure. Placing it after CI means CORS and header changes are immediately validated by the pipeline, catching Spring Security filter ordering errors early.
**Delivers:** `WebConfig.java` (CORS policy with explicit allowed origins), `SecurityConfig` updated with `.cors(withDefaults())` and security headers, `application-test.properties` with safe test secrets, `.env.example`.
**Critical:** Adding `WebConfig` alone is not sufficient for CORS. Spring Security intercepts OPTIONS preflight requests before MVC CORS resolves. Both the `CorsConfigurationSource` bean and `.cors(withDefaults())` in `SecurityConfig.filterChain()` are mandatory.

### Phase 6: Docker Production Hardening
**Rationale:** Health checks, resource limits, and Dockerfile improvements are low-risk additions. Placed after security so the full security configuration is in place before the production image is defined.
**Delivers:** `docker-compose.yml` with health checks on all 4 services, memory limits, env var cleanup; `Dockerfile` with layered JAR, non-root user, and OCI source label.
**Critical:** The OCI `LABEL org.opencontainers.image.source=https://github.com/St4r4x/restaurant-analytics` must be in the Dockerfile. Without it, the GHCR package is unlinked from the repository and `GITHUB_TOKEN` pushes fail with `permission_denied` after the initial unlinked push.

### Phase 7: E2E Tests (Playwright)
**Rationale:** Depends on the CI pipeline (Phase 4) and the Docker production hardening (Phase 6 — the E2E job runs `docker compose up` with health checks). E2E tests are the last layer of the test pyramid and must validate a working application, not construct one.
**Delivers:** `src/test/java/com/aflokkat/e2e/` with 4-5 test classes (Login, Search, Dashboard, Analytics); CI `e2e` job with Chromium install, `docker compose up`, health polling, and Failsafe execution.
**Critical:** JWT lives in `localStorage`, not cookies. Playwright's `storageState()` does not help. `BaseE2ETest` must call `/api/auth/login` via `APIRequestContext`, extract `accessToken`, and inject it via `ctx.addInitScript("localStorage.setItem('accessToken', TOKEN)")` before any authenticated page navigation. Use a `DataSeeder`-seeded test user with a known password.

### Phase 8: Code Quality (OpenAPI + Dead Code + ResponseUtil Audit)
**Rationale:** High portfolio value, no functional impact, no backend dependencies. Placed after Phase 7 to avoid template changes invalidating newly-written E2E tests.
**Delivers:** Complete `@Operation` / `@ApiResponse` / `@Tag` OpenAPI annotations across all controllers; dead code removal per `CLEANUP.md`; consistent `ResponseUtil` usage replacing ad-hoc `ResponseEntity.badRequest().build()` calls.

### Phase 9: MongoDB Indexing
**Rationale:** Pure optimisation with no functional regression risk. Placed after Testcontainers (Phase 3) so index creation can be validated in the container-backed integration test environment using a controlled fixture.
**Delivers:** `ensureIndexes()` on `RestaurantDAO` or `@PostConstruct` on `SyncService`; indexes on `camis` (unique), `boro`, `cuisine_description`, compound on `grades.score + grades.grade`; optional text index on `dba` and 2dsphere on `address.coord`.
**Critical:** Do not test aggregation correctness with Mockito — the mock will pass while real MongoDB pipeline syntax fails. Use the Testcontainers container with a controlled fixture of 20 documents covering 5 boroughs, 3 cuisines, and varied scores.

### Phase 10: UI Visual Redesign
**Rationale:** Purely presentational; zero backend risk; no other phase depends on it. Doing it last prevents rework if any earlier phase changes template structure and prevents E2E test regressions from class name changes.
**Delivers:** `design-system.css` with CSS custom properties, all 13 Thymeleaf templates updated to reference `design-system.css`, dark/neutral palette, shared card and button classes, animated grade badges, Leaflet marker clustering.
**Critical:** JS-rendered DOM nodes (restaurant cards, map markers) use hardcoded CSS class name strings in `innerHTML` assignments in `.js` files. Renaming a CSS class in `design-system.css` does NOT update those strings. Grep for any class name across both `.js` and `.html` files before renaming.

### Phase Ordering Rationale

- **Phases 1-2 are mandatory first** — Phase 1 (logging) has zero dependencies; Phase 2 (argLine fix) is a hard blocker for everything that follows. Neither can be reordered.
- **Phase 3 before Phase 4** — The CI integration job has no value if it requires a live database. Testcontainers must be in place first.
- **Phase 4 gates Phases 5-10** — Once CI is live it acts as the regression guard for all subsequent work. Any misconfiguration in Phase 5+ is caught immediately by the pipeline.
- **Phase 7 (E2E) after Phase 4 and Phase 6** — E2E tests require a working `docker compose up` stack with health checks; both must exist first.
- **Phase 10 (UI) last** — Highest change surface (13 templates); no other phase depends on it; doing it last avoids JS class name mismatches invalidating Phase 7 E2E tests.

### Research flags

Phases with well-documented patterns (skip additional research phase):
- **Phases 1, 5, 6, 8, 9, 10** — Standard Spring Boot logging, security CORS, Docker Compose, OpenAPI annotations, MongoDB indexing, and CSS design system patterns are all thoroughly documented and directly applicable.

Phases where implementation complexity warrants close attention during planning:
- **Phase 2 (JaCoCo argLine)** — The `@{argLine}` late-binding fix is non-obvious and the failure mode (silent StackOverflowError) is misleading. Plan this phase with explicit verification steps.
- **Phase 3 (Testcontainers + AppConfig patch)** — The static singleton initialization order problem is subtle. The `AbstractContainerBase` static block and `AppConfig` patch are tightly coupled; both must be implemented atomically.
- **Phase 7 (Playwright E2E in CI)** — `localStorage`-based JWT auth requires a non-standard Playwright setup pattern; the NYC API sync timing in CI requires `NYC_API_MAX_RECORDS=200` to limit to ~10 seconds.

---

## Watch Out For

### 1. JaCoCo `prepare-agent` silently drops the Mockito/Java 25 `argLine` fix (CRITICAL)
The existing Surefire config has `<argLine>-XX:+EnableDynamicAgentLoading</argLine>` as a literal string. JaCoCo's `prepare-agent` goal writes its agent flag to the Maven `argLine` property but cannot merge with a literal — it simply overwrites it. The Mockito ByteBuddy self-attachment flag disappears. All controller slice tests throw `StackOverflowError` on Java 25, reproducing a v2.0 bug in a confusing way.
**Prevention:** Change to `<argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>` and add `<properties><argLine></argLine></properties>` to the POM. This is a one-line fix and must be the first change in Phase 2.

### 2. `MongoClientFactory` static singleton binds to wrong URI before Testcontainers starts (CRITICAL)
`MongoClientFactory` calls `MongoClients.create(AppConfig.getMongoUri())` at first class load. If any test class that accesses `RestaurantDAO` initialises before `AbstractContainerBase` sets the container URI, the singleton permanently points to `mongodb://mongodb:27017` (the Docker Compose network address, not the Testcontainers ephemeral port). All DAO calls fail silently with connection refused.
**Prevention:** Two changes required together — patch `AppConfig.getProperty()` to check `System.getProperty()` as step 0, AND have `AbstractContainerBase`'s `static {}` block call `System.setProperty("MONGODB_URI", ...)` before any test instantiates a DAO. Either change alone does not work.

### 3. CORS `WebConfig` alone returns 403 on OPTIONS preflight (HIGH)
Adding `WebConfig implements WebMvcConfigurer` with `addCorsMappings()` defines a CORS policy at the MVC layer. But Spring Security's filter chain runs before MVC. An OPTIONS preflight request hits Spring Security first, which returns 403 before MVC can apply the CORS headers — even with `anyRequest().permitAll()`.
**Prevention:** Both are required: `CorsConfigurationSource` bean (defines the policy) and `http.cors(withDefaults())` in `SecurityConfig.filterChain()` (registers CORS handling at the Security filter level). If only one is present, cross-origin requests will fail with a 403 that is difficult to diagnose.

### 4. Playwright E2E authentication cannot use `storageState()` for JWT in `localStorage` (HIGH)
Playwright's standard auth pattern saves and restores browser cookies via `storageState()`. This app stores JWT in `localStorage`, not cookies. While `storageState` does save `localStorage`, it is origin-scoped — saved state is useless if the base URL differs between local and CI.
**Prevention:** In `BaseE2ETest`, call `/api/auth/login` via `APIRequestContext`, extract `accessToken` from the JSON response, and inject it via `ctx.addInitScript("localStorage.setItem('accessToken', '" + token + "')")` before the first page navigation. Use a dedicated test user seeded by `DataSeeder` with a known password, not a real account.

### 5. GHCR Docker push fails with `permission_denied` if the image package is unlinked (MEDIUM)
`GITHUB_TOKEN`-based GHCR authentication only works when the container package is linked to the owning repository. If the image name casing differs from the repository or the OCI `LABEL` is absent from the Dockerfile, the first push creates an unlinked package and subsequent pushes fail.
**Prevention:** Add `LABEL org.opencontainers.image.source=https://github.com/St4r4x/restaurant-analytics` to the Dockerfile. Use lowercase for the image name in the workflow (`ghcr.io/st4r4x/restaurant-analytics`). After the first push, verify the package appears under the repository's Packages tab.

---

## Open Questions

1. **JaCoCo coverage baseline is unknown.** The 60%/50% thresholds are reasonable starting estimates, but the actual coverage of the existing 27 tests has not been measured. If the baseline is below 60%, the threshold must be set to `baseline - 5%` on day one and raised incrementally. **Resolution:** Run `mvn jacoco:report` as the first action in Phase 2 before enabling the `check` goal.

2. **Testcontainers 1.x vs 2.x version conflict between research files.** STACK.md recommends 1.19.8; ARCHITECTURE.md references 2.0.4. **Resolution (decided here):** Use **1.19.8**. Testcontainers 2.x removed JUnit 4 support entirely. The project has JUnit 4 tests via `junit-vintage-engine` (`SecurityConfigTest`, `RestaurantDAOIntegrationTest`). Migrating those tests is out of scope for v3.0.

3. **E2E test data availability in CI.** Playwright tests against `/search`, `/map`, and analytics require restaurant data in MongoDB. On first startup, the app syncs from the NYC Open Data API; an uncapped sync can take several minutes. **Resolution:** Set `NYC_API_MAX_RECORDS=200` via env var in the E2E docker-compose environment to cap sync at ~10 seconds. The health-check polling loop should allow 120 seconds.

4. **Current Dockerfile state is unread.** Whether it already uses a multi-stage layered JAR build is unknown. **Resolution:** Read `Dockerfile` at the start of Phase 6 before writing any changes to avoid overwriting an already-correct setup.

5. **Playwright version adequacy.** 1.49.0 is pinned for stability; 1.59.0 is the current latest (April 2026). If 1.49.0 has a CI incompatibility with the Chromium version on `ubuntu-latest`, upgrade to the current stable. **Resolution:** Try 1.49.0 first; upgrade only if the browser install step fails.

---

## Confidence Assessment

| Area | Confidence | Notes |
|---|---|---|
| Stack additions | HIGH | All Maven coordinates verified on Maven Central; version constraints verified against Spring Boot 2.6.15 BOM and library release notes |
| Feature scope | HIGH | Based on direct audit of 36 validated v2.0 requirements; v3.0 additions are well-defined infrastructure concerns with no functional ambiguity |
| Architecture integration points | HIGH | Based on direct codebase inspection of `MongoClientFactory`, `AppConfig`, `SecurityConfig`, `pom.xml`, and all 27 test files |
| Pitfalls (JaCoCo argLine, MongoClientFactory, CORS) | HIGH | All three verified from official documentation and direct source inspection of the actual problematic code |
| Playwright E2E in CI | MEDIUM | Java 1.49.0 verified; `docker compose + Playwright` pattern in CI is viable but the localStorage JWT injection is non-standard |
| JaCoCo coverage baseline | LOW | Unknown until first run; 60% threshold is an estimate based on existing test count and style |
| UI redesign scope | MEDIUM | CSS design system approach is clear; exact effort depends on volume of hardcoded class names in `.js` files |

**Overall confidence:** HIGH for build order and integration points; MEDIUM for effort estimates on Phase 7 and Phase 10.

### Gaps to address

- **Coverage baseline:** Resolve by running `mvn jacoco:report` as Phase 2's first deliverable before enabling the `check` threshold.
- **Testcontainers 1.x vs 2.x conflict:** Resolved here — use 1.19.8.
- **Dockerfile current state:** Must be read at the start of Phase 6.
- **NYC API sync timing in CI:** Cap at `NYC_API_MAX_RECORDS=200` in the E2E compose environment.

---

## Sources

### Primary (HIGH confidence — direct inspection or official documentation)

- `pom.xml` (direct read 2026-04-11) — locked stack, Surefire argLine literal, Mockito 5.17.0, ByteBuddy 1.16.0 override, JUnit 4/5 mix, Bucket4j 7.6.1
- `AppConfig.java`, `MongoClientFactory.java` (direct read) — static singleton pattern, property lookup chain without `System.getProperty()`
- `SecurityConfig.java`, `RateLimitFilter.java` (direct read) — filter ordering, CORS absence, `anyRequest().permitAll()` intent
- `simplelogger.properties` (direct read) — confirmed non-functional (SLF4J Simple Logger not on classpath under Spring Boot)
- All 27 test files (direct read) — JUnit 4/5 mix confirmed, `standaloneSetup` pattern, Mockito structure
- Spring Boot 2.6.15 BOM — Logback 1.2.12, SLF4J 1.7.36, JUnit Jupiter 5.8.2, Hibernate Validator 6.2.5
- Maven Central verification — Testcontainers 1.19.8, JaCoCo 0.8.12, logstash-logback-encoder 7.3, Playwright 1.49.0
- logstash-logback-encoder GitHub release notes — 7.4 dropped Logback 1.2.x support confirmed
- JaCoCo Maven plugin official docs — `prepare-agent` late-binding `argLine` behavior
- Spring Security 5.6 CORS reference — `CorsConfigurationSource` + `.cors(withDefaults())` both required
- Bucket4j release notes — 8.11.0 raised JDK minimum to 17; 7.6.1 correct for Java 11
- GitHub Actions runner docs — Docker socket available on `ubuntu-latest` without DinD
- Docker Compose v2 official docs — health check syntax, `condition: service_healthy`
- GitHub Packages docs — GHCR `GITHUB_TOKEN` auth and OCI source label requirement

### Secondary (MEDIUM confidence)

- Playwright Java official docs — `APIRequestContext` auth pattern, `addInitScript`, headless browser lifecycle
- Testcontainers JUnit 4 `@ClassRule` + Spring context limitation — confirmed from TC GitHub Issues and Spring `@DynamicPropertySource` documentation
- Spring Boot layered JAR documentation (2.3+) — confirmed applicable to 2.6.15

---

*Research completed: 2026-04-11*
*Ready for roadmap: yes*
