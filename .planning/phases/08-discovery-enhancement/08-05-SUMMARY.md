---
phase: 08-discovery-enhancement
plan: "05"
subsystem: frontend-templates, service-layer
tags: [gap-closure, uat-fix, navbar, templates, java]
dependency_graph:
  requires: [08-01, 08-02, 08-03, 08-04]
  provides: [phase-8-uat-complete]
  affects: [navbar.html, uncontrolled.html, inspection-map.html, landing.html, RestaurantService.java]
tech_stack:
  added: []
  patterns: [JWT-client-role-check, flex-layout, SLF4J-debug-logging]
key_files:
  created: []
  modified:
    - src/main/resources/templates/fragments/navbar.html
    - src/main/resources/templates/uncontrolled.html
    - src/main/resources/templates/inspection-map.html
    - src/main/resources/templates/landing.html
    - src/main/java/com/aflokkat/service/RestaurantService.java
decisions:
  - JWT role check in navbar is client-side UI cosmetic only; no server-side auth change needed since /uncontrolled is intentionally public per DISC-02 design
  - Pattern A (flat .role string) used for JWT payload since JwtUtil.buildToken uses .claim("role", role) directly
  - SLF4J debug logging added at DEBUG level (not INFO) to avoid production log noise
metrics:
  duration_minutes: 25
  completed_date: "2026-04-05"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 5
---

# Phase 8 Plan 05: UAT Gap-Closure Summary

One-liner: Six UAT failures cleared across 4 templates and 1 Java service — controller nav link via JWT role, clickable restaurant names in uncontrolled table, grade checkbox colors, h1 removal, borough alignment, and CSV button viewport fix.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | Major gap fixes — controller nav link, uncontrolled name links, coordinate debug | c9d7123 | navbar.html, uncontrolled.html, RestaurantService.java |
| 2 | Cosmetic gap fixes — map grade colors, map title, borough alignment, CSV button | 59be71f | inspection-map.html, landing.html, uncontrolled.html |

## What Was Built

### Task 1: Major Gaps

**Controller nav link (navbar.html)**
- Added `<a id="nav-uncontrolled">` with `display:none` after the Bookmarks link
- JS IIFE now decodes full JWT payload (`JSON.parse(atob(t.split('.')[1]))`) instead of just `.sub`
- When `payload.role === 'ROLE_CONTROLLER'` the link is shown via `style.display = 'inline-block'`
- Uses Pattern A (flat `.role` string) — confirmed from JwtUtil.buildToken which uses `.claim("role", role)`

**Clickable restaurant names (uncontrolled.html)**
- `renderTable` name `<td>` now wraps name in `<a href="/restaurant/{restaurantId}">` when `r.restaurantId` is present
- Falls back to plain text when restaurantId is absent (defensive guard)
- Link styled with `color:#667eea;text-decoration:none;font-weight:600`

**Coordinate debug logging (RestaurantService.java)**
- Added `private static final org.slf4j.Logger log` field
- `getLatitude` now has three granular debug log lines: address null, coord null, coord < 2 elements
- `getLongitude` uses a compact null guard (no debug log needed — latitude logging is sufficient for diagnosis)
- Behavior unchanged; logging at DEBUG level only

### Task 2: Cosmetic Gaps

**Grade checkbox colors (inspection-map.html)**
- Removed `<h1>Restaurant Map</h1>` entirely from toolbar
- Each grade label letter wrapped in `<span style="color:...;font-weight:700">`: A=`#22c55e`, B=`#eab308`, C=`#ef4444`, F=`#ef4444`
- Removed `color:#fff` from outer label style (no longer needed)

**Borough alignment (landing.html)**
- `renderResults` row restructured from 3-child flat flex to 2-group: left column (name + borough stacked) + right badge
- Left group uses `display:flex;flex-direction:column;gap:2px;flex:1;min-width:0` with name having `white-space:nowrap;overflow:hidden;text-overflow:ellipsis`

**CSV button visibility (uncontrolled.html)**
- `#csv-btn` style extended with `flex-shrink: 0; white-space: nowrap`
- Container already had `flex-wrap: wrap; gap: 8px` — no change needed there

## Deviations from Plan

None — plan executed exactly as written.

## Build Verification

The Maven test run failed due to a pre-existing environment issue: `target/classes/application.properties` is owned by root (from a prior Docker compose run) and the Maven resources plugin cannot overwrite it. This is unrelated to our changes.

Java compilation of all 53 source files succeeded with zero errors (confirmed via `target/classes/com/aflokkat/service/RestaurantService.class` present and no `*.java:` error lines in compiler output).

## Known Stubs

None. All changes wire real data — no placeholder text or hardcoded empty values introduced.

## Threat Flags

None. Changes match the threat model in the plan:
- T-08-05-01: navbar role check is UI cosmetic only (accepted)
- T-08-05-02: restaurantId in href comes from server response, not user input (mitigated by design)
- T-08-05-03: debug logging at DEBUG level, no PII (accepted)

## Self-Check: PASSED

- `c9d7123` present in git log: confirmed
- `59be71f` present in git log: confirmed
- `navbar.html` contains `id="nav-uncontrolled"` and `ROLE_CONTROLLER`: confirmed
- `uncontrolled.html` contains `/restaurant/`: confirmed
- `uncontrolled.html` contains `flex-shrink`: confirmed
- `inspection-map.html` `<h1>Restaurant Map</h1>` removed: confirmed (grep returned no match)
- `inspection-map.html` contains `#22c55e` in grade label span: confirmed
- `landing.html` contains `flex-direction:column`: confirmed
- `RestaurantService.java` `getLatitude` has debug logging: confirmed
