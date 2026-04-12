# Phase 13: Config & Docker Hardening - Context

**Gathered:** 2026-04-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Remove all hardcoded secrets from source code and configuration files, and harden the Docker Compose stack with:
- All credentials (JWT, signup codes, DB password) injected via `.env` / environment variables — zero hardcoded values in tracked files
- Startup fail-fast assertion if JWT_SECRET is missing or < 32 chars
- Memory resource limits on all 4 containers
- Dockerfile updated to Java 25 + JRE-Alpine (fixes critical version mismatch with pom.xml)
- Non-root user in Dockerfile runtime stage
- `.dockerignore` to keep build context lean
- `.env.example` documenting all required environment variables
- `src/test/resources/application-test.properties` with safe test values

Health checks and `depends_on: condition: service_healthy` are **already in place** in `docker-compose.yml` — they need verification and memory limit additions only, not a rewrite.

</domain>

<decisions>
## Implementation Decisions

### Local Dev Secret Injection
- **D-01:** `.env` file is the local developer injection mechanism. Developer copies `.env.example` → `.env`, fills in values, and `mvn spring-boot:run` works without Docker. AppConfig already loads `.env` via dotenv (before `System.getenv()` and `application.properties` in priority order). `.env` is gitignored.

### JWT Secret Removal & Startup Assertion
- **D-02:** Remove `jwt.secret=...` from `application.properties` entirely. Replace `AppConfig.getJwtSecret()` default fallback (`"changeit-please-change-it"`) with a startup assertion: if `JWT_SECRET` env var is not set or its length is < 32 characters, throw `IllegalStateException` with a descriptive message (e.g., `"JWT_SECRET environment variable is not set or too short (minimum 32 characters). Set it in your .env file or environment."`).
- **D-03:** The assertion lives in `AppConfig.getJwtSecret()` — fails before Spring context fully loads, consistent with existing AppConfig pattern.
- **D-04:** Signup codes (`CONTROLLER_SIGNUP_CODE`, `ADMIN_SIGNUP_CODE`) are NOT asserted at startup — null = disabled is the intentional AuthService behavior.

### docker-compose Secrets Scope
- **D-05:** ALL credentials move to `.env` — JWT_SECRET, CONTROLLER_SIGNUP_CODE, ADMIN_SIGNUP_CODE, POSTGRES_PASSWORD, POSTGRES_USER, POSTGRES_DB, SPRING_DATASOURCE_PASSWORD. `docker-compose.yml` references them via `${VAR}` syntax. Single source of truth for all secrets. This removes `CONTROLLER_SIGNUP_CODE: changeme` (hardcoded) and `POSTGRES_PASSWORD: restaurant` (hardcoded) from the Compose file.

### Dockerfile Java Version Fix
- **D-06:** Upgrade Dockerfile builder stage to `FROM maven:3.9-eclipse-temurin-25` and runtime stage to `FROM eclipse-temurin:25-jre-alpine`. Fixes critical mismatch: pom.xml compiles with `java.version=25` but current Dockerfile uses Java 21, which would throw `UnsupportedClassVersionError` at startup. Alpine satisfies DOCKER-04.

### Claude's Discretion
- Memory limit values: app `512m`, MongoDB `512m`, Redis `128m`, PostgreSQL `256m` — reasonable baselines for a portfolio stack.
- `.dockerignore` content: exclude `src/`, `target/`, `.git/`, `.planning/`, `*.md`, `*.log`, `.env`, `docker-compose*.yml` — keeps build context to pom.xml + assembled JAR.
- `application-test.properties` content: safe non-production values — a 64-char dummy JWT secret, `localhost` DB/Redis URLs, empty signup codes.
- The existing `application.properties` comment block around `jwt.secret` is removed along with the value; a comment noting the env var is sufficient.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §Config & Secrets — CFG-01, CFG-02, CFG-03, CFG-04, CFG-05
- `.planning/REQUIREMENTS.md` §Docker — DOCKER-01, DOCKER-02, DOCKER-03, DOCKER-04, DOCKER-05, DOCKER-06, DOCKER-07

### Phase Goal & Success Criteria
- `.planning/ROADMAP.md` §Phase 13 — 5 success criteria (grep-zero check, fail-fast startup, docker compose up, .env.example, non-root + Alpine)

### Files to Read Before Modifying
- `src/main/resources/application.properties` — current properties; shows what needs to be env-var'd
- `docker-compose.yml` — already has health checks + depends_on; only needs memory limits + secret removal
- `Dockerfile` — two-stage build confirmed; needs Java 21→25, jammy→alpine, non-root user addition
- `src/main/java/com/st4r4x/config/AppConfig.java` — getJwtSecret() default fallback to remove; assertion to add
- `src/main/java/com/st4r4x/service/AuthService.java` — signup code @Value injection (already correct, no changes needed)
- `src/main/java/com/st4r4x/security/JwtUtil.java` — uses AppConfig.getJwtSecret() on line 20; assertion in AppConfig will propagate

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AppConfig.getProperty()` — already checks `System.getenv()` → dotenv → `application.properties` in that order. The `.env` injection approach requires no changes to this lookup chain.
- `docker-compose.yml` — `depends_on: condition: service_healthy` and all 4 health checks (app, MongoDB, Redis, Postgres) are already correctly wired. This phase adds `deploy: resources: limits` and swaps hardcoded secrets for `${VAR}` references.
- `Dockerfile` — two-stage build already in place. Phase 13 changes: line 1 `FROM maven:3.9-eclipse-temurin-25`, line 16 `FROM eclipse-temurin:25-jre-alpine`, add `RUN addgroup -S appuser && adduser -S appuser -G appuser` and `USER appuser`.

### Established Patterns
- `AppConfig` uses static methods with `getProperty(key, default)` — startup assertion fits naturally as `throw new IllegalStateException(...)` inside `getJwtSecret()` when value is null or `.length() < 32`.
- No Spring `@Value` usage in AppConfig — assertion must be in `AppConfig.getJwtSecret()` itself, not in a bean.

### Integration Points
- `JwtUtil.java:20` — calls `AppConfig.getJwtSecret()` during Spring context initialization. Assertion will bubble up as application startup failure before the context completes.
- `AuthService.java` — `@Value("${controller.signup.code:#{null}}")` and `@Value("${admin.signup.code:#{null}}")` already correct; signup codes need matching entries in `.env.example` (nullable).
- `application.properties` — `spring.datasource.password=restaurant` must also be replaced with `${SPRING_DATASOURCE_PASSWORD}` (matches `SPRING_DATASOURCE_PASSWORD` in docker-compose).

</code_context>

<specifics>
## Specific Ideas

- `.env.example` should include descriptive comments for each variable (e.g., `# Minimum 32 characters — generate with: openssl rand -hex 32`).
- The CONTEXT.md note on Java 25 Alpine: `eclipse-temurin:25-jre-alpine` is the correct image tag. If it doesn't exist at build time, fall back to `eclipse-temurin:25-jdk-alpine` and open an issue — do not silently fall back to Java 21.
- The success criterion "grepping for `changeme` returns zero matches" is satisfied by removing `CONTROLLER_SIGNUP_CODE: changeme` from `docker-compose.yml` and replacing with `${CONTROLLER_SIGNUP_CODE}`.

</specifics>

<deferred>
## Deferred Ideas

- Rotating JWT secrets without downtime — future security hardening phase.
- Docker Secrets (Swarm/K8s) instead of env vars — out of scope per REQUIREMENTS.md (Spring Cloud Config / Vault excluded).
- `.env` file validation script (pre-commit hook) — could be a CI-09 addition.

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 13-config-docker-hardening*
*Context gathered: 2026-04-12*
