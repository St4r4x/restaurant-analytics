package com.st4r4x.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

/**
 * Singleton MongoClient Factory
 * Garantit une seule connexion MongoDB pour toute l'application
 */
public class MongoClientFactory {
    private static MongoClient mongoClient;
    
    private MongoClientFactory() {
        // Classe utilitaire, pas d'instantiation
    }
    
    /**
     * Récupère l'instance unique du MongoClient
     */
    public static synchronized MongoClient getInstance() {
        if (mongoClient == null) {
            mongoClient = MongoClients.create(AppConfig.getMongoUri());
        }
        return mongoClient;
    }
    
    /**
     * Ferme la connexion MongoDB
     */
    public static synchronized void closeInstance() {
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
        }
    }
}
