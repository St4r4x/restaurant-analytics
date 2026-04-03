---
status: complete
phase: 07-homepage-navigation
source: [07-01-SUMMARY.md, 07-02-SUMMARY.md, 07-03-SUMMARY.md]
started: 2026-04-03T20:00:00Z
updated: 2026-04-03T20:00:00Z
---

## Current Test
<!-- OVERWRITE each test - shows where we are -->

[testing complete]

## Tests

### 1. Anonymous visitor sees landing page
expected: Open http://localhost:8080/ in an incognito window (or clear localStorage first). Hero section with "NYC Restaurant Inspector" heading + live KPI stats strip visible. Search card and 3 sample restaurant cards present. No redirect to /login.
result: pass

### 2. Navbar: Sign In shown for anonymous users
expected: On the landing page (not logged in), the sticky navbar shows "NYC Inspections" logo on the left, Search/Map/Analytics pills in the center, and a "Sign In" button on the right. No username.
result: pass

### 3. Landing page search works
expected: Type "pizza" (or any restaurant name) in the Search Restaurants input. After ~300ms, a list of matching restaurants appears below the input with name, borough, and grade badge. Clicking a result navigates to /restaurant/{id}.
result: pass

### 4. Sample restaurants load
expected: The "Discover Restaurants" section on the landing page shows 3 restaurant cards. Each card has: restaurant name, borough, grade badge, and a "View Details" link. Cards are visible on page load (no manual action needed).
result: pass

### 5. Customer login → personalized dashboard
expected: Login as customer_test / password. After login, you land on / and see the personalized dashboard with: "Your Bookmarks" section (or "You haven't bookmarked any restaurants yet"), "NYC Hygiene Overview" strip with 4 KPI tiles (Total Restaurants, % Grade A, Avg Score, At-Risk Count) showing numbers.
result: issue
reported: "instead of the number of bookmark a list of bookmark will be better"
severity: minor

### 6. Navbar: username + Sign Out for logged-in users
expected: While logged in, the navbar right side shows your username ("customer_test") as a clickable link, and a "Sign Out" button next to it. No "Sign In" button.
result: pass

### 7. Profile page loads without loop
expected: While logged in, click the username link in the navbar (or go to http://localhost:8080/profile). The profile card appears with: avatar icon (👤), username, email, green "CUSTOMER" role badge, and a bookmark count. No redirect to /login or infinite reload.
result: pass

### 8. Sign Out works
expected: Click "Sign Out" in the navbar. You are redirected to /login. After sign-out, revisiting / shows the landing page (not the personalized dashboard) and the navbar shows "Sign In" again.
result: pass

### 9. Controller login → /dashboard redirect
expected: Login as controller_test / password. After login, you are redirected to /dashboard (not the landing page or /). The dashboard page loads normally.
result: pass

### 10. Navbar present on all existing pages
expected: Visit /analytics, /inspection-map, and /restaurant/{any-id}. The persistent sticky navbar appears at the top of each page with the same logo, nav pills, and auth area. Page content starts below the navbar (not hidden behind it).
result: pass

## Summary

total: 10
passed: 9
issues: 1
pending: 0
skipped: 0

## Gaps

- truth: "Profile page shows bookmark count as a number"
  status: failed
  reason: "User reported: instead of the number of bookmark a list of bookmark will be better"
  severity: minor
  test: 5
  root_cause: ""
  artifacts: []
  missing: []
  debug_session: ""
