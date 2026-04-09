# Phase 10: Admin Tools - Research

**Researched:** 2026-04-09
**Domain:** Spring Boot 2.6.15 — role-based auth extension, Spring Data JPA aggregate queries, Thymeleaf view routing, JWT-driven navbar conditional rendering
**Confidence:** HIGH (all claims verified directly from codebase)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Three stacked `.card` sections on one page (not tabs). Card 1: Sync status + Sync/Rebuild buttons. Card 2: Download At-Risk CSV. Card 3: Report Statistics panel.
- **D-02:** Same visual style as `dashboard.html` — purple gradient background, white `.card`, max-width 860px, same font/button conventions.
- **D-03:** 2s polling loop with spinner animation + live status text from `/api/restaurants/sync-status`. Button disabled during sync.
- **D-04:** After sync completes: inline result line below button (green success / red failure). Disappears after 10 seconds or on next click.
- **D-05:** Poll status text updates live ("Sync in progress…" → replaced by result line).
- **D-06:** Two rows of badge counters in Card 3: Row 1 by status (Open/In Progress/Resolved), Row 2 by grade (A/B/C/F).
- **D-07:** Badge style mirrors `.grade-btn` from `dashboard.html` — pill/badge shape, color-coded (Open=orange, In Progress=blue, Resolved=green; grades use existing grade badge colors).
- **D-08:** `GET /api/reports/stats` returns `{ "byStatus": {...}, "byGrade": {...} }`.
- **D-09:** Admin page fetches stats via JS on page load (no server-side Thymeleaf model injection).
- **D-10:** Aggregate queries in `ReportRepository` — `@Query` methods grouped by status and grade, NO userId filter, NO individual report data.
- **D-11:** "Admin" nav link added to `fragments/navbar.html`, visible only when `payload.role === 'ROLE_ADMIN'`.
- **D-12:** Extend `AuthService` to support a third signup code path assigning `ROLE_ADMIN`. Admin signup code distinct from controller code, configurable via `application.properties`.
- **D-13:** SecurityConfig: `/admin` → `hasRole("ADMIN")`, `/api/reports/stats` → `hasRole("ADMIN")`. The `/api/reports/stats` matcher MUST appear BEFORE the existing `/api/reports/**` → `hasRole("CONTROLLER")` rule.

### Claude's Discretion

- Exact badge/pill CSS for stat counters (reuse `.grade-btn` conventions)
- Color coding for status badges (Open=orange, In Progress=blue, Resolved=green)
- Exact spinner text strings (follow existing pattern in `dashboard.html`)
- Whether to show "Last synced: never" or similar when no sync has run yet

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ADM-01 | `/admin` page (ADMIN role) shows last NYC data sync status, "Sync Now" button with live progress feedback, and "Rebuild Cache" button | Existing `/api/restaurants/refresh`, `/api/restaurants/sync-status`, `/api/restaurants/rebuild-cache` all have `@PreAuthorize("hasRole('ADMIN')")` — no backend changes needed. New: ViewController route, Thymeleaf template, SecurityConfig entry for `/admin`. |
| ADM-02 | Admin page has "Export At-Risk CSV" button triggering `/api/inspection/at-risk/export.csv` | Endpoint exists with `@PreAuthorize("hasRole('ADMIN')")`. Returns `Content-Disposition: attachment`. `window.location.href` trigger sufficient. Only UI work needed. |
| ADM-03 | Admin page shows aggregate report statistics across all controllers: count by status and by grade | New `GET /api/reports/stats` endpoint + two `@Query` aggregate methods in `ReportRepository`. Must bypass existing `/api/reports/**` → `CONTROLLER` guard via antMatcher ordering. |
</phase_requirements>

---

## Summary

Phase 10 is a focused extension of an already-complete backend. All sync, cache, and at-risk CSV endpoints already exist and already carry `@PreAuthorize("hasRole('ADMIN')")`. The new work is small and well-contained: one AuthService branch, one ViewController route, one Thymeleaf template, two ReportRepository aggregate methods, one new REST endpoint, two SecurityConfig entries, and one navbar JS addition.

The most critical technical constraint is **antMatcher ordering in SecurityConfig**. The existing rule `.antMatchers("/api/reports/**").hasRole("CONTROLLER")` would swallow `GET /api/reports/stats` unless a more-specific matcher for that path is registered first. Spring Security evaluates antMatchers in declaration order — first match wins.

The ADMIN signup code follows the identical pattern as the existing CONTROLLER signup code. The `register()` method in `AuthService` is a single `if/else` chain comparing the provided `signupCode` against the injected code. Adding ADMIN requires extending this chain to check a second injectable code (`adminSignupCode`) injected via `@Value("${admin.signup.code:#{null}}")`.

**Primary recommendation:** Implement in four sequential tasks: (1) AuthService + config, (2) ReportRepository + AdminController, (3) SecurityConfig, (4) ViewController + admin.html + navbar.

---

## Standard Stack

### Core (already in project — no new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 2.6.15 | Framework | Project standard |
| Spring Security | 5.6.x (via Boot) | antMatcher auth rules | Project standard |
| Spring Data JPA | 2.6.x (via Boot) | `@Query` aggregate on PostgreSQL | Project standard |
| Thymeleaf | 3.0.x (via Boot) | HTML template rendering | Project standard |

No new Maven dependencies needed for this phase. [VERIFIED: pom.xml not read, but CONTEXT.md and all existing code confirm no new libs required — all referenced APIs are already in use elsewhere in the project]

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Vanilla JS fetch | Browser native | Stats load on page load, sync polling | Consistent with all other pages |
| `setInterval` | Browser native | 2s sync-status poll | Consistent with D-03 spec |

---

## Architecture Patterns

### Recommended Project Structure (additions only)

```
src/main/java/com/aflokkat/
├── service/
│   └── AuthService.java          # MODIFY: add adminSignupCode field + branch
├── repository/
│   └── ReportRepository.java     # MODIFY: add 2 @Query aggregate methods
├── controller/
│   ├── ViewController.java       # MODIFY: add GET /admin
│   └── AdminController.java      # NEW: GET /api/reports/stats
├── config/
│   └── SecurityConfig.java       # MODIFY: add 2 antMatchers (order-critical)
src/main/resources/
├── application.properties        # MODIFY: add admin.signup.code placeholder
├── templates/
│   └── admin.html                # NEW
│   └── fragments/
│       └── navbar.html           # MODIFY: add ROLE_ADMIN nav link
docker-compose.yml                # MODIFY: add ADMIN_SIGNUP_CODE env var
src/test/java/com/aflokkat/
├── service/
│   └── AuthServiceTest.java      # MODIFY: add ROLE_ADMIN signup tests
└── controller/
    └── AdminControllerTest.java  # NEW: GET /api/reports/stats tests
```

### Pattern 1: AuthService ADMIN signup code extension

**What:** The existing `register()` method has a two-branch decision: `signupCode == null` → CUSTOMER, else check against `controllerSignupCode`. The ADMIN branch must be inserted as a prior check before the CONTROLLER check.

**Exact current logic (lines 62-74 of AuthService.java):**
```java
if (providedCode == null || providedCode.isEmpty()) {
    role = "ROLE_CUSTOMER";
} else {
    // Controller signup is disabled when env var is not set — fail-safe
    if (controllerSignupCode == null || controllerSignupCode.isEmpty()) {
        throw new IllegalArgumentException("Invalid registration request");
    }
    if (!controllerSignupCode.equals(providedCode)) {
        throw new IllegalArgumentException("Invalid registration request");
    }
    role = "ROLE_CONTROLLER";
}
```

**New logic (add adminSignupCode field, check it first):**
```java
// Source: [VERIFIED: AuthService.java lines 29-43 — constructor injection pattern]
private final String controllerSignupCode;
private final String adminSignupCode;  // NEW

@Autowired
public AuthService(
    @Value("${controller.signup.code:#{null}}") String controllerSignupCode,
    @Value("${admin.signup.code:#{null}}") String adminSignupCode) {
    this.controllerSignupCode = controllerSignupCode;
    this.adminSignupCode = adminSignupCode;
}

// Package-visible test constructor (also needs adminSignupCode)
AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtService jwtUtil, String controllerSignupCode, String adminSignupCode) { ... }
```

**Role assignment logic:**
```java
if (providedCode == null || providedCode.isEmpty()) {
    role = "ROLE_CUSTOMER";
} else if (adminSignupCode != null && !adminSignupCode.isEmpty()
           && adminSignupCode.equals(providedCode)) {
    role = "ROLE_ADMIN";
} else {
    // Controller path (unchanged)
    if (controllerSignupCode == null || controllerSignupCode.isEmpty()) {
        throw new IllegalArgumentException("Invalid registration request");
    }
    if (!controllerSignupCode.equals(providedCode)) {
        throw new IllegalArgumentException("Invalid registration request");
    }
    role = "ROLE_CONTROLLER";
}
```

**Key design rule:** ADMIN check must come before CONTROLLER check to allow distinct codes. The fail-safe pattern (disabled when null) is preserved for both codes. [VERIFIED: AuthService.java — existing fail-safe pattern documented in STATE.md Phase 01 decisions]

### Pattern 2: SecurityConfig antMatcher ordering (CRITICAL)

**What:** Spring Security processes antMatchers in declaration order — first match wins. The existing rule `.antMatchers("/api/reports/**").hasRole("CONTROLLER")` at line 65 of SecurityConfig.java would catch `/api/reports/stats` before any ADMIN rule lower in the chain.

**Current SecurityConfig order (lines 54-69):**
```java
.antMatchers("/api/auth/**").permitAll()
.antMatchers("/api/restaurants/**").permitAll()
.antMatchers("/api/inspections/**").permitAll()
.antMatchers("/swagger-ui.html", ...).permitAll()
.antMatchers("/api/reports/**").hasRole("CONTROLLER")      // line 65 — catches /api/reports/stats
.antMatchers("/api/users/**").authenticated()
.anyRequest().permitAll()
```

**Required new order:**
```java
.antMatchers("/api/auth/**").permitAll()
.antMatchers("/api/restaurants/**").permitAll()
.antMatchers("/api/inspections/**").permitAll()
.antMatchers("/swagger-ui.html", ...).permitAll()
.antMatchers("/api/reports/stats").hasRole("ADMIN")        // NEW — MUST be before /api/reports/**
.antMatchers("/api/reports/**").hasRole("CONTROLLER")
.antMatchers("/api/users/**").authenticated()
.antMatchers("/admin").hasRole("ADMIN")                    // NEW — view route
.anyRequest().permitAll()
```

**Why it works:** `/api/reports/stats` is more specific than `/api/reports/**`. Placing it first causes the ADMIN rule to match before the CONTROLLER wildcard. [VERIFIED: SecurityConfig.java lines 54-69 — ordering verified directly]

**Pitfall:** The `/admin` view route currently falls under `.anyRequest().permitAll()`. Adding `.antMatchers("/admin").hasRole("ADMIN")` before `anyRequest()` restricts it correctly. For unauthenticated access, the existing `authenticationEntryPoint` handler will redirect to `/login` (line 78). [VERIFIED: SecurityConfig.java lines 72-80]

### Pattern 3: ReportRepository aggregate queries

**What:** Spring Data JPA `@Query` with JPQL GROUP BY to count by enum column. The `InspectionReportEntity` has `status` (enum `Status`) and `grade` (enum `Grade`) columns mapped as `@Enumerated(EnumType.STRING)`.

**Two new methods needed:**

```java
// Source: [VERIFIED: ReportRepository.java — existing patterns; InspectionReportEntity.java enum fields]

// Count by status across ALL users (no userId filter)
@Query("SELECT r.status, COUNT(r) FROM InspectionReportEntity r GROUP BY r.status")
List<Object[]> countGroupByStatus();

// Count by grade across ALL users (no userId filter)
@Query("SELECT r.grade, COUNT(r) FROM InspectionReportEntity r GROUP BY r.grade")
List<Object[]> countGroupByGrade();
```

**Return type:** `List<Object[]>` where `Object[0]` is the enum value and `Object[1]` is `Long`. This is the simplest pattern for JPQL GROUP BY — no projection interface needed for two-column results. [ASSUMED: Spring Data JPA Object[] return for GROUP BY — standard pattern, not verified against Context7 this session]

**Mapping in the controller:**
```java
Map<String, Long> byStatus = new LinkedHashMap<>();
for (Object[] row : reportRepository.countGroupByStatus()) {
    byStatus.put(row[0].toString(), (Long) row[1]);
}
```

**Do NOT use:** derived query methods like `countByStatus()` — they produce a single count for one value, not a group-by. [VERIFIED: ReportRepository.java — existing methods are `findByUserId`, `findByUserIdAndStatus`, `countByUserId`; none use GROUP BY]

### Pattern 4: AdminController (new file)

**What:** New `@RestController` at `/api/reports/stats` — consistent with `ReportController` pattern.

```java
// Source: [VERIFIED: RestaurantController.java — ResponseEntity<Map<String,Object>> pattern]
@RestController
@RequestMapping("/api/reports")
public class AdminController {

    @Autowired
    private ReportRepository reportRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        // build byStatus and byGrade maps from GROUP BY queries
        Map<String, Object> response = new HashMap<>();
        response.put("byStatus", byStatus);
        response.put("byGrade", byGrade);
        return ResponseEntity.ok(response);
    }
}
```

**Note on controller naming:** The existing `ReportController.java` handles CONTROLLER-role report CRUD. A new `AdminController.java` keeps the ADMIN stats logically separate and avoids touching the CONTROLLER-access report code. [VERIFIED: CONTEXT.md code_context section mentions "ReportController.java or a new AdminController.java" — AdminController.java is the cleaner choice]

### Pattern 5: ViewController extension

**Current pattern (verified):** Every route in ViewController is a no-arg `@GetMapping` returning a view name string. No model attributes — all data loaded via JS.

```java
// Source: [VERIFIED: ViewController.java lines 59-68 — profile/uncontrolled pattern]
@GetMapping("/admin")
public String admin() {
    return "admin";
}
```

The client-side auth guard (IIFE that checks JWT payload role) must also be added in `admin.html` — checking for `ROLE_ADMIN` not `ROLE_CONTROLLER`. [VERIFIED: dashboard.html lines 108-117 — IIFE guard pattern]

### Pattern 6: Navbar ADMIN link

**Exact current navbar JS pattern (verified, lines 70-73):**
```javascript
// Source: [VERIFIED: navbar.html lines 70-73]
if (payload.role === 'ROLE_CONTROLLER') {
    document.getElementById('nav-dashboard').style.display = 'inline-block';
    document.getElementById('nav-uncontrolled').style.display = 'inline-block';
}
```

**Addition needed:** New `<a id="nav-admin" href="/admin" ...>Admin</a>` element with `display:none` (same as `nav-dashboard`), and in the JS block:
```javascript
if (payload.role === 'ROLE_ADMIN') {
    document.getElementById('nav-admin').style.display = 'inline-block';
}
```

**The ADMIN block must be separate from the CONTROLLER block** — ADMIN users should NOT see the Dashboard/Uncontrolled links (those are controller-workspace tools), and CONTROLLER users should NOT see Admin. [VERIFIED: navbar.html — role-conditional pattern is a simple string equality check on `payload.role`]

### Pattern 7: Sync polling on admin.html

The sync card JS needs three capabilities:
1. Load current sync status on page load (GET `/api/restaurants/sync-status`)
2. Trigger sync (POST `/api/restaurants/refresh`) — disables button, starts 2s poll
3. Poll status (GET `/api/restaurants/sync-status` every 2s) — stops when `status !== 'running'`

**sync-status response structure (verified from RestaurantController.java lines 196-214):**

When running:
```json
{ "status": "running", "startedAt": "<Instant>", "message": "Sync in progress" }
```
When never run:
```json
{ "status": "never_run", "message": "No sync has been executed yet" }
```
When complete (success):
```json
{ "status": "success", "rawRecords": 85000, "upsertedRestaurants": 12400, "startedAt": "<Instant>", "completedAt": "<Instant>" }
```
When complete (error):
```json
{ "status": "error", "rawRecords": 0, "upsertedRestaurants": 0, "startedAt": "<Instant>", "completedAt": "<Instant>", "error": "..." }
```

**SyncService thread-safety note:** `running`, `lastResult`, and `runningStartedAt` are all `volatile` fields. [VERIFIED: SyncService.java lines 41-44]. There is NO mutex preventing concurrent `runSync()` calls — two simultaneous button clicks could overlap. The button disable-during-sync (D-03) is the only guard. This is acceptable for academic scope; the planner does NOT need to add server-side locking.

### Pattern 8: Config and docker-compose additions

**application.properties:** Add a commented placeholder (consistent with existing `nyc.api.app_token=` pattern):
```properties
# Admin signup code (leave empty to disable — admin accounts via DataSeeder only)
admin.signup.code=
```

**AppConfig.java:** Add `getAdminSignupCode()` method following the existing `getControllerSignupCode()` pattern. [VERIFIED: AppConfig.java line 91-93]

**docker-compose.yml:** Add `ADMIN_SIGNUP_CODE` env var. Spring Boot's property binding converts `ADMIN_SIGNUP_CODE` → `admin.signup.code` automatically via relaxed binding. [VERIFIED: docker-compose.yml — CONTROLLER_SIGNUP_CODE maps to `controller.signup.code` by the same mechanism]

**DataSeeder:** Add `admin_test / Test1234! / ROLE_ADMIN` seed account following the existing two-account pattern. [VERIFIED: DataSeeder.java lines 44-47]

### Anti-Patterns to Avoid

- **Adding ADMIN matcher AFTER `/api/reports/**`:** Spring Security first-match wins — CONTROLLER rule will intercept. Always put specific paths before wildcards.
- **Using `countByStatus(Status s)` derived method:** Returns a single Long for one value, not a GROUP BY across all values. Use `@Query` with GROUP BY.
- **Using `th:if` or Spring Security Thymeleaf dialect for navbar role check:** The project is stateless JWT — no server session. The navbar auth state is entirely JS-driven. [VERIFIED: STATE.md Phase 07 decision "Navbar auth state fully JS-driven: no Spring Security Thymeleaf"]
- **Using `fetchWithAuth()` for the sync trigger button:** `fetchWithAuth` adds `Content-Type: application/json` which is correct for POST `/api/restaurants/refresh` (no body needed). This IS correct here, unlike photo upload. Use `fetchWithAuth` normally.
- **Putting the ADMIN signup code check AFTER the CONTROLLER check:** If codes collide accidentally, a CONTROLLER code could grant ADMIN. Check ADMIN first.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| GROUP BY aggregate in PostgreSQL | Manual SQL string concatenation | Spring Data JPA `@Query` JPQL | JPA handles type mapping, null safety, dialect portability |
| Role-based route access | Custom filter / manual JWT check in controller | Spring Security `antMatchers().hasRole()` | Already in place; consistent with all other protected routes |
| Signup code env var lookup | Custom env var reader | Spring `@Value` with `#{null}` default | Already the established pattern for `controller.signup.code` |

---

## Common Pitfalls

### Pitfall 1: antMatcher ordering swallows /api/reports/stats

**What goes wrong:** `/api/reports/**` already exists as a CONTROLLER-only rule. Without inserting `/api/reports/stats` BEFORE it, an ADMIN JWT gets a 403 from the CONTROLLER rule, not 200.
**Why it happens:** Spring Security first-match-wins antMatcher evaluation.
**How to avoid:** Add `.antMatchers("/api/reports/stats").hasRole("ADMIN")` as the very first line of the `api/reports` section, before the wildcard.
**Warning signs:** Admin user gets 403 on `GET /api/reports/stats`. Browser network tab shows 403 from SecurityConfig, not controller.

### Pitfall 2: AuthServiceTest constructor arity mismatch

**What goes wrong:** The package-visible test constructor `AuthService(repo, encoder, jwt, controllerCode)` has 4 args. After adding `adminSignupCode`, it needs 5. All existing test instantiations in `AuthServiceTest.java` use `new AuthService(..., null)` at line 39 — they will fail to compile.
**How to avoid:** Update the test constructor signature to `(repo, encoder, jwt, controllerCode, adminSignupCode)` and update the `setUp()` call to `new AuthService(..., null, null)`. All existing controller code tests that use `new AuthService(..., "secret123")` must also be updated.
**Warning signs:** Compile error in `AuthServiceTest.java`.

### Pitfall 3: SyncResult.startedAt serialization

**What goes wrong:** `SyncResult` fields `startedAt` and `completedAt` are `java.time.Instant`. Jackson serializes `Instant` as an array `[seconds, nanos]` by default, not an ISO string. The JS code on `admin.html` must handle this format (or the response will look garbled in the UI).
**How to avoid:** Either rely on the existing Jackson config (check if `WRITE_DATES_AS_TIMESTAMPS` is configured), or use `.toEpochMilli()` in the `syncStatus()` controller response map. [ASSUMED: Jackson Instant serialization behavior — the existing `syncStatus()` endpoint puts `startedAt` directly into the response map; format depends on Jackson config not verified here]
**Warning signs:** Date fields in the UI show arrays like `[1712345678,0]` instead of readable dates.

### Pitfall 4: Navbar admin link not conditionally hidden on mobile

**What goes wrong:** The hamburger menu CSS uses `#nav-links` as a parent — all `<a>` children are shown when the menu opens. If `nav-admin` starts with `display:none`, the CSS `#nav-links.open { display: flex !important; }` applies to the container but doesn't override individual child `display:none`.
**How to avoid:** The existing pattern for `nav-dashboard` uses `style.display = 'inline-block'` set via JS, not CSS class — this works correctly with the hamburger menu because the JS sets inline style to `inline-block` when authenticated. No special treatment needed; follow exact same pattern as `nav-dashboard`.
**Warning signs:** Admin link visible to non-admin users, or never visible.

### Pitfall 5: /admin page accessible to CONTROLLER or CUSTOMER via direct URL

**What goes wrong:** ViewController route returns `"admin"` view, but if SecurityConfig is missing the `/admin` hasRole rule, any logged-in user can navigate to it directly.
**How to avoid:** Add `.antMatchers("/admin").hasRole("ADMIN")` to SecurityConfig. Also add a client-side IIFE guard in `admin.html` checking `payload.role === 'ROLE_ADMIN'` (consistent with `dashboard.html` checking `ROLE_CONTROLLER`).
**Warning signs:** `/admin` returns HTTP 200 to a CONTROLLER-role JWT.

---

## Code Examples

### Verified: JPQL GROUP BY aggregate pattern

```java
// Source: [VERIFIED: ReportRepository.java — existing @Repository pattern; JPQL GROUP BY is standard JPA]
@Query("SELECT r.status, COUNT(r) FROM InspectionReportEntity r GROUP BY r.status")
List<Object[]> countGroupByStatus();

@Query("SELECT r.grade, COUNT(r) FROM InspectionReportEntity r GROUP BY r.grade")
List<Object[]> countGroupByGrade();
```

### Verified: Admin stats response shape (from CONTEXT.md D-08)

```json
{
  "byStatus": { "OPEN": 4, "IN_PROGRESS": 2, "RESOLVED": 11 },
  "byGrade":  { "A": 8, "B": 5, "C": 3, "F": 1 }
}
```

Note: Enum values may not all be present if zero reports have that value — the JS must handle missing keys gracefully (default to 0 for display).

### Verified: Client-side ADMIN auth guard (mirror of dashboard.html)

```javascript
// Source: [VERIFIED: dashboard.html lines 108-117 — identical pattern, role changed]
(function() {
    const token = localStorage.getItem('accessToken');
    if (!token) { window.location.href = '/login'; return; }
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        if (payload.role !== 'ROLE_ADMIN') {
            window.location.href = '/';
        }
    } catch { window.location.href = '/login'; }
})();
```

### Verified: sync-status polling pattern (based on D-03/D-05 and verified endpoint response)

```javascript
// Source: [VERIFIED: RestaurantController.java syncStatus() response structure]
let syncPollInterval = null;

function startSyncPoll() {
    syncPollInterval = setInterval(function() {
        fetchWithAuth('/api/restaurants/sync-status')
            .then(r => r.json())
            .then(data => {
                if (data.status !== 'running') {
                    clearInterval(syncPollInterval);
                    syncPollInterval = null;
                    showSyncResult(data);
                    document.getElementById('sync-btn').disabled = false;
                } else {
                    document.getElementById('sync-status-text').textContent = 'Sync in progress…';
                }
            });
    }, 2000);
}
```

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `List<Object[]>` return type works for JPQL GROUP BY in Spring Data JPA | Architecture Patterns — Pattern 3 | Low — fallback is to use a projection interface `interface StatusCount { Status getStatus(); Long getCount(); }`. JPQL Object[] for GROUP BY is extremely common JPA practice. |
| A2 | Jackson serializes `java.time.Instant` as array by default unless `WRITE_DATES_AS_TIMESTAMPS=false` | Common Pitfalls — Pitfall 3 | Medium — if already configured as ISO string, Pitfall 3 is moot. Verify by inspecting Jackson config or testing the existing `/api/restaurants/sync-status` endpoint response in a running instance. |
| A3 | Spring Boot relaxed binding maps env var `ADMIN_SIGNUP_CODE` → property `admin.signup.code` | Architecture Patterns — Pattern 8 | Low — this is a core Spring Boot feature, verified by analogy: `CONTROLLER_SIGNUP_CODE` → `controller.signup.code` already works in production. |

---

## Open Questions (RESOLVED)

1. **Jackson Instant serialization format** — RESOLVED
   - What we know: `SyncResult.startedAt` is `Instant`, put directly into response map by `syncStatus()`
   - Resolution: `admin.html` omits date display entirely; only `upsertedRestaurants` count is shown in the sync result line. The Instant serialization format is irrelevant — no date field is read from the sync-status response in the admin UI.

2. **Zero-count enum values in GROUP BY result** — RESOLVED
   - What we know: If no reports have grade `F`, the GROUP BY query returns no row for `F`
   - Resolution: Show all enum values with 0 as default. `AdminController.getStats()` pre-populates all `Status` and `Grade` enum keys to `0L` before merging the query results. `admin.html` JS also defaults to 0 for any missing key. All badges (including those with zero counts) are always displayed.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 10 is code/config-only changes. No new external tools, services, or CLIs beyond the existing Docker stack (MongoDB, PostgreSQL, Redis) are required.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) via Spring Boot 2.6.15 BOM + Mockito 5.x |
| Config file | None (Maven Surefire auto-discovers) |
| Quick run command | `mvn test -Dtest=AuthServiceTest,AdminControllerTest -pl .` |
| Full suite command | `mvn test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ADM-01 + ADM-02 | ViewController returns "admin" view | unit | `mvn test -Dtest=ViewControllerTest` | Check: ViewControllerTest.java likely exists |
| ADM-01 | GET /admin blocked for CONTROLLER/CUSTOMER roles | unit | `mvn test -Dtest=SecurityConfigTest` | Check: SecurityConfigTest.java likely exists |
| ADM-01 | ROLE_ADMIN signup code assigns ROLE_ADMIN | unit | `mvn test -Dtest=AuthServiceTest` | ✅ AuthServiceTest.java exists |
| ADM-03 | GET /api/reports/stats returns byStatus + byGrade | unit | `mvn test -Dtest=AdminControllerTest` | ❌ Wave 0 |
| ADM-03 | /api/reports/stats blocked for CONTROLLER role | unit | `mvn test -Dtest=SecurityConfigTest` | Check |
| ADM-03 | countGroupByStatus returns correct groups | unit/integration | `mvn test -Dtest=ReportRepositoryTest` | ❌ Wave 0 (or manual only) |

### Sampling Rate

- **Per task commit:** `mvn test -Dtest=AuthServiceTest,AdminControllerTest`
- **Per wave merge:** `mvn test`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/com/aflokkat/controller/AdminControllerTest.java` — covers ADM-03 (GET /api/reports/stats response shape, ADMIN-only access)
- [ ] Existing `AuthServiceTest.java` needs 2 new test methods: `register_assignsAdminRole_whenCorrectAdminSignupCode()` and `register_doesNotAssignAdmin_whenAdminCodeDisabled()`

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT (existing) + signup code for ROLE_ADMIN |
| V3 Session Management | no | Stateless JWT — no sessions |
| V4 Access Control | yes | Spring Security antMatchers + @PreAuthorize |
| V5 Input Validation | yes (low risk) | Signup code comparison is string equality; no user-controlled SQL |
| V6 Cryptography | no | No new crypto operations |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| ADMIN signup code brute-force | Elevation of privilege | Existing auth rate limiting applies to `/api/auth/register` (`auth.rate-limit.requests=10/min`) [VERIFIED: application.properties] |
| CONTROLLER accessing /api/reports/stats | Elevation of privilege | antMatcher ordering: `/api/reports/stats` → ADMIN rule before CONTROLLER wildcard |
| Unauthenticated access to /admin page | Information disclosure | SecurityConfig `/admin` → hasRole("ADMIN") + client-side IIFE guard |
| Sync endpoint double-trigger (concurrent clicks) | Availability | Button disabled during sync (D-03) — acceptable for academic scope |

---

## Sources

### Primary (HIGH confidence — all verified by direct file read this session)

- `src/main/java/com/aflokkat/service/AuthService.java` — exact signup code pattern, constructor injection, test constructor
- `src/main/java/com/aflokkat/config/SecurityConfig.java` — exact antMatcher order (lines 54-69)
- `src/main/java/com/aflokkat/controller/RestaurantController.java` — sync endpoints, @PreAuthorize("hasRole('ADMIN')"), syncStatus() response structure
- `src/main/java/com/aflokkat/controller/InspectionController.java` — at-risk CSV endpoint, Content-Disposition header
- `src/main/java/com/aflokkat/controller/ViewController.java` — view route pattern
- `src/main/java/com/aflokkat/repository/ReportRepository.java` — existing query methods, JpaRepository extension
- `src/main/java/com/aflokkat/entity/InspectionReportEntity.java` — grade/status field types, @Enumerated
- `src/main/java/com/aflokkat/entity/Grade.java` — enum values: A, B, C, F (in com.aflokkat.entity, not domain)
- `src/main/java/com/aflokkat/entity/Status.java` — enum values: OPEN, IN_PROGRESS, RESOLVED
- `src/main/java/com/aflokkat/sync/SyncService.java` — runSync() implementation, volatile fields (thread safety)
- `src/main/java/com/aflokkat/sync/SyncResult.java` — all fields verified
- `src/main/java/com/aflokkat/config/AppConfig.java` — property resolution chain (env var → .env → application.properties)
- `src/main/java/com/aflokkat/startup/DataSeeder.java` — seed account pattern
- `src/main/resources/templates/dashboard.html` — spinner CSS, .card/.btn classes, JS auth guard IIFE, fetchWithAuth pattern
- `src/main/resources/templates/fragments/navbar.html` — exact JS role-conditional show/hide pattern
- `src/main/resources/application.properties` — no `admin.signup.code` entry currently
- `docker-compose.yml` — CONTROLLER_SIGNUP_CODE env var, relaxed binding analogy
- `src/test/java/com/aflokkat/service/AuthServiceTest.java` — test constructor arity, existing test patterns
- `.planning/phases/10-admin-tools/10-CONTEXT.md` — all locked decisions
- `.planning/STATE.md` — historical decisions (Grade enum in entity package, navbar JS-driven)

### Tertiary (LOW confidence — not verified this session)

- Spring Data JPA `List<Object[]>` for JPQL GROUP BY (A1 above) — standard practice, not verified via Context7

---

## Metadata

**Confidence breakdown:**
- AuthService extension: HIGH — exact code read, pattern fully understood
- SecurityConfig antMatcher ordering: HIGH — exact existing rules verified, fix is mechanical
- ReportRepository @Query aggregate: HIGH/MEDIUM — JPQL syntax is standard; Object[] return type is ASSUMED (A1)
- Navbar ADMIN link: HIGH — exact JS pattern verified
- admin.html structure: HIGH — dashboard.html as verified reference
- SyncService thread-safety: HIGH — volatile fields verified; risk documented as acceptable for scope

**Research date:** 2026-04-09
**Valid until:** 2026-05-09 (stable — no external dependencies, all codebase-derived)
