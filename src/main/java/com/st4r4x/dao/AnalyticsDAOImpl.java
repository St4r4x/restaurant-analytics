package com.st4r4x.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.springframework.stereotype.Repository;

import com.st4r4x.config.AppConfig;
import com.st4r4x.config.MongoClientFactory;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

@Repository
public class AnalyticsDAOImpl implements AnalyticsDAO {

    private final MongoDatabase database;
    private final String collectionName;

    public AnalyticsDAOImpl() {
        MongoClient mongoClient = MongoClientFactory.getInstance();
        this.database = mongoClient.getDatabase(AppConfig.getMongoDatabase());
        this.collectionName = AppConfig.getMongoCollection();
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
}
