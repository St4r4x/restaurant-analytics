package com.aflokkat.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Configuration manager pour charger les properties et .env
 */
public class AppConfig {
    private static final Properties properties = new Properties();
    private static Dotenv dotenv;

    static {
        // Charger .env s'il existe
        try {
            dotenv = Dotenv.configure().ignoreIfMissing().load();
        } catch (Exception e) {
            System.err.println("Avertissement: .env n'a pas pu être chargé");
        }

        // Charger application.properties
        try (InputStream input = AppConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors du chargement de application.properties", e);
        }
    }

    public static String getMongoUri() {
        return getProperty("mongodb.uri", "mongodb://localhost:27017");
    }

    public static String getMongoDatabase() {
        return getProperty("mongodb.database", "newyork");
    }

    public static String getMongoCollection() {
        return getProperty("mongodb.collection", "restaurants");
    }

    public static String getNycApiUrl() {
        return getProperty("nyc.api.url", "https://data.cityofnewyork.us/resource/43nn-pn8j.json");
    }

    public static String getNycApiToken() {
        return getProperty("nyc.api.token", "");
    }

    public static int getNycApiPageSize() {
        String value = getProperty("nyc.api.page-size", "1000");
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return 1000; }
    }

    private static String getProperty(String key, String defaultValue) {
        // 1. Variable d'environnement système (Docker, CI...)
        String envKey = key.replace(".", "_").toUpperCase();
        String envValue = System.getenv(envKey);
        if (envValue != null) return envValue;

        // 2. Fichier .env
        if (dotenv != null) {
            String dotenvValue = dotenv.get(envKey, null);
            if (dotenvValue != null) return dotenvValue;
        }

        // 3. application.properties
        return properties.getProperty(key, defaultValue);
    }
}
