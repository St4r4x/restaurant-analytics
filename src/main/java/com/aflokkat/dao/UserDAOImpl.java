package com.aflokkat.dao;

import java.util.Objects;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.aflokkat.config.AppConfig;
import com.aflokkat.config.MongoClientFactory;
import com.aflokkat.domain.User;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

@Repository
public class UserDAOImpl implements UserDAO {
    private static final Logger logger = LoggerFactory.getLogger(UserDAOImpl.class);
    private static final String USERS_COLLECTION = "users";

    private final MongoCollection<Document> usersCollection;

    public UserDAOImpl() {
        MongoClient mongoClient = MongoClientFactory.getInstance();
        MongoDatabase database = mongoClient.getDatabase(AppConfig.getMongoDatabase());
        this.usersCollection = database.getCollection(USERS_COLLECTION);
        logger.info("UserDAOImpl initialisé - DB: {}, Collection: {}", AppConfig.getMongoDatabase(), USERS_COLLECTION);
    }

    @Override
    public User createUser(User user) {
        Document doc = new Document();
        doc.append("username", user.getUsername())
           .append("email", user.getEmail())
           .append("passwordHash", user.getPasswordHash())
           .append("role", user.getRole())
           .append("createdAt", user.getCreatedAt())
           .append("updatedAt", user.getUpdatedAt());

        usersCollection.insertOne(doc);
        ObjectId id = doc.getObjectId("_id");
        user.setId(id.toHexString());
        return user;
    }

    @Override
    public User findByUsername(String username) {
        Document doc = usersCollection.find(new Document("username", username)).first();
        return toUser(doc);
    }

    @Override
    public User findByEmail(String email) {
        Document doc = usersCollection.find(new Document("email", email)).first();
        return toUser(doc);
    }

    @Override
    public User findById(String id) {
        if (id == null) return null;
        Document doc = usersCollection.find(new Document("_id", new ObjectId(id))).first();
        return toUser(doc);
    }

    private User toUser(Document doc) {
        if (Objects.isNull(doc)) return null;
        User user = new User();
        user.setId(doc.getObjectId("_id").toHexString());
        user.setUsername(doc.getString("username"));
        user.setEmail(doc.getString("email"));
        user.setPasswordHash(doc.getString("passwordHash"));
        user.setRole(doc.getString("role"));
        user.setCreatedAt(doc.getDate("createdAt"));
        user.setUpdatedAt(doc.getDate("updatedAt"));
        return user;
    }
}
