---
status: diagnosed
phase: 08-discovery-enhancement
source: [08-01-SUMMARY.md, 08-02-SUMMARY.md, 08-03-SUMMARY.md, 08-04-SUMMARY.md]
started: 2026-04-05T17:20:26Z
updated: 2026-04-05T17:35:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Uncontrolled Page Loads
expected: Navigate to /uncontrolled (no login needed). A table appears listing restaurants with grade C/Z or no inspection in the past 12 months. Each row shows restaurant name, borough, cuisine, last grade badge, last score, and days since inspection. Rows with days > 365 are shown in bold red.
result: issue
reported: "it works but we need a link to uncontrolled for controller"
severity: major

### 2. Uncontrolled Borough Filter
expected: On /uncontrolled, select a borough (e.g. Manhattan) from the dropdown. The table updates to show only restaurants from that borough — no page reload. Selecting "All Boroughs" shows all rows again.
result: pass

### 3. Uncontrolled Client-Side Sort
expected: On /uncontrolled, click the "Score" column header. Rows re-order by score (ascending/descending toggle) with an arrow indicator (↑/↓) appearing in the header. Click "Days Since" column header — rows re-order by days since inspection.
result: pass

### 4. Uncontrolled CSV Download
expected: On /uncontrolled, click "Download CSV". The browser downloads a .csv file. Open it — it contains rows for uncontrolled restaurants matching the current borough filter, with headers for name, borough, cuisine, grade, score, days since inspection.
result: issue
reported: "no button download csv"
severity: major

### 5. Map Filter Bar — Grade Checkboxes
expected: Open /inspection-map. A filter bar appears at the top with 4 grade checkboxes: A (green), B (yellow), C (red), F (red) — all checked by default. Uncheck grade A: all green A-grade markers disappear from the map immediately, no network request. Re-check A: they return.
result: issue
reported: "no color on this grade checkboxes and a Restaurant Map title not necessary"
severity: cosmetic

### 6. Map Filter Bar — Borough Filter
expected: On /inspection-map, select a borough from the borough dropdown (e.g. "Manhattan"). The map updates to show only markers from that borough. The live count badge updates to reflect the filtered count.
result: pass

### 7. Map Filter Bar — Cuisine Dropdown
expected: On /inspection-map, the cuisine dropdown is populated with cuisine types from the API. Selecting a cuisine (e.g. "Chinese") filters the map to show only restaurants with that cuisine. Count badge updates accordingly.
result: pass

### 8. Map Live Count Badge
expected: On /inspection-map with all filters active, a count badge is visible showing "X restaurants". As filters are toggled (grades, borough, cuisine), the count updates in real time to reflect visible markers.
result: pass

### 9. Nearby Restaurants on Detail Page
expected: Open any restaurant detail page (/restaurant/{id}) for a restaurant that has latitude/longitude. A "Nearby restaurants" section appears below the inspection history showing up to 5 nearby restaurants (within 500m), each with name, grade badge, and a clickable link to their detail page. The current restaurant is not listed among the nearby results.
result: issue
reported: "no nearby section (tested /restaurant/40365632 which has confirmed lat/lng)"
severity: major

### 10. Sort Control on Search Results
expected: On the landing page (/) perform a search (e.g. "pizza"). Results appear with a sort control dropdown above them. Select "Best Score" — cards reorder with lowest score first (better hygiene). Select "Worst Score" — highest score first. Select "A→Z" — alphabetical by name. No network request fires on sort change.
result: issue
reported: "it works but the borough column isn't align"
severity: cosmetic

## Summary

total: 10
passed: 5
issues: 6
pending: 0
skipped: 0
blocked: 0

## Gaps

- truth: "A controller can navigate to /uncontrolled from within the app without typing the URL directly"
  status: failed
  reason: "User reported: it works but we need a link to uncontrolled for controller"
  severity: major
  test: 1
  root_cause: "Navbar has no role-conditional logic — all users see the same links; /uncontrolled is unreachable from nav for controllers"
  artifacts:
    - path: "src/main/resources/templates/fragments/navbar.html"
      issue: "No controller-only nav link; JWT role not read to conditionally show links"
  missing:
    - "Add hidden <a href='/uncontrolled'> in navbar center links, shown via JS when JWT payload role === 'ROLE_CONTROLLER'"
  debug_session: ""

- truth: "A Download CSV button is visible on /uncontrolled that downloads the current filtered list"
  status: false_positive
  reason: "Button exists at line 44 of uncontrolled.html (id=csv-btn) — may be hidden at narrow viewport widths due to flex layout"
  severity: cosmetic
  test: 4
  root_cause: "Button is present in template; potential visibility issue at narrow viewport widths in flex container"
  artifacts:
    - path: "src/main/resources/templates/uncontrolled.html"
      issue: "csv-btn may overflow/hide at narrow viewport in flex row with justify-content:space-between"
  missing:
    - "Ensure csv-btn remains visible at all viewport widths (add overflow handling or move button below filter row)"
  debug_session: ""

- truth: "Grade checkboxes on /inspection-map are color-coded (A=green, B=yellow, C/F=red) and the page has no unnecessary 'Restaurant Map' title"
  status: failed
  reason: "User reported: no color on this grade checkboxes and a Restaurant Map title not necessary"
  severity: cosmetic
  test: 5
  root_cause: "gradeColor() JS function exists but was never applied to checkbox label styles; <h1>Restaurant Map</h1> at line 69 renders visibly in toolbar"
  artifacts:
    - path: "src/main/resources/templates/inspection-map.html"
      issue: "Labels all use color:#fff; <h1> at line 69 should be removed"
  missing:
    - "Set label color per grade: A=#22c55e, B=#eab308, C/F=#ef4444"
    - "Remove <h1>Restaurant Map</h1> at line 69"
  debug_session: ""

- truth: "A Nearby Restaurants section appears on /restaurant/{id} for restaurants with lat/lng, showing up to 5 nearby places"
  status: failed
  reason: "User reported: no nearby section (tested /restaurant/40365632 which has confirmed lat/lng)"
  severity: major
  test: 9
  root_cause: "getLatitude/getLongitude in RestaurantService read from address.coord[1]/[0] but that field may not be populated in MongoDB; JS exits early when lat/lng is null leaving #nearby-section hidden"
  artifacts:
    - path: "src/main/java/com/aflokkat/service/RestaurantService.java"
      issue: "getLatitude/getLongitude read address.coord which may be null for many restaurants"
    - path: "src/main/java/com/aflokkat/domain/Address.java"
      issue: "@BsonProperty(\"coord\") — verify this matches actual MongoDB field name"
    - path: "src/main/resources/templates/restaurant.html"
      issue: "loadNearby() exits at line 266 if lat/lng is falsy — correct guard but depends on upstream data"
  missing:
    - "Verify actual MongoDB coordinate field path (run db.restaurants.findOne({restaurant_id:'40365632'}))"
    - "Fix @BsonProperty in Address.java if field name differs from 'coord'"
    - "Fix getLatitude/getLongitude extraction in RestaurantService if coord structure differs"
  debug_session: ""

- truth: "Search result cards on the landing page have a properly aligned borough column"
  status: failed
  reason: "User reported: it works but the borough column isn't align"
  severity: cosmetic
  test: 10
  root_cause: "justify-content:space-between with 3 flex children causes borough span (middle child) to float inconsistently based on sibling text widths"
  artifacts:
    - path: "src/main/resources/templates/landing.html"
      issue: ".result-row uses space-between; borough <span> has no fixed width"
  missing:
    - "Add min-width constraint to borough span, or restructure to 2 flex groups (name+borough stacked left, grade badge right)"
  debug_session: ""

- truth: "Restaurant names in the /uncontrolled table are clickable links to /restaurant/{id}"
  status: failed
  reason: "User reported: restaurant name in uncontrolled view should be link to restaurant detail"
  severity: major
  test: 1
  root_cause: "uncontrolled.html renders name as plain text — no anchor tag wrapping the restaurantId"
  artifacts:
    - path: "src/main/resources/templates/uncontrolled.html"
      issue: "name cell renders as plain text instead of <a href='/restaurant/{restaurantId}'>"
  missing:
    - "Wrap restaurant name in <a href='/restaurant/${r.restaurantId}'> in the table row rendering JS"
  debug_session: ""
