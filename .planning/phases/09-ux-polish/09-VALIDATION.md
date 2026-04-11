---
phase: 9
slug: ux-polish
status: compliant
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-07
audited: 2026-04-11
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + Mockito — same as existing test suite |
| **Config file** | none — surefire plugin in pom.xml |
| **Quick run command** | `mvn test` |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test` (no new Java code — full suite is the fast check)
- **After every plan wave:** Run `mvn test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 09-01-01 | 01 | 1 | UX-06, UX-07 | build | `mvn test` (compile check — templates-only) | ✅ | ✅ green |
| 09-01-02 | 01 | 1 | UX-06, UX-07 | manual | See Manual-Only Verifications | N/A | ⬜ manual |
| 09-02-01 | 02 | 2 | UX-06 | build | `mvn test` (compile check — templates-only) | ✅ | ✅ green |
| 09-02-02 | 02 | 2 | UX-05 | manual | See Manual-Only Verifications | N/A | ⬜ manual |
| 09-03-01 | 03 | 2 | UX-05, UX-06 | build | `mvn test` (compile check — templates-only) | ✅ | ✅ green |
| 09-03-02 | 03 | 2 | UX-05, UX-06 | manual | See Manual-Only Verifications | N/A | ⬜ manual |
| 09-03-03 | 03 | 2 | UX-05, UX-06 | manual | See Manual-Only Verifications | N/A | ⬜ manual |
| 09-04-01 | 04 | 3 | UX-07 | build | `mvn test` (compile check — templates-only) | ✅ | ✅ green |
| 09-04-02 | 04 | 3 | UX-07 | manual | See Manual-Only Verifications | N/A | ⬜ manual |
| 09-04-03 | 04 | 3 | UX-05 | manual | See Manual-Only Verifications | N/A | ⬜ manual |
| 09-05-01 | 05 | 4 | UX-08 | build | `mvn test` (compile check — templates-only) | ✅ | ✅ green |
| 09-05-02 | 05 | 4 | UX-08 | manual | See Manual-Only Verifications | N/A | ⬜ manual |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

No new Java classes were introduced in Phase 9. All work is Thymeleaf templates and CSS/JS within those templates. The existing test suite verifies that the application still compiles and all prior Java behavior is unaffected.

- [x] `mvn test` — full suite passes with templates-only changes in place
  - No new service, controller, DAO, entity, or repository class was added
  - No existing Java source file was modified
  - Template changes do not affect the Spring Boot compile or test classpath

> **Templates-only phase:** The Nyquist wave-0 check is satisfied by the build itself. Any Java regression introduced by the template edits (e.g. a broken `th:replace` fragment path causing a Thymeleaf parse error at startup) would surface as a Spring context load failure in existing integration tests.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `.skel` shimmer animation visible during data fetch | UX-06 | CSS animation requires a live browser with rendered DOM | Load `/analytics` with throttled network (Chrome DevTools, Slow 3G) — verify skeleton cards pulse with shimmer before data arrives |
| `window.showToast()` renders toast bottom-right on error | UX-07 | DOM injection and CSS positioning require a live browser | Disconnect network, trigger any page data fetch (e.g. load `/analytics`) — verify a red error toast appears bottom-right and auto-dismisses after ~3 seconds |
| Pagination Prev/Next controls on search results (20/page) | UX-05 | Client-side JS slice rendering requires a live browser with real API data | On landing page, search a common term (e.g. "pizza") — verify results show ≤20 rows, Prev/Next buttons appear, clicking Next advances to next 20 |
| Pagination on at-risk table, uncontrolled list, bookmarks list | UX-05 | Client-side JS pagination state requires live data and browser DOM | Load `/analytics` with >20 at-risk entries: verify Prev/Next controls. Load `/uncontrolled` and `/my-bookmarks` with >20 items: verify same controls |
| Hamburger menu collapses nav at ≤768px viewport | UX-08 | CSS media queries and JS toggle require a live browser | Open any page, resize to 375px width (or use DevTools mobile emulation at iPhone SE) — verify three-bar hamburger icon appears, clicking it reveals nav links as a dropdown, clicking a link closes the menu |
| All pages render correctly at 320px–768px viewport widths | UX-08 | Responsive grid/table layout requires visual browser verification | Open `/`, `/analytics`, `/uncontrolled`, `/my-bookmarks`, `/dashboard`, `/inspection-map` at 375px width (Chrome DevTools iPhone SE) — verify no horizontal scrollbar except inside `.overflow-x:auto` table wrappers, grids stack to single column |
| Toast replaces inline error HTML in all 8 templates | UX-07 | Dynamic fetch error path requires live browser with network blocking | Block API endpoints in DevTools, load each page — verify no inline `<p style="color:#c33">` errors appear; instead a toast notification fires |

---

## Validation Audit 2026-04-11

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated to manual-only | 0 |
| Already manual-only | 7 |
| Automated tests passing | `mvn test` green (build + prior suite — no new Java tests needed) |

Phase 9 is a templates-only phase. All UX behaviors (animation, toast rendering, pagination DOM state, CSS breakpoints) are inherently browser-dependent and cannot be exercised by JUnit/Mockito. The automated signal for this phase is build integrity: `mvn test` passing confirms no Java regression was introduced and all Spring context wiring remains intact. Manual browser verification at a live 375px viewport is required for UX-05, UX-06, UX-07, and UX-08 sign-off.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify (build) or manual-only classification
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (full suite runs after every wave)
- [x] Wave 0 covered — no new Java classes; existing suite is the wave-0 anchor
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** 2026-04-11 (gsd-validate-phase audit)
