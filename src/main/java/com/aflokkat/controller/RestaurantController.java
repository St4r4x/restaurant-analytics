package com.aflokkat.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aflokkat.aggregation.AggregationCount;
import com.aflokkat.aggregation.BoroughCuisineScore;
import com.aflokkat.aggregation.CuisineScore;
import com.aflokkat.domain.Restaurant;
import com.aflokkat.service.RestaurantService;

/**
 * REST API Controller pour l'accès aux données MongoDB
 */
@RestController
@RequestMapping("/api/restaurants")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RestaurantController {
    
    @Autowired
    private RestaurantService restaurantService;
    
    /**
     * USE CASE 1: Nombre de restaurants par quartier
     */
    @GetMapping("/by-borough")
    public ResponseEntity<Map<String, Object>> getByBorough() {
        try {
            List<AggregationCount> data = restaurantService.getRestaurantCountByBorough();
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
            List<BoroughCuisineScore> data = restaurantService.getAverageScoreByCuisineAndBorough(cuisine);
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
            List<CuisineScore> data = restaurantService.getWorstCuisinesByAverageScoreInBorough(borough, limit);
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
    
    private ResponseEntity<Map<String, Object>> errorResponse(Exception e) {
        int status = (e instanceof IllegalArgumentException) ? 400 : 500;
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", e.getMessage());
        return ResponseEntity.status(status).body(response);
    }
}
