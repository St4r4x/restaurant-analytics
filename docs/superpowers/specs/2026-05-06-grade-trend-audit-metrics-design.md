# Grade Trend Chart + Audit Log + Prometheus Metrics — Design Spec

## Goal

Three independent improvements shipped together:
1. **Grade Trend Chart** — visualise a restaurant's inspection score history on the detail page
2. **Audit Log** — append-only record of admin and controller actions, queryable via the admin panel
3. **Prometheus Metrics** — expose Spring Boot Actuator's `/actuator/prometheus` scrape endpoint, locked to ADMIN role

---

## Feature 1: Grade Trend Chart

### Approach

Client-side only. The restaurant detail page already receives the full restaurant object (including the embedded `grades: List<InspectionRecord>` array) from `GET /api/restaurants/{id}`. No new backend endpoint is needed.

Chart.js is added via CDN on `restaurant.html`. On page load, the JS that already parses the restaurant response also extracts the `grades` array, sorts it chronologically by `inspectionDate`, and renders a line chart.

### Chart spec

- **X-axis:** inspection date (formatted `MMM YYYY`)
- **Y-axis:** score (0–28, lower is better — invert axis so better scores appear higher)
- **Data points:** one per `InspectionRecord` where `score != null`
- **Point colour:** grade-based — A = `#2e7d32`, B = `#f9a825`, C = `#e65100`, F/null = `#b71c1c`
- **Line colour:** neutral (`#888`)
- **Tooltip:** shows date, score, and grade letter
- **Placement:** new "Score History" card in the right column of the detail page, below the existing inspections table

### Data shape used

```js
// Already available in the page's restaurant object
restaurant.grades = [
  { inspectionDate: "2024-03-15", grade: "A", score: 9, ... },
  { inspectionDate: "2023-11-02", grade: "B", score: 18, ... },
  ...
]
```

Fields used: `inspectionDate` (String), `grade` (String), `score` (Integer). Records where `score` is null are skipped. The array is sorted ascending by date before rendering.

### No backend changes

The existing `RestaurantController` already serialises the full `Restaurant` document including `grades`. No new endpoint, DAO method, or service change required.

---

## Feature 2: Audit Log

### Data model

New JPA entity: `AuditLogEntity` in `com.st4r4x.entity`.

```
Table: audit_log (PostgreSQL, auto-created by Hibernate)

id            BIGINT PK AUTO_INCREMENT
actor_username VARCHAR(255) NOT NULL       -- who performed the action
actor_role     VARCHAR(50)  NOT NULL       -- their role at the time
action         VARCHAR(100) NOT NULL       -- enum name, e.g. USER_ROLE_CHANGED
target_type    VARCHAR(100)               -- e.g. "User", "CronJob", "Report" (nullable)
target_id      VARCHAR(100)               -- e.g. userId or jobKey (nullable)
detail         TEXT                        -- JSON string with action-specific fields
created_at     TIMESTAMP NOT NULL DEFAULT now()
```

`action` values (enum `AuditAction`):

| Value | Triggered by |
|---|---|
| `USER_ROLE_CHANGED` | `POST /api/admin/users/{id}/role` |
| `SYNC_TRIGGERED` | `POST /api/restaurants/refresh` |
| `CRON_JOB_TRIGGERED` | `POST /api/admin/cron/run/{jobKey}` |
| `OSM_ENRICH_TRIGGERED` | `POST /api/admin/osm-enrich` |
| `CACHE_REBUILT` | `POST /api/restaurants/rebuild-cache` |
| `REPORT_STATUS_CHANGED` | `PUT /api/reports/{id}` (status field only) |

### Service

New `AuditService` in `com.st4r4x.service`:

```java
public void log(String actorUsername, String actorRole,
                AuditAction action,
                String targetType, String targetId,
                Map<String, Object> detail)
```

- Serialises `detail` map to JSON string via Jackson `ObjectMapper`
- Persists via `AuditLogRepository extends JpaRepository<AuditLogEntity, Long>`
- Called explicitly at each audited call site — no AOP magic

`actorUsername` and `actorRole` are extracted from the Spring Security context (`SecurityContextHolder.getContext().getAuthentication()`) inside `AuditService.log()` so callers don't need to pass them.

Revised signature (callers pass only the action-specific data):

```java
public void log(AuditAction action, String targetType, String targetId, Map<String, Object> detail)
```

### Repository

```java
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    Page<AuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
```

### API endpoint

`GET /api/admin/audit?page=0&size=20` — ADMIN only (`@PreAuthorize("hasRole('ADMIN')")`).

Response:
```json
{
  "content": [
    {
      "id": 42,
      "actorUsername": "alice",
      "actorRole": "ROLE_ADMIN",
      "action": "USER_ROLE_CHANGED",
      "targetType": "User",
      "targetId": "7",
      "detail": "{\"oldRole\":\"ROLE_CUSTOMER\",\"newRole\":\"ROLE_CONTROLLER\"}",
      "createdAt": "2026-05-06T14:32:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 143,
  "totalPages": 8
}
```

`detail` is returned as a raw JSON string; the client parses it if needed.

### Admin UI

New "Audit Log" card at the bottom of `admin.html`. Shows a table with columns: Date/Time, Actor, Action, Target, Detail (truncated to 80 chars). Paginated with Prev/Next buttons. Loaded via `fetchWithAuth('/api/admin/audit?page=0&size=20')` on page load.

### Call sites

`AuditService` is `@Autowired` into `AdminController`, `RestaurantController` (for sync/cache), and `ReportController` (for status changes). Each relevant method calls `auditService.log(...)` after the successful operation, before returning the response.

---

## Feature 3: Prometheus Metrics

### Dependencies added to pom.xml

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### application.properties additions

```properties
# Expose only health (public) and prometheus (admin-locked)
management.endpoints.web.exposure.include=health,prometheus
management.endpoint.health.show-details=never
management.metrics.tags.application=restaurant-analytics
```

### Security configuration

In `SecurityConfig`, add `/actuator/prometheus` to the ADMIN-required matcher. `/actuator/health` stays public (used by Railway health checks and the existing `curl` health check in CI).

```java
.requestMatchers("/actuator/health").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
```

This ensures the Prometheus scrape endpoint is not publicly accessible. When wiring up Grafana Cloud later, it will need a bearer token (the admin JWT) in the scrape config — which is standard practice.

### What gets exposed for free

Spring Boot Actuator + Micrometer auto-instruments:
- JVM heap, GC pause duration, thread count
- HTTP request count and latency per endpoint (`http.server.requests`)
- Datasource connection pool (HikariCP)
- Redis connection pool
- Uptime and process CPU

No custom metrics in this iteration.

---

## File changes summary

| File | Change |
|---|---|
| `pom.xml` | Add `spring-boot-starter-actuator` + `micrometer-registry-prometheus` |
| `application.properties` | Add actuator exposure + metrics tag |
| `SecurityConfig.java` | Lock `/actuator/**` to ADMIN, keep `/actuator/health` public |
| `entity/AuditLogEntity.java` | New JPA entity |
| `entity/AuditAction.java` | New enum |
| `repository/AuditLogRepository.java` | New JPA repository |
| `service/AuditService.java` | New service |
| `controller/AdminController.java` | Add `GET /api/admin/audit` + inject `AuditService` + log admin actions |
| `controller/RestaurantController.java` | Inject `AuditService` + log sync/cache actions |
| `controller/ReportController.java` | Inject `AuditService` + log report status changes |
| `templates/admin.html` | Add Audit Log card |
| `templates/restaurant.html` | Add Chart.js CDN + Score History card |
| `test/AdminControllerTest.java` | Add tests for `GET /api/admin/audit` |
| `test/AuditServiceTest.java` | New unit test for `AuditService.log()` |

---

## Out of scope

- Audit log retention policy / purge job (add to backlog if log grows large)
- Custom business metrics (sync duration, OSM enrichment rate) — defer to Grafana Cloud phase
- Grafana Cloud wiring — deferred to when there is real traffic
