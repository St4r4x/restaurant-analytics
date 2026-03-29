---
phase: 01-role-infrastructure
plan: 03
subsystem: auth
tags: [rate-limiting, bucket4j, servlet-filter, spring-security, brute-force-protection]

# Dependency graph
requires:
  - phase: 01-role-infrastructure
    provides: AppConfig getters pattern for property/env-var resolution
provides:
  - Per-IP rate limiting on /api/auth/** endpoints via Bucket4j 7.6.1 token-bucket
  - RateLimitFilter @Component @Order(1) registered automatically before JWT filter
  - Configurable threshold via auth.rate-limit.requests and auth.rate-limit.window-minutes
affects: [02-customer-features, 03-controller-features]

# Tech tracking
tech-stack:
  added: [bucket4j-core 7.6.1 (io.github.bucket4j)]
  patterns: [OncePerRequestFilter with shouldNotFilter() scoping, ConcurrentHashMap per-IP bucket registry, test constructor for threshold injection]

key-files:
  created:
    - src/main/java/com/aflokkat/security/RateLimitFilter.java
    - src/test/java/com/aflokkat/security/RateLimitFilterTest.java
  modified:
    - pom.xml
    - src/main/java/com/aflokkat/config/AppConfig.java
    - src/main/resources/application.properties

key-decisions:
  - "Used Bucket4j 7.6.1 (not 8.x) — required for Java 11 compatibility; actual Maven package is io.github.bucket4j not com.bucket4j"
  - "RateLimitFilter registered via @Component only — not also via http.addFilterBefore() — to avoid double-application"
  - "@Order(1) ensures rate limiting runs before JwtAuthenticationFilter"
  - "Test constructor RateLimitFilter(int, int) allows unit tests without AppConfig / property loading"
  - "shouldNotFilter() returns true for all non-/api/auth/** paths — auth endpoints only"

patterns-established:
  - "Bucket4j token-bucket pattern: Bucket.builder().addLimit(Bandwidth.classic(n, Refill.greedy(n, Duration.ofMinutes(w))))"
  - "Per-IP bucket registry: ConcurrentHashMap<String, Bucket> with computeIfAbsent"
  - "HTTP 429 response: response.setStatus(429) + JSON body {status:error,message:Too many requests}"

requirements-completed: [AUTH-04]

# Metrics
duration: 15min
completed: 2026-03-27
---

# Phase 1 Plan 03: Rate Limiting Summary

**Per-IP Bucket4j 7.6.1 token-bucket rate limiter on /api/auth/** returning HTTP 429 after threshold exceeded**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-27T13:55:00Z
- **Completed:** 2026-03-27T14:10:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Bucket4j 7.6.1 dependency added to pom.xml with correct Maven groupId
- AppConfig extended with `getAuthRateLimitRequests()` and `getAuthRateLimitWindowMinutes()` getters
- `RateLimitFilter` implemented as `@Component @Order(1) OncePerRequestFilter` with per-IP bucket registry
- 2 unit tests proving threshold enforcement (passes at limit, 429 on exceed)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add bucket4j-core 7.6.0 to pom.xml and add AppConfig rate-limit getters** - `e520afa` (feat)
2. **Task 2 RED: Add failing tests for RateLimitFilter** - `c922dd2` (test)
3. **Task 2 GREEN: Implement RateLimitFilter** - `09df32b` (feat)

## Files Created/Modified
- `src/main/java/com/aflokkat/security/RateLimitFilter.java` - Per-IP rate limiting filter for /api/auth/** endpoints
- `src/test/java/com/aflokkat/security/RateLimitFilterTest.java` - Unit tests for threshold enforcement (2 tests)
- `pom.xml` - Added bucket4j-core 7.6.1 dependency
- `src/main/java/com/aflokkat/config/AppConfig.java` - Added `getAuthRateLimitRequests()` and `getAuthRateLimitWindowMinutes()`
- `src/main/resources/application.properties` - Added `auth.rate-limit.requests=10` and `auth.rate-limit.window-minutes=1`

## Decisions Made
- Bucket4j 7.6.1 used (not 7.6.0 which is absent from Maven Central); actual package is `io.github.bucket4j` not `com.bucket4j` as suggested in plan context
- RateLimitFilter registered via `@Component` only — not also via `http.addFilterBefore()` to avoid double-application (per plan anti-pattern note)
- Test constructor `RateLimitFilter(int maxRequests, int windowMinutes)` enables pure unit tests without Spring context or AppConfig loading

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected Bucket4j package path from com.bucket4j to io.github.bucket4j**
- **Found during:** Task 2 GREEN (RateLimitFilter compilation)
- **Issue:** Plan context specified `import com.bucket4j.Bandwidth/Bucket/Refill` but the actual package in Bucket4j 7.x jar is `io.github.bucket4j.*`
- **Fix:** Linter/compiler feedback revealed the correct package; imports corrected to `io.github.bucket4j.*`
- **Files modified:** src/main/java/com/aflokkat/security/RateLimitFilter.java
- **Verification:** `mvn test -Dtest=RateLimitFilterTest` exits 0, both tests pass
- **Committed in:** `09df32b` (Task 2 GREEN commit)

**2. [Rule 1 - Bug] Bucket4j version 7.6.0 not available on Maven Central; used 7.6.1**
- **Found during:** Task 1 compile check
- **Issue:** `bucket4j-core:7.6.0` not resolvable from central; 7.6.1 is the correct available version
- **Fix:** Dependency version set to 7.6.1 (linter auto-corrected)
- **Files modified:** pom.xml
- **Verification:** `mvn compile -q` exits 0
- **Committed in:** `e520afa` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 - Bug)
**Impact on plan:** Both fixes necessary for correctness. No scope creep. All must-haves and behavioral truths from plan frontmatter are satisfied.

## Issues Encountered
- Full test suite (`mvn test -q`) has pre-existing failures from plans 01-01 and 01-02 (AuthServiceTest static mock setup, SecurityConfigTest StackOverflow, DataSeederTest assertion). These are out-of-scope for this plan and logged as deferred items. RateLimitFilterTest passes cleanly in isolation.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Rate limiting is active on all /api/auth/** endpoints (register, login, refresh)
- Threshold defaults: 10 requests per minute per IP, configurable via env vars AUTH_RATE_LIMIT_REQUESTS / AUTH_RATE_LIMIT_WINDOW_MINUTES
- Phase 2 feature work can proceed; RateLimitFilter will automatically protect any new /api/auth/ routes added

---
*Phase: 01-role-infrastructure*
*Completed: 2026-03-27*
