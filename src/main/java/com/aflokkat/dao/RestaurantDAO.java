package com.aflokkat.dao;

import java.util.List;
import java.util.Map;

import com.aflokkat.aggregation.AggregationCount;
import com.aflokkat.aggregation.BoroughCuisineScore;
import com.aflokkat.aggregation.CuisineScore;
import com.aflokkat.domain.Restaurant;

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
    Map<String, Long> getStatisticsByBorough();
    
    /**
     * USE CASE 1: Compte les restaurants par quartier
     */
    List<AggregationCount> getRestaurantCountByBorough();
    
    /**
     * USE CASE 2: Calcule le score moyen par quartier pour une cuisine donnée
     */
    List<BoroughCuisineScore> getAverageScoreByCuisineAndBorough(String cuisine);
    
    /**
     * USE CASE 3: Retourne les pires cuisines (score moyen le plus bas) dans un quartier
     */
    List<CuisineScore> getWorstCuisinesByAverageScoreInBorough(String borough, int limit);

    /**
     * USE CASE 3 (global): Pires cuisines tous quartiers confondus
     */
    List<CuisineScore> getWorstCuisinesByAverageScore(int limit);

    /**
     * USE CASE 4: Retourne les cuisines avec un minimum de restaurants
     */
    List<String> getCuisinesWithMinimumCount(int minCount);

    /**
     * Retourne la liste de tous les types de cuisine distincts, triés alphabétiquement
     */
    List<String> getDistinctCuisines();
    
    /**
     * Ferme la connexion
     */
    void close();
}
