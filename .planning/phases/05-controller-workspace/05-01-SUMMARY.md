---
phase: 05-controller-workspace
plan: "01"
subsystem: routing-security
tags: [spring-security, view-controller, tdd, jwt, role-based-access]
dependency_graph:
  requires: []
  provides: [dashboard-route, dashboard-security-guard]
  affects: [ViewController, SecurityConfig]
tech_stack:
  added: []
  patterns: [TDD red-green, UsernamePasswordAuthenticationToken for Java 25 compatibility]
key_files:
  created:
    - src/test/java/com/aflokkat/controller/ViewControllerDashboardTest.java
  modified:
    - src/main/java/com/aflokkat/controller/ViewController.java
    - src/main/java/com/aflokkat/config/SecurityConfig.java
    - src/test/java/com/aflokkat/config/SecurityConfigTest.java
decisions:
  - Use UsernamePasswordAuthenticationToken instead of mock(Authentication.class) due to Java 25 Byte Buddy limitation
  - antMatchers("/dashboard").hasRole("CONTROLLER") inserted immediately before anyRequest().permitAll()
metrics:
  duration_seconds: 1626
  completed_date: "2026-04-03"
  tasks_completed: 3
  files_modified: 4
requirements: [CTRL-05, CTRL-06]
---

# Phase 05 Plan 01: Dashboard Routing and Security Guard Summary

JWT-protected /dashboard route for ROLE_CONTROLLER with Spring Security antMatcher guard and redirect logic in ViewController.

## What Was Done

### Task 1: ViewControllerDashboardTest.java — RED phase
Created `src/test/java/com/aflokkat/controller/ViewControllerDashboardTest.java` with 3 JUnit 5 unit tests verifying ViewController routing behavior. The tests failed at compilation (RED) because `index()` had no `Authentication` parameter.

### Task 2: Modify ViewController.java — GREEN phase
Modified `ViewController.java` to:
- Accept `Authentication auth` parameter in `index()` with a `auth != null` null guard
- Return `"redirect:/dashboard"` for users with `ROLE_CONTROLLER` authority
- Return `"index"` for all other users (anonymous or non-controller)
- Add new `@GetMapping("/dashboard")` method returning `"dashboard"` template

All 3 ViewControllerDashboardTest tests now pass.

### Task 3: SecurityConfigTest + SecurityConfig — TDD RED/GREEN
Extended `SecurityConfigTest.java` with 3 integration tests:
- `dashboard_redirectsToLogin_whenUnauthenticated` — unauthenticated GET /dashboard → 302
- `dashboard_returns403_forCustomer` — ROLE_CUSTOMER → 403
- `dashboard_returns200_forController` — ROLE_CONTROLLER → 200

Added `/dashboard` stub endpoint to `StubReportsController`.

After confirming RED (2 failures), modified `SecurityConfig.java` to insert `.antMatchers("/dashboard").hasRole("CONTROLLER")` immediately before `.anyRequest().permitAll()`. All 6 SecurityConfigTest tests now pass.

## Tests Passing

| Class | Count | Framework |
|-------|-------|-----------|
| ViewControllerDashboardTest | 3 | JUnit 5 + Mockito |
| SecurityConfigTest | 6 (3 existing + 3 new) | JUnit 4 + Spring MockMvc |

Total new test methods: 6

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Mockito cannot mock Authentication interface on Java 25**
- **Found during:** Task 1 (RED → GREEN transition)
- **Issue:** `mock(Authentication.class)` fails with `MockitoException: Could not modify all classes [interface java.security.Principal, interface org.springframework.security.core.Authentication, interface java.io.Serializable]` due to Byte Buddy JVM instrumentation restrictions on Java 25.
- **Fix:** Replaced `mock(Authentication.class)` with `new UsernamePasswordAuthenticationToken(...)` — a concrete implementation that does not require Byte Buddy proxy generation. This is consistent with the pattern used throughout the rest of the project (ReportControllerTest, SecurityConfigTest).
- **Files modified:** `src/test/java/com/aflokkat/controller/ViewControllerDashboardTest.java`
- **Commit:** 5054d8b

## Commits

| Hash | Description |
|------|-------------|
| 5054d8b | feat(05-01): add Authentication redirect and /dashboard route in ViewController |
| 09e1c04 | feat(05-01): guard /dashboard with ROLE_CONTROLLER in SecurityConfig |

## Self-Check: PASSED

All created/modified files exist on disk. Both task commits (5054d8b, 09e1c04) confirmed in git log.
