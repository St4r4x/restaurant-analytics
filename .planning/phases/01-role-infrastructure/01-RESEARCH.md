# Phase 1: Role Infrastructure - Research

**Researched:** 2026-03-27
**Domain:** Spring Security role-based access control, JWT role claims, Servlet rate limiting, application seeding
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Registration flow**
- Single endpoint: `POST /api/auth/register` with optional `signupCode` field in the request body
- No `signupCode` provided → user becomes `ROLE_CUSTOMER`
- Correct `signupCode` (matches env var) → user becomes `ROLE_CONTROLLER`
- Wrong `signupCode` → HTTP 400 with generic message `"Invalid registration request"` (do not reveal the code system exists)
- Env var name: `CONTROLLER_SIGNUP_CODE` (consistent with existing ALL_CAPS pattern in AppConfig)
- If `CONTROLLER_SIGNUP_CODE` env var is not set → controller registration is disabled; any attempt returns 400 (fail-safe)

**URL security scope**
- `/api/auth/**` → fully public (login, register, refresh)
- `/api/restaurants/**` → fully public (read-only NYC data, no reason to gate)
- `/api/inspections/**` → fully public (read-only NYC data)
- `/api/users/**` → require valid JWT (any role); unauthenticated → 401
- `/api/reports/**` → require `ROLE_CONTROLLER`; unauthenticated → 401, valid CUSTOMER JWT → 403
- Swagger (`/swagger-ui.html`, `/api-docs/**`, `/v3/api-docs/**`) → fully public
- View routes (non-`/api/**`) → left fully open for now (view-level auth is Phase 3 scope)

**Access denied responses**
- Unauthenticated requests to protected endpoints → 401 with JSON body (already handled by existing `authenticationEntryPoint` in `SecurityConfig` for `/api/**`)
- Authenticated but wrong role → 403 (handled by Spring Security's default `AccessDeniedException` handler — add an `accessDeniedHandler` returning JSON 403)

**Role values**
- Store `ROLE_CUSTOMER` / `ROLE_CONTROLLER` in `UserEntity.role` (replaces current `ROLE_USER`)
- Use `hasRole("CUSTOMER")` / `hasRole("CONTROLLER")` in security rules — Spring strips the prefix internally

### Claude's Discretion
- Rate limiting implementation (Bucket4j or custom Servlet filter) and threshold config placement
- Seed account credentials and idempotency behavior (skip if already exists is the safe default)
- Exact Hibernate migration for the role column value change

### Deferred Ideas (OUT OF SCOPE)
- None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| AUTH-01 | User account has a CUSTOMER or CONTROLLER role stored in PostgreSQL | `UserEntity.role` column already exists as a String; change stored values from `ROLE_USER` to `ROLE_CUSTOMER` / `ROLE_CONTROLLER`; `spring.jpa.hibernate.ddl-auto=update` will accept new values without migration |
| AUTH-02 | Controller can register via a dedicated endpoint using a shared signup code | Extend `RegisterRequest` with optional `signupCode`; add validation logic in `AuthService.register()` reading `CONTROLLER_SIGNUP_CODE` via `AppConfig.getProperty()` pattern |
| AUTH-03 | URL-level security guards block CONTROLLER endpoints from unauthenticated or CUSTOMER access | Replace `anyRequest().permitAll()` with explicit `antMatchers` in `SecurityConfig.filterChain()`; add `accessDeniedHandler` for 403 JSON; `JwtAuthenticationFilter` already populates `SecurityContext` with `SimpleGrantedAuthority(role)` — no filter changes needed |
| AUTH-04 | Auth endpoints (login/register) have rate limiting to prevent brute-force attacks | Add Bucket4j `OncePerRequestFilter` (or a `HandlerInterceptor`) applied only to `/api/auth/**`; configure threshold via `AppConfig` pattern; return HTTP 429 with JSON body |
| AUTH-05 | One CUSTOMER and one CONTROLLER test account are seeded automatically on application startup | Add a `@Component DataSeeder implements ApplicationRunner`; check existence via `UserRepository.findByUsername()` before saving; BCrypt-encode passwords with the existing `PasswordEncoder` bean |
</phase_requirements>

---

## Summary

Phase 1 is a targeted security hardening exercise on an existing Spring Boot 2.6.15 / Spring Security 5 application. The JWT infrastructure (token generation, claim extraction, filter wiring) is already correct and does not need changes. The two main structural changes are: (1) replacing the blanket `anyRequest().permitAll()` with fine-grained `antMatchers`, and (2) adding signup-code logic and role assignment to `AuthService.register()`. Two new components need to be created from scratch: a rate-limiting filter for auth endpoints and a startup data seeder.

Because Hibernate DDL auto is set to `update`, the `role` column value change from `ROLE_USER` to `ROLE_CUSTOMER`/`ROLE_CONTROLLER` requires no schema migration — only the application code that writes the value needs updating. Any existing rows with `ROLE_USER` will remain; seeds use idempotent `findByUsername` checks so re-starts are safe.

Rate limiting is the only area with genuine implementation choice (Bucket4j library vs. hand-rolled map). Bucket4j integrates with Spring Boot without framework coupling, supports per-key (per-IP) buckets, and is the mature library choice for this stack.

**Primary recommendation:** Follow the exact change map below — minimum-footprint modifications to existing files plus two new small classes (`RateLimitFilter`, `DataSeeder`).

---

## Standard Stack

### Core (already in pom.xml — no additions needed for AUTH-01/02/03/05)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-security | managed by 2.6.15 BOM (~5.6.x) | `SecurityFilterChain`, `antMatchers`, access denied handler | Already present |
| jjwt-api / jjwt-impl / jjwt-jackson | 0.11.5 | JWT generation and validation | Already present |
| spring-boot-starter-data-jpa | managed | `UserRepository` (PostgreSQL / Hibernate) | Already present |

### New Dependency (AUTH-04 only)
| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| bucket4j-core | 7.6.0 | In-process token-bucket rate limiting | No extra infra, thread-safe, battle-tested, clean Spring filter integration; Spring Boot 2.6 compatible |

**Installation (add to pom.xml):**
```xml
<dependency>
  <groupId>com.bucket4j</groupId>
  <artifactId>bucket4j-core</artifactId>
  <version>7.6.0</version>
</dependency>
```

Note: Bucket4j 8.x requires Java 17. Use **7.6.0** for Java 11 compatibility.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Bucket4j | Hand-rolled `ConcurrentHashMap<String, AtomicInteger>` | Custom map lacks time-window reset and per-key expiry; acceptable for POC but misses burst semantics |
| Bucket4j | Spring `HandlerInterceptor` + custom logic | Same as above — still need a bucket library for correct token-bucket math |
| Bucket4j | Redis-backed rate limiter | Overkill for single-node academic app; adds Redis coupling to auth path |

---

## Architecture Patterns

### Recommended Project Structure Changes
```
com.aflokkat/
├── config/
│   ├── SecurityConfig.java       # MODIFY: antMatchers + accessDeniedHandler
│   └── AppConfig.java            # MODIFY: add getControllerSignupCode()
├── service/
│   └── AuthService.java          # MODIFY: signup code logic + role assignment
├── dto/
│   └── RegisterRequest.java      # MODIFY: add signupCode field
├── security/
│   ├── JwtAuthenticationFilter.java  # NO CHANGE
│   ├── JwtUtil.java                  # NO CHANGE
│   └── RateLimitFilter.java          # NEW: Bucket4j OncePerRequestFilter
└── startup/
    └── DataSeeder.java               # NEW: ApplicationRunner seed component
```

### Pattern 1: antMatchers URL Security (Spring Security 5 style)
**What:** Replaces the single `anyRequest().permitAll()` with ordered `antMatchers` rules.
**When to use:** Spring Security 5 on Spring Boot 2.6 — this is `HttpSecurity.authorizeRequests()` API (not the newer `authorizeHttpRequests()` from Spring Security 6).

```java
// In SecurityConfig.filterChain() — replace .anyRequest().permitAll() block
http
    .authorizeRequests()
        // Public
        .antMatchers("/api/auth/**").permitAll()
        .antMatchers("/api/restaurants/**").permitAll()
        .antMatchers("/api/inspections/**").permitAll()
        .antMatchers("/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**",
                     "/swagger-ui/**", "/webjars/**").permitAll()
        // Controllers only
        .antMatchers("/api/reports/**").hasRole("CONTROLLER")
        // Any valid JWT
        .antMatchers("/api/users/**").authenticated()
        // Non-API views: open for now
        .anyRequest().permitAll()
```

**Critical ordering:** more specific rules must come before `.anyRequest()`. Spring Security evaluates top-down and stops at first match.

### Pattern 2: Access Denied Handler (403 JSON)
**What:** `AccessDeniedHandler` writes a JSON 403 response when an authenticated user lacks the required role.

```java
// Inside filterChain(), in the .exceptionHandling() block
.accessDeniedHandler((request, response, accessDeniedException) -> {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json");
    response.getWriter().write("{\"status\":\"error\",\"message\":\"Forbidden\"}");
})
```

This pairs with the existing `authenticationEntryPoint` (401) already in `SecurityConfig`.

### Pattern 3: Signup Code Check in AuthService
**What:** Read `CONTROLLER_SIGNUP_CODE` env var; determine role before persisting.

```java
// In AuthService.register() — before userRepository.save()
String signupCode = AppConfig.getControllerSignupCode(); // returns null if env var absent
String providedCode = request.getSignupCode();           // null if field absent from request

String role;
if (providedCode == null || providedCode.isEmpty()) {
    role = "ROLE_CUSTOMER";
} else {
    // Controller signup disabled when env var is not set
    if (signupCode == null || signupCode.isEmpty()) {
        throw new IllegalArgumentException("Invalid registration request");
    }
    if (!signupCode.equals(providedCode)) {
        throw new IllegalArgumentException("Invalid registration request");
    }
    role = "ROLE_CONTROLLER";
}
UserEntity userEntity = new UserEntity(request.getUsername(), request.getEmail(), hash, role);
```

### Pattern 4: Bucket4j Rate Limiting Filter
**What:** `OncePerRequestFilter` that rate-limits `/api/auth/**` by client IP using a per-IP `Bucket`.

```java
// RateLimitFilter.java (sketch)
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    // e.g. 10 requests per minute per IP
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int maxRequests = AppConfig.getAuthRateLimitRequests();   // new AppConfig key
    private final Duration window   = Duration.ofMinutes(
            AppConfig.getAuthRateLimitWindowMinutes());

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String ip = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                      .addLimit(Bandwidth.classic(maxRequests,
                              Refill.greedy(maxRequests, window)))
                      .build());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Too many requests\"}");
        }
    }
}
```

**Config keys to add to `application.properties` and `AppConfig`:**
```properties
auth.rate-limit.requests=10
auth.rate-limit.window-minutes=1
```
Env var equivalents: `AUTH_RATE_LIMIT_REQUESTS`, `AUTH_RATE_LIMIT_WINDOW_MINUTES`.

### Pattern 5: DataSeeder (ApplicationRunner)
**What:** Runs after Spring context is fully started; creates seed accounts if absent.

```java
// DataSeeder.java
@Component
public class DataSeeder implements ApplicationRunner {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seedUser("customer_test", "customer@test.com", "ROLE_CUSTOMER");
        seedUser("controller_test", "controller@test.com", "ROLE_CONTROLLER");
    }

    private void seedUser(String username, String email, String role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;  // idempotent
        }
        String hash = passwordEncoder.encode("Test1234!");
        userRepository.save(new UserEntity(username, email, hash, role));
    }
}
```

Seed credentials should either be hardcoded to a well-known test value or configurable via env vars. For academic scope, hardcoded test credentials are acceptable. Password `Test1234!` is used here as a placeholder — planner should define final values.

### Anti-Patterns to Avoid
- **`anyRequest().authenticated()` as catch-all:** This breaks view routes and health endpoints. End the chain with `anyRequest().permitAll()` and rely on explicit path rules for locked-down sections.
- **`@PreAuthorize` on controller methods instead of `antMatchers`:** Both are available (`@EnableMethodSecurity` is already present), but URL-level guards are the primary lock. Method security is a complementary secondary layer, not the primary one.
- **Modifying `JwtAuthenticationFilter`:** The filter already creates `SimpleGrantedAuthority(role)` correctly. The `ROLE_` prefix in stored values maps directly to Spring's `hasRole("CUSTOMER")` check (Spring prepends `ROLE_` internally). No filter changes needed.
- **Bucket4j 8.x on Java 11:** Bucket4j 8.x requires Java 17. Use 7.6.0.
- **Registering `RateLimitFilter` as a Spring Security filter via `addFilterBefore`:** Because this filter is already a Spring `@Component`, Spring Boot auto-registers it in the servlet filter chain. Do NOT also add it via `http.addFilterBefore()` — that would apply it twice.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Token-bucket rate limiting | `Map<IP, timestamp[]>` with manual window expiry | Bucket4j | Correct burst semantics, thread-safe, handles concurrent requests without race conditions |
| Role checking in controllers | Manual `SecurityContextHolder.getContext().getAuthentication()` role checks | Spring Security `antMatchers` + `hasRole()` | Framework handles edge cases (null auth, expired token, etc.) |
| Password hashing | Custom hash function | `BCryptPasswordEncoder` (already wired) | Already present; BCrypt handles salting, cost factor |

**Key insight:** In this phase every new capability maps to a Spring Security configuration primitive or a small class. The framework handles the heavy lifting; the work is wiring, not building.

---

## Common Pitfalls

### Pitfall 1: `antMatchers` order matters
**What goes wrong:** Placing `.anyRequest().permitAll()` before `.antMatchers("/api/reports/**").hasRole(...)` makes the controller-only rule unreachable.
**Why it happens:** Spring Security evaluates rules top-to-bottom and stops at first match.
**How to avoid:** Always put specific path rules first, `.anyRequest()` last.
**Warning signs:** 403 never returned for `/api/reports/**` regardless of token.

### Pitfall 2: Double filter registration
**What goes wrong:** `RateLimitFilter` is applied twice — once by Spring Boot's auto-registration of `@Component` filters, once by `http.addFilterBefore()` in `SecurityConfig`.
**Why it happens:** Spring Boot automatically registers any `@Component` that extends `GenericFilterBean` or `OncePerRequestFilter`.
**How to avoid:** Either annotate with `@Component` and do NOT call `http.addFilterBefore()`, OR do NOT annotate and register only via `http.addFilterBefore()`. Choose one.
**Warning signs:** Rate limit halved (two tokens consumed per request), or filter appears twice in debug logs.

### Pitfall 3: `hasRole()` vs `hasAuthority()` mismatch
**What goes wrong:** Storing `ROLE_CONTROLLER` but calling `hasAuthority("ROLE_CONTROLLER")` works; but mixing `hasRole("CONTROLLER")` with `hasAuthority("CONTROLLER")` (no prefix) fails silently.
**Why it happens:** `hasRole("X")` auto-prepends `ROLE_` internally. `hasAuthority("X")` matches exactly.
**How to avoid:** Decide once: store `ROLE_CONTROLLER`, use `hasRole("CONTROLLER")` everywhere. This is the locked decision.
**Warning signs:** Access granted or denied inconsistently across different endpoints.

### Pitfall 4: Seeder runs before schema is ready
**What goes wrong:** `DataSeeder` (implementing `ApplicationRunner`) runs after the Spring context starts, but if PostgreSQL is not yet healthy, it throws a connection error.
**Why it happens:** `ApplicationRunner` runs after full context initialization, which should be fine — but only when the DB health check in Docker Compose has already passed.
**How to avoid:** Docker Compose `depends_on` with `condition: service_healthy` on `postgres` is already configured. Add a `@Transactional` or a try/catch with a warning log to handle edge cases gracefully.

### Pitfall 5: Existing `ROLE_USER` rows in the database
**What goes wrong:** Any user registered before Phase 1 ships has `role = 'ROLE_USER'`, which does not match either `ROLE_CUSTOMER` or `ROLE_CONTROLLER`. Their JWT will be valid but they will be blocked from all protected resources.
**Why it happens:** The role column is a plain string; Hibernate `ddl-auto=update` does not migrate data.
**How to avoid:** This is an academic project with ephemeral Docker volumes. The solution is `docker compose down -v` + `docker compose up -d` to reset data during development. No Flyway/Liquibase migration needed. Document this in the plan.

### Pitfall 6: Bucket4j `ConcurrentHashMap` growing unbounded
**What goes wrong:** The per-IP bucket map grows forever if the app runs for days (every unique IP ever seen occupies memory).
**Why it happens:** `ConcurrentHashMap` has no eviction.
**How to avoid:** For academic scope, this is acceptable. If eviction is desired, use Guava `CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.HOURS).build()`. Flag this as a known limitation.

---

## Code Examples

### Verified: SecurityConfig antMatchers (Spring Security 5 / Spring Boot 2.6)
```java
// Uses .authorizeRequests() — the Spring Security 5 API
// Spring Boot 2.6.x includes Spring Security 5.6.x
http
    .csrf().disable()
    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    .and()
    .authorizeRequests()
        .antMatchers("/api/auth/**").permitAll()
        .antMatchers("/api/restaurants/**").permitAll()
        .antMatchers("/api/inspections/**").permitAll()
        .antMatchers("/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**",
                     "/swagger-ui/**", "/webjars/**").permitAll()
        .antMatchers("/api/reports/**").hasRole("CONTROLLER")
        .antMatchers("/api/users/**").authenticated()
        .anyRequest().permitAll()
    .and()
    .exceptionHandling()
        .authenticationEntryPoint(/* existing 401 handler */)
        .accessDeniedHandler((req, res, ex) -> {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setContentType("application/json");
            res.getWriter().write("{\"status\":\"error\",\"message\":\"Forbidden\"}");
        })
    .and()
    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

### Verified: AppConfig extension pattern (follows existing code)
```java
// AppConfig.java — add alongside existing getters
public static String getControllerSignupCode() {
    // Returns null if CONTROLLER_SIGNUP_CODE is not set in env or application.properties
    return getProperty("controller.signup.code", null);
}

public static int getAuthRateLimitRequests() {
    return getIntProperty("auth.rate-limit.requests", 10);
}

public static int getAuthRateLimitWindowMinutes() {
    return getIntProperty("auth.rate-limit.window-minutes", 1);
}
```

### Verified: Bucket4j 7.x API (Java 11 compatible)
```java
// Bucket creation in Bucket4j 7.x
import com.bucket4j.Bandwidth;
import com.bucket4j.Bucket;
import com.bucket4j.Refill;
import java.time.Duration;

Bucket bucket = Bucket.builder()
    .addLimit(Bandwidth.classic(maxRequests, Refill.greedy(maxRequests, Duration.ofMinutes(1))))
    .build();

// Try to consume a token
if (bucket.tryConsume(1)) {
    // proceed
} else {
    // return 429
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `WebSecurityConfigurerAdapter` extends | `SecurityFilterChain` @Bean | Spring Security 5.7 / Boot 2.7 | Already using new style — no action needed |
| `authorizeHttpRequests()` | `authorizeRequests()` | Spring Security 6 removed old API | This app uses Boot 2.6 / Security 5.6 — `authorizeRequests()` is correct here |
| `antMatchers` | `requestMatchers` | Spring Security 6 | Use `antMatchers` on Security 5.6 |

**Deprecated/outdated:**
- `WebSecurityConfigurerAdapter`: replaced by `SecurityFilterChain` bean — already done correctly in the project.
- `authorizeHttpRequests()` + `requestMatchers()`: these are the Spring Security 6 APIs. Do NOT use them here; the project is on 5.6.

---

## Open Questions

1. **Seed account passwords**
   - What we know: `DataSeeder` needs to encode and store passwords
   - What's unclear: Should passwords be configurable env vars or hardcoded test values?
   - Recommendation: Hardcode a known test password (e.g., `Test1234!`) for academic scope; document the credentials in a test README or comments. Optionally add `SEED_CUSTOMER_PASSWORD` / `SEED_CONTROLLER_PASSWORD` env vars following the `AppConfig` pattern if the planner wants configurability.

2. **Rate limit threshold values**
   - What we know: Threshold must be configurable (from CONTEXT.md discretion)
   - What's unclear: Exact default numbers (requests per window)
   - Recommendation: Default to 10 requests per 1-minute window per IP for auth endpoints. This is conservative but functional for an academic app.

3. **ConcurrentHashMap bucket eviction**
   - What we know: Unbounded map is a memory concern for long-running instances
   - What's unclear: Whether the academic scope warrants the fix
   - Recommendation: Flag as known limitation; do not add Guava dependency just for this. A comment in the code is sufficient.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) via `spring-boot-starter-test` + JUnit 4 via explicit dependency |
| Config file | None — Maven Surefire picks up both via the BOM |
| Quick run command | `mvn test -Dtest=AuthServiceTest,JwtUtilTest -DfailIfNoTests=false` |
| Full suite command | `mvn test` |

Note: Existing tests use JUnit 5 (`@ExtendWith(MockitoExtension.class)`, `@Test` from `org.junit.jupiter`) despite JUnit 4 being listed in pom.xml. The `spring-boot-starter-test` BOM manages JUnit 5 automatically.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AUTH-01 | `register()` stores `ROLE_CUSTOMER` when no signup code given | unit | `mvn test -Dtest=AuthServiceTest#register_assignsCustomerRole_whenNoSignupCode` | ❌ Wave 0 |
| AUTH-01 | `register()` stores `ROLE_CONTROLLER` when correct code given | unit | `mvn test -Dtest=AuthServiceTest#register_assignsControllerRole_whenCorrectSignupCode` | ❌ Wave 0 |
| AUTH-02 | `register()` throws when wrong signup code given | unit | `mvn test -Dtest=AuthServiceTest#register_throws_whenWrongSignupCode` | ❌ Wave 0 |
| AUTH-02 | `register()` throws when signup code given but env var not set | unit | `mvn test -Dtest=AuthServiceTest#register_throws_whenSignupCodeEnvVarAbsent` | ❌ Wave 0 |
| AUTH-03 | `/api/reports/**` returns 401 for unauthenticated request | unit/slice | `mvn test -Dtest=SecurityConfigTest#reports_returns401_whenUnauthenticated` | ❌ Wave 0 |
| AUTH-03 | `/api/reports/**` returns 403 for CUSTOMER JWT | unit/slice | `mvn test -Dtest=SecurityConfigTest#reports_returns403_forCustomerJwt` | ❌ Wave 0 |
| AUTH-03 | `/api/reports/**` returns 200 (or non-4xx) for CONTROLLER JWT | unit/slice | `mvn test -Dtest=SecurityConfigTest#reports_allowsAccess_forControllerJwt` | ❌ Wave 0 |
| AUTH-04 | RateLimitFilter returns 429 after threshold exceeded | unit | `mvn test -Dtest=RateLimitFilterTest#filter_returns429_afterThresholdExceeded` | ❌ Wave 0 |
| AUTH-04 | RateLimitFilter passes through before threshold | unit | `mvn test -Dtest=RateLimitFilterTest#filter_passes_beforeThreshold` | ❌ Wave 0 |
| AUTH-05 | DataSeeder creates seed accounts on startup | unit | `mvn test -Dtest=DataSeederTest#run_createsCustomerAndController_whenAbsent` | ❌ Wave 0 |
| AUTH-05 | DataSeeder is idempotent (no duplicate on re-run) | unit | `mvn test -Dtest=DataSeederTest#run_skipsExisting_whenAlreadySeeded` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `mvn test -Dtest=AuthServiceTest,JwtUtilTest -DfailIfNoTests=false`
- **Per wave merge:** `mvn test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/com/aflokkat/service/AuthServiceTest.java` — extend existing file with new role-assignment test cases (AUTH-01, AUTH-02)
- [ ] `src/test/java/com/aflokkat/config/SecurityConfigTest.java` — new `@WebMvcTest`-style slice test for URL guards (AUTH-03); note: requires MockMvc + test security config
- [ ] `src/test/java/com/aflokkat/security/RateLimitFilterTest.java` — new unit test for rate limit filter (AUTH-04)
- [ ] `src/test/java/com/aflokkat/startup/DataSeederTest.java` — new Mockito unit test for DataSeeder (AUTH-05)

**Note on SecurityConfigTest:** `@WebMvcTest` with Spring Security requires `@WithMockUser` or custom `SecurityMockMvcConfigurer` to inject test JWTs. The test must use `@Import(SecurityConfig.class)` and mock the `JwtAuthenticationFilter`. This is the most complex test to wire.

---

## Sources

### Primary (HIGH confidence)
- Direct code inspection of `SecurityConfig.java`, `JwtAuthenticationFilter.java`, `JwtUtil.java`, `AuthService.java`, `AppConfig.java`, `RegisterRequest.java`, `UserEntity.java`, `AuthController.java` — all in project source
- Spring Boot 2.6.15 / Spring Security 5.6.x — confirmed via `pom.xml` parent `spring-boot-starter-parent:2.6.15`
- pom.xml dependency list — confirms jjwt 0.11.5, springdoc 1.8.0, JUnit 4.13.2, no Bucket4j currently present

### Secondary (MEDIUM confidence)
- Bucket4j 7.x Java 11 compatibility — known from library documentation; 8.x requires Java 17 is a well-documented breaking change
- Spring Security `authorizeRequests()` vs `authorizeHttpRequests()` API split — documented in Spring Security 5.6 migration guide and 6.0 release notes

### Tertiary (LOW confidence)
- None — all claims supported by project source inspection or well-established Spring Security documentation

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified against pom.xml; only new dependency is Bucket4j 7.6.0
- Architecture: HIGH — based on direct code reading of all referenced files; patterns are standard Spring Security 5 idioms
- Pitfalls: HIGH — derived from actual code inspection (existing filter wiring, ddl-auto=update, @Component registration behavior)

**Research date:** 2026-03-27
**Valid until:** 2026-06-27 (Spring Boot 2.6 is stable; Bucket4j 7.x API is stable)
