package com.aflokkat.dao;

import java.util.List;
import java.util.Map;

import com.aflokkat.aggregation.AggregationCount;
import com.aflokkat.aggregation.BoroughCuisineScore;
import com.aflokkat.aggregation.CuisineScore;
import com.aflokkat.domain.Restaurant;
import com.aflokkat.dto.AtRiskEntry;
import com.aflokkat.dto.HeatmapPoint;

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
     * USE CASE 3 (global): Worst cuisines across all boroughs
     */
    List<CuisineScore> findWorstCuisinesByAverageScore(int limit);

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
     * Upserts a batch of restaurants keyed by restaurantId (camis).
     * Inserts if not present, replaces if already exists.
     *
     * @return number of documents upserted
     */
    int upsertRestaurants(List<Restaurant> restaurants);

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

    /**
     * Returns restaurants with a bad last grade (C or Z).
     * @param borough optional borough filter (null = all)
     */
    List<AtRiskEntry> findAtRiskRestaurants(String borough, int limit);

    /**
     * Returns grade distribution per borough.
     * Each Document: {_id: "MANHATTAN", grades: [{grade: "A", count: 3200}, {grade: "B", count: 400}, ...]}
     * Only grades A, B, C are included.
     */
    List<org.bson.Document> findBoroughGradeDistribution();

    /**
     * Returns cuisines with the highest average inspection score (most violations = worst).
     * Sort: avgScore descending.
     */
    List<CuisineScore> findBestCuisinesByAverageScore(int limit);

    /**
     * Returns the count of restaurants with last grade C or Z.
     * Uses $count aggregation — does NOT load documents.
     */
    long countAtRiskRestaurants();

    /**
     * Searches restaurants by name or street address using a case-insensitive $regex.
     * Returns at most {@code limit} results.
     */
    List<Restaurant> searchByNameOrAddress(String q, int limit);

    /**
     * Returns restaurants with last grade C/Z OR not inspected in the past 12 months.
     * @param borough optional borough filter (null or empty = all boroughs)
     * @param limit   max results to return
     */
    List<com.aflokkat.dto.UncontrolledEntry> findUncontrolled(String borough, int limit);

    /**
     * Returns lightweight map points for all restaurants with coordinates.
     * Each Document contains: restaurantId, name, lat, lng, grade, borough, cuisine.
     */
    List<org.bson.Document> findMapPoints();

    /**
     * Closes the connection
     */
    void close();
}
