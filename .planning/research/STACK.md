# Technology Stack — Milestone Research

**Project:** Restaurant Hygiene Control App — Role-based reporting milestone
**Researched:** 2026-03-27
**Base constraint:** Spring Boot 2.6.15 / Java 11 — no framework upgrade

---

## Context: What Already Exists

The existing stack is fixed and must not be replaced:

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | Spring Boot | 2.6.15 |
| Language | Java | 11 (source/target) |
| Security | Spring Security + JWT | SB 2.6 managed (5.6.x) |
| Auth tokens | jjwt | 0.11.5 |
| ORM | Spring Data JPA / Hibernate | SB 2.6 managed |
| RDBMS | PostgreSQL | 15 |
| Document DB | mongodb-driver-sync | SB 2.6 managed |
| Cache | Redis 7 + Spring Data Redis | SB 2.6 managed |
| Templates | Thymeleaf | SB 2.6 managed |
| API docs | springdoc-openapi-ui | 1.8.0 |
| Build | Maven | 3.8 |

The role string is already stored in `UserEntity.role` (varchar) and propagated into the JWT `role` claim. `JwtAuthenticationFilter` already converts it to a `SimpleGrantedAuthority` and sets it on the `SecurityContext`. `SecurityConfig` already has `@EnableMethodSecurity`.

---

## Recommended Additions

### 1. Role-Based Access Control

**Verdict: zero new dependencies required.**

The machinery is already in place:

- `UserEntity.role` is a plain `String` — storing `"ROLE_CUSTOMER"` or `"ROLE_CONTROLLER"` (Spring's `hasRole()` expects the `ROLE_` prefix automatically added; storing it with prefix means `hasAuthority()` works unambiguously — prefer `ROLE_CUSTOMER` / `ROLE_CONTROLLER` stored literally).
- `JwtUtil.generateAccessToken(username, role)` already encodes the role claim.
- `JwtAuthenticationFilter` already reads the role and creates a `SimpleGrantedAuthority`.
- `@EnableMethodSecurity` is already on `SecurityConfig`.

**What to add in code (not dependencies):**

- Add enum or constants class `UserRole { ROLE_CUSTOMER, ROLE_CONTROLLER }` to validate values at registration.
- Add a dedicated `/api/auth/register/controller` endpoint that validates a registration code before assigning `ROLE_CONTROLLER`.
- Annotate CONTROLLER-only endpoints with `@PreAuthorize("hasRole('CONTROLLER')")` and CUSTOMER endpoints with `@PreAuthorize("hasRole('CUSTOMER')")`.
- Add a registration code property: `app.controller.registration-code=<secret>` in `application.properties` and `AppConfig`.

**Why `hasRole()` not `hasAuthority()`:** `hasRole('CONTROLLER')` checks for authority `ROLE_CONTROLLER`, which matches what `JwtAuthenticationFilter` stores. This is the standard Spring Security convention. Consistency with the `ROLE_` prefix makes the intent obvious to future readers.

**Confidence:** HIGH — verified by reading existing `JwtAuthenticationFilter`, `SecurityConfig`, `UserEntity`, `JwtUtil`.

---

### 2. Controller Inspection Report — Data Storage

**Verdict: PostgreSQL via Spring Data JPA. No new dependencies.**

Reports have a well-defined relational shape:
- One report per restaurant per controller inspection date.
- Foreign key to `users` (who filed it).
- A `restaurant_id` (MongoDB `camis` value, stored as a string reference).
- Violation list, score, grade, status, follow-up date.
- Photo references (file paths or URLs — see section 3).

The JPA layer (`BookmarkEntity`, `UserRepository`) already demonstrates the pattern. Add:

```
entity/InspectionReportEntity.java     (@Entity, @Table("inspection_reports"))
repository/InspectionReportRepository.java   (JpaRepository<InspectionReportEntity, Long>)
service/InspectionReportService.java
controller/InspectionReportController.java
dto/InspectionReportRequest.java
dto/InspectionReportResponse.java
```

**Schema additions needed:**
- `inspection_reports` table (created via `spring.jpa.hibernate.ddl-auto=update` in dev, or a Flyway migration for production rigor).
- Violations stored as a `TEXT` column (JSON array string) — avoids a separate join table for an academic project.

**Why not MongoDB for reports:** Reports are structured, relational (linked to a user, reference a restaurant by ID), need transactional guarantees, and the project constraint explicitly states "PostgreSQL for user/report metadata."

**Why not a separate violations table:** Academic scope — a TEXT JSON column is simpler and queryable enough for the reporting needs. A separate table would add join complexity for no gain at this scale.

**Confidence:** HIGH — directly from project constraints in PROJECT.md, existing JPA pattern observed in codebase.

---

### 3. File Upload — Photos on Inspection Reports

**Verdict: Spring Boot built-in `MultipartFile` + local filesystem storage in Docker volume. No new dependencies.**

Spring Boot 2.6 auto-configures multipart support via `StandardServletMultipartResolver` (Servlet 3.0 native). It is on by default; only configuration properties need setting.

**Configuration to add in `application.properties`:**

```properties
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=20MB
spring.servlet.multipart.file-size-threshold=2MB
app.upload.dir=/app/uploads
```

**Docker Compose volume addition:**

```yaml
volumes:
  - uploads_data:/app/uploads
```

This is the only infrastructure change needed.

**Controller approach:**

```java
@PostMapping(value = "/api/reports/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasRole('CONTROLLER')")
public ResponseEntity<?> uploadPhoto(@PathVariable Long id,
                                      @RequestParam("file") MultipartFile file) { ... }
```

Store the saved file path (relative to upload dir) in `InspectionReportEntity.photoPaths` (TEXT column, comma-separated or JSON array).

**Why not S3 / object storage:** Academic project, Docker Compose deployment, no cloud dependency. A local volume is correct here. S3 adds AWS dependency, IAM setup, and cost for zero benefit in this context.

**Why not store bytes in PostgreSQL (BYTEA/LOB):** Photo files in a relational DB bloat the database, hurt backup/restore performance, and prevent efficient serving via static file serving. File paths + filesystem is the standard pattern.

**Serving uploaded photos:**

Add a Spring MVC `ResourceHandler` in `WebMvcConfigurer` to serve `/uploads/**` from the `app.upload.dir` path:

```java
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/uploads/**")
            .addResourceLocations("file:" + uploadDir + "/");
}
```

No new dependency needed — this is standard Spring MVC.

**Confidence:** HIGH — Spring Boot 2.6 multipart auto-configuration is well-documented and the existing stack includes `spring-boot-starter-web` which bundles it.

---

### 4. Customer Map UI — Restaurant Map

**Verdict: Leaflet.js 1.9.x via CDN in Thymeleaf template. No Maven dependency.**

Leaflet.js is the standard open-source map library for server-rendered web applications. It is:
- Lightweight (~42KB gzipped).
- Free with OpenStreetMap tiles (free for low-traffic non-commercial/academic use, which this project is).
- Works directly in a Thymeleaf template with no build toolchain.
- Actively maintained (1.9.4 as of late 2024).

**Why not Google Maps:** Requires API key, billing account, not appropriate for academic projects.

**Why not Mapbox:** Requires API key, freemium with usage limits.

**Why not a React/Vue component:** Adding a JS framework for a single map page in a Thymeleaf monolith is over-engineering. The existing `ViewController.java` pattern serves HTML templates; a Leaflet script tag in a template is perfectly consistent with this approach.

**Template inclusion:**

```html
<!-- In the <head> section of the map Thymeleaf template -->
<link rel="stylesheet"
      href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
```

**Data source:** The existing `RestaurantDAO` / `RestaurantService` already computes lat/lng from the `Address.coord` GeoJSON field (see `RestaurantService.toView()`). A new endpoint `GET /api/restaurants/map-points?borough=X&grade=A&limit=500` returning `[{lat, lng, name, grade, restaurantId}]` feeds the map markers. The frontend Leaflet code fetches this via `fetch()` and renders markers.

**Marker clustering:** For large datasets (500+ points), use `Leaflet.markercluster` plugin via CDN alongside Leaflet. Also free, no server-side dependency.

```html
<link rel="stylesheet"
      href="https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.css" />
<link rel="stylesheet"
      href="https://unpkg.com/leaflet.markercluster@1.5.3/dist/MarkerCluster.Default.css" />
<script src="https://unpkg.com/leaflet.markercluster@1.5.3/dist/leaflet.markercluster.js"></script>
```

**Confidence:** MEDIUM — Leaflet 1.9.x version and CDN URL verified via knowledge; CDN availability confirmed via unpkg.com conventions. OpenStreetMap tile policy assessed from training data (confirmed free for low-traffic academic use).

---

### 5. Input Validation

**Verdict: Add `spring-boot-starter-validation`. One new dependency.**

The existing `ValidationUtil` class handles manual validation (`requireNonEmpty`, `requirePositive`). For the new request DTOs (`InspectionReportRequest`, `RegisterRequest` extensions), Bean Validation annotations (`@NotBlank`, `@Size`, `@Min`, `@Max`) on DTOs with `@Valid` in controller method signatures are cleaner and more idiomatic for Spring Boot.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Spring Boot 2.6 manages the version (Hibernate Validator 6.2.x, Jakarta Bean Validation 2.0). Compatible with Java 11. Uses `javax.validation.*` namespace (not `jakarta.validation.*` — the `javax.*` namespace is Spring Boot 2.x).

**Why add this now:** New DTOs for inspection reports have more fields than existing DTOs. Manual validation in `ValidationUtil` does not scale cleanly to 8+ fields. `@Valid` + `BindingResult` or `MethodArgumentNotValidException` handler is the standard Spring Boot pattern and produces structured 400 responses.

**Confidence:** HIGH — spring-boot-starter-validation is the standard Spring Boot validation mechanism, version managed by the Spring Boot 2.6 BOM.

---

## Full Dependency Delta

Only one net-new Maven dependency is needed for this milestone:

```xml
<!-- Bean Validation (Hibernate Validator) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
    <!-- version managed by Spring Boot 2.6.15 BOM -->
</dependency>
```

All other additions are:
- Configuration properties (`application.properties`)
- Infrastructure additions (`docker-compose.yml` volume)
- Frontend CDN scripts (in Thymeleaf templates)
- Code additions following existing patterns

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| RBAC | Existing Spring Security + `@PreAuthorize` | Spring Security ACL, Keycloak | Massive overkill for 2 roles. ACL is for object-level permissions. Keycloak requires a separate service. |
| Report storage | PostgreSQL JPA | MongoDB document | Reports are relational (user FK, structured fields). Project constraint is explicit. |
| Photo storage | Local filesystem volume | AWS S3, Cloudinary | Academic project, Docker Compose only. No cloud dependencies needed. |
| Photo storage | Local filesystem volume | PostgreSQL BYTEA | Files in DB bloat storage, slow backups, can't be served efficiently. |
| Map library | Leaflet.js 1.9.x CDN | Google Maps API, Mapbox | Both require paid API keys. Leaflet + OSM tiles are free and appropriate for academic use. |
| Map approach | Server endpoint + Leaflet JS | GeoJSON layer via Spring | Same outcome, `fetch()` + Leaflet is simpler to implement and debug. |
| Validation | spring-boot-starter-validation | Extend ValidationUtil | Bean Validation is idiomatic Spring Boot, handles DTOs cleanly, produces structured errors. |

---

## What NOT to Use

**Spring Data MongoDB / `@Document` annotations:** The existing codebase deliberately uses raw `mongodb-driver-sync` for aggregation pipelines. Do not introduce Spring Data MongoDB — it conflicts with the existing `MongoClientFactory` singleton pattern and would require a significant refactor for zero gain.

**Spring Session / server-side sessions:** The architecture is stateless JWT. Do not add server-side session management for roles — the role in the JWT claim is the source of truth.

**Flyway / Liquibase:** Appropriate for production-grade schema management but adds complexity for an academic project. `spring.jpa.hibernate.ddl-auto=update` is sufficient for development. If schema migrations are added, use Flyway (not Liquibase — Flyway is simpler for SQL-native teams).

**React / Vue / Angular frontend:** The existing `ViewController` + Thymeleaf pattern is the correct approach for this project scope. A SPA framework would require a separate build pipeline, CORS configuration expansion, and conflicts with the existing server-side rendering approach.

**Spring Boot 3.x upgrade:** Out of scope per project constraints. Spring Boot 2.6.15 uses `javax.*` namespace; Boot 3.x uses `jakarta.*`. Mixing is not possible.

---

## Infrastructure Changes Summary

| Component | Change | Reason |
|-----------|--------|--------|
| `docker-compose.yml` | Add `uploads_data` named volume, mount to `/app/uploads` in app container | Persist uploaded photos across container restarts |
| `application.properties` | Add `spring.servlet.multipart.*` properties, `app.upload.dir`, `app.controller.registration-code` | Configure file upload limits and controller signup secret |

---

## Sources

- Existing codebase: `SecurityConfig.java`, `JwtAuthenticationFilter.java`, `JwtUtil.java`, `UserEntity.java`, `pom.xml` — direct inspection (HIGH confidence)
- Spring Security 5.6.x method security docs: `@PreAuthorize`, `hasRole()` vs `hasAuthority()` semantics (HIGH confidence)
- Spring MVC multipart docs: `MultipartFile`, `StandardServletMultipartResolver`, `spring.servlet.multipart.*` properties (HIGH confidence)
- Spring Boot 2.6 BOM: managed versions for validation, JPA, Redis (HIGH confidence)
- Leaflet.js: version 1.9.4, CDN inclusion pattern, marker cluster plugin (MEDIUM confidence — version from training data, CDN pattern from unpkg conventions)
- OpenStreetMap tile policy: free for low-traffic non-commercial/academic use (MEDIUM confidence — policy verified as permissive for academic projects from training data)

---

*Stack research: 2026-03-27*
