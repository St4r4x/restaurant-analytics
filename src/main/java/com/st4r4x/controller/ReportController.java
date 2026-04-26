package com.st4r4x.controller;

import com.st4r4x.config.AppConfig;
import com.st4r4x.dao.RestaurantDAO;
import com.st4r4x.domain.Restaurant;
import com.st4r4x.dto.ReportRequest;
import com.st4r4x.entity.InspectionReportEntity;
import com.st4r4x.entity.Status;
import com.st4r4x.entity.UserEntity;
import com.st4r4x.repository.ReportRepository;
import com.st4r4x.repository.UserRepository;
import com.st4r4x.util.ResponseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    // ── POST /api/reports/{id}/photo ──────────────────────────────────────────
    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<Map<String, Object>> uploadPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            UserEntity currentUser = getCurrentUser();
            InspectionReportEntity report = reportRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Report not found"));

            // Ownership check
            if (!report.getUser().getId().equals(currentUser.getId())) {
                Map<String, Object> body = new HashMap<>();
                body.put("status", "error");
                body.put("message", "Forbidden");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
            }

            // Save file to {uploadsDir}/{reportId}/{timestamp}_{originalFilename}
            String uploadsDir = AppConfig.getUploadsDir();
            Path targetDir = Paths.get(uploadsDir, String.valueOf(id));
            Files.createDirectories(targetDir);  // idempotent
            String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path targetPath = targetDir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Update entity
            report.setPhotoPath(targetPath.toString());
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

    // ── GET /api/reports/{id}/photo ───────────────────────────────────────────
    @GetMapping("/{id}/photo")
    @Transactional
    public ResponseEntity<Resource> getPhoto(@PathVariable Long id) {
        try {
            InspectionReportEntity report = reportRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Report not found"));

            if (report.getPhotoPath() == null) {
                return ResponseEntity.notFound().build();
            }

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
        } catch (Exception e) {
            // Return 404 for file not found or unexpected errors
            return ResponseEntity.notFound().build();
        }
    }
}
