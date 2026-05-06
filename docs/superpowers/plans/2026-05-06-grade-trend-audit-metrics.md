# Grade Trend Chart + Audit Log + Prometheus Metrics — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship three independent improvements: grade-coloured score trend chart on the restaurant detail page, an append-only audit log for admin/controller actions with an admin UI, and a Prometheus metrics scrape endpoint locked to ADMIN.

**Architecture:** The chart is pure client-side (data already in the API response, Chart.js already loaded). The audit log is a new PostgreSQL table + JPA entity + service + admin card, with explicit `auditService.log()` calls in AdminController, RestaurantController, and ReportController. Prometheus is two pom.xml dependencies + three properties lines + one SecurityConfig rule.

**Tech Stack:** Java 25, Spring Boot 4.0.5, Chart.js 4.4 (CDN already loaded), JPA/Hibernate (PostgreSQL), Spring Boot Actuator, Micrometer Prometheus registry, JUnit 5 + Mockito.

---

## File Map

| File | Action |
|---|---|
| `pom.xml` | Add `spring-boot-starter-actuator` + `micrometer-registry-prometheus` |
| `src/main/resources/application.properties` | Add actuator exposure + metrics tag |
| `src/main/java/com/st4r4x/config/SecurityConfig.java` | Lock `/actuator/**` to ADMIN, keep `/actuator/health` public |
| `src/main/java/com/st4r4x/entity/AuditAction.java` | New enum |
| `src/main/java/com/st4r4x/entity/AuditLogEntity.java` | New JPA entity for `audit_log` table |
| `src/main/java/com/st4r4x/repository/AuditLogRepository.java` | New JPA repository with paginated findAll |
| `src/main/java/com/st4r4x/service/AuditService.java` | New service |
| `src/main/java/com/st4r4x/controller/AdminController.java` | Add GET /api/admin/audit, inject AuditService, log admin actions |
| `src/main/java/com/st4r4x/controller/RestaurantController.java` | Inject AuditService, log SYNC_TRIGGERED + CACHE_REBUILT |
| `src/main/java/com/st4r4x/controller/ReportController.java` | Inject AuditService, log REPORT_STATUS_CHANGED on PATCH |
| `src/main/resources/templates/admin.html` | Add Audit Log card with paginated table |
| `src/main/resources/templates/restaurant.html` | Enhance renderScoreChart() with grade-coloured points + tooltip |
| `src/test/java/com/st4r4x/service/AuditServiceTest.java` | New unit test |
| `src/test/java/com/st4r4x/controller/AdminControllerTest.java` | Add tests for GET /api/admin/audit |

---

## GROUP A — Prometheus Metrics (no dependencies, ~15 min)

### Task 1: Add Actuator + Micrometer dependencies

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/java/com/st4r4x/config/SecurityConfig.java`

- [ ] **Step 1: Add dependencies to pom.xml**

Open `pom.xml`. In the `<dependencies>` block, after the `spring-boot-starter-validation` entry, add:

```xml
<!-- Actuator + Prometheus metrics -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

- [ ] **Step 2: Add actuator config to application.properties**

At the end of `src/main/resources/application.properties`, add:

```properties
# Actuator / Prometheus
management.endpoints.web.exposure.include=health,prometheus
management.endpoint.health.show-details=never
management.metrics.tags.application=restaurant-analytics
```

- [ ] **Step 3: Lock /actuator/prometheus to ADMIN in SecurityConfig**

In `src/main/java/com/st4r4x/config/SecurityConfig.java`, inside `filterChain()`, add two lines at the top of the `authorizeHttpRequests` block, before `.requestMatchers("/api/auth/**").permitAll()`:

```java
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
```

The full updated block:

```java
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/restaurants/**").permitAll()
                .requestMatchers("/api/inspection/**").permitAll()
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/api-docs/**",
                    "/v3/api-docs/**",
                    "/webjars/**"
                ).permitAll()
                .requestMatchers("/api/reports/stats").hasRole("ADMIN")
                .requestMatchers("/api/reports/**").hasRole("CONTROLLER")
                .requestMatchers("/api/users/**").authenticated()
                .anyRequest().permitAll()
            )
```

- [ ] **Step 4: Build and verify**

```bash
mvn clean package -DskipTests -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Manual smoke test (optional but recommended)**

Start the app (`mvn spring-boot:run`) and verify:

```bash
# Public — no auth needed
curl -s http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# Admin-locked — returns 401 without token
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/prometheus
# Expected: 401

# With admin JWT returns metric lines starting with "# HELP"
curl -s -H "Authorization: Bearer <admin-jwt>" http://localhost:8080/actuator/prometheus | head -3
```

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/main/resources/application.properties src/main/java/com/st4r4x/config/SecurityConfig.java
git commit -m "feat(metrics): expose /actuator/prometheus locked to ADMIN role"
```

---

## GROUP B — Audit Log (~1 hour)

### Task 2: AuditAction enum + AuditLogEntity + AuditLogRepository + AuditService

**Files:**
- Create: `src/main/java/com/st4r4x/entity/AuditAction.java`
- Create: `src/main/java/com/st4r4x/entity/AuditLogEntity.java`
- Create: `src/main/java/com/st4r4x/repository/AuditLogRepository.java`
- Create: `src/main/java/com/st4r4x/service/AuditService.java`
- Create: `src/test/java/com/st4r4x/service/AuditServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/st4r4x/service/AuditServiceTest.java`:

```java
package com.st4r4x.service;

import com.st4r4x.entity.AuditAction;
import com.st4r4x.entity.AuditLogEntity;
import com.st4r4x.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @InjectMocks private AuditService auditService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext(String username, String role) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        when(auth.getAuthorities()).thenReturn(
            (Collection) List.of(new SimpleGrantedAuthority(role)));
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    void log_persistsEntryWithActorFromSecurityContext() {
        setupSecurityContext("admin-user", "ROLE_ADMIN");
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditService.log(AuditAction.USER_ROLE_CHANGED, "User", "7",
            Map.of("oldRole", "ROLE_CUSTOMER", "newRole", "ROLE_CONTROLLER"));

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLogEntity saved = captor.getValue();

        assertThat(saved.getActorUsername()).isEqualTo("admin-user");
        assertThat(saved.getActorRole()).isEqualTo("ROLE_ADMIN");
        assertThat(saved.getAction()).isEqualTo(AuditAction.USER_ROLE_CHANGED);
        assertThat(saved.getTargetType()).isEqualTo("User");
        assertThat(saved.getTargetId()).isEqualTo("7");
        assertThat(saved.getDetail()).contains("ROLE_CONTROLLER");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void log_handlesNullDetailGracefully() {
        setupSecurityContext("admin-user", "ROLE_ADMIN");
        when(auditLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        auditService.log(AuditAction.CACHE_REBUILT, null, null, null);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=AuditServiceTest -q 2>&1 | tail -5
```

Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Create AuditAction.java**

```java
// src/main/java/com/st4r4x/entity/AuditAction.java
package com.st4r4x.entity;

public enum AuditAction {
    USER_ROLE_CHANGED,
    SYNC_TRIGGERED,
    CRON_JOB_TRIGGERED,
    OSM_ENRICH_TRIGGERED,
    CACHE_REBUILT,
    REPORT_STATUS_CHANGED
}
```

- [ ] **Step 4: Create AuditLogEntity.java**

```java
// src/main/java/com/st4r4x/entity/AuditLogEntity.java
package com.st4r4x.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Date;

@Entity
@Table(name = "audit_log",
       indexes = @Index(name = "idx_audit_log_created_at", columnList = "created_at"))
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_username", nullable = false)
    private String actorUsername;

    @Column(name = "actor_role", nullable = false, length = 50)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private AuditAction action;

    @Column(name = "target_type", length = 100)
    private String targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    public AuditLogEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String v) { this.actorUsername = v; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String v) { this.actorRole = v; }
    public AuditAction getAction() { return action; }
    public void setAction(AuditAction v) { this.action = v; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String v) { this.targetType = v; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String v) { this.targetId = v; }
    public String getDetail() { return detail; }
    public void setDetail(String v) { this.detail = v; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date v) { this.createdAt = v; }
}
```

- [ ] **Step 5: Create AuditLogRepository.java**

```java
// src/main/java/com/st4r4x/repository/AuditLogRepository.java
package com.st4r4x.repository;

import com.st4r4x.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    Page<AuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
```

- [ ] **Step 6: Create AuditService.java**

```java
// src/main/java/com/st4r4x/service/AuditService.java
package com.st4r4x.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.st4r4x.entity.AuditAction;
import com.st4r4x.entity.AuditLogEntity;
import com.st4r4x.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void log(AuditAction action, String targetType, String targetId, Map<String, Object> detail) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String actorUsername = (auth != null) ? auth.getName() : "anonymous";
            String actorRole = resolveRole(auth);

            AuditLogEntity entry = new AuditLogEntity();
            entry.setActorUsername(actorUsername);
            entry.setActorRole(actorRole);
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetail(detail != null ? objectMapper.writeValueAsString(detail) : null);
            entry.setCreatedAt(new Date());

            auditLogRepository.save(entry);
        } catch (JsonProcessingException e) {
            logger.warn("AuditService: failed to serialize detail for action {}: {}", action, e.getMessage());
        } catch (Exception e) {
            logger.warn("AuditService: failed to persist audit entry for action {}: {}", action, e.getMessage());
        }
    }

    private String resolveRole(Authentication auth) {
        if (auth == null) return "anonymous";
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        if (authorities == null || authorities.isEmpty()) return "unknown";
        return authorities.iterator().next().getAuthority();
    }
}
```

- [ ] **Step 7: Run tests — expect pass**

```bash
mvn test -Dtest=AuditServiceTest -q
```

Expected: 2 tests PASS.

- [ ] **Step 8: Commit**

```bash
git add \
  src/main/java/com/st4r4x/entity/AuditAction.java \
  src/main/java/com/st4r4x/entity/AuditLogEntity.java \
  src/main/java/com/st4r4x/repository/AuditLogRepository.java \
  src/main/java/com/st4r4x/service/AuditService.java \
  src/test/java/com/st4r4x/service/AuditServiceTest.java
git commit -m "feat(audit): add AuditLogEntity, AuditService, AuditLogRepository"
```

---

### Task 3: GET /api/admin/audit endpoint + call sites in 3 controllers

**Files:**
- Modify: `src/main/java/com/st4r4x/controller/AdminController.java`
- Modify: `src/main/java/com/st4r4x/controller/RestaurantController.java`
- Modify: `src/main/java/com/st4r4x/controller/ReportController.java`
- Modify: `src/test/java/com/st4r4x/controller/AdminControllerTest.java`

- [ ] **Step 1: Add tests for GET /api/admin/audit to AdminControllerTest**

Open `src/test/java/com/st4r4x/controller/AdminControllerTest.java`.

Add new mock fields at the top of the class (after existing `@Mock` fields):

```java
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditService auditService;
```

Add these imports:

```java
import com.st4r4x.entity.AuditAction;
import com.st4r4x.entity.AuditLogEntity;
import com.st4r4x.repository.AuditLogRepository;
import com.st4r4x.service.AuditService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
```

Add these two test methods at the end of the class:

```java
    @Test
    void getAuditLog_returns200_withPagedEntries() throws Exception {
        AuditLogEntity entry = new AuditLogEntity();
        entry.setActorUsername("admin");
        entry.setActorRole("ROLE_ADMIN");
        entry.setAction(AuditAction.USER_ROLE_CHANGED);
        entry.setTargetType("User");
        entry.setTargetId("5");
        entry.setDetail("{\"oldRole\":\"ROLE_CUSTOMER\",\"newRole\":\"ROLE_CONTROLLER\"}");
        entry.setCreatedAt(new java.util.Date());

        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any()))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/audit?page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].actorUsername").value("admin"))
            .andExpect(jsonPath("$.content[0].action").value("USER_ROLE_CHANGED"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void getAuditLog_emptyPage_returns200() throws Exception {
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(any()))
            .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/admin/audit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(0));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn test -Dtest=AdminControllerTest -q 2>&1 | tail -5
```

Expected: FAIL — `getAuditLog` does not exist yet.

- [ ] **Step 3: Add GET /api/admin/audit to AdminController**

Open `src/main/java/com/st4r4x/controller/AdminController.java`.

Add after the existing `@Autowired` fields:

```java
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditService auditService;
```

Add imports:

```java
import com.st4r4x.entity.AuditAction;
import com.st4r4x.entity.AuditLogEntity;
import com.st4r4x.repository.AuditLogRepository;
import com.st4r4x.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestParam;
```

Add this method before `getStats()`:

```java
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/audit")
    public ResponseEntity<Map<String, Object>> getAuditLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLogEntity> result = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<Map<String, Object>> content = result.getContent().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("actorUsername", e.getActorUsername());
                    m.put("actorRole", e.getActorRole());
                    m.put("action", e.getAction());
                    m.put("targetType", e.getTargetType());
                    m.put("targetId", e.getTargetId());
                    m.put("detail", e.getDetail());
                    m.put("createdAt", e.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(response);
    }
```

- [ ] **Step 4: Add audit.log() calls to AdminController's existing methods**

In `setUserRole()`, capture the old role before changing it, then log after save. Replace the `.map(u -> {` lambda body:

```java
                .map(u -> {
                    String oldRole = u.getRole();
                    u.setRole(newRole);
                    userRepository.save(u);
                    auditService.log(AuditAction.USER_ROLE_CHANGED, "User", String.valueOf(id),
                            Map.of("oldRole", oldRole, "newRole", newRole));
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("status", "success");
                    resp.put("id", u.getId());
                    resp.put("username", u.getUsername());
                    resp.put("role", u.getRole());
                    return ResponseEntity.ok(resp);
                })
```

In `triggerOsmEnrich()`, after `osmEnrichmentService.enrichAll();`, add:

```java
        auditService.log(AuditAction.OSM_ENRICH_TRIGGERED, null, null, null);
```

In `runCronJob()`, after the early return for unknown key, before building the success response, add:

```java
        auditService.log(AuditAction.CRON_JOB_TRIGGERED, "CronJob", jobKey, null);
```

- [ ] **Step 5: Inject AuditService into RestaurantController**

Open `src/main/java/com/st4r4x/controller/RestaurantController.java`.

First, grep the file to find the refresh and rebuild-cache method names:

```bash
grep -n "PostMapping\|refresh\|rebuild" src/main/java/com/st4r4x/controller/RestaurantController.java | head -20
```

Add field and imports:

```java
    @Autowired
    private AuditService auditService;
```

```java
import com.st4r4x.entity.AuditAction;
import com.st4r4x.service.AuditService;
```

In the `POST /api/restaurants/refresh` handler, after the sync is accepted (after any `syncService.startSync()` or equivalent call), add:

```java
            auditService.log(AuditAction.SYNC_TRIGGERED, null, null, null);
```

In the `POST /api/restaurants/rebuild-cache` handler, after the cache rebuild call, add:

```java
            auditService.log(AuditAction.CACHE_REBUILT, null, null, null);
```

- [ ] **Step 6: Inject AuditService into ReportController**

Open `src/main/java/com/st4r4x/controller/ReportController.java`.

Add field:

```java
    @Autowired
    private AuditService auditService;
```

Add import:

```java
import com.st4r4x.entity.AuditAction;
import com.st4r4x.service.AuditService;
```

In `patchReport()`, capture the old status before the partial-update block and log after save, only if status changed. Replace the partial-update block starting at `// Partial update`:

```java
            Status oldStatus = report.getStatus();
            if (req.getGrade()          != null) { report.setGrade(req.getGrade()); }
            if (req.getStatus()         != null) { report.setStatus(req.getStatus()); }
            if (req.getViolationCodes() != null) { report.setViolationCodes(req.getViolationCodes()); }
            if (req.getNotes()          != null) { report.setNotes(req.getNotes()); }
            report.setUpdatedAt(new Date());
            reportRepository.save(report);

            if (req.getStatus() != null && !req.getStatus().equals(oldStatus)) {
                auditService.log(AuditAction.REPORT_STATUS_CHANGED, "Report", String.valueOf(id),
                        Map.of("oldStatus", oldStatus.name(), "newStatus", req.getStatus().name()));
            }
```

- [ ] **Step 7: Run tests**

```bash
mvn test -Dtest=AdminControllerTest,AuditServiceTest -q
```

Expected: all tests PASS.

- [ ] **Step 8: Commit**

```bash
git add \
  src/main/java/com/st4r4x/controller/AdminController.java \
  src/main/java/com/st4r4x/controller/RestaurantController.java \
  src/main/java/com/st4r4x/controller/ReportController.java \
  src/test/java/com/st4r4x/controller/AdminControllerTest.java
git commit -m "feat(audit): add GET /api/admin/audit and log actions in admin+restaurant+report controllers"
```

---

### Task 4: Audit Log card in admin.html

**Files:**
- Modify: `src/main/resources/templates/admin.html`

- [ ] **Step 1: Add CSS for audit table**

In the `<style>` block of `admin.html`, after the `.bug-btn` rules, add:

```css
.audit-table { width: 100%; border-collapse: collapse; font-size: 0.82em; margin-top: 8px; }
.audit-table th { text-align: left; padding: 7px 10px; border-bottom: 2px solid #e8e0d8; font-weight: 700; color: #555; font-size: 0.78em; text-transform: uppercase; letter-spacing: 0.05em; }
.audit-table td { padding: 7px 10px; border-bottom: 1px solid #f0ebe3; vertical-align: top; max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.audit-table tr:last-child td { border-bottom: none; }
.audit-action { font-weight: 600; font-size: 0.82em; color: #1a1a1a; }
.audit-pagination { display: flex; gap: 8px; margin-top: 12px; align-items: center; font-size: 0.82em; color: #555; }
```

- [ ] **Step 2: Add the Audit Log card HTML**

In `admin.html`, before the closing `</div>` of the `.container` div (just before the `<!-- Floating bug report button -->` comment), add:

```html
    <!-- Card 6: Audit Log -->
    <div class="card">
        <h2>Audit Log</h2>
        <p class="desc-text">Recent admin and controller actions. Most recent first.</p>
        <div id="audit-loading" class="status-text">Loading audit log…</div>
        <table class="audit-table" id="audit-table" style="display:none">
            <thead>
                <tr>
                    <th>Date / Time</th>
                    <th>Actor</th>
                    <th>Action</th>
                    <th>Target</th>
                    <th>Detail</th>
                </tr>
            </thead>
            <tbody id="audit-tbody"></tbody>
        </table>
        <div id="audit-empty" class="status-text" style="display:none">No audit entries yet.</div>
        <div class="audit-pagination" id="audit-pagination" style="display:none">
            <button id="audit-prev" class="btn btn-secondary" onclick="loadAudit(auditPage-1)" disabled>&#8592; Prev</button>
            <span id="audit-page-info"></span>
            <button id="audit-next" class="btn btn-secondary" onclick="loadAudit(auditPage+1)">Next &#8594;</button>
        </div>
    </div>
```

- [ ] **Step 3: Add audit JS — uses only DOM methods (no dynamic HTML string injection)**

In the `<script>` block of `admin.html`, after the `changeRole` function and before the `// ── Page load` comment, add:

```javascript
// ── Audit Log ─────────────────────────────────────────────────────────────────
var auditPage = 0;
var auditTotalPages = 0;
var ACTION_LABELS = {
    USER_ROLE_CHANGED: 'Role Changed',
    SYNC_TRIGGERED: 'Sync Triggered',
    CRON_JOB_TRIGGERED: 'Cron Triggered',
    OSM_ENRICH_TRIGGERED: 'OSM Enrich',
    CACHE_REBUILT: 'Cache Rebuilt',
    REPORT_STATUS_CHANGED: 'Report Status'
};

function loadAudit(page) {
    page = page || 0;
    if (page < 0 || (auditTotalPages > 0 && page >= auditTotalPages)) return;
    auditPage = page;
    fetchWithAuth('/api/admin/audit?page=' + page + '&size=20')
        .then(function(r) { return r.json(); })
        .then(function(data) {
            document.getElementById('audit-loading').style.display = 'none';
            auditTotalPages = data.totalPages || 0;
            var tbody = document.getElementById('audit-tbody');
            tbody.textContent = '';

            if (!data.content || data.content.length === 0) {
                document.getElementById('audit-empty').style.display = 'block';
                document.getElementById('audit-pagination').style.display = 'none';
                return;
            }

            data.content.forEach(function(e) {
                var tr = document.createElement('tr');

                var tdDate = document.createElement('td');
                tdDate.textContent = fmtDate(e.createdAt);
                tr.appendChild(tdDate);

                var tdActor = document.createElement('td');
                tdActor.textContent = e.actorUsername || '—';
                tr.appendChild(tdActor);

                var tdAction = document.createElement('td');
                var actionSpan = document.createElement('span');
                actionSpan.className = 'audit-action';
                actionSpan.textContent = ACTION_LABELS[e.action] || e.action;
                tdAction.appendChild(actionSpan);
                tr.appendChild(tdAction);

                var tdTarget = document.createElement('td');
                tdTarget.textContent = e.targetType
                    ? (e.targetType + (e.targetId ? ' #' + e.targetId : ''))
                    : '—';
                tr.appendChild(tdTarget);

                var tdDetail = document.createElement('td');
                var raw = e.detail || '';
                tdDetail.title = raw;
                tdDetail.textContent = raw.length > 60 ? raw.substring(0, 60) + '…' : (raw || '—');
                tr.appendChild(tdDetail);

                tbody.appendChild(tr);
            });

            document.getElementById('audit-table').style.display = 'table';
            document.getElementById('audit-pagination').style.display = 'flex';
            document.getElementById('audit-page-info').textContent =
                'Page ' + (data.page + 1) + ' of ' + (data.totalPages || 1) +
                ' (' + data.totalElements + ' entries)';
            document.getElementById('audit-prev').disabled = (data.page === 0);
            document.getElementById('audit-next').disabled = (data.page + 1 >= data.totalPages);
        })
        .catch(function() {
            document.getElementById('audit-loading').textContent = 'Failed to load audit log';
        });
}
```

- [ ] **Step 4: Call loadAudit on page load**

In the `DOMContentLoaded` listener, after `loadUsers();`, add:

```javascript
    loadAudit(0);
```

- [ ] **Step 5: Build**

```bash
mvn clean package -DskipTests -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/templates/admin.html
git commit -m "feat(audit): add Audit Log card to admin page with DOM-safe rendering and pagination"
```

---

## GROUP C — Grade Trend Chart Enhancement (~15 min)

### Task 5: Grade-coloured points + inverted y-axis in renderScoreChart

**Files:**
- Modify: `src/main/resources/templates/restaurant.html`

The `renderScoreChart()` function already exists and renders a basic line chart. This task replaces it with grade-coloured points, inverted y-axis, and a richer tooltip.

Note: Chart.js 4.4 is already loaded via CDN on line 10 of the template.

- [ ] **Step 1: Replace renderScoreChart() in restaurant.html**

Find the function starting at `function renderScoreChart(grades) {` (around line 330) and replace it entirely with:

```javascript
function renderScoreChart(grades) {
  var sorted = (grades || [])
    .filter(function(g) { return g.date && g.score != null; })
    .sort(function(a, b) { return a.date.localeCompare(b.date); });

  if (sorted.length === 0) {
    document.getElementById('score-chart').parentElement.textContent = 'No score data available';
    document.getElementById('score-chart').parentElement.style.cssText = 'color:#aaa;font-size:0.9em;padding:20px 0';
    return;
  }

  var GRADE_COLORS = { A: '#2e7d32', B: '#f9a825', C: '#e65100', F: '#b71c1c' };
  function gradeColor(g) { return GRADE_COLORS[g] || '#b71c1c'; }

  var chart = new Chart(document.getElementById('score-chart'), {
    type: 'line',
    data: {
      labels: sorted.map(function(g) { return g.date.substring(0, 10); }),
      datasets: [{
        label: 'Score',
        data: sorted.map(function(g) { return g.score; }),
        borderColor: '#888',
        backgroundColor: 'rgba(136,136,136,0.08)',
        tension: 0.3,
        fill: true,
        pointRadius: 6,
        pointHoverRadius: 8,
        pointBackgroundColor: sorted.map(function(g) { return gradeColor(g.grade); }),
        pointBorderColor: sorted.map(function(g) { return gradeColor(g.grade); })
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: function(ctx) {
              var g = sorted[ctx.dataIndex];
              return 'Score: ' + g.score + '  |  Grade: ' + (g.grade || '—');
            }
          }
        }
      },
      scales: {
        y: {
          reverse: true,
          min: 0,
          title: { display: true, text: 'Score (lower = better)', font: { size: 11 } },
          grid: { color: '#f0f0f0' }
        },
        x: {
          grid: { display: false },
          ticks: { maxTicksLimit: 8, font: { size: 10 } }
        }
      }
    }
  });
  setTimeout(function() { chart.resize(); }, 50);
}
```

Key changes vs the original:
- `pointBackgroundColor` / `pointBorderColor` are per-point arrays based on grade
- `y.reverse: true` — lower score (better) appears higher
- Tooltip `label` callback shows score AND grade letter
- Line colour is neutral `#888` — grade info conveyed by point colour, not line

- [ ] **Step 2: Build**

```bash
mvn clean package -DskipTests -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Manual visual check**

Start the app and open a restaurant detail page (e.g. `/restaurant/40356018`). Verify:
- Points are coloured (green for A, amber for B, orange for C, red for F)
- Y-axis label reads "Score (lower = better)"
- Low scores appear near the top of the chart
- Hover tooltip shows "Score: 9  |  Grade: A"
- If no inspection scores exist, the chart area shows plain text "No score data available"

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/restaurant.html
git commit -m "feat(chart): grade-coloured points, inverted y-axis, grade in tooltip on score history chart"
```

---

## Final: Full test run + CHANGELOG + semver bump

- [ ] **Step 1: Run full test suite**

```bash
mvn test
```

Expected: all tests pass. No regressions in existing tests.

- [ ] **Step 2: Update CHANGELOG.md**

Under `## [Unreleased]`, add or extend the Features section:

```markdown
- Grade trend chart: per-point colour by grade (A=green, B=amber, C=orange, F=red), inverted y-axis, grade in tooltip
- Audit log: append-only PostgreSQL table (actor, action, target, detail) for admin and controller actions; paginated Audit Log card on admin page; `GET /api/admin/audit` (ADMIN only)
- Prometheus metrics: `GET /actuator/prometheus` (ADMIN only) via Spring Boot Actuator + Micrometer; auto-instruments JVM, HTTP, datasource, Redis
```

- [ ] **Step 3: Bump semver to 2.2.0**

In `src/main/resources/application.properties`, update:

```properties
app.semver=2.2.0
```

- [ ] **Step 4: Final commit**

```bash
git add CHANGELOG.md src/main/resources/application.properties
git commit -m "chore(release): bump to 2.2.0 — grade trend, audit log, prometheus metrics"
```
