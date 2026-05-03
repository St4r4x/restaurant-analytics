package com.st4r4x.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import org.springframework.stereotype.Repository;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.st4r4x.aggregation.CuisineScore;
import com.st4r4x.config.AppConfig;
import com.st4r4x.config.MongoClientFactory;
import com.st4r4x.domain.Restaurant;
import com.st4r4x.dto.AtRiskEntry;
import com.st4r4x.dto.UncontrolledEntry;

@Repository
public class AnalyticsDAOImpl implements AnalyticsDAO {

    private final MongoDatabase database;
    private final String collectionName;
    private final CodecRegistry pojoCodecRegistry;

    public AnalyticsDAOImpl() {
        MongoClient mongoClient = MongoClientFactory.getInstance();
        this.database = mongoClient.getDatabase(AppConfig.getMongoDatabase());
        this.collectionName = AppConfig.getMongoCollection();
        this.pojoCodecRegistry = fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );
    }

    private <T> List<T> aggregate(List<Document> pipeline, Class<T> clazz) {
        List<T> results = new ArrayList<>();
        database.withCodecRegistry(pojoCodecRegistry)
                .getCollection(collectionName, clazz)
                .aggregate(pipeline)
                .forEach(results::add);
        return results;
    }

    @Override
    public long countAll() {
        return database.getCollection(collectionName).countDocuments();
    }

    @Override
    public List<Document> findMapPoints() {
        List<Document> pipeline = Arrays.asList(
            new Document("$match", new Document("address.coord", new Document("$exists", true))),
            new Document("$project", new Document("_id", 0)
                .append("restaurantId", "$restaurant_id")
                .append("name", 1)
                .append("grade", new Document("$arrayElemAt", Arrays.asList("$grades.grade", 0)))
                .append("borough", 1)
                .append("cuisine", 1)
                .append("lat",  new Document("$arrayElemAt", Arrays.asList("$address.coord", 1)))
                .append("lng",  new Document("$arrayElemAt", Arrays.asList("$address.coord", 0)))
            )
        );
        List<Document> results = new ArrayList<>();
        database.getCollection(collectionName)
                .aggregate(pipeline)
                .forEach(results::add);
        return results;
    }

    @Override
    public List<Document> findBoroughGradeDistribution() {
        List<Document> pipeline = Arrays.asList(
            new Document("$addFields", new Document("lastGrade",
                new Document("$arrayElemAt", Arrays.asList("$grades.grade", 0)))),
            new Document("$match", new Document("lastGrade",
                new Document("$in", Arrays.asList("A", "B", "C")))),
            new Document("$group", new Document()
                .append("_id", new Document()
                    .append("borough", "$borough")
                    .append("grade", "$lastGrade"))
                .append("count", new Document("$sum", 1))),
            new Document("$group", new Document()
                .append("_id", "$_id.borough")
                .append("grades", new Document("$push", new Document()
                    .append("grade", "$_id.grade")
                    .append("count", "$count")))),
            new Document("$sort", new Document("_id", 1))
        );
        List<Document> results = new ArrayList<>();
        database.getCollection(collectionName)
            .aggregate(pipeline)
            .forEach(results::add);
        return results;
    }

    @Override
    public long countAtRiskRestaurants() {
        List<Document> pipeline = Arrays.asList(
            new Document("$addFields", new Document("lastGrade",
                new Document("$arrayElemAt", Arrays.asList("$grades.grade", 0)))),
            new Document("$match", new Document("lastGrade",
                new Document("$in", Arrays.asList("C", "Z")))),
            new Document("$count", "total")
        );
        List<Document> results = new ArrayList<>();
        database.getCollection(collectionName)
            .aggregate(pipeline)
            .forEach(results::add);
        return results.isEmpty() ? 0L : (long) results.get(0).getInteger("total", 0);
    }

    @Override
    public List<CuisineScore> findWorstCuisinesByAverageScore(int limit) {
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
    public List<CuisineScore> findBestCuisinesByAverageScore(int limit) {
        return aggregate(Arrays.asList(
            new Document("$unwind", "$grades"),
            new Document("$group", new Document()
                .append("_id", "$cuisine")
                .append("avgScore", new Document("$avg", "$grades.score"))
                .append("count", new Document("$sum", 1))),
            new Document("$sort", new Document("avgScore", -1)),
            new Document("$limit", limit)
        ), CuisineScore.class);
    }

    @Override
    public List<AtRiskEntry> findAtRiskRestaurants(String borough, int limit) {
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

    @Override
    public List<UncontrolledEntry> findUncontrolled(String borough, int limit) {
        long twelveMonthsAgoMs = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000;
        List<Document> pipeline = new ArrayList<>();
        if (borough != null && !borough.isEmpty()) {
            pipeline.add(new Document("$match", new Document("borough", borough)));
        }
        pipeline.add(new Document("$addFields", new Document()
            .append("lastGrade", new Document("$arrayElemAt", Arrays.asList("$grades.grade", 0)))
            .append("lastScore", new Document("$arrayElemAt", Arrays.asList("$grades.score", 0)))
            .append("lastInspectionDate", new Document("$arrayElemAt", Arrays.asList("$grades.date", 0)))
        ));
        pipeline.add(new Document("$addFields", new Document(
            "lastInspectionMs", new Document("$toLong",
                new Document("$toDate", new Document("$ifNull",
                    Arrays.asList("$lastInspectionDate", new java.util.Date(0)))))
        )));
        pipeline.add(new Document("$match", new Document("$or", Arrays.asList(
            new Document("lastGrade", new Document("$in", Arrays.asList("C", "Z"))),
            new Document("lastInspectionMs", new Document("$lt", twelveMonthsAgoMs))
        ))));
        pipeline.add(new Document("$project", new Document()
            .append("_id", 0)
            .append("restaurant_id", "$restaurant_id")
            .append("name", 1)
            .append("borough", 1)
            .append("cuisine", 1)
            .append("lastGrade", 1)
            .append("lastScore", 1)
            .append("daysSinceInspection", new Document("$toInt",
                new Document("$divide", Arrays.asList(
                    new Document("$subtract", Arrays.asList(System.currentTimeMillis(), "$lastInspectionMs")),
                    86_400_000L
                ))
            ))
        ));
        pipeline.add(new Document("$sort", new Document("lastScore", -1)));
        pipeline.add(new Document("$limit", limit));
        return aggregate(pipeline, UncontrolledEntry.class);
    }

    @Override
    public List<Restaurant> searchByNameOrAddress(String q, int limit) {
        Document regex = new Document("$regex", q).append("$options", "i");
        Document filter = new Document("$or", Arrays.asList(
            new Document("name", regex),
            new Document("address.street", regex)
        ));
        List<Restaurant> results = new ArrayList<>();
        database.withCodecRegistry(pojoCodecRegistry)
                .getCollection(collectionName, Restaurant.class)
                .find(filter).limit(limit).forEach(results::add);
        return results;
    }
}
