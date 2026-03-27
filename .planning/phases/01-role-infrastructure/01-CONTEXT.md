# Phase 1: Role Infrastructure - Context

**Gathered:** 2026-03-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Extend JWT authentication and Spring Security to support two roles (CUSTOMER / CONTROLLER), implement URL-level access guards, add rate limiting on auth endpoints, and seed one test account of each role on startup. No UI work in this phase — pure backend API and security layer.

</domain>

<decisions>
## Implementation Decisions

### Registration flow
- Single endpoint: `POST /api/auth/register` with optional `signupCode` field in the request body
- No `signupCode` provided → user becomes `ROLE_CUSTOMER`
- Correct `signupCode` (matches env var) → user becomes `ROLE_CONTROLLER`
- Wrong `signupCode` → HTTP 400 with generic message `"Invalid registration request"` (do not reveal the code system exists)
- Env var name: `CONTROLLER_SIGNUP_CODE` (consistent with existing ALL_CAPS pattern in AppConfig)
- If `CONTROLLER_SIGNUP_CODE` env var is not set → controller registration is disabled; any attempt returns 400 (fail-safe)

### URL security scope
- `/api/auth/**` → fully public (login, register, refresh)
- `/api/restaurants/**` → fully public (read-only NYC data, no reason to gate)
- `/api/inspections/**` → fully public (read-only NYC data)
- `/api/users/**` → require valid JWT (any role); unauthenticated → 401
- `/api/reports/**` → require `ROLE_CONTROLLER`; unauthenticated → 401, valid CUSTOMER JWT → 403
- Swagger (`/swagger-ui.html`, `/api-docs/**`, `/v3/api-docs/**`) → fully public
- View routes (non-`/api/**`) → left fully open for now (view-level auth is Phase 3 scope)

### Access denied responses
- Unauthenticated requests to protected endpoints → 401 with JSON body (already handled by existing authenticationEntryPoint in SecurityConfig for `/api/**`)
- Authenticated but wrong role → 403 (handled by Spring Security's default AccessDeniedException handler — add an accessDeniedHandler returning JSON 403)

### Role values
- Store `ROLE_CUSTOMER` / `ROLE_CONTROLLER` in `UserEntity.role` (replaces current `ROLE_USER`)
- Use `hasRole("CUSTOMER")` / `hasRole("CONTROLLER")` in security rules — Spring strips the prefix internally

### Claude's Discretion
- Rate limiting implementation (Bucket4j or custom Servlet filter) and threshold config placement
- Seed account credentials and idempotency behavior (skip if already exists is the safe default)
- Exact Hibernate migration for the role column value change

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` — AUTH-01 through AUTH-05 (all Phase 1 requirements)
- `.planning/ROADMAP.md` — Phase 1 goal, success criteria, and dependency notes

### Existing security layer (must read before modifying)
- `src/main/java/com/aflokkat/config/SecurityConfig.java` — current `anyRequest().permitAll()` to replace; existing authenticationEntryPoint, JWT filter wiring
- `src/main/java/com/aflokkat/security/JwtAuthenticationFilter.java` — extracts role claim as `SimpleGrantedAuthority(role)`; already supports ROLE_* values
- `src/main/java/com/aflokkat/security/JwtUtil.java` — `generateAccessToken(username, role)` — token already carries role claim

### Auth layer (must read before modifying)
- `src/main/java/com/aflokkat/service/AuthService.java` — `register()` hardcodes `"ROLE_USER"`; needs signup code logic
- `src/main/java/com/aflokkat/dto/RegisterRequest.java` — needs `signupCode` field added
- `src/main/java/com/aflokkat/controller/AuthController.java` — `POST /api/auth/register` endpoint
- `src/main/java/com/aflokkat/entity/UserEntity.java` — role field already exists as String column

### Config pattern
- `src/main/java/com/aflokkat/config/AppConfig.java` — env var resolution pattern (`System.getenv(key.replace(".", "_").toUpperCase())`) — follow this pattern for `CONTROLLER_SIGNUP_CODE`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `UserEntity.role` (String column): already exists — just change the stored values from `ROLE_USER` to `ROLE_CUSTOMER` / `ROLE_CONTROLLER`
- `JwtAuthenticationFilter`: already creates `SimpleGrantedAuthority(role)` from the JWT claim — no changes needed to the filter itself
- `AppConfig.getProperty()`: env var → .env → application.properties fallback chain — reuse this pattern for `CONTROLLER_SIGNUP_CODE`
- `@EnableMethodSecurity` on SecurityConfig: `@PreAuthorize("hasRole('CONTROLLER')")` is available for method-level guards if needed

### Established Patterns
- Error responses: anonymous `Object` with `status` + `message` fields (see `AuthController.errorResponse()`) — keep same shape for auth errors
- Config keys: ALL_CAPS env var names derived from dot-notation property names (e.g., `redis.host` → `REDIS_HOST`)

### Integration Points
- `SecurityConfig.filterChain()`: replace `anyRequest().permitAll()` with explicit `antMatchers` — this is the single change point for URL-level guards
- `AuthService.register()`: add signup code check before role assignment
- `Application.java`: add a `CommandLineRunner` or `ApplicationRunner` bean for seeding (or a separate `DataSeeder` @Component)

</code_context>

<specifics>
## Specific Ideas

- No specific references — open to standard Spring Security antMatcher patterns and Bucket4j for rate limiting

</specifics>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-role-infrastructure*
*Context gathered: 2026-03-27*
