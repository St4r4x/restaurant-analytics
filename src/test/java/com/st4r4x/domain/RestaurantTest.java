package com.st4r4x.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires pour les POJOs
 */
public class RestaurantTest {

    @Test
    public void testRestaurantCreation() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(new ObjectId());
        restaurant.setName("Test Restaurant");
        restaurant.setCuisine("Italian");
        restaurant.setBorough("Manhattan");

        assertNotNull(restaurant.getId());
        assertEquals("Test Restaurant", restaurant.getName());
        assertEquals("Italian", restaurant.getCuisine());
        assertEquals("Manhattan", restaurant.getBorough());
    }

    @Test
    public void testRestaurantToString() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setCuisine("Italian");
        restaurant.setBorough("Manhattan");

        String toString = restaurant.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("Test Restaurant"), "toString should contain name");
    }
}
