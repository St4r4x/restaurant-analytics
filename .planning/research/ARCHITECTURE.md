# Architecture Patterns

**Domain:** Role-based inspection report system integrated into existing NYC restaurant analytics app
**Researched:** 2026-03-27
**Confidence:** HIGH (based on direct codebase analysis — not external sources)

---

## Recommended Architecture

The new milestone adds three orthogonal concerns on top of an existing layered Spring Boot monolith:

1. **Role-based access control** (CUSTOMER / CONTROLLER split)
2. **Controller inspection reports** (structured form data + photo attachments)
3. **Customer-facing search and map UI** (read-only, extends existing API)

All three fit cleanly into the existing controller → service → DAO/repository pattern. No new frameworks are required. The key principle is: **extend existing layers, don't add parallel ones**.

---

## Component Boundaries

### Existing Components (Unchanged)

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| `RestaurantController` | Analytics REST endpoints | `RestaurantService`, `RestaurantCacheService` |
| `InspectionController` | NYC inspection query endpoints | `RestaurantService` |
| `AuthController` | Login / register / refresh | `AuthService` |
| `UserController` | User profile, bookmarks | `UserRepository`, `BookmarkRepository` |
| `ViewController` | Thymeleaf HTML template serving | None (renders static templates) |
| `RestaurantService` | Analytics business logic, computed fields | `RestaurantDAO` |
| `AuthService` | User registration, login, token gen | `UserRepository`, `JwtUtil` |
| `RestaurantDAOImpl` | MongoDB aggregation pipelines | MongoDB (`newyork.restaurants`) |
| `UserRepository` | Spring JPA — `UserEntity` | PostgreSQL |
| `BookmarkRepository` | Spring JPA — `BookmarkEntity` | PostgreSQL |
| `RestaurantCacheService` | Redis cache-aside + sorted set | Redis |
| `JwtUtil` | Token generation + claims extraction | (stateless) |
| `JwtAuthenticationFilter` | Per-request token validation | `JwtUtil`, `SecurityConfig` |
| `SecurityConfig` | Spring Security filter chain | `JwtAuthenticationFilter` |

### New Components Required

| Component | Responsibility | Communicates With | Where |
|-----------|---------------|-------------------|-------|
| `InspectionReportEntity` | JPA entity for controller reports | PostgreSQL | `entity/` |
| `InspectionReportRepository` | Spring JPA CRUD for reports | `InspectionReportEntity` | `repository/` |
| `InspectionReportService` | Report create / read / update logic + validation | `InspectionReportRepository`, `ValidationUtil` | `service/` |
| `InspectionReportController` | REST endpoints for CRUD on reports | `InspectionReportService` | `controller/` |
| `PhotoStorageService` | Photo upload, store, retrieve | Local filesystem (Docker volume) | `service/` |
| `RestaurantSearchController` | Customer-facing search by name/address | `RestaurantService`, `RestaurantCacheService` | `controller/` |

**Role and security changes (not new components — modifications to existing ones):**

| Existing Component | Modification |
|-------------------|-------------|
| `UserEntity` | Add `role` field (`CUSTOMER` or `CONTROLLER`), add `registrationCode` used-flag |
| `AuthService` | Validate registration code on `CONTROLLER` signup, embed role in JWT claims |
| `JwtUtil` | Embed role in access token claims; expose `extractRole()` method |
| `SecurityConfig` | Configure `@PreAuthorize` rules: controller-only routes require `ROLE_CONTROLLER` |

---

## Where Each Concern Lives

### Controller Inspection Reports

**Database: PostgreSQL (JPA)**

Rationale: Reports are structured relational data owned by a specific user, need transactional guarantees (create/update/delete), and have no aggregation or geospatial query requirements. They belong alongside `UserEntity` and `BookmarkEntity` — not in MongoDB, which is for NYC Open Data restaurant records only.

**Entity shape (`InspectionReportEntity`):**

```
id              BIGSERIAL PK
controller_id   BIGINT FK → UserEntity.id
restaurant_id   VARCHAR    (camis from MongoDB — cross-store reference by ID)
created_at      TIMESTAMP
updated_at      TIMESTAMP
status          VARCHAR    (OPEN / IN_PROGRESS / CLOSED)
overall_grade   VARCHAR    (A / B / C / N)
overall_score   INTEGER
violations      TEXT       (JSON-encoded list, or separate table in v2)
notes           TEXT
photo_paths     TEXT       (JSON-encoded list of relative paths)
```

`restaurant_id` is the MongoDB `camis` / `restaurant_id` field — a string cross-store reference. The report service looks up restaurant details from MongoDB via `RestaurantService` when building responses; it does not duplicate restaurant data into PostgreSQL.

**Repository (`InspectionReportRepository`):**
Standard Spring JPA. Custom queries needed:
- `findByControllerId(Long controllerId)` — list a controller's own reports
- `findByRestaurantId(String restaurantId)` — all reports for a restaurant (admin view, future)
- `findByControllerIdAndReportId(Long controllerId, Long reportId)` — ownership check

**Service (`InspectionReportService`):**
- `createReport(Long controllerId, CreateReportRequest dto)` → validates input, saves entity, returns DTO
- `getReport(Long controllerId, Long reportId)` → ownership check, returns DTO with restaurant details injected from `RestaurantService`
- `listReports(Long controllerId)` → paginated list
- `updateReport(Long controllerId, Long reportId, UpdateReportRequest dto)` → ownership check, update fields

**Controller (`InspectionReportController`):**
- `POST /api/reports` — create (requires `ROLE_CONTROLLER`)
- `GET /api/reports` — list own reports (requires `ROLE_CONTROLLER`)
- `GET /api/reports/{id}` — get single (requires `ROLE_CONTROLLER`, ownership enforced in service)
- `PUT /api/reports/{id}` — update (requires `ROLE_CONTROLLER`)
- `POST /api/reports/{id}/photos` — upload photos (requires `ROLE_CONTROLLER`)

---

### Role-Based Access Control

**How it integrates with existing JWT/Spring Security:**

The existing `UserEntity` already has a `roles` concept (the codebase already references `@PreAuthorize("hasRole('ADMIN')")`). The extension is minimal:

1. Add `role` column to `UserEntity` (`CUSTOMER` or `CONTROLLER`). A single enum field is sufficient for v1 — no separate roles table needed.

2. `AuthService.register()` branches on registration input:
   - Normal registration path → role = `CUSTOMER`
   - Registration with valid `registrationCode` → role = `CONTROLLER`
   - Invalid code → reject with 400

3. `JwtUtil.generateAccessToken()` includes role in claims:
   ```
   claims.put("role", user.getRole())   // "CUSTOMER" or "CONTROLLER"
   ```
   The `JwtAuthenticationFilter` already extracts username and sets `UsernamePasswordAuthenticationToken`. It needs to also set authorities from the role claim so `@PreAuthorize` works.

4. `SecurityConfig` adds `@EnableMethodSecurity` (or the Spring Boot 2.6.x equivalent `@EnableGlobalMethodSecurity(prePostEnabled = true)`) if not already present. Then controllers use `@PreAuthorize("hasRole('CONTROLLER')")` on report endpoints.

5. Public endpoints (customer search, restaurant detail) remain open or require only authentication (`hasAnyRole('CUSTOMER', 'CONTROLLER')`).

**Registration code strategy:** A single hard-coded or config-property code (`controller.registration.code` in `application.properties`) is sufficient for an academic project. No database table needed.

---

### Photo Uploads

**Storage: Local filesystem inside the Docker app container, exposed via a named volume.**

Rationale: No S3 or object storage is available in the Docker Compose stack. For an academic project, local filesystem with a Docker volume is the correct choice. The volume persists photos across container restarts.

**Implementation approach:**

- `PhotoStorageService` receives `MultipartFile`, generates a UUID-based filename, writes to `/app/uploads/reports/{reportId}/`, and returns the relative path.
- Paths are stored as a JSON-encoded string array in `InspectionReportEntity.photo_paths`.
- Photos are served via a `GET /api/reports/{id}/photos/{filename}` endpoint that reads from disk and returns `application/octet-stream` or the appropriate image MIME type.
- `docker-compose.yml` adds a volume mount: `./uploads:/app/uploads` so photos are not lost on container rebuild.

**Spring Boot 2.6.x configuration needed:**

```properties
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=30MB
uploads.dir=/app/uploads
```

---

### Customer Search and Map UI

**How it connects to the existing REST API:**

Customer search is a read path entirely served by existing or minor extensions to `RestaurantService` + `RestaurantDAOImpl`.

New DAO methods needed in `RestaurantDAO` / `RestaurantDAOImpl`:
- `findByNameLike(String name, int limit)` — text search on `name` field, use MongoDB `$regex` or `$text` index
- `findByAddressLike(String address, int limit)` — `$regex` on `address.street` + `address.building`

These return `List<Restaurant>` which `RestaurantService` converts via `toView()` to include computed grade/score.

**New REST endpoint (`RestaurantSearchController` or added to `RestaurantController`):**
- `GET /api/restaurants/search?q={query}&limit=N` — calls service, cacheable in Redis by query string

**Customer UI — Thymeleaf templates:**
- `search.html` — search bar, results list with grade badge per restaurant
- `restaurant-detail.html` — full detail page (extends existing `restaurant.html` or replaces it)
- `map.html` — interactive map using Leaflet.js (already likely referenced in `inspection-map.html`)
- Served by `ViewController` — add `@GetMapping("/search")`, `@GetMapping("/restaurant/{id}")`, `@GetMapping("/map")` methods

The map uses the existing `GET /api/restaurants/heatmap` or similar endpoint that returns `List<HeatmapPoint>` (already a DTO in the codebase), feeding Leaflet markers client-side.

**Access control on customer UI:** All customer-facing pages and search API are public or require `ROLE_CUSTOMER` or `ROLE_CONTROLLER`. No controller-only data is exposed.

---

## Data Flow

### Controller Creates Inspection Report

```
POST /api/reports
  → InspectionReportController (requires ROLE_CONTROLLER)
  → Extract controllerId from JWT via SecurityContext
  → InspectionReportService.createReport(controllerId, dto)
    → ValidationUtil checks required fields
    → InspectionReportRepository.save(entity)  [PostgreSQL]
  → Return InspectionReportDto (with restaurant_id, but no restaurant details)

POST /api/reports/{id}/photos
  → InspectionReportController
  → PhotoStorageService.store(file, reportId)  [local filesystem]
  → InspectionReportService.appendPhoto(reportId, path)
  → Return updated photo list
```

### Controller Views Their Report (with restaurant context)

```
GET /api/reports/{id}
  → InspectionReportController (requires ROLE_CONTROLLER)
  → InspectionReportService.getReport(controllerId, reportId)
    → InspectionReportRepository.findById  [PostgreSQL]
    → Ownership check: report.controllerId == controllerId
    → RestaurantService.findRestaurantById(report.restaurantId)  [MongoDB via DAO]
  → Return combined DTO: report fields + restaurant name/borough/grade
```

### Customer Searches for a Restaurant

```
GET /api/restaurants/search?q=pizza+brooklyn
  → RestaurantController or RestaurantSearchController (public)
  → RestaurantCacheService.getOrLoad("search:pizza+brooklyn", supplier, typeRef)
    → If miss: RestaurantService.searchRestaurants("pizza brooklyn")
      → RestaurantDAOImpl.findByNameLike("pizza brooklyn", 20)  [MongoDB $regex]
      → toView() for each result (adds grade, score, coordinates)
  → Return List<Map> with grade badge, score, lat/lng for map markers
```

### Customer Views Restaurant Detail

```
GET /restaurant/{id}  (ViewController — Thymeleaf)
  → ViewController.restaurantDetail(id)
  → RestaurantService.findRestaurantById(id)  [MongoDB]
  → RestaurantService.toView(restaurant)  (computed fields)
  → Render restaurant-detail.html with grade, score, inspection history
  → No controller reports shown (v1: internal only)
```

---

## Suggested Build Order

Dependencies drive this order — each step unblocks the next.

**Step 1: Role infrastructure (foundational — everything depends on this)**
- Modify `UserEntity` to add `role` field
- Modify `AuthService` to set role on registration (+ registration code check)
- Modify `JwtUtil` to embed role in token claims
- Modify `JwtAuthenticationFilter` to set authorities from role claim
- Update `SecurityConfig` to enable method security
- Write auth tests for both roles

Rationale: Without roles in the token, neither `@PreAuthorize` on report endpoints nor role-gated UI pages can work.

**Step 2: Inspection report CRUD (depends on Step 1)**
- Create `InspectionReportEntity` + `InspectionReportRepository`
- Create `InspectionReportService` with create/read/update/list
- Create `InspectionReportController` with `@PreAuthorize("hasRole('CONTROLLER')")`
- Add DTOs: `CreateReportRequest`, `UpdateReportRequest`, `InspectionReportDto`
- Write service unit tests and repository integration tests

Rationale: Core feature for controllers; photo upload builds on top of this.

**Step 3: Photo upload (depends on Step 2)**
- Create `PhotoStorageService`
- Add photo upload endpoint to `InspectionReportController`
- Add volume mount to `docker-compose.yml`
- Add multipart config to `application.properties`

Rationale: Reports are usable without photos; photo upload is a separate concern.

**Step 4: Customer search (depends on Step 1, independent of Steps 2-3)**
- Add `findByNameLike` / `findByAddressLike` to `RestaurantDAO` + `RestaurantDAOImpl`
- Add `searchRestaurants()` to `RestaurantService`
- Add `GET /api/restaurants/search` endpoint
- Wire into Redis cache (key: `search:{query}`, TTL 300s — shorter TTL since results are more dynamic)
- Write DAO integration tests for search methods

**Step 5: Customer UI (depends on Step 4)**
- Add `search.html`, `restaurant-detail.html`, `map.html` Thymeleaf templates
- Add routes to `ViewController`
- Add Leaflet.js map integration in `map.html` using existing heatmap/coordinates API
- Test with both roles (CUSTOMER and CONTROLLER can both see public pages)

---

## Patterns to Follow

### Pattern 1: JPA Service Ownership Check

Every service method for controller reports must verify ownership before reading or mutating:

```java
InspectionReportEntity report = reportRepository.findById(reportId)
    .orElseThrow(() -> new IllegalArgumentException("Report not found"));
if (!report.getControllerId().equals(controllerId)) {
    throw new IllegalArgumentException("Access denied");  // 400 → caught as 403 at controller
}
```

Use `IllegalArgumentException` consistently with the existing pattern (`ValidationUtil` throws this, controllers catch it as 400). For v1 a 400 is acceptable; a proper 403 can be introduced later.

### Pattern 2: Cross-Store Reference (PostgreSQL → MongoDB)

Do not copy restaurant data into PostgreSQL. Store only `restaurant_id` (the MongoDB `camis` value). Enrich reports with restaurant details at read time:

```java
// In InspectionReportService.getReport()
InspectionReportDto dto = toDto(entity);
Restaurant restaurant = restaurantService.findRestaurantById(entity.getRestaurantId());
dto.setRestaurantName(restaurant.getName());
dto.setBorough(restaurant.getBorough());
// ...
return dto;
```

This keeps the data model clean and avoids stale restaurant data in the report store.

### Pattern 3: Cache Key Namespacing for Search

Use a distinct key namespace so search cache is invalidated independently from analytics:

```
restaurants:search:{url-encoded-query}   TTL 300s
restaurants:by-borough                   TTL 3600s  (existing)
restaurants:top                          sorted set (existing)
```

The existing `invalidateAll()` method clears `restaurants:*`, which will also clear search cache on nightly sync — correct behavior since restaurant data changes.

### Pattern 4: Thin Controllers (Existing Convention)

All new controllers follow the existing pattern: thin controller, logic in service.

```java
@PostMapping
@PreAuthorize("hasRole('CONTROLLER')")
public ResponseEntity<?> createReport(@RequestBody CreateReportRequest req,
                                      Authentication auth) {
    try {
        Long controllerId = extractUserId(auth);
        InspectionReportDto result = reportService.createReport(controllerId, req);
        return ResponseEntity.status(201).body(result);
    } catch (IllegalArgumentException e) {
        return ResponseUtil.errorResponse(400, e.getMessage());
    } catch (Exception e) {
        return ResponseUtil.errorResponse(500, "Internal error");
    }
}
```

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Storing Restaurant Data in PostgreSQL

**What goes wrong:** Copying `name`, `borough`, `cuisine` from MongoDB into the `InspectionReportEntity` to avoid cross-store lookups.
**Why bad:** Creates two authoritative sources for restaurant data. Nightly sync updates MongoDB; PostgreSQL copy becomes stale. Report shows outdated restaurant name.
**Instead:** Store only `restaurant_id` (camis), join at read time via `RestaurantService`.

### Anti-Pattern 2: Adding a New Security Layer Parallel to JwtAuthenticationFilter

**What goes wrong:** Creating a separate role-check interceptor or `HandlerInterceptor` for report endpoints instead of using Spring Security's `@PreAuthorize`.
**Why bad:** Two security layers that can contradict each other. Spring Security's filter chain already covers all requests; `@PreAuthorize` is the correct extension point within that chain.
**Instead:** Add role to JWT claims in `JwtUtil`, set `GrantedAuthority` in `JwtAuthenticationFilter`, use `@PreAuthorize` on controller methods.

### Anti-Pattern 3: Using MongoDB for Controller Reports

**What goes wrong:** Creating a `reports` MongoDB collection for inspection reports.
**Why bad:** Reports are relational (owner FK, restaurant FK, status), benefit from transactions, and have no aggregation or geospatial requirements. Adding another MongoDB collection adds complexity with no benefit.
**Instead:** PostgreSQL + JPA, alongside existing `UserEntity` and `BookmarkEntity`.

### Anti-Pattern 4: Bypassing the Service Layer in the Report Controller

**What goes wrong:** Injecting `InspectionReportRepository` directly into `InspectionReportController`.
**Why bad:** Violates existing architectural convention. Validation, ownership checks, and cross-store enrichment belong in the service layer.
**Instead:** Controller calls `InspectionReportService` exclusively, which calls the repository.

### Anti-Pattern 5: Serving Photos via a Streaming Endpoint Without Access Control

**What goes wrong:** `GET /uploads/{filename}` served as a static resource (Spring's `addResourceHandlers`) with no auth check.
**Why bad:** Any unauthenticated client can fetch any photo by guessing filenames. Reports are marked internal in v1.
**Instead:** Route photo serving through `InspectionReportController` which validates `ROLE_CONTROLLER` and report ownership before reading from disk.

---

## Scalability Considerations

This is an academic project; scalability is not a primary concern. For reference:

| Concern | Current Scale | Notes |
|---------|--------------|-------|
| Photo storage | Local Docker volume | Sufficient for academic demo; would move to object storage (S3/MinIO) in production |
| Search | MongoDB `$regex` | Unindexed regex is slow on large collections; add a text index on `name` in production |
| Reports | PostgreSQL, single schema | Scales well for hundreds/thousands of reports |
| Role check overhead | JWT claim lookup (in-memory) | Negligible; no DB call on every request |

---

## Directory Map for New Files

```
src/main/java/com/aflokkat/
├── controller/
│   ├── InspectionReportController.java    NEW — CRUD for controller reports
│   └── ViewController.java                MODIFIED — add customer UI routes
├── service/
│   ├── InspectionReportService.java       NEW — report business logic
│   └── PhotoStorageService.java           NEW — file upload/retrieve
├── entity/
│   ├── UserEntity.java                    MODIFIED — add role field
│   └── InspectionReportEntity.java        NEW — JPA entity for reports
├── repository/
│   └── InspectionReportRepository.java    NEW — Spring JPA for reports
├── dto/
│   ├── CreateReportRequest.java           NEW
│   ├── UpdateReportRequest.java           NEW
│   └── InspectionReportDto.java           NEW — response DTO (includes restaurant fields)
├── dao/
│   ├── RestaurantDAO.java                 MODIFIED — add findByNameLike, findByAddressLike
│   └── RestaurantDAOImpl.java             MODIFIED — implement search methods
├── service/
│   └── RestaurantService.java             MODIFIED — add searchRestaurants()
└── security/
    ├── JwtUtil.java                       MODIFIED — embed/extract role claim
    └── JwtAuthenticationFilter.java       MODIFIED — set GrantedAuthority from role

src/main/resources/
└── templates/
    ├── search.html                        NEW — customer restaurant search
    ├── restaurant-detail.html             NEW (or update restaurant.html)
    └── map.html                           NEW — interactive Leaflet map
```

---

## Sources

- Direct analysis of `/home/missia03/Aflokkat/big_data/quickstart-app/.planning/codebase/ARCHITECTURE.md`
- Direct analysis of `/home/missia03/Aflokkat/big_data/quickstart-app/.planning/codebase/STRUCTURE.md`
- Direct analysis of `/home/missia03/Aflokkat/big_data/quickstart-app/.planning/PROJECT.md`
- Spring Boot 2.6.x documentation (Spring Security method security, multipart upload, JPA)
- Confidence: HIGH — derived from direct codebase inspection, not external web research

---

*Research date: 2026-03-27*
