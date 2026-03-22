package com.aflokkat.controller;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aflokkat.dto.AtRiskEntry;
import com.aflokkat.service.RestaurantService;
import com.aflokkat.util.ResponseUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Inspection", description = "Inspection agent tools — admin only")
@RestController
@RequestMapping("/api/inspection")
@PreAuthorize("hasRole('ADMIN')")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class InspectionController {

    @Autowired
    private RestaurantService restaurantService;

    @Operation(summary = "At-risk restaurants", description = "Returns restaurants whose last grade is C or Z. Admin only.")
    @GetMapping("/at-risk")
    public ResponseEntity<Map<String, Object>> getAtRisk(
            @RequestParam(required = false) String borough,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            List<AtRiskEntry> data = restaurantService.getAtRiskRestaurants(borough, limit);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Export at-risk restaurants as CSV", description = "Downloads at-risk restaurants as a CSV file. Admin only.")
    @GetMapping("/at-risk/export.csv")
    public ResponseEntity<byte[]> exportAtRiskCsv(
            @RequestParam(required = false) String borough,
            @RequestParam(defaultValue = "500") int limit) {
        try {
            List<AtRiskEntry> data = restaurantService.getAtRiskRestaurants(borough, limit);
            StringBuilder sb = new StringBuilder();
            sb.append("restaurant_id,name,borough,cuisine,lastGrade,lastScore,consecutiveBadGrades\n");
            for (AtRiskEntry e : data) {
                sb.append(csvField(e.getRestaurantId())).append(",");
                sb.append(csvField(e.getName())).append(",");
                sb.append(csvField(e.getBorough())).append(",");
                sb.append(csvField(e.getCuisine())).append(",");
                sb.append(csvField(e.getLastGrade())).append(",");
                sb.append(e.getLastScore() != null ? e.getLastScore() : "").append(",");
                sb.append(e.getConsecutiveBadGrades() != null ? e.getConsecutiveBadGrades() : "").append("\n");
            }
            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"at-risk-restaurants.csv\"");
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    private String csvField(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private ResponseEntity<Map<String, Object>> errorResponse(Exception e) {
        return ResponseUtil.errorResponse(e);
    }
}
