---
phase: 07-homepage-navigation
plan: "01"
subsystem: backend-routing
tags: [routing, security, dao, endpoints, tdd]
dependency_graph:
  requires: []
  provides:
    - GET /api/restaurants/sample endpoint
    - GET /api/users/me enriched with bookmarkCount and reportCount
    - ViewController landing route split (null auth -> landing, customer -> index, controller -> /dashboard)
    - GET /profile view route
    - /profile and /dashboard security guards in SecurityConfig
  affects:
    - RestaurantDAO interface and implementation
    - RestaurantController
    - UserController
    - SecurityConfig
    - ViewController
    - BookmarkRepository
    - ReportRepository
tech_stack:
  added: []
  patterns:
    - TDD (RED/GREEN per task)
    - Spring Data JPA derived query (countByUserId)
    - MongoDB $sample aggregation
    - Spring Security antMatchers
key_files:
  created:
    - src/test/java/com/aflokkat/controller/RestaurantControllerSampleTest.java
    - src/test/java/com/aflokkat/controller/UserControllerMeTest.java
    - src/test/java/com/aflokkat/controller/ViewControllerProfileTest.java
  modified:
    - src/main/java/com/aflokkat/dao/RestaurantDAO.java
    - src/main/java/com/aflokkat/dao/RestaurantDAOImpl.java
    - src/main/java/com/aflokkat/controller/RestaurantController.java
    - src/main/java/com/aflokkat/controller/UserController.java
    - src/main/java/com/aflokkat/repository/BookmarkRepository.java
    - src/main/java/com/aflokkat/repository/ReportRepository.java
    - src/main/java/com/aflokkat/controller/ViewController.java
    - src/main/java/com/aflokkat/config/SecurityConfig.java
    - src/test/java/com/aflokkat/controller/ViewControllerDashboardTest.java
decisions:
  - "Task 1: restaurantDAO injected directly into RestaurantController for /sample — consistent with search/map-points pattern, avoids RestaurantService mock complexity on Java 25"
  - "Task 1: ReportRepository.countByUserId uses Spring Data JPA derived query traversing @ManyToOne user relationship (user.id column)"
  - "Task 2: .antMatchers(\"/dashboard\").hasRole(\"CONTROLLER\") restored — this guard was added in commit 09e1c04 (Phase 5) but was overwritten by a later SecurityConfig edit in Phase 6"
metrics:
  duration: 2177s
  completed: "2026-04-03"
  tasks_completed: 2
  files_modified: 11
---

# Phase 7 Plan 01: Backend Routing and Endpoints Summary

Wire up all backend and routing changes Phase 7 requires: `$sample` DAO method + GET /api/restaurants/sample endpoint, enriched /api/users/me with bookmarkCount/reportCount, ViewController landing split for null auth, /profile route, and security guards for /profile and /dashboard.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | DAO + endpoints: findSampleRestaurants + enriched /api/users/me | 9e178c2 | 8 files |
| 2 | ViewController routing split + /profile security + update broken test | 7eda6b3 | 4 files |

## Test Results

- RestaurantControllerSampleTest: 3/3 pass
- UserControllerMeTest: 3/3 pass
- ViewControllerDashboardTest: 3/3 pass (1 renamed + assertion updated)
- ViewControllerProfileTest: 1/1 pass
- SecurityConfigTest: 6/6 pass (2 previously failing restored by /dashboard guard fix)
- **Total: 34 tests across all controller/config tests — 0 failures**

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Restored /dashboard ROLE_CONTROLLER security guard**
- **Found during:** Task 2, when running SecurityConfigTest after adding /profile guard
- **Issue:** SecurityConfigTest had 2 pre-existing failures: `dashboard_redirectsToLogin_whenUnauthenticated` and `dashboard_returns403_forCustomer`. The `.antMatchers("/dashboard").hasRole("CONTROLLER")` rule was added in commit `09e1c04` (Phase 5 plan 01) but was later overwritten by a SecurityConfig edit in Phase 6 that didn't include it.
- **Fix:** Restored `.antMatchers("/dashboard").hasRole("CONTROLLER")` immediately after `.antMatchers("/api/users/**").authenticated()` in SecurityConfig
- **Files modified:** `src/main/java/com/aflokkat/config/SecurityConfig.java`
- **Commit:** 7eda6b3

## Self-Check: PASSED

All files verified present. All commits verified in git log.
