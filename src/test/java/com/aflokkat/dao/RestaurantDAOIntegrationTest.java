package com.aflokkat.dao;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.aflokkat.aggregation.AggregationCount;
import com.aflokkat.aggregation.BoroughCuisineScore;
import com.aflokkat.aggregation.CuisineScore;
import com.aflokkat.domain.Restaurant;

/**
 * Tests d'intégration pour les 4 use cases
 * 
 * NOTE: Ces tests nécessitent une connexion MongoDB actuelle
 * sur localhost:27017 avec la base de données "newyork"
 * 
 * Pour les exécuter: mvn verify -Dtest=RestaurantDAOIntegrationTest
 * 
 * Pour les ignorer en build normal: @Ignore
 */
public class RestaurantDAOIntegrationTest {
    
    private RestaurantDAO restaurantDAO;
    
    @Before
    public void setup() {
        restaurantDAO = new RestaurantDAOImpl();
    }
    
    @After
    public void teardown() {
        if (restaurantDAO != null) {
            restaurantDAO.close();
        }
    }
    
    // =============== USE CASE 1 TESTS ===============
    
    @Test
    public void testUseCase1_GetRestaurantCountByBorough_ReturnsData() {
        List<AggregationCount> results = restaurantDAO.getRestaurantCountByBorough();
        
        assertNotNull("Results should not be null", results);
        assertFalse("Results should not be empty", results.isEmpty());
        assertTrue("Should have at least 5 boroughs", results.size() >= 5);
    }
    
    @Test
    public void testUseCase1_CountByBorough_DataValidation() {
        List<AggregationCount> results = restaurantDAO.getRestaurantCountByBorough();
        
        // Check that all counts are positive
        for (AggregationCount count : results) {
            assertNotNull("Borough ID should not be null", count.getId());
            assertTrue("Count should be positive", count.getCount() > 0);
        }
    }
    
    @Test
    public void testUseCase1_CountByBorough_SortedDescending() {
        List<AggregationCount> results = restaurantDAO.getRestaurantCountByBorough();
        
        // Verify sorted in descending order
        for (int i = 1; i < results.size(); i++) {
            assertTrue(
                "Should be sorted descending",
                results.get(i - 1).getCount() >= results.get(i).getCount()
            );
        }
    }
    
    // =============== USE CASE 2 TESTS ===============
    
    @Test
    public void testUseCase2_GetAverageScoreByCuisineAndBorough_Italian() {
        List<BoroughCuisineScore> results = restaurantDAO.getAverageScoreByCuisineAndBorough("Italian");
        
        assertNotNull("Results should not be null", results);
        assertFalse("Results should not be empty", results.isEmpty());
    }
    
    @Test
    public void testUseCase2_AverageScore_ValidData() {
        List<BoroughCuisineScore> results = restaurantDAO.getAverageScoreByCuisineAndBorough("Italian");
        
        // Check that scores are valid
        for (BoroughCuisineScore score : results) {
            assertNotNull("Borough should not be null", score.getBorough());
            assertTrue("Average score should be positive", score.getAvgScore() > 0);
        }
    }
    
    @Test
    public void testUseCase2_AverageScore_InvalidCuisine() {
        List<BoroughCuisineScore> results = restaurantDAO.getAverageScoreByCuisineAndBorough("NonExistentCuisine12345");
        
        // Should return empty list, not null
        assertNotNull("Results should not be null", results);
    }
    
    // =============== USE CASE 3 TESTS ===============
    
    @Test
    public void testUseCase3_GetWorstCuisines_Manhattan() {
        List<CuisineScore> results = restaurantDAO.getWorstCuisinesByAverageScoreInBorough("Manhattan", 3);
        
        assertNotNull("Results should not be null", results);
        assertTrue("Should return top 3 worst", results.size() <= 3);
    }
    
    @Test
    public void testUseCase3_WorstCuisines_ValidData() {
        List<CuisineScore> results = restaurantDAO.getWorstCuisinesByAverageScoreInBorough("Manhattan", 5);
        
        for (CuisineScore score : results) {
            assertNotNull("Cuisine should not be null", score.getCuisine());
            assertTrue("Average score should be positive", score.getAvgScore() > 0);
            assertTrue("Count should be positive", score.getCount() > 0);
        }
    }
    
    @Test
    public void testUseCase3_WorstCuisines_SortedByScore() {
        List<CuisineScore> results = restaurantDAO.getWorstCuisinesByAverageScoreInBorough("Manhattan", 5);
        
        // Verify sorted by average score (ascending = worst first)
        for (int i = 1; i < results.size(); i++) {
            assertTrue(
                "Should be sorted ascending (worst first)",
                results.get(i - 1).getAvgScore() <= results.get(i).getAvgScore()
            );
        }
    }
    
    // =============== USE CASE 4 TESTS ===============
    
    @Test
    public void testUseCase4_GetCuisinesWithMinimumCount_500() {
        List<String> results = restaurantDAO.getCuisinesWithMinimumCount(500);
        
        assertNotNull("Results should not be null", results);
        assertFalse("Should have cuisines with >500 restaurants", results.isEmpty());
    }
    
    @Test
    public void testUseCase4_CuisinesWithMinCount_Alphabetical() {
        List<String> results = restaurantDAO.getCuisinesWithMinimumCount(500);
        
        // Verify sorted alphabetically
        for (int i = 1; i < results.size(); i++) {
            assertTrue(
                "Should be sorted alphabetically",
                results.get(i - 1).compareTo(results.get(i)) <= 0
            );
        }
    }
    
    @Test
    public void testUseCase4_CuisinesWithHighMinCount() {
        List<String> results = restaurantDAO.getCuisinesWithMinimumCount(1000);
        
        // Should have fewer results with higher minimum count
        assertNotNull("Results should not be null", results);
    }
    
    // =============== VALIDATION TESTS ===============
    
    @Test(expected = IllegalArgumentException.class)
    public void testFindAll_NegativeLimit() {
        restaurantDAO.findAll(-1);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testFindAll_ZeroLimit() {
        restaurantDAO.findAll(0);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testFindByCuisine_EmptyCuisine() {
        restaurantDAO.findByCuisine("", 10);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testFindByCuisine_NullCuisine() {
        restaurantDAO.findByCuisine(null, 10);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCountByField_InvalidFieldName() {
        restaurantDAO.countByField("field-with-dash");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCountByField_FieldNameWithDot() {
        restaurantDAO.countByField("field.nested");
    }
    
    // =============== GENERIC QUERIES TESTS ===============
    
    @Test
    public void testCountAll_ReturnsPositiveNumber() {
        long count = restaurantDAO.countAll();
        assertTrue("Total count should be positive", count > 0);
    }
    
    @Test
    public void testCountByCuisine_Italian() {
        long count = restaurantDAO.countByCuisine("Italian");
        assertTrue("Italian restaurants count should be positive", count > 0);
    }
    
    @Test
    public void testFindByCuisine_Italian() {
        List<Restaurant> results = restaurantDAO.findByCuisine("Italian", 10);
        assertNotNull("Results should not be null", results);
        assertFalse("Should find Italian restaurants", results.isEmpty());
    }
}
