# Requirements — v3.0 Production Readiness

**Milestone:** v3.0 — Production Readiness
**Goal:** Transform the academic project into a portfolio-grade, deployable application with full CI/CD, comprehensive test coverage, and production-quality code across every layer.
**Date:** 2026-04-11

---

## CI/CD

### GitHub Actions Pipeline

- [ ] **CI-01**: User can see build status on every push to `develop` and `main` via GitHub Actions
- [ ] **CI-02**: User can see the pipeline fail fast when any unit test fails (Maven build gate)
- [ ] **CI-03**: User can see separate jobs for build, unit-test, integration-test, E2E, and Docker — with clear failure attribution
- [ ] **CI-04**: User can see Maven dependencies cached across runs (keyed on pom.xml hash)
- [ ] **CI-05**: User can pull a Docker image from GHCR after every successful push to `main`
- [ ] **CI-06**: User can see Docker build validated (but not pushed) on feature/develop branches
- [ ] **CI-07**: User can see no plaintext credentials in workflow YAML (all secrets via `${{ secrets.* }}`)
- [ ] **CI-08**: User can see a JaCoCo coverage report published as a PR comment (coverage delta visible without downloading artifacts)
- [ ] **CI-09**: User can see a workflow status badge in README showing CI is active

---

## Testing

### Unit & Slice Tests

- [ ] **TEST-01**: User can run `mvn test` and see all existing 27 test files pass (no regression)
- [ ] **TEST-02**: User can see service-layer unit tests covering `AuthService` and `RestaurantService` with Mockito mocks
- [ ] **TEST-03**: User can see controller slice tests covering all auth, restaurant, inspection, and admin endpoints (HTTP status codes, JSON shape)

### Integration Tests

- [ ] **TEST-04**: User can run integration tests with real MongoDB and PostgreSQL via Testcontainers (no live database required)
- [ ] **TEST-05**: User can see existing `RestaurantDAOIntegrationTest` migrated to Testcontainers (no `localhost:27017` assumption)
- [ ] **TEST-06**: User can run integration tests in CI without any external DB dependency

### Coverage

- [ ] **TEST-07**: User can see JaCoCo code coverage report generated after `mvn test`
- [ ] **TEST-08**: User can see the build fail when instruction coverage drops below a defined threshold (baseline measured first)

### E2E Tests

- [ ] **TEST-09**: User can run Playwright browser tests covering login flow (valid + invalid credentials)
- [ ] **TEST-10**: User can run Playwright browser tests covering restaurant search and result display
- [ ] **TEST-11**: User can run Playwright browser tests covering the interactive map page load
- [ ] **TEST-12**: User can run Playwright browser tests covering controller dashboard access (role-gated)
- [ ] **TEST-13**: User can run E2E tests in CI using `docker compose` to boot the application

---

## Database

### MongoDB Indexing & Optimization

- [ ] **DB-01**: User can see indexes on `camis`, `boro`, `cuisine_description`, and `grades.score`/`grades.grade` created programmatically at startup
- [ ] **DB-02**: User can see a text index on `dba` (restaurant name) enabling `$text` search to replace slow `$regex` on full collection
- [ ] **DB-03**: User can see a 2dsphere geospatial index on `address.coord` enabling proper `$near` queries for nearby restaurants
- [ ] **DB-04**: User can see DAO list queries use field projections (return only `camis`, `dba`, `boro`, `cuisine_description`, `grades[0]`)
- [ ] **DB-05**: User can see index creation consolidated in a dedicated `ensureIndexes()` method (called at startup or sync time)

---

## Config & Secrets

- [ ] **CFG-01**: User can see no hardcoded secrets in `application.properties` or source code (all replaced with `${ENV_VAR}` references)
- [ ] **CFG-02**: User can see JWT secret read from environment (`JWT_SECRET`) with startup assertion enforcing minimum 32 chars
- [ ] **CFG-03**: User can see controller and admin signup codes read from environment (`CONTROLLER_SIGNUP_CODE`, `ADMIN_SIGNUP_CODE`)
- [ ] **CFG-04**: User can find a `src/test/resources/application-test.properties` with safe test values (no production secrets)
- [ ] **CFG-05**: User can find a `.env.example` at project root documenting all required environment variables with descriptions

---

## Docker

- [ ] **DOCKER-01**: User can see health checks verified and correct on all 4 services (app, MongoDB, Redis, PostgreSQL)
- [ ] **DOCKER-02**: User can see `depends_on: condition: service_healthy` enforced so app only starts after all DBs are ready
- [ ] **DOCKER-03**: User can see memory limits configured on all containers (`deploy: resources: limits`)
- [ ] **DOCKER-04**: User can see a multi-stage Dockerfile (builder stage with Maven, runtime stage with JRE-Alpine only)
- [ ] **DOCKER-05**: User can see the app container run as a non-root user in the Dockerfile
- [ ] **DOCKER-06**: User can see a `.dockerignore` file preventing source, tests, and git history from entering the build context
- [ ] **DOCKER-07**: User can find a `.env.example` file (shared with CFG-05) documenting how to configure the Compose stack

---

## Security

- [ ] **SEC-01**: User can see an explicit CORS policy configured in both `WebMvcConfigurer` and `SecurityConfig` (not just one)
- [ ] **SEC-02**: User can see security headers (`X-Content-Type-Options`, `X-Frame-Options`) present in all responses
- [ ] **SEC-03**: User can see `@Valid` annotations on all `@RequestBody` DTOs with appropriate `@NotBlank`, `@Size`, `@Email` constraints
- [ ] **SEC-04**: User can see `MethodArgumentNotValidException` handled globally by `@RestControllerAdvice` with structured JSON error
- [ ] **SEC-05**: User can see Bucket4j rate limiting wired on `/api/auth/login` and `/api/auth/register` endpoints
- [ ] **SEC-06**: User can see rate limiting extended to `/api/restaurants/**` public endpoints (higher limit than auth)
- [ ] **SEC-07**: User can see `server.forward-headers-strategy=native` configured for HTTPS-ready reverse proxy operation
- [ ] **SEC-08**: User can see JWT secret length assertion at startup (fails fast if < 32 chars)

---

## Code Quality

- [ ] **QA-01**: User can see `logback-spring.xml` replacing the non-functional `simplelogger.properties`
- [ ] **QA-02**: User can see structured JSON log output in production profile (via logstash-logback-encoder 7.3)
- [ ] **QA-03**: User can see a request ID (UUID) propagated via MDC and returned as `X-Request-ID` response header
- [ ] **QA-04**: User can see dead code removed per the existing CLEANUP.md audit (unused classes, endpoints, commented blocks)
- [ ] **QA-05**: User can see every controller endpoint annotated with `@Operation`, `@ApiResponse`, and `@Tag` in Swagger UI
- [ ] **QA-06**: User can see all auth endpoints marked with `@SecurityRequirement` in Swagger
- [ ] **QA-07**: User can see all controllers using `ResponseUtil` consistently (no ad-hoc `ResponseEntity.badRequest().build()`)
- [ ] **QA-08**: User can see a global `@RestControllerAdvice` mapping all exception types to structured JSON with `status`, `message`, `timestamp`

---

## UI Redesign

- [ ] **UI-01**: User can see a CSS design token system (`:root` custom properties) defining palette, spacing, and typography for all 10 pages
- [ ] **UI-02**: User can see a dark/neutral color scheme across all pages (Vercel-style: `#0f0f0f`–`#1a1a1a` background, `#f5f5f5` text)
- [ ] **UI-03**: User can see Inter (or equivalent sans-serif) as the consistent font across all pages via `--font-sans`
- [ ] **UI-04**: User can see a shared `.card` CSS class applied consistently to analytics cards, search results, and dashboard items
- [ ] **UI-05**: User can see consistent button variants (`.btn-primary`, `.btn-secondary`, `.btn-danger`) with uniform padding and radius
- [ ] **UI-06**: User can see animated grade badges (A/B/C/Z) as colored badges with CSS `@keyframes` entrance animation
- [ ] **UI-07**: User can see skeleton loading placeholders updated to match the new dark theme palette
- [ ] **UI-08**: User can see Lucide SVG icons replacing all inline emoji and Unicode characters across all templates

---

## Future Requirements (Deferred)

- Real-time notifications for bookmarked restaurant updates (requires WebSocket)
- PDF export of controller reports
- Object storage for photos (S3/GCS)
- Cross-controller report view for admin
- Bulk photo upload

---

## Out of Scope

| Item | Reason |
|------|--------|
| Kubernetes / Helm charts | Portfolio project; Docker Compose is the correct artifact |
| OAuth2 / OIDC | Full JWT system already works; replacement adds risk with no portfolio value |
| Let's Encrypt / TLS in the app | Correct place is a reverse proxy; document nginx pattern instead |
| Dark mode toggle | Doubles CSS complexity; pick one theme and commit |
| Migrating JUnit 4 tests to JUnit 5 | 27 existing tests work; migration risk with no value |
| React / Vue frontend migration | Thymeleaf is working; redesign in-place with CSS |
| Bootstrap → Tailwind migration | Full template refactor risk; keep existing framework |
| Spring Cloud Config / Vault | Overkill for portfolio scale; env vars are sufficient |
| Multi-tenant / SaaS features | Portfolio quality target, not real SaaS |

---

## Traceability

*Filled by roadmapper — maps requirements to phases.*

| REQ-ID | Phase | Status |
|--------|-------|--------|
| (to be filled) | | |
