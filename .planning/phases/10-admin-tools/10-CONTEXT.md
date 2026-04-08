# Phase 10: Admin Tools - Context

**Gathered:** 2026-04-08
**Status:** Ready for planning

<domain>
## Phase Boundary

A `/admin` page accessible to CONTROLLER-role users only. It surfaces three capabilities:
1. Data sync control — last sync date/status, "Sync NYC Data" button with live 2s-poll progress, "Rebuild Cache" button
2. At-risk CSV export — "Download At-Risk CSV" button triggering the existing `/api/inspection/at-risk/export.csv` endpoint
3. Aggregate report statistics — counts by status (OPEN/IN_PROGRESS/RESOLVED) and by grade (A/B/C/F) across all controllers, without exposing individual reports

No new sync or cache backend logic — all backend endpoints already exist. The new work is:
- One new Thymeleaf template (`admin.html`)
- One new ViewController route (`GET /admin`)
- One new REST endpoint (`GET /api/reports/stats` — aggregate counts)
- New ReportRepository aggregate query
- SecurityConfig entries for both `/admin` and `/api/reports/stats`
- Navbar entry for CONTROLLER role

</domain>

<decisions>
## Implementation Decisions

### Page Layout
- **D-01:** Three stacked `.card` sections on one page (not tabs, not a single card with dividers)
  - Card 1: Sync status + "Sync NYC Data" button + "Rebuild Cache" button
  - Card 2: "Download At-Risk CSV" button with brief description
  - Card 3: "Report Statistics" panel with badge-style counters
- **D-02:** Same visual style as `dashboard.html` — purple gradient background, white `.card` class, same max-width (860px), same font/button conventions

### Sync Progress Feedback
- **D-03:** During sync (2s polling loop): show a spinner animation + live status text pulled from the `/api/restaurants/sync-status` response. Use existing `.spinner` CSS from `dashboard.html`. Button disabled during sync to prevent concurrent triggers.
- **D-04:** After sync completes: show an inline result line directly below the button — green for success ("Done — 12,400 records upserted"), red for failure (error message). Disappears after 10 seconds or on next button click.
- **D-05:** "Sync in progress" text updates to reflect live status from the poll response (e.g. "Sync in progress…" while running, then replaced by the result line).

### Report Statistics Display
- **D-06:** Two rows of badge-style counters in Card 3:
  - Row 1 (status): `[Open: N]  [In Progress: N]  [Resolved: N]`
  - Row 2 (grade): `[A: N]  [B: N]  [C: N]  [F: N]`
- **D-07:** Badge style mirrors the `.grade-btn` visual from `dashboard.html` — pill/badge shape, color-coded (e.g. Open=orange, In Progress=blue, Resolved=green; grades use existing grade badge colors from `index.html` / `dashboard.html`).

### Report Stats API
- **D-08:** New REST endpoint: `GET /api/reports/stats` (CONTROLLER role only). Returns:
  ```json
  {
    "byStatus": { "OPEN": 4, "IN_PROGRESS": 2, "RESOLVED": 11 },
    "byGrade":  { "A": 8, "B": 5, "C": 3, "F": 1 }
  }
  ```
- **D-09:** Admin page fetches stats via JS on page load (same pattern as `dashboard.html` fetching `/api/reports`). No server-side Thymeleaf model injection.
- **D-10:** Aggregate query uses `ReportRepository` — add two `@Query` methods (or use Spring Data `countBy` derivations): one group-by-status, one group-by-grade. Must NOT filter by userId (aggregate across all controllers). Must NOT return individual report data.

### Navbar
- **D-11:** Add "Admin" link to the navbar fragment (`fragments/navbar.html` or equivalent) visible only when the user has `ROLE_CONTROLLER`. Use same conditional rendering pattern as existing CONTROLLER-only nav items.

### Security
- **D-12:** Add to SecurityConfig:
  - `/admin` → `hasRole("CONTROLLER")`
  - `/api/reports/stats` → `hasRole("CONTROLLER")`
  - (Existing: `/api/reports/**` already has `hasRole("CONTROLLER")` — confirm stats endpoint falls under this or add separately)

### Claude's Discretion
- Exact badge/pill CSS for the stat counters (can reuse `.grade-btn` conventions)
- Color coding for status badges (standard traffic-light: Open=orange, In Progress=blue, Resolved=green)
- Exact spinner text strings (follow existing pattern in dashboard.html)
- Whether to show "Last synced: never" or similar when no sync has run yet

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing backend endpoints (no changes needed)
- `src/main/java/com/aflokkat/controller/RestaurantController.java` — `POST /api/restaurants/refresh` (trigger sync), `GET /api/restaurants/sync-status`, `POST /api/restaurants/rebuild-cache`
- `src/main/java/com/aflokkat/controller/InspectionController.java` — `GET /api/inspection/at-risk/export.csv`
- `src/main/java/com/aflokkat/sync/SyncService.java` — `runSync()`, `isRunning()`, `getLastResult()`, `getRunningStartedAt()`
- `src/main/java/com/aflokkat/sync/SyncResult.java` — fields: `startedAt`, `completedAt`, `rawRecords`, `upsertedRestaurants`, `success`, `errorMessage`

### Data model
- `src/main/java/com/aflokkat/entity/InspectionReportEntity.java` — fields: `grade` (enum A/B/C/F), `status` (enum OPEN/IN_PROGRESS/RESOLVED), `user`
- `src/main/java/com/aflokkat/entity/Status.java` — enum: OPEN, IN_PROGRESS, RESOLVED
- `src/main/java/com/aflokkat/entity/Grade.java` — (confirm location — used in InspectionReportEntity)
- `src/main/java/com/aflokkat/repository/ReportRepository.java` — add aggregate query methods here

### Security & routing
- `src/main/java/com/aflokkat/config/SecurityConfig.java` — add `/admin` and `/api/reports/stats` entries
- `src/main/java/com/aflokkat/controller/ViewController.java` — add `GET /admin` route

### UI patterns to replicate
- `src/main/resources/templates/dashboard.html` — spinner CSS, `.card` class, `.btn` variants, JS polling pattern, tab structure reference
- `src/main/resources/templates/fragments/` — navbar fragment (add CONTROLLER-only "Admin" link)

### Requirements
- `.planning/REQUIREMENTS.md` — ADM-01, ADM-02, ADM-03

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `.spinner` CSS class in `dashboard.html` — use for sync progress animation
- `.card`, `.btn`, `.btn-primary`, `.btn-secondary` classes — reuse for all admin page UI
- `.grade-btn` CSS — reuse as visual base for stat badge counters
- Navbar fragment in `src/main/resources/templates/fragments/` — add Admin link with CONTROLLER condition
- JS polling pattern from `dashboard.html` — `setInterval` / `fetch` pattern for the 2s sync-status poll

### Established Patterns
- Thymeleaf templates served by `ViewController.java` routes with no model attributes (JS fetches data)
- SecurityConfig uses `antMatchers("/path/**").hasRole("ROLE")` — follow same pattern
- REST controllers use `ResponseEntity<Map<String, Object>>` for JSON responses
- `ReportRepository` is a Spring JPA repo — add `@Query` or derived query methods for aggregation

### Integration Points
- `ViewController.java` needs a new `@GetMapping("/admin")` method returning `"admin"` view
- `ReportController.java` or a new `AdminController.java` needs `GET /api/reports/stats`
- `ReportRepository.java` needs two new aggregate query methods (by status, by grade)
- Navbar fragment needs conditional rendering for the Admin link

</code_context>

<specifics>
## Specific Ideas

- Sync polling: exact 2-second interval (specified in ROADMAP success criteria)
- "Download At-Risk CSV" must trigger an actual file download (not open in browser tab) — the existing endpoint returns `Content-Disposition: attachment`, so `window.location.href = '/api/inspection/at-risk/export.csv'` is sufficient from JS
- Report stats must be aggregate across ALL controllers — the query must NOT filter by `userId`

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 10-admin-tools*
*Context gathered: 2026-04-08*
