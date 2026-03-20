package com.aflokkat.dao;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;

import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.aflokkat.aggregation.AggregationCount;
import com.aflokkat.aggregation.BoroughCuisineScore;
import com.aflokkat.aggregation.CuisineScore;
import com.aflokkat.config.AppConfig;
import com.aflokkat.config.MongoClientFactory;
import com.aflokkat.domain.Restaurant;
import com.aflokkat.util.ValidationUtil;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

/**
 * Implémentation du DAO pour MongoDB avec pattern Singleton pour la connexion
 */
@Repository
public class RestaurantDAOImpl implements RestaurantDAO {
    private static final Logger logger = LoggerFactory.getLogger(RestaurantDAOImpl.class);
    
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Restaurant> restaurantCollection;
    private CodecRegistry pojoCodecRegistry;
    
    public RestaurantDAOImpl() {
        // Utiliser le MongoClient Singleton
        this.mongoClient = MongoClientFactory.getInstance();
        this.database = mongoClient.getDatabase(AppConfig.getMongoDatabase());
        
        // Initialiser le CodecRegistry une seule fois
        this.pojoCodecRegistry = getPojoCodecRegistry();
        
        this.restaurantCollection = database.withCodecRegistry(pojoCodecRegistry)
                .getCollection(AppConfig.getMongoCollection(), Restaurant.class);
        
        logger.info("RestaurantDAOImpl initialisé - DB: {}, Collection: {}", 
                AppConfig.getMongoDatabase(), AppConfig.getMongoCollection());
    }
    
    /**
     * Crée et retourne le CodecRegistry pour les POJOs (extraction de la redondance)
     */
    private CodecRegistry getPojoCodecRegistry() {
        return fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );
    }
    
    /**
     * Agrégation générique réutilisable
     */
    private <T> List<T> aggregate(List<Document> pipeline, Class<T> resultClass) {
        List<T> results = new ArrayList<>();
        MongoCollection<T> collection = database.withCodecRegistry(pojoCodecRegistry)
                .getCollection(AppConfig.getMongoCollection(), resultClass);
        collection.aggregate(pipeline).forEach(results::add);
        return results;
    }
    
    @Override
    public List<Restaurant> findAll(int limit) {
        ValidationUtil.requirePositive(limit, "limit");
        List<Restaurant> results = new ArrayList<>();
        restaurantCollection.find()
            .limit(limit)
            .forEach(results::add);
        return results;
    }
    
    @Override
    public List<Restaurant> findByCuisine(String cuisine, int limit) {
        ValidationUtil.requireNonEmpty(cuisine, "cuisine");
        ValidationUtil.requirePositive(limit, "limit");
        List<Restaurant> results = new ArrayList<>();
        Document filter = new Document("cuisine", cuisine);
        
        restaurantCollection.find(filter)
            .sort(new Document("name", 1))
            .limit(limit)
            .forEach(results::add);
        return results;
    }
    
    @Override
    public List<Restaurant> findWithFilters(Map<String, Object> filters, int limit) {
        ValidationUtil.requirePositive(limit, "limit");
        if (filters == null || filters.isEmpty()) {
            throw new IllegalArgumentException("filters ne peut pas être null ou vide");
        }
        List<Restaurant> results = new ArrayList<>();
        Document filterDoc = new Document(filters);
        
        restaurantCollection.find(filterDoc)
            .limit(limit)
            .forEach(results::add);
        return results;
    }
    
    @Override
    public long countAll() {
        return restaurantCollection.countDocuments();
    }
    
    @Override
    public long countByCuisine(String cuisine) {
        ValidationUtil.requireNonEmpty(cuisine, "cuisine");
        Document filter = new Document("cuisine", cuisine);
        return restaurantCollection.countDocuments(filter);
    }
    
    @Override
    public Map<String, Long> getStatisticsByBorough() {
        Map<String, Long> stats = new HashMap<>();
        countByField("borough").forEach(count -> stats.put(count.getId(), (long) count.getCount()));
        return stats;
    }
    
    @Override
    public List<AggregationCount> countByField(String fieldName) {
        ValidationUtil.validateFieldName(fieldName);
        logger.debug("Agrégation: comptage par champ '{}'", fieldName);
        return aggregate(Arrays.asList(
            new Document("$group", new Document()
                .append("_id", "$" + fieldName)
                .append("count", new Document("$sum", 1))),
            new Document("$sort", new Document("count", -1))
        ), AggregationCount.class);
    }
    
    @Override
    public List<AggregationCount> getRestaurantCountByBorough() {
        logger.debug("Requête: Comptage des restaurants par quartier");
        return countByField("borough");
    }
    
    @Override
    public List<BoroughCuisineScore> getAverageScoreByCuisineAndBorough(String cuisine) {
        ValidationUtil.requireNonEmpty(cuisine, "cuisine");
        logger.debug("Requête: Score moyen par quartier pour cuisine '{}'", cuisine);
        return aggregate(Arrays.asList(
            new Document("$match", new Document("cuisine", cuisine)),
            new Document("$unwind", "$grades"),
            new Document("$group", new Document()
                .append("_id", "$borough")
                .append("avgScore", new Document("$avg", "$grades.score"))),
            new Document("$sort", new Document("_id", 1))
        ), BoroughCuisineScore.class);
    }
    
    @Override
    public List<CuisineScore> getWorstCuisinesByAverageScoreInBorough(String borough, int limit) {
        ValidationUtil.requireNonEmpty(borough, "borough");
        ValidationUtil.requirePositive(limit, "limit");
        return aggregate(Arrays.asList(
            new Document("$match", new Document("borough", borough)),
            new Document("$unwind", "$grades"),
            new Document("$group", new Document()
                .append("_id", "$cuisine")
                .append("avgScore", new Document("$avg", "$grades.score"))
                .append("count", new Document("$sum", 1))),
            new Document("$sort", new Document("avgScore", 1)),
            new Document("$limit", limit)
        ), CuisineScore.class);
    }
    
    @Override
    public List<CuisineScore> getWorstCuisinesByAverageScore(int limit) {
        ValidationUtil.requirePositive(limit, "limit");
        return aggregate(Arrays.asList(
            new Document("$unwind", "$grades"),
            new Document("$group", new Document()
                .append("_id", "$cuisine")
                .append("avgScore", new Document("$avg", "$grades.score"))
                .append("count", new Document("$sum", 1))),
            new Document("$sort", new Document("avgScore", 1)),
            new Document("$limit", limit)
        ), CuisineScore.class);
    }

    @Override
    public List<String> getDistinctCuisines() {
        List<String> results = new ArrayList<>();
        restaurantCollection.distinct("cuisine", String.class)
            .forEach(results::add);
        java.util.Collections.sort(results);
        return results;
    }

    @Override
    public List<String> getCuisinesWithMinimumCount(int minCount) {
        ValidationUtil.requirePositive(minCount, "minCount");
        List<String> results = new ArrayList<>();
        aggregate(Arrays.asList(
            new Document("$group", new Document()
                .append("_id", "$cuisine")
                .append("count", new Document("$sum", 1))),
            new Document("$match", new Document("count", new Document("$gte", minCount))),
            new Document("$sort", new Document("_id", 1))
        ), AggregationCount.class).forEach(doc -> results.add(doc.getId()));
        return results;
    }
    
    @Override
    public int upsertRestaurants(List<Restaurant> restaurants) {
        if (restaurants == null || restaurants.isEmpty()) return 0;

        ReplaceOptions upsertOption = new ReplaceOptions().upsert(true);
        List<WriteModel<Restaurant>> operations = new ArrayList<>(restaurants.size());

        for (Restaurant r : restaurants) {
            if (r.getRestaurantId() == null || r.getRestaurantId().isEmpty()) continue;
            Bson filter = new Document("restaurant_id", r.getRestaurantId());
            operations.add(new ReplaceOneModel<>(filter, r, upsertOption));
        }

        if (operations.isEmpty()) return 0;
        restaurantCollection.bulkWrite(operations);

        logger.info("Upserted {} restaurants into MongoDB", operations.size());
        return operations.size();
    }

    /**
     * Ferme la connexion MongoDB via le Singleton Factory
     */
    @Override
    public void close() {
        logger.info("Fermeture du DAO");
        MongoClientFactory.closeInstance();
    }
}
