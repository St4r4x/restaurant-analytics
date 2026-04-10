# Phase 5: Controller Workspace - Context

**Gathered:** 2026-04-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Controllers can manage their inspection reports entirely through a dedicated UI — search a
restaurant, file a report, edit it, attach a photo — without touching the API directly.
All backend API endpoints already exist from Phase 2. This phase is purely frontend:
one new Thymeleaf template (`dashboard.html`), one new ViewController route, one
SecurityConfig entry.

No new REST endpoints. No schema changes.

</domain>

<decisions>
## Implementation Decisions

### Area 1: Dashboard vs index — Routing

- **`/` with ROLE_CONTROLLER → server-side redirect to `/dashboard`.**
  `ViewController.index()` reads `SecurityContextHolder.getContext().getAuthentication()`
  and checks for `ROLE_CONTROLLER` authority. If found, returns `"redirect:/dashboard"`.
  Non-controllers (CUSTOMER, anonymous) see `index.html` as before.

- **`/dashboard` is SecurityConfig-guarded.**
  Add `/dashboard` to the antMatchers requiring `ROLE_CONTROLLER`. An unauthenticated or
  CUSTOMER request to `/dashboard` returns 403 / redirects to `/login` via the existing
  access-denied handler. No separate client-side guard needed on `dashboard.html`.

- **`dashboard.html` does NOT add a duplicate token check.** The SecurityConfig guard is
  authoritative. The JS on the page still uses `getAuthHeaders()` / `fetchWithAuth()` for
  API calls (same pattern as `index.html`).

### Area 2: Report List Layout

- **Vertical card list**, consistent with the `top-restaurant-item` row pattern in
  `index.html`. No CSS framework — inline CSS matching the existing purple gradient theme
  (`#667eea` / `#764ba2`).

- **Status filter tabs** at the top of the list:
  `[ All | Open | In Progress | Resolved ]`
  Active tab is highlighted. Clicking a tab calls `GET /api/reports?status=X` (or no
  `status` param for All) and re-renders the list client-side. No page reload.

- **Each card shows:**
  - Left border color = grade color (green A, yellow B, red C/F — same as search results)
  - Grade badge (same `gradeBadgeHtml()` pattern as `index.html`)
  - Restaurant name (fetched from the enriched report response)
  - Borough · date (formatted)
  - Thumbnail: 48×48px `<img>` calling `GET /api/reports/{id}/photo` — only rendered when
    `photoPath` is non-null in the report response
  - Action buttons: `[ Edit ]` and `[ Photo ]`

### Area 3: New Report Form

- **Modal overlay.** A `[ + New Report ]` button at the top of the dashboard opens a
  centered modal with a dark backdrop overlay. Same visual style as the rest of the page.

- **Modal fields:**
  1. Restaurant search — text input with live autocomplete dropdown (300ms debounce,
     calls `GET /api/restaurants/search?q=...&limit=10`). Each dropdown row shows
     **name + borough** (e.g. "Joe's Pizza — Manhattan"). Selecting a row locks in the
     `restaurantId` hidden value.
  2. Grade — four toggle buttons `[A] [B] [C] [F]`, one selectable at a time
  3. Status — `<select>` with OPEN / IN_PROGRESS / RESOLVED
  4. Violations — text input (comma-separated codes, e.g. `04L,10F`)
  5. Notes — `<textarea>`

- **Submit behavior:** `POST /api/reports` with JSON body. On success, the modal closes
  and the new card is **prepended to the list** without a full re-fetch (or the list
  re-fetches from `GET /api/reports`). No page reload.

- **Cancel:** modal closes, form state is reset.

### Area 4: Edit Panel UX

- **Inline expansion below the card.** When `[ Edit ]` is clicked, the card expands
  downward to reveal an edit form pre-filled with the current report values. Other cards
  are not displaced (the form pushes down content below the expanded card). A `[ Cancel ]`
  collapses back to the card view without saving. `[ Save ]` calls
  `PATCH /api/reports/{id}` and collapses the panel, updating the card values in place
  without a full list reload. Only one card can be in edit mode at a time (opening another
  closes the current one).

- **Edit fields:** grade (toggle buttons), status (select), violationCodes (text input),
  notes (textarea). Restaurant is read-only (not editable per Phase 2 contract).

- **Photo upload — separate `[ Photo ]` button on each card.** Clicking it triggers a
  hidden `<input type="file">`. On file select, the file is POSTed to
  `POST /api/reports/{id}/photo` as `multipart/form-data`. On success, the card thumbnail
  is refreshed by re-fetching the report or updating the `src` attribute of the thumbnail
  `<img>` to `GET /api/reports/{id}/photo` + a cache-buster query param.

### Claude's Discretion

- Exact modal CSS (can reuse `.card` class conventions from `index.html`)
- Debounce timer value for restaurant autocomplete (300ms matches `index.html` search bar)
- Grade border color constants (reuse `borderColor(grade)` pattern from `index.html`)
- Tab active state styling (can use `background: #667eea; color: white` vs transparent)
- File type hint text for the photo button (e.g. `accept="image/*"`)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Existing API (Phase 2 — already implemented, do not re-implement)
- `POST /api/reports` — create report (body: `restaurantId`, `grade`, `status`, `violationCodes`, `notes`)
- `GET /api/reports?status=X` — list own reports, optional status filter
- `PATCH /api/reports/{id}` — partial update (grade, status, violationCodes, notes)
- `POST /api/reports/{id}/photo` — multipart upload
- `GET /api/reports/{id}/photo` — stream photo bytes

### Existing search endpoint (Phase 3 — already implemented)
- `GET /api/restaurants/search?q=...&limit=N` — restaurant autocomplete source

### Routing and security
- `src/main/java/com/aflokkat/controller/ViewController.java` — add `/dashboard` route + ROLE_CONTROLLER check in `index()`
- `src/main/java/com/aflokkat/config/SecurityConfig.java` — add `/dashboard` to ROLE_CONTROLLER antMatchers

### Template pattern to replicate
- `src/main/resources/templates/index.html` — visual theme (CSS variables, card style, `fetchWithAuth`, `gradeBadgeHtml`, `borderColor`, search debounce pattern)
- `src/main/resources/templates/my-bookmarks.html` — simpler single-purpose page structure

### Entity shape (response from GET /api/reports)
- `src/main/java/com/aflokkat/entity/InspectionReportEntity.java` — fields: id, restaurantId, grade, status, violationCodes, notes, photoPath, createdAt
- Enriched response also includes `restaurantName` and `borough` (from MongoDB, set in ReportController)

</canonical_refs>

<code_context>
## Existing Code Insights

### Pattern reuse checklist for `dashboard.html`
- Token check: `const token = localStorage.getItem("accessToken"); if (!token) window.location.href = '/login';` — **not needed** (SecurityConfig handles it), but `fetchWithAuth()` must still be used for API calls
- `fetchWithAuth(url, options)` — copy verbatim from `index.html`
- `gradeBadgeHtml(grade)` — copy verbatim from `index.html` search section
- `borderColor(grade)` — copy verbatim from `index.html` search section
- Spinner: `<div class="spinner"></div>` + `@keyframes spin` — copy from `index.html`

### ViewController.index() modification
```java
@GetMapping("/")
public String index(Authentication auth) {
    if (auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_CONTROLLER"))) {
        return "redirect:/dashboard";
    }
    return "index";
}
```
Spring Security injects `Authentication` directly when it's a `@GetMapping` method parameter — no manual `SecurityContextHolder` call needed.

### SecurityConfig addition
Add to the chain (before the existing ROLE_CONTROLLER line for `/api/reports/**`):
```java
.antMatchers("/dashboard").hasRole("CONTROLLER")
```

### Report response shape (from ReportController)
The enriched GET /api/reports response includes:
```json
{
  "id": 12,
  "restaurantId": "41528050",
  "restaurantName": "Joe's Pizza",
  "borough": "Manhattan",
  "grade": "A",
  "status": "OPEN",
  "violationCodes": "04L,10F",
  "notes": "...",
  "photoPath": "/app/uploads/12/photo.jpg",   // null if no photo
  "createdAt": "2026-04-01T..."
}
```
`photoPath` being non-null is the signal to render a thumbnail in the card.

### Photo thumbnail src
```js
img.src = `/api/reports/${report.id}/photo?t=${Date.now()}`;
```
The `?t=` cache-buster forces the browser to re-fetch after a new upload.

</code_context>

<deferred>
## Deferred Ideas

- Drag-and-drop between Kanban status columns (deferred — Kanban layout not chosen)
- Bulk report export to PDF (deferred to v3 per REQUIREMENTS.md: PLAT-V2-02)
- Edit panel with photo upload merged (deferred — separate button chosen instead)

</deferred>

---

*Phase: 05-controller-workspace*
*Context gathered: 2026-04-01*
