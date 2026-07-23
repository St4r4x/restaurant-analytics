package com.st4r4x.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.st4r4x.config.AppConfig;
import com.st4r4x.dao.RestaurantDAO;
import com.st4r4x.domain.Restaurant;
import com.st4r4x.entity.AuditAction;
import com.st4r4x.entity.AuditLogEntity;
import com.st4r4x.entity.BookmarkEntity;
import com.st4r4x.entity.InspectionReportEntity;
import com.st4r4x.entity.UserEntity;
import com.st4r4x.repository.AuditLogRepository;
import com.st4r4x.repository.BookmarkRepository;
import com.st4r4x.repository.ReportRepository;
import com.st4r4x.repository.UserRepository;
import com.st4r4x.service.AuditService;
import com.st4r4x.util.ResponseUtil;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private RestaurantDAO restaurantDAO;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditService auditService;

    private UserEntity getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    /**
     * Deletes {uploadsDir}/{reportId}/ recursively. Best-effort: a missing or
     * already-deleted directory must not block account deletion.
     */
    private void deletePhotoDirectory(Long reportId) {
        Path dir = Paths.get(AppConfig.getUploadsDir(), String.valueOf(reportId));
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    logger.warn("Failed to delete {} during account deletion: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warn("Failed to walk photo directory {} during account deletion: {}", dir, e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getProfile() {
        try {
            UserEntity user = getCurrentUser();
            Map<String, Object> data = new HashMap<>();
            data.put("id", user.getId());
            data.put("username", user.getUsername());
            data.put("email", user.getEmail());
            data.put("role", user.getRole());
            data.put("createdAt", user.getCreatedAt());
            long bookmarkCount = bookmarkRepository.countByUserId(user.getId());
            Long reportCount = null;
            if ("ROLE_CONTROLLER".equals(user.getRole())) {
                reportCount = reportRepository.countByUserId(user.getId());
            }
            data.put("bookmarkCount", bookmarkCount);
            data.put("reportCount", reportCount);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseUtil.errorResponse(e);
        }
    }

    @GetMapping("/me/bookmarks")
    public ResponseEntity<Map<String, Object>> getBookmarks() {
        try {
            UserEntity user = getCurrentUser();
            List<BookmarkEntity> bookmarks = bookmarkRepository.findByUserId(user.getId());
            List<String> restaurantIds = bookmarks.stream()
                    .map(BookmarkEntity::getRestaurantId)
                    .collect(Collectors.toList());
            List<Restaurant> restaurants = restaurantIds.isEmpty()
                    ? Collections.emptyList()
                    : restaurantDAO.findByIds(restaurantIds);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", restaurants);
            response.put("count", restaurants.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseUtil.errorResponse(e);
        }
    }

    @PostMapping("/me/bookmarks/{restaurantId}")
    public ResponseEntity<Map<String, Object>> addBookmark(@PathVariable String restaurantId) {
        try {
            UserEntity user = getCurrentUser();
            Map<String, Object> response = new HashMap<>();
            if (bookmarkRepository.existsByUserIdAndRestaurantId(user.getId(), restaurantId)) {
                response.put("status", "success");
                response.put("message", "Already bookmarked");
                return ResponseEntity.ok(response);
            }
            bookmarkRepository.save(new BookmarkEntity(user, restaurantId));
            response.put("status", "success");
            response.put("message", "Bookmark added");
            response.put("restaurantId", restaurantId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseUtil.errorResponse(e);
        }
    }

    @DeleteMapping("/me")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteAccount() {
        try {
            UserEntity user = getCurrentUser();
            String username = user.getUsername();

            auditService.log(AuditAction.USER_DELETED, "User", username, null);

            List<InspectionReportEntity> reports = reportRepository.findByUserId(user.getId());
            for (InspectionReportEntity report : reports) {
                deletePhotoDirectory(report.getId());
            }
            reportRepository.deleteAll(reports);

            List<BookmarkEntity> bookmarks = bookmarkRepository.findByUserId(user.getId());
            bookmarkRepository.deleteAll(bookmarks);

            List<AuditLogEntity> priorEntries = auditLogRepository.findByActorUsername(username);
            for (AuditLogEntity entry : priorEntries) {
                entry.setActorUsername("[deleted-user]");
            }
            auditLogRepository.saveAll(priorEntries);

            userRepository.delete(user);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Account deleted");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseUtil.errorResponse(e);
        }
    }

    @DeleteMapping("/me/bookmarks/{restaurantId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> removeBookmark(@PathVariable String restaurantId) {
        try {
            UserEntity user = getCurrentUser();
            bookmarkRepository.deleteByUserIdAndRestaurantId(user.getId(), restaurantId);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Bookmark removed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseUtil.errorResponse(e);
        }
    }
}
