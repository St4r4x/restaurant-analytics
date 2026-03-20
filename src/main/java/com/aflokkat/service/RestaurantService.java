package com.aflokkat.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.aflokkat.aggregation.AggregationCount;
import com.aflokkat.aggregation.BoroughCuisineScore;
import com.aflokkat.aggregation.CuisineScore;
import com.aflokkat.dao.RestaurantDAO;
import com.aflokkat.domain.Restaurant;

/**
 * Service layer pour gérer la logique métier
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
     * USE CASE 1: Nombre de restaurants par quartier
     */
    public List<AggregationCount> getRestaurantCountByBorough() {
        return restaurantDAO.getRestaurantCountByBorough();
    }
    
    // =============== USE CASE 2 ===============
    
    /**
     * USE CASE 2: Score moyen par quartier pour une cuisine donnée
     */
    public List<BoroughCuisineScore> getAverageScoreByCuisineAndBorough(String cuisine) {
        return restaurantDAO.getAverageScoreByCuisineAndBorough(cuisine);
    }
    
    // =============== USE CASE 3 ===============
    
    /**
     * USE CASE 3: Pires cuisines (score moyen le plus bas) dans un quartier
     */
    public List<CuisineScore> getWorstCuisinesByAverageScoreInBorough(String borough, int limit) {
        return restaurantDAO.getWorstCuisinesByAverageScoreInBorough(borough, limit);
    }
    
    // =============== USE CASE 4 ===============
    
    /**
     * USE CASE 4: Cuisines avec minimum de restaurants
     */
    public List<String> getCuisinesWithMinimumCount(int minCount) {
        return restaurantDAO.getCuisinesWithMinimumCount(minCount);
    }
    
    // =============== GENERIC QUERIES ===============

    /**
     * Compte le nombre total de restaurants
     */
    public long countAll() {
        return restaurantDAO.countAll();
    }
    
    /**
     * Retourne les statistiques par quartier
     */
    public Map<String, Long> getStatisticsByBorough() {
        return restaurantDAO.getStatisticsByBorough();
    }
    
    // =============== TRASH ADVISOR ===============

    /**
     * Retourne les restaurants des pires cuisines avec leurs coordonnées GPS.
     * @param maxScore  score moyen maximum des cuisines à inclure (null = pas de filtre)
     * @param restaurantLimit  nombre maximum de restaurants retournés
     */
    public List<Restaurant> getTrashAdvisorRestaurants(String borough, int limit, Double maxScore, int restaurantLimit, String cuisineFilter) {
        boolean allBoroughs = (borough == null || borough.isEmpty());
        boolean hasCuisineFilter = (cuisineFilter != null && !cuisineFilter.isEmpty());

        // Quand une cuisine est explicitement choisie, on cherche directement ses restaurants
        if (hasCuisineFilter) {
            Map<String, Object> filters = new java.util.HashMap<>();
            if (!allBoroughs) filters.put("borough", borough);
            filters.put("cuisine", cuisineFilter);
            return restaurantDAO.findWithFilters(filters, restaurantLimit);
        }

        // Sinon : logique "pires cuisines"
        List<CuisineScore> worstCuisines = allBoroughs
                ? restaurantDAO.getWorstCuisinesByAverageScore(limit)
                : restaurantDAO.getWorstCuisinesByAverageScoreInBorough(borough, limit);

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
     * Retourne les N cuisines les plus représentées par nombre de restaurants
     */
    public List<AggregationCount> getTopCuisinesByCount(int limit) {
        return restaurantDAO.countByField("cuisine").stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Retourne tous les types de cuisine distincts, triés alphabétiquement
     */
    public List<String> getDistinctCuisines() {
        return restaurantDAO.getDistinctCuisines();
    }

    /**
     * Retourne un restaurant aléatoire via $sample MongoDB
     */
    public Restaurant getRandomRestaurant() {
        return restaurantDAO.findRandom();
    }
}
