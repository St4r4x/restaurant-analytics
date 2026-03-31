---
created: 2026-03-31T12:11:36.449Z
title: Remove legacy routes and dead code
area: general
files:
  - src/main/java/com/aflokkat/controller/ViewController.java
  - src/main/java/com/aflokkat/controller/InspectionController.java
  - src/main/java/com/aflokkat/controller/RestaurantController.java
  - src/main/resources/templates/inspection.html
  - src/main/resources/templates/hygiene-radar.html
---

## Problem

The codebase has accumulated legacy routes and code from before the GSD phases:
- `ViewController` serves routes like `/inspection` and `/hygiene-radar` that are no longer linked from the customer-facing UI
- `InspectionController` may expose endpoints that duplicate or conflict with current architecture
- `RestaurantController` has older aggregation endpoints (`/worst-cuisines`, `/cuisine-scores`, `/popular-cuisines`) that are not used by any current template
- Corresponding HTML templates (`inspection.html`, `hygiene-radar.html`) are orphaned
- Dead code increases build size and makes the codebase harder to navigate for new contributors

## Solution

1. Audit all routes in `ViewController` — remove any not linked from a current page
2. Audit `RestaurantController` endpoints — remove any not called by current templates or tests
3. Remove or repurpose orphaned templates (`inspection.html`, `hygiene-radar.html`)
4. Run full test suite after removal to catch any regressions
5. Verify Swagger UI reflects the clean endpoint list
