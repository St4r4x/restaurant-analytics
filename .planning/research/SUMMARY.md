# Project Research Summary

**Project:** Restaurant Hygiene Control App — Role-based reporting milestone
**Domain:** Spring Boot RBAC extension + inspection report CRUD + customer-facing search/map UI
**Researched:** 2026-03-27
**Confidence:** HIGH

## Executive Summary

This milestone extends an existing, well-structured Spring Boot 2.6.15 monolith to support two distinct user roles — CONTROLLER (hygiene inspector) and CUSTOMER (public user) — built on top of NYC Open Data restaurant inspection data already loaded into MongoDB. The existing codebase has JWT auth, a `role` column on `UserEntity`, `@EnableMethodSecurity` on `SecurityConfig`, and a `JwtAuthenticationFilter` that already creates `SimpleGrantedAuthority`. This means RBAC requires zero new dependencies and minimal structural additions: the wiring already exists, it just needs to be activated and tested correctly.

The recommended implementation follows a strict dependency-first build order. Role infrastructure must come first (Phase 1), because both the controller report feature and the role-gated UI pages cannot work without roles propagated in JWT claims. Inspection report CRUD (Phase 2) builds on top of that foundation — storing reports in PostgreSQL via JPA alongside the existing `UserEntity`/`BookmarkEntity` pattern, referencing MongoDB restaurants by `camis` ID only (never duplicating restaurant data). Photo upload extends Phase 2 as a separable concern. Customer-facing search and map UI (Phase 3) depends on role infrastructure but is otherwise orthogonal to report CRUD and can be parallelized once Phase 1 is done.

The key risks in this milestone are all security-related. The most critical: `anyRequest().permitAll()` in `SecurityConfig` means `@PreAuthorize` alone is not sufficient — URL-level route guards must be added in parallel. The second critical risk is the `ROLE_` prefix convention in Spring Security; mixing `hasRole()` and `hasAuthority()` causes silent authorization failures. A third critical risk is photo persistence: files stored inside the Docker container without a named volume are lost on every rebuild. All three are preventable with explicit test coverage and the mitigations documented in PITFALLS.md.

---

## Key Findings

### Recommended Stack

The stack is entirely fixed by existing project constraints (Spring Boot 2.6.15, Java 11). No framework upgrades are possible or needed. Only one net-new Maven dependency is required: `spring-boot-starter-validation` for Bean Validation on new request DTOs. Frontend map library (Leaflet.js 1.9.4 + Leaflet.markercluster) is added via CDN in Thymeleaf templates — no Maven dependency.

**Core technologies:**
- Spring Security 5.6.x + `@PreAuthorize` — RBAC with zero new code structure; `@EnableMethodSecurity` already present
- PostgreSQL + Spring JPA — inspection report storage; relational shape, owner FK, transactional; same pattern as `BookmarkEntity`
- MongoDB (raw `mongodb-driver-sync`) — restaurant/inspection read path only; no new collections needed for this milestone
- Redis 7 (`RestaurantCacheService`) — extend cache-aside pattern to search results with 300s TTL
- `spring-boot-starter-validation` — Bean Validation (`@NotBlank`, `@Size`, `@Valid`) for new report DTOs
- Leaflet.js 1.9.4 (CDN) — map UI in Thymeleaf templates; free with OSM tiles; no build toolchain needed
- `MultipartFile` (Spring Boot built-in) — photo upload; no S3 or external dependency; Docker named volume for persistence

**Critical version note:** Spring Boot 2.6 uses `javax.validation.*` namespace, not `jakarta.validation.*`. Do not upgrade to Spring Boot 3.x (out of scope; would require full namespace migration).

### Expected Features

**Must have — CONTROLLER role:**
- Secure registration via registration code (no controller role without this gate)
- `@PreAuthorize("hasRole('CONTROLLER')")` enforced at both URL and method level
- Inspection report CRUD: `POST /api/reports`, `GET /api/reports/my`, `GET /api/reports/{id}`, `PUT /api/reports/{id}`
- Report fields: `restaurantId`, `violationCodes`, `score`, `grade`, `status` (OPEN/IN_PROGRESS/CLOSED), `notes`
- At-risk restaurant list access (repoint existing endpoint from ADMIN to CONTROLLER)

**Must have — CUSTOMER role:**
- Open registration with default `CUSTOMER` role (no code required)
- `GET /api/restaurants/search?q=` — name/address search (new endpoint, new DAO methods)
- Restaurant detail page with prominent grade display + inspection history timeline
- Grade trend indicator (computed from existing `grades` array — no new data needed)
- Map page with grade-based marker colors

**Should have (differentiators):**
- Pre-filled restaurant context when creating a controller report (fetch from MongoDB at report creation)
- Filter own reports by status/restaurant (`?status=OPEN&restaurantId=X`)
- Cuisine-type + borough filter on map UI
- Borough-level hygiene summary widget surfaced in customer dashboard

**Defer to v2+:**
- Photo attachments on reports (file storage adds complexity; not table stakes for v1 MVP)
- "Similar restaurants nearby" geospatial query
- Email/SMS notifications for grade changes
- Publishing reports to external systems (NYC Open Data API is read-only anyway)

### Architecture Approach

The milestone extends the existing controller → service → DAO/repository layered monolith without adding parallel structures. Three orthogonal concerns each map cleanly to existing patterns: RBAC extends the JWT/Spring Security wiring already in place; inspection reports follow the `BookmarkEntity` JPA pattern; customer search extends `RestaurantDAO` with two new methods. The cross-store design principle is strict: PostgreSQL reports reference MongoDB restaurants by `camis` ID only — restaurant details are enriched at read time via `RestaurantService`, never copied into PostgreSQL.

**New components:**
1. `InspectionReportEntity` + `InspectionReportRepository` — JPA entity and Spring Data repository for controller reports in PostgreSQL
2. `InspectionReportService` — report CRUD with ownership checks and cross-store restaurant enrichment
3. `InspectionReportController` — REST endpoints gated by `@PreAuthorize("hasRole('CONTROLLER')")`
4. `PhotoStorageService` — `MultipartFile` to local filesystem; paths stored as JSON text in entity
5. `RestaurantDAO.findByNameLike` / `findByAddressLike` — MongoDB `$regex` search methods, wired into `RestaurantService.searchRestaurants()`

**Modified components:**
- `UserEntity` — add `role` field (`ROLE_CUSTOMER` / `ROLE_CONTROLLER`)
- `AuthService` — validate registration code, set role atomically inside `@Transactional`
- `JwtUtil` — embed/extract `role` claim
- `JwtAuthenticationFilter` — set `GrantedAuthority` from role claim
- `SecurityConfig` — add explicit `antMatchers` URL rules; CORS lockdown
- `ViewController` — add customer UI routes (`/search`, `/restaurant/{id}`, `/map`)

### Critical Pitfalls

1. **`anyRequest().permitAll()` still active after adding `@PreAuthorize`** — method security via AOP proxy does not protect HTTP-level access; add `.antMatchers("/api/reports/**").hasRole("CONTROLLER")` to `SecurityConfig.filterChain()` as a first-line defense. Test: send CUSTOMER JWT to a CONTROLLER endpoint and assert HTTP 403.

2. **`ROLE_` prefix mismatch — `hasRole()` vs `hasAuthority()` mixed usage** — `hasRole('CONTROLLER')` checks for `ROLE_CONTROLLER`; `hasAuthority('CONTROLLER')` checks for literal `CONTROLLER`. Pick one convention (recommend: store `ROLE_CONTROLLER` in DB, always use `hasRole()`) and document it at the top of `SecurityConfig`. Silent 403 failures result from mixing the two.

3. **Photo files lost on container restart** — `MultipartFile.transferTo()` to a container path without a named Docker volume means all photos vanish on `docker compose up --build`. Add `uploads_data:/app/uploads` named volume to `docker-compose.yml` before any photo is ever uploaded in testing.

4. **Race condition in controller registration: save-then-promote pattern** — calling `userRepository.save()` before the registration code is validated leaves orphaned `ROLE_USER` rows if validation fails. Validate the code first, then save with the correct role, all inside a single `@Transactional` service method.

5. **Refresh token has no revocation mechanism** — stolen refresh tokens are valid 7 days with no server-side invalidation. For academic scope: store a hash of the refresh token in the `users` table; validate the hash on every refresh; delete on logout. This is a one-column addition.

---

## Implications for Roadmap

Based on combined research, the dependency graph is unambiguous. Role infrastructure is the sole blocking dependency for all other work. Customer search and controller report CRUD can proceed in parallel after Phase 1.

### Phase 1: Role Infrastructure and Security Foundation

**Rationale:** Every other feature in this milestone depends on JWT claims containing a valid role and URL-level access control being enforced. Building this first unblocks all other phases and is the highest-risk area (security correctness must be test-verified before any feature-level code is written on top of it). Existing security issues (CORS wildcard, `anyRequest().permitAll()`) become critical once CONTROLLER accounts exist — fix them here.

**Delivers:**
- Registration flow splits into CUSTOMER (open) and CONTROLLER (registration-code gated)
- JWT access tokens carry `role` claim; `JwtAuthenticationFilter` sets `GrantedAuthority`
- `SecurityConfig` has explicit URL guards for `/api/reports/**`; CORS restricted to known origins
- Refresh token hash stored and validated in DB

**Addresses features from FEATURES.md:**
- Secure controller registration via registration code
- Open customer registration defaulting to `ROLE_CUSTOMER`
- Role-based route protection

**Avoids pitfalls:**
- Pitfall 1 (permitAll bypass) — URL-level guards added
- Pitfall 2 (register race condition) — atomic `@Transactional` registration
- Pitfall 4 (stale refresh token) — token hash in DB
- Pitfall 5 (ROLE_ prefix) — convention chosen and documented
- Pitfall 13 (signup code in plaintext) — env var injection via Docker Compose
- Pitfall 14 (CORS wildcard) — explicit origin allowlist

**Research flag:** Standard Spring Security patterns; no phase research needed.

---

### Phase 2: Controller Inspection Report CRUD

**Rationale:** Core deliverable for the CONTROLLER role. Depends on Phase 1 (role in JWT needed for `@PreAuthorize` to work). Photo upload is a separable sub-concern within this phase — implement basic CRUD first, then add photo upload as the final step so report functionality is testable before introducing file system concerns.

**Delivers:**
- `InspectionReportEntity` schema auto-created in PostgreSQL via `ddl-auto=update`
- REST API: `POST /api/reports`, `GET /api/reports/my`, `GET /api/reports/{id}`, `PUT /api/reports/{id}`
- Report status state machine: OPEN → IN_PROGRESS → CLOSED
- Cross-store enrichment: report response includes restaurant name/borough pulled from MongoDB at read time
- `POST /api/reports/{id}/photos` with Docker named volume for persistence

**Uses from STACK.md:**
- Spring JPA + PostgreSQL (no new dependency — same pattern as `BookmarkEntity`)
- `spring-boot-starter-validation` (only net-new Maven dependency)
- `MultipartFile` (Spring Boot built-in multipart support)

**Implements architecture components:**
- `InspectionReportEntity`, `InspectionReportRepository`, `InspectionReportService`, `InspectionReportController`, `PhotoStorageService`

**Avoids pitfalls:**
- Pitfall 3 (file loss) — named Docker volume added before first upload
- Pitfall 8 (MIME spoofing) — magic byte validation + UUID filenames
- Pitfall 9 (cross-owner data leak) — all queries scoped to authenticated `controllerId`
- Pitfall 10 (unguarded Thymeleaf routes) — controller dashboard routes added to `SecurityConfig`
- Pitfall 11 (MongoDB codec) — reports stay in PostgreSQL; no new MongoDB collections
- Pitfall 12 (413 not JSON) — `MaxUploadSizeExceededException` handler in `@ControllerAdvice`

**Research flag:** Standard Spring JPA CRUD and file upload patterns; no phase research needed.

---

### Phase 3: Customer Search and Map UI

**Rationale:** Depends on Phase 1 (roles needed to gate customer-only vs public routes) but is fully independent of Phase 2 (no shared code with report CRUD). Can be developed in parallel with Phase 2 after Phase 1 is complete. All data already exists in MongoDB — this phase is primarily new read paths and Thymeleaf templates.

**Delivers:**
- `GET /api/restaurants/search?q=&limit=N` — name/address search backed by MongoDB `$regex`
- Redis cache for search results (key: `restaurants:search:{query}`, TTL 300s)
- Restaurant detail page: grade badge, inspection history timeline, violation summary (critical vs non-critical)
- Grade trend indicator computed from existing `grades` array
- Leaflet.js interactive map with grade-colored markers and `Leaflet.markercluster` for 27K-restaurant dataset
- Thymeleaf templates: `search.html`, `restaurant-detail.html`, `map.html`

**Uses from STACK.md:**
- Leaflet.js 1.9.4 + Leaflet.markercluster 1.5.3 (CDN only — no Maven dependency)
- MongoDB `$regex` index on `name` and `address.street`
- Existing `RestaurantCacheService`, `HeatmapPoint` DTO, `Address.coord` GeoJSON field

**Avoids pitfalls:**
- Pitfall 6 (mixed content HTTP tiles) — always use `https://` OSM tile URLs
- Pitfall 7 (27K markers crash browser) — `Leaflet.markercluster` plugin + viewport-bounded API limit

**Research flag:** Leaflet.js integration is standard and well-documented; MongoDB `$regex` text search is straightforward. No phase research needed. One gap: verify whether a MongoDB text index already exists on `name`; if not, it should be created for search performance (see Gaps below).

---

### Phase 4: Integration Polish and Test Coverage

**Rationale:** After functional phases are complete, targeted testing closes the gaps most likely to cause production issues: cross-role security boundaries, report ownership invariants, photo persistence, and end-to-end customer flows.

**Delivers:**
- Security integration tests: CUSTOMER JWT against CONTROLLER endpoints (assert 403), anonymous against authenticated endpoints (assert 401)
- Report ownership tests: controller B cannot read controller A's reports
- Photo persistence test: verify file survives container restart
- Customer search tests: DAO integration tests for `findByNameLike` / `findByAddressLike`
- Rate limiting on auth endpoints (Spring Security `RateLimitFilter` or equivalent) — existing codebase concern escalated by RBAC

**Avoids pitfalls:**
- Pitfall 1 (detectability) — explicit 403 tests catch URL-guard gaps
- Pitfall 9 (detectability) — cross-owner access test

**Research flag:** Testing patterns are well-established in the existing test suite (JUnit 4 + Mockito). No phase research needed.

---

### Phase Ordering Rationale

- **Security before features:** Phase 1 must precede everything. An incomplete RBAC foundation means feature code written on top of it inherits the security gap.
- **Core CONTROLLER feature before UI polish:** Phase 2 delivers the primary new functionality; Phase 3 delivers customer-facing read paths. Both can proceed in parallel after Phase 1.
- **Photo upload last in Phase 2:** Basic CRUD is independently useful and testable. Photo upload adds infrastructure complexity (volume, MIME checks, error handling) that should not block the core feature.
- **Pitfall-driven ordering:** The three critical pitfalls (URL-guard bypass, file persistence, registration race condition) are all addressed in Phases 1-2, before any UI or polish work begins.

### Research Flags

Phases with standard patterns (skip `/gsd:research-phase`):
- **Phase 1 — Role Infrastructure:** Spring Security 5.6 RBAC is extensively documented; existing codebase already has 80% of the wiring. Standard patterns apply.
- **Phase 2 — Report CRUD:** Spring JPA CRUD + `MultipartFile` are well-established patterns; `BookmarkEntity` in the codebase is a direct analogue.
- **Phase 3 — Customer UI:** Leaflet.js + Thymeleaf integration is straightforward; MongoDB `$regex` search is documented.
- **Phase 4 — Integration/Polish:** Standard JUnit 4 + Mockito testing; existing test suite provides patterns.

No phases require `/gsd:research-phase` before planning — all research was conducted upfront and confidence is HIGH across all areas.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Directly verified by reading existing `pom.xml`, `SecurityConfig`, `JwtAuthenticationFilter`, `UserEntity`, `application.properties`; only Leaflet CDN version is MEDIUM |
| Features | HIGH | Grounded in existing domain model (`Restaurant.grades`, `Grade.violationCode`, `Address.coord`), existing endpoints, and stated project requirements in PROJECT.md |
| Architecture | HIGH | Direct codebase analysis of `ARCHITECTURE.md` and `STRUCTURE.md`; patterns are explicit analogues of existing code |
| Pitfalls | HIGH | Spring Security method security AOP limitations, Docker volume ephemerality, Spring multipart defaults are all HIGH confidence; Leaflet marker performance threshold is MEDIUM |

**Overall confidence:** HIGH

### Gaps to Address

- **MongoDB text index on `name`:** PITFALLS.md notes `RestaurantDAOImpl` calls `ensureIndexes()`. Verify whether a text index or `$text` index already exists on the `name` field before implementing search. If it does not exist, `$regex` search will perform a full collection scan on 27K documents — acceptable for dev/demo but should have an index added.
- **Leaflet CDN version pinning:** Leaflet 1.9.4 and Leaflet.markercluster 1.5.3 are from training data (MEDIUM confidence). Before implementation, verify the latest stable versions at `unpkg.com/leaflet` and `unpkg.com/leaflet.markercluster` to ensure CDN URLs are correct.
- **Registration code rotation:** For academic scope, a Docker Compose env var for `CONTROLLER_SIGNUP_CODE` is sufficient. However, once deployed, rotating the code requires a container restart. This is documented as acceptable scope for v1.
- **`ddl-auto=update` for new `inspection_reports` table:** Suitable for development and academic demo. If the project ever moves toward a graded production deployment, Flyway migrations should replace `ddl-auto=update`.

---

## Sources

### Primary (HIGH confidence)
- Existing codebase: `SecurityConfig.java`, `JwtAuthenticationFilter.java`, `JwtUtil.java`, `UserEntity.java`, `BookmarkEntity.java`, `pom.xml`, `RestaurantDAOImpl.java`, `ViewController.java` — direct file inspection
- `.planning/PROJECT.md` — stated project requirements and constraints
- `.planning/codebase/ARCHITECTURE.md`, `.planning/codebase/STRUCTURE.md` — existing architectural documentation
- Spring Security 5.6 documentation — `@PreAuthorize`, `hasRole()` vs `hasAuthority()`, method security AOP proxies
- Spring Boot 2.6 BOM — managed versions for validation, JPA, Redis, multipart
- Spring MVC multipart auto-configuration — `StandardServletMultipartResolver`, `MultipartFile`, default size limits

### Secondary (MEDIUM confidence)
- Leaflet.js 1.9.4 CDN inclusion pattern and marker performance benchmarks — training data + unpkg.com conventions
- OpenStreetMap tile policy (free for low-traffic academic use) — training data assessment
- Leaflet.markercluster 1.5.3 — community consensus on clustering threshold (~1000 markers without clustering)

### Tertiary (LOW confidence)
- None — all research findings are HIGH or MEDIUM confidence

---
*Research completed: 2026-03-27*
*Ready for roadmap: yes*
