# Phase 7: Homepage & Navigation - Research

**Researched:** 2026-04-03
**Domain:** Spring MVC / Thymeleaf fragments, client-side JWT routing, MongoDB $sample aggregation
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **Homepage routing split:** `ViewController.index(Authentication auth)` returns `"landing"` for anonymous, `redirect:/dashboard` for ROLE_CONTROLLER, `"index"` for ROLE_CUSTOMER. Two templates, server-side.
- **Anonymous browsing:** restaurant detail and search are already public; login only prompted on Bookmark click.
- **Old index.html content:** borough chart, cuisine scores, inspection heatmap, nearby map all move to `/analytics`. The `index.html` rewrite replaces all of that.
- **New index.html (ROLE_CUSTOMER):** three strips — recent bookmarks, nearby restaurants (if geolocation allowed), 4 KPI tiles.
- **Navbar:** Thymeleaf fragment at `templates/fragments/navbar.html`, auth state JS-driven from `localStorage.getItem("accessToken")`, inserted via `th:replace="fragments/navbar :: navbar"` in every template.
- **Navbar links:** Logo · Search (/) · Map (/inspection-map) · Analytics (/analytics) · auth area.
- **Nav CSS:** self-contained inline styles, purple gradient (`#667eea` / `#764ba2`), sits above `.container`.
- **Landing page:** three sections — hero (KPI stats), search CTA, sample restaurants strip.
- **City stats in hero:** reuse `GET /api/analytics/kpi`; show 2-3 values.
- **Sample restaurants:** new endpoint `GET /api/restaurants/sample?limit=3` using MongoDB `$sample`.
- **Profile page:** new endpoint `GET /api/users/me` (JWT-required) returns `{ username, email, role, bookmarkCount, reportCount }`. `reportCount` null for CUSTOMER. Standalone card page, purple gradient.
- **Username in navbar links to `/profile`.** `/profile` requires JWT; unauthenticated access → client-side redirect to `/login`.

### Claude's Discretion

- Exact navbar height, padding, logo text vs icon
- Whether to add the navbar to `login.html`
- CSS for the role badge (green vs orange — outline or filled)
- Whether the sample restaurants section has a "Reload" button
- Exact behavior when geolocation is denied (message or hide section)

### Deferred Ideas (OUT OF SCOPE)

- None — discussion stayed within phase scope

</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| UX-01 | Non-authenticated visitors see a landing page with city-wide stats, a search CTA, and 3 sample restaurants | New `landing.html` template, `GET /api/restaurants/sample`, reuse `GET /api/analytics/kpi` |
| UX-02 | Authenticated users see a personalised dashboard on `/`: recent bookmarks, nearby restaurants (if geolocation allowed), and a summary stats strip | Rewrite `index.html` with 3 strips; reuse `GET /api/users/me/bookmarks`, `GET /api/restaurants/nearby`, `GET /api/analytics/kpi` |
| UX-03 | Persistent top navbar on all pages: logo, Search/Map/Analytics links, right side shows Login or username + Logout | Thymeleaf fragment pattern; JS-driven auth state from localStorage JWT |
| UX-04 | `/profile` page shows logged-in user's username, email, role badge, bookmark count, and (for controllers) report count | New `GET /api/users/me` endpoint extension, `countByUserId()` on `BookmarkRepository` and `ReportRepository`, new `profile.html` |

</phase_requirements>

---

## Summary

Phase 7 is a frontend-dominant phase with two small backend additions. The bulk of work is Thymeleaf templating: one new fragment (`navbar.html`), two new templates (`landing.html`, `profile.html`), one template rewrite (`index.html`), and seven templates updated to insert the navbar. The backend changes are minimal: one new DAO method (`findSampleRestaurants`), one new controller endpoint on `RestaurantController` (`GET /api/restaurants/sample`), and one enrichment of the existing `GET /api/users/me` endpoint (add `bookmarkCount` and `reportCount`).

All infrastructure needed already exists. The project uses raw Spring MVC, Thymeleaf, inline CSS, no CSS frameworks, no build tooling beyond Maven. The auth state in the navbar must be JS-driven (localStorage JWT check) because the application uses stateless JWT — there is no server-side session for Thymeleaf `sec:` attributes. The existing `ViewController.index(Authentication auth)` already has the ROLE_CONTROLLER redirect from Phase 5; it needs only the `auth == null` → `"landing"` branch and a new `GET /profile` route.

The biggest gap is that `GET /api/users/me` already exists but only returns `id, username, email, role, createdAt`. It needs `bookmarkCount` and `reportCount`. The `BookmarkRepository` has `findByUserId(Long)` (count via `.size()`). The `ReportRepository` has `findByUserId(Long)` (count via `.size()` — no `countByUserId` method exists yet, or a derived query can be added). These are trivial JPA additions.

**Primary recommendation:** Build in three waves — (1) backend stubs + tests, (2) fragment + template work, (3) navbar insertion into existing templates.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Thymeleaf | 2.6.15 BOM (3.0.x) | Server-side HTML templates | Already in project; only option per Spring Boot 2.6.x |
| Spring MVC | 5.3.x (Spring Boot 2.6.15) | Request routing, view resolution | Already in project |
| MongoDB driver-sync | Already in pom.xml | `$sample` aggregation | Already in project |
| Spring Data JPA | Already in project | `BookmarkRepository`, `ReportRepository` count queries | Already in project |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Chart.js | 4.4.0 CDN | KPI tile animations (inherited from analytics.html) | Only on personalised index.html |
| Leaflet | 1.9.4 CDN | Nearby restaurants geolocation | Only on personalised index.html — not on landing |

**No new dependencies.** This phase adds zero lines to `pom.xml`.

---

## Architecture Patterns

### Recommended Project Structure

New files:
```
src/main/resources/templates/
├── fragments/
│   └── navbar.html         # NEW — th:fragment="navbar"
├── landing.html            # NEW — anonymous visitor homepage
├── profile.html            # NEW — /profile page
└── index.html              # REWRITE — personalised customer dashboard

Modified files (navbar insertion only):
├── analytics.html
├── dashboard.html
├── restaurant.html
├── inspection-map.html
└── my-bookmarks.html
```

New Java files:
```
src/main/java/com/aflokkat/
├── controller/ViewController.java     # MODIFY — add landing branch + /profile route
├── controller/UserController.java     # MODIFY — enrich /api/users/me
├── controller/RestaurantController.java # MODIFY — add /api/restaurants/sample
├── dao/RestaurantDAO.java             # MODIFY — add findSampleRestaurants interface method
└── dao/RestaurantDAOImpl.java         # MODIFY — implement findSampleRestaurants
```

### Pattern 1: Thymeleaf Fragment Navbar

**What:** A `<nav th:fragment="navbar">` in a dedicated file, inserted into every template body.
**When to use:** Any element repeated across multiple pages.

```html
<!-- fragments/navbar.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
<nav th:fragment="navbar"
     style="background:linear-gradient(135deg,#667eea 0%,#764ba2 100%);
            height:56px;display:flex;align-items:center;
            padding:0 24px;gap:24px;position:sticky;top:0;z-index:100">
  <!-- content omitted — see UI-SPEC -->
  <span id="nav-auth"></span>
</nav>
<script>
  (function() {
    var t = localStorage.getItem('accessToken');
    var el = document.getElementById('nav-auth');
    if (!t) { el.innerHTML = '<a href="/login">Sign In</a>'; return; }
    try {
      var sub = JSON.parse(atob(t.split('.')[1])).sub;
      el.innerHTML = '<a href="/profile">' + sub + '</a> '
        + '<button onclick="(function(){localStorage.removeItem(\'accessToken\');'
        + 'localStorage.removeItem(\'refreshToken\');window.location.href=\'/login\'})()">Sign Out</button>';
    } catch(e) { el.innerHTML = '<a href="/login">Sign In</a>'; }
  })();
</script>
</body>
</html>
```

Consumer template insertion (first child of `<body>`):
```html
<body>
  <div th:replace="fragments/navbar :: navbar"></div>
  <div class="container" style="padding-top: 72px">
    ...
  </div>
</body>
```

**Confidence:** HIGH — standard Thymeleaf 3 fragment pattern, verified against Spring Boot 2.6.x docs.

### Pattern 2: MongoDB $sample Aggregation

**What:** Returns N randomly-selected documents per request.
**When to use:** `GET /api/restaurants/sample?limit=N` on the landing page.

```java
// RestaurantDAOImpl.java — consistent with existing aggregate() helper
@Override
public List<Restaurant> findSampleRestaurants(int limit) {
    return aggregate(Arrays.asList(
        new Document("$sample", new Document("size", limit))
    ), Restaurant.class);
}
```

This mirrors the existing `findRandom()` method (which uses `$sample` with size 1) — exact same pattern, just parameterised.

**Confidence:** HIGH — pattern already exists in `findRandom()` at line 201-207 of `RestaurantDAOImpl.java`.

### Pattern 3: ViewController Routing Split

**What:** `index(Authentication auth)` returns different views based on auth state and role.
**Current state:** Returns `"index"` for all non-CONTROLLER (including anonymous).
**Required change:** Returns `"landing"` when `auth == null`, keeps `"index"` for ROLE_CUSTOMER.

```java
@GetMapping("/")
public String index(Authentication auth) {
    if (auth == null) {
        return "landing";
    }
    if (auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_CONTROLLER"))) {
        return "redirect:/dashboard";
    }
    return "index";  // ROLE_CUSTOMER
}

@GetMapping("/profile")
public String profile() {
    return "profile";
}
```

**Test impact:** `ViewControllerDashboardTest` has an existing test `index_returnsIndex_forAnonymous()` that asserts `"index"` when auth is null. This test MUST be updated to assert `"landing"`.

**Confidence:** HIGH — existing test file is at `src/test/java/com/aflokkat/controller/ViewControllerDashboardTest.java`, lines 28-30.

### Pattern 4: SecurityConfig — /profile Route

**What:** `/profile` must be accessible to authenticated users only; unauthenticated requests should redirect to `/login`.
**Current state:** `anyRequest().permitAll()` catches all non-API view routes.
**Required change:** Add `.antMatchers("/profile").authenticated()` before `anyRequest().permitAll()`.

```java
// SecurityConfig.java — inside authorizeRequests()
.antMatchers("/api/users/**").authenticated()
.antMatchers("/profile").authenticated()   // ADD THIS LINE
.anyRequest().permitAll()
```

The existing `authenticationEntryPoint` already handles the non-API redirect to `/login` when unauthenticated, so no additional code is needed.

**Confidence:** HIGH — confirmed against `SecurityConfig.java` current state.

### Pattern 5: Enriched /api/users/me

**What:** Current `GET /api/users/me` returns `id, username, email, role, createdAt`. Needs `bookmarkCount` and `reportCount`.

```java
// UserController.java — add to getProfile()
long bookmarkCount = bookmarkRepository.countByUserId(user.getId());
Long reportCount = null;
if ("ROLE_CONTROLLER".equals(user.getRole())) {
    reportCount = reportRepository.countByUserId(user.getId());
}
data.put("bookmarkCount", bookmarkCount);
data.put("reportCount", reportCount);
```

`BookmarkRepository` needs a derived query:
```java
long countByUserId(Long userId);
```

`ReportRepository` needs a derived query:
```java
long countByUserId(Long userId);
```

Spring Data JPA will auto-implement both. No SQL needed.

**Confidence:** HIGH — Spring Data JPA derives `count` queries from method names, verified against Spring Data JPA docs.

### Pattern 6: Active Nav Link Detection

**What:** JS compares `window.location.pathname` to each link's `href` and applies the active pill style.

```javascript
// Inside navbar fragment <script>
document.querySelectorAll('nav a[data-nav]').forEach(function(link) {
  if (window.location.pathname === link.getAttribute('href')) {
    link.style.background = 'rgba(255,255,255,0.28)';
    link.style.border = '1px solid rgba(255,255,255,0.5)';
  }
});
```

**Confidence:** HIGH — standard DOM pattern, no library dependency.

### Anti-Patterns to Avoid

- **Using `th:sec` (Spring Security Thymeleaf dialect) for auth-state rendering:** The project uses stateless JWT. There is no `SecurityContext` during Thymeleaf rendering of public pages. The auth area in the navbar MUST be client-side JS only, not `th:if="${#authorization.expression('isAuthenticated()')}"`
- **Redirecting anonymous users away from `/`:** The old `index.html` has `if (!token) { window.location.href = "/login"; }` at line 812. This JS guard MUST be removed from the new `index.html` (customers are authenticated so it stays implicitly) but MUST NOT be present in `landing.html`.
- **Loading Leaflet on landing.html:** Leaflet is only needed on the personalised `index.html` for the nearby strip. Adding it to `landing.html` wastes bandwidth on every anonymous page load.
- **Using `@Autowired` field injection for new dependencies:** The project pattern for controllers is `@Autowired` field injection (see `UserController`). Stay consistent — do not introduce constructor injection in this phase (it would be inconsistent and violate the established pattern noted in Phase 4 decisions).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Random document selection | Custom random index logic | MongoDB `$sample` aggregation | Already implemented in `findRandom()` — just parameterise |
| Repeated HTML navbar | Copy-paste into every template | Thymeleaf `th:fragment` / `th:replace` | Maintenance nightmare with 8 templates |
| JWT username decode | Custom base64 library | `atob(token.split('.')[1])` (built-in browser API) | Already used in `index.html` lines 1511-1518 |
| Bookmark count query | Manual loop over `findByUserId()` | JPA derived query `countByUserId()` | One method declaration, zero SQL |

---

## Common Pitfalls

### Pitfall 1: Existing test `index_returnsIndex_forAnonymous` will fail

**What goes wrong:** `ViewControllerDashboardTest.index_returnsIndex_forAnonymous()` currently asserts `"index"` when `auth == null`. After the routing change it will assert `"landing"`.
**Why it happens:** The test was written before the landing page was planned.
**How to avoid:** Update the test assertion to `assertEquals("landing", viewController.index(null))` in the same commit as the routing change.
**Warning signs:** `mvn test` fails on `ViewControllerDashboardTest` after editing `ViewController.java`.

### Pitfall 2: `index.html` JS auth guard redirects authenticated customers away

**What goes wrong:** The current `index.html` contains `if (!token) { window.location.href = "/login"; }`. The new `index.html` is the authenticated customer view — this guard is correct. BUT if any vestige of it leaks into `landing.html`, anonymous users will be redirected to login on the landing page.
**Why it happens:** Copy-paste from `index.html` as template base.
**How to avoid:** `landing.html` must NOT contain any token check or redirect. Verify by loading `/` in an incognito window.

### Pitfall 3: Navbar fragment `<script>` fires before DOM elements exist

**What goes wrong:** The navbar JS runs during fragment inclusion; if `<span id="nav-auth">` is not yet in the DOM when the script runs, `getElementById` returns null and auth state is not rendered.
**Why it happens:** `<script>` in the fragment body runs at parse time.
**How to avoid:** Wrap the auth state script in an IIFE (already shown in Pattern 1) — it runs synchronously but the `<span id="nav-auth">` is defined before the `<script>` tag within the same fragment. Alternatively use `DOMContentLoaded`. The IIFE approach is simpler given the fragment is self-contained.

### Pitfall 4: `th:replace` vs `th:insert` — duplicate `<html>` tags

**What goes wrong:** Using `th:insert` instead of `th:replace` wraps the fragment in an extra element; using `th:replace` on a `<div>` replaces the div with the `<nav>` — which is the intended result.
**Why it happens:** Confusion between Thymeleaf 3 fragment inclusion modes.
**How to avoid:** Use `<div th:replace="fragments/navbar :: navbar"></div>` — the `<div>` is replaced entirely by the `<nav>` element.

### Pitfall 5: `padding-top` on `.container` not applied on existing templates

**What goes wrong:** The navbar is `position: sticky; top: 0` (56px). Without `padding-top: 72px` on `.container`, the top of page content is hidden under the navbar.
**Why it happens:** Existing templates do not have this padding.
**How to avoid:** Add `style="padding-top: 72px"` to the `.container` div in every template that receives the navbar. This is the ONLY structural change to existing templates per the UI-SPEC.

### Pitfall 6: `ReportRepository` does not have `countByUserId`

**What goes wrong:** The `GET /api/users/me` enrichment for CONTROLLER needs a report count. `ReportRepository` currently only has `findByUserId` and `findByUserIdAndStatus`. Calling `.size()` on `findByUserId()` loads all report entities into memory.
**Why it happens:** The count method was not needed before.
**How to avoid:** Add `long countByUserId(Long userId)` to `ReportRepository` — Spring Data JPA generates the query automatically. Do not use `findByUserId(...).size()`.

### Pitfall 7: JWT decode fails on malformed token

**What goes wrong:** `atob(token.split('.')[1])` throws if the token is expired, truncated, or the localStorage value is corrupted.
**Why it happens:** JWT base64url uses `-` and `_` instead of `+` and `/`. `atob` requires standard base64.
**How to avoid:** The existing codebase already uses `atob()` directly (e.g., `index.html` line 1512) and it works because the JWT payload segment happens to not contain `-` or `_` chars in the `sub` claim. To be safe, wrap in try/catch and fall back to the logged-out state — already shown in Pattern 1.

---

## Code Examples

### $sample aggregation (consistent with existing findRandom)
```java
// Source: RestaurantDAOImpl.java lines 201-207 (existing findRandom pattern)
// New method:
@Override
public List<Restaurant> findSampleRestaurants(int limit) {
    return aggregate(Arrays.asList(
        new Document("$sample", new Document("size", limit))
    ), Restaurant.class);
}
```

### RestaurantController — sample endpoint
```java
// Source: RestaurantController.java getRandom() pattern (lines 236-253)
@Operation(summary = "Random sample of restaurants")
@GetMapping("/sample")
public ResponseEntity<Map<String, Object>> getSample(
        @RequestParam(defaultValue = "3") int limit) {
    try {
        List<Restaurant> data = restaurantDAO.findSampleRestaurants(limit);
        List<Map<String, Object>> views = data.stream()
            .map(RestaurantService::toView)
            .collect(Collectors.toList());
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", views);
        response.put("count", views.size());
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        return errorResponse(e);
    }
}
```

### KPI tiles markup (copy from analytics.html)
```html
<!-- Source: analytics.html lines 49-66 -->
<div class="dashboard" style="grid-template-columns: repeat(4, 1fr)">
    <div class="card" style="text-align:center">
        <div id="kpi-total" style="font-size:2em;font-weight:700;color:#667eea">&#8212;</div>
        <div style="font-size:0.82em;color:#666;margin-top:4px">Total Restaurants</div>
    </div>
    <!-- ... 3 more tiles ... -->
</div>
```

### Grade badge JS helper (copy from index.html)
```javascript
// Source: index.html lines 1550-1555
function gradeBadgeHtml(grade) {
    const g = grade || '—';
    let bg = '#ffebee', color = '#b71c1c';
    if (g === 'A') { bg = '#e8f5e9'; color = '#2e7d32'; }
    else if (g === 'B') { bg = '#fff8e1'; color = '#f57f17'; }
    return `<span style="display:inline-block;padding:2px 8px;border-radius:12px;font-weight:700;font-size:0.82em;background:${bg};color:${color}">${g}</span>`;
}
```

### Search debounce pattern (copy from index.html)
```javascript
// Source: index.html lines 1612-1618
searchInput.addEventListener('input', function() {
    clearTimeout(searchTimer);
    const q = this.value.trim();
    if (q.length < 2) { hideResults(); return; }
    searchTimer = setTimeout(() => doSearch(q), 300);
});
```

### JWT decode with fallback (from index.html line 1511-1518)
```javascript
// Source: index.html lines 1511-1518
const t = localStorage.getItem('accessToken');
try {
    const payload = JSON.parse(atob(t.split('.')[1]));
    // payload.sub = username
} catch(e) {
    // treat as logged out
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single `/` route returning `"index"` for all users | Three-branch routing: landing / customer dashboard / controller redirect | Phase 7 | `index_returnsIndex_forAnonymous` test breaks — must update |
| Navigation via inline pill links in each template header | Thymeleaf fragment navbar | Phase 7 | Navbar changes only need to be made in one file |
| `GET /api/users/me` returns minimal profile data | Returns `bookmarkCount` and `reportCount` | Phase 7 | Profile page and `/api/users/me` now serve the profile page |

**Deprecated/outdated in this phase:**
- Old `index.html` header pill links (Search, Map, Analytics, Logout inline in `<header>`): replaced by the navbar fragment. The header element in each template becomes a page-specific title strip only.

---

## Open Questions

1. **Nearby strip on personalised `index.html` — card list or map?**
   - What we know: UI-SPEC specifies "card list, not a map" — geolocation + Leaflet migrated as card list. The existing `index.html` nearby section uses a Leaflet map + a list below.
   - What's unclear: Whether Leaflet CDN should still be loaded on `index.html` if the map is removed.
   - Recommendation: Remove Leaflet from `index.html` — the new nearby strip is a card list calling `GET /api/restaurants/nearby`, rendering cards the same way as the bookmark strip. This reduces page weight.

2. **`/api/users/me` currently returns `role` as stored string (`ROLE_CUSTOMER` / `ROLE_CONTROLLER`)**
   - What we know: `UserEntity.getRole()` returns the raw DB string (e.g., `"ROLE_CUSTOMER"`).
   - What's unclear: Profile badge should display `"CUSTOMER"` not `"ROLE_CUSTOMER"`.
   - Recommendation: Strip the `ROLE_` prefix in the JS on the profile page: `role.replace('ROLE_', '')`. No backend change needed.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Mockito — Spring Boot 2.6.15 BOM |
| Config file | none — surefire plugin in pom.xml |
| Quick run command | `mvn test -Dtest=ViewControllerDashboardTest,ViewControllerAnalyticsTest` |
| Full suite command | `mvn test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UX-01 | `GET /` with null auth returns `"landing"` view | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_returnsLanding_forAnonymous -pl .` | ❌ Wave 0 (existing test must be renamed/updated) |
| UX-01 | `GET /api/restaurants/sample?limit=3` returns 3 restaurants | unit | `mvn test -Dtest=RestaurantControllerSampleTest -pl .` | ❌ Wave 0 |
| UX-02 | `GET /` with ROLE_CUSTOMER auth returns `"index"` view | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_returnsIndex_forCustomer -pl .` | ✅ exists |
| UX-03 | `GET /` with ROLE_CONTROLLER auth redirects to `/dashboard` | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_redirectsToDashboard_forController -pl .` | ✅ exists |
| UX-04 | `GET /profile` view route returns `"profile"` | unit | `mvn test -Dtest=ViewControllerProfileTest -pl .` | ❌ Wave 0 |
| UX-04 | `GET /api/users/me` returns bookmarkCount and reportCount fields | unit | `mvn test -Dtest=UserControllerMeTest -pl .` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `mvn test -Dtest=ViewControllerDashboardTest,ViewControllerAnalyticsTest`
- **Per wave merge:** `mvn test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] Update `ViewControllerDashboardTest#index_returnsIndex_forAnonymous` → rename to `index_returnsLanding_forAnonymous` and assert `"landing"`
- [ ] `src/test/java/com/aflokkat/controller/RestaurantControllerSampleTest.java` — covers UX-01 sample endpoint
- [ ] `src/test/java/com/aflokkat/controller/ViewControllerProfileTest.java` — covers UX-04 `/profile` route
- [ ] `src/test/java/com/aflokkat/controller/UserControllerMeTest.java` — covers UX-04 enriched `/api/users/me`
- [ ] `long countByUserId(Long userId)` on `BookmarkRepository` — required before UserControllerMeTest compiles
- [ ] `long countByUserId(Long userId)` on `ReportRepository` — required before UserControllerMeTest compiles

---

## Sources

### Primary (HIGH confidence)
- Direct code inspection: `ViewController.java`, `SecurityConfig.java`, `RestaurantController.java`, `UserController.java`, `RestaurantDAOImpl.java`, `RestaurantDAO.java`, `BookmarkRepository.java`, `ReportRepository.java`, `UserEntity.java`, `InspectionReportEntity.java`
- Direct template inspection: `index.html`, `analytics.html`
- Direct test inspection: `ViewControllerDashboardTest.java`, `ViewControllerAnalyticsTest.java`
- `.planning/phases/07-homepage-navigation/07-CONTEXT.md` — locked decisions
- `.planning/phases/07-homepage-navigation/07-UI-SPEC.md` — design contract
- `.planning/REQUIREMENTS.md` — UX-01 through UX-04

### Secondary (MEDIUM confidence)
- Spring Boot 2.6.15 + Thymeleaf 3 fragment docs (Thymeleaf `th:replace`/`th:fragment` pattern is stable and unchanged since Thymeleaf 3.0)
- Spring Data JPA derived query naming convention (`countByUserId`) — standard, well-documented

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — zero new dependencies; everything already in pom.xml
- Architecture: HIGH — all patterns verified against existing code in the repo
- Pitfalls: HIGH — identified from existing code (auth guard in index.html, broken test assertion)

**Research date:** 2026-04-03
**Valid until:** 2026-05-03 (stable stack — no fast-moving dependencies)
