package com.st4r4x.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests unitaires pour les POJOs d'agrégation
 */
public class AggregationPojoTest {

    @Test
    public void testAggregationCountCreation() {
        AggregationCount count = new AggregationCount();
        count.setId("Manhattan");
        count.setCount(100);

        assertEquals("Manhattan", count.getId());
        assertEquals(100L, (long) count.getCount());
    }

    @Test
    public void testAggregationCountToString() {
        AggregationCount count = new AggregationCount();
        count.setId("Manhattan");
        count.setCount(10259);

        String toString = count.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("Manhattan"), "toString should contain borough name");
        assertTrue(toString.contains("10259"), "toString should contain count");
    }

    @Test
    public void testBoroughCuisineScoreCreation() {
        BoroughCuisineScore score = new BoroughCuisineScore();
        score.setBorough("Manhattan");
        score.setAvgScore(12.5);

        assertEquals("Manhattan", score.getBorough());
        assertEquals(12.5, score.getAvgScore(), 0.01);
    }

    @Test
    public void testBoroughCuisineScoreToString() {
        BoroughCuisineScore score = new BoroughCuisineScore();
        score.setBorough("Manhattan");
        score.setAvgScore(12.11);

        String toString = score.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("Manhattan"), "toString should contain borough");
    }

    @Test
    public void testCuisineScoreCreation() {
        CuisineScore score = new CuisineScore();
        score.setCuisine("Italian");
        score.setAvgScore(12.5);
        score.setCount(150);

        assertEquals("Italian", score.getCuisine());
        assertEquals(12.5, score.getAvgScore(), 0.01);
        assertEquals(150L, (long) score.getCount());
    }

    @Test
    public void testCuisineScoreToString() {
        CuisineScore score = new CuisineScore();
        score.setCuisine("Italian");
        score.setAvgScore(12.25);
        score.setCount(1000);

        String toString = score.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("Italian"), "toString should contain cuisine");
        assertTrue(toString.contains("1000"), "toString should contain count");
    }
}
