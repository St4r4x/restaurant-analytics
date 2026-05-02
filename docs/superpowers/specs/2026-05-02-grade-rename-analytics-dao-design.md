# Design: Grade Rename + AnalyticsDAO Split

**Date:** 2026-05-02  
**Status:** Approved

## Problem

Two independent issues surfaced by the knowledge graph:

1. **Name collision** — `com.st4r4x.domain.Grade` (MongoDB POJO, full inspection record) and `com.st4r4x.entity.Grade` (JPA enum `A, B, C, F`) share the same simple name. A developer can import the wrong one silently. `ReportRequest` already imports the entity enum — the collision is live.

2. **RestaurantDAOImpl does two jobs** — 3 methods (`findMapPoints`, `findBoroughGradeDistribution`, `countAtRiskRestaurants`) bypass the typed POJO `restaurantCollection` and call `database.getCollection()` directly, returning raw `Document`. This leaks implementation detail, bridges 6 graph communities (vs 5 for the interface), and violates single responsibility. `AnalyticsController` already bypasses the service layer and talks to the DAO directly — these methods belong in their own DAO.

Both changes are pure structural refactors: no behavior changes, no API changes, no data migrations.

---

## Change 1 — Grade Rename

### Files renamed

| Old name | New name | Package |
|----------|----------|---------|
| `Grade.java` | `InspectionRecord.java` | `com.st4r4x.domain` |
| `Grade.java` | `LetterGrade.java` | `com.st4r4x.entity` |

### Rationale

- `InspectionRecord` — accurately describes the domain POJO: a full inspection event embedding date, score, violation code, violation description, critical flag, action, and inspection type. It is not just a letter.
- `LetterGrade` — accurately describes the enum: it is a letter (`A`, `B`, `C`, `F`) assigned to a user-submitted report. Nothing more.

### References to update

**`InspectionRecord` (was `domain.Grade`):**
- `com.st4r4x.domain.Restaurant` — field `List<Grade> grades` → `List<InspectionRecord> grades`
- `com.st4r4x.sync.SyncService` — import + `buildRestaurant()` local variable + list declaration
- `com.st4r4x.service.RestaurantService` — import only
- `com.st4r4x.cache.RestaurantCacheService` — import only
- `com.st4r4x.domain.RestaurantTest` — any test assertions on grade objects

**`LetterGrade` (was `entity.Grade`):**
- `com.st4r4x.entity.InspectionReportEntity` — field `Grade grade` → `LetterGrade grade`
- `com.st4r4x.dto.ReportRequest` — field + getter/setter
- `com.st4r4x.controller.AdminController` — import + any field references
- `com.st4r4x.controller.ReportController` — field references (no direct import — accessed via `ReportRequest.getGrade()`)
- `com.st4r4x.controller.ReportControllerTest` — mock/assertion references
- `com.st4r4x.service.AuthServiceTest` — any references

---

## Change 2 — AnalyticsDAO Split

### New interface: `com.st4r4x.dao.AnalyticsDAO`

```java
public interface AnalyticsDAO {
    List<Document> findMapPoints();
    List<Document> findBoroughGradeDistribution();
    long countAtRiskRestaurants();
}
```

### New implementation: `com.st4r4x.dao.AnalyticsDAOImpl`

- Annotated `@Repository`
- Constructor acquires `MongoClient` from `MongoClientFactory.getInstance()`, gets database from `AppConfig.getMongoDatabase()`, gets collection name from `AppConfig.getMongoCollection()` — same pattern as `RestaurantDAOImpl`
- The 3 method bodies are moved verbatim from `RestaurantDAOImpl`
- Holds its own `MongoDatabase database` field for the raw `Document` queries

### `RestaurantDAO` / `RestaurantDAOImpl`

- Remove the 3 method declarations from `RestaurantDAO` interface
- Remove the 3 method implementations from `RestaurantDAOImpl`
- No other changes

### `AnalyticsController`

- Add `@Autowired AnalyticsDAO analyticsDAO` field
- Replace calls to `restaurantDAO.findMapPoints()`, `restaurantDAO.findBoroughGradeDistribution()`, `restaurantDAO.countAtRiskRestaurants()` with `analyticsDAO.*`
- Remove `restaurantDAO` field if it is only used for those 3 methods (verify first)

### `AnalyticsControllerTest`

- Add `@Mock AnalyticsDAO analyticsDAO`
- Inject into controller under test
- Move the 3 method stubs from `RestaurantDAO` mock to `AnalyticsDAO` mock

---

## What does NOT change

- All API endpoints — same URLs, same response shapes
- MongoDB queries — moved verbatim, not rewritten
- `RestaurantCacheService` — unchanged
- `RestaurantService` — unchanged
- Any frontend template — unchanged
- Docker / application.properties — unchanged

---

## Testing strategy

- All existing tests pass after the rename (only import paths change)
- `AnalyticsControllerTest` verifies the 3 endpoints still return the expected mocked data via `AnalyticsDAO`
- No new integration tests required — behavior is identical
