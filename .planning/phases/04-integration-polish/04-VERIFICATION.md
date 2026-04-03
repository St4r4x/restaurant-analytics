---
phase: 04-integration-polish
verified: 2026-04-01T10:20:00Z
status: gaps_found
score: 5/6 must-haves verified
re_verification: false
gaps:
  - truth: "No French visible UI text remains in any template (buttons, headings, placeholders, error messages)"
    status: partial
    reason: "restaurant.html still contains two French strings not addressed by plan 04-01 acceptance criteria: the page title 'Fiche Restaurant' and the grade label 'Note ${latestGrade}'"
    artifacts:
      - path: "src/main/resources/templates/restaurant.html"
        issue: "Line 6: <title>Fiche Restaurant</title> — French page title. Line 266: 'Note ${latestGrade}' — French word for grade/score."
    missing:
      - "Translate <title>Fiche Restaurant</title> to <title>Restaurant Details</title>"
      - "Translate 'Note ${latestGrade}' to 'Grade ${latestGrade}'"
---

# Phase 4: Integration Polish Verification Report

**Phase Goal:** Security boundaries, ownership rules, and photo persistence are verified
end-to-end with targeted tests; the application behaves correctly at all role/permission
boundaries. Phase also includes cleanup work (translate to English, remove legacy routes,
refresh documentation).
**Verified:** 2026-04-01T10:20:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                           | Status      | Evidence                                                                                         |
|----|-------------------------------------------------------------------------------------------------|-------------|--------------------------------------------------------------------------------------------------|
| 1  | SecurityConfigTest asserts 401 for anonymous and 403 for CUSTOMER against /api/reports/**       | VERIFIED    | 3/3 tests pass: reports_returns401_whenUnauthenticated, reports_returns403_forCustomerJwt, reports_allowsAccess_forControllerJwt |
| 2  | ReportControllerTest has listReports_doesNotReturnOtherControllersReports (SC-2 read path)      | VERIFIED    | Test exists and passes; verifies findByUserId(99L) called, findByUserId(42L) never called        |
| 3  | ReportControllerTest has patchReport_returns403_whenNotOwner (SC-2 edit path)                   | VERIFIED    | Test exists and passes; verifies 403 + save() never called when owner.id != caller.id           |
| 4  | ReportControllerTest has uploadsDir_fileWrittenAndReadableFromSamePath (SC-3)                   | VERIFIED    | Test exists and passes; uses reflection to patch AppConfig, writes file, re-reads via same path |
| 5  | HTML templates have lang="en", no French visible UI text                                        | PARTIAL     | All 5 templates have lang="en". restaurant.html line 6: "Fiche Restaurant" (title); line 266: "Note ${latestGrade}" |
| 6  | Legacy routes/endpoints removed; hygiene-radar.html and inspection.html deleted                 | VERIFIED    | ViewController: 5 routes, no /hygiene-radar or /inspection. RestaurantController: no /worst-cuisines, /cuisine-scores, /popular-cuisines. Templates: only index.html, login.html, restaurant.html, inspection-map.html, my-bookmarks.html |
| 7  | README.md, ARCHITECTURE.md, CHANGELOG.md rewritten in English with dual-role documentation     | VERIFIED    | README.md: 140 lines, English, ROLE_CUSTOMER + ROLE_CONTROLLER, seeded accounts, full endpoint table. ARCHITECTURE.md: 461 lines, InspectionReportEntity, uploads_data volume, searchByNameOrAddress. CHANGELOG.md: 35 lines, phases 1–3 history |

**Score:** 6/7 truths verified (5/6 must-haves — SC-4 translation is partial)

---

### Required Artifacts

| Artifact                                                                              | Expected                                    | Status     | Details                                                                                          |
|---------------------------------------------------------------------------------------|---------------------------------------------|------------|--------------------------------------------------------------------------------------------------|
| `src/test/java/com/aflokkat/config/SecurityConfigTest.java`                           | SC-1: 401 anon + 403 customer tests         | VERIFIED   | 101 lines; 3 test methods; passes 3/3                                                            |
| `src/test/java/com/aflokkat/controller/ReportControllerTest.java`                     | SC-2 read/edit + SC-3 file-I/O tests        | VERIFIED   | 440 lines; 14 test methods including listReports_doesNotReturnOtherControllersReports (line 219) and uploadsDir_fileWrittenAndReadableFromSamePath (line 421) |
| `src/main/java/com/aflokkat/controller/ViewController.java`                           | 5 routes, no legacy routes                  | VERIFIED   | 36 lines; 5 @GetMapping annotations (confirmed with grep -c)                                     |
| `src/main/java/com/aflokkat/controller/RestaurantController.java`                     | No dead endpoints                           | VERIFIED   | /worst-cuisines, /cuisine-scores, /popular-cuisines absent; confirmed with grep                  |
| `src/main/resources/templates/restaurant.html`                                        | English UI text                             | STUB       | lang="en" set but line 6 "Fiche Restaurant" and line 266 "Note ${latestGrade}" remain French    |
| `README.md`                                                                            | English, dual-role documentation            | VERIFIED   | Contains ROLE_CUSTOMER, ROLE_CONTROLLER, customer_test, controller_test, PATCH /api/reports/{id} |
| `ARCHITECTURE.md`                                                                      | Updated with phases 1–3 additions           | VERIFIED   | Contains InspectionReportEntity, Grade enum, uploads_data:/app/uploads, searchByNameOrAddress    |
| `CHANGELOG.md`                                                                         | Phase history written                       | VERIFIED   | Contains [Phase 1], [Phase 2], [Phase 3] with correct content                                    |

---

### Key Link Verification

| From                                             | To                              | Via                                     | Status   | Details                                                                     |
|--------------------------------------------------|---------------------------------|-----------------------------------------|----------|-----------------------------------------------------------------------------|
| SecurityConfigTest                               | SecurityConfig filter chain     | AnnotationConfigWebApplicationContext   | WIRED    | Context registers SecurityConfig + SecurityAutoConfiguration; filter chain applied via springSecurity() |
| listReports_doesNotReturnOtherControllersReports | reportRepository.findByUserId   | verify(reportRepository).findByUserId(99L) | WIRED  | Lines 236–237 in ReportControllerTest verify 99L called, 42L never called   |
| uploadsDir_fileWrittenAndReadableFromSamePath    | AppConfig.getUploadsDir()       | reflection patch setUploadsDir()        | WIRED    | setUploadsDir(tempDir.toString()) called at line 424; AppConfig.getUploadsDir() called twice    |
| ReportControllerTest.tearDown()                  | SecurityContextHolder           | clearContext() in @AfterEach            | WIRED    | @AfterEach tearDown() at line 73–75; prevents auth context leak across tests |

---

### Requirements Coverage

No explicit requirement IDs were declared for this phase. Phase 4 validates correctness
of AUTH-01 through CUST-04 from earlier phases. All security contract requirements are
covered by the passing test suite:

- AUTH-01 (role-based access): covered by SecurityConfigTest (401/403/200 boundary tests)
- CTRL-02 (report ownership read isolation): covered by listReports_doesNotReturnOtherControllersReports
- CTRL-03 (report ownership edit guard): covered by patchReport_returns403_whenNotOwner
- CTRL-04 (photo persistence): covered by uploadsDir_fileWrittenAndReadableFromSamePath

---

### Anti-Patterns Found

| File                                               | Line | Pattern                                       | Severity | Impact                                                                                              |
|----------------------------------------------------|------|-----------------------------------------------|----------|-----------------------------------------------------------------------------------------------------|
| `src/main/resources/templates/restaurant.html`     | 6    | French title: `<title>Fiche Restaurant</title>` | Warning  | Visible page title is French; user sees "Fiche Restaurant" in browser tab                          |
| `src/main/resources/templates/restaurant.html`     | 266  | French label: `Note ${latestGrade}`           | Warning  | "Note" is French for grade/score; displayed to user in grade card                                  |
| `src/main/resources/templates/index.html`          | 463  | Stale nav link: `/hygiene-radar`              | Info     | Links to a removed Thymeleaf route; clicking returns 404. REST endpoint /api/restaurants/hygiene-radar still exists |
| `src/main/resources/templates/index.html`          | 725–943 | Dead API links: /cuisine-scores, /worst-cuisines, /popular-cuisines | Info | Dashboard references removed REST endpoints; API explorer links and JS calls will fail at runtime |
| `src/main/java/com/aflokkat/Application.java`      | 8    | French Javadoc: "Point d'entrée Spring Boot..." | Info    | Developer-facing only; explicitly deferred in 04-01-SUMMARY.md                                    |
| `src/main/java/com/aflokkat/util/ValidationUtil.java` | 31 | French comment: "Validate qu'un fieldName..." | Info    | Developer-facing only; explicitly deferred in 04-01-SUMMARY.md                                    |
| `src/main/java/com/aflokkat/config/MongoClientFactory.java` | 18 | French comment: "Récupère l'instance unique..." | Info | Developer-facing only; explicitly deferred in 04-01-SUMMARY.md                                    |

**Severity classification:**
- Blocker: none
- Warning: restaurant.html French title + grade label (user-visible, contradicts SC-4 truth)
- Info: index.html stale links (runtime UX impact but outside plan scope); deferred Java comments (developer-facing, out-of-scope)

---

### Human Verification Required

#### 1. RestaurantCacheServiceTest pre-existing failures

**Test:** Run `mvn test -Dtest=RestaurantCacheServiceTest` and inspect the 8 errors.
**Expected:** Failures are pre-existing (before phase 4) and unrelated to phase 4 changes.
**Why human:** The 04-04-SUMMARY.md documents these as pre-existing via git stash check, but full suite verification was not performed in the original test run due to environment limitations (no live MongoDB/PostgreSQL). A human should confirm whether these failures are genuinely pre-existing or were introduced by phase 4 changes.

#### 2. index.html stale endpoint references

**Test:** Open `http://localhost:8080/` while logged in. Click the Worst Cuisines / Cuisine Scores / Popular Cuisines API links in the API Explorer section.
**Expected:** These sections should either be removed or replaced with working endpoints. Currently the links and JS calls reference `/api/restaurants/worst-cuisines`, `/api/restaurants/cuisine-scores`, `/api/restaurants/popular-cuisines` which no longer exist.
**Why human:** Cannot verify at runtime without a running instance. The impact on the main dashboard functionality (charts, map) is unclear — only the API explorer links and specific JS fetch calls are affected.

---

### Gaps Summary

One gap blocks the SC-4 translation truth. `restaurant.html` was included in the 04-01 plan scope and its French text was partially translated, but two strings were missed:

1. `<title>Fiche Restaurant</title>` — the browser tab title shown to users
2. `Note ${latestGrade}` — a label displayed in the grade card on the restaurant detail page

These were not in the 04-01 plan's explicit acceptance criteria for restaurant.html (which only listed `Error:` / `Erreur:`) but are clearly French visible UI text. The SC-4 truth as stated in the phase must-haves ("No French visible UI text remains in any template") is not satisfied.

The gap is small and isolated to two string literals in one file. It does not affect test results, security behavior, or any other must-have.

---

_Verified: 2026-04-01T10:20:00Z_
_Verifier: Claude (gsd-verifier)_
