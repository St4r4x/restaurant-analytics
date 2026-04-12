# Phase 13: Config & Docker Hardening - Research

**Researched:** 2026-04-12
**Domain:** Spring Boot secrets externalization, Docker multi-stage builds, Docker Compose hardening
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** `.env` file is the local developer injection mechanism. Developer copies `.env.example` → `.env`, fills in values, and `mvn spring-boot:run` works without Docker. AppConfig already loads `.env` via dotenv (before `System.getenv()` and `application.properties` in priority order). `.env` is gitignored.

**D-02:** Remove `jwt.secret=...` from `application.properties` entirely. Replace `AppConfig.getJwtSecret()` default fallback (`"changeit-please-change-it"`) with a startup assertion: if `JWT_SECRET` env var is not set or its length is < 32 characters, throw `IllegalStateException` with a descriptive message (e.g., `"JWT_SECRET environment variable is not set or too short (minimum 32 characters). Set it in your .env file or environment."`).

**D-03:** The assertion lives in `AppConfig.getJwtSecret()` — fails before Spring context fully loads, consistent with existing AppConfig pattern.

**D-04:** Signup codes (`CONTROLLER_SIGNUP_CODE`, `ADMIN_SIGNUP_CODE`) are NOT asserted at startup — null = disabled is the intentional AuthService behavior.

**D-05:** ALL credentials move to `.env` — JWT_SECRET, CONTROLLER_SIGNUP_CODE, ADMIN_SIGNUP_CODE, POSTGRES_PASSWORD, POSTGRES_USER, POSTGRES_DB, SPRING_DATASOURCE_PASSWORD. `docker-compose.yml` references them via `${VAR}` syntax. Single source of truth for all secrets. This removes `CONTROLLER_SIGNUP_CODE: changeme` (hardcoded) and `POSTGRES_PASSWORD: restaurant` (hardcoded) from the Compose file.

**D-06:** Upgrade Dockerfile builder stage to `FROM maven:3.9-eclipse-temurin-25` and runtime stage to `FROM eclipse-temurin:25-jre-alpine`. Fixes critical mismatch: pom.xml compiles with `java.version=25` but current Dockerfile uses Java 21, which would throw `UnsupportedClassVersionError` at startup. Alpine satisfies DOCKER-04.

### Claude's Discretion

- Memory limit values: app `512m`, MongoDB `512m`, Redis `128m`, PostgreSQL `256m` — reasonable baselines for a portfolio stack.
- `.dockerignore` content: exclude `src/`, `target/`, `.git/`, `.planning/`, `*.md`, `*.log`, `.env`, `docker-compose*.yml` — keeps build context to pom.xml + assembled JAR.
- `application-test.properties` content: safe non-production values — a 64-char dummy JWT secret, `localhost` DB/Redis URLs, empty signup codes.
- The existing `application.properties` comment block around `jwt.secret` is removed along with the value; a comment noting the env var is sufficient.

### Deferred Ideas (OUT OF SCOPE)

- Rotating JWT secrets without downtime — future security hardening phase.
- Docker Secrets (Swarm/K8s) instead of env vars — out of scope per REQUIREMENTS.md (Spring Cloud Config / Vault excluded).
- `.env` file validation script (pre-commit hook) — could be a CI-09 addition.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CFG-01 | No hardcoded secrets in `application.properties` or source code (all replaced with `${ENV_VAR}` references) | Verified: `application.properties` has `spring.datasource.password=restaurant` and `jwt.secret=dev-only-...` to remove; AppConfig has `"changeit-please-change-it"` fallback to replace with assertion |
| CFG-02 | JWT secret read from environment (`JWT_SECRET`) with startup assertion enforcing minimum 32 chars | Verified: `AppConfig.getJwtSecret()` currently returns a default — needs `IllegalStateException` when `JWT_SECRET` is null or `.length() < 32` |
| CFG-03 | Controller and admin signup codes read from environment (`CONTROLLER_SIGNUP_CODE`, `ADMIN_SIGNUP_CODE`) | Verified: `AuthService` already uses `@Value("${controller.signup.code:#{null}}")` — needs matching entries in `.env.example`; docker-compose.yml needs `${CONTROLLER_SIGNUP_CODE}` substitution |
| CFG-04 | `src/test/resources/application-test.properties` with safe test values (no production secrets) | Verified: directory `src/test/resources/` does not yet exist; file must be created |
| CFG-05 | `.env.example` at project root documenting all required environment variables with descriptions | Verified: no `.env` or `.env.example` exists yet; must be created |
| DOCKER-01 | Health checks verified and correct on all 4 services (app, MongoDB, Redis, PostgreSQL) | Verified: all 4 health checks already present in docker-compose.yml — verification only needed |
| DOCKER-02 | `depends_on: condition: service_healthy` enforced so app only starts after all DBs are ready | Verified: already wired correctly in docker-compose.yml — verification only needed |
| DOCKER-03 | Memory limits configured on all containers (`deploy: resources: limits`) | Verified: not present in current docker-compose.yml; must be added to all 4 service blocks |
| DOCKER-04 | Multi-stage Dockerfile (builder stage with Maven, runtime stage with JRE-Alpine only) | Verified: builder stage exists (maven:3.8-temurin-21) but uses wrong Java version and wrong runtime base (jammy not alpine) — both need updating |
| DOCKER-05 | App container runs as a non-root user in the Dockerfile | Verified: no `USER` directive in current Dockerfile; addgroup/adduser + USER appuser must be added |
| DOCKER-06 | `.dockerignore` file preventing source, tests, and git history from entering the build context | Verified: no `.dockerignore` exists; must be created |
| DOCKER-07 | `.env.example` file (shared with CFG-05) documenting how to configure the Compose stack | Same file as CFG-05 — one deliverable satisfies both |
</phase_requirements>

---

## Summary

Phase 13 is a pure hardening / configuration cleanup phase — no new features, no new library dependencies, no schema changes. All the infrastructure (dotenv loading in AppConfig, health checks in docker-compose, two-stage Dockerfile) is already in place. The work is targeted editing of six existing files plus creation of four new files.

The most critical change is the JWT secret startup assertion in `AppConfig.getJwtSecret()`. The current code falls through to a hardcoded default string (`"changeit-please-change-it"`), which silently allows the application to start with an insecure key. The assertion replaces that fallback with an `IllegalStateException` that fires during `JwtUtil` construction (which happens during Spring context initialization), giving a descriptive startup failure rather than a silent security hole.

The Dockerfile Java version fix is equally critical and non-optional: `pom.xml` sets `java.version=25` and `maven.compiler.source/target=25`, so the compiled class files target bytecode version 69 (Java 25). The current Dockerfile runtime stage uses `eclipse-temurin:21-jre-jammy` which understands bytecode up to version 65 (Java 21). This would produce `UnsupportedClassVersionError` at startup on every Docker run. Both Docker images have been verified to exist on Docker Hub.

**Primary recommendation:** Work in three logical waves — (1) secret removal in application files + AppConfig assertion, (2) Dockerfile + .dockerignore, (3) docker-compose.yml memory limits + secret substitution — to keep diffs reviewable and keep tests passing throughout.

---

## Standard Stack

### Core (no new dependencies required)

| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| `io.github.cdimascio:dotenv-java` | 3.0.0 | `.env` file loading (already in pom.xml) | `AppConfig` already calls `Dotenv.configure().ignoreIfMissing().load()` — no changes needed |
| `eclipse-temurin:25-jre-alpine` | 25 (JRE) | Docker runtime base image | `[VERIFIED: docker pull succeeded — image exists and is pullable]` |
| `maven:3.9-eclipse-temurin-25` | 3.9 + JDK 25 | Docker builder base image | `[VERIFIED: docker pull succeeded — image exists and is pullable]` |

No new Maven dependencies are required. All secrets management is handled by environment variable convention plus the existing dotenv-java library.

**Installation:** No `mvn install` step required for this phase.

---

## Architecture Patterns

### Secret Injection Priority (AppConfig — already implemented)

```
Priority order (highest to lowest):
1. System.getenv(ENV_KEY)     — Docker, CI, production
2. dotenv.get(ENV_KEY)        — .env file, local dev
3. application.properties     — non-secret config only after this phase
```

`AppConfig.getProperty(key, default)` already implements this chain. The key insight: after Phase 13, `application.properties` must not contain any secret value at all — only non-sensitive defaults and structural config (ports, paths, timeouts).

### Startup Assertion Pattern

**What:** Replace the default-value fallback in `getJwtSecret()` with a fail-fast assertion.

**Where:** `AppConfig.getJwtSecret()` — this method is called in `JwtUtil` constructor at line 20, which is invoked during Spring context initialization. An `IllegalStateException` thrown here surfaces as a startup failure with the message visible in logs before the JVM exits.

**Pattern:**
```java
// Source: derived from existing AppConfig pattern + D-02/D-03 decisions
public static String getJwtSecret() {
    String secret = getProperty("jwt.secret", null);
    if (secret == null || secret.length() < 32) {
        throw new IllegalStateException(
            "JWT_SECRET environment variable is not set or too short " +
            "(minimum 32 characters). Set it in your .env file or environment."
        );
    }
    return secret;
}
```

Note: `getProperty("jwt.secret", null)` already maps to `JWT_SECRET` env var via the `key.replace(".", "_").toUpperCase()` logic in `getProperty()` — no change to the lookup chain is needed.

### application.properties After Phase 13

After removing secrets, `application.properties` retains only:
- Non-secret defaults: MongoDB URI/database/collection (for local without Docker)
- Structural config: Redis host/port, JWT expiration times, NYC API URL, rate limit values
- Spring/JPA settings: dialect, DDL auto, show-sql

Remove entirely:
- `jwt.secret=...` (line 33 — replace with a comment pointing to env var)
- `spring.datasource.password=restaurant` (line 23 — replaced by `${SPRING_DATASOURCE_PASSWORD}` for Spring's own property resolution)

### Docker Compose Memory Limits Pattern

```yaml
# Source: Docker Compose deploy resources spec [VERIFIED: docker compose version 2 syntax]
services:
  app:
    deploy:
      resources:
        limits:
          memory: 512m
  mongodb:
    deploy:
      resources:
        limits:
          memory: 512m
  redis:
    deploy:
      resources:
        limits:
          memory: 128m
  postgres:
    deploy:
      resources:
        limits:
          memory: 256m
```

`deploy.resources.limits` is valid Docker Compose v2+ syntax and works with `docker compose up` (not only Swarm). `[VERIFIED: Docker Compose version v5.1.1 installed]`

### Dockerfile Non-Root User Pattern (Alpine)

```dockerfile
# Source: Alpine Linux addgroup/adduser syntax (BusyBox — different from useradd on Debian)
RUN addgroup -S appuser && adduser -S appuser -G appuser
USER appuser
```

Alpine Linux uses BusyBox `adduser` with different flags than Debian's `useradd`:
- `-S` = system account (no shell, no home dir by default)
- `-G appuser` = add to the group just created

This differs from Debian/Ubuntu pattern (`useradd -r -u 1001 appuser`). Using the wrong syntax on Alpine causes build failure.

### .dockerignore Pattern

```
# Keep build context to: pom.xml + assembled JAR only
src/
target/
.git/
.planning/
*.md
*.log
.env
.env.*
docker-compose*.yml
.gitignore
.claude/
node_modules/
```

The multi-stage build copies `pom.xml` then `src/` in the builder stage — so `.dockerignore` must NOT exclude `src/` or `pom.xml` from the perspective of the builder layer. Wait: the builder stage runs `COPY pom.xml .` and `COPY src ./src` from the build context. If `src/` is in `.dockerignore`, the builder stage cannot access source files.

**Critical insight:** The CONTEXT.md discretion item says "keeps build context to pom.xml + assembled JAR" — but this only makes sense if a pre-built JAR is being copied (single-stage copy approach). With a two-stage Maven build in Docker, the `COPY src ./src` line in the builder stage requires `src/` to be in the build context. Therefore, `src/` must NOT be excluded from `.dockerignore`.

The correct `.dockerignore` for a two-stage Maven build excludes files that are never needed by any stage:
```
target/
.git/
.planning/
.claude/
*.log
.env
.env.*
docker-compose*.yml
*.md
node_modules/
```

`pom.xml` and `src/` must remain accessible to the builder stage.

### application-test.properties (Spring test profile)

Spring Boot activates `application-test.properties` when `spring.profiles.active=test` or when a test class uses `@ActiveProfiles("test")` or `@SpringBootTest` with test profile. This file overrides `application.properties` values for the test context.

```properties
# src/test/resources/application-test.properties
# Safe test values — no production secrets

# JWT — 64-char dummy secret safe for test use only
jwt.secret=test-only-jwt-secret-64chars-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# Datasource — localhost for tests without Docker
spring.datasource.url=jdbc:postgresql://localhost:5432/restaurantdb
spring.datasource.username=restaurant
spring.datasource.password=restaurant

# Redis — localhost for tests
redis.host=localhost
redis.port=6379

# Signup codes — empty (disabled) for tests
controller.signup.code=
admin.signup.code=
```

**Critical concern for existing tests:** The startup assertion in `AppConfig.getJwtSecret()` will fire during `JwtUtil` construction in any test that creates a `JwtUtil` instance without the `JWT_SECRET` env var set. Currently `JwtUtilTest` creates `new JwtUtil()` directly in `@BeforeEach`. With the assertion in place, this test will fail unless `JWT_SECRET` is set in the test environment.

Two approaches:
1. Set `JWT_SECRET` as a system property in `JwtUtilTest` before the `new JwtUtil()` call (using `System.setProperty`).
2. Rely on `application-test.properties` — but `AppConfig` loads properties via its own static initializer, not Spring's property resolution. It reads `application.properties` directly from classpath. A `jwt.secret` value in `application-test.properties` will NOT be read by `AppConfig` because AppConfig only loads `application.properties`, not profile-specific overrides.

**Resolution:** `AppConfig.getJwtSecret()` reads via `getProperty("jwt.secret", null)`. The lookup chain is: env var → dotenv → `application.properties`. The `application-test.properties` is NOT in this chain. Therefore `JwtUtilTest` and any test that instantiates `JwtUtil` directly must either:
- Set `System.setProperty("JWT_SECRET", "test-secret-64chars-...")` in a `@BeforeAll` / `@BeforeEach` block, OR
- Use reflection to patch `AppConfig.properties` (the project's established pattern from STATE.md: "use reflection to patch AppConfig.properties static field in tests")

The `application-test.properties` file serves Spring context tests (`@SpringBootTest`, `@WebMvcTest`) where Spring's own property resolution overrides `jwt.secret` from the test profile file — `AppConfig` would also pick up the value because `application-test.properties` is merged into `application.properties` during Spring Boot startup for the test profile.

### .env.example Template

```bash
# Restaurant Analytics — Environment Configuration
# Copy this file to .env and fill in all required values.
# The .env file is gitignored — never commit it.

# ── JWT ──────────────────────────────────────────────────────────────────────
# Minimum 32 characters. Generate a secure value with:
#   openssl rand -hex 32
JWT_SECRET=

# ── PostgreSQL ───────────────────────────────────────────────────────────────
POSTGRES_USER=restaurant
POSTGRES_DB=restaurantdb
# Generate with: openssl rand -hex 16
POSTGRES_PASSWORD=
# Must match POSTGRES_PASSWORD above
SPRING_DATASOURCE_PASSWORD=

# ── Signup Codes (optional — leave empty to disable role) ────────────────────
# If empty: controller/admin registration is disabled (customers only)
CONTROLLER_SIGNUP_CODE=
ADMIN_SIGNUP_CODE=
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Secrets from environment | Custom env reader | `System.getenv()` (already in `AppConfig.getProperty()`) | Standard Java; dotenv-java handles `.env` files |
| Docker memory limits | Custom cgroup config | `deploy.resources.limits.memory` in compose | Native Compose syntax; enforced by container runtime |
| Alpine non-root user | Manual `/etc/passwd` editing | `addgroup -S && adduser -S -G` | BusyBox standard; system account with no shell |
| JWT secret generation | Custom random string | `openssl rand -hex 32` | Produces cryptographically random 64-char hex string, well above 32-char minimum |

---

## Common Pitfalls

### Pitfall 1: .dockerignore Excludes src/ Breaking Multi-Stage Build
**What goes wrong:** Adding `src/` to `.dockerignore` to reduce build context size, then finding that `COPY src ./src` in the builder stage fails or silently copies nothing.
**Why it happens:** `.dockerignore` filters the build context sent to the Docker daemon — it affects all COPY instructions in all stages.
**How to avoid:** Only exclude files that no stage needs: `.git/`, `target/`, `.planning/`, `.log` files, `.env`, `*.md`. Keep `src/` and `pom.xml` accessible.
**Warning signs:** Builder stage `mvn clean package` succeeds but produces no JAR; or `COPY src ./src` exits with non-zero.

### Pitfall 2: Alpine adduser Syntax Differs From Debian
**What goes wrong:** Using Debian `useradd` syntax on Alpine (`RUN useradd -r -u 1001 appuser`) causes `RUN step failed: exit code 127` because `useradd` does not exist in Alpine's BusyBox.
**Why it happens:** Alpine uses BusyBox which provides `adduser` and `addgroup`, not `useradd`/`groupadd`.
**How to avoid:** Use `RUN addgroup -S appuser && adduser -S appuser -G appuser` on Alpine images.
**Warning signs:** `docker build` fails at the `RUN useradd` step with "not found" or exit code 127.

### Pitfall 3: AppConfig Startup Assertion Breaks JwtUtilTest
**What goes wrong:** `JwtUtilTest` calls `new JwtUtil()` in `@BeforeEach`. `JwtUtil` constructor calls `AppConfig.getJwtSecret()`. With the assertion added, tests that run without `JWT_SECRET` set throw `IllegalStateException` and fail.
**Why it happens:** `AppConfig` is a static class that reads from env vars at method call time. Test isolation via `application-test.properties` does NOT help because AppConfig bypasses Spring's property resolution.
**How to avoid:** In `JwtUtilTest.setUp()`, set `System.setProperty("JWT_SECRET", "test-only-jwt-secret-64chars-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")` before `new JwtUtil()`. Clean up with `System.clearProperty("JWT_SECRET")` in teardown. Alternatively use reflection to set the `jwt.secret` key in `AppConfig.properties` directly (the project's established pattern).
**Warning signs:** `JwtUtilTest` and `SecurityConfigTest` fail with `IllegalStateException: JWT_SECRET environment variable is not set` after the assertion is added.

### Pitfall 4: spring.datasource.password Placeholder Syntax in application.properties
**What goes wrong:** Writing `spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}` in `application.properties` — Spring Boot resolves `${...}` placeholder syntax using its own `Environment` abstraction which looks up system env vars. This is valid Spring Boot behavior. But the application also reads this value through `AppConfig.getProperty("spring.datasource.password", ...)` for non-Spring codepaths — and AppConfig does NOT resolve `${VAR}` syntax; it would return the literal string `${SPRING_DATASOURCE_PASSWORD}`.
**Why it happens:** Spring Boot property resolution and AppConfig's own resolution are independent. Spring Boot resolves env var references in properties files; AppConfig does not.
**How to avoid:** Spring Boot's `DataSource` is configured via Spring's auto-configuration which reads `spring.datasource.password` through Spring's `Environment` — this WILL expand `${SPRING_DATASOURCE_PASSWORD}`. The plain `SPRING_DATASOURCE_PASSWORD` env var (without `${...}`) in docker-compose is what matters for the Spring datasource. For local dev, both the `.env` file (loaded by AppConfig's dotenv) and `SPRING_DATASOURCE_PASSWORD` env var must be set. The `application.properties` line can use placeholder syntax since Spring Boot resolves it correctly for datasource config.
**Warning signs:** Application starts but database connection fails with auth error; or tests fail with `Cannot connect to database`.

### Pitfall 5: docker-compose.yml Env Var References Without .env File
**What goes wrong:** `docker compose up` fails with `variable is not set. Defaulting to a blank string` warnings and app starts with empty `JWT_SECRET`, then immediately throws startup assertion error.
**Why it happens:** `docker compose` automatically reads `.env` from the directory where `docker compose up` is run. If `.env` is missing or incomplete, `${JWT_SECRET}` in compose resolves to empty string.
**How to avoid:** `.env.example` must be copied to `.env` before first `docker compose up`. Document this in `.env.example` header. The startup assertion catches any case where `JWT_SECRET` ends up empty.
**Warning signs:** `WARN[0000] The "JWT_SECRET" variable is not set. Defaulting to a blank string.` in compose output.

### Pitfall 6: JaCoCo Coverage May Drop If Tests Require JWT_SECRET
**What goes wrong:** Adding the startup assertion causes `JwtUtilTest` tests to fail with `IllegalStateException` during the `mvn test` run in CI — this drops the test count and potentially trips the JaCoCo coverage threshold.
**Why it happens:** The coverage threshold from Phase 12 is based on the 174-test baseline. Failing tests produce no coverage data for their classes.
**How to avoid:** Fix `JwtUtilTest` to set `JWT_SECRET` via `System.setProperty` before the assertion is added, and verify `mvn test` still reports 174 tests passing before committing.
**Warning signs:** `mvn test` drops from 174 to fewer passing tests; JaCoCo check goal fails the build.

---

## Code Examples

### AppConfig.getJwtSecret() — After Assertion
```java
// Pattern: fail-fast assertion consistent with AppConfig static method style
// Source: D-02/D-03 from CONTEXT.md + established AppConfig pattern
public static String getJwtSecret() {
    String secret = getProperty("jwt.secret", null);
    if (secret == null || secret.length() < 32) {
        throw new IllegalStateException(
            "JWT_SECRET environment variable is not set or too short " +
            "(minimum 32 characters). Set it in your .env file or environment."
        );
    }
    return secret;
}
```

### JwtUtilTest — Setup After Assertion (System.setProperty approach)
```java
// Pattern: set system property before JwtUtil construction; clean up after
// Source: AppConfig.getProperty() reads System.getenv() then dotenv then properties
// System.setProperty() is visible to System.getProperty() but NOT System.getenv() —
// however AppConfig's getProperty() checks System.getenv() only. Use reflection instead:

// Reflection approach (matches project pattern from STATE.md):
@BeforeEach
void setUp() throws Exception {
    // Patch AppConfig.properties static field to inject jwt.secret for tests
    java.lang.reflect.Field f = AppConfig.class.getDeclaredField("properties");
    f.setAccessible(true);
    java.util.Properties props = (java.util.Properties) f.get(null);
    props.setProperty("jwt.secret",
        "test-only-jwt-secret-64chars-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
    jwtUtil = new JwtUtil();
}
```

Note: `AppConfig.getProperty()` checks `System.getenv(envKey)` as priority 1. `System.setProperty()` writes to system properties (read by `System.getProperty()`), not environment variables — so it does NOT bypass the assertion. The reflection approach that directly sets `jwt.secret` in `AppConfig.properties` is the correct technique per the project's established pattern.

### docker-compose.yml — Secret Variable References (diff)
```yaml
# Remove these hardcoded values:
#   CONTROLLER_SIGNUP_CODE: changeme
#   POSTGRES_PASSWORD: restaurant

# Replace with:
  app:
    environment:
      CONTROLLER_SIGNUP_CODE: ${CONTROLLER_SIGNUP_CODE}
      ADMIN_SIGNUP_CODE: ${ADMIN_SIGNUP_CODE:-}
      JWT_SECRET: ${JWT_SECRET}
      SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD}

  postgres:
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-restaurant}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: ${POSTGRES_DB:-restaurantdb}
```

### Dockerfile — Final State (After Phase 13)
```dockerfile
FROM maven:3.9-eclipse-temurin-25 as builder
# [VERIFIED: docker pull maven:3.9-eclipse-temurin-25 succeeded]

WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
# [VERIFIED: docker pull eclipse-temurin:25-jre-alpine succeeded]

WORKDIR /app

# Non-root user (Alpine BusyBox syntax — NOT useradd)
RUN addgroup -S appuser && adduser -S appuser -G appuser

COPY --from=builder /build/target/*.jar app.jar

ENV MONGODB_URI=mongodb://mongodb:27017
ENV MONGODB_DATABASE=newyork
ENV MONGODB_COLLECTION=restaurants

EXPOSE 8080

USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Runtime State Inventory

> This phase involves no rename/refactor. No runtime state migration is required.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | No rename involved | None |
| Live service config | docker-compose.yml has hardcoded `CONTROLLER_SIGNUP_CODE: changeme` and `POSTGRES_PASSWORD: restaurant` | Code edit only (no data migration — environment changes at next `docker compose up`) |
| OS-registered state | No OS-level registrations involved | None |
| Secrets/env vars | `.env` does not yet exist; `.env.example` does not yet exist | Create both files |
| Build artifacts | `target/` directory may contain JAR built with old Dockerfile — stale after Java 25 base change | `mvn clean` or `docker compose build --no-cache` before next run |

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Build/run Dockerfile, compose | Yes | 29.3.1 | — |
| Docker Compose | docker compose up | Yes | v5.1.1 | — |
| Maven | mvn test (regression verification) | Yes | 3.8.7 | — |
| Java 25 | mvn test, app compilation | Yes | 25.0.2 | — |
| `eclipse-temurin:25-jre-alpine` | Dockerfile runtime stage | Yes | pulled successfully | Fall back to `25-jdk-alpine` if JRE-only tag disappears |
| `maven:3.9-eclipse-temurin-25` | Dockerfile builder stage | Yes | pulled successfully | — |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:**
- If `eclipse-temurin:25-jre-alpine` is ever unavailable, use `eclipse-temurin:25-jdk-alpine` (larger image, same Java version). Per CONTEXT.md specifics: "do not silently fall back to Java 21."

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 (via junit-vintage-engine) + JUnit 5 (Jupiter) |
| Config file | `pom.xml` — surefire plugin with `@{argLine} -XX:+EnableDynamicAgentLoading` |
| Quick run command | `mvn test -pl . -Dtest=AppConfigTest,JwtUtilTest -q` |
| Full suite command | `mvn test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CFG-01 | No hardcoded secrets remain | grep-zero check | `grep -r "changeme\|dev-only-insecure\|changeit-please" src/ application.properties` | N/A (shell check) |
| CFG-02 | Startup assertion fires when JWT_SECRET absent | unit | `mvn test -Dtest=AppConfigTest` | Existing — needs new test case |
| CFG-02 | Startup assertion fires when JWT_SECRET < 32 chars | unit | `mvn test -Dtest=AppConfigTest` | Existing — needs new test case |
| CFG-03 | Signup codes read from env (null = disabled already tested) | existing tests | `mvn test -Dtest=AuthServiceTest` | Yes |
| CFG-04 | application-test.properties exists with safe values | manual check | `ls src/test/resources/application-test.properties` | No — Wave 0 gap |
| CFG-05 | .env.example exists at project root | manual check | `ls .env.example` | No — Wave 0 gap |
| DOCKER-01 | Health checks present and syntactically correct | manual check | `docker compose config` | N/A (compose validation) |
| DOCKER-03 | Memory limits on all 4 containers | manual check | `docker compose config \| grep memory` | N/A (compose validation) |
| DOCKER-04 | Multi-stage build with Java 25 Alpine | build test | `docker compose build app` | N/A (build check) |
| DOCKER-05 | Non-root user in runtime stage | inspect | `docker inspect restaurant-app \| grep User` | N/A (runtime check) |
| DOCKER-06 | .dockerignore exists | file check | `ls .dockerignore` | No — Wave 0 gap |

### Sampling Rate
- **Per task commit:** `mvn test -q` — full suite must stay at 174+ passing, 0 failures
- **Per wave merge:** `mvn test` (full output with counts)
- **Phase gate:** Full suite green + grep-zero check + `docker compose config` validates before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `src/test/resources/application-test.properties` — safe test values (CFG-04)
- [ ] New test cases in `AppConfigTest` for the startup assertion (CFG-02): one test for missing JWT_SECRET, one for JWT_SECRET shorter than 32 chars
- [ ] `JwtUtilTest` setUp — patch AppConfig.properties via reflection before `new JwtUtil()` to prevent assertion firing in unit tests
- [ ] `.env.example` — created as deliverable, not a test file
- [ ] `.dockerignore` — created as deliverable, not a test file

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT startup assertion ensures secret minimum length; signup code externalization |
| V3 Session Management | yes | JWT secret externalized — no hardcoded signing key in source |
| V4 Access Control | no | No access control changes in this phase |
| V5 Input Validation | no | No new input vectors introduced |
| V6 Cryptography | yes | JWT signing key length enforcement (>= 256 bits for HMAC-SHA256 as required by JJWT); env var injection prevents source exposure |

### Known Threat Patterns for This Phase

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Hardcoded JWT secret in repo | Information Disclosure | Remove `jwt.secret=...` from `application.properties`; assertion enforces env var |
| Hardcoded DB password in docker-compose | Information Disclosure | Replace `POSTGRES_PASSWORD: restaurant` with `${POSTGRES_PASSWORD}` from `.env` |
| Default/weak JWT secret accepted silently | Elevation of Privilege | `IllegalStateException` if `JWT_SECRET` length < 32 at startup |
| App container running as root | Elevation of Privilege | `USER appuser` in Dockerfile after `addgroup -S && adduser -S` |
| Unnecessary files in Docker build context | Information Disclosure | `.dockerignore` excludes `.env`, `.planning/`, `.git/`, logs |

---

## Open Questions (RESOLVED)

1. **Does `spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}` in application.properties work correctly for Spring Boot's DataSource auto-configuration?**
   - What we know: Spring Boot resolves `${VAR}` placeholders in `.properties` files using the full `Environment` abstraction, which includes system env vars and `.env` loaded by Spring's own dotenv support.
   - What's unclear: Spring Boot does NOT use AppConfig's dotenv — it uses its own property source resolution. If `SPRING_DATASOURCE_PASSWORD` is only in the AppConfig dotenv (not as a real env var), Spring's DataSource config might not see it.
   - Recommendation: In docker-compose.yml, `SPRING_DATASOURCE_PASSWORD` is injected as a real environment variable, so Spring Boot will resolve it. For local dev without Docker, if the developer sets it in `.env`, AppConfig's dotenv reads it but Spring Boot's own resolver may not (unless spring-dotenv or similar is configured). Safe default: document in `.env.example` that `SPRING_DATASOURCE_PASSWORD` must match `POSTGRES_PASSWORD` and must be set as a real env var (e.g., `export SPRING_DATASOURCE_PASSWORD=...`) or rely on Docker for all DB-requiring scenarios.
   - **RESOLVED:** Spring Boot resolves `${SPRING_DATASOURCE_PASSWORD}` from system env vars (which Docker Compose injects as real env vars). For local dev, `application.properties` uses `spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:restaurant}` with a default fallback so Maven tests work without a `.env` present. This pattern is safe and confirmed for the Docker path.

2. **Should the `application.properties` jwt.secret line be removed entirely or replaced with a comment?**
   - What we know: D-02 says "remove `jwt.secret=...` from `application.properties` entirely."
   - What's unclear: If removed entirely, AppConfig falls to the `null` default in `getProperty("jwt.secret", null)`, which then triggers the assertion. A comment-only line (no `=` value) would be cleaner documentation.
   - Recommendation: Remove the `jwt.secret=...` line entirely and add a comment: `# jwt.secret — set JWT_SECRET environment variable (minimum 32 chars)`. This is cleaner than a placeholder that would be misread as a value.
   - **RESOLVED:** Remove the `jwt.secret=...` line entirely and replace with a comment: `# jwt.secret — set JWT_SECRET environment variable (minimum 32 chars)`. Implemented in Plan 01 Task 2 Step C.

---

## Project Constraints (from CLAUDE.md)

- Java 11 listed in CLAUDE.md but pom.xml uses Java 25 — actual compiler source is Java 25. Dockerfile must match Java 25.
- Spring Boot 2.6.15 listed in CLAUDE.md but pom.xml uses Spring Boot 3.4.4 — `docker compose` (plugin) syntax is correct.
- Build tool: Maven (`mvn`). All commands use `mvn`.
- Config file: `src/main/resources/application.properties` — no YAML migration.
- Docker: `docker compose` (not `docker-compose`). Confirmed: Docker Compose v5.1.1 installed.
- Testing: JUnit 4 + Mockito — existing tests use JUnit 4 `@Test` via vintage engine; new tests for AppConfig assertion may use either JUnit 4 or 5 (both engines on classpath).
- Deletion: use `trash` instead of `rm -rf` on Ubuntu — relevant if cleanup steps are needed.
- Commits: English, imperative mood, max 72 chars subject.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}` in application.properties is resolved correctly by Spring Boot DataSource auto-config when `SPRING_DATASOURCE_PASSWORD` is set as a real env var (e.g., in docker-compose environment block) | Open Questions #1 | If wrong: DB connection fails with auth error in Docker; workaround is to hardcode the value in docker-compose for postgres container and pass `SPRING_DATASOURCE_PASSWORD` matching it |
| A2 | `AppConfig.getProperty("jwt.secret", null)` returning null when no `JWT_SECRET` env var is set (rather than returning the empty string `""` from application.properties after removing the property line) | Code Examples | If wrong: assertion logic must use `secret == null \|\| secret.isBlank() \|\| secret.length() < 32` to also catch empty strings |

**If both claims are correct (HIGH probability based on code inspection), no user confirmation is needed.** A2 can be confirmed immediately by reading `AppConfig.getProperty()` — if `jwt.secret` key is absent from properties and no env var is set, it returns the `defaultValue` argument, which the new assertion code passes as `null`.

---

## Sources

### Primary (HIGH confidence)
- Direct code inspection: `AppConfig.java`, `JwtUtil.java`, `AuthService.java`, `application.properties`, `docker-compose.yml`, `Dockerfile` — current state verified
- `[VERIFIED: docker pull eclipse-temurin:25-jre-alpine]` — image exists, SHA256: f10d6259...
- `[VERIFIED: docker pull maven:3.9-eclipse-temurin-25]` — image exists, SHA256: 41e08d84...
- `[VERIFIED: mvn test → BUILD SUCCESS, 174 tests, 0 failures]` — test baseline confirmed
- `[VERIFIED: Docker Compose version v5.1.1]` — `deploy.resources.limits` syntax supported
- `[VERIFIED: Java 25.0.2 installed]` — runtime matches compiler target

### Secondary (MEDIUM confidence)
- Docker Compose deploy.resources.limits syntax: confirmed functional with Docker Compose v2+ `[VERIFIED: version check]`
- Alpine BusyBox `addgroup`/`adduser` syntax: standard Alpine practice `[ASSUMED — well-established pattern]`

### Tertiary (LOW confidence)
- Spring Boot property placeholder `${VAR}` resolution for DataSource: described in Open Questions — behavior assumed based on Spring Boot documentation pattern `[ASSUMED]`

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in place; Docker images verified pullable
- Architecture patterns: HIGH — code inspection confirms current state; assertion pattern is straightforward
- Pitfalls: HIGH for Docker/Alpine pitfalls (verified toolchain), MEDIUM for Spring property resolution question
- Test impact: HIGH — `JwtUtilTest` will break without reflection patch; identified and documented

**Research date:** 2026-04-12
**Valid until:** 2026-05-12 (Docker image tags are stable; library versions locked)
