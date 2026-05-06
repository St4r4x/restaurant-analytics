package com.st4r4x.controller;

import com.st4r4x.entity.AuditAction;
import com.st4r4x.entity.AuditLogEntity;
import com.st4r4x.entity.LetterGrade;
import com.st4r4x.entity.Status;
import com.st4r4x.entity.UserEntity;
import com.st4r4x.repository.AuditLogRepository;
import com.st4r4x.repository.ReportRepository;
import com.st4r4x.repository.UserRepository;
import com.st4r4x.service.AuditService;
import com.st4r4x.sync.CronScheduler;
import com.st4r4x.sync.OsmEnrichmentService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for ADMIN-only operations.
 * Provides aggregate report statistics across all controllers (ADM-03).
 * Individual report data is NOT returned — only counts grouped by enum values.
 *
 * No class-level @RequestMapping — each method declares its full path so that
 * /api/admin/* and /api/reports/* endpoints can coexist in the same controller.
 */
@RestController
public class AdminController {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OsmEnrichmentService osmEnrichmentService;

    @Autowired
    private CronScheduler cronScheduler;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditService auditService;

    private static final List<String> ALLOWED_ROLES =
            List.of("ROLE_CUSTOMER", "ROLE_CONTROLLER", "ROLE_ADMIN");

    /**
     * GET /api/admin/users — list all users (id, username, email, role).
     * Password hashes are never returned. ADMIN role required.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers() {
        List<Map<String, Object>> users = userRepository.findAll().stream()
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("email", u.getEmail());
                    m.put("role", u.getRole());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /**
     * POST /api/admin/users/{id}/role — update the role of a user.
     * Body: { "role": "ROLE_CONTROLLER" }
     * Allowed values: ROLE_CUSTOMER, ROLE_CONTROLLER, ROLE_ADMIN.
     * ADMIN role required.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/users/{id}/role")
    public ResponseEntity<Map<String, Object>> setUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String newRole = body.get("role");
        if (newRole == null || !ALLOWED_ROLES.contains(newRole)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "error");
            err.put("message", "Invalid role. Allowed: " + ALLOWED_ROLES);
            return ResponseEntity.badRequest().body(err);
        }
        return userRepository.findById(id)
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
                .orElseGet(() -> {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("status", "error");
                    err.put("message", "User not found: " + id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
                });
    }

    /**
     * POST /api/admin/osm-enrich — triggers a full OSM re-enrichment of all restaurants.
     * Uses an absolute path to avoid the /api/reports prefix from @RequestMapping.
     * The enrichAll() method is @Async and returns immediately; enrichment runs in background.
     * ADMIN role required.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/osm-enrich")
    public ResponseEntity<Map<String, Object>> triggerOsmEnrich() {
        osmEnrichmentService.enrichAll();
        auditService.log(AuditAction.OSM_ENRICH_TRIGGERED, null, null, null);
        Map<String, Object> body = new HashMap<>();
        body.put("status", "accepted");
        body.put("message", "OSM enrichment started in background");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    /**
     * POST /api/admin/cron/run/{jobKey} — manually trigger a cron job by key.
     * Valid keys: cache-warmup, osm-reenrichment, es-reindex.
     * cache-warmup runs inline (200 OK when done); the other two run async (202 Accepted).
     * ADMIN role required.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/cron/run/{jobKey}")
    public ResponseEntity<Map<String, Object>> runCronJob(@PathVariable String jobKey) {
        boolean known = cronScheduler.runJob(jobKey);
        if (!known) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "error");
            body.put("message", "Unknown job key: " + jobKey + ". Valid keys: cache-warmup, osm-reenrichment, es-reindex");
            return ResponseEntity.badRequest().body(body);
        }
        auditService.log(AuditAction.CRON_JOB_TRIGGERED, "CronJob", jobKey, null);
        boolean async = jobKey.equals("osm-reenrichment") || jobKey.equals("es-reindex");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", async ? "accepted" : "success");
        body.put("job", jobKey);
        body.put("message", async ? "Job started in background" : "Job completed");
        return async
                ? ResponseEntity.status(HttpStatus.ACCEPTED).body(body)
                : ResponseEntity.ok(body);
    }

    /**
     * GET /api/admin/cron/status — returns the status of all scheduled cron jobs.
     * Returns a map of job names to JobStatus objects, including last run time, duration, and error info.
     * ADMIN role required.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/cron/status")
    public ResponseEntity<Map<String, Object>> getCronStatus() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "success");
        body.put("jobs", cronScheduler.getStatus());
        return ResponseEntity.ok(body);
    }

    /**
     * GET /api/admin/audit — returns paginated audit log entries, ordered by most recent first.
     * ADMIN role required.
     */
    @Operation(summary = "Get paginated audit log", tags = {"Admin"})
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

    /**
     * GET /api/reports/stats — aggregate counts by status and grade across ALL controllers.
     * ADMIN role required. Does NOT filter by userId — intentionally aggregates all reports.
     * All enum values (OPEN/IN_PROGRESS/RESOLVED, A/B/C/F) are always present in the response,
     * defaulting to 0 when no reports have that value.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/reports/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        // Pre-populate all expected Status enum values with 0 so missing values default correctly
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Status s : Status.values()) {
            byStatus.put(s.name(), 0L);
        }
        for (Object[] row : reportRepository.countGroupByStatus()) {
            Status status = (Status) row[0];
            byStatus.put(status.name(), (Long) row[1]);
        }

        // Pre-populate all expected LetterGrade enum values with 0 so missing values default correctly
        Map<String, Long> byGrade = new LinkedHashMap<>();
        for (LetterGrade g : LetterGrade.values()) {
            byGrade.put(g.name(), 0L);
        }
        for (Object[] row : reportRepository.countGroupByGrade()) {
            LetterGrade grade = (LetterGrade) row[0];
            byGrade.put(grade.name(), (Long) row[1]);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("byStatus", byStatus);
        response.put("byGrade", byGrade);
        return ResponseEntity.ok(response);
    }
}
