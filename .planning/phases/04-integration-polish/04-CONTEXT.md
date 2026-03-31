# Phase 4: Integration Polish - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 4 is the final phase. It has two parts executed in this order:

**Part 1 — Cleanup (must run first):**
1. Translate entire project to English (HTML templates, Java comments)
2. Remove legacy routes and dead code (orphaned routes, unused endpoints, dead templates)
3. Refresh project documentation (README, ARCHITECTURE, CHANGELOG)

**Part 2 — Security tests (run after cleanup, clean codebase):**
4. Add 2 targeted test methods that prove the remaining security/persistence invariants

No new features. No new production endpoints.

</domain>

<decisions>
## Implementation Decisions

### Execution Order
- **Cleanup first, tests second.** Rationale: tests should be written against the clean
  codebase. Legacy templates and dead routes removed before tests are written.
- Plans will reflect this order: 3 cleanup plans → 1 test plan.

### Part 1A: Translate to English
- HTML templates: change `lang="fr"` to `lang="en"`, translate all visible UI text
  (titles, labels, buttons, empty states, error messages)
- Java source files: translate French inline comments to English
- Keep consistent tone with English copy already present (copy used in phases 1-3)
- Files in scope: `index.html`, `restaurant.html`, `inspection-map.html`,
  `my-bookmarks.html`, `hygiene-radar.html`, `login.html`, `inspection.html`,
  `ViewController.java` (any French comments)
- Do NOT change any IDs, class names, Thymeleaf attributes, or JS variable names — text
  and comments only

### Part 1B: Remove Legacy Routes and Dead Code
- **Audit ViewController.java** — remove routes not linked from any current page:
  `/inspection` and `/hygiene-radar` are candidates
- **Audit RestaurantController.java** — remove endpoints not called by current templates
  or tests: `/worst-cuisines`, `/cuisine-scores`, `/popular-cuisines` are candidates
- **Remove orphaned templates**: `inspection.html`, `hygiene-radar.html`
- Run `mvn test` after removal to catch regressions before proceeding
- If a test references a removed route, update the test — do not skip removal

### Part 1C: Refresh Documentation
- **README.md**: rewrite — project overview, stack, quick-start, API summary, user
  roles (CUSTOMER / CONTROLLER), seeded test accounts (customer@test.com /
  controller@test.com)
- **ARCHITECTURE.md**: add new entities (`InspectionReportEntity`, `Grade`, `Status`
  enums), new DAO methods (`searchByNameOrAddress`, `findMapPoints`), new controllers
  (`ReportController`), Docker volumes (`uploads_data`)
- **CHANGELOG.md**: document phases 1, 2, and 3 changes
- **CLAUDE.md** (project root): verify still accurate — likely mostly up to date
- **Root ROADMAP.md** (if different from `.planning/ROADMAP.md`): check and update or
  remove if it conflicts

### Part 2: Security Tests
- **SC-1** is already fully covered by `SecurityConfigTest` (3 passing tests). No new
  code needed. Planner must verify tests still pass after cleanup.
- **SC-2 read path**: add `listReports_doesNotReturnOtherControllersReports` to
  `ReportControllerTest` — explicit two-controller scenario (ctrl_a owns reports,
  ctrl_b calls GET /api/reports, gets empty list + verify never called with ctrl_a id)
- **SC-3**: add `uploadsDir_fileWrittenAndReadableFromSamePath` to `ReportControllerTest`
  — use `@TempDir`, write file to `AppConfig.getUploadsDir()` path, re-read via same
  call. Docker round-trip cannot be automated from JUnit; unit test + `docker-compose.yml`
  inspection is the accepted contract (established in Phase 2).
- Both new tests go in `ReportControllerTest` (JUnit 5) — no new test class needed
- SecurityContext must be reset after any test that overrides it (`SecurityContextHolder.clearContext()`)

### Claude's Discretion
- Order of template translation (can batch all in one pass)
- Exact wording of English copy where French had idioms
- Which CHANGELOG format to follow (chronological by phase is fine)

</decisions>

<canonical_refs>
## Canonical References

### Cleanup targets
- `src/main/java/com/aflokkat/controller/ViewController.java` — audit routes
- `src/main/java/com/aflokkat/controller/RestaurantController.java` — audit endpoints
- `src/main/resources/templates/` — all HTML templates
- `README.md`, `ARCHITECTURE.md`, `CHANGELOG.md` at project root

### Test targets
- `src/test/java/com/aflokkat/config/SecurityConfigTest.java` — SC-1 (already passing, verify only)
- `src/test/java/com/aflokkat/controller/ReportControllerTest.java` — add SC-2 read + SC-3

### Research (all Java 25 constraints and patterns)
- `.planning/phases/04-integration-polish/04-RESEARCH.md`

### Prior phase context
- `.planning/phases/01-role-infrastructure/01-CONTEXT.md` — security rules (ROLE_CONTROLLER / ROLE_CUSTOMER)
- `.planning/phases/02-controller-reports/02-CONTEXT.md` — reports, photo upload, AppConfig

</canonical_refs>

<code_context>
## Existing Code Insights

### Java 25 hard constraints (do not forget)
- **Never `@WebMvcTest`** — JVM crash
- **Never `spy()` on JPA entities** — `VerifyError`; use `ArgumentCaptor`
- **Never `mockStatic(AppConfig.class)`** — `VerifyError`; use reflection on `AppConfig.properties`
- **`assumeTrue(false)`** not `Assumptions.abort()` — JUnit 5.8.2 only
- **`SecurityConfigTest` is JUnit 4** — never mix Jupiter annotations into it

### Established test patterns (reuse, don't reinvent)
- Ownership test: `SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(...))`
- Uploads dir patch: `Field f = AppConfig.class.getDeclaredField("properties"); f.setAccessible(true); ((Properties) f.get(null)).setProperty("app.uploads.dir", tempDir.toString())`
- Security filter test: `AnnotationConfigWebApplicationContext` + `springSecurity(filter)` + `.with(authentication(auth))`

### Current test coverage (baseline before Phase 4)
- `SecurityConfigTest`: 3 tests — 401 anon, 403 customer, 200 controller (all passing) → SC-1 DONE
- `ReportControllerTest`: 12 tests including `patchReport_returns403_whenNotOwner` → SC-2 edit DONE
- SC-2 read and SC-3 both missing → 2 new test methods needed

</code_context>

<todo_absorption>
## Absorbed Todos

The following todo files are absorbed into this phase and should be removed from
`.planning/todos/pending/` after planning:

- `2026-03-31-translate-entire-project-to-english.md`
- `2026-03-31-remove-legacy-routes-and-dead-code.md`
- `2026-03-31-refresh-and-update-project-documentation.md`

</todo_absorption>

---

*Phase: 04-integration-polish*
*Context gathered: 2026-03-31*
