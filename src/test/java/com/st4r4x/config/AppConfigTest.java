package com.st4r4x.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires pour AppConfig
 */
public class AppConfigTest {

    private String savedJwtSecret;
    private Object savedDotenv;
    private boolean dotenvCleared = false;

    // Helper method (private) to get the AppConfig.properties static field:
    private Properties getAppConfigProperties() {
        try {
            Field f = AppConfig.class.getDeclaredField("properties");
            f.setAccessible(true);
            return (Properties) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Null out AppConfig.dotenv so .env file on disk cannot interfere with JWT tests
    private void clearDotenv() {
        try {
            Field f = AppConfig.class.getDeclaredField("dotenv");
            f.setAccessible(true);
            savedDotenv = f.get(null);
            f.set(null, null);
            dotenvCleared = true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void restoreJwtSecret() {
        Properties props = getAppConfigProperties();
        if (savedJwtSecret == null) {
            props.remove("jwt.secret");
        } else {
            props.setProperty("jwt.secret", savedJwtSecret);
        }
        // Restore dotenv whenever clearDotenv() was called, even if the saved value is null
        if (dotenvCleared) {
            try {
                Field f = AppConfig.class.getDeclaredField("dotenv");
                f.setAccessible(true);
                f.set(null, savedDotenv);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            dotenvCleared = false;
        }
    }

    @Test
    void testGetMongoUri_DefaultValue() {
        // Should return default value when no env var is set
        String uri = AppConfig.getMongoUri();
        assertNotNull(uri);
        assertTrue(uri.contains("mongodb:"));
    }

    @Test
    void testGetMongoUri_ReturnsValidURL() {
        String uri = AppConfig.getMongoUri();
        assertTrue(uri.startsWith("mongodb://"), "URI should start with mongodb://");
    }

    @Test
    void testGetMongoDatabase_DefaultValue() {
        String database = AppConfig.getMongoDatabase();
        assertNotNull(database);
        assertFalse(database.isEmpty());
    }

    @Test
    void testGetMongoDatabase_ReturnsNewyork() {
        // Default should be newyork
        String database = AppConfig.getMongoDatabase();
        assertEquals("newyork", database);
    }

    @Test
    void testGetMongoCollection_DefaultValue() {
        String collection = AppConfig.getMongoCollection();
        assertNotNull(collection);
        assertFalse(collection.isEmpty());
    }

    @Test
    void testGetMongoCollection_ReturnsRestaurants() {
        // Default should be restaurants
        String collection = AppConfig.getMongoCollection();
        assertEquals("restaurants", collection);
    }

    @Test
    void testApplicationPropertiesLoaded() {
        // All three config values should be non-null
        assertNotNull(AppConfig.getMongoUri());
        assertNotNull(AppConfig.getMongoDatabase());
        assertNotNull(AppConfig.getMongoCollection());
    }

    @Test
    void testGetJwtSecret_throwsWhenAbsent() {
        assertThrows(IllegalStateException.class, () -> {
            clearDotenv();
            Properties props = getAppConfigProperties();
            savedJwtSecret = props.getProperty("jwt.secret");
            props.remove("jwt.secret");
            AppConfig.getJwtSecret();
        });
    }

    @Test
    void testGetJwtSecret_throwsWhenTooShort() {
        assertThrows(IllegalStateException.class, () -> {
            clearDotenv();
            Properties props = getAppConfigProperties();
            savedJwtSecret = props.getProperty("jwt.secret");
            props.setProperty("jwt.secret", "short-less-than-32-chars");  // 24 chars — too short
            AppConfig.getJwtSecret();
        });
    }

    @Test
    void testGetJwtSecret_succeedsWithValidSecret() {
        clearDotenv();
        Properties props = getAppConfigProperties();
        savedJwtSecret = props.getProperty("jwt.secret");
        props.setProperty("jwt.secret", "exactly-32-characters-long-123456");  // 34 chars — valid
        String result = AppConfig.getJwtSecret();
        assertEquals("exactly-32-characters-long-123456", result);
    }
}
