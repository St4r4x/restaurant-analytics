# Feature Landscape

**Domain:** Production-readiness for Spring Boot 2.6.15 + MongoDB + PostgreSQL + Redis monolith
**Researched:** 2026-04-11
**Overall confidence:** HIGH — grounded in existing codebase audit, verified against official docs

---

## Scope Note

This file covers v3.0 only: CI/CD, testing, database hardening, config/secrets, Docker, code quality, security, and UI redesign. Application features (auth, roles, reports, map, analytics) are already shipped and are NOT re-researched here.

---

## CI/CD: GitHub Actions Pipeline

### Table Stakes

Features that must exist for the pipeline to be meaningful.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Trigger on push/PR to develop and main | Without triggers the pipeline never runs | Low | `on: push: branches: [develop, main]` + `pull_request` event |
| Java 11 + Maven build step | Compile the project before any tests | Low | `actions/setup-java@v4` with `distribution: temurin`, `java-version: 11` |
| `mvn test` unit test step | Gate merges on test pass | Low | Runs surefire; must pass for pipeline to continue |
| Build failure on test failure | If tests can fail silently the pipeline adds no value | Low | GitHub Actions exits non-zero automatically from `mvn test` |
| Maven dependency cache | Without caching, cold downloads take 3-5 min per run | Low | `actions/cache@v4` keyed on `pom.xml` hash |
| Docker build step | Verify the image actually builds on every push | Med | `docker/build-push-action@v6`; `push: false` on non-main branches |
| Docker push to registry on main | Deliver a deployable image as pipeline output | Med | Push to `ghcr.io` using `GITHUB_TOKEN`; no extra secrets needed |
| Secrets via GitHub Actions secrets | No plaintext credentials in workflow YAML | Low | `${{ secrets.JWT_SECRET }}` etc.; project-level secrets in repo settings |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Separate jobs: build, test, docker | Parallel execution; clear failure attribution | Med | `needs:` dependency graph between jobs |
| Integration test job using Testcontainers | Real DB under CI reveals what mocks miss | High | Requires Docker-in-Docker in the runner — covered by `ubuntu-latest` runners natively |
| E2E job with Playwright | Browser-level smoke test catches JS/template regressions | High | Requires the app container to be running; use `docker compose up -d` in a CI step before Playwright |
| JaCoCo coverage report published as PR comment | Coverage delta visible without downloading artifacts | Med | `jacoco-report` GitHub Action or upload HTML report as artifact |
| Workflow status badge in README | Shows repo is actively maintained — portfolio signal | Low | 1-line markdown badge from GitHub Actions |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Separate deployment pipeline to staging/prod | Academic/portfolio scope; no live server | Stop at Docker push to registry |
| Slack/email notifications on failure | Adds integrations with zero portfolio value | GitHub UI shows status |
| Matrix builds across Java versions | Only Java 11 is supported by the stack | Single version matrix |
| Self-hosted runners | Operational overhead; not warranted at this scale | Use `ubuntu-latest` GitHub-hosted runners |

### Feature Dependencies

```
Maven cache → build job
build job → test job
build job → docker build job
test job → jacoco report
integration test job → Testcontainers (Docker socket on runner)
E2E job → app container running in CI
```

---

## Testing: Layers and Coverage

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Existing unit tests pass cleanly | 27 test files already exist; they must stay green | Low | `mvn test` currently passes — maintain that baseline |
| Service layer unit tests (Mockito) | Services contain business logic; mocks isolate from DB | Med | `AuthService`, `RestaurantService` — mock DAO/repository dependencies |
| DAO unit tests (Mockito, no DB) | Fast feedback on DAO logic without a running MongoDB | Med | Already partially present (`RestaurantDAOImplTest`) |
| Controller slice tests (`@WebMvcTest`) | Verify HTTP contract (status codes, JSON shape) without full context | Med | Already partially present; gaps in auth/admin endpoints |
| JaCoCo coverage plugin in pom.xml | Coverage must be measurable before setting a threshold | Low | Add `jacoco-maven-plugin` to build; configure `prepare-agent` + `report` goals |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Integration tests with Testcontainers | Real MongoDB + PostgreSQL containers; eliminates assumption gap between mock and real behavior | High | `org.testcontainers:mongodb` + `org.testcontainers:postgresql`; `@DynamicPropertySource` pattern. Java 11 compatible with Testcontainers 1.19.x. Existing `RestaurantDAOIntegrationTest` assumes live `localhost:27017` — migrate it to Testcontainers |
| JaCoCo minimum threshold enforced at build | Prevents silent coverage regression | Low | `<rule><limits><limit>` with `COVEREDRATIO` 0.60; fail build if below |
| E2E tests with Playwright Java | Browser test covers login flow, search, map render | High | `com.microsoft.playwright:playwright:1.49.0` (latest stable supporting Java 11). Tests should cover: login, restaurant search, map page load, controller dashboard access |
| Test categorization: unit vs integration vs e2e | Clean separation prevents slow tests blocking fast feedback | Med | Use JUnit 5 `@Tag` or Maven profiles: `unit`, `integration`, `e2e` |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Testing MongoDB aggregation pipelines with mocks | Mock can pass while real pipeline syntax fails | Use Testcontainers for any aggregation test |
| 100% coverage target | Getters/setters, generated code — meaningless to cover | 60% instruction coverage is a credible threshold for this size |
| E2E tests for every page | Time-consuming; brittle; diminishing returns beyond smoke tests | Cover 3-5 critical paths only: login, search, map, report filing |
| JUnit 4 → JUnit 5 migration | 27 existing tests use JUnit 4 via `junit-vintage-engine`; migration risk with no new value | Keep JUnit 4 tests as-is; write new tests in JUnit 5 style |

### Feature Dependencies

```
JaCoCo plugin in pom.xml → coverage threshold enforcement
Testcontainers dependencies → integration test migration
Playwright dependency → E2E tests
E2E tests → running app (CI or local Docker Compose)
```

---

## Database: MongoDB Indexing and Optimization

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Index on `camis` (restaurant unique ID) | Every restaurant lookup by `camis` does a full collection scan without it | Low | `db.restaurants.createIndex({ camis: 1 }, { unique: true })` |
| Index on `boro` (borough) | `by-borough` aggregation and borough filter on map both scan without this | Low | `db.restaurants.createIndex({ boro: 1 })` |
| Index on `cuisine_description` | `worst-cuisines` and `popular-cuisines` aggregate on this field | Low | `db.restaurants.createIndex({ cuisine_description: 1 })` |
| Index on `grades.grade` + `grades.score` | `at-risk` pipeline filters by grade letter and score | Med | Compound index: `{ "grades.score": 1, "grades.grade": 1 }` — array field, use with caution |
| Index creation in SyncService or startup | Indexes must be created programmatically, not manually | Low | Call `createIndex()` in `SyncService.ensureIndexes()` or `@PostConstruct` on DAO |
| `explain()` review on the two heaviest aggregation pipelines | Without profiling, index choices are guesses | Med | Run `explain("executionStats")` on `worst-cuisines` and `at-risk` pipelines; document results |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Text index on `dba` (restaurant name) | Enables full-text search with `$text` operator; much faster than `$regex` on full collection | Med | `db.restaurants.createIndex({ dba: "text" })` — only one text index allowed per collection; name it |
| 2dsphere geospatial index on `address.coord` | Enables `$near` / `$geoWithin` queries for the nearby restaurants feature; current implementation uses bounding box filter | Med | `db.restaurants.createIndex({ "address.coord": "2dsphere" })` — requires coord stored as `[lng, lat]` GeoJSON Point or legacy pair |
| Projection in DAO queries | Returning full documents for list views wastes bandwidth and deserialization time | Low | Add `projection()` to list queries: return only `camis`, `dba`, `boro`, `cuisine_description`, `grades[0]` |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Index on every field | Write overhead; memory pressure; MongoDB discourages over-indexing | Index only fields in query filters and sort keys |
| Migrating from raw driver to Spring Data MongoDB | Raw aggregation pipelines are already written and tested; migration risk with no new capability | Keep `mongodb-driver-sync` raw driver |
| Moving restaurant data to PostgreSQL | MongoDB fits the document shape (nested grades array) | Keep dual-DB architecture |

### Feature Dependencies

```
ensureIndexes() method → runs at startup or sync time
Text index → search endpoint using $text
2dsphere index → nearby restaurants using $near
explain() profiling → informs final index selection
```

---

## Config and Secrets Management

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| No hardcoded secrets in source | Basic security hygiene; required for public portfolio repo | Low | Audit all `application.properties` for secrets; replace with env var references |
| `application.properties` reads from environment variables | Docker Compose already injects `MONGODB_URI`, `REDIS_HOST` etc.; this must be consistent | Low | Use `${ENV_VAR:default}` Spring property syntax throughout |
| JWT secret from environment | `jwt.secret` is currently in `application.properties`; minimum 32 chars enforced | Low | `jwt.secret=${JWT_SECRET}` — fail fast on startup if blank |
| Controller and Admin signup codes from environment | `changeme` is the current Docker Compose default — must be replaced | Low | `CONTROLLER_SIGNUP_CODE` + `ADMIN_SIGNUP_CODE` as required env vars |
| Separate `application-test.properties` for test profile | Tests must not depend on production secrets | Low | Create `src/test/resources/application-test.properties` with safe test values |
| `.env.example` file at project root | Documents required env vars for new developers and for portfolio reviewers | Low | List all required vars with description, no real values |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Fail-fast on missing required secrets | Application should not start silently with blank JWT secret | Low | `@Value("${jwt.secret}") private String jwtSecret;` + `@PostConstruct` assertion, or Spring Boot `@Validated` config properties |
| `docker-compose.override.yml` pattern for local dev | Keeps production `docker-compose.yml` clean; local values in override file | Low | `.gitignore` the override file; document pattern in README |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| HashiCorp Vault / AWS Secrets Manager | Operational overhead not warranted for portfolio scale | Simple env vars + Docker secrets are sufficient |
| Dotenv in production (`dotenv-java` dependency) | `dotenv-java` is already in pom.xml for dev convenience; should not be the production config mechanism | In Docker/CI, rely on env vars injected by the runtime; dotenv file is `.gitignore`d |
| Spring Cloud Config Server | Adds a service dependency; overkill for a single monolith | application.properties + env vars is the correct tier |

### Feature Dependencies

```
.env.example → developer onboarding
application-test.properties → test suite isolation
JWT secret env var → AuthService startup validation
```

---

## Docker: Production Compose

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Health checks on all 4 services | Already partially done (`docker-compose.yml` has healthchecks); verify all are correct | Low | App: `curl -f http://localhost:8080/api/restaurants/health`. MongoDB: `mongosh --eval "db.adminCommand('ping')"`. Redis: `redis-cli ping`. Postgres: `pg_isready -U restaurant` |
| `depends_on: condition: service_healthy` | Prevents app from starting before DB is ready | Low | Already set; verify it is correct for all 4 services |
| No default/placeholder secrets in compose | `changeme` must be replaced by env var references | Low | `${CONTROLLER_SIGNUP_CODE}` etc. in compose env block |
| Named volumes for persistent data | MongoDB, PostgreSQL, Redis data must survive container restarts | Low | Already present; verify all 4 data paths are covered |
| `restart: unless-stopped` | Containers recover from crashes without manual intervention | Low | Already set; verify it is on all services |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Memory limits on containers | Prevents one container starving others on a shared host | Low | `deploy: resources: limits: memory: 512m` for app; `256m` for MongoDB/Redis/Postgres |
| Production Dockerfile: multi-stage build | Reduces final image size; removes build tools from runtime | Med | Stage 1: `maven:3.9-eclipse-temurin-11` build; Stage 2: `eclipse-temurin:11-jre-alpine` runtime. Current `Dockerfile` may already do this — audit it |
| Non-root user in Dockerfile | Security best practice; required by some registries | Low | `RUN addgroup -S app && adduser -S app -G app` + `USER app` |
| `.dockerignore` file | Prevents source code / test classes from being copied into image context | Low | Exclude `src/`, `target/`, `.git/`, `*.md` |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Kubernetes / Helm charts | Portfolio project; one compose file is the right artifact | Docker Compose is sufficient |
| Separate compose files per environment | Adds complexity; use override pattern instead | `docker-compose.yml` + `docker-compose.override.yml` for local |
| MongoDB authentication in compose | Adds setup friction with no security benefit inside a Docker network | Rely on network isolation within the compose network |

### Feature Dependencies

```
Health checks → depends_on condition: service_healthy
Multi-stage Dockerfile → smaller production image
Non-root user → multi-stage build (user created in builder stage)
Memory limits → requires Docker Compose v3.9+ (already using `docker compose` plugin)
```

---

## Code Quality

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Structured logging with MDC | Unstructured logs are hard to trace in multi-request scenarios | Med | Add `org.slf4j.MDC` context (requestId, userId) in a Spring filter; use `LoggerFactory.getLogger()` consistently (already in use) |
| Log levels consistent with environment | DEBUG logs in production generate noise and expose internals | Low | `logging.level.root=INFO` in production properties; `DEBUG` only for `com.aflokkat` in dev |
| Remove dead code identified in CLEANUP.md | Dead code confuses future readers and inflates coverage reports | Med | Follow existing `CLEANUP.md` audit; remove unused classes, endpoints, and commented-out blocks |
| Complete OpenAPI annotations on all endpoints | `springdoc-openapi-ui 1.8.0` already in pom.xml — annotations must be complete | Med | Every controller method needs `@Operation`, `@ApiResponse` with correct status codes; auth endpoints need `@SecurityRequirement` |
| Consistent HTTP error responses | Clients must be able to parse errors reliably | Med | Global `@RestControllerAdvice` with `ResponseUtil`; every exception maps to a structured JSON body with `status`, `message`, `timestamp` |
| `ResponseUtil` used consistently | `ResponseUtil` exists; ensure it is actually called everywhere | Low | Audit all controllers; replace ad-hoc `ResponseEntity.badRequest().build()` with `ResponseUtil` calls |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Request ID propagated via MDC | Every log line in a request chain shares the same ID — essential for debugging | Med | Generate UUID in `JwtAuthenticationFilter` or a dedicated `RequestIdFilter`; put in MDC and response header `X-Request-ID` |
| `logback-spring.xml` for structured output | JSON log lines are parseable by log aggregators; pattern console is not | Med | Use `net.logstash.logback:logstash-logback-encoder:7.4` for JSON output in a Spring profile; keep plain text for local dev. **Note:** `logstash-logback-encoder 7.4` supports Java 11 and Logback 1.4.x (shipped with Spring Boot 2.6.15) |
| OpenAPI grouped by tag | Large API is easier to navigate when endpoints are tagged by domain | Low | `@Tag(name = "restaurants")`, `@Tag(name = "auth")` etc. on controllers |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Migrating from Logback to Log4j2 | No benefit; adds risk; Spring Boot 2.6 defaults to Logback | Keep Logback |
| CheckStyle or PMD enforced in build | Time-consuming to configure; not warranted for portfolio | Manual code review is sufficient |
| Moving from `@RestController` to reactive WebFlux | Requires full rewrite; no portfolio value at this scale | Keep blocking Spring MVC |

### Feature Dependencies

```
MDC request ID → structured logging
logback-spring.xml → structured JSON output (needs logstash-logback-encoder)
OpenAPI annotations → accurate Swagger UI
ResponseUtil → consistent error JSON shape
```

---

## Security

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| CORS policy configured explicitly | Without explicit CORS, browsers block cross-origin requests (and a wildcard `*` is a security flag) | Low | `CorsConfigurationSource` bean with explicit `allowedOrigins`; `.cors(withDefaults())` in `SecurityConfig`. For portfolio: allow `http://localhost:8080` and any deployment domain |
| Input validation on all request bodies | Missing validation allows malformed data into the DB | Med | `@Valid` + `@NotBlank`, `@Size`, `@Email` annotations on all `@RequestBody` DTOs; `@RestControllerAdvice` handles `MethodArgumentNotValidException` |
| Rate limiting on auth endpoints | Brute-force protection on `/api/auth/login` and `/api/auth/register` | Low | `Bucket4j 7.6.1` already in pom.xml — configure a filter/interceptor applying a token bucket on auth paths. **Note:** Bucket4j 7.x required for Java 11; do not upgrade to 8.x |
| JWT secret length enforcement | A short JWT secret makes tokens trivially forgeable | Low | Assert `jwt.secret.length() >= 32` at startup |
| No sensitive data in logs | Email addresses and passwords must not appear in logs | Low | Audit log statements in `AuthService`; remove any `log.debug("password={}")` patterns |
| Spring Security `anyRequest().authenticated()` audit | Current config uses `anyRequest().permitAll()` with client-side guards — document why and confirm it is intentional | Low | It is intentional (JWT app, no server session); document this clearly in `SecurityConfig` |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Rate limiting on all public API endpoints | Prevents data scraping of the 27k restaurant dataset | Med | Extend Bucket4j filter to cover `/api/restaurants/**`; higher limit than auth endpoints (e.g. 100 req/min per IP) |
| HTTPS-ready config | Application should work behind a TLS-terminating reverse proxy | Low | `server.forward-headers-strategy=native` in `application.properties`; document how to run behind nginx with SSL |
| Security headers via Spring Security | `X-Content-Type-Options`, `X-Frame-Options`, `X-XSS-Protection` | Low | Spring Security adds these by default; verify they are not being disabled anywhere |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Let's Encrypt / TLS termination in the app | Correct place is a reverse proxy (nginx) or a CDN | Document the nginx reverse proxy pattern; don't terminate TLS in Spring Boot |
| OAuth2 / OIDC integration | Full auth system already works; replacement adds risk with no portfolio value | Keep existing JWT system |
| CSRF protection on the REST API | REST APIs with JWT auth don't use cookies; CSRF doesn't apply | Keep `csrf().disable()` as it already is |
| Fail2ban / IP blocking | Infrastructure concern; not warranted for portfolio | Rate limiting via Bucket4j is sufficient |

### Feature Dependencies

```
CORS policy → explicit allowed origins (needs deployment URL)
Input validation → @Valid on DTOs + MethodArgumentNotValidException handler
Rate limiting → existing Bucket4j dependency (already in pom.xml)
HTTPS-ready → forward-headers-strategy property + nginx docs
```

---

## UI: Visual Redesign

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Consistent color palette across all 10 pages | Current UI mixes ad-hoc colors; professional look requires a system | Med | Define CSS custom properties: `--color-bg`, `--color-surface`, `--color-primary`, `--color-text`; apply globally via `base.html` layout |
| Dark or neutral background (not stark white) | Portfolio evaluators associate white backgrounds with unfinished academic work; dark/neutral reads as intentional | Med | Target: `#0f0f0f` to `#1a1a1a` background; `#f5f5f5` text — Vercel-style neutral-dark |
| Typography system | One heading font + one body font, consistent sizing scale | Low | Use a system font stack or a single Google Font (e.g. `Inter`) for all pages; define `--font-sans` |
| Card component consistent treatment | Cards appear on analytics, search, dashboard — they must look identical | Med | Shared `.card` CSS class: `background: var(--color-surface)`, `border-radius: 8px`, subtle border or shadow |
| Button variants: primary / secondary / danger | Inconsistent buttons make the UI feel unpolished | Low | `.btn-primary`, `.btn-secondary`, `.btn-danger`; consistent padding and border-radius |
| Responsive grid that does not break on mobile | Already mobile-responsive; must remain so after redesign | Med | Audit CSS after redesign; test at 375px and 768px breakpoints |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Design token system via CSS variables | Single-source-of-truth for colors/spacing; easy to iterate | Low | 10-15 variables in `:root` cover all needs |
| Animated grade badges | Grade letter (A/B/C/Z) styled as a colored badge with subtle entrance animation | Low | CSS `@keyframes` on badge; green for A, yellow for B, red for C/Z |
| Skeleton loading redesigned to match new palette | Current skeletons may clash with new dark theme | Low | Update skeleton color from light grey to `var(--color-surface-elevated)` |
| Consistent icon set (Lucide or Heroicons SVG) | Mix of emoji and Unicode characters reads as unfinished | Med | Replace all inline emoji/icon characters with a single SVG icon set; Lucide is free, MIT-licensed, and tree-shakeable |
| Map marker clustering | 27k markers on the map without clustering is unusable; Leaflet.markercluster is the standard plugin | Med | Already using Leaflet; add `leaflet.markercluster` JS plugin |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Migrating from Thymeleaf to React/Vue | Full frontend rewrite; not warranted; Thymeleaf is working | Redesign in-place using CSS and JS within existing templates |
| CSS framework swap (Bootstrap → Tailwind) | Full template refactor; high breakage risk | Keep existing Bootstrap or systematically remove it; don't introduce a new utility framework mid-redesign |
| Dark mode toggle | Doubles CSS complexity; not worth it for a portfolio project | Pick one theme (dark/neutral) and commit |
| Animation-heavy UI | Distracts from functionality; slows perceived performance | Limit to 2-3 micro-animations (badge entrance, skeleton pulse, toast slide-in) |
| New Thymeleaf layout fragments | Already have `base.html` layout; adding fragments mid-redesign risks template regression | Use the existing layout; adjust CSS within it |

### Feature Dependencies

```
CSS design tokens → all per-component styles
Typography system → heading + body font choices
Card component → analytics page, search results, dashboard
Map marker clustering → Leaflet.markercluster plugin
Icon set → replaces inline emoji/Unicode across all templates
```

---

## Feature Dependencies (Cross-Category)

```
GitHub Actions CI → Maven cache, Java setup
CI unit test job → existing test suite stays green
CI integration test job → Testcontainers in pom.xml
CI E2E job → Playwright in pom.xml + app container in CI
JaCoCo plugin → coverage threshold enforcement
MongoDB indexes → SyncService.ensureIndexes() or DAO @PostConstruct
.env.example → no hardcoded secrets in application.properties
application-test.properties → Testcontainers test isolation
Docker health checks → all 4 services healthy before app starts
Bucket4j rate limiting → already in pom.xml (7.6.1)
CORS policy → SecurityConfig CorsConfigurationSource bean
Input validation → @Valid on DTOs + global exception handler
CSS design tokens → entire UI redesign
```

---

## MVP Recommendation

### Phase ordering rationale

1. **Config/secrets first** — every subsequent phase needs clean env var handling; hardcoded secrets in source are a blocker for a public portfolio repo
2. **Testing infrastructure second** — JaCoCo + Testcontainers foundation; existing tests must stay green throughout
3. **CI/CD third** — pipeline is useful only once tests are trustworthy; pipeline builds on the test layer
4. **Database/indexes fourth** — optimization; no functional regression risk; measurable via explain()
5. **Docker/infra fifth** — health checks + resource limits are low-risk additions to working compose
6. **Security sixth** — CORS + validation + rate limiting; mostly additive to existing security layer
7. **Code quality seventh** — logging + dead code + OpenAPI; high value but no functional impact
8. **UI redesign last** — highest change surface; no backend dependencies; can be done independently

### Deferred (out of v3 scope)

| Feature | Reason |
|---------|--------|
| Real-time notifications for bookmarked restaurants | Requires WebSocket infrastructure; deferred in PROJECT.md |
| PDF export of reports | Deferred in PROJECT.md |
| Object storage for photos (S3/GCS) | Out of scope in PROJECT.md |
| OAuth2 / OIDC | No portfolio value given working JWT system |
| Kubernetes manifests | Portfolio project; Docker Compose is the correct artifact |

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| CI/CD (GitHub Actions) | HIGH | Official GitHub docs verified; standard Maven workflow patterns well-established |
| Testing (JUnit/Mockito/Testcontainers) | HIGH | Testcontainers 1.19.x verified for Java 11; existing test infrastructure audited |
| Testing (Playwright Java) | MEDIUM | Playwright Java 1.49 confirmed; E2E in CI with compose is viable but has setup complexity |
| JaCoCo | HIGH | Official Maven plugin docs; 60% threshold is conventional for projects this size |
| MongoDB indexing | HIGH | Standard MongoDB index patterns; confirmed against current query shapes in codebase |
| Docker health checks | HIGH | Current docker-compose.yml audited; patterns confirmed against Docker docs |
| Config/secrets | HIGH | Spring Boot env var interpolation is standard; no surprises |
| Security (CORS, rate limiting) | HIGH | Spring Security CORS docs verified; Bucket4j 7.6.1 already in pom.xml |
| Security (input validation) | HIGH | Bean Validation API verified; existing ValidationUtil can be augmented |
| UI redesign | MEDIUM | Aesthetic direction (Vercel/Linear) is clear; exact component scope depends on current template audit |

---

## Sources

- Spring Boot 2.6 official reference — actuator, logging, security, CORS
- GitHub Actions official docs — Java/Maven workflow, Docker push workflow
- Docker Compose official reference — healthcheck syntax
- Testcontainers official guide — Spring Boot integration pattern
- Playwright Java official docs — `com.microsoft.playwright:playwright:1.58.0`
- Resilience4j / Bucket4j documentation — rate limiter configuration
- SpringDoc official site — version 1.8.0 confirmed for Spring Boot 2.x
- Codebase audit — `pom.xml`, `docker-compose.yml`, 27 existing test files, `SecurityConfig`, `application.properties`
