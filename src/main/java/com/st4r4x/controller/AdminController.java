package com.st4r4x.controller;

import com.st4r4x.entity.InspectionGrade;
import com.st4r4x.entity.Status;
import com.st4r4x.repository.ReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for ADMIN-only operations.
 * Provides aggregate report statistics across all controllers (ADM-03).
 * Individual report data is NOT returned — only counts grouped by enum values.
 */
@RestController
@RequestMapping("/api/reports")
public class AdminController {

    @Autowired
    private ReportRepository reportRepository;

    /**
     * GET /api/reports/stats — aggregate counts by status and grade across ALL controllers.
     * ADMIN role required. Does NOT filter by userId — intentionally aggregates all reports.
     * All enum values (OPEN/IN_PROGRESS/RESOLVED, A/B/C/F) are always present in the response,
     * defaulting to 0 when no reports have that value.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/stats")
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

        // Pre-populate all expected InspectionGrade enum values with 0 so missing values default correctly
        Map<String, Long> byGrade = new LinkedHashMap<>();
        for (InspectionGrade g : InspectionGrade.values()) {
            byGrade.put(g.name(), 0L);
        }
        for (Object[] row : reportRepository.countGroupByGrade()) {
            InspectionGrade grade = (InspectionGrade) row[0];
            byGrade.put(grade.name(), (Long) row[1]);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("byStatus", byStatus);
        response.put("byGrade", byGrade);
        return ResponseEntity.ok(response);
    }
}
