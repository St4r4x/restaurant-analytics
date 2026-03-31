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
     * Récupère tous les restaurants avec limite
     */
    List<Restaurant> findAll(int limit);
    
    /**
     * Récupère les restaurants de type de cuisine spécifiée
     */
    List<Restaurant> findByCuisine(String cuisine, int limit);
    
    /**
     * Recherche avec filtres multiples
     */
    List<Restaurant> findWithFilters(Map<String, Object> filters, int limit);
    
    /**
     * Agrège les restaurants par un champ spécifié et les trie par nombre décroissant
     */
    List<AggregationCount> countByField(String fieldName);
    
    /**
     * Compte le nombre total de restaurants
     */
    long countAll();
    
    /**
     * Compte le nombre de restaurants pour une cuisine spécifique
     */
    long countByCuisine(String cuisine);
    
    /**
     * Retourne les statistiques par quartier
     */
    Map<String, Long> findStatisticsByBorough();
    
    /**
     * USE CASE 1: Compte les restaurants par quartier
     */
    List<AggregationCount> findCountByBorough();
    
    /**
     * USE CASE 2: Calcule le score moyen par quartier pour une cuisine donnée
     */
    List<BoroughCuisineScore> findAverageScoreByCuisineAndBorough(String cuisine);
    
    /**
     * USE CASE 3: Retourne les pires cuisines (score moyen le plus bas) dans un quartier
     */
    List<CuisineScore> findWorstCuisinesByAverageScoreInBorough(String borough, int limit);

    /**
     * USE CASE 3 (global): Pires cuisines tous quartiers confondus
     */
    List<CuisineScore> findWorstCuisinesByAverageScore(int limit);

    /**
     * USE CASE 4: Retourne les cuisines avec un minimum de restaurants
     */
    List<String> findCuisinesWithMinimumCount(int minCount);

    /**
     * Retourne la liste de tous les types de cuisine distincts, triés alphabétiquement
     */
    List<String> findDistinctCuisines();

    /**
     * Retourne un restaurant aléatoire via $sample
     */
    Restaurant findRandom();

    /**
     * Upserts a batch of restaurants keyed by restaurantId (camis).
     * Inserts if not present, replaces if already exists.
     *
     * @return number of documents upserted
     */
    int upsertRestaurants(List<Restaurant> restaurants);

    /**
     * Retourne les restaurants correspondant à une liste d'IDs (restaurant_id / camis)
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
     * Searches restaurants by name or street address using a case-insensitive $regex.
     * Returns at most {@code limit} results.
     */
    List<Restaurant> searchByNameOrAddress(String q, int limit);

    /**
     * Returns lightweight map points for all restaurants with coordinates.
     * Each Document contains: restaurantId, name, lat, lng, grade.
     */
    List<org.bson.Document> findMapPoints();

    /**
     * Ferme la connexion
     */
    void close();
}
