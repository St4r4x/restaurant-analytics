package com.st4r4x.controller;

import com.st4r4x.entity.LetterGrade;
import com.st4r4x.entity.Status;
import com.st4r4x.repository.ReportRepository;
import com.st4r4x.sync.OsmEnrichmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private OsmEnrichmentService osmEnrichmentService;

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
        Map<String, Object> body = new HashMap<>();
        body.put("status", "accepted");
        body.put("message", "OSM enrichment started in background");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
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
