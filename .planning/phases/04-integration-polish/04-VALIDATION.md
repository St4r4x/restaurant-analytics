---
phase: 4
slug: integration-polish
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-31
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) 5.8.2 + JUnit 4 Vintage (via junit-vintage-engine) |
| **Config file** | `pom.xml` (surefire: `-XX:+EnableDynamicAgentLoading`) |
| **Quick run command** | `mvn test -Dtest=ReportControllerTest,SecurityConfigTest -q` |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=ReportControllerTest,SecurityConfigTest -q`
- **After every plan wave:** Run `mvn test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 1 | Translation | compile + manual grep | `mvn test -q` | ✅ existing | ⬜ pending |
| 04-02-01 | 02 | 1 | Legacy removal | compile + manual check | `mvn test -q` | ✅ existing | ⬜ pending |
| 04-03-01 | 03 | 1 | Docs refresh | manual review | `ls README.md ARCHITECTURE.md CHANGELOG.md` | ✅ existing | ⬜ pending |
| 04-04-01 | 04 | 2 | SC-1 (401/403) | unit (security filter) | `mvn test -Dtest=SecurityConfigTest` | ✅ already passing | ⬜ pending |
| 04-04-02 | 04 | 2 | SC-2 read path | unit (controller) | `mvn test -Dtest=ReportControllerTest#listReports_doesNotReturnOtherControllersReports` | ❌ W0 | ⬜ pending |
| 04-04-03 | 04 | 2 | SC-3 file I/O | unit (file I/O) | `mvn test -Dtest=ReportControllerTest#uploadsDir_fileWrittenAndReadableFromSamePath` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/aflokkat/controller/ReportControllerTest.java` — add `@Disabled` stubs for `listReports_doesNotReturnOtherControllersReports` and `uploadsDir_fileWrittenAndReadableFromSamePath`

*SC-1 is already covered by existing `SecurityConfigTest` (3 passing tests). SC-2 edit path is already covered by `patchReport_returns403_whenNotOwner`. Wave 0 only needs the 2 new stub methods.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Docker volume photo persistence across container restart | SC-3 (Docker layer) | `docker compose down/up` cannot be invoked from JUnit | Run `docker compose down && docker compose up --build`, verify previously-uploaded photo still accessible at `GET /api/reports/{id}/photo` |
| All French UI text removed from templates | Translation | Visual check of rendered pages | Navigate to each page (/, /restaurant/{id}, /inspection-map, /my-bookmarks, /login), verify no French labels remain |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
