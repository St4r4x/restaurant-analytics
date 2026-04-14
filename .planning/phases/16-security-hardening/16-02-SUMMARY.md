---
phase: 16-security-hardening
plan: 02
subsystem: security
tags: [spring-security, cors, security-headers, java, spring-boot]

# Dependency graph
requires:
  - phase: 16-01
    provides: RateLimitFilter and Phase 16 security foundation already present
provides:
  - CorsConfigurationSource bean whitelisting only http://localhost:8080
  - http.cors() wired in SecurityConfig blocking unlisted origins
  - X-Content-Type-Options nosniff and X-Frame-Options DENY on every response
  - @CrossOrigin wildcard removed from AuthController, RestaurantController, AnalyticsController, InspectionController
affects: [16-03, 16-04, any-controller-changes]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Global CORS policy via CorsConfigurationSource bean + http.cors() — both required, either alone causes 403"
    - "Security headers via http.headers().frameOptions().deny() in old-style Spring Security DSL"
    - "Controller-level @CrossOrigin removed — policy lives in SecurityConfig only"

key-files:
  created: []
  modified:
    - src/main/java/com/aflokkat/config/SecurityConfig.java
    - src/test/java/com/aflokkat/config/SecurityConfigTest.java
    - src/main/java/com/aflokkat/controller/AuthController.java
    - src/main/java/com/aflokkat/controller/RestaurantController.java
    - src/main/java/com/aflokkat/controller/AnalyticsController.java
    - src/main/java/com/aflokkat/controller/InspectionController.java

key-decisions:
  - "Spring Security 5.6 old-style DSL used for cors()/headers() — cannot cleanly mix old and lambda styles in same chain; headers().frameOptions().deny().and() chains correctly back to HttpSecurity"
  - "contentTypeOptions() not called explicitly — Spring Security enables nosniff by default when headers() is called; confirmed by passing test"
  - "AnalyticsController wildcard import org.springframework.web.bind.annotation.* expanded to explicit imports after CrossOrigin removal"

patterns-established:
  - "CORS policy: CorsConfigurationSource bean + http.cors() both required (D-03 from CONTEXT.md)"
  - "No @CrossOrigin on any controller — SecurityConfig is the single source of CORS truth"

requirements-completed: [SEC-01, SEC-02]

# Metrics
duration: 25min
completed: 2026-04-14
---

# Phase 16 Plan 02: CORS Policy and Security Headers Summary

**Global CORS whitelist blocking unlisted origins (403) and X-Content-Type-Options/X-Frame-Options headers on every response, with @CrossOrigin wildcard removed from all four controllers**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-14T13:50:00Z
- **Completed:** 2026-04-14T14:15:42Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Added `CorsConfigurationSource` bean to SecurityConfig whitelisting only `http://localhost:8080` — OPTIONS from `http://evil.example.com` now returns 403
- Wired `http.cors()` and `http.headers().frameOptions().deny()` into the Spring Security filter chain, adding `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY` to every response
- Removed `@CrossOrigin(origins = "*", allowedHeaders = "*")` and its import from all 4 controllers — CORS is now governed by the global SecurityConfig policy only
- Extended SecurityConfigTest from 11 to 14 tests, covering the 3 new CORS/headers behaviors; all 14 pass GREEN

## Task Commits

Each task was committed atomically:

1. **Task 1: Add CORS policy and security headers to SecurityConfig** - `4ddf54b` (feat)
2. **Task 2: Remove @CrossOrigin from all four controllers** - `125f986` (feat)

## Files Created/Modified

- `src/main/java/com/aflokkat/config/SecurityConfig.java` — Added `CorsConfigurationSource` bean, `http.cors()`, and `http.headers().frameOptions().deny()` to filterChain
- `src/test/java/com/aflokkat/config/SecurityConfigTest.java` — Added 3 new tests: `cors_unlistedOrigin_returns403`, `responseHeaders_containXContentTypeOptions`, `responseHeaders_containXFrameOptions`; added `/api/restaurants/by-borough` stub endpoint
- `src/main/java/com/aflokkat/controller/AuthController.java` — Removed `@CrossOrigin` annotation and import
- `src/main/java/com/aflokkat/controller/RestaurantController.java` — Removed `@CrossOrigin` annotation and import
- `src/main/java/com/aflokkat/controller/AnalyticsController.java` — Removed `@CrossOrigin` annotation; expanded wildcard import to explicit imports
- `src/main/java/com/aflokkat/controller/InspectionController.java` — Removed `@CrossOrigin` annotation and import

## Decisions Made

- Spring Security 5.6 old-style fluent DSL used throughout (`.cors().and().headers()...`). The plan showed lambda-style `cors(withDefaults())` but this project uses the old chained API already; mixing styles in the same filterChain causes compilation errors. Used `.cors().and()` with the existing chain style instead.
- `contentTypeOptions()` is not called explicitly — Spring Security adds `X-Content-Type-Options: nosniff` by default whenever `headers()` is invoked. The passing test confirms this behavior.
- `AnalyticsController` used a wildcard `import org.springframework.web.bind.annotation.*` that included `CrossOrigin`. After removing the annotation, expanded to explicit imports to avoid unused wildcard.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Spring Security DSL style mismatch**
- **Found during:** Task 1 (Add CORS policy and security headers to SecurityConfig)
- **Issue:** Plan specified lambda-style `http.cors(withDefaults()).headers(headers -> headers.contentTypeOptions(withDefaults()).frameOptions(fo -> fo.deny()))` but the existing SecurityConfig uses old-style chained DSL. Mixing styles caused a compilation error: `cannot find symbol: method and()` on `HeadersConfigurer`.
- **Fix:** Converted to old-style DSL: `.cors().and().headers().frameOptions().deny().and()`. Removed `withDefaults()` and lambda syntax. `contentTypeOptions` is enabled by default in Spring Security so no explicit call needed.
- **Files modified:** `src/main/java/com/aflokkat/config/SecurityConfig.java`
- **Verification:** `mvn test -Dtest=SecurityConfigTest` — 14/14 tests pass, including `responseHeaders_containXContentTypeOptions`
- **Committed in:** `4ddf54b` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 — bug/incompatibility)
**Impact on plan:** DSL style change only — behavior is identical. Both CORS filtering and security headers work as specified. No scope creep.

## Issues Encountered

None beyond the DSL style mismatch documented above.

## Known Stubs

None — all behaviors are fully wired and verified by passing tests.

## Threat Flags

No new security surface introduced. All changes are purely restrictive (removing wildcard CORS, adding denial headers).

## Next Phase Readiness

- Plan 16-03 (input validation on auth DTOs) can proceed: SecurityConfig is stable
- Plan 16-04 (rate limiting extension to `/api/restaurants/**`) can proceed: filterChain ordering unchanged
- CORS dual-wiring pattern is established and confirmed working — future controllers must NOT add `@CrossOrigin`

---
*Phase: 16-security-hardening*
*Completed: 2026-04-14*
