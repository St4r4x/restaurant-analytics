---
phase: 2
slug: controller-reports
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-30
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 + JUnit 5 (Vintage engine) + Mockito 5.17.0 |
| **Config file** | `pom.xml` (surefire: `-XX:+EnableDynamicAgentLoading`) |
| **Quick run command** | `mvn test -Dtest=ReportControllerTest` |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=ReportControllerTest`
- **After every plan wave:** Run `mvn test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 0 | CTRL-01–04 | unit stub | `mvn test -Dtest=ReportControllerTest` | ❌ W0 | ⬜ pending |
| 02-01-02 | 01 | 1 | CTRL-01 | unit | `mvn test -Dtest=ReportControllerTest#createReport*` | ❌ W0 | ⬜ pending |
| 02-01-03 | 01 | 1 | CTRL-02 | unit | `mvn test -Dtest=ReportControllerTest#listReports*` | ❌ W0 | ⬜ pending |
| 02-02-01 | 02 | 2 | CTRL-03 | unit | `mvn test -Dtest=ReportControllerTest#patchReport*` | ❌ W0 | ⬜ pending |
| 02-03-01 | 03 | 3 | CTRL-04 | unit | `mvn test -Dtest=ReportControllerTest#photoUpload*` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/main/java/com/aflokkat/entity/InspectionReportEntity.java` — entity must exist before tests compile
- [ ] `src/main/java/com/aflokkat/repository/ReportRepository.java` — repository must exist before tests compile
- [ ] `src/test/java/com/aflokkat/controller/ReportControllerTest.java` — test stubs for CTRL-01 through CTRL-04

**Test pattern (mandatory — Java 25 safe):**
```java
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {
    @Mock private ReportRepository reportRepository;
    @Mock private RestaurantDAO restaurantDAO;
    @Mock private UserRepository userRepository;
    // standaloneSetup for HTTP-layer assertions
}
```
**NEVER use `@WebMvcTest`** — crashes JVM on Java 25 (Byte Buddy incompatibility, established in Phase 1).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Photo survives `docker compose down && docker compose up` | CTRL-04 | Requires live Docker + volume mount | 1. Upload photo via POST. 2. `docker compose down`. 3. `docker compose up -d`. 4. GET photo endpoint returns 200 with file bytes. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
