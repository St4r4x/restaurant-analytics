---
phase: 11-logging-infrastructure
plan: "02"
subsystem: security/logging
tags: [logging, request-tracing, mdc, filter, tdd]
dependency_graph:
  requires:
    - "11-01: logback-spring.xml with %X{requestId} pattern defined"
  provides:
    - "RequestIdFilter: UUID per request in MDC and X-Request-ID header"
    - "QA-03: every HTTP request carries a traceable server-generated UUID"
  affects:
    - "All HTTP responses now include X-Request-ID header"
    - "All log lines during a request include requestId field (via MDC + logback-spring.xml)"
tech_stack:
  added:
    - "org.slf4j.MDC (SLF4J API — already present transitively)"
    - "java.util.UUID.randomUUID() for server-side ID generation"
  patterns:
    - "@Component @Order(0) servlet filter registration (matches RateLimitFilter @Order(1) pattern)"
    - "MDC.put before chain.doFilter / MDC.remove in finally block"
    - "TDD: RED test (compilation failure) → GREEN implementation → verify full suite"
key_files:
  created:
    - src/main/java/com/aflokkat/security/RequestIdFilter.java
    - src/test/java/com/aflokkat/security/RequestIdFilterTest.java
  modified: []
decisions:
  - "D-01 enforced: client X-Request-ID header never read — prevents log injection, guarantees UUID format"
  - "D-03 enforced: MDC.remove in finally block — prevents cross-request contamination on pooled threads"
  - "@Component @Order(0) only — no SecurityConfig registration (prevents double-execution; same pattern as RateLimitFilter)"
metrics:
  duration: "31m"
  completed_date: "2026-04-11"
  tasks_completed: 1
  tasks_total: 1
  files_created: 2
  files_modified: 0
---

# Phase 11 Plan 02: RequestIdFilter Summary

**One-liner:** OncePerRequestFilter at @Order(0) generating UUID per request, stored in SLF4J MDC as "requestId" and returned as X-Request-ID response header, with 5 TDD unit tests.

## What Was Built

`RequestIdFilter` is a Spring servlet filter registered as `@Component @Order(0)`. For every HTTP request it:

1. Generates a server-side `UUID.randomUUID().toString()` — never reads client `X-Request-ID` (D-01)
2. Calls `MDC.put("requestId", uuid)` before `chain.doFilter()` — all downstream log lines include this value via the `%X{requestId}` pattern from Plan 01
3. Calls `response.setHeader("X-Request-ID", uuid)` before `chain.doFilter()` — callers can correlate their request to the server logs
4. Calls `MDC.remove("requestId")` in a `finally` block — prevents stale ID leaking to the next request on the same thread (D-03)

Running at `@Order(0)`, the filter precedes `RateLimitFilter` (`@Order(1)`) and the Spring Security `FilterChainProxy`. All requests receive a UUID: authenticated, unauthenticated, Swagger, NYC sync endpoints, and public analytics routes.

## TDD Execution

**RED phase:** `RequestIdFilterTest.java` created first. Running `mvn test -Dtest=RequestIdFilterTest` produced a compilation error (`cannot find symbol: class RequestIdFilter`) — confirming RED.

**GREEN phase:** `RequestIdFilter.java` created. Running `mvn test -Dtest=RequestIdFilterTest` produced: `Tests run: 5, Failures: 0, Errors: 0` — GREEN confirmed.

## Test Coverage (5 tests)

| Test | Behavior Verified |
|------|-------------------|
| `filter_setsXRequestIdHeader` | X-Request-ID header is present in response |
| `filter_headerIsValidUuid` | Header matches lowercase UUID v4 regex |
| `filter_ignoresClientSuppliedRequestId` | Server-generated value != client-supplied value (D-01) |
| `filter_clearsMdcAfterRequest` | `MDC.get("requestId")` is null after filter returns (D-03) |
| `filter_generatesUniqueIdsPerRequest` | Two sequential requests get distinct UUIDs |

## Pre-existing Test Failures (Not Caused By This Plan)

The full `mvn test` suite has 33 pre-existing test errors in:
- `RestaurantCacheServiceTest` (8 errors — Mockito MissingMethodInvocation, InvalidUseOfMatchers)
- `NycOpenDataClientTest` (2 errors — Mockito IllegalArgument, InvalidUseOfMatchers)
- `SyncServiceTest` (5 errors — Mockito issues)
- `RestaurantDAOIntegrationTest` (integration test requiring live MongoDB, not run in unit scope)

These failures exist on the base commit (verified by `git stash` + run) — they are out of scope per deviation rules.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced.

The threat mitigations from the plan's threat register are fully implemented:

| Threat ID | Status |
|-----------|--------|
| T-11-04 (client X-Request-ID tampering) | Mitigated — `request.getHeader("X-Request-ID")` never called |
| T-11-05 (MDC stale requestId) | Mitigated — `MDC.remove("requestId")` in finally block |
| T-11-06 (UUID in JSON logs) | Accepted — UUID has no semantic meaning |
| T-11-07 (SecureRandom throughput) | Accepted — portfolio scale, no burst requirements |

## Self-Check: PASSED

| Item | Status |
|------|--------|
| `src/main/java/com/aflokkat/security/RequestIdFilter.java` | FOUND |
| `src/test/java/com/aflokkat/security/RequestIdFilterTest.java` | FOUND |
| `.planning/phases/11-logging-infrastructure/11-02-SUMMARY.md` | FOUND |
| commit `30dbe02` | FOUND |
