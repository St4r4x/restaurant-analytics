---
phase: 08-discovery-enhancement
plan: "02"
subsystem: templates
tags: [disc-02, uncontrolled, thymeleaf, client-side]
dependency_graph:
  requires: [08-01]
  provides: [/uncontrolled page template]
  affects: [uncontrolled.html]
tech_stack:
  added: []
  patterns: [client-side fetch on DOMContentLoaded, gradeBadgeHtml badge helper, module-level sort state, borough param forwarded to CSV href]
key_files:
  created:
    - src/main/resources/templates/uncontrolled.html
  modified: []
---

# Plan 08-02 Summary — uncontrolled.html template

## What was built

Created `src/main/resources/templates/uncontrolled.html` — the public-facing page for DISC-02.

## Key decisions

- Fetches `/api/inspection/uncontrolled?limit=500` on DOMContentLoaded (no auth required)
- Borough filter updates both the table data and the CSV download href
- Client-side sort: `sortCol` + `sortAsc` state, toggled on th click, `↑`/`↓` indicators in column headers
- Days > 365 rendered in bold red (`#b71c1c`) via inline `daysStyle`
- Grade badge colors copied exactly from analytics.html (A=green, B=yellow, C/F/Z/null=red)
- Navbar included via `th:replace="fragments/navbar :: navbar"`

## Verification status

- [x] All required HTML elements present (uncontrolled-tbody, borough-filter-uncontrolled, csv-btn, sort-score-header, sort-days-header, gradeBadgeHtml, api/inspection/uncontrolled, navbar fragment, daysSinceInspection > 365)
- [ ] Human verification pending (checkpoint in plan 08-02 task 2)
