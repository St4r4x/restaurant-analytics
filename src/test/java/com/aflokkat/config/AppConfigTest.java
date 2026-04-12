package com.aflokkat.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Properties;

import org.junit.After;
import org.junit.Test;

/**
 * Tests unitaires pour AppConfig
 */
public class AppConfigTest {

    private String savedJwtSecret;
    private Object savedDotenv;
    private boolean dotenvCleared = false;

    // Helper method (private) to get the AppConfig.properties static field:
    private Properties getAppConfigProperties() throws Exception {
        Field f = AppConfig.class.getDeclaredField("properties");
        f.setAccessible(true);
        return (Properties) f.get(null);
    }

    // Null out AppConfig.dotenv so .env file on disk cannot interfere with JWT tests
    private void clearDotenv() throws Exception {
        Field f = AppConfig.class.getDeclaredField("dotenv");
        f.setAccessible(true);
        savedDotenv = f.get(null);
        f.set(null, null);
        dotenvCleared = true;
    }

    @After
    public void restoreJwtSecret() throws Exception {
        Properties props = getAppConfigProperties();
        if (savedJwtSecret == null) {
            props.remove("jwt.secret");
        } else {
            props.setProperty("jwt.secret", savedJwtSecret);
        }
        // Restore dotenv whenever clearDotenv() was called, even if the saved value is null
        if (dotenvCleared) {
            Field f = AppConfig.class.getDeclaredField("dotenv");
            f.setAccessible(true);
            f.set(null, savedDotenv);
            dotenvCleared = false;
        }
    }

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

    @Test(expected = IllegalStateException.class)
    public void testGetJwtSecret_throwsWhenAbsent() throws Exception {
        clearDotenv();
        Properties props = getAppConfigProperties();
        savedJwtSecret = props.getProperty("jwt.secret");
        props.remove("jwt.secret");
        AppConfig.getJwtSecret();
    }

    @Test(expected = IllegalStateException.class)
    public void testGetJwtSecret_throwsWhenTooShort() throws Exception {
        clearDotenv();
        Properties props = getAppConfigProperties();
        savedJwtSecret = props.getProperty("jwt.secret");
        props.setProperty("jwt.secret", "short-less-than-32-chars");  // 24 chars — too short
        AppConfig.getJwtSecret();
    }

    @Test
    public void testGetJwtSecret_succeedsWithValidSecret() throws Exception {
        clearDotenv();
        Properties props = getAppConfigProperties();
        savedJwtSecret = props.getProperty("jwt.secret");
        props.setProperty("jwt.secret", "exactly-32-characters-long-123456");  // 34 chars — valid
        String result = AppConfig.getJwtSecret();
        assertEquals("exactly-32-characters-long-123456", result);
    }
}
