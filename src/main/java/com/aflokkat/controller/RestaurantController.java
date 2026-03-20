package com.aflokkat.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aflokkat.cache.RestaurantCacheService;
import com.aflokkat.aggregation.AggregationCount;
import com.aflokkat.aggregation.BoroughCuisineScore;
import com.aflokkat.aggregation.CuisineScore;
import com.aflokkat.dto.TopRestaurantEntry;
import com.aflokkat.domain.Restaurant;
import com.aflokkat.service.RestaurantService;
import com.aflokkat.sync.SyncResult;
import com.aflokkat.sync.SyncService;

/**
 * REST API Controller pour l'accès aux données MongoDB
 */
@RestController
@RequestMapping("/api/restaurants")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RestaurantController {
    
    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private SyncService syncService;

    @Autowired
    private RestaurantCacheService cacheService;
    
    /**
     * USE CASE 1: Nombre de restaurants par quartier
     */
    @GetMapping("/by-borough")
    public ResponseEntity<Map<String, Object>> getByBorough() {
        try {
            List<AggregationCount> data = cacheService.getOrLoad(
                    RestaurantCacheService.KEY_BY_BOROUGH,
                    restaurantService::getRestaurantCountByBorough,
                    new com.fasterxml.jackson.core.type.TypeReference<List<AggregationCount>>() {});
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }
    
    /**
     * USE CASE 2: Score moyen par quartier pour une cuisine donnée
     */
    @GetMapping("/cuisine-scores")
    public ResponseEntity<Map<String, Object>> getCuisineScores(
            @RequestParam(defaultValue = "Italian") String cuisine) {
        try {
            List<BoroughCuisineScore> data = cacheService.getOrLoad(
                    RestaurantCacheService.KEY_CUISINE_SCORES_PREFIX + cuisine,
                    () -> restaurantService.getAverageScoreByCuisineAndBorough(cuisine),
                    new com.fasterxml.jackson.core.type.TypeReference<List<BoroughCuisineScore>>() {});
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("cuisine", cuisine);
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }
    
    /**
     * USE CASE 3: Pires cuisines dans un quartier
     */
    @GetMapping("/worst-cuisines")
    public ResponseEntity<Map<String, Object>> getWorstCuisines(
            @RequestParam(defaultValue = "Manhattan") String borough,
            @RequestParam(defaultValue = "5") int limit) {
        try {
            List<CuisineScore> data = cacheService.getOrLoad(
                    RestaurantCacheService.KEY_WORST_CUISINES_PREFIX + borough + ":" + limit,
                    () -> restaurantService.getWorstCuisinesByAverageScoreInBorough(borough, limit),
                    new com.fasterxml.jackson.core.type.TypeReference<List<CuisineScore>>() {});
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("borough", borough);
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }
    
    /**
     * USE CASE 4: Cuisines avec minimum de restaurants
     */
    @GetMapping("/popular-cuisines")
    public ResponseEntity<Map<String, Object>> getPopularCuisines(
            @RequestParam(defaultValue = "500") int minCount) {
        try {
            List<String> data = restaurantService.getCuisinesWithMinimumCount(minCount);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("minCount", minCount);
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }
    
    /**
     * Statistiques générales
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("totalRestaurants", restaurantService.countAll());
            response.put("boroughStats", restaurantService.getStatisticsByBorough());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }
    
    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "API is running");
        return ResponseEntity.ok(response);
    }
    
    /**
     * TRASH ADVISOR: Obtient les pires restaurants avec leurs coordonnées GPS
     */
    @GetMapping("/trash-advisor")
    public ResponseEntity<Map<String, Object>> getTrashAdvisor(
            @RequestParam(required = false) String borough,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Double maxScore,
            @RequestParam(defaultValue = "25") int restaurantLimit,
            @RequestParam(required = false) String cuisine) {
        try {
            List<Restaurant> restaurants = restaurantService.getTrashAdvisorRestaurants(borough, limit, maxScore, restaurantLimit, cuisine);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("borough", borough);
            response.put("data", restaurants);
            response.put("count", restaurants.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Tous les types de cuisine distincts (pour alimenter les filtres)
     */
    @GetMapping("/cuisines")
    public ResponseEntity<Map<String, Object>> getCuisines() {
        try {
            List<String> data = restaurantService.getDistinctCuisines();
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Top N cuisines par nombre de restaurants
     */
    @GetMapping("/by-cuisine")
    public ResponseEntity<Map<String, Object>> getByCuisine(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<AggregationCount> data = restaurantService.getTopCuisinesByCount(limit);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }
    
    /**
     * Triggers a manual data sync from the NYC Open Data API.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        try {
            SyncResult result = syncService.runSync();
            Map<String, Object> response = new HashMap<>();
            response.put("status", result.isSuccess() ? "success" : "error");
            response.put("rawRecords", result.getRawRecords());
            response.put("upsertedRestaurants", result.getUpsertedRestaurants());
            response.put("startedAt", result.getStartedAt());
            response.put("completedAt", result.getCompletedAt());
            if (!result.isSuccess()) response.put("error", result.getErrorMessage());
            int httpStatus = result.isSuccess() ? 200 : 502;
            return ResponseEntity.status(httpStatus).body(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Returns the status of the last data sync.
     */
    @GetMapping("/sync-status")
    public ResponseEntity<Map<String, Object>> syncStatus() {
        SyncResult last = syncService.getLastResult();
        Map<String, Object> response = new HashMap<>();
        if (last == null) {
            response.put("status", "never_run");
            response.put("message", "No sync has been executed yet");
        } else {
            response.put("status", last.isSuccess() ? "success" : "error");
            response.put("rawRecords", last.getRawRecords());
            response.put("upsertedRestaurants", last.getUpsertedRestaurants());
            response.put("startedAt", last.getStartedAt());
            response.put("completedAt", last.getCompletedAt());
            if (!last.isSuccess()) response.put("error", last.getErrorMessage());
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Top N healthiest restaurants from the Redis sorted set (lowest inspection score).
     */
    @GetMapping("/top")
    public ResponseEntity<Map<String, Object>> getTop(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<TopRestaurantEntry> data = cacheService.getTopRestaurants(limit);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

        private ResponseEntity<Map<String, Object>> errorResponse(Exception e) {
        int status = (e instanceof IllegalArgumentException) ? 400 : 500;
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", e.getMessage());
        return ResponseEntity.status(status).body(response);
    }
}
