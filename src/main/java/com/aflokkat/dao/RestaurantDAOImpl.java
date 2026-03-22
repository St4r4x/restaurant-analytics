package com.aflokkat.dao;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mongodb.client.model.IndexOptions;
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
import com.aflokkat.dto.AtRiskEntry;
import com.aflokkat.dto.HeatmapPoint;

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

        // 2dsphere index for geospatial queries (idempotent)
        restaurantCollection.createIndex(
            new Document("address.coord", "2dsphere"),
            new IndexOptions().background(true)
        );

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
        restaurantCollection.aggregate(pipeline, resultClass).forEach(results::add);
        return results;
    }
    
    @Override
    public List<Restaurant> findAll(int limit) {
        List<Restaurant> results = new ArrayList<>();
        // type 3 = BSON embedded document — skip legacy docs where address is a plain string
        restaurantCollection.find(new Document("address", new Document("$type", 3)))
            .limit(limit)
            .forEach(results::add);
        return results;
    }
    
    @Override
    public List<Restaurant> findByCuisine(String cuisine, int limit) {
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
        List<Restaurant> results = new ArrayList<>();
        // type 3 = BSON embedded document — skip legacy docs where address is a plain string
        Document filterDoc = new Document(filters).append("address", new Document("$type", 3));

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
    public Restaurant findRandom() {
        List<Restaurant> result = new ArrayList<>();
        restaurantCollection.aggregate(Arrays.asList(
            new Document("$sample", new Document("size", 1))
        )).forEach(result::add);
        return result.isEmpty() ? null : result.get(0);
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

    @Override
    public List<Restaurant> findByIds(List<String> restaurantIds) {
        List<Restaurant> results = new ArrayList<>();
        restaurantCollection.find(new Document("restaurant_id",
            new Document("$in", restaurantIds))).forEach(results::add);
        return results;
    }

    @Override
    public Restaurant findByRestaurantId(String restaurantId) {
        return restaurantCollection.find(new Document("restaurant_id", restaurantId)).first();
    }

    @Override
    public List<Restaurant> findRecentlyInspected(int days, int limit) {
        long cutoffMs = System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000;
        java.util.Date cutoff = new java.util.Date(cutoffMs);
        // Use $toDate to handle both BSON Date and ISO string formats
        List<Document> idPipeline = Arrays.asList(
            new Document("$unwind", "$grades"),
            new Document("$match", new Document("$expr", new Document("$gte", Arrays.asList(
                new Document("$toDate", "$grades.date"),
                new Document("$toDate", cutoff)
            )))),
            new Document("$sort", new Document("grades.date", -1)),
            new Document("$group", new Document("_id", "$restaurant_id")),
            new Document("$limit", limit)
        );
        List<String> ids = new ArrayList<>();
        restaurantCollection.aggregate(idPipeline, Document.class)
            .forEach(doc -> ids.add(doc.getString("_id")));
        if (ids.isEmpty()) return new ArrayList<>();
        return findByIds(ids);
    }

    @Override
    public List<Restaurant> findNearby(double lat, double lng, int radiusMeters, int limit) {
        List<Document> pipeline = Arrays.asList(
            new Document("$geoNear", new Document()
                .append("near", new Document("type", "Point")
                    .append("coordinates", Arrays.asList(lng, lat)))
                .append("distanceField", "distance")
                .append("maxDistance", radiusMeters)
                .append("spherical", true)),
            new Document("$limit", limit)
        );
        return aggregate(pipeline, Restaurant.class);
    }

    @Override
    public List<HeatmapPoint> getHeatmapData(String borough, int limit) {
        List<Document> pipeline = new ArrayList<>();
        if (borough != null && !borough.isEmpty()) {
            pipeline.add(new Document("$match", new Document("borough", borough)));
        }
        pipeline.add(new Document("$match", new Document("address.coord", new Document("$exists", true))));
        pipeline.add(new Document("$addFields", new Document("latestScore",
            new Document("$arrayElemAt", Arrays.asList("$grades.score", 0)))));
        pipeline.add(new Document("$match", new Document("latestScore", new Document("$ne", null))));
        pipeline.add(new Document("$project", new Document("_id", 0)
            .append("lat", new Document("$arrayElemAt", Arrays.asList("$address.coord", 1)))
            .append("lng", new Document("$arrayElemAt", Arrays.asList("$address.coord", 0)))
            .append("weight", "$latestScore")));
        pipeline.add(new Document("$limit", limit));
        return aggregate(pipeline, HeatmapPoint.class);
    }

    @Override
    public List<AtRiskEntry> getAtRiskRestaurants(String borough, int limit) {
        List<Document> pipeline = new ArrayList<>();
        if (borough != null && !borough.isEmpty()) {
            pipeline.add(new Document("$match", new Document("borough", borough)));
        }
        pipeline.add(new Document("$project", new Document("_id", 0)
            .append("name", 1)
            .append("borough", 1)
            .append("cuisine", 1)
            .append("restaurant_id", 1)
            .append("lastGrades", new Document("$slice", Arrays.asList("$grades", 3)))));
        pipeline.add(new Document("$addFields", new Document()
            .append("lastGrade", new Document("$arrayElemAt", Arrays.asList("$lastGrades.grade", 0)))
            .append("lastScore", new Document("$arrayElemAt", Arrays.asList("$lastGrades.score", 0)))
            .append("consecutiveBadGrades", new Document("$size", new Document("$filter", new Document()
                .append("input", new Document("$ifNull", Arrays.asList("$lastGrades", Arrays.asList())))
                .append("as", "g")
                .append("cond", new Document("$in", Arrays.asList("$$g.grade",
                    Arrays.asList("C", "Z", "N", "P")))))))));
        pipeline.add(new Document("$match", new Document("lastGrade",
            new Document("$in", Arrays.asList("C", "Z")))));
        pipeline.add(new Document("$sort", new Document("lastScore", -1)));
        pipeline.add(new Document("$limit", limit));
        return aggregate(pipeline, AtRiskEntry.class);
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
