---
phase: 09-ux-polish
plan: 01
subsystem: frontend
tags: [thymeleaf, css, javascript, skeleton, toast, fragment]
dependency_graph:
  requires: []
  provides: [fragments/ux-utils.html, window.showToast, .skel CSS class]
  affects: [all 10 Thymeleaf templates]
tech_stack:
  added: []
  patterns: [shared Thymeleaf fragment for cross-template utilities, th:block for head injection]
key_files:
  created:
    - src/main/resources/templates/fragments/ux-utils.html
  modified:
    - src/main/resources/templates/landing.html
    - src/main/resources/templates/analytics.html
    - src/main/resources/templates/uncontrolled.html
    - src/main/resources/templates/my-bookmarks.html
    - src/main/resources/templates/dashboard.html
    - src/main/resources/templates/index.html
    - src/main/resources/templates/restaurant.html
    - src/main/resources/templates/inspection-map.html
    - src/main/resources/templates/profile.html
    - src/main/resources/templates/login.html
decisions:
  - "Use th:block (not div) wrapper for fragment — div is invalid inside HTML head"
  - "Add xmlns:th to 5 templates missing namespace: my-bookmarks, dashboard, restaurant, inspection-map, login"
  - "Fragment injected after title tag, before any style blocks per plan spec"
  - "Use textContent (not innerHTML) in showToast for XSS safety — satisfies T-09-01 threat mitigation"
metrics:
  duration: 12m
  completed: 2026-04-07
  tasks_completed: 2
  files_changed: 11
requirements: [UX-06, UX-07]
---

# Phase 09 Plan 01: UX Utils Fragment (Skeleton + Toast) Summary

**One-liner:** Shared Thymeleaf `ux-utils` fragment providing skeleton shimmer CSS and `window.showToast()` toast system, included in all 10 templates via `th:block th:replace`.

## What Was Built

A new `fragments/ux-utils.html` file was created as a Wave 1 foundation for Phase 09. It provides:

1. **Skeleton shimmer CSS** — `.skel` class with `@keyframes skel-shimmer` (1.4s infinite, 200% background-size sweep). Any element with class `skel` gets animated loading shimmer.

2. **Toast notification system** — `window.showToast(msg, type, durationMs)` function exposed globally. Types: `info`, `success`, `error`. Default duration 3s. Toast container created lazily on first call, positioned fixed bottom-right (z-index 9999). Uses `textContent` assignment (XSS-safe per threat model T-09-01).

All 10 application templates were updated to include the fragment via `<th:block th:replace="fragments/ux-utils :: ux-utils"></th:block>` inserted after the `<title>` tag in `<head>`. Five templates that were missing the `xmlns:th` Thymeleaf namespace declaration had it added.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | Create fragments/ux-utils.html | e1024b2 |
| 2 | Add ux-utils include to all 10 templates | 690519a |

## Deviations from Plan

None — plan executed exactly as written.

The plan explicitly called out `xmlns:th` additions for templates missing it; this was applied as specified (not a deviation).

## Known Stubs

None. This plan creates infrastructure (CSS/JS utilities) with no data rendering stubs.

## Threat Flags

None. This plan creates a CSS/JS fragment with no network endpoints, no user input processing, and no auth paths. The threat model item T-09-01 (XSS via toast msg) was explicitly addressed: `textContent` is used instead of `innerHTML`.

## Self-Check: PASSED

- `src/main/resources/templates/fragments/ux-utils.html` — FOUND
- Task 1 commit e1024b2 — FOUND
- Task 2 commit 690519a — FOUND
- 10 templates include ux-utils fragment — VERIFIED
- `mvn package -DskipTests` — BUILD SUCCESS
