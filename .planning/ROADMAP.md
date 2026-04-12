# Roadmap: Restaurant Hygiene Control App

## Milestones

- ✅ **v1.0 — Foundation** — Phases 1-4 (shipped 2026-04-01): auth, controller reports API, customer discovery UI, integration tests → [archive](.planning/milestones/v1.0-ROADMAP.md)
- ✅ **v2.0 — Full Product** — Phases 5-10 (shipped 2026-04-11): controller dashboard UI, public analytics page, dual landing/home routing, map filters + uncontrolled tracker, UX polish (pagination/skeletons/toasts/mobile), admin tools → [archive](milestones/v2.0-ROADMAP.md)
- 🔄 **v3.0 — Production Readiness** — Phases 11-20 (started 2026-04-11): CI/CD pipeline, test coverage, security hardening, Docker production config, code quality, UI redesign

## Overview

**v1.0** transformed the Spring Boot NYC restaurant analytics API into a dual-role web application with JWT auth, controller report filing, customer search/map/bookmark UI, and a hardened security layer.

**v2.0** completes the product: controllers get a full UI workspace, a public analytics dashboard surfaces city-wide hygiene trends, the homepage is redesigned for both anonymous visitors and authenticated users, discovery is enhanced with map filters and an uncontrolled-restaurants tracker, and the whole app gets UX polish (pagination, skeletons, toasts, mobile).

**v3.0** transforms the academic project into a portfolio-grade, deployable application: structured logging first (every subsequent phase benefits immediately), Maven build tooling hardened (JaCoCo + Failsafe argLine fix), config and Docker production-hardened, Testcontainers integration tests that run without a live database, GitHub Actions CI pipeline consuming all test infrastructure, security hardening (CORS, headers, rate limiting, input validation), code quality sweep (OpenAPI, dead code, ResponseUtil), Playwright E2E smoke tests, unit and controller slice tests, and finally a full visual redesign with a dark/neutral design system.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

### Previous milestones (phases 1-10)

- [x] **Phase 1: Role Infrastructure** - Extend JWT auth and Spring Security for CUSTOMER/CONTROLLER roles (completed 2026-03-29)
- [x] **Phase 2: Controller Reports** - CRUD API for internal inspection reports stored in PostgreSQL (completed 2026-03-30)
- [x] **Phase 3: Customer Discovery** - Restaurant search, detail page, and interactive map UI (completed 2026-03-31)
- [x] **Phase 4: Integration Polish** - Cross-role security tests, ownership invariant tests, rate limiting (completed 2026-04-01)
- [x] **Phase 5: Controller Workspace** - Controller dashboard UI (completed 2026-04-03)
- [x] **Phase 6: Analytics & Stats** - Public analytics page (completed 2026-04-03)
- [x] **Phase 7: Homepage & Navigation** - Dual landing/home routing, persistent navbar (completed 2026-04-03)
- [x] **Phase 8: Discovery Enhancement** - Map filters, uncontrolled tracker, nearby restaurants, sort controls (completed 2026-04-10)
- [x] **Phase 9: UX Polish** - Pagination, skeleton loading, toast notifications, mobile responsive (completed 2026-04-10)
- [x] **Phase 10: Admin Tools** - Sync controls, CSV export, aggregate report stats (completed 2026-04-11)

### v3.0 phases (11-20)

- [x] **Phase 11: Logging Infrastructure** - Replace non-functional simplelogger.properties with structured Logback, add request ID propagation via MDC (completed 2026-04-11)
- [x] **Phase 12: Maven Build Hardening** - Wire JaCoCo coverage report and Failsafe plugin with correct argLine late-binding to unblock all test infrastructure (completed 2026-04-12)
- [x] **Phase 13: Config & Docker Hardening** - Eliminate all hardcoded secrets, production-grade Docker Compose with health checks and resource limits (completed 2026-04-12)
- [ ] **Phase 14: Testcontainers Integration Tests** - Make existing integration test self-contained against real MongoDB and PostgreSQL via Testcontainers
- [ ] **Phase 15: GitHub Actions CI Pipeline** - Five-job pipeline (build, unit-test, integration, E2E placeholder, Docker) consuming phases 12-14 artifacts
- [ ] **Phase 16: Security Hardening** - Explicit CORS policy, security headers, input validation, rate limiting extension, HTTPS-ready config
- [ ] **Phase 17: Code Quality & MongoDB Indexing** - Dead code removal, complete OpenAPI docs, ResponseUtil consistency, MongoDB indexes at startup
- [ ] **Phase 18: E2E Tests (Playwright)** - Browser smoke tests covering login, search, map, and controller dashboard via docker compose
- [ ] **Phase 19: Unit & Controller Tests** - Service and DAO unit tests with Mockito, controller slice tests covering all endpoints
- [ ] **Phase 20: UI Visual Redesign** - CSS design token system, dark/neutral palette, shared component classes, animated grade badges, Lucide icons

## Phase Details

> v1.0 and v2.0 phase details archived. See [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md) and [milestones/v2.0-ROADMAP.md](milestones/v2.0-ROADMAP.md).

---

### Phase 11: Logging Infrastructure
**Goal**: Every request produces structured, identifiable log output and carries a traceable request ID through all service layers
**Depends on**: Phase 10 (v2.0 complete)
**Requirements**: QA-01, QA-02, QA-03
**Success Criteria** (what must be TRUE):
  1. Starting the application with `--spring.profiles.active=prod` produces JSON log lines readable by log aggregators (Logstash-compatible), not plaintext
  2. Starting the application without a prod profile produces human-readable plaintext log lines that include `[requestId]` on every line generated by a request
  3. Every HTTP response includes an `X-Request-ID` header containing a UUID that matches the `requestId` field in the corresponding log lines
  4. No log output contains raw stack traces from `simplelogger.properties` — that file is deleted and its configuration is inert
**Plans**: 2 plans
Plans:
- [x] 11-01-PLAN.md — Add logstash-logback-encoder 7.3 dependency, create logback-spring.xml (prod JSON / dev plaintext with requestId), delete simplelogger.properties
- [x] 11-02-PLAN.md — Create RequestIdFilter (@Component @Order(0)) with 5-test TDD suite covering UUID generation, X-Request-ID header, MDC cleanup, and client-value rejection

---

### Phase 12: Maven Build Hardening
**Goal**: The Maven build produces a JaCoCo coverage report after every `mvn test` run and fails the build when coverage drops below a defined threshold, without breaking any existing Mockito-instrumented tests
**Depends on**: Phase 11
**Requirements**: TEST-07, TEST-08
**Success Criteria** (what must be TRUE):
  1. Running `mvn test` generates a JaCoCo HTML report at `target/site/jacoco/index.html` showing line and branch coverage metrics
  2. Running `mvn test` with coverage below the configured threshold exits with a non-zero code and a clear JaCoCo threshold violation message — not a cryptic StackOverflowError
  3. All 28 existing test files pass with zero regressions after the JaCoCo and Failsafe plugins are added (the argLine late-binding fix prevents Mockito instrumentation failure)
  4. The coverage threshold is documented in `pom.xml` with a comment explaining that it reflects the measured baseline, not an aspirational target
**Plans**: 2 plans
Plans:
- [x] 12-01-PLAN.md — Fix Surefire @{argLine} late-binding, @Ignore RestaurantDAOIntegrationTest, wire JaCoCo prepare-agent + report, measure baseline
- [x] 12-02-PLAN.md — Add JaCoCo check goal with measured threshold + Failsafe plugin with @{argLine} late-binding

---

### Phase 13: Config & Docker Hardening
**Goal**: The application has no hardcoded secrets anywhere in source or configuration, and the Docker Compose stack starts reliably with health-checked dependencies, resource limits, and a multi-stage production image
**Depends on**: Phase 12
**Requirements**: CFG-01, CFG-02, CFG-03, CFG-04, CFG-05, DOCKER-01, DOCKER-02, DOCKER-03, DOCKER-04, DOCKER-05, DOCKER-06, DOCKER-07
**Success Criteria** (what must be TRUE):
  1. Cloning the repository and grepping for `changeme`, `secret`, or any raw JWT-secret string in `application.properties` and all Java source files returns zero matches
  2. Starting the app without the `JWT_SECRET` environment variable set causes the application to refuse to start with a descriptive error message (not a silent null or a runtime NullPointerException)
  3. Running `docker compose up` starts all four services in dependency order (app waits for MongoDB, PostgreSQL, and Redis health checks to pass) without manual retry
  4. A new developer can find `.env.example` at the project root, copy it to `.env`, fill in the values, and run the stack without reading any other documentation
  5. The production Docker image runs as a non-root user and is built in two stages (builder with Maven, runtime with JRE-Alpine only), resulting in an image smaller than a single-stage build
**Plans**: 3 plans
Plans:
- [x] 13-01-PLAN.md — Test infrastructure (application-test.properties, JwtUtilTest reflection patch) + AppConfig startup assertion + application.properties secret removal + .env.example
- [x] 13-02-PLAN.md — Dockerfile upgrade (maven:3.9-eclipse-temurin-25, eclipse-temurin:25-jre-alpine, non-root appuser) + .dockerignore
- [x] 13-03-PLAN.md — docker-compose.yml: replace hardcoded secrets with ${VAR} references, add memory limits to all 4 services

---

### Phase 14: Testcontainers Integration Tests
**Goal**: Integration tests run against real MongoDB and PostgreSQL containers with no live database required, and are runnable in CI without any external service dependency
**Depends on**: Phase 13 (AppConfig env var chain, application-test.properties)
**Requirements**: TEST-04, TEST-05, TEST-06
**Success Criteria** (what must be TRUE):
  1. Running `mvn failsafe:integration-test` on a machine with Docker installed but no running MongoDB or PostgreSQL starts containers automatically, runs tests, and tears them down — without any `localhost:27017` configuration
  2. The existing `RestaurantDAOIntegrationTest` is renamed and extended so it passes in CI without requiring a pre-seeded database on the runner
  3. A developer can delete their local MongoDB and PostgreSQL installations and still run the full integration test suite successfully
**Plans**: 4 plans
Plans:
- [ ] 14-01-PLAN.md — pom.xml: add Testcontainers 1.19.8 (3 artifacts) + Failsafe plugin; fix Surefire argLine; add AppConfig.getProperty() System.getProperty() tier-0
- [ ] 14-02-PLAN.md — RestaurantDAOIT.java: rename + rewrite RestaurantDAOIntegrationTest with TC mongo:7.0, @ClassRule, 60-doc seed, 14 assertions
- [ ] 14-03-PLAN.md — UserRepositoryIT.java: new test with TC postgres:15-alpine + mongo:7.0, @SpringBootTest + ApplicationContextInitializer pattern, 4 assertions
- [ ] 14-04-PLAN.md — Final verification: mvn verify green + CHANGELOG.md update

---

### Phase 15: GitHub Actions CI Pipeline
**Goal**: Every push to `develop` or `main` triggers an automated pipeline with separate, clearly attributed jobs for build, unit tests, integration tests, and Docker, and every successful `main` push publishes a Docker image to GHCR
**Depends on**: Phase 12 (JaCoCo report), Phase 14 (Testcontainers integration job)
**Requirements**: CI-01, CI-02, CI-03, CI-04, CI-05, CI-06, CI-07, CI-08, CI-09
**Success Criteria** (what must be TRUE):
  1. Pushing a commit with a failing unit test to `develop` makes the GitHub Actions checks page show a red status with the failure attributed to the `unit-tests` job — not a generic build failure
  2. Pushing a commit to `main` that passes all jobs results in a new Docker image appearing under the repository's Packages tab on GitHub
  3. Opening the repository's README shows a green CI badge linked to the workflow run page
  4. Pushing a commit to `develop` completes the pipeline run without downloading any Maven dependencies from the internet (cache hit on pom.xml hash)
  5. No workflow YAML file contains any literal secret value — all credentials are referenced via `${{ secrets.* }}`
  6. A JaCoCo coverage summary is visible as a comment on pull requests targeting `develop` without requiring the reviewer to download an artifact
**Plans**: TBD

---

### Phase 16: Security Hardening
**Goal**: The application enforces an explicit CORS policy, adds security response headers, validates all request inputs with structured error responses, and fails fast on misconfigured secrets
**Depends on**: Phase 15 (CI acts as regression guard for security config changes)
**Requirements**: SEC-01, SEC-02, SEC-03, SEC-04, SEC-05, SEC-06, SEC-07, SEC-08
**Success Criteria** (what must be TRUE):
  1. Sending a cross-origin OPTIONS preflight request to `/api/restaurants/**` from an unlisted origin returns HTTP 403 — not HTTP 200 with a wildcard `Access-Control-Allow-Origin` header
  2. Every HTTP response from the application includes `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY` headers
  3. POSTing a login request with an empty password field returns HTTP 400 with a JSON body containing `status`, `message`, and `timestamp` fields — not a 500 or an empty body
  4. Sending more than the configured number of login requests from a single IP within one minute returns HTTP 429 — and the same limit applies independently to restaurant search endpoints at a higher threshold
  5. Starting the application with a `JWT_SECRET` shorter than 32 characters causes startup failure with a message naming the problem — not a silent truncation or a runtime error on the first token decode
**Plans**: TBD

---

### Phase 17: Code Quality & MongoDB Indexing
**Goal**: The codebase has no dead code identified in CLEANUP.md, all controller endpoints are fully documented in Swagger, error responses are structured consistently, and MongoDB queries benefit from purpose-built indexes
**Depends on**: Phase 14 (indexes can be validated in Testcontainers environment)
**Requirements**: QA-04, QA-05, QA-06, QA-07, QA-08, DB-01, DB-02, DB-03, DB-04, DB-05
**Success Criteria** (what must be TRUE):
  1. Opening Swagger UI at `/swagger-ui.html` shows every endpoint grouped by domain tag, with a description, at least one response code, and — for authenticated endpoints — a lock icon indicating the security requirement
  2. Any HTTP error response from the application (400, 401, 403, 404, 429, 500) returns a JSON body with exactly the fields `status`, `message`, and `timestamp` — no raw Spring error pages, no empty bodies
  3. Running `explain()` on the restaurant search query against a populated MongoDB collection shows an index scan (IXSCAN), not a collection scan (COLLSCAN)
  4. Grepping the codebase for classes and endpoints listed in CLEANUP.md returns zero matches — dead code is removed
  5. The restaurant list DAO query returns only the projected fields (`camis`, `dba`, `boro`, `cuisine_description`, `grades[0]`), not full documents — verifiable by inspecting the query in the MongoDB profiler or via a debug log line
**Plans**: TBD

---

### Phase 18: E2E Tests (Playwright)
**Goal**: Automated browser tests cover the four critical user flows — login, restaurant search, map page, and controller dashboard access — and run in CI against a live docker compose stack
**Depends on**: Phase 15 (CI pipeline), Phase 13 (Docker health checks)
**Requirements**: TEST-09, TEST-10, TEST-11, TEST-12, TEST-13
**Success Criteria** (what must be TRUE):
  1. Running `mvn failsafe:integration-test -Pe2e` on a machine with Docker and the Chromium browser installed executes login, search, map, and dashboard tests and reports pass/fail per test — not a single aggregate failure
  2. The CI pipeline's `e2e` job boots the full application stack via `docker compose up`, waits for health checks to pass, runs all Playwright tests, and tears down the stack — without leaving orphaned containers
  3. The login test covers both valid credentials (redirects to home) and invalid credentials (shows an error message) as separate test cases
  4. The controller dashboard test verifies that a CUSTOMER-role JWT cannot access `/dashboard` (redirect or error visible in the browser) — not just an API 403
**Plans**: TBD

---

### Phase 19: Unit & Controller Tests
**Goal**: Service-layer business logic and all REST controller endpoints are covered by focused unit and slice tests that run in milliseconds with no database or network dependency
**Depends on**: Phase 12 (JaCoCo coverage threshold enforced, argLine fix in place)
**Requirements**: TEST-01, TEST-02, TEST-03
**Success Criteria** (what must be TRUE):
  1. Running `mvn test` passes all 27 existing test files and all newly added tests with zero failures — no regression from new test infrastructure
  2. A developer can see Mockito-based tests for `AuthService` (register, login, token refresh) and `RestaurantService` (search, stats, by-borough) that assert behavior on inputs without starting a Spring context
  3. A developer can inspect `@WebMvcTest` slice tests for `AuthController`, `RestaurantController`, `InspectionController`, and `UserController` that assert HTTP status codes and response JSON shape without a running database
**Plans**: TBD

---

### Phase 20: UI Visual Redesign
**Goal**: All 13 application pages share a consistent dark/neutral design system with a CSS token foundation, unified component classes, animated grade badges, and SVG icons replacing all inline emoji
**Depends on**: Phase 18 (E2E tests established — UI class name changes must not silently break them)
**Requirements**: UI-01, UI-02, UI-03, UI-04, UI-05, UI-06, UI-07, UI-08
**Success Criteria** (what must be TRUE):
  1. Opening any of the 13 pages in a browser shows a dark background in the `#0f0f0f`–`#1a1a1a` range with `#f5f5f5` body text — no page has a white or light background
  2. The browser DevTools shows all color, spacing, and typography values resolved from CSS custom properties defined in `design-system.css` — no inline style attributes or hardcoded hex values in template markup
  3. Inspecting a restaurant card on the search page, the analytics page, and the dashboard shows the same `.card` CSS class with identical visual appearance across all three contexts
  4. Viewing a restaurant with a grade A badge shows a colored badge with a CSS entrance animation (fade-in or slide-up) — and the same badge style applies consistently to grades B, C, and Z with distinct colors
  5. Grepping all Thymeleaf templates and static JS files for emoji characters (Unicode range U+1F300–U+1FAFF) returns zero matches — all icons are Lucide SVG elements
**Plans**: TBD
**UI hint**: yes

---

## Progress

**Execution Order:**
v1.0: Phases 1 → 2 → 3 → 4
v2.0: Phase 5 → (6 ∥ 7) → 8 → 9 → 10
v3.0: Phase 11 → 12 → 13 → (14 ∥ 15*) → 16 → 17 → 18 → 19 → 20
  *Phase 15 depends on 12 and 14; 14 depends on 13. Sequential in practice.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Role Infrastructure | 4/4 | Complete | 2026-03-29 |
| 2. Controller Reports | 3/3 | Complete | 2026-03-31 |
| 3. Customer Discovery | 4/4 | Complete | 2026-03-31 |
| 4. Integration Polish | 4/4 | Complete | 2026-04-01 |
| 5. Controller Workspace | 2/2 | Complete | 2026-04-03 |
| 6. Analytics & Stats | 3/3 | Complete | 2026-04-03 |
| 7. Homepage & Navigation | 4/4 | Complete | 2026-04-03 |
| 8. Discovery Enhancement | 5/5 | Complete | 2026-04-10 |
| 9. UX Polish | 5/5 | Complete | 2026-04-10 |
| 10. Admin Tools | 3/3 | Complete | 2026-04-11 |
| 11. Logging Infrastructure | 2/2 | Complete    | 2026-04-11 |
| 12. Maven Build Hardening | 2/2 | Complete    | 2026-04-12 |
| 13. Config & Docker Hardening | 3/3 | Complete    | 2026-04-12 |
| 14. Testcontainers Integration Tests | 0/4 | Not started | - |
| 15. GitHub Actions CI Pipeline | 0/? | Not started | - |
| 16. Security Hardening | 0/? | Not started | - |
| 17. Code Quality & MongoDB Indexing | 0/? | Not started | - |
| 18. E2E Tests (Playwright) | 0/? | Not started | - |
| 19. Unit & Controller Tests | 0/? | Not started | - |
| 20. UI Visual Redesign | 0/? | Not started | - |
