package com.st4r4x.dao;

import java.util.List;
import java.util.Map;

import com.st4r4x.aggregation.AggregationCount;
import com.st4r4x.aggregation.BoroughCuisineScore;
import com.st4r4x.aggregation.CuisineScore;
import com.st4r4x.domain.Restaurant;
import com.st4r4x.dto.HeatmapPoint;

public interface RestaurantDAO {
    /**
     * Returns all restaurants up to the given limit
     */
    List<Restaurant> findAll(int limit);
    
    /**
     * Returns restaurants of the specified cuisine type
     */
    List<Restaurant> findByCuisine(String cuisine, int limit);
    
    /**
     * Search with multiple filters
     */
    List<Restaurant> findWithFilters(Map<String, Object> filters, int limit);
    
    /**
     * Aggregates restaurants by a given field and sorts by count descending
     */
    List<AggregationCount> countByField(String fieldName);
    
    /**
     * Returns the total restaurant count
     */
    long countAll();
    
    /**
     * Returns the restaurant count for a specific cuisine
     */
    long countByCuisine(String cuisine);
    
    /**
     * Returns statistics per borough
     */
    Map<String, Long> findStatisticsByBorough();
    
    /**
     * USE CASE 1: Count restaurants per borough
     */
    List<AggregationCount> findCountByBorough();
    
    /**
     * USE CASE 2: Compute average inspection score per borough for a given cuisine
     */
    List<BoroughCuisineScore> findAverageScoreByCuisineAndBorough(String cuisine);
    
    /**
     * USE CASE 3: Return the worst cuisines (highest average score) in a borough
     */
    List<CuisineScore> findWorstCuisinesByAverageScoreInBorough(String borough, int limit);

    /**
     * USE CASE 4: Return cuisine types with at least minCount restaurants
     */
    List<String> findCuisinesWithMinimumCount(int minCount);

    /**
     * Returns all distinct cuisine types, sorted alphabetically
     */
    List<String> findDistinctCuisines();

    /**
     * Returns a random restaurant via $sample
     */
    Restaurant findRandom();

    /**
     * Returns N randomly-selected restaurants via $sample aggregation.
     */
    List<Restaurant> findSampleRestaurants(int limit);

    /**
     * Returns restaurants matching a list of IDs (restaurant_id / camis)
     */
    List<Restaurant> findByIds(List<String> restaurantIds);

    /**
     * Finds a restaurant by its restaurant_id field.
     */
    Restaurant findByRestaurantId(String restaurantId);

    /**
     * Returns restaurants that had at least one inspection in the last N days.
     */
    List<Restaurant> findRecentlyInspected(int days, int limit);

    /**
     * Returns restaurants within radiusMeters of the given lat/lng coordinates.
     * Requires a 2dsphere index on address.coord.
     */
    List<Restaurant> findNearby(double lat, double lng, int radiusMeters, int limit);

    /**
     * Returns heatmap data points (lat, lng, weight=score) for the map overlay.
     * @param borough optional borough filter (null = all)
     */
    List<HeatmapPoint> findHeatmapData(String borough, int limit);
}
