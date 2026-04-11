package com.st4r4x.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests unitaires pour AppConfig
 */
public class AppConfigTest {
    
    @Test
    public void testGetMongoUri_DefaultValue() {
        // Should return default value when no env var is set
        String uri = AppConfig.getMongoUri();
        assertNotNull(uri);
        assertTrue(uri.contains("mongodb:"));
    }
    
    @Test
    public void testGetMongoUri_ReturnsValidURL() {
        String uri = AppConfig.getMongoUri();
        assertTrue("URI should start with mongodb://", uri.startsWith("mongodb://"));
    }
    
    @Test
    public void testGetMongoDatabase_DefaultValue() {
        String database = AppConfig.getMongoDatabase();
        assertNotNull(database);
        assertFalse(database.isEmpty());
    }
    
    @Test
    public void testGetMongoDatabase_ReturnsNewyork() {
        // Default should be newyork
        String database = AppConfig.getMongoDatabase();
        assertEquals("newyork", database);
    }
    
    @Test
    public void testGetMongoCollection_DefaultValue() {
        String collection = AppConfig.getMongoCollection();
        assertNotNull(collection);
        assertFalse(collection.isEmpty());
    }
    
    @Test
    public void testGetMongoCollection_ReturnsRestaurants() {
        // Default should be restaurants
        String collection = AppConfig.getMongoCollection();
        assertEquals("restaurants", collection);
    }
    
    @Test
    public void testApplicationPropertiesLoaded() {
        // All three config values should be non-null
        assertNotNull(AppConfig.getMongoUri());
        assertNotNull(AppConfig.getMongoDatabase());
        assertNotNull(AppConfig.getMongoCollection());
    }
}
