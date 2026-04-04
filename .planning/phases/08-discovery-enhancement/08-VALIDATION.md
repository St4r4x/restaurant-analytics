---
phase: 8
slug: discovery-enhancement
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-03
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5.8.2 + Mockito 5.x (Spring Boot 2.6.15 BOM) |
| **Config file** | none — convention-based Maven Surefire |
| **Quick run command** | `mvn test -Dtest=ViewControllerUncontrolledTest,InspectionControllerUncontrolledTest -DfailIfNoTests=false` |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=ViewControllerUncontrolledTest,InspectionControllerUncontrolledTest -DfailIfNoTests=false`
- **After every plan wave:** Run `mvn test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 8-01-01 | 01 | 0 | DISC-02 | unit | `mvn test -Dtest=ViewControllerUncontrolledTest#testUncontrolledPage_returnsView` | ❌ W0 | ⬜ pending |
| 8-01-02 | 01 | 0 | DISC-02 | unit | `mvn test -Dtest=InspectionControllerUncontrolledTest#testUncontrolled_returns200` | ❌ W0 | ⬜ pending |
| 8-01-03 | 01 | 0 | DISC-02 | unit | `mvn test -Dtest=InspectionControllerUncontrolledTest#testExportCsv_returnsTextCsv` | ❌ W0 | ⬜ pending |
| 8-02-01 | 02 | 1 | DISC-02 | unit | `mvn test -Dtest=ViewControllerUncontrolledTest,InspectionControllerUncontrolledTest` | ✅ W0 | ⬜ pending |
| 8-03-01 | 03 | 2 | DISC-01 | manual | See Manual-Only Verifications | N/A | ⬜ pending |
| 8-04-01 | 04 | 2 | DISC-03 | manual | See Manual-Only Verifications | N/A | ⬜ pending |
| 8-05-01 | 05 | 2 | DISC-04 | manual | See Manual-Only Verifications | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/aflokkat/controller/ViewControllerUncontrolledTest.java` — stub for `GET /uncontrolled` view route (DISC-02)
- [ ] `src/test/java/com/aflokkat/controller/InspectionControllerUncontrolledTest.java` — stubs for `GET /api/inspection/uncontrolled` (200 + data array) and `GET /api/inspection/uncontrolled/export.csv` (text/csv)

*(DISC-01, DISC-03, DISC-04 are pure client-side JS — no automated test infrastructure needed)*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Grade checkboxes + borough + cuisine filters update map markers in <200ms | DISC-01 | Client-side JS only, Leaflet DOM manipulation | Load `/inspection-map`, uncheck grade A, observe markers disappear without network request; toggle borough and cuisine |
| Nearby restaurants section appears on restaurant detail page | DISC-03 | Client-side fetch + rendering | Navigate to `/restaurant/{id}` for a restaurant with known GPS coords; confirm up to 5 nearby cards appear below inspection history |
| Sort control reorders search results client-side | DISC-04 | Client-side DOM reorder | On landing page, search "pizza", then select "Best Score" — confirm results reorder without new network request |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
