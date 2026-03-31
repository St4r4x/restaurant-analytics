package com.aflokkat.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.aflokkat.cache.RestaurantCacheService;
import com.aflokkat.dao.RestaurantDAO;
import com.aflokkat.aggregation.AggregationCount;
import com.aflokkat.aggregation.BoroughCuisineScore;
import com.aflokkat.aggregation.CuisineScore;
import com.aflokkat.dto.HeatmapPoint;
import com.aflokkat.dto.TopRestaurantEntry;
import com.aflokkat.domain.Restaurant;
import com.aflokkat.service.RestaurantService;
import com.aflokkat.sync.SyncResult;
import com.aflokkat.sync.SyncService;
import com.aflokkat.util.ResponseUtil;
import org.bson.Document;

/**
 * REST API Controller pour l'accès aux données MongoDB
 */
@Tag(name = "Restaurants", description = "NYC restaurant inspection data — analytics, sync and leaderboard")
@RestController
@RequestMapping("/api/restaurants")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RestaurantController {

    @Autowired
    private RestaurantDAO restaurantDAO;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private SyncService syncService;

    @Autowired
    private RestaurantCacheService cacheService;

    /**
     * USE CASE 1: Nombre de restaurants par quartier
     */
    @Operation(summary = "Restaurant count per borough", description = "Returns the number of restaurants in each NYC borough, sorted by count. Result is cached in Redis for 1 hour.")
    @GetMapping("/by-borough")
    public ResponseEntity<Map<String, Object>> getByBorough() {
        try {
            List<AggregationCount> data = cacheService.getOrLoadByBorough(
                    restaurantService::getRestaurantCountByBorough);
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
    @Operation(summary = "Average inspection score by borough for a cuisine", description = "For the given cuisine type, returns the average inspection score per borough. Lower score = better health. Cached in Redis.")
    @GetMapping("/cuisine-scores")
    public ResponseEntity<Map<String, Object>> getCuisineScores(
            @Parameter(description = "Cuisine type (e.g. Italian, Chinese, American)", example = "Italian") @RequestParam(defaultValue = "Italian") String cuisine) {
        try {
            List<BoroughCuisineScore> data = cacheService.getOrLoadCuisineScores(cuisine,
                    () -> restaurantService.getAverageScoreByCuisineAndBorough(cuisine));
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
    @Operation(summary = "Worst cuisines by average inspection score in a borough", description = "Returns the cuisine types with the highest average inspection score (most violations) in a given borough. Cached in Redis.")
    @GetMapping("/worst-cuisines")
    public ResponseEntity<Map<String, Object>> getWorstCuisines(
            @Parameter(description = "Borough name", example = "Manhattan") @RequestParam(defaultValue = "Manhattan") String borough,
            @Parameter(description = "Maximum number of cuisines to return") @RequestParam(defaultValue = "5") int limit) {
        try {
            List<CuisineScore> data = cacheService.getOrLoadWorstCuisines(borough, limit,
                    () -> restaurantService.getWorstCuisinesByAverageScoreInBorough(borough, limit));
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
    @Operation(summary = "Cuisines with a minimum restaurant count", description = "Returns cuisine types that have at least minCount restaurants in the dataset.")
    @GetMapping("/popular-cuisines")
    public ResponseEntity<Map<String, Object>> getPopularCuisines(
            @Parameter(description = "Minimum number of restaurants required") @RequestParam(defaultValue = "500") int minCount) {
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
    @Operation(summary = "Global statistics", description = "Total restaurant count and breakdown by borough.")
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
    @Operation(summary = "Health check")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "API is running");
        return ResponseEntity.ok(response);
    }

    /**
     * HYGIENE RADAR: Obtient les pires restaurants avec leurs coordonnées GPS
     */
    @Operation(summary = "Hygiene Radar", description = "Returns restaurants from the worst-scoring cuisines, optionally filtered by borough, cuisine and max score. Useful for finding the most violation-prone establishments.")
    @GetMapping("/hygiene-radar")
    public ResponseEntity<Map<String, Object>> getHygieneRadar(
            @RequestParam(required = false) String borough,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Double maxScore,
            @RequestParam(defaultValue = "25") int restaurantLimit,
            @RequestParam(required = false) String cuisine) {
        try {
            List<Restaurant> restaurants = restaurantService.getHygieneRadarRestaurants(borough, limit, maxScore, restaurantLimit, cuisine);
            List<Map<String, Object>> views = restaurants.stream().map(RestaurantService::toView).collect(Collectors.toList());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("borough", borough);
            response.put("data", views);
            response.put("count", views.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    /**
     * Tous les types de cuisine distincts (pour alimenter les filtres)
     */
    @Operation(summary = "All distinct cuisine types", description = "Returns a sorted list of all cuisine types present in the dataset. Useful for populating filter dropdowns.")
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
    @Operation(summary = "Top N cuisines by restaurant count")
    @GetMapping("/by-cuisine")
    public ResponseEntity<Map<String, Object>> getByCuisine(
            @Parameter(description = "Number of cuisines to return") @RequestParam(defaultValue = "10") int limit) {
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
    @Operation(summary = "Trigger manual data sync", description = "Fetches fresh data from the NYC Open Data API, upserts into MongoDB, and invalidates the Redis cache. Returns the sync result with record counts.")
    @PreAuthorize("hasRole('ADMIN')")
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
    @Operation(summary = "Last sync status", description = "Returns the result of the most recent data sync, or a never_run status if no sync has been executed.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/sync-status")
    public ResponseEntity<Map<String, Object>> syncStatus() {
        Map<String, Object> response = new HashMap<>();
        if (syncService.isRunning()) {
            response.put("status", "running");
            response.put("startedAt", syncService.getRunningStartedAt());
            response.put("message", "Sync in progress");
            return ResponseEntity.ok(response);
        }
        SyncResult last = syncService.getLastResult();
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
    @Operation(summary = "Top healthiest restaurants", description = "Returns the restaurants with the lowest inspection scores (best health) from the Redis sorted set. Updated on every sync.")
    @GetMapping("/top")
    public ResponseEntity<Map<String, Object>> getTop(
            @Parameter(description = "Number of restaurants to return") @RequestParam(defaultValue = "10") int limit) {
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

    @Operation(summary = "Random restaurant", description = "Returns a single restaurant picked at random from the dataset.")
    @GetMapping("/random")
    public ResponseEntity<Map<String, Object>> getRandom() {
        try {
            Restaurant data = restaurantService.getRandomRestaurant();
            Map<String, Object> response = new HashMap<>();
            if (data == null) {
                response.put("status", "error");
                response.put("message", "No restaurants in database");
                return ResponseEntity.status(404).body(response);
            }
            response.put("status", "success");
            response.put("data", RestaurantService.toView(data));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Restaurant detail", description = "Returns the full document for a single restaurant, including all grades and computed badge fields.")
    @GetMapping("/{restaurantId}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String restaurantId) {
        try {
            Restaurant data = restaurantService.getByRestaurantId(restaurantId);
            Map<String, Object> response = new HashMap<>();
            if (data == null) {
                response.put("status", "error");
                response.put("message", "Restaurant not found: " + restaurantId);
                return ResponseEntity.status(404).body(response);
            }
            response.put("status", "success");
            response.put("data", RestaurantService.toView(data));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Recently inspected restaurants", description = "Returns restaurants that had at least one inspection in the last N days.")
    @GetMapping("/recent-inspections")
    public ResponseEntity<Map<String, Object>> getRecentInspections(
            @RequestParam(defaultValue = "3650") int days,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Map<String, Object>> data = restaurantService.getRecentlyInspected(days, limit)
                    .stream().map(RestaurantService::toView).collect(Collectors.toList());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("days", days);
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Nearby restaurants", description = "Returns restaurants within the given radius (meters) around the provided coordinates. Requires a 2dsphere index.")
    @GetMapping("/nearby")
    public ResponseEntity<Map<String, Object>> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "500") int radius,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Map<String, Object>> data = restaurantService.getNearbyRestaurants(lat, lng, radius, limit)
                    .stream().map(RestaurantService::toView).collect(Collectors.toList());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Heatmap data", description = "Returns lat/lng/weight points for the violation heatmap overlay. Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/heatmap")
    public ResponseEntity<Map<String, Object>> getHeatmap(
            @RequestParam(required = false) String borough,
            @RequestParam(defaultValue = "500") int limit) {
        try {
            List<HeatmapPoint> data = restaurantService.getHeatmapData(borough, limit);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", data);
            response.put("count", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Rebuild Redis cache from MongoDB", description = "Reads all restaurants from MongoDB and repopulates the Redis leaderboard sorted set. Use when Redis is empty after a restart. Admin only.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/rebuild-cache")
    public ResponseEntity<Map<String, Object>> rebuildCache(
            @RequestParam(defaultValue = "25000") int limit) {
        try {
            List<Restaurant> restaurants = restaurantService.getAllRestaurants(limit);
            cacheService.invalidateAll();
            cacheService.updateTopRestaurants(restaurants);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("restaurantsProcessed", restaurants.size());
            response.put("message", "Cache rebuilt successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Search restaurants by name or address", description = "Case-insensitive regex search on restaurant name and street address. Returns at most limit results (default 20).")
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchRestaurants(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Restaurant> data = restaurantDAO.searchByNameOrAddress(q, limit);
            List<Map<String, Object>> views = data.stream()
                .map(RestaurantService::toView)
                .collect(Collectors.toList());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", views);
            response.put("count", views.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(e);
        }
    }

    @Operation(summary = "Map points for all restaurants", description = "Returns lightweight projection documents (restaurantId, name, lat, lng, grade) for all restaurants that have coordinates.")
    @GetMapping("/map-points")
    public ResponseEntity<Map<String, Object>> getMapPoints() {
        try {
            List<Document> data = restaurantDAO.findMapPoints();
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
        return ResponseUtil.errorResponse(e);
    }
}
