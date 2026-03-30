package com.aflokkat.controller;

import com.aflokkat.dao.RestaurantDAO;
import com.aflokkat.domain.Restaurant;
import com.aflokkat.dto.ReportRequest;
import com.aflokkat.entity.InspectionReportEntity;
import com.aflokkat.entity.Status;
import com.aflokkat.entity.UserEntity;
import com.aflokkat.repository.ReportRepository;
import com.aflokkat.repository.UserRepository;
import com.aflokkat.util.ResponseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired private ReportRepository reportRepository;
    @Autowired private RestaurantDAO restaurantDAO;
    @Autowired private UserRepository userRepository;

    // ── Auth helper ────────────────────────────────────────────────────────
    private UserEntity getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    // ── Response helper ────────────────────────────────────────────────────
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

    // ── POST /api/reports ──────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Map<String, Object>> createReport(@RequestBody ReportRequest req) {
        try {
            if (req.getRestaurantId() == null || req.getRestaurantId().isBlank()) {
                throw new IllegalArgumentException("restaurantId is required");
            }
            if (req.getGrade() == null) {
                throw new IllegalArgumentException("grade is required");
            }
            UserEntity currentUser = getCurrentUser();

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

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", toResponseMap(saved));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseUtil.errorResponse(e);
        }
    }

    // ── GET /api/reports ───────────────────────────────────────────────────
    @GetMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> listReports(
            @RequestParam(required = false) Status status) {
        try {
            UserEntity currentUser = getCurrentUser();
            List<InspectionReportEntity> entities = (status != null)
                    ? reportRepository.findByUserIdAndStatus(currentUser.getId(), status)
                    : reportRepository.findByUserId(currentUser.getId());

            List<Map<String, Object>> data = new ArrayList<>();
            for (InspectionReportEntity e : entities) {
                data.add(toResponseMap(e));
            }
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseUtil.errorResponse(e);
        }
    }

    // ── PATCH /api/reports/{id} ────────────────────────────────────────────
    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> patchReport(
            @PathVariable Long id,
            @RequestBody ReportRequest req) {
        try {
            UserEntity currentUser = getCurrentUser();
            InspectionReportEntity report = reportRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Report not found"));

            // Ownership check — build 403 response manually (do NOT throw)
            if (!report.getUser().getId().equals(currentUser.getId())) {
                Map<String, Object> body = new HashMap<>();
                body.put("status", "error");
                body.put("message", "Forbidden");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
            }

            // Partial update — only apply non-null fields; restaurantId is ignored
            if (req.getGrade()          != null) { report.setGrade(req.getGrade()); }
            if (req.getStatus()         != null) { report.setStatus(req.getStatus()); }
            if (req.getViolationCodes() != null) { report.setViolationCodes(req.getViolationCodes()); }
            if (req.getNotes()          != null) { report.setNotes(req.getNotes()); }
            report.setUpdatedAt(new Date());
            reportRepository.save(report);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", toResponseMap(report));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseUtil.errorResponse(e);
        }
    }
}
