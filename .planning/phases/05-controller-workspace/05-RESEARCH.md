# Phase 5: Controller Workspace - Research

**Researched:** 2026-04-02
**Domain:** Thymeleaf static HTML + vanilla JS frontend, Spring MVC view routing, Spring Security antMatchers
**Confidence:** HIGH — all findings sourced directly from the project's own source files

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Routing
- `/` with ROLE_CONTROLLER redirects server-side to `/dashboard` via `ViewController.index(Authentication auth)` checking `ROLE_CONTROLLER` authority.
- `/dashboard` is SecurityConfig-guarded: `.antMatchers("/dashboard").hasRole("CONTROLLER")`. Unauthenticated or CUSTOMER access redirects to `/login` via existing authenticationEntryPoint.
- `dashboard.html` does NOT duplicate a client-side token check. `fetchWithAuth()` is still used for all API calls.

#### Report List Layout
- Vertical card list, no CSS framework, inline CSS matching `#667eea`/`#764ba2` purple theme.
- Status filter tabs at list top: `[ All | Open | In Progress | Resolved ]`. Active tab highlighted. Clicking calls `GET /api/reports?status=X` (or no param for All) and re-renders client-side, no page reload.
- Each card: left border color = grade color, grade badge, restaurant name, borough + date, 48x48 thumbnail when `photoPath` non-null, `[ Edit ]` and `[ Photo ]` buttons.

#### New Report Form
- Modal overlay opened by `[ + New Report ]` button. Dark backdrop. Same visual style.
- Modal fields: restaurant search with live autocomplete (300ms debounce, `GET /api/restaurants/search?q=&limit=10`, dropdown shows name + borough), grade toggle buttons `[A] [B] [C] [F]`, status `<select>`, violations text input, notes `<textarea>`.
- Submit: `POST /api/reports`. On success modal closes, new card prepended without page reload. Cancel resets form and closes modal.

#### Edit Panel UX
- Inline expansion below the card. Only one edit panel open at a time (opening another closes current). `[ Cancel ]` collapses without saving. `[ Save ]` calls `PATCH /api/reports/{id}` and collapses, updating card values in place.
- Edit fields: grade toggle, status select, violationCodes text, notes textarea. Restaurant is read-only.

#### Photo Upload
- Separate `[ Photo ]` button per card triggers hidden `<input type="file" accept="image/*">`. On file select, POST to `POST /api/reports/{id}/photo` as multipart/form-data. On success, thumbnail `src` updated with `?t=Date.now()` cache-buster.

### Claude's Discretion
- Exact modal CSS (can reuse `.card` class conventions from `index.html`)
- Debounce timer value for restaurant autocomplete (300ms matches `index.html` search bar)
- Grade border color constants (reuse `borderColor(grade)` pattern from `index.html`)
- Tab active state styling (e.g. `background: #667eea; color: white` vs transparent)
- File type hint text for the photo button (e.g. `accept="image/*"`)

### Deferred Ideas (OUT OF SCOPE)
- Drag-and-drop between Kanban status columns
- Bulk report export to PDF (deferred to v3: PLAT-V2-02)
- Edit panel with photo upload merged
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CTRL-05 | Controller can create an inspection report via a web form — search restaurant by name, fill in grade/violations/notes, submit without using the API directly | Modal + autocomplete pattern; `POST /api/reports` already exists |
| CTRL-06 | Controller can view all their reports on a dashboard page with status filter tabs and grade badges | `GET /api/reports?status=` already exists; template card rendering pattern from `index.html` |
| CTRL-07 | Controller can edit a report from the dashboard via an inline edit panel, without leaving the page | Inline expand/collapse pattern; `PATCH /api/reports/{id}` already exists |
| CTRL-08 | Controller can upload a photo and see a thumbnail preview on the report card | Hidden file input pattern; `POST /api/reports/{id}/photo` and `GET /api/reports/{id}/photo` already exist |
</phase_requirements>

---

## Summary

Phase 5 is a pure frontend phase. Every backend API endpoint required already exists from Phases 2 and 3. The work is:

1. Two small Java changes: add `Authentication` parameter to `ViewController.index()` (2-line change) and add `/dashboard` to SecurityConfig antMatchers (1-line change).
2. One new HTML template: `dashboard.html`, a single-page vanilla JS application that consumes the existing report and search APIs.

The project uses plain HTML files with inline CSS, served by `ViewController` which returns only the template name — no Thymeleaf expressions needed. All data is loaded client-side via `fetchWithAuth()` using JWT from localStorage. This is exactly the pattern established in `index.html`, `my-bookmarks.html`, `restaurant.html`, and `inspection-map.html`.

The primary complexity is the JavaScript state management inside `dashboard.html`: the tab/filter system, the modal lifecycle, the inline edit panel (one-at-a-time constraint), and the photo upload + thumbnail refresh. These are all self-contained JS patterns with no library dependency. All patterns are already present in the existing codebase and can be directly copied or adapted.

**Primary recommendation:** Write `dashboard.html` by adapting patterns already present in `index.html` (gradeBadgeHtml, borderColor, fetchWithAuth, debounce search) and `my-bookmarks.html` (simple card list structure). The two Java file changes are minimal, surgical additions.

---

## Standard Stack

### Core
| Library / API | Version | Purpose | Why Standard |
|---------------|---------|---------|--------------|
| Spring Boot (ViewController) | 2.6.15 | Serve `dashboard.html` at `/dashboard` | Already in use; ViewController pattern established |
| Spring Security (SecurityConfig) | bundled with Spring Boot 2.6.15 | Guard `/dashboard` with `hasRole("CONTROLLER")` | Existing `.antMatchers()` chain |
| Vanilla JS (no framework) | ES6 (browser-native) | DOM rendering, fetch, event handling | Project convention — no external JS dependencies |
| Inline CSS | — | Styling matching purple theme | Project convention — no CSS framework |

### No New Dependencies
No `pom.xml` changes required. Zero new Maven dependencies.

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Vanilla JS DOM rendering | React/Vue/Alpine | Not in project stack; adding dependencies violates project constraints |
| Inline CSS | Tailwind/Bootstrap | Violates project constraint — existing templates use inline CSS |

---

## Architecture Patterns

### Recommended Project Structure

Only two modified files and one new file:

```
src/main/java/com/aflokkat/
└── controller/
    └── ViewController.java          — modify: add Authentication param + redirect logic
src/main/java/com/aflokkat/config/
└── SecurityConfig.java              — modify: add /dashboard antMatcher
src/main/resources/templates/
└── dashboard.html                   — create new
```

No new packages, entities, DTOs, repositories, or services.

### Pattern 1: ViewController — Exact code change needed

**Current code** (`ViewController.java` lines 12-15, read from source):
```java
@GetMapping("/")
public String index() {
    return "index";
}
```

**Modified code:**
```java
// Source: ViewController.java + CONTEXT.md code_context
import org.springframework.security.core.Authentication;

@GetMapping("/")
public String index(Authentication auth) {
    if (auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_CONTROLLER"))) {
        return "redirect:/dashboard";
    }
    return "index";
}

@GetMapping("/dashboard")
public String dashboard() {
    return "dashboard";
}
```

**Key facts confirmed by reading ViewController.java:**
- The file currently has NO `Authentication` import. It must be added: `import org.springframework.security.core.Authentication;`
- The `/dashboard` route itself also needs to be added to `ViewController` to serve `dashboard.html`. The CONTEXT.md mentions this in the canonical refs section.
- Spring MVC injects `Authentication` as a method parameter automatically for `@Controller` methods — no `@Autowired` or `SecurityContextHolder` needed.
- `auth` is `null` for unauthenticated (anonymous) requests to `/`. Always guard with `auth != null`.

### Pattern 2: SecurityConfig — Exact line to add

**Current chain** (SecurityConfig.java lines 53-69, read from source):
```java
.antMatchers("/api/auth/**").permitAll()
.antMatchers("/api/restaurants/**").permitAll()
.antMatchers("/api/inspections/**").permitAll()
.antMatchers(  /* swagger */ ).permitAll()
.antMatchers("/api/reports/**").hasRole("CONTROLLER")
.antMatchers("/api/users/**").authenticated()
.anyRequest().permitAll()
```

**Add this one line** before `.anyRequest().permitAll()`:
```java
.antMatchers("/dashboard").hasRole("CONTROLLER")
```

**Confirmed correct insertion point:** After `.antMatchers("/api/users/**").authenticated()` and before `.anyRequest().permitAll()`. Processing order matters — anyRequest must remain last.

**Behavior of the existing access-denied handler** (SecurityConfig.java lines 83-87):
The `accessDeniedHandler` returns JSON 403 for ALL requests, including browser navigation. This means a CUSTOMER navigating to `/dashboard` gets a JSON blob, not a redirect. The `authenticationEntryPoint` (lines 72-81) does redirect browser navigation to `/login`, but only for unauthenticated requests. An authenticated CUSTOMER hitting `/dashboard` gets JSON 403 — acceptable for Phase 5 scope.

### Pattern 3: fetchWithAuth + gradeBadgeHtml + borderColor — copy verbatim from index.html

Three utility functions must be copied verbatim into `dashboard.html` and declared at top-level (not inside an IIFE).

**Source location in index.html:**

```js
// Source: index.html lines 815-836 — copy verbatim
function getAuthHeaders() {
    const token = localStorage.getItem("accessToken");
    if (!token) return { "Content-Type": "application/json" };
    return { "Content-Type": "application/json", Authorization: "Bearer " + token };
}
function handleFetchErrorResponse(response) {
    if (response.status === 401 || response.status === 403) {
        localStorage.removeItem("accessToken");
        localStorage.removeItem("refreshToken");
        window.location.href = "/login";
        throw new Error("Unauthorized");
    }
    return response;
}
function fetchWithAuth(url, options = {}) {
    options.headers = { ...getAuthHeaders(), ...(options.headers || {}) };
    return fetch(url, options).then(handleFetchErrorResponse);
}
```

```js
// Source: index.html lines 1547-1559 (inside IIFE) — extract to top-level in dashboard.html
function gradeBadgeHtml(grade) {
    const g = grade || '—';
    let bg = '#ffebee', color = '#b71c1c';
    if (g === 'A') { bg = '#e8f5e9'; color = '#2e7d32'; }
    else if (g === 'B') { bg = '#fff8e1'; color = '#f57f17'; }
    return `<span style="display:inline-block;padding:2px 8px;border-radius:12px;font-weight:700;font-size:0.82em;background:${bg};color:${color}">${g}</span>`;
}

function borderColor(grade) {
    if (grade === 'A') return '#22c55e';
    if (grade === 'B') return '#eab308';
    return '#ef4444';
}
```

**IMPORTANT:** In `index.html`, `gradeBadgeHtml` and `borderColor` are defined inside a search IIFE (lines 1540-1615). In `dashboard.html` they must be top-level because multiple parts of the page call them (card list rendering, edit panel rendering). Do not copy the IIFE wrapper.

### Pattern 4: Report card DOM structure

Each report gets a card div + a sibling edit-panel div. Both are rendered at list build time, with the edit panel hidden.

```html
<!-- Confirmed fields from ReportController.toResponseMap() -->
<div id="card-{id}" style="background:#f8f9ff;border-radius:8px;padding:10px 12px;margin-bottom:8px;border-left:4px solid {borderColor(grade)};display:flex;align-items:center;gap:10px">
    {gradeBadgeHtml(grade)}
    <div style="flex:1;min-width:0">
        <div style="font-weight:600;font-size:0.9em">{restaurantName}</div>
        <div style="font-size:0.78em;color:#888">{borough} · {formatDate(createdAt)}</div>
        <div style="font-size:0.78em;color:#888">Status: {STATUS_LABELS[status]}</div>
    </div>
    <!-- Thumbnail: only if photoPath non-null -->
    <img id="thumb-{id}" src="/api/reports/{id}/photo" style="width:48px;height:48px;object-fit:cover;border-radius:6px">
    <!-- or placeholder div if no photo -->
    <div id="thumb-{id}" style="width:48px;height:48px"></div>
    <!-- Action buttons -->
    <button onclick="openEditPanel({id})">Edit</button>
    <button onclick="document.getElementById('file-{id}').click()">Photo</button>
    <input type="file" id="file-{id}" accept="image/*" style="display:none" onchange="uploadPhoto({id}, this)">
</div>
<div id="edit-panel-{id}" style="display:none;background:#f0f2ff;border-radius:8px;padding:16px;margin-bottom:8px;border-left:4px solid #667eea">
    <!-- pre-filled edit fields -->
</div>
```

### Pattern 5: One-at-a-time inline edit panel

```js
let openEditId = null;

function openEditPanel(id) {
    if (openEditId !== null && openEditId !== id) {
        closeEditPanel(openEditId);
    }
    openEditId = id;
    document.getElementById('edit-panel-' + id).style.display = 'block';
}

function closeEditPanel(id) {
    const panel = document.getElementById('edit-panel-' + id);
    if (panel) panel.style.display = 'none';
    if (openEditId === id) openEditId = null;
}
```

**Reset rule:** Call `openEditId = null` before any full list re-render (tab switch triggers full innerHTML replacement, destroying the edit panel DOM).

### Pattern 6: Modal overlay — vanilla JS

```html
<!-- Backdrop covers viewport, flex-centers the modal box -->
<div id="modal-backdrop" style="display:none;position:fixed;inset:0;background:rgba(0,0,0,0.5);z-index:100;align-items:center;justify-content:center">
    <div style="background:white;border-radius:12px;padding:28px 32px;max-width:520px;width:92%;max-height:90vh;overflow-y:auto;box-shadow:0 20px 60px rgba(0,0,0,0.4)">
        <!-- form fields -->
    </div>
</div>
```

```js
function openModal() {
    document.getElementById('modal-backdrop').style.display = 'flex';
    // reset form state here
}
function closeModal() {
    document.getElementById('modal-backdrop').style.display = 'none';
}
// Close on backdrop click
document.getElementById('modal-backdrop').addEventListener('click', function(e) {
    if (e.target === this) closeModal();
});
```

### Pattern 7: Autocomplete with locked restaurantId

The autocomplete must track the selected `restaurantId` in a module-level variable. Typing after a selection clears it.

```js
let selectedRestaurantId = null;
let autocompleteTimer = null;

// On input
restaurantSearchInput.addEventListener('input', function() {
    selectedRestaurantId = null;  // clear any previous selection when user types
    clearTimeout(autocompleteTimer);
    const q = this.value.trim();
    if (q.length < 2) { hideDropdown(); return; }
    autocompleteTimer = setTimeout(() => doAutocomplete(q), 300);
});

function doAutocomplete(q) {
    // Source: index.html doSearch pattern (no auth needed — /api/restaurants is permitAll)
    fetch('/api/restaurants/search?q=' + encodeURIComponent(q) + '&limit=10')
        .then(r => r.json())
        .then(data => { if (data.status === 'success') renderDropdown(data.data || []); })
        .catch(() => {});
}

function renderDropdown(restaurants) {
    // Each item has data-id, data-name. Click locks selectedRestaurantId.
    // On click: selectedRestaurantId = item.dataset.id; input.value = item.dataset.name; hide dropdown
}
```

**Search endpoint response shape** (confirmed from index.html line 1592):
```json
{ "status": "success", "data": [ { "restaurantId": "...", "name": "...", "borough": "...", "latestGrade": "A" } ] }
```

**Submit guard:** Before calling `POST /api/reports`, check `if (!selectedRestaurantId)` and show an error message. Proceeding without a selection results in HTTP 400 "restaurantId is required" from the server.

### Pattern 8: Multipart photo upload via fetch + FormData

**CRITICAL:** Do NOT use `fetchWithAuth()` for photo upload. `getAuthHeaders()` sets `Content-Type: application/json`, which breaks multipart parsing.

```js
function uploadPhoto(reportId, fileInput) {
    const file = fileInput.files[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);  // param name must be "file" — confirmed from @RequestParam("file")

    const token = localStorage.getItem('accessToken');
    fetch('/api/reports/' + reportId + '/photo', {
        method: 'POST',
        headers: { Authorization: 'Bearer ' + token },
        // No Content-Type header — browser sets multipart/form-data + boundary automatically
        body: formData
    })
    .then(r => r.json())
    .then(data => {
        if (data.status === 'success') {
            refreshThumbnail(reportId);
            fileInput.value = '';  // reset so the same file can be re-uploaded
        }
    })
    .catch(() => {});
}

function refreshThumbnail(reportId) {
    const el = document.getElementById('thumb-' + reportId);
    if (!el) return;
    if (el.tagName === 'IMG') {
        el.src = '/api/reports/' + reportId + '/photo?t=' + Date.now();
    } else {
        // First photo ever — replace placeholder div with img
        const img = document.createElement('img');
        img.id = 'thumb-' + reportId;
        img.src = '/api/reports/' + reportId + '/photo?t=' + Date.now();
        img.alt = 'photo';
        img.style.cssText = 'width:48px;height:48px;object-fit:cover;border-radius:6px;flex-shrink:0';
        el.replaceWith(img);
    }
}
```

### Anti-Patterns to Avoid
- **Using fetchWithAuth() for photo upload:** Sets `Content-Type: application/json`, breaking multipart. Use raw `fetch()` with only Authorization header.
- **Calling SecurityContextHolder in ViewController:** Spring MVC injects `Authentication` automatically — no manual lookup.
- **Placing /dashboard antMatcher after anyRequest():** anyRequest must be last. The new line goes before it.
- **Thymeleaf expressions in dashboard.html:** ViewController returns only the template name; no server-side data is passed. No `th:` attributes.
- **Duplicate client-side token check at page load:** SecurityConfig handles it server-side. The `if (!token) redirect` pattern from index.html is NOT needed in dashboard.html.
- **Passing display labels to status API param:** Use enum values (`OPEN`, `IN_PROGRESS`, `RESOLVED`), not display strings.

---

## Confirmed API Shape (read from ReportController.java)

### GET /api/reports response

Source: `ReportController.toResponseMap()` lines 46-62 (read from source).

```json
{
  "status": "success",
  "count": 2,
  "data": [
    {
      "id": 12,
      "restaurantId": "41528050",
      "restaurantName": "Joe's Pizza",
      "borough": "Manhattan",
      "grade": "A",
      "status": "OPEN",
      "violationCodes": "04L,10F",
      "notes": "...",
      "photoPath": "/api/reports/12/photo",
      "createdAt": "...",
      "updatedAt": "..."
    }
  ]
}
```

**Critical facts confirmed from source:**
- `photoPath` is already the full API URL (`/api/reports/{id}/photo`), not a filesystem path. The controller transforms it in `toResponseMap()` at line 57-59. The JS just checks `report.photoPath !== null` and uses `img.src = '/api/reports/' + id + '/photo'`.
- `restaurantName` and `borough` can be `null` if the restaurant ID is not found in MongoDB.
- `status` is the enum string: `"OPEN"`, `"IN_PROGRESS"`, `"RESOLVED"`.
- `grade` is the enum string: `"A"`, `"B"`, `"C"`, `"F"`.
- `updatedAt` is present in the response.

### POST /api/reports request body

Source: `ReportRequest.java` (read from source).

```json
{
  "restaurantId": "41528050",
  "grade": "A",
  "status": "OPEN",
  "violationCodes": "04L,10F",
  "notes": "..."
}
```

- `restaurantId` and `grade` are required. `status` defaults to `OPEN` if omitted. Others are optional.

### PATCH /api/reports/{id} behavior

Source: `ReportController.patchReport()` lines 142-146.

- Only non-null JSON fields are applied. Sending `{"violationCodes": null}` leaves the field unchanged.
- To CLEAR a field, send `""` (empty string), not `null`.
- `restaurantId` is ignored in PATCH requests.

### POST /api/reports/{id}/photo

Source: `ReportController.uploadPhoto()` line 163.

- Form parameter name: `file` (must match `@RequestParam("file")`).
- Response on success: same `toResponseMap()` shape, with `photoPath` now non-null.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Grade badge HTML | Custom badge component | Copy `gradeBadgeHtml()` from `index.html` | Already tested, visually consistent |
| Grade border color | Custom color map | Copy `borderColor()` from `index.html` | Consistent with search results |
| Auth fetch wrapper | New fetch utility | Copy `fetchWithAuth()` from `index.html` | Handles 401/403 redirect, token injection |
| Restaurant search | New search implementation | Copy debounce pattern from `index.html` search bar | Same endpoint, same 300ms debounce |
| JWT role detection | Custom token decoder | `JSON.parse(atob(token.split('.')[1])).role` from index.html `checkRoleLinks()` | Already proven pattern |

**Key insight:** `dashboard.html` is an assembly of patterns already proven in `index.html`. The only new JS logic is the modal lifecycle, the edit panel accordion, and the photo upload flow.

---

## Common Pitfalls

### Pitfall 1: Content-Type header in photo upload
**What goes wrong:** Using `fetchWithAuth()` for multipart POST sets `Content-Type: application/json`. Server receives malformed body → 400 error.
**Why it happens:** `getAuthHeaders()` always injects `Content-Type: application/json`. `fetchWithAuth()` merges this, overriding browser's automatic multipart setting.
**How to avoid:** Use raw `fetch()` with only `{ Authorization: 'Bearer ' + token }` for photo uploads. Never set `Content-Type` manually for FormData requests.
**Warning signs:** Server returns 400 "Required request part 'file' is not present".

### Pitfall 2: Authentication injection null check
**What goes wrong:** `ViewController.index(Authentication auth)` throws NPE for anonymous users if `auth.getAuthorities()` is called without null guard.
**Why it happens:** Spring Security injects `null` when there is no authentication (anonymous request to `/`).
**How to avoid:** Always guard: `if (auth != null && auth.getAuthorities().stream()...)`.
**Warning signs:** NPE in ViewController; public homepage breaks for logged-out users.

### Pitfall 3: antMatcher position in SecurityConfig
**What goes wrong:** Adding `/dashboard` antMatcher after `anyRequest().permitAll()` has no effect — anyRequest consumes all remaining paths.
**Why it happens:** Spring Security processes antMatchers in order, first match wins.
**How to avoid:** Add before `anyRequest()`. Confirmed correct position: after `.antMatchers("/api/users/**").authenticated()`.
**Warning signs:** CUSTOMER or anonymous can access `/dashboard` without 403.

### Pitfall 4: gradeBadgeHtml defined inside IIFE scope in index.html
**What goes wrong:** If copied directly from the search IIFE in index.html, the function is not in global scope and throws `ReferenceError` when called from outside.
**Why it happens:** In `index.html`, these are local to the IIFE at line 1540.
**How to avoid:** In `dashboard.html`, define at top-level `<script>` scope.
**Warning signs:** `ReferenceError: gradeBadgeHtml is not defined` in browser console.

### Pitfall 5: Edit panel state leak on tab switch
**What goes wrong:** Tab switch triggers full list re-render (innerHTML replacement), destroying edit panel DOM but leaving `openEditId` set. Next Edit click searches for a non-existent DOM element.
**Why it happens:** `innerHTML` replacement removes the tracked DOM elements.
**How to avoid:** Reset `openEditId = null` inside the tab-switch handler before re-rendering.
**Warning signs:** Edit button becomes unresponsive after switching tabs with a panel open.

### Pitfall 6: Status enum value mismatch
**What goes wrong:** Passing tab label "In Progress" as `?status=In Progress` → server returns 400 or empty list.
**Why it happens:** Server expects exact Java enum name: `IN_PROGRESS` (confirmed from Status.java).
**How to avoid:** Map labels to enum values explicitly:
```js
const STATUS_VALUES = { 'All': null, 'Open': 'OPEN', 'In Progress': 'IN_PROGRESS', 'Resolved': 'RESOLVED' };
```

### Pitfall 7: PATCH null vs empty string for clearing fields
**What goes wrong:** User clears violations field, PATCH sends `{"violationCodes": null}`. Controller ignores null (line 143: `if (req.getViolationCodes() != null)`). Old value persists.
**How to avoid:** For string fields the user can clear, always send `""` not `null`:
```js
body.violationCodes = document.getElementById('edit-violations-' + id).value;  // "" if cleared, not null
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Client-side `if (!token) redirect` on page load | Server-side SecurityConfig guard for `/dashboard` | dashboard.html does NOT need the top-level token redirect |
| `anyRequest().permitAll()` covers all view routes | Explicit `/dashboard` antMatcher for CONTROLLER | `/dashboard` cannot be accessed without CONTROLLER role |
| Controller detects own role via SecurityContextHolder | Spring MVC `Authentication` parameter injection | Simpler, testable, no static call |

---

## Open Questions

1. **accessDeniedHandler sends JSON 403 for CUSTOMER navigating to /dashboard**
   - What we know: The existing handler always returns JSON `{"status":"error","message":"Forbidden"}` for all requests (SecurityConfig.java lines 83-87). The authenticationEntryPoint (lines 72-81) redirects non-API browser navigation to `/login`, but only for unauthenticated users, not access-denied ones.
   - Impact: A logged-in CUSTOMER navigating to `/dashboard` sees JSON instead of a friendly redirect. Acceptable for Phase 5 (the UI will not expose a `/dashboard` link to CUSTOMERs).
   - Recommendation: Out of scope. Document for Phase 7 homepage redesign.

2. **`toResponseMap()` performs N+1 MongoDB lookups**
   - What we know: For each report in the list, `toResponseMap()` calls `restaurantDAO.findByRestaurantId()`. With 50+ reports this is 50+ MongoDB queries per page load.
   - Impact: Negligible at Phase 5 scope (controllers file a handful of reports). Not a correctness issue.
   - Recommendation: Accept for Phase 5. Flag for optimization if Phase 10 admin stats expose the same pattern at scale.

---

## Validation Architecture

`nyquist_validation` is enabled (`config.json: "nyquist_validation": true`).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Mockito — same as `ReportControllerTest.java` |
| Config file | None (Maven Surefire picks up `**/*Test.java`) |
| Quick run command | `mvn test -Dtest=ViewControllerDashboardTest,SecurityConfigTest -pl .` |
| Full suite command | `mvn test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CTRL-05 | `GET /` with ROLE_CONTROLLER returns `redirect:/dashboard` | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_redirectsToDashboard_forController` | ❌ Wave 0 |
| CTRL-05 | `GET /` with ROLE_CUSTOMER returns `"index"` | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_returnsIndex_forCustomer` | ❌ Wave 0 |
| CTRL-05 | `GET /` with null auth returns `"index"` | unit | `mvn test -Dtest=ViewControllerDashboardTest#index_returnsIndex_forAnonymous` | ❌ Wave 0 |
| CTRL-05 | New Report form submits + card appears without reload | manual browser | — | manual-only |
| CTRL-06 | `GET /dashboard` unauthenticated → redirect to `/login` | integration | `mvn test -Dtest=SecurityConfigTest#dashboard_redirectsToLogin_whenUnauthenticated` | ❌ Wave 0 |
| CTRL-06 | `GET /dashboard` with CUSTOMER JWT → 403 | integration | `mvn test -Dtest=SecurityConfigTest#dashboard_returns403_forCustomer` | ❌ Wave 0 |
| CTRL-06 | `GET /dashboard` with CONTROLLER JWT → 200 | integration | `mvn test -Dtest=SecurityConfigTest#dashboard_returns200_forController` | ❌ Wave 0 |
| CTRL-07 | Inline edit panel updates card without reload | manual browser | — | manual-only |
| CTRL-08 | Photo upload + thumbnail refresh | manual browser | — | manual-only |

**Manual-only justification:** SC-2 (New Report modal), SC-3 (edit panel), and SC-4 (photo thumbnail) require browser DOM interaction that cannot be meaningfully tested with MockMvc.

### Test Descriptions

#### ViewControllerDashboardTest (new file — Wave 0)
Pattern: JUnit 5 + Mockito `@ExtendWith(MockitoExtension.class)`, `@InjectMocks ViewController`, build `Authentication` mock manually.

```java
// index_redirectsToDashboard_forController:
// Build Authentication with singletonList(new SimpleGrantedAuthority("ROLE_CONTROLLER"))
// Call viewController.index(auth)
// Assert return value equals "redirect:/dashboard"

// index_returnsIndex_forAnonymous:
// Call viewController.index(null)
// Assert return value equals "index"

// index_returnsIndex_forCustomer:
// Build Authentication with ROLE_CUSTOMER authority
// Call viewController.index(auth)
// Assert return value equals "index"
```

#### SecurityConfigTest additions (extend existing JUnit 4 class)
The existing `SecurityConfigTest` uses JUnit 4 annotations and `AnnotationConfigWebApplicationContext` with a `StubReportsController` inner class. New methods must follow the same JUnit 4 pattern.

```java
// Add StubViewController inner class (or extend existing) with @GetMapping("/dashboard")
// dashboard_redirectsToLogin_whenUnauthenticated: GET /dashboard, no auth → expect 302 to /login
// dashboard_returns403_forCustomer: GET /dashboard with ROLE_CUSTOMER → expect 403
// dashboard_returns200_forController: GET /dashboard with ROLE_CONTROLLER → expect 200
```

**JUnit version caution:** New `ViewControllerDashboardTest` uses JUnit 5 (`org.junit.jupiter.api.Test`). Additions to `SecurityConfigTest` use JUnit 4 (`org.junit.Test`) to match the existing class. Do not mix in the same class.

### Sampling Rate
- **Per task commit:** `mvn test -Dtest=ViewControllerDashboardTest,SecurityConfigTest`
- **Per wave merge:** `mvn test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/com/aflokkat/controller/ViewControllerDashboardTest.java` — 3 unit tests for the `index()` redirect logic
- [ ] `SecurityConfigTest.java` additions — 3 integration tests for `/dashboard` security guard

---

## Manual Test Protocol (by Success Criterion)

### SC-1: Dashboard loads, shows reports with tabs, grade badges, thumbnails

1. Log in as the seeded controller account.
2. Navigate to `/` — verify the browser address bar changes to `/dashboard`.
3. If no reports exist yet, create one via `curl -X POST /api/reports` or Swagger, then refresh.
4. Verify each card shows: restaurant name, colored grade badge (green/yellow/red), borough, formatted date (not a raw epoch number like `1743500000000`), and a 48x48 thumbnail when a photo exists.
5. Click each status tab (All / Open / In Progress / Resolved). Watch the browser Network tab — only a `GET /api/reports?status=...` fetch should fire, no full page navigation.
6. Verify "All" tab shows all reports; status tabs filter correctly.

**Date format check:** If `createdAt` renders as a large number, JS must call `new Date(report.createdAt).toLocaleDateString('en-US', ...)`.

### SC-2: New Report modal — autocomplete, submit, card prepended

1. Click `[ + New Report ]` — verify modal appears with dark backdrop.
2. Click outside the modal box (on the backdrop) — verify modal closes.
3. Click `[ + New Report ]` again. Type 1 character in the restaurant search field — verify no dropdown appears. Type 2+ characters — verify dropdown appears within ~300ms.
4. Do NOT click a dropdown row. Click Submit — verify an error is shown (form should not submit with no restaurant selected).
5. Type again and click a dropdown row — verify the input locks to the restaurant name and the dropdown disappears.
6. Fill in grade (click a toggle), status, violations, notes. Click Submit.
7. Verify modal closes and the new card appears at the TOP of the list without a page reload.
8. Verify the card shows the correct restaurant name and grade badge.

### SC-3: Edit panel — inline expansion, one-at-a-time, PATCH, card update

1. Click `[ Edit ]` on card A — verify edit form expands below card A, pre-filled with current values.
2. Click `[ Edit ]` on card B — verify card A's panel collapses and card B's opens.
3. In card B's edit panel, change the grade to a different value. Click `[ Save ]`.
4. Verify the card's grade badge updates immediately (color and letter change) without a page reload.
5. Open DevTools Network tab — only one `PATCH /api/reports/{id}` request should have fired.
6. Refresh the page — verify the saved grade persists.
7. Open an edit panel, clear the violations field entirely, Save. Refresh — verify violations field is empty (not showing old value). This tests the empty-string-not-null behavior.

### SC-4: Photo upload — thumbnail appears on card

1. Click `[ Photo ]` on a card — verify a file picker dialog opens.
2. Select a JPEG or PNG image.
3. Verify within ~2s a 48x48 thumbnail appears on the card (not a broken image icon).
4. Refresh the page — verify thumbnail still appears (persisted to disk and photoPath saved in DB).
5. Click `[ Photo ]` again on the same card, select a different image — verify thumbnail updates (cache-buster prevents browser caching the old image).

### Verify: Server-side redirect

1. Log out. Navigate to `/` — verify redirect to `/login` (authenticationEntryPoint).
2. Log in as CUSTOMER. Navigate to `/` — verify `index.html` loads (no redirect to dashboard).
3. Log in as CONTROLLER. Navigate to `/` — verify redirect to `/dashboard`.

### Verify: SecurityConfig rejects non-CONTROLLER from /dashboard

1. Logged out: navigate to `/dashboard` — verify redirect to `/login`.
2. Logged in as CUSTOMER: navigate to `/dashboard` directly (type URL) — verify HTTP 403 (raw JSON or browser error page, not the dashboard HTML).
3. Logged in as CONTROLLER: navigate to `/dashboard` — verify 200 and dashboard HTML renders.

---

## Sources

### Primary (HIGH confidence)
- `src/main/java/com/aflokkat/controller/ViewController.java` — current method signatures, import gaps
- `src/main/java/com/aflokkat/config/SecurityConfig.java` — exact antMatcher chain, handler behavior, correct insertion point
- `src/main/java/com/aflokkat/controller/ReportController.java` — `toResponseMap()` response shape, `photoPath` null vs URL behavior, `@RequestParam("file")` name, PATCH null-skip logic
- `src/main/resources/templates/index.html` — verbatim JS functions to copy (gradeBadgeHtml, borderColor, fetchWithAuth, debounce search pattern, card DOM structure)
- `src/main/resources/templates/my-bookmarks.html` — page structure reference
- `src/main/java/com/aflokkat/entity/Grade.java` — enum values: A, B, C, F
- `src/main/java/com/aflokkat/entity/Status.java` — enum values: OPEN, IN_PROGRESS, RESOLVED
- `src/main/java/com/aflokkat/dto/ReportRequest.java` — POST/PATCH body field names
- `src/test/java/com/aflokkat/config/SecurityConfigTest.java` — JUnit 4 test pattern for SecurityConfig
- `src/test/java/com/aflokkat/controller/ReportControllerTest.java` — JUnit 5 Mockito pattern
- `.planning/phases/05-controller-workspace/05-CONTEXT.md` — all locked decisions and canonical refs

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; all libraries already in project
- Architecture: HIGH — patterns directly read from existing source files
- API response shape: HIGH — read line-by-line from ReportController.java
- JS patterns: HIGH — read directly from index.html, verbatim copy with exact line numbers
- Pitfalls: HIGH — derived from actual code reading (null handling in PATCH confirmed at lines 142-146, multipart boundary behavior is browser-standard, IIFE scope confirmed at index.html lines 1540-1615)

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (stable stack; changes to ViewController, SecurityConfig, or ReportController would require update)
