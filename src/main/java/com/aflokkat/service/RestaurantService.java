package com.aflokkat.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.aflokkat.aggregation.AggregationCount;
import com.aflokkat.aggregation.BoroughCuisineScore;
import com.aflokkat.aggregation.CuisineScore;
import com.aflokkat.dao.RestaurantDAO;
import com.aflokkat.domain.Address;
import com.aflokkat.domain.Grade;
import com.aflokkat.domain.Restaurant;
import com.aflokkat.dto.AtRiskEntry;
import com.aflokkat.dto.HeatmapPoint;
import com.aflokkat.util.ValidationUtil;

/**
 * Service layer for business logic
 */
@Service
public class RestaurantService {

    private final RestaurantDAO restaurantDAO;

    @Autowired
    public RestaurantService(RestaurantDAO restaurantDAO) {
        this.restaurantDAO = restaurantDAO;
    }
    
    // =============== USE CASE 1 ===============
    
    /**
     * USE CASE 1: Restaurant count per borough
     */
    public List<AggregationCount> getRestaurantCountByBorough() {
        return restaurantDAO.findCountByBorough();
    }
    
    // =============== USE CASE 2 ===============
    
    /**
     * USE CASE 2: Average inspection score per borough for a given cuisine
     */
    public List<BoroughCuisineScore> getAverageScoreByCuisineAndBorough(String cuisine) {
        ValidationUtil.requireNonEmpty(cuisine, "cuisine");
        return restaurantDAO.findAverageScoreByCuisineAndBorough(cuisine);
    }
    
    // =============== USE CASE 3 ===============
    
    /**
     * USE CASE 3: Worst cuisines (highest average inspection score) in a borough
     */
    public List<CuisineScore> getWorstCuisinesByAverageScoreInBorough(String borough, int limit) {
        ValidationUtil.requireNonEmpty(borough, "borough");
        ValidationUtil.requirePositive(limit, "limit");
        return restaurantDAO.findWorstCuisinesByAverageScoreInBorough(borough, limit);
    }
    
    // =============== USE CASE 4 ===============
    
    /**
     * USE CASE 4: Cuisines with a minimum restaurant count
     */
    public List<String> getCuisinesWithMinimumCount(int minCount) {
        ValidationUtil.requirePositive(minCount, "minCount");
        return restaurantDAO.findCuisinesWithMinimumCount(minCount);
    }
    
    // =============== GENERIC QUERIES ===============

    /**
     * Returns the total restaurant count
     */
    public long countAll() {
        return restaurantDAO.countAll();
    }
    
    /**
     * Returns aggregate statistics per borough
     */
    public Map<String, Long> getStatisticsByBorough() {
        return restaurantDAO.findStatisticsByBorough();
    }
    
    // =============== HYGIENE RADAR ===============

    /**
     * Returns restaurants from the worst cuisines, with GPS coordinates.
     * @param maxScore  maximum average cuisine score to include (null = no filter)
     * @param restaurantLimit  maximum number of restaurants returned
     */
    public List<Restaurant> getHygieneRadarRestaurants(String borough, int limit, Double maxScore, int restaurantLimit, String cuisineFilter) {
        ValidationUtil.requirePositive(limit, "limit");
        ValidationUtil.requirePositive(restaurantLimit, "restaurantLimit");
        boolean allBoroughs = (borough == null || borough.isEmpty());
        boolean hasCuisineFilter = (cuisineFilter != null && !cuisineFilter.isEmpty());

        // When a cuisine is explicitly chosen, search for its restaurants directly
        if (hasCuisineFilter) {
            Map<String, Object> filters = new java.util.HashMap<>();
            if (!allBoroughs) filters.put("borough", borough);
            filters.put("cuisine", cuisineFilter);
            return restaurantDAO.findWithFilters(filters, restaurantLimit);
        }

        // Otherwise: "worst cuisines" logic
        List<CuisineScore> worstCuisines = allBoroughs
                ? restaurantDAO.findWorstCuisinesByAverageScore(limit)
                : restaurantDAO.findWorstCuisinesByAverageScoreInBorough(borough, limit);

        if (maxScore != null) {
            worstCuisines = worstCuisines.stream()
                    .filter(c -> c.getAvgScore() != null && c.getAvgScore() <= maxScore)
                    .collect(Collectors.toList());
        }

        if (worstCuisines.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> cuisineNames = worstCuisines.stream()
                .map(CuisineScore::getCuisine)
                .collect(Collectors.toList());

        Map<String, Object> filters = new java.util.HashMap<>();
        if (!allBoroughs) filters.put("borough", borough);
        filters.put("cuisine", new org.bson.Document("$in", cuisineNames));
        return restaurantDAO.findWithFilters(filters, restaurantLimit);
    }

    // =============== TOP CUISINES ===============

    /**
     * Returns the top N cuisine types by restaurant count
     */
    public List<AggregationCount> getTopCuisinesByCount(int limit) {
        return restaurantDAO.countByField("cuisine").stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Returns all distinct cuisine types, sorted alphabetically
     */
    public List<String> getDistinctCuisines() {
        return restaurantDAO.findDistinctCuisines();
    }

    /**
     * Returns a random restaurant via MongoDB $sample
     */
    public Restaurant getRandomRestaurant() {
        return restaurantDAO.findRandom();
    }

    public List<Restaurant> getAllRestaurants(int limit) {
        ValidationUtil.requirePositive(limit, "limit");
        return restaurantDAO.findAll(limit);
    }

    public Restaurant getByRestaurantId(String restaurantId) {
        ValidationUtil.requireNonEmpty(restaurantId, "restaurantId");
        return restaurantDAO.findByRestaurantId(restaurantId);
    }

    public List<Restaurant> getRecentlyInspected(int days, int limit) {
        ValidationUtil.requirePositive(limit, "limit");
        return restaurantDAO.findRecentlyInspected(days, limit);
    }

    public List<Restaurant> getNearbyRestaurants(double lat, double lng, int radiusMeters, int limit) {
        ValidationUtil.requirePositive(radiusMeters, "radiusMeters");
        ValidationUtil.requirePositive(limit, "limit");
        return restaurantDAO.findNearby(lat, lng, radiusMeters, limit);
    }

    public List<HeatmapPoint> getHeatmapData(String borough, int limit) {
        ValidationUtil.requirePositive(limit, "limit");
        return restaurantDAO.findHeatmapData(borough, limit);
    }

    public List<AtRiskEntry> getAtRiskRestaurants(String borough, int limit) {
        ValidationUtil.requirePositive(limit, "limit");
        return restaurantDAO.findAtRiskRestaurants(borough, limit);
    }

    // ── Restaurant computed fields (business logic extracted from POJO) ───────

    private static Grade getLatestGradeEntry(Restaurant r) {
        List<Grade> grades = r.getGrades();
        if (grades == null || grades.isEmpty()) return null;
        return grades.stream()
                .filter(g -> g.getDate() != null)
                .max(Comparator.comparing(Grade::getDate))
                .orElse(null);
    }

    public static String getLatestGrade(Restaurant r) {
        Grade g = getLatestGradeEntry(r);
        return g != null ? g.getGrade() : null;
    }

    public static Integer getLatestScore(Restaurant r) {
        Grade g = getLatestGradeEntry(r);
        return (g != null && g.getScore() != null) ? g.getScore() : null;
    }

    public static String getTrend(Restaurant r) {
        List<Grade> grades = r.getGrades();
        if (grades == null || grades.size() < 2) return "stable";
        List<Grade> sorted = grades.stream()
                .filter(g -> g.getDate() != null && g.getScore() != null)
                .sorted(Comparator.comparing(Grade::getDate).reversed())
                .collect(Collectors.toList());
        if (sorted.size() < 2) return "stable";
        int recent = sorted.get(0).getScore();
        int prev = sorted.get(1).getScore();
        // Lower score = better (fewer violations)
        if (recent < prev - 5) return "improving";
        if (recent > prev + 5) return "worsening";
        return "stable";
    }

    public static String getBadgeColor(Restaurant r) {
        String g = getLatestGrade(r);
        if (g == null || g.isEmpty()) return "red";
        switch (g) {
            case "A": return "green";
            case "B": return "yellow";
            case "C": return "orange";
            default:  return "red";
        }
    }

    public static Double getLatitude(Restaurant r) {
        Address a = r.getAddress();
        if (a != null && a.getCoord() != null && a.getCoord().size() >= 2) {
            return a.getCoord().get(1); // GeoJSON: [longitude, latitude]
        }
        return null;
    }

    public static Double getLongitude(Restaurant r) {
        Address a = r.getAddress();
        if (a != null && a.getCoord() != null && a.getCoord().size() >= 2) {
            return a.getCoord().get(0); // GeoJSON: [longitude, latitude]
        }
        return null;
    }

    /**
     * Builds a view map for a restaurant, including all stored fields and computed ones.
     * Use this in controllers instead of serializing the Restaurant POJO directly.
     */
    public static Map<String, Object> toView(Restaurant r) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", r.getId() != null ? r.getId().toHexString() : null);
        map.put("restaurantId", r.getRestaurantId());
        map.put("name", r.getName());
        map.put("cuisine", r.getCuisine());
        map.put("borough", r.getBorough());
        map.put("address", r.getAddress());
        map.put("phone", r.getPhone());
        map.put("grades", r.getGrades());
        map.put("latestGrade", getLatestGrade(r));
        map.put("latestScore", getLatestScore(r));
        map.put("trend", getTrend(r));
        map.put("badgeColor", getBadgeColor(r));
        map.put("latitude", getLatitude(r));
        map.put("longitude", getLongitude(r));
        return map;
    }
}
