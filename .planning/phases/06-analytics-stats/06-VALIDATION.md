---
phase: 6
slug: analytics-stats
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-03
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (jupiter) via Spring Boot 2.6.15 BOM + Mockito (ExtendWith) |
| **Config file** | none — inherited from Spring Boot BOM |
| **Quick run command** | `mvn test -Dtest=AnalyticsControllerTest -pl . -q` |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=AnalyticsControllerTest -q`
- **After every plan wave:** Run `mvn test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 6-01-01 | 01 | 0 | STAT-01..04 | unit | `mvn test -Dtest=AnalyticsControllerTest -q` | ❌ Wave 0 | ⬜ pending |
| 6-01-02 | 01 | 0 | STAT-04 | unit | `mvn test -Dtest=ViewControllerAnalyticsTest -q` | ❌ Wave 0 | ⬜ pending |
| 6-02-01 | 02 | 1 | STAT-01 | unit | `mvn test -Dtest=AnalyticsControllerTest#testKpis_returns200 -q` | ❌ Wave 0 | ⬜ pending |
| 6-02-02 | 02 | 1 | STAT-02 | unit | `mvn test -Dtest=AnalyticsControllerTest#testBoroughGrades_returns5Boroughs -q` | ❌ Wave 0 | ⬜ pending |
| 6-02-03 | 02 | 1 | STAT-03 | unit | `mvn test -Dtest=AnalyticsControllerTest#testCuisineRankings_returnsTwoLists -q` | ❌ Wave 0 | ⬜ pending |
| 6-02-04 | 02 | 1 | STAT-04 | unit | `mvn test -Dtest=AnalyticsControllerTest#testAtRisk_returnsEntries -q` | ❌ Wave 0 | ⬜ pending |
| 6-03-01 | 03 | 2 | STAT-04 | unit | `mvn test -Dtest=ViewControllerAnalyticsTest#testAnalyticsPage_returns200 -q` | ❌ Wave 0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/aflokkat/controller/AnalyticsControllerTest.java` — stub test class with 4 methods (STAT-01 through STAT-04); follow `@ExtendWith(MockitoExtension.class)` + `MockMvcBuilders.standaloneSetup()` pattern — never `@WebMvcTest`
- [ ] `src/test/java/com/aflokkat/controller/ViewControllerAnalyticsTest.java` — stub for `testAnalyticsPage_returns200` (STAT-04 public page)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Chart.js stacked horizontal bars render with correct A/B/C colors | STAT-02 | DOM/Canvas rendering not testable in JUnit | Load `/analytics` in browser, verify each borough has green/yellow/red segments |
| KPI tiles show `—` before data loads then update | STAT-01 | Timing-dependent UI | Open `/analytics` with network throttling, observe tile values |
| At-risk table links navigate to `/restaurant/{id}` | STAT-04 | Navigation behavior | Click a "View" link, verify redirect to correct detail page |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
