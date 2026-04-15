# Phase 16: Security Hardening - Context

**Gathered:** 2026-04-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire an explicit CORS policy into SecurityConfig, add security response headers to every HTTP response, annotate all auth-layer `@RequestBody` DTOs with validation constraints and handle `MethodArgumentNotValidException` globally, extend `RateLimitFilter` to cover `/api/restaurants/**` at a higher per-IP threshold, and add `server.forward-headers-strategy=native` for HTTPS-ready reverse proxy operation.

**Out of scope:** non-auth DTO validation (ReportRequest), CSP/HSTS/Referrer-Policy headers, CORS origin parameterization via env var.

</domain>

<decisions>
## Implementation Decisions

### CORS Policy
- **D-01:** Whitelist `http://localhost:8080` only — tight policy that satisfies the success criterion (unlisted origin → 403). No wildcard.
- **D-02:** Allowed methods: `GET, POST, PUT, DELETE, OPTIONS`. All methods the API actually uses; OPTIONS is mandatory for preflight.
- **D-03:** Wire CORS via **both** `CorsConfigurationSource` bean **and** `http.cors(withDefaults())` in `SecurityConfig` — either alone causes OPTIONS preflight 403 (carried forward from STATE.md accumulated context).

### Security Response Headers
- **D-04:** Add exactly the 2 headers required by SEC-02: `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY`. No CSP, no HSTS, no Referrer-Policy — those belong in a future hardening pass.

### Input Validation
- **D-05:** `AuthRequest`: `@NotBlank` on both `username` and `password`.
- **D-06:** `RegisterRequest`: `@NotBlank` on `username` and `password`; `@NotBlank @Email` on `email`. No `@Size` min on password — not in requirements.
- **D-07:** `RefreshRequest`: `@NotBlank` on `refreshToken`.
- **D-08:** Scope: auth DTOs only (`AuthRequest`, `RegisterRequest`, `RefreshRequest`). `ReportRequest` is controller-role-gated — skip for this phase.
- **D-09:** Add a `@RestControllerAdvice` global exception handler for `MethodArgumentNotValidException` → HTTP 400 with JSON body `{status, message, timestamp}`. This is the new class; no existing `@RestControllerAdvice` exists in the codebase.

### Rate Limiting Extension
- **D-10:** `/api/restaurants/**` limit: **100 req/min per IP** (10× the auth limit of 10/min).
- **D-11:** Implement in the **same `RateLimitFilter`** — add a second `ConcurrentHashMap<String, Bucket>` for restaurant-path buckets, add a second `shouldNotFilter` branch. Keeps all rate-limit logic co-located.
- **D-12:** Add `restaurant.rate-limit.requests=100` and `restaurant.rate-limit.window-minutes=1` to `application.properties`. Consistent with the existing `auth.rate-limit.*` pattern. Read via new `AppConfig.getRestaurantRateLimitRequests()` / `getRestaurantRateLimitWindowMinutes()` methods.

### HTTPS-Ready Config
- **D-13:** Add `server.forward-headers-strategy=native` to `application.properties` (SEC-07). No other reverse-proxy config needed for this phase.

### JWT Startup Assertion
- **D-14:** Already implemented in Phase 13 (`AppConfig.getJwtSecret()` throws `IllegalStateException` if `JWT_SECRET` is missing or < 32 chars). SEC-08 requirement is satisfied in code — this phase only needs to verify it in the existing test suite, no new code required.

### Claude's Discretion
- Security headers implementation: use Spring Security's `headers()` DSL inside `SecurityConfig.filterChain()` — cleaner than a custom `OncePerRequestFilter`.
- `@RestControllerAdvice` class location: `com.aflokkat.controller` package (alongside other controllers).
- Validation error message format: collect all field errors from `MethodArgumentNotValidException.getBindingResult()` into a comma-separated `message` string.
- `allowedHeaders` for CORS: `Authorization`, `Content-Type` (covers JWT bearer and JSON body).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` §Security — SEC-01 through SEC-08 (all 8 requirements for this phase)

### Phase Goal & Success Criteria
- `.planning/ROADMAP.md` §Phase 16 — 5 success criteria (CORS 403, response headers, 400 validation error, 429 rate limit, startup fail-fast)

### Files to Read Before Modifying
- `src/main/java/com/aflokkat/config/SecurityConfig.java` — add `CorsConfigurationSource` bean and `http.cors(withDefaults())` + `http.headers(...)` for security headers
- `src/main/java/com/aflokkat/security/RateLimitFilter.java` — extend with second bucket map for `/api/restaurants/**`
- `src/main/java/com/aflokkat/config/AppConfig.java` — add `getRestaurantRateLimitRequests()` / `getRestaurantRateLimitWindowMinutes()` alongside existing `getAuthRateLimit*()` methods
- `src/main/resources/application.properties` — add `restaurant.rate-limit.requests=100`, `restaurant.rate-limit.window-minutes=1`, `server.forward-headers-strategy=native`
- `src/main/java/com/aflokkat/dto/AuthRequest.java` — add `@NotBlank` on username + password
- `src/main/java/com/aflokkat/dto/RegisterRequest.java` — add `@NotBlank` on username + password, `@NotBlank @Email` on email
- `src/main/java/com/aflokkat/dto/RefreshRequest.java` — add `@NotBlank` on refreshToken
- `src/test/java/com/aflokkat/config/SecurityConfigTest.java` — existing test; verify CORS and header assertions fit new config
- `src/test/java/com/aflokkat/security/RateLimitFilterTest.java` — existing test; extend for restaurant-path bucket

### Prior Phase Context
- `.planning/STATE.md` §Accumulated Context — CORS dual-wiring requirement, anyRequest().permitAll() rationale, Mockito/Java 25 test constraints
- `.planning/phases/13-config-docker-hardening/13-CONTEXT.md` — JWT startup assertion already implemented (D-02/D-03 there = D-14 here)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RateLimitFilter.java` — already implements per-IP Bucket4j token-bucket for `/api/auth/**`; extend with second bucket map for `/api/restaurants/**`
- `AppConfig.java` — `getAuthRateLimitRequests()` / `getAuthRateLimitWindowMinutes()` pattern to copy for restaurant rate-limit properties
- `SecurityConfig.filterChain()` — add `cors(withDefaults())` and `headers()` DSL calls here; existing structure is clean

### Established Patterns
- Rate limit config reads from `application.properties` via `AppConfig.getIntProperty(key, default)` — use same pattern for restaurant limits
- No `@RestControllerAdvice` exists anywhere in the codebase — create from scratch
- `anyRequest().permitAll()` stays unchanged (client-side IIFE guards for `/admin`/`/dashboard` are intentional)
- Package is `com.aflokkat` (renamed in Phase 21 from `com.st4r4x`) — use correct package in all new classes

### Integration Points
- `SecurityConfig.filterChain()` — `cors()` and `headers()` are added here
- `AuthController` — `@RequestBody AuthRequest`, `@RequestBody RegisterRequest`, `@RequestBody RefreshRequest` need `@Valid` annotation added to parameter
- New `GlobalExceptionHandler` class wires into Spring MVC automatically via `@RestControllerAdvice`
- `pom.xml` — `spring-boot-starter-validation` dependency needed if not already present; check before adding

</code_context>

<specifics>
## Specific Ideas

- The STATE.md note is explicit: CORS needs **both** the bean **and** `http.cors(withDefaults())` — don't forget either half.
- The ROADMAP success criterion for CORS tests an **unlisted** origin returning 403, not just that a listed origin gets 200. The test should send an `Origin: http://evil.example.com` header.
- The ROADMAP success criterion for validation tests **empty password** specifically (not null, not missing field) — ensure `@NotBlank` covers this (it does: empty string fails `@NotBlank`).
- The ROADMAP success criterion for rate limiting tests the **same IP** hitting login > N times within 1 minute. The test for restaurant rate-limiting should independently verify the 100-req threshold doesn't bleed into the auth 10-req threshold.
- `pom.xml` may already have `spring-boot-starter-validation` — Spring Boot starter includes it transitively via `spring-boot-starter-web`. Verify before adding an explicit dependency.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 16-security-hardening*
*Context gathered: 2026-04-13*
