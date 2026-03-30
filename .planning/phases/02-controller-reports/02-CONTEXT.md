# Phase 2: Controller Reports - Context

**Gathered:** 2026-03-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Controllers can create, view, edit, and attach a photo to internal inspection reports scoped to a specific restaurant. Reports are stored in PostgreSQL (JPA). Customers cannot see these reports — they are internal only. Photo storage uses a local Docker volume. No external data is written to the NYC Open Data API.

</domain>

<decisions>
## Implementation Decisions

### Violation representation
- `violationCodes` stored as a comma-separated `TEXT` column (e.g. `"04L,10F,09C"`) on the report entity — no extra table
- `notes` stored as a separate free-text `TEXT` column for the controller's written observations
- Both fields are optional at creation, editable via PATCH
- `grade` stored as a Java enum `Grade {A, B, C, F}`, persisted with `@Enumerated(EnumType.STRING)`
- `status` stored as a Java enum `Status {OPEN, IN_PROGRESS, RESOLVED}`, persisted with `@Enumerated(EnumType.STRING)`

### Photo storage and serving
- Photos saved to `/app/uploads/{reportId}/{filename}` inside the container
- `/app/uploads` is mounted as a named Docker volume so files survive `docker compose down && docker compose up`
- Upload path configurable via `app.uploads.dir` property (default `/app/uploads`)
- `GET /api/reports/{id}/photo` streams the file bytes back to the client (not a static resource URL)
- `photoPath` stored as a single `TEXT` column on the report entity — one photo per report in v1
- A second upload overwrites the first (no history needed)

### Report update method
- `PATCH /api/reports/{id}` — partial update; only fields present in the request body are updated, null fields are left unchanged
- Editable fields: `grade`, `status`, `violationCodes`, `notes`
- If the authenticated controller is not the report owner → HTTP 403 `{"status": "error", "message": "Forbidden"}`
- Restaurant link (`restaurantId`) cannot be changed after creation

### List response enrichment
- `GET /api/reports` returns the authenticated controller's own reports only
- Each report in the list is enriched with `restaurantName` and `borough` fetched from MongoDB (same as POST response)
- Filter: `?status=OPEN|IN_PROGRESS|RESOLVED` (optional query param); no status param → return all reports for that controller
- Repository method: `findByUserId(Long userId)` and `findByUserIdAndStatus(Long userId, Status status)` — mirrors `BookmarkRepository` pattern

### Claude's Discretion
- Exact `InspectionReportEntity` column names and nullable constraints (follow `BookmarkEntity` conventions)
- File naming on disk (timestamp + original filename is a safe default)
- `multipart/form-data` handling details for photo upload endpoint
- Exact Hibernate DDL for enums and text columns

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements
- `.planning/REQUIREMENTS.md` — CTRL-01 through CTRL-04 (all Phase 2 requirements)
- `.planning/ROADMAP.md` — Phase 2 goal and success criteria (4 success criteria define exact endpoint behavior)

### Existing JPA layer (template for report entity + repo)
- `src/main/java/com/aflokkat/entity/BookmarkEntity.java` — `@ManyToOne(user)`, `@JoinColumn`, `@Column` patterns to replicate
- `src/main/java/com/aflokkat/repository/BookmarkRepository.java` — `findByUserId` pattern; `ReportRepository` must follow same conventions
- `src/main/java/com/aflokkat/entity/UserEntity.java` — `id` field type (Long) used in foreign key join

### Existing controller pattern (template for ReportController)
- `src/main/java/com/aflokkat/controller/UserController.java` — `getCurrentUser()` helper, `ResponseUtil.errorResponse()`, response shape `{"status":"success","data":...}`
- `src/main/java/com/aflokkat/util/ResponseUtil.java` — reuse for all report error responses

### Security config (verify ROLE_CONTROLLER guard is in place)
- `src/main/java/com/aflokkat/config/SecurityConfig.java` — `/api/reports/**` must require `ROLE_CONTROLLER`

### Config pattern (for uploads.dir property)
- `src/main/java/com/aflokkat/config/AppConfig.java` — env var resolution pattern to follow for `APP_UPLOADS_DIR`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BookmarkEntity`: direct template — same `@ManyToOne(fetch = FetchType.LAZY)` + `@JoinColumn(name = "user_id")` pattern for `InspectionReportEntity`
- `BookmarkRepository.findByUserId(Long userId)`: exact pattern for `ReportRepository.findByUserId` and `findByUserIdAndStatus`
- `UserController.getCurrentUser()`: replicate this helper in `ReportController` to get the authenticated `UserEntity`
- `ResponseUtil.errorResponse(e)`: reuse in all catch blocks in `ReportController`

### Established Patterns
- Response shape: `{"status": "success", "data": ..., "count": ...}` — keep consistent across all report endpoints
- Error shape: `{"status": "error", "message": "..."}` — used by `AuthController.errorResponse()` and `ResponseUtil`
- Spring JPA auto DDL: Hibernate creates tables on startup — no manual migration script needed for the new `inspection_reports` table

### Integration Points
- `RestaurantDAO.findById(String id)` (or equivalent) — needed to enrich POST and GET responses with `restaurantName` + `borough` from MongoDB
- `UserRepository.findByUsername(String username)` — used by `getCurrentUser()` to load the `UserEntity` from the JWT principal
- `docker-compose.yml` — must add a named volume mount for `/app/uploads` and declare the volume

</code_context>

<specifics>
## Specific Ideas

- No specific UI/UX references — this phase is pure REST API (no Thymeleaf templates)
- The photo streaming endpoint should set the correct `Content-Type` header based on the uploaded file's MIME type

</specifics>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-controller-reports*
*Context gathered: 2026-03-30*
