package com.aflokkat.controller;

import com.aflokkat.dto.AtRiskEntry;
import com.aflokkat.aggregation.CuisineScore;
import com.aflokkat.dao.RestaurantDAO;
import com.aflokkat.util.ResponseUtil;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public analytics endpoints — no authentication required.
 * All endpoints fall under anyRequest().permitAll() in SecurityConfig.
 * Do NOT add @PreAuthorize to any method in this controller.
 *
 * Uses RestaurantDAO directly (no service wrapper) — consistent with RestaurantController
 * search/map-points pattern where there is no business logic. RestaurantService cannot be
 * mocked on Java 25 (constructor-injection Mockito limitation).
 */
@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AnalyticsController {

    @Autowired
    private RestaurantDAO restaurantDAO;

    /**
     * STAT-01: Returns 4 city-wide KPI values in a single response.
     */
    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> getKpis() {
        try {
            long total = restaurantDAO.countAll();
            long atRisk = restaurantDAO.countAtRiskRestaurants();

            // Compute percentGradeA from borough grade distribution data
            List<Document> boroughData = restaurantDAO.findBoroughGradeDistribution();
            long totalA = 0, totalABC = 0;
            for (Document borough : boroughData) {
                @SuppressWarnings("unchecked")
                List<Document> grades = (List<Document>) borough.get("grades");
                if (grades != null) {
                    for (Document g : grades) {
                        long count = g.getInteger("count", 0);
                        totalABC += count;
                        if ("A".equals(g.getString("grade"))) {
                            totalA += count;
                        }
                    }
                }
            }
            double percentGradeA = (totalABC > 0) ? ((double) totalA / totalABC * 100.0) : 0.0;

            // avgScore: weighted average from top-200 cuisines by lowest score (ascending sort)
            List<CuisineScore> allCuisines = restaurantDAO.findWorstCuisinesByAverageScore(200);
            double avgScore = 0.0;
            if (!allCuisines.isEmpty()) {
                double sum = 0;
                int cnt = 0;
                for (CuisineScore cs : allCuisines) {
                    if (cs.getAvgScore() != null && cs.getCount() != null) {
                        sum += cs.getAvgScore() * cs.getCount();
                        cnt += cs.getCount();
                    }
                }
                avgScore = (cnt > 0) ? sum / cnt : 0.0;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("totalRestaurants", total);
            response.put("percentGradeA", Math.round(percentGradeA * 10.0) / 10.0);
            response.put("avgScore", Math.round(avgScore * 10.0) / 10.0);
            response.put("atRiskCount", atRisk);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseUtil.errorResponse(e);
        }
    }

    /**
     * STAT-02: Returns per-borough grade distribution (A, B, C only).
     */
    @GetMapping("/borough-grades")
    public ResponseEntity<Map<String, Object>> getBoroughGrades() {
        try {
            List<Document> raw = restaurantDAO.findBoroughGradeDistribution();
            List<Map<String, Object>> data = new ArrayList<>();
            for (Document doc : raw) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("borough", doc.getString("_id"));
                @SuppressWarnings("unchecked")
                List<Document> grades = (List<Document>) doc.get("grades");
                List<Map<String, Object>> gradeList = new ArrayList<>();
                if (grades != null) {
                    for (Document g : grades) {
                        Map<String, Object> gm = new HashMap<>();
                        gm.put("grade", g.getString("grade"));
                        gm.put("count", g.getInteger("count", 0));
                        gradeList.add(gm);
                    }
                }
                entry.put("grades", gradeList);
                data.add(entry);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseUtil.errorResponse(e);
        }
    }

    /**
     * STAT-03: Returns top 10 cleanest (lowest avg score) and top 10 worst (highest avg score) cuisines.
     * - "best" list: uses findWorstCuisinesByAverageScore (sorts avgScore ASC = lowest = cleanest)
     * - "worst" list: uses findBestCuisinesByAverageScore (sorts avgScore DESC = highest = worst)
     */
    @GetMapping("/cuisine-rankings")
    public ResponseEntity<Map<String, Object>> getCuisineRankings() {
        try {
            List<CuisineScore> best = restaurantDAO.findWorstCuisinesByAverageScore(10);
            List<CuisineScore> worst = restaurantDAO.findBestCuisinesByAverageScore(10);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("best", best);
            response.put("worst", worst);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseUtil.errorResponse(e);
        }
    }

    /**
     * STAT-04: Returns the 50 most at-risk restaurants (last grade C or Z).
     */
    @GetMapping("/at-risk")
    public ResponseEntity<Map<String, Object>> getAtRisk() {
        try {
            List<AtRiskEntry> entries = restaurantDAO.findAtRiskRestaurants(null, 50);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", entries);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseUtil.errorResponse(e);
        }
    }
}
