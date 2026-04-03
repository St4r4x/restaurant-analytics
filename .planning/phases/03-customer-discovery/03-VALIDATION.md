---
phase: 3
slug: customer-discovery
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-31
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) via Spring Boot 2.6.15 BOM + Mockito |
| **Config file** | `pom.xml` (surefire: `-XX:+EnableDynamicAgentLoading`) |
| **Quick run command** | `mvn test -Dtest=RestaurantControllerSearchTest` |
| **Full suite command** | `mvn test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=RestaurantControllerSearchTest`
- **After every plan wave:** Run `mvn test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** ~30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 0 | CUST-01, CUST-03 | unit stub | `mvn test -Dtest=RestaurantControllerSearchTest` | ❌ W0 | ⬜ pending |
| 03-01-02 | 01 | 1 | CUST-01 | unit | `mvn test -Dtest=RestaurantControllerSearchTest#testSearch*` | ❌ W0 | ⬜ pending |
| 03-01-03 | 01 | 1 | CUST-03 | unit | `mvn test -Dtest=RestaurantControllerSearchTest#testMapPoints*` | ❌ W0 | ⬜ pending |
| 03-02-01 | 02 | 2 | CUST-02 | manual | browser — see Manual-Only section | n/a | ⬜ pending |
| 03-03-01 | 03 | 3 | CUST-04 | manual | browser — see Manual-Only section | n/a | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/aflokkat/controller/RestaurantControllerSearchTest.java` — test stubs for CUST-01 (search endpoint) and CUST-03 (map-points endpoint)

**Test pattern (mandatory — Java 25 safe):**
```java
@ExtendWith(MockitoExtension.class)
class RestaurantControllerSearchTest {
    @Mock private RestaurantDAO restaurantDAO;
    // standaloneSetup — NEVER use @WebMvcTest (JVM crash on Java 25)
}
```

No additional framework install needed — JUnit 5 and Mockito already on classpath.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Detail page shows grade badge, score, inspection history | CUST-02 | Template rendering — no server-side logic change, pure HTML/JS | Navigate to `/restaurant/{id}`, verify colored grade circle, latest score number, inspection table with all rows |
| Map loads 27K markers without freezing (markerCluster) | CUST-03 | Browser performance — cannot be unit tested | Open `/inspection-map`, wait for load, zoom in/out — no freeze, clusters visible at zoom-out |
| Bookmark toggle state shows correctly on detail page | CUST-04 | Requires JWT + live API | Log in, navigate to restaurant, verify `✓ Saved` toggle state on bookmarked restaurant |
| `/my-bookmarks` page shows saved list | CUST-04 | Template rendering + live API | Log in, bookmark a restaurant, navigate to `/my-bookmarks`, verify it appears |
| Auth redirect on unauthenticated bookmark click | CUST-04 | Browser behavior | Without token, click bookmark button, verify redirect to `/login` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
