---
phase: 16-security-hardening
plan: "04"
subsystem: security/rate-limiting
tags: [rate-limiting, bucket4j, sec-05, sec-06, sec-07, reverse-proxy]
dependency_graph:
  requires: [16-01]
  provides: [restaurant-rate-limit, forward-headers-strategy]
  affects: [RateLimitFilter, AppConfig, application.properties]
tech_stack:
  added: []
  patterns: [independent-bucket-per-path, 4-arg-test-constructor, backward-compatible-2-arg-constructor]
key_files:
  created:
    - src/main/java/com/aflokkat/security/RateLimitFilter.java
    - src/main/java/com/aflokkat/config/AppConfig.java
  modified:
    - src/main/resources/application.properties
    - src/test/java/com/aflokkat/security/RateLimitFilterTest.java
decisions:
  - "Use javax.servlet (not jakarta) — project is on Spring Boot 2.6.15 which ships Servlet 4 / javax namespace"
  - "2-arg backward-compatible constructor added so existing tests need no change"
  - "server.forward-headers-strategy=native verified by grep only — runtime verification deferred to Phase 18 E2E"
metrics:
  duration: ~15 minutes
  completed: 2026-04-14
  tasks_completed: 2
  files_changed: 4
---

# Phase 16 Plan 04: Restaurant Rate Limit + Forward Headers Summary

**One-liner:** Bucket4j restaurant rate limit (100 req/min) with independent per-path buckets and server.forward-headers-strategy=native for HTTPS-readiness.

## What Was Built

### Task 1: AppConfig methods + application.properties (commit 47fcfc0)

Added two static methods to `AppConfig.java` using the same `getIntProperty()` pattern as the existing auth rate-limit methods:

- `getRestaurantRateLimitRequests()` — reads `restaurant.rate-limit.requests`, default 100
- `getRestaurantRateLimitWindowMinutes()` — reads `restaurant.rate-limit.window-minutes`, default 1

Added to `application.properties`:

```properties
restaurant.rate-limit.requests=100
restaurant.rate-limit.window-minutes=1
server.forward-headers-strategy=native
```

### Task 2: RateLimitFilter with dual buckets (commits 8f7ae32, 5eea0a4)

Rewrote `RateLimitFilter` using TDD (RED → GREEN):

- **RED** (8f7ae32): Added 2 new tests using 4-arg constructor — failed with "no suitable constructor found"
- **GREEN** (5eea0a4): Full rewrite with:
  - `authBuckets` + `restaurantBuckets` — two independent `ConcurrentHashMap<String, Bucket>`
  - 4-arg constructor `(authMax, authWindow, restaurantMax, restaurantWindow)` for tests
  - 2-arg backward-compatible constructor `(authMax, authWindow)` — existing tests unchanged
  - `shouldNotFilter()` now returns false for both `/api/auth/` and `/api/restaurants/` paths
  - `doFilterInternal()` routes by URI prefix to the correct bucket map

## Verification Results

| Check | Result |
|-------|--------|
| `grep restaurant.rate-limit.requests=100` | 1 match |
| `grep server.forward-headers-strategy=native` | 1 match |
| `grep -c restaurantBuckets RateLimitFilter.java` | 2 |
| `grep -c "public RateLimitFilter" RateLimitFilter.java` | 3 |
| RateLimitFilterTest (4 tests) | BUILD SUCCESS |
| AppConfigTest (7 tests) | BUILD SUCCESS |

## Decisions Made

1. **javax.servlet preserved**: Plan interface showed `jakarta.servlet` imports, but the project is Spring Boot 2.6.15 (Servlet 4 / javax namespace). Used `javax.servlet` to match actual runtime. If the project were upgraded to Spring Boot 3+, this would need to change to `jakarta.servlet`.

2. **2-arg constructor added for backward compat**: The existing `RateLimitFilterTest` uses `new RateLimitFilter(3, 1)` in `@BeforeEach`. Rather than modify those tests, a 2-arg constructor was added that delegates to the 4-arg one with production-default restaurant limits. Zero test changes needed for existing tests.

3. **server.forward-headers-strategy=native — static verification only**: The PLAN.md and CONTEXT.md both explicitly accept that behavioral runtime verification of this property (that `ForwardedHeaderFilter` correctly reads `X-Forwarded-For`) requires a reverse-proxy harness. This is deferred to Phase 18 E2E infrastructure. The SEC-07 requirement is met by configuration presence.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Used `javax.servlet` instead of `jakarta.servlet`**
- **Found during:** Task 2 implementation
- **Issue:** Plan interface showed `jakarta.servlet` imports. The actual project runs Spring Boot 2.6.15 which uses `javax.servlet` (not `jakarta`). Using `jakarta` would cause compilation failure.
- **Fix:** Used `javax.servlet` imports in RateLimitFilter.java, matching the existing codebase.
- **Files modified:** `src/main/java/com/aflokkat/security/RateLimitFilter.java`
- **Commit:** 5eea0a4

## Known Stubs

None — all rate-limit logic is fully wired. AppConfig reads from `application.properties`, RateLimitFilter reads from AppConfig, filter applies to live request paths.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes introduced. The restaurant rate-limit path (`/api/restaurants/**`) was already public; this plan adds the T-16-10 mitigation (DoS protection) rather than introducing new surface.

## Self-Check: PASSED

| Item | Status |
|------|--------|
| `src/main/java/com/aflokkat/config/AppConfig.java` | FOUND |
| `src/main/java/com/aflokkat/security/RateLimitFilter.java` | FOUND |
| `src/main/resources/application.properties` | FOUND |
| `src/test/java/com/aflokkat/security/RateLimitFilterTest.java` | FOUND |
| `.planning/phases/16-security-hardening/16-04-SUMMARY.md` | FOUND |
| commit 47fcfc0 (Task 1) | FOUND |
| commit 8f7ae32 (RED tests) | FOUND |
| commit 5eea0a4 (Task 2 GREEN) | FOUND |
