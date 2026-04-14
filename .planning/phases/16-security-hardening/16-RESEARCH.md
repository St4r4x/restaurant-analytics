# Phase 16: Security Hardening — Research

**Researched:** 2026-04-13
**Domain:** Spring Security 7 / Spring Boot 4 — CORS, headers DSL, Bean Validation, Bucket4j rate-limit extension
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**CORS Policy**
- D-01: Whitelist `http://localhost:8080` only — tight policy. No wildcard.
- D-02: Allowed methods: `GET, POST, PUT, DELETE, OPTIONS`.
- D-03: Wire CORS via **both** `CorsConfigurationSource` bean **and** `http.cors(withDefaults())` in `SecurityConfig`.

**Security Response Headers**
- D-04: Add exactly `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY`. No CSP/HSTS/Referrer-Policy.

**Input Validation**
- D-05: `AuthRequest`: `@NotBlank` on `username` and `password`.
- D-06: `RegisterRequest`: `@NotBlank` on `username` and `password`; `@NotBlank @Email` on `email`.
- D-07: `RefreshRequest`: `@NotBlank` on `refreshToken`.
- D-08: Scope: auth DTOs only. `ReportRequest` skipped.
- D-09: New `@RestControllerAdvice` global exception handler for `MethodArgumentNotValidException` → HTTP 400 JSON `{status, message, timestamp}`.

**Rate Limiting Extension**
- D-10: `/api/restaurants/**` limit: 100 req/min per IP.
- D-11: Same `RateLimitFilter` — second `ConcurrentHashMap<String, Bucket>` for restaurant-path buckets.
- D-12: `restaurant.rate-limit.requests=100` and `restaurant.rate-limit.window-minutes=1` in `application.properties`.

**HTTPS-Ready Config**
- D-13: Add `server.forward-headers-strategy=native` to `application.properties`.

**JWT Startup Assertion**
- D-14: Already implemented in Phase 13 — verify in test suite only, no new code.

### Claude's Discretion
- Security headers: use Spring Security `headers()` DSL in `SecurityConfig.filterChain()`.
- `@RestControllerAdvice` class location: `com.aflokkat.controller` package.
- Validation error message: collect all field errors from `MethodArgumentNotValidException.getBindingResult()` into a comma-separated `message` string.
- `allowedHeaders` for CORS: `Authorization`, `Content-Type`.

### Deferred Ideas (OUT OF SCOPE)
- Non-auth DTO validation (ReportRequest)
- CSP/HSTS/Referrer-Policy headers
- CORS origin parameterization via env var
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SEC-01 | Explicit CORS policy in both WebMvcConfigurer and SecurityConfig (not just one) | D-03: CorsConfigurationSource bean + http.cors(withDefaults()); VERIFIED Spring Security 7.0.4 API |
| SEC-02 | Security headers (X-Content-Type-Options, X-Frame-Options) in all responses | D-04: headers() DSL — contentTypeOptions(withDefaults()) + frameOptions(fc -> fc.deny()); VERIFIED bytecode |
| SEC-03 | @Valid on all @RequestBody DTOs with constraints | D-05/D-06/D-07: @NotBlank/@Email on 3 auth DTOs; spring-boot-starter-validation REQUIRED |
| SEC-04 | MethodArgumentNotValidException handled globally by @RestControllerAdvice | D-09: New GlobalExceptionHandler class; no existing handler |
| SEC-05 | Bucket4j rate limiting on /api/auth/login and /api/auth/register | Already implemented in existing RateLimitFilter; no new code |
| SEC-06 | Rate limiting extended to /api/restaurants/** (higher limit) | D-10/D-11/D-12: second ConcurrentHashMap + AppConfig methods |
| SEC-07 | server.forward-headers-strategy=native configured | D-13: one-line properties addition |
| SEC-08 | JWT secret length assertion at startup | D-14: already in AppConfig.getJwtSecret(); verify test exists |
</phase_requirements>

---

## Summary

Phase 16 adds five security controls to an existing Spring Boot 4.0.5 / Spring Security 7.0.4 application. All five are confined to configuration and infrastructure layers — no business logic changes. The implementation work divides cleanly into: (1) CORS global policy, (2) response header defaults, (3) Bean Validation on auth DTOs + global exception handler, (4) restaurant-path rate-limit bucket, and (5) two one-line properties additions.

The most significant non-obvious finding is that four controllers (`AuthController`, `RestaurantController`, `AnalyticsController`, `InspectionController`) currently carry `@CrossOrigin(origins = "*", allowedHeaders = "*")` at the class level. These annotations take precedence over the `SecurityConfig` CORS bean for those endpoints and will serve wildcard `Access-Control-Allow-Origin` headers regardless of the global policy. They must be removed as part of SEC-01 implementation — this is not mentioned in CONTEXT.md and constitutes a hidden coupling.

The second significant finding is that `spring-boot-starter-validation` (which brings `hibernate-validator`) is **not present** in the dependency tree. `spring-boot-starter-web` in Spring Boot 4.x only pulls `jakarta.validation-api` (the API jar), not the Hibernate Validator implementation. Without the implementation jar, `@NotBlank` annotations compile but are silently ignored at runtime — `@Valid`-annotated parameters will never trigger HTTP 400. The explicit starter dependency must be added to `pom.xml`.

**Primary recommendation:** Add `spring-boot-starter-validation` to pom.xml, strip `@CrossOrigin` from all four controllers, implement the global CORS policy via `CorsConfigurationSource` bean, wire `http.cors(withDefaults())` and `http.headers()` in `SecurityConfig`, annotate DTOs, create `GlobalExceptionHandler`, and extend `RateLimitFilter`.

---

## Standard Stack

### Core (all already in pom.xml except validation)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Security | 7.0.4 | CORS DSL, headers DSL | Managed by Spring Boot 4.0.5 BOM |
| Spring Web | 7.0.6 | `CorsConfigurationSource`, `UrlBasedCorsConfigurationSource` | Managed by Spring Boot BOM |
| spring-boot-starter-validation | 4.0.5 (BOM-managed) | Pulls hibernate-validator + jakarta.validation-api impl | **MUST ADD to pom.xml** |
| Bucket4j | 7.6.1 | Token-bucket rate limiting | Already present; pinned for Java < 17 compat |
| JUnit 5 + Mockito 5.17.0 | existing | Unit tests | Already present |

### Dependency to Add

```xml
<!-- Bean Validation implementation — spring-boot-starter-web only brings the API jar, not hibernate-validator -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

[VERIFIED: Maven dependency tree shows `jakarta.validation:jakarta.validation-api:3.1.1` via spring-boot-starter-web, but `org.hibernate.validator:hibernate-validator` is absent — confirmed by `mvn dependency:tree` grep and local cache inspection]

**Installation:**

```bash
# No manual installation — Maven resolves automatically.
# Verify after adding to pom.xml:
mvn dependency:tree | grep hibernate-validator
```

---

## Architecture Patterns

### Recommended Project Structure Changes

```
src/main/java/com/st4r4x/
├── config/
│   └── SecurityConfig.java          # ADD: CorsConfigurationSource bean, http.cors(), http.headers()
├── controller/
│   └── GlobalExceptionHandler.java  # NEW: @RestControllerAdvice for MethodArgumentNotValidException
├── dto/
│   ├── AuthRequest.java             # ADD: @NotBlank on username + password
│   ├── RegisterRequest.java         # ADD: @NotBlank on username + password, @NotBlank @Email on email
│   └── RefreshRequest.java          # ADD: @NotBlank on refreshToken
├── security/
│   └── RateLimitFilter.java         # EXTEND: second ConcurrentHashMap for restaurant buckets
└── resources/
    └── application.properties       # ADD: restaurant.rate-limit.*, server.forward-headers-strategy
```

### Pattern 1: CORS via CorsConfigurationSource Bean

**What:** Register a `CorsConfigurationSource` bean that Spring Security's `CorsConfigurer` discovers automatically via `withDefaults()`.

**When to use:** Any Spring Security 5+ application that needs per-path CORS control.

**Key rule from STATE.md:** Either the bean OR `http.cors(withDefaults())` alone causes OPTIONS preflight to return 403. BOTH are required.

```java
// Source: Spring Security 7.0.4 bytecode + STATE.md accumulated context
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import static org.springframework.security.config.Customizer.withDefaults;

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:8080"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}

// In filterChain():
http.cors(withDefaults())   // discovers the CorsConfigurationSource bean above
    .headers(headers -> headers
        .contentTypeOptions(withDefaults())   // X-Content-Type-Options: nosniff
        .frameOptions(fo -> fo.deny())        // X-Frame-Options: DENY
    )
```

[VERIFIED: Spring Security 7.0.4 bytecode — `CorsConfigurer`, `HeadersConfigurer`, `FrameOptionsConfig.deny()`, `ContentTypeOptionsConfig` (default = nosniff)]

### Pattern 2: Bean Validation on @RequestBody

**What:** Add `@Valid` to `@RequestBody` parameters in controllers; Spring MVC calls the validator before entering the method body. Violations throw `MethodArgumentNotValidException`.

**When to use:** All auth DTOs receiving untrusted input.

```java
// Source: Jakarta Validation 3.1.1 API + Spring MVC
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

public class RegisterRequest {
    @NotBlank
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
    // ...
}

// In AuthController:
@PostMapping("/login")
public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) { ... }
```

[VERIFIED: `jakarta.validation:jakarta.validation-api:3.1.1` is on the classpath via spring-boot-starter-web; `@NotBlank` and `@Email` are in that API jar. Hibernate Validator (implementation) must be added via spring-boot-starter-validation]

### Pattern 3: @RestControllerAdvice Global Exception Handler

**What:** A single class annotated `@RestControllerAdvice` intercepts `MethodArgumentNotValidException` across all controllers before the response reaches the client.

**When to use:** Any Spring MVC app that needs uniform JSON error shapes from validation failures.

```java
// Source: Spring MVC + Jakarta Validation
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));

        return Map.of(
            "status", "error",
            "message", message,
            "timestamp", Instant.now().toString()
        );
    }
}
```

[ASSUMED: The exact response field names (`status`, `message`, `timestamp`) match SUCCESS CRITERIA verbatim — locked by decision D-09]

### Pattern 4: Extending RateLimitFilter for Two Path Groups

**What:** Add a second `ConcurrentHashMap<String, Bucket>` keyed on IP. Override `shouldNotFilter()` is replaced by inline path-dispatch logic in `doFilterInternal()`.

**Design constraint from existing code:** The current `shouldNotFilter()` returns `true` for anything that is NOT `/api/auth/`. This means non-auth, non-restaurant paths are skipped entirely. For restaurant paths, a second bucket map is needed — `shouldNotFilter()` must be updated to exclude paths that match neither pattern.

**Test constructor impact:** The current 2-arg test constructor `RateLimitFilter(int maxRequests, int windowMinutes)` drives auth-path tests. After extension, tests for restaurant paths need a way to inject restaurant bucket parameters. Options:
- Add a 4-arg constructor: `RateLimitFilter(int authMax, int authWindow, int restaurantMax, int restaurantWindow)`.
- The default no-arg constructor reads from `AppConfig` as before.

[VERIFIED: existing RateLimitFilter.java source — line 41-51 shows current constructor pattern]

### Anti-Patterns to Avoid

- **`@CrossOrigin` on controllers alongside a global CORS bean:** Controller-level `@CrossOrigin(origins = "*")` overrides the SecurityConfig CORS bean for that controller's endpoints. All four controllers (`AuthController`, `RestaurantController`, `AnalyticsController`, `InspectionController`) carry this annotation and must have it removed. [VERIFIED: grep on codebase]
- **`http.cors()` without a bean:** Calling `http.cors(withDefaults())` with no `CorsConfigurationSource` bean in context makes Spring Security fall back to Spring MVC's CORS handling — OPTIONS preflights return 403 because no policy is registered. [ASSUMED based on STATE.md note which is itself [VERIFIED] from prior phase execution]
- **`allowCredentials(true)` with wildcard origin:** Browser rejects `Access-Control-Allow-Credentials: true` if `Access-Control-Allow-Origin: *`. Since the policy uses an explicit origin (`http://localhost:8080`), `allowCredentials(true)` is safe and required for JWT auth from browser clients. [ASSUMED: standard CORS spec behavior]
- **Adding `spring-boot-starter-validation` and expecting it to work without `@Valid` on parameters:** The `@NotBlank` on DTO fields is only enforced when Spring calls the validator. That happens only when the controller method parameter has `@Valid`. Missing `@Valid` on even one parameter means that DTO's constraints are silently ignored.
- **Calling `http.headers()` with no customizer:** `http.headers(withDefaults())` in Spring Security 6+ enables ALL default headers, including X-XSS-Protection. The correct call is a narrowed customizer that enables only the two required headers and leaves all others at their default state. Using `withDefaults()` on `headers()` is acceptable since it enables `contentTypeOptions` and `frameOptions` by default (among others), but if the test only checks for exactly 2 headers, using explicit customizers avoids false failures from extra headers.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Per-IP token bucket | Custom counter + timestamp map | Bucket4j 7.6.1 | Already in codebase; handles concurrency, refill math, clock drift |
| Validation error responses | Manual field inspection in each controller | `@RestControllerAdvice` + `MethodArgumentNotValidException` | Spring MVC fires this automatically; manual handling misses nested validation, list fields |
| CORS header injection | `OncePerRequestFilter` writing headers manually | Spring Security `CorsConfigurer` + `CorsConfigurationSource` | Handles preflight response body, 403 for unlisted origins, method/header matching automatically |
| Security headers | Custom filter writing header strings | Spring Security `headers()` DSL | DSL validates at startup, handles all response paths including error responses |

---

## Common Pitfalls

### Pitfall 1: @CrossOrigin Overrides Global CORS Bean

**What goes wrong:** After adding the `CorsConfigurationSource` bean and `http.cors(withDefaults())`, OPTIONS preflight to `/api/auth/login` from an unlisted origin returns 200 with `Access-Control-Allow-Origin: *` instead of 403.

**Why it happens:** `@CrossOrigin(origins = "*", allowedHeaders = "*")` on `AuthController` (and three other controllers) is processed by Spring MVC's handler mapping before Spring Security's CORS filter checks the bean policy. Spring MVC's per-controller CORS wins.

**How to avoid:** Remove `@CrossOrigin` from `AuthController`, `RestaurantController`, `AnalyticsController`, `InspectionController` when adding the global CORS bean.

**Warning signs:** Integration test using `MockMvc` with an `Origin: http://evil.example.com` header returns status 200 instead of 403 on auth endpoints.

[VERIFIED: grep on codebase found `@CrossOrigin(origins = "*")` on 4 controller classes]

### Pitfall 2: hibernate-validator Absent — @Valid Silently Ignored

**What goes wrong:** After adding `@NotBlank` to `AuthRequest.password` and `@Valid` to the controller parameter, POSTing `{"username":"x","password":""}` still returns 200 (or an exception other than 400), not HTTP 400.

**Why it happens:** `spring-boot-starter-web` pulls `jakarta.validation-api` (the interface jar) but not `hibernate-validator` (the implementation). Spring MVC's `SmartValidator` logs `NoProviderFoundException` and skips validation entirely.

**How to avoid:** Add `spring-boot-starter-validation` to `pom.xml`. This is the only way to get `hibernate-validator` into the classpath.

**Warning signs:** `mvn test` output contains `INFO OptionalValidatorFactoryBean -- Failed to set up a Bean Validation provider: jakarta.validation.NoProviderFoundException` — this is already visible in the current test run, confirming validation is non-functional today.

[VERIFIED: `mvn test` output — NoProviderFoundException logged repeatedly by existing controller tests]

### Pitfall 3: RateLimitFilter Constructor Incompatibility After Extension

**What goes wrong:** After extending `RateLimitFilter` with a second bucket map, existing `RateLimitFilterTest` fails to compile because the 2-arg test constructor no longer captures both bucket configs.

**Why it happens:** `RateLimitFilterTest` uses `new RateLimitFilter(3, 1)` — only auth parameters. If the new constructor signature changes (e.g., to 4-arg), the test must be updated.

**How to avoid:** Either (a) add a 4-arg constructor that takes both auth and restaurant parameters, keeping the 2-arg constructor for backward-compat with existing tests; or (b) update `RateLimitFilterTest` to use the new 4-arg form.

**Warning signs:** Compilation error in `RateLimitFilterTest` during `mvn test`.

[VERIFIED: RateLimitFilterTest.java — `new RateLimitFilter(3, 1)` on line 7]

### Pitfall 4: SecurityConfigTest Breaks After Adding CORS + Headers

**What goes wrong:** The existing `SecurityConfigTest` (which manually bootstraps Spring Security context using `AnnotationConfigWebApplicationContext`) fails with a `NullPointerException` or context refresh error after adding the `CorsConfigurationSource` bean.

**Why it happens:** `SecurityConfigTest` registers `SecurityConfig.class` directly. If `SecurityConfig.filterChain()` now requires a `CorsConfigurationSource` bean (via `withDefaults()` discovery), the minimal context must expose that bean too — otherwise context refresh fails with a missing dependency or the CORS configurer can't wire up.

**How to avoid:** The `CorsConfigurationSource` bean is defined as a `@Bean` in `SecurityConfig` itself (not in a separate config class). Since `SecurityConfigTest` registers `SecurityConfig.class`, the bean is automatically available in the test context.

**Warning signs:** `SecurityConfigTest` fails with `NoSuchBeanDefinitionException: CorsConfigurationSource` or context refresh error.

[ASSUMED — risk is LOW because the bean will be co-located in SecurityConfig]

### Pitfall 5: `http.headers()` Disables Default Headers by Omission

**What goes wrong:** Calling `http.headers(h -> h.contentTypeOptions(withDefaults()))` explicitly without also chaining `.frameOptions(...)` disables frameOptions (and vice versa), because `headers()` operates on an explicit opt-in model when customized.

**Why it happens:** When a customizer is passed to `http.headers(customizer)`, Spring Security applies only the headers that the customizer configures. Omitting `frameOptions` in the customizer means X-Frame-Options is not set.

**How to avoid:** Chain both: `headers(h -> h.contentTypeOptions(withDefaults()).frameOptions(fo -> fo.deny()))`.

[VERIFIED: Spring Security 7.0.4 HeadersConfigurer bytecode — `contentTypeOptions()` and `frameOptions()` are independent methods requiring explicit invocation]

---

## Code Examples

### CorsConfigurationSource Bean (complete)

```java
// Source: Spring Web 7.0.6 (UrlBasedCorsConfigurationSource) + Spring Security 7.0.4 (Customizer.withDefaults())
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import static org.springframework.security.config.Customizer.withDefaults;

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:8080"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}
```

### filterChain() additions (excerpt)

```java
// Source: Spring Security 7.0.4 bytecode-verified API
http
    .cors(withDefaults())         // discovers CorsConfigurationSource bean
    .headers(headers -> headers
        .contentTypeOptions(withDefaults())     // X-Content-Type-Options: nosniff
        .frameOptions(fo -> fo.deny())          // X-Frame-Options: DENY
    )
    // ... existing csrf, sessionManagement, authorizeHttpRequests, etc.
```

### GlobalExceptionHandler

```java
// Source: Spring MVC @RestControllerAdvice pattern
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return Map.of(
            "status", "error",
            "message", message,
            "timestamp", Instant.now().toString()
        );
    }
}
```

### RateLimitFilter extension (structural sketch)

```java
// Source: existing RateLimitFilter.java pattern — applied to second bucket map
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> restaurantBuckets = new ConcurrentHashMap<>();   // NEW
    private final int authMax;
    private final int authWindow;
    private final int restaurantMax;     // NEW
    private final int restaurantWindow;  // NEW

    public RateLimitFilter() {
        this(AppConfig.getAuthRateLimitRequests(), AppConfig.getAuthRateLimitWindowMinutes(),
             AppConfig.getRestaurantRateLimitRequests(), AppConfig.getRestaurantRateLimitWindowMinutes());
    }

    public RateLimitFilter(int authMax, int authWindow, int restaurantMax, int restaurantWindow) {
        this.authMax = authMax;
        this.authWindow = authWindow;
        this.restaurantMax = restaurantMax;
        this.restaurantWindow = restaurantWindow;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/auth/") && !uri.startsWith("/api/restaurants/");
    }

    @Override
    protected void doFilterInternal(...) {
        String ip = request.getRemoteAddr();
        String uri = request.getRequestURI();

        Bucket bucket;
        if (uri.startsWith("/api/auth/")) {
            bucket = authBuckets.computeIfAbsent(ip, k -> newBucket(authMax, authWindow));
        } else {
            bucket = restaurantBuckets.computeIfAbsent(ip, k -> newBucket(restaurantMax, restaurantWindow));
        }

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"error\",\"message\":\"Too many requests\"}");
        }
    }

    private Bucket newBucket(int max, int windowMinutes) {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(max, Refill.greedy(max, Duration.ofMinutes(windowMinutes))))
            .build();
    }
}
```

### application.properties additions

```properties
# Rate limiting — restaurant endpoints (/api/restaurants/**)
# ENV: RESTAURANT_RATE_LIMIT_REQUESTS, RESTAURANT_RATE_LIMIT_WINDOW_MINUTES
restaurant.rate-limit.requests=100
restaurant.rate-limit.window-minutes=1

# Reverse proxy / HTTPS support
server.forward-headers-strategy=native
```

### pom.xml addition

```xml
<!-- Bean Validation implementation (hibernate-validator) — not transitive from spring-boot-starter-web in SB4 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `WebSecurityConfigurerAdapter` | `SecurityFilterChain` bean | Spring Security 5.7 / SB 2.7 | Already migrated — this project uses the new API |
| `javax.validation.*` | `jakarta.validation.*` | Spring Boot 3.0 / Jakarta EE 9 | Already on jakarta namespace — no change needed |
| `http.cors()` no-arg (deprecated) | `http.cors(withDefaults())` or `http.cors(c -> ...)` | Spring Security 6.1 | Use `withDefaults()` — confirmed in SS 7.0.4 bytecode |
| `http.headers()` no-arg (deprecated) | `http.headers(h -> h.xxx())` | Spring Security 6.1 | Explicit customizer required to avoid deprecation warning |

**Deprecated/outdated:**
- `@CrossOrigin(origins = "*")` on controllers: technically valid Java but conflicts with global security CORS policy. Must be removed.
- `http.csrf(csrf -> csrf.disable())`: stays as-is (stateless JWT app, unchanged).

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `allowCredentials(true)` required for browser JWT auth over CORS | Code Examples — CorsConfigurationSource | Low: if wrong, change to false; browser behavior is well-documented |
| A2 | `http.headers(withDefaults())` enables both contentTypeOptions and frameOptions by default | Anti-Patterns | Low: VERIFIED the individual methods exist; if defaults changed, switch to explicit chain |
| A3 | `GlobalExceptionHandler` in `com.aflokkat.controller` package is auto-scanned by component scan | Architecture Patterns | Low: standard Spring Boot component scan includes all subpackages of the base package |
| A4 | SecurityConfigTest context refresh will succeed after CORS bean is added to SecurityConfig | Pitfall 4 | Low: CORS bean co-located in SecurityConfig — no extra registration needed |

---

## Open Questions (RESOLVED)

1. **Does `http.cors(withDefaults())` also need `CorsFilter` to be in the filter chain order?**
   - What we know: STATE.md explicitly says "both the bean AND `http.cors(withDefaults())`" required — from a prior phase that tested this. Spring Security's `CorsConfigurer` inserts a `CorsFilter` into the security filter chain when configured.
   - What's unclear: Whether `server.forward-headers-strategy=native` (D-13) interacts with CORS processing order.
   - Recommendation: D-13 adds `ForwardedHeaderFilter` for X-Forwarded-* processing — it is independent of CORS. No interaction expected.

2. **Should `allowCredentials(true)` be set on the CORS configuration?**
   - What we know: the app issues JWTs that live in `localStorage`; browser `fetch()` calls with `Authorization` header do not require `allowCredentials`. `allowCredentials` is for cookie-based auth.
   - What's unclear: Whether any current frontend JS uses `credentials: 'include'`.
   - Recommendation: Set `allowCredentials(false)` (the default) — simpler, avoids browser restrictions. The `Authorization` header works without it.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Maven | Build | ✓ | (project build tool) | — |
| Java 25 | Compilation | ✓ | 25 | — |
| Spring Boot 4.0.5 | Runtime | ✓ (in pom.xml) | 4.0.5 | — |
| Spring Security 7.0.4 | CORS/headers DSL | ✓ (transitive) | 7.0.4 | — |
| spring-boot-starter-validation | Bean Validation | ✗ not in pom.xml | — (4.0.5 BOM-managed) | **Must add — no fallback** |
| Bucket4j 7.6.1 | Rate limit extension | ✓ (in pom.xml) | 7.6.1 | — |
| Maven Central | Download validation starter | ✓ | reachable | — |

**Missing dependencies with no fallback:**
- `spring-boot-starter-validation` — must be added to pom.xml before any @NotBlank constraint will be enforced at runtime.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito 5.17.0 |
| Config file | pom.xml (maven-surefire-plugin) |
| Quick run command | `mvn test -Dtest=SecurityConfigTest,RateLimitFilterTest,GlobalExceptionHandlerTest -q` |
| Full suite command | `mvn test -q` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SEC-01 | OPTIONS preflight from unlisted origin returns 403 | unit (MockMvc) | `mvn test -Dtest=SecurityConfigTest -q` | Partial — test exists but lacks CORS assertions |
| SEC-02 | Every response includes X-Content-Type-Options and X-Frame-Options | unit (MockMvc) | `mvn test -Dtest=SecurityConfigTest -q` | Partial — same test, new assertions needed |
| SEC-03 | POST /api/auth/login with empty password returns 400 | unit (MockMvc + @Valid) | `mvn test -Dtest=AuthControllerValidationTest -q` | ❌ Wave 0 |
| SEC-04 | 400 body contains status, message, timestamp fields | unit | `mvn test -Dtest=GlobalExceptionHandlerTest -q` | ❌ Wave 0 |
| SEC-05 | Auth rate-limit already tested | unit | `mvn test -Dtest=RateLimitFilterTest -q` | ✅ exists |
| SEC-06 | Restaurant path returns 429 after 100 req/min | unit | `mvn test -Dtest=RateLimitFilterTest -q` | Partial — test exists but only covers auth path |
| SEC-07 | server.forward-headers-strategy=native in properties | manual verify | grep on application.properties | — |
| SEC-08 | JWT startup assertion already tested | unit | `mvn test -Dtest=AppConfigTest -q` | ✅ exists |

### Sampling Rate

- **Per task commit:** `mvn test -Dtest=SecurityConfigTest,RateLimitFilterTest -q`
- **Per wave merge:** `mvn test -q`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/com/st4r4x/controller/AuthControllerValidationTest.java` — covers SEC-03 (empty field → 400)
- [ ] `src/test/java/com/st4r4x/controller/GlobalExceptionHandlerTest.java` — covers SEC-04 (JSON shape: status, message, timestamp)
- [ ] Extend `SecurityConfigTest` — add CORS assertions (SEC-01: unlisted origin → 403; SEC-02: header presence)
- [ ] Extend `RateLimitFilterTest` — add restaurant-path bucket test (SEC-06)

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT (existing) + @NotBlank prevents empty-credential bypass |
| V3 Session Management | no | Stateless JWT |
| V4 Access Control | yes | Spring Security requestMatchers (existing) |
| V5 Input Validation | yes | `jakarta.validation` + `@Valid` + GlobalExceptionHandler |
| V6 Cryptography | no | JWT secret assertion already done (Phase 13) |
| V14 Configuration | yes | CORS policy, security headers, forward-headers-strategy |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| CORS wildcard → session riding | Spoofing | Explicit origin whitelist via CorsConfigurationSource |
| Clickjacking via iframe | Tampering | X-Frame-Options: DENY |
| MIME sniffing → XSS | Tampering | X-Content-Type-Options: nosniff |
| Credential stuffing / brute-force | Spoofing | Bucket4j rate limit on /api/auth/** (existing) |
| API scraping | Denial of Service | Bucket4j rate limit on /api/restaurants/** (SEC-06) |
| Empty/missing credentials bypass | Spoofing | @NotBlank + @Valid → HTTP 400 before AuthService |
| Header injection via reverse proxy | Elevation of Privilege | server.forward-headers-strategy=native (SEC-07) |

---

## Project Constraints (from CLAUDE.md)

- Package: `com.aflokkat` (renamed from `com.st4r4x` in Phase 21 — use `com.aflokkat` in all new files)
- Build: Maven (`mvn`) — no Gradle
- New classes use `@RestControllerAdvice` in `com.aflokkat.controller` package
- `application.properties` is the config file (no dotenv in production)
- Docker Compose plugin: `docker compose` (not `docker-compose`)
- JUnit 5 + Mockito — `@WebMvcTest` is forbidden on Java 25 (causes StackOverflowError) — use `MockMvcBuilders.standaloneSetup()` pattern
- Mockito mock(Authentication.class) fails on Java 25 — use `UsernamePasswordAuthenticationToken` directly
- `mockStatic(AppConfig.class)` causes VerifyError on Java 25 — use reflection to patch `AppConfig.properties`
- Bucket4j pinned at 7.6.1 — do NOT upgrade

---

## Sources

### Primary (HIGH confidence)

- Maven dependency:tree output — confirmed `hibernate-validator` absent, `jakarta.validation-api:3.1.1` present
- Spring Security 7.0.4 bytecode (local Maven cache) — `CorsConfigurer.class`, `HeadersConfigurer.class`, `FrameOptionsConfig.deny()`, `ContentTypeOptionsConfig`, `Customizer.withDefaults()`
- Spring Web 7.0.6 bytecode (local Maven cache) — `CorsConfigurationSource`, `UrlBasedCorsConfigurationSource`
- Existing codebase source files — `SecurityConfig.java`, `RateLimitFilter.java`, `AppConfig.java`, `AuthController.java`, `AuthRequest.java`, `RegisterRequest.java`, `RefreshRequest.java`
- `mvn test` run — confirms `NoProviderFoundException` (validation non-functional today)

### Secondary (MEDIUM confidence)

- STATE.md accumulated context — "CORS requires both CorsConfigurationSource bean AND http.cors(withDefaults())" — from prior phase execution
- CONTEXT.md — all locked decisions (D-01 through D-14) are accepted without re-verification

### Tertiary (LOW confidence)

- None — all claims verified via codebase inspection or bytecode analysis

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified via `mvn dependency:tree` and bytecode
- Architecture: HIGH — all patterns grounded in existing code + SS7 bytecode
- Pitfalls: HIGH for pitfalls 1 and 2 (VERIFIED in codebase); MEDIUM for pitfall 4 (ASSUMED low risk)

**Research date:** 2026-04-13
**Valid until:** 2026-05-13 (Spring Boot 4 is moving fast; re-verify if >30 days)
