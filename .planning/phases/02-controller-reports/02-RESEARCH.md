# Phase 2: Controller Reports - Research

**Researched:** 2026-03-30
**Domain:** Spring Boot JPA entity + REST CRUD + multipart file upload (no new libraries)
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- `violationCodes` stored as a comma-separated `TEXT` column (e.g. `"04L,10F,09C"`) on the report entity — no extra table
- `notes` stored as a separate free-text `TEXT` column
- Both fields are optional at creation, editable via PATCH
- `grade` stored as Java enum `Grade {A, B, C, F}`, persisted with `@Enumerated(EnumType.STRING)`
- `status` stored as Java enum `Status {OPEN, IN_PROGRESS, RESOLVED}`, persisted with `@Enumerated(EnumType.STRING)`
- Photos saved to `/app/uploads/{reportId}/{filename}` inside the container
- `/app/uploads` mounted as a named Docker volume — files survive `docker compose down && up`
- Upload path configurable via `app.uploads.dir` property (default `/app/uploads`)
- `GET /api/reports/{id}/photo` streams file bytes back (not a static resource URL)
- `photoPath` stored as a single `TEXT` column — one photo per report; second upload overwrites
- `PATCH /api/reports/{id}` — partial update; only fields present in the request body are updated
- Editable fields: `grade`, `status`, `violationCodes`, `notes`
- Non-owner PATCH attempt → HTTP 403 `{"status": "error", "message": "Forbidden"}`
- `restaurantId` cannot be changed after creation
- `GET /api/reports` returns authenticated controller's own reports only
- Each list item enriched with `restaurantName` and `borough` from MongoDB
- Filter: `?status=OPEN|IN_PROGRESS|RESOLVED` (optional); absent → all reports for that controller
- Repository methods: `findByUserId(Long userId)` and `findByUserIdAndStatus(Long userId, Status status)`

### Claude's Discretion
- Exact `InspectionReportEntity` column names and nullable constraints (follow `BookmarkEntity` conventions)
- File naming on disk (timestamp + original filename is a safe default)
- `multipart/form-data` handling details for photo upload endpoint
- Exact Hibernate DDL for enums and text columns

### Deferred Ideas (OUT OF SCOPE)
- None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CTRL-01 | Controller can create an inspection report for a restaurant (violations, grade A/B/C/F, status open/in-progress/resolved) | POST endpoint + `InspectionReportEntity` JPA entity + `ReportRepository` |
| CTRL-02 | Controller can view a list of their own submitted inspection reports | GET endpoint with `findByUserId` / `findByUserIdAndStatus`, MongoDB enrichment |
| CTRL-03 | Controller can edit their own inspection reports | PATCH endpoint with ownership check (403 on mismatch) |
| CTRL-04 | Controller can attach a photo to an inspection report | Multipart upload endpoint + named Docker volume |
</phase_requirements>

---

## Summary

Phase 2 adds four REST endpoints under `/api/reports/**` secured exclusively to `ROLE_CONTROLLER`. The
security guard is already in place in `SecurityConfig` (`.antMatchers("/api/reports/**").hasRole("CONTROLLER")`).
All implementation relies on libraries already in the project: Spring Data JPA (entity + repository),
Spring Web (`@RequestParam MultipartFile`), and the existing `RestaurantDAO.findByRestaurantId()` for MongoDB
enrichment. No new Maven dependencies are needed.

The main engineering decisions are already locked: a single `InspectionReportEntity` table in PostgreSQL
carries the report data (enums as strings, commas-separated violation codes, nullable text columns), and a
named Docker volume (`uploads_data:/app/uploads`) persists photo files across container restarts. The photo
endpoint streams bytes directly with `ResponseEntity<Resource>` using `UrlResource` — no static serving config.

`AppConfig` resolves env vars by uppercasing and replacing dots with underscores, so adding
`app.uploads.dir` with a default of `/app/uploads` will automatically be overridable via
`APP_UPLOADS_DIR` in `docker-compose.yml`. The `BookmarkEntity` + `BookmarkRepository` pair is the
direct structural template for the new entity and repository; `UserController.getCurrentUser()` is the
direct template for the controller's auth helper.

**Primary recommendation:** Build exactly four focused plans — (1) entity + repository, (2) POST + GET
endpoints, (3) PATCH endpoint, (4) photo upload + Docker volume — in that order, as each plan's output
is consumed by the next.

---

## Standard Stack

### Core (already in pom.xml — no additions needed)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-data-jpa | 2.6.15 (BOM) | JPA entity + Spring Data repository | Already used for `BookmarkEntity`/`UserEntity` |
| spring-boot-starter-web | 2.6.15 (BOM) | REST endpoints + `MultipartFile` | Already used by all controllers |
| postgresql | 2.6.15 (BOM) | JDBC driver for PostgreSQL | Already powers `users`/`bookmarks` tables |
| mongodb-driver-sync | 2.6.15 (BOM) | MongoDB read for name/borough enrichment | Already used by `RestaurantDAOImpl` |

### No New Dependencies
All capabilities required for this phase (JPA entities, multipart file upload, file streaming with
`UrlResource`) are provided by `spring-boot-starter-web` and `spring-boot-starter-data-jpa`, which are
already on the classpath. The `pom.xml` does NOT need to change.

### Spring Boot 2.6.15 `MultipartFile` — built-in defaults
Spring Boot autoconfigures multipart support by default:
- Max file size: 1 MB (configurable via `spring.servlet.multipart.max-file-size`)
- Max request size: 10 MB (configurable via `spring.servlet.multipart.max-request-size`)
For academic scope, defaults are sufficient. If photos are larger, add to `application.properties`:
```properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

---

## Architecture Patterns

### Recommended Project Structure additions
```
com.aflokkat/
├── entity/
│   └── InspectionReportEntity.java   # new — mirrors BookmarkEntity structure
├── repository/
│   └── ReportRepository.java         # new — mirrors BookmarkRepository
├── controller/
│   └── ReportController.java         # new — mirrors UserController pattern
└── dto/
    └── ReportRequest.java            # new — POST/PATCH request body
```

### Pattern 1: JPA Entity with Enum columns (mirrors BookmarkEntity)

**What:** `InspectionReportEntity` uses the same `@ManyToOne(fetch=FetchType.LAZY)` + `@JoinColumn`
pattern as `BookmarkEntity`. Enums are stored as strings via `@Enumerated(EnumType.STRING)`.

**When to use:** Any PostgreSQL entity that owns a foreign key to `users`.

**Example:**
```java
// Template source: src/main/java/com/aflokkat/entity/BookmarkEntity.java
@Entity
@Table(name = "inspection_reports")
public class InspectionReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "restaurant_id", nullable = false)
    private String restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Grade grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(columnDefinition = "TEXT")
    private String violationCodes;   // comma-separated, nullable

    @Column(columnDefinition = "TEXT")
    private String notes;            // nullable

    @Column(columnDefinition = "TEXT")
    private String photoPath;        // nullable — set by upload endpoint

    @Column(name = "created_at")
    private Date createdAt = new Date();

    @Column(name = "updated_at")
    private Date updatedAt = new Date();

    // ... getters / setters
}
```

The two enums live alongside the entity:
```java
public enum Grade  { A, B, C, F }
public enum Status { OPEN, IN_PROGRESS, RESOLVED }
```

Hibernate auto-DDL (`spring.jpa.hibernate.ddl-auto=update`) will create the `inspection_reports` table
on first startup — no SQL migration script is needed.

### Pattern 2: Spring Data Repository with derived queries (mirrors BookmarkRepository)

**What:** `ReportRepository` extends `JpaRepository<InspectionReportEntity, Long>` and declares two
derived-query methods to filter by owner and optionally by status.

**Example:**
```java
// Template source: src/main/java/com/aflokkat/repository/BookmarkRepository.java
@Repository
public interface ReportRepository extends JpaRepository<InspectionReportEntity, Long> {
    List<InspectionReportEntity> findByUserId(Long userId);
    List<InspectionReportEntity> findByUserIdAndStatus(Long userId, Status status);
}
```

Spring Data JPA generates both queries at startup via the naming convention — no `@Query` annotation
needed.

### Pattern 3: `getCurrentUser()` helper in ReportController (mirrors UserController)

**What:** Extract the authenticated username from `SecurityContextHolder`, load the `UserEntity` from
`UserRepository`. Copy verbatim from `UserController.getCurrentUser()`.

**Example:**
```java
// Template source: src/main/java/com/aflokkat/controller/UserController.java
private UserEntity getCurrentUser() {
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    return userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
}
```

### Pattern 4: Ownership check → HTTP 403

**What:** After loading the report from the repository, compare `report.getUser().getId()` against
`currentUser.getId()`. On mismatch, return HTTP 403 manually (do not throw — `ResponseUtil.errorResponse`
maps `IllegalArgumentException` to 400, not 403).

**Example:**
```java
if (!report.getUser().getId().equals(currentUser.getId())) {
    Map<String, Object> body = new HashMap<>();
    body.put("status", "error");
    body.put("message", "Forbidden");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
}
```

### Pattern 5: Photo upload with `MultipartFile` + `UrlResource` streaming

**What:** `POST /api/reports/{id}/photo` accepts `multipart/form-data` with a `file` part. Saves to
`{uploadsDir}/{reportId}/{timestamp}_{originalFilename}`. Updates `photoPath` on the entity.
`GET /api/reports/{id}/photo` loads the file as a `UrlResource` and streams it back with correct
`Content-Type`.

**Example — upload:**
```java
@PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<Map<String, Object>> uploadPhoto(
        @PathVariable Long id,
        @RequestParam("file") MultipartFile file) {
    // ... ownership check, save file, update entity
}
```

**Example — stream:**
```java
@GetMapping("/{id}/photo")
public ResponseEntity<Resource> getPhoto(@PathVariable Long id) {
    // ...
    Path filePath = Paths.get(report.getPhotoPath());
    Resource resource = new UrlResource(filePath.toUri());
    String contentType = Files.probeContentType(filePath);  // java.nio.file.Files
    return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
            .body(resource);
}
```

`Files.probeContentType()` is standard Java NIO — no extra library needed.

### Pattern 6: MongoDB enrichment in list/create responses

**What:** After saving or listing reports, call `restaurantDAO.findByRestaurantId(report.getRestaurantId())`
to pull `name` and `borough` from MongoDB. Assemble a response map with both JPA and MongoDB fields.

**Example:**
```java
// Template: UserController.getBookmarks() → restaurantDAO.findByIds()
Restaurant r = restaurantDAO.findByRestaurantId(entity.getRestaurantId());
Map<String, Object> data = new HashMap<>();
data.put("id", entity.getId());
data.put("restaurantId", entity.getRestaurantId());
data.put("restaurantName", r != null ? r.getName() : null);
data.put("borough",        r != null ? r.getBorough() : null);
data.put("grade",          entity.getGrade());
data.put("status",         entity.getStatus());
data.put("violationCodes", entity.getViolationCodes());
data.put("notes",          entity.getNotes());
data.put("photoPath",      entity.getPhotoPath());
data.put("createdAt",      entity.getCreatedAt());
data.put("updatedAt",      entity.getUpdatedAt());
```

### Pattern 7: `app.uploads.dir` property via AppConfig

**What:** `AppConfig.getProperty()` resolves `app.uploads.dir` as env var `APP_UPLOADS_DIR` first,
then `.env`, then `application.properties`. Add a static getter that mirrors the existing pattern.

**Example:**
```java
// Template: AppConfig.getRedisHost() / getRedisPort()
public static String getUploadsDir() {
    return getProperty("app.uploads.dir", "/app/uploads");
}
```

In `application.properties`:
```properties
app.uploads.dir=/app/uploads
```

In `docker-compose.yml` (app service):
```yaml
environment:
  APP_UPLOADS_DIR: /app/uploads
volumes:
  - uploads_data:/app/uploads
```

Top-level `volumes:` block:
```yaml
volumes:
  mongodb_data:
  postgres_data:
  uploads_data:       # new
```

### Anti-Patterns to Avoid

- **Returning 400 for ownership violations:** `ResponseUtil.errorResponse()` maps `IllegalArgumentException`
  to HTTP 400. Never throw an exception for 403 — build the response map manually with
  `ResponseEntity.status(HttpStatus.FORBIDDEN)`.
- **Eagerly fetching the user relation:** `@ManyToOne(fetch = FetchType.LAZY)` is correct for this entity.
  Calling `report.getUser()` outside a transaction scope will throw `LazyInitializationException` —
  always load the report inside the controller method's transaction or use `.getId()` directly via a
  `findByUserId` query instead.
- **Storing the full file path and making it public:** `photoPath` is an internal disk path. The API
  returns `GET /api/reports/{id}/photo` as the URL, not the raw file path.
- **Binding a `@RequestBody` for PATCH and applying all fields blindly:** Only apply non-null fields from
  the DTO. A null `grade` in the JSON body means "leave unchanged", not "set to null".
- **Mounting a non-existent or broken directory as a Docker volume:** The `init-restaurants.js` incident
  (a root-owned directory) is the precedent. Verify `/app/uploads` is a clean directory, not a file or
  a root-owned artifact, before the volume mount.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Derived query for owner filter | Custom JPQL `@Query` | `findByUserId` naming convention | Spring Data JPA generates it |
| MIME type detection | Custom extension map | `java.nio.file.Files.probeContentType()` | JDK built-in, handles edge cases |
| File streaming | Byte array in memory | `UrlResource` + `ResponseEntity<Resource>` | Streams without loading entire file into heap |
| Table creation | Manual `CREATE TABLE` SQL script | Hibernate `ddl-auto=update` | Already the project pattern |
| JSON error shape | Custom exception handler | `ResponseUtil.errorResponse()` + manual 403 map | Consistent with all other controllers |

**Key insight:** This phase adds zero new libraries. Everything (JPA, multipart, file streaming, enums,
owned queries) is already shipped with Spring Boot 2.6.15 on the classpath.

---

## Common Pitfalls

### Pitfall 1: PATCH applies nulls and wipes existing data
**What goes wrong:** Deserializing `{"grade": null}` and calling `entity.setGrade(null)` clears the
field even though the client only wanted to update `status`.
**Why it happens:** Jackson maps absent and explicitly-null JSON fields the same way when using a POJO.
**How to avoid:** Use a dedicated `ReportPatchRequest` DTO. In the controller, check each field for null
before applying: `if (req.getGrade() != null) entity.setGrade(req.getGrade())`.
**Warning signs:** Tests that send a partial body and then GET the same report see cleared fields.

### Pitfall 2: Lazy loading `user` outside a transaction
**What goes wrong:** `report.getUser().getId()` throws `org.hibernate.LazyInitializationException` when
called after the transaction that loaded `report` has closed (common in @RestController methods without
`@Transactional`).
**Why it happens:** `FetchType.LAZY` defers loading the `user` row until it is first accessed. If the
Hibernate session is closed before access, the proxy throws.
**How to avoid:** Either (a) add `@Transactional` to the controller method, or (b) use
`report.getUser().getId()` immediately after loading within the same call stack, or (c) add a
`findByIdAndUserId(Long id, Long userId)` method to the repository so the ownership check happens in SQL.
**Warning signs:** `LazyInitializationException: could not initialize proxy` in the logs.

### Pitfall 3: Docker volume not declared in top-level `volumes:`
**What goes wrong:** `docker compose up` fails with `service "app" refers to undefined volume` or the
container starts but writes to an anonymous volume that is lost on `down`.
**Why it happens:** A named volume under `services.app.volumes` must also be declared under the
top-level `volumes:` key.
**How to avoid:** Add `uploads_data:` to the top-level `volumes:` block alongside `mongodb_data` and
`postgres_data`. Verify with `docker volume ls` after the first `docker compose up`.
**Warning signs:** Photos are accessible in the running container but disappear after `docker compose down && up`.

### Pitfall 4: Photo directory not created before first write
**What goes wrong:** `Files.copy()` or `FileOutputStream` throws `NoSuchFileException` because
`/app/uploads/{reportId}/` does not exist yet.
**Why it happens:** Docker volume mounts the root `/app/uploads` directory but does not pre-create
subdirectories.
**How to avoid:** Always call `Files.createDirectories(targetDir)` before writing the file — this is
idempotent and safe to call even if the directory exists.
**Warning signs:** `java.nio.file.NoSuchFileException` on first photo upload for any report.

### Pitfall 5: `Content-Type` header absent on photo streaming response
**What goes wrong:** Browser treats the response as `application/octet-stream` (download prompt) instead
of displaying the image inline.
**Why it happens:** `ResponseEntity<Resource>` without an explicit `contentType()` defaults to
`application/octet-stream`.
**How to avoid:** Use `Files.probeContentType(path)` and set it on the response. Fall back to
`application/octet-stream` if detection returns null.
**Warning signs:** Browser shows "Save As" dialog instead of rendering the image.

### Pitfall 6: Enum `@RequestBody` binding fails on mixed case
**What goes wrong:** Sending `{"grade": "a"}` throws `HttpMessageNotReadableException` because Jackson
cannot deserialize lowercase `"a"` into `Grade.A` by default.
**Why it happens:** Jackson enum deserialization is case-sensitive by default.
**How to avoid:** Either (a) document that clients must send uppercase, or (b) add
`@JsonProperty("A") A` annotations on the enum constants, or (c) configure
`spring.jackson.deserialization.read-enums-using-to-string=true` if tolerant matching is desired.
The simplest approach for an academic project: document uppercase and let it fail loudly with a 400.
**Warning signs:** Tests pass with uppercase literals but postman calls with lowercase fail unexpectedly.

---

## Code Examples

### Create `InspectionReportEntity` record (in POST handler)
```java
// Pattern: BookmarkEntity constructor
InspectionReportEntity report = new InspectionReportEntity();
report.setUser(currentUser);
report.setRestaurantId(req.getRestaurantId());
report.setGrade(req.getGrade());
report.setStatus(req.getStatus() != null ? req.getStatus() : Status.OPEN);
report.setViolationCodes(req.getViolationCodes());
report.setNotes(req.getNotes());
report.setCreatedAt(new Date());
report.setUpdatedAt(new Date());
InspectionReportEntity saved = reportRepository.save(report);
```

### PATCH partial update
```java
if (req.getGrade()          != null) { report.setGrade(req.getGrade()); }
if (req.getStatus()         != null) { report.setStatus(req.getStatus()); }
if (req.getViolationCodes() != null) { report.setViolationCodes(req.getViolationCodes()); }
if (req.getNotes()          != null) { report.setNotes(req.getNotes()); }
report.setUpdatedAt(new Date());
reportRepository.save(report);
```

### File save on upload
```java
String uploadsDir = AppConfig.getUploadsDir();
Path targetDir = Paths.get(uploadsDir, String.valueOf(reportId));
Files.createDirectories(targetDir);   // idempotent
String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
Path targetPath = targetDir.resolve(filename);
Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
report.setPhotoPath(targetPath.toString());
report.setUpdatedAt(new Date());
reportRepository.save(report);
```

### Stream photo back
```java
Path filePath = Paths.get(report.getPhotoPath());
Resource resource = new UrlResource(filePath.toUri());
if (!resource.exists()) {
    return ResponseEntity.notFound().build();
}
String contentType = Files.probeContentType(filePath);
return ResponseEntity.ok()
    .contentType(MediaType.parseMediaType(
        contentType != null ? contentType : "application/octet-stream"))
    .body(resource);
```

### Response enrichment helper
```java
private Map<String, Object> toResponseMap(InspectionReportEntity entity) {
    Restaurant restaurant = restaurantDAO.findByRestaurantId(entity.getRestaurantId());
    Map<String, Object> data = new HashMap<>();
    data.put("id",             entity.getId());
    data.put("restaurantId",   entity.getRestaurantId());
    data.put("restaurantName", restaurant != null ? restaurant.getName()    : null);
    data.put("borough",        restaurant != null ? restaurant.getBorough() : null);
    data.put("grade",          entity.getGrade());
    data.put("status",         entity.getStatus());
    data.put("violationCodes", entity.getViolationCodes());
    data.put("notes",          entity.getNotes());
    data.put("photoPath",      entity.getPhotoPath() != null
                                   ? "/api/reports/" + entity.getId() + "/photo" : null);
    data.put("createdAt",      entity.getCreatedAt());
    data.put("updatedAt",      entity.getUpdatedAt());
    return data;
}
```

Note: expose the photo as a URL (`/api/reports/{id}/photo`), not the raw disk path.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@WebMvcTest` slice tests | `JUnit 4 + standaloneSetup` | Phase 1 (Mockito/Byte Buddy on Java 25 crash) | ReportController tests must use the same standaloneSetup pattern |
| `mockito-inline` separate artifact | Merged into `mockito-core` 5.x | Phase 1 upgrade | Do NOT add `mockito-inline` to pom.xml |
| Spring Boot `@Autowired` field injection | Constructor injection for services / `@Autowired` on fields still used in controllers | Phase 1 decision | Controllers (`UserController`) still use `@Autowired` on fields — keep that pattern for `ReportController` |

**Deprecated/outdated:**
- `@WebMvcTest` slice test pattern: DO NOT USE — causes JVM crash on Java 25 with Byte Buddy. Use `JUnit 4 + AnnotationConfigWebApplicationContext + standaloneSetup` (established in Phase 1 `SecurityConfigTest`).
- `mockito-inline` dependency: removed in Phase 1. Using it again will cause MockMaker conflicts.

---

## Open Questions

1. **`restaurantDAO.findByRestaurantId()` when the restaurant does not exist in MongoDB**
   - What we know: The method returns `null` if no document matches.
   - What's unclear: Should POST reject a `restaurantId` that does not exist in MongoDB, or silently
     proceed and return null for `restaurantName`/`borough`?
   - Recommendation: Silently return null for the enrichment fields (academic project, simplest path).
     If validation is desired, add an early check and return HTTP 400 with `"Restaurant not found"`.

2. **`@Transactional` scope for PATCH and upload methods**
   - What we know: `UserController` does NOT declare `@Transactional` — it works because JPA methods are
     themselves transactional by default.
   - What's unclear: Loading an entity and then calling `report.getUser()` (lazy) requires an active
     Hibernate session.
   - Recommendation: Add `@Transactional` to `PATCH` and ownership-checking methods, OR use a
     repository query like `findByIdAndUserId` to avoid touching the lazy `user` proxy at all.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + JUnit 5 (Vintage engine) + Mockito 5.17.0 |
| Config file | pom.xml (surefire: `-XX:+EnableDynamicAgentLoading`) |
| Quick run command | `mvn test -Dtest=ReportControllerTest,ReportRepositoryTest -pl .` |
| Full suite command | `mvn test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CTRL-01 | POST creates report with MongoDB enrichment | unit | `mvn test -Dtest=ReportControllerTest#createReport*` | ❌ Wave 0 |
| CTRL-02 | GET returns only caller's reports; filter by status works | unit | `mvn test -Dtest=ReportControllerTest#listReports*` | ❌ Wave 0 |
| CTRL-03 | PATCH updates owned report; non-owner gets 403 | unit | `mvn test -Dtest=ReportControllerTest#patchReport*` | ❌ Wave 0 |
| CTRL-04 | Photo upload saves file; GET streams it back | unit | `mvn test -Dtest=ReportControllerTest#photoUpload*` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `mvn test -Dtest=ReportControllerTest`
- **Per wave merge:** `mvn test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/com/aflokkat/controller/ReportControllerTest.java` — covers CTRL-01 through CTRL-04
- [ ] `src/main/java/com/aflokkat/entity/InspectionReportEntity.java` — entity must exist before tests compile
- [ ] `src/main/java/com/aflokkat/repository/ReportRepository.java` — repository must exist before tests compile

### Test Pattern to Follow
All new tests must use the established Java 25-safe pattern from Phase 1:
```java
// Source: existing AuthServiceTest.java pattern (@ExtendWith(MockitoExtension.class))
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {
    @Mock private ReportRepository reportRepository;
    @Mock private RestaurantDAO restaurantDAO;
    @Mock private UserRepository userRepository;
    // ... standaloneSetup for HTTP-layer assertions
}
```
Do NOT use `@WebMvcTest` — it crashes the JVM on Java 25.

---

## Sources

### Primary (HIGH confidence)
- Direct code inspection of `BookmarkEntity.java`, `BookmarkRepository.java`, `UserController.java`,
  `SecurityConfig.java`, `AppConfig.java`, `RestaurantDAOImpl.java`, `ResponseUtil.java`,
  `docker-compose.yml`, `application.properties`, `pom.xml` — all patterns verified from source
- Spring Boot 2.6.15 BOM — confirms all required libraries are already on the classpath

### Secondary (MEDIUM confidence)
- Spring Data JPA derived query naming convention (`findByUserIdAndStatus`) — standard documented
  behavior, consistent with existing `BookmarkRepository.findByUserId` which works in production

### Tertiary (LOW confidence)
- None — all findings are directly verified from the project's own source code

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified from `pom.xml`; no new dependencies needed
- Architecture: HIGH — all patterns copied directly from working project code
- Pitfalls: HIGH — lazy-loading and Docker volume issues are verified against known project incidents
- Test patterns: HIGH — based on Phase 1 established patterns that resolve Java 25 / Byte Buddy issues

**Research date:** 2026-03-30
**Valid until:** Stable — Spring Boot 2.6.15 is pinned; no fast-moving dependencies introduced
