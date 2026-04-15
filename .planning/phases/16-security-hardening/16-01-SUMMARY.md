---
phase: 16-security-hardening
plan: "01"
subsystem: test-scaffold
tags: [security, tdd, wave-0, cors, rate-limit, validation, exception-handler]
dependency_graph:
  requires: []
  provides: [16-01-red-tests]
  affects: [16-02-PLAN, 16-03-PLAN, 16-04-PLAN]
tech_stack:
  added: []
  patterns: [tdd-wave-0, standaloneSetup-mockMvc, junit4-junit5-hybrid]
key_files:
  created:
    - src/test/java/com/aflokkat/controller/AuthControllerValidationTest.java
    - src/test/java/com/aflokkat/controller/GlobalExceptionHandlerTest.java
  modified:
    - src/test/java/com/aflokkat/config/SecurityConfigTest.java
    - src/test/java/com/aflokkat/security/RateLimitFilterTest.java
decisions:
  - "Header tests (X-Content-Type-Options, X-Frame-Options) are green at Wave 0 because Spring Security 5.x sets these headers by default — this is correct behavior, not a false positive; Plan 02 will add explicit headers() DSL which keeps them green"
  - "RateLimitFilterTest 4-arg constructor tests cause compile failure — accepted RED state per Wave 0 spec"
  - "GlobalExceptionHandlerTest causes compile failure (class missing) — accepted RED state per Wave 0 spec"
metrics:
  duration_minutes: 5
  completed_date: "2026-04-14"
  tasks_completed: 3
  tasks_total: 3
  files_created: 2
  files_modified: 2
---

# Phase 16 Plan 01: Wave 0 Security Test Scaffold Summary

Wave 0 TDD scaffold for Phase 16 security hardening — four test files written with failing assertions that prove missing security controls before any production code is changed.

## What Was Built

Four test files establish the Wave 0 RED baseline for Phase 16:

1. **SecurityConfigTest** (extended) — 3 new tests for CORS restriction and security headers. The CORS test (`cors_unlistedOrigin_returns403`) fails at runtime because `SecurityConfig` has no `CorsConfigurationSource` bean. The two header tests pass immediately because Spring Security 5.x adds `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY` by default — Plan 02 will add an explicit `headers()` DSL call that keeps them green.

2. **RateLimitFilterTest** (extended) — 2 new tests for restaurant-path rate limiting using a 4-arg constructor (`RateLimitFilter(int authMax, int authWindow, int restaurantMax, int restaurantWindow)`) that does not yet exist. Tests fail with a compilation error — correct RED state.

3. **AuthControllerValidationTest** (created) — 5 tests asserting that empty/invalid DTO fields return HTTP 400. Tests fail to compile because `GlobalExceptionHandler` is missing — correct RED state.

4. **GlobalExceptionHandlerTest** (created) — 3 tests asserting the error response shape `{status, message, timestamp}` on validation failure. Fails to compile because `GlobalExceptionHandler` is missing — correct RED state.

## RED State Verification

| Test File | RED Mechanism | Status |
|-----------|---------------|--------|
| SecurityConfigTest | cors_unlistedOrigin_returns403 — runtime ERROR (no CORS adapter) | RED |
| RateLimitFilterTest | Compile error — 4-arg constructor missing | RED |
| AuthControllerValidationTest | Compile error — GlobalExceptionHandler class missing | RED |
| GlobalExceptionHandlerTest | Compile error — GlobalExceptionHandler class missing | RED |

Pre-existing SecurityConfigTest tests (11): all GREEN before new tests were added (confirmed by isolated run before adding Tasks 2/3).

## Commits

| Task | Commit | Files |
|------|--------|-------|
| Task 1: SecurityConfigTest CORS/header tests | cb7a8e0 | SecurityConfigTest.java |
| Task 2: RateLimitFilterTest restaurant-path tests | 5e44e36 | RateLimitFilterTest.java |
| Task 3: AuthControllerValidationTest + GlobalExceptionHandlerTest | f368f6f | AuthControllerValidationTest.java, GlobalExceptionHandlerTest.java |

## Deviations from Plan

### Observation: X-Content-Type-Options and X-Frame-Options Tests Are Green at Wave 0

**Found during:** Task 1 verification
**Issue:** The plan stated these two header tests would fail today because "SecurityConfig has no headers() DSL call." However, Spring Security 5.x (Spring Boot 2.6.15) adds these response headers by default even without explicit `http.headers()` configuration. The tests pass immediately.
**Assessment:** Not a false positive — the headers are genuinely present. The tests correctly verify behavior that already exists. Plan 02's explicit `headers()` DSL call will keep them green (it won't remove existing default headers). The Wave 0 contract requires tests that are RED; these two pass, but they still provide valid regression coverage.
**Action taken:** Documented as deviation. No code change. Plan 02 should still add the explicit headers() DSL for clarity, and the tests will remain green.
**Files modified:** None (documentation only)

## Known Stubs

None — this plan creates only test scaffolding with no production code.

## Threat Flags

None — this plan adds test files only, no new network endpoints, auth paths, or schema changes.

## Self-Check: PASSED

- FOUND: src/test/java/com/aflokkat/config/SecurityConfigTest.java
- FOUND: src/test/java/com/aflokkat/security/RateLimitFilterTest.java
- FOUND: src/test/java/com/aflokkat/controller/AuthControllerValidationTest.java
- FOUND: src/test/java/com/aflokkat/controller/GlobalExceptionHandlerTest.java
- FOUND commit cb7a8e0: test(16-01): extend SecurityConfigTest with CORS and security header assertions
- FOUND commit 5e44e36: test(16-01): extend RateLimitFilterTest with restaurant-path bucket tests
- FOUND commit f368f6f: test(16-01): create AuthControllerValidationTest and GlobalExceptionHandlerTest (RED)
